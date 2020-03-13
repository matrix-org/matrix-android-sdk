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

package org.matrix.androidsdk.crypto.keysbackup

import androidx.annotation.UiThread
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import org.matrix.androidsdk.HomeServerConnectionConfig
import org.matrix.androidsdk.core.JsonUtility
import org.matrix.androidsdk.core.Log
import org.matrix.androidsdk.core.callback.ApiCallback
import org.matrix.androidsdk.core.callback.SimpleApiCallback
import org.matrix.androidsdk.core.callback.SuccessCallback
import org.matrix.androidsdk.core.callback.SuccessErrorCallback
import org.matrix.androidsdk.core.listeners.ProgressListener
import org.matrix.androidsdk.core.listeners.StepProgressListener
import org.matrix.androidsdk.core.model.MatrixError
import org.matrix.androidsdk.crypto.MXCRYPTO_ALGORITHM_MEGOLM
import org.matrix.androidsdk.crypto.MXCRYPTO_ALGORITHM_MEGOLM_BACKUP
import org.matrix.androidsdk.crypto.MegolmSessionData
import org.matrix.androidsdk.crypto.cryptostore.db.model.KeysBackupDataEntity
import org.matrix.androidsdk.crypto.data.ImportRoomKeysResult
import org.matrix.androidsdk.crypto.data.MXDeviceInfo
import org.matrix.androidsdk.crypto.data.MXOlmInboundGroupSession2
import org.matrix.androidsdk.crypto.internal.MXCryptoImpl
import org.matrix.androidsdk.crypto.model.keys.*
import org.matrix.androidsdk.crypto.model.rest.keys.BackupKeysResult
import org.matrix.androidsdk.crypto.model.rest.keys.UpdateKeysBackupVersionBody
import org.matrix.androidsdk.crypto.rest.RoomKeysRestClient
import org.matrix.androidsdk.crypto.util.computeRecoveryKey
import org.matrix.androidsdk.crypto.util.extractCurveKeyFromRecoveryKey
import org.matrix.olm.OlmException
import org.matrix.olm.OlmPkDecryption
import org.matrix.olm.OlmPkEncryption
import org.matrix.olm.OlmPkMessage
import retrofit2.Converter
import java.security.InvalidParameterException
import java.util.*
import kotlin.collections.HashMap

/**
 * A KeysBackup class instance manage incremental backup of e2e keys (megolm keys)
 * to the user's homeserver.
 */
