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

import android.content.Context
import android.util.Base64
import org.matrix.androidsdk.MXSession
import org.matrix.androidsdk.crypto.data.MXDeviceInfo
import org.matrix.androidsdk.crypto.data.MXKey
import org.matrix.androidsdk.crypto.data.MXUsersDevicesMap
import org.matrix.androidsdk.rest.callback.ApiCallback
import org.matrix.androidsdk.rest.callback.SimpleApiCallback
import org.matrix.androidsdk.rest.model.Event
import org.matrix.androidsdk.rest.model.MatrixError
import org.matrix.androidsdk.rest.model.crypto.*
import org.matrix.androidsdk.util.JsonUtils
import org.matrix.androidsdk.util.Log
import org.matrix.olm.OlmSAS
import org.matrix.olm.OlmUtility
import java.lang.Exception
import kotlin.properties.Delegates

/**
 * Represents an ongoing interactive key verification between two devices.
 */
class SASVerificationTransaction(transactionId: String, otherUserID: String, val autoAccept : Boolean = true) : VerificationTransaction(transactionId, otherUserID) {

    companion object {
        val LOG_TAG = SASVerificationTransaction::javaClass.name

        //ordered by preferred order
        val KNOWN_AGREEMENT_PROTOCOLS = listOf(MXKey.KEY_CURVE_25519_TYPE)
        //ordered by preferred order
        val KNOWN_HASHES = listOf("sha256")
        //ordered by preferred order
        val KNOWN_MAC = listOf("hmac-sha256")

        val KNOWN_SHORT_CODES = listOf(KeyVerificationStart.SAS_MODE_EMOJI, KeyVerificationStart.SAS_MODE_DECIMAL)

    }

    enum class SASVerificationTxState {
        None,
        // I have started a verification request
        Started,
        // Other user/device sent me a request
        OnStarted,
        // I have accepted a request started by the other user/device
        Accepted,
        // My request has been accepted by the other user/device
        OnAccepted,
        // I have sent my public key
        KeySent,
        // The other user/device has sent me his public key
        OnKeyReceived,
        // Short code is ready to be displayed
        ShortCodeReady,
        // I have compared the code and manually said that they match
        ShortCodeAccepted,

        MacSent,
        Verified,

        //Global: The verification has been cancelled (by me or other), see cancelReason for details
        Cancelled
    }

    var state by Delegates.observable(SASVerificationTxState.None) { _, _, new ->
        //        println("$property has changed from $old to $new")
        listeners.forEach {
            try {
                it.transactionUpdated(this)
            } catch (e: Throwable) {
                Log.e(LOG_TAG, "## Error while notifying listeners", e)
            }
        }
        if (new == SASVerificationTxState.Cancelled) {
            releaseSAS()
        }
    }

    var cancelledReason: CancelCode? = null

    private var olmSas: OlmSAS? = null

    var startReq: KeyVerificationStart? = null
    var accepted: KeyVerificationAccept? = null
    var otherKey: String? = null
    var shortCodeBytes: ByteArray? = null


    fun getSAS(): OlmSAS {
        if (olmSas == null) olmSas = OlmSAS()
        return olmSas!!
    }

    //To override finalize(), all you need to do is simply declare it, without using the override keyword:
    protected fun finalize() {
        releaseSAS()
    }

    private fun releaseSAS() {
        // finalization logic
        olmSas?.releaseSas()
        olmSas = null
    }

