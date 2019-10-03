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

package org.matrix.androidsdk.common

import android.os.SystemClock
import android.text.TextUtils
import org.junit.Assert.*
import org.matrix.androidsdk.MXSession
import org.matrix.androidsdk.core.JsonUtils
import org.matrix.androidsdk.crypto.MXCRYPTO_ALGORITHM_MEGOLM
import org.matrix.androidsdk.crypto.MXCRYPTO_ALGORITHM_MEGOLM_BACKUP
import org.matrix.androidsdk.crypto.interfaces.CryptoEvent
import org.matrix.androidsdk.crypto.keysbackup.MegolmBackupAuthData
import org.matrix.androidsdk.crypto.keysbackup.MegolmBackupCreationInfo
import org.matrix.androidsdk.data.RoomState
import org.matrix.androidsdk.listeners.MXEventListener
import org.matrix.androidsdk.rest.model.Event
import org.matrix.androidsdk.rest.model.RoomMember
import org.matrix.androidsdk.rest.model.message.Message
import java.util.*
import java.util.concurrent.CountDownLatch

/**
 * Synchronously enable crypto for the session and fail if it does not work
 */
fun MXSession.enableCrypto(testHelper: CommonTestHelper) {
    val latch = CountDownLatch(1)
    enableCrypto(true, TestApiCallback(latch))
    testHelper.await(latch)
}


class CryptoTestHelper(val mTestHelper: CommonTestHelper) {

    val messagesFromAlice: List<String> = Arrays.asList("0 - Hello I'm Alice!", "4 - Go!")
    val messagesFromBob: List<String> = Arrays.asList("1 - Hello I'm Bob!", "2 - Isn't life grand?", "3 - Let's go to the opera.")

    // Set this value to false to test the new Realm store and to true to test legacy Filestore
    val USE_LEGACY_CRYPTO_STORE = false

    // Lazy loading is on by default now
    private val LAZY_LOADING_ENABLED = true

    val defaultSessionParams = SessionTestParams(true, false, LAZY_LOADING_ENABLED, USE_LEGACY_CRYPTO_STORE)
    val encryptedSessionParams = SessionTestParams(true, true, LAZY_LOADING_ENABLED, USE_LEGACY_CRYPTO_STORE)

    fun buildTextEvent(text: String, session: MXSession, roomId: String): Event {
        val message = Message()
        message.msgtype = Message.MSGTYPE_TEXT
        message.body = text

        return Event(message, session.credentials.userId, roomId)
    }

    /**
     * @return alice session
     */
    fun doE2ETestWithAliceInARoom(): CryptoTestData {
        val results = HashMap<String, Any>()
        val aliceSession = mTestHelper.createAccount(TestConstants.USER_ALICE, defaultSessionParams)
        val lock0 = CountDownLatch(1)

        aliceSession.enableCrypto(true, object : TestApiCallback<Void?>(lock0) {
            override fun onSuccess(info: Void?) {
                results["enableCrypto"] = "enableCrypto"
                super.onSuccess(info)
            }
        })
        mTestHelper.await(lock0)
        assertTrue(results.containsKey("enableCrypto"))

        var roomId: String? = null
        val lock1 = CountDownLatch(1)

        aliceSession.createRoom(object : TestApiCallback<String>(lock1) {
            override fun onSuccess(info: String) {
                roomId = info
                super.onSuccess(info)
            }
        })

        mTestHelper.await(lock1)
        assertNotNull(roomId)

        val room = aliceSession.dataHandler.getRoom(roomId!!)

        val lock2 = CountDownLatch(1)
        room.enableEncryptionWithAlgorithm(MXCRYPTO_ALGORITHM_MEGOLM, object : TestApiCallback<Void?>(lock2) {
            override fun onSuccess(info: Void?) {
                results["enableEncryptionWithAlgorithm"] = "enableEncryptionWithAlgorithm"
                super.onSuccess(info)
            }
        })
        mTestHelper.await(lock2)
        assertTrue(results.containsKey("enableEncryptionWithAlgorithm"))

        return CryptoTestData(aliceSession, roomId!!)
    }

