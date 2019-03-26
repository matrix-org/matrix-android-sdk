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
package org.matrix.androidsdk.rest.model.crypto

import com.google.gson.annotations.SerializedName
import org.matrix.androidsdk.crypto.verification.SASVerificationTransaction
import org.matrix.androidsdk.util.Log

/**
 * Sent by Alice to initiate an interactive key verification.
 */
class KeyVerificationStart : SendToDeviceObject {

    /**
     * Alice’s device ID
     */
    @SerializedName("from_device")
    @JvmField
    var fromDevice: String? = null

    @JvmField
    var method = VERIF_METHOD_SAS

    /**
     * String to identify the transaction.
     * This string must be unique for the pair of users performing verification for the duration that the transaction is valid.
     * Alice’s device should record this ID and use it in future messages in this transaction.
     */
    @SerializedName("transaction_id")
    @JvmField
    var transactionID: String? = null

    /**
     * An array of key agreement protocols that Alice’s client understands.
     * Must include “curve25519”.
     * Other methods may be defined in the future
     */
    @JvmField
    var key_agreement_protocols: List<String>? = null

    /**
     * An array of hashes that Alice’s client understands.
     * Must include “sha256”.  Other methods may be defined in the future.
     */
    @JvmField
    var hashes: List<String>? = null

    /**
     * An array of message authentication codes that Alice’s client understands.
     * Must include “hmac-sha256”.
     * Other methods may be defined in the future.
     */
    @JvmField
    var message_authentication_codes: List<String>? = null

    /**
     * An array of short authentication string methods that Alice’s client (and Alice) understands.
     * Must include “decimal”.
     * This document also describes the “emoji” method.
     * Other methods may be defined in the future
     */
    @JvmField
    var short_authentication_string: List<String>? = null


    companion object {
        val VERIF_METHOD_SAS = "m.sas.v1"
        val SAS_MODE_DECIMAL = "decimal"
        val SAS_MODE_EMOJI = "emoji"
    }


    fun isValid(): Boolean {
        if (transactionID.isNullOrBlank()
                || fromDevice.isNullOrBlank()
                || method.isNullOrBlank()
                || key_agreement_protocols?.size == 0
                || hashes?.size == 0
                || hashes?.contains("sha256") == false
                || message_authentication_codes?.size == 0
                || message_authentication_codes?.contains("hmac-sha256") == false
                || short_authentication_string?.size == 0
                || short_authentication_string?.contains(KeyVerificationStart.SAS_MODE_DECIMAL) == false) {
            Log.e(SASVerificationTransaction.LOG_TAG, "## received invalid verification request")
            return false
        }
        return true
    }
}
