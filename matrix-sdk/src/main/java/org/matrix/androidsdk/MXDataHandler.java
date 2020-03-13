/*
 * Copyright 2014 OpenMarket Ltd
 * Copyright 2017 Vector Creations Ltd
 * Copyright 2018 New Vector Ltd
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
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;

import org.jetbrains.annotations.NotNull;
import org.matrix.androidsdk.call.MXCallsManager;
import org.matrix.androidsdk.core.BingRulesManager;
import org.matrix.androidsdk.core.DataHandlerInterface;
import org.matrix.androidsdk.core.FilterUtil;
import org.matrix.androidsdk.core.JsonUtils;
import org.matrix.androidsdk.core.Log;
import org.matrix.androidsdk.core.MXOsHandler;
import org.matrix.androidsdk.core.callback.ApiCallback;
import org.matrix.androidsdk.core.callback.SimpleApiCallback;
import org.matrix.androidsdk.core.model.MatrixError;
import org.matrix.androidsdk.crypto.MXCrypto;
import org.matrix.androidsdk.crypto.MXCryptoError;
import org.matrix.androidsdk.crypto.MXDecryptionException;
import org.matrix.androidsdk.crypto.MXEventDecryptionResult;
import org.matrix.androidsdk.crypto.interfaces.CryptoDataHandler;
import org.matrix.androidsdk.crypto.interfaces.CryptoEvent;
import org.matrix.androidsdk.crypto.interfaces.CryptoEventListener;
import org.matrix.androidsdk.data.DataRetriever;
import org.matrix.androidsdk.data.MyUser;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.data.RoomSummary;
import org.matrix.androidsdk.data.metrics.MetricsListener;
import org.matrix.androidsdk.data.store.IMXStore;
import org.matrix.androidsdk.data.store.MXMemoryStore;
import org.matrix.androidsdk.db.MXMediaCache;
import org.matrix.androidsdk.groups.GroupsManager;
import org.matrix.androidsdk.listeners.IMXEventListener;
import org.matrix.androidsdk.network.NetworkConnectivityReceiver;
import org.matrix.androidsdk.rest.client.AccountDataRestClient;
import org.matrix.androidsdk.rest.client.EventsRestClient;
import org.matrix.androidsdk.rest.client.PresenceRestClient;
import org.matrix.androidsdk.rest.client.ProfileRestClient;
import org.matrix.androidsdk.rest.client.RoomsRestClient;
import org.matrix.androidsdk.rest.model.ChunkEvents;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.ReceiptData;
import org.matrix.androidsdk.rest.model.RoomAliasDescription;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.rest.model.bingrules.BingRule;
import org.matrix.androidsdk.rest.model.bingrules.PushRuleSet;
import org.matrix.androidsdk.rest.model.bingrules.PushRulesResponse;
import org.matrix.androidsdk.rest.model.filter.FilterBody;
import org.matrix.androidsdk.rest.model.filter.RoomEventFilter;
import org.matrix.androidsdk.rest.model.filter.RoomFilter;
import org.matrix.androidsdk.rest.model.group.InvitedGroupSync;
import org.matrix.androidsdk.rest.model.login.Credentials;
import org.matrix.androidsdk.rest.model.sync.AccountDataElement;
import org.matrix.androidsdk.rest.model.sync.InvitedRoomSync;
import org.matrix.androidsdk.rest.model.sync.SyncResponse;
import org.matrix.androidsdk.ssl.UnrecognizedCertificateException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The data handler provides a layer to help manage matrix input and output.
 * <ul>
 * <li>Handles events</li>
 * <li>Stores the data in its storage layer</li>
 * <li>Provides the means for an app to get callbacks for data changes</li>
 * </ul>
 */
public class MXDataHandler implements CryptoDataHandler, DataHandlerInterface {
    private static final String LOG_TAG = MXDataHandler.class.getSimpleName();

    public interface RequestNetworkErrorListener {
        /**
         * Call there is a configuration error.
         *
         * @param matrixErrorCode the matrix error code
         */
        void onConfigurationError(String matrixErrorCode);

        /**
         * Call when the requests are rejected after a SSL update
         *
         * @param exception the exception
         */
        void onSSLCertificateError(UnrecognizedCertificateException exception);
    }

    private MxEventDispatcher mMxEventDispatcher;

    private final IMXStore mStore;
    private final Credentials mCredentials;
    private volatile String mInitialSyncToToken = null;
    private DataRetriever mDataRetriever;
    private BingRulesManager mBingRulesManager;
    private MXCallsManager mCallsManager;
    private MXMediaCache mMediaCache;

    private MetricsListener mMetricsListener;

    private ProfileRestClient mProfileRestClient;
    private PresenceRestClient mPresenceRestClient;
    private RoomsRestClient mRoomsRestClient;
    private EventsRestClient mEventsRestClient;
    private AccountDataRestClient mAccountDataRestClient;

    private NetworkConnectivityReceiver mNetworkConnectivityReceiver;

    private MyUser mMyUser;

    private HandlerThread mSyncHandlerThread;
    private final MXOsHandler mSyncHandler;

    // list of ignored users
    // null -> not initialized
    // should be retrieved from the store
    private List<String> mIgnoredUserIdsList;

    // list all the roomIds of the current direct chat rooms
    private List<String> mLocalDirectChatRoomIdsList = null;

    private boolean mIsAlive = true;

    private RequestNetworkErrorListener mRequestNetworkErrorListener;

    // the left rooms are managed
    // by default, they are not supported
    private boolean mAreLeftRoomsSynced;

    //
    private final List<ApiCallback<Void>> mLeftRoomsRefreshCallbacks = new ArrayList<>();
    private boolean mIsRetrievingLeftRooms;

    // the left rooms are saved in a dedicated store.
    private final MXMemoryStore mLeftRoomsStore;

    // e2e decoder
    private MXCrypto mCrypto;

    // the crypto is only started when the sync did not retrieve new device
    private boolean mIsStartingCryptoWithInitialSync = false;

    // groups manager
    private GroupsManager mGroupsManager;

    // Resource limit exceeded error
    @Nullable
    private MatrixError mResourceLimitExceededError;

    // tell if the lazy loading is enabled
    private boolean mIsLazyLoadingEnabled;

    // Filter for pagination
    @Nullable
    private RoomEventFilter mCustomPaginationFilter;

    /**
     * Default constructor.
     *
     * @param store       the data storage implementation.
     * @param credentials the credentials
     */
    public MXDataHandler(IMXStore store, Credentials credentials) {
        mStore = store;
        mCredentials = credentials;

        mMxEventDispatcher = new MxEventDispatcher();

        mSyncHandlerThread = new HandlerThread("MXDataHandler" + mCredentials.userId, Thread.MIN_PRIORITY);
        mSyncHandlerThread.start();
        mSyncHandler = new MXOsHandler(mSyncHandlerThread.getLooper());
        mLeftRoomsStore = new MXMemoryStore(credentials, store.getContext());
    }

    public void setLazyLoadingEnabled(boolean enabled) {
        mIsLazyLoadingEnabled = enabled;
    }

    public boolean isLazyLoadingEnabled() {
        return mIsLazyLoadingEnabled;
    }

    /**
     * Set the network error listener.
     *
     * @param requestNetworkErrorListener the network error listener
     */
    public void setRequestNetworkErrorListener(RequestNetworkErrorListener requestNetworkErrorListener) {
        mRequestNetworkErrorListener = requestNetworkErrorListener;
    }

    /**
     * Update the metrics listener
     *
     * @param metricsListener the metrics listener
     */
    public void setMetricsListener(MetricsListener metricsListener) {
        mMetricsListener = metricsListener;
    }

    /**
     * @return the credentials
     */
    public Credentials getCredentials() {
        return mCredentials;
    }

    /**
     * Update the profile Rest client.
     *
     * @param profileRestClient the REST client
     */
    public void setProfileRestClient(ProfileRestClient profileRestClient) {
        mProfileRestClient = profileRestClient;
    }

    /**
     * @return the profile REST client
     */
    public ProfileRestClient getProfileRestClient() {
        return mProfileRestClient;
    }

    /**
     * Update the presence Rest client.
     *
     * @param presenceRestClient the REST client
     */
    public void setPresenceRestClient(PresenceRestClient presenceRestClient) {
        mPresenceRestClient = presenceRestClient;
    }

