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
package org.matrix.androidsdk.common

import okhttp3.*

class MockOkHttpInterceptor : Interceptor {

    private var rules: ArrayList<Rule> = ArrayList()


    fun addRule(rule: Rule) {
        rules.add(rule)
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val iterator = rules.iterator()
        val originalRequest = chain.request()
        while (iterator.hasNext()) {
            val next = iterator.next()
            if (originalRequest.url().toString().contains(next.match)) {
                return next.process(originalRequest)
            }
        }
        return chain.proceed(originalRequest)
    }


    abstract class Rule(val match: String) {
        abstract fun process(originalRequest: Request): Response
    }

    class SimpleRule(match: String, val code: Int, val body: String? = null) : Rule(match) {

        override fun process(originalRequest: Request): Response {
            return Response.Builder()
                    .protocol(Protocol.HTTP_1_1)
                    .request(originalRequest)
                    .message("mocked answer")
                    .body(ResponseBody.create(null, body ?: "{}"))
                    .code(code).build()
        }

    }
}