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

import com.google.gson.Gson;
import com.squareup.okhttp.OkHttpClient;

import org.matrix.androidsdk.rest.model.login.Credentials;
import org.matrix.androidsdk.ssl.CertUtil;
import org.matrix.androidsdk.ssl.Fingerprint;
import org.matrix.androidsdk.ssl.PinnedTrustManager;
import org.matrix.androidsdk.util.JsonUtils;
import org.matrix.androidsdk.util.UnsentEventsManager;

import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import retrofit.RequestInterceptor;
import retrofit.RestAdapter;
import retrofit.client.OkClient;
import retrofit.converter.GsonConverter;

/**
 * Class for making Matrix API calls.
 */
public class RestClient<T> {

    private static final String LOG_TAG = "RestClient";

    public static final String URI_API_PREFIX = "/_matrix/client/api/v1";
    public static final String URI_API_PREFIX_V2 = "/_matrix/client/v2_alpha";
    public static final String URI_IDENTITY_PREFIX = "/_matrix/identity/api/v1";
    private static final String PARAM_ACCESS_TOKEN = "access_token";

    private static final int CONNECTION_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 60000;

    protected Credentials mCredentials;

    protected T mApi;

    protected Gson gson;

    protected UnsentEventsManager mUnsentEventsManager;

    /**
     * Public constructor.
     * @param hsConfig The homeserver connection config.
     */
    public RestClient(HomeserverConnectionConfig hsConfig, Class<T> type, String uriPrefix, boolean withNullSerialization) {
        // The JSON -> object mapper
        gson = JsonUtils.getGson(withNullSerialization);

        mCredentials = hsConfig.getCredentials();

        OkHttpClient okHttpClient = new OkHttpClient();

        okHttpClient.setConnectTimeout(CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        okHttpClient.setReadTimeout(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        okHttpClient.setSslSocketFactory(CertUtil.newPinnedSSLSocketFactory(hsConfig));
        okHttpClient.setHostnameVerifier(CertUtil.newHostnameVerifier(hsConfig));

        // Rest adapter for turning API interfaces into actual REST-calling objects
        RestAdapter restAdapter = new RestAdapter.Builder()
                .setEndpoint(hsConfig.getHomeserverUri().toString() + uriPrefix)
                .setConverter(new GsonConverter(gson))
                .setClient(new OkClient(okHttpClient))
                .setRequestInterceptor(new RequestInterceptor() {
                    @Override
                    public void intercept(RequestInterceptor.RequestFacade request) {
                        // Add the access token to all requests if it is set
                        if ((mCredentials != null) && (mCredentials.accessToken != null)) {
                            request.addEncodedQueryParam(PARAM_ACCESS_TOKEN, mCredentials.accessToken);
                        }
                    }
                })
                .build();

        restAdapter.setLogLevel(RestAdapter.LogLevel.FULL);

        mApi = restAdapter.create(type);
    }

    /**
     * Set the unsentEvents manager.
     * @param unsentEventsManager The unsentEvents manager.
     */
    public void setUnsentEventsManager(UnsentEventsManager unsentEventsManager) {
        mUnsentEventsManager = unsentEventsManager;
    }

    /**
     * Get the user's credentials. Typically for saving them somewhere persistent.
     * @return the user credentials
     */
    public Credentials getCredentials() {
        return mCredentials;
    }

    /**
     * Provide the user's credentials. To be called after login or registration.
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
     * @param api the api object
     */
    protected void setApi(T api) {
        mApi = api;
    }
}
