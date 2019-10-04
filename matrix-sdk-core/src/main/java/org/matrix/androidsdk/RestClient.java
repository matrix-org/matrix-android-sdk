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
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;

import org.matrix.androidsdk.core.Log;
import org.matrix.androidsdk.core.PolymorphicRequestBodyConverter;
import org.matrix.androidsdk.core.UnsentEventsManager;
import org.matrix.androidsdk.core.json.GsonProvider;
import org.matrix.androidsdk.core.listeners.IMXNetworkEventListener;
import org.matrix.androidsdk.network.NetworkConnectivityReceiver;
import org.matrix.androidsdk.rest.model.login.Credentials;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Class for making Matrix API calls.
 */
public class RestClient<T> {
    private static final String LOG_TAG = RestClient.class.getSimpleName();

    public static final String URI_API_PREFIX_PATH_MEDIA_R0 = "_matrix/media/r0/";
    public static final String URI_API_PREFIX_PATH_MEDIA_PROXY_UNSTABLE = "_matrix/media_proxy/unstable/";
    public static final String URI_API_PREFIX_PATH = "_matrix/client/";
    public static final String URI_API_PREFIX_PATH_R0 = "_matrix/client/r0/";
    public static final String URI_API_PREFIX_PATH_UNSTABLE = "_matrix/client/unstable/";

    /**
     * Prefix used in path of identity server API requests.
     */
    public static final String URI_IDENTITY_PATH = "_matrix/identity/api/v1/";
    public static final String URI_IDENTITY_PATH_V2 = "_matrix/identity/v2/";

    public static final String URI_API_PREFIX_IDENTITY = "_matrix/identity/api/v1";

    /**
     * Prefix used for integration manager
     */
    public static final String URI_INTEGRATION_MANAGER_PATH = "_matrix/integrations/v1/";

    /**
     * List the servers which should be used to define the base url.
     */
    public enum EndPointServer {
        HOME_SERVER,
        IDENTITY_SERVER,
        ANTIVIRUS_SERVER
    }

    protected static final int CONNECTION_TIMEOUT_MS = 30000;

    private String mAccessToken;

    protected T mApi;

    protected UnsentEventsManager mUnsentEventsManager;

    protected HomeServerConnectionConfig mHsConfig;

    // unitary tests only
    public static boolean mUseMXExecutor = false;

    // the user agent
    private static String sUserAgent = null;

    // http client
    private OkHttpClient mOkHttpClient;

    public RestClient(HomeServerConnectionConfig hsConfig, Class<T> type, String uriPrefix) {
        this(hsConfig, type, uriPrefix, GsonProvider.provideKotlinGson(), EndPointServer.HOME_SERVER);
    }

    public RestClient(HomeServerConnectionConfig hsConfig, Class<T> type, String uriPrefix, Gson gson) {
        this(hsConfig, type, uriPrefix, gson, EndPointServer.HOME_SERVER);
    }

    /**
     * Public constructor.
     *
     * @param hsConfig              the home server configuration.
     * @param type                  the REST type
     * @param uriPrefix             the URL request prefix
     * @param gson                  the gson parser
     * @param useIdentityServer     true to use the identity server URL as base request
     */
    public RestClient(HomeServerConnectionConfig hsConfig, Class<T> type, String uriPrefix, Gson gson, boolean useIdentityServer) {
        this(hsConfig, type, uriPrefix, gson, useIdentityServer ? EndPointServer.IDENTITY_SERVER : EndPointServer.HOME_SERVER);
    }

    /**
     * Public constructor.
     *
     * @param hsConfig              the home server configuration.
     * @param type                  the REST type
     * @param uriPrefix             the URL request prefix
     * @param gson                  the gson parser
     * @param endPointServer        tell which server is used to define the base url
     */
    public RestClient(HomeServerConnectionConfig hsConfig, Class<T> type, String uriPrefix, Gson gson, EndPointServer endPointServer) {
        mHsConfig = hsConfig;

        if (endPointServer == EndPointServer.HOME_SERVER) {
            Credentials credentials = hsConfig.getCredentials();
            if (credentials != null) {
                mAccessToken = credentials.accessToken;
            }
        }

        Interceptor authenticationInterceptor = new Interceptor() {

            @Override
            public Response intercept(Chain chain) throws IOException {
                Request request = chain.request();
                Request.Builder newRequestBuilder = request.newBuilder();
                if (null != sUserAgent) {
                    // set a custom user agent
                    newRequestBuilder.addHeader("User-Agent", sUserAgent);
                }

                // Add the access token to all requests if it is set
                if (mAccessToken != null) {
                    newRequestBuilder.addHeader("Authorization", "Bearer " + mAccessToken);
                }

                request = newRequestBuilder.build();

                return chain.proceed(request);
            }
        };

        final String endPoint = makeEndpoint(hsConfig, uriPrefix, endPointServer);
        mOkHttpClient = RestHttpClientFactoryProvider.Companion.getDefaultProvider().createHttpClient(hsConfig, endPoint, authenticationInterceptor);

        // Rest adapter for turning API interfaces into actual REST-calling objects
        Retrofit.Builder builder = new Retrofit.Builder()
                .baseUrl(endPoint)
                .addConverterFactory(PolymorphicRequestBodyConverter.FACTORY)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .client(mOkHttpClient);

        Retrofit retrofit = builder.build();

        mApi = retrofit.create(type);
    }