    public fun start(session: MXSession) {

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

    /**
     * To be called by the client when the user has verified that
     * both short codes do match
     */
    public fun userHasVerifiedShortCode(session: MXSession) {
        if (state != SASVerificationTxState.ShortCodeReady) {
            throw IllegalStateException("Short code is not ready")
        }

        state = SASVerificationTxState.ShortCodeAccepted
        //Alice and Bob’ devices calculate the HMAC of their own device keys and a comma-separated,
        // sorted list of the key IDs that they wish the other user to verify,
        //the shared secret as the input keying material, no salt, and with the input parameter set to the concatenation of:
        // - the string “MATRIX_KEY_VERIFICATION_MAC”,
        // - the Matrix ID of the user whose key is being MAC-ed,
        // - the device ID of the device sending the MAC,
        // - the Matrix ID of the other user,
        // - the device ID of the device receiving the MAC,
        // - the transaction ID, and
        // - the key ID of the key being MAC-ed, or the string “KEY_IDS” if the item being MAC-ed is the list of key IDs.

        val keyId = "ed25519:${session.crypto!!.myDevice.deviceId}"
        val baseInfo = "MATRIX_KEY_VERIFICATION_MAC" +
                session.myUserId +
                session.crypto!!.myDevice.deviceId +
                otherUserID + otherDevice +
                transactionId

        val macKey = getSAS().calculateMac(session.crypto!!.myDevice.identityKey(), baseInfo + keyId)
        val keysMac = getSAS().calculateMac(keyId, baseInfo + "KEY_IDS")

        if (macKey == null || keysMac == null) {
            //Should not happen
            Log.e(LOG_TAG, "## SAS verification [$transactionId] failed to send KeyMac, empty key hashes.")
            cancel(session, CancelCode.User)
            return
        }

        val macMsg = KeyVerificationMac.new(transactionId,
                mapOf<String, String>(Pair(keyId, hashUsingAgreedHashMethod(String(macKey, Charsets.UTF_8))
                        ?: "")),
                hashUsingAgreedHashMethod(String(keysMac, Charsets.UTF_8)) ?: "")

        sendToOther(Event.EVENT_TYPE_KEY_VERIFICATION_MAC, macMsg, session, SASVerificationTxState.MacSent, CancelCode.User)

        //const keyId = `ed25519:${this._baseApis.deviceId}`;
        //        const mac = {};
        //        const baseInfo = "MATRIX_KEY_VERIFICATION_MAC"
        //              + this._baseApis.getUserId() + this._baseApis.deviceId
        //              + this.userId + this.deviceId
        //              + this.transactionId;
        //
        //        mac[keyId] = olmSAS.calculate_mac(
        //            this._baseApis.getDeviceEd25519Key(),
        //            baseInfo + keyId,
        //        );
        //        const keys = olmSAS.calculate_mac(
        //            keyId,
        //            baseInfo + "KEY_IDS",
        //        );
        //this._sendToDevice("m.key.verification.mac", { mac, keys });
    }

    override fun acceptToDeviceEvent(session: MXSession, senderId: String, event: SendToDeviceObject) {
        when (event) {
            is KeyVerificationStart -> onVerificationRequest(session, event)
            is KeyVerificationAccept -> onVerificationAccept(session, event)
            is KeyVerificationKey -> onKeyVerificationKey(session, senderId, event)
            else -> {
                Log.e(LOG_TAG, "TODO")
            }
        }
    }

    //privates

    private fun onVerificationRequest(session: MXSession, startReq: KeyVerificationStart) {
        if (state != SASVerificationTxState.None) {
            Log.e(LOG_TAG, "## received verification request from invalid state")
            //should I cancel??
            throw IllegalStateException("Interactive Key verification already started")
        }
        this.startReq = startReq
        state = SASVerificationTxState.OnStarted
        this.otherDevice = startReq.fromDevice
        if(autoAccept) {
            performAccept(session)
        }

    }

    private fun performAccept( session: MXSession) {
        if( state != SASVerificationTxState.OnStarted) {
            Log.e(LOG_TAG, "## Cannot perform accept from state $state")
            return
        }

        // Select a key agreement protocol, a hash algorithm, a message authentication code,
        // and short authentication string methods out of the lists given in requester's message.
        val agreedProtocol = startReq!!.key_agreement_protocols?.firstOrNull { KNOWN_AGREEMENT_PROTOCOLS.contains(it) }
        val agreedHash = startReq!!.hashes?.firstOrNull { KNOWN_HASHES.contains(it) }
        val agreedMac = startReq!!.message_authentication_codes?.firstOrNull { KNOWN_MAC.contains(it) }
        val agreedShortCode = startReq!!.short_authentication_string?.filter { KNOWN_SHORT_CODES.contains(it) }

        //No common key sharing/hashing/hmac/SAS methods.
        //If a device is unable to complete the verification because the devices are unable to find a common key sharing,
        // hashing, hmac, or SAS method, then it should send a m.key.verification.cancel message
        if (
                listOf(agreedProtocol, agreedHash, agreedMac).any { it.isNullOrBlank() }
                || agreedShortCode?.size == 0
        ) {
            //Failed to find agreement
            Log.e(LOG_TAG, "## Failed to find agreement ")
            cancel(session, CancelCode.UnknownMethod)
            return
        }

        //Bob’s device ensures that it has a copy of Alice’s device key.
        session.crypto!!.getDeviceInfo(this.otherUserID, otherDevice, object : SimpleApiCallback<MXDeviceInfo>() {
            override fun onSuccess(info: MXDeviceInfo?) {
                if (info?.identityKey() == null) {
                    Log.e(LOG_TAG, "## Failed to find device key ")
                    //TODO not sure if this could really happen :/
                    //would be probably better to download the keys
                    //for now I cancel
                    session.crypto?.decryptingThreadHandler?.post {
                        cancel(session, CancelCode.User)
                    }
                } else {
    //                    val otherKey = info.identityKey()
                    //need to jump back to correct thread
                    val accept = KeyVerificationAccept.new(
                            tid = transactionId,
                            key_agreement_protocol = agreedProtocol!!,
                            hash = agreedHash!!,
                            message_authentication_code = agreedMac!!,
                            short_authentication_string = agreedShortCode!!,
                            commitment = Base64.encodeToString("yo i don't commit FUUU".toByteArray(), Base64.DEFAULT)
                    )
                    session.crypto?.decryptingThreadHandler?.post {
                        doAccept(session, accept)
                    }
                }
            }
        })
    }


    private fun onVerificationAccept(session: MXSession, accept: KeyVerificationAccept) {
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
        sendToOther(Event.EVENT_TYPE_KEY_VERIFICATION_KEY, keyToDevice, session, SASVerificationTxState.KeySent, CancelCode.User)
    }

    private fun onKeyVerificationKey(session: MXSession, userId: String, vKey: KeyVerificationKey) {
        if (state != SASVerificationTxState.KeySent && state != SASVerificationTxState.Accepted) {
            Log.e(LOG_TAG, "## received key from invalid state $state")
            cancel(session, CancelCode.UnexpectedMessage)
            return
        }

        otherKey = vKey.key
        if (state == SASVerificationTxState.Accepted) {
            // Upon receipt of the m.key.verification.key message from Alice’s device,
            // Bob’s device replies with a to_device message with type set to m.key.verification.key,
            // sending Bob’s public key QB
            val pubKey = getSAS().publicKey

            val keyToDevice = KeyVerificationKey.new(transactionId, pubKey)
            //we need to send this to other device now
            sendToOther(Event.EVENT_TYPE_KEY_VERIFICATION_KEY, keyToDevice, session, SASVerificationTxState.KeySent, CancelCode.User)

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
                    "$otherUserID$otherDevice" +
                    "${session.myUserId}${session.crypto!!.myDevice.deviceId}" +
                    "$transactionId"
            //decimal: generate five bytes by using HKDF.
            //emoji: generate six bytes by using HKDF.
            shortCodeBytes = getSAS().generateShortCode(sasInfo, 6)


            Log.e(LOG_TAG, "************  BOB CODE ${getDecimalCodeRepresentation(shortCodeBytes!!)}")
            Log.e(LOG_TAG, "************  BOB EMOJI CODE ${getShortCodeRepresentation(KeyVerificationStart.SAS_MODE_EMOJI)}")

            state = SASVerificationTxState.ShortCodeReady


        } else if (state == SASVerificationTxState.KeySent) {
            // Upon receipt of the m.key.verification.key message from Bob’s device,
            // Alice’s device checks that the commitment property from the Bob’s m.key.verification.accept
            // message is the same as the expected value based on the value of the key property received
            // in Bob’s m.key.verification.key and the content of Alice’s m.key.verification.start message.

            //check commitment
            val concat = vKey.key + JsonUtils.canonicalize(JsonUtils.getBasicGson().toJsonTree(startReq!!)).toString()
            var otherCommitment = hashUsingAgreedHashMethod(concat) ?: ""

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
                        "$transactionId"
                //decimal: generate five bytes by using HKDF.
                //emoji: generate six bytes by using HKDF.
                shortCodeBytes = getSAS().generateShortCode(sasInfo, 6)
                Log.e(LOG_TAG, "************  ALICE CODE ${getDecimalCodeRepresentation(shortCodeBytes!!)}")
                Log.e(LOG_TAG, "************  ALICE EMOJI CODE ${getShortCodeRepresentation(KeyVerificationStart.SAS_MODE_EMOJI)}")
                state = SASVerificationTxState.ShortCodeReady
            } else {
                //bad commitement
                cancel(session, CancelCode.MismatchedCommitment)
            }
        }
    }

