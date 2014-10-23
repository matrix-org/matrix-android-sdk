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
package org.matrix.androidsdk;

import android.util.Log;

import org.matrix.androidsdk.rest.client.EventsRestClient;
import org.matrix.androidsdk.rest.client.PresenceRestClient;
import org.matrix.androidsdk.rest.client.ProfileRestClient;
import org.matrix.androidsdk.rest.client.RoomsRestClient;
import org.matrix.androidsdk.rest.model.login.Credentials;
import org.matrix.androidsdk.sync.DefaultEventsThreadListener;
import org.matrix.androidsdk.sync.EventsThread;
import org.matrix.androidsdk.sync.EventsThreadListener;

/**
 * Class that represents one user's session with a particular home server.
 * There can potentially be multiple sessions for handling multiple accounts.
 */
public class MXSession {

    private static final String LOG_TAG = "MXSession";

    private MXDataHandler mDataHandler;
    private EventsThread mEventsThread;
    private Credentials mCredentials;

    // Api clients
    private EventsRestClient mEventsRestClient;
    private ProfileRestClient mProfileRestClient;
    private PresenceRestClient mPresenceRestClient;
    private RoomsRestClient mRoomsRestClient;

    /**
     * Create a basic session for direct API calls.
     * @param credentials the user credentials
     */
    public MXSession(Credentials credentials) {
        mCredentials = credentials;

        mEventsRestClient = new EventsRestClient(credentials);
        mProfileRestClient = new ProfileRestClient(credentials);
        mPresenceRestClient = new PresenceRestClient(credentials);
        mRoomsRestClient = new RoomsRestClient(credentials);
    }

    /**
     * Create a user session with a data handler.
     * @param dataHandler the data handler
     * @param credentials the user credentials
     */
    public MXSession(MXDataHandler dataHandler, Credentials credentials) {
        this(credentials);
        mDataHandler = dataHandler;
    }

    /**
     * Set the credentials to use.
     * @param credentials the credentials
     */
    public void setCredentials(Credentials credentials) {
        mCredentials = credentials;
        mEventsRestClient.setCredentials(credentials);
        mProfileRestClient.setCredentials(credentials);
        mPresenceRestClient.setCredentials(credentials);
        mRoomsRestClient.setCredentials(credentials);
    }

    /**
     * Get the data handler.
     * @return the data handler.
     */
    public MXDataHandler getDataHandler() {
        return mDataHandler;
    }

    /**
     * Get the user credentials.
     * @return the credentials
     */
    public Credentials getCredentials() {
        return mCredentials;
    }

    /**
     * Get the API client for requests to the events API.
     * @return the events API client
     */
    public EventsRestClient getEventsApiClient() {
        return mEventsRestClient;
    }

    /**
     * Get the API client for requests to the profile API.
     * @return the profile API client
     */
    public ProfileRestClient getProfileApiClient() {
        return mProfileRestClient;
    }

    /**
     * Get the API client for requests to the presence API.
     * @return the presence API client
     */
    public PresenceRestClient getPresenceApiClient() {
        return mPresenceRestClient;
    }

    /**
     * Get the API client for requests to the rooms API.
     * @return the rooms API client
     */
    public RoomsRestClient getRoomsApiClient() {
        return mRoomsRestClient;
    }

    protected void setEventsApiClient(EventsRestClient eventsRestClient) {
        this.mEventsRestClient = eventsRestClient;
    }

    protected void setProfileApiClient(ProfileRestClient profileRestClient) {
        this.mProfileRestClient = profileRestClient;
    }

    protected void setPresenceApiClient(PresenceRestClient presenceRestClient) {
        this.mPresenceRestClient = presenceRestClient;
    }

    protected void setRoomsApiClient(RoomsRestClient roomsRestClient) {
        this.mRoomsRestClient = roomsRestClient;
    }

    /**
     * Start the event stream (events thread that listens for events) with a custom event listener.
     * Use this version if not using a data handler.
     * @param eventsListener the custom event listener
     */
    public void startEventStream(EventsThreadListener eventsListener) {
        mEventsThread = new EventsThread(mEventsRestClient, eventsListener);
        if (mCredentials.accessToken != null && !mEventsThread.isAlive()) {
            mEventsThread.start();
        }
    }

    /**
     * Start the event stream (events thread that listens for events).
     * Use this version if using a data handler.
     */
    public void startEventStream() {
        if (mDataHandler == null) {
            Log.e(LOG_TAG, "Error starting the event stream: No data handler is defined");
            return;
        }
        startEventStream(new DefaultEventsThreadListener(mDataHandler));
    }

    /**
     * Gracefully stop the event stream.
     */
    public void stopEventStream() {
        mEventsThread.kill();
    }
}
