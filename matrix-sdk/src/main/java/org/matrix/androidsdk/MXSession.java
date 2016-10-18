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
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import org.matrix.androidsdk.call.MXCallsManager;
import org.matrix.androidsdk.crypto.MXCrypto;
import org.matrix.androidsdk.data.DataRetriever;
import org.matrix.androidsdk.data.IMXStore;
import org.matrix.androidsdk.data.MyUser;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.data.RoomTag;
import org.matrix.androidsdk.db.MXLatestChatMessageCache;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.network.NetworkConnectivityReceiver;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.ApiFailureCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.client.AccountDataRestClient;
import org.matrix.androidsdk.rest.client.BingRulesRestClient;
import org.matrix.androidsdk.rest.client.CallRestClient;
import org.matrix.androidsdk.rest.client.CryptoRestClient;
import org.matrix.androidsdk.rest.client.EventsRestClient;
import org.matrix.androidsdk.rest.client.PresenceRestClient;
import org.matrix.androidsdk.rest.client.ProfileRestClient;
import org.matrix.androidsdk.rest.client.PushersRestClient;
import org.matrix.androidsdk.rest.client.RoomsRestClient;
import org.matrix.androidsdk.rest.client.ThirdPidRestClient;
import org.matrix.androidsdk.rest.model.CreateRoomResponse;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.RoomResponse;
import org.matrix.androidsdk.rest.model.Search.SearchResponse;
import org.matrix.androidsdk.rest.model.User;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

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

    // Api clients
    private EventsRestClient mEventsRestClient;
    private ProfileRestClient mProfileRestClient;
    private PresenceRestClient mPresenceRestClient;
    private RoomsRestClient mRoomsRestClient;
    private BingRulesRestClient mBingRulesRestClient;
    private PushersRestClient mPushersRestClient;
    private ThirdPidRestClient mThirdPidRestClient;
    private CallRestClient mCallRestClient;
    private AccountDataRestClient mAccountDataRestClient;
    private CryptoRestClient mCryptoRestClient;

    private ApiFailureCallback mFailureCallback;

    private ContentManager mContentManager;

    public MXCallsManager mCallsManager;

    private Context mAppContent;
    private NetworkConnectivityReceiver mNetworkConnectivityReceiver;
    private UnsentEventsManager mUnsentEventsManager;

    private MXLatestChatMessageCache mLatestChatMessageCache;
    private MXMediasCache mMediasCache;

    private BingRulesManager mBingRulesManager = null;

    private boolean mIsAliveSession = true;

    // online status
    private boolean mIsOnline = true;

    private HomeserverConnectionConfig mHsConfig;

    // the application is launched from a notification
    // so, mEventsThread.start might be not ready
    private boolean mIsCatchupPending = false;

    // regex pattern to find matrix user ids in a string.
    public static final String MATRIX_USER_IDENTIFIER_REGEX = "@[A-Z0-9]+:[A-Z0-9.-]+\\.[A-Z]{2,}";
    public static final Pattern PATTERN_CONTAIN_MATRIX_USER_IDENTIFIER =  Pattern.compile(MATRIX_USER_IDENTIFIER_REGEX, Pattern.CASE_INSENSITIVE);

    // regex pattern to find room aliases in a string.
    public static final String MATRIX_ROOM_ALIAS_REGEX = "#[A-Z0-9._%+-\\\\#]+:[A-Z0-9.-]+\\.[A-Z]{2,}";
    public static final Pattern PATTERN_CONTAIN_MATRIX_ALIAS =  Pattern.compile(MATRIX_ROOM_ALIAS_REGEX, Pattern.CASE_INSENSITIVE);

    // regex pattern to find room ids in a string.
    public static final String MATRIX_ROOM_IDENTIFIER_REGEX = "![A-Z0-9]+:[A-Z0-9.-]+\\.[A-Z]{2,}";
    public static final Pattern PATTERN_CONTAIN_MATRIX_ROOM_IDENTIFIER =  Pattern.compile(MATRIX_ROOM_IDENTIFIER_REGEX, Pattern.CASE_INSENSITIVE);

    // regex pattern to find message ids in a string.
    public static final String MATRIX_MESSAGE_IDENTIFIER_REGEX = "\\$[A-Z0-9]+:[A-Z0-9.-]+\\.[A-Z]{2,}";
    public static final Pattern PATTERN_CONTAIN_MATRIX_MESSAGE_IDENTIFIER =  Pattern.compile(MATRIX_MESSAGE_IDENTIFIER_REGEX, Pattern.CASE_INSENSITIVE);

    // regex pattern to find permalink with message id.
    // Android does not support in URL so extract it.
    public static final Pattern PATTERN_CONTAIN_MATRIX_TO_PERMALINK_ROOM_ID =  Pattern.compile("https:\\/\\/matrix\\.to\\/#\\/"+ MATRIX_ROOM_IDENTIFIER_REGEX  +"\\/" + MATRIX_MESSAGE_IDENTIFIER_REGEX, Pattern.CASE_INSENSITIVE);
    public static final Pattern PATTERN_CONTAIN_MATRIX_TO_PERMALINK_ROOM_ALIAS =  Pattern.compile("https:\\/\\/matrix\\.to\\/#\\/"+ MATRIX_ROOM_ALIAS_REGEX  +"\\/" + MATRIX_MESSAGE_IDENTIFIER_REGEX, Pattern.CASE_INSENSITIVE);

    public static final Pattern PATTERN_CONTAIN_APP_LINK_PERMALINK_ROOM_ID =  Pattern.compile("https:\\/\\/[A-Z0-9.-]+\\.[A-Z]{2,}\\/[A-Z]{3,}\\/#\\/room\\/"+ MATRIX_ROOM_IDENTIFIER_REGEX  +"\\/" + MATRIX_MESSAGE_IDENTIFIER_REGEX, Pattern.CASE_INSENSITIVE);
    public static final Pattern PATTERN_CONTAIN_APP_LINK_PERMALINK_ROOM_ALIAS =  Pattern.compile("https:\\/\\/[A-Z0-9.-]+\\.[A-Z]{2,}\\/[A-Z]{3,}\\/#\\/room\\/"+ MATRIX_ROOM_ALIAS_REGEX  +"\\/" + MATRIX_MESSAGE_IDENTIFIER_REGEX, Pattern.CASE_INSENSITIVE);


    /**
     * Create a basic session for direct API calls.
     *
     * @param hsConfig the home server connection config
     */
    public MXSession(HomeserverConnectionConfig hsConfig) {
        mCredentials = hsConfig.getCredentials();
        mHsConfig = hsConfig;

        mEventsRestClient = new EventsRestClient(hsConfig);
        mProfileRestClient = new ProfileRestClient(hsConfig);
        mPresenceRestClient = new PresenceRestClient(hsConfig);
        mRoomsRestClient = new RoomsRestClient(hsConfig);
        mBingRulesRestClient = new BingRulesRestClient(hsConfig);
        mPushersRestClient = new PushersRestClient(hsConfig);
        mThirdPidRestClient = new ThirdPidRestClient(hsConfig);
        mCallRestClient = new CallRestClient(hsConfig);
        mAccountDataRestClient = new AccountDataRestClient(hsConfig);
        mCryptoRestClient = new CryptoRestClient(hsConfig);
    }

    /**
     * Create a user session with a data handler.
     *
     * @param hsConfig    the home server connection config
     * @param dataHandler the data handler
     * @param appContext  the application context
     */
    public MXSession(HomeserverConnectionConfig hsConfig, MXDataHandler dataHandler, Context appContext) {
        this(hsConfig);
        mDataHandler = dataHandler;

        // Initialize a data retriever with rest clients
        mDataRetriever = new DataRetriever();
        mDataRetriever.setRoomsRestClient(mRoomsRestClient);
        mDataHandler.setDataRetriever(mDataRetriever);
        mDataHandler.setProfileRestClient(mProfileRestClient);
        mDataHandler.setPresenceRestClient(mPresenceRestClient);
        mDataHandler.setThirdPidRestClient(mThirdPidRestClient);
        mDataHandler.setRoomsRestClient(mRoomsRestClient);

        // application context
        mAppContent = appContext;

        mNetworkConnectivityReceiver = new NetworkConnectivityReceiver();
        mNetworkConnectivityReceiver.checkNetworkConnection(appContext);

        mAppContent.registerReceiver(mNetworkConnectivityReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

        mBingRulesManager = new BingRulesManager(this, mNetworkConnectivityReceiver);
        mDataHandler.setPushRulesManager(mBingRulesManager);

        mUnsentEventsManager = new UnsentEventsManager(mNetworkConnectivityReceiver, mDataHandler);

        mContentManager = new ContentManager(hsConfig, mUnsentEventsManager);

        //
        mCallsManager = new MXCallsManager(this, mAppContent);
        mDataHandler.setCallsManager(mCallsManager);

        // the rest client
        mEventsRestClient.setUnsentEventsManager(mUnsentEventsManager);

        mProfileRestClient.setUnsentEventsManager(mUnsentEventsManager);
        mPresenceRestClient.setUnsentEventsManager(mUnsentEventsManager);
        mRoomsRestClient.setUnsentEventsManager(mUnsentEventsManager);
        mBingRulesRestClient.setUnsentEventsManager(mUnsentEventsManager);
        mThirdPidRestClient.setUnsentEventsManager(mUnsentEventsManager);
        mCallRestClient.setUnsentEventsManager(mUnsentEventsManager);
        mAccountDataRestClient.setUnsentEventsManager(mUnsentEventsManager);
        mCryptoRestClient.setUnsentEventsManager(mUnsentEventsManager);

        // return the default cache manager
        mLatestChatMessageCache = new MXLatestChatMessageCache(mCredentials.userId);
        mMediasCache = new MXMediasCache(mContentManager, mCredentials.userId, appContext);
        mDataHandler.setMediasCache(mMediasCache);
    }

    private void checkIfAlive() {
        synchronized (this) {
            if (!mIsAliveSession) {
                Log.e(LOG_TAG, "Use of a release session");
                //throw new AssertionError("Should not used a cleared mxsession ");
            }
        }
    }

    /**
     * @return the SDK version.
     */
    public String getVersion(boolean longFormat) {
        checkIfAlive();

        String versionName = BuildConfig.VERSION_NAME;

        if (!TextUtils.isEmpty(versionName)) {
            String gitVersion = mAppContent.getResources().getString(R.string.git_sdk_revision);

            if (longFormat) {
                String date = mAppContent.getResources().getString(R.string.git_sdk_revision_date);
                versionName += " (" + gitVersion + "-" + date + ")";
            } else {
                versionName += " (" + gitVersion + ")";
            }
        }
        return versionName;
    }

    /**
     * Get the data handler.
     *
     * @return the data handler.
     */
    public MXDataHandler getDataHandler() {
        checkIfAlive();
        return mDataHandler;
    }

    /**
     * Get the user credentials.
     *
     * @return the credentials
     */
    public Credentials getCredentials() {
        checkIfAlive();
        return mCredentials;
    }

    /**
     * Get the API client for requests to the events API.
     *
     * @return the events API client
     */
    public EventsRestClient getEventsApiClient() {
        checkIfAlive();
        return mEventsRestClient;
    }

    /**
     * Get the API client for requests to the profile API.
     *
     * @return the profile API client
     */
    public ProfileRestClient getProfileApiClient() {
        checkIfAlive();
        return mProfileRestClient;
    }

    /**
     * Get the API client for requests to the presence API.
     *
     * @return the presence API client
     */
    public PresenceRestClient getPresenceApiClient() {
        checkIfAlive();
        return mPresenceRestClient;
    }

    /**
     * Refresh the presence info of a dedicated user.
     *
     * @param userId   the user userID.
     * @param callback the callback.
     */
    public void refreshUserPresence(final String userId, final ApiCallback<Void> callback) {
        mPresenceRestClient.getPresence(userId, new ApiCallback<User>() {
            @Override
            public void onSuccess(User user) {
                User currentUser = mDataHandler.getStore().getUser(userId);

                if (null != currentUser) {
                    currentUser.presence = user.presence;
                    currentUser.currently_active = user.currently_active;
                    currentUser.lastActiveAgo = user.lastActiveAgo;
                } else {
                    currentUser = user;
                }

                currentUser.setLatestPresenceTs(System.currentTimeMillis());
                mDataHandler.getStore().storeUser(currentUser);
                if (null != callback) {
                    callback.onSuccess(null);
                }
            }

            @Override
            public void onNetworkError(Exception e) {
                if (null != callback) {
                    callback.onNetworkError(e);
                }
            }

            @Override
            public void onMatrixError(MatrixError e) {
                if (null != callback) {
                    callback.onMatrixError(e);
                }
            }

            @Override
            public void onUnexpectedError(Exception e) {
                if (null != callback) {
                    callback.onUnexpectedError(e);
                }
            }
        });
    }

    /**
     * Get the API client for requests to the bing rules API.
     *
     * @return the bing rules API client
     */
    public BingRulesRestClient getBingRulesApiClient() {
        checkIfAlive();
        return mBingRulesRestClient;
    }

    public CallRestClient getCallRestClient() {
        checkIfAlive();
        return mCallRestClient;
    }

    public PushersRestClient getPushersRestClient() {
        checkIfAlive();
        return mPushersRestClient;
    }

    public CryptoRestClient getCryptoRestClient() {
        checkIfAlive();
        return mCryptoRestClient;
    }

    public HomeserverConnectionConfig getHomeserverConfig() {
        checkIfAlive();
        return mHsConfig;
    }

    /**
     * Get the API client for requests to the rooms API.
     *
     * @return the rooms API client
     */
    public RoomsRestClient getRoomsApiClient() {
        checkIfAlive();
        return mRoomsRestClient;
    }

    protected void setEventsApiClient(EventsRestClient eventsRestClient) {
        checkIfAlive();
        this.mEventsRestClient = eventsRestClient;
    }

    protected void setProfileApiClient(ProfileRestClient profileRestClient) {
        checkIfAlive();
        this.mProfileRestClient = profileRestClient;
    }

    protected void setPresenceApiClient(PresenceRestClient presenceRestClient) {
        checkIfAlive();
        this.mPresenceRestClient = presenceRestClient;
    }

    protected void setRoomsApiClient(RoomsRestClient roomsRestClient) {
        checkIfAlive();
        this.mRoomsRestClient = roomsRestClient;
    }

    public MXLatestChatMessageCache getLatestChatMessageCache() {
        checkIfAlive();
        return mLatestChatMessageCache;
    }

    public MXMediasCache getMediasCache() {
        checkIfAlive();
        return mMediasCache;
    }

    /**
     * Clear the session data
     */
    public void clear(Context context) {
        checkIfAlive();

        synchronized (this) {
            mIsAliveSession = false;
        }

        // stop events stream
        stopEventStream();

        // cancel any listener
        mDataHandler.clear();

        // network event will not be listened anymore
        mAppContent.unregisterReceiver(mNetworkConnectivityReceiver);
        mNetworkConnectivityReceiver.removeListeners();

        // auto resent messages will not be resent
        mUnsentEventsManager.clear();

        mLatestChatMessageCache.clearCache(context);
        mMediasCache.clear();

        if (null != mCrypto) {
            mCrypto.close();
        }
    }

    /**
     * @return true if the session is active i.e. has not been cleared after a logout.
     */
    public boolean isAlive() {
        synchronized (this) {
            return mIsAliveSession;
        }
    }

    /**
     * Get the content manager (for uploading and downloading content) associated with the session.
     *
     * @return the content manager
     */
    public ContentManager getContentManager() {
        checkIfAlive();
        return mContentManager;
    }

    /**
     * Get the session's current user. The MyUser object provides methods for updating user properties which are not possible for other users.
     *
     * @return the session's MyUser object
     */
    public MyUser getMyUser() {
        checkIfAlive();

        return mDataHandler.getMyUser();
    }


    /**
     * Get the session's current userid.
     *
     * @return the session's MyUser id
     */
    public String getMyUserId() {
        checkIfAlive();

        if (null != mDataHandler.getMyUser()) {
            return mDataHandler.getMyUser().user_id;
        }
        return null;
    }

    /**
     * Start the event stream (events thread that listens for events) with an event listener.
     *
     * @param anEventsListener            the event listener or null if using a DataHandler
     * @param networkConnectivityReceiver the network connectivity listener.
     * @param initialToken                the initial sync token (null to start from scratch)
     */
    public void startEventStream(final EventsThreadListener anEventsListener, final NetworkConnectivityReceiver networkConnectivityReceiver, final String initialToken) {
        checkIfAlive();

        if (mEventsThread != null) {
            Log.e(LOG_TAG, "Ignoring startEventStream() : Thread already created.");
            return;
        }

        if (mDataHandler == null) {
            Log.e(LOG_TAG, "Error starting the event stream: No data handler is defined");
            return;
        }

        Log.d(LOG_TAG, "startEventStream : create the event stream");

        final EventsThreadListener fEventsListener = (null == anEventsListener) ? new DefaultEventsThreadListener(mDataHandler) : anEventsListener;

        mEventsThread = new EventsThread(mEventsRestClient, fEventsListener, initialToken);
        mEventsThread.setNetworkConnectivityReceiver(networkConnectivityReceiver);

        if (mFailureCallback != null) {
            mEventsThread.setFailureCallback(mFailureCallback);
        }

        if (mCredentials.accessToken != null && !mEventsThread.isAlive()) {
            mEventsThread.start();

            if (mIsCatchupPending) {
                Log.d(LOG_TAG, "startEventStream : there was a pending catchup : the catchup will be triggered in 5 seconds");

                mIsCatchupPending = false;
                Handler handler = new Handler(Looper.getMainLooper());
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Log.d(LOG_TAG, "startEventStream : pause the stream");
                        pauseEventStream();
                    }
                }, 5000);
            }
        }
    }

    /**
     * Refresh the access token
     */
    public void refreshToken() {
        checkIfAlive();

        mProfileRestClient.refreshTokens(new ApiCallback<Credentials>() {
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
     * Update the online status
     * @param isOnline true if the client must be seen as online
     */
    public void setIsOnline(boolean isOnline) {
        if (isOnline != mIsOnline) {
            mIsOnline = isOnline;

            if (null != mEventsThread) {
                mEventsThread.setIsOnline(isOnline);
            }
        }
    }

    /**
     * Tell if the client is seen as "online"
     */
    public boolean isOnline() {
        return mIsOnline;
    }

    /**
     * Update the heartbeat request timeout.
     * @param ms the delay in ms
     */
    public void setSyncTimeout(int ms) {
        if (null != mEventsThread) {
            mEventsThread.setServerLongPollTimeout(ms);
        }
    }

    /**
     * @return the heartbeat request timeout
     */
    public int getSyncTimeout() {
        if (null != mEventsThread) {
            return mEventsThread.getServerLongPollTimeout();
        }

        return 0;
    }

    /**
     * Set a delay between two sync requests.
     * @param ms the delay in ms
     */
    public void setSyncDelay(int ms) {
        if (null != mEventsThread) {
            mEventsThread.setSyncDelay(ms);
        }
    }

    /**
     * @return the delay between two sync requests.
     */
    public int getSyncDelay() {
        if (null != mEventsThread) {
            mEventsThread.getSyncDelay();
        }

        return 0;
    }

    /**
     * Shorthand for {@link #startEventStream(org.matrix.androidsdk.sync.EventsThreadListener)} with no eventListener
     * using a DataHandler and no specific failure callback.
     *
     * @param initialToken the initial sync token (null to sync from scratch).
     */
    public void startEventStream(String initialToken) {
        checkIfAlive();
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

    /**
     * Pause the event stream
     */
    public void pauseEventStream() {
        checkIfAlive();

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

    /**
     * Resume the event stream
     */
    public void resumeEventStream() {
        checkIfAlive();

        if (null != mNetworkConnectivityReceiver) {
            // mNetworkConnectivityReceiver is a broadcastReceiver
            // but some users reported that the network updates wre not broadcasted.
            mNetworkConnectivityReceiver.checkNetworkConnection(mAppContent);
        }

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

    /**
     * Trigger a catchup
     */
    public void catchupEventStream() {
        checkIfAlive();

        if (null != mEventsThread) {
            Log.d(LOG_TAG, "catchupEventStream");
            mEventsThread.catchup();
        } else {
            Log.e(LOG_TAG, "catchupEventStream : mEventsThread is null so catchup when the thread will be created");
            mIsCatchupPending = true;
        }
    }

    /**
     * Set a global failure callback implementation.
     *
     * @param failureCallback the failure callback
     */
    public void setFailureCallback(ApiFailureCallback failureCallback) {
        checkIfAlive();

        mFailureCallback = failureCallback;
        if (mEventsThread != null) {
            mEventsThread.setFailureCallback(failureCallback);
        }
    }

    /**
     * Create a new room.
     *
     * @param callback   the async callback once the room is ready
     */
    public void createRoom(final ApiCallback<String> callback) {
        createRoom(null, null, null, callback);
    }

    /**
     * Create a new room with given properties.
     *
     * @param params the creation parameters.
     * @param callback the async callback once the room is ready
     */
    public void createRoom(final Map<String, Object> params, final ApiCallback<String> callback) {
        mRoomsRestClient.createRoom(params, new SimpleApiCallback<CreateRoomResponse>(callback) {
            @Override
            public void onSuccess(CreateRoomResponse info) {
                final String roomId = info.roomId;
                Room createdRoom = mDataHandler.getRoom(roomId);

                // the creation events are not be called during the creation
                if (createdRoom.getState().getMember(mCredentials.userId) == null) {
                    createdRoom.setOnInitialSyncCallback(new ApiCallback<Void>() {
                        @Override
                        public void onSuccess(Void info) {
                            callback.onSuccess(roomId);
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
                } else {
                    callback.onSuccess(roomId);
                }
            }
        });

    }

    /**
     * Create a new room with given properties. Needs the data handler.
     *
     * @param name       the room name
     * @param topic      the room topic
     * @param alias      the room alias
     * @param callback   the async callback once the room is ready
     */
    public void createRoom(String name, String topic, String alias, final ApiCallback<String> callback) {
        createRoom(name, topic, RoomState.DIRECTORY_VISIBILITY_PRIVATE, alias, RoomState.GUEST_ACCESS_CAN_JOIN, RoomState.HISTORY_VISIBILITY_SHARED, callback);
    }

    /**
     * Create a new room with given properties. Needs the data handler.
     *
     * @param name       the room name
     * @param topic      the room topic
     * @param visibility the room visibility
     * @param alias      the room alias
     * @param guestAccess the guest access rule (see {@link RoomState#GUEST_ACCESS_CAN_JOIN} or {@link RoomState#GUEST_ACCESS_FORBIDDEN})
     * @param historyVisibility the history visibility
     * @param callback   the async callback once the room is ready
     */
    public void createRoom(String name, String topic, String visibility, String alias, String guestAccess, String historyVisibility, final ApiCallback<String> callback) {
        checkIfAlive();

        mRoomsRestClient.createRoom(name, topic, visibility, alias, guestAccess, historyVisibility, new SimpleApiCallback<CreateRoomResponse>(callback) {
            @Override
            public void onSuccess(CreateRoomResponse info) {
                final String roomId = info.roomId;
                Room createdRoom = mDataHandler.getRoom(roomId);

                // the creation events are not be called during the creation
                if (createdRoom.getState().getMember(mCredentials.userId) == null) {
                    createdRoom.setOnInitialSyncCallback(new ApiCallback<Void>() {
                        @Override
                        public void onSuccess(Void info) {
                            callback.onSuccess(roomId);
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
                } else {
                    callback.onSuccess(roomId);
                }
            }
        });
    }

    /**
     * Join a room by its roomAlias
     *
     * @param roomIdOrAlias the room alias
     * @param callback      the async callback once the room is joined. The RoomId is provided.
     */
    public void joinRoom(String roomIdOrAlias, final ApiCallback<String> callback) {
        checkIfAlive();

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
     *
     * @param address  the user id.
     * @param media    the media.
     * @param callback the 3rd party callback
     */
    public void lookup3Pid(String address, String media, final ApiCallback<String> callback) {
        checkIfAlive();

        mThirdPidRestClient.lookup3Pid(address, media, callback);
    }

    /**
     * Retrieve user matrix id from a 3rd party id.
     *
     * @param addresses 3rd party ids
     * @param mediums   the medias.
     * @param callback  the 3rd parties callback
     */
    public void lookup3Pids(ArrayList<String> addresses, ArrayList<String> mediums, ApiCallback<ArrayList<String>> callback) {
        checkIfAlive();

        mThirdPidRestClient.lookup3Pids(addresses, mediums, callback);
    }

    /**
     * Perform a remote text search.
     *
     * @param text        the text to search for.
     * @param rooms       a list of rooms to search in. nil means all rooms the user is in.
     * @param beforeLimit the number of events to get before the matching results.
     * @param afterLimit  the number of events to get after the matching results.
     * @param nextBatch   the token to pass for doing pagination from a previous response.
     * @param callback    the request callback
     */
    public void searchMessageText(String text, List<String> rooms, int beforeLimit, int afterLimit, String nextBatch, final ApiCallback<SearchResponse> callback) {
        checkIfAlive();
        if (null != callback) {
            mEventsRestClient.searchMessagesByText(text, rooms, beforeLimit, afterLimit, nextBatch, callback);
        }
    }

    /**
     * Perform a remote text search.
     *
     * @param text      the text to search for.
     * @param rooms     a list of rooms to search in. nil means all rooms the user is in.
     * @param nextBatch the token to pass for doing pagination from a previous response.
     * @param callback  the request callback
     */
    public void searchMessagesByText(String text, List<String> rooms, String nextBatch, final ApiCallback<SearchResponse> callback) {
        checkIfAlive();
        if (null != callback) {
            mEventsRestClient.searchMessagesByText(text, rooms, 0, 0, nextBatch, callback);
        }
    }

    /**
     * Perform a remote text search.
     *
     * @param text      the text to search for.
     * @param nextBatch the token to pass for doing pagination from a previous response.
     * @param callback  the request callback
     */
    public void searchMessagesByText(String text, String nextBatch, final ApiCallback<SearchResponse> callback) {
        checkIfAlive();
        if (null != callback) {
            mEventsRestClient.searchMessagesByText(text, null, 0, 0, nextBatch, callback);
        }
    }

    /**
     * Cancel any pending search request
     */
    public void cancelSearchMessagesByText() {
        checkIfAlive();
        mEventsRestClient.cancelSearchMessagesByText();
    }

    /**
     * Perform a remote text search for a dedicated media types list
     *
     * @param name         the text to search for.
     * @param rooms        a list of rooms to search in. nil means all rooms the user is in.
     * @param nextBatch    the token to pass for doing pagination from a previous response.
     * @param callback     the request callback
     */
    public void searchMediasByName(String name, List<String> rooms, String nextBatch, final ApiCallback<SearchResponse> callback) {
        checkIfAlive();

        if (null != callback) {
            mEventsRestClient.searchMediasByText(name, rooms, 0, 0, nextBatch, callback);
        }
    }

    /**
     * Cancel any pending file search request
     */
    public void cancelSearchMediasByText() {
        checkIfAlive();
        mEventsRestClient.cancelSearchMediasByText();
    }

    /**
     * Return the fulfilled active BingRule for the event.
     *
     * @param event the event
     * @return the fulfilled bingRule
     */
    public BingRule fulfillRule(Event event) {
        checkIfAlive();
        return mBingRulesManager.fulfilledBingRule(event);
    }

    /**
     * @return true if the calls are supported
     */
    public boolean isVoipCallSupported() {
        if (null != mCallsManager) {
            return mCallsManager.isSupported();
        } else {
            return false;
        }
    }

    /**
     * Get the list of rooms that are tagged the specified tag.
     * The returned array is ordered according to the room tag order.
     *
     * @param tag RoomTag.ROOM_TAG_XXX values
     * @return the rooms list.
     */
    public List<Room> roomsWithTag(final String tag) {
        ArrayList<Room> taggedRooms = new ArrayList<>();

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
                        } else if (null != tag1.mOrder) {
                            res = -1;
                        } else if (null != tag2.mOrder) {
                            res = +1;
                        }

                        // In case of same order, order rooms by their last event
                        if (0 == res) {
                            IMXStore store = mDataHandler.getStore();

                            Event latestEvent1 = store.getLatestEvent(r1.getRoomId());
                            Event latestEvent2 = store.getLatestEvent(r2.getRoomId());

                            // sanity check
                            if ((null != latestEvent2) && (null != latestEvent1)) {
                                long diff = (latestEvent2.getOriginServerTs() - latestEvent1.getOriginServerTs());
                                res = (diff == 0) ? 0 : (diff > 0) ? +1 : -1;
                            }
                        }

                        return res;
                    }
                });
            }
        } else {
            Collection<Room> rooms = mDataHandler.getStore().getRooms();

            for (Room room : rooms) {
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
     *
     * @param tag RoomTag.ROOM_TAG_XXX values
     * @return the room IDs list.
     */
    public List<String> roomIdsWithTag(final String tag) {
        List<Room> roomsWithTag = roomsWithTag(tag);

        ArrayList<String> roomIdsList = new ArrayList<>();

        for (Room room : roomsWithTag) {
            roomIdsList.add(room.getRoomId());
        }

        return roomIdsList;
    }

    /**
     * Compute the tag order to use for a room tag so that the room will appear in the expected position
     * in the list of rooms stamped with this tag.
     *
     * @param index       the targeted index of the room in the list of rooms with the tag `tag`.
     * @param originIndex the origin index. Integer.MAX_VALUE if there is none.
     * @param tag         the tag
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
                } else {
                    orderA = prevTag.mOrder;
                }
            }

            if (index <= roomsWithTag.size() - 1) {
                RoomTag nextTag = roomsWithTag.get(index).getAccountData().roomTag(tag);

                if (null == nextTag.mOrder) {
                    Log.e(LOG_TAG, "computeTagOrderForRoom: Next room in sublist has no ordering metadata. This should never happen.");
                } else {
                    orderB = nextTag.mOrder;
                }
            }
        }

        return (orderA + orderB) / 2.0;
    }

    /**
     * Update the account password
     *
     * @param oldPassword the former account password
     * @param newPassword the new account password
     * @param callback    the callback
     */
    public void updatePassword(String oldPassword, String newPassword, ApiCallback<Void> callback) {
        mProfileRestClient.updatePassword(getMyUserId(), oldPassword, newPassword, callback);
    }

    /**
     * Reset the password to a new one.
     *
     * @param newPassword    the new password
     * @param threepid_creds the three pids.
     * @param callback       the callback
     */
    public void resetPassword(final String newPassword, final Map<String, String> threepid_creds, final ApiCallback<Void> callback) {
        mProfileRestClient.resetPassword(newPassword, threepid_creds, callback);
    }

    /**
     * Triggers a request to update the userId to ignore
     * @param userIds the userIds to ignoer
     * @param callback the callback
     */
    private void updateUsers(ArrayList<String> userIds, ApiCallback<Void> callback) {
        HashMap<String, Object> ignoredUsersDict = new HashMap<>();

        for (String userId : userIds) {
            ignoredUsersDict.put(userId, new ArrayList<>());
        }

        HashMap<String, Object> params = new HashMap<>();
        params.put(AccountDataRestClient.ACCOUNT_DATA_KEY_IGNORED_USERS, ignoredUsersDict);

        mAccountDataRestClient.setAccountData(getMyUserId(), AccountDataRestClient.ACCOUNT_DATA_TYPE_IGNORED_USER_LIST,  params, callback);
    }

    /**
     * Tells if an user is in the ignored user ids list
     * @param userId the user id to test
     * @return true if the user is ignored
     */
    public boolean isUserIgnored(String userId) {
        if (null != userId) {
            return getDataHandler().getIgnoredUserIds().indexOf(userId) >= 0;
        }

        return false;
    }

    /**
     * Ignore a list of users.
     * @param userIds the user ids list to ignore
     * @param callback the result callback
     */
    public void ignoreUsers(ArrayList<String> userIds, ApiCallback<Void> callback) {
        List<String> curUserIdsToIgnore = getDataHandler().getIgnoredUserIds();
        ArrayList<String> userIdsToIgnore = new ArrayList<>(getDataHandler().getIgnoredUserIds());

        // something to add
        if ((null !=  userIds) && (userIds.size() > 0)) {
            // add the new one
            for (String userId : userIds) {
                if (userIdsToIgnore.indexOf(userId) < 0) {
                    userIdsToIgnore.add(userId);
                }
            }

            // some items have been added
            if (curUserIdsToIgnore.size() != userIdsToIgnore.size()) {
                updateUsers(userIdsToIgnore, callback);
            }
        }
    }

    /**
     * Unignore a list of users.
     * @param userIds the user ids list to unignore
     * @param callback the result callback
     */
    public void unIgnoreUsers(ArrayList<String> userIds, ApiCallback<Void> callback) {
        List<String> curUserIdsToIgnore = getDataHandler().getIgnoredUserIds();
        ArrayList<String> userIdsToIgnore = new ArrayList<>(getDataHandler().getIgnoredUserIds());

        // something to add
        if ((null != userIds) && (userIds.size() > 0)) {
            // add the new one
            for (String userId : userIds) {
                userIdsToIgnore.remove(userId);
            }

            // some items have been added
            if (curUserIdsToIgnore.size() != userIdsToIgnore.size()) {
                updateUsers(userIdsToIgnore, callback);
            }
        }
    }

    /**
     * @return the network receiver.
     */
    public NetworkConnectivityReceiver getNetworkConnectivityReceiver() {
        return mNetworkConnectivityReceiver;
    }

    //==============================================================================================================
    // Crypto
    //==============================================================================================================

    /**
     * The module that manages E2E encryption.
     * Null if the feature is not enabled
     */
    private MXCrypto mCrypto;

    /**
     * @return the crypto instance
     */
    public MXCrypto getCrypto() {
        return mCrypto;
    }

    /**
     * @return true if the crypto is enabled
     */
    public boolean isCryptoEnabled() {
        return null != mCrypto;
    }

    /**
     * Enable / disable the crypto
     * @param cryptoEnabled
     */
    public void setCryptoEnabled(boolean cryptoEnabled) {
        if (cryptoEnabled != isCryptoEnabled()) {
            if (cryptoEnabled) {
                Log.d(LOG_TAG, "Crypto is enabled");
                mCrypto = new MXCrypto(this);
            } else {
                Log.d(LOG_TAG, "Crypto is disabled");
                mCrypto = null;
                //@TODO: Reset crypto store and release memory
            }

            mDataHandler.setCrypto(mCrypto);
        }
    }
}