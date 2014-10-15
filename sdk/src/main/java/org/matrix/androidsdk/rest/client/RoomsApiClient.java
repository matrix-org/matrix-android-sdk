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
package org.matrix.androidsdk.rest.client;

import com.google.gson.JsonObject;

import org.matrix.androidsdk.MXApiClient;
import org.matrix.androidsdk.api.RoomsApi;
import org.matrix.androidsdk.api.response.CreateRoomResponse;
import org.matrix.androidsdk.api.response.Event;
import org.matrix.androidsdk.api.response.Message;
import org.matrix.androidsdk.api.response.RoomMember;
import org.matrix.androidsdk.api.response.TokensChunkResponse;
import org.matrix.androidsdk.api.response.login.Credentials;
import org.matrix.androidsdk.data.RoomState;

import java.util.List;

import retrofit.RestAdapter;
import retrofit.client.Response;

/**
 * Class used to make requests to the rooms API.
 */
public class RoomsApiClient extends MXApiClient {

    RoomsApi mApi;

    /**
     * Public constructor.
     * @param credentials the user's credentials
     */
    public RoomsApiClient(Credentials credentials) {
        super(credentials);
    }

    @Override
    protected void initApi(RestAdapter restAdapter) {
        mApi = restAdapter.create(RoomsApi.class);
    }

    /**
     * Protected setter for injection by unit tests.
     * @param api the api object
     */
    protected void setApi(RoomsApi api) {
        mApi = api;
    }

    /**
     * Send a message to a room.
     * @param roomId the the room id
     * @param message the message
     * @param callback the callback containing the created event if successful
     */
    public void sendMessage(String roomId, Message message, final ApiCallback<Event> callback) {
        mApi.sendMessage(roomId, message, new DefaultCallback<Event>() {
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
        mApi.send(roomId, eventType, content, new DefaultCallback<Event>() {
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
        mApi.messages(roomId, MESSAGES_PAGINATION_LIMIT, new DefaultCallback<TokensChunkResponse<Message>>() {
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
        mApi.messagesFrom(roomId, fromToken, MESSAGES_PAGINATION_LIMIT, new DefaultCallback<TokensChunkResponse<Message>>() {
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
        mApi.messagesFrom(roomId, toToken, MESSAGES_PAGINATION_LIMIT, new DefaultCallback<TokensChunkResponse<Message>>() {
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
        mApi.members(roomId, new DefaultCallback<TokensChunkResponse<RoomMember>>() {
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
        mApi.state(roomId, new DefaultCallback<List<Event>>() {
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
        mApi.invite(roomId, userId, new DefaultCallback<Void>() {
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
        mApi.join(roomId, new DefaultCallback<Void>() {
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
        mApi.leave(roomId, new DefaultCallback<Void>() {
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
        mApi.ban(roomId, userId, reason, new DefaultCallback<Void>() {
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

        mApi.createRoom(roomState, new DefaultCallback<CreateRoomResponse>() {
            @Override
            public void success(CreateRoomResponse createRoomResponse, Response response) {
                callback.onSuccess(createRoomResponse);
            }
        });
    }
}
