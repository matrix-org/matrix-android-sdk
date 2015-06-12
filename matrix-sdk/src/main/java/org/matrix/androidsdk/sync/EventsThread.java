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

import android.util.Log;

import org.matrix.androidsdk.listeners.IMXNetworkEventListener;
import org.matrix.androidsdk.network.NetworkConnectivityReceiver;
import org.matrix.androidsdk.rest.callback.ApiFailureCallback;
import org.matrix.androidsdk.rest.callback.RestAdapterCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.client.EventsRestClient;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.InitialSyncResponse;
import org.matrix.androidsdk.rest.model.MatrixError;
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

    private EventsRestClient mApiClient;
    private EventsThreadListener mListener = null;
    private String mCurrentToken = null;

    private boolean mInitialSyncDone = false;
    private boolean mPaused = true;
    private boolean mIsCatchingUp = false;
    private boolean mKilling = false;
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
            if (isConnected && mPaused && !mKilling) {
                unpause();
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
        mApiClient = apiClient;
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
        if (null != mCurrentToken) {
            Log.d(LOG_TAG, "Resuming initial sync from " + mCurrentToken);
        } else {
            Log.d(LOG_TAG, "Requesting initial sync...");
        }

        mPaused = false;
        Boolean removePresenceEvents = false;

        // a start token is provided ?
        if (null != mCurrentToken) {
            // assume the initial sync is done
            mInitialSyncDone = true;

            mListener.onInitialSyncComplete(null);
            removePresenceEvents = true;

            // get the members presence
            mApiClient.initialSyncWithLimit(new SimpleApiCallback<InitialSyncResponse>(mFailureCallback) {
                @Override
                public void onSuccess(InitialSyncResponse initialSync) {
                    mListener.onMembersPresencesSyncComplete(initialSync.presence);
                }

                @Override
                public void onNetworkError(Exception e) {
                }

                @Override
                public void onMatrixError(MatrixError e) {
                }

                @Override
                public void onUnexpectedError(Exception e) {
                }
            }, 0);
        }

        // Start with initial sync
        while (!mInitialSyncDone) {
            final CountDownLatch latch = new CountDownLatch(1);

            // if a start token is provided
            // get only the user presences.
            // else starts a sync from scratch
            mApiClient.initialSyncWithLimit(new SimpleApiCallback<InitialSyncResponse>(mFailureCallback) {
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
                    Log.i(LOG_TAG, "Waiting a bit before retrying");
                    try {
                        Thread.sleep(RETRY_WAIT_TIME_MS);
                    } catch (InterruptedException e1) {
                        Log.e(LOG_TAG, "Unexpected interruption while sleeping: " + e1.getMessage());
                    }
                    latch.countDown();
                }

                @Override
                public void onNetworkError(Exception e) {
                    if (null != mCurrentToken) {
                        onSuccess(null);
                    } else {
                        super.onNetworkError(e);
                        sleepAndUnblock();
                    }
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    super.onMatrixError(e);
                    sleepAndUnblock();
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    super.onUnexpectedError(e);
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
            if (mPaused) {
                Log.d(LOG_TAG, "Event stream is paused. Waiting.");
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
                    TokensChunkResponse<Event> eventsResponse = mApiClient.events(mCurrentToken, mEventRequestTimeout);

                    if (!mKilling) {
                        // set the dedicated token when they are known.
                        if ((null != eventsResponse.chunk) && (eventsResponse.chunk.size() > 0)) {
                            eventsResponse.chunk.get(0).mToken = eventsResponse.start;
                            eventsResponse.chunk.get(eventsResponse.chunk.size() - 1).mToken = eventsResponse.end;
                        }

                        // remove presence events because they will be retrieved by a global request
                        if (removePresenceEvents) {
                            ArrayList<Event> events = new ArrayList<Event>();

                            for(Event event : eventsResponse.chunk) {
                                if (!Event.EVENT_TYPE_PRESENCE.equals(event.type)) {
                                    events.add(event);
                                }
                            }

                            eventsResponse.chunk = events;
                            removePresenceEvents = false;
                        }

                        // the catchup request is done once.
                        if (mIsCatchingUp) {
                            Log.e(LOG_TAG, "Stop the catchup");
                            // stop any catch up
                            mIsCatchingUp = false;
                            mPaused = true;
                        }

                        mListener.onEventsReceived(eventsResponse.chunk, eventsResponse.end);
                        mCurrentToken = eventsResponse.end;
                    }

                    // reset to the default value
                    mEventRequestTimeout = EventsRestClient.EVENT_STREAM_TIMEOUT_MS;

                } catch (Exception e) {
                    Log.e(LOG_TAG, "Waiting a bit before retrying : " + e.getMessage());

                    if ((mEventsFailureCallback != null) && (e instanceof RetrofitError)) {
                        mEventsFailureCallback.failure((RetrofitError) e);
                    }

                    boolean isConnected;
                    synchronized (this) {
                        isConnected = mbIsConnected;
                    }

                    // detected if the device is connected before trying again
                    if (isConnected) {
                        try {
                            Thread.sleep(RETRY_WAIT_TIME_MS);
                        } catch (InterruptedException e1) {
                            Log.e(LOG_TAG, "Unexpected interruption while sleeping: " + e1.getMessage());
                        }
                    } else {
                        // no network -> wait that a network connection comes back.
                        pause();
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