class KeysBackup(private val mCrypto: MXCryptoImpl,
                 homeServerConnectionConfig: HomeServerConnectionConfig) {

    private val mRoomKeysRestClient = RoomKeysRestClient(homeServerConnectionConfig)

    private val mKeysBackupStateManager = KeysBackupStateManager(mCrypto)

    // The backup version
    var mKeysBackupVersion: KeysVersionResult? = null
        private set

    // The backup key being used.
    private var mBackupKey: OlmPkEncryption? = null

    private val mRandom = Random()

    private var backupAllGroupSessionsCallback: ApiCallback<Void?>? = null

    private var mKeysBackupStateListener: KeysBackupStateManager.KeysBackupStateListener? = null

    val isEnabled: Boolean
        get() = mKeysBackupStateManager.isEnabled

    val isStucked: Boolean
        get() = mKeysBackupStateManager.isStucked

    val state: KeysBackupStateManager.KeysBackupState
        get() = mKeysBackupStateManager.state

    val currentBackupVersion: String?
        get() = mKeysBackupVersion?.version

    fun addListener(listener: KeysBackupStateManager.KeysBackupStateListener) {
        mKeysBackupStateManager.addListener(listener)
    }

    fun removeListener(listener: KeysBackupStateManager.KeysBackupStateListener) {
        mKeysBackupStateManager.removeListener(listener)
    }

    /**
     * Set up the data required to create a new backup version.
     * The backup version will not be created and enabled until [createKeysBackupVersion]
     * is called.
     * The returned [MegolmBackupCreationInfo] object has a `recoveryKey` member with
     * the user-facing recovery key string.
     *
     * @param password an optional passphrase string that can be entered by the user
     * when restoring the backup as an alternative to entering the recovery key.
     * @param progressListener a progress listener, as generating private key from password may take a while
     * @param callback Asynchronous callback
     */
    fun prepareKeysBackupVersion(password: String?,
                                 progressListener: ProgressListener?,
                                 callback: SuccessErrorCallback<MegolmBackupCreationInfo>) {
        mCrypto.getDecryptingThreadHandler().post {
            try {
                val olmPkDecryption = OlmPkDecryption()
                val megolmBackupAuthData = MegolmBackupAuthData()

                if (password != null) {
                    // Generate a private key from the password
                    val backgroundProgressListener = if (progressListener == null) {
                        null
                    } else {
                        object : ProgressListener {
                            override fun onProgress(progress: Int, total: Int) {
                                mCrypto.getUIHandler().post {
                                    try {
                                        progressListener.onProgress(progress, total)
                                    } catch (e: Exception) {
                                        Log.e(LOG_TAG, "prepareKeysBackupVersion: onProgress failure", e)
                                    }
                                }
                            }
                        }
                    }

                    val generatePrivateKeyResult = generatePrivateKeyWithPassword(password, backgroundProgressListener)
                    megolmBackupAuthData.publicKey = olmPkDecryption.setPrivateKey(generatePrivateKeyResult.privateKey)
                    megolmBackupAuthData.privateKeySalt = generatePrivateKeyResult.salt
                    megolmBackupAuthData.privateKeyIterations = generatePrivateKeyResult.iterations
                } else {
                    val publicKey = olmPkDecryption.generateKey()

                    megolmBackupAuthData.publicKey = publicKey
                }

                megolmBackupAuthData.signatures = mCrypto.signObject(JsonUtility.getCanonicalizedJsonString(megolmBackupAuthData.signalableJSONDictionary()))


                val megolmBackupCreationInfo = MegolmBackupCreationInfo()
                megolmBackupCreationInfo.algorithm = MXCRYPTO_ALGORITHM_MEGOLM_BACKUP
                megolmBackupCreationInfo.authData = megolmBackupAuthData
                megolmBackupCreationInfo.recoveryKey = computeRecoveryKey(olmPkDecryption.privateKey())

                mCrypto.getUIHandler().post { callback.onSuccess(megolmBackupCreationInfo) }
            } catch (e: OlmException) {
                Log.e(LOG_TAG, "OlmException: ", e)

                mCrypto.getUIHandler().post { callback.onUnexpectedError(e) }
            }
        }
    }

    /**
     * Create a new keys backup version and enable it, using the information return from [prepareKeysBackupVersion].
     *
     * @param keysBackupCreationInfo the info object from [prepareKeysBackupVersion].
     * @param callback               Asynchronous callback
     */
    fun createKeysBackupVersion(keysBackupCreationInfo: MegolmBackupCreationInfo,
                                callback: ApiCallback<KeysVersion>) {
        val createKeysBackupVersionBody = CreateKeysBackupVersionBody()
        createKeysBackupVersionBody.algorithm = keysBackupCreationInfo.algorithm
        createKeysBackupVersionBody.authData = JsonUtility.getBasicGson().toJsonTree(keysBackupCreationInfo.authData)

        mKeysBackupStateManager.state = KeysBackupStateManager.KeysBackupState.Enabling

        mRoomKeysRestClient.createKeysBackupVersion(createKeysBackupVersionBody, object : ApiCallback<KeysVersion> {
            override fun onSuccess(info: KeysVersion) {
                // Reset backup markers.
                mCrypto.cryptoStore.resetBackupMarkers()

                val keyBackupVersion = KeysVersionResult()
                keyBackupVersion.algorithm = createKeysBackupVersionBody.algorithm
                keyBackupVersion.authData = createKeysBackupVersionBody.authData
                keyBackupVersion.version = info.version

                // We can consider that the server does not have keys yet
                keyBackupVersion.count = 0
                keyBackupVersion.hash = null

                enableKeysBackup(keyBackupVersion)

                callback.onSuccess(info)
            }

            override fun onUnexpectedError(e: Exception?) {
                mKeysBackupStateManager.state = KeysBackupStateManager.KeysBackupState.Disabled
                callback.onUnexpectedError(e)
            }

            override fun onNetworkError(e: Exception?) {
                mKeysBackupStateManager.state = KeysBackupStateManager.KeysBackupState.Disabled
                callback.onNetworkError(e)
            }

            override fun onMatrixError(e: MatrixError?) {
                mKeysBackupStateManager.state = KeysBackupStateManager.KeysBackupState.Disabled
                callback.onMatrixError(e)
            }
        })
    }

    /**
     * Delete a keys backup version. It will delete all backed up keys on the server, and the backup itself.
     * If we are backing up to this version. Backup will be stopped.
     *
     * @param version  the backup version to delete.
     * @param callback Asynchronous callback
     */
    fun deleteBackup(version: String, callback: ApiCallback<Void>?) {
        mCrypto.getDecryptingThreadHandler().post {
            // If we're currently backing up to this backup... stop.
            // (We start using it automatically in createKeysBackupVersion so this is symmetrical).
            if (mKeysBackupVersion != null && version == mKeysBackupVersion!!.version) {
                resetKeysBackupData()
                mKeysBackupVersion = null
                mKeysBackupStateManager.state = KeysBackupStateManager.KeysBackupState.Unknown
            }

            mRoomKeysRestClient.deleteBackup(version, object : ApiCallback<Void> {
                private fun eventuallyRestartBackup() {
                    // Do not stay in KeysBackupState.Unknown but check what is available on the homeserver
                    if (state == KeysBackupStateManager.KeysBackupState.Unknown) {
                        checkAndStartKeysBackup()
                    }
                }

                override fun onSuccess(info: Void?) {
                    eventuallyRestartBackup()

                    mCrypto.getUIHandler().post { callback?.onSuccess(null) }
                }

                override fun onUnexpectedError(e: Exception) {
                    eventuallyRestartBackup()

                    mCrypto.getUIHandler().post { callback?.onUnexpectedError(e) }
                }

                override fun onNetworkError(e: Exception) {
                    eventuallyRestartBackup()

                    mCrypto.getUIHandler().post { callback?.onNetworkError(e) }
                }

                override fun onMatrixError(e: MatrixError) {
                    eventuallyRestartBackup()

                    mCrypto.getUIHandler().post { callback?.onMatrixError(e) }
                }
            })
        }
    }

    /**
     * Ask if the backup on the server contains keys that we may do not have locally.
     * This should be called when entering in the state READY_TO_BACKUP
     */
    fun canRestoreKeys(): Boolean {
        // Server contains more keys than locally
        val totalNumberOfKeysLocally = getTotalNumbersOfKeys()

        val keysBackupData = mCrypto.cryptoStore.keysBackupData

        val totalNumberOfKeysServer = keysBackupData?.backupLastServerNumberOfKeys ?: -1
        val hashServer = keysBackupData?.backupLastServerHash

        return when {
            totalNumberOfKeysLocally < totalNumberOfKeysServer -> {
                // Server contains more keys than this device
                true
            }
            totalNumberOfKeysLocally == totalNumberOfKeysServer -> {
                // Same number, compare hash?
                // TODO We have not found any algorithm to determine if a restore is recommended here. Return false for the moment
                false
            }
            else -> false
        }
    }

    /**
     * Facility method to get the total number of locally stored keys
     */
    fun getTotalNumbersOfKeys(): Int {
        return mCrypto.cryptoStore.inboundGroupSessionsCount(false)

    }

    /**
     * Facility method to get the number of backed up keys
     */
    fun getTotalNumbersOfBackedUpKeys(): Int {
        return mCrypto.cryptoStore.inboundGroupSessionsCount(true)
    }

    /**
     * Start to back up keys immediately.
     *
     * @param progressListener the callback to follow the progress
     * @param callback the main callback
     */
    fun backupAllGroupSessions(progressListener: ProgressListener?,
                               callback: ApiCallback<Void?>?) {
        // Get a status right now
        getBackupProgress(object : ProgressListener {
            override fun onProgress(progress: Int, total: Int) {
                // Reset previous listeners if any
                resetBackupAllGroupSessionsListeners()
                Log.d(LOG_TAG, "backupAllGroupSessions: backupProgress: $progress/$total")
                try {
                    progressListener?.onProgress(progress, total)
                } catch (e: Exception) {
                    Log.e(LOG_TAG, "backupAllGroupSessions: onProgress failure", e)
                }

                if (progress == total) {
                    Log.d(LOG_TAG, "backupAllGroupSessions: complete")
                    callback?.onSuccess(null)
                    return
                }

                backupAllGroupSessionsCallback = callback

                // Listen to `state` change to determine when to call onBackupProgress and onComplete
                mKeysBackupStateListener = object : KeysBackupStateManager.KeysBackupStateListener {
                    override fun onStateChange(newState: KeysBackupStateManager.KeysBackupState) {
                        getBackupProgress(object : ProgressListener {
                            override fun onProgress(progress: Int, total: Int) {
                                try {
                                    progressListener?.onProgress(progress, total)
                                } catch (e: Exception) {
                                    Log.e(LOG_TAG, "backupAllGroupSessions: onProgress failure 2", e)
                                }

                                // If backup is finished, notify the main listener
                                if (state === KeysBackupStateManager.KeysBackupState.ReadyToBackUp) {
                                    backupAllGroupSessionsCallback?.onSuccess(null)
                                    resetBackupAllGroupSessionsListeners()
                                }
                            }
                        })
                    }
                }

                mKeysBackupStateManager.addListener(mKeysBackupStateListener!!)

                backupKeys()
            }
        })
    }

    /**
     * Check trust on a key backup version.
     *
     * @param keysBackupVersion the backup version to check.
     * @param callback block called when the operations completes.
     */
    fun getKeysBackupTrust(keysBackupVersion: KeysVersionResult,
                           callback: SuccessCallback<KeysBackupVersionTrust>) {
        mCrypto.getDecryptingThreadHandler().post {
            val keysBackupVersionTrust = getKeysBackupTrustBg(keysBackupVersion)

            mCrypto.getUIHandler().post { callback.onSuccess(keysBackupVersionTrust) }
        }
    }

    /**
     * Check trust on a key backup version.
     * This has to be called on background thread.
     *
     * @param keysBackupVersion the backup version to check.
     * @return a KeysBackupVersionTrust object
     */
    @WorkerThread
    private fun getKeysBackupTrustBg(keysBackupVersion: KeysVersionResult): KeysBackupVersionTrust {
        val myUserId = mCrypto.myDevice.userId

        val keysBackupVersionTrust = KeysBackupVersionTrust()
        val authData = keysBackupVersion.getAuthDataAsMegolmBackupAuthData()

        if (keysBackupVersion.algorithm == null
                || authData == null
                || authData.publicKey.isEmpty()
                || authData.signatures.isNullOrEmpty()) {
            Log.d(LOG_TAG, "getKeysBackupTrust: Key backup is absent or missing required data")
            return keysBackupVersionTrust
        }

        val mySigs: Map<String, *> = authData.signatures!![myUserId] as Map<String, *>
        if (mySigs.isEmpty()) {
            Log.d(LOG_TAG, "getKeysBackupTrust: Ignoring key backup because it lacks any signatures from this user")
            return keysBackupVersionTrust
        }

        for (keyId in mySigs.keys) {
            // XXX: is this how we're supposed to get the device id?
            var deviceId: String? = null
            val components = keyId.split(":")
            if (components.size == 2) {
                deviceId = components[1]
            }

            var device: MXDeviceInfo? = null
            if (deviceId != null) {
                device = mCrypto.cryptoStore.getUserDevice(deviceId, myUserId)

                var isSignatureValid = false

                if (device == null) {
                    Log.d(LOG_TAG, "getKeysBackupTrust: Signature from unknown device $deviceId")
                } else {
                    mCrypto.getOlmDevice()?.let {
                        try {
                            it.verifySignature(device.fingerprint(), authData.signalableJSONDictionary(), mySigs[keyId] as String)
                            isSignatureValid = true
                        } catch (e: OlmException) {
                            Log.d(LOG_TAG, "getKeysBackupTrust: Bad signature from device " + device.deviceId + " " + e.localizedMessage)
                        }
                    }

                    if (isSignatureValid && device.isVerified) {
                        keysBackupVersionTrust.usable = true
                    }
                }

                val signature = KeysBackupVersionTrustSignature()
                signature.device = device
                signature.valid = isSignatureValid
                signature.deviceId = deviceId
                keysBackupVersionTrust.signatures.add(signature)
            }
        }

        return keysBackupVersionTrust
    }

    /**
     * Set trust on a keys backup version.
     * It adds (or removes) the signature of the current device to the authentication part of the keys backup version.
     *
     * @param keysBackupVersion the backup version to check.
     * @param trust the trust to set to the keys backup.
     * @param callback block called when the operations completes.
     */
    fun trustKeysBackupVersion(keysBackupVersion: KeysVersionResult,
                               trust: Boolean,
                               callback: ApiCallback<Void>) {
        Log.d(LOG_TAG, "trustKeyBackupVersion: $trust, version ${keysBackupVersion.version}")

        mCrypto.getDecryptingThreadHandler().post {
            val myUserId = mCrypto.myDevice.userId

            // Get auth data to update it
            val authData = getMegolmBackupAuthData(keysBackupVersion)

            if (authData == null) {
                Log.w(LOG_TAG, "trustKeyBackupVersion:trust: Key backup is missing required data")

                mCrypto.getUIHandler().post {
                    callback.onUnexpectedError(IllegalArgumentException("Missing element"))
                }

                return@post
            }

            // Get current signatures, or create an empty set
            val myUserSignatures = (authData.signatures!![myUserId]?.toMutableMap() ?: HashMap())

            if (trust) {
                // Add current device signature
                val deviceSignatures = mCrypto.signObject(JsonUtility.getCanonicalizedJsonString(authData.signalableJSONDictionary()))

                deviceSignatures[myUserId]?.forEach { entry ->
                    myUserSignatures[entry.key] = entry.value
                }
            } else {
                // Remove current device signature
                myUserSignatures.remove("ed25519:${mCrypto.myDevice.deviceId}")
            }

            // Create an updated version of KeysVersionResult
            val updateKeysBackupVersionBody = UpdateKeysBackupVersionBody(keysBackupVersion.version!!)

            updateKeysBackupVersionBody.algorithm = keysBackupVersion.algorithm

            val newMegolmBackupAuthData = authData.copy()

            val newSignatures = newMegolmBackupAuthData.signatures!!.toMutableMap()
            newSignatures[myUserId] = myUserSignatures

            newMegolmBackupAuthData.signatures = newSignatures

            updateKeysBackupVersionBody.authData = JsonUtility.getBasicGson().toJsonTree(newMegolmBackupAuthData)

            // And send it to the homeserver

            mRoomKeysRestClient.updateKeysBackupVersion(keysBackupVersion.version!!, updateKeysBackupVersionBody, object : ApiCallback<Void> {
                override fun onSuccess(info: Void?) {
                    // Relaunch the state machine on this updated backup version
                    val newKeysBackupVersion = KeysVersionResult()

                    newKeysBackupVersion.version = keysBackupVersion.version
                    newKeysBackupVersion.algorithm = keysBackupVersion.algorithm
                    newKeysBackupVersion.count = keysBackupVersion.count
                    newKeysBackupVersion.hash = keysBackupVersion.hash
                    newKeysBackupVersion.authData = updateKeysBackupVersionBody.authData

                    checkAndStartWithKeysBackupVersion(newKeysBackupVersion)

                    mCrypto.getUIHandler().post {
                        callback.onSuccess(null)
                    }
                }

                override fun onUnexpectedError(e: Exception?) {
                    mCrypto.getUIHandler().post {
                        callback.onUnexpectedError(e)
                    }
                }

                override fun onNetworkError(e: Exception?) {
                    mCrypto.getUIHandler().post {
                        callback.onNetworkError(e)
                    }
                }

                override fun onMatrixError(e: MatrixError?) {
                    mCrypto.getUIHandler().post {
                        callback.onMatrixError(e)
                    }
                }
            })
        }
    }

    /**
     * Set trust on a keys backup version.
     *
     * @param keysBackupVersion the backup version to check.
     * @param recoveryKey the recovery key to challenge with the key backup public key.
     * @param callback block called when the operations completes.
     */
    fun trustKeysBackupVersionWithRecoveryKey(keysBackupVersion: KeysVersionResult,
                                              recoveryKey: String,
                                              callback: ApiCallback<Void>) {
        Log.d(LOG_TAG, "trustKeysBackupVersionWithRecoveryKey: version ${keysBackupVersion.version}")

        mCrypto.getDecryptingThreadHandler().post {
            if (!isValidRecoveryKeyForKeysBackupVersion(recoveryKey, keysBackupVersion)) {
                Log.w(LOG_TAG, "trustKeyBackupVersionWithRecoveryKey: Invalid recovery key.")

                mCrypto.getUIHandler().post {
                    callback.onUnexpectedError(IllegalArgumentException("Invalid recovery key or password"))
                }
                return@post
            }

            trustKeysBackupVersion(keysBackupVersion, true, callback)
        }
    }

    /**
     * Set trust on a keys backup version.
     *
     * @param keysBackupVersion the backup version to check.
     * @param password the pass phrase to challenge with the keyBackupVersion public key.
     * @param callback block called when the operations completes.
     */
    fun trustKeysBackupVersionWithPassphrase(keysBackupVersion: KeysVersionResult,
                                             password: String,
                                             callback: ApiCallback<Void>) {
        Log.d(LOG_TAG, "trustKeysBackupVersionWithPassphrase: version ${keysBackupVersion.version}")

        mCrypto.getDecryptingThreadHandler().post {
            val recoveryKey = recoveryKeyFromPassword(password, keysBackupVersion, null)

            if (recoveryKey == null) {
                Log.w(LOG_TAG, "trustKeysBackupVersionWithPassphrase: Key backup is missing required data")

                mCrypto.getUIHandler().post {
                    callback.onUnexpectedError(IllegalArgumentException("Missing element"))
                }

                return@post
            }

            // Check trust using the recovery key
            trustKeysBackupVersionWithRecoveryKey(keysBackupVersion, recoveryKey, callback)
        }
    }

    /**
     * Get public key from a Recovery key
     *
     * @param recoveryKey the recovery key
     * @return the corresponding public key, from Olm
     */
    @WorkerThread
    private fun pkPublicKeyFromRecoveryKey(recoveryKey: String): String? {
        // Extract the primary key
        val privateKey = extractCurveKeyFromRecoveryKey(recoveryKey)

        if (privateKey == null) {
            Log.w(LOG_TAG, "pkPublicKeyFromRecoveryKey: private key is null")

            return null
        }

        // Built the PK decryption with it
        val pkPublicKey: String

        try {
            val decryption = OlmPkDecryption()
            pkPublicKey = decryption.setPrivateKey(privateKey)
        } catch (e: OlmException) {
            return null
        }

        return pkPublicKey
    }

    private fun resetBackupAllGroupSessionsListeners() {
        backupAllGroupSessionsCallback = null

        mKeysBackupStateListener?.let {
            mKeysBackupStateManager.removeListener(it)
        }

        mKeysBackupStateListener = null
    }

    /**
     * Return the current progress of the backup
     */
    fun getBackupProgress(progressListener: ProgressListener) {
        mCrypto.getDecryptingThreadHandler().post {
            val backedUpKeys = mCrypto.cryptoStore.inboundGroupSessionsCount(true)
            val total = mCrypto.cryptoStore.inboundGroupSessionsCount(false)

            mCrypto.getUIHandler().post { progressListener.onProgress(backedUpKeys, total) }
        }
    }

    /**
     * Restore a backup with a recovery key from a given backup version stored on the homeserver.
     *
     * @param keysVersionResult    the backup version to restore from.
     * @param recoveryKey          the recovery key to decrypt the retrieved backup.
     * @param roomId               the id of the room to get backup data from.
     * @param sessionId            the id of the session to restore.
     * @param stepProgressListener the step progress listener
     * @param callback             Callback. It provides the number of found keys and the number of successfully imported keys.
     */
    fun restoreKeysWithRecoveryKey(keysVersionResult: KeysVersionResult,
                                   recoveryKey: String,
                                   roomId: String?,
                                   sessionId: String?,
                                   stepProgressListener: StepProgressListener?,
                                   callback: ApiCallback<ImportRoomKeysResult>) {
        Log.d(LOG_TAG, "restoreKeysWithRecoveryKey: From backup version: ${keysVersionResult.version}")

        mCrypto.getDecryptingThreadHandler().post(Runnable {
            // Check if the recovery is valid before going any further
            if (!isValidRecoveryKeyForKeysBackupVersion(recoveryKey, keysVersionResult)) {
                Log.e(LOG_TAG, "restoreKeysWithRecoveryKey: Invalid recovery key for this keys version")
                mCrypto.getUIHandler().post { callback.onUnexpectedError(InvalidParameterException("Invalid recovery key")) }
                return@Runnable
            }

            // Get a PK decryption instance
            val decryption = pkDecryptionFromRecoveryKey(recoveryKey)
            if (decryption == null) {
                // This should not happen anymore
                Log.e(LOG_TAG, "restoreKeysWithRecoveryKey: Invalid recovery key. Error")
                mCrypto.getUIHandler().post { callback.onUnexpectedError(InvalidParameterException("Invalid recovery key")) }
                return@Runnable
            }

            if (stepProgressListener != null) {
                mCrypto.getUIHandler().post { stepProgressListener.onStepProgress(StepProgressListener.Step.DownloadingKey) }
            }

            // Get backed up keys from the homeserver
            getKeys(sessionId, roomId, keysVersionResult.version!!, object : ApiCallback<KeysBackupData> {
                override fun onUnexpectedError(e: Exception) {
                    mCrypto.getUIHandler().post { callback.onUnexpectedError(e) }
                }

                override fun onNetworkError(e: Exception) {
                    mCrypto.getUIHandler().post { callback.onNetworkError(e) }
                }

                override fun onMatrixError(e: MatrixError) {
                    mCrypto.getUIHandler().post { callback.onMatrixError(e) }
                }

                override fun onSuccess(keysBackupData: KeysBackupData) {
                    val sessionsData = ArrayList<MegolmSessionData>()
                    // Restore that data
                    var sessionsFromHsCount = 0
                    for (roomIdLoop in keysBackupData.roomIdToRoomKeysBackupData.keys) {
                        for (sessionIdLoop in keysBackupData.roomIdToRoomKeysBackupData[roomIdLoop]!!.sessionIdToKeyBackupData.keys) {
                            sessionsFromHsCount++

                            val keyBackupData = keysBackupData.roomIdToRoomKeysBackupData[roomIdLoop]!!.sessionIdToKeyBackupData[sessionIdLoop]!!

                            val sessionData = decryptKeyBackupData(keyBackupData, sessionIdLoop, roomIdLoop, decryption)

                            sessionData?.let {
                                sessionsData.add(it)
                            }
                        }
                    }
                    Log.d(LOG_TAG, "restoreKeysWithRecoveryKey: Decrypted " + sessionsData.size + " keys out of "
                            + sessionsFromHsCount + " from the backup store on the homeserver")

                    // Do not trigger a backup for them if they come from the backup version we are using
                    val backUp = keysVersionResult.version != mKeysBackupVersion?.version
                    if (backUp) {
                        Log.d(LOG_TAG, "restoreKeysWithRecoveryKey: Those keys will be backed up to backup version: " + mKeysBackupVersion?.version)
                    }

                    // Import them into the crypto store
                    val progressListener = if (stepProgressListener != null) {
                        object : ProgressListener {
                            override fun onProgress(progress: Int, total: Int) {
                                // Note: no need to post to UI thread, importMegolmSessionsData() will do it
                                stepProgressListener.onStepProgress(StepProgressListener.Step.ImportingKey(progress, total))
                            }
                        }
                    } else {
                        null
                    }

                    mCrypto.importMegolmSessionsData(sessionsData, backUp, progressListener, callback)
                }
            })
        })
    }

    /**
     * Restore a backup with a password from a given backup version stored on the homeserver.
     *
     * @param keysBackupVersion the backup version to restore from.
     * @param password the password to decrypt the retrieved backup.
     * @param roomId the id of the room to get backup data from.
     * @param sessionId the id of the session to restore.
     * @param stepProgressListener the step progress listener
     * @param callback Callback. It provides the number of found keys and the number of successfully imported keys.
     */
    fun restoreKeyBackupWithPassword(keysBackupVersion: KeysVersionResult,
                                     password: String,
                                     roomId: String?,
                                     sessionId: String?,
                                     stepProgressListener: StepProgressListener?,
                                     callback: ApiCallback<ImportRoomKeysResult>) {
        Log.d(LOG_TAG, "[MXKeyBackup] restoreKeyBackup with password: From backup version: ${keysBackupVersion.version}")

        mCrypto.getDecryptingThreadHandler().post {
            val progressListener = if (stepProgressListener != null) {
                object : ProgressListener {
                    override fun onProgress(progress: Int, total: Int) {
                        mCrypto.getUIHandler().post {
                            stepProgressListener.onStepProgress(StepProgressListener.Step.ComputingKey(progress, total))
                        }
                    }
                }
            } else {
                null
            }

            val recoveryKey = recoveryKeyFromPassword(password, keysBackupVersion, progressListener)

            if (recoveryKey == null) {
                mCrypto.getUIHandler().post {
                    Log.d(LOG_TAG, "backupKeys: Invalid configuration")
                    callback.onUnexpectedError(IllegalStateException("Invalid configuration"))
                }

                return@post
            }

            restoreKeysWithRecoveryKey(keysBackupVersion, recoveryKey, roomId, sessionId, stepProgressListener, callback)
        }
    }

    /**
     * Same method as [RoomKeysRestClient.getRoomKey] except that it accepts nullable
     * parameters and always returns a KeysBackupData object through the Callback
     */
    private fun getKeys(sessionId: String?,
                        roomId: String?,
                        version: String,
                        callback: ApiCallback<KeysBackupData>) {
        if (roomId != null && sessionId != null) {
            // Get key for the room and for the session
            mRoomKeysRestClient.getRoomKey(roomId, sessionId, version, object : SimpleApiCallback<KeyBackupData>(callback) {
                override fun onSuccess(info: KeyBackupData) {
                    // Convert to KeysBackupData
                    val keysBackupData = KeysBackupData()
                    keysBackupData.roomIdToRoomKeysBackupData = HashMap()
                    val roomKeysBackupData = RoomKeysBackupData()
                    roomKeysBackupData.sessionIdToKeyBackupData = HashMap()
                    roomKeysBackupData.sessionIdToKeyBackupData[sessionId] = info
                    keysBackupData.roomIdToRoomKeysBackupData[roomId] = roomKeysBackupData

                    callback.onSuccess(keysBackupData)
                }
            })
        } else if (roomId != null) {
            // Get all keys for the room
            mRoomKeysRestClient.getRoomKeys(roomId, version, object : SimpleApiCallback<RoomKeysBackupData>(callback) {
                override fun onSuccess(info: RoomKeysBackupData) {
                    // Convert to KeysBackupData
                    val keysBackupData = KeysBackupData()
                    keysBackupData.roomIdToRoomKeysBackupData = HashMap()
                    keysBackupData.roomIdToRoomKeysBackupData[roomId] = info

                    callback.onSuccess(keysBackupData)
                }
            })
        } else {
            // Get all keys
            mRoomKeysRestClient.getKeys(version, callback)
        }
    }

    @VisibleForTesting
    @WorkerThread
    fun pkDecryptionFromRecoveryKey(recoveryKey: String): OlmPkDecryption? {
        // Extract the primary key
        val privateKey = extractCurveKeyFromRecoveryKey(recoveryKey)

        // Built the PK decryption with it
        var decryption: OlmPkDecryption? = null
        if (privateKey != null) {
            try {
                decryption = OlmPkDecryption()
                decryption.setPrivateKey(privateKey)
            } catch (e: OlmException) {
                Log.e(LOG_TAG, "OlmException", e)
            }

        }

        return decryption
    }

    /**
     * Do a backup if there are new keys, with a delay
     */
    fun maybeBackupKeys() {
        when {
            isStucked -> {
                // If not already done, or in error case, check for a valid backup version on the homeserver.
                // If there is one, maybeBackupKeys will be called again.
                checkAndStartKeysBackup()
            }
            state == KeysBackupStateManager.KeysBackupState.ReadyToBackUp -> {
                mKeysBackupStateManager.state = KeysBackupStateManager.KeysBackupState.WillBackUp

                // Wait between 0 and 10 seconds, to avoid backup requests from
                // different clients hitting the server all at the same time when a
                // new key is sent
                val delayInMs = mRandom.nextInt(KEY_BACKUP_WAITING_TIME_TO_SEND_KEY_BACKUP_MILLIS).toLong()

                mCrypto.getUIHandler().postDelayed({ backupKeys() }, delayInMs)
            }
            else -> {
                Log.d(LOG_TAG, "maybeBackupKeys: Skip it because state: $state")
            }
        }
    }

    /**
     * Get information about a backup version defined on the homeserver.
     *
     * It can be different than mKeysBackupVersion.
     * @param version the backup version
     * @param callback
     */
    fun getVersion(version: String,
                   callback: ApiCallback<KeysVersionResult?>) {
        mRoomKeysRestClient.getKeysBackupVersion(version, object : SimpleApiCallback<KeysVersionResult>(callback) {
            override fun onSuccess(info: KeysVersionResult) {
                callback.onSuccess(info)
            }

            override fun onMatrixError(e: MatrixError) {
                // Workaround because the homeserver currently returns M_NOT_FOUND when there is no key backup
                if (e.errcode == MatrixError.NOT_FOUND) {
                    callback.onSuccess(null)
                } else {
                    // Transmit the error
                    callback.onMatrixError(e)
                }
            }
        })
    }

    /**
     * Retrieve the current version of the backup from the home server
     *
     * It can be different than mKeysBackupVersion.
     * @param callback onSuccess(null) will be called if there is no backup on the server
     */
    fun getCurrentVersion(callback: ApiCallback<KeysVersionResult?>) {
        mRoomKeysRestClient.getKeysBackupLastVersion(object : SimpleApiCallback<KeysVersionResult>(callback) {
            override fun onSuccess(info: KeysVersionResult) {
                callback.onSuccess(info)
            }

            override fun onMatrixError(e: MatrixError) {
                // Workaround because the homeserver currently returns M_NOT_FOUND when there is no key backup
                if (e.errcode == MatrixError.NOT_FOUND) {
                    callback.onSuccess(null)
                } else {
                    // Transmit the error
                    callback.onMatrixError(e)
                }
            }
        })
    }

    /**
     * This method fetches the last backup version on the server, then compare to the currently backup version use.
     * If versions are not the same, the current backup is deleted (on server or locally), then the backup may be started again, using the last version.
     *
     * @param callback true if backup is already using the last version, and false if it is not the case
     */
    fun forceUsingLastVersion(callback: ApiCallback<Boolean>) {
        getCurrentVersion(object : SimpleApiCallback<KeysVersionResult?>(callback) {
            override fun onSuccess(info: KeysVersionResult?) {
                val localBackupVersion = mKeysBackupVersion?.version
                val serverBackupVersion = info?.version

                if (serverBackupVersion == null) {
                    if (localBackupVersion == null) {
                        // No backup on the server, and backup is not active
                        callback.onSuccess(true)
                    } else {
                        // No backup on the server, and we are currently backing up, so stop backing up
                        callback.onSuccess(false)
                        resetKeysBackupData()
                        mKeysBackupVersion = null
                        mKeysBackupStateManager.state = KeysBackupStateManager.KeysBackupState.Disabled
                    }
                } else {
                    if (localBackupVersion == null) {
                        // backup on the server, and backup is not active
                        callback.onSuccess(false)
                        // Do a check
                        checkAndStartWithKeysBackupVersion(info)
                    } else {
                        // Backup on the server, and we are currently backing up, compare version
                        if (localBackupVersion == serverBackupVersion) {
                            // We are already using the last version of the backup
                            callback.onSuccess(true)
                        } else {
                            // We are not using the last version, so delete the current version we are using on the server
                            callback.onSuccess(false)

                            // This will automatically check for the last version then
                            deleteBackup(localBackupVersion, null)
                        }
                    }
                }
            }
        })
    }

    /**
     * Check the server for an active key backup.
     *
     * If one is present and has a valid signature from one of the user's verified
     * devices, start backing up to it.
     */
    fun checkAndStartKeysBackup() {
        if (!isStucked) {
            // Try to start or restart the backup only if it is in unknown or bad state
            Log.w(LOG_TAG, "checkAndStartKeysBackup: invalid state: $state")

            return
        }

        mKeysBackupVersion = null
        mKeysBackupStateManager.state = KeysBackupStateManager.KeysBackupState.CheckingBackUpOnHomeserver

        getCurrentVersion(object : ApiCallback<KeysVersionResult?> {
            override fun onSuccess(keyBackupVersion: KeysVersionResult?) {
                checkAndStartWithKeysBackupVersion(keyBackupVersion)
            }

            override fun onUnexpectedError(e: Exception?) {
                Log.e(LOG_TAG, "checkAndStartKeysBackup: Failed to get current version", e)
                mKeysBackupStateManager.state = KeysBackupStateManager.KeysBackupState.Unknown
            }

            override fun onNetworkError(e: Exception?) {
                Log.e(LOG_TAG, "checkAndStartKeysBackup: Failed to get current version", e)
                mKeysBackupStateManager.state = KeysBackupStateManager.KeysBackupState.Unknown
            }

            override fun onMatrixError(e: MatrixError?) {
                Log.e(LOG_TAG, "checkAndStartKeysBackup: Failed to get current version " + e?.localizedMessage)
                mKeysBackupStateManager.state = KeysBackupStateManager.KeysBackupState.Unknown
            }
        })
    }

    private fun checkAndStartWithKeysBackupVersion(keyBackupVersion: KeysVersionResult?) {
        Log.d(LOG_TAG, "checkAndStartWithKeyBackupVersion: ${keyBackupVersion?.version}")

        mKeysBackupVersion = keyBackupVersion

        if (keyBackupVersion == null) {
            Log.d(LOG_TAG, "checkAndStartWithKeysBackupVersion: Found no key backup version on the homeserver")
            resetKeysBackupData()
            mKeysBackupStateManager.state = KeysBackupStateManager.KeysBackupState.Disabled
        } else {
            getKeysBackupTrust(keyBackupVersion, SuccessCallback { trustInfo ->
                val versionInStore = mCrypto.cryptoStore.keyBackupVersion

                if (trustInfo.usable) {
                    Log.d(LOG_TAG, "checkAndStartWithKeysBackupVersion: Found usable key backup. version: " + keyBackupVersion.version)
                    // Check the version we used at the previous app run
                    if (versionInStore != null && versionInStore != keyBackupVersion.version) {
                        Log.d(LOG_TAG, " -> clean the previously used version $versionInStore")
                        resetKeysBackupData()
                    }

                    Log.d(LOG_TAG, "   -> enabling key backups")
                    enableKeysBackup(keyBackupVersion)
                } else {
                    Log.d(LOG_TAG, "checkAndStartWithKeysBackupVersion: No usable key backup. version: " + keyBackupVersion.version)
                    if (versionInStore != null) {
                        Log.d(LOG_TAG, "   -> disabling key backup")
                        resetKeysBackupData()
                    }

                    mKeysBackupStateManager.state = KeysBackupStateManager.KeysBackupState.NotTrusted
                }
            })
        }
    }

    /* ==========================================================================================
     * Private
     * ========================================================================================== */

    /**
     * Extract MegolmBackupAuthData data from a backup version.
     *
     * @param keysBackupData the key backup data
     *
     * @return the authentication if found and valid, null in other case
     */
    private fun getMegolmBackupAuthData(keysBackupData: KeysVersionResult): MegolmBackupAuthData? {
        if (keysBackupData.version.isNullOrBlank()
                || keysBackupData.algorithm != MXCRYPTO_ALGORITHM_MEGOLM_BACKUP
                || keysBackupData.authData == null) {
            return null
        }

        val authData = keysBackupData.getAuthDataAsMegolmBackupAuthData()

        if (authData.signatures == null
                || authData.publicKey.isBlank()) {
            return null
        }

        return authData
    }

    /**
     * Compute the recovery key from a password and key backup version.
     *
     * @param password the password.
     * @param keysBackupData the backup and its auth data.
     *
     * @return the recovery key if successful, null in other cases
     */
    @WorkerThread
    private fun recoveryKeyFromPassword(password: String, keysBackupData: KeysVersionResult, progressListener: ProgressListener?): String? {
        val authData = getMegolmBackupAuthData(keysBackupData)

        if (authData == null) {
            Log.w(LOG_TAG, "recoveryKeyFromPassword: invalid parameter")
            return null
        }

        if (authData.privateKeySalt.isNullOrBlank()
                || authData.privateKeyIterations == null) {
            Log.w(LOG_TAG, "recoveryKeyFromPassword: Salt and/or iterations not found in key backup auth data")

            return null
        }

        // Extract the recovery key from the passphrase
        val data = retrievePrivateKeyWithPassword(password, authData.privateKeySalt!!, authData.privateKeyIterations!!, progressListener)

        return computeRecoveryKey(data)
    }

    /**
     * Check if a recovery key matches key backup authentication data.
     *
     * @param recoveryKey the recovery key to challenge.
     * @param keysBackupData the backup and its auth data.
     *
     * @return true if successful.
     */
    @WorkerThread
    private fun isValidRecoveryKeyForKeysBackupVersion(recoveryKey: String, keysBackupData: KeysVersionResult): Boolean {
        // Build PK decryption instance with the recovery key
        val publicKey = pkPublicKeyFromRecoveryKey(recoveryKey)

        if (publicKey == null) {
            Log.w(LOG_TAG, "isValidRecoveryKeyForKeysBackupVersion: public key is null")

            return false
        }

        val authData = getMegolmBackupAuthData(keysBackupData)

        if (authData == null) {
            Log.w(LOG_TAG, "isValidRecoveryKeyForKeysBackupVersion: Key backup is missing required data")

            return false
        }

        // Compare both
        if (publicKey != authData.publicKey) {
            Log.w(LOG_TAG, "isValidRecoveryKeyForKeysBackupVersion: Public keys mismatch")

            return false
        }

        // Public keys match!
        return true
    }

    /**
     * Enable backing up of keys.
     * This method will update the state and will start sending keys in nominal case
     *
     * @param keysVersionResult backup information object as returned by [getCurrentVersion].
     */
    private fun enableKeysBackup(keysVersionResult: KeysVersionResult) {
        if (keysVersionResult.authData != null) {
            val retrievedMegolmBackupAuthData = keysVersionResult.getAuthDataAsMegolmBackupAuthData()

            if (retrievedMegolmBackupAuthData != null) {
                mKeysBackupVersion = keysVersionResult
                mCrypto.cryptoStore.keyBackupVersion = keysVersionResult.version

                onServerDataRetrieved(keysVersionResult.count, keysVersionResult.hash)

                try {
                    mBackupKey = OlmPkEncryption().apply {
                        setRecipientKey(retrievedMegolmBackupAuthData.publicKey)
                    }
                } catch (e: OlmException) {
                    Log.e(LOG_TAG, "OlmException", e)
                    mKeysBackupStateManager.state = KeysBackupStateManager.KeysBackupState.Disabled
                    return
                }

                mKeysBackupStateManager.state = KeysBackupStateManager.KeysBackupState.ReadyToBackUp

                maybeBackupKeys()
            } else {
                Log.e(LOG_TAG, "Invalid authentication data")
                mKeysBackupStateManager.state = KeysBackupStateManager.KeysBackupState.Disabled
            }
        } else {
            Log.e(LOG_TAG, "Invalid authentication data")
            mKeysBackupStateManager.state = KeysBackupStateManager.KeysBackupState.Disabled
        }
    }

    /**
     * Update the DB with data fetch from the server
     */
    private fun onServerDataRetrieved(count: Int?, hash: String?) {
        mCrypto.cryptoStore.keysBackupData = KeysBackupDataEntity()
                .apply {
                    backupLastServerNumberOfKeys = count
                    backupLastServerHash = hash
                }
    }

    /**
     * Reset all local key backup data.
     *
     * Note: This method does not update the state
     */
    private fun resetKeysBackupData() {
        resetBackupAllGroupSessionsListeners()

        mCrypto.cryptoStore.keyBackupVersion = null
        mCrypto.cryptoStore.keysBackupData = null
        mBackupKey = null

        // Reset backup markers
        mCrypto.cryptoStore.resetBackupMarkers()
    }

    /**
     * Send a chunk of keys to backup
     */
    @UiThread
    private fun backupKeys() {
        Log.d(LOG_TAG, "backupKeys")

        // Sanity check, as this method can be called after a delay, the state may have change during the delay
        if (!isEnabled || mBackupKey == null || mKeysBackupVersion == null) {
            Log.d(LOG_TAG, "backupKeys: Invalid configuration")
            backupAllGroupSessionsCallback?.onUnexpectedError(IllegalStateException("Invalid configuration"))
            resetBackupAllGroupSessionsListeners()

            return
        }

        if (state === KeysBackupStateManager.KeysBackupState.BackingUp) {
            // Do nothing if we are already backing up
            Log.d(LOG_TAG, "backupKeys: Invalid state: $state")
            return
        }

        // Get a chunk of keys to backup
        val sessions = mCrypto.cryptoStore.inboundGroupSessionsToBackup(KEY_BACKUP_SEND_KEYS_MAX_COUNT)

        Log.d(LOG_TAG, "backupKeys: 1 - " + sessions.size + " sessions to back up")

        if (sessions.isEmpty()) {
            // Backup is up to date
            mKeysBackupStateManager.state = KeysBackupStateManager.KeysBackupState.ReadyToBackUp

            backupAllGroupSessionsCallback?.onSuccess(null)
            resetBackupAllGroupSessionsListeners()
            return
        }

        mKeysBackupStateManager.state = KeysBackupStateManager.KeysBackupState.BackingUp

        mCrypto.encryptingThreadHandler.post {
            Log.d(LOG_TAG, "backupKeys: 2 - Encrypting keys")

            // Gather data to send to the homeserver
            // roomId -> sessionId -> MXKeyBackupData
            val keysBackupData = KeysBackupData()
            keysBackupData.roomIdToRoomKeysBackupData = HashMap()

            for (session in sessions) {
                val keyBackupData = encryptGroupSession(session)
                if (keysBackupData.roomIdToRoomKeysBackupData[session.mRoomId] == null) {
                    val roomKeysBackupData = RoomKeysBackupData()
                    roomKeysBackupData.sessionIdToKeyBackupData = HashMap()
                    keysBackupData.roomIdToRoomKeysBackupData[session.mRoomId] = roomKeysBackupData
                }

                try {
                    keysBackupData.roomIdToRoomKeysBackupData[session.mRoomId]!!.sessionIdToKeyBackupData[session.mSession.sessionIdentifier()] = keyBackupData
                } catch (e: OlmException) {
                    Log.e(LOG_TAG, "OlmException", e)
                }
            }

            Log.d(LOG_TAG, "backupKeys: 4 - Sending request")

            // Make the request
            mRoomKeysRestClient.backupKeys(mKeysBackupVersion!!.version!!, keysBackupData, object : ApiCallback<BackupKeysResult> {
                override fun onNetworkError(e: Exception) {
                    mCrypto.getUIHandler().post {
                        backupAllGroupSessionsCallback?.onNetworkError(e)
                        resetBackupAllGroupSessionsListeners()

                        onError()
                    }
                }

                private fun onError() {
                    Log.e(LOG_TAG, "backupKeys: backupKeys failed.")

                    // Retry a bit later
                    mKeysBackupStateManager.state = KeysBackupStateManager.KeysBackupState.ReadyToBackUp
                    maybeBackupKeys()
                }

                override fun onMatrixError(e: MatrixError) {
                    mCrypto.getUIHandler().post {
                        Log.e(LOG_TAG, "backupKeys: backupKeys failed. Error: " + e.localizedMessage)

                        when (e.errcode) {
                            MatrixError.NOT_FOUND,
                            MatrixError.WRONG_ROOM_KEYS_VERSION -> {
                                // Backup has been deleted on the server, or we are not using the last backup version
                                mKeysBackupStateManager.state = KeysBackupStateManager.KeysBackupState.WrongBackUpVersion
                                backupAllGroupSessionsCallback?.onMatrixError(e)
                                resetBackupAllGroupSessionsListeners()
                                resetKeysBackupData()
                                mKeysBackupVersion = null

                                // Do not stay in KeysBackupState.WrongBackUpVersion but check what is available on the homeserver
                                checkAndStartKeysBackup()
                            }
                            else -> // Come back to the ready state so that we will retry on the next received key
                                mKeysBackupStateManager.state = KeysBackupStateManager.KeysBackupState.ReadyToBackUp
                        }
                    }
                }

                override fun onUnexpectedError(e: Exception) {
                    mCrypto.getUIHandler().post {
                        backupAllGroupSessionsCallback?.onUnexpectedError(e)
                        resetBackupAllGroupSessionsListeners()

                        onError()
                    }
                }

                override fun onSuccess(info: BackupKeysResult) {
                    mCrypto.getUIHandler().post {
                        Log.d(LOG_TAG, "backupKeys: 5a - Request complete")

                        // Mark keys as backed up
                        mCrypto.cryptoStore.markBackupDoneForInboundGroupSessions(sessions)

                        if (sessions.size < KEY_BACKUP_SEND_KEYS_MAX_COUNT) {
                            Log.d(LOG_TAG, "backupKeys: All keys have been backed up")
                            onServerDataRetrieved(info.count, info.hash)

                            // Note: Changing state will trigger the call to backupAllGroupSessionsCallback.onSuccess()
                            mKeysBackupStateManager.state = KeysBackupStateManager.KeysBackupState.ReadyToBackUp
                        } else {
                            Log.d(LOG_TAG, "backupKeys: Continue to back up keys")
                            mKeysBackupStateManager.state = KeysBackupStateManager.KeysBackupState.WillBackUp

                            backupKeys()
                        }
                    }
                }
            })
        }
    }

    @VisibleForTesting
    @WorkerThread
    fun encryptGroupSession(session: MXOlmInboundGroupSession2): KeyBackupData {
        // Gather information for each key
        val device = mCrypto.deviceWithIdentityKey(session.mSenderKey, MXCRYPTO_ALGORITHM_MEGOLM)

        // Build the m.megolm_backup.v1.curve25519-aes-sha2 data as defined at
        // https://github.com/uhoreg/matrix-doc/blob/e2e_backup/proposals/1219-storing-megolm-keys-serverside.md#mmegolm_backupv1curve25519-aes-sha2-key-format
        val sessionData = session.exportKeys()
        val sessionBackupData = mapOf(
                "algorithm" to sessionData!!.algorithm,
                "sender_key" to sessionData.senderKey,
                "sender_claimed_keys" to sessionData.senderClaimedKeys,
                "forwarding_curve25519_key_chain" to (sessionData.forwardingCurve25519KeyChain ?: ArrayList<Any>()),
                "session_key" to sessionData.sessionKey)

        var encryptedSessionBackupData: OlmPkMessage? = null
        try {
            encryptedSessionBackupData = mBackupKey?.encrypt(JsonUtility.getGson(false).toJson(sessionBackupData))
        } catch (e: OlmException) {
            Log.e(LOG_TAG, "OlmException", e)
        }

        // Build backup data for that key
        val keyBackupData = KeyBackupData()
        try {
            keyBackupData.firstMessageIndex = session.mSession.firstKnownIndex
        } catch (e: OlmException) {
            Log.e(LOG_TAG, "OlmException", e)
        }

        keyBackupData.forwardedCount = session.mForwardingCurve25519KeyChain.size
        keyBackupData.isVerified = device?.isVerified == true

        val data = mapOf(
                "ciphertext" to encryptedSessionBackupData!!.mCipherText,
                "mac" to encryptedSessionBackupData.mMac,
                "ephemeral" to encryptedSessionBackupData.mEphemeralKey)

        keyBackupData.sessionData = JsonUtility.getGson(false).toJsonTree(data)

        return keyBackupData
    }

    @VisibleForTesting
    @WorkerThread
    fun decryptKeyBackupData(keyBackupData: KeyBackupData, sessionId: String, roomId: String, decryption: OlmPkDecryption): MegolmSessionData? {
        var sessionBackupData: MegolmSessionData? = null

        val jsonObject = keyBackupData.sessionData?.asJsonObject

        val ciphertext = jsonObject?.get("ciphertext")?.asString
        val mac = jsonObject?.get("mac")?.asString
        val ephemeralKey = jsonObject?.get("ephemeral")?.asString

        if (ciphertext != null && mac != null && ephemeralKey != null) {
            val encrypted = OlmPkMessage()
            encrypted.mCipherText = ciphertext
            encrypted.mMac = mac
            encrypted.mEphemeralKey = ephemeralKey

            try {
                val decrypted = decryption.decrypt(encrypted)
                sessionBackupData = JsonUtility.toClass(decrypted, MegolmSessionData::class.java)
            } catch (e: OlmException) {
                Log.e(LOG_TAG, "OlmException", e)
            }

            if (sessionBackupData != null) {
                sessionBackupData.sessionId = sessionId
                sessionBackupData.roomId = roomId
            }
        }

        return sessionBackupData
    }

    fun getRoomKeysRestClient(): RoomKeysRestClient {
        return mRoomKeysRestClient
    }

    companion object {
        private val LOG_TAG = KeysBackup::class.java.simpleName

        // Maximum delay in ms in {@link maybeBackupKeys}
        private const val KEY_BACKUP_WAITING_TIME_TO_SEND_KEY_BACKUP_MILLIS = 10000

        // Maximum number of keys to send at a time to the homeserver.
        private const val KEY_BACKUP_SEND_KEYS_MAX_COUNT = 100
    }

    /* ==========================================================================================
     * DEBUG INFO
     * ========================================================================================== */

    override fun toString() = "KeysBackup for $mCrypto"
}
