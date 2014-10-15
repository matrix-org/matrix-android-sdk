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

import android.util.Log;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.squareup.okhttp.OkHttpClient;

import org.matrix.androidsdk.api.EventsApi;
import org.matrix.androidsdk.api.LoginApi;
import org.matrix.androidsdk.api.PresenceApi;
import org.matrix.androidsdk.api.ProfileApi;
import org.matrix.androidsdk.api.RegistrationApi;
import org.matrix.androidsdk.api.RoomsApi;
import org.matrix.androidsdk.api.response.CreateRoomResponse;
import org.matrix.androidsdk.api.response.Event;
import org.matrix.androidsdk.api.response.InitialSyncResponse;
import org.matrix.androidsdk.api.response.MatrixError;
import org.matrix.androidsdk.api.response.Message;
import org.matrix.androidsdk.api.response.PublicRoom;
import org.matrix.androidsdk.api.response.RoomMember;
import org.matrix.androidsdk.api.response.TokensChunkResponse;
import org.matrix.androidsdk.api.response.User;
import org.matrix.androidsdk.api.response.login.Credentials;
import org.matrix.androidsdk.api.response.login.PasswordLoginParams;
import org.matrix.androidsdk.data.RoomState;

import java.util.List;
import java.util.concurrent.TimeUnit;

import retrofit.Callback;
import retrofit.ErrorHandler;
import retrofit.RequestInterceptor;
import retrofit.RestAdapter;
import retrofit.RetrofitError;
import retrofit.client.OkClient;
import retrofit.client.Response;
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
    protected static final int EVENT_STREAM_TIMEOUT_MS = 30000;
    protected static final int MESSAGES_PAGINATION_LIMIT = 15;

    private ProfileApi mProfileApi;
    private LoginApi mLoginApi;
    private RegistrationApi mRegistrationApi;
    private PresenceApi mPresenceApi;
    private RoomsApi mRoomsApi;

    protected Credentials mCredentials;

    protected Gson gson;

    /**
     * Generic callback interface for asynchronously returning information.
     * @param <T> the type of information
     */
    public interface ApiCallback<T> {
        public void onSuccess(T info);
    }

    /**
     * Default retrofit callback providing a default failure implementation.
     * @param <T>
     */
    public abstract static class DefaultCallback<T> implements Callback<T> {

        @Override
        public void failure(RetrofitError error) {
            Log.e(LOG_TAG, "REST error: " + error.getMessage());
        }
    }

    /**
     * Public constructor.
     * @param hsDomain the home server domain name
     */
    public MXApiClient(String hsDomain) {
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
                .setEndpoint("http://" + hsDomain + URI_PREFIX)
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
                .setErrorHandler(new ErrorHandler() {
                    @Override
                    public Throwable handleError(RetrofitError cause) {
                        if (cause.isNetworkError()) {
                            Log.e(LOG_TAG, cause.getMessage());
                            return null;
                        }
                        return cause;
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
        this(credentials.homeServer);
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
