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

@file:Suppress("DEPRECATION")

package org.matrix.androidsdk.crypto

import android.support.test.InstrumentationRegistry
import android.text.TextUtils
import org.junit.Assert.*
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.matrix.androidsdk.common.*
import org.matrix.androidsdk.crypto.data.MXDeviceInfo
import org.matrix.androidsdk.crypto.data.MXOlmSession
import org.matrix.androidsdk.data.RoomState
import org.matrix.androidsdk.data.cryptostore.IMXCryptoStore
import org.matrix.androidsdk.data.cryptostore.MXFileCryptoStore
import org.matrix.androidsdk.data.cryptostore.db.RealmCryptoStore
import org.matrix.androidsdk.data.timeline.EventTimeline
import org.matrix.androidsdk.listeners.MXEventListener
import org.matrix.androidsdk.rest.model.Event
import org.matrix.androidsdk.rest.model.crypto.RoomKeyRequestBody
import org.matrix.androidsdk.util.Log
import org.matrix.olm.OlmAccount
import org.matrix.olm.OlmManager
import org.matrix.olm.OlmSession
import java.util.concurrent.CountDownLatch

@FixMethodOrder(MethodSorters.JVM)
class CryptoStoreImportationTest {

    private val mTestHelper = CommonTestHelper()
    private val mCryptoTestHelper = CryptoTestHelper(mTestHelper)
    private val cryptoStoreHelper = CryptoStoreHelper()

    private val sessionTestParamLegacy = SessionTestParams(withInitialSync = true, withCryptoEnabled = true, withLegacyCryptoStore = true)
    private val sessionTestParamRealm = SessionTestParams(withInitialSync = true, withCryptoEnabled = true, withLegacyCryptoStore = false)

    @Before
    fun ensureLibLoaded() {
        OlmManager()
    }

    @Test
    fun test_importationEmptyStore() {
        testImportation(
                doOnFileStore = {
                    // Nothing to do for this test
                },
                checkOnRealmStore = {
                    // Compare the two store contents
                    assertEquals("deviceId_sample", it.deviceId)
                    assertNull(it.account)
                })
    }

    @Test
    fun test_importationOlmAccount() {
        val olmAccount = OlmAccount()

        testImportation(
                doOnFileStore = {
                    it.storeAccount(olmAccount)
                },
                checkOnRealmStore = {
                    val olmAccountFromRealm = it.account

                    assertNotNull(olmAccountFromRealm)
                    assertEquals(olmAccount.identityKeys(), olmAccountFromRealm.identityKeys())
                })
    }

    @Test
    fun test_importationRooms() {
        testImportation(
                doOnFileStore = {
                    it.storeRoomAlgorithm("roomId1", "algo1")
                    it.storeRoomAlgorithm("roomId2", "algo2")

                    it.roomsListBlacklistUnverifiedDevices = listOf("roomId2")
                },
                checkOnRealmStore = {
                    assertEquals("algo1", it.getRoomAlgorithm("roomId1"))
                    assertEquals("algo2", it.getRoomAlgorithm("roomId2"))

                    assertEquals(listOf("roomId2"), it.roomsListBlacklistUnverifiedDevices)
                })
    }

    @Test
    fun test_importationUsers() {
        val deviceTrackingStatus = HashMap<String, Int>().apply {
            put("userId1", MXDeviceList.TRACKING_STATUS_DOWNLOAD_IN_PROGRESS)
        }

        testImportation(
                doOnFileStore = {
                    it.storeUserDevice("userId1", MXDeviceInfo().apply {
                        deviceId = "deviceId1"
                        userId = "userId1"
                        mVerified = MXDeviceInfo.DEVICE_VERIFICATION_VERIFIED
                    })

                    it.saveDeviceTrackingStatuses(deviceTrackingStatus)
                },
                checkOnRealmStore = {
                    val deviceFromRealm = it.getUserDevice("deviceId1", "userId1")

                    assertEquals(MXDeviceInfo.DEVICE_VERIFICATION_VERIFIED, deviceFromRealm.mVerified)
                    assertEquals(deviceTrackingStatus, it.deviceTrackingStatuses)
                })
    }

