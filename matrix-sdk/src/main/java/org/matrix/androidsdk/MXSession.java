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
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import org.jetbrains.annotations.NotNull;
import org.matrix.androidsdk.call.MXCallsManager;
import org.matrix.androidsdk.core.BingRulesManager;
import org.matrix.androidsdk.core.ContentManager;
import org.matrix.androidsdk.core.FileContentUtils;
import org.matrix.androidsdk.core.FilterUtil;
import org.matrix.androidsdk.core.JsonUtils;
import org.matrix.androidsdk.core.Log;
import org.matrix.androidsdk.core.PolymorphicRequestBodyConverter;
import org.matrix.androidsdk.core.UnsentEventsManager;
import org.matrix.androidsdk.core.VersionsUtilKt;
import org.matrix.androidsdk.core.callback.ApiCallback;
import org.matrix.androidsdk.core.callback.ApiFailureCallback;
import org.matrix.androidsdk.core.callback.SimpleApiCallback;
import org.matrix.androidsdk.core.model.MatrixError;
import org.matrix.androidsdk.crypto.MXCrypto;
import org.matrix.androidsdk.crypto.MXCryptoConfig;
import org.matrix.androidsdk.crypto.cryptostore.IMXCryptoStore;
import org.matrix.androidsdk.crypto.cryptostore.MXFileCryptoStore;
import org.matrix.androidsdk.crypto.cryptostore.db.RealmCryptoStore;
import org.matrix.androidsdk.crypto.interfaces.CryptoSession;
import org.matrix.androidsdk.crypto.internal.MXCryptoImpl;
import org.matrix.androidsdk.crypto.model.rest.DeleteDeviceAuth;
import org.matrix.androidsdk.crypto.model.rest.DeleteDeviceParams;
import org.matrix.androidsdk.crypto.model.rest.DevicesListResponse;
import org.matrix.androidsdk.crypto.rest.CryptoRestClient;
import org.matrix.androidsdk.crypto.rest.RoomKeysRestClient;
import org.matrix.androidsdk.data.DataRetriever;
import org.matrix.androidsdk.data.MyUser;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomSummary;
import org.matrix.androidsdk.data.RoomTag;
import org.matrix.androidsdk.data.comparator.RoomComparatorWithTag;
import org.matrix.androidsdk.data.metrics.MetricsListener;
import org.matrix.androidsdk.data.store.IMXStore;
import org.matrix.androidsdk.data.store.MXStoreListener;
import org.matrix.androidsdk.db.MXLatestChatMessageCache;
import org.matrix.androidsdk.db.MXMediaCache;
import org.matrix.androidsdk.features.identityserver.IdentityServerManager;
import org.matrix.androidsdk.features.identityserver.IdentityServerNotConfiguredException;
import org.matrix.androidsdk.features.integrationmanager.IntegrationManager;
import org.matrix.androidsdk.features.terms.TermsManager;
import org.matrix.androidsdk.groups.GroupsManager;
import org.matrix.androidsdk.network.NetworkConnectivityReceiver;
import org.matrix.androidsdk.rest.client.AccountDataRestClient;
import org.matrix.androidsdk.rest.client.CallRestClient;
import org.matrix.androidsdk.rest.client.EventsRestClient;
import org.matrix.androidsdk.rest.client.FilterRestClient;
import org.matrix.androidsdk.rest.client.GroupsRestClient;
import org.matrix.androidsdk.rest.client.LoginRestClient;
import org.matrix.androidsdk.rest.client.MediaScanRestClient;
import org.matrix.androidsdk.rest.client.OpenIdRestClient;
import org.matrix.androidsdk.rest.client.PresenceRestClient;
import org.matrix.androidsdk.rest.client.ProfileRestClient;
import org.matrix.androidsdk.rest.client.PushRulesRestClient;
import org.matrix.androidsdk.rest.client.PushersRestClient;
import org.matrix.androidsdk.rest.client.RoomsRestClient;
import org.matrix.androidsdk.rest.model.CreateRoomParams;
import org.matrix.androidsdk.rest.model.CreateRoomResponse;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.ReceiptData;
import org.matrix.androidsdk.rest.model.RoomDirectoryVisibility;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.rest.model.Versions;
import org.matrix.androidsdk.rest.model.bingrules.BingRule;
import org.matrix.androidsdk.rest.model.filter.FilterBody;
import org.matrix.androidsdk.rest.model.filter.FilterResponse;
import org.matrix.androidsdk.rest.model.filter.RoomEventFilter;
import org.matrix.androidsdk.rest.model.login.Credentials;
import org.matrix.androidsdk.rest.model.login.LoginFlow;
import org.matrix.androidsdk.rest.model.login.RegistrationFlowResponse;
import org.matrix.androidsdk.rest.model.login.ThreePidCredentials;
import org.matrix.androidsdk.rest.model.login.TokenRefreshResponse;
import org.matrix.androidsdk.rest.model.message.MediaMessage;
import org.matrix.androidsdk.rest.model.message.Message;
import org.matrix.androidsdk.rest.model.openid.RequestOpenIdTokenResponse;
import org.matrix.androidsdk.rest.model.pid.Invite3Pid;
import org.matrix.androidsdk.rest.model.search.SearchResponse;
import org.matrix.androidsdk.rest.model.search.SearchUsersResponse;
import org.matrix.androidsdk.rest.model.sync.AccountDataElement;
import org.matrix.androidsdk.rest.model.sync.RoomResponse;
import org.matrix.androidsdk.sync.DefaultEventsThreadListener;
import org.matrix.androidsdk.sync.EventsThread;
import org.matrix.androidsdk.sync.EventsThreadListener;
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

import kotlin.Pair;

/**
 * Class that represents one user's session with a particular home server.
 * There can potentially be multiple sessions for handling multiple accounts.
 */
public class MXSession implements CryptoSession {
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
    private final PushRulesRestClient mPushRulesRestClient;
    private PushersRestClient mPushersRestClient;
    private final CallRestClient mCallRestClient;
    private final AccountDataRestClient mAccountDataRestClient;
    private final OpenIdRestClient mOpenIdRestClient;
    private final LoginRestClient mLoginRestClient;
    private final GroupsRestClient mGroupsRestClient;
    private final MediaScanRestClient mMediaScanRestClient;
    private final FilterRestClient mFilterRestClient;

