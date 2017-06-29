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

package org.matrix.androidsdk.util;

import android.content.Context;
import android.text.TextUtils;
import org.matrix.androidsdk.util.Log;

import org.matrix.androidsdk.MXDataHandler;
import org.matrix.androidsdk.listeners.IMXNetworkEventListener;
import org.matrix.androidsdk.network.NetworkConnectivityReceiver;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.RestAdapterCallback;
import org.matrix.androidsdk.rest.model.MatrixError;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import retrofit2.Response;


/**
 * unsent matrix events manager
 * This manager schedules the unsent events sending.
 * 1 - it keeps the unsent events order (i.e. wait that the first event is resent before sending the second one)
 * 2 - Apply the retry rules (event time life, 3 tries...)
 */
public class UnsentEventsManager {

    private static final String LOG_TAG = "UnsentEventsManager";

    // 3 minutes
    private static final int MAX_MESSAGE_LIFETIME_MS = 180000;

    // perform only MAX_RETRIES retries
    private static final int MAX_RETRIES = 4;

    // The jitter value to apply to compute a random retry time.
    private static final int RETRY_JITTER_MS = 3000;

    // the network receiver
    private final NetworkConnectivityReceiver mNetworkConnectivityReceiver;
    // faster way to check if the event is already sent
    private final HashMap<Object, UnsentEventSnapshot> mUnsentEventsMap = new HashMap<>();
    // get the sending order
    private final ArrayList<UnsentEventSnapshot> mUnsentEvents = new ArrayList<>();
    // true of the device is connected to a data network
    private boolean mbIsConnected = false;

    // matrix error management
    private final MXDataHandler mDataHandler;

    /**
     * storage class
     */
    private class UnsentEventSnapshot {
        // first time the message has been sent
        // -1 to ignore age test
        private long mAge;
        // the number of retries
        // it should be limited
        private int mRetryCount;
        // retry callback.
        private RestAdapterCallback.RequestRetryCallBack mRequestRetryCallBack;
        // retry timer
        private Timer mAutoResendTimer = null;

        public Timer mLifeTimeTimer = null;
        // the retry is in progress
        public boolean mIsResending = false;
        // human description of the event
        // The snapshot creator can hide some fields
        public String mEventDescription = null;

        /**
         *
         */
        public boolean waitToBeResent() {
            return (null != mAutoResendTimer);
        }

