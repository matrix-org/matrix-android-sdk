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

import org.junit.Assert.assertEquals
import org.junit.Test

class HelperTest {

    @Test
    fun testHash_ok() {
        assertEquals("e9ee13b1ba2afc0825f4e556114785dd", "alice_15428931567802abf5ba7-d685-4333-af47-d38417ab3724:localhost:8480".hash())
    }

    @Test
    fun testHash_size_ok() {
        // Any String will have a 32 char hash
        for (i in 1..100) {
            assertEquals(32, "a".repeat(i).hash().length)
        }
    }
}
