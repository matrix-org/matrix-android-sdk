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
import android.text.TextUtils;
import android.util.Log;

import org.matrix.androidsdk.call.MXCallsManager;
import org.matrix.androidsdk.data.DataRetriever;
import org.matrix.androidsdk.data.IMXStore;
import org.matrix.androidsdk.data.MyUser;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomTag;
import org.matrix.androidsdk.db.MXLatestChatMessageCache;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.network.NetworkConnectivityReceiver;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.ApiFailureCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.client.BingRulesRestClient;
import org.matrix.androidsdk.rest.client.CallRestClient;
import org.matrix.androidsdk.rest.client.EventsRestClient;
import org.matrix.androidsdk.rest.client.EventsRestClientV2;
import org.matrix.androidsdk.rest.client.PresenceRestClient;
import org.matrix.androidsdk.rest.client.ProfileRestClient;
import org.matrix.androidsdk.rest.client.PushersRestClient;
import org.matrix.androidsdk.rest.client.RegistrationRestClient;
import org.matrix.androidsdk.rest.client.RoomsRestClient;
import org.matrix.androidsdk.rest.client.RoomsRestClientV2;
import org.matrix.androidsdk.rest.client.ThirdPidRestClient;
import org.matrix.androidsdk.rest.model.CreateRoomResponse;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.RoomResponse;
import org.matrix.androidsdk.rest.model.Search.SearchResponse;
import org.matrix.androidsdk.rest.model.bingrules.BingRule;
import org.matrix.androidsdk.rest.model.login.Credentials;
import org.matrix.androidsdk.sync.DefaultEventsThreadListener;
import org.matrix.androidsdk.sync.EventsThread;
import org.matrix.androidsdk.sync.EventsThreadListener;
import org.matrix.androidsdk.util.BingRulesManager;
import org.matrix.androidsdk.util.ContentManager;
import org.matrix.androidsdk.util.UnsentEventsManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Class that represents one user's session with a particular home server.
 * There can potentially be multiple sessions for handling multiple accounts.
 */
public class MXSession {

    private static final String LOG_TAG = "MXSession";

    // define the used api versions
    // API V1 : first implementation but with very slow catchup
    // API V2 : improved V1 : the main improvement is the catchup.
    public static final int REST_CLIENT_API_VERSION_1 = 1;
    public static final int REST_CLIENT_API_VERSION_2 = 2;

    // define the preferred server API when it is available.
    public static final int PREFERED_API_VERSION = REST_CLIENT_API_VERSION_2;

    private DataRetriever mDataRetriever;
    private MXDataHandler mDataHandler;
    private EventsThread mEventsThread;
    private Credentials mCredentials;
    private MyUser mMyUser;

    // Api clients
    private EventsRestClient mEventsRestClient;
    private EventsRestClientV2 mEventsRestClientV2;
    private ProfileRestClient mProfileRestClient;
    private PresenceRestClient mPresenceRestClient;
    private RoomsRestClient mRoomsRestClient;
    private RoomsRestClientV2 mRoomsRestClientV2;
    private BingRulesRestClient mBingRulesRestClient;
    private PushersRestClient mPushersRestClient;
    private ThirdPidRestClient mThirdPidRestClient;
    private CallRestClient mCallRestClient;
    private RegistrationRestClient mRegistrationRestClient;

    private ApiFailureCallback mFailureCallback;

    private ContentManager mContentManager;

    public MXCallsManager mCallsManager;

    private Context mAppContent;
    private NetworkConnectivityReceiver mNetworkConnectivityReceiver;
    private UnsentEventsManager mUnsentEventsManager;

    private MXLatestChatMessageCache mLatestChatMessageCache;
    private MXMediasCache mMediasCache;

    private BingRulesManager mBingRulesManager = null;

    private Boolean mIsActiveSession = true;

    private HomeserverConnectionConfig mHsConfig;

    /**
     * @return true if the client uses the SYNC API V1
     */
    public static Boolean useSyncV1() {
        return PREFERED_API_VERSION == REST_CLIENT_API_VERSION_1;
    }

