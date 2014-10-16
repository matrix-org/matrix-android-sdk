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

import org.matrix.androidsdk.MXApiClient;
import org.matrix.androidsdk.rest.client.EventsApiClient;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.InitialSyncResponse;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.TokensChunkResponse;

import java.util.List;
import java.util.concurrent.CountDownLatch;

import retrofit.RetrofitError;
import retrofit.client.Response;

/**
 * Thread that continually watches the event stream and sends events to its listener.
 */
public class EventsThread extends Thread {
    private static final String LOG_TAG = "EventsThread";

    private static final int RETRY_WAIT_TIME_MS = 10000;

    private EventsApiClient mApiClient;
    private IEventsThreadListener mListener = null;
    private String mCurrentToken;

    private boolean mInitialSyncDone = false;
    private boolean mPaused = true;
    private boolean mKilling = false;

    /**
     * Interface to implement to listen to the event thread.
     */
    public interface IEventsThreadListener {

        /**
         * Called with the response of the initial sync.
         * @param response the response
         */
        public void onInitialSyncComplete(InitialSyncResponse response);

        /**
         * Called every time events come down the stream.
         * @param events the events
         */
        public void onEventsReceived(List<Event> events);
    }

    /**
     * Shared implementation of the API callback for all API calls from this class.
     * {@inheritDoc}
     */
    private class DefaultApiCallback<T> implements MXApiClient.ApiCallback<T> {
        @Override
        public void onSuccess(T info) {
        }

        @Override
        public void onNetworkError(Exception e) {
            Log.e(LOG_TAG, "Network error: " + e.getMessage());
            Log.i(LOG_TAG, "Waiting a bit before retrying");
            try {
                Thread.sleep(RETRY_WAIT_TIME_MS);
            } catch (InterruptedException e1) {
                Log.e(LOG_TAG, "Unexpected interruption while sleeping: " + e1.getMessage());
            }
        }

        @Override
        public void onMatrixError(MatrixError e) {
            // TODO: Handle Matrix errors
        }

        @Override
        public void onUnexpectedError(Exception e) {
            Log.e(LOG_TAG, "Unexpected error: " + e.getMessage());
        }
    }

    // Custom Retrofit error callback that will convert Retrofit errors into our own error callback
    private MXApiClient.ConvertFailureCallback eventsFailureCallback = new MXApiClient.ConvertFailureCallback(new DefaultApiCallback()) {
        @Override
        public void success(Object o, Response response) {
            // This won't happen
        }
    };

    /**
     *
     * @param apiClient API client to make the events API calls
     * @param listener a listener to inform
     */
    public EventsThread(EventsApiClient apiClient, IEventsThreadListener listener) {
        super("Events thread");
        mApiClient = apiClient;
        mListener = listener;
    }

    /**
     * Pause the thread. It will resume where it left off when unpause()d.
     */
    public void pause() {
        mPaused = true;
    }

    /**
     * Unpause the thread if it had previously been paused. If not, this does nothing.
     */
    public void unpause() {
        if (mPaused) {
            mPaused = false;
            notify();
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
            mApiClient.initialSync(new DefaultApiCallback<InitialSyncResponse>() {
                @Override
                public void onSuccess(InitialSyncResponse initialSync) {
                    Log.i(LOG_TAG, "Received initial sync response.");
                    mListener.onInitialSyncComplete(initialSync);
                    mCurrentToken = initialSync.end;
                    mInitialSyncDone = true;
                    // unblock the events thread
                    latch.countDown();
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
                try {
                    wait();
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
                eventsFailureCallback.failure(error);
            }
        }
    }
}
