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

import android.os.HandlerThread;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import org.matrix.androidsdk.call.MXCallsManager;
import org.matrix.androidsdk.data.DataRetriever;
import org.matrix.androidsdk.data.IMXStore;
import org.matrix.androidsdk.data.MyUser;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.data.RoomSummary;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.listeners.IMXEventListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.client.AccountDataRestClient;
import org.matrix.androidsdk.rest.client.PresenceRestClient;
import org.matrix.androidsdk.rest.client.ProfileRestClient;
import org.matrix.androidsdk.rest.client.RoomsRestClient;
import org.matrix.androidsdk.rest.client.ThirdPidRestClient;
import org.matrix.androidsdk.rest.json.ConditionDeserializer;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.RoomAliasDescription;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.Sync.SyncResponse;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.rest.model.bingrules.BingRule;
import org.matrix.androidsdk.rest.model.bingrules.BingRuleSet;
import org.matrix.androidsdk.rest.model.bingrules.Condition;
import org.matrix.androidsdk.rest.model.login.Credentials;
import org.matrix.androidsdk.util.BingRulesManager;
import org.matrix.androidsdk.util.JsonUtils;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import android.os.Handler;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Until;

/**
 * The data handler provides a layer to help manage matrix input and output.
 * <ul>
 * <li>Handles events</li>
 * <li>Stores the data in its storage layer</li>
 * <li>Provides the means for an app to get callbacks for data changes</li>
 * </ul>
 */
public class MXDataHandler implements IMXEventListener {
    private static final String LOG_TAG = "MXData";

    public interface InvalidTokenListener {
        /**
         * Call when the access token is corrupted
         */
        void onTokenCorrupted();
    }

    private List<IMXEventListener> mEventListeners = new ArrayList<IMXEventListener>();

    private IMXStore mStore;
    private Credentials mCredentials;
    private volatile boolean mInitialSyncComplete = false;
    private DataRetriever mDataRetriever;
    private BingRulesManager mBingRulesManager;
    private MXCallsManager mCallsManager;

    private ProfileRestClient mProfileRestClient;
    private PresenceRestClient mPresenceRestClient;
    private ThirdPidRestClient mThirdPidRestClient;
    private RoomsRestClient mRoomsRestClient;

    private MyUser mMyUser;

    private HandlerThread mSyncHandlerThread;
    private Handler mSyncHandler;
    private Handler mUiHandler;

    // list of ignored users
    // null -> not initialized
    // should be retrieved from the store
    private List<String> mIgnoredUserIdsList;

    private boolean mIsAlive = true;

    InvalidTokenListener mInvalidTokenListener;

    /**
     * Default constructor.
     * @param store the data storage implementation.
     */
    public MXDataHandler(IMXStore store, Credentials credentials,InvalidTokenListener invalidTokenListener) {
        mStore = store;
        mCredentials = credentials;

        mUiHandler = new Handler(Looper.getMainLooper());

        mSyncHandlerThread = new HandlerThread("MXDataHandler" + mCredentials.userId, Thread.MIN_PRIORITY);
        mSyncHandlerThread.start();
        mSyncHandler = new Handler(mSyncHandlerThread.getLooper());

        mInvalidTokenListener = invalidTokenListener;
    }

    public Credentials getCredentials() {
        return mCredentials;
    }

    // setters / getters
    public void setProfileRestClient(ProfileRestClient profileRestClient) {
        mProfileRestClient = profileRestClient;
    }

    public ProfileRestClient getProfileRestClient() {
        return mProfileRestClient;
    }

    public void setPresenceRestClient(PresenceRestClient presenceRestClient) {
        mPresenceRestClient = presenceRestClient;
    }

    public PresenceRestClient getPresenceRestClient() {
        return mPresenceRestClient;
    }

    public void setThirdPidRestClient(ThirdPidRestClient thirdPidRestClient) {
        mThirdPidRestClient = thirdPidRestClient;
    }

    public ThirdPidRestClient getThirdPidRestClient() {
        return mThirdPidRestClient;
    }

    public void setRoomsRestClient(RoomsRestClient roomsRestClient) {
        mRoomsRestClient = roomsRestClient;
    }