    private ApiFailureCallback mFailureCallback;

    private ContentManager mContentManager;

    public MXCallsManager mCallsManager;

    private MetricsListener mMetricsListener;

    private Context mContext;
    private NetworkConnectivityReceiver mNetworkConnectivityReceiver;
    private UnsentEventsManager mUnsentEventsManager;

    private MXLatestChatMessageCache mLatestChatMessageCache;
    private MXMediaCache mMediaCache;

    private BingRulesManager mBingRulesManager = null;

    private TermsManager termsManager;

    private IdentityServerManager mIdentityServerManager;

    private IntegrationManager mIntegrationManager;

    private boolean mIsAliveSession = true;

    // online status
    private boolean mIsOnline = false;
    private int mSyncTimeout = 0;
    private int mSyncDelay = 0;

    private final HomeServerConnectionConfig mHsConfig;

    // the application is launched from a notification
    // so, mEventsThread.start might be not ready
    private boolean mIsBgCatchupPending = false;

    private FilterBody mCurrentFilter = new FilterBody();

    // tell if the data save mode is enabled
    private boolean mUseDataSaveMode;

    // the groups manager
    private GroupsManager mGroupsManager;

    // load the crypto libs.
    public static OlmManager mOlmManager = new OlmManager();

    private IMXCryptoStore mCryptoStore;

    /**
     * Create a basic session for direct API calls.
     *
     * @param hsConfig      the home server connection config
     * @param pushServerUrl specific pushServerUrl
     */
    private MXSession(HomeServerConnectionConfig hsConfig,
                      @Nullable String pushServerUrl) {
        mCredentials = hsConfig.getCredentials();
        mHsConfig = hsConfig;

        mEventsRestClient = new EventsRestClient(hsConfig);
        mProfileRestClient = new ProfileRestClient(hsConfig);
        mPresenceRestClient = new PresenceRestClient(hsConfig);
        mRoomsRestClient = new RoomsRestClient(hsConfig);
        mPushRulesRestClient = new PushRulesRestClient(hsConfig);

        // If not empty, create a special PushersRestClient
        if (!TextUtils.isEmpty(pushServerUrl)) {
            // pusher uses a custom server
            try {
                HomeServerConnectionConfig alteredHsConfig = new HomeServerConnectionConfig.Builder(hsConfig)
                        .withHomeServerUri(Uri.parse(pushServerUrl))
                        .build();
                mPushersRestClient = new PushersRestClient(alteredHsConfig);
            } catch (Exception e) {
                Log.e(LOG_TAG, "## withPushServerUrl() failed " + e.getMessage(), e);
            }
        }

        if (null == mPushersRestClient) {
            // In case of error, or if pushServerUrl is not defined, use default config
            mPushersRestClient = new PushersRestClient(hsConfig);
        }

        mCallRestClient = new CallRestClient(hsConfig);
        mAccountDataRestClient = new AccountDataRestClient(hsConfig);
        mOpenIdRestClient = new OpenIdRestClient(hsConfig);
        mLoginRestClient = new LoginRestClient(hsConfig);
        mGroupsRestClient = new GroupsRestClient(hsConfig);
        mMediaScanRestClient = new MediaScanRestClient(hsConfig);
        mFilterRestClient = new FilterRestClient(hsConfig);
    }

