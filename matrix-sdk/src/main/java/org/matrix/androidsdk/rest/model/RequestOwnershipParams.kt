/*
 * Copyright 2019 Vector Creations Ltd
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
package org.matrix.androidsdk.rest.model

import com.google.gson.annotations.SerializedName

class RequestOwnershipParams {

    @JvmField
    @SerializedName("client_secret")
    var clientSecret: String? = null

    @JvmField
    @SerializedName("sid")
    var sid: String? = null

    @JvmField
    @SerializedName("token")
    var token: String? = null

    companion object {
        fun with(clientSecret: String, sid: String, token: String): RequestOwnershipParams {
            return RequestOwnershipParams().apply {
                this.clientSecret = clientSecret
                this.sid = sid
                this.token = token
            }
        }
    }
}