    /**
     * Provide the list of user Ids to ignore.
     * The result cannot be null.
     * @return the user Ids list
     */
    public List<String> getIgnoredUserIds() {
        if (null == mIgnoredUserIdsList) {
            mIgnoredUserIdsList = mStore.getIgnoredUserIdsList();
        }

        // avoid the null case
        if (null == mIgnoredUserIdsList) {
            mIgnoredUserIdsList = new ArrayList<String>();
        }

        return mIgnoredUserIdsList;
    }

    private void checkIfAlive() {
        synchronized (this) {
            if (!mIsAlive) {
                Log.e(LOG_TAG, "use of a released dataHandler");
                //throw new AssertionError("Should not used a MXDataHandler");
            }
        }
    }

    public boolean isAlive() {
        synchronized (this) {
            return mIsAlive;
        }
    }

    /**
     * The current token is not anymore valid
     */
    public void onInvalidToken() {
        if (null != mInvalidTokenListener) {
            mInvalidTokenListener.onTokenCorrupted();
        }
    }

    /**
     * Get the session's current user. The MyUser object provides methods for updating user properties which are not possible for other users.
     * @return the session's MyUser object
     */
    public MyUser getMyUser() {
        checkIfAlive();

        IMXStore store = getStore();

        // MyUser is initialized as late as possible to have a better chance at having the info in storage,
        // which should be the case if this is called after the initial sync
        if (mMyUser == null) {
            mMyUser = new MyUser(store.getUser(mCredentials.userId));
            mMyUser.setDataHandler(this);

            // assume the profile is not yet initialized
            if (null == store.displayName()) {
                store.setAvatarURL(mMyUser.getAvatarUrl());
                store.setDisplayName(mMyUser.displayname);
            } else {
                // use the latest user information
                // The user could have updated his profile in offline mode and kill the application.
                mMyUser.displayname = store.displayName();
                mMyUser.setAvatarUrl(store.avatarURL());
            }

            // Handle the case where the user is null by loading the user information from the server
            mMyUser.user_id = mCredentials.userId;
        } else if (null != store) {
            // assume the profile is not yet initialized
            if ((null == store.displayName()) && (null != mMyUser.displayname)) {
                // setAvatarURL && setDisplayName perform a commit if it is required.
                store.setAvatarURL(mMyUser.getAvatarUrl());
                store.setDisplayName(mMyUser.displayname);
            } else if (!TextUtils.equals(mMyUser.displayname, store.displayName())) {
                mMyUser.displayname = store.displayName();
                mMyUser.setAvatarUrl(store.avatarURL());
            }
        }

        // check if there is anything to refresh
        mMyUser.refreshUserInfos(null);

        return mMyUser;
    }

    /**
     * @return true if the initial sync is completed.
     */
    public boolean isInitialSyncComplete() {
        checkIfAlive();
        return mInitialSyncComplete;
    }

    public DataRetriever getDataRetriever() {
        checkIfAlive();
        return mDataRetriever;
    }

    public void setDataRetriever(DataRetriever dataRetriever) {
        checkIfAlive();
        mDataRetriever = dataRetriever;
    }

    public void setPushRulesManager(BingRulesManager bingRulesManager) {
        if (isAlive()) {
            mBingRulesManager = bingRulesManager;

            mBingRulesManager.loadRules(new SimpleApiCallback<Void>() {
                @Override
                public void onSuccess(Void info) {
                    MXDataHandler.this.onBingRulesUpdate();
                }
            });
        }
    }

    public void setCallsManager(MXCallsManager callsManager) {
        checkIfAlive();
        mCallsManager = callsManager;
    }

    public MXCallsManager getCallsManager() {
        checkIfAlive();
        return mCallsManager;
    }

    public BingRuleSet pushRules() {
        if (isAlive() && (null != mBingRulesManager)) {
            return mBingRulesManager.pushRules();
        }

        return null;
    }

    public void refreshPushRules() {
        if (isAlive() && (null != mBingRulesManager)) {
            mBingRulesManager.loadRules(new SimpleApiCallback<Void>() {
                @Override
                public void onSuccess(Void info) {
                    MXDataHandler.this.onBingRulesUpdate();
                }
            });
        }
    }

