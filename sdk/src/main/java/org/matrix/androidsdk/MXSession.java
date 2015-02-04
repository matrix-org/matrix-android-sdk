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

import org.matrix.androidsdk.data.DataRetriever;
import org.matrix.androidsdk.data.MyUser;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.ApiFailureCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.client.BingRulesRestClient;
import org.matrix.androidsdk.rest.client.EventsRestClient;
import org.matrix.androidsdk.rest.client.PresenceRestClient;
import org.matrix.androidsdk.rest.client.ProfileRestClient;
import org.matrix.androidsdk.rest.client.RoomsRestClient;
import org.matrix.androidsdk.rest.model.CreateRoomResponse;
import org.matrix.androidsdk.rest.model.login.Credentials;
import org.matrix.androidsdk.sync.DefaultEventsThreadListener;
import org.matrix.androidsdk.sync.EventsThread;
import org.matrix.androidsdk.sync.EventsThreadListener;
import org.matrix.androidsdk.util.BingRulesManager;
import org.matrix.androidsdk.util.ContentManager;

/**
 * Class that represents one user's session with a particular home server.
 * There can potentially be multiple sessions for handling multiple accounts.
 */
public class MXSession {

    private static final String LOG_TAG = "MXSession";

    private MXDataHandler mDataHandler;
    private EventsThread mEventsThread;
    private Credentials mCredentials;
    private MyUser mMyUser;

    // Api clients
    private EventsRestClient mEventsRestClient;
    private ProfileRestClient mProfileRestClient;
    private PresenceRestClient mPresenceRestClient;
    private RoomsRestClient mRoomsRestClient;
    private BingRulesRestClient mBingRulesRestClient;

    private ApiFailureCallback mFailureCallback;

    private ContentManager mContentManager;

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
        mBingRulesRestClient = new BingRulesRestClient(credentials);

        mContentManager = new ContentManager(credentials.homeServer, credentials.accessToken);
    }

    /**
     * Create a user session with a data handler.
     * @param dataHandler the data handler
     * @param credentials the user credentials
     */
    public MXSession(MXDataHandler dataHandler, Credentials credentials) {
        this(credentials);
        mDataHandler = dataHandler;

        // Initialize a data retriever with rest clients
        DataRetriever dataRetriever = new DataRetriever();
        dataRetriever.setRoomsRestClient(mRoomsRestClient);

        mDataHandler.setDataRetriever(dataRetriever);

        mDataHandler.setPushRulesManager(new BingRulesManager(this));
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
     * Get the API client for requests to the bing rules API.
     * @return the bing rules API client
     */
    public BingRulesRestClient getBingRulesApiClient() {
        return mBingRulesRestClient;
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
     * Get the content manager (for uploading and downloading content) associated with the session.
     * @return the content manager
     */
    public ContentManager getContentManager() {
        return mContentManager;
    }

    /**
     * Get the session's current user. The MyUser object provides methods for updating user properties which are not possible for other users.
     * @return the session's MyUser object
     */
    public MyUser getMyUser() {
        // MyUser is initialized as late as possible to have a better chance at having the info in storage,
        // which should be the case if this is called after the initial sync
        if (mMyUser == null) {
            // TODO: Handle the case where the user is null by loading the user information from the server
            mMyUser = new MyUser(mDataHandler.getStore().getUser(mCredentials.userId));
            mMyUser.setProfileRestClient(mProfileRestClient);
            mMyUser.setPresenceRestClient(mPresenceRestClient);
        }
        return mMyUser;
    }

    /**
     * Start the event stream (events thread that listens for events) with an event listener.
     * @param eventsListener the event listener or null if using a DataHandler
     */
    public void startEventStream(EventsThreadListener eventsListener) {
        if (mEventsThread != null) {
            Log.w(LOG_TAG, "Ignoring startEventStream() : Thread already created.");
            return;
        }

        if (eventsListener == null) {
            if (mDataHandler == null) {
                Log.e(LOG_TAG, "Error starting the event stream: No data handler is defined");
                return;
            }
            eventsListener = new DefaultEventsThreadListener(mDataHandler);
        }

        mEventsThread = new EventsThread(mEventsRestClient, eventsListener);
        if (mFailureCallback != null) {
            mEventsThread.setFailureCallback(mFailureCallback);
        }
        if (mCredentials.accessToken != null && !mEventsThread.isAlive()) {
            mEventsThread.start();
        }
    }

    /**
     * Shorthand for {@link #startEventStream(org.matrix.androidsdk.sync.EventsThreadListener)} with no eventListener
     * using a DataHandler and no specific failure callback.
     */
    public void startEventStream() {
        startEventStream(null);
    }

    /**
     * Gracefully stop the event stream.
     */
    public void stopEventStream() {
        mEventsThread.kill();
        mEventsThread = null;
    }

    public void pauseEventStream() {
        mEventsThread.pause();
    }

    public void resumeEventStream() {
        mEventsThread.unpause();
    }

    /**
     * Set a global failure callback implementation.
     * @param failureCallback the failure callback
     */
    public void setFailureCallback(ApiFailureCallback failureCallback) {
        mFailureCallback = failureCallback;
        if (mEventsThread != null) {
            mEventsThread.setFailureCallback(failureCallback);
        }
    }

    /**
     * Create a new room with given properties. Needs the data handler.
     * @param name the room name
     * @param topic the room topic
     * @param visibility the room visibility
     * @param alias the room alias
     * @param callback the async callback once the room is ready
     */
    public void createRoom(String name, String topic, String visibility, String alias, final ApiCallback<String> callback) {
        mRoomsRestClient.createRoom(name, topic, visibility, alias, new SimpleApiCallback<CreateRoomResponse>(callback) {
            @Override
            public void onSuccess(CreateRoomResponse info) {
                final String roomId = info.roomId;
                Room createdRoom = mDataHandler.getRoom(roomId);
                createdRoom.initialSync(new SimpleApiCallback<Void>(callback) {
                    @Override
                    public void onSuccess(Void aVoid) {
                        callback.onSuccess(roomId);
                    }
                });
            }
        });
    }
}