    /**
     * @return the presence REST client
     */
    public PresenceRestClient getPresenceRestClient() {
        return mPresenceRestClient;
    }

    /**
     * Update the rooms Rest client.
     *
     * @param roomsRestClient the rooms client
     */
    public void setRoomsRestClient(RoomsRestClient roomsRestClient) {
        mRoomsRestClient = roomsRestClient;
    }

    /**
     * Update the events Rest client.
     *
     * @param eventsRestClient the events client
     */
    public void setEventsRestClient(EventsRestClient eventsRestClient) {
        mEventsRestClient = eventsRestClient;
    }

    /**
     * Update the account data Rest client.
     *
     * @param accountDataRestClient the account data client
     */
    public void setAccountDataRestClient(AccountDataRestClient accountDataRestClient) {
        mAccountDataRestClient = accountDataRestClient;
    }

    /**
     * Update the network connectivity receiver.
     *
     * @param networkConnectivityReceiver the network connectivity receiver
     */
    public void setNetworkConnectivityReceiver(NetworkConnectivityReceiver networkConnectivityReceiver) {
        mNetworkConnectivityReceiver = networkConnectivityReceiver;

        if (null != getCrypto()) {
            getCrypto().setNetworkConnectivityReceiver(mNetworkConnectivityReceiver);
        }
    }

    /**
     * Set the groups manager.
     *
     * @param groupsManager the groups manager
     */
    public void setGroupsManager(GroupsManager groupsManager) {
        mGroupsManager = groupsManager;
    }

    /**
     * @return the crypto engine
     */
    public MXCrypto getCrypto() {
        return mCrypto;
    }

    /**
     * Update the crypto engine.
     *
     * @param crypto the crypto engine
     */
    public void setCrypto(MXCrypto crypto) {
        mCrypto = crypto;
    }

    /**
     * @return true if the crypto is enabled
     */
    public boolean isCryptoEnabled() {
        return null != mCrypto;
    }

    /**
     * Provide the list of user Ids to ignore.
     * The result cannot be null.
     *
     * @return the user Ids list
     */
    public List<String> getIgnoredUserIds() {
        if (null == mIgnoredUserIdsList) {
            mIgnoredUserIdsList = mStore.getIgnoredUserIdsList();
        }

        // avoid the null case
        if (null == mIgnoredUserIdsList) {
            mIgnoredUserIdsList = new ArrayList<>();
        }

        return mIgnoredUserIdsList;
    }

    /**
     * Test if the current instance is still active.
     * When the session is closed, many objects keep a reference to this class
     * to dispatch events : isAlive() should be called before calling a method of this class.
     */
    private void checkIfAlive() {
        synchronized (this) {
            if (!mIsAlive) {
                Log.e(LOG_TAG, "use of a released dataHandler", new Exception("use of a released dataHandler"));
                //throw new AssertionError("Should not used a MXDataHandler");
            }
        }
    }

    /**
     * Tell if the current instance is still active.
     * When the session is closed, many objects keep a reference to this class
     * to dispatch events : isAlive() should be called before calling a method of this class.
     *
     * @return true if it is active.
     */
    public boolean isAlive() {
        synchronized (this) {
            return mIsAlive;
        }
    }

    /**
     * Dispatch the configuration error.
     *
     * @param matrixErrorCode the matrix error code.
     */
    @Override
    public void onConfigurationError(String matrixErrorCode) {
        if (null != mRequestNetworkErrorListener) {
            mRequestNetworkErrorListener.onConfigurationError(matrixErrorCode);
        }
    }

    /**
     * Call when the requests are rejected after a SSL update.
     *
     * @param exception the SSL certificate exception
     */
    @Override
    public void onSSLCertificateError(UnrecognizedCertificateException exception) {
        if (null != mRequestNetworkErrorListener) {
            mRequestNetworkErrorListener.onSSLCertificateError(exception);
        }
    }

    /**
     * Get the last resource limit exceeded error if any or null
     *
     * @return the last resource limit exceeded error if any or null
     */
    @Nullable
    public MatrixError getResourceLimitExceededError() {
        return mResourceLimitExceededError;
    }

    /**
     * Get the session's current user. The MyUser object provides methods for updating user properties which are not possible for other users.
     *
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
                store.setAvatarURL(mMyUser.getAvatarUrl(), System.currentTimeMillis());
                store.setDisplayName(mMyUser.displayname, System.currentTimeMillis());
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
                store.setAvatarURL(mMyUser.getAvatarUrl(), System.currentTimeMillis());
                store.setDisplayName(mMyUser.displayname, System.currentTimeMillis());
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
        return (null != mInitialSyncToToken);
    }

    /**
     * @return the DataRetriever.
     */
    public DataRetriever getDataRetriever() {
        checkIfAlive();
        return mDataRetriever;
    }

    /**
     * Update the dataRetriever.
     *
     * @param dataRetriever the dataRetriever.
     */
    public void setDataRetriever(DataRetriever dataRetriever) {
        checkIfAlive();
        mDataRetriever = dataRetriever;
    }

    /**
     * Update the push rules manager.
     *
     * @param bingRulesManager the new push rules manager.
     */
    public void setPushRulesManager(BingRulesManager bingRulesManager) {
        if (isAlive()) {
            mBingRulesManager = bingRulesManager;

            mBingRulesManager.loadRules(new SimpleApiCallback<Void>() {
                @Override
                public void onSuccess(Void info) {
                    onBingRulesUpdate();
                }
            });
        }
    }

    /**
     * Update the calls manager.
     *
     * @param callsManager the new calls manager.
     */
    public void setCallsManager(MXCallsManager callsManager) {
        checkIfAlive();
        mCallsManager = callsManager;
    }

    /**
     * @return the user calls manager.
     */
    public MXCallsManager getCallsManager() {
        checkIfAlive();
        return mCallsManager;
    }

    /**
     * Update the media cache.
     *
     * @param mediaCache the new media cache.
     */
    public void setMediaCache(MXMediaCache mediaCache) {
        checkIfAlive();
        mMediaCache = mediaCache;
    }

    /**
     * Retrieve the media cache.
     *
     * @return the used mediaCache
     */
    public MXMediaCache getMediaCache() {
        checkIfAlive();
        return mMediaCache;
    }

    /**
     * @return the used push rules set.
     */
    @Nullable
    public PushRuleSet pushRules() {
        if (isAlive() && (null != mBingRulesManager)) {
            return mBingRulesManager.pushRules();
        }

        return null;
    }

    /**
     * Trigger a push rules refresh.
     */
    public void refreshPushRules() {
        if (isAlive() && (null != mBingRulesManager)) {
            mBingRulesManager.loadRules(new SimpleApiCallback<Void>() {
                @Override
                public void onSuccess(Void info) {
                    onBingRulesUpdate();
                }
            });
        }
    }

    /**
     * @return the used BingRulesManager.
     */
    public BingRulesManager getBingRulesManager() {
        checkIfAlive();
        return mBingRulesManager;
    }

    /**
     * Set the crypto events listener, or remove it
     *
     * @param listener the listener or null to remove the listener
     */
    @Override
    public void setCryptoEventsListener(@Nullable CryptoEventListener listener) {
        mMxEventDispatcher.setCryptoEventsListener(listener);
    }

    /**
     * Add a listener to the listeners list.
     *
     * @param listener the listener to add.
     */
    public void addListener(IMXEventListener listener) {
        if (isAlive() && (null != listener)) {
            synchronized (mMxEventDispatcher) {
                mMxEventDispatcher.addListener(listener);
            }

            if (null != mInitialSyncToToken) {
                listener.onInitialSyncComplete(mInitialSyncToToken);
            }
        }
    }

    /**
     * Remove a listener from the listeners list.
     *
     * @param listener to remove.
     */
    public void removeListener(IMXEventListener listener) {
        if (isAlive() && (null != listener)) {
            synchronized (mMxEventDispatcher) {
                mMxEventDispatcher.removeListener(listener);
            }
        }
    }

