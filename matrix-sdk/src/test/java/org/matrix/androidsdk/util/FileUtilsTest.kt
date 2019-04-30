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

package org.matrix.androidsdk.util

import android.webkit.MimeTypeMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class FileUtilsTest {

    @Test
    fun getFileExtension_nominalCase() {
        assertEquals("jpg", getFileExtension("test.jpg"))
        assertEquals("jpg", getFileExtension("test.Jpg"))
        assertEquals("jpg", getFileExtension("test.JPG"))
        assertEquals("jpg", getFileExtension("test.foo.JPG"))
        assertEquals("jpg", getFileExtension("https://example.org/test.jpg"))
        assertEquals("jpg", getFileExtension("https://example.org/test.jpg#fragment.bar"))
        assertEquals("jpg", getFileExtension("https://example.org/test.jpg?param=x.foo"))
        assertEquals("jpg", getFileExtension("https://example.org/test.jpg?param=x.foo#fragment.bar"))
    }

    @Test
    fun getFileExtension_errorCase() {
        assertNull(getFileExtension(""))
        assertNull(getFileExtension("."))
        assertNull(getFileExtension("test."))
        assertNull(getFileExtension("test.foo."))
        assertNull(getFileExtension("https://example.org/test"))
        assertNull(getFileExtension("https://example.org/test#fragment.bar"))
        assertNull(getFileExtension("https://example.org/test?param=x.foo"))
        assertNull(getFileExtension("https://example.org/test?param=x.foo#fragment.bar"))
    }

    @Test
    fun getFileExtension_MimeTypeMap_issue() {
        // These are the problems
        // "ı" (i without point) in file name (like in Turkish)
        assertEquals("", MimeTypeMap.getFileExtensionFromUrl("test_ı.jpg"))
        // "+" in file name
        assertEquals("", MimeTypeMap.getFileExtensionFromUrl("test_+1.jpg"))

        // Now fixed
        assertEquals("jpg", getFileExtension("test_ı.jpg"))
        assertEquals("jpg", getFileExtension("test_+1.jpg"))
    }
}