    /**
     * Create a user session with a data handler.
     * Private, please use the MxSession.Builder now
     *
     * @param hsConfig                  the home server connection config
     * @param dataHandler               the data handler
     * @param appContext                the application context
     * @param pushServerUrl             specific pushServerUrl
     * @param withFileEncryptionEnabled true to encrypt the crypto store
     * @param withLegacyCryptoStore     true to use the legacy file crypto store (for test only)
     * @param metricsListener           the metrics listener
     */
    private MXSession(HomeServerConnectionConfig hsConfig,
                      MXDataHandler dataHandler,
                      Context appContext,
                      @Nullable String pushServerUrl,
                      boolean withFileEncryptionEnabled,
                      boolean withLegacyCryptoStore,
                      @Nullable MetricsListener metricsListener) {
        this(hsConfig, pushServerUrl);
        mDataHandler = dataHandler;

        // application context
        mContext = appContext;

        mMetricsListener = metricsListener;

        // Init the crypto store
        mCryptoStore = withLegacyCryptoStore ? new MXFileCryptoStore(withFileEncryptionEnabled) : new RealmCryptoStore(withFileEncryptionEnabled);
        mCryptoStore.initWithCredentials(mContext, mCredentials);

        mDataHandler.getStore().addMXStoreListener(new MXStoreListener() {
            @Override
            public void onStoreReady(String accountId) {
                Log.d(LOG_TAG, "## onStoreReady()");
                getDataHandler().onStoreReady();
            }

            @Override
            public void onStoreCorrupted(String accountId, String description) {
                //NPE reported on store
                if (getDataHandler().getStore() != null) {
                    Log.d(LOG_TAG, "## onStoreCorrupted() : token " + getDataHandler().getStore().getEventStreamToken());

                    // nothing was saved
                    if (null == getDataHandler().getStore().getEventStreamToken()) {
                        getDataHandler().onStoreReady();
                    }
                } else {
                    Log.d(LOG_TAG, "## onStoreCorrupted() : no store access ");
                }
            }

            @Override
            public void postProcess(String accountId) {
                getDataHandler().checkPermanentStorageData();

                // test if the crypto instance has already been created
                if (null == mCrypto) {
                    if (mCryptoStore.hasData() || mEnableCryptoWhenStartingMXSession) {
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
                final List<String> senders = new ArrayList<>();

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
        mDataHandler.setRoomsRestClient(mRoomsRestClient);
        mDataHandler.setEventsRestClient(mEventsRestClient);
        mDataHandler.setAccountDataRestClient(mAccountDataRestClient);

        mNetworkConnectivityReceiver = new NetworkConnectivityReceiver();
        mNetworkConnectivityReceiver.checkNetworkConnection(appContext);
        mDataHandler.setNetworkConnectivityReceiver(mNetworkConnectivityReceiver);
        mContext.registerReceiver(mNetworkConnectivityReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));

        mBingRulesManager = new BingRulesManager(this, mNetworkConnectivityReceiver);
        mDataHandler.setPushRulesManager(mBingRulesManager);

        mUnsentEventsManager = new UnsentEventsManager(mNetworkConnectivityReceiver, mDataHandler);

        mContentManager = new ContentManager(hsConfig, mUnsentEventsManager);

        //
        mCallsManager = new MXCallsManager(this, mContext);
        mDataHandler.setCallsManager(mCallsManager);

        // the rest client
        mEventsRestClient.setUnsentEventsManager(mUnsentEventsManager);

        mProfileRestClient.setUnsentEventsManager(mUnsentEventsManager);
        mPresenceRestClient.setUnsentEventsManager(mUnsentEventsManager);
        mRoomsRestClient.setUnsentEventsManager(mUnsentEventsManager);
        mPushRulesRestClient.setUnsentEventsManager(mUnsentEventsManager);
        mCallRestClient.setUnsentEventsManager(mUnsentEventsManager);
        mAccountDataRestClient.setUnsentEventsManager(mUnsentEventsManager);
        mLoginRestClient.setUnsentEventsManager(mUnsentEventsManager);
        mGroupsRestClient.setUnsentEventsManager(mUnsentEventsManager);

        // return the default cache manager
        mLatestChatMessageCache = new MXLatestChatMessageCache(mCredentials.userId);
        mMediaCache = new MXMediaCache(mContentManager, mNetworkConnectivityReceiver, mCredentials.userId, appContext);
        mDataHandler.setMediaCache(mMediaCache);

        mMediaScanRestClient.setMxStore(mDataHandler.getStore());
        mMediaCache.setMediaScanRestClient(mMediaScanRestClient);

        mGroupsManager = new GroupsManager(mDataHandler, mGroupsRestClient);
        mDataHandler.setGroupsManager(mGroupsManager);

        termsManager = new TermsManager(this);

        mIdentityServerManager = new IdentityServerManager(this, appContext);

        mIntegrationManager = new IntegrationManager(this, appContext);
    }

    private void checkIfAlive() {
        synchronized (this) {
            if (!mIsAliveSession) {
                // Create an Exception to log the stack trace
                Log.e(LOG_TAG, "Use of a released session", new Exception("Use of a released session"));

                //throw new AssertionError("Should not used a cleared mxsession ");
            }
        }
    }

    /**
     * Init the user-agent used by the REST requests.
     *
     * @param appContext        the application context
     * @param flavorDescription the flavor description, or null if not defined
     */
    public static void initUserAgent(@Nullable Context appContext,
                                     @Nullable String flavorDescription) {
        RestClient.initUserAgent(appContext, BuildConfig.VERSION_NAME, flavorDescription == null ? "SDKApp" : flavorDescription);
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
            String gitVersion = mContext.getString(R.string.git_sdk_revision);

            if (longFormat) {
                String date = mContext.getString(R.string.git_sdk_revision_date);
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
    @Override
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

    public FilterRestClient getFilterRestClient() {
        checkIfAlive();
        return mFilterRestClient;
    }

    /**
     * Get the API client for requests to the Room Keys API.
     *
     * @return the Room Keys API client
     */
    public RoomKeysRestClient getRoomKeysRestClient() {
        checkIfAlive();
        return mCrypto.getKeysBackup().getRoomKeysRestClient();
    }

    /**
     * Get the TermsManager.
     *
     * @return the TermsManager
     */
    public TermsManager getTermsManager() {
        checkIfAlive();
        return termsManager;
    }

    /**
     * Refresh the presence info of a dedicated user.
     *
     * @param userId   the user userID.
     * @param callback the callback.
     */
    public void refreshUserPresence(final String userId, final ApiCallback<Void> callback) {
        mPresenceRestClient.getPresence(userId, new SimpleApiCallback<User>(callback) {
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
        });
    }

    /**
     * Get the API client for requests to the bing rules API.
     *
     * @return the bing rules API client
     */
    public PushRulesRestClient getBingRulesApiClient() {
        checkIfAlive();
        return mPushRulesRestClient;
    }

    public IdentityServerManager getIdentityServerManager() {
        checkIfAlive();
        return mIdentityServerManager;
    }

    public IntegrationManager getIntegrationManager() {
        checkIfAlive();
        return mIntegrationManager;
    }

    public CallRestClient getCallRestClient() {
        checkIfAlive();
        return mCallRestClient;
    }

    public PushersRestClient getPushersRestClient() {
        checkIfAlive();
        return mPushersRestClient;
    }

    // TODO Check if it is used by clients of the SDK
    public CryptoRestClient getCryptoRestClient() {
        checkIfAlive();
        return mCrypto.getCryptoRestClient();
    }

    @VisibleForTesting
    public CryptoRestClient getCryptoRestClientForTest() {
        checkIfAlive();
        if (mCrypto != null) {
            return mCrypto.getCryptoRestClient();
        } else {
            // Create a client, for test only
            return new CryptoRestClient(mHsConfig);
        }
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

    public MediaScanRestClient getMediaScanRestClient() {
        checkIfAlive();
        return mMediaScanRestClient;
    }

    public AccountDataRestClient getAccountDataRestClient() {
        checkIfAlive();
        return mAccountDataRestClient;
    }

    protected void setEventsApiClient(EventsRestClient eventsRestClient) {
        checkIfAlive();
        mEventsRestClient = eventsRestClient;
    }

    protected void setProfileApiClient(ProfileRestClient profileRestClient) {
        checkIfAlive();
        mProfileRestClient = profileRestClient;
    }

    protected void setPresenceApiClient(PresenceRestClient presenceRestClient) {
        checkIfAlive();
        mPresenceRestClient = presenceRestClient;
    }

    protected void setRoomsApiClient(RoomsRestClient roomsRestClient) {
        checkIfAlive();
        mRoomsRestClient = roomsRestClient;
    }

    public MXLatestChatMessageCache getLatestChatMessageCache() {
        checkIfAlive();
        return mLatestChatMessageCache;
    }

    public MXMediaCache getMediaCache() {
        checkIfAlive();
        return mMediaCache;
    }

    /**
     * Provides the application caches size.
     *
     * @param context  the context
     * @param callback the asynchronous callback
     */
    public static void getApplicationSizeCaches(final Context context, final ApiCallback<Long> callback) {
        AsyncTask<Void, Void, Long> task = new AsyncTask<Void, Void, Long>() {
            @Override
            protected Long doInBackground(Void... params) {
                return FileContentUtils.getDirectorySize(context, context.getApplicationContext().getFilesDir().getParentFile(), 5);
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
            Log.e(LOG_TAG, "## getApplicationSizeCaches() : failed " + e.getMessage(), e);
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
            mContext.unregisterReceiver(mNetworkConnectivityReceiver);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## clearApplicationCaches() : unregisterReceiver failed " + e.getMessage(), e);
        }
        mNetworkConnectivityReceiver.removeListeners();

        // auto resent messages will not be resent
        mUnsentEventsManager.clear();

        mLatestChatMessageCache.clearCache(context);
        mMediaCache.clear();

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
                Log.e(LOG_TAG, "## clear() failed " + e.getMessage(), e);
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
     * Remove the media older than the provided timestamp.
     *
     * @param context   the context
     * @param timestamp the timestamp (in seconds)
     */
    public void removeMediaBefore(final Context context, final long timestamp) {
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
                        Message message = null;

                        if (TextUtils.equals(Event.EVENT_TYPE_MESSAGE, event.getType())) {
                            message = JsonUtils.toMessage(event.getContent());
                        } else if (TextUtils.equals(Event.EVENT_TYPE_STICKER, event.getType())) {
                            message = JsonUtils.toStickerMessage(event.getContent());
                        }

                        if (null != message && message instanceof MediaMessage) {
                            MediaMessage mediaMessage = (MediaMessage) message;

                            if (mediaMessage.isThumbnailLocalContent()) {
                                filesToKeep.add(Uri.parse(mediaMessage.getThumbnailUrl()).getPath());
                            }

                            if (mediaMessage.isLocalContent()) {
                                filesToKeep.add(Uri.parse(mediaMessage.getUrl()).getPath());
                            }
                        }
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "## removeMediaBefore() : failed " + e.getMessage(), e);
                    }
                }
            }
        }

        AsyncTask<Void, Void, Void> task = new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                long length = getMediaCache().removeMediaBefore(timestamp, filesToKeep);

                // delete also the log files
                // they might be large
                File logsDir = Log.getLogDirectory();

                if (null != logsDir) {
                    File[] logFiles = logsDir.listFiles();

                    if (null != logFiles) {
                        for (File file : logFiles) {
                            if (FileContentUtils.getLastAccessTime(file) < timestamp) {
                                length += file.length();
                                file.delete();
                            }
                        }
                    }
                }

                if (0 != length) {
                    Log.d(LOG_TAG, "## removeMediaBefore() : save " + android.text.format.Formatter.formatFileSize(context, length));
                } else {
                    Log.d(LOG_TAG, "## removeMediaBefore() : useless");
                }

                return null;
            }
        };
        try {
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## removeMediaBefore() : failed " + e.getMessage(), e);
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
    public void startEventStream(final EventsThreadListener anEventsListener,
                                 final NetworkConnectivityReceiver networkConnectivityReceiver,
                                 final String initialToken) {
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

            mEventsThread = new EventsThread(mContext, mEventsRestClient, fEventsListener, initialToken);
            setSyncFilter(mCurrentFilter);
            mEventsThread.setMetricsListener(mMetricsListener);
            mEventsThread.setNetworkConnectivityReceiver(networkConnectivityReceiver);
            mEventsThread.setIsOnline(mIsOnline);
            mEventsThread.setServerLongPollTimeout(mSyncTimeout);
            mEventsThread.setSyncDelay(mSyncDelay);

            if (mFailureCallback != null) {
                mEventsThread.setFailureCallback(mFailureCallback);
            }

            if (mCredentials.accessToken != null && !mEventsThread.isAlive()) {
                // GA issue
                try {
                    mEventsThread.start();
                } catch (Exception e) {
                    Log.e(LOG_TAG, "## startEventStream() :  mEventsThread.start failed " + e.getMessage(), e);
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

        mProfileRestClient.refreshTokens(getCredentials().refreshToken, new ApiCallback<TokenRefreshResponse>() {
            @Override
            public void onSuccess(TokenRefreshResponse info) {
                Log.d(LOG_TAG, "refreshToken : succeeds.");
            }

            @Override
            public void onNetworkError(Exception e) {
                Log.e(LOG_TAG, "refreshToken : onNetworkError " + e.getMessage(), e);
            }

            @Override
            public void onMatrixError(MatrixError e) {
                Log.e(LOG_TAG, "refreshToken : onMatrixError " + e.getMessage());
            }

            @Override
            public void onUnexpectedError(Exception e) {
                Log.e(LOG_TAG, "refreshToken : onMatrixError " + e.getMessage(), e);
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
     * Update the data save mode.
     *
     * @param enabled true to enable the data save mode
     */
    public void setUseDataSaveMode(boolean enabled) {
        mUseDataSaveMode = enabled;

        if (mEventsThread != null) {
            setSyncFilter(mCurrentFilter);
        }
    }

    /**
     * Allows setting the filter used by the EventsThread
     *
     * @param filter the content of the filter param on sync requests
     */
    public synchronized void setSyncFilter(FilterBody filter) {
        Log.d(LOG_TAG, "setSyncFilter ## " + filter);
        mCurrentFilter = filter;

        // Enable Data save mode and/or LazyLoading
        FilterUtil.enableDataSaveMode(mCurrentFilter, mUseDataSaveMode);
        FilterUtil.enableLazyLoading(mCurrentFilter, mDataHandler.isLazyLoadingEnabled());

        convertFilterToFilterId();
    }

    /**
     * Allows setting the filter used for the pagination
     * The lazyLoading attribute will be overidden by the Matrix SDK, you do not have to take care of it
     *
     * @param filter the content of the filter param on pagination requests. Null to reset the filter.
     */
    public void setPaginationFilter(@Nullable RoomEventFilter filter) {
        Log.d(LOG_TAG, "setPaginationFilter ## " + filter);
        mDataHandler.setPaginationFilter(filter);
    }

    /**
     * Convert a filter to a filterId
     * Either it is already known to the server, or send the filter to the server to get a filterId
     */
    private void convertFilterToFilterId() {
        // Ensure mCurrentFilter has not been updated in the same time
        final String wantedJsonFilter = mCurrentFilter.toJSONString();

        // Check if the current filter is known by the server, to directly use the filterId
        String filterId = getDataHandler().getStore().getFilters().get(wantedJsonFilter);

        if (TextUtils.isEmpty(filterId)) {
            // enable the filter in JSON representation so do not block sync until the filter response is there
            mEventsThread.setFilterOrFilterId(wantedJsonFilter);

            // Send the filter to the server
            mFilterRestClient.uploadFilter(getMyUserId(), mCurrentFilter, new SimpleApiCallback<FilterResponse>() {
                @Override
                public void onSuccess(FilterResponse filter) {
                    // Store the couple filter/filterId
                    getDataHandler().getStore().addFilter(wantedJsonFilter, filter.filterId);

                    // Ensure the filter is still corresponding to the current filter
                    if (TextUtils.equals(wantedJsonFilter, mCurrentFilter.toJSONString())) {
                        // Tell the event thread to use the id now
                        mEventsThread.setFilterOrFilterId(filter.filterId);
                    }
                }
            });
        } else {
            // Tell the event thread to use the id now
            mEventsThread.setFilterOrFilterId(filterId);
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
            mNetworkConnectivityReceiver.checkNetworkConnection(mContext);
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
        startEventStream(null, mNetworkConnectivityReceiver, initialToken);
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

        if (null != getMediaCache()) {
            getMediaCache().clearTmpDecryptedMediaCache();
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
            mNetworkConnectivityReceiver.checkNetworkConnection(mContext);
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

        if (null != getMediaCache()) {
            getMediaCache().clearShareDecryptedMediaCache();
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
        createRoom(name, topic, RoomDirectoryVisibility.DIRECTORY_VISIBILITY_PRIVATE, alias, null, callback);
    }

    /**
     * Create a new room with given properties. Needs the data handler.
     *
     * @param name       the room name
     * @param topic      the room topic
     * @param visibility the room visibility
     * @param alias      the room alias
     * @param algorithm  the crypto algorithm (null to create an unencrypted room)
     * @param callback   the async callback once the room is ready
     */
    public void createRoom(String name,
                           String topic,
                           String visibility,
                           String alias,
                           String algorithm,
                           final ApiCallback<String> callback) {
        checkIfAlive();

        CreateRoomParams params = new CreateRoomParams();
        params.name = !TextUtils.isEmpty(name) ? name : null;
        params.topic = !TextUtils.isEmpty(topic) ? topic : null;
        params.visibility = !TextUtils.isEmpty(visibility) ? visibility : null;
        params.roomAliasName = !TextUtils.isEmpty(alias) ? alias : null;
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
            CreateRoomParams params = new CreateRoomParams();

            params.addCryptoAlgorithm(algorithm);
            params.setDirectMessage();


            try {
                Pair<List<Invite3Pid>, List<String>> listPair = getIdentityServerManager()
                        .getInvite3pid(mHsConfig.getCredentials().userId,
                                Arrays.asList(aParticipantUserId)
                        );
                params.invitedUserIds = listPair.getSecond();
                params.invite3pids = listPair.getFirst();
                createRoom(params, aCreateRoomCallBack);
                retCode = true;
            } catch (IdentityServerNotConfiguredException e) {
                aCreateRoomCallBack.onUnexpectedError(new IdentityServerNotConfiguredException());
                retCode = false;
            }
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

        toggleDirectChatRoom(roomId, userId, new SimpleApiCallback<Void>(callback) {
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
                if (!createdRoom.isJoined()) {
                    createdRoom.setOnInitialSyncCallback(new SimpleApiCallback<Void>(callback) {
                        @Override
                        public void onSuccess(Void info) {
                            createdRoom.markAllAsRead(null);

                            if (params.isDirect()) {
                                finalizeDMRoomCreation(roomId, params.getFirstInvitedUserId(), callback);
                            } else {
                                callback.onSuccess(roomId);
                            }
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
        joinRoom(roomIdOrAlias, null, callback);
    }

    /**
     * Join a room by its roomAlias
     *
     * @param roomIdOrAlias the room alias
     * @param viaServers    The servers to attempt to join the room through. One of the servers must be participating in the room. Can be null.
     * @param callback      the async callback once the room is joined. The RoomId is provided.
     */
    public void joinRoom(String roomIdOrAlias,
                         @Nullable List<String> viaServers,
                         final ApiCallback<String> callback) {
        checkIfAlive();

        // sanity check
        if ((null != mDataHandler) && (null != roomIdOrAlias)) {
            mDataRetriever.getRoomsRestClient().joinRoom(roomIdOrAlias,
                    viaServers,
                    null,
                    new SimpleApiCallback<RoomResponse>(callback) {
                        @Override
                        public void onSuccess(final RoomResponse roomResponse) {
                            final String roomId = roomResponse.roomId;
                            Room joinedRoom = mDataHandler.getRoom(roomId);

                            // wait until the initial sync is done
                            if (!joinedRoom.isJoined()) {
                                joinedRoom.setOnInitialSyncCallback(new SimpleApiCallback<Void>(callback) {
                                    @Override
                                    public void onSuccess(Void info) {
                                        callback.onSuccess(roomId);
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
                isRequestSent = room.markAllAsRead(new SimpleApiCallback<Void>(callback) {
                    @Override
                    public void onSuccess(Void anything) {
                        markRoomsAsRead(roomsIterator, callback);
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
     * @param addresses 3rd party ids
     * @param mediums   the media.
     * @param callback  the 3rd parties callback
     */
    public void lookup3Pids(List<String> addresses, List<String> mediums, ApiCallback<List<String>> callback) {
        checkIfAlive();

        mIdentityServerManager.lookup3Pids(addresses, mediums, callback);
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
    public void searchMessageText(String text,
                                  List<String> rooms,
                                  int beforeLimit,
                                  int afterLimit,
                                  String nextBatch,
                                  final ApiCallback<SearchResponse> callback) {
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
    public void searchMediaByName(String name, List<String> rooms, String nextBatch, final ApiCallback<SearchResponse> callback) {
        checkIfAlive();

        if (null != callback) {
            mEventsRestClient.searchMediaByText(name, rooms, 0, 0, nextBatch, callback);
        }
    }

    /**
     * Cancel any pending file search request
     */
    public void cancelSearchMediaByText() {
        checkIfAlive();
        mEventsRestClient.cancelSearchMediaByText();
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
        final List<Room> taggedRooms = new ArrayList<>();

        // sanity check
        if (null == mDataHandler.getStore()) {
            return taggedRooms;
        }

        if (!TextUtils.equals(tag, RoomTag.ROOM_TAG_NO_TAG)) {
            final Collection<Room> rooms = mDataHandler.getStore().getRooms();
            for (Room room : rooms) {
                if (null != room.getAccountData().roomTag(tag)) {
                    taggedRooms.add(room);
                }
            }
            if (taggedRooms.size() > 0) {
                Collections.sort(taggedRooms, new RoomComparatorWithTag(tag));
            }
        } else {
            final Collection<Room> rooms = mDataHandler.getStore().getRooms();
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
    public void toggleDirectChatRoom(final String roomId,
                                     @Nullable final String aParticipantUserId,
                                     final ApiCallback<Void> callback) {
        IMXStore store = getDataHandler().getStore();
        Room room = store.getRoom(roomId);

        if (null != room) {
            if (getDataHandler().getDirectChatRoomIdsList().contains(roomId)) {
                // The room is already seen as direct chat
                removeDirectChatRoomFromAccountData(roomId, callback);
            } else {
                // The room was not yet seen as direct chat
                if (null == aParticipantUserId) {
                    searchOtherUserInRoomToCreateDirectChat(room, new SimpleApiCallback<String>(callback) {
                        @Override
                        public void onSuccess(String info) {
                            addDirectChatRoomToAccountData(roomId, info, callback);
                        }
                    });
                } else {
                    addDirectChatRoomToAccountData(roomId, aParticipantUserId, callback);
                }
            }
        } else {
            if (callback != null) {
                callback.onUnexpectedError(new Exception("Unknown room"));
            }
        }
    }

    /**
     * Search another user in the room to create a direct chat
     *
     * @param room     the room to search in
     * @param callback the callback to get the selected user id
     */
    private void searchOtherUserInRoomToCreateDirectChat(@NonNull final Room room,
                                                         @NonNull final ApiCallback<String> callback) {
        room.getActiveMembersAsync(new SimpleApiCallback<List<RoomMember>>(callback) {
            @Override
            public void onSuccess(List<RoomMember> members) {
                // should never happen but it was reported by a GA issue
                if (members.isEmpty()) {
                    callback.onUnexpectedError(new Exception("Error"));

                    return;
                }

                RoomMember directChatMember = null;

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

                callback.onSuccess(directChatMember.getUserId());
            }
        });
    }

    /**
     * Add the room to the direct chat room list in AccountData
     *
     * @param roomId       the room roomId
     * @param chosenUserId userId of the direct chat room
     * @param callback     the asynchronous callback
     */
    private void addDirectChatRoomToAccountData(String roomId,
                                                @NonNull String chosenUserId,
                                                ApiCallback<Void> callback) {
        IMXStore store = getDataHandler().getStore();
        Map<String, List<String>> params;

        if (null != store.getDirectChatRoomsDict()) {
            params = new HashMap<>(store.getDirectChatRoomsDict());
        } else {
            params = new HashMap<>();
        }

        List<String> roomIdsList = new ArrayList<>();

        // search if there is an entry with the same user
        if (params.containsKey(chosenUserId)) {
            roomIdsList = new ArrayList<>(params.get(chosenUserId));
        }

        roomIdsList.add(roomId); // update room list with the new room
        params.put(chosenUserId, roomIdsList);

        // Store and upload the updated map
        getDataHandler().setDirectChatRoomsMap(params, callback);
    }

    /**
     * Remove the room to the direct chat room list in AccountData
     *
     * @param roomId   the room roomId
     * @param callback the asynchronous callback
     */
    private void removeDirectChatRoomFromAccountData(String roomId,
                                                     ApiCallback<Void> callback) {
        IMXStore store = getDataHandler().getStore();

        Map<String, List<String>> params;

        if (null != store.getDirectChatRoomsDict()) {
            params = new HashMap<>(store.getDirectChatRoomsDict());
        } else {
            params = new HashMap<>();
        }

        // remove the current room from the direct chat list rooms
        if (null != store.getDirectChatRoomsDict()) {
            List<String> keysList = new ArrayList<>(params.keySet());

            for (String key : keysList) {
                List<String> roomIdsList = params.get(key);
                if (roomIdsList.contains(roomId)) {
                    roomIdsList.remove(roomId);

                    if (roomIdsList.isEmpty()) {
                        // Remove this entry
                        params.remove(key);
                    }
                }
            }
        } else {
            // should not happen: if the room has to be removed, it means the room has been
            //  previously detected as being part of the listOfList
            Log.e(LOG_TAG, "## removeDirectChatRoomFromAccountData(): failed to remove a direct chat room (not seen as direct chat room)");
            if (callback != null) {
                callback.onUnexpectedError(new Exception("Error"));
            }
            return;
        }

        // Store and upload the updated map
        getDataHandler().setDirectChatRoomsMap(params, callback);
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
     * @param newPassword         the new password
     * @param threePidCredentials the three pids.
     * @param callback            the callback
     */
    public void resetPassword(final String newPassword, final ThreePidCredentials threePidCredentials, final ApiCallback<Void> callback) {
        mProfileRestClient.resetPassword(newPassword, threePidCredentials, callback);
    }

    /**
     * Triggers a request to update the userIds to ignore
     *
     * @param userIds  the userIds to ignore
     * @param callback the callback
     */
    private void updateUsers(List<String> userIds, ApiCallback<Void> callback) {
        Map<String, Object> ignoredUsersDict = new HashMap<>();

        for (String userId : userIds) {
            ignoredUsersDict.put(userId, new HashMap<>());
        }

        Map<String, Object> params = new HashMap<>();
        params.put(AccountDataElement.ACCOUNT_DATA_KEY_IGNORED_USERS, ignoredUsersDict);

        mAccountDataRestClient.setAccountData(getMyUserId(), AccountDataElement.ACCOUNT_DATA_TYPE_IGNORED_USER_LIST, params, callback);
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
    public void ignoreUsers(List<String> userIds, ApiCallback<Void> callback) {
        List<String> curUserIdsToIgnore = getDataHandler().getIgnoredUserIds();
        List<String> userIdsToIgnore = new ArrayList<>(getDataHandler().getIgnoredUserIds());

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
    public void unIgnoreUsers(List<String> userIds, ApiCallback<Void> callback) {
        List<String> curUserIdsToIgnore = getDataHandler().getIgnoredUserIds();
        List<String> userIdsToIgnore = new ArrayList<>(getDataHandler().getIgnoredUserIds());

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

    public void enableIntegrationManagerUsage(Boolean enable, ApiCallback<Void> callback) {
        Map<String, Object> enableImAccountDataEl = new HashMap<>();
        enableImAccountDataEl.put("enabled", enable);

        mAccountDataRestClient.setAccountData(getMyUserId(), AccountDataElement.ACCOUNT_DATA_TYPE_INTEGRATION_PROVISIONING, enableImAccountDataEl, callback);
    }

    /**
     * @return the network receiver.
     */
    public NetworkConnectivityReceiver getNetworkConnectivityReceiver() {
        return mNetworkConnectivityReceiver;
    }


    /**
     * Ask the home server if the lazy loading of room members is supported.
     *
     * @param callback the callback, to be notified if the server actually support the lazy loading. True if supported
     */
    public void canEnableLazyLoading(final ApiCallback<Boolean> callback) {
        // Check that the server support the lazy loading
        mLoginRestClient.getVersions(new SimpleApiCallback<Versions>(callback) {
            @Override
            public void onSuccess(Versions info) {
                // Check if we can enable lazyLoading
                callback.onSuccess(VersionsUtilKt.supportLazyLoadMembers(info));
            }
        });
    }

    /**
     * Ask the home server if an identity server is required.
     *
     * @param callback the callback, to be notified if the server requires an id server
     */
    public void doesServerRequireIdentityServerParam(final ApiCallback<Boolean> callback) {
        mLoginRestClient.doesServerRequireIdentityServerParam(callback);
    }

    /**
     * Ask if the `id_access_token` parameter can be safely passed to the homeserver.
     * Some homeservers may trigger errors if they are not prepared for the new parameter.
     *
     * @param callback the callback.
     */
    public void doesServerAcceptIdentityAccessToken(final ApiCallback<Boolean> callback) {
        mLoginRestClient.doesServerAcceptIdentityAccessToken(callback);
    }

    public void doesServerSeparatesAddAndBind(final ApiCallback<Boolean> callback) {
        mLoginRestClient.doesServerSeparatesAddAndBind(callback);
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

        mLoginRestClient.logout(new ApiCallback<Void>() {

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
            public void onSuccess(Void info) {
                Log.d(LOG_TAG, "## logout() : succeed -> clearing the application data ");
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
     * Deactivate the account.
     *
     * @param context       the application context
     * @param userPassword  current password
     * @param eraseUserData true to also erase all the user data
     * @param callback      the success and failure callback
     */
    public void deactivateAccount(final Context context,
                                  final String userPassword,
                                  final boolean eraseUserData,
                                  final ApiCallback<Void> callback) {
        mProfileRestClient.deactivateAccount(getMyUserId(), userPassword, eraseUserData, new SimpleApiCallback<Void>(callback) {

            @Override
            public void onSuccess(Void info) {
                Log.d(LOG_TAG, "## deactivateAccount() : succeed -> clearing the application data ");

                // Clear crypto data
                // For security and because it will be no more useful as we will get a new device id
                // on the next log in
                enableCrypto(false, null);

                clear(context, new SimpleApiCallback<Void>(callback) {
                    @Override
                    public void onSuccess(Void info) {
                        if (null != callback) {
                            callback.onSuccess(null);
                        }
                    }
                });
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
        params.put(AccountDataElement.ACCOUNT_DATA_KEY_URL_PREVIEW_DISABLE, !status);

        Log.d(LOG_TAG, "## setURLPreviewStatus() : status " + status);
        mAccountDataRestClient.setAccountData(getMyUserId(), AccountDataElement.ACCOUNT_DATA_TYPE_PREVIEW_URLS, params, new ApiCallback<Void>() {
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
                Log.e(LOG_TAG, "## setURLPreviewStatus() : failed " + e.getMessage(), e);
                callback.onNetworkError(e);
            }

            @Override
            public void onMatrixError(MatrixError e) {
                Log.e(LOG_TAG, "## setURLPreviewStatus() : failed " + e.getMessage());
                callback.onMatrixError(e);
            }

            @Override
            public void onUnexpectedError(Exception e) {
                Log.e(LOG_TAG, "## setURLPreviewStatus() : failed " + e.getMessage(), e);
                callback.onUnexpectedError(e);
            }
        });
    }

    /**
     * Add user widget to the user Account Data
     *
     * @param params
     * @param callback
     */
    public void addUserWidget(final Map<String, Object> params, final ApiCallback<Void> callback) {
        Log.d(LOG_TAG, "## addUserWidget()");

        mAccountDataRestClient.setAccountData(getMyUserId(), AccountDataElement.ACCOUNT_DATA_TYPE_WIDGETS, params, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                Log.d(LOG_TAG, "## addUserWidget() : succeeds");

                getDataHandler().getStore().setUserWidgets(params);
                if (null != callback) {
                    callback.onSuccess(null);
                }
            }

            @Override
            public void onNetworkError(Exception e) {
                Log.e(LOG_TAG, "## addUserWidget() : failed " + e.getMessage(), e);
                callback.onNetworkError(e);
            }

            @Override
            public void onMatrixError(MatrixError e) {
                Log.e(LOG_TAG, "## addUserWidget() : failed " + e.getMessage());
                callback.onMatrixError(e);
            }

            @Override
            public void onUnexpectedError(Exception e) {
                Log.e(LOG_TAG, "## addUserWidget() : failed " + e.getMessage(), e);
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

    /**
     * Get user widget from user AccountData
     *
     * @return
     */
    public Map<String, Object> getUserWidgets() {
        return getDataHandler().getStore().getUserWidgets();
    }

    //==============================================================================================================
    // Crypto
    //==============================================================================================================

    /**
     * The module that manages E2E encryption.
     * Null if the feature is not enabled
     */
    @Nullable
    private MXCrypto mCrypto;

    /**
     * @return the crypto instance
     */
    @Nullable
    public MXCrypto getCrypto() {
        return mCrypto;
    }

    @NotNull
    @Override
    public MXCrypto requireCrypto() {
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
     * Optional set of parameters used to configure/customize the e2e encryption
     */
    @Nullable
    private static MXCryptoConfig sCryptoConfig;

    /**
     * Define the set of parameters used to configure/customize the e2e encryption
     * This configuration must be set before instantiating the session
     */
    public static void setCryptoConfig(@Nullable MXCryptoConfig cryptoConfig) {
        sCryptoConfig = cryptoConfig;
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
        if ((mCryptoStore.hasData() || mEnableCryptoWhenStartingMXSession) && (null == mCrypto)) {
            boolean isStoreLoaded = false;
            try {
                // open the store
                mCryptoStore.open();
                isStoreLoaded = true;
            } catch (UnsatisfiedLinkError e) {
                Log.e(LOG_TAG, "## checkCrypto() failed " + e.getMessage(), e);
            }

            if (!isStoreLoaded) {
                // load again the olm manager
                // reported by rageshake, it seems that the olm lib is unloaded.
                mOlmManager = new OlmManager();

                try {
                    // open the store
                    mCryptoStore.open();
                    isStoreLoaded = true;
                } catch (UnsatisfiedLinkError e) {
                    Log.e(LOG_TAG, "## checkCrypto() failed 2 " + e.getMessage(), e);
                }
            }

            if (!isStoreLoaded) {
                Log.e(LOG_TAG, "## checkCrypto() : cannot enable the crypto because of olm lib");
                return;
            }

            mCrypto = new MXCryptoImpl(this,
                    mCryptoStore,
                    sCryptoConfig,
                    mHsConfig);
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
                mCryptoStore.open();
                mCrypto = new MXCryptoImpl(this,
                        mCryptoStore,
                        sCryptoConfig,
                        mHsConfig);
                mCrypto.start(true, new SimpleApiCallback<Void>(callback) {
                    @Override
                    public void onSuccess(Void info) {
                        decryptRoomSummaries();
                        if (null != callback) {
                            callback.onSuccess(null);
                        }
                    }
                });
            } else if (null != mCrypto) {
                Log.d(LOG_TAG, "Crypto is disabled");
                mCrypto.close();
                mCryptoStore.deleteStore();
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
        mCrypto.getCryptoRestClient().getDevices(callback);
    }

    /**
     * Set a device name.
     *
     * @param deviceId   the device id
     * @param deviceName the device name
     * @param callback   the asynchronous callback
     */
    public void setDeviceName(final String deviceId, final String deviceName, final ApiCallback<Void> callback) {
        mCrypto.getCryptoRestClient().setDeviceName(deviceId, deviceName, callback);
    }

    /**
     * Delete a device
     *
     * @param deviceId the device id
     * @param password the passwoerd
     * @param callback the asynchronous callback.
     */
    public void deleteDevice(final String deviceId, final String password, final ApiCallback<Void> callback) {
        mCrypto.getCryptoRestClient().deleteDevice(deviceId, new DeleteDeviceParams(), new SimpleApiCallback<Void>(callback) {
            @Override
            public void onSuccess(Void info) {
                // should never happen
                if (null != callback) {
                    callback.onSuccess(null);
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
                        Log.e(LOG_TAG, "## deleteDevice(): Received status 401 - Exception - JsonUtils.toRegistrationFlowResponse()", castExcept);
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

        mCrypto.getCryptoRestClient().deleteDevice(deviceId, params, new SimpleApiCallback<Void>(callback) {
            @Override
            public void onSuccess(Void info) {
                if (null != callback) {
                    callback.onSuccess(null);
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
        });
    }

    /**
     * Gets a bearer token from the homeserver that the user can
     * present to a third party in order to prove their ownership
     * of the Matrix account they are logged into.
     *
     * @param callback the asynchronous callback called when finished
     */
    public void openIdToken(final ApiCallback<RequestOpenIdTokenResponse> callback) {
        mOpenIdRestClient.requestToken(getMyUserId(), callback);
    }

    /**
     * @return the groups manager
     */
    public GroupsManager getGroupsManager() {
        return mGroupsManager;
    }

    @NotNull
    @Override
    public String getDeviceId() {
        return getCredentials().deviceId;
    }

    @Override
    public void setDeviceId(@NotNull String deviceId) {
        getCredentials().deviceId = deviceId;
    }

    /* ==========================================================================================
     * Builder
     * ========================================================================================== */

    public static class Builder {
        private final HomeServerConnectionConfig mHsConfig;
        private final MXDataHandler mDataHandler;
        private final Context mContext;
        @Nullable
        private String mPushServerUrl;
        // True if file encryption is enabled
        private boolean mEnableFileEncryption;
        // Used by unit tests to test Crypto store migration
        private boolean mUseLegacyCryptoStore;
        private MetricsListener mMetricsListener;


        public Builder(@NonNull HomeServerConnectionConfig hsConfig,
                       @NonNull MXDataHandler dataHandler,
                       @NonNull Context context) {
            mHsConfig = hsConfig;
            mDataHandler = dataHandler;
            mContext = context;
        }

        public Builder withFileEncryption(boolean enableFileEncryption) {
            mEnableFileEncryption = enableFileEncryption;
            return this;
        }

        @VisibleForTesting
        public Builder withLegacyCryptoStore(boolean useLegacyCryptoStore) {
            mUseLegacyCryptoStore = useLegacyCryptoStore;
            return this;
        }

        /**
         * Create a pusher rest client, overriding the push server url if necessary
         *
         * @param pushServerUrl the push server url, or null or empty to use the default PushersRestClient
         * @return this builder, to chain calls
         */
        public Builder withPushServerUrl(@Nullable String pushServerUrl) {
            mPushServerUrl = pushServerUrl;
            return this;
        }

        /**
         * Set the metrics listener of this session
         *
         * @param metricsListener the metrics listener
         * @return this builder, to chain calls
         */
        public Builder withMetricsListener(@Nullable MetricsListener metricsListener) {
            mMetricsListener = metricsListener;

            return this;
        }

        /**
         * Build the session
         *
         * @return the built session
         */
        public MXSession build() {
            return new MXSession(mHsConfig,
                    mDataHandler,
                    mContext,
                    mPushServerUrl,
                    mEnableFileEncryption,
                    mUseLegacyCryptoStore,
                    mMetricsListener);
        }
    }

    /* ==========================================================================================
     * Debug info
     * ========================================================================================== */

    @Override
    public String toString() {
        return getMyUserId() + " " + super.toString();
    }
}