    private fun doAccept(session: MXSession, accept: KeyVerificationAccept) {
        this.accepted = accept

        //The hash commitment is the hash (using the selected hash algorithm) of the unpadded base64 representation of QB,
        // concatenated with the canonical JSON representation of the content of the m.key.verification.start message
        val concat = getSAS().publicKey + JsonUtils.canonicalize(JsonUtils.getBasicGson().toJsonTree(startReq!!)).toString()
        accept.commitment = hashUsingAgreedHashMethod(concat) ?: ""
        //we need to send this to other device now
        sendToOther(Event.EVENT_TYPE_KEY_VERIFICATION_ACCEPT, accept, session, SASVerificationTxState.Accepted, CancelCode.User)
    }

    override fun cancel(session: MXSession, code: CancelCode) {
        cancelledReason = code
        state = SASVerificationTxState.Cancelled
        ShortCodeVerificationManager.cancelTransaction(session,
                this.transactionId,
                this.otherUserID,
                this.otherDevice ?: "",
                code)
    }

    private fun sendToOther(type: String, keyToDevice: Any, session: MXSession, nextState: SASVerificationTxState, onErrorReason: CancelCode) {
        val contentMap = MXUsersDevicesMap<Any>()
        contentMap.setObject(keyToDevice, otherUserID, otherDevice)

        session.cryptoRestClient.sendToDevice(type, contentMap, transactionId, object : ApiCallback<Void> {
            override fun onSuccess(info: Void?) {
                state = nextState
                Log.d(LOG_TAG, "## SAS verification [$transactionId] pub key sent ")
            }

            override fun onUnexpectedError(e: Exception?) {
                Log.d(LOG_TAG, "## SAS verification [$transactionId] failed in state : $state")
                cancel(session, onErrorReason)
            }

            override fun onNetworkError(e: Exception?) {
                Log.d(LOG_TAG, "## SAS verification [$transactionId] failed in state : $state")
                cancel(session, onErrorReason)
            }

            override fun onMatrixError(e: MatrixError?) {
                Log.d(LOG_TAG, "## SAS verification [$transactionId] failed in state : $state")
                cancel(session, onErrorReason)
            }
        })
    }

