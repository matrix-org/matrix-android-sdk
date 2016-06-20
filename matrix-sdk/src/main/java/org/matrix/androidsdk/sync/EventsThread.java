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
package org.matrix.androidsdk.sync;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import org.matrix.androidsdk.listeners.IMXNetworkEventListener;
import org.matrix.androidsdk.network.NetworkConnectivityReceiver;
import org.matrix.androidsdk.rest.callback.ApiFailureCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.client.EventsRestClient;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.Sync.SyncResponse;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;

/**
 * Thread that continually watches the event stream and sends events to its listener.
 */
public class EventsThread extends Thread {
    private static final String LOG_TAG = "EventsThread";

    private static final int RETRY_WAIT_TIME_MS = 10000;

    private static final int DEFAULT_SERVER_TIMEOUT_MS = 30000;
    private static final int DEFAULT_CLIENT_TIMEOUT_MS = 120000;

    private EventsRestClient mEventsRestClient = null;

    private EventsThreadListener mListener = null;
    private String mCurrentToken = null;

    private boolean mInitialSyncDone = false;
    private boolean mPaused = true;
    private boolean mIsNetworkSuspended = false;
    private boolean mIsCatchingUp = false;
    private boolean mIsOnline = true;

    private boolean mKilling = false;

    private int mServerTimeoutms = DEFAULT_SERVER_TIMEOUT_MS;

    // add a delay between two sync requests
    private int mRequestDelayMs = 0;
    private Timer mSyncDelayTimer = null;

    // avoid sync on "this" because it might differ if there is a timer.
    private Object mSyncObject = new Object();

    // Custom Retrofit error callback that will convert Retrofit errors into our own error callback
    private ApiFailureCallback mFailureCallback;

    // avoid restarting the listener if there is no network.
    // wait that there is an available network.
    private NetworkConnectivityReceiver mNetworkConnectivityReceiver;
    private boolean mbIsConnected = true;
    IMXNetworkEventListener mNetworkListener = new IMXNetworkEventListener() {
        @Override
        public void onNetworkConnectionUpdate(boolean isConnected) {
            synchronized (mSyncObject) {
                mbIsConnected = isConnected;
            }

            // the thread has been suspended and there is an available network
            if (isConnected && !mKilling) {
                onNetworkAvailable();
            }
        }
    };

    /**
     * Default constructor.
     * @param apiClient API client to make the events API calls
     * @param listener a listener to inform
     * @param initialToken the sync initial token.
     */
    public EventsThread(EventsRestClient apiClient, EventsThreadListener listener, String initialToken) {
        super("Events thread");
        mEventsRestClient = apiClient;
        mListener = listener;
        mCurrentToken = initialToken;
    }

    /**
     * Update the long poll timeout.
     * @param ms the timeout in ms
     */
    public void setServerLongPollTimeout(int ms) {
        mServerTimeoutms = Math.max(ms, DEFAULT_SERVER_TIMEOUT_MS);
    }

    /**
     * @return the long poll timeout
     */
    public int getServerLongPollTimeout() {
        return mServerTimeoutms;
    }

    /**
     * Set a delay between two sync requests.
     * @param ms the delay in ms
     */
    public void setSyncDelay(int ms) {
        mRequestDelayMs = Math.max(0, ms);
    }

    /**
     * @return the delay between two sync requests.
     */
    public int getSyncDelay() {
        return mRequestDelayMs;
    }

    /**
     * Set the network connectivity listener.
     * It is used to avoid restarting the events threads each 10 seconds when there is no available network.
     * @param networkConnectivityReceiver the network receiver
     */
    public void setNetworkConnectivityReceiver(NetworkConnectivityReceiver networkConnectivityReceiver) {
        mNetworkConnectivityReceiver = networkConnectivityReceiver;
    }

    /**
     * Set the failure callback.
     * @param failureCallback
     */
    public void setFailureCallback(ApiFailureCallback failureCallback) {
        mFailureCallback = failureCallback;
    }

    /**
     * Pause the thread. It will resume where it left off when unpause()d.
     */
    public void pause() {
        Log.i(LOG_TAG, "pause()");
        mPaused = true;
        mIsCatchingUp = false;
    }