    /**
     * Clear the instance data.
     */
    public void clear() {
        synchronized (mMxEventDispatcher) {
            mIsAlive = false;
            // remove any listener
            mMxEventDispatcher.clearListeners();
        }

        // clear the store
        mStore.close();
        mStore.clear();

        if (null != mSyncHandlerThread) {
            mSyncHandlerThread.quit();
            mSyncHandlerThread = null;
        }
    }

    /**
     * @return the current user id.
     */
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
    void checkPermanentStorageData() {
        if (!isAlive()) {
            Log.e(LOG_TAG, "checkPermanentStorageData : the session is not anymore active");
            return;
        }

        // When the data are extracted from a persistent storage,
        // some fields are not retrieved :
        // They are used to retrieve some data
        // so add the missing links.
        Collection<RoomSummary> summaries = mStore.getSummaries();
        for (RoomSummary summary : summaries) {
            if (null != summary.getLatestRoomState()) {
                summary.getLatestRoomState().setDataHandler(this);
            }
        }
    }


    /**
     * @return the used store.
     */
    @Override
    @Nullable
    public IMXStore getStore() {
        if (isAlive()) {
            return mStore;
        } else {
            Log.e(LOG_TAG, "getStore : the session is not anymore active");
            return null;
        }
    }

    /**
     * Provides the store in which the room is stored.
     *
     * @param roomId the room id
     * @return the used store
     */
    public IMXStore getStore(String roomId) {
        if (isAlive()) {
            if (null == roomId) {
                return mStore;
            } else {
                if (null != mLeftRoomsStore.getRoom(roomId)) {
                    return mLeftRoomsStore;
                } else {
                    return mStore;
                }
            }
        } else {
            Log.e(LOG_TAG, "getStore : the session is not anymore active");
            return null;
        }
    }

    /**
     * Returns the member with userID;
     *
     * @param members the members List
     * @param userID  the user ID
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
     *
     * @param roomId the room ID
     * @return true it exists.
     */
    public boolean doesRoomExist(String roomId) {
        return (null != roomId) && (null != mStore.getRoom(roomId));
    }

    /**
     * @return the left rooms
     */
    public Collection<Room> getLeftRooms() {
        return new ArrayList<>(mLeftRoomsStore.getRooms());
    }

    /**
     * Get the room object for the corresponding room id. Creates and initializes the object if there is none.
     *
     * @param roomId the room id
     * @return the corresponding room
     */
    @Override
    public Room getRoom(String roomId) {
        return getRoom(roomId, true);
    }

    /**
     * Get the room object for the corresponding room id.
     * The left rooms are not included.
     *
     * @param roomId the room id
     * @param create create the room it does not exist.
     * @return the corresponding room
     */
    public Room getRoom(String roomId, boolean create) {
        return getRoom(mStore, roomId, create);
    }

    /**
     * Get the room object for the corresponding room id.
     * By default, the left rooms are not included.
     *
     * @param roomId        the room id
     * @param testLeftRooms true to test if the room is a left room
     * @param create        create the room it does not exist.
     * @return the corresponding room
     */
    public Room getRoom(String roomId, boolean testLeftRooms, boolean create) {
        Room room = null;

        if (null != roomId) {
            room = mStore.getRoom(roomId);

            if ((null == room) && testLeftRooms) {
                room = mLeftRoomsStore.getRoom(roomId);
            }

            if ((null == room) && create) {
                room = getRoom(mStore, roomId, create);
            }
        }

        return room;
    }

    /**
     * Get the room object from the corresponding room id.
     *
     * @param store  the dedicated store
     * @param roomId the room id
     * @param create create the room it does not exist.
     * @return the corresponding room
     */
    public Room getRoom(IMXStore store, String roomId, boolean create) {
        if (!isAlive()) {
            Log.e(LOG_TAG, "getRoom : the session is not anymore active");
            return null;
        }

        // sanity check
        if (TextUtils.isEmpty(roomId)) {
            return null;
        }

        Room room;

        synchronized (this) {
            room = store.getRoom(roomId);
            if ((room == null) && create) {
                Log.d(LOG_TAG, "## getRoom() : create the room " + roomId);
                room = new Room(this, store, roomId);
                store.storeRoom(room);
            } else if ((null != room) && (null == room.getDataHandler())) {
                // GA reports that some rooms have no data handler
                // so ensure that it is not properly set
                Log.e(LOG_TAG, "getRoom " + roomId + " was not initialized");
                store.storeRoom(room);
            }
        }

        return room;
    }

    /**
     * Provides the room summaries list.
     *
     * @param withLeftOnes set to true to include the left rooms
     * @return the room summaries
     */
    public Collection<RoomSummary> getSummaries(boolean withLeftOnes) {
        List<RoomSummary> summaries = new ArrayList<>();

        summaries.addAll(getStore().getSummaries());

        if (withLeftOnes) {
            summaries.addAll(mLeftRoomsStore.getSummaries());
        }

        return summaries;
    }

