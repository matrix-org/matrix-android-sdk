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

@file:Suppress("DEPRECATION")

package org.matrix.androidsdk.crypto.cryptostore.db

import android.content.Context
import io.realm.Realm
import io.realm.RealmList
import io.realm.kotlin.where
import org.matrix.androidsdk.core.Log
import org.matrix.androidsdk.crypto.cryptostore.MXFileCryptoStore
import org.matrix.androidsdk.crypto.cryptostore.db.model.*
import org.matrix.androidsdk.crypto.cryptostore.db.query.getOrCreate
import org.matrix.androidsdk.rest.model.login.Credentials

/**
 * This class migrate the legacy FileCryptoStore to the Realm DB
 */
internal class CryptoFileStoreImporter(private val enableFileEncryption: Boolean,
                                       private val context: Context,
                                       private val credentials: Credentials) : Realm.Transaction {

    override fun execute(realm: Realm) {
        // Create a FileCryptoStore
        val fileCryptoStore = MXFileCryptoStore(enableFileEncryption)
        fileCryptoStore.initWithCredentials(context, credentials)

        if (fileCryptoStore.hasData()) {
            Log.d(LOG_TAG, "Importing data...")

            val start = System.currentTimeMillis()

            fileCryptoStore.open()

            // Metadata
            importMetaData(fileCryptoStore, realm)
            // Rooms
            importRooms(fileCryptoStore, realm)
            // Users
            importUsers(fileCryptoStore, realm)
            // Outgoing room key requests
            importOutgoingRoomKeyRequests(fileCryptoStore, realm)
            // Incoming room key requests
            importIncomingRoomKeyRequests(fileCryptoStore, realm)
            // Olm sessions
            importOlmSessions(fileCryptoStore, realm)
            // Inbound Sessions
            importInboundGroupSessions(fileCryptoStore, realm)

            fileCryptoStore.close()

            Log.d(LOG_TAG, "Importing data done in " + (System.currentTimeMillis() - start) + "ms")
        } else {
            // Already migrated, or new account
            Log.d(LOG_TAG, "No data to import")
        }

        // At last
        fileCryptoStore.deleteStore()
    }

    private fun importMetaData(fileCryptoStore: MXFileCryptoStore, realm: Realm) {
        Log.d(LOG_TAG, "Importing metadata")

        realm.createObject(CryptoMetadataEntity::class.java, credentials.getUserId()).apply {
            deviceId = fileCryptoStore.deviceId
            backupVersion = null
            // TODO deviceSyncToken = fileCryptoStore.
            putOlmAccount(fileCryptoStore.account)
            globalBlacklistUnverifiedDevices = fileCryptoStore.globalBlacklistUnverifiedDevices
        }
    }

    private fun importRooms(fileCryptoStore: MXFileCryptoStore, realm: Realm) {
        // Create CryptoRoomEntity in DB
        fileCryptoStore.roomsAlgorithms?.entries
                ?.also {
                    Log.d(LOG_TAG, "Importing ${it.size} rooms")
                }
                ?.forEach { entry ->
                    CryptoRoomEntity.getOrCreate(realm, entry.key).apply {
                        algorithm = entry.value
                    }
                }

        // Set flag blacklistUnverifiedDevices
        fileCryptoStore.roomsListBlacklistUnverifiedDevices
                .also {
                    Log.d(LOG_TAG, "Setting ${it.size} room blacklistUnverifiedDevices flags to true")
                }
                .forEach {
                    realm.where<CryptoRoomEntity>()
                            .equalTo(CryptoRoomEntityFields.ROOM_ID, it)
                            .findFirst()
                            ?.blacklistUnverifiedDevices = true
                }
    }

    private fun importUsers(fileCryptoStore: MXFileCryptoStore, realm: Realm) {
        fileCryptoStore.allUsersDevices?.map?.entries
                ?.also {
                    Log.d(LOG_TAG, "Importing ${it.size} users")
                }
                ?.forEach { userIdToDevices ->
                    // Keys are user id
                    realm.createObject(UserEntity::class.java, userIdToDevices.key).apply {
                        devices = RealmList()

                        userIdToDevices.value.entries.forEach { deviceIdToDevice ->
                            devices.add(
                                    DeviceInfoEntity.getOrCreate(realm, userIdToDevices.key, deviceIdToDevice.value.deviceId).apply {
                                        deviceId = deviceIdToDevice.key
                                        identityKey = deviceIdToDevice.value.identityKey()
                                        putDeviceInfo(deviceIdToDevice.value)
                                    }
                            )
                        }
                    }
                }

        fileCryptoStore.deviceTrackingStatuses
                ?.also {
                    Log.d(LOG_TAG, "Importing ${it.size} device tracking status")
                }
                ?.forEach { entry ->
                    realm.where<UserEntity>()
                            .equalTo(UserEntityFields.USER_ID, entry.key)
                            .findFirst()
                            ?.deviceTrackingStatus = entry.value
                }
    }

    private fun importOutgoingRoomKeyRequests(fileCryptoStore: MXFileCryptoStore, realm: Realm) {
        fileCryptoStore.outgoingRoomKeyRequests
                ?.also {
                    Log.d(LOG_TAG, "Importing ${it.size} OutgoingRoomKeyRequests")
                }
                ?.forEach { entry ->
                    realm.createObject(OutgoingRoomKeyRequestEntity::class.java, entry.value.mRequestId).apply {
                        putRecipients(entry.value.mRecipients)
                        putRequestBody(entry.value.mRequestBody)
                        state = entry.value.mState.ordinal
                        cancellationTxnId = entry.value.mCancellationTxnId
                    }
                }
    }

    private fun importIncomingRoomKeyRequests(fileCryptoStore: MXFileCryptoStore, realm: Realm) {
        fileCryptoStore.pendingIncomingRoomKeyRequests
                ?.also {
                    Log.d(LOG_TAG, "Importing ${it.size} IncomingRoomKeyRequests")
                }
                ?.forEach { entry ->
                    realm.createObject(IncomingRoomKeyRequestEntity::class.java).apply {
                        requestId = entry.mRequestId
                        userId = entry.mUserId
                        deviceId = entry.mDeviceId
                        putRequestBody(entry.mRequestBody)
                    }
                }
    }

    private fun importOlmSessions(fileCryptoStore: MXFileCryptoStore, realm: Realm) {
        fileCryptoStore.olmSessions
                ?.also {
                    Log.d(LOG_TAG, "Importing ${it.size} olmSessions")
                }
                ?.forEach { deviceKeyToMap ->
                    deviceKeyToMap.value.forEach { olmSessionIdToOlmSession ->
                        realm.createObject(OlmSessionEntity::class.java, OlmSessionEntity.createPrimaryKey(olmSessionIdToOlmSession.key, deviceKeyToMap.key))
                                .apply {
                                    deviceKey = deviceKeyToMap.key
                                    sessionId = olmSessionIdToOlmSession.key
                                    putOlmSession(olmSessionIdToOlmSession.value)
                                    lastReceivedMessageTs = 0
                                }
                    }
                }
    }

    private fun importInboundGroupSessions(fileCryptoStore: MXFileCryptoStore, realm: Realm) {
        fileCryptoStore.inboundGroupSessions
                .also {
                    Log.d(LOG_TAG, "Importing ${it.size} InboundGroupSessions")
                }
                .forEach {
                    realm.createObject(OlmInboundGroupSessionEntity::class.java,
                            OlmInboundGroupSessionEntity.createPrimaryKey(it.mSession.sessionIdentifier(), it.mSenderKey)).apply {
                        sessionId = it.mSession.sessionIdentifier()
                        senderKey = it.mSenderKey
                        putInboundGroupSession(it)
                    }
                }
    }

    // Add this two methods to allow multiple sessions
    override fun hashCode() = 99

    override fun equals(other: Any?) = other is CryptoFileStoreImporter

    companion object {
        private const val LOG_TAG = "CryptoFileStoreImporter"
    }
}
