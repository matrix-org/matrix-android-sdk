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

import android.app.Activity;
import android.content.Context;
import android.media.MediaActionSound;
import android.view.View;
import android.widget.Toast;

import org.matrix.androidsdk.R;
import org.matrix.androidsdk.rest.model.MatrixError;

/**
 * A stub implementation of {@link ApiCallback} which only chosen callbacks
 * can be implemented.
 */
public class SimpleApiCallback<T> implements ApiCallback<T> {

    private static final String LOG_TAG = "SimpleApiCallback";

    private Activity mActivity;

    private Context mContext = null;
    private View mPostView = null;

    /**
     * Failure callback to pass on failures to.
     */
    private ApiFailureCallback failureCallback = null;

    /**
     * Constructor
     */
    public SimpleApiCallback() {
    }

    /**
     * Constructor
     * @param activity The context.
     */
    public SimpleApiCallback(Activity activity) {
        mActivity = activity;
    }

    /**
     * Constructor
     * @param context The context.
     */
    public SimpleApiCallback(Context context, View postOnView) {
        mContext = context;
        mPostView = postOnView;
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

    private void displayToast(final String message) {
        if (null != mActivity) {
            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(mActivity, message, Toast.LENGTH_SHORT).show();
                }
            });
        } else if ((null != mContext) && (null != mPostView)) {
            mPostView.post(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(mContext, message, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    @Override
    public void onNetworkError(Exception e) {
        if (failureCallback != null) {
            try {
                failureCallback.onNetworkError(e);
            }catch (Exception exception) {
            }
        } else {
            displayToast("Network Error");
        }
    }

    @Override
    public void onMatrixError(final MatrixError e) {
        if (failureCallback != null) {
            try {
                failureCallback.onMatrixError(e);
            } catch (Exception exception) {
            }
        } else {
            displayToast("Matrix Error : " + e.error);
        }
    }

    @Override
    public void onUnexpectedError(final Exception e) {
        if (failureCallback != null) {
            try {
                failureCallback.onUnexpectedError(e);
            } catch (Exception exception) {
            }
        } else {
            displayToast(e.getLocalizedMessage());
        }
    }
}