    /**
     * @return true if the client uses the SYNC API V2
     */
    public static Boolean useSyncV2() {
        return PREFERED_API_VERSION == REST_CLIENT_API_VERSION_2;
    }

    /**
     * Create a basic session for direct API calls.
     * @param hsConfig the home server connection config
     */
    public MXSession(HomeserverConnectionConfig hsConfig) {
        mCredentials = hsConfig.getCredentials();
        mHsConfig = hsConfig;

        mEventsRestClient = new EventsRestClient(hsConfig);

        if (useSyncV2()) {
            mEventsRestClientV2 = new EventsRestClientV2(hsConfig);
        }

        mProfileRestClient = new ProfileRestClient(hsConfig);
        mPresenceRestClient = new PresenceRestClient(hsConfig);
        mRoomsRestClient = new RoomsRestClient(hsConfig);
        mRoomsRestClientV2 = new RoomsRestClientV2(hsConfig);
        mBingRulesRestClient = new BingRulesRestClient(hsConfig);
        mPushersRestClient = new PushersRestClient(hsConfig);
        mThirdPidRestClient = new ThirdPidRestClient(hsConfig);
        mCallRestClient = new CallRestClient(hsConfig);
        mRegistrationRestClient = new RegistrationRestClient(hsConfig);
    }

