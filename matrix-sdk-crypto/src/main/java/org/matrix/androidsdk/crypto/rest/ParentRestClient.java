/*
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
package org.matrix.androidsdk.crypto.rest;

import android.support.annotation.NonNull;
import android.text.TextUtils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.matrix.androidsdk.core.BuildConfig;
import org.matrix.androidsdk.core.interceptors.CurlLoggingInterceptor;
import org.matrix.androidsdk.core.interceptors.FormattedJsonHttpLogger;
import org.matrix.androidsdk.core.json.BooleanDeserializer;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Converter;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Class for making Matrix API calls.
 */
public class ParentRestClient<T> {
    private static final String LOG_TAG = ParentRestClient.class.getSimpleName();

    public static final String URI_API_PREFIX_PATH_R0 = "_matrix/client/r0/";
    public static final String URI_API_PREFIX_PATH_UNSTABLE = "_matrix/client/unstable/";

    protected static final int CONNECTION_TIMEOUT_MS = 30000;
    private static final int READ_TIMEOUT_MS = 60000;
    private static final int WRITE_TIMEOUT_MS = 60000;

    private final String mAccessToken;

    protected T mApi;

    // the user agent
    private static String sUserAgent = null;

    protected ParentRestClient(String baseUrl, String accessToken, Class<T> type, String uriPrefix, Converter.Factory converterFactory) {
        mAccessToken = accessToken;

        Gson gson = new GsonBuilder()
                .registerTypeAdapter(boolean.class, new BooleanDeserializer(false))
                .registerTypeAdapter(Boolean.class, new BooleanDeserializer(true))
                .create();

        Interceptor authentInterceptor = new Interceptor() {

            @Override
            public Response intercept(Chain chain) throws IOException {
                Request request = chain.request();
                Request.Builder newRequestBuilder = request.newBuilder();
                if (null != sUserAgent) {
                    // set a custom user agent
                    newRequestBuilder.addHeader("User-Agent", sUserAgent);
                }

                // Add the access token to all requests if it is set
                if (!TextUtils.isEmpty(mAccessToken)) {
                    newRequestBuilder.addHeader("Authorization", "Bearer " + mAccessToken);
                }

                request = newRequestBuilder.build();

                return chain.proceed(request);
            }
        };

        OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient().newBuilder()
                .connectTimeout(CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .writeTimeout(WRITE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .addInterceptor(authentInterceptor);

        if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor(new FormattedJsonHttpLogger());
            loggingInterceptor.setLevel(BuildConfig.OKHTTP_LOGGING_LEVEL);

            okHttpClientBuilder
                    .addInterceptor(loggingInterceptor)
                    .addInterceptor(new CurlLoggingInterceptor());
        }

        final String endPoint = makeEndpoint(baseUrl, uriPrefix);

        /* TODO
        try {
            Pair<SSLSocketFactory, X509TrustManager> pair = CertUtil.newPinnedSSLSocketFactory(hsConfig);
            okHttpClientBuilder.sslSocketFactory(pair.first, pair.second);
            okHttpClientBuilder.hostnameVerifier(CertUtil.newHostnameVerifier(hsConfig));
            okHttpClientBuilder.connectionSpecs(CertUtil.newConnectionSpecs(hsConfig, endPoint));
        } catch (Exception e) {
            Log.e(LOG_TAG, "## RestClient() setSslSocketFactory failed: " + e.getMessage(), e);
        }
        */

        // http client
        OkHttpClient okHttpClient = okHttpClientBuilder.build();

        // Rest adapter for turning API interfaces into actual REST-calling objects
        Retrofit.Builder builder = new Retrofit.Builder()
                .baseUrl(endPoint)
                .addConverterFactory(converterFactory)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .client(okHttpClient);

        Retrofit retrofit = builder.build();

        mApi = retrofit.create(type);
    }

    @NonNull
    private String makeEndpoint(String homeServerUrl, String uriPrefix) {
        String baseUrl;
        baseUrl = sanitizeBaseUrl(homeServerUrl);
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
     * Set the user agent
     *
     * @param userAgent
     */
    public static void initUserAgent(@NonNull String userAgent) {
        sUserAgent = userAgent;
    }
}
