/*
 * Copyright 2019 New Vector Ltd
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

package org.matrix.androidsdk.crypto.verification

import android.os.Handler
import android.os.Looper
import org.matrix.androidsdk.core.JsonUtility
import org.matrix.androidsdk.core.Log
import org.matrix.androidsdk.core.callback.ApiCallback
import org.matrix.androidsdk.core.model.MatrixError
import org.matrix.androidsdk.crypto.data.MXDeviceInfo
import org.matrix.androidsdk.crypto.data.MXUsersDevicesMap
import org.matrix.androidsdk.crypto.interfaces.CryptoEvent
import org.matrix.androidsdk.crypto.interfaces.CryptoSession
import org.matrix.androidsdk.crypto.rest.model.crypto.*
import java.util.*
import kotlin.collections.HashMap

/**
 * Manages all current verifications transactions with short codes.
 * Short codes interactive verification is a more user friendly way of verifying devices
 * that is still maintaining a good level of security (alternative to the 43-character strings compare method).
 */
class VerificationManager(val session: CryptoSession) : VerificationTransaction.Listener {

    interface VerificationManagerListener {
        fun transactionCreated(tx: VerificationTransaction)
        fun transactionUpdated(tx: VerificationTransaction)
        fun markedAsManuallyVerified(userId: String, deviceId: String)
    }

    private val uiHandler = Handler(Looper.getMainLooper())

    // map [sender : [transaction]]
    private val txMap = HashMap<String, HashMap<String, VerificationTransaction>>()

    // Event received from the sync
    fun onToDeviceEvent(event: CryptoEvent) {
        session.requireCrypto().getDecryptingThreadHandler().post {
            when (event.getType()) {
                CryptoEvent.EVENT_TYPE_KEY_VERIFICATION_START -> {
                    onStartRequestReceived(event)
                }
                CryptoEvent.EVENT_TYPE_KEY_VERIFICATION_CANCEL -> {
                    onCancelReceived(event)
                }
                CryptoEvent.EVENT_TYPE_KEY_VERIFICATION_ACCEPT -> {
                    onAcceptReceived(event)
                }
                CryptoEvent.EVENT_TYPE_KEY_VERIFICATION_KEY -> {
                    onKeyReceived(event)
                }
                CryptoEvent.EVENT_TYPE_KEY_VERIFICATION_MAC -> {
                    onMacReceived(event)
                }
                else -> {
                    //ignore
                }
            }
        }
    }

    private var listeners = ArrayList<VerificationManagerListener>()

    fun addListener(listener: VerificationManagerListener) {
        uiHandler.post {
            if (!listeners.contains(listener)) {
                listeners.add(listener)
            }
        }
    }

    fun removeListener(listener: VerificationManagerListener) {
        uiHandler.post {
            listeners.remove(listener)
        }
    }

    private fun dispatchTxAdded(tx: VerificationTransaction) {
        uiHandler.post {
            listeners.forEach {
                try {
                    it.transactionCreated(tx)
                } catch (e: Throwable) {
                    Log.e(LOG_TAG, "## Error while notifying listeners", e)
                }

            }
        }
    }

    private fun dispatchTxUpdated(tx: VerificationTransaction) {
        uiHandler.post {
            listeners.forEach {
                try {
                    it.transactionUpdated(tx)
                } catch (e: Throwable) {
                    Log.e(LOG_TAG, "## Error while notifying listeners", e)
                }
            }
        }
    }

    fun markedLocallyAsManuallyVerified(userId: String, deviceID: String) {
        session.requireCrypto().setDeviceVerification(MXDeviceInfo.DEVICE_VERIFICATION_VERIFIED,
                deviceID,
                userId,
                object : ApiCallback<Void> {
                    override fun onSuccess(info: Void?) {
                        uiHandler.post {
                            listeners.forEach {
                                try {
                                    it.markedAsManuallyVerified(userId, deviceID)
                                } catch (e: Throwable) {
                                    Log.e(LOG_TAG, "## Error while notifying listeners", e)
                                }
                            }
                        }
                    }

                    override fun onUnexpectedError(e: Exception?) {
                        Log.e(SASVerificationTransaction.LOG_TAG, "## Manual verification failed in state", e)
                    }

                    override fun onNetworkError(e: Exception?) {
                        Log.e(SASVerificationTransaction.LOG_TAG, "## Manual verification failed in state", e)
                    }

                    override fun onMatrixError(e: MatrixError?) {
                        Log.e(SASVerificationTransaction.LOG_TAG, "## Manual verification failed in state " + e?.mReason)
                    }
                })
    }

