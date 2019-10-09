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
package org.matrix.androidsdk.crypto

import androidx.test.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import org.junit.Assert
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.matrix.androidsdk.RestClientHttpClientFactory
import org.matrix.androidsdk.RestHttpClientFactoryProvider
import org.matrix.androidsdk.common.*
import org.matrix.androidsdk.core.Log
import org.matrix.androidsdk.crypto.interfaces.CryptoEvent
import org.matrix.androidsdk.listeners.MXEventListener
import org.matrix.androidsdk.rest.model.Event
import java.util.concurrent.CountDownLatch

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class PartialSharedSessionTest {


    private val mTestHelper = CommonTestHelper()
    private val mCryptoTestHelper = CryptoTestHelper(mTestHelper)


    private val defaultSessionParamsWithInitialSync = SessionTestParams(
            withInitialSync = true,
            withCryptoEnabled = true,
            withLazyLoading = false,
            withLegacyCryptoStore = false)

    @Test
    fun testPartialSharedSession() {

        val blockKeyRequests = true
        val mockInterceptor = MockOkHttpInterceptor()

        mockInterceptor.addRule(object : MockOkHttpInterceptor.Rule("_matrix/client/") {
            override fun process(originalRequest: Request): Response? {
                Log.e(PartialSharedSessionTest::class.java.name, "************* ${originalRequest.url().toString()}")
                Log.e(PartialSharedSessionTest::class.java.name, "************* >> ${originalRequest.body().toString()}")
                if (originalRequest.url().toString().contains("sendToDevice/m.room_key_request/")) {
                    if (blockKeyRequests) {
                        return Response.Builder()
                                .protocol(Protocol.HTTP_1_1)
                                .request(originalRequest)
                                .message("mocked answer")
                                .body(ResponseBody.create(null, "{}"))
                                .code(200)
                                .build()
                    }
                }
                return null
            }

        })

        RestHttpClientFactoryProvider.defaultProvider = RestClientHttpClientFactory(mockInterceptor)

        val context = InstrumentationRegistry.getContext()

        val aliceSession = mTestHelper.createAccount(TestConstants.USER_ALICE, defaultSessionParamsWithInitialSync)
        aliceSession.credentials.deviceId = "AliceDevice"

        val bobSession = mTestHelper.createAccount(TestConstants.USER_BOB, defaultSessionParamsWithInitialSync)
        bobSession.credentials.deviceId = "BobDevice"

        var roomId: String? = null
        var latch = CountDownLatch(1)
        aliceSession.createRoom(object : TestApiCallback<String>(latch) {
            override fun onSuccess(info: String) {
                roomId = info
                super.onSuccess(info)
            }
        })
        mTestHelper.await(latch)

        val aliceRoom = aliceSession.dataHandler
                .store!!.getRoom(roomId)


        latch = CountDownLatch(1)
        aliceRoom.invite(aliceSession, bobSession.myUserId, TestApiCallback<Void>(latch))
        mTestHelper.await(latch)

        latch = CountDownLatch(1)
        bobSession.joinRoom(roomId, TestApiCallback<String>(latch))
        mTestHelper.await(latch)

        latch = CountDownLatch(1)
        aliceRoom
                .enableEncryptionWithAlgorithm(MXCRYPTO_ALGORITHM_MEGOLM, TestApiCallback<Void>(latch))
        mTestHelper.await(latch)

        aliceSession.crypto!!.setWarnOnUnknownDevices(false)
        bobSession.crypto!!.setWarnOnUnknownDevices(false)

        val sentEvents = ArrayList<Event>()

        val mxListener = object : MXEventListener() {
            override fun onEventSent(event: Event?, prevEventId: String?) {
                super.onEventSent(event, prevEventId)
                sentEvents.add(event!!)
            }
        }
        aliceRoom.dataHandler.addListener(mxListener)
        listOf<String>("Message1", "Message2", "Message3", "Message4", "Message5").forEach {
            val latch = CountDownLatch(1)
            aliceRoom.sendEvent(mCryptoTestHelper.buildTextEvent(it, aliceSession, roomId!!), TestApiCallback<Void>(latch))
            mTestHelper.await(latch)
        }
        aliceRoom.dataHandler.removeListener(mxListener)


        //Now i want to log a new alice session

        val aliceNewSession = mTestHelper.logIntoAccount(aliceSession.myUserId, defaultSessionParamsWithInitialSync)

        val aliceRoomOtherSession = aliceNewSession.dataHandler.store!!.getRoom(aliceRoom.roomId)

        val aliceSecondSessionEvents = sentEvents.map {
            aliceRoomOtherSession.store.getEvent(it.eventId, aliceRoomOtherSession.roomId)
        }

        Assert.assertEquals(5, aliceSecondSessionEvents.size)

        val olmSessionID = aliceSecondSessionEvents[0].contentAsJsonObject?.get("session_id")?.asString
        Assert.assertNotNull(olmSessionID)
        //All message are encrypted and same session, and as we have block key requests, they should be encrypted
        aliceSecondSessionEvents.forEach {
            assert(it.type == Event.EVENT_TYPE_MESSAGE_ENCRYPTED)
            Assert.assertEquals(olmSessionID, it.contentAsJsonObject?.get("session_id")?.asString)
            Assert.assertNull(it.clearEvent)
        }

        val secondMessage = aliceSecondSessionEvents[1]
        val sessionID = secondMessage.contentAsJsonObject?.get("session_id")?.asString
        val senderKey = secondMessage.contentAsJsonObject?.get("sender_key")?.asString
        val osession = aliceSession.crypto?.getOlmDevice()?.getInboundGroupSession(
                sessionID,
                senderKey, aliceRoom.roomId)


        val megolmSessionData = MegolmSessionData()
        megolmSessionData.senderClaimedEd25519Key = osession!!.mKeysClaimed.get("ed25519")
        megolmSessionData.forwardingCurve25519KeyChain = osession.mForwardingCurve25519KeyChain
        megolmSessionData.senderKey = osession.mSenderKey
        megolmSessionData.senderClaimedKeys = osession.mKeysClaimed
        megolmSessionData.roomId = osession.mRoomId
        megolmSessionData.sessionId = osession.mSession.sessionIdentifier()
        megolmSessionData.sessionKey = osession.mSession.export(1)
        megolmSessionData.algorithm = MXCRYPTO_ALGORITHM_MEGOLM


        //import olm session in new session from index 1
        aliceNewSession.crypto!!.getOlmDevice()!!.importInboundGroupSessions(listOf(megolmSessionData))

        val dec = aliceNewSession.crypto!!.decryptEvent(aliceRoomOtherSession.store.getLatestEvent(aliceRoom.roomId), null)
        Assert.assertNotNull(dec)

        //I should not be able do decrypt the first one
        var first: MXEventDecryptionResult? = null
        try {
            first = aliceNewSession.crypto?.decryptEvent(aliceSecondSessionEvents[0], null)
        } catch (e: MXDecryptionException) {

        }

        Assert.assertNull(first)

        //Let's simulate a new toDevice m.forwarded_room_key event with a better session (lower chain index)
        val toDeviceEvent = Event()
        toDeviceEvent.type = Event.EVENT_TYPE_MESSAGE_ENCRYPTED
        toDeviceEvent.sender = aliceSession.myUserId
        val decryptEvent = MXEventDecryptionResult()
        val jo = JsonObject()
        jo.add("type", JsonPrimitive(CryptoEvent.EVENT_TYPE_FORWARDED_ROOM_KEY))

        decryptEvent.mClearEvent = jo
        toDeviceEvent.setClearData(decryptEvent)

        val content = JsonObject()

        content.add("algorithm", JsonPrimitive(MXCRYPTO_ALGORITHM_MEGOLM))
        content.add("session_id", JsonPrimitive(osession.mSession.sessionIdentifier()))
        content.add("session_key", JsonPrimitive(osession.mSession.export(0)))
        content.add("sender_claimed_ed25519_key", JsonPrimitive(osession.mKeysClaimed.get("ed25519")))
        content.add("sender_key", JsonPrimitive(osession.mSenderKey))
        content.add("room_id", JsonPrimitive(osession.mRoomId))

        val sck = JsonObject()
        sck.add("ed25519", JsonPrimitive(osession.mKeysClaimed.get("ed25519")))
        content.add("sender_claimed_keys", sck)

        toDeviceEvent.clearEvent.updateContent(content)


        aliceNewSession.dataHandler.onToDeviceEvent(toDeviceEvent)


        //We should now have received a session with an early chain index, and should be able to decrypt
        try {
            first = aliceNewSession.crypto?.decryptEvent(aliceSecondSessionEvents[0], null)
        } catch (e: MXDecryptionException) {
            Assert.assertTrue("Should be able to decrypt", false)
        }

        Assert.assertNotNull(first)

        aliceSession.clear(context)
        aliceNewSession.clear(context)
        bobSession.clear(context)
    }
}