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
package org.matrix.androidsdk.crypto.rest.model.crypto

import com.google.gson.annotations.SerializedName
import org.matrix.androidsdk.crypto.verification.CancelCode

/**
 * To device event sent by either party to cancel a key verification.
 */
class KeyVerificationCancel : SendToDeviceObject {

    companion object {
        fun create(tid: String, cancelCode: CancelCode): KeyVerificationCancel {
            return KeyVerificationCancel().apply {
                this.transactionID = tid
                this.code = cancelCode.value
                this.reason = cancelCode.humanReadable
            }
        }
    }

    /**
     * the transaction ID of the verification to cancel
     */
    @SerializedName("transaction_id")
    @JvmField
    var transactionID: String? = null

    /**
     * machine-readable reason for cancelling, see #CancelCode
     */
    @JvmField
    var code: String? = null

    /**
     * human-readable reason for cancelling.  This should only be used if the receiving client does not understand the code given.
     */
    @JvmField
    var reason: String? = null

    fun isValid(): Boolean {
        if (transactionID.isNullOrBlank() || code.isNullOrBlank()) {
            return false
        }
        return true
    }
}