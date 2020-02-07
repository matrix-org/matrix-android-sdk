/*
 * Copyright 2020 New Vector Ltd
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
package org.matrix.androidsdk.event

import android.text.TextUtils
import androidx.test.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.matrix.androidsdk.common.CommonTestHelper
import org.matrix.androidsdk.common.CryptoTestHelper
import org.matrix.androidsdk.common.TestApiCallback
import org.matrix.androidsdk.common.TestConstants
import org.matrix.androidsdk.core.Log
import org.matrix.androidsdk.data.RoomState
import org.matrix.androidsdk.listeners.MXEventListener
import org.matrix.androidsdk.rest.model.Event
import java.util.concurrent.CountDownLatch

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SendCustomEventTest {
    private val mTestHelper = CommonTestHelper()
    private val mCryptoTestHelper = CryptoTestHelper(mTestHelper)

    @Test
    fun test01_sendCustomEvent() {
        Log.e(LOG_TAG, "test01_sendEvent")
        val context = InstrumentationRegistry.getContext()
        val bobSession = mTestHelper.createAccount(TestConstants.USER_BOB, mCryptoTestHelper.defaultSessionParams)
        var roomId: String? = null
        val lock1 = CountDownLatch(1)
        bobSession.createRoom(object : TestApiCallback<String>(lock1) {
            override fun onSuccess(info: String) {
                roomId = info
                super.onSuccess(info)
            }
        })
        mTestHelper.await(lock1)
        assertNotNull(roomId)
        val room = bobSession.dataHandler.getRoom(roomId!!)
        // Wait for the event
        var receivedEvent: Event? = null
        val lock3 = CountDownLatch(1)
        bobSession.dataHandler.addListener(object : MXEventListener() {
            override fun onLiveEvent(event: Event, roomState: RoomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE)) {
                    receivedEvent = event
                    lock3.countDown()
                }
            }
        })
        // Send event
        val parser = JsonParser()
        val element = parser.parse("{" +
                "\"body\" : \"message body\"," +
                "\"msgtype\" : \"m.text\"," +
                "\"mirrorIdKey\" : \"customValue\"" +
                "}")
        val content = element.asJsonObject
        val event = Event(Event.EVENT_TYPE_MESSAGE, content, bobSession.myUserId, roomId)
        val lock2 = CountDownLatch(1)
        room.sendEvent(event, TestApiCallback(lock2))
        mTestHelper.await(lock2)
        // Wait for the callback
        mTestHelper.await(lock3)

        assertNotNull(receivedEvent)
        assertEquals("message body", receivedEvent!!.content.asJsonObject.get("body")?.asString)
        assertEquals("m.text", receivedEvent!!.content.asJsonObject.get("msgtype")?.asString)
        assertEquals("customValue", receivedEvent!!.content.asJsonObject.get("mirrorIdKey")?.asString)

        bobSession.clear(context)
    }

    companion object {
        private const val LOG_TAG = "EventTest"
    }
}