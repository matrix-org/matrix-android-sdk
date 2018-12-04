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

package org.matrix.androidsdk.gson

import com.google.gson.annotations.SerializedName
import org.junit.Assert.assertEquals
import org.junit.Test
import org.matrix.androidsdk.util.JsonUtils

class GsonTest {

    data class Data(val toto: String)

    @Test
    fun testSerialization() {
        assertEquals("{\"toto\":\"value\"}", JsonUtils.getBasicGson().toJson(Data("value")))
    }

    data class DataWithAnnotation(@SerializedName("titi") val toto: String)

    @Test
    fun testSerializationWithAnnotation() {
        assertEquals("{\"titi\":\"value\"}", JsonUtils.getBasicGson().toJson(DataWithAnnotation("value")))
    }
}