    public BingRulesManager getBingRulesManager() {
        checkIfAlive();
        return mBingRulesManager;
    }

    public void addListener(IMXEventListener listener) {
        if (isAlive()) {
            synchronized (this) {
                // avoid adding twice
                if (mEventListeners.indexOf(listener) == -1) {
                    mEventListeners.add(listener);
                }
            }

            if (mInitialSyncComplete) {
                listener.onInitialSyncComplete();
            }
        }
    }

    public void removeListener(IMXEventListener listener) {
        if (isAlive()) {
            synchronized (this) {
                mEventListeners.remove(listener);
            }
        }
    }

    public void clear() {
        synchronized (this) {
            mIsAlive = false;
            // remove any listener
            mEventListeners.clear();
        }

        // clear the store
        mStore.close();
        mStore.clear();

        if (null != mSyncHandlerThread) {
            mSyncHandlerThread.quit();
            mSyncHandlerThread = null;
        }
    }

    public String getUserId() {
        if (isAlive()) {
            return mCredentials.userId;
        } else {
            return "dummy";
        }
    }

    /**
     * Update the missing data fields loaded from a permanent storage.
     */
    public void checkPermanentStorageData() {
        if (!isAlive()) {
            Log.e(LOG_TAG, "checkPermanentStorageData : the session is not anymore active");
            return;
        }

        if (mStore.isPermanent()) {
            // When the data are extracted from a persistent storage,
            // some fields are not retrieved :
            // They are used to retrieve some data
            // so add the missing links.

            Collection<Room> rooms =  mStore.getRooms();

            for(Room room : rooms) {
                room.init(room.getRoomId(), this);
            }

            Collection<RoomSummary> summaries = mStore.getSummaries();
            for(RoomSummary summary : summaries) {
                if (null != summary.getLatestRoomState()) {
                    summary.getLatestRoomState().setDataHandler(this);
                }
            }
        }
    }

    /**
     * @return the used store.
     */
    public IMXStore getStore() {
        if (isAlive()) {
            return mStore;
        } else {
            Log.e(LOG_TAG, "getStore : the session is not anymore active");
            return null;
        }
    }

    /**
     * Returns the member with userID;
     * @param members the members List
     * @param userID the user ID
     * @return the roomMember if it exists.
     */
    public RoomMember getMember(Collection<RoomMember> members, String userID) {
        if (isAlive()) {
            for (RoomMember member : members) {
                if (TextUtils.equals(userID, member.getUserId())) {
                    return member;
                }
            }
        } else {
            Log.e(LOG_TAG, "getMember : the session is not anymore active");
        }
        return null;
    }

    /**
     * Check a room exists with the dedicated roomId
     * @param roomId the room ID
     * @return true it exists.
     */
    public boolean doesRoomExist(String roomId) {
        return (null != roomId) && (null != mStore.getRoom(roomId));
    }

    /**
     * Get the room object for the corresponding room id. Creates and initializes the object if there is none.
     * @param roomId the room id
     * @return the corresponding room
     */
    public Room getRoom(String roomId) {
        return getRoom(roomId, true);
    }

    /**
     * Get the room object for the corresponding room id.
     * @param roomId the room id
     * @param create create the room it does not exist.
     * @return the corresponding room
     */
    public Room getRoom(String roomId, boolean create) {
        if (!isAlive()) {
            Log.e(LOG_TAG, "getRoom : the session is not anymore active");
            return null;
        }

        // sanity check
        if (TextUtils.isEmpty(roomId)) {
            return null;
        }

        Room room = mStore.getRoom(roomId);
        if ((room == null) && create) {
            room = new Room();
            room.init(roomId, this);
            mStore.storeRoom(room);
        }
        return room;
    }