    @Test
    fun test_importationOutgoingRoomKeyRequest() {
        val request = OutgoingRoomKeyRequest(
                // Request body
                RoomKeyRequestBody().apply {
                    algorithm = "algorithm"
                    roomId = "roomId"
                    senderKey = "senderKey"
                    sessionId = "sessionId"
                },
                // Recipients
                ArrayList<Map<String, String>>().apply {
                    add(HashMap<String, String>().apply {
                        put("recipient", "recipientsValue")
                    })
                },
                "RequestId",
                OutgoingRoomKeyRequest.RequestState.CANCELLATION_PENDING_AND_WILL_RESEND)
                .apply {
                    mCancellationTxnId = "mCancellationTxnId"
                }

        testImportation(
                doOnFileStore = {
                    it.getOrAddOutgoingRoomKeyRequest(request)
                },
                checkOnRealmStore = {
                    val requestFromRealm = it.getOutgoingRoomKeyRequest(request.mRequestBody)

                    assertNotNull(requestFromRealm)

                    assertEquals("algorithm", requestFromRealm!!.mRequestBody.algorithm)
                    assertEquals("roomId", requestFromRealm.mRequestBody.roomId)
                    assertEquals("senderKey", requestFromRealm.mRequestBody.senderKey)
                    assertEquals("sessionId", requestFromRealm.mRequestBody.sessionId)
                    assertEquals("recipientsValue", requestFromRealm.mRecipients[0]["recipient"])
                    assertEquals("RequestId", requestFromRealm.mRequestId)
                    assertEquals(OutgoingRoomKeyRequest.RequestState.CANCELLATION_PENDING_AND_WILL_RESEND, requestFromRealm.mState)
                    assertEquals("mCancellationTxnId", requestFromRealm.mCancellationTxnId)
                })
    }

    @Test
    fun test_importationIncomingRoomKeyRequest() {
        val request = IncomingRoomKeyRequest().apply {
            mUserId = "userId"
            mDeviceId = "DeviceId"
            mRequestId = "RequestId"
            mRequestBody = RoomKeyRequestBody().apply {
                algorithm = "Algo"
                roomId = "RoomId"
                senderKey = "SenderKey"
                sessionId = "SessionId"
            }
        }

        testImportation(
                doOnFileStore = {
                    it.storeIncomingRoomKeyRequest(request)
                },
                checkOnRealmStore = {
                    assertEquals(1, it.pendingIncomingRoomKeyRequests.size)

                    val requestFromRealm = it.getIncomingRoomKeyRequest(request.mUserId, request.mDeviceId, request.mRequestId)

                    assertEquals("userId", requestFromRealm!!.mUserId)
                    assertEquals("DeviceId", requestFromRealm.mDeviceId)
                    assertEquals("RequestId", requestFromRealm.mRequestId)
                    assertEquals("Algo", requestFromRealm.mRequestBody.algorithm)
                    assertEquals("RoomId", requestFromRealm.mRequestBody.roomId)
                    assertEquals("SenderKey", requestFromRealm.mRequestBody.senderKey)
                    assertEquals("SessionId", requestFromRealm.mRequestBody.sessionId)
                })
    }

    @Test
    fun test_importationOlmSessions() {
        val session = OlmSession()

        val sessionId = session.sessionIdentifier()

        testImportation(
                doOnFileStore = {
                    it.storeSession(MXOlmSession(session), "deviceID")
                },
                checkOnRealmStore = {
                    val sessionsFromRealm = it.getDeviceSessionIds("deviceID")

                    assertEquals(1, sessionsFromRealm!!.size)
                    assertTrue(sessionsFromRealm.contains(sessionId))

                    val sessionFromRealm = it.getDeviceSession(sessionId, "deviceID")

                    assertNotNull(sessionFromRealm)
                    assertEquals(sessionId, sessionFromRealm?.olmSession?.sessionIdentifier())
                })
    }

    @Test
    fun test_importationInboundGroupSessions() {
        // This is tested in test_integration_importationInboundGroupSession
    }

    /* ==========================================================================================
     * Integration tests
     * ========================================================================================== */

    @Test
    fun test_integration_importationEmptyStore() {
        Log.e(LOG_TAG, "test01_testCryptoNoDeviceId")

        // Create an account using the file store
        val context = InstrumentationRegistry.getContext()
        val bobSession = mTestHelper.createAccount(TestConstants.USER_BOB, sessionTestParamLegacy)

        assertNotNull(bobSession.crypto)
        assertNotNull(bobSession.crypto?.cryptoStore)
        assertTrue(bobSession.crypto?.cryptoStore is MXFileCryptoStore)

        assertNotNull(bobSession.credentials.deviceId)

        // Open again the session, with the Realm store. It will trigger the importation
        val bobSession2 = mTestHelper.createNewSession(bobSession, sessionTestParamRealm)

        // Importation should be ok
        assertNotNull(bobSession2.crypto)
        assertNotNull(bobSession2.crypto?.cryptoStore)
        assertTrue(bobSession2.crypto?.cryptoStore is RealmCryptoStore)

        // Crypto store should contains device
        assertEquals(bobSession.crypto?.cryptoStore?.deviceId, bobSession2.crypto?.cryptoStore?.deviceId)

        bobSession.clear(context)
        bobSession2.clear(context)
    }

