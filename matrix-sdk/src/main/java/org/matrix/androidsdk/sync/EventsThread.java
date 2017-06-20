/*
 * Copyright 2014 OpenMarket Ltd
 * Copyright 2017 Vector Creations Ltd
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

import org.matrix.androidsdk.rest.model.Sync.RoomsSyncResponse;
import org.matrix.androidsdk.util.Log;

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
    private boolean mGotFirstCatchupChunk = false;
    private boolean mIsOnline = true;

    private boolean mKilling = false;

    private int mDefaultServerTimeoutms = DEFAULT_SERVER_TIMEOUT_MS;
    private int mNextServerTimeoutms = DEFAULT_SERVER_TIMEOUT_MS;

    // add a delay between two sync requests
    private int mRequestDelayMs = 0;
    private Timer mSyncDelayTimer = null;
    private final Object mSyncDelayTimerLock = new Object();

    // avoid sync on "this" because it might differ if there is a timer.
    private final Object mSyncObject = new Object();

    // Custom Retrofit error callback that will convert Retrofit errors into our own error callback
    private ApiFailureCallback mFailureCallback;

    // avoid restarting the listener if there is no network.
    // wait that there is an available network.
    private NetworkConnectivityReceiver mNetworkConnectivityReceiver;
    private boolean mbIsConnected = true;


    private final IMXNetworkEventListener mNetworkListener = new IMXNetworkEventListener() {
        @Override
        public void onNetworkConnectionUpdate(boolean isConnected) {
            Log.d(LOG_TAG, "onNetworkConnectionUpdate : before " + mbIsConnected + " now " + isConnected);

            synchronized (mSyncObject) {
                mbIsConnected = isConnected;
            }

            // the thread has been suspended and there is an available network
            if (isConnected && !mKilling) {
                Log.d(LOG_TAG, "onNetworkConnectionUpdate : call onNetworkAvailable");
                onNetworkAvailable();
            }
        }
    };

    /**
     * Default constructor.
     *
     * @param apiClient    API client to make the events API calls
     * @param listener     a listener to inform
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
     *
     * @param ms the timeout in ms
     */
    public void setServerLongPollTimeout(int ms) {
        mDefaultServerTimeoutms = Math.max(ms, DEFAULT_SERVER_TIMEOUT_MS);
        Log.d(LOG_TAG, "setServerLongPollTimeout : " + mDefaultServerTimeoutms);

    }

    /**
     * @return the long poll timeout
     */
    public int getServerLongPollTimeout() {
        return mDefaultServerTimeoutms;
    }

    /**
     * Set a delay between two sync requests.
     *
     * @param ms the delay in ms
     */
    public void setSyncDelay(int ms) {
        mRequestDelayMs = Math.max(0, ms);

        Log.d(LOG_TAG, "setSyncDelay : " + mRequestDelayMs);

        Timer syncDelayTimer;

        synchronized (mSyncDelayTimerLock) {
            syncDelayTimer = mSyncDelayTimer;
            mSyncDelayTimer = null;
        }

        if (null != syncDelayTimer) {
            Log.d(LOG_TAG, "setSyncDelay : cancel the delay timer");

            syncDelayTimer.cancel();
            // and sync asap
            synchronized (mSyncObject) {
                mSyncObject.notify();
            }
        }
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
     *
     * @param networkConnectivityReceiver the network receiver
     */
    public void setNetworkConnectivityReceiver(NetworkConnectivityReceiver networkConnectivityReceiver) {
        mNetworkConnectivityReceiver = networkConnectivityReceiver;
    }

    /**
     * Set the failure callback.
     *
     * @param failureCallback the failure callback.
     */
    public void setFailureCallback(ApiFailureCallback failureCallback) {
        mFailureCallback = failureCallback;
    }

    /**
     * Pause the thread. It will resume where it left off when unpause()d.
     */
    public void pause() {
        Log.d(LOG_TAG, "pause()");
        mPaused = true;
        mIsCatchingUp = false;
    }

    /**
     * A network connection has been retrieved.
     */
    private void onNetworkAvailable() {
        Log.d(LOG_TAG, "onNetWorkAvailable()");
        if (mIsNetworkSuspended) {
            mIsNetworkSuspended = false;

            if (mPaused) {
                Log.d(LOG_TAG, "the event thread is still suspended");
            } else {
                Log.d(LOG_TAG, "Resume the thread");
                // cancel any catchup process.
                mIsCatchingUp = false;

                synchronized (mSyncObject) {
                    mSyncObject.notify();
                }
            }
        } else {
            Log.d(LOG_TAG, "onNetWorkAvailable() : nothing to do");
        }
    }

    /**
     * Unpause the thread if it had previously been paused. If not, this does nothing.
     */
    public void unpause() {
        Log.d(LOG_TAG, "unpause()");

        if (mPaused) {
            Log.d(LOG_TAG, "unpause : the thread was paused so resume it.");

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
            Log.d(LOG_TAG, "unpause : the thread was paused so wake it up");

            mPaused = false;
            synchronized (mSyncObject) {
                mSyncObject.notify();
            }
        }

        mGotFirstCatchupChunk = false;
        mIsCatchingUp = true;
    }

    /**
     * Allow the thread to finish its current processing, then permanently stop.
     */
    public void kill() {
        Log.d(LOG_TAG, "killing ...");

        mKilling = true;

        if (mPaused) {
            Log.d(LOG_TAG, "killing : the thread was pause so wake it up");

            mPaused = false;
            synchronized (mSyncObject) {
                mSyncObject.notify();
            }

            Log.d(LOG_TAG, "Resume the thread to kill it.");
        }
    }

    /**
     * Cancel the killing process
     */
    public void cancelKill() {
        if (mKilling) {
            Log.d(LOG_TAG, "## cancelKill() : Cancel the pending kill");
            mKilling = false;
        } else {
            Log.d(LOG_TAG, "## cancelKill() : Nothing to d");
        }
    }

    /**
     * Update the online status
     *
     * @param isOnline true if the client must be seen as online
     */
    public void setIsOnline(boolean isOnline) {
        Log.d(LOG_TAG, "setIsOnline to " + isOnline);
        mIsOnline = isOnline;
    }

    @Override
    public void run() {
        try {
            Looper.prepare();
        } catch (Exception e) {
            Log.e(LOG_TAG, "## run() : prepare failed " + e.getMessage());
        }
        startSync();
    }

    /**
     * Tells if a sync request contains some changed devices.
     *
     * @param syncResponse the sync response
     * @return true if the response contains some changed devices.
     */
    private static boolean hasDevicesChanged(SyncResponse syncResponse) {
        return (null != syncResponse.deviceLists) &&
                (null != syncResponse.deviceLists.changed) &&
                (syncResponse.deviceLists.changed.size() > 0);
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

        int serverTimeout;

        mPaused = false;

        //
        mInitialSyncDone = null != mCurrentToken;

        if (mInitialSyncDone) {
            // get the latest events asap
            serverTimeout = 0;
            // dummy initial sync
            // to hide the splash screen
            SyncResponse dummySyncResponse = new SyncResponse();
            dummySyncResponse.nextBatch = mCurrentToken;
            mListener.onSyncResponse(dummySyncResponse, null, true);
        } else {

            // Start with initial sync
            while (!mInitialSyncDone) {
                final CountDownLatch latch = new CountDownLatch(1);
                mEventsRestClient.syncFromToken(null, 0, DEFAULT_CLIENT_TIMEOUT_MS, null, null, new SimpleApiCallback<SyncResponse>(mFailureCallback) {
                    @Override
                    public void onSuccess(SyncResponse syncResponse) {
                        Log.d(LOG_TAG, "Received initial sync response.");
                        mNextServerTimeoutms = hasDevicesChanged(syncResponse) ? 0 : mDefaultServerTimeoutms;
                        mListener.onSyncResponse(syncResponse, null, (0 == mNextServerTimeoutms));
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
            serverTimeout = mNextServerTimeoutms;
        }

        Log.d(LOG_TAG, "Starting event stream from token " + mCurrentToken);

        // sanity check
        if (null != mNetworkConnectivityReceiver) {
            mNetworkConnectivityReceiver.addEventListener(mNetworkListener);
            //
            mbIsConnected = mNetworkConnectivityReceiver.isConnected();
            mIsNetworkSuspended = !mbIsConnected;
        }

        // Then repeatedly long-poll for events
        while (!mKilling) {

            // test if a delay between two syncs
            if ((!mPaused && !mIsNetworkSuspended) && (0 != mRequestDelayMs)) {
                Log.d(LOG_TAG, "startSync : start a delay timer");

                synchronized (mSyncDelayTimerLock) {
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
            }

            Timer syncDelayTimer;

            synchronized (mSyncDelayTimerLock) {
                syncDelayTimer = mSyncDelayTimer;
            }

            if (mPaused || mIsNetworkSuspended || (null != syncDelayTimer)) {
                if (null != syncDelayTimer) {
                    Log.d(LOG_TAG, "Event stream is paused because there is a timer delay.");
                } else if (mIsNetworkSuspended) {
                    Log.d(LOG_TAG, "Event stream is paused because there is no available network.");
                } else {
                    Log.d(LOG_TAG, "Event stream is paused. Waiting.");
                }

                try {
                    Log.d(LOG_TAG, "startSync : wait ...");

                    synchronized (mSyncObject) {
                        mSyncObject.wait();
                    }

                    synchronized (mSyncDelayTimerLock) {
                        if (null != mSyncDelayTimer) {
                            mSyncDelayTimer.cancel();
                            mSyncDelayTimer = null;
                        }
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

                final int fServerTimeout = serverTimeout;
                mNextServerTimeoutms = mDefaultServerTimeoutms;

                mEventsRestClient.syncFromToken(mCurrentToken, serverTimeout, DEFAULT_CLIENT_TIMEOUT_MS, (mIsCatchingUp && mIsOnline) ? "offline" : null, inlineFilter, new SimpleApiCallback<SyncResponse>(mFailureCallback) {
                    @Override
                    public void onSuccess(SyncResponse syncResponse) {
                        if (!mKilling) {
                            // poll /sync with timeout=0 until
                            // we get no to_device messages back.
                            if (0 == fServerTimeout) {
                                if (hasDevicesChanged(syncResponse)) {
                                    mNextServerTimeoutms = 0;
                                }
                            }

                            // the catchup request is suspended when there is no need
                            // to loop again
                            if (mIsCatchingUp && (0 != mNextServerTimeoutms)) {
                                // the catchup triggers sync requests until there are some useful events
                                int eventCounts = 0;

                                if (null != syncResponse.rooms) {
                                    RoomsSyncResponse roomsSyncResponse = syncResponse.rooms;

                                    if (null != roomsSyncResponse.join) {
                                        eventCounts += roomsSyncResponse.join.size();
                                    }

                                    if (null != roomsSyncResponse.invite) {
                                        eventCounts += roomsSyncResponse.invite.size();
                                    }
                                }

                                Log.d(LOG_TAG, "Got " + eventCounts + " useful events while catching up");

                                if (!mGotFirstCatchupChunk) {
                                    mGotFirstCatchupChunk = (0 != eventCounts);

                                    if (mGotFirstCatchupChunk) {
                                        Log.e(LOG_TAG, "Got first catchup chunk");
                                    } else {
                                        Log.e(LOG_TAG, "Empty chunk : sync again");
                                    }

                                    mNextServerTimeoutms = mDefaultServerTimeoutms / 10;
                                } else {
                                    if (0 == eventCounts) {
                                        Log.e(LOG_TAG, "Stop the catchup");
                                        // stop any catch up
                                        mIsCatchingUp = false;
                                        mPaused = true;
                                    } else {
                                        Log.e(LOG_TAG, "Catchup still in progress");
                                        mNextServerTimeoutms = mDefaultServerTimeoutms / 10;
                                    }
                                }
                            }
                            Log.d(LOG_TAG, "Got event response");
                            mListener.onSyncResponse(syncResponse, mCurrentToken, (0 == mNextServerTimeoutms));
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
                } catch (Exception e) {
                    // reported by GA
                    // The thread might have been killed.
                    Log.e(LOG_TAG, "latch.await() failed " + e.getMessage());
                }
            }

            serverTimeout = mNextServerTimeoutms;
        }

        if (null != mNetworkConnectivityReceiver) {
            mNetworkConnectivityReceiver.removeEventListener(mNetworkListener);
        }
        Log.d(LOG_TAG, "Event stream terminating.");
    }
}
