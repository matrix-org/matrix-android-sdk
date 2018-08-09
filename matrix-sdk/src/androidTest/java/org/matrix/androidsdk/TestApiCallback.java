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

package org.matrix.androidsdk;

import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;

import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.util.Log;

import java.util.concurrent.CountDownLatch;

/**
 * Simple implementation of ApiCallback, which count down the CountDownLatch on each API callback
 *
 * @param <T>
 */
class TestApiCallback<T> implements ApiCallback<T> {

    @NonNull
    private final CountDownLatch mCountDownLatch;

    TestApiCallback(@NonNull CountDownLatch countDownLatch) {
        mCountDownLatch = countDownLatch;
    }

    @CallSuper
    @Override
    public void onSuccess(T info) {
        mCountDownLatch.countDown();
    }

    @CallSuper
    @Override
    public void onNetworkError(Exception e) {
        Log.e("TestApiCallback", e.getMessage(), e);

        mCountDownLatch.countDown();
    }

    @CallSuper
    @Override
    public void onMatrixError(MatrixError e) {
        Log.e("TestApiCallback", e.getMessage() + " " + e.errcode);

        mCountDownLatch.countDown();
    }

    @CallSuper
    @Override
    public void onUnexpectedError(Exception e) {
        Log.e("TestApiCallback", e.getMessage(), e);

        mCountDownLatch.countDown();
    }
}
