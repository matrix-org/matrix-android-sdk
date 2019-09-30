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

package org.matrix.androidsdk.core

/**
 * Convert a string to an UTF8 String
 *
 * @param s the string to convert
 * @return the utf-8 string
 */
fun convertToUTF8(s: String): String? {
    var out: String? = s

    if (null != out) {
        try {
            val bytes = out.toByteArray(charset("UTF-8"))
            out = String(bytes)
        } catch (e: Exception) {
            Log.e("Util", "## convertToUTF8()  failed " + e.message, e)
        }

    }

    return out
}

/**
 * Convert a string from an UTF8 String
 *
 * @param s the string to convert
 * @return the utf-16 string
 */
fun convertFromUTF8(s: String): String? {
    var out: String? = s

    if (null != out) {
        try {
            val bytes = out.toByteArray()
            out = String(bytes, charset("UTF-8"))
        } catch (e: Exception) {
            Log.e("Util", "## convertFromUTF8()  failed " + e.message, e)
        }

    }

    return out
}


/**
 * Base64 URL conversion methods
 */

fun base64UrlToBase64(base64Url: String?): String? {
    return base64Url
            ?.replace("-".toRegex(), "+")
            ?.replace("_".toRegex(), "/")
}

fun base64ToBase64Url(base64: String?): String? {
    return base64
            ?.replace("\n".toRegex(), "")
            ?.replace("\\+".toRegex(), "-")
            ?.replace("/".toRegex(), "_")
            ?.replace("=".toRegex(), "")
}

fun base64ToUnpaddedBase64(base64: String?): String? {
    return base64
            ?.replace("\n".toRegex(), "")
            ?.replace("=".toRegex(), "")
}