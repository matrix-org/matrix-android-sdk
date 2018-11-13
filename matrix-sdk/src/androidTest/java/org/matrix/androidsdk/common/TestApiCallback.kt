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

import android.support.annotation.CallSuper
import org.junit.Assert.fail
import org.matrix.androidsdk.rest.callback.ApiCallback
import org.matrix.androidsdk.rest.model.MatrixError
import org.matrix.androidsdk.util.Log
import java.util.concurrent.CountDownLatch

/**
 * Simple implementation of ApiCallback, which count down the CountDownLatch on each API callback
 * @param onlySuccessful true to fail if an error occurs. This is the default behavior
 * @param <T>
 */
open class TestApiCallback<T> @JvmOverloads constructor(private val countDownLatch: CountDownLatch,
                                                        private val onlySuccessful: Boolean = true) : ApiCallback<T> {

    @CallSuper
    override fun onSuccess(info: T) {
        countDownLatch.countDown()
    }

    @CallSuper
    override fun onNetworkError(e: Exception) {
        Log.e("TestApiCallback", e.message, e)

        if (onlySuccessful) {
            fail("onNetworkError " + e.localizedMessage)
        }

        countDownLatch.countDown()
    }

    @CallSuper
    override fun onMatrixError(e: MatrixError) {
        Log.e("TestApiCallback", e.message + " " + e.errcode)

        if (onlySuccessful) {
            fail("onMatrixError " + e.localizedMessage)
        }

        countDownLatch.countDown()
    }

    @CallSuper
    override fun onUnexpectedError(e: Exception) {
        Log.e("TestApiCallback", e.message, e)

        if (onlySuccessful) {
            fail("onUnexpectedError " + e.localizedMessage)
        }

        countDownLatch.countDown()
    }
}