    /**
     * Retrieve a room Id by its alias.
     * @param roomAlias the room alias
     * @param callback the asynchronous callback
     */
    public void roomIdByAlias(final String roomAlias, final ApiCallback<String> callback) {
        String roomId = null;

        Collection<Room> rooms = getStore().getRooms();

        for(Room room : rooms) {
            if (TextUtils.equals(room.getState().alias, roomAlias)) {
                roomId = room.getRoomId();
                break;
            } else {
                List<String> aliases = room.getState().aliases;

                if (null != aliases) {
                    for(String alias : aliases) {
                        if (TextUtils.equals(alias, roomAlias)) {
                            roomId = room.getRoomId();
                            break;
                        }
                    }
                }

                // find one matched room id.
                if (null != roomId) {
                    break;
                }
            }
        }

        if (null != roomId) {
            final String fRoomId = roomId;

            Handler handler = new Handler(Looper.getMainLooper());

            handler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onSuccess(fRoomId);
                }
            });
        } else {
            mRoomsRestClient.roomIdByAlias(roomAlias, new ApiCallback<RoomAliasDescription>() {
                @Override
                public void onSuccess(RoomAliasDescription info) {
                    callback.onSuccess(info.room_id);
                }

                @Override
                public void onNetworkError(Exception e) {
                    callback.onNetworkError(e);
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    callback.onMatrixError(e);
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    callback.onUnexpectedError(e);
                }
            });
        }

    }

    /**
     * Delete an event.
     * @param event The event to be stored.
     */
    public void deleteRoomEvent(Event event) {
        if (isAlive()) {
            Room room = getRoom(event.roomId);

            if (null != room) {
                mStore.deleteEvent(event);
                Event lastEvent = mStore.getLatestEvent(event.roomId);
                RoomState beforeLiveRoomState = room.getState().deepCopy();

                mStore.storeSummary(event.roomId, lastEvent, beforeLiveRoomState, mCredentials.userId);
            }
        } else {
            Log.e(LOG_TAG, "deleteRoomEvent : the session is not anymore active");
        }
    }

    /**
     * Return an user from his id.
     * @param userId the user id;.
     * @return the user.
     */
    public User getUser(String userId) {
        if (!isAlive()) {
            Log.e(LOG_TAG, "getUser : the session is not anymore active");
            return null;
        } else {
            return mStore.getUser(userId);
        }
    }

    //================================================================================
    // Sync V2
    //================================================================================

    public void handlePresenceEvent(Event presenceEvent) {
        // Presence event
        if (Event.EVENT_TYPE_PRESENCE.equals(presenceEvent.type)) {
            User userPresence = JsonUtils.toUser(presenceEvent.content);

            // use the sender by default
            if (!TextUtils.isEmpty(presenceEvent.getSender())) {
                userPresence.user_id = presenceEvent.getSender();
            }

            User user = mStore.getUser(userPresence.user_id);

            if (user == null) {
                user = userPresence;
                user.lastActiveReceived();
                user.setDataHandler(this);
                mStore.storeUser(user);
            }
            else {
                user.currently_active = userPresence.currently_active;
                user.presence = userPresence.presence;
                user.lastActiveAgo = userPresence.lastActiveAgo;
                user.lastActiveReceived();
            }

            // check if the current user has been updated
            if (mCredentials.userId.equals(user.user_id)) {
                mStore.setAvatarURL(user.getAvatarUrl());
                mStore.setDisplayName(user.displayname);
            }

            this.onPresenceUpdate(presenceEvent, user);
        }
    }

    public void onSyncReponse(final SyncResponse syncResponse, final boolean isInitialSync) {
        // perform the sync in background
        // to avoid UI thread lags.
        mSyncHandler.post(new Runnable() {
            @Override
            public void run() {
               manageResponse(syncResponse, isInitialSync);
            }
        });
    }

    private void manageResponse(final SyncResponse syncResponse, final boolean isInitialSync) {
        boolean isEmptyResponse = true;

        // sanity check
        if (null != syncResponse) {
            Log.d(LOG_TAG, "onSyncComplete");

            // sanity check
            if (null != syncResponse.rooms) {

                // left room management
                // it should be done at the end but it seems there is a server issue
                // when inviting after leaving a room, the room is defined in the both leave & invite rooms list.
                if ((null != syncResponse.rooms.leave) && (syncResponse.rooms.leave.size() > 0)) {
                    Log.d(LOG_TAG, "Received " + syncResponse.rooms.leave.size() + " left rooms");

                    Set<String> roomIds = syncResponse.rooms.leave.keySet();

                    for (String roomId : roomIds) {
                        // RoomSync leftRoomSync = syncResponse.rooms.leave.get(roomId);

                        // Presently we remove the existing room from the rooms list.
                        // FIXME SYNCV2 Archive/Display the left rooms!
                        // For that create 'handleArchivedRoomSync' method

                        // Retrieve existing room
                        // check if the room still exists.
                        if (null != this.getStore().getRoom(roomId)) {
                            this.getStore().deleteRoom(roomId);
                            onLeaveRoom(roomId);
                        }
                    }

                    isEmptyResponse = false;
                }

                // joined rooms events
                if ((null != syncResponse.rooms.join) && (syncResponse.rooms.join.size() > 0)) {
                    Log.d(LOG_TAG, "Received " + syncResponse.rooms.join.size() + " joined rooms");

                    Set<String> roomIds = syncResponse.rooms.join.keySet();

                    // Handle first joined rooms
                    for (String roomId : roomIds) {
                        Room room = getRoom(roomId);

                        // sanity check
                        if (null != room) {
                            room.handleJoinedRoomSync(syncResponse.rooms.join.get(roomId), isInitialSync);
                        }
                    }

                    isEmptyResponse = false;
                }

                // invited room management
                if ((null != syncResponse.rooms.invite) && (syncResponse.rooms.invite.size() > 0)) {
                    Log.d(LOG_TAG, "Received " + syncResponse.rooms.invite.size() + " invited rooms");

                    Set<String> roomIds = syncResponse.rooms.invite.keySet();

                    for (String roomId : roomIds) {
                        getRoom(roomId).handleInvitedRoomSync(syncResponse.rooms.invite.get(roomId));
                    }

                    isEmptyResponse = false;
                }
            }

            // Handle presence of other users
            if ((null != syncResponse.presence) && (null != syncResponse.presence.events)) {
                for (Event presenceEvent : syncResponse.presence.events) {
                    handlePresenceEvent(presenceEvent);
                }
            }

            // account data
            if (null != syncResponse.accountData) {
                List<String> newIgnoredUsers = ignoredUsersFromAccountData(syncResponse.accountData);

                if (null != newIgnoredUsers) {
                    List<String> curIgnoredUsers = getIgnoredUserIds();

                    // the both lists are not empty
                    if ((0 != newIgnoredUsers.size()) || (0 != curIgnoredUsers.size())) {
                        // check if the ignored users list has been updated
                        if ((newIgnoredUsers.size() != curIgnoredUsers.size()) || !newIgnoredUsers.contains(curIgnoredUsers)) {
                            // update the store
                            mStore.setIgnoredUserIdsList(newIgnoredUsers);
                            mIgnoredUserIdsList = newIgnoredUsers;

                            if (!isInitialSync) {
                                // warn there is an update
                                onIgnoredUsersListUpdate();
                            }
                        }
                    }
                }
            }

            if (!isEmptyResponse) {
                getStore().setEventStreamToken(syncResponse.nextBatch);
                getStore().commit();
            }
        }

        if (isInitialSync) {
            onInitialSyncComplete();
        } else {
            try {
                onLiveEventsChunkProcessed();
            } catch (Exception e) {
                Log.e(LOG_TAG, "onLiveEventsChunkProcessed failed " + e.getLocalizedMessage());
            }

            try {
                // check if an incoming call has been received
                mCallsManager.checkPendingIncomingCalls();
            } catch (Exception e) {
                Log.e(LOG_TAG, "checkPendingIncomingCalls failed " + e + " " + e.getLocalizedMessage());
            }
        }
    }

    /**
     * Extract the ignored users list from the account data dictionary.
     *
     * @param accountData the account data dictionary.
     * @return the ignored users list. null means that there is not defined user ids list.
     */
    private List<String> ignoredUsersFromAccountData(Map<String, Object> accountData) {
        List<String> ignoredUsers = null;

        try {
            if (accountData.containsKey("events")) {
                List<Map<String, Object>> events = (List< Map<String, Object>>)accountData.get("events");

                if (0 != events.size()) {
                    for (Map<String, Object> event : events) {
                        String type = (String)event.get("type");

                        if (TextUtils.equals(type, AccountDataRestClient.ACCOUNT_DATA_TYPE_IGNORED_USER_LIST)) {
                            if (event.containsKey("content")) {
                                Map<String, Object> contentDict = (Map<String, Object>)event.get("content");

                                if (contentDict.containsKey(AccountDataRestClient.ACCOUNT_DATA_KEY_IGNORED_USERS)) {
                                    Map<String, Object> ignored_users = (Map<String, Object>)contentDict.get(AccountDataRestClient.ACCOUNT_DATA_KEY_IGNORED_USERS);

                                    if (null != ignored_users) {
                                        ignoredUsers = new ArrayList<String>(ignored_users.keySet());
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "ignoredUsersFromAccountData failed " + e.getLocalizedMessage());
        }

        return ignoredUsers;
    }

    /**
     * Refresh the unread summary counters of the updated rooms.
     */
    private void refreshUnreadCounters() {
        // refresh the unread counter
        for(String roomId : mUpdatedRoomIdList) {
            Room room = mStore.getRoom(roomId);

            if (null != room) {
                room.refreshUnreadCounter();
            }
        }

        mUpdatedRoomIdList.clear();
    }

    //================================================================================
    // Listeners management
    //================================================================================

    // Proxy IMXEventListener callbacks to everything in mEventListeners
    List<IMXEventListener> getListenersSnapshot() {
        ArrayList<IMXEventListener> eventListeners;

        synchronized (this) {
            eventListeners = new ArrayList<IMXEventListener>(mEventListeners);
        }

        return eventListeners;
    }

    public void onStoreReady() {
        final List<IMXEventListener> eventListeners = getListenersSnapshot();

        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                for (IMXEventListener listener : eventListeners) {
                    try {
                        listener.onStoreReady();
                    } catch (Exception e) {
                    }
                }
            }
        });
    }

    @Override
    public void onAccountInfoUpdate(final MyUser myUser) {
        final List<IMXEventListener> eventListeners = getListenersSnapshot();

        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                for (IMXEventListener listener : eventListeners) {
                    try {
                        listener.onAccountInfoUpdate(myUser);
                    } catch (Exception e) {
                    }
                }
            }
        });
    }

    @Override
    public void onPresenceUpdate(final Event event, final User user) {
        final List<IMXEventListener> eventListeners = getListenersSnapshot();

        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                for (IMXEventListener listener : eventListeners) {
                    try {
                        listener.onPresenceUpdate(event, user);
                    } catch (Exception e) {
                    }
                }
            }
        });
    }

    private ArrayList<String> mUpdatedRoomIdList = new ArrayList<String>();

    @Override
    public void onLiveEvent(final Event event, final RoomState roomState) {
        //
        if (!TextUtils.equals(Event.EVENT_TYPE_TYPING, event.type) && !TextUtils.equals(Event.EVENT_TYPE_RECEIPT, event.type) && !TextUtils.equals(Event.EVENT_TYPE_TYPING, event.type)) {
            if (mUpdatedRoomIdList.indexOf(roomState.roomId) < 0) {
                mUpdatedRoomIdList.add(roomState.roomId);
            }
        }

        final List<IMXEventListener> eventListeners = getListenersSnapshot();

        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                for (IMXEventListener listener : eventListeners) {
                    try {
                        listener.onLiveEvent(event, roomState);
                    } catch (Exception e) {
                    }
                }
            }
        });
    }

    @Override
    public void onLiveEventsChunkProcessed() {
        refreshUnreadCounters();

        final List<IMXEventListener> eventListeners = getListenersSnapshot();

        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                for (IMXEventListener listener : eventListeners) {
                    try {
                        listener.onLiveEventsChunkProcessed();
                    } catch (Exception e) {
                    }
                }
            }
        });
    }

    @Override
    public void onBingEvent(final Event event, final RoomState roomState, final BingRule bingRule) {
        final List<IMXEventListener> eventListeners = getListenersSnapshot();

        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                for (IMXEventListener listener : eventListeners) {
                    try {
                        listener.onBingEvent(event, roomState, bingRule);
                    } catch (Exception e) {
                    }
                }
            }
        });
    }

    @Override
    public void onSentEvent(final Event event) {
        final List<IMXEventListener> eventListeners = getListenersSnapshot();

        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                for (IMXEventListener listener : eventListeners) {
                    try {
                        listener.onSentEvent(event);
                    } catch (Exception e) {
                    }
                }
            }
        });
    }

    @Override
    public void onFailedSendingEvent(final Event event) {
        final List<IMXEventListener> eventListeners = getListenersSnapshot();

        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                for (IMXEventListener listener : eventListeners) {
                    try {
                        listener.onFailedSendingEvent(event);
                    } catch (Exception e) {
                    }
                }
            }
        });
    }

    @Override
    public void onBingRulesUpdate() {
        final List<IMXEventListener> eventListeners = getListenersSnapshot();

        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                for (IMXEventListener listener : eventListeners) {
                    try {
                        listener.onBingRulesUpdate();
                    } catch (Exception e) {
                    }
                }
            }
        });
    }

    @Override
    public void onInitialSyncComplete() {
        mInitialSyncComplete = true;

        refreshUnreadCounters();

        final List<IMXEventListener> eventListeners = getListenersSnapshot();

        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                for (IMXEventListener listener : eventListeners) {
                    try {
                        listener.onInitialSyncComplete();
                    } catch (Exception e) {
                    }
                }
            }
        });
    }

    public void onNewRoom(final String roomId) {
        final List<IMXEventListener> eventListeners = getListenersSnapshot();

        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                for (IMXEventListener listener : eventListeners) {
                    try {
                        listener.onNewRoom(roomId);
                    } catch (Exception e) {
                    }
                }
            }
        });
    }

    public void onJoinRoom(final String roomId) {
        final List<IMXEventListener> eventListeners = getListenersSnapshot();

        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                for (IMXEventListener listener : eventListeners) {
                    try {
                        listener.onJoinRoom(roomId);
                    } catch (Exception e) {
                    }
                }
            }
        });
    }

    public void onRoomInitialSyncComplete(final String roomId) {
        final List<IMXEventListener> eventListeners = getListenersSnapshot();

        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                for (IMXEventListener listener : eventListeners) {
                    try {
                        listener.onRoomInitialSyncComplete(roomId);
                    } catch (Exception e) {
                    }
                }
            }
        });
    }

    public void onRoomInternalUpdate(final String roomId) {
        final List<IMXEventListener> eventListeners = getListenersSnapshot();

        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                for (IMXEventListener listener : eventListeners) {
                    try {
                        listener.onRoomInternalUpdate(roomId);
                    } catch (Exception e) {
                    }
                }
            }
        });
    }

    public void onLeaveRoom(final String roomId) {
        final List<IMXEventListener> eventListeners = getListenersSnapshot();

        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                for (IMXEventListener listener : eventListeners) {
                    try {
                        listener.onLeaveRoom(roomId);
                    } catch (Exception e) {
                    }
                }
            }
        });
    }

    public void onReceiptEvent(final String roomId, final List<String> senderIds) {

        // refresh the unread countres at the end of the process chunk
        if (mUpdatedRoomIdList.indexOf(roomId) < 0) {
            mUpdatedRoomIdList.add(roomId);
        }

        final List<IMXEventListener> eventListeners = getListenersSnapshot();

        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                for (IMXEventListener listener : eventListeners) {
                    try {
                        listener.onReceiptEvent(roomId, senderIds);
                    } catch (Exception e) {
                    }
                }
            }
        });
    }

    public void onRoomTagEvent(final String roomId) {
        final List<IMXEventListener> eventListeners = getListenersSnapshot();

        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                for (IMXEventListener listener : eventListeners) {
                    try {
                        listener.onRoomTagEvent(roomId);
                    } catch (Exception e) {
                    }
                }
            }
        });
    }

    public void onRoomSyncWithLimitedTimeline(final String roomId) {
        final List<IMXEventListener> eventListeners = getListenersSnapshot();

        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                for (IMXEventListener listener : eventListeners) {
                    try {
                        listener.onRoomSyncWithLimitedTimeline(roomId);
                    } catch (Exception e) {
                    }
                }
            }
        });
    }

    public void onIgnoredUsersListUpdate() {
        final List<IMXEventListener> eventListeners = getListenersSnapshot();

        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                for (IMXEventListener listener : eventListeners) {
                    try {
                        listener.onIgnoredUsersListUpdate();
                    } catch (Exception e) {
                    }
                }
            }
        });
    }
}
