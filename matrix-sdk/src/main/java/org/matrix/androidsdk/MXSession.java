/*
 * Copyright 2014 OpenMarket Ltd
 * Copyright 2017 Vector Creations Ltd
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
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.google.gson.JsonObject;

import org.matrix.androidsdk.call.MXCallsManager;
import org.matrix.androidsdk.crypto.MXCrypto;
import org.matrix.androidsdk.data.DataRetriever;
import org.matrix.androidsdk.data.MyUser;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.data.RoomSummary;
import org.matrix.androidsdk.data.RoomTag;
import org.matrix.androidsdk.data.cryptostore.IMXCryptoStore;
import org.matrix.androidsdk.data.cryptostore.MXFileCryptoStore;
import org.matrix.androidsdk.data.store.IMXStore;
import org.matrix.androidsdk.data.store.MXStoreListener;
import org.matrix.androidsdk.db.MXLatestChatMessageCache;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.groups.GroupsManager;
import org.matrix.androidsdk.network.NetworkConnectivityReceiver;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.ApiFailureCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.client.AccountDataRestClient;
import org.matrix.androidsdk.rest.client.BingRulesRestClient;
import org.matrix.androidsdk.rest.client.CallRestClient;
import org.matrix.androidsdk.rest.client.CryptoRestClient;
import org.matrix.androidsdk.rest.client.EventsRestClient;
import org.matrix.androidsdk.rest.client.GroupsRestClient;
import org.matrix.androidsdk.rest.client.LoginRestClient;
import org.matrix.androidsdk.rest.client.PresenceRestClient;
import org.matrix.androidsdk.rest.client.ProfileRestClient;
import org.matrix.androidsdk.rest.client.PushersRestClient;
import org.matrix.androidsdk.rest.client.RoomsRestClient;
import org.matrix.androidsdk.rest.client.ThirdPidRestClient;
import org.matrix.androidsdk.rest.model.CreateRoomParams;
import org.matrix.androidsdk.rest.model.CreateRoomResponse;
import org.matrix.androidsdk.rest.model.pid.DeleteDeviceAuth;
import org.matrix.androidsdk.rest.model.pid.DeleteDeviceParams;
import org.matrix.androidsdk.rest.model.sync.DevicesListResponse;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.message.MediaMessage;
import org.matrix.androidsdk.rest.model.message.Message;
import org.matrix.androidsdk.rest.model.ReceiptData;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.sync.RoomResponse;
import org.matrix.androidsdk.rest.model.search.SearchResponse;
import org.matrix.androidsdk.rest.model.search.SearchUsersResponse;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.rest.model.bingrules.BingRule;
import org.matrix.androidsdk.rest.model.login.Credentials;
import org.matrix.androidsdk.rest.model.login.LoginFlow;
import org.matrix.androidsdk.rest.model.login.RegistrationFlowResponse;
import org.matrix.androidsdk.sync.DefaultEventsThreadListener;
import org.matrix.androidsdk.sync.EventsThread;
import org.matrix.androidsdk.sync.EventsThreadListener;
import org.matrix.androidsdk.util.BingRulesManager;
import org.matrix.androidsdk.util.ContentManager;
import org.matrix.androidsdk.util.ContentUtils;
import org.matrix.androidsdk.util.JsonUtils;
import org.matrix.androidsdk.util.Log;
import org.matrix.androidsdk.util.UnsentEventsManager;
import org.matrix.olm.OlmManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Class that represents one user's session with a particular home server.
 * There can potentially be multiple sessions for handling multiple accounts.
 */
public class MXSession {
    private static final String LOG_TAG = MXSession.class.getSimpleName();

    private DataRetriever mDataRetriever;
    private MXDataHandler mDataHandler;
    private EventsThread mEventsThread;
    private final Credentials mCredentials;

    // Api clients
    private EventsRestClient mEventsRestClient;
    private ProfileRestClient mProfileRestClient;
    private PresenceRestClient mPresenceRestClient;
    private RoomsRestClient mRoomsRestClient;
    private final BingRulesRestClient mBingRulesRestClient;
    private final PushersRestClient mPushersRestClient;
    private final ThirdPidRestClient mThirdPidRestClient;
    private final CallRestClient mCallRestClient;
    private final AccountDataRestClient mAccountDataRestClient;
    private final CryptoRestClient mCryptoRestClient;
    private final LoginRestClient mLoginRestClient;
    private final GroupsRestClient mGroupsRestClient;

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
    private boolean mIsOnline = false;
    private int mSyncTimeout = 0;
    private int mSyncDelay = 0;

    private final HomeServerConnectionConfig mHsConfig;

    // the application is launched from a notification
    // so, mEventsThread.start might be not ready
    private boolean mIsBgCatchupPending = false;

    // tell if the data save mode is enabled
    private boolean mUseDataSaveMode;

    // tell if the rageshake mode is enabled
    public boolean mUseRageShakeMode;
    
    // the groups manager
    private GroupsManager mGroupsManager;

    // load the crypto libs.
    public static OlmManager mOlmManager = new OlmManager();

    // regex pattern to find matrix user ids in a string.
    // See https://matrix.org/speculator/spec/HEAD/appendices.html#historical-user-ids
    public static final String MATRIX_USER_IDENTIFIER_REGEX = "@[A-Z0-9\\x21-\\x39\\x3B-\\x7F]+:[A-Z0-9.-]+(\\.[A-Z]{2,})?+(\\:[0-9]{2,})?";
    public static final Pattern PATTERN_CONTAIN_MATRIX_USER_IDENTIFIER = Pattern.compile(MATRIX_USER_IDENTIFIER_REGEX, Pattern.CASE_INSENSITIVE);

    // regex pattern to find room aliases in a string.
    public static final String MATRIX_ROOM_ALIAS_REGEX = "#[A-Z0-9._%#+-]+:[A-Z0-9.-]+(\\.[A-Z]{2,})?+(\\:[0-9]{2,})?";
    public static final Pattern PATTERN_CONTAIN_MATRIX_ALIAS = Pattern.compile(MATRIX_ROOM_ALIAS_REGEX, Pattern.CASE_INSENSITIVE);

