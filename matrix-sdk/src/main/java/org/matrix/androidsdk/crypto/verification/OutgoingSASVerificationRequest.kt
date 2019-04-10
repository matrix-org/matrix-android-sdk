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

import org.matrix.androidsdk.MXSession
import org.matrix.androidsdk.crypto.data.MXUsersDevicesMap
import org.matrix.androidsdk.rest.callback.ApiCallback
import org.matrix.androidsdk.rest.model.Event
import org.matrix.androidsdk.rest.model.MatrixError
import org.matrix.androidsdk.rest.model.crypto.KeyVerificationAccept
import org.matrix.androidsdk.rest.model.crypto.KeyVerificationKey
import org.matrix.androidsdk.rest.model.crypto.KeyVerificationMac
import org.matrix.androidsdk.rest.model.crypto.KeyVerificationStart
import org.matrix.androidsdk.util.JsonUtils
import org.matrix.androidsdk.util.Log

class OutgoingSASVerificationRequest(transactionId: String, otherUserID: String, otherDevice: String)
    : SASVerificationTransaction(transactionId, otherUserID, otherDevice, isIncoming = false) {


    enum class State {
        UNKNOWN,
        WAIT_FOR_START,
        WAIT_FOR_KEY_AGREEMENT,
        SHOW_SAS,
        WAIT_FOR_VERIFICATION,
        VERIFIED,
        CANCELLED_BY_ME,
        CANCELLED_BY_OTHER
    }

    val uxState: State
        get() {
            return when (state) {
                SASVerificationTxState.None -> State.WAIT_FOR_START
                SASVerificationTxState.SendingStart,
                SASVerificationTxState.Started,
                SASVerificationTxState.OnAccepted,
                SASVerificationTxState.SendingKey,
                SASVerificationTxState.KeySent,
                SASVerificationTxState.OnKeyReceived -> State.WAIT_FOR_KEY_AGREEMENT
                SASVerificationTxState.ShortCodeReady -> State.SHOW_SAS
                SASVerificationTxState.ShortCodeAccepted,
                SASVerificationTxState.SendingMac,
                SASVerificationTxState.MacSent,
                SASVerificationTxState.Verifying -> State.WAIT_FOR_VERIFICATION
                SASVerificationTxState.Verified -> State.VERIFIED
                SASVerificationTxState.OnCancelled -> State.CANCELLED_BY_ME
                SASVerificationTxState.Cancelled -> State.CANCELLED_BY_OTHER
                else -> {
                    State.UNKNOWN
                }
            }
        }

    override fun onVerificationStart(session: MXSession, startReq: KeyVerificationStart) {
        Log.e(LOG_TAG, "## onVerificationStart - unexpected id:$transactionId")
        cancel(session, CancelCode.UnexpectedMessage)
    }

    fun start(session: MXSession) {

        if (state != SASVerificationTxState.None) {
            Log.e(LOG_TAG, "## start verification from invalid state")
            //should I cancel??
            throw IllegalStateException("Interactive Key verification already started")
        }

        val startMessage = KeyVerificationStart()
        startMessage.fromDevice = session.crypto?.myDevice?.deviceId
        startMessage.method = KeyVerificationStart.VERIF_METHOD_SAS
        startMessage.transactionID = transactionId
        startMessage.key_agreement_protocols = KNOWN_AGREEMENT_PROTOCOLS
        startMessage.hashes = KNOWN_HASHES
        startMessage.message_authentication_codes = KNOWN_MAC
        startMessage.short_authentication_string = KNOWN_SHORT_CODES

        startReq = startMessage
        val contentMap = MXUsersDevicesMap<Any>()
        contentMap.setObject(startMessage, otherUserID, otherDevice)
        state = SASVerificationTxState.SendingStart
        session.cryptoRestClient.sendToDevice(Event.EVENT_TYPE_KEY_VERIFICATION_START, contentMap, transactionId, object : ApiCallback<Void> {
            override fun onSuccess(info: Void?) {
                state = SASVerificationTxState.Started
                Log.d(LOG_TAG, "## SAS verification [$transactionId] started ")
            }

            override fun onUnexpectedError(e: Exception?) {
                Log.e(LOG_TAG, "## SAS verification [$transactionId] failed to start.")
                cancelledReason = CancelCode.User
                state = SASVerificationTxState.Cancelled
            }

            override fun onNetworkError(e: Exception?) {
                Log.e(LOG_TAG, "## SAS verification [$transactionId] failed to start.")
                cancelledReason = CancelCode.User
                state = SASVerificationTxState.Cancelled
            }

            override fun onMatrixError(e: MatrixError?) {
                Log.e(LOG_TAG, "## SAS verification [$transactionId] failed to start.")
                cancelledReason = CancelCode.User
                state = SASVerificationTxState.Cancelled
            }
        })
    }

    override fun onVerificationAccept(session: MXSession, accept: KeyVerificationAccept) {
        Log.d(LOG_TAG, "## onVerificationAccept id:$transactionId")
        if (state != SASVerificationTxState.Started) {
            Log.e(LOG_TAG, "## received accept request from invalid state $state")
            cancel(session, CancelCode.UnexpectedMessage)
            return
        }
        //Check that the agreement is correct
        if (
                !KNOWN_AGREEMENT_PROTOCOLS.contains(accept.key_agreement_protocol)
                || !KNOWN_HASHES.contains(accept.hash)
                || !KNOWN_MAC.contains(accept.message_authentication_code)
                || accept.short_authentication_string!!.intersect(KNOWN_SHORT_CODES).isEmpty()

        ) {
            Log.e(LOG_TAG, "## received accet request from invalid state")
            cancel(session, CancelCode.UnknownMethod)
            return
        }

        //Upon receipt of the m.key.verification.accept message from Bob’s device,
        // Alice’s device stores the commitment value for later use.
        accepted = accept
        state = SASVerificationTxState.OnAccepted

        //  Alice’s device creates an ephemeral Curve25519 key pair (dA,QA),
        // and replies with a to_device message with type set to “m.key.verification.key”, sending Alice’s public key QA
        val pubKey = getSAS().publicKey

        val keyToDevice = KeyVerificationKey.new(transactionId, pubKey)
        //we need to send this to other device now
        state = SASVerificationTxState.SendingKey
        sendToOther(Event.EVENT_TYPE_KEY_VERIFICATION_KEY, keyToDevice, session, SASVerificationTxState.KeySent, CancelCode.User) {
            //It is possible that we receive the next event before this one :/, in this case we should keep state
            if (state == SASVerificationTxState.SendingKey) {
                state = SASVerificationTxState.KeySent
            }
        }
    }

    override fun onKeyVerificationKey(session: MXSession, userId: String, vKey: KeyVerificationKey) {
        Log.d(LOG_TAG, "## onKeyVerificationKey id:$transactionId")
        if (state != SASVerificationTxState.SendingKey && state != SASVerificationTxState.KeySent) {
            Log.e(LOG_TAG, "## received key from invalid state $state")
            cancel(session, CancelCode.UnexpectedMessage)
            return
        }

        otherKey = vKey.key
        // Upon receipt of the m.key.verification.key message from Bob’s device,
        // Alice’s device checks that the commitment property from the Bob’s m.key.verification.accept
        // message is the same as the expected value based on the value of the key property received
        // in Bob’s m.key.verification.key and the content of Alice’s m.key.verification.start message.

        //check commitment
        val concat = vKey.key + JsonUtils.canonicalize(JsonUtils.getBasicGson().toJsonTree(startReq!!)).toString()
        val otherCommitment = hashUsingAgreedHashMethod(concat) ?: ""

        if (accepted!!.commitment.equals(otherCommitment)) {
            getSAS().setTheirPublicKey(otherKey)
            //(Note: In all of the following HKDF is as defined in RFC 5869, and uses the previously agreed-on hash function as the hash function,
            // the shared secret as the input keying material, no salt, and with the input parameter set to the concatenation of:
            // - the string “MATRIX_KEY_VERIFICATION_SAS”,
            // - the Matrix ID of the user who sent the m.key.verification.start message,
            // - the device ID of the device that sent the m.key.verification.start message,
            // - the Matrix ID of the user who sent the m.key.verification.accept message,
            // - he device ID of the device that sent the m.key.verification.accept message
            // - the transaction ID.
            val sasInfo = "MATRIX_KEY_VERIFICATION_SAS" +
                    "${session.myUserId}${session.crypto!!.myDevice.deviceId}" +
                    "$otherUserID$otherDevice" +
                    transactionId
            //decimal: generate five bytes by using HKDF.
            //emoji: generate six bytes by using HKDF.
            shortCodeBytes = getSAS().generateShortCode(sasInfo, 6)
//            Log.e(LOG_TAG, "************  ALICE CODE ${getDecimalCodeRepresentation(shortCodeBytes!!)}")
//            Log.e(LOG_TAG, "************  ALICE EMOJI CODE ${getShortCodeRepresentation(KeyVerificationStart.SAS_MODE_EMOJI)}")
            state = SASVerificationTxState.ShortCodeReady
        } else {
            //bad commitement
            cancel(session, CancelCode.MismatchedCommitment)
        }
    }

    override fun onKeyVerificationMac(session: MXSession, vKey: KeyVerificationMac) {
        Log.d(LOG_TAG, "## onKeyVerificationMac id:$transactionId")
        if (
                state != SASVerificationTxState.OnKeyReceived &&
                state != SASVerificationTxState.ShortCodeReady &&
                state != SASVerificationTxState.ShortCodeAccepted &&
                state != SASVerificationTxState.SendingMac &&
                state != SASVerificationTxState.MacSent) {
            Log.e(LOG_TAG, "## received key from invalid state $state")
            cancel(session, CancelCode.UnexpectedMessage)
            return
        }

        theirMac = vKey

        //Do I have my Mac?
        if (myMac != null) {
            //I can check
            verifyMacs(session)
        }
        //Wait for ShortCode Accepted
    }
}