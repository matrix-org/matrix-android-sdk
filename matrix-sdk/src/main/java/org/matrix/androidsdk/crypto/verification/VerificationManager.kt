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
import org.matrix.androidsdk.MXSession
import org.matrix.androidsdk.crypto.data.MXUsersDevicesMap
import org.matrix.androidsdk.listeners.MXEventListener
import org.matrix.androidsdk.rest.callback.ApiCallback
import org.matrix.androidsdk.rest.model.Event
import org.matrix.androidsdk.rest.model.MatrixError
import org.matrix.androidsdk.rest.model.crypto.*
import org.matrix.androidsdk.util.JsonUtils
import org.matrix.androidsdk.util.Log
import java.util.*
import kotlin.collections.HashMap

/**
 * Manages all current verifications transactions with short codes.
 * Short codes interactive verification is a more user friendly way of verifying devices
 * that is still maintaining a good level of security (alternative to the 43-character strings compare method).
 *
 */
class VerificationManager(val session: MXSession) : VerificationTransaction.Listener {

    interface ManagerListener {
        fun transactionCreated(tx: VerificationTransaction)
        fun transactionUpdated(tx: VerificationTransaction)
    }

    private val uiHandler = Handler(Looper.getMainLooper())

    var autoAcceptIncomingRequests = false

    // map [sender : [transaction]]
    private val txMap = HashMap<String, HashMap<String, VerificationTransaction>>()

    private val sessionListener = object : MXEventListener() {
        override fun onToDeviceEvent(event: Event?) {
            if (event?.sender == null) return
            session.crypto?.decryptingThreadHandler?.post {
                when (event.getType()) {
                    Event.EVENT_TYPE_KEY_VERIFICATION_START -> {
                        onStartRequestReceived(event)
                    }
                    Event.EVENT_TYPE_KEY_VERIFICATION_CANCEL -> {
                        onCancelReceived(event)
                    }
                    Event.EVENT_TYPE_KEY_VERIFICATION_ACCEPT -> {
                        onAcceptReceived(event)
                    }
                    Event.EVENT_TYPE_KEY_VERIFICATION_KEY -> {
                        onKeyReceived(event)
                    }
                    Event.EVENT_TYPE_KEY_VERIFICATION_MAC -> {
                        onMacReceived(event)
                    }
                    else -> {
                        //ignore
                    }
                }
            }
        }
    }

    init {
        session.dataHandler.addListener(sessionListener)
    }

    private var listeners = ArrayList<ManagerListener>()

    fun addListener(listener: ManagerListener) {
        if (!listeners.contains(listener)) listeners.add(listener)
    }

    fun removeListener(listener: ManagerListener) {
        listeners.remove(listener)
    }

    private fun dispatchTxAdded(tx: VerificationTransaction) {
        listeners.forEach {
            uiHandler.post {
                try {
                    it.transactionCreated(tx)
                } catch (e: Throwable) {
                    Log.e(LOG_TAG, "## Error while notifying listeners", e)
                }

            }
        }
    }

    private fun dispatchTxUpdated(tx: VerificationTransaction) {
        listeners.forEach {
            uiHandler.post {
                try {
                    it.transactionUpdated(tx)
                } catch (e: Throwable) {
                    Log.e(LOG_TAG, "## Error while notifying listeners", e)
                }

            }
        }
    }