        /**
         * Resend the event after a delay.
         * @param delayMs the delay in milliseconds.
         */
        public void resendEventAfter(int delayMs) {
            stopTimer();

            if (null != mEventDescription) {
                Log.d(LOG_TAG, "Resend after " + delayMs + " [" +  mEventDescription + "]");
            }

            mAutoResendTimer = new Timer();
            mAutoResendTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        UnsentEventSnapshot.this.mIsResending = true;

                        if (null != mEventDescription) {
                            Log.d(LOG_TAG, "Resend [" +  mEventDescription + "]");
                        }

                        mRequestRetryCallBack.onRetry();
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "## resendEventAfter() : onRetry failed " + e.getMessage());
                    }
                }
            }, delayMs);
        }

        /**
         * Stop any pending resending timer.
         */
        public void stopTimer() {
            if (null != mAutoResendTimer) {
                mAutoResendTimer.cancel();
                mAutoResendTimer = null;
            }
        }

        /**
         * Stop timers.
         */
        public void stopTimers() {
            if (null != mAutoResendTimer) {
                mAutoResendTimer.cancel();
                mAutoResendTimer = null;
            }

            if (null != mLifeTimeTimer) {
                mLifeTimeTimer.cancel();
                mLifeTimeTimer = null;
            }
        }
    }

    /**
     * Constructor
     * @param networkConnectivityReceiver the network received
     * @param dataHandler the data handler
     */
    public UnsentEventsManager(NetworkConnectivityReceiver networkConnectivityReceiver, MXDataHandler dataHandler) {
        mNetworkConnectivityReceiver = networkConnectivityReceiver;

        // add a default listener
        // to resend the unsent messages
        mNetworkConnectivityReceiver.addEventListener(new IMXNetworkEventListener() {
            @Override
            public void onNetworkConnectionUpdate(boolean isConnected) {
                mbIsConnected = isConnected;

                if (isConnected) {
                    resentUnsents();
                }
            }
        });

        mbIsConnected = mNetworkConnectivityReceiver.isConnected();

        mDataHandler = dataHandler;
    }

    /**
     * Warn that the apiCallback has been called
     * @param apiCallback the called apiCallback
     */
    public void onEventSent(ApiCallback apiCallback) {
        if (null != apiCallback) {
            UnsentEventSnapshot snapshot = null;

            synchronized (mUnsentEventsMap) {
                if (mUnsentEventsMap.containsKey(apiCallback)) {
                    snapshot = mUnsentEventsMap.get(apiCallback);
                }
            }

            if (null != snapshot) {
                if (null != snapshot.mEventDescription) {
                    Log.d(LOG_TAG, "Resend Succeeded [" +  snapshot.mEventDescription + "]");
                }

                snapshot.stopTimers();

                synchronized (mUnsentEventsMap) {
                    mUnsentEventsMap.remove(apiCallback);
                    mUnsentEvents.remove(snapshot);
                }

                resentUnsents();
            }
        }
    }

    /**
     * Clear the session data
     */
    public void clear() {
        synchronized (mUnsentEventsMap) {
            for(UnsentEventSnapshot snapshot : mUnsentEvents) {
                snapshot.stopTimers();
            }

            mUnsentEvents.clear();
            mUnsentEventsMap.clear();
        }
    }

    /**
     * @return the network connectivity receiver
     */
    public NetworkConnectivityReceiver getNetworkConnectivityReceiver() {
        return mNetworkConnectivityReceiver;
    }

    /**
     * @return the context
     */
    public Context getContext() {
        return mDataHandler.getStore().getContext();
    }

    public MXDataHandler getDataHandler() {
        return mDataHandler;
    }

    /**
     * The event failed to be sent and cannot be resent.
     * It triggers the error callbacks.
     * @param eventDescription the event description
     * @param exception the exception
     * @param callback the callback.
     */
    private static void triggerErrorCallback(MXDataHandler dataHandler, String eventDescription, Response response, Exception exception, ApiCallback callback) {
        if ((null != exception) && !TextUtils.isEmpty(exception.getMessage())) {
            // privacy
            //Log.e(LOG_TAG, error.getMessage() + " url=" + error.getUrl());
            Log.e(LOG_TAG, exception.getLocalizedMessage());
        }

        if (null == exception) {
            try {
                if (null != eventDescription) {
                    Log.e(LOG_TAG, "Unexpected Error " + eventDescription);
                }
                if (null != callback) {
                    callback.onUnexpectedError(null);
                }
            } catch (Exception e) {
                // privacy
                //Log.e(LOG_TAG, "Exception UnexpectedError " + e.getMessage() + " while managing " + error.getUrl());
                Log.e(LOG_TAG, "Exception UnexpectedError " + e.getLocalizedMessage());
            }
        }
        else if (exception instanceof IOException) {
            try {
                if (null != eventDescription) {
                    Log.e(LOG_TAG, "Network Error " + eventDescription);
                }
                if (null != callback) {
                    callback.onNetworkError((Exception) exception);
                }
            } catch (Exception e) {
                // privacy
                //Log.e(LOG_TAG, "Exception NetworkError " + e.getMessage() + " while managing " + error.getUrl());
                Log.e(LOG_TAG, "Exception NetworkError " + e.getLocalizedMessage());
            }
        }
        else {
            // Try to convert this into a Matrix error
            MatrixError mxError;
            try {
                mxError = JsonUtils.getGson(false).fromJson(response.errorBody().string(), MatrixError.class);
            }
            catch (Exception e) {
                mxError = null;
            }
            if (mxError != null) {
                try {
                    if (null != eventDescription) {
                        Log.e(LOG_TAG, "Matrix Error " + mxError + " " + eventDescription);
                    }

                    if (TextUtils.equals(MatrixError.UNKNOWN_TOKEN, mxError.errcode)) {
                        dataHandler.onInvalidToken();
                    } else if (null != callback) {
                        callback.onMatrixError(mxError);
                    }

                } catch (Exception e) {
                    // privacy
                    //Log.e(LOG_TAG, "Exception MatrixError " + e.getMessage() + " while managing " + error.getUrl());
                    Log.e(LOG_TAG, "Exception MatrixError " + e.getLocalizedMessage());
                }
            }
            else {
                try {
                    if (null != eventDescription) {
                        Log.e(LOG_TAG, "Unexpected Error " + eventDescription);
                    }

                    if (null != callback) {
                        callback.onUnexpectedError(exception);
                    }
                } catch (Exception e) {
                    // privacy
                    //Log.e(LOG_TAG, "Exception UnexpectedError " + e.getMessage() + " while managing " + error.getUrl());
                    Log.e(LOG_TAG, "Exception UnexpectedError " + e.getLocalizedMessage());
                }
            }
        }
    }

    /**
     * warns that an event failed to be sent.
     * @param eventDescription the event description
     * @param ignoreEventTimeLifeInOffline tell if the event timelife is ignored in offline mode
     * @param apiCallback the apiCallback.
     * @param requestRetryCallBack requestRetryCallBack.
     */
    public void onEventSendingFailed(final String eventDescription, final boolean ignoreEventTimeLifeInOffline, final Response response, final Exception exception, final ApiCallback apiCallback, final RestAdapterCallback.RequestRetryCallBack requestRetryCallBack) {
        boolean isManaged = false;

        if (null != eventDescription) {
            Log.d(LOG_TAG, "Fail to send [" +  eventDescription + "]");
        }

        if ((null != requestRetryCallBack) && (null != apiCallback)) {
            synchronized (mUnsentEventsMap) {
                UnsentEventSnapshot snapshot;

                // Try to convert this into a Matrix error
                MatrixError mxError = null;

                if (null != response) {
                    try {
                        mxError = JsonUtils.getGson(false).fromJson(response.errorBody().string(), MatrixError.class);
                    } catch (Exception e) {
                        mxError = null;
                    }
                }

                // trace the matrix error.
                if ((null != eventDescription) && (null != mxError)) {
                    Log.d(LOG_TAG, "Matrix error " + mxError.errcode + " " + mxError.getLocalizedMessage() + " [" +  eventDescription + "]");
                }

                int matrixRetryTimeout = -1;

                if ((null != mxError) &&  MatrixError.LIMIT_EXCEEDED.equals(mxError.errcode) && (null != mxError.retry_after_ms)) {
                    matrixRetryTimeout = mxError.retry_after_ms + 200;
                }

                // some matrix errors are not trapped.
                if ((null == mxError) || !mxError.isSupportedErrorCode() || MatrixError.LIMIT_EXCEEDED.equals(mxError.errcode)) {
                    // is it the first time that the event has been sent ?
                    if (mUnsentEventsMap.containsKey(apiCallback)) {
                        snapshot = mUnsentEventsMap.get(apiCallback);

                        snapshot.mIsResending = false;
                        snapshot.stopTimer();

                        // assume that LIMIT_EXCEEDED error is not a default retry
                        if (matrixRetryTimeout < 0) {
                            snapshot.mRetryCount++;
                        }

                        // any event has a time life to avoid very old messages
                        long timeLife = 0;

                        // age < 0 means that the event time life is ignored
                        if (snapshot.mAge > 0) {
                            timeLife = System.currentTimeMillis() - snapshot.mAge;
                        }

                        if ((timeLife > MAX_MESSAGE_LIFETIME_MS) || (snapshot.mRetryCount > MAX_RETRIES)) {
                            snapshot.stopTimers();
                            mUnsentEventsMap.remove(apiCallback);
                            mUnsentEvents.remove(snapshot);

                            if (null != eventDescription) {
                                Log.d(LOG_TAG, "Cancel [" +  eventDescription + "]");
                            }

                            isManaged = false;
                        } else {
                            isManaged = true;
                        }
                    } else {
                        snapshot = new UnsentEventSnapshot();

                        snapshot.mAge = ignoreEventTimeLifeInOffline ? -1 : System.currentTimeMillis();
                        snapshot.mRequestRetryCallBack = requestRetryCallBack;
                        snapshot.mRetryCount = 1;
                        snapshot.mEventDescription = eventDescription;
                        mUnsentEventsMap.put(apiCallback, snapshot);
                        mUnsentEvents.add(snapshot);

                        if (mbIsConnected || !ignoreEventTimeLifeInOffline) {
                            // the event has a life time
                            final UnsentEventSnapshot fSnapshot = snapshot;
                            fSnapshot.mLifeTimeTimer = new Timer();
                            fSnapshot.mLifeTimeTimer.schedule(new TimerTask() {
                                @Override
                                public void run() {
                                    try {

                                        if (null != eventDescription) {
                                            Log.d(LOG_TAG, "Cancel to send [" + eventDescription + "]");
                                        }

                                        fSnapshot.stopTimers();
                                        synchronized (mUnsentEventsMap) {
                                            mUnsentEventsMap.remove(apiCallback);
                                            mUnsentEvents.remove(fSnapshot);
                                        }

                                        triggerErrorCallback(mDataHandler, eventDescription, response, exception, apiCallback);
                                    } catch (Exception e) {
                                        Log.e(LOG_TAG, "## onEventSendingFailed() : failure Msg=" + e.getMessage());
                                    }
                                }
                            }, MAX_MESSAGE_LIFETIME_MS);
                        } else if (ignoreEventTimeLifeInOffline) {
                            Log.d(LOG_TAG, "The request " + eventDescription + " will be sent when a network will be available");
                        }

                        isManaged = true;
                    }

                    // retry to send the message ?
                    if (isManaged) {
                        // resend the event only if there is an available network
                        // retrofitError.isNetworkError() does not provide a valid description of the failure
                        // 1- there is no available network / the connection is lost. (what we could expect)
                        // 2- the server did not response after 15s : the client would wrongly behave, it would wait until to switch to a valid network
                        //    It never happens, so the message is never resent.
                        //
                        if (mbIsConnected) {
                            int jitterTime = ((int)Math.pow(2, snapshot.mRetryCount)) + (Math.abs(new Random(System.currentTimeMillis()).nextInt()) % RETRY_JITTER_MS);
                            snapshot.resendEventAfter((matrixRetryTimeout > 0) ? matrixRetryTimeout : jitterTime);
                        }
                    }
                }
            }
        }

        if (!isManaged) {
            Log.d(LOG_TAG, "Cannot resend it");
            triggerErrorCallback(mDataHandler, eventDescription, response, exception, apiCallback);
        }
    }

    /**
     * check if some messages must be resent
     */
    private void resentUnsents() {
        Log.d(LOG_TAG, "resentUnsents");

        synchronized (mUnsentEventsMap) {
            if (mUnsentEvents.size() > 0) {
                try {
                    // retry the first
                    for(int index = 0; index < mUnsentEvents.size(); index++) {
                        UnsentEventSnapshot unsentEventSnapshot = mUnsentEvents.get(index);

                        // check if there is no required delay to resend the message
                        if (!unsentEventSnapshot.waitToBeResent()) {
                            // if the message is already resending,
                            if (unsentEventSnapshot.mIsResending) {
                                // do not resend any other one to try to keep the messages sending order.
                                return;
                            } else {
                                if (null != unsentEventSnapshot.mEventDescription) {
                                    Log.d(LOG_TAG, "Automatically resend " + unsentEventSnapshot.mEventDescription);
                                }

                                unsentEventSnapshot.mIsResending = true;
                                unsentEventSnapshot.mRequestRetryCallBack.onRetry();
                            }
                            break;
                        }
                    }
                } catch (Exception e) {
                    Log.e(LOG_TAG, "## resentUnsents() : failure Msg=" + e.getMessage());
                }
            }
        }
    }
}
