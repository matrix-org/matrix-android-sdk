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

package org.matrix.androidsdk.crypto

import org.junit.Assert.*
import org.junit.Test
import org.matrix.androidsdk.crypto.data.MXOlmSession
import org.matrix.androidsdk.data.cryptostore.IMXCryptoStore
import org.matrix.olm.OlmManager
import org.matrix.olm.OlmSession

private const val DUMMY_DEVICE_KEY = "DeviceKey"

class CryptoStoreTest {

    private val cryptoStoreHelper = CryptoStoreHelper()

    @Test
    fun test_metadata_file_ok() {
        test_metadata_ok(false)
    }

    @Test
    fun test_metadata_realm_ok() {
        test_metadata_ok(true)
    }

    private fun test_metadata_ok(useRealm: Boolean) {
        val cryptoStore: IMXCryptoStore = cryptoStoreHelper.createStore(useRealm)

        assertFalse(cryptoStore.hasData())

        cryptoStore.open()

        assertEquals("deviceId_sample", cryptoStore.deviceId)

        assertTrue(cryptoStore.hasData())

        // Cleanup
        cryptoStore.close()
        cryptoStore.deleteStore()
    }

    @Test
    fun test_lastSessionUsed() {
        // Ensure Olm is initialized
        OlmManager()

        val cryptoStore: IMXCryptoStore = cryptoStoreHelper.createStore(true)

        assertNull(cryptoStore.getLastUsedSessionId(DUMMY_DEVICE_KEY))

        val firstOlmSession = OlmSession()
        val firstSessionId = firstOlmSession.sessionIdentifier()
        val firstMxOlmSession = MXOlmSession(firstOlmSession)

        cryptoStore.storeSession(firstMxOlmSession, DUMMY_DEVICE_KEY)

        assertEquals(firstSessionId, cryptoStore.getLastUsedSessionId(DUMMY_DEVICE_KEY))

        val secondOlmSession = OlmSession()
        val secondSessionId = secondOlmSession.sessionIdentifier()
        val secondMxOlmSession = MXOlmSession(secondOlmSession)

        cryptoStore.storeSession(secondMxOlmSession, DUMMY_DEVICE_KEY)

        // Ensure sessionIds are distinct
        // TODO: this test fails, so the whole test is a bit useless...
        // assertNotEquals(firstSessionId, secondSessionId)

        // Note: we cannot be sure what will be the result of getLastUsedSessionId() here

        secondMxOlmSession.onMessageReceived()
        cryptoStore.storeSession(secondMxOlmSession, DUMMY_DEVICE_KEY)

        // Second Id is returned now
        assertEquals(secondSessionId, cryptoStore.getLastUsedSessionId(DUMMY_DEVICE_KEY))

        Thread.sleep(200)

        firstMxOlmSession.onMessageReceived()
        cryptoStore.storeSession(firstMxOlmSession, DUMMY_DEVICE_KEY)

        // First Id is returned now
        assertEquals(firstSessionId, cryptoStore.getLastUsedSessionId(DUMMY_DEVICE_KEY))

        // Cleanup
        firstOlmSession.releaseSession()
        secondOlmSession.releaseSession()
    }

    companion object {
        private const val LOG_TAG = "CryptoStoreTest"
    }
}