    fun getShortCodeRepresentation(short_authentication_string: String): String? {
        if (shortCodeBytes == null) {
            return null
        }
        if (KeyVerificationStart.SAS_MODE_DECIMAL == short_authentication_string) {
            if (shortCodeBytes!!.size < 5) return null
            return getDecimalCodeRepresentation(shortCodeBytes!!)
        } else if (KeyVerificationStart.SAS_MODE_EMOJI == short_authentication_string) {
            if (shortCodeBytes!!.size < 6) return null
            return getEmojiCodeRepresentation(shortCodeBytes!!, null)?.joinToString(" ") { it.emoji }
        } else {
            return null
        }
    }

    private fun hashUsingAgreedHashMethod(toHash: String): String? {
        if ("sha256".toLowerCase() == accepted?.hash?.toLowerCase()) {
            val olmUtil = OlmUtility()
            val hashBytes = olmUtil.sha256(toHash)
            olmUtil.releaseUtility()
            return hashBytes
        }
        return null
    }

    /**
     * decimal: generate five bytes by using HKDF.
     * Take the first 13 bits and convert it to a decimal number (which will be a number between 0 and 8191 inclusive),
     * and add 1000 (resulting in a number between 1000 and 9191 inclusive).
     * Do the same with the second 13 bits, and the third 13 bits, giving three 4-digit numbers.
     * In other words, if the five bytes are B0, B1, B2, B3, and B4, then the first number is (B0 << 5 | B1 >> 3) + 1000,
     * the second number is ((B1 & 0x7) << 10 | B2 << 2 | B3 >> 6) + 1000, and the third number is ((B3 & 0x3f) << 7 | B4 >> 1) + 1000.
     * (This method of converting 13 bits at a time is used to avoid requiring 32-bit clients to do big-number arithmetic,
     * and adding 1000 to the number avoids having clients to worry about properly zero-padding the number when displaying to the user.)
     * The three 4-digit numbers are displayed to the user either with dashes (or another appropriate separator) separating the three numbers,
     * or with the three numbers on separate lines.
     */
    public fun getDecimalCodeRepresentation(byteArray: ByteArray): String? {
        val b0 = byteArray[0].toInt()
        val b1 = byteArray[1].toInt()
        val b2 = byteArray[2].toInt()
        val b3 = byteArray[3].toInt()
        val b4 = byteArray[4].toInt()
        //(B0 << 5 | B1 >> 3) + 1000
        val first = (b0.shl(5) or b1.shr(3)) + 1000
        //((B1 & 0x7) << 10 | B2 << 2 | B3 >> 6) + 1000
        val second = ((b1 and 0x7).shl(5) or b2.shl(2) or b3.shr(6)) + 1000
        //((B3 & 0x3f) << 7 | B4 >> 1) + 1000
        val third = ((b3 and 0x3f).shl(7) or b4.shr(1)) + 1000
        return "$first-$second-$third"
    }