    private fun onStartRequestReceived(event: CryptoEvent) {
        val startReq = JsonUtility.getBasicGson()
                .fromJson(event.content, KeyVerificationStart::class.java)
        val otherUserId = event.getSender()
        if (!startReq.isValid()) {
            Log.e(SASVerificationTransaction.LOG_TAG, "## received invalid verification request")
            if (startReq.transactionID != null) {
                cancelTransaction(
                        session,
                        startReq.transactionID!!,
                        otherUserId,
                        startReq?.fromDevice ?: event.senderKey,
                        CancelCode.UnknownMethod
                )
            }
            return
        }
        //Download device keys prior to everything
        checkKeysAreDownloaded(
                session,
                otherUserId,
                startReq,
                success = {
                    Log.d(SASVerificationTransaction.LOG_TAG, "## SAS onStartRequestReceived ${startReq.transactionID!!}")
                    val tid = startReq.transactionID!!
                    val existing = getExistingTransaction(otherUserId, tid)
                    val existingTxs = getExistingTransactionsForUser(otherUserId)
                    if (existing != null) {
                        //should cancel both!
                        Log.d(SASVerificationTransaction.LOG_TAG, "## SAS onStartRequestReceived - Request exist with same if ${startReq.transactionID!!}")
                        existing.cancel(session, CancelCode.UnexpectedMessage)
                        cancelTransaction(session, tid, otherUserId, startReq.fromDevice!!, CancelCode.UnexpectedMessage)
                    } else if (existingTxs?.isEmpty() == false) {
                        Log.d(SASVerificationTransaction.LOG_TAG,
                                "## SAS onStartRequestReceived - There is already a transaction with this user ${startReq.transactionID!!}")
                        //Multiple keyshares between two devices: any two devices may only have at most one key verification in flight at a time.
                        existingTxs.forEach {
                            it.cancel(session, CancelCode.UnexpectedMessage)
                        }
                        cancelTransaction(session, tid, otherUserId, startReq.fromDevice!!, CancelCode.UnexpectedMessage)
                    } else {
                        //Ok we can create
                        if (KeyVerificationStart.VERIF_METHOD_SAS == startReq.method) {
                            Log.d(SASVerificationTransaction.LOG_TAG, "## SAS onStartRequestReceived - request accepted ${startReq.transactionID!!}")
                            val tx = IncomingSASVerificationTransaction(startReq.transactionID!!, otherUserId)
                            addTransaction(tx)
                            tx.acceptToDeviceEvent(session, otherUserId, startReq)
                        } else {
                            Log.e(SASVerificationTransaction.LOG_TAG, "## SAS onStartRequestReceived - unknown method ${startReq.method}")
                            cancelTransaction(session, tid, otherUserId, startReq.fromDevice
                                    ?: event.senderKey, CancelCode.UnknownMethod)
                        }
                    }
                },
                error = {
                    cancelTransaction(session, startReq.transactionID!!, otherUserId, startReq.fromDevice!!, CancelCode.UnexpectedMessage)
                })
    }

