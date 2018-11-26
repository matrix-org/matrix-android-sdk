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

package org.matrix.androidsdk.data.cryptostore.db

import junit.framework.Assert.*
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.matrix.androidsdk.crypto.IncomingRoomKeyRequest
import org.matrix.androidsdk.rest.model.crypto.RoomKeyRequestBody

@FixMethodOrder(MethodSorters.JVM)
class DbHelperTest {

    // Base64 is needed here
    @Test
    fun testSerialization_ok() {
        // Create an arbitrary serializable object
        val obj = IncomingRoomKeyRequest().apply {
            mRequestBody = RoomKeyRequestBody().apply {
                algorithm = "algo"
                room_id = "roomId"
                sender_key = "senderKey"
                session_id = "sessionId"
            }
            mDeviceId = "deviceId"
            mUserId = "userId"
            mRequestId = "requestId"
        }

        val s = serializeForRealm(obj)

        assertTrue(s?.isNotEmpty() == true)

        val obj2 = deserializeFromRealm<IncomingRoomKeyRequest>(s)

        assertNotNull(obj2)

        assertEquals(obj.mDeviceId, obj2!!.mDeviceId)
        assertEquals(obj.mUserId, obj2.mUserId)
        assertEquals(obj.mRequestId, obj2.mRequestId)
        assertEquals(obj.mRequestBody.session_id, obj2.mRequestBody.session_id)
        assertEquals(obj.mRequestBody.algorithm, obj2.mRequestBody.algorithm)
        assertEquals(obj.mRequestBody.room_id, obj2.mRequestBody.room_id)
        assertEquals(obj.mRequestBody.sender_key, obj2.mRequestBody.sender_key)
    }
}