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
import android.util.Log;

import org.matrix.androidsdk.listeners.IMXNetworkEventListener;
import org.matrix.androidsdk.network.NetworkConnectivityReceiver;
import org.matrix.androidsdk.rest.callback.ApiFailureCallback;
import org.matrix.androidsdk.rest.callback.RestAdapterCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.client.EventsRestClient;
import org.matrix.androidsdk.rest.client.EventsRestClientV2;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.InitialSyncResponse;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.SyncV2.SyncResponse;
import org.matrix.androidsdk.rest.model.TokensChunkResponse;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;

import retrofit.RetrofitError;

/**
 * Thread that continually watches the event stream and sends events to its listener.
 */
public class EventsThread extends Thread {
    private static final String LOG_TAG = "EventsThread";

    private static final int RETRY_WAIT_TIME_MS = 10000;

    private static final int  SERVER_TIMEOUT_MS = 30000;
    private static final int CLIENT_TIMEOUT_MS = 120000;

    private EventsRestClient mEventsRestClientV1 = null;
    private EventsRestClientV2 mEventsRestClientV2 = null;

    private EventsThreadListener mListener = null;
    private String mCurrentToken = null;

    private boolean mInitialSyncDone = false;
    private boolean mPaused = true;
    private boolean mIsNetworkSuspended = false;
    private boolean mIsCatchingUp = false;
    private boolean mKilling = false;
    private boolean mIsGettingPresences = false;
    private int mEventRequestTimeout = EventsRestClient.EVENT_STREAM_TIMEOUT_MS;

    // Custom Retrofit error callback that will convert Retrofit errors into our own error callback
    private RestAdapterCallback mEventsFailureCallback;
    private ApiFailureCallback mFailureCallback;

