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

import android.content.Context;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.util.Log;

import org.matrix.androidsdk.data.DataRetriever;
import org.matrix.androidsdk.data.IMXStore;
import org.matrix.androidsdk.data.MyUser;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.db.MXLatestChatMessageCache;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.network.NetworkConnectivityReceiver;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.ApiFailureCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.client.BingRulesRestClient;
import org.matrix.androidsdk.rest.client.EventsRestClient;
import org.matrix.androidsdk.rest.client.PresenceRestClient;
import org.matrix.androidsdk.rest.client.ProfileRestClient;
import org.matrix.androidsdk.rest.client.PushersRestClient;
import org.matrix.androidsdk.rest.client.RoomsRestClient;
import org.matrix.androidsdk.rest.client.ThirdPidRestClient;
import org.matrix.androidsdk.rest.model.CreateRoomResponse;
import org.matrix.androidsdk.rest.model.RoomResponse;
import org.matrix.androidsdk.rest.model.login.Credentials;
import org.matrix.androidsdk.sync.DefaultEventsThreadListener;
import org.matrix.androidsdk.sync.EventsThread;
import org.matrix.androidsdk.sync.EventsThreadListener;
import org.matrix.androidsdk.util.BingRulesManager;
import org.matrix.androidsdk.util.ContentManager;
import org.matrix.androidsdk.util.UnsentEventsManager;

import java.util.ArrayList;

/**
 * Class that represents one user's session with a particular home server.
 * There can potentially be multiple sessions for handling multiple accounts.
 */
public class MXSession {

    private static final String LOG_TAG = "MXSession";

    private DataRetriever mDataRetriever;
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
    private PushersRestClient mPushersRestClient;
    private ThirdPidRestClient mThirdPidRestClient;

    private ApiFailureCallback mFailureCallback;

    private ContentManager mContentManager;

    private Context mAppContent;
    private NetworkConnectivityReceiver mNetworkConnectivityReceiver;
    private UnsentEventsManager mUnsentEventsManager;

