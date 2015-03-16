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

import android.content.Context;
import android.widget.Toast;

import org.matrix.androidsdk.R;
import org.matrix.androidsdk.rest.model.MatrixError;

/**
 * A stub implementation of {@link ApiCallback} which only chosen callbacks
 * can be implemented.
 */
public class SimpleApiCallback<T> implements ApiCallback<T> {

    private static final String LOG_TAG = "SimpleApiCallback";

    private Context mContext = null;

    /**
     * Failure callback to pass on failures to.
     */
    private ApiFailureCallback failureCallback;

    /**
     * Constructor
     * @param context The context.
     */
    public SimpleApiCallback(Context context) {
        mContext = context;
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
            try {
                ((ApiCallback) failureCallback).onSuccess(info);
            }  catch (Exception exception) {
            }
        }
    }

    @Override
    public void onNetworkError(Exception e) {
        if (failureCallback != null) {
            try {
                failureCallback.onNetworkError(e);
            }catch (Exception exception) {
            }
        } else if (null != mContext) {
            Toast.makeText(mContext, "Network Error", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onMatrixError(MatrixError e) {
        if (failureCallback != null) {
            try {
                failureCallback.onMatrixError(e);
            } catch (Exception exception) {
            }
        } else if (null != mContext) {
            Toast.makeText(mContext, "Matrix Error : " + e.error, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onUnexpectedError(Exception e) {
        if (failureCallback != null) {
            try {
                failureCallback.onUnexpectedError(e);
            } catch (Exception exception) {
            }
        } else if (null != mContext) {
            Toast.makeText(mContext, e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
