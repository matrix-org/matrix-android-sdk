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

import com.google.gson.annotations.SerializedName

/**
 * Data model for [org.matrix.androidsdk.rest.model.keys.KeysAlgorithmAndData.authData] in case
 * of [org.matrix.androidsdk.crypto.MXCRYPTO_ALGORITHM_MEGOLM_BACKUP].
 */
data class MegolmBackupAuthData(
        /**
         * The curve25519 public key used to encrypt the backups.
         */
        @SerializedName("public_key")
        val publicKey: String = "",

        /**
         * Signatures of the public key.
         */
        val signatures: Map<String, Map<String, String>>? = null
) {
    /**
     * Same as the parent [MXJSONModel JSONDictionary] but return only
     * data that must be signed.
     */
    fun signalableJSONDictionary(): Map<String, Any> = mapOf("public_key" to publicKey)
}