    public void onNetworkAvailable() {
        Log.i(LOG_TAG, "onNetWorkAvailable()");
        if (mIsNetworkSuspended) {
            mIsNetworkSuspended = false;

            if (mPaused) {
                Log.i(LOG_TAG, "the event thread is still suspended");
            } else {
                Log.i(LOG_TAG, "Resume the thread");
                // cancel any catchup process.
                mIsCatchingUp = false;

                synchronized (mSyncObject) {
                    mSyncObject.notify();
                }
            }
        } else {
            Log.i(LOG_TAG, "onNetWorkAvailable() : nothing to do");
        }
    }

    /**
     * Unpause the thread if it had previously been paused. If not, this does nothing.
     */
    public void unpause() {
        Log.i(LOG_TAG, "unpause()");
        if (mPaused) {
            mPaused = false;
            synchronized (mSyncObject) {
                mSyncObject.notify();
            }
        }

        // cancel any catchup process.
        mIsCatchingUp = false;
    }

    /**
     * Catchup until some events are retrieved.
     */
    public void catchup() {
        Log.d(LOG_TAG, "catchup()");
        if (mPaused) {
            mPaused = false;
            synchronized (mSyncObject) {
                mSyncObject.notify();
            }
        }

        mIsCatchingUp = true;
    }

    /**
     * Allow the thread to finish its current processing, then permanently stop.
     */
    public void kill() {
        Log.d(LOG_TAG, "killing ...");

        mKilling = true;

        if (mPaused) {
            mPaused = false;
            synchronized (mSyncObject) {
                mSyncObject.notify();
            }

            Log.d(LOG_TAG, "Resume the thread to kill it.");
        }
    }

    /**
     * Update the online status
     * @param isOnline true if the client must be seen as online
     */
    public void setIsOnline(boolean isOnline) {
        Log.d(LOG_TAG, "setIsOnline to " + isOnline);
        mIsOnline = isOnline;
    }

    @Override
    public void run() {
        startSync();
    }

