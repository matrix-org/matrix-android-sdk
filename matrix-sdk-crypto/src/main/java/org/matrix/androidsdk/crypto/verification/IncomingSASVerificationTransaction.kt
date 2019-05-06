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

import android.util.Base64
import org.matrix.androidsdk.core.JsonUtility
import org.matrix.androidsdk.core.Log
import org.matrix.androidsdk.core.callback.SimpleApiCallback
import org.matrix.androidsdk.crypto.data.MXDeviceInfo
import org.matrix.androidsdk.crypto.interfaces.CryptoEvent
import org.matrix.androidsdk.crypto.interfaces.CryptoSession
import org.matrix.androidsdk.crypto.rest.model.crypto.KeyVerificationAccept
import org.matrix.androidsdk.crypto.rest.model.crypto.KeyVerificationKey
import org.matrix.androidsdk.crypto.rest.model.crypto.KeyVerificationMac
import org.matrix.androidsdk.crypto.rest.model.crypto.KeyVerificationStart

class IncomingSASVerificationTransaction(transactionId: String, otherUserID: String)
    : SASVerificationTransaction(transactionId, otherUserID, null, true) {

    enum class State {
        UNKNOWN,
        SHOW_ACCEPT,
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
                SASVerificationTxState.OnStarted -> State.SHOW_ACCEPT
                SASVerificationTxState.SendingAccept,
                SASVerificationTxState.Accepted,
                SASVerificationTxState.OnKeyReceived,
                SASVerificationTxState.SendingKey,
                SASVerificationTxState.KeySent -> State.WAIT_FOR_KEY_AGREEMENT
                SASVerificationTxState.ShortCodeReady -> State.SHOW_SAS
                SASVerificationTxState.ShortCodeAccepted,
                SASVerificationTxState.SendingMac,
                SASVerificationTxState.MacSent,
                SASVerificationTxState.Verifying -> State.WAIT_FOR_VERIFICATION
                SASVerificationTxState.Verified -> State.VERIFIED
                SASVerificationTxState.Cancelled -> State.CANCELLED_BY_ME
                SASVerificationTxState.OnCancelled -> State.CANCELLED_BY_OTHER
                else -> State.UNKNOWN
            }
        }

    override fun onVerificationStart(session: CryptoSession, startReq: KeyVerificationStart) {
        Log.d(LOG_TAG, "## SAS received verification request from state $state")
        if (state != SASVerificationTxState.None) {
            Log.e(LOG_TAG, "## received verification request from invalid state")
            //should I cancel??
            throw IllegalStateException("Interactive Key verification already started")
        }
        this.startReq = startReq
        state = SASVerificationTxState.OnStarted
        this.otherDeviceId = startReq.fromDevice

    }


    fun performAccept(session: CryptoSession) {
        if (state != SASVerificationTxState.OnStarted) {
            Log.e(LOG_TAG, "## Cannot perform accept from state $state")
            return
        }

        // Select a key agreement protocol, a hash algorithm, a message authentication code,
        // and short authentication string methods out of the lists given in requester's message.
        val agreedProtocol = startReq!!.keyAgreementProtocols?.firstOrNull { KNOWN_AGREEMENT_PROTOCOLS.contains(it) }
        val agreedHash = startReq!!.hashes?.firstOrNull { KNOWN_HASHES.contains(it) }
        val agreedMac = startReq!!.messageAuthenticationCodes?.firstOrNull { KNOWN_MACS.contains(it) }
        val agreedShortCode = startReq!!.shortAuthenticationStrings?.filter { KNOWN_SHORT_CODES.contains(it) }

        //No common key sharing/hashing/hmac/SAS methods.
        //If a device is unable to complete the verification because the devices are unable to find a common key sharing,
        // hashing, hmac, or SAS method, then it should send a m.key.verification.cancel message
        if (listOf(agreedProtocol, agreedHash, agreedMac).any { it.isNullOrBlank() }
                || agreedShortCode.isNullOrEmpty()) {
            //Failed to find agreement
            Log.e(LOG_TAG, "## Failed to find agreement ")
            cancel(session, CancelCode.UnknownMethod)
            return
        }

        //Bob’s device ensures that it has a copy of Alice’s device key.
        session.requireCrypto().getDeviceInfo(this.otherUserId, otherDeviceId, object : SimpleApiCallback<MXDeviceInfo>() {
            override fun onSuccess(info: MXDeviceInfo?) {
                if (info?.fingerprint() == null) {
                    Log.e(LOG_TAG, "## Failed to find device key ")
                    //TODO force download keys!!
                    //would be probably better to download the keys
                    //for now I cancel
                    session.requireCrypto().getDecryptingThreadHandler().post {
                        cancel(session, CancelCode.User)
                    }
                } else {
                    //                    val otherKey = info.identityKey()
                    //need to jump back to correct thread
                    val accept = KeyVerificationAccept.create(
                            tid = transactionId,
                            keyAgreementProtocol = agreedProtocol!!,
                            hash = agreedHash!!,
                            messageAuthenticationCode = agreedMac!!,
                            shortAuthenticationStrings = agreedShortCode,
                            commitment = Base64.encodeToString("temporary commitment".toByteArray(), Base64.DEFAULT)
                    )
                    session.requireCrypto().getDecryptingThreadHandler().post {
                        doAccept(session, accept)
                    }
                }
            }
        })
    }


    private fun doAccept(session: CryptoSession, accept: KeyVerificationAccept) {
        this.accepted = accept
        Log.d(LOG_TAG, "## SAS accept request id:$transactionId")

        //The hash commitment is the hash (using the selected hash algorithm) of the unpadded base64 representation of QB,
        // concatenated with the canonical JSON representation of the content of the m.key.verification.start message
        val concat = getSAS().publicKey + JsonUtility.canonicalize(JsonUtility.getBasicGson().toJsonTree(startReq!!)).toString()
        accept.commitment = hashUsingAgreedHashMethod(concat) ?: ""
        //we need to send this to other device now
        state = SASVerificationTxState.SendingAccept
        sendToOther(CryptoEvent.EVENT_TYPE_KEY_VERIFICATION_ACCEPT, accept, session, SASVerificationTxState.Accepted, CancelCode.User) {
            if (state == SASVerificationTxState.SendingAccept) {
                //It is possible that we receive the next event before this one :/, in this case we should keep state
                state = SASVerificationTxState.Accepted
            }
        }
    }


    override fun onVerificationAccept(session: CryptoSession, accept: KeyVerificationAccept) {
        Log.d(LOG_TAG, "## SAS invalid message for incoming request id:$transactionId")
        cancel(session, CancelCode.UnexpectedMessage)
    }

    override fun onKeyVerificationKey(session: CryptoSession, userId: String, vKey: KeyVerificationKey) {
        Log.d(LOG_TAG, "## SAS received key for request id:$transactionId")
        if (state != SASVerificationTxState.SendingAccept && state != SASVerificationTxState.Accepted) {
            Log.e(LOG_TAG, "## received key from invalid state $state")
            cancel(session, CancelCode.UnexpectedMessage)
            return
        }

        otherKey = vKey.key
        // Upon receipt of the m.key.verification.key message from Alice’s device,
        // Bob’s device replies with a to_device message with type set to m.key.verification.key,
        // sending Bob’s public key QB
        val pubKey = getSAS().publicKey

        val keyToDevice = KeyVerificationKey.create(transactionId, pubKey)
        //we need to send this to other device now
        state = SASVerificationTxState.SendingKey
        this.sendToOther(CryptoEvent.EVENT_TYPE_KEY_VERIFICATION_KEY, keyToDevice, session, SASVerificationTxState.KeySent, CancelCode.User) {
            if (state == SASVerificationTxState.SendingKey) {
                //It is possible that we receive the next event before this one :/, in this case we should keep state
                state = SASVerificationTxState.KeySent
            }
        }

        // Alice’s and Bob’s devices perform an Elliptic-curve Diffie-Hellman
        // (calculate the point (x,y)=dAQB=dBQA and use x as the result of the ECDH),
        // using the result as the shared secret.

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
                "$otherUserId$otherDeviceId" +
                "${session.myUserId}${session.requireCrypto().myDevice.deviceId}" +
                transactionId
        //decimal: generate five bytes by using HKDF.
        //emoji: generate six bytes by using HKDF.
        shortCodeBytes = getSAS().generateShortCode(sasInfo, 6)

        Log.e(LOG_TAG, "************  BOB CODE ${getDecimalCodeRepresentation(shortCodeBytes!!)}")
        Log.e(LOG_TAG, "************  BOB EMOJI CODE ${getShortCodeRepresentation(KeyVerificationStart.SAS_MODE_EMOJI)}")

        state = SASVerificationTxState.ShortCodeReady
    }

    override fun onKeyVerificationMac(session: CryptoSession, vKey: KeyVerificationMac) {
        Log.d(LOG_TAG, "## SAS received mac for request id:$transactionId")
        //Check for state?
        if (state != SASVerificationTxState.SendingKey
                && state != SASVerificationTxState.KeySent
                && state != SASVerificationTxState.ShortCodeReady
                && state != SASVerificationTxState.ShortCodeAccepted
                && state != SASVerificationTxState.SendingMac
                && state != SASVerificationTxState.MacSent) {
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