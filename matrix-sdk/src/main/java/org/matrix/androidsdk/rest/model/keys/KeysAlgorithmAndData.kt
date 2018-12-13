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

package org.matrix.androidsdk.rest.model.keys

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName
import org.matrix.androidsdk.crypto.keysbackup.MegolmBackupAuthData
import org.matrix.androidsdk.util.JsonUtils

/**
 * <pre>
 *     Example:
 *
 *     {
 *         "algorithm": "m.megolm_backup.v1.curve25519-aes-sha2",
 *         "auth_data": {
 *             "public_key": "abcdefg",
 *             "signatures": {
 *                 "something": {
 *                     "ed25519:something": "hijklmnop"
 *                 }
 *             }
 *         }
 *     }
 * </pre>
 */
open class KeysAlgorithmAndData {

    /**
     * The algorithm used for storing backups. Currently, only "m.megolm_backup.v1.curve25519-aes-sha2" is defined
     */
    var algorithm: String? = null

    /**
     * algorithm-dependent data, for "m.megolm_backup.v1.curve25519-aes-sha2" see [org.matrix.androidsdk.crypto.keysbackup.MegolmBackupAuthData]
     */
    @SerializedName("auth_data")
    var authData: JsonElement? = null

    /**
     * Facility method to convert authData to a MegolmBackupAuthData object
     */
    fun getAuthDataAsMegolmBackupAuthData() = JsonUtils.getBasicGson()
            .fromJson(authData, MegolmBackupAuthData::class.java)
}
