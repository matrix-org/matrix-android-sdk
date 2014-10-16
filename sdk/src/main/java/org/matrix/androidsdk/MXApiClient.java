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

import android.net.Uri;
import android.util.Log;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.squareup.okhttp.OkHttpClient;

import org.matrix.androidsdk.api.LoginApi;
import org.matrix.androidsdk.api.PresenceApi;
import org.matrix.androidsdk.api.ProfileApi;
import org.matrix.androidsdk.api.RegistrationApi;
import org.matrix.androidsdk.api.RoomsApi;
import org.matrix.androidsdk.api.response.MatrixError;
import org.matrix.androidsdk.api.response.login.Credentials;

import java.util.concurrent.TimeUnit;

import retrofit.Callback;
import retrofit.ErrorHandler;
import retrofit.RequestInterceptor;
import retrofit.RestAdapter;
import retrofit.RetrofitError;
import retrofit.client.OkClient;
import retrofit.converter.GsonConverter;

/**
 * Class for making Matrix API calls.
 */
public abstract class MXApiClient {

    private static final String LOG_TAG = "MXApiClient";

    private static final String URI_PREFIX = "/_matrix/client/api/v1";
    private static final String PARAM_ACCESS_TOKEN = "access_token";

    private static final int CONNECTION_TIMEOUT_MS = 60000;
    private static final int READ_TIMEOUT_MS = 60000;

    protected Credentials mCredentials;

    protected Gson gson;

    /**
     * Generic callback interface for asynchronously returning information.
     * @param <T> the type of information to return on success
     */
    public interface ApiCallback<T> {

        /**
         * Called if the API call is successful.
         * @param info the returned information
         */
        public void onSuccess(T info);

        /**
         * Called if there is a network error.
         * @param e the exception
         */
        public void onNetworkError(Exception e);

        /**
         * Called in case of a Matrix error.
         * @param e the Matrix error
         */
        public void onMatrixError(MatrixError e);

        /**
         * Called for some other type of error.
         * @param e the exception
         */
        public void onUnexpectedError(Exception e);
    }

    /**
     * A stub implementation of {@link MXApiClient.ApiCallback} which only chosen callbacks
     * can be implemented.
     */
    public static class SimpleApiCallback<T> implements MXApiClient.ApiCallback<T> {

        @Override
        public void onSuccess(T info) {

        }

        @Override
        public void onNetworkError(Exception e) {

        }

        @Override
        public void onMatrixError(MatrixError e) {

        }

        @Override
        public void onUnexpectedError(Exception e) {

        }
    }

    /**
     * Custom Retrofit error callback class that will call one of our ApiCallback error callbacks on a Retrofit failure.
     * When subclassing this, the Retrofit callback success call needs to be implemented.
     * @param <T> the type to return on success
     */
    public abstract static class ConvertFailureCallback<T> implements Callback<T> {

        private ApiCallback apiCallback;

        public ConvertFailureCallback(ApiCallback apiCallback) {
            this.apiCallback = apiCallback;
        }

        /**
         * Default failure implementation that calls the right error handler
         * @param error
         */
        @Override
        public void failure(RetrofitError error) {
            Log.e(LOG_TAG, error.getMessage() + " url=" + error.getUrl()+" body=" + error.getBody());
            if (error.isNetworkError()) {
                apiCallback.onNetworkError(error);
            }
            else {
                // Try to convert this into a Matrix error
                MatrixError mxError = (MatrixError) error.getBodyAs(MatrixError.class);
                if (mxError != null) {
                    apiCallback.onMatrixError(mxError);
                }
                else {
                    apiCallback.onUnexpectedError(error);
                }
            }
        }
    }

    /**
     * Public constructor.
     * @param hsUri The http[s] URI to the home server.
     */
    public MXApiClient(Uri hsUri) {
        // sanity check
        if (hsUri == null || (!"http".equals(hsUri.getScheme()) && !"https".equals(hsUri.getScheme())) ) {
            throw new RuntimeException("Invalid home server URI: "+hsUri);
        }

        // The JSON -> object mapper
        gson = new GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .create();

        // HTTP client
        OkHttpClient okHttpClient = new OkHttpClient();
        okHttpClient.setConnectTimeout(CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        okHttpClient.setReadTimeout(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        // Rest adapter for turning API interfaces into actual REST-calling objects
        RestAdapter restAdapter = new RestAdapter.Builder()
                .setEndpoint(hsUri.toString() + URI_PREFIX)
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

        initApi(restAdapter);
    }

    /**
     * Initialize the API object using the given RestAdapter.
     * @param restAdapter the Retrofit RestAdapter used to create API objects
     */
    protected abstract void initApi(RestAdapter restAdapter);

    /**
     * Constructor providing the full user credentials. To use to avoid having to log the user in.
     * @param credentials the user credentials
     */
    public MXApiClient(Credentials credentials) {
        this(Uri.parse(credentials.homeServer));
        mCredentials = credentials;
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
    protected MXApiClient() {
    }
}