    private fun onStartRequestReceived(event: Event) {
        val startReq = JsonUtils.getBasicGson()
                .fromJson(event.content, KeyVerificationStart::class.java)
        val otherUserId = event.sender!!
        if (!startReq.isValid()) {
            Log.e(SASVerificationTransaction.LOG_TAG, "## received invalid verification request")
            if (startReq.transactionID != null) {
                cancelTransaction(
                        session,
                        startReq.transactionID!!,
                        otherUserId,
                        startReq?.fromDevice ?: event.senderKey(),
                        CancelCode.UnknownMethod
                )
            }
            return
        }
        val tid = startReq.transactionID!!
        val existing = getExistingTransaction(otherUserId, tid)
        val existingTxs = getExistingTransactionsForUser(otherUserId)
        if (existing != null) {
            //should cancel both!
            existing.cancel(session, CancelCode.UnexpectedMessage)
            cancelTransaction(session, tid, otherUserId, startReq.fromDevice!!, CancelCode.UnexpectedMessage)
        } else if (existingTxs?.isEmpty() == false) {
            //Multiple keyshares between two devices: any two devices may only have at most one key verification in flight at a time.
            existingTxs.forEach {
                it.cancel(session, CancelCode.UnexpectedMessage)
            }
            cancelTransaction(session, tid, otherUserId, startReq.fromDevice!!, CancelCode.UnexpectedMessage)
        } else {
            //Ok we can create
            if (KeyVerificationStart.VERIF_METHOD_SAS == startReq.method) {
                val tx = IncomingSASVerificationTransaction(startReq.transactionID!!, otherUserId, autoAccept = autoAcceptIncomingRequests)
                addTransaction(tx)
                tx.acceptToDeviceEvent(session, otherUserId, startReq)
            } else {
                cancelTransaction(session, tid, otherUserId, startReq.fromDevice
                        ?: event.senderKey(), CancelCode.UnknownMethod)
            }
        }
    }

    private fun onCancelReceived(event: Event) {
        val cancelReq = JsonUtils.getBasicGson()
                .fromJson(event.content, KeyVerificationCancel::class.java)
        if (!cancelReq.isValid()) {
            //ignore
            Log.e(LOG_TAG, "## Received invalid accept request")
            return
        }
        val otherUserId = event.sender!!
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

    private fun onAcceptReceived(event: Event) {
        val acceptReq = JsonUtils.getBasicGson()
                .fromJson(event.content, KeyVerificationAccept::class.java)
        if (!acceptReq.isValid()) {
            //ignore
            Log.e(LOG_TAG, "## Received invalid accept request")
            return
        }
        val otherUserId = event.sender!!
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


    private fun onKeyReceived(event: Event) {
        val keyReq = JsonUtils.getBasicGson()
                .fromJson(event.content, KeyVerificationKey::class.java)
        if (!keyReq.isValid()) {
            //ignore
            Log.e(LOG_TAG, "## Received invalid key request")
            return
        }
        val otherUserId = event.sender!!
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

    private fun onMacReceived(event: Event) {
        val macReq = JsonUtils.getBasicGson()
                .fromJson(event.content, KeyVerificationMac::class.java)
        if (!macReq.isValid()) {
            //ignore
            Log.e(LOG_TAG, "## Received invalid key request")
            return
        }
        val otherUserId = event.sender!!
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
        tx.otherUserID.let { otherUserId ->
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
            session.crypto?.encryptingThreadHandler?.post {
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
                .append(session.crypto!!.myDevice.deviceId).append("|")
                .append(userId).append("|")
                .append(deviceID).append("|")
                .append(UUID.randomUUID().toString())
        return buff.toString()

    }


    override fun transactionUpdated(tx: VerificationTransaction) {
        dispatchTxUpdated(tx)
        if (tx is SASVerificationTransaction
                &&
                (tx.state == SASVerificationTransaction.SASVerificationTxState.Cancelled
                        || tx.state == SASVerificationTransaction.SASVerificationTxState.OnCancelled)
        ) {
            //remove
            this.removeTransaction(tx.otherUserID, tx.transactionId)
        }
    }

    companion object {
        val LOG_TAG = VerificationManager::class.java.name!!

        fun cancelTransaction(session: MXSession, transactionId: String, userId: String, userDevice: String, code: CancelCode) {
            val cancelMessage = KeyVerificationCancel.new(transactionId, code)
            val contentMap = MXUsersDevicesMap<Any>()
            contentMap.setObject(cancelMessage, userId, userDevice)

            session.cryptoRestClient.sendToDevice(Event.EVENT_TYPE_KEY_VERIFICATION_CANCEL, contentMap, transactionId, object : ApiCallback<Void> {
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