    // regex pattern to find room ids in a string.
    public static final String MATRIX_ROOM_IDENTIFIER_REGEX = "![A-Z0-9]+:[A-Z0-9.-]+(\\.[A-Z]{2,})?+(\\:[0-9]{2,})?";
    public static final Pattern PATTERN_CONTAIN_MATRIX_ROOM_IDENTIFIER = Pattern.compile(MATRIX_ROOM_IDENTIFIER_REGEX, Pattern.CASE_INSENSITIVE);

    // regex pattern to find message ids in a string.
    public static final String MATRIX_MESSAGE_IDENTIFIER_REGEX = "\\$[A-Z0-9]+:[A-Z0-9.-]+(\\.[A-Z]{2,})?+(\\:[0-9]{2,})?";
    public static final Pattern PATTERN_CONTAIN_MATRIX_MESSAGE_IDENTIFIER = Pattern.compile(MATRIX_MESSAGE_IDENTIFIER_REGEX, Pattern.CASE_INSENSITIVE);

    // regex pattern to find group ids in a string.
    public static final String MATRIX_GROUP_IDENTIFIER_REGEX = "\\+[A-Z0-9=_\\-./]+:[A-Z0-9.-]+(\\.[A-Z]{2,})?+(\\:[0-9]{2,})?";
    public static final Pattern PATTERN_CONTAIN_MATRIX_GROUP_IDENTIFIER = Pattern.compile(MATRIX_GROUP_IDENTIFIER_REGEX, Pattern.CASE_INSENSITIVE);

    // regex pattern to find permalink with message id.
    // Android does not support in URL so extract it.
    public static final Pattern PATTERN_CONTAIN_MATRIX_TO_PERMALINK_ROOM_ID = Pattern.compile("https:\\/\\/matrix\\.to\\/#\\/" + MATRIX_ROOM_IDENTIFIER_REGEX + "\\/" + MATRIX_MESSAGE_IDENTIFIER_REGEX, Pattern.CASE_INSENSITIVE);
    public static final Pattern PATTERN_CONTAIN_MATRIX_TO_PERMALINK_ROOM_ALIAS = Pattern.compile("https:\\/\\/matrix\\.to\\/#\\/" + MATRIX_ROOM_ALIAS_REGEX + "\\/" + MATRIX_MESSAGE_IDENTIFIER_REGEX, Pattern.CASE_INSENSITIVE);

    public static final Pattern PATTERN_CONTAIN_APP_LINK_PERMALINK_ROOM_ID = Pattern.compile("https:\\/\\/[A-Z0-9.-]+\\.[A-Z]{2,}\\/[A-Z]{3,}\\/#\\/room\\/" + MATRIX_ROOM_IDENTIFIER_REGEX + "\\/" + MATRIX_MESSAGE_IDENTIFIER_REGEX, Pattern.CASE_INSENSITIVE);
    public static final Pattern PATTERN_CONTAIN_APP_LINK_PERMALINK_ROOM_ALIAS = Pattern.compile("https:\\/\\/[A-Z0-9.-]+\\.[A-Z]{2,}\\/[A-Z]{3,}\\/#\\/room\\/" + MATRIX_ROOM_ALIAS_REGEX + "\\/" + MATRIX_MESSAGE_IDENTIFIER_REGEX, Pattern.CASE_INSENSITIVE);

    /**
     * Create a basic session for direct API calls.
     *
     * @param hsConfig the home server connection config
     */
    private MXSession(HomeServerConnectionConfig hsConfig) {
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
        mGroupsRestClient = new GroupsRestClient(hsConfig);
    }