    /**
     * Start the events sync
     */
    private void startSync() {
        if (null != mCurrentToken) {
            Log.d(LOG_TAG, "Resuming initial sync from " + mCurrentToken);
        } else {
            Log.d(LOG_TAG, "Requesting initial sync...");
        }

        int serverTimeout = 0;

        mPaused = false;

        //
        mInitialSyncDone = null != mCurrentToken;

        if (mInitialSyncDone) {
            // get the latest events asap
            serverTimeout = 0;
            // dummy initial sync
            // to hide the splash screen
            mListener.onSyncResponse(null, true);
        } else {

            // Start with initial sync
            while (!mInitialSyncDone) {
                final CountDownLatch latch = new CountDownLatch(1);

                mEventsRestClient.syncFromToken(null, 0, DEFAULT_CLIENT_TIMEOUT_MS, null, null, new SimpleApiCallback<SyncResponse>(mFailureCallback) {
                    @Override
                    public void onSuccess(SyncResponse syncResponse) {
                        Log.d(LOG_TAG, "Received initial sync response.");
                        mListener.onSyncResponse(syncResponse, true);
                        mCurrentToken = syncResponse.nextBatch;
                        mInitialSyncDone = true;
                        // unblock the events thread
                        latch.countDown();
                    }

                    private void sleepAndUnblock() {
                        Log.i(LOG_TAG, "Waiting a bit before retrying");
                        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                            public void run() {
                                latch.countDown();
                            }
                        }, RETRY_WAIT_TIME_MS);
                    }

                    @Override
                    public void onNetworkError(Exception e) {
                        if (null != mCurrentToken) {
                            onSuccess(null);
                        } else {
                            Log.e(LOG_TAG, "Sync V2 onNetworkError " + e.getLocalizedMessage());
                            super.onNetworkError(e);
                            sleepAndUnblock();
                        }
                    }

                    @Override
                    public void onMatrixError(MatrixError e) {
                        super.onMatrixError(e);

                        if (TextUtils.equals(MatrixError.UNKNOWN_TOKEN, e.errcode)) {
                            mListener.onInvalidToken();
                        } else {
                            sleepAndUnblock();
                        }
                    }

                    @Override
                    public void onUnexpectedError(Exception e) {
                        super.onUnexpectedError(e);
                        Log.e(LOG_TAG, "Sync V2 onUnexpectedError " + e.getLocalizedMessage());
                        sleepAndUnblock();
                    }
                });

                // block until the initial sync callback is invoked.
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    Log.e(LOG_TAG, "Interrupted whilst performing initial sync.");
                }
            }

            serverTimeout = mServerTimeoutms;
        }

        Log.d(LOG_TAG, "Starting event stream from token " + mCurrentToken);

        // sanity check
        if (null != mNetworkConnectivityReceiver) {
            mNetworkConnectivityReceiver.addEventListener(mNetworkListener);
            //
            mbIsConnected = mNetworkConnectivityReceiver.isConnected();
            mPaused = !mbIsConnected;
        }

        // Then repeatedly long-poll for events
        while (!mKilling) {

            // test if a delay between two syncs
            if ((!mPaused && !mIsNetworkSuspended) && (0 != mRequestDelayMs)) {
                mSyncDelayTimer = new Timer();

                mSyncDelayTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        Log.d(LOG_TAG, "start a sync after " + mRequestDelayMs + " ms");

                        synchronized (mSyncObject) {
                            mSyncObject.notify();
                        }
                    }
                }, mRequestDelayMs);
            }

            if (mPaused || mIsNetworkSuspended || (null != mSyncDelayTimer)) {
                if (null != mSyncDelayTimer) {
                    Log.d(LOG_TAG, "Event stream is paused because there is a timer delay.");
                } else if (mIsNetworkSuspended) {
                    Log.d(LOG_TAG, "Event stream is paused because there is no available network.");
                } else {
                    Log.d(LOG_TAG, "Event stream is paused. Waiting.");
                }

                try {
                    synchronized (mSyncObject) {
                        mSyncObject.wait();
                    }

                    if (null != mSyncDelayTimer) {
                        mSyncDelayTimer.cancel();
                        mSyncDelayTimer = null;
                    }

                    Log.d(LOG_TAG, "Event stream woken from pause.");

                    // perform a catchup asap
                    serverTimeout = 0;
                } catch (InterruptedException e) {
                    Log.e(LOG_TAG, "Unexpected interruption while paused: " + e.getMessage());
                }
            }

            // the service could have been killed while being paused.
            if (!mKilling) {
                String inlineFilter = null; //"{\"room\":{\"timeline\":{\"limit\":250}}}";

                final CountDownLatch latch = new CountDownLatch(1);

                Log.d(LOG_TAG, "Get events from token " + mCurrentToken);

                mEventsRestClient.syncFromToken(mCurrentToken, serverTimeout, DEFAULT_CLIENT_TIMEOUT_MS, (mIsCatchingUp && mIsOnline) ? "offline" : null, inlineFilter, new SimpleApiCallback<SyncResponse>(mFailureCallback) {
                    @Override
                    public void onSuccess(SyncResponse syncResponse) {
                        if (!mKilling) {
                            // the catchup request is done once.
                            if (mIsCatchingUp) {
                                Log.e(LOG_TAG, "Stop the catchup");
                                // stop any catch up
                                mIsCatchingUp = false;
                                mPaused = true;
                            }

                            Log.d(LOG_TAG, "Got event response");
                            mListener.onSyncResponse(syncResponse, false);
                            mCurrentToken = syncResponse.nextBatch;
                            Log.d(LOG_TAG, "mCurrentToken is now set to " + mCurrentToken);
                        }

                        // unblock the events thread
                        latch.countDown();
                    }

                    private void onError(String description) {
                        boolean isConnected;
                        Log.d(LOG_TAG, "Got an error while polling events " + description);

                        synchronized (mSyncObject) {
                            isConnected = mbIsConnected;
                        }

                        // detected if the device is connected before trying again
                        if (isConnected) {
                            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                                public void run() {
                                    latch.countDown();
                                }
                            }, RETRY_WAIT_TIME_MS);

                        } else {
                            // no network -> wait that a network connection comes back.
                            mIsNetworkSuspended = true;
                            latch.countDown();
                        }
                    }

                    @Override
                    public void onNetworkError(Exception e) {
                        onError(e.getLocalizedMessage());
                    }

                    @Override
                    public void onMatrixError(MatrixError e) {
                        if (TextUtils.equals(MatrixError.UNKNOWN_TOKEN, e.errcode)) {
                            mListener.onInvalidToken();
                        } else {
                            onError(e.getLocalizedMessage());
                        }
                    }

                    @Override
                    public void onUnexpectedError(Exception e) {
                        onError(e.getLocalizedMessage());
                    }
                });

                // block until the sync callback is invoked.
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    Log.e(LOG_TAG, "Interrupted whilst polling message");
                }
            }

            serverTimeout = mServerTimeoutms;
        }

        if (null != mNetworkConnectivityReceiver) {
            mNetworkConnectivityReceiver.removeEventListener(mNetworkListener);
        }
        Log.d(LOG_TAG, "Event stream terminating.");
    }
}