    // avoid restarting the listener if there is no network.
    // wait that there is an available network.
    private NetworkConnectivityReceiver mNetworkConnectivityReceiver;
    private Boolean mbIsConnected = true;
    IMXNetworkEventListener mNetworkListener = new IMXNetworkEventListener() {
        @Override
        public void onNetworkConnectionUpdate(boolean isConnected) {
            synchronized (this) {
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
     * @param apiClientV1 API client to make the events API calls (V1 implementation)
     * @param apiClientV2 API client to make the events API calls (V2 implementation)
     * @param listener a listener to inform
     * @param initialToken the sync initial token.
     */
    public EventsThread(EventsRestClient apiClientV1, EventsRestClientV2 apiClientV2, EventsThreadListener listener, String initialToken) {
        super("Events thread");
        mEventsRestClientV1 = apiClientV1;
        mEventsRestClientV2 = apiClientV2;
        mListener = listener;
        mCurrentToken = initialToken;
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
        mEventsFailureCallback = new RestAdapterCallback(new SimpleApiCallback(failureCallback));
    }

    /**
     * Pause the thread. It will resume where it left off when unpause()d.
     */
    public void pause() {
        Log.i(LOG_TAG, "pause()");
        mPaused = true;
        mIsCatchingUp = false;
    }

    /**
     * @return true if the thread is paused.
     */
    public Boolean isPaused() {
        return mPaused;
    }

    public void onNetworkAvailable() {
        Log.i(LOG_TAG, "onNetWorkAvailable()");
        if (mIsNetworkSuspended) {
            mIsNetworkSuspended = false;

            if (mPaused) {
                Log.i(LOG_TAG, "the event thread is still suspended");
            } else {
                Log.i(LOG_TAG, "Resume the thread");
                // request the latest events asap
                mEventRequestTimeout = 0;
                // cancel any catchup process.
                mIsCatchingUp = false;

                synchronized (this) {
                    notify();
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
            synchronized (this) {
                notify();
            }
        }

        // request the latest events asap
        mEventRequestTimeout = 0;
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
            synchronized (this) {
                notify();
            }
        }

        // request the latest events once
        // without any delay.
        mEventRequestTimeout = 0;
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
            synchronized (this) {
                notify();
            }

            Log.d(LOG_TAG, "Resume the thread to kill it.");
        }
    }

    @Override
    public void run() {
        // prefer the api V2 aimplementation
        if (null != mEventsRestClientV2) {
            runV2();
        } else {
            runV1();
        }
    }

    /**
     * Use the API sync V1 to get the events
     */
    private void runV2() {
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
            mListener.onSyncV2Reponse(null, true);
        } else {

            // Start with initial sync
            while (!mInitialSyncDone) {
                final CountDownLatch latch = new CountDownLatch(1);

                mEventsRestClientV2.syncFromToken(null, 0, CLIENT_TIMEOUT_MS, null, null, new SimpleApiCallback<SyncResponse>(mFailureCallback) {
                    @Override
                    public void onSuccess(SyncResponse syncResponse) {
                        Log.d(LOG_TAG, "Received initial sync response.");
                        mListener.onSyncV2Reponse(syncResponse, true);
                        mCurrentToken = syncResponse.nextBatch;
                        mInitialSyncDone = true;
                        // unblock the events thread
                        latch.countDown();
                    }

                    private void sleepAndUnblock() {
                        Log.i(LOG_TAG, "Waiting a bit before retrying");
                        new Handler().postDelayed(new Runnable() {
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
                        Log.e(LOG_TAG, "Sync V2 onMatrixError " + e.getLocalizedMessage());
                        sleepAndUnblock();
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

            serverTimeout = SERVER_TIMEOUT_MS;
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
            if (mPaused || mIsNetworkSuspended) {
                if (mIsNetworkSuspended) {
                    Log.d(LOG_TAG, "Event stream is paused because there is no available network.");
                } else {
                    Log.d(LOG_TAG, "Event stream is paused. Waiting.");
                }

                try {
                    synchronized (this) {
                        wait();
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
                // *** PATCH SYNC V2 ***
                String inlineFilter = "{\"room\":{\"timeline\":{\"limit\":250}}}";
                // *** PATCH SYNC V2 ***

                final CountDownLatch latch = new CountDownLatch(1);

                Log.d(LOG_TAG, "Get events from token " + mCurrentToken);

                mEventsRestClientV2.syncFromToken(mCurrentToken, serverTimeout, CLIENT_TIMEOUT_MS, mIsCatchingUp ? "offline" : null, inlineFilter, new SimpleApiCallback<SyncResponse>(mFailureCallback) {
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
                            mListener.onSyncV2Reponse(syncResponse, false);
                            mCurrentToken = syncResponse.nextBatch;
                            Log.d(LOG_TAG, "mCurrentToken is now set to " + mCurrentToken);
                        }

                        // unblock the events thread
                        latch.countDown();
                    }

                    private void onError(String description) {
                        boolean isConnected;
                        Log.d(LOG_TAG, "Got an error while polling events " + description);

                        synchronized (this) {
                            isConnected = mbIsConnected;
                        }

                        // detected if the device is connected before trying again
                        if (!isConnected) {
                            // no network -> wait that a network connection comes back.
                            mIsNetworkSuspended = true;
                        }

                        latch.countDown();
                    }

                    @Override
                    public void onNetworkError(Exception e) {
                        onError(e.getLocalizedMessage());
                    }

                    @Override
                    public void onMatrixError(MatrixError e) {
                        onError(e.getLocalizedMessage());
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

            serverTimeout = SERVER_TIMEOUT_MS;
        }

        if (null != mNetworkConnectivityReceiver) {
            mNetworkConnectivityReceiver.removeEventListener(mNetworkListener);
        }
        Log.d(LOG_TAG, "Event stream terminating.");
    }

    /**
     * Use the API sync V1 to get the events
     */
    private void runV1() {
        if (null != mCurrentToken) {
            Log.d(LOG_TAG, "Resuming initial sync from " + mCurrentToken);
        } else {
            Log.d(LOG_TAG, "Requesting initial sync...");
        }

        mPaused = false;

        // a start token is provided ?
        if (null != mCurrentToken) {
            // assume the initial sync is done
            mInitialSyncDone = true;

            mListener.onInitialSyncComplete(null);
            synchronized (mEventsRestClientV1) {
                mIsGettingPresences = true;
            }

            Log.d(LOG_TAG, "Requesting presences update");

            // get the members presence
            mEventsRestClientV1.initialSyncWithLimit(new SimpleApiCallback<InitialSyncResponse>(mFailureCallback) {
                @Override
                public void onSuccess(InitialSyncResponse initialSync) {
                    Log.d(LOG_TAG, "presence update is received");
                    mListener.onMembersPresencesSyncComplete(initialSync.presence);
                    Log.d(LOG_TAG, "presence update is managed");
                    synchronized (mEventsRestClientV1) {
                        mIsGettingPresences = false;
                    }
                }

                @Override
                public void onNetworkError(Exception e) {
                    synchronized (mEventsRestClientV1) {
                        mIsGettingPresences = false;
                    }
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    synchronized (mEventsRestClientV1) {
                        mIsGettingPresences = false;
                    }
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    synchronized (mEventsRestClientV1) {
                        mIsGettingPresences = false;
                    }
                }
            }, 0);
        }

        // Start with initial sync
        while (!mInitialSyncDone) {
            final CountDownLatch latch = new CountDownLatch(1);

            // if a start token is provided
            // get only the user presences.
            // else starts a sync from scratch
            mEventsRestClientV1.initialSyncWithLimit(new SimpleApiCallback<InitialSyncResponse>(mFailureCallback) {
                @Override
                public void onSuccess(InitialSyncResponse initialSync) {
                    Log.i(LOG_TAG, "Received initial sync response.");
                    mListener.onInitialSyncComplete(initialSync);
                    mCurrentToken = initialSync.end;
                    mInitialSyncDone = true;
                    // unblock the events thread
                    latch.countDown();
                }

                private void sleepAndUnblock() {
                    new Handler().postDelayed(new Runnable() {
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
                        Log.e(LOG_TAG, "Sync V1 onNetworkError " + e.getLocalizedMessage());
                        super.onNetworkError(e);
                        sleepAndUnblock();
                    }
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    Log.e(LOG_TAG, "Sync V1 onMatrixError " + e.getLocalizedMessage());
                    super.onMatrixError(e);
                    sleepAndUnblock();
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    super.onUnexpectedError(e);
                    Log.e(LOG_TAG, "Sync V1 onUnexpectedError " + e.getLocalizedMessage());
                    sleepAndUnblock();
                }
            }, 10);

            // block until the initial sync callback is invoked.
            try {
                latch.await();
            } catch (InterruptedException e) {
                Log.e(LOG_TAG, "Interrupted whilst performing initial sync.");
            }
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
            if (mPaused || mIsNetworkSuspended) {
                if (mIsNetworkSuspended) {
                    Log.d(LOG_TAG, "Event stream is paused because there is no available network.");
                } else {
                    Log.d(LOG_TAG, "Event stream is paused. Waiting.");
                }

                try {
                    synchronized (this) {
                        wait();
                    }
                    Log.d(LOG_TAG, "Event stream woken from pause.");
                } catch (InterruptedException e) {
                    Log.e(LOG_TAG, "Unexpected interruption while paused: " + e.getMessage());
                }
            }

            // the service could have been killed while being paused.
            if (!mKilling) {
                try {
                    Log.d(LOG_TAG, "Get events from token " + mCurrentToken);
                    TokensChunkResponse<Event> eventsResponse = mEventsRestClientV1.events(mCurrentToken, mEventRequestTimeout);

                    if (null != eventsResponse.chunk) {
                        Log.d(LOG_TAG, "Got eventsResponse.chunk with " + eventsResponse.chunk.size() + " items");
                    } else {
                        Log.d(LOG_TAG, "Got eventsResponse with no chunk");
                    }

                    if (!mKilling) {
                        // set the dedicated token when they are known.
                        if ((null != eventsResponse.chunk) && (eventsResponse.chunk.size() > 0)) {
                            eventsResponse.chunk.get(0).setIntenalPaginationToken(eventsResponse.start);
                            eventsResponse.chunk.get(eventsResponse.chunk.size() - 1).setIntenalPaginationToken(eventsResponse.end);
                        }

                        // remove presence events because they will be retrieved by a global request
                        // same behaviours for the typing events
                        Boolean isGettingsPresence;

                        synchronized (mEventsRestClientV1) {
                            isGettingsPresence = mIsGettingPresences;
                        }

                        if (isGettingsPresence) {
                            ArrayList<Event> events = new ArrayList<Event>();

                            for(Event event : eventsResponse.chunk) {
                                if (!Event.EVENT_TYPE_PRESENCE.equals(event.type) && !Event.EVENT_TYPE_TYPING.equals(event.type)) {
                                    events.add(event);
                                }
                            }

                            eventsResponse.chunk = events;
                        }

                        // the catchup request is done once.
                        if (mIsCatchingUp) {
                            Log.e(LOG_TAG, "Stop the catchup");
                            // stop any catch up
                            mIsCatchingUp = false;
                            mPaused = true;
                        }

                        mListener.onEventsReceived(eventsResponse.chunk, eventsResponse.end);
                        Log.d(LOG_TAG, "mCurrentToken is now set to " + eventsResponse.end);
                        mCurrentToken = eventsResponse.end;
                    }

                    // reset to the default value
                    mEventRequestTimeout = EventsRestClient.EVENT_STREAM_TIMEOUT_MS;

                } catch (Exception e) {
                    Log.e(LOG_TAG, "Waiting a bit before retrying : " + e.getMessage() + " " + e.getStackTrace());

                    if ((mEventsFailureCallback != null) && (e instanceof RetrofitError)) {
                        mEventsFailureCallback.failure((RetrofitError) e);
                    }

                    boolean isConnected;
                    synchronized (this) {
                        isConnected = mbIsConnected;
                    }

                    // detected if the device is connected before trying again
                    if (!isConnected) {
                        // no network -> wait that a network connection comes back.
                        mIsNetworkSuspended = true;
                    }
                }
            }
        }

        if (null != mNetworkConnectivityReceiver) {
            mNetworkConnectivityReceiver.removeEventListener(mNetworkListener);
        }
        Log.d(LOG_TAG, "Event stream terminating.");
    }
}