    /**
     * @param cryptedBob
     * @return alice and bob sessions
     */
    fun doE2ETestWithAliceAndBobInARoom(cryptedBob: Boolean = true): CryptoTestData {
        val statuses = HashMap<String, String>()

        val cryptoTestData = doE2ETestWithAliceInARoom()
        val aliceSession = cryptoTestData.firstSession
        val aliceRoomId = cryptoTestData.roomId

        val room = aliceSession.dataHandler.getRoom(aliceRoomId)

        val bobSession = mTestHelper.createAccount(TestConstants.USER_BOB, defaultSessionParams)
        val lock0 = CountDownLatch(1)

        bobSession.enableCrypto(cryptedBob, object : TestApiCallback<Void?>(lock0) {
            override fun onSuccess(info: Void?) {
                statuses["enableCrypto"] = "enableCrypto"
                super.onSuccess(info)
            }
        })
        mTestHelper.await(lock0)

        val lock1 = CountDownLatch(2)

        val bobEventListener = object : MXEventListener() {
            override fun onNewRoom(roomId: String) {
                if (TextUtils.equals(roomId, aliceRoomId)) {
                    if (!statuses.containsKey("onNewRoom")) {
                        statuses["onNewRoom"] = "onNewRoom"
                        lock1.countDown()
                    }
                }
            }
        }

        bobSession.dataHandler.addListener(bobEventListener)

        room.invite(aliceSession, bobSession.myUserId, object : TestApiCallback<Void?>(lock1) {
            override fun onSuccess(info: Void?) {
                statuses["invite"] = "invite"
                super.onSuccess(info)
            }
        })

        mTestHelper.await(lock1)

        assertTrue(statuses.containsKey("invite") && statuses.containsKey("onNewRoom"))

        bobSession.dataHandler.removeListener(bobEventListener)

        val lock2 = CountDownLatch(2)

        bobSession.joinRoom(aliceRoomId, TestApiCallback(lock2))

        room.addEventListener(object : MXEventListener() {
            override fun onLiveEvent(event: Event, roomState: RoomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_STATE_ROOM_MEMBER)) {
                    val contentToConsider = event.contentAsJsonObject
                    val member = JsonUtils.toRoomMember(contentToConsider)

                    if (TextUtils.equals(member.membership, RoomMember.MEMBERSHIP_JOIN)) {
                        statuses["AliceJoin"] = "AliceJoin"
                        lock2.countDown()
                    }
                }
            }
        })

        mTestHelper.await(lock2)

        // Ensure bob can send messages to the room
        val roomFromBobPOV = bobSession.dataHandler.getRoom(aliceRoomId)
        assertNotNull(roomFromBobPOV.state.powerLevels)
        assertTrue(roomFromBobPOV.state.powerLevels.maySendMessage(bobSession.myUserId))

        assertTrue(statuses.toString() + "", statuses.containsKey("AliceJoin"))

        bobSession.dataHandler.removeListener(bobEventListener)

        return CryptoTestData(aliceSession, aliceRoomId, bobSession)
    }

    /**
     * @return Alice, Bob and Sam session
     */
    fun doE2ETestWithAliceAndBobAndSamInARoom(): CryptoTestData {
        val statuses = HashMap<String, String>()

        val cryptoTestData = doE2ETestWithAliceAndBobInARoom(true)
        val aliceSession = cryptoTestData.firstSession
        val aliceRoomId = cryptoTestData.roomId

        val room = aliceSession.dataHandler.getRoom(aliceRoomId)

        val samSession = mTestHelper.createAccount(TestConstants.USER_SAM, defaultSessionParams)
        val lock0 = CountDownLatch(1)

        samSession.enableCrypto(true, object : TestApiCallback<Void?>(lock0) {
            override fun onSuccess(info: Void?) {
                statuses["enableCrypto"] = "enableCrypto"
                super.onSuccess(info)
            }
        })
        mTestHelper.await(lock0)

        val lock1 = CountDownLatch(2)

        val samEventListener = object : MXEventListener() {
            override fun onNewRoom(roomId: String) {
                if (TextUtils.equals(roomId, aliceRoomId)) {
                    if (!statuses.containsKey("onNewRoom")) {
                        statuses["onNewRoom"] = "onNewRoom"
                        lock1.countDown()
                    }
                }
            }
        }

        samSession.dataHandler.addListener(samEventListener)

        room.invite(aliceSession, samSession.myUserId, object : TestApiCallback<Void?>(lock1) {
            override fun onSuccess(info: Void?) {
                statuses["invite"] = "invite"
                super.onSuccess(info)
            }
        })

        mTestHelper.await(lock1)

        assertTrue(statuses.containsKey("invite") && statuses.containsKey("onNewRoom"))

        samSession.dataHandler.removeListener(samEventListener)

        val lock2 = CountDownLatch(1)

        samSession.joinRoom(aliceRoomId, object : TestApiCallback<String>(lock2) {
            override fun onSuccess(info: String) {
                statuses["joinRoom"] = "joinRoom"
                super.onSuccess(info)
            }
        })

        mTestHelper.await(lock2)
        assertTrue(statuses.containsKey("joinRoom"))

        // wait the initial sync
        SystemClock.sleep(1000)

        samSession.dataHandler.removeListener(samEventListener)

        return CryptoTestData(aliceSession, aliceRoomId, cryptoTestData.secondSession, samSession)
    }

    /**
     * @param cryptedBob
     * @return Alice and Bob sessions
     */
    fun doE2ETestWithAliceAndBobInARoomWithEncryptedMessages(cryptedBob: Boolean): CryptoTestData {
        val cryptoTestData = doE2ETestWithAliceAndBobInARoom(cryptedBob)
        val aliceSession = cryptoTestData.firstSession
        val aliceRoomId = cryptoTestData.roomId
        val bobSession = cryptoTestData.secondSession!!

        bobSession.crypto?.setWarnOnUnknownDevices(false)

        aliceSession.crypto?.setWarnOnUnknownDevices(false)

        val roomFromBobPOV = bobSession.dataHandler.getRoom(aliceRoomId)
        val roomFromAlicePOV = aliceSession.dataHandler.getRoom(aliceRoomId)

        var messagesReceivedByBobCount = 0
        var lock = CountDownLatch(3)

        val bobEventsListener = object : MXEventListener() {
            override fun onLiveEvent(event: Event, roomState: RoomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE) && !TextUtils.equals(event.getSender(), bobSession.myUserId)) {
                    messagesReceivedByBobCount++
                    lock.countDown()
                }
            }
        }

        roomFromBobPOV.addEventListener(bobEventsListener)

        val results = HashMap<String, Any>()

        bobSession.dataHandler.addListener(object : MXEventListener() {
            override fun onToDeviceEvent(event: Event) {
                results["onToDeviceEvent"] = event
                lock.countDown()
            }
        })

        // Alice sends a message
        roomFromAlicePOV.sendEvent(buildTextEvent(messagesFromAlice[0], aliceSession, aliceRoomId), TestApiCallback<Void>(lock, true))
        mTestHelper.await(lock)
        assertTrue(results.containsKey("onToDeviceEvent"))
        assertEquals(1, messagesReceivedByBobCount)

        // Bob send a message
        lock = CountDownLatch(1)
        roomFromBobPOV.sendEvent(buildTextEvent(messagesFromBob[0], bobSession, aliceRoomId), TestApiCallback<Void>(lock, true))
        // android does not echo the messages sent from itself
        messagesReceivedByBobCount++
        mTestHelper.await(lock)
        assertEquals(2, messagesReceivedByBobCount)

        // Bob send a message
        lock = CountDownLatch(1)
        roomFromBobPOV.sendEvent(buildTextEvent(messagesFromBob[1], bobSession, aliceRoomId), TestApiCallback<Void>(lock, true))
        // android does not echo the messages sent from itself
        messagesReceivedByBobCount++
        mTestHelper.await(lock)
        assertEquals(3, messagesReceivedByBobCount)

        // Bob send a message
        lock = CountDownLatch(1)
        roomFromBobPOV.sendEvent(buildTextEvent(messagesFromBob[2], bobSession, aliceRoomId), TestApiCallback<Void>(lock, true))
        // android does not echo the messages sent from itself
        messagesReceivedByBobCount++
        mTestHelper.await(lock)
        assertEquals(4, messagesReceivedByBobCount)

        // Alice sends a message
        lock = CountDownLatch(2)
        roomFromAlicePOV.sendEvent(buildTextEvent(messagesFromAlice[1], aliceSession, aliceRoomId), TestApiCallback<Void>(lock, true))
        mTestHelper.await(lock)
        assertEquals(5, messagesReceivedByBobCount)

        return cryptoTestData
    }

    fun checkEncryptedEvent(event: CryptoEvent, roomId: String, clearMessage: String, senderSession: MXSession) {
        assertEquals(Event.EVENT_TYPE_MESSAGE_ENCRYPTED, event.wireType)
        assertNotNull(event.wireContent)

        val eventWireContent = event.wireContent.asJsonObject
        assertNotNull(eventWireContent)

        assertNull(eventWireContent.get("body"))
        assertEquals(MXCRYPTO_ALGORITHM_MEGOLM, eventWireContent.get("algorithm").asString)

        assertNotNull(eventWireContent.get("ciphertext"))
        assertNotNull(eventWireContent.get("session_id"))
        assertNotNull(eventWireContent.get("sender_key"))

        assertEquals(senderSession.credentials.deviceId, eventWireContent.get("device_id").asString)

        assertNotNull(event.getEventId())
        assertEquals(roomId, event.getRoomId())
        assertEquals(Event.EVENT_TYPE_MESSAGE, event.getType())
        assertTrue(event.getAge() < 10000)

        val eventContent = event.contentAsJsonObject
        assertNotNull(eventContent)
        assertEquals(clearMessage, eventContent.get("body").asString)
        assertEquals(senderSession.myUserId, event.getSender())
    }

    fun createFakeMegolmBackupAuthData(): MegolmBackupAuthData {
        return MegolmBackupAuthData(
                publicKey = "abcdefg",
                signatures = HashMap<String, Map<String, String>>().apply {
                    this["something"] = HashMap<String, String>().apply {
                        this["ed25519:something"] = "hijklmnop"
                    }
                }
        )
    }

    fun createFakeMegolmBackupCreationInfo(): MegolmBackupCreationInfo {
        return MegolmBackupCreationInfo().apply {
            algorithm = MXCRYPTO_ALGORITHM_MEGOLM_BACKUP
            authData = createFakeMegolmBackupAuthData()
        }
    }
}
