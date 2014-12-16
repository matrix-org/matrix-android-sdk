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

import org.matrix.androidsdk.RestClient;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.rest.api.RoomsApi;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.RestAdapterCallback;
import org.matrix.androidsdk.rest.model.BannedUser;
import org.matrix.androidsdk.rest.model.CreateRoomResponse;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.Message;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.RoomResponse;
import org.matrix.androidsdk.rest.model.TokensChunkResponse;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.rest.model.login.Credentials;

import java.util.List;

import retrofit.RestAdapter;
import retrofit.client.Response;

/**
 * Class used to make requests to the rooms API.
 */
public class RoomsRestClient extends RestClient {

    protected static final int MESSAGES_PAGINATION_LIMIT = 15;

    private RoomsApi mApi;

    /**
     * {@inheritDoc}
     */
    public RoomsRestClient(Credentials credentials) {
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
     * @param roomId the room id
     * @param message the message
     * @param callback the callback containing the created event if successful
     */
    public void sendMessage(String roomId, Message message, ApiCallback<Event> callback) {
        mApi.sendMessage(roomId, message, new RestAdapterCallback<Event>(callback));
    }

    /**
     * Send a message to a room.
     * @param roomId the room id
     * @param eventType the type of event
     * @param content the event content
     * @param callback the callback containing the created event if successful
     */
    public void sendEvent(String roomId, String eventType, JsonObject content, ApiCallback<Event> callback) {
        mApi.send(roomId, eventType, content, new RestAdapterCallback<Event>(callback));
    }

    /**
     * Get the last messages for the given room.
     * @param roomId the room id
     * @param callback the callback called with the response. Messages will be returned in reverse order.
     */
    public void getLatestRoomMessages(String roomId, ApiCallback<TokensChunkResponse<Event>> callback) {
        mApi.messages(roomId, "b", MESSAGES_PAGINATION_LIMIT, new RestAdapterCallback<TokensChunkResponse<Event>>(callback));
    }

    /**
     * Get messages for the given room starting from the given token.
     * @param roomId the room id
     * @param fromToken the token identifying the message to start from
     * @param callback the callback called with the response. Messages will be returned in reverse order.
     */
    public void getEarlierMessages(String roomId, String fromToken, ApiCallback<TokensChunkResponse<Event>> callback) {
        mApi.messagesFrom(roomId, "b", fromToken, MESSAGES_PAGINATION_LIMIT, new RestAdapterCallback<TokensChunkResponse<Event>>(callback));
    }

    /**
     * Get the list of members for the given room.
     * @param roomId the room id
     * @param callback the async callback
     */
    public void getRoomMembers(String roomId, final ApiCallback<List<RoomMember>> callback) {
        mApi.members(roomId, new RestAdapterCallback<TokensChunkResponse<RoomMember>>(callback) {
            @Override
            public void success(TokensChunkResponse<RoomMember> messageTokensChunkResponse, Response response) {
                callback.onSuccess(messageTokensChunkResponse.chunk);
            }
        });
    }

    /**
     * Get the list of members for the given room.
     * @param roomId the room id
     * @param callback the async callback
     */
    public void getRoomState(String roomId, ApiCallback<List<Event>> callback) {
        mApi.state(roomId, new RestAdapterCallback<List<Event>>(callback));
    }

    /**
     * Invite a user to a room.
     * @param roomId the room id
     * @param userId the user id
     * @param callback the async callback
     */
    public void inviteToRoom(String roomId, String userId, ApiCallback<Void> callback) {
        User user = new User();
        user.userId = userId;
        mApi.invite(roomId, user, new RestAdapterCallback<Void>(callback));
    }

    /**
     * Join a room.
     * @param roomId the room id
     * @param callback the async callback
     */
    public void joinRoom(String roomId, ApiCallback<Void> callback) {
        mApi.join(roomId, new JsonObject(), new RestAdapterCallback<Void>(callback));
    }

    /**
     * Leave a room.
     * @param roomId the room id
     * @param callback the async callback
     */
    public void leaveRoom(String roomId, ApiCallback<Void> callback) {
        mApi.leave(roomId, new JsonObject(), new RestAdapterCallback<Void>(callback));
    }

    /**
     * Kick a user from a room.
     * @param roomId the room id
     * @param userId the user id
     * @param callback the async callback
     */
    public void kickFromRoom(String roomId, String userId, ApiCallback<Void> callback) {
        // Kicking is done by posting that the user is now in a "leave" state
        RoomMember member = new RoomMember();
        member.membership = RoomMember.MEMBERSHIP_LEAVE;

        mApi.roomMember(roomId, userId, member, new RestAdapterCallback<Void>(callback));
    }

    /**
     * Ban a user from a room.
     * @param roomId the room id
     * @param user the banned user object (userId and reason for ban)
     * @param callback the async callback
     */
    public void banFromRoom(String roomId, BannedUser user, ApiCallback<Void> callback) {
        mApi.ban(roomId, user, new RestAdapterCallback<Void>(callback));
    }

    /**
     * Create a new room.
     * @param name the room name
     * @param topic the room topic
     * @param visibility the room visibility
     * @param alias an optional room alias
     * @param callback the async callback
     */
    public void createRoom(String name, String topic, String visibility, String alias, ApiCallback<CreateRoomResponse> callback) {
        RoomState roomState = new RoomState();
        roomState.name = name;
        roomState.topic = topic;
        roomState.visibility = visibility;
        roomState.roomAliasName = alias;

        mApi.createRoom(roomState, new RestAdapterCallback<CreateRoomResponse>(callback));
    }

    /**
     * Perform an initial sync on the room
     * @param roomId the room id
     * @param callback the async callback
     */
    public void initialSync(String roomId, ApiCallback<RoomResponse> callback) {
        mApi.initialSync(roomId, MESSAGES_PAGINATION_LIMIT, new RestAdapterCallback<RoomResponse>(callback));
    }

    /**
     * Update the room name.
     * @param roomId the room id
     * @param name the room name
     * @param callback the async callback
     */
    public void updateName(String roomId, String name, ApiCallback<Void> callback) {
        RoomState roomState = new RoomState();
        roomState.name = name;

        mApi.roomName(roomId, roomState, new RestAdapterCallback<Void>(callback));
    }

    /**
     * Update the room topic.
     * @param roomId the room id
     * @param topic the room topic
     * @param callback the async callback
     */
    public void updateTopic(String roomId, String topic, ApiCallback<Void> callback) {
        RoomState roomState = new RoomState();
        roomState.topic = topic;

        mApi.roomTopic(roomId, roomState, new RestAdapterCallback<Void>(callback));
    }
}
