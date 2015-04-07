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

import android.util.Log;

import org.matrix.androidsdk.listeners.IMXNetworkEventListener;
import org.matrix.androidsdk.network.NetworkConnectivityReceiver;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.util.UnsentEventsManager;

import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;

public class RestAdapterCallback<T> implements Callback<T> {

    private static final String LOG_TAG = "RestAdapterCallback";

    /**
     * Callback when a request failed after a network error.
     * This callback should manage the request auto resent.
     */
    public interface RequestRetryCallBack {
        public void onRetry();
    }

    private String mEventDescription;
    private ApiCallback mApiCallback;
    private RequestRetryCallBack mRequestRetryCallBack;
    private UnsentEventsManager mUnsentEventsManager;


    public RestAdapterCallback(ApiCallback apiCallback) {
        this.mApiCallback = apiCallback;
        this.mRequestRetryCallBack = null;
        this.mUnsentEventsManager = null;
    }

    public RestAdapterCallback(String description, UnsentEventsManager unsentEventsManager, ApiCallback apiCallback, RequestRetryCallBack requestRetryCallBack)  {
        if (null != description) {
            Log.d(LOG_TAG, "Trigger the event [" + description + "]");
        }

        this.mEventDescription = description;
        this.mApiCallback = apiCallback;
        this.mRequestRetryCallBack = requestRetryCallBack;
        this.mUnsentEventsManager = unsentEventsManager;
    }

    @Override
    public void success(T t, Response response) {
        if (null != mEventDescription) {
            Log.d(LOG_TAG, "Succeed : [" + mEventDescription + "]");
        }

        // add try catch to prevent application crashes while managing destroyed object
        try {
            if (null != mUnsentEventsManager) {
                mUnsentEventsManager.onEventSent(mApiCallback);
            }
            mApiCallback.onSuccess(t);
        } catch (Exception e) {
            Log.e(LOG_TAG, "Exception success " + e.getMessage() + " while managing " + response.getUrl());
        }
    }

    /**
     * Default failure implementation that calls the right error handler
     * @param error
     */
    @Override
    public void failure(RetrofitError error) {
        if (null != mEventDescription) {
            Log.d(LOG_TAG, "Failed : [" + mEventDescription + "]");
        }

        if (null != mUnsentEventsManager) {
            Log.d(LOG_TAG, "Add it to the UnsentEventsManager");
            mUnsentEventsManager.onEventSendingFailed(mEventDescription, error, mApiCallback, mRequestRetryCallBack);
        } else {
            if (error.isNetworkError()) {
                try {
                    mApiCallback.onNetworkError(error);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "Exception NetworkError " + e.getMessage() + " while managing " + error.getUrl());
                }
            }
            else {
                // Try to convert this into a Matrix error
                MatrixError mxError;
                try {
                    mxError = (MatrixError) error.getBodyAs(MatrixError.class);
                }
                catch (Exception e) {
                    mxError = null;
                }
                if (mxError != null) {
                    try {
                        mApiCallback.onMatrixError(mxError);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "Exception MatrixError " + e.getMessage() + " while managing " + error.getUrl());
                    }
                }
                else {
                    try {
                        mApiCallback.onUnexpectedError(error);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "Exception UnexpectedError " + e.getMessage() + " while managing " + error.getUrl());
                    }
                }
            }
        }
    }
}
