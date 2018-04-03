/*
 * Copyright 2014 OpenMarket Ltd
 * Copyright 2017 Vector Creations Ltd
 
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
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.text.TextUtils;

import com.google.gson.Gson;
import com.squareup.okhttp.OkHttpClient;

import org.matrix.androidsdk.listeners.IMXNetworkEventListener;
import org.matrix.androidsdk.network.NetworkConnectivityReceiver;
import org.matrix.androidsdk.rest.client.MXRestExecutor;
import org.matrix.androidsdk.rest.model.login.Credentials;
import org.matrix.androidsdk.ssl.CertUtil;
import org.matrix.androidsdk.util.JsonUtils;
import org.matrix.androidsdk.util.Log;
import org.matrix.androidsdk.util.UnsentEventsManager;

import java.util.concurrent.TimeUnit;

import retrofit.RequestInterceptor;
import retrofit.RestAdapter;
import retrofit.client.OkClient;
import retrofit.converter.GsonConverter;

/**
 * Class for making Matrix API calls.
 */
public class RestClient<T> {
    private static final String LOG_TAG = RestClient.class.getSimpleName();

    public static final String URI_API_PREFIX_PATH_MEDIA_R0 = "/_matrix/media/r0";
    public static final String URI_API_PREFIX_PATH_R0 = "/_matrix/client/r0";
    public static final String URI_API_PREFIX_PATH_UNSTABLE = "/_matrix/client/unstable";

    /**
     * Prefix used in path of identity server API requests.
     */
    public static final String URI_API_PREFIX_IDENTITY = "/_matrix/identity/api/v1";

    private static final String PARAM_ACCESS_TOKEN = "access_token";

    protected static final int CONNECTION_TIMEOUT_MS = 30000;
    private static final int READ_TIMEOUT_MS = 60000;
    private static final int WRITE_TIMEOUT_MS = 60000;

    protected Credentials mCredentials;

    protected T mApi;

    protected Gson gson;

    protected UnsentEventsManager mUnsentEventsManager;

    protected HomeServerConnectionConfig mHsConfig;

    // unitary tests only
    public static boolean mUseMXExececutor = false;

    // the user agent
    private static String sUserAgent = null;

    // http client
    private OkHttpClient mOkHttpClient = new OkHttpClient();

    public RestClient(HomeServerConnectionConfig hsConfig, Class<T> type, String uriPrefix, boolean withNullSerialization) {
        this(hsConfig, type, uriPrefix, withNullSerialization, false);
    }

