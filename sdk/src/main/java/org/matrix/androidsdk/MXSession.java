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

import org.matrix.androidsdk.rest.client.EventsApiClient;
import org.matrix.androidsdk.rest.client.PresenceApiClient;
import org.matrix.androidsdk.rest.client.ProfileApiClient;
import org.matrix.androidsdk.rest.client.RoomsApiClient;
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
    private EventsApiClient mEventsApiClient;
    private ProfileApiClient mProfileApiClient;
    private PresenceApiClient mPresenceApiClient;
    private RoomsApiClient mRoomsApiClient;

    /**
     * Create a basic session for direct API calls.
     * @param credentials the user credentials
     */
    public MXSession(Credentials credentials) {
        mCredentials = credentials;

        mEventsApiClient = new EventsApiClient(credentials);
        mProfileApiClient = new ProfileApiClient(credentials);
        mPresenceApiClient = new PresenceApiClient(credentials);
        mRoomsApiClient = new RoomsApiClient(credentials);
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
        mEventsApiClient.setCredentials(credentials);
        mProfileApiClient.setCredentials(credentials);
        mPresenceApiClient.setCredentials(credentials);
        mRoomsApiClient.setCredentials(credentials);
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
    public EventsApiClient getEventsApiClient() {
        return mEventsApiClient;
    }

    /**
     * Get the API client for requests to the profile API.
     * @return the profile API client
     */
    public ProfileApiClient getProfileApiClient() {
        return mProfileApiClient;
    }

    /**
     * Get the API client for requests to the presence API.
     * @return the presence API client
     */
    public PresenceApiClient getPresenceApiClient() {
        return mPresenceApiClient;
    }

    /**
     * Get the API client for requests to the rooms API.
     * @return the rooms API client
     */
    public RoomsApiClient getRoomsApiClient() {
        return mRoomsApiClient;
    }

    protected void setEventsApiClient(EventsApiClient eventsApiClient) {
        this.mEventsApiClient = eventsApiClient;
    }

    protected void setProfileApiClient(ProfileApiClient profileApiClient) {
        this.mProfileApiClient = profileApiClient;
    }

    protected void setPresenceApiClient(PresenceApiClient presenceApiClient) {
        this.mPresenceApiClient = presenceApiClient;
    }

    protected void setRoomsApiClient(RoomsApiClient roomsApiClient) {
        this.mRoomsApiClient = roomsApiClient;
    }

    /**
     * Start the event stream (events thread that listens for events) with a custom event listener.
     * Use this version if not using a data handler.
     * @param eventsListener the custom event listener
     */
    public void startEventStream(EventsThreadListener eventsListener) {
        if (mEventsThread != null) {
            Log.w(LOG_TAG, "Ignoring startEventStream() : Thread already created.");
            return;
        }
        mEventsThread = new EventsThread(mEventsApiClient, eventsListener);
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

    public void pauseEventStream() {
        mEventsThread.pause();
    }

    public void resumeEventStream() {
        mEventsThread.unpause();
    }

    /**
     * Gracefully stop the event stream.
     */
    public void stopEventStream() {
        mEventsThread.kill();
        mEventsThread = null;
    }
}