    private MXLatestChatMessageCache mLatestChatMessageCache;
    private MXMediasCache mMediasCache;

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
        mPushersRestClient = new PushersRestClient(credentials);
        mThirdPidRestClient = new ThirdPidRestClient(credentials);
    }

    /**
     * Create a user session with a data handler.
     * @param dataHandler the data handler
     * @param credentials the user credentials
     * @param appContext the application context
     */
    public MXSession(MXDataHandler dataHandler, Credentials credentials, Context appContext) {
        this(credentials);
        mDataHandler = dataHandler;

        // Initialize a data retriever with rest clients
        mDataRetriever = new DataRetriever();
        mDataRetriever.setRoomsRestClient(mRoomsRestClient);
        mDataHandler.setDataRetriever(mDataRetriever);
        mDataHandler.setPushRulesManager(new BingRulesManager(this));

        // application context
        mAppContent = appContext;

        mNetworkConnectivityReceiver = new NetworkConnectivityReceiver();
        mAppContent.registerReceiver(mNetworkConnectivityReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

        mUnsentEventsManager = new UnsentEventsManager(mNetworkConnectivityReceiver);
        mContentManager = new ContentManager(credentials.homeServer, credentials.accessToken, mUnsentEventsManager);

        mDataHandler.setContentManager(mContentManager);

        // the rest client
        mEventsRestClient.setUnsentEventsManager(mUnsentEventsManager);
        mProfileRestClient.setUnsentEventsManager(mUnsentEventsManager);
        mPresenceRestClient.setUnsentEventsManager(mUnsentEventsManager);
        mRoomsRestClient.setUnsentEventsManager(mUnsentEventsManager);
        mBingRulesRestClient.setUnsentEventsManager(mUnsentEventsManager);
        mThirdPidRestClient.setUnsentEventsManager(mUnsentEventsManager);

        // return the default cache manager
        mLatestChatMessageCache = new MXLatestChatMessageCache(credentials.userId);
        mMediasCache = new MXMediasCache(mContentManager, credentials.userId, appContext);
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

    public PushersRestClient getPushersRestClient() {
        return mPushersRestClient;
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

    public MXLatestChatMessageCache getLatestChatMessageCache() {
        return mLatestChatMessageCache;
    }

    public MXMediasCache getMediasCache() {
        return mMediasCache;
    }

    /**
     * Clear the session data
     */
    public void clear(Context context) {
        // stop events stream
        stopEventStream();

        // cancel any listener
        mDataHandler.clear();

        // network event will not be listened anymore
        mAppContent.unregisterReceiver(mNetworkConnectivityReceiver);
        mNetworkConnectivityReceiver.clear();

        // auto resent messages will not be resent
        mUnsentEventsManager.clear();

        // stop any pending request
        // clear data
        mContentManager.clear();

        mLatestChatMessageCache.clearCache(context);
        mMediasCache.clearCache();
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

            IMXStore store = mDataHandler.getStore();

            mMyUser = new MyUser(store.getUser(mCredentials.userId));
            mMyUser.setProfileRestClient(mProfileRestClient);
            mMyUser.setPresenceRestClient(mPresenceRestClient);

            // assume the profile is not yet initialized
            if (null == store.displayName()) {
                store.setAvatarURL(mMyUser.avatarUrl);
                store.setDisplayName(mMyUser.displayname);
            } else {
                // use the latest user information
                // The user could have updated his profile in offline mode and kill the application.
                mMyUser.displayname = store.displayName();
                mMyUser.avatarUrl = store.avatarURL();
            }

            // Handle the case where the user is null by loading the user information from the server
            mMyUser.userId = mCredentials.userId;
        }
        return mMyUser;
    }

    /**
     * Start the event stream (events thread that listens for events) with an event listener.
     * @param eventsListener the event listener or null if using a DataHandler
     * @param networkConnectivityReceiver the network connectivity listener.
     * @param initialToken the initial sync token (null to start from scratch)
     */
    public void startEventStream(EventsThreadListener eventsListener, NetworkConnectivityReceiver networkConnectivityReceiver, String initialToken) {
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

        mEventsThread = new EventsThread(mEventsRestClient, eventsListener, initialToken);
        mEventsThread.setNetworkConnectivityReceiver(networkConnectivityReceiver);

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
     * @param initialToken the initial sync token (null to sync from scratch).
     */
    public void startEventStream(String initialToken) {
        startEventStream(null, this.mNetworkConnectivityReceiver, initialToken);
    }

    /**
     * Gracefully stop the event stream.
     */
    public void stopEventStream() {
        if (null != mEventsThread) {
            mEventsThread.kill();
            mEventsThread = null;
        }
    }

    public void pauseEventStream() {
        mEventsThread.pause();
    }

    public void resumeEventStream() {
        mEventsThread.unpause();
    }

    public void catchupEventStream() {
        mEventsThread.catchup();
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

    /**
     * Join a room by its roomAlias
     * @param roomIdOrAlias the room alias
     * @param callback the async callback once the room is joined. The RoomId is provided.
     */
    public void joinRoom(String roomIdOrAlias, final ApiCallback<String> callback) {
        // sanity check
        if ((null != mDataHandler) && (null != roomIdOrAlias)) {
            mDataRetriever.getRoomsRestClient().joinRoom(roomIdOrAlias, new SimpleApiCallback<RoomResponse>(callback) {
                @Override
                public void onSuccess(final RoomResponse roomResponse) {
                    callback.onSuccess(roomResponse.roomId);
                }
            });
        }
    }

    /**
     * Retrieve user matrix id from a 3rd party id.
     * @param address the user id.
     * @param media the media.
     * @param callback the 3rd party callback
     */
    public void lookup3Pid(String address, String media, final ApiCallback<String> callback) {
        mThirdPidRestClient.lookup3Pid(address, media, callback);
    }

    /**
     * Retrieve user matrix id from a 3rd party id.
     * @param addresses 3rd party ids
     * @param mediums the medias.
     * @param callback the 3rd parties callback
     */
    public void lookup3Pids(ArrayList<String> addresses, ArrayList<String> mediums, ApiCallback<ArrayList<String>> callback) {
        mThirdPidRestClient.lookup3Pids(addresses, mediums, callback);
    }
}
