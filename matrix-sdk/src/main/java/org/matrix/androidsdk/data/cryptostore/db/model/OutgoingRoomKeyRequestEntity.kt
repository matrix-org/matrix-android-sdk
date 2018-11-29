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

package org.matrix.androidsdk.data.cryptostore.db.model

import io.realm.RealmObject
import io.realm.annotations.PrimaryKey
import org.matrix.androidsdk.crypto.OutgoingRoomKeyRequest
import org.matrix.androidsdk.data.cryptostore.db.deserializeFromRealm
import org.matrix.androidsdk.data.cryptostore.db.serializeForRealm

internal open class OutgoingRoomKeyRequestEntity(
        @PrimaryKey var requestId: String? = null,
        var cancellationTxnId: String? = null,
        // Serialized Json
        var recipientsData: String? = null,
        // Serialized Json
        var requestBodyString: String? = null,
        var state: Int = 0
) : RealmObject() {

    /**
     * Convert to OutgoingRoomKeyRequest
     */
    fun toOutgoingRoomKeyRequest(): OutgoingRoomKeyRequest {
        return OutgoingRoomKeyRequest(
                getRequestBody(),
                getRecipients(),
                requestId,
                OutgoingRoomKeyRequest.RequestState.from(state)
        ).apply {
            this.mCancellationTxnId = cancellationTxnId
        }
    }

    fun getRecipients(): List<MutableMap<String, String>>? {
        return deserializeFromRealm(recipientsData)
    }

    fun putRecipients(recipients: List<MutableMap<String, String>>?) {
        recipientsData = serializeForRealm(recipients)
    }

    fun getRequestBody(): Map<String, String>? {
        return deserializeFromRealm(requestBodyString)
    }

    fun putRequestBody(requestBody: Map<String, String>?) {
        requestBodyString = serializeForRealm(requestBody)
    }
}


