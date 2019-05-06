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

package org.matrix.androidsdk.crypto.interfaces

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.matrix.androidsdk.crypto.MXCryptoError
import org.matrix.androidsdk.crypto.MXEventDecryptionResult
import org.matrix.androidsdk.crypto.model.crypto.EncryptedEventContent
import org.matrix.androidsdk.crypto.model.crypto.ForwardedRoomKeyContent
import org.matrix.androidsdk.crypto.model.crypto.OlmEventContent
import org.matrix.androidsdk.crypto.model.crypto.RoomKeyContent
import org.matrix.androidsdk.crypto.rest.model.crypto.RoomKeyShare
import org.matrix.androidsdk.crypto.rest.model.crypto.RoomKeyShareRequest

interface CryptoEvent {
    val contentAsJsonObject: JsonObject
    val wireEventContent: CryptoEventContent
    val wireType: String
    val content: JsonElement
    val wireContent: JsonElement
    val senderKey: String
    val keysClaimed: MutableMap<String, String>

    fun getRoomId(): String
    fun getEventId(): String
    fun getAge(): Long
    fun getStateKey(): String
    fun getType(): CharSequence
    fun getSender(): String
    fun toRoomKeyShare(): RoomKeyShare
    fun toRoomKeyShareRequest(): RoomKeyShareRequest
    fun toRoomKeyContent(): RoomKeyContent
    fun toOlmEventContent(): OlmEventContent
    fun toEncryptedEventContent(): EncryptedEventContent
    fun toForwardedRoomKeyContent(): ForwardedRoomKeyContent

    fun setCryptoError(cryptoError: MXCryptoError)

    fun setClearData(decryptionResult: MXEventDecryptionResult)

    companion object {
        const val EVENT_TYPE_ROOM_KEY = "m.room_key"
        const val EVENT_TYPE_ROOM_KEY_REQUEST = "m.room_key_request"
        const val EVENT_TYPE_FORWARDED_ROOM_KEY = "m.forwarded_room_key"
        const val EVENT_TYPE_MESSAGE_ENCRYPTED = "m.room.encrypted"
        const val EVENT_TYPE_MESSAGE_ENCRYPTION = "m.room.encryption"
        const val EVENT_TYPE_STATE_ROOM_MEMBER = "m.room.member"

        // Interactive key verification
        const val EVENT_TYPE_KEY_VERIFICATION_START = "m.key.verification.start"
        const val EVENT_TYPE_KEY_VERIFICATION_ACCEPT = "m.key.verification.accept"
        const val EVENT_TYPE_KEY_VERIFICATION_KEY = "m.key.verification.key"
        const val EVENT_TYPE_KEY_VERIFICATION_MAC = "m.key.verification.mac"
        const val EVENT_TYPE_KEY_VERIFICATION_CANCEL = "m.key.verification.cancel"
    }
}