    /**
     * Create a user session with a data handler.
     * @param hsConfig the home server connection config
     * @param dataHandler the data handler
     * @param appContext the application context
     */
    public MXSession(HomeserverConnectionConfig hsConfig, MXDataHandler dataHandler, Context appContext) {
        this(hsConfig);
        mDataHandler = dataHandler;

        // Initialize a data retriever with rest clients
        mDataRetriever = new DataRetriever();
        mDataRetriever.setRoomsRestClient(mRoomsRestClient);
        mDataRetriever.setRoomsRestClientV2(mRoomsRestClientV2);
        mDataHandler.setDataRetriever(mDataRetriever);
        mBingRulesManager = new BingRulesManager(this);
        mDataHandler.setPushRulesManager(mBingRulesManager);

        // application context
        mAppContent = appContext;

        mNetworkConnectivityReceiver = new NetworkConnectivityReceiver();
        mAppContent.registerReceiver(mNetworkConnectivityReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

        mUnsentEventsManager = new UnsentEventsManager(mNetworkConnectivityReceiver);

        mContentManager = new ContentManager(hsConfig, mUnsentEventsManager);
        mDataHandler.setContentManager(mContentManager);

        //
        mCallsManager = new MXCallsManager(this, mAppContent);
        mDataHandler.setCallsManager(mCallsManager);

        // the rest client
        mEventsRestClient.setUnsentEventsManager(mUnsentEventsManager);

        if (null != mEventsRestClientV2) {
            mEventsRestClientV2.setUnsentEventsManager(mUnsentEventsManager);
        }

        mProfileRestClient.setUnsentEventsManager(mUnsentEventsManager);
        mPresenceRestClient.setUnsentEventsManager(mUnsentEventsManager);
        mRoomsRestClient.setUnsentEventsManager(mUnsentEventsManager);
        mRoomsRestClientV2.setUnsentEventsManager(mUnsentEventsManager);
        mBingRulesRestClient.setUnsentEventsManager(mUnsentEventsManager);
        mThirdPidRestClient.setUnsentEventsManager(mUnsentEventsManager);
        mCallRestClient.setUnsentEventsManager(mUnsentEventsManager);
        mRegistrationRestClient.setUnsentEventsManager(mUnsentEventsManager);

        // return the default cache manager
        mLatestChatMessageCache = new MXLatestChatMessageCache(mCredentials.userId);
        mMediasCache = new MXMediasCache(mContentManager, mCredentials.userId, appContext);
        mDataHandler.setMediasCache(mMediasCache);
    }


    private void checkIfActive() {
        synchronized (this) {
            if (!mIsActiveSession) {
                throw new AssertionError("Should not used a cleared mxsession ");
            }
        }
    }

    /**
     * Get the data handler.
     * @return the data handler.
     */
    public MXDataHandler getDataHandler() {
        checkIfActive();
        return mDataHandler;
    }

    /**
     * Get the user credentials.
     * @return the credentials
     */
    public Credentials getCredentials() {
        checkIfActive();
        return mCredentials;
    }

    /**
     * Get the API client for requests to the events API.
     * @return the events API client
     */
    public EventsRestClient getEventsApiClient() {
        checkIfActive();
        return mEventsRestClient;
    }

    /**
     * Get the API client for requests to the profile API.
     * @return the profile API client
     */
    public ProfileRestClient getProfileApiClient() {
        checkIfActive();
        return mProfileRestClient;
    }

    /**
     * Get the API client for requests to the presence API.
     * @return the presence API client
     */
    public PresenceRestClient getPresenceApiClient() {
        checkIfActive();
        return mPresenceRestClient;
    }

    /**
     * Get the API client for requests to the bing rules API.
     * @return the bing rules API client
     */
    public BingRulesRestClient getBingRulesApiClient() {
        checkIfActive();
        return mBingRulesRestClient;
    }

    public CallRestClient getCallRestClient() {
        checkIfActive();
        return mCallRestClient;
    }

    public PushersRestClient getPushersRestClient() {
        checkIfActive();
        return mPushersRestClient;
    }

    public HomeserverConnectionConfig getHomeserverConfig() {
        checkIfActive();
        return mHsConfig;
    }

    /**
     * Get the API client for requests to the rooms API.
     * @return the rooms API client
     */
    public RoomsRestClient getRoomsApiClient() {
        checkIfActive();
        return mRoomsRestClient;
    }

    protected void setEventsApiClient(EventsRestClient eventsRestClient) {
        checkIfActive();
        this.mEventsRestClient = eventsRestClient;
    }

    protected void setProfileApiClient(ProfileRestClient profileRestClient) {
        checkIfActive();
        this.mProfileRestClient = profileRestClient;
    }

    protected void setPresenceApiClient(PresenceRestClient presenceRestClient) {
        checkIfActive();
        this.mPresenceRestClient = presenceRestClient;
    }

    protected void setRoomsApiClient(RoomsRestClient roomsRestClient) {
        checkIfActive();
        this.mRoomsRestClient = roomsRestClient;
    }

    public MXLatestChatMessageCache getLatestChatMessageCache() {
        checkIfActive();
        return mLatestChatMessageCache;
    }

    public MXMediasCache getMediasCache() {
        checkIfActive();
        return mMediasCache;
    }

    /**
     * Clear the session data
     */
    public void clear(Context context) {
        checkIfActive();

        synchronized (this) {
            mIsActiveSession = false;
        }

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
     * @return true if the session is active i.e. has not been cleared after a logout.
     */
    public Boolean isActive() {
        synchronized (this) {
            return mIsActiveSession;
        }
    }

    /**
     * Get the content manager (for uploading and downloading content) associated with the session.
     * @return the content manager
     */
    public ContentManager getContentManager() {
        checkIfActive();
        return mContentManager;
    }

    /**
     * Get the session's current user. The MyUser object provides methods for updating user properties which are not possible for other users.
     * @return the session's MyUser object
     */
    public MyUser getMyUser() {
        checkIfActive();

        IMXStore store = mDataHandler.getStore();

        // MyUser is initialized as late as possible to have a better chance at having the info in storage,
        // which should be the case if this is called after the initial sync
        if (mMyUser == null) {
            mMyUser = new MyUser(store.getUser(mCredentials.userId));
            mMyUser.setProfileRestClient(mProfileRestClient);
            mMyUser.setPresenceRestClient(mPresenceRestClient);
            mMyUser.setDataHandler(mDataHandler);

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
        } else {
            // assume the profile is not yet initialized
            if ((null == store.displayName()) && (null != mMyUser.displayname)) {
                // setAvatarURL && setDisplayName perform a commit if it is required.
                store.setAvatarURL(mMyUser.avatarUrl);
                store.setDisplayName(mMyUser.displayname);
            } else if (!TextUtils.equals(mMyUser.displayname, store.displayName())) {
                mMyUser.displayname = store.displayName();
                mMyUser.avatarUrl = store.avatarURL();
            }
        }

        // check if there is anything to refresh
        mMyUser.refreshUserInfos(null);

        return mMyUser;
    }

    /**
     * Start the event stream (events thread that listens for events) with an event listener.
     * @param eventsListener the event listener or null if using a DataHandler
     * @param networkConnectivityReceiver the network connectivity listener.
     * @param initialToken the initial sync token (null to start from scratch)
     */
    public void startEventStream(final EventsThreadListener anEventsListener, final NetworkConnectivityReceiver networkConnectivityReceiver, final String initialToken) {
        checkIfActive();

        if (mEventsThread != null) {
            Log.e(LOG_TAG, "Ignoring startEventStream() : Thread already created.");
            return;
        }

        if (mDataHandler == null) {
            Log.e(LOG_TAG, "Error starting the event stream: No data handler is defined");
            return;
        }

        final EventsThreadListener fEventsListener = (null == anEventsListener) ? new DefaultEventsThreadListener(mDataHandler): anEventsListener;

        mEventsThread = new EventsThread(mEventsRestClient, mEventsRestClientV2, fEventsListener, initialToken);
        mEventsThread.setNetworkConnectivityReceiver(networkConnectivityReceiver);

        if (mFailureCallback != null) {
            mEventsThread.setFailureCallback(mFailureCallback);
        }

        if (mCredentials.accessToken != null && !mEventsThread.isAlive()) {
            mEventsThread.start();
        }
    }

    /**
     * Refresh the access token
     */
    public void refreshToken() {
        checkIfActive();

        mRegistrationRestClient.refreshTokens(new ApiCallback<Credentials>() {
            @Override
            public void onSuccess(Credentials info) {
                Log.d(LOG_TAG, "refreshToken : succeeds.");
            }

            @Override
            public void onNetworkError(Exception e) {
                Log.d(LOG_TAG, "refreshToken : onNetworkError " + e.getLocalizedMessage());
            }

            @Override
            public void onMatrixError(MatrixError e) {
                Log.d(LOG_TAG, "refreshToken : onMatrixError " + e.getLocalizedMessage());
            }

            @Override
            public void onUnexpectedError(Exception e) {
                Log.d(LOG_TAG, "refreshToken : onMatrixError " + e.getLocalizedMessage());
            }
        });
    }

    /**
     * Shorthand for {@link #startEventStream(org.matrix.androidsdk.sync.EventsThreadListener)} with no eventListener
     * using a DataHandler and no specific failure callback.
     * @param initialToken the initial sync token (null to sync from scratch).
     */
    public void startEventStream(String initialToken) {
        checkIfActive();
        startEventStream(null, this.mNetworkConnectivityReceiver, initialToken);
    }

    /**
     * Gracefully stop the event stream.
     */
    public void stopEventStream() {
        if (null != mCallsManager) {
            mCallsManager.stopTurnServerRefresh();
        }

        if (null != mEventsThread) {
            Log.d(LOG_TAG, "stopEventStream");

            mEventsThread.kill();
            mEventsThread = null;
        } else {
            Log.e(LOG_TAG, "stopEventStream : mEventsThread is already null");
        }
    }

    public void pauseEventStream() {
        checkIfActive();

        if (null != mCallsManager) {
            mCallsManager.pauseTurnServerRefresh();
        }

        if (null != mEventsThread) {
            Log.d(LOG_TAG, "pauseEventStream");
            mEventsThread.pause();
        } else {
            Log.e(LOG_TAG, "pauseEventStream : mEventsThread is null");
        }
    }

    public void resumeEventStream() {
        checkIfActive();

        if (null != mCallsManager) {
            mCallsManager.unpauseTurnServerRefresh();
        }

        if (null != mEventsThread) {
            Log.d(LOG_TAG, "unpause");
            mEventsThread.unpause();
        } else {
            Log.e(LOG_TAG, "resumeEventStream : mEventsThread is null");
        }
    }

    public void catchupEventStream() {
        checkIfActive();

        if (null != mEventsThread) {
            Log.d(LOG_TAG, "catchupEventStream");
            mEventsThread.catchup();
        } else {
            Log.e(LOG_TAG, "catchupEventStream : mEventsThread is null");
        }
    }

    /**
     * Set a global failure callback implementation.
     * @param failureCallback the failure callback
     */
    public void setFailureCallback(ApiFailureCallback failureCallback) {
        checkIfActive();

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
        checkIfActive();

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
        checkIfActive();

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
        checkIfActive();

        mThirdPidRestClient.lookup3Pid(address, media, callback);
    }

    /**
     * Retrieve user matrix id from a 3rd party id.
     * @param addresses 3rd party ids
     * @param mediums the medias.
     * @param callback the 3rd parties callback
     */
    public void lookup3Pids(ArrayList<String> addresses, ArrayList<String> mediums, ApiCallback<ArrayList<String>> callback) {
        checkIfActive();

        mThirdPidRestClient.lookup3Pids(addresses, mediums, callback);
    }

    /**
     * Perform a remote text search.
     * @param text the text to search for.
     * @param rooms a list of rooms to search in. nil means all rooms the user is in.
     * @param beforeLimit the number of events to get before the matching results.
     * @param afterLimit the number of events to get after the matching results.
     * @param nextBatch the token to pass for doing pagination from a previous response.
     * @param callback the request callback
     */
    public void searchMessageText(String text, List<String> rooms, int beforeLimit, int afterLimit, String nextBatch, final ApiCallback<SearchResponse> callback) {
        checkIfActive();

        mEventsRestClient.searchMessageText(text, rooms, beforeLimit, afterLimit, nextBatch, callback);
    }

    /**
     * Perform a remote text search.
     * @param text the text to search for.
     * @param rooms a list of rooms to search in. nil means all rooms the user is in.
     * @param nextBatch the token to pass for doing pagination from a previous response.
     * @param callback the request callback
     */
    public void searchMessageText(String text, List<String> rooms, String nextBatch, final ApiCallback<SearchResponse> callback) {
        checkIfActive();

        mEventsRestClient.searchMessageText(text, rooms, 0, 0, nextBatch, callback);
    }

    /**
     * Perform a remote text search.
     * @param text the text to search for.
     * @param nextBatch the token to pass for doing pagination from a previous response.
     * @param callback the request callback
     */
    public void searchMessageText(String text, String nextBatch, final ApiCallback<SearchResponse> callback) {
        checkIfActive();

        mEventsRestClient.searchMessageText(text, null, 0, 0, nextBatch, callback);
    }


    /**
     * Return the fulfilled active BingRule for the event.
     * @param event the event
     * @return the fulfilled bingRule
     */
    public BingRule fulfillRule(Event event) {
        checkIfActive();

        return mBingRulesManager.fulfilledBingRule(event);
    }

    /**
     * @return true if the calls are supported
     */
    public Boolean isVoipCallSupported() {
        if (null != mCallsManager) {
            return mCallsManager.isSupported();
        } else {
            return false;
        }
    }
    
    /**
     * Get the list of rooms that are tagged the specified tag.
     * The returned array is ordered according to the room tag order.
     * @param tag  RoomTag.ROOM_TAG_XXX values
     * @return the rooms list.
     */
    public List<Room>roomsWithTag(final String tag) {
        ArrayList<Room> taggedRooms = new ArrayList<Room>();

        if (!TextUtils.equals(tag, RoomTag.ROOM_TAG_NO_TAG)) {
            Collection<Room> rooms = mDataHandler.getStore().getRooms();

            for (Room room : rooms) {
                if (null != room.getAccountData().roomTag(tag)) {
                    taggedRooms.add(room);
                }
            }

            if (taggedRooms.size() > 0) {
                Collections.sort(taggedRooms, new Comparator<Room>() {
                    @Override
                    public int compare(Room r1, Room r2) {
                        int res = 0;

                        RoomTag tag1 = r1.getAccountData().roomTag(tag);
                        RoomTag tag2 = r2.getAccountData().roomTag(tag);

                        if ((null != tag1.mOrder) && (null != tag2.mOrder)) {
                            double diff = (tag1.mOrder - tag2.mOrder);
                            res = (diff == 0) ? 0 : (diff > 0) ? +1 : -1;
                        }
                        else if (null != tag1.mOrder) {
                            res = +1;
                        }
                        else if (null != tag2.mOrder) {
                            res = -1;
                        }

                        // In case of same order, order rooms by their last event
                        if (0 == res) {
                            IMXStore store = mDataHandler.getStore();

                            Event latestEvent1 = store.getLatestEvent(r1.getRoomId());
                            Event latestEvent2 = store.getLatestEvent(r2.getRoomId());

                            // sanity check
                            if ((null != latestEvent2) && (null != latestEvent1)) {
                                long diff = (latestEvent1.getOriginServerTs() - latestEvent2.getOriginServerTs());
                                res = (diff == 0) ? 0 : (diff > 0) ? +1 : -1;
                            }
                        }

                        return res;
                    }
                });
            }
        } else {
            Collection<Room> rooms = mDataHandler.getStore().getRooms();

            for(Room room : rooms) {
                if (!room.getAccountData().hasTags()) {
                    taggedRooms.add(room);
                }
            }
        }

        return taggedRooms;
    }

    /**
     * Get the list of roomIds that are tagged the specified tag.
     * The returned array is ordered according to the room tag order.
     * @param tag  RoomTag.ROOM_TAG_XXX values
     * @return the room IDs list.
     */
    public List<String>roomIdsWithTag(final String tag) {
        List<Room> roomsWithTag = roomsWithTag(tag);

        ArrayList<String> roomIdsList = new ArrayList<String>();

        for(Room room : roomsWithTag) {
            roomIdsList.add(room.getRoomId());
        }

        return roomIdsList;
    }

    /**
     * Compute the tag order to use for a room tag so that the room will appear in the expected position
     * in the list of rooms stamped with this tag.
     * @param index the targeted index of the room in the list of rooms with the tag `tag`.
     * @param originIndex the origin index. Integer.MAX_VALUE if there is none.
     * @param tag the tag
     * @return the tag order to apply to get the expected position.
     */
    public Double tagOrderToBeAtIndex(int index, int originIndex, String tag) {
        // Algo (and the [0.0, 1.0] assumption) inspired from matrix-react-sdk:
        // We sort rooms by the lexicographic ordering of the 'order' metadata on their tags.
        // For convenience, we calculate this for now a floating point number between 0.0 and 1.0.

        Double orderA = 0.0; // by default we're next to the beginning of the list
        Double orderB = 1.0; // by default we're next to the end of the list too

        List<Room> roomsWithTag = roomsWithTag(tag);

        if (roomsWithTag.size() > 0) {
            // when an object is moved down, the index must be incremented
            // because the object will be removed from the list to be inserted after its destination
            if ((originIndex != Integer.MAX_VALUE) && (originIndex < index)) {
                index++;
            }

            if (index > 0) {
                // Bound max index to the array size
                int prevIndex = (index < roomsWithTag.size()) ? index : roomsWithTag.size();

                RoomTag prevTag = roomsWithTag.get(prevIndex - 1).getAccountData().roomTag(tag);

                if (null == prevTag.mOrder) {
                    Log.e(LOG_TAG, "computeTagOrderForRoom: Previous room in sublist has no ordering metadata. This should never happen.");
                }
                else {
                    orderA = prevTag.mOrder;
                }
            }

            if (index <= roomsWithTag.size() - 1)
            {
                RoomTag nextTag = roomsWithTag.get(index).getAccountData().roomTag(tag);

                if (null == nextTag.mOrder) {
                    Log.e(LOG_TAG, "computeTagOrderForRoom: Next room in sublist has no ordering metadata. This should never happen.");
                }
                else {
                    orderB = nextTag.mOrder;
                }
            }
        }

        return (orderA + orderB) / 2.0;
    }
}