    private fun checkKeysAreDownloaded(session: CryptoSession,
                                       otherUserId: String,
                                       startReq: KeyVerificationStart,
                                       success: (MXUsersDevicesMap<MXDeviceInfo>) -> Unit,
                                       error: () -> Unit) {
        session.requireCrypto().getDeviceList().downloadKeys(listOf(otherUserId), true, object : ApiCallback<MXUsersDevicesMap<MXDeviceInfo>> {
            override fun onUnexpectedError(e: Exception) {
                session.requireCrypto().getDecryptingThreadHandler().post {
                    error()
                }
            }

            override fun onNetworkError(e: Exception) {
                session.requireCrypto().getDecryptingThreadHandler().post {
                    error()
                }
            }

            override fun onMatrixError(e: MatrixError) {
                session.requireCrypto().getDecryptingThreadHandler().post {
                    error()
                }
            }

            override fun onSuccess(info: MXUsersDevicesMap<MXDeviceInfo>) {
                session.requireCrypto().getDecryptingThreadHandler().post {
                    if (info.getUserDeviceIds(otherUserId).contains(startReq.fromDevice)) {
                        success(info)
                    } else {
                        error()
                    }
                }
            }
        })
    }

    private fun onCancelReceived(event: CryptoEvent) {
        Log.d(LOG_TAG, "## SAS onCancelReceived")
        val cancelReq = JsonUtility.getBasicGson()
                .fromJson(event.content, KeyVerificationCancel::class.java)
        if (!cancelReq.isValid()) {
            //ignore
            Log.e(LOG_TAG, "## Received invalid accept request")
            return
        }
        val otherUserId = event.getSender()

        Log.d(LOG_TAG, "## SAS onCancelReceived otherUser:$otherUserId reason:${cancelReq.reason}")
        val existing = getExistingTransaction(otherUserId, cancelReq.transactionID!!)
        if (existing == null) {
            Log.e(LOG_TAG, "## Received invalid cancel request")
            return
        }
        if (existing is SASVerificationTransaction) {
            existing.cancelledReason = safeValueOf(cancelReq.code)
            existing.state = SASVerificationTransaction.SASVerificationTxState.OnCancelled
        }
    }

    private fun onAcceptReceived(event: CryptoEvent) {
        val acceptReq = JsonUtility.getBasicGson()
                .fromJson(event.content, KeyVerificationAccept::class.java)
        if (!acceptReq.isValid()) {
            //ignore
            Log.e(LOG_TAG, "## Received invalid accept request")
            return
        }
        val otherUserId = event.getSender()
        val existing = getExistingTransaction(otherUserId, acceptReq.transactionID!!)
        if (existing == null) {
            Log.e(LOG_TAG, "## Received invalid accept request")
            return

        }

        if (existing is SASVerificationTransaction) {
            existing.acceptToDeviceEvent(session, otherUserId, acceptReq)
        } else {
            //not other types now
        }
    }


    private fun onKeyReceived(event: CryptoEvent) {
        val keyReq = JsonUtility.getBasicGson()
                .fromJson(event.content, KeyVerificationKey::class.java)
        if (!keyReq.isValid()) {
            //ignore
            Log.e(LOG_TAG, "## Received invalid key request")
            return
        }
        val otherUserId = event.getSender()
        val existing = getExistingTransaction(otherUserId, keyReq.transactionID!!)
        if (existing == null) {
            Log.e(LOG_TAG, "## Received invalid accept request")
            return
        }
        if (existing is SASVerificationTransaction) {
            existing.acceptToDeviceEvent(session, otherUserId, keyReq)
        } else {
            //not other types now
        }
    }

    private fun onMacReceived(event: CryptoEvent) {
        val macReq = JsonUtility.getBasicGson()
                .fromJson(event.content, KeyVerificationMac::class.java)
        if (!macReq.isValid()) {
            //ignore
            Log.e(LOG_TAG, "## Received invalid key request")
            return
        }
        val otherUserId = event.getSender()
        val existing = getExistingTransaction(otherUserId, macReq.transactionID!!)
        if (existing == null) {
            Log.e(LOG_TAG, "## Received invalid accept request")
            return
        }
        if (existing is SASVerificationTransaction) {
            existing.acceptToDeviceEvent(session, otherUserId, macReq)
        } else {
            //not other types known for now
        }
    }

    fun getExistingTransaction(otherUser: String, tid: String): VerificationTransaction? {
        synchronized(lock = txMap) {
            return txMap[otherUser]?.get(tid)
        }
    }

