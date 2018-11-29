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
import org.matrix.androidsdk.data.cryptostore.IMXCryptoStore

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


    companion object {
        private const val LOG_TAG = "CryptoStoreTest"
    }
}