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
import org.matrix.androidsdk.crypto.data.MXOlmInboundGroupSession2
import org.matrix.androidsdk.data.cryptostore.db.deserializeFromRealm
import org.matrix.androidsdk.data.cryptostore.db.serializeForRealm

fun OlmInboundGroupSessionEntity.Companion.createPrimaryKey(sessionId: String?, senderKey: String?) = "$sessionId|$senderKey"

open class OlmInboundGroupSessionEntity(
        // Combined value to build a primary key
        @PrimaryKey var primaryKey: String? = null,
        var sessionId: String? = null,
        var senderKey: String? = null,
        // olmInboundGroupSessionData contains Json
        var olmInboundGroupSessionData: String? = null,
        // Indicate if the key has been backed up to the homeserver
        var backedUp: Boolean = false)
    : RealmObject() {

    fun getInboundGroupSession(): MXOlmInboundGroupSession2? {
        return deserializeFromRealm(olmInboundGroupSessionData)
    }

    fun putInboundGroupSession(mxOlmInboundGroupSession2: MXOlmInboundGroupSession2?) {
        olmInboundGroupSessionData = serializeForRealm(mxOlmInboundGroupSession2)
    }

    companion object
}