    private fun getExistingTransactionsForUser(otherUser: String): Collection<VerificationTransaction>? {
        synchronized(txMap) {
            return txMap[otherUser]?.values
        }
    }

    private fun removeTransaction(otherUser: String, tid: String) {
        synchronized(txMap) {
            txMap[otherUser]?.remove(tid)?.removeListener(this)
        }
    }

    private fun addTransaction(tx: VerificationTransaction) {
        tx.otherUserId.let { otherUserId ->
            synchronized(txMap) {
                if (txMap[otherUserId] == null) {
                    txMap[otherUserId] = HashMap()
                }
                txMap[otherUserId]?.set(tx.transactionId, tx)
                dispatchTxAdded(tx)
                tx.addListener(this)
            }
        }
    }

    fun beginKeyVerificationSAS(userId: String, deviceID: String): String? {
        return beginKeyVerification(KeyVerificationStart.VERIF_METHOD_SAS, userId, deviceID)
    }

    fun beginKeyVerification(method: String, userId: String, deviceID: String): String? {
        val txID = createUniqueIDForTransaction(userId, deviceID)
        //should check if already one (and cancel it)
        if (KeyVerificationStart.VERIF_METHOD_SAS == method) {
            val tx = OutgoingSASVerificationRequest(txID, userId, deviceID)
            addTransaction(tx)
            session.requireCrypto().getDecryptingThreadHandler().post {
                tx.start(session)
            }
            return txID
        } else {
            throw IllegalArgumentException("Unknown verification method")
        }
    }

    /**
     * This string must be unique for the pair of users performing verification for the duration that the transaction is valid
     */
    private fun createUniqueIDForTransaction(userId: String, deviceID: String): String {
        val buff = StringBuffer()
        buff
                .append(session.myUserId).append("|")
                .append(session.requireCrypto().myDevice.deviceId).append("|")
                .append(userId).append("|")
                .append(deviceID).append("|")
                .append(UUID.randomUUID().toString())
        return buff.toString()
    }


    override fun transactionUpdated(tx: VerificationTransaction) {
        dispatchTxUpdated(tx)
        if (tx is SASVerificationTransaction
                && (tx.state == SASVerificationTransaction.SASVerificationTxState.Cancelled
                        || tx.state == SASVerificationTransaction.SASVerificationTxState.OnCancelled
                        || tx.state == SASVerificationTransaction.SASVerificationTxState.Verified)
        ) {
            //remove
            this.removeTransaction(tx.otherUserId, tx.transactionId)
        }
    }

    companion object {
        val LOG_TAG: String = VerificationManager::class.java.name

        fun cancelTransaction(session: CryptoSession, transactionId: String, userId: String, userDevice: String, code: CancelCode) {
            val cancelMessage = KeyVerificationCancel.create(transactionId, code)
            val contentMap = MXUsersDevicesMap<Any>()
            contentMap.setObject(cancelMessage, userId, userDevice)

            session.requireCrypto()
                    .getCryptoRestClient()
                    .sendToDevice(CryptoEvent.EVENT_TYPE_KEY_VERIFICATION_CANCEL, contentMap, transactionId, object : ApiCallback<Void> {
                        override fun onSuccess(info: Void?) {
                            Log.d(SASVerificationTransaction.LOG_TAG, "## SAS verification [$transactionId] canceled for reason ${code.value}")
                        }

                        override fun onUnexpectedError(e: Exception?) {
                            Log.e(SASVerificationTransaction.LOG_TAG, "## SAS verification [$transactionId] failed to cancel.", e)
                        }

                        override fun onNetworkError(e: Exception?) {
                            Log.e(SASVerificationTransaction.LOG_TAG, "## SAS verification [$transactionId] failed to cancel.", e)
                        }

                        override fun onMatrixError(e: MatrixError?) {
                            Log.e(SASVerificationTransaction.LOG_TAG, "## SAS verification [$transactionId] failed to cancel. ${e?.localizedMessage}")
                        }
                    })
        }
    }
}