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

/**
 * Backup data for one key.
 */
class KeyBackupData {

    /**
     * Required. The index of the first message in the session that the key can decrypt.
     */
    @SerializedName("first_message_index")
    var firstMessageIndex: Long = 0

    /**
     * Required. The number of times this key has been forwarded.
     */
    @SerializedName("forwarded_count")
    var forwardedCount: Int = 0

    /**
     * Whether the device backing up the key has verified the device that the key is from.
     */
    @SerializedName("is_verified")
    var isVerified: Boolean = false

    /**
     * Algorithm-dependent data.
     */
    @SerializedName("session_data")
    var sessionData: JsonElement? = null
}