    /**
     * Create a user session with a data handler.
     *
     * @param hsConfig    the home server connection config
     * @param dataHandler the data handler
     * @param appContext  the application context
     */
    public MXSession(HomeServerConnectionConfig hsConfig, MXDataHandler dataHandler, Context appContext) {
        this(hsConfig);
        mDataHandler = dataHandler;

        mDataHandler.getStore().addMXStoreListener(new MXStoreListener() {
            @Override
            public void onStoreReady(String accountId) {
                Log.d(LOG_TAG, "## onStoreReady()");
                getDataHandler().onStoreReady();
            }

            @Override
            public void onStoreCorrupted(String accountId, String description) {
                Log.d(LOG_TAG, "## onStoreCorrupted() : token " + getDataHandler().getStore().getEventStreamToken());

                // nothing was saved
                if (null == getDataHandler().getStore().getEventStreamToken()) {
                    getDataHandler().onStoreReady();
                }
            }

            @Override
            public void postProcess(String accountId) {
                getDataHandler().checkPermanentStorageData();

                // test if the crypto instance has already been created
                if (null == mCrypto) {
                    MXFileCryptoStore store = new MXFileCryptoStore();
                    store.initWithCredentials(mAppContent, mCredentials);

                    if (store.hasData() || mEnableCryptoWhenStartingMXSession) {
                        Log.d(LOG_TAG, "## postProcess() : create the crypto instance for session " + this);
                        checkCrypto();
                    } else {
                        Log.e(LOG_TAG, "## postProcess() : no crypto data");
                    }
                } else {
                    Log.e(LOG_TAG, "## postProcess() : mCrypto is already created");
                }
            }

            @Override
            public void onReadReceiptsLoaded(final String roomId) {
                final List<ReceiptData> receipts = mDataHandler.getStore().getEventReceipts(roomId, null, false, false);
                final ArrayList<String> senders = new ArrayList<>();

                for (ReceiptData receipt : receipts) {
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
        mDataHandler.setEventsRestClient(mEventsRestClient);
        mDataHandler.setAccountDataRestClient(mAccountDataRestClient);

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
        mGroupsRestClient.setUnsentEventsManager(mUnsentEventsManager);

        // return the default cache manager
        mLatestChatMessageCache = new MXLatestChatMessageCache(mCredentials.userId);
        mMediasCache = new MXMediasCache(mContentManager, mNetworkConnectivityReceiver, mCredentials.userId, appContext);
        mDataHandler.setMediasCache(mMediasCache);

        mGroupsManager = new GroupsManager(mDataHandler, mGroupsRestClient);
        mDataHandler.setGroupsManager(mGroupsManager);
    }

    private void checkIfAlive() {
        synchronized (this) {
            if (!mIsAliveSession) {
                try {
                    StackTraceElement[] callstacks = Thread.currentThread().getStackTrace();

                    StringBuilder sb = new StringBuilder();
                    for (StackTraceElement element : callstacks) {
                        sb.append(element.toString());
                        sb.append("\n");
                    }

                    Log.e(LOG_TAG, "Use of a released session : \n" + sb.toString());
                } catch (Exception e) {
                    Log.e(LOG_TAG, "Use of a released session : \n");
                }

                //throw new AssertionError("Should not used a cleared mxsession ");
            }
        }
    }

    /**
     * Init the user-agent used by the REST requests.
     *
     * @param context the application context
     */
    public static void initUserAgent(Context context) {
        RestClient.initUserAgent(context);
    }

    /**
     * Provides the lib version.
     *
     * @param longFormat true to have a long format i.e with date and time.
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
     * Provides the crypto lib version.
     *
     * @param context    the context
     * @param longFormat true to have a long version (with date and time)
     * @return the crypto lib version
     */
    public String getCryptoVersion(Context context, boolean longFormat) {
        String version = "";

        if (null != mOlmManager) {
            version = longFormat ? mOlmManager.getDetailedVersion(context) : mOlmManager.getVersion();
        }

        return version;
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

    public ThirdPidRestClient getThirdPidRestClient() {
        checkIfAlive();
        return mThirdPidRestClient;
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

    public HomeServerConnectionConfig getHomeServerConfig() {
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
     * Provides the application caches size.
     *
     * @param context  the context
     * @param callback the asynchronous callback
     */
    public static void getApplicationSizeCaches(final Context context, final SimpleApiCallback<Long> callback) {
        AsyncTask<Void, Void, Long> task = new AsyncTask<Void, Void, Long>() {
            @Override
            protected Long doInBackground(Void... params) {
                return ContentUtils.getDirectorySize(context, context.getApplicationContext().getFilesDir().getParentFile(), 5);
            }

            @Override
            protected void onPostExecute(Long result) {
                Log.d(LOG_TAG, "## getCacheSize() : " + result);
                if (null != callback) {
                    callback.onSuccess(result);
                }
            }
        };
        try {
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        } catch (final Exception e) {
            Log.e(LOG_TAG, "## getApplicationSizeCaches() : failed " + e.getMessage());
            task.cancel(true);

            (new android.os.Handler(Looper.getMainLooper())).post(new Runnable() {
                @Override
                public void run() {
                    if (null != callback) {
                        callback.onUnexpectedError(e);
                    }
                }
            });

        }
    }

    /**
     * Clear the application cache
     */
    private void clearApplicationCaches(Context context) {
        mDataHandler.clear();

        // network event will not be listened anymore
        try {
            mAppContent.unregisterReceiver(mNetworkConnectivityReceiver);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## clearApplicationCaches() : unregisterReceiver failed " + e.getMessage());
        }
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
     * Clear the session data synchronously.
     *
     * @param context the context
     */
    public void clear(final Context context) {
        clear(context, null);
    }

    /**
     * Clear the session data.
     * if the callback is null, the clear is synchronous.
     *
     * @param context  the context
     * @param callback the asynchronous callback
     */
    public void clear(final Context context, final ApiCallback<Void> callback) {
        synchronized (this) {
            if (!mIsAliveSession) {
                Log.e(LOG_TAG, "## clear() was already called");
                return;
            }

            mIsAliveSession = false;
        }

        // stop events stream
        stopEventStream();

        if (null == callback) {
            clearApplicationCaches(context);
        } else {
            // clear the caches in a background thread to avoid blocking the UI thread
            AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... params) {
                    clearApplicationCaches(context);
                    return null;
                }

                @Override
                protected void onPostExecute(Void args) {
                    if (null != callback) {
                        callback.onSuccess(null);
                    }
                }
            };

            try {
                task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            } catch (final Exception e) {
                Log.e(LOG_TAG, "## clear() failed " + e.getMessage());
                task.cancel(true);

                (new android.os.Handler(Looper.getMainLooper())).post(new Runnable() {
                    @Override
                    public void run() {
                        if (null != callback) {
                            callback.onUnexpectedError(e);
                        }
                    }
                });
            }
        }
    }

    /**
     * Remove the medias older than the provided timestamp.
     *
     * @param context   the context
     * @param timestamp the timestamp (in seconds)
     */
    public void removeMediasBefore(final Context context, final long timestamp) {
        // list the files to keep even if they are older than the provided timestamp
        // because their upload failed
        final Set<String> filesToKeep = new HashSet<>();
        IMXStore store = getDataHandler().getStore();

        Collection<Room> rooms = store.getRooms();

        for (Room room : rooms) {
            Collection<Event> events = store.getRoomMessages(room.getRoomId());
            if (null != events) {
                for (Event event : events) {
                    try {
                        if (TextUtils.equals(Event.EVENT_TYPE_MESSAGE, event.getType())) {
                            Message message = JsonUtils.toMessage(event.getContent());

                            if (message instanceof MediaMessage) {
                                MediaMessage mediaMessage = (MediaMessage) message;

                                if (mediaMessage.isThumbnailLocalContent()) {
                                    filesToKeep.add(Uri.parse(mediaMessage.getThumbnailUrl()).getPath());
                                }

                                if (mediaMessage.isLocalContent()) {
                                    filesToKeep.add(Uri.parse(mediaMessage.getUrl()).getPath());
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "## removeMediasBefore() : failed " + e.getMessage());
                    }
                }
            }
        }

        AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                long length = getMediasCache().removeMediasBefore(timestamp, filesToKeep);

                // delete also the log files
                // they might be large
                File logsDir = Log.getLogDirectory();

                if (null != logsDir) {
                    File[] logFiles = logsDir.listFiles();

                    if (null != logFiles) {
                        for (File file : logFiles) {
                            if (ContentUtils.getLastAccessTime(file) < timestamp) {
                                length += file.length();
                                file.delete();
                            }
                        }
                    }
                }

                if (0 != length) {
                    Log.d(LOG_TAG, "## removeMediasBefore() : save " + android.text.format.Formatter.formatFileSize(context, length));
                } else {
                    Log.d(LOG_TAG, "## removeMediasBefore() : useless");
                }

                return null;
            }
        };
        try {
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## removeMediasBefore() : failed " + e.getMessage());
            task.cancel(true);
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

        // reported by a rageshake issue
        // startEventStream might be called several times
        // when the service is killed and automatically restarted.
        // It might be restarted by itself and by android at the same time.
        synchronized (LOG_TAG) {
            if (mEventsThread != null) {
                if (!mEventsThread.isAlive()) {
                    mEventsThread = null;
                    Log.e(LOG_TAG, "startEventStream() : create a new EventsThread");
                } else {
                    // https://github.com/vector-im/riot-android/issues/1331
                    mEventsThread.cancelKill();
                    Log.e(LOG_TAG, "Ignoring startEventStream() : Thread already created.");
                    return;
                }
            }

            if (mDataHandler == null) {
                Log.e(LOG_TAG, "Error starting the event stream: No data handler is defined");
                return;
            }

            Log.d(LOG_TAG, "startEventStream : create the event stream");

            final EventsThreadListener fEventsListener = (null == anEventsListener) ? new DefaultEventsThreadListener(mDataHandler) : anEventsListener;

            mEventsThread = new EventsThread(mAppContent, mEventsRestClient, fEventsListener, initialToken);
            mEventsThread.setNetworkConnectivityReceiver(networkConnectivityReceiver);
            mEventsThread.setIsOnline(mIsOnline);
            mEventsThread.setServerLongPollTimeout(mSyncTimeout);
            mEventsThread.setSyncDelay(mSyncDelay);

            if (mFailureCallback != null) {
                mEventsThread.setFailureCallback(mFailureCallback);
            }

            mEventsThread.setUseDataSaveMode(mUseDataSaveMode);
            mEventsThread.setUseRageShakeMode(mUseRageShakeMode);

            if (mCredentials.accessToken != null && !mEventsThread.isAlive()) {
                // GA issue
                try {
                    mEventsThread.start();
                } catch (Exception e) {
                    Log.e(LOG_TAG, "## startEventStream() :  mEventsThread.start failed " + e.getMessage());
                }

                if (mIsBgCatchupPending) {
                    Log.d(LOG_TAG, "startEventStream : start a catchup");
                    mIsBgCatchupPending = false;
                    // catchup retrieve any available messages before stop the sync
                    mEventsThread.catchup();
                }
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
                Log.d(LOG_TAG, "refreshToken : onNetworkError " + e.getMessage());
            }

            @Override
            public void onMatrixError(MatrixError e) {
                Log.d(LOG_TAG, "refreshToken : onMatrixError " + e.getMessage());
            }

            @Override
            public void onUnexpectedError(Exception e) {
                Log.d(LOG_TAG, "refreshToken : onMatrixError " + e.getMessage());
            }
        });
    }

    /**
     * Update the online status
     *
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
     * @return true if the client is seen as "online"
     */
    public boolean isOnline() {
        return mIsOnline;
    }

    /**
     * Update the heartbeat request timeout.
     *
     * @param ms the delay in ms
     */
    public void setSyncTimeout(int ms) {
        mSyncTimeout = ms;
        if (null != mEventsThread) {
            mEventsThread.setServerLongPollTimeout(ms);
        }
    }

    /**
     * @return the heartbeat request timeout
     */
    public int getSyncTimeout() {
        return mSyncTimeout;
    }

    /**
     * Set a delay between two sync requests.
     *
     * @param ms the delay in ms
     */
    public void setSyncDelay(int ms) {
        mSyncDelay = ms;
        if (null != mEventsThread) {
            mEventsThread.setSyncDelay(ms);
        }
    }

    /**
     * @return the delay between two sync requests.
     */
    public int getSyncDelay() {
        return mSyncDelay;
    }

    /**
     * Update the data save mode
     *
     * @param enabled true to enable the data save mode
     */
    public void setUseDataSaveMode(boolean enabled) {
        mUseDataSaveMode = enabled;
        if (null != mEventsThread) {
            mEventsThread.setUseDataSaveMode(enabled);
        }
    }

    /**
     * Update the rageshake mode
     *
     * @param enabled true to enable the rageshake mode
     */
    public void setUseRageShakeMode(boolean enabled) {
        mUseRageShakeMode = enabled;
        if (null != mEventsThread) {
            mEventsThread.setUseRageShakeMode(enabled);
        }
    }

    /**
     * Refresh the network connection information.
     * On android version older than 6.0, the doze mode might have killed the network connection.
     */
    public void refreshNetworkConnection() {
        if (null != mNetworkConnectivityReceiver) {
            // mNetworkConnectivityReceiver is a broadcastReceiver
            // but some users reported that the network updates were not dispatched
            mNetworkConnectivityReceiver.checkNetworkConnection(mAppContent);
        }
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

        if (null != getMediasCache()) {
            getMediasCache().clearTmpCache();
        }

        if (null != mGroupsManager) {
            mGroupsManager.onSessionPaused();
        }
    }

    /**
     * @return the current sync token
     */
    public String getCurrentSyncToken() {
        return (null != mEventsThread) ? mEventsThread.getCurrentSyncToken() : null;
    }

    /**
     * Resume the event stream
     */
    public void resumeEventStream() {
        checkIfAlive();

        if (null != mNetworkConnectivityReceiver) {
            // mNetworkConnectivityReceiver is a broadcastReceiver
            // but some users reported that the network updates were not dispatched
            mNetworkConnectivityReceiver.checkNetworkConnection(mAppContent);
        }

        if (null != mCallsManager) {
            mCallsManager.unpauseTurnServerRefresh();
        }

        if (null != mEventsThread) {
            Log.d(LOG_TAG, "## resumeEventStream() : unpause");
            mEventsThread.unpause();
        } else {
            Log.e(LOG_TAG, "resumeEventStream : mEventsThread is null");
        }

        if (mIsBgCatchupPending) {
            mIsBgCatchupPending = false;
            Log.d(LOG_TAG, "## resumeEventStream() : cancel bg sync");
        }

        if (null != mGroupsManager) {
            mGroupsManager.onSessionResumed();
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
            mIsBgCatchupPending = true;
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
     * @param callback the async callback once the room is ready
     */
    public void createRoom(final ApiCallback<String> callback) {
        createRoom(null, null, null, callback);
    }

    /**
     * Create a new room with given properties. Needs the data handler.
     *
     * @param name     the room name
     * @param topic    the room topic
     * @param alias    the room alias
     * @param callback the async callback once the room is ready
     */
    public void createRoom(String name, String topic, String alias, final ApiCallback<String> callback) {
        createRoom(name, topic, RoomState.DIRECTORY_VISIBILITY_PRIVATE, alias, RoomState.GUEST_ACCESS_CAN_JOIN, RoomState.HISTORY_VISIBILITY_SHARED, null, callback);
    }

    /**
     * Create a new room with given properties. Needs the data handler.
     *
     * @param name              the room name
     * @param topic             the room topic
     * @param visibility        the room visibility
     * @param alias             the room alias
     * @param guestAccess       the guest access rule (see {@link RoomState#GUEST_ACCESS_CAN_JOIN} or {@link RoomState#GUEST_ACCESS_FORBIDDEN})
     * @param historyVisibility the history visibility
     * @param algorithm         the crypto algorithm (null to create an unencrypted room)
     * @param callback          the async callback once the room is ready
     */
    public void createRoom(String name, String topic, String visibility, String alias, String guestAccess, String historyVisibility, String algorithm, final ApiCallback<String> callback) {
        checkIfAlive();

        CreateRoomParams params = new CreateRoomParams();
        params.name = !TextUtils.isEmpty(name) ? name : null;
        params.topic = !TextUtils.isEmpty(topic) ? topic : null;
        params.visibility = !TextUtils.isEmpty(visibility) ? visibility : null;
        params.roomAliasName = !TextUtils.isEmpty(alias) ? alias : null;
        params.guest_access = !TextUtils.isEmpty(guestAccess) ? guestAccess : null;
        params.history_visibility = !TextUtils.isEmpty(historyVisibility) ? historyVisibility : null;
        params.addCryptoAlgorithm(algorithm);

        createRoom(params, callback);
    }

    /**
     * Create an encrypted room.
     *
     * @param algorithm the encryption algorithm.
     * @param callback  the async callback once the room is ready
     */
    public void createEncryptedRoom(String algorithm, final ApiCallback<String> callback) {
        CreateRoomParams params = new CreateRoomParams();
        params.addCryptoAlgorithm(algorithm);
        createRoom(params, callback);
    }

    /**
     * Create a direct message room with one participant.<br>
     * The participant can be a user ID or mail address. Once the room is created, on success, the room
     * is set as a "direct message" with the participant.
     *
     * @param aParticipantUserId  user ID (or user mail) to be invited in the direct message room
     * @param aCreateRoomCallBack async call back response
     * @return true if the invite was performed, false otherwise
     */
    public boolean createDirectMessageRoom(final String aParticipantUserId, final ApiCallback<String> aCreateRoomCallBack) {
        return createDirectMessageRoom(aParticipantUserId, null, aCreateRoomCallBack);
    }

    /**
     * Create a direct message room with one participant.<br>
     * The participant can be a user ID or mail address. Once the room is created, on success, the room
     * is set as a "direct message" with the participant.
     *
     * @param aParticipantUserId  user ID (or user mail) to be invited in the direct message room
     * @param algorithm           the crypto algorithm (null to create an unencrypted room)
     * @param aCreateRoomCallBack async call back response
     * @return true if the invite was performed, false otherwise
     */
    public boolean createDirectMessageRoom(final String aParticipantUserId, final String algorithm, final ApiCallback<String> aCreateRoomCallBack) {
        boolean retCode = false;

        if (!TextUtils.isEmpty(aParticipantUserId)) {
            retCode = true;
            CreateRoomParams params = new CreateRoomParams();

            params.addCryptoAlgorithm(algorithm);
            params.setDirectMessage();
            params.addParticipantIds(mHsConfig, Arrays.asList(aParticipantUserId));

            createRoom(params, aCreateRoomCallBack);
        }

        return retCode;
    }

    /**
     * Finalise the created room as a direct chat one.
     *
     * @param roomId   the room id
     * @param userId   the user id
     * @param callback the asynchronous callback
     */
    private void finalizeDMRoomCreation(final String roomId, String userId, final ApiCallback<String> callback) {
        final String fRoomId = roomId;

        toggleDirectChatRoom(roomId, userId, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                Room room = getDataHandler().getRoom(fRoomId);

                if (null != room) {
                    room.markAllAsRead(null);
                }

                if (null != callback) {
                    callback.onSuccess(fRoomId);
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
     * Create a new room with given properties.
     *
     * @param params   the creation parameters.
     * @param callback the async callback once the room is ready
     */
    public void createRoom(final CreateRoomParams params, final ApiCallback<String> callback) {
        mRoomsRestClient.createRoom(params, new SimpleApiCallback<CreateRoomResponse>(callback) {
            @Override
            public void onSuccess(CreateRoomResponse info) {
                final String roomId = info.roomId;
                final Room createdRoom = mDataHandler.getRoom(roomId);

                // the creation events are not be called during the creation
                if (createdRoom.isWaitingInitialSync()) {
                    createdRoom.setOnInitialSyncCallback(new ApiCallback<Void>() {
                        @Override
                        public void onSuccess(Void info) {
                            createdRoom.markAllAsRead(null);

                            if (params.isDirect()) {
                                finalizeDMRoomCreation(roomId, params.getFirstInvitedUserId(), callback);
                            } else {
                                callback.onSuccess(roomId);
                            }
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
                    createdRoom.markAllAsRead(null);

                    if (params.isDirect()) {
                        finalizeDMRoomCreation(roomId, params.getFirstInvitedUserId(), callback);
                    } else {
                        callback.onSuccess(roomId);
                    }
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

                    // wait until the initial sync is done
                    if (joinedRoom.isWaitingInitialSync()) {
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
                        // to initialise the notification counters
                        joinedRoom.markAllAsRead(null);
                        callback.onSuccess(roomId);
                    }
                }
            });
        }
    }

    /**
     * Send the read receipts to the latest room messages.
     *
     * @param rooms    the rooms list
     * @param callback the asynchronous callback
     */
    public void markRoomsAsRead(final Collection<Room> rooms, final ApiCallback<Void> callback) {
        if ((null == rooms) || (0 == rooms.size())) {
            if (null != callback) {
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onSuccess(null);
                    }
                });
            }
            return;
        }

        markRoomsAsRead(rooms.iterator(), callback);
    }

    /**
     * Send the read receipts to the latest room messages.
     *
     * @param roomsIterator the rooms list iterator
     * @param callback      the asynchronous callback
     */
    private void markRoomsAsRead(final Iterator roomsIterator, final ApiCallback<Void> callback) {
        if (roomsIterator.hasNext()) {
            Room room = (Room) roomsIterator.next();
            boolean isRequestSent = false;

            if (mNetworkConnectivityReceiver.isConnected()) {
                isRequestSent = room.markAllAsRead(new ApiCallback<Void>() {
                    @Override
                    public void onSuccess(Void anything) {
                        markRoomsAsRead(roomsIterator, callback);
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
            } else {
                // update the local data
                room.sendReadReceipt();
            }

            if (!isRequestSent) {
                markRoomsAsRead(roomsIterator, callback);
            }

        } else {
            if (null != callback) {
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onSuccess(null);
                    }
                });
            }
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
    public void lookup3Pids(List<String> addresses, List<String> mediums, ApiCallback<List<String>> callback) {
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
     * @param name      the text to search for.
     * @param rooms     a list of rooms to search in. nil means all rooms the user is in.
     * @param nextBatch the token to pass for doing pagination from a previous response.
     * @param callback  the request callback
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
     * Perform a remote users search by name / user id.
     *
     * @param name          the text to search for.
     * @param limit         the maximum number of items to retrieve (can be null)
     * @param userIdsFilter the user ids filter (can be null)
     * @param callback      the callback
     */
    public void searchUsers(String name, Integer limit, Set<String> userIdsFilter, final ApiCallback<SearchUsersResponse> callback) {
        checkIfAlive();

        if (null != callback) {
            mEventsRestClient.searchUsers(name, limit, userIdsFilter, callback);
        }
    }

    /**
     * Cancel any pending user search
     */
    public void cancelUsersSearch() {
        checkIfAlive();
        mEventsRestClient.cancelUsersSearch();
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

        // sanity check
        if (null == mDataHandler.getStore()) {
            return taggedRooms;
        }

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

        List<String> roomIdsList = new ArrayList<>();

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
            if (0 != directChatRoomIdsListRetValue.size()) {
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
        final String mRoomId;
        final String mParticipantUserId;

        public RoomIdsListRetroCompat(String aParticipantUserId, String aRoomId) {
            this.mParticipantUserId = aParticipantUserId;
            this.mRoomId = aRoomId;
        }
    }

    /**
     * Return the direct chat room list for retro compatibility with 1:1 rooms.
     *
     * @param aStore                         store instance
     * @param aDirectChatRoomIdsListRetValue the other participants in the 1:1 room
     */
    private void getDirectChatRoomIdsListRetroCompat(IMXStore aStore, ArrayList<RoomIdsListRetroCompat> aDirectChatRoomIdsListRetValue) {
        RoomIdsListRetroCompat item;

        if ((null != aStore) && (null != aDirectChatRoomIdsListRetValue)) {
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

                            if (TextUtils.equals(members.get(0).getUserId(), getMyUserId())) {
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
     *
     * @param aSearchedUserId user ID
     * @return the list of the direct chat room Id
     */
    public List<String> getDirectChatRoomIdsList(String aSearchedUserId) {
        ArrayList<String> directChatRoomIdsList = new ArrayList<>();
        IMXStore store = getDataHandler().getStore();
        Room room;

        HashMap<String, List<String>> params;

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

    /**
     * Toggles the direct chat status of a room.<br>
     * Create a new direct chat room in the account data section if the room does not exist,
     * otherwise the room is removed from the account data section.
     * Direct chat room user ID choice algorithm:<br>
     * 1- oldest joined room member
     * 2- oldest invited room member
     * 3- the user himself
     *
     * @param roomId             the room roomId
     * @param aParticipantUserId the participant user id
     * @param callback           the asynchronous callback
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
            if (!getDirectChatRoomIdsList().contains(roomId)) {
                List<String> roomIdsList = new ArrayList<>();
                RoomMember directChatMember = null;
                String chosenUserId;

                if (null == aParticipantUserId) {
                    List<RoomMember> members = new ArrayList<>(room.getActiveMembers());

                    // should never happen but it was reported by a GA issue
                    if (members.isEmpty()) {
                        return;
                    }

                    if (members.size() > 1) {
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

            // update the store value
            // do not wait the server request echo to update the store
            getDataHandler().getStore().setDirectChatRoomsDict(params);

            mAccountDataRestClient.setAccountData(getMyUserId(), AccountDataRestClient.ACCOUNT_DATA_TYPE_DIRECT_MESSAGES, params, callback);
        }
    }

    /**
     * For the value account_data with the rooms list passed in aRoomIdsListToAdd for a given user ID (aParticipantUserId)<br>
     * WARNING: this method must be used with care because it erases the account_data object.
     *
     * @param aRoomParticipantUserIdList the couple direct chat rooms ID / user IDs
     * @param callback                   the asynchronous response callback
     */
    private void forceDirectChatRoomValue(List<RoomIdsListRetroCompat> aRoomParticipantUserIdList, ApiCallback<Void> callback) {
        Map<String, List<String>> params = new HashMap<>();
        List<String> roomIdsList;

        if (null != aRoomParticipantUserIdList) {
            for (RoomIdsListRetroCompat item : aRoomParticipantUserIdList) {
                if (params.containsKey(item.mParticipantUserId)) {
                    roomIdsList = new ArrayList<>(params.get(item.mParticipantUserId));
                    roomIdsList.add(item.mRoomId);
                } else {
                    roomIdsList = new ArrayList<>();
                    roomIdsList.add(item.mRoomId);
                }
                params.put(item.mParticipantUserId, roomIdsList);
            }

            mAccountDataRestClient.setAccountData(getMyUserId(), AccountDataRestClient.ACCOUNT_DATA_TYPE_DIRECT_MESSAGES, params, callback);
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
     *
     * @param userIds  the userIds to ignoer
     * @param callback the callback
     */
    private void updateUsers(ArrayList<String> userIds, ApiCallback<Void> callback) {
        Map<String, Object> ignoredUsersDict = new HashMap<>();

        for (String userId : userIds) {
            ignoredUsersDict.put(userId, new ArrayList<>());
        }

        Map<String, Object> params = new HashMap<>();
        params.put(AccountDataRestClient.ACCOUNT_DATA_KEY_IGNORED_USERS, ignoredUsersDict);

        mAccountDataRestClient.setAccountData(getMyUserId(), AccountDataRestClient.ACCOUNT_DATA_TYPE_IGNORED_USER_LIST, params, callback);
    }

    /**
     * Tells if an user is in the ignored user ids list
     *
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
     *
     * @param userIds  the user ids list to ignore
     * @param callback the result callback
     */
    public void ignoreUsers(ArrayList<String> userIds, ApiCallback<Void> callback) {
        List<String> curUserIdsToIgnore = getDataHandler().getIgnoredUserIds();
        ArrayList<String> userIdsToIgnore = new ArrayList<>(getDataHandler().getIgnoredUserIds());

        // something to add
        if ((null != userIds) && (userIds.size() > 0)) {
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
     *
     * @param userIds  the user ids list to unignore
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
     *
     * @param context  the application context
     * @param callback the callback success and failure callback
     */
    public void logout(final Context context, final ApiCallback<Void> callback) {
        synchronized (this) {
            if (!mIsAliveSession) {
                Log.e(LOG_TAG, "## logout() was already called");
                return;
            }

            mIsAliveSession = false;
        }

        // Clear crypto data
        // For security and because it will be no more useful as we will get a new device id
        // on the next log in
        enableCrypto(false, null);

        mLoginRestClient.logout(new ApiCallback<JsonObject>() {

            private void clearData() {
                // required else the clear won't be done
                mIsAliveSession = true;

                clear(context, new SimpleApiCallback<Void>() {
                    @Override
                    public void onSuccess(Void info) {
                        if (null != callback) {
                            callback.onSuccess(null);
                        }
                    }
                });
            }

            @Override
            public void onSuccess(JsonObject info) {
                Log.e(LOG_TAG, "## logout() : succeed -> clearing the application data ");
                clearData();
            }

            private void onError(String errorMessage) {
                Log.e(LOG_TAG, "## logout() : failed " + errorMessage);
                clearData();
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

    /**
     * Update the URL preview status by default
     *
     * @param status   the status
     * @param callback
     */
    public void setURLPreviewStatus(final boolean status, final ApiCallback<Void> callback) {
        Map<String, Object> params = new HashMap<>();
        params.put(AccountDataRestClient.ACCOUNT_DATA_KEY_URL_PREVIEW_DISABLE, !status);

        Log.d(LOG_TAG, "## setURLPreviewStatus() : status " + status);
        mAccountDataRestClient.setAccountData(getMyUserId(), AccountDataRestClient.ACCOUNT_DATA_TYPE_PREVIEW_URLS, params, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                Log.d(LOG_TAG, "## setURLPreviewStatus() : succeeds");

                getDataHandler().getStore().setURLPreviewEnabled(status);
                if (null != callback) {
                    callback.onSuccess(null);
                }
            }

            @Override
            public void onNetworkError(Exception e) {
                Log.e(LOG_TAG, "## setURLPreviewStatus() : failed " + e.getMessage());
                callback.onNetworkError(e);
            }

            @Override
            public void onMatrixError(MatrixError e) {
                Log.e(LOG_TAG, "## setURLPreviewStatus() : failed " + e.getMessage());
                callback.onMatrixError(e);
            }

            @Override
            public void onUnexpectedError(Exception e) {
                Log.e(LOG_TAG, "## setURLPreviewStatus() : failed " + e.getMessage());
                callback.onUnexpectedError(e);
            }
        });
    }

    /**
     * Tells if the global URL preview settings is enabled
     *
     * @return true if it is enabled.
     */
    public boolean isURLPreviewEnabled() {
        return getDataHandler().getStore().isURLPreviewEnabled();
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
        if (null != getDataHandler().getStore()) {
            Collection<RoomSummary> summaries = getDataHandler().getStore().getSummaries();

            for (RoomSummary summary : summaries) {
                mDataHandler.decryptEvent(summary.getLatestReceivedEvent(), null);
            }
        }
    }

    /**
     * Check if the crypto engine is properly initialized.
     * Launch it it is was not yet done.
     */
    public void checkCrypto() {
        MXFileCryptoStore fileCryptoStore = new MXFileCryptoStore();
        fileCryptoStore.initWithCredentials(mAppContent, mCredentials);

        if ((fileCryptoStore.hasData() || mEnableCryptoWhenStartingMXSession) && (null == mCrypto)) {
            boolean isStoreLoaded = false;
            try {
                // open the store
                fileCryptoStore.open();
                isStoreLoaded = true;
            } catch (UnsatisfiedLinkError e) {
                Log.e(LOG_TAG, "## checkCrypto() failed " + e.getMessage());
            }

            if (!isStoreLoaded) {
                // load again the olm manager
                // reported by rageshake, it seems that the olm lib is unloaded.
                mOlmManager = new OlmManager();

                try {
                    // open the store
                    fileCryptoStore.open();
                    isStoreLoaded = true;
                } catch (UnsatisfiedLinkError e) {
                    Log.e(LOG_TAG, "## checkCrypto() failed 2 " + e.getMessage());
                }
            }

            if (!isStoreLoaded) {
                Log.e(LOG_TAG, "## checkCrypto() : cannot enable the crypto because of olm lib");
                return;
            }

            mCrypto = new MXCrypto(MXSession.this, fileCryptoStore);
            mDataHandler.setCrypto(mCrypto);
            // the room summaries are not stored with decrypted content
            decryptRoomSummaries();

            Log.d(LOG_TAG, "## checkCrypto() : the crypto engine is ready");
        } else if (mDataHandler.getCrypto() != mCrypto) {
            Log.e(LOG_TAG, "## checkCrypto() : the data handler crypto was not initialized");
            mDataHandler.setCrypto(mCrypto);
        }
    }

    /**
     * Enable / disable the crypto.
     *
     * @param cryptoEnabled true to enable the crypto
     * @param callback      the asynchronous callback called when the action has been done
     */
    public void enableCrypto(boolean cryptoEnabled, final ApiCallback<Void> callback) {
        if (cryptoEnabled != isCryptoEnabled()) {
            if (cryptoEnabled) {
                Log.d(LOG_TAG, "Crypto is enabled");
                MXFileCryptoStore fileCryptoStore = new MXFileCryptoStore();
                fileCryptoStore.initWithCredentials(mAppContent, mCredentials);
                fileCryptoStore.open();
                mCrypto = new MXCrypto(this, fileCryptoStore);
                mCrypto.start(true, new ApiCallback<Void>() {
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
     *
     * @param callback the asynchronous callback
     */
    public void getDevicesList(ApiCallback<DevicesListResponse> callback) {
        mCryptoRestClient.getDevices(callback);
    }

    /**
     * Set a device name.
     *
     * @param deviceId   the device id
     * @param deviceName the device name
     * @param callback   the asynchronous callback
     */
    public void setDeviceName(final String deviceId, final String deviceName, final ApiCallback<Void> callback) {
        mCryptoRestClient.setDeviceName(deviceId, deviceName, callback);
    }

    /**
     * Delete a device
     *
     * @param deviceId the device id
     * @param password the passwoerd
     * @param callback the asynchronous callback.
     */
    public void deleteDevice(final String deviceId, final String password, final ApiCallback<Void> callback) {
        mCryptoRestClient.deleteDevice(deviceId, new DeleteDeviceParams(), new ApiCallback<Void>() {
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
                Log.d(LOG_TAG, "## deleteDevice() : onMatrixError " + matrixError.getMessage());
                RegistrationFlowResponse registrationFlowResponse = null;

                // expected status code is 401
                if ((null != matrixError.mStatus) && (matrixError.mStatus == 401)) {
                    try {
                        registrationFlowResponse = JsonUtils.toRegistrationFlowResponse(matrixError.mErrorBodyAsString);
                    } catch (Exception castExcept) {
                        Log.e(LOG_TAG, "## deleteDevice(): Received status 401 - Exception - JsonUtils.toRegistrationFlowResponse()");
                    }
                } else {
                    Log.d(LOG_TAG, "## deleteDevice(): Received not expected status 401 =" + matrixError.mStatus);
                }

                List<String> stages = new ArrayList<>();

                // check if the server response can be casted
                if ((null != registrationFlowResponse)
                        && (null != registrationFlowResponse.flows)
                        && !registrationFlowResponse.flows.isEmpty()) {
                    for (LoginFlow flow : registrationFlowResponse.flows) {
                        if (null != flow.stages) {
                            stages.addAll(flow.stages);
                        }
                    }
                }

                if (!stages.isEmpty()) {
                    DeleteDeviceParams params = new DeleteDeviceParams();
                    params.auth = new DeleteDeviceAuth();
                    params.auth.session = registrationFlowResponse.session;
                    params.auth.user = mCredentials.userId;
                    params.auth.password = password;

                    Log.d(LOG_TAG, "## deleteDevice() : supported stages " + stages);

                    deleteDevice(deviceId, params, stages, callback);
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

    /**
     * Delete a device.
     *
     * @param deviceId the device id.
     * @param params   the delete device params
     * @param stages   the supported stages
     * @param callback the asynchronous callback
     */
    private void deleteDevice(final String deviceId, final DeleteDeviceParams params, final List<String> stages, final ApiCallback<Void> callback) {
        // test the first one
        params.auth.type = stages.get(0);
        stages.remove(0);

        mCryptoRestClient.deleteDevice(deviceId, params, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
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
                boolean has401Error = (null != matrixError.mStatus) && (matrixError.mStatus == 401);

                // failed, try next flow type
                if ((has401Error || TextUtils.equals(matrixError.errcode, MatrixError.FORBIDDEN) || TextUtils.equals(matrixError.errcode, MatrixError.UNKNOWN))
                        && !stages.isEmpty()) {
                    deleteDevice(deviceId, params, stages, callback);
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

    /**
     * Tells if a string is a valid user Id.
     *
     * @param anUserId the string to test
     * @return true if the string is a valid user id
     */
    public static boolean isUserId(String anUserId) {
        return (null != anUserId) && PATTERN_CONTAIN_MATRIX_USER_IDENTIFIER.matcher(anUserId).matches();
    }

    /**
     * Tells if a string is a valid room id.
     *
     * @param aRoomId the string to test
     * @return true if the string is a valid room Id
     */
    public static boolean isRoomId(String aRoomId) {
        return (null != aRoomId) && PATTERN_CONTAIN_MATRIX_ROOM_IDENTIFIER.matcher(aRoomId).matches();
    }

    /**
     * Tells if a string is a valid room alias.
     *
     * @param aRoomAlias the string to test
     * @return true if the string is a valid room alias.
     */
    public static boolean isRoomAlias(String aRoomAlias) {
        return (null != aRoomAlias) && PATTERN_CONTAIN_MATRIX_ALIAS.matcher(aRoomAlias).matches();
    }

    /**
     * Tells if a string is a valid message id.
     *
     * @param aMessageId the string to test
     * @return true if the string is a valid message id.
     */
    public static boolean isMessageId(String aMessageId) {
        return (null != aMessageId) && PATTERN_CONTAIN_MATRIX_MESSAGE_IDENTIFIER.matcher(aMessageId).matches();
    }

    /**
     * Tells if a string is a valid group id.
     *
     * @param aGroupId the string to test
     * @return true if the string is a valid message id.
     */
    public static boolean isGroupId(String aGroupId) {
        return (null != aGroupId) && PATTERN_CONTAIN_MATRIX_GROUP_IDENTIFIER.matcher(aGroupId).matches();
    }

    /**
     * Gets a bearer token from the homeserver that the user can
     * present to a third party in order to prove their ownership
     * of the Matrix account they are logged into.
     *
     * @param callback the asynchronous callback called when finished
     */
    public void openIdToken(final ApiCallback<Map<Object, Object>> callback) {
        mAccountDataRestClient.openIdToken(getMyUserId(), callback);
    }

    /**
     * @return the groups manager
     */
    public GroupsManager getGroupsManager() {
        return mGroupsManager;
    }
}