    @NonNull
    private String makeEndpoint(HomeServerConnectionConfig hsConfig, String uriPrefix, EndPointServer endPointServer) {
        String baseUrl;
        switch (endPointServer) {
            case IDENTITY_SERVER:
                baseUrl = hsConfig.getIdentityServerUri().toString();
                break;
            case ANTIVIRUS_SERVER:
                baseUrl = hsConfig.getAntiVirusServerUri().toString();
                break;
            case HOME_SERVER:
            default:
                baseUrl = hsConfig.getHomeserverUri().toString();

        }
        baseUrl = sanitizeBaseUrl(baseUrl);
        String dynamicPath = sanitizeDynamicPath(uriPrefix);
        return baseUrl + dynamicPath;
    }

    private String sanitizeBaseUrl(String baseUrl) {
        if (baseUrl.endsWith("/")) {
            return baseUrl;
        }
        return baseUrl + "/";
    }

    private String sanitizeDynamicPath(String dynamicPath) {
        // remove any trailing http in the uri prefix
        if (dynamicPath.startsWith("http://")) {
            dynamicPath = dynamicPath.substring("http://".length());
        } else if (dynamicPath.startsWith("https://")) {
            dynamicPath = dynamicPath.substring("https://".length());
        }
        return dynamicPath;
    }

    /**
     * Create an user agent with the application version.
     * Ex: Riot/0.8.12 (Linux; U; Android 6.0.1; SM-A510F Build/MMB29; Flavour FDroid; MatrixAndroidSDK 0.9.6)
     *
     * @param appContext        the application context
     * @param versionName       the application version name. Can be BuildConfig.VERSION_NAME by instance
     * @param flavorDescription the flavor description
     */
    public static void initUserAgent(@Nullable Context appContext,
                                     @NonNull String versionName,
                                     @NonNull String flavorDescription) {
        String appName = "";
        String appVersion = "";

        if (null != appContext) {
            try {
                String appPackageName = appContext.getApplicationContext().getPackageName();
                PackageManager pm = appContext.getPackageManager();
                ApplicationInfo appInfo = pm.getApplicationInfo(appPackageName, 0);
                appName = pm.getApplicationLabel(appInfo).toString();

                PackageInfo pkgInfo = pm.getPackageInfo(appContext.getApplicationContext().getPackageName(), 0);
                appVersion = pkgInfo.versionName;

                // Use appPackageName instead of appName if appName contains any non-ASCII character
                if (!appName.matches("\\A\\p{ASCII}*\\z")) {
                    appName = appPackageName;
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "## initUserAgent() : failed " + e.getMessage(), e);
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
        if ((null == sUserAgent) || (sUserAgent.lastIndexOf(")") == -1) || !sUserAgent.contains("(")) {
            sUserAgent = appName + "/" + appVersion + " ( Flavour " + flavorDescription
                    + "; MatrixAndroidSDK " + versionName + ")";
        } else {
            // update
            sUserAgent = appName + "/" + appVersion + " " +
                    sUserAgent.substring(sUserAgent.indexOf("("), sUserAgent.lastIndexOf(")") - 1) +
                    "; Flavour " + flavorDescription +
                    "; MatrixAndroidSDK " + versionName + ")";
        }
    }

    /**
     * Get the current user agent
     *
     * @return the current user agent, or null in case of error or if not initialized yet
     */
    @Nullable
    public static String getUserAgent() {
        return sUserAgent;
    }

    /**
     * Refresh the connection timeouts.
     *
     * @param networkConnectivityReceiver the network connectivity receiver
     */
    private void refreshConnectionTimeout(NetworkConnectivityReceiver networkConnectivityReceiver) {
        OkHttpClient.Builder builder = mOkHttpClient.newBuilder();

        if (networkConnectivityReceiver.isConnected()) {
            float factor = networkConnectivityReceiver.getTimeoutScale();

            builder
                    .connectTimeout((int) (CONNECTION_TIMEOUT_MS * factor), TimeUnit.MILLISECONDS)
                    .readTimeout((int) (RestClientHttpClientFactory.READ_TIMEOUT_MS * factor), TimeUnit.MILLISECONDS)
                    .writeTimeout((int) (RestClientHttpClientFactory.WRITE_TIMEOUT_MS * factor), TimeUnit.MILLISECONDS);

            Log.d(LOG_TAG, "## refreshConnectionTimeout()  : update setConnectTimeout to " + (CONNECTION_TIMEOUT_MS * factor) + " ms");
            Log.d(LOG_TAG, "## refreshConnectionTimeout()  : update setReadTimeout to " + (RestClientHttpClientFactory.READ_TIMEOUT_MS * factor) + " ms");
            Log.d(LOG_TAG, "## refreshConnectionTimeout()  : update setWriteTimeout to " + (RestClientHttpClientFactory.WRITE_TIMEOUT_MS * factor) + " ms");
        } else {
            builder.connectTimeout(1, TimeUnit.MILLISECONDS);
            Log.d(LOG_TAG, "## refreshConnectionTimeout()  : update the requests timeout to 1 ms");
        }

        // FIXME It has no effect to the rest client
        mOkHttpClient = builder.build();
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

        if (timeoutMs != mOkHttpClient.connectTimeoutMillis()) {
            // FIXME It has no effect to the rest client
            mOkHttpClient = mOkHttpClient.newBuilder().connectTimeout(timeoutMs, TimeUnit.MILLISECONDS).build();
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
                Log.d(LOG_TAG, "## setUnsentEventsManager()  : update the requests timeout to " + (isConnected ? CONNECTION_TIMEOUT_MS : 1) + " ms");
                refreshConnectionTimeout(networkConnectivityReceiver);
            }
        });
    }

    /**
     * Update the Access Token of the Rest Client. To be called after login or registration.
     *
     * @param newAccessToken the new Access Token
     */
    public void setAccessToken(String newAccessToken) {
        mAccessToken = newAccessToken;
    }

}
