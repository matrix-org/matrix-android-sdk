/*
 * Copyright 2018 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.matrix.androidsdk.crypto.cryptostore.db

import android.content.Context
import android.text.TextUtils
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.Sort
import io.realm.kotlin.where
import org.matrix.androidsdk.core.Log
import org.matrix.androidsdk.crypto.IncomingRoomKeyRequest
import org.matrix.androidsdk.crypto.OutgoingRoomKeyRequest
import org.matrix.androidsdk.crypto.cryptostore.IMXCryptoStore
import org.matrix.androidsdk.crypto.cryptostore.db.model.*
import org.matrix.androidsdk.crypto.cryptostore.db.query.delete
import org.matrix.androidsdk.crypto.cryptostore.db.query.getById
import org.matrix.androidsdk.crypto.cryptostore.db.query.getOrCreate
import org.matrix.androidsdk.crypto.data.MXDeviceInfo
import org.matrix.androidsdk.crypto.data.MXOlmInboundGroupSession2
import org.matrix.androidsdk.crypto.data.MXOlmSession
import org.matrix.androidsdk.crypto.model.crypto.RoomKeyRequestBody
import org.matrix.androidsdk.rest.model.login.Credentials
import org.matrix.olm.OlmAccount
import org.matrix.olm.OlmException
import java.io.File
import kotlin.collections.set

// enableFileEncryption is used to migrate the previous store
class RealmCryptoStore(private val enableFileEncryption: Boolean = false) : IMXCryptoStore {

    /* ==========================================================================================
     * Memory cache, to correctly release JNI objects
     * ========================================================================================== */

    // The olm account
    private var olmAccount: OlmAccount? = null

    // Cache for OlmSession, to release them properly
    private val olmSessionsToRelease = HashMap<String, MXOlmSession>()

    // Cache for InboundGroupSession, to release them properly
    private val inboundGroupSessionToRelease = HashMap<String, MXOlmInboundGroupSession2>()

    /* ==========================================================================================
     * Other data
     * ========================================================================================== */

    private lateinit var credentials: Credentials
    private lateinit var realmConfiguration: RealmConfiguration

    override fun initWithCredentials(context: Context, credentials: Credentials) {
        this.credentials = credentials

        // Ensure realm is initialized
        Realm.init(context.applicationContext)

        realmConfiguration = RealmConfiguration.Builder()
                .directory(File(context.filesDir, (credentials.getUserId() ?: "defaultUserId").hash()))
                .name("crypto_store.realm")
                .modules(RealmCryptoStoreModule())
                .schemaVersion(RealmCryptoStoreMigration.CRYPTO_STORE_SCHEMA_VERSION)
                .migration(RealmCryptoStoreMigration)
                .initialData(CryptoFileStoreImporter(enableFileEncryption, context, credentials))
                .build()
    }

    override fun isCorrupted(): Boolean {
        // Should not happen anymore
        return false
    }

    override fun hasData(): Boolean {
        return doWithRealm(realmConfiguration) {
            !it.isEmpty
                    // Check if there is a MetaData object
                    && it.where<CryptoMetadataEntity>().count() > 0
        }
    }

    override fun deleteStore() {
        doRealmTransaction(realmConfiguration) {
            it.deleteAll()
        }
    }

    override fun open() {
        // Ensure CryptoMetadataEntity is inserted in DB
        doRealmTransaction(realmConfiguration) { realm ->
            var currentMetadata = realm.where<CryptoMetadataEntity>().findFirst()

            var deleteAll = false

            if (currentMetadata != null) {
                // Check credentials
                // The device id may not have been provided in credentials.
                // Check it only if provided, else trust the stored one.
                if (!TextUtils.equals(currentMetadata.userId, credentials.getUserId())
                        || (credentials.getDeviceId() != null && !TextUtils.equals(credentials.getDeviceId(), currentMetadata.deviceId))) {
                    Log.w(LOG_TAG, "## open() : Credentials do not match, close this store and delete data")
                    deleteAll = true
                    currentMetadata = null
                }
            }

            if (currentMetadata == null) {
                if (deleteAll) {
                    realm.deleteAll()
                }

                // Metadata not found, or database cleaned, create it
                realm.createObject(CryptoMetadataEntity::class.java, credentials.getUserId()).apply {
                    deviceId = credentials.getDeviceId()
                }
            }
        }
    }

    override fun close() {
        olmSessionsToRelease.forEach {
            it.value.olmSession.releaseSession()
        }
        olmSessionsToRelease.clear()

        inboundGroupSessionToRelease.forEach {
            it.value.mSession.releaseSession()
        }
        inboundGroupSessionToRelease.clear()

        olmAccount?.releaseAccount()
    }

    override fun storeDeviceId(deviceId: String?) {
        doRealmTransaction(realmConfiguration) {
            it.where<CryptoMetadataEntity>().findFirst()?.deviceId = deviceId
        }
    }

    override fun getDeviceId(): String {
        return doRealmQueryAndCopy(realmConfiguration) {
            it.where<CryptoMetadataEntity>().findFirst()
        }?.deviceId ?: ""
    }

    override fun storeAccount(account: OlmAccount) {
        olmAccount = account

        doRealmTransaction(realmConfiguration) {
            it.where<CryptoMetadataEntity>().findFirst()?.putOlmAccount(account)
        }
    }

    override fun getAccount(): OlmAccount? {
        if (olmAccount == null) {
            olmAccount = doRealmQueryAndCopy(realmConfiguration) { it.where<CryptoMetadataEntity>().findFirst() }?.getOlmAccount()
        }

        return olmAccount
    }

    override fun storeUserDevice(userId: String?, deviceInfo: MXDeviceInfo?) {
        if (userId == null || deviceInfo == null) {
            return
        }

        doRealmTransaction(realmConfiguration) {
            val user = UserEntity.getOrCreate(it, userId)

            // Create device info
            val deviceInfoEntity = DeviceInfoEntity.getOrCreate(it, userId, deviceInfo.deviceId).apply {
                deviceId = deviceInfo.deviceId
                identityKey = deviceInfo.identityKey()
                putDeviceInfo(deviceInfo)
            }

            if (!user.devices.contains(deviceInfoEntity)) {
                user.devices.add(deviceInfoEntity)
            }
        }
    }

    override fun getUserDevice(deviceId: String?, userId: String?): MXDeviceInfo? {
        if (deviceId == null || userId == null) {
            return null
        }

        return doRealmQueryAndCopy(realmConfiguration) {
            it.where<DeviceInfoEntity>()
                    .equalTo(DeviceInfoEntityFields.PRIMARY_KEY, DeviceInfoEntity.createPrimaryKey(userId, deviceId))
                    .findFirst()
        }
                ?.getDeviceInfo()
    }

    override fun deviceWithIdentityKey(identityKey: String?): MXDeviceInfo? {
        if (identityKey == null) {
            return null
        }

        return doRealmQueryAndCopy(realmConfiguration) {
            it.where<DeviceInfoEntity>()
                    .equalTo(DeviceInfoEntityFields.IDENTITY_KEY, identityKey)
                    .findFirst()
        }
                ?.getDeviceInfo()
    }

    override fun storeUserDevices(userId: String?, devices: MutableMap<String, MXDeviceInfo>?) {
        if (userId == null) {
            return
        }
        doRealmTransaction(realmConfiguration) { realm ->
            if (devices == null) {
                // Remove the user
                UserEntity.delete(realm, userId)
            } else {
                UserEntity.getOrCreate(realm, userId)
                        .let { u ->
                            // Add the devices
                            // Ensure all other devices are deleted
                            u.devices.deleteAllFromRealm()

                            u.devices.addAll(
                                    devices.map {
                                        DeviceInfoEntity.getOrCreate(realm, userId, it.value.deviceId).apply {
                                            deviceId = it.value.deviceId
                                            identityKey = it.value.identityKey()
                                            putDeviceInfo(it.value)
                                        }
                                    }
                            )
                        }
            }
        }
    }

    override fun getUserDevices(userId: String?): MutableMap<String, MXDeviceInfo>? {
        return doRealmQueryAndCopy(realmConfiguration) {
            it.where<UserEntity>()
                    .equalTo(UserEntityFields.USER_ID, userId)
                    .findFirst()
        }
                ?.devices
                ?.mapNotNull { it.getDeviceInfo() }
                ?.associateBy { it.deviceId }
                ?.toMutableMap()
    }

    override fun storeRoomAlgorithm(roomId: String, algorithm: String) {
        doRealmTransaction(realmConfiguration) {
            CryptoRoomEntity.getOrCreate(it, roomId).algorithm = algorithm
        }
    }

    override fun getRoomAlgorithm(roomId: String): String? {
        return doRealmQueryAndCopy(realmConfiguration) {
            CryptoRoomEntity.getById(it, roomId)
        }
                ?.algorithm
    }

    override fun storeSession(session: MXOlmSession?, deviceKey: String?) {
        if (session == null || deviceKey == null) {
            return
        }

        var sessionIdentifier: String? = null

        try {
            sessionIdentifier = session.olmSession.sessionIdentifier()
        } catch (e: OlmException) {
            Log.e(LOG_TAG, "## storeSession() : sessionIdentifier failed " + e.message, e)
        }

        if (sessionIdentifier != null) {
            val key = OlmSessionEntity.createPrimaryKey(sessionIdentifier, deviceKey)

            // Release memory of previously known session, if it is not the same one
            if (olmSessionsToRelease[key]?.olmSession != session.olmSession) {
                olmSessionsToRelease[key]?.olmSession?.releaseSession()
            }

            olmSessionsToRelease[key] = session

            doRealmTransaction(realmConfiguration) {
                val realmOlmSession = OlmSessionEntity().apply {
                    primaryKey = key
                    sessionId = sessionIdentifier
                    this.deviceKey = deviceKey
                    putOlmSession(session.olmSession)
                    lastReceivedMessageTs = session.lastReceivedMessageTs
                }

                it.insertOrUpdate(realmOlmSession)
            }
        }
    }

    override fun getDeviceSession(sessionId: String?, deviceKey: String?): MXOlmSession? {
        if (sessionId == null || deviceKey == null) {
            return null
        }

        val key = OlmSessionEntity.createPrimaryKey(sessionId, deviceKey)

        // If not in cache (or not found), try to read it from realm
        if (olmSessionsToRelease[key] == null) {
            doRealmQueryAndCopy(realmConfiguration) {
                it.where<OlmSessionEntity>()
                        .equalTo(OlmSessionEntityFields.PRIMARY_KEY, key)
                        .findFirst()
            }
                    ?.let {
                        val olmSession = it.getOlmSession()
                        if (olmSession != null && it.sessionId != null) {
                            olmSessionsToRelease[key] = MXOlmSession(olmSession, it.lastReceivedMessageTs)
                        }
                    }
        }

        return olmSessionsToRelease[key]
    }

    override fun getLastUsedSessionId(deviceKey: String?): String? {
        return doRealmQueryAndCopy(realmConfiguration) {
            it.where<OlmSessionEntity>()
                    .equalTo(OlmSessionEntityFields.DEVICE_KEY, deviceKey)
                    .sort(OlmSessionEntityFields.LAST_RECEIVED_MESSAGE_TS, Sort.DESCENDING)
                    .findFirst()
        }
                ?.sessionId
    }

    override fun getDeviceSessionIds(deviceKey: String?): MutableSet<String> {
        return doRealmQueryAndCopyList(realmConfiguration) {
            it.where<OlmSessionEntity>()
                    .equalTo(OlmSessionEntityFields.DEVICE_KEY, deviceKey)
                    .findAll()
        }
                .mapNotNull {
                    it.sessionId
                }
                .toMutableSet()
    }

    override fun storeInboundGroupSessions(sessions: MutableList<MXOlmInboundGroupSession2>) {
        if (sessions.isEmpty()) {
            return
        }

        doRealmTransaction(realmConfiguration) {
            sessions.forEach { session ->
                var sessionIdentifier: String? = null

                try {
                    sessionIdentifier = session.mSession.sessionIdentifier()
                } catch (e: OlmException) {
                    Log.e(LOG_TAG, "## storeInboundGroupSession() : sessionIdentifier failed " + e.message, e)
                }

                if (sessionIdentifier != null) {
                    val key = OlmInboundGroupSessionEntity.createPrimaryKey(sessionIdentifier, session.mSenderKey)

                    // Release memory of previously known session, if it is not the same one
                    if (inboundGroupSessionToRelease[key] != session) {
                        inboundGroupSessionToRelease[key]?.mSession?.releaseSession()
                    }

                    inboundGroupSessionToRelease[key] = session

                    val realmOlmInboundGroupSession = OlmInboundGroupSessionEntity().apply {
                        primaryKey = key
                        sessionId = sessionIdentifier
                        senderKey = session.mSenderKey
                        putInboundGroupSession(session)
                    }

                    it.insertOrUpdate(realmOlmInboundGroupSession)
                }
            }
        }
    }

    override fun getInboundGroupSession(sessionId: String?, senderKey: String?): MXOlmInboundGroupSession2? {
        val key = OlmInboundGroupSessionEntity.createPrimaryKey(sessionId, senderKey)

        // If not in cache (or not found), try to read it from realm
        if (inboundGroupSessionToRelease[key] == null) {
            doRealmQueryAndCopy(realmConfiguration) {
                it.where<OlmInboundGroupSessionEntity>()
                        .equalTo(OlmInboundGroupSessionEntityFields.PRIMARY_KEY, key)
                        .findFirst()
            }
                    ?.getInboundGroupSession()
                    ?.let {
                        inboundGroupSessionToRelease[key] = it
                    }
        }

        return inboundGroupSessionToRelease[key]
    }

    /**
     * Note: the result will be only use to export all the keys and not to use the MXOlmInboundGroupSession2,
     * so there is no need to use or update `inboundGroupSessionToRelease` for native memory management
     */
    override fun getInboundGroupSessions(): MutableList<MXOlmInboundGroupSession2> {
        return doRealmQueryAndCopyList(realmConfiguration) {
            it.where<OlmInboundGroupSessionEntity>()
                    .findAll()
        }
                .mapNotNull {
                    it.getInboundGroupSession()
                }
                .toMutableList()
    }

    override fun removeInboundGroupSession(sessionId: String?, senderKey: String?) {
        val key = OlmInboundGroupSessionEntity.createPrimaryKey(sessionId, senderKey)

        // Release memory of previously known session
        inboundGroupSessionToRelease[key]?.mSession?.releaseSession()
        inboundGroupSessionToRelease.remove(key)

        doRealmTransaction(realmConfiguration) {
            it.where<OlmInboundGroupSessionEntity>()
                    .equalTo(OlmInboundGroupSessionEntityFields.PRIMARY_KEY, key)
                    .findAll()
                    .deleteAllFromRealm()
        }
    }

    /* ==========================================================================================
     * Keys backup
     * ========================================================================================== */

    override fun getKeyBackupVersion(): String? {
        return doRealmQueryAndCopy(realmConfiguration) {
            it.where<CryptoMetadataEntity>().findFirst()
        }?.backupVersion
    }

    override fun setKeyBackupVersion(keyBackupVersion: String?) {
        doRealmTransaction(realmConfiguration) {
            it.where<CryptoMetadataEntity>().findFirst()?.backupVersion = keyBackupVersion
        }
    }

    override fun getKeysBackupData(): KeysBackupDataEntity? {
        return doRealmQueryAndCopy(realmConfiguration) {
            it.where<KeysBackupDataEntity>().findFirst()
        }
    }

    override fun setKeysBackupData(keysBackupData: KeysBackupDataEntity?) {
        doRealmTransaction(realmConfiguration) {
            if (keysBackupData == null) {
                // Clear the table
                it.where<KeysBackupDataEntity>()
                        .findAll()
                        .deleteAllFromRealm()
            } else {
                // Only one object
                it.copyToRealmOrUpdate(keysBackupData)
            }
        }
    }

    override fun resetBackupMarkers() {
        doRealmTransaction(realmConfiguration) {
            it.where<OlmInboundGroupSessionEntity>()
                    .findAll()
                    .map { inboundGroupSession ->
                        inboundGroupSession.backedUp = false
                    }
        }
    }

    override fun markBackupDoneForInboundGroupSessions(sessions: MutableList<MXOlmInboundGroupSession2>) {
        if (sessions.isEmpty()) {
            return
        }

        doRealmTransaction(realmConfiguration) {
            sessions.forEach { session ->
                try {
                    val key = OlmInboundGroupSessionEntity.createPrimaryKey(session.mSession.sessionIdentifier(), session.mSenderKey)

                    it.where<OlmInboundGroupSessionEntity>()
                            .equalTo(OlmInboundGroupSessionEntityFields.PRIMARY_KEY, key)
                            .findFirst()
                            ?.backedUp = true
                } catch (e: OlmException) {
                    Log.e(LOG_TAG, "OlmException", e)
                }
            }
        }
    }

    override fun inboundGroupSessionsToBackup(limit: Int): List<MXOlmInboundGroupSession2> {
        return doRealmQueryAndCopyList(realmConfiguration) {
            it.where<OlmInboundGroupSessionEntity>()
                    .equalTo(OlmInboundGroupSessionEntityFields.BACKED_UP, false)
                    .limit(limit.toLong())
                    .findAll()
        }.mapNotNull { inboundGroupSession ->
            inboundGroupSession.getInboundGroupSession()
        }
    }

    override fun inboundGroupSessionsCount(onlyBackedUp: Boolean): Int {
        return doWithRealm(realmConfiguration) {
            it.where<OlmInboundGroupSessionEntity>()
                    .apply {
                        if (onlyBackedUp) {
                            equalTo(OlmInboundGroupSessionEntityFields.BACKED_UP, true)
                        }
                    }
                    .count()
                    .toInt()
        }
    }

    override fun setGlobalBlacklistUnverifiedDevices(block: Boolean) {
        doRealmTransaction(realmConfiguration) {
            it.where<CryptoMetadataEntity>().findFirst()?.globalBlacklistUnverifiedDevices = block
        }
    }

    override fun getGlobalBlacklistUnverifiedDevices(): Boolean {
        return doRealmQueryAndCopy(realmConfiguration) {
            it.where<CryptoMetadataEntity>().findFirst()
        }?.globalBlacklistUnverifiedDevices
                ?: false
    }

    override fun setRoomsListBlacklistUnverifiedDevices(roomIds: MutableList<String>) {
        doRealmTransaction(realmConfiguration) {
            // Reset all
            it.where<CryptoRoomEntity>()
                    .findAll()
                    .forEach { room ->
                        room.blacklistUnverifiedDevices = false
                    }

            // Enable those in the list
            it.where<CryptoRoomEntity>()
                    .`in`(CryptoRoomEntityFields.ROOM_ID, roomIds.toTypedArray())
                    .findAll()
                    .forEach { room ->
                        room.blacklistUnverifiedDevices = true
                    }
        }
    }

    override fun getRoomsListBlacklistUnverifiedDevices(): MutableList<String> {
        return doRealmQueryAndCopyList(realmConfiguration) {
            it.where<CryptoRoomEntity>()
                    .equalTo(CryptoRoomEntityFields.BLACKLIST_UNVERIFIED_DEVICES, true)
                    .findAll()
        }
                .mapNotNull {
                    it.roomId
                }
                .toMutableList()
    }

    override fun getDeviceTrackingStatuses(): MutableMap<String, Int> {
        return doRealmQueryAndCopyList(realmConfiguration) {
            it.where<UserEntity>()
                    .findAll()
        }
                .associateBy {
                    it.userId!!
                }
                .mapValues {
                    it.value.deviceTrackingStatus
                }
                .toMutableMap()
    }

    override fun saveDeviceTrackingStatuses(deviceTrackingStatuses: MutableMap<String, Int>?) {
        if (deviceTrackingStatuses == null) {
            return
        }

        doRealmTransaction(realmConfiguration) {
            deviceTrackingStatuses
                    .map { entry ->
                        UserEntity.getOrCreate(it, entry.key)
                                .deviceTrackingStatus = entry.value
                    }
        }
    }

    override fun getDeviceTrackingStatus(userId: String?, defaultValue: Int): Int {
        return doRealmQueryAndCopy(realmConfiguration) {
            it.where<UserEntity>()
                    .equalTo(UserEntityFields.USER_ID, userId)
                    .findFirst()
        }
                ?.deviceTrackingStatus
                ?: defaultValue
    }

    override fun getOutgoingRoomKeyRequest(requestBody: RoomKeyRequestBody?): OutgoingRoomKeyRequest? {
        if (requestBody == null) {
            return null
        }

        return doRealmQueryAndCopy(realmConfiguration) {
            it.where<OutgoingRoomKeyRequestEntity>()
                    .equalTo(OutgoingRoomKeyRequestEntityFields.REQUEST_BODY_ALGORITHM, requestBody.algorithm)
                    .equalTo(OutgoingRoomKeyRequestEntityFields.REQUEST_BODY_ROOM_ID, requestBody.roomId)
                    .equalTo(OutgoingRoomKeyRequestEntityFields.REQUEST_BODY_SENDER_KEY, requestBody.senderKey)
                    .equalTo(OutgoingRoomKeyRequestEntityFields.REQUEST_BODY_SESSION_ID, requestBody.sessionId)
                    .findFirst()
        }
                ?.toOutgoingRoomKeyRequest()
    }

    override fun getOrAddOutgoingRoomKeyRequest(request: OutgoingRoomKeyRequest?): OutgoingRoomKeyRequest? {
        if (request?.mRequestBody == null) {
            return null
        }

        val existingOne = getOutgoingRoomKeyRequest(request.mRequestBody)

        if (existingOne != null) {
            return existingOne
        }

        // Insert the request and return the one passed in parameter
        doRealmTransaction(realmConfiguration) {
            it.createObject(OutgoingRoomKeyRequestEntity::class.java, request.mRequestId).apply {
                putRequestBody(request.mRequestBody)
                putRecipients(request.mRecipients)
                cancellationTxnId = request.mCancellationTxnId
                state = request.mState.ordinal
            }
        }

        return request
    }

    override fun getOutgoingRoomKeyRequestByState(states: MutableSet<OutgoingRoomKeyRequest.RequestState>?): OutgoingRoomKeyRequest? {
        if (states == null) {
            return null
        }

        return doRealmQueryAndCopy(realmConfiguration) {
            it.where<OutgoingRoomKeyRequestEntity>()
                    .`in`(OutgoingRoomKeyRequestEntityFields.STATE, states.map { it.ordinal }.toTypedArray())
                    .findFirst()
        }
                ?.toOutgoingRoomKeyRequest()
    }

    override fun updateOutgoingRoomKeyRequest(request: OutgoingRoomKeyRequest?) {
        if (request == null) {
            return
        }

        doRealmTransaction(realmConfiguration) {
            val obj = OutgoingRoomKeyRequestEntity().apply {
                requestId = request.mRequestId
                cancellationTxnId = request.mCancellationTxnId
                state = request.mState.ordinal
                putRecipients(request.mRecipients)
                putRequestBody(request.mRequestBody)
            }

            it.insertOrUpdate(obj)
        }
    }

    override fun deleteOutgoingRoomKeyRequest(transactionId: String?) {
        if (transactionId == null) {
            return
        }

        doRealmTransaction(realmConfiguration) {
            it.where<OutgoingRoomKeyRequestEntity>()
                    .equalTo(OutgoingRoomKeyRequestEntityFields.REQUEST_ID, transactionId)
                    .findFirst()
                    ?.deleteFromRealm()
        }
    }

    override fun storeIncomingRoomKeyRequest(incomingRoomKeyRequest: IncomingRoomKeyRequest?) {
        if (incomingRoomKeyRequest == null) {
            return
        }

        doRealmTransaction(realmConfiguration) {
            // Delete any previous store request with the same parameters
            it.where<IncomingRoomKeyRequestEntity>()
                    .equalTo(IncomingRoomKeyRequestEntityFields.USER_ID, incomingRoomKeyRequest.mUserId)
                    .equalTo(IncomingRoomKeyRequestEntityFields.DEVICE_ID, incomingRoomKeyRequest.mDeviceId)
                    .equalTo(IncomingRoomKeyRequestEntityFields.REQUEST_ID, incomingRoomKeyRequest.mRequestId)
                    .findAll()
                    .deleteAllFromRealm()

            // Then store it
            it.createObject(IncomingRoomKeyRequestEntity::class.java).apply {
                userId = incomingRoomKeyRequest.mUserId
                deviceId = incomingRoomKeyRequest.mDeviceId
                requestId = incomingRoomKeyRequest.mRequestId
                putRequestBody(incomingRoomKeyRequest.mRequestBody)
            }
        }
    }

    override fun deleteIncomingRoomKeyRequest(incomingRoomKeyRequest: IncomingRoomKeyRequest?) {
        if (incomingRoomKeyRequest == null) {
            return
        }

        doRealmTransaction(realmConfiguration) {
            it.where<IncomingRoomKeyRequestEntity>()
                    .equalTo(IncomingRoomKeyRequestEntityFields.USER_ID, incomingRoomKeyRequest.mUserId)
                    .equalTo(IncomingRoomKeyRequestEntityFields.DEVICE_ID, incomingRoomKeyRequest.mDeviceId)
                    .equalTo(IncomingRoomKeyRequestEntityFields.REQUEST_ID, incomingRoomKeyRequest.mRequestId)
                    .findAll()
                    .deleteAllFromRealm()
        }
    }

    override fun getIncomingRoomKeyRequest(userId: String?, deviceId: String?, requestId: String?): IncomingRoomKeyRequest? {
        return doRealmQueryAndCopy(realmConfiguration) {
            it.where<IncomingRoomKeyRequestEntity>()
                    .equalTo(IncomingRoomKeyRequestEntityFields.USER_ID, userId)
                    .equalTo(IncomingRoomKeyRequestEntityFields.DEVICE_ID, deviceId)
                    .equalTo(IncomingRoomKeyRequestEntityFields.REQUEST_ID, requestId)
                    .findFirst()
        }
                ?.toIncomingRoomKeyRequest()
    }

    override fun getPendingIncomingRoomKeyRequests(): MutableList<IncomingRoomKeyRequest> {
        return doRealmQueryAndCopyList(realmConfiguration) {
            it.where<IncomingRoomKeyRequestEntity>()
                    .findAll()
        }
                .map {
                    it.toIncomingRoomKeyRequest()
                }
                .toMutableList()
    }

    companion object {
        private const val LOG_TAG = "RealmCryptoStore"
    }
}