    /**
     * emoji: generate six bytes by using HKDF.
     * Split the first 42 bits into 7 groups of 6 bits, as one would do when creating a base64 encoding.
     * For each group of 6 bits, look up the emoji from Appendix A corresponding
     * to that number 7 emoji are selected from a list of 64 emoji (see Appendix A)
     */
    fun getEmojiCodeRepresentation(byteArray: ByteArray, context: Context?): List<VerificationEmoji.EmojiRepresentation>? {
        val b0 = byteArray[0].toInt()
        val b1 = byteArray[1].toInt()
        val b2 = byteArray[2].toInt()
        val b3 = byteArray[3].toInt()
        val b4 = byteArray[4].toInt()
        val b5 = byteArray[5].toInt()
        return listOf(
                VerificationEmoji.getEmojiForCode((b0 and 0xFC).shr(2), context)!!,
                VerificationEmoji.getEmojiForCode((b0 and 0x3).shl(4) or (b1 and 0xF0).shr(4), context)!!,
                VerificationEmoji.getEmojiForCode((b1 and 0xF).shl(2) or (b2 and 0xC0).shr(6), context)!!,
                VerificationEmoji.getEmojiForCode((b2 and 0x3F), context)!!,

                VerificationEmoji.getEmojiForCode((b3 and 0xFC).shr(2), context)!!,
                VerificationEmoji.getEmojiForCode((b3 and 0x3).shl(4) or (b4 and 0xF0).shr(4), context)!!,
                VerificationEmoji.getEmojiForCode((b4 and 0xF).shl(2) or (b5 and 0xC0).shr(6), context)!!
        )
    }

}