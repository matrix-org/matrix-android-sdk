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

import com.google.gson.JsonObject;

import org.matrix.androidsdk.call.MXCallsManager;
import org.matrix.androidsdk.crypto.MXCrypto;
import org.matrix.androidsdk.data.DataRetriever;
import org.matrix.androidsdk.data.RoomSummary;
import org.matrix.androidsdk.data.cryptostore.IMXCryptoStore;
import org.matrix.androidsdk.data.store.IMXStore;
import org.matrix.androidsdk.data.cryptostore.MXFileCryptoStore;
import org.matrix.androidsdk.data.MyUser;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.data.RoomTag;
import org.matrix.androidsdk.data.store.MXStoreListener;
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
import org.matrix.androidsdk.rest.client.LoginRestClient;
import org.matrix.androidsdk.rest.client.PresenceRestClient;
import org.matrix.androidsdk.rest.client.ProfileRestClient;
import org.matrix.androidsdk.rest.client.PushersRestClient;
import org.matrix.androidsdk.rest.client.RoomsRestClient;
import org.matrix.androidsdk.rest.client.ThirdPidRestClient;
import org.matrix.androidsdk.rest.model.CreateRoomResponse;
import org.matrix.androidsdk.rest.model.DeleteDeviceAuth;
import org.matrix.androidsdk.rest.model.DeleteDeviceParams;
import org.matrix.androidsdk.rest.model.DevicesListResponse;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.ReceiptData;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.RoomResponse;
import org.matrix.androidsdk.rest.model.Search.SearchResponse;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.rest.model.bingrules.BingRule;
import org.matrix.androidsdk.rest.model.login.Credentials;
import org.matrix.androidsdk.rest.model.login.RegistrationFlowResponse;
import org.matrix.androidsdk.sync.DefaultEventsThreadListener;
import org.matrix.androidsdk.sync.EventsThread;
import org.matrix.androidsdk.sync.EventsThreadListener;
import org.matrix.androidsdk.util.BingRulesManager;
import org.matrix.androidsdk.util.ContentManager;
import org.matrix.androidsdk.util.JsonUtils;
import org.matrix.androidsdk.util.UnsentEventsManager;
import org.matrix.olm.OlmManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RunnableFuture;
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
    private LoginRestClient mLoginRestClient;

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

    // load the crypto libs.
    public static OlmManager mOlmManager = new OlmManager(android.os.Build.VERSION.SDK_INT < 23);

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
        mLoginRestClient = new LoginRestClient(hsConfig);
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

        mDataHandler.getStore().addMXStoreListener(new MXStoreListener() {
            @Override
            public void postProcess(String accountId) {
                MXFileCryptoStore store = new MXFileCryptoStore();
                store.initWithCredentials(mAppContent, mCredentials);

                if (store.hasData() || mEnableCryptoWhenStartingMXSession) {
                    // open the store
                    store.open();

                    // enable
                    mCrypto = new MXCrypto(MXSession.this, store);
                    mDataHandler.setCrypto(mCrypto);

                    // the room summaries are not stored with decrypted content
                    decryptRoomSummaries();
                }
            }

            @Override
            public void onReadReceiptsLoaded(final String roomId) {
                final List<ReceiptData> receipts = mDataHandler.getStore().getEventReceipts(roomId, null, false, false);
                final ArrayList<String> senders = new ArrayList<>();

                for(ReceiptData receipt : receipts) {
                    senders.add(receipt.userId);
                }

                mDataHandler.onReceiptEvent(roomId, senders);
            }
        });

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
        mDataHandler.setNetworkConnectivityReceiver(mNetworkConnectivityReceiver);
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
        mLoginRestClient.setUnsentEventsManager(mUnsentEventsManager);

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
     * @return the crypto lib version
     */
    public String getCryptoVersion() {
        if (null != mOlmManager) {
            return mOlmManager.getOlmLibVersion();
        }

        return "";
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

            // GA issue
            try {
                mEventsThread.start();
            } catch (Exception e) {
                Log.e(LOG_TAG, "## startEventStream() :  mEventsThread.start failed " + e.getMessage());
            }

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
     * Shorthand for {@link #startEventStream(EventsThreadListener, NetworkConnectivityReceiver, String)} with no eventListener
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

        if (null != mCrypto) {
            mCrypto.pause();
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

        if (null != mCrypto) {
            mCrypto.resume();
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
     * Create a direct message room with one participant.<br>
     * The participant can be a user ID or mail address. Once the room is created, on success, the room
     * is set as a "direct message" with the participant.
     * @param aParticipantUserId user ID (or user mail) to be invited in the direct message room
     * @param aCreateRoomCallBack async call back response
     * @return true if the invite was performed, false otherwise
     */
    public boolean createRoomDirectMessage(final String aParticipantUserId, final ApiCallback<String> aCreateRoomCallBack) {
        boolean retCode = false;

        if(!TextUtils.isEmpty(aParticipantUserId)) {
            retCode = true;
            HashMap<String, Object> params = new HashMap<>();
            params.put("preset","trusted_private_chat");
            params.put("is_direct", true);

            if (android.util.Patterns.EMAIL_ADDRESS.matcher(aParticipantUserId).matches()) {
                // retrieve the identity server
                String identityServer = mHsConfig.getIdentityServerUri().toString();

                // build the invite third party object
                HashMap<String, String> parameters = new HashMap<>();
                parameters.put("id_server", identityServer);
                parameters.put("medium", "email");
                parameters.put("address", aParticipantUserId);

                params.put("invite_3pid", Arrays.asList(parameters));
            } else {
                if (!aParticipantUserId.equals(getMyUserId())) {
                    // send invite only if the participant ID is not the user ID
                    params.put("invite", Arrays.asList(aParticipantUserId));
                }
            }

            createRoom(params, new ApiCallback<String>() {
                @Override
                public void onSuccess(String roomId) {
                    final Room room = getDataHandler().getRoom(roomId);
                    final String fRoomId = roomId;

                    toggleDirectChatRoom(roomId, aParticipantUserId, new ApiCallback<Void>() {
                        @Override
                        public void onSuccess(Void info) {
                            if(null != aCreateRoomCallBack) {
                                aCreateRoomCallBack.onSuccess(fRoomId);
                            }
                        }

                        @Override
                        public void onNetworkError(Exception e) {
                            if(null != aCreateRoomCallBack) {
                                aCreateRoomCallBack.onNetworkError(e);
                            }
                        }

                        @Override
                        public void onMatrixError(MatrixError e) {
                            if(null != aCreateRoomCallBack) {
                                aCreateRoomCallBack.onMatrixError(e);
                            }
                        }

                        @Override
                        public void onUnexpectedError(Exception e) {
                            if(null != aCreateRoomCallBack) {
                                aCreateRoomCallBack.onUnexpectedError(e);
                            }
                        }
                    });
                }

                @Override
                public void onNetworkError(Exception e) {
                    if(null != aCreateRoomCallBack) {
                        aCreateRoomCallBack.onNetworkError(e);
                    }
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    if(null != aCreateRoomCallBack) {
                        aCreateRoomCallBack.onMatrixError(e);
                    }
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    if(null != aCreateRoomCallBack) {
                        aCreateRoomCallBack.onUnexpectedError(e);
                    }
                }
            });
        }

        return retCode;
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
                    final String roomId = roomResponse.roomId;
                    Room joinedRoom = mDataHandler.getRoom(roomId);
                    RoomMember member = joinedRoom.getState().getMember(mCredentials.userId);
                    String state = (null != member) ? member.membership : null;

                    // wait until the initial sync is done
                    if ((state == null) || TextUtils.equals(state, RoomMember.MEMBERSHIP_INVITE)) {
                        joinedRoom.setOnInitialSyncCallback(new ApiCallback<Void>() {
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
     * @return the direct chat room ids list
     */
    public List<String> getDirectChatRoomIdsList() {
        IMXStore store = getDataHandler().getStore();
        ArrayList<String> directChatRoomIdsList = new ArrayList<>();

        if (null == store) {
            Log.e(LOG_TAG,"## getDirectChatRoomIdsList() : null store");
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
                    // test if the room is defined once and exists
                    if ((directChatRoomIdsList.indexOf(roomId) < 0) && (null != store.getRoom(roomId))) {
                        directChatRoomIdsList.add(roomId);
                    }
                }
            }
        } else {
            // background compatibility heuristic (named looksLikeDirectMessageRoom in the JS)
            ArrayList<RoomIdsListRetroCompat> directChatRoomIdsListRetValue = new ArrayList<>();
            getDirectChatRoomIdsListRetroCompat(store, directChatRoomIdsListRetValue);

            // force direct chat room list to be updated with retro compatibility rooms values
            if(0 != directChatRoomIdsListRetValue.size()) {
                forceDirectChatRoomValue(directChatRoomIdsListRetValue, new ApiCallback<Void>() {
                    @Override
                    public void onSuccess(Void info) {
                        Log.d(LOG_TAG, "## getDirectChatRoomIdsList(): background compatibility heuristic => account_data update succeed");
                    }

                    @Override
                    public void onMatrixError(MatrixError e) {
                        if (MatrixError.FORBIDDEN.equals(e.errcode)) {
                            Log.e(LOG_TAG, "## getDirectChatRoomIdsList(): onMatrixError Msg=" + e.error);
                        }
                    }

                    @Override
                    public void onNetworkError(Exception e) {
                        Log.e(LOG_TAG, "## getDirectChatRoomIdsList(): onNetworkError Msg=" + e.getMessage());
                    }

                    @Override
                    public void onUnexpectedError(Exception e) {
                        Log.e(LOG_TAG, "## getDirectChatRoomIdsList(): onUnexpectedError Msg=" + e.getMessage());
                    }
                });
            }
        }

        return directChatRoomIdsList;
    }

    /**
     * This class defines a direct chat backward compliancyc structure
     */
    private class RoomIdsListRetroCompat {
        String mRoomId;
        String mParticipantUserId;

        public RoomIdsListRetroCompat(String aParticipantUserId, String aRoomId) {
            this.mParticipantUserId = aParticipantUserId;
            this.mRoomId = aRoomId;
        }
    }

    /**
     * Return the direct chat room list for retro compatibility with 1:1 rooms.
     * @param aStore strore instance
     * @param aDirectChatRoomIdsListRetValue the other participants in the 1:1 room
     */
    private void getDirectChatRoomIdsListRetroCompat(IMXStore aStore, ArrayList<RoomIdsListRetroCompat> aDirectChatRoomIdsListRetValue) {
        RoomIdsListRetroCompat item;

        if((null != aStore) && (null != aDirectChatRoomIdsListRetValue)) {
            ArrayList<Room> rooms = new ArrayList<>(aStore.getRooms());
            ArrayList<RoomMember> members;
            int otherParticipantIndex;

            for (Room r : rooms) {
                // Show 1:1 chats in separate "Direct Messages" section as long as they haven't
                // been moved to a different tag section
                if ((r.getActiveMembers().size() == 2) && (null != r.getAccountData()) && (!r.getAccountData().hasTags())) {
                    RoomMember roomMember = r.getMember(getMyUserId());
                    members = new ArrayList<>(r.getActiveMembers());

                    if (null != roomMember) {
                        String membership = roomMember.membership;

                        if (TextUtils.equals(membership, RoomMember.MEMBERSHIP_JOIN) ||
                                TextUtils.equals(membership, RoomMember.MEMBERSHIP_BAN) ||
                                TextUtils.equals(membership, RoomMember.MEMBERSHIP_LEAVE)) {

                            if(TextUtils.equals(members.get(0).getUserId(), getMyUserId())) {
                                otherParticipantIndex = 1;
                            } else {
                                otherParticipantIndex = 0;
                            }

                            item = new RoomIdsListRetroCompat(members.get(otherParticipantIndex).getUserId(), r.getRoomId());
                            aDirectChatRoomIdsListRetValue.add(item);
                        }
                    }
                }
            }
        }
    }

    /**
     * Return the list of the direct chat room IDs for the user given in parameter.<br>
     * Based on the account_data map content, the entry associated with aSearchedUserId is returned.
     * @param aSearchedUserId user ID
     * @return the list of the direct chat room Id
     */
    public List<String> getDirectChatRoomIdsList(String aSearchedUserId) {
        ArrayList<String> directChatRoomIdsList = new ArrayList<>();
        IMXStore store = getDataHandler().getStore();
        Room room;

        HashMap<String, List<String>> params;

        if(null != store.getDirectChatRoomsDict()) {
            params = new HashMap<>(store.getDirectChatRoomsDict());
            if (params.containsKey(aSearchedUserId)) {
                directChatRoomIdsList = new ArrayList<>();

                for(String roomId: params.get(aSearchedUserId)) {
                    room = store.getRoom(roomId);
                    if(null != room) { // skipp empty rooms
                        directChatRoomIdsList.add(roomId);
                    }
                }
            } else {
                Log.w(LOG_TAG,"## getDirectChatRoomIdsList(): UserId "+aSearchedUserId+" has no entry in account_data");
            }
        } else {
            Log.w(LOG_TAG,"## getDirectChatRoomIdsList(): failure - getDirectChatRoomsDict()=null");
        }

        return directChatRoomIdsList;
    }

    /**
     * Toggles the direct chat status of a room.<br>
     * Create a new direct chat room in the account data section if the room does not exist,
     * otherwise the room is removed from the account data section.
     * Direct chat room user ID choice algorithm:<br>
     * 1- oldest joined room member
     * 2- oldest invited room member
     * 3- the user himself
     * @param roomId the room roomId
     * @param callback the asynchronous callback
     */
    public void toggleDirectChatRoom(String roomId, String aParticipantUserId, ApiCallback<Void> callback) {
        IMXStore store = getDataHandler().getStore();
        Room room = store.getRoom(roomId);

        if (null != room) {
            HashMap<String, List<String>> params;

            if (null != store.getDirectChatRoomsDict()) {
                params = new HashMap<>(store.getDirectChatRoomsDict());
            } else {
                params = new HashMap<>();
            }

            // if the room was not yet seen as direct chat
            if (getDirectChatRoomIdsList().indexOf(roomId) < 0) {
                ArrayList<String> roomIdsList = new ArrayList<>();
                RoomMember directChatMember = null;
                String chosenUserId;

                if(null == aParticipantUserId) {
                    ArrayList<RoomMember> members = new ArrayList<>(room.getActiveMembers());

                    if(members.size()>1) {
                        // sort algo: oldest join first, then oldest invited
                        Collections.sort(members, new Comparator<RoomMember>() {
                            @Override
                            public int compare(RoomMember r1, RoomMember r2) {
                                int res;
                                long diff;

                                if (RoomMember.MEMBERSHIP_JOIN.equals(r2.membership) && RoomMember.MEMBERSHIP_INVITE.equals(r1.membership)) {
                                    res = 1;
                                } else if (r2.membership.equals(r1.membership)) {
                                    diff = r1.getOriginServerTs() - r2.getOriginServerTs();
                                    res = (0 == diff) ? 0 : ((diff > 0) ? 1 : -1);
                                } else {
                                    res = -1;
                                }
                                return res;
                            }
                        });

                        int nextIndexSearch = 0;

                        // take the oldest join member
                        if (!TextUtils.equals(members.get(0).getUserId(), getMyUserId())) {
                            if (RoomMember.MEMBERSHIP_JOIN.equals(members.get(0).membership)) {
                                directChatMember = members.get(0);
                            }
                        } else {
                            nextIndexSearch = 1;
                            if (RoomMember.MEMBERSHIP_JOIN.equals(members.get(1).membership)) {
                                directChatMember = members.get(1);
                            }
                        }

                        // no join member found, test the oldest join member
                        if (null == directChatMember) {
                            if (RoomMember.MEMBERSHIP_INVITE.equals(members.get(nextIndexSearch).membership)) {
                                directChatMember = members.get(nextIndexSearch);
                            }
                        }
                    }

                    // last option: get the logged user
                    if (null == directChatMember) {
                        directChatMember = members.get(0);
                    }

                    chosenUserId = directChatMember.getUserId();
                } else {
                    chosenUserId = aParticipantUserId;
                }

                // search if there is an entry with the same user
                if (params.containsKey(chosenUserId)) {
                    roomIdsList = new ArrayList<>(params.get(chosenUserId));
                }

                roomIdsList.add(roomId); // update room list with the new room
                params.put(chosenUserId, roomIdsList);
            } else {
                // remove the current room from the direct chat list rooms
                if (null != store.getDirectChatRoomsDict()) {
                    Collection<List<String>> listOfList = store.getDirectChatRoomsDict().values();

                    for (List<String> list : listOfList) {
                        if (list.contains(roomId)) {
                            list.remove(roomId);
                        }
                    }
                } else {
                    // should not happen: if the room has to be removed, it means the room has been
                    //  previously detected as being part of the listOfList
                    Log.e(LOG_TAG, "## toggleDirectChatRoom(): failed to remove a direct chat room (not seen as direct chat room)");
                    return;
                }
            }

            HashMap<String, Object> requestParams = new HashMap<>();
            Collection<String> userIds = params.keySet();

            for(String userId : userIds) {
                requestParams.put(userId, params.get(userId));
            }

            mAccountDataRestClient.setAccountData(getMyUserId(), AccountDataRestClient.ACCOUNT_DATA_TYPE_DIRECT_MESSAGES, requestParams, callback);
        }
    }

    /**
     * For the value account_data with the rooms list passed in aRoomIdsListToAdd for a given user ID (aParticipantUserId)<br>
     * WARNING: this method must be used with care because it erases the account_data object.
     * @param aRoomParticipantUserIdList the couple direct chat rooms ID / user IDs
     * @param callback the asynchronous response callback
     */
    public void forceDirectChatRoomValue(ArrayList<RoomIdsListRetroCompat> aRoomParticipantUserIdList, ApiCallback<Void> callback) {
        HashMap<String, List<String>> params = new HashMap<>();
        ArrayList<String> roomIdsList;

        if(null != aRoomParticipantUserIdList) {

            for(RoomIdsListRetroCompat item: aRoomParticipantUserIdList) {
                if(params.containsKey(item.mParticipantUserId)) {
                    roomIdsList = new ArrayList<>(params.get(item.mParticipantUserId));
                    roomIdsList.add(item.mRoomId);
                } else {
                    roomIdsList = new ArrayList<>();
                    roomIdsList.add(item.mRoomId);
                }
                params.put(item.mParticipantUserId, roomIdsList);
            }

            HashMap<String, Object> requestParams = new HashMap<>();

            Collection<String> userIds = params.keySet();
            for(String userId : userIds) {
                requestParams.put(userId, params.get(userId));
            }

            mAccountDataRestClient.setAccountData(getMyUserId(), AccountDataRestClient.ACCOUNT_DATA_TYPE_DIRECT_MESSAGES, requestParams, callback);
        }
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

    /**
     * Invalidate the access token, so that it can no longer be used for authorization.
     * @param context the application context
     * @param callback the callback success and failure callback
     */
    public void logout(final Context context, final ApiCallback<Void> callback) {
        // Clear crypto data
        // For security and because it will be no more useful as we will get a new device id
        // on the next log in
        enableCrypto(false, null);

        mLoginRestClient.logout(new ApiCallback<JsonObject>() {
            @Override
            public void onSuccess(JsonObject info) {
                clear(context);
                if (null != callback) {
                    callback.onSuccess(null);
                }
            }

            @Override
            public void onNetworkError(Exception e) {
                clear(context);
                if (null != callback) {
                    callback.onNetworkError(e);
                }
            }

            @Override
            public void onMatrixError(MatrixError e) {
                clear(context);
                if (null != callback) {
                    callback.onMatrixError(e);
                }
            }

            @Override
            public void onUnexpectedError(Exception e) {
                clear(context);
                if (null != callback) {
                    callback.onUnexpectedError(e);
                }
            }
        });
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
     * enable encryption by default when launching the session
     */
    private boolean mEnableCryptoWhenStartingMXSession = false;

    /**
     * Enable the crypto when initializing a new session.
     */
    public void enableCryptoWhenStarting() {
        mEnableCryptoWhenStartingMXSession = true;
    }

    /**
     * When the encryption is toogled, the room summaries must be updated
     * to display the right messages.
     */
    private void decryptRoomSummaries() {
        Collection<RoomSummary> summaries = getDataHandler().getStore().getSummaries();

        for(RoomSummary summary :summaries) {
            mDataHandler.decryptEvent(summary.getLatestReceivedEvent(), null);
        }
    }

    /**
     * Enable / disable the crypto
     * @param cryptoEnabled true to enable the crypto
     */
    public void enableCrypto(boolean cryptoEnabled, final ApiCallback<Void> callback) {
        if (cryptoEnabled != isCryptoEnabled()) {
            if (cryptoEnabled) {
                Log.d(LOG_TAG, "Crypto is enabled");
                MXFileCryptoStore fileCryptoStore = new MXFileCryptoStore();
                fileCryptoStore.initWithCredentials(mAppContent, mCredentials);
                fileCryptoStore.open();
                mCrypto = new MXCrypto(this, fileCryptoStore);
                mCrypto.start(new ApiCallback<Void>() {
                    @Override
                    public void onSuccess(Void info) {
                        decryptRoomSummaries();
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
            } else if (null != mCrypto) {
                Log.d(LOG_TAG, "Crypto is disabled");
                IMXCryptoStore store = mCrypto.mCryptoStore;
                mCrypto.close();
                store.deleteStore();
                mCrypto = null;
                mDataHandler.setCrypto(null);

                decryptRoomSummaries();

                if (null != callback) {
                    callback.onSuccess(null);
                }
            }

            mDataHandler.setCrypto(mCrypto);
        } else {
            if (null != callback) {
                callback.onSuccess(null);
            }
        }
    }

    /**
     * Retrieves the devices list
     * @param callback the asynchronous callback
     */
    public void getDevicesList(ApiCallback<DevicesListResponse> callback) {
        mCryptoRestClient.getDevices(callback);
    }

    /**
     * Delete a device
     * @param deviceId the device id
     * @param password the passwoerd
     * @param callback the asynchronous callback.
     */
    public void deleteDevice(final String deviceId, final String password, final ApiCallback<Void> callback) {
        DeleteDeviceParams dummyparams = new DeleteDeviceParams();

        mCryptoRestClient.deleteDevice(deviceId, dummyparams, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                // should never happen
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
            public void onMatrixError(MatrixError matrixError) {
                Log.d(LOG_TAG, "## checkNameAvailability(): The registration continues");
                RegistrationFlowResponse registrationFlowResponse = null;

                // expected status code is 401
                if ((null != matrixError.mStatus) && (matrixError.mStatus == 401)) {
                    try {
                        registrationFlowResponse = JsonUtils.toRegistrationFlowResponse(matrixError.mErrorBodyAsString);
                    } catch (Exception castExcept) {
                        Log.e(LOG_TAG, "## deleteDevice(): Received status 401 - Exception - JsonUtils.toRegistrationFlowResponse()");
                    }
                } else {
                    Log.d(LOG_TAG, "## deleteDevice(): Received not expected status 401 ="+ matrixError.mStatus);
                }

                // check if the server response can be casted
                if (null != registrationFlowResponse) {
                    DeleteDeviceParams params = new DeleteDeviceParams();

                    params.auth = new DeleteDeviceAuth();
                    params.auth.session = registrationFlowResponse.session;
                    params.auth.type = "m.login.password";
                    params.auth.user = mCredentials.userId;
                    params.auth.password = password;
                    mCryptoRestClient.deleteDevice(deviceId, params, callback);
                } else {
                    if (null != callback) {
                        callback.onMatrixError(matrixError);
                    }
                }
            }

            @Override
            public void onUnexpectedError(Exception e) {
                if (null != callback) {
                    callback.onNetworkError(e);
                }
            }
        });
    }
}