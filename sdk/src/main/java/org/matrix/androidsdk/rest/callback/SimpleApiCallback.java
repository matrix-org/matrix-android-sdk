/* 
 * Copyright 2014 OpenMarket Ltd
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
package org.matrix.androidsdk.rest.callback;

import org.matrix.androidsdk.rest.model.MatrixError;

/**
 * A stub implementation of {@link ApiCallback} which only chosen callbacks
 * can be implemented.
 */
public class SimpleApiCallback<T> implements ApiCallback<T> {

    private static final String LOG_TAG = "SimpleApiCallback";

    /**
     * Failure callback to pass on failures to.
     */
    private ApiFailureCallback failureCallback;

    /**
     * Default constructor.
     */
    public SimpleApiCallback() {
    }

    /**
     * Constructor to delegate failure callback to another object. This allows us to stack failure callback implementations
     * in a decorator-type approach.
     * @param failureCallback the failure callback implementation to delegate to
     */
    public SimpleApiCallback(ApiFailureCallback failureCallback) {
        this.failureCallback = failureCallback;
    }

    @Override
    public void onSuccess(T info) {
        // If the delegate has an onSuccess implementation, use it
        if (failureCallback instanceof ApiCallback) {
            ((ApiCallback) failureCallback).onSuccess(info);
        }
    }

    @Override
    public void onNetworkError(Exception e) {
        if (failureCallback != null) {
            failureCallback.onNetworkError(e);
        }
    }

    @Override
    public void onMatrixError(MatrixError e) {
        if (failureCallback != null) {
            failureCallback.onMatrixError(e);
        }
    }

    @Override
    public void onUnexpectedError(Exception e) {
        if (failureCallback != null) {
            failureCallback.onUnexpectedError(e);
        }
    }
}