    /**
     * Retrieve a room Id by its alias.
     *
     * @param roomAlias the room alias
     * @param callback  the asynchronous callback
     */
    public void roomIdByAlias(final String roomAlias, final ApiCallback<String> callback) {
        String roomId = null;

        Collection<Room> rooms = getStore().getRooms();

        for (Room room : rooms) {
            if (TextUtils.equals(room.getState().getCanonicalAlias(), roomAlias)) {
                roomId = room.getRoomId();
                break;
            } else {
                // getAliases cannot be null
                List<String> aliases = room.getState().getAliases();

                for (String alias : aliases) {
                    if (TextUtils.equals(alias, roomAlias)) {
                        roomId = room.getRoomId();
                        break;
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
            mRoomsRestClient.getRoomIdByAlias(roomAlias, new SimpleApiCallback<RoomAliasDescription>(callback) {
                @Override
                public void onSuccess(RoomAliasDescription info) {
                    callback.onSuccess(info.room_id);
                }
            });
        }

    }

    /**
     * Get the members of a Room with a request to the server. it will exclude the members who has left the room
     *
     * @param roomId   the id of the room
     * @param callback the callback
     */
    public void getMembersAsync(final String roomId, final ApiCallback<List<RoomMember>> callback) {
        // We do not pass the event stream token because of race condition issue. It's ok to pass null as a stream token
        mRoomsRestClient.getRoomMembers(roomId, null, null, RoomMember.MEMBERSHIP_LEAVE,
                new SimpleApiCallback<ChunkEvents>(callback) {
                    @Override
                    public void onSuccess(ChunkEvents info) {
                        Room room = getRoom(roomId);

                        if (info.chunk != null) {
                            for (Event event : info.chunk) {
                                room.getState().applyState(event, true, getStore());
                            }
                        }

                        callback.onSuccess(room.getState().getLoadedMembers());
                    }
                });
    }

    /**
     * Delete an event.
     *
     * @param event The event to be stored.
     */
    public void deleteRoomEvent(Event event) {
        if (isAlive()) {
            Room room = getRoom(event.roomId);

            if (null != room) {
                mStore.deleteEvent(event);
                Event lastEvent = mStore.getLatestEvent(event.roomId);
                RoomState beforeLiveRoomState = room.getState().deepCopy();

                RoomSummary summary = mStore.getSummary(event.roomId);
                if (null == summary) {
                    summary = new RoomSummary(null, lastEvent, beforeLiveRoomState, mCredentials.userId);
                } else {
                    summary.setLatestReceivedEvent(lastEvent, beforeLiveRoomState);
                }

                if (TextUtils.equals(summary.getReadReceiptEventId(), event.eventId)) {
                    summary.setReadReceiptEventId(lastEvent.eventId);
                }

                if (TextUtils.equals(summary.getReadMarkerEventId(), event.eventId)) {
                    summary.setReadMarkerEventId(lastEvent.eventId);
                }

                mStore.storeSummary(summary);
            }
        } else {
            Log.e(LOG_TAG, "deleteRoomEvent : the session is not anymore active");
        }
    }

    /**
     * Return an user from his id.
     *
     * @param userId the user id;.
     * @return the user.
     */
    public User getUser(String userId) {
        if (!isAlive()) {
            Log.e(LOG_TAG, "getUser : the session is not anymore active");
            return null;
        } else {
            User user = mStore.getUser(userId);

            if (null == user) {
                user = mLeftRoomsStore.getUser(userId);
            }

            return user;
        }
    }

    //================================================================================
    // Account Data management
    //================================================================================

    /**
     * Manage the sync accountData field
     *
     * @param accountDataElements the account data not empty events list
     * @param isInitialSync       true if it is an initial sync response
     */
    private void manageAccountData(List<AccountDataElement> accountDataElements, boolean isInitialSync) {
        try {
            for (AccountDataElement accountDataElement : accountDataElements) {
                if (AccountDataElement.ACCOUNT_DATA_TYPE_IGNORED_USER_LIST.equals(accountDataElement.type)) {
                    // ignored users list
                    manageIgnoredUsers(accountDataElement, isInitialSync);
                } else if (AccountDataElement.ACCOUNT_DATA_TYPE_PUSH_RULES.equals(accountDataElement.type)) {
                    // push rules
                    managePushRulesUpdate(accountDataElement);
                } else if (AccountDataElement.ACCOUNT_DATA_TYPE_DIRECT_MESSAGES.equals(accountDataElement.type)) {
                    // direct messages rooms
                    manageDirectChatRooms(accountDataElement, isInitialSync);
                } else if (AccountDataElement.ACCOUNT_DATA_TYPE_PREVIEW_URLS.equals(accountDataElement.type)) {
                    // URL preview
                    manageUrlPreview(accountDataElement);
                } else if (AccountDataElement.ACCOUNT_DATA_TYPE_WIDGETS.equals(accountDataElement.type)) {
                    // User widgets
                    manageUserWidgets(accountDataElement);
                } else if (AccountDataElement.ACCOUNT_DATA_TYPE_ACCEPTED_TERMS.equals(accountDataElement.type)) {
                    // Accepted terms
                    manageAcceptedTerms(accountDataElement);
                }
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "manageAccountData failed " + e.getMessage(), e);
        }
    }

    /**
     * Refresh the push rules from the account data events list
     *
     * @param accountDataElement the account data element
     */
    private void managePushRulesUpdate(AccountDataElement accountDataElement) {
        Gson gson = JsonUtils.getGson(false);

        // convert the data to PushRulesResponse
        // because BingRulesManager supports only PushRulesResponse
        JsonElement element = gson.toJsonTree(accountDataElement.content);
        getBingRulesManager().buildRules(gson.fromJson(element, PushRulesResponse.class));

        // warn the client that the push rules have been updated
        onBingRulesUpdate();
    }

    private void manageAcceptedTerms(AccountDataElement accountDataElement) {
        // Nothing to do
    }

    /**
     * Check if the ignored users list is updated
     *
     * @param accountDataElement the account data element of correct type
     */
    private void manageIgnoredUsers(AccountDataElement accountDataElement, boolean isInitialSync) {
        List<String> newIgnoredUsers = null;

        // Extract the ignored users list from the account data events list.
        if (accountDataElement.content.containsKey(AccountDataElement.ACCOUNT_DATA_KEY_IGNORED_USERS)) {
            Map<String, Object> ignored_users = (Map<String, Object>) accountDataElement.content.get(AccountDataElement.ACCOUNT_DATA_KEY_IGNORED_USERS);

            if (null != ignored_users) {
                newIgnoredUsers = new ArrayList<>(ignored_users.keySet());
            }
        }

        if (null != newIgnoredUsers) {
            List<String> curIgnoredUsers = getIgnoredUserIds();

            // the both lists are not empty
            if ((0 != newIgnoredUsers.size()) || (0 != curIgnoredUsers.size())) {
                // check if the ignored users list has been updated
                if ((newIgnoredUsers.size() != curIgnoredUsers.size()) || !newIgnoredUsers.containsAll(curIgnoredUsers)) {
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

    /**
     * Extract the direct chat rooms list from the dedicated events.
     *
     * @param accountDataElement the account data element of correct type
     */
    private void manageDirectChatRooms(AccountDataElement accountDataElement, boolean isInitialSync) {
        Log.d(LOG_TAG, "## manageDirectChatRooms() : update direct chats map" + accountDataElement.content);

        Gson gson = JsonUtils.getGson(false);
        JsonElement element = gson.toJsonTree(accountDataElement.content);

        Map<String, List<String>> parsedList = gson.fromJson(element, new TypeToken<Map<String, List<String>>>() {
        }.getType());

        mStore.setDirectChatRoomsDict(parsedList);

        // reset the current list of the direct chat roomIDs
        // to update it
        mLocalDirectChatRoomIdsList = null;

        if (!isInitialSync) {
            // warn there is an update
            onDirectMessageChatRoomsListUpdate();
        }
    }

    /**
     * Manage the URL preview flag
     *
     * @param accountDataElement the account data element of correct type
     */
    private void manageUrlPreview(AccountDataElement accountDataElement) {
        Map<String, Object> contentDict = accountDataElement.content;

        Log.d(LOG_TAG, "## manageUrlPreview() : " + contentDict);
        boolean enable = true;
        if (contentDict.containsKey(AccountDataElement.ACCOUNT_DATA_KEY_URL_PREVIEW_DISABLE)) {
            enable = !((boolean) contentDict.get(AccountDataElement.ACCOUNT_DATA_KEY_URL_PREVIEW_DISABLE));
        }

        mStore.setURLPreviewEnabled(enable);
    }

    /**
     * Manage the user widgets
     *
     * @param accountDataElement the account data element of correct type
     */
    private void manageUserWidgets(AccountDataElement accountDataElement) {
        Map<String, Object> contentDict = accountDataElement.content;

        Log.d(LOG_TAG, "## manageUserWidgets() : " + contentDict);

        mStore.setUserWidgets(contentDict);
    }

    //================================================================================
    // Sync V2
    //================================================================================

    /**
     * Handle a presence event.
     *
     * @param presenceEvent the presence event.
     */
    private void handlePresenceEvent(Event presenceEvent) {
        // Presence event
        if (Event.EVENT_TYPE_PRESENCE.equals(presenceEvent.getType())) {
            User userPresence = JsonUtils.toUser(presenceEvent.getContent());

            // use the sender by default
            if (!TextUtils.isEmpty(presenceEvent.getSender())) {
                userPresence.user_id = presenceEvent.getSender();
            }

            User user = mStore.getUser(userPresence.user_id);

            if (user == null) {
                user = userPresence;
                user.setDataHandler(this);
            } else {
                user.currently_active = userPresence.currently_active;
                user.presence = userPresence.presence;
                user.lastActiveAgo = userPresence.lastActiveAgo;
            }

            user.setLatestPresenceTs(System.currentTimeMillis());

            // check if the current user has been updated
            if (mCredentials.userId.equals(user.user_id)) {
                // always use the up-to-date information
                getMyUser().displayname = user.displayname;
                getMyUser().avatar_url = user.getAvatarUrl();

                mStore.setAvatarURL(user.getAvatarUrl(), presenceEvent.getOriginServerTs());
                mStore.setDisplayName(user.displayname, presenceEvent.getOriginServerTs());
            }

            mStore.storeUser(user);
            onPresenceUpdate(presenceEvent, user);
        }
    }

    /**
     * Manage a syncResponse.
     *
     * @param syncResponse the syncResponse to manage.
     * @param fromToken    the start sync token
     * @param isCatchingUp true when there is a pending catch-up
     */
    public void onSyncResponse(final SyncResponse syncResponse, final String fromToken, final boolean isCatchingUp) {
        // perform the sync in background
        // to avoid UI thread lags.
        mSyncHandler.post(new Runnable() {
            @Override
            public void run() {
                manageResponse(syncResponse, fromToken, isCatchingUp);
            }
        });
    }

    /**
     * Delete a room from its room id.
     * The room data is copied into the left rooms store.
     *
     * @param roomId the room id
     */
    public void deleteRoom(String roomId) {
        // copy the room from a store to another one
        Room r = getStore().getRoom(roomId);

        if (null != r) {
            if (mAreLeftRoomsSynced) {
                Room leftRoom = getRoom(mLeftRoomsStore, roomId, true);
                leftRoom.setIsLeft(true);

                // copy the summary
                RoomSummary summary = getStore().getSummary(roomId);
                if (null != summary) {
                    mLeftRoomsStore.storeSummary(new RoomSummary(summary, summary.getLatestReceivedEvent(), summary.getLatestRoomState(), getUserId()));
                }

                // copy events and receiptData
                // it is not required but it is better, it could be useful later
                // the room summary should be enough to be displayed in the recent pages
                List<ReceiptData> receipts = new ArrayList<>();
                Collection<Event> events = getStore().getRoomMessages(roomId);

                if (null != events) {
                    for (Event e : events) {
                        receipts.addAll(getStore().getEventReceipts(roomId, e.eventId, false, false));
                        mLeftRoomsStore.storeLiveRoomEvent(e);
                    }

                    for (ReceiptData receipt : receipts) {
                        mLeftRoomsStore.storeReceipt(receipt, roomId);
                    }
                }

                // copy the state
                leftRoom.getTimeline().setState(r.getTimeline().getState());
            }

            // remove the previous definition
            getStore().deleteRoom(roomId);
        }
    }

    /**
     * Manage the sync response in a background thread.
     *
     * @param syncResponse the syncResponse to manage.
     * @param fromToken    the start sync token
     * @param isCatchingUp true when there is a pending catch-up
     */
    private void manageResponse(final SyncResponse syncResponse, final String fromToken, final boolean isCatchingUp) {
        if (!isAlive()) {
            Log.e(LOG_TAG, "manageResponse : ignored because the session has been closed");
            return;
        }

        boolean isInitialSync = (null == fromToken);
        boolean isEmptyResponse = true;

        // sanity check
        if (null != syncResponse) {
            Log.d(LOG_TAG, "onSyncComplete");

            // Handle the to device events before the room ones
            // to ensure to decrypt them properly
            if ((null != syncResponse.toDevice)
                    && (null != syncResponse.toDevice.events)
                    && (syncResponse.toDevice.events.size() > 0)) {
                Log.d(LOG_TAG, "manageResponse : receives " + syncResponse.toDevice.events.size() + " toDevice events");

                for (Event toDeviceEvent : syncResponse.toDevice.events) {
                    handleToDeviceEvent(toDeviceEvent);
                }
            }

            // Handle account data before the room events
            // to be able to update direct chats dictionary during invites handling.
            if (syncResponse.accountData != null
                    && syncResponse.accountData.accountDataElements != null
                    && !syncResponse.accountData.accountDataElements.isEmpty()) {
                Log.d(LOG_TAG, "Received " + syncResponse.accountData.accountDataElements.size() + " accountData events");
                manageAccountData(syncResponse.accountData.accountDataElements, isInitialSync);

                // Global management, to be sure to handle any account data
                getStore().storeAccountData(syncResponse.accountData);

                for (AccountDataElement accountDataElement : syncResponse.accountData.accountDataElements) {
                    mMxEventDispatcher.dispatchOnAccountDataUpdate(accountDataElement);
                }
            }

            // sanity check
            if (null != syncResponse.rooms) {
                // joined rooms events
                if ((null != syncResponse.rooms.join) && (syncResponse.rooms.join.size() > 0)) {
                    Log.d(LOG_TAG, "Received " + syncResponse.rooms.join.size() + " joined rooms");
                    if (mMetricsListener != null) {
                        mMetricsListener.onRoomsLoaded(syncResponse.rooms.join.size());
                    }
                    Set<String> roomIds = syncResponse.rooms.join.keySet();
                    // Handle first joined rooms
                    for (String roomId : roomIds) {
                        try {
                            if (null != mLeftRoomsStore.getRoom(roomId)) {
                                Log.d(LOG_TAG, "the room " + roomId + " moves from left to the joined ones");
                                mLeftRoomsStore.deleteRoom(roomId);
                            }

                            getRoom(roomId).handleJoinedRoomSync(syncResponse.rooms.join.get(roomId), isInitialSync);
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "## manageResponse() : handleJoinedRoomSync failed " + e.getMessage() + " for room " + roomId, e);
                        }
                    }

                    isEmptyResponse = false;
                }

                // invited room management
                if ((null != syncResponse.rooms.invite) && (syncResponse.rooms.invite.size() > 0)) {
                    Log.d(LOG_TAG, "Received " + syncResponse.rooms.invite.size() + " invited rooms");

                    Set<String> roomIds = syncResponse.rooms.invite.keySet();

                    Map<String, List<String>> updatedDirectChatRoomsDict = null;
                    boolean hasChanged = false;

                    for (String roomId : roomIds) {
                        try {
                            Log.d(LOG_TAG, "## manageResponse() : the user has been invited to " + roomId);

                            if (null != mLeftRoomsStore.getRoom(roomId)) {
                                Log.d(LOG_TAG, "the room " + roomId + " moves from left to the invited ones");
                                mLeftRoomsStore.deleteRoom(roomId);
                            }

                            Room room = getRoom(roomId);
                            InvitedRoomSync invitedRoomSync = syncResponse.rooms.invite.get(roomId);

                            room.handleInvitedRoomSync(invitedRoomSync);

                            // Handle here the invites to a direct chat.
                            if (room.isDirectChatInvitation()) {
                                // Retrieve the inviter user id.
                                String participantUserId = null;
                                for (Event event : invitedRoomSync.inviteState.events) {
                                    if (null != event.sender) {
                                        participantUserId = event.sender;
                                        break;
                                    }
                                }

                                if (null != participantUserId) {
                                    // Prepare the updated dictionary.
                                    if (null == updatedDirectChatRoomsDict) {
                                        if (null != getStore().getDirectChatRoomsDict()) {
                                            // Consider the current dictionary.
                                            updatedDirectChatRoomsDict = new HashMap<>(getStore().getDirectChatRoomsDict());
                                        } else {
                                            updatedDirectChatRoomsDict = new HashMap<>();
                                        }
                                    }

                                    List<String> roomIdsList;
                                    if (updatedDirectChatRoomsDict.containsKey(participantUserId)) {
                                        roomIdsList = new ArrayList<>(updatedDirectChatRoomsDict.get(participantUserId));
                                    } else {
                                        roomIdsList = new ArrayList<>();
                                    }

                                    // Check whether the room was not yet seen as direct chat
                                    if (roomIdsList.indexOf(roomId) < 0) {
                                        Log.d(LOG_TAG, "## manageResponse() : add this new invite in direct chats");

                                        roomIdsList.add(roomId); // update room list with the new room
                                        updatedDirectChatRoomsDict.put(participantUserId, roomIdsList);
                                        hasChanged = true;
                                    }
                                }
                            }
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "## manageResponse() : handleInvitedRoomSync failed " + e.getMessage() + " for room " + roomId, e);
                        }
                    }

                    isEmptyResponse = false;

                    if (hasChanged) {
                        // Update account data to add new direct chat room(s)
                        mAccountDataRestClient.setAccountData(mCredentials.userId, AccountDataElement.ACCOUNT_DATA_TYPE_DIRECT_MESSAGES,
                                updatedDirectChatRoomsDict, new ApiCallback<Void>() {
                                    @Override
                                    public void onSuccess(Void info) {
                                        Log.d(LOG_TAG, "## manageResponse() : succeeds");
                                    }

                                    @Override
                                    public void onNetworkError(Exception e) {
                                        Log.e(LOG_TAG, "## manageResponse() : update account data failed " + e.getMessage(), e);
                                        // TODO: we should try again.
                                    }

                                    @Override
                                    public void onMatrixError(MatrixError e) {
                                        Log.e(LOG_TAG, "## manageResponse() : update account data failed " + e.getMessage());
                                    }

                                    @Override
                                    public void onUnexpectedError(Exception e) {
                                        Log.e(LOG_TAG, "## manageResponse() : update account data failed " + e.getMessage(), e);
                                    }
                                });
                    }
                }

                // left room management
                // it should be done at the end but it seems there is a server issue
                // when inviting after leaving a room, the room is defined in the both leave & invite rooms list.
                if ((null != syncResponse.rooms.leave) && (syncResponse.rooms.leave.size() > 0)) {
                    Log.d(LOG_TAG, "Received " + syncResponse.rooms.leave.size() + " left rooms");

                    Set<String> roomIds = syncResponse.rooms.leave.keySet();

                    for (String roomId : roomIds) {
                        // RoomSync leftRoomSync = syncResponse.rooms.leave.get(roomId);

                        // Presently we remove the existing room from the rooms list.
                        // FIXME SYNC V2 Archive/Display the left rooms!
                        // For that create 'handleArchivedRoomSync' method

                        String membership = RoomMember.MEMBERSHIP_LEAVE;
                        Room room = getRoom(roomId);

                        // Retrieve existing room
                        // check if the room still exists.
                        if (null != room) {
                            // use 'handleJoinedRoomSync' to pass the last events to the room before leaving it.
                            // The room will then be able to notify its listeners.
                            room.handleJoinedRoomSync(syncResponse.rooms.leave.get(roomId), isInitialSync);

                            RoomMember member = room.getMember(getUserId());
                            if (null != member) {
                                membership = member.membership;
                            }

                            Log.d(LOG_TAG, "## manageResponse() : leave the room " + roomId);
                        }

                        if (!TextUtils.equals(membership, RoomMember.MEMBERSHIP_KICK) && !TextUtils.equals(membership, RoomMember.MEMBERSHIP_BAN)) {
                            // ensure that the room data are properly deleted
                            getStore().deleteRoom(roomId);
                            onLeaveRoom(roomId);
                        } else {
                            onRoomKick(roomId);
                        }

                        // don't add to the left rooms if the user has been kicked / banned
                        if ((mAreLeftRoomsSynced) && TextUtils.equals(membership, RoomMember.MEMBERSHIP_LEAVE)) {
                            Room leftRoom = getRoom(mLeftRoomsStore, roomId, true);
                            leftRoom.handleJoinedRoomSync(syncResponse.rooms.leave.get(roomId), isInitialSync);
                        }
                    }

                    isEmptyResponse = false;
                }
            }

            // groups
            if (null != syncResponse.groups) {
                // Handle invited groups
                if ((null != syncResponse.groups.invite) && !syncResponse.groups.invite.isEmpty()) {
                    // Handle invited groups
                    for (String groupId : syncResponse.groups.invite.keySet()) {
                        InvitedGroupSync invitedGroupSync = syncResponse.groups.invite.get(groupId);
                        mGroupsManager.onNewGroupInvitation(groupId, invitedGroupSync.profile, invitedGroupSync.inviter, !isInitialSync);
                    }
                }

                // Handle joined groups
                if ((null != syncResponse.groups.join) && !syncResponse.groups.join.isEmpty()) {
                    for (String groupId : syncResponse.groups.join.keySet()) {
                        mGroupsManager.onJoinGroup(groupId, !isInitialSync);
                    }
                }
                // Handle left groups
                if ((null != syncResponse.groups.leave) && !syncResponse.groups.leave.isEmpty()) {
                    // Handle joined groups
                    for (String groupId : syncResponse.groups.leave.keySet()) {
                        mGroupsManager.onLeaveGroup(groupId, !isInitialSync);
                    }
                }
            }

            // Handle presence of other users
            if ((null != syncResponse.presence) && (null != syncResponse.presence.events)) {
                Log.d(LOG_TAG, "Received " + syncResponse.presence.events.size() + " presence events");
                for (Event presenceEvent : syncResponse.presence.events) {
                    handlePresenceEvent(presenceEvent);
                }
            }

            if (null != mCrypto) {
                mCrypto.onSyncCompleted(syncResponse, fromToken, isCatchingUp);
            }

            IMXStore store = getStore();

            if (!isEmptyResponse && (null != store)) {
                store.setEventStreamToken(syncResponse.nextBatch);
                store.commit();
            }
        }

        if (isInitialSync) {
            if (!isCatchingUp) {
                startCrypto(true);
            } else {
                // the events thread sends a dummy initial sync event
                // when the application is restarted.
                mIsStartingCryptoWithInitialSync = !isEmptyResponse;
            }

            onInitialSyncComplete((null != syncResponse) ? syncResponse.nextBatch : null);
        } else {

            if (!isCatchingUp) {
                startCrypto(mIsStartingCryptoWithInitialSync);
            }

            try {
                onLiveEventsChunkProcessed(fromToken, (null != syncResponse) ? syncResponse.nextBatch : fromToken);
            } catch (Exception e) {
                Log.e(LOG_TAG, "onLiveEventsChunkProcessed failed " + e.getMessage(), e);
            }

            try {
                // check if an incoming call has been received
                mCallsManager.checkPendingIncomingCalls();
            } catch (Exception e) {
                Log.e(LOG_TAG, "checkPendingIncomingCalls failed " + e + " " + e.getMessage(), e);
            }
        }
    }

    /**
     * Refresh the unread summary counters of the updated rooms.
     */
    private void refreshUnreadCounters() {
        Set<String> roomIdsList;

        synchronized (mUpdatedRoomIdList) {
            roomIdsList = new HashSet<>(mUpdatedRoomIdList);
            mUpdatedRoomIdList.clear();
        }
        // refresh the unread counter
        for (String roomId : roomIdsList) {
            Room room = mStore.getRoom(roomId);

            if (null != room) {
                room.refreshUnreadCounter();
            }
        }
    }

    /**
     * @return true if the historical rooms loaded
     */
    public boolean areLeftRoomsSynced() {
        return mAreLeftRoomsSynced;
    }

    /**
     * @return true if the left rooms are retrieving
     */
    public boolean isRetrievingLeftRooms() {
        return mIsRetrievingLeftRooms;
    }

    /**
     * Release the left rooms store
     */
    public void releaseLeftRooms() {
        if (mAreLeftRoomsSynced) {
            mLeftRoomsStore.clear();
            mAreLeftRoomsSynced = false;
        }
    }

    /**
     * Retrieve the historical rooms
     *
     * @param callback the asynchronous callback.
     */
    public void retrieveLeftRooms(ApiCallback<Void> callback) {
        // already loaded
        if (mAreLeftRoomsSynced) {
            if (null != callback) {
                callback.onSuccess(null);
            }
        } else {
            int count;

            synchronized (mLeftRoomsRefreshCallbacks) {
                if (null != callback) {
                    mLeftRoomsRefreshCallbacks.add(callback);
                }
                count = mLeftRoomsRefreshCallbacks.size();
            }

            // start the request only for the first listener
            if (1 == count) {
                mIsRetrievingLeftRooms = true;

                Log.d(LOG_TAG, "## refreshHistoricalRoomsList() : requesting");

                mEventsRestClient.syncFromToken(null, 0, 30000, null, getLeftRoomsFilter(), new ApiCallback<SyncResponse>() {
                    @Override
                    public void onSuccess(final SyncResponse syncResponse) {

                        Runnable r = new Runnable() {
                            @Override
                            public void run() {
                                if (null != syncResponse.rooms.leave) {
                                    Set<String> roomIds = syncResponse.rooms.leave.keySet();

                                    // Handle first joined rooms
                                    for (String roomId : roomIds) {
                                        Room room = getRoom(mLeftRoomsStore, roomId, true);

                                        // sanity check
                                        if (null != room) {
                                            room.setIsLeft(true);
                                            room.handleJoinedRoomSync(syncResponse.rooms.leave.get(roomId), true);

                                            RoomMember selfMember = room.getState().getMember(getUserId());

                                            // keep only the left rooms (i.e not the banned / kicked ones)
                                            if ((null == selfMember) || !TextUtils.equals(selfMember.membership, RoomMember.MEMBERSHIP_LEAVE)) {
                                                mLeftRoomsStore.deleteRoom(roomId);
                                            }
                                        }
                                    }

                                    Log.d(LOG_TAG, "## refreshHistoricalRoomsList() : " + mLeftRoomsStore.getRooms().size() + " left rooms");
                                }

                                mIsRetrievingLeftRooms = false;
                                mAreLeftRoomsSynced = true;

                                synchronized (mLeftRoomsRefreshCallbacks) {
                                    for (ApiCallback<Void> c : mLeftRoomsRefreshCallbacks) {
                                        c.onSuccess(null);
                                    }
                                    mLeftRoomsRefreshCallbacks.clear();
                                }
                            }
                        };

                        Thread t = new Thread(r);
                        t.setPriority(Thread.MIN_PRIORITY);
                        t.start();
                    }

                    @Override
                    public void onNetworkError(Exception e) {
                        synchronized (mLeftRoomsRefreshCallbacks) {
                            Log.e(LOG_TAG, "## refreshHistoricalRoomsList() : failed " + e.getMessage(), e);

                            for (ApiCallback<Void> c : mLeftRoomsRefreshCallbacks) {
                                c.onNetworkError(e);
                            }
                            mLeftRoomsRefreshCallbacks.clear();
                        }
                    }

                    @Override
                    public void onMatrixError(MatrixError e) {
                        synchronized (mLeftRoomsRefreshCallbacks) {
                            Log.e(LOG_TAG, "## refreshHistoricalRoomsList() : failed " + e.getMessage());

                            for (ApiCallback<Void> c : mLeftRoomsRefreshCallbacks) {
                                c.onMatrixError(e);
                            }
                            mLeftRoomsRefreshCallbacks.clear();
                        }
                    }

                    @Override
                    public void onUnexpectedError(Exception e) {
                        synchronized (mLeftRoomsRefreshCallbacks) {
                            Log.e(LOG_TAG, "## refreshHistoricalRoomsList() : failed " + e.getMessage(), e);

                            for (ApiCallback<Void> c : mLeftRoomsRefreshCallbacks) {
                                c.onUnexpectedError(e);
                            }
                            mLeftRoomsRefreshCallbacks.clear();
                        }
                    }
                });
            }
        }
    }

    /**
     * Return a filter to sync left room, depending if lazyloading is on or off
     * Without LL:
     * <pre>
     *     {
     *         "room": {
     *             "timeline": {
     *                 "limit": 1
     *                 },
     *             "include_leave":true
     *         }
     *     }
     * </pre>
     * With LL:
     * <pre>
     *     {
     *         "room": {
     *             "timeline": {
     *                 "limit": 1
     *                 },
     *             "include_leave":true,
     *             "state": {
     *                 "lazy_load_members": true
     *                 }
     *         }
     *     }
     * </pre>
     */
    private String getLeftRoomsFilter() {
        FilterBody filterBody = new FilterBody();
        filterBody.room = new RoomFilter();
        filterBody.room.timeline = new RoomEventFilter();
        filterBody.room.timeline.limit = 1;
        filterBody.room.includeLeave = true;

        FilterUtil.enableLazyLoading(filterBody, isLazyLoadingEnabled());

        return filterBody.toJSONString();
    }

    /* package */
    void setPaginationFilter(@Nullable RoomEventFilter paginationFilter) {
        mCustomPaginationFilter = paginationFilter;
    }

    /**
     * Get the pagination filter, which can be customized by the client. Lazy loading param is managed here.
     *
     * @return the RoomEventFilter to use for pagination
     */
    public RoomEventFilter getPaginationFilter() {
        if (mCustomPaginationFilter != null) {
            // Ensure lazy loading param is correct
            FilterUtil.enableLazyLoading(mCustomPaginationFilter, isLazyLoadingEnabled());
            return mCustomPaginationFilter;
        } else {
            return FilterUtil.createRoomEventFilter(isLazyLoadingEnabled());
        }
    }

    /*
     * Handle a 'toDevice' event
     * @param event the event
     */
    private void handleToDeviceEvent(Event event) {
        // Decrypt event if necessary
        decryptEvent(event, null);

        if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE)
                && (null != event.getContent())
                && TextUtils.equals(JsonUtils.getMessageMsgType(event.getContent()), "m.bad.encrypted")) {
            Log.e(LOG_TAG, "## handleToDeviceEvent() : Warning: Unable to decrypt to-device event : " + event.getContent());
        } else {
            onToDeviceEvent(event);
        }
    }

    /**
     * Decrypt an encrypted event
     *
     * @param event      the event to decrypt
     * @param timelineId the timeline identifier
     * @return true if the event has been decrypted
     */
    public boolean decryptEvent(Event event, String timelineId) {
        if ((null != event) && TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE_ENCRYPTED)) {
            if (null != getCrypto()) {
                MXEventDecryptionResult result = null;
                try {
                    result = getCrypto().decryptEvent(event, timelineId);
                } catch (MXDecryptionException exception) {
                    event.setCryptoError(exception.getCryptoError());
                }

                if (null != result) {
                    event.setClearData(result);
                    return true;
                }
            } else {
                event.setCryptoError(new MXCryptoError(MXCryptoError.ENCRYPTING_NOT_ENABLED_ERROR_CODE, MXCryptoError.ENCRYPTING_NOT_ENABLED_REASON, null));
            }
        }

        return false;
    }

    /**
     * Reset replay attack data for the given timeline.
     *
     * @param timelineId the timeline id
     */
    public void resetReplayAttackCheckInTimeline(String timelineId) {
        if ((null != timelineId) && (null != mCrypto) && (null != mCrypto.getOlmDevice())) {
            mCrypto.resetReplayAttackCheckInTimeline(timelineId);
        }
    }

    //================================================================================
    // Listeners management
    //================================================================================

    /**
     * Dispatch that the store is ready.
     */
    public void onStoreReady() {
        mMxEventDispatcher.dispatchOnStoreReady();
    }

    public void onAccountInfoUpdate(final MyUser myUser) {
        mMxEventDispatcher.dispatchOnAccountInfoUpdate(myUser);
    }

    public void onPresenceUpdate(final Event event, final User user) {
        mMxEventDispatcher.dispatchOnPresenceUpdate(event, user);
    }

    /**
     * Stored the room id of the rooms which have received some events
     * which imply an unread messages counter refresh.
     */
    private final Set<String> mUpdatedRoomIdList = new HashSet<>();

    /**
     * Tell if a room Id event should be ignored
     *
     * @param roomId the room id
     * @return true to do not dispatch the event.
     */
    private boolean ignoreEvent(String roomId) {
        if (mIsRetrievingLeftRooms && !TextUtils.isEmpty(roomId)) {
            return null != mLeftRoomsStore.getRoom(roomId);
        } else {
            return false;
        }
    }

    public void onLiveEvent(final Event event, final RoomState roomState) {
        if (ignoreEvent(event.roomId)) {
            return;
        }

        String type = event.getType();

        if (!TextUtils.equals(Event.EVENT_TYPE_TYPING, type)
                && !TextUtils.equals(Event.EVENT_TYPE_RECEIPT, type)) {
            synchronized (mUpdatedRoomIdList) {
                mUpdatedRoomIdList.add(roomState.roomId);
            }
        }

        mMxEventDispatcher.dispatchOnLiveEvent(event, roomState);
    }

    public void onLiveEventsChunkProcessed(final String startToken, final String toToken) {
        // reset the resource limit exceeded error
        mResourceLimitExceededError = null;

        refreshUnreadCounters();

        mMxEventDispatcher.dispatchOnLiveEventsChunkProcessed(startToken, toToken);
    }

    public void onBingEvent(final Event event, final RoomState roomState, final BingRule bingRule) {
        mMxEventDispatcher.dispatchOnBingEvent(event, roomState, bingRule, ignoreEvent(event.roomId));
    }

    /**
     * Update the event state and warn the listener if there is an update.
     *
     * @param event    the event
     * @param newState the new state
     */
    public void updateEventState(Event event, Event.SentState newState) {
        if ((null != event) && (event.mSentState != newState)) {
            event.mSentState = newState;
            //crash reported on app store
            if (getStore() != null) {
                getStore().flushRoomEvents(event.roomId);
            } else {
                Log.e(LOG_TAG, "#updateEventState Failed to access to store ");
            }

            onEventSentStateUpdated(event);
        }
    }

    public void onEventSentStateUpdated(final Event event) {
        mMxEventDispatcher.dispatchOnEventSentStateUpdated(event, ignoreEvent(event.roomId));
    }

    public void onEventSent(final Event event, final String prevEventId) {
        mMxEventDispatcher.dispatchOnEventSent(event, prevEventId, ignoreEvent(event.roomId));
    }

    public void onBingRulesUpdate() {
        mMxEventDispatcher.dispatchOnBingRulesUpdate();
    }

    /**
     * Start the crypto
     */
    private void startCrypto(final boolean isInitialSync) {
        if ((null != getCrypto()) && !getCrypto().isStarted() && !getCrypto().isStarting()) {
            getCrypto().setNetworkConnectivityReceiver(mNetworkConnectivityReceiver);
            getCrypto().start(isInitialSync, new ApiCallback<Void>() {
                @Override
                public void onSuccess(Void info) {
                    onCryptoSyncComplete();
                }

                private void onError(String errorMessage) {
                    Log.e(LOG_TAG, "## onInitialSyncComplete() : getCrypto().start fails " + errorMessage);
                }

                @Override
                public void onNetworkError(Exception e) {
                    onError(e.getMessage());
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    onError(e.getMessage());
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    onError(e.getMessage());
                }
            });
        }
    }

    public void onInitialSyncComplete(String toToken) {
        mInitialSyncToToken = toToken;

        refreshUnreadCounters();

        mMxEventDispatcher.dispatchOnInitialSyncComplete(toToken);
    }

    public void onSyncError(final MatrixError matrixError) {
        // Store the resource limit exceeded error
        if (MatrixError.RESOURCE_LIMIT_EXCEEDED.equals(matrixError.errcode)) {
            mResourceLimitExceededError = matrixError;
        }

        mMxEventDispatcher.dispatchOnSyncError(matrixError);
    }

    public void onCryptoSyncComplete() {
        mMxEventDispatcher.dispatchOnCryptoSyncComplete();
    }

    public void onNewRoom(final String roomId) {
        mMxEventDispatcher.dispatchOnNewRoom(roomId, ignoreEvent(roomId));
    }

    public void onJoinRoom(final String roomId) {
        mMxEventDispatcher.dispatchOnJoinRoom(roomId, ignoreEvent(roomId));
    }

    public void onRoomInternalUpdate(final String roomId) {
        mMxEventDispatcher.dispatchOnRoomInternalUpdate(roomId, ignoreEvent(roomId));
    }

    public void onNotificationCountUpdate(final String roomId) {
        mMxEventDispatcher.dispatchOnNotificationCountUpdate(roomId, ignoreEvent(roomId));
    }

    public void onLeaveRoom(final String roomId) {
        mMxEventDispatcher.dispatchOnLeaveRoom(roomId, ignoreEvent(roomId));
    }

    public void onRoomKick(final String roomId) {
        mMxEventDispatcher.dispatchOnRoomKick(roomId, ignoreEvent(roomId));
    }

    public void onReceiptEvent(final String roomId, final List<String> senderIds) {
        synchronized (mUpdatedRoomIdList) {
            // refresh the unread counter at the end of the process chunk
            mUpdatedRoomIdList.add(roomId);
        }

        mMxEventDispatcher.dispatchOnReceiptEvent(roomId, senderIds, ignoreEvent(roomId));
    }

    public void onRoomTagEvent(final String roomId) {
        mMxEventDispatcher.dispatchOnRoomTagEvent(roomId, ignoreEvent(roomId));
    }

    public void onReadMarkerEvent(final String roomId) {
        mMxEventDispatcher.dispatchOnReadMarkerEvent(roomId, ignoreEvent(roomId));
    }

    public void onRoomFlush(final String roomId) {
        mMxEventDispatcher.dispatchOnRoomFlush(roomId, ignoreEvent(roomId));
    }

    public void onIgnoredUsersListUpdate() {
        mMxEventDispatcher.dispatchOnIgnoredUsersListUpdate();
    }

    public void onToDeviceEvent(final Event event) {
        mMxEventDispatcher.dispatchOnToDeviceEvent(event, ignoreEvent(event.roomId));
    }

    public void onDirectMessageChatRoomsListUpdate() {
        mMxEventDispatcher.dispatchOnDirectMessageChatRoomsListUpdate();
    }

    @Override
    public void onEventDecrypted(final CryptoEvent event) {
        mMxEventDispatcher.dispatchOnEventDecrypted(event);
    }

    public void onNewGroupInvitation(final String groupId) {
        mMxEventDispatcher.dispatchOnNewGroupInvitation(groupId);
    }

    public void onJoinGroup(final String groupId) {
        mMxEventDispatcher.dispatchOnJoinGroup(groupId);
    }

    public void onLeaveGroup(final String groupId) {
        mMxEventDispatcher.dispatchOnLeaveGroup(groupId);
    }

    public void onGroupProfileUpdate(final String groupId) {
        mMxEventDispatcher.dispatchOnGroupProfileUpdate(groupId);
    }

    public void onGroupRoomsListUpdate(final String groupId) {
        mMxEventDispatcher.dispatchOnGroupRoomsListUpdate(groupId);
    }

    public void onGroupUsersListUpdate(final String groupId) {
        mMxEventDispatcher.dispatchOnGroupUsersListUpdate(groupId);
    }

    public void onGroupInvitedUsersListUpdate(final String groupId) {
        mMxEventDispatcher.dispatchOnGroupInvitedUsersListUpdate(groupId);
    }

    /**
     * @return the direct chat room ids list
     */
    public List<String> getDirectChatRoomIdsList() {
        if (null != mLocalDirectChatRoomIdsList) return mLocalDirectChatRoomIdsList;

        IMXStore store = getStore();
        List<String> directChatRoomIdsList = new ArrayList<>();

        if (null == store) {
            Log.e(LOG_TAG, "## getDirectChatRoomIdsList() : null store");
            return directChatRoomIdsList;
        }

        Collection<List<String>> listOfList = null;

        if (null != store.getDirectChatRoomsDict()) {
            listOfList = store.getDirectChatRoomsDict().values();
        }

        // if the direct messages entry has been defined
        if (null != listOfList) {
            for (List<String> list : listOfList) {
                for (String roomId : list) {
                    // test if the room is defined once
                    if ((directChatRoomIdsList.indexOf(roomId) < 0)) {
                        directChatRoomIdsList.add(roomId);
                    }
                }
            }
        }

        return mLocalDirectChatRoomIdsList = directChatRoomIdsList;
    }

    /**
     * Store and upload the provided direct chat rooms map.
     *
     * @param directChatRoomsMap the direct chats map
     * @param callback           the asynchronous callback
     */
    public void setDirectChatRoomsMap(Map<String, List<String>> directChatRoomsMap, ApiCallback<Void> callback) {
        Log.d(LOG_TAG, "## setDirectChatRoomsMap()");
        IMXStore store = getStore();
        if (null != store) {
            // update the store value
            // do not wait the server request echo to update the store
            store.setDirectChatRoomsDict(directChatRoomsMap);
        } else {
            Log.e(LOG_TAG, "## setDirectChatRoomsMap() : null store");
        }
        mLocalDirectChatRoomIdsList = null;
        // Upload the new map
        mAccountDataRestClient.setAccountData(getMyUser().user_id, AccountDataElement.ACCOUNT_DATA_TYPE_DIRECT_MESSAGES, directChatRoomsMap, callback);
    }

    /**
     * This class defines a direct chat backward compliancyc structure
     */
    private class RoomIdsListRetroCompat {
        final String mRoomId;
        final String mParticipantUserId;

        public RoomIdsListRetroCompat(String aParticipantUserId, String aRoomId) {
            mParticipantUserId = aParticipantUserId;
            mRoomId = aRoomId;
        }
    }

    /**
     * Return the list of the direct chat room IDs for the user given in parameter.<br>
     * Based on the account_data map content, the entry associated with aSearchedUserId is returned.
     *
     * @param aSearchedUserId user ID
     * @return the list of the direct chat room Id
     */
    public List<String> getDirectChatRoomIdsList(String aSearchedUserId) {
        List<String> directChatRoomIdsList = new ArrayList<>();
        IMXStore store = getStore();
        Room room;

        Map<String, List<String>> params;

        if (null != store.getDirectChatRoomsDict()) {
            params = new HashMap<>(store.getDirectChatRoomsDict());
            if (params.containsKey(aSearchedUserId)) {
                directChatRoomIdsList = new ArrayList<>();

                for (String roomId : params.get(aSearchedUserId)) {
                    room = store.getRoom(roomId);
                    if (null != room) { // skipp empty rooms
                        directChatRoomIdsList.add(roomId);
                    }
                }
            } else {
                Log.w(LOG_TAG, "## getDirectChatRoomIdsList(): UserId " + aSearchedUserId + " has no entry in account_data");
            }
        } else {
            Log.w(LOG_TAG, "## getDirectChatRoomIdsList(): failure - getDirectChatRoomsDict()=null");
        }

        return directChatRoomIdsList;
    }

    @NotNull
    @Override
    public Context getContext() {
        return getStore().getContext();
    }
}