    @Test
    fun test_integration_importationInboundGroupSession() {
        Log.e(LOG_TAG, "test_integration_importationInboundGroupSession")

        val context = InstrumentationRegistry.getContext()
        val results = java.util.HashMap<String, Any>()

        val cryptoTestData = mCryptoTestHelper.doE2ETestWithAliceAndBobInARoom(true)
        val aliceSession = cryptoTestData.firstSession
        val aliceRoomId = cryptoTestData.roomId
        val bobSession = cryptoTestData.secondSession

        val messageFromAlice = "Hello I'm Alice!"

        val roomFromBobPOV = bobSession!!.dataHandler.getRoom(aliceRoomId)
        val roomFromAlicePOV = aliceSession.dataHandler.getRoom(aliceRoomId)

        assertTrue(roomFromBobPOV.isEncrypted)
        assertTrue(roomFromAlicePOV.isEncrypted)

        aliceSession.crypto!!.setWarnOnUnknownDevices(false)

        val lock = CountDownLatch(2)

        val eventListener = object : MXEventListener() {
            override fun onLiveEvent(event: Event, roomState: RoomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE)) {
                    mCryptoTestHelper.checkEncryptedEvent(event, aliceRoomId, messageFromAlice, aliceSession)

                    results["onLiveEvent"] = "onLiveEvent"
                    lock.countDown()
                }
            }
        }

        roomFromBobPOV.addEventListener(eventListener)

        roomFromAlicePOV.sendEvent(mCryptoTestHelper.buildTextEvent(messageFromAlice, aliceSession, aliceRoomId), TestApiCallback<Void>(lock))
        mTestHelper.await(lock)

        assertTrue(results.containsKey("onLiveEvent"))

        // Close alice and bob session
        aliceSession.crypto!!.close()
        bobSession.crypto!!.close()

        // Do not login, but instead create a new session
        val aliceSession2 = mTestHelper.createNewSession(aliceSession, sessionTestParamRealm)

        // Check that the store contains the inboundGroupSession
        assertTrue(aliceSession2.crypto?.cryptoStore is RealmCryptoStore)

        // Not empty list
        assertTrue(aliceSession2.crypto!!.cryptoStore!!.inboundGroupSessions!!.isNotEmpty())

        // Bob should still be able to decrypt message from Alice

        // Do not login, but instead create a new session
        val bobSession2 = mTestHelper.createNewSession(bobSession, sessionTestParamRealm)

        // Check that the store contains the inboundGroupSession
        assertTrue(bobSession2.crypto!!.cryptoStore is RealmCryptoStore)

        // Not empty list
        assertFalse(bobSession2.crypto!!.cryptoStore!!.inboundGroupSessions!!.isEmpty())

        val roomFromBobPOV2 = bobSession2.dataHandler.getRoom(aliceRoomId)
        assertTrue(roomFromBobPOV2.isEncrypted)

        val lock2 = CountDownLatch(1)

        val eventListener2 = object : EventTimeline.Listener {
            override fun onEvent(event: Event, direction: EventTimeline.Direction, roomState: RoomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE)) {
                    mCryptoTestHelper.checkEncryptedEvent(event, aliceRoomId, messageFromAlice, aliceSession)

                    results["onLiveEvent2"] = "onLiveEvent2"
                    lock2.countDown()
                }
            }
        }

        roomFromBobPOV2.timeline.addEventTimelineListener(eventListener2)

        roomFromBobPOV2.timeline.backPaginate(1, null)

        mTestHelper.await(lock2)
        assertTrue(results.containsKey("onLiveEvent2"))

        cryptoTestData.clear(context)
        aliceSession2.clear(context)
        bobSession2.clear(context)
    }

    /* ==========================================================================================
     * Private
     * ========================================================================================== */

    private fun testImportation(doOnFileStore: (IMXCryptoStore) -> Unit,
                                checkOnRealmStore: (IMXCryptoStore) -> Unit) {
        val context = InstrumentationRegistry.getContext()

        val credentials = cryptoStoreHelper.createCredential()

        val fileCryptoStore = MXFileCryptoStore(false)
        fileCryptoStore.initWithCredentials(context, credentials)

        fileCryptoStore.open()

        // Let each test do what they want to configure the file store
        doOnFileStore.invoke(fileCryptoStore)

        // It will trigger the importation
        val realmCryptoStore = RealmCryptoStore()
        realmCryptoStore.initWithCredentials(context, credentials)

        // Check the realm store content
        checkOnRealmStore.invoke(realmCryptoStore)

        // Check that file store has been deleted
        assertFalse(fileCryptoStore.hasData())

        fileCryptoStore.close()
        realmCryptoStore.close()
    }

    companion object {
        private const val LOG_TAG = "CryptoStoreImportationTest"
    }
}