    /**
     * Public constructor.
     *
     * @param hsConfig              the home server configuration.
     * @param type                  the REST type
     * @param uriPrefix             the URL request prefix
     * @param withNullSerialization true to serialise class member with null value
     * @param useIdentityServer     true to use the identity server URL as base request
     */
    public RestClient(HomeServerConnectionConfig hsConfig, Class<T> type, String uriPrefix, boolean withNullSerialization, boolean useIdentityServer) {
        // The JSON -> object mapper
        gson = JsonUtils.getGson(withNullSerialization);

        mHsConfig = hsConfig;
        mCredentials = hsConfig.getCredentials();

        mOkHttpClient = new OkHttpClient();

        mOkHttpClient.setConnectTimeout(CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        mOkHttpClient.setReadTimeout(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        mOkHttpClient.setWriteTimeout(WRITE_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        try {
            mOkHttpClient.setSslSocketFactory(CertUtil.newPinnedSSLSocketFactory(hsConfig));
        } catch (Exception e) {
            Log.e(LOG_TAG, "## RestClient() setSslSocketFactory failed" + e.getMessage());
        }

        try {
            mOkHttpClient.setHostnameVerifier(CertUtil.newHostnameVerifier(hsConfig));
        } catch (Exception e) {
            Log.e(LOG_TAG, "## RestClient() setSslSocketFactory failed" + e.getMessage());
        }

        // remove any trailing http in the uri prefix
        if (uriPrefix.startsWith("http://")) {
            uriPrefix = uriPrefix.substring("http://".length());
        } else if (uriPrefix.startsWith("https://")) {
            uriPrefix = uriPrefix.substring("https://".length());
        }

        final String endPoint = (useIdentityServer ? hsConfig.getIdentityServerUri().toString() : hsConfig.getHomeserverUri().toString()) + uriPrefix;

        // Rest adapter for turning API interfaces into actual REST-calling objects
        RestAdapter.Builder builder = new RestAdapter.Builder()
                .setEndpoint(endPoint)
                .setConverter(new GsonConverter(gson))
                .setClient(new OkClient(mOkHttpClient))
                .setRequestInterceptor(new RequestInterceptor() {
                    @Override
                    public void intercept(RequestInterceptor.RequestFacade request) {
                        if (null != sUserAgent) {
                            // set a custom user agent
                            request.addHeader("User-Agent", sUserAgent);
                        }

                        // Add the access token to all requests if it is set
                        if ((mCredentials != null) && (mCredentials.accessToken != null)) {
                            request.addHeader("Authorization", "Bearer " + mCredentials.accessToken);
                        }
                    }
                });

        if (mUseMXExececutor) {
            builder.setExecutors(new MXRestExecutor(), new MXRestExecutor());
        }

        RestAdapter restAdapter = builder.build();

        // debug only
        //restAdapter.setLogLevel(RestAdapter.LogLevel.FULL);

        mApi = restAdapter.create(type);
    }

    /**
     * Create an user agent with the application version.
     *
     * @param appContext the application context
     */
    public static void initUserAgent(Context appContext) {
        String appName = "";
        String appVersion = "";

        if (null != appContext) {
            try {
                PackageManager pm = appContext.getPackageManager();
                ApplicationInfo appInfo = pm.getApplicationInfo(appContext.getApplicationContext().getPackageName(), 0);
                appName = pm.getApplicationLabel(appInfo).toString();

                PackageInfo pkgInfo = pm.getPackageInfo(appContext.getApplicationContext().getPackageName(), 0);
                appVersion = pkgInfo.versionName;
            } catch (Exception e) {
                Log.e(LOG_TAG, "## initUserAgent() : failed " + e.getMessage());
            }
        }

        sUserAgent = System.getProperty("http.agent");

        // cannot retrieve the application version
        if (TextUtils.isEmpty(appName) || TextUtils.isEmpty(appVersion)) {
            if (null == sUserAgent) {
                sUserAgent = "Java" + System.getProperty("java.version");
            }
            return;
        }

        // if there is no user agent or cannot parse it
        if ((null == sUserAgent) || (sUserAgent.lastIndexOf(")") == -1) || (sUserAgent.indexOf("(") == -1)) {
            sUserAgent = appName + "/" + appVersion + " ( Flavour " + appContext.getString(R.string.flavor_description) + "; MatrixAndroidSDK " + BuildConfig.VERSION_NAME + ")";
        } else {
            // update
            sUserAgent = appName + "/" + appVersion + " " +
                    sUserAgent.substring(sUserAgent.indexOf("("), sUserAgent.lastIndexOf(")") - 1) +
                    "; Flavour " + appContext.getString(R.string.flavor_description) +
                    "; MatrixAndroidSDK " + BuildConfig.VERSION_NAME + ")";
        }
    }

    /**
     * Refresh the connection timeouts.
     *
     * @param networkConnectivityReceiver the network connectivity receiver
     */
    private void refreshConnectionTimeout(NetworkConnectivityReceiver networkConnectivityReceiver) {
        if (networkConnectivityReceiver.isConnected()) {
            float factor = networkConnectivityReceiver.getTimeoutScale();

            mOkHttpClient.setConnectTimeout((int) (CONNECTION_TIMEOUT_MS * factor), TimeUnit.MILLISECONDS);
            mOkHttpClient.setReadTimeout((int) (READ_TIMEOUT_MS * factor), TimeUnit.MILLISECONDS);
            mOkHttpClient.setWriteTimeout((int) (WRITE_TIMEOUT_MS * factor), TimeUnit.MILLISECONDS);

            Log.e(LOG_TAG, "## refreshConnectionTimeout()  : update setConnectTimeout to " + mOkHttpClient.getConnectTimeout() + " ms");
            Log.e(LOG_TAG, "## refreshConnectionTimeout()  : update setReadTimeout to " + mOkHttpClient.getReadTimeout() + " ms");
            Log.e(LOG_TAG, "## refreshConnectionTimeout()  : update setWriteTimeout to " + mOkHttpClient.getWriteTimeout() + " ms");
        } else {
            mOkHttpClient.setConnectTimeout(1, TimeUnit.MILLISECONDS);
            Log.e(LOG_TAG, "## refreshConnectionTimeout()  : update the requests timeout to 1 ms");
        }
    }

    /**
     * Update the connection timeout
     *
     * @param aTimeoutMs the connection timeout
     */
    protected void setConnectionTimeout(int aTimeoutMs) {
        int timeoutMs = aTimeoutMs;

        if (null != mUnsentEventsManager) {
            NetworkConnectivityReceiver networkConnectivityReceiver = mUnsentEventsManager.getNetworkConnectivityReceiver();

            if (null != networkConnectivityReceiver) {
                if (networkConnectivityReceiver.isConnected()) {
                    timeoutMs *= networkConnectivityReceiver.getTimeoutScale();
                } else {
                    timeoutMs = 1000;
                }
            }
        }

        if (timeoutMs != mOkHttpClient.getConnectTimeout()) {
            mOkHttpClient.setConnectTimeout(timeoutMs, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Set the unsentEvents manager.
     *
     * @param unsentEventsManager The unsentEvents manager.
     */
    public void setUnsentEventsManager(UnsentEventsManager unsentEventsManager) {
        mUnsentEventsManager = unsentEventsManager;

        final NetworkConnectivityReceiver networkConnectivityReceiver = mUnsentEventsManager.getNetworkConnectivityReceiver();
        refreshConnectionTimeout(networkConnectivityReceiver);

        networkConnectivityReceiver.addEventListener(new IMXNetworkEventListener() {
            @Override
            public void onNetworkConnectionUpdate(boolean isConnected) {
                Log.e(LOG_TAG, "## setUnsentEventsManager()  : update the requests timeout to " + (isConnected ? CONNECTION_TIMEOUT_MS : 1) + " ms");
                refreshConnectionTimeout(networkConnectivityReceiver);
            }
        });
    }

    /**
     * Get the user's credentials. Typically for saving them somewhere persistent.
     *
     * @return the user credentials
     */
    public Credentials getCredentials() {
        return mCredentials;
    }

    /**
     * Provide the user's credentials. To be called after login or registration.
     *
     * @param credentials the user credentials
     */
    public void setCredentials(Credentials credentials) {
        mCredentials = credentials;
    }

    /**
     * Default protected constructor for unit tests.
     */
    protected RestClient() {
    }

    /**
     * Protected setter for injection by unit tests.
     *
     * @param api the api object
     */
    protected void setApi(T api) {
        mApi = api;
    }
}
