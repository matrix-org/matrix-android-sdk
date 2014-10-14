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
public class MXApiClient {

    private static final String LOG_TAG = "MXApiClient";

    private static final String URI_PREFIX = "/_matrix/client/api/v1";
    private static final String PARAM_ACCESS_TOKEN = "access_token";

    private static final int CONNECTION_TIMEOUT_MS = 60000;
    private static final int READ_TIMEOUT_MS = 60000;
    private static final int EVENT_STREAM_TIMEOUT_MS = 30000;
    private static final int MESSAGES_PAGINATION_LIMIT = 15;

    private EventsApi mEventsApi;
    private ProfileApi mProfileApi;
    private LoginApi mLoginApi;
    private RegistrationApi mRegistrationApi;
    private PresenceApi mPresenceApi;
    private RoomsApi mRoomsApi;

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
        mRegistrationApi = restAdapter.create(RegistrationApi.class);
        mPresenceApi = restAdapter.create(PresenceApi.class);
        mRoomsApi = restAdapter.create(RoomsApi.class);
    }

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

    /**
     * Protected setter for injecting the registration API for unit tests.
     * @param api the registration API
     */
    protected void setRegistrationApi(RegistrationApi api) {
        mRegistrationApi = api;
    }

    /**
     * Protected setter for injecting the presence API for unit tests.
     * @param api the registration API
     */
    protected void setPresenceApi(PresenceApi api) {
        mPresenceApi = api;
    }

    /**
     * Protected setter for injecting the rooms API for unit tests.
     * @param api the rooms API
     */
    protected void setRoomsApi(RoomsApi api) {
        mRoomsApi = api;
    }

    ////////////////////////////////////////////////
    // Events API
    ////////////////////////////////////////////////

    /**
     * Get the list of the home server's public rooms.
     * @param callback callback to provide the list of public rooms on success
     */
    public void loadPublicRooms(final LoadPublicRoomsCallback callback) {
        mEventsApi.publicRooms(new DefaultCallback<TokensChunkResponse<PublicRoom>>() {
            @Override
            public void success(TokensChunkResponse<PublicRoom> typedResponse, Response response) {
                callback.onRoomsLoaded(typedResponse.chunk);
            }
        });
    }

    /**
     * Get initial information about the user's rooms, messages, other users.
     * @param callback callback to provide the information
     */
    public void initialSync(final InitialSyncCallback callback) {
        // Only retrieving one message per room for now
        mEventsApi.initialSync(1, new DefaultCallback<InitialSyncResponse>() {
            @Override
            public void success(InitialSyncResponse initialSync, Response response) {
                callback.onSynced(initialSync);
            }
        });
    }

    /**
     * {@link #events(String, int)} with a default timeout.
     * @param fromToken the token provided by the previous call's response
     * @return a list of events
     */
    public TokensChunkResponse<Event> events(String fromToken) {
        return events(fromToken, EVENT_STREAM_TIMEOUT_MS);
    }

    /**
     * Long poll for the next events. To be called repeatedly to listen to the events stream.
     * @param fromToken the token provided by the previous call's response
     * @param timeoutMs max time before the server sends a response
     * @return a list of events
     */
    public TokensChunkResponse<Event> events(String fromToken, int timeoutMs) {
        return mEventsApi.events(fromToken, timeoutMs);
    }

    /**
     * Callback for returning the list of public rooms.
     */
    public interface LoadPublicRoomsCallback {
        public void onRoomsLoaded(List<PublicRoom> publicRooms);
    }

    /**
     * Callback for returning the initial sync information.
     */
    public interface InitialSyncCallback {
        public void onSynced(InitialSyncResponse initialSync);
    }

    ////////////////////////////////////////////////
    // Profile API
    ////////////////////////////////////////////////

    /**
     * Get the user's display name.
     * @param userId the user id
     * @param callback the callback to return the name on success
     */
    public void displayname(String userId, final ApiCallback<String> callback) {
        mProfileApi.displayname(userId, new DefaultCallback<User>() {
            @Override
            public void success(User user, Response response) {
                callback.onSuccess(user.displayname);
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

        mProfileApi.displayname(mCredentials.userId, user, new DefaultCallback<Void>() {
            @Override
            public void success(Void aVoid, Response response) {
                callback.onSuccess(aVoid);
            }
        });
    }

    /**
     * Get the user's avatar URL.
     * @param userId the user id
     * @param callback the callback to return the URL on success
     */
    public void avatarUrl(String userId, final ApiCallback<String> callback) {
        mProfileApi.avatarUrl(userId, new DefaultCallback<User>() {
            @Override
            public void success(User user, Response response) {
                callback.onSuccess(user.avatarUrl);
            }
        });
    }

    /**
     * Update this user's own avatar URL.
     * @param newUrl the new name
     * @param callback the callback if the call succeeds
     */
    public void updateAvatarUrl(String newUrl, final ApiCallback<Void> callback) {
        User user = new User();
        user.avatarUrl = newUrl;

        mProfileApi.avatarUrl(mCredentials.userId, user, new DefaultCallback<Void>() {
            @Override
            public void success(Void aVoid, Response response) {
                callback.onSuccess(aVoid);
            }
        });
    }

    ////////////////////////////////////////////////
    // Login API
    ////////////////////////////////////////////////

    /**
     * Callback to return the user's credentials on success, a matrix error on error.
     */
    public interface LoginCallback {

        /**
         * Called when the user was successfully logged in.
         * @param credentials the user's credentials
         */
        public void onLoggedIn(Credentials credentials);

        /**
         * Called on login error.
         * @param error the error
         */
        public void onError(MatrixError error);
    }

    /**
     * Attempt a user/password log in.
     * @param user the user name
     * @param password the password
     * @param callback the callback success and failure callback
     */
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

    ////////////////////////////////////////////////
    // Registration API
    ////////////////////////////////////////////////

    /**
     * Attempt a user/password registration.
     * @param user the user name
     * @param password the password
     * @param callback the callback success and failure callback
     */
    public void registerWithPassword(String user, String password, final LoginCallback callback) {
        PasswordLoginParams params = new PasswordLoginParams();
        params.user = user;
        params.password = password;

        mRegistrationApi.register(params, new Callback<JsonObject>() {
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

    ////////////////////////////////////////////////
    // Presence API
    ////////////////////////////////////////////////

    /**
     * Set this user's presence.
     * @param presence the presence state
     * @param statusMsg a status message
     * @param callback on success callback
     */
    public void setPresence(String presence, String statusMsg, final ApiCallback<Void> callback) {
        User userPresence = new User();
        userPresence.presence = presence;
        userPresence.statusMsg = statusMsg;

        mPresenceApi.presenceStatus(mCredentials.userId, userPresence, new DefaultCallback<Void>() {
            @Override
            public void success(Void aVoid, Response response) {
                callback.onSuccess(aVoid);
            }
        });
    }

    /**
     * Get a user's presence state.
     * @param userId the user id
     * @param callback on success callback containing a User object with populated presence and statusMsg fields
     */
    public void getPresence(String userId, final ApiCallback<User> callback) {
        mPresenceApi.presenceStatus(userId, new DefaultCallback<User>() {
            @Override
            public void success(User user, Response response) {
                callback.onSuccess(user);
            }
        });
    }

    ////////////////////////////////////////////////
    // Rooms API
    ////////////////////////////////////////////////

    /**
     * Send a message to a room.
     * @param roomId the the room id
     * @param message the message
     * @param callback the callback containing the created event if successful
     */
    public void sendMessage(String roomId, Message message, final ApiCallback<Event> callback) {
        mRoomsApi.sendMessage(roomId, message, new DefaultCallback<Event>() {
            @Override
            public void success(Event event, Response response) {
                callback.onSuccess(event);
            }
        });
    }

    /**
     * Send a message to a room.
     * @param roomId the the room id
     * @param eventType the type of event
     * @param content the event content
     * @param callback the callback containing the created event if successful
     */
    public void sendEvent(String roomId, String eventType, JsonObject content, final ApiCallback<Event> callback) {
        mRoomsApi.send(roomId, eventType, content, new DefaultCallback<Event>() {
            @Override
            public void success(Event event, Response response) {
                callback.onSuccess(event);
            }
        });
    }

    /**
     * Get the last messages for the given room.
     * @param roomId the room id
     * @param callback the callback called with the response
     */
    public void getLastRoomMessages(String roomId, final ApiCallback<List<Message>> callback) {
        mRoomsApi.messages(roomId, MESSAGES_PAGINATION_LIMIT, new DefaultCallback<TokensChunkResponse<Message>>() {
            @Override
            public void success(TokensChunkResponse<Message> messageTokensChunkResponse, Response response) {
                callback.onSuccess(messageTokensChunkResponse.chunk);
            }
        });
    }

    /**
     * Get messages for the given room starting from the given token.
     * @param roomId the room id
     * @param fromToken the token identifying the message to start from
     * @param callback the callback called with the response
     */
    public void getRoomMessagesFrom(String roomId, String fromToken, final ApiCallback<List<Message>> callback) {
        mRoomsApi.messagesFrom(roomId, fromToken, MESSAGES_PAGINATION_LIMIT, new DefaultCallback<TokensChunkResponse<Message>>() {
            @Override
            public void success(TokensChunkResponse<Message> messageTokensChunkResponse, Response response) {
                callback.onSuccess(messageTokensChunkResponse.chunk);
            }
        });
    }

    /**
     * Get messages for the given room up to the given token.
     * @param roomId the room id
     * @param toToken the token identifying up to which message we should
     * @param callback the callback called with the response
     */
    public void getRoomMessagesTo(String roomId, String toToken, final ApiCallback<List<Message>> callback) {
        mRoomsApi.messagesFrom(roomId, toToken, MESSAGES_PAGINATION_LIMIT, new DefaultCallback<TokensChunkResponse<Message>>() {
            @Override
            public void success(TokensChunkResponse<Message> messageTokensChunkResponse, Response response) {
                callback.onSuccess(messageTokensChunkResponse.chunk);
            }
        });
    }

    /**
     * Get the list of members for the given room.
     * @param roomId the room id
     * @param callback the callback called with the response
     */
    public void getRoomMembers(String roomId, final ApiCallback<List<RoomMember>> callback) {
        mRoomsApi.members(roomId, new DefaultCallback<TokensChunkResponse<RoomMember>>() {
            @Override
            public void success(TokensChunkResponse<RoomMember> messageTokensChunkResponse, Response response) {
                callback.onSuccess(messageTokensChunkResponse.chunk);
            }
        });
    }

    /**
     * Get the list of members for the given room.
     * @param roomId the room id
     * @param callback the callback called with the response
     */
    public void getRoomState(String roomId, final ApiCallback<List<Event>> callback) {
        mRoomsApi.state(roomId, new DefaultCallback<List<Event>>() {
            @Override
            public void success(List<Event> stateEvents, Response response) {
                callback.onSuccess(stateEvents);
            }
        });
    }

    /**
     * Invite a user to a room.
     * @param roomId the room id
     * @param userId the the user id
     * @param callback on success callback
     */
    public void inviteToRoom(String roomId, String userId, final ApiCallback<Void> callback) {
        mRoomsApi.invite(roomId, userId, new DefaultCallback<Void>() {
            @Override
            public void success(Void aVoid, Response response) {
                callback.onSuccess(aVoid);
            }
        });
    }

    /**
     * Join a room.
     * @param roomId the room id
     * @param callback on success callback
     */
    public void joinRoom(String roomId, final ApiCallback<Void> callback) {
        mRoomsApi.join(roomId, new DefaultCallback<Void>() {
            @Override
            public void success(Void aVoid, Response response) {
                callback.onSuccess(aVoid);
            }
        });
    }

    /**
     * Leave a room.
     * @param roomId the room id
     * @param callback on success callback
     */
    public void leaveRoom(String roomId, final ApiCallback<Void> callback) {
        mRoomsApi.leave(roomId, new DefaultCallback<Void>() {
            @Override
            public void success(Void aVoid, Response response) {
                callback.onSuccess(aVoid);
            }
        });
    }

    /**
     * Ban a user from a room.
     * @param roomId the room id
     * @param userId the the user id
     * @param reason the reason for the ban
     * @param callback on success callback
     */
    public void banFromRoom(String roomId, String userId, String reason, final ApiCallback<Void> callback) {
        mRoomsApi.ban(roomId, userId, reason, new DefaultCallback<Void>() {
            @Override
            public void success(Void aVoid, Response response) {
                callback.onSuccess(aVoid);
            }
        });
    }

    /**
     * Create a new room.
     * @param name the room name
     * @param topic the room topic
     * @param visibility the room visibility
     * @param alias an optional room alias
     * @param callback the callback in case of success
     */
    public void createRoom(String name, String topic, String visibility, String alias, final ApiCallback<CreateRoomResponse> callback) {
        RoomState roomState = new RoomState();
        roomState.name = name;
        roomState.topic = topic;
        roomState.visibility = visibility;
        roomState.roomAliasName = alias;

        mRoomsApi.createRoom(roomState, new DefaultCallback<CreateRoomResponse>() {
            @Override
            public void success(CreateRoomResponse createRoomResponse, Response response) {
                callback.onSuccess(createRoomResponse);
            }
        });
    }


}
