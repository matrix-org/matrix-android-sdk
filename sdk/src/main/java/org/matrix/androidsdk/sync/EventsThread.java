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

import org.matrix.androidsdk.rest.callback.ApiFailureCallback;
import org.matrix.androidsdk.rest.callback.RestAdapterCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.client.EventsRestClient;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.InitialSyncResponse;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.TokensChunkResponse;

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
    private String mCurrentToken;

    private boolean mInitialSyncDone = false;
    private boolean mPaused = true;
    private boolean mKilling = false;

    // Custom Retrofit error callback that will convert Retrofit errors into our own error callback
    private RestAdapterCallback mEventsFailureCallback;
    private ApiFailureCallback mFailureCallback;

    /**
     * Default constructor.
     * @param apiClient API client to make the events API calls
     * @param listener a listener to inform
     */
    public EventsThread(EventsRestClient apiClient, EventsThreadListener listener) {
        super("Events thread");
        mApiClient = apiClient;
        mListener = listener;
    }

    /**
     * Set the failure callback.
     * @param failureCallback
     */
    public void setFailureCallback(ApiFailureCallback failureCallback) {
        mFailureCallback = failureCallback;
        mEventsFailureCallback = new RestAdapterCallback(new SimpleApiCallback(failureCallback), null);
    }

    /**
     * Pause the thread. It will resume where it left off when unpause()d.
     */
    public void pause() {
        Log.i(LOG_TAG, "pause()");
        mPaused = true;
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
    }

    /**
     * Allow the thread to finish its current processing, then permanently stop.
     */
    public void kill() {
        mKilling = true;
    }

    @Override
    public void run() {
        Log.d(LOG_TAG, "Requesting initial sync...");
        mPaused = false;

        // Start with initial sync
        while (!mInitialSyncDone) {
            final CountDownLatch latch = new CountDownLatch(1);
            mApiClient.initialSync(new SimpleApiCallback<InitialSyncResponse>(mFailureCallback) {
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
                    super.onNetworkError(e);
                    sleepAndUnblock();
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
            });

            // block until the initial sync callback is invoked.
            try {
                latch.await();
            } catch (InterruptedException e) {
                Log.e(LOG_TAG, "Interrupted whilst performing initial sync.");
            }
        }

        Log.d(LOG_TAG, "Starting event stream from token " + mCurrentToken);

        // Then repeatedly long-poll for events
        while (!mKilling) {
            if (mPaused) {
                Log.i(LOG_TAG, "Event stream is paused. Waiting.");
                try {
                    synchronized (this) {
                        wait();
                    }
                    Log.i(LOG_TAG, "Event stream woken from pause.");
                } catch (InterruptedException e) {
                    Log.e(LOG_TAG, "Unexpected interruption while paused: " + e.getMessage());
                }
            }

            try {
                TokensChunkResponse<Event> eventsResponse = mApiClient.events(mCurrentToken);
                mListener.onEventsReceived(eventsResponse.chunk);
                mCurrentToken = eventsResponse.end;
            }
            catch (RetrofitError error) {
                if (mEventsFailureCallback != null) {
                    mEventsFailureCallback.failure(error);
                }
                Log.i(LOG_TAG, "Waiting a bit before retrying");
                try {
                    Thread.sleep(RETRY_WAIT_TIME_MS);
                } catch (InterruptedException e1) {
                    Log.e(LOG_TAG, "Unexpected interruption while sleeping: " + e1.getMessage());
                }
            }
        }
        Log.d(LOG_TAG, "Event stream terminating.");
    }
}
