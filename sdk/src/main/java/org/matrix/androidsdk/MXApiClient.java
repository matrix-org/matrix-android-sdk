package org.matrix.androidsdk;

import android.util.Log;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.squareup.okhttp.OkHttpClient;

import org.matrix.androidsdk.api.EventsApi;
import org.matrix.androidsdk.api.LoginApi;
import org.matrix.androidsdk.api.ProfileApi;
import org.matrix.androidsdk.api.response.Event;
import org.matrix.androidsdk.api.response.InitialSyncResponse;
import org.matrix.androidsdk.api.response.MatrixError;
import org.matrix.androidsdk.api.response.PublicRoom;
import org.matrix.androidsdk.api.response.TokensChunkResponse;
import org.matrix.androidsdk.api.response.User;
import org.matrix.androidsdk.api.response.login.Credentials;
import org.matrix.androidsdk.api.response.login.PasswordLoginParams;

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
 * Class for making matrix API calls.
 */
public class MXApiClient {

    private static final String LOG_TAG = "MXApiClient";

    private static final String URI_PREFIX = "/_matrix/client/api/v1";
    private static final String PARAM_ACCESS_TOKEN = "access_token";

    private static final int CONNECTION_TIMEOUT_MS = 60000;
    private static final int READ_TIMEOUT_MS = 60000;
    private static final int EVENT_STREAM_TIMEOUT_MS = 30000;

    private EventsApi mEventsApi;
    private ProfileApi mProfileApi;
    private LoginApi mLoginApi;

    private Credentials mCredentials;

    private Gson gson;

    /**
     * Generic callback interface for asynchronously returning information.
     * @param <T> the type of information
     */
    public interface ApiCallback<T> {
        public void onSuccess(T info);
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

        mEventsApi = restAdapter.create(EventsApi.class);
        mProfileApi = restAdapter.create(ProfileApi.class);
        mLoginApi = restAdapter.create(LoginApi.class);
    }

    public MXApiClient(Credentials credentials) {
        this(credentials.homeServer);
        mCredentials = credentials;
    }

    public EventsApi getEventsApiClient() {
        return mEventsApi;
    }

    public ProfileApi getProfileApiClient() {
        return mProfileApi;
    }

    public LoginApi getLoginApiClient() {
        return mLoginApi;
    }

    public Credentials getCredentials() {
        return mCredentials;
    }

    public void setCredentials(Credentials credentials) {
        mCredentials = credentials;
    }

    /**
     * Default protected constructor for unit tests.
     */
    protected MXApiClient() {
    }

    /**
     * Protected setter for injecting the events API for unit tests.
     * @param api the events API
     */
    protected void setEventsApi(EventsApi api) {
        mEventsApi = api;
    }

    /**
     * Protected setter for injecting the events API for unit tests.
     * @param api the profile API
     */
    protected void setProfileApi(ProfileApi api) {
        mProfileApi = api;
    }

    /**
     * Protected setter for injecting the login API for unit tests.
     * @param api the login API
     */
    protected void setLoginApi(LoginApi api) {
        mLoginApi = api;
    }

    ////////////////////////////////////////////////
    // Events API
    ////////////////////////////////////////////////
    public void loadPublicRooms(final LoadPublicRoomsCallback callback) {
        mEventsApi.publicRooms(new Callback<TokensChunkResponse<PublicRoom>>() {
            @Override
            public void success(TokensChunkResponse<PublicRoom> typedResponse, Response response) {
                callback.onRoomsLoaded(typedResponse.chunk);
            }

            @Override
            public void failure(RetrofitError error) {
                Log.e(LOG_TAG, "REST error: " + error.getMessage());
            }
        });
    }

    public void initialSync(final InitialSyncCallback callback) {
        mEventsApi.initialSync(1, new Callback<InitialSyncResponse>() {
            @Override
            public void success(InitialSyncResponse initialSync, Response response) {
                callback.onSynced(initialSync);
            }

            @Override
            public void failure(RetrofitError error) {
                Log.e(LOG_TAG, "REST error: " + error.getMessage());
            }
        });
    }

    public TokensChunkResponse<Event> events(String fromToken) {
        return events(fromToken, EVENT_STREAM_TIMEOUT_MS);
    }

    public TokensChunkResponse<Event> events(String fromToken, int timeoutMs) {
        return mEventsApi.events(fromToken, timeoutMs);
    }

    public interface LoadPublicRoomsCallback {
        public void onRoomsLoaded(List<PublicRoom> publicRooms);
    }

    public interface InitialSyncCallback {
        public void onSynced(InitialSyncResponse initialSync);
    }

    ////////////////////////////////////////////////
    // Profile API
    ////////////////////////////////////////////////
    public void displayname(String userId, final ApiCallback<String> callback) {
        mProfileApi.displayname(userId, new Callback<User>() {
            @Override
            public void success(User user, Response response) {
                callback.onSuccess(user.displayname);
            }

            @Override
            public void failure(RetrofitError error) {
                Log.e(LOG_TAG, "REST error: " + error.getMessage());
            }
        });
    }

    /**
     * Update this user's own display name.
     * @param newName the new name
     * @param callback the callback if the call succeeds
     */
    public void updateDisplayname(String newName, final ApiCallback<Void> callback) {
        User user = new User();
        user.displayname = newName;

        mProfileApi.displayname(mCredentials.userId, user, new Callback<Void>() {
            @Override
            public void success(Void aVoid, Response response) {
                callback.onSuccess(aVoid);
            }

            @Override
            public void failure(RetrofitError error) {
                Log.e(LOG_TAG, "REST error: " + error.getMessage());
            }
        });
    }

    public void avatarUrl(String userId, final ApiCallback<String> callback) {
        mProfileApi.avatarUrl(userId, new Callback<User>() {
            @Override
            public void success(User user, Response response) {
                callback.onSuccess(user.avatarUrl);
            }

            @Override
            public void failure(RetrofitError error) {
                Log.e(LOG_TAG, "REST error: " + error.getMessage());
            }
        });
    }

    ////////////////////////////////////////////////
    // Login API
    ////////////////////////////////////////////////
    public interface LoginCallback {
        public void onLoggedIn(Credentials credentials);
        public void onError(MatrixError error);
    }

    public void loginWithPassword(String user, String password, final LoginCallback callback) {
        PasswordLoginParams params = new PasswordLoginParams();
        params.user = user;
        params.password = password;

        mLoginApi.login(params, new Callback<JsonObject>() {
            @Override
            public void success(JsonObject jsonObject, Response response) {
                mCredentials = gson.fromJson(jsonObject, Credentials.class);
                callback.onLoggedIn(mCredentials);
            }

            @Override
            public void failure(RetrofitError error) {
                callback.onError((MatrixError) error.getBodyAs(MatrixError.class));
            }
        });
    }
}
