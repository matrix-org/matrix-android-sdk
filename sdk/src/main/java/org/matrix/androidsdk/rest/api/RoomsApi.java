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
package org.matrix.androidsdk.rest.api;

import com.google.gson.JsonObject;

import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.rest.model.BannedUser;
import org.matrix.androidsdk.rest.model.CreateRoomResponse;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.Message;
import org.matrix.androidsdk.rest.model.MessageFeedback;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.RoomResponse;
import org.matrix.androidsdk.rest.model.TokensChunkResponse;
import org.matrix.androidsdk.rest.model.User;

import java.util.List;

import retrofit.Callback;
import retrofit.http.Body;
import retrofit.http.Field;
import retrofit.http.GET;
import retrofit.http.POST;
import retrofit.http.PUT;
import retrofit.http.Path;
import retrofit.http.Query;

/**
 * The rooms REST API.
 */
public interface RoomsApi {

    /**
     * Send an event about a room.
     * @param roomId the room id
     * @param eventType the event type
     * @param content the event content
     * @param callback the asynchronous callback called with the response
     */
    @POST("/rooms/{roomId}/send/{eventType}")
    public void send(@Path("roomId") String roomId, @Path("eventType") String eventType, @Body JsonObject content,
                     Callback<Event> callback);

    /**
     * Set state information for a room. The state key can be omitted.
     * @param roomId the room id
     * @param eventType the event type
     * @param stateKey the state key
     * @param state the state values
     * @param callback the asynchronous callback called when finished
     */
    @PUT("/rooms/{roomId}/state/{eventType}/{stateKey}")
    public void state(@Path("roomId") String roomId, @Path("eventType") String eventType, @Path("stateKey") String stateKey,
                      @Body RoomState state, Callback<Void> callback);

    /**
     * Send a message for the specified room.
     * @param roomId the room id
     * @param message the message
     * @param callback the asynchronous callback called with the response
     */
    @POST("/rooms/{roomId}/send/m.room.message")
    public void sendMessage(@Path("roomId") String roomId, @Body Message message, Callback<Event> callback);

    /**
     * Set the room topic.
     * @param roomId the room id
     * @param state state object containing the new topic in the topic field
     * @param callback the asynchronous callback called with the response
     */
    @PUT("/rooms/{roomId}/state/m.room.topic")
    public void roomTopic(@Path("roomId") String roomId, @Body RoomState state, Callback<Void> callback);

    /**
     * Get the room topic.
     * @param roomId the room id
     * @param callback the asynchronous callback called with the response
     */
    @GET("/rooms/{roomId}/state/m.room.topic")
    public void roomTopic(@Path("roomId") String roomId, Callback<RoomState> callback);

    /**
     * Set the room name.
     * @param roomId the room id
     * @param state state object containing the new room name in the name field
     * @param callback the asynchronous callback called when finished
     */
    @PUT("/rooms/{roomId}/state/m.room.name")
    public void roomName(@Path("roomId") String roomId, @Body RoomState state, Callback<Void> callback);

    /**
     * Get the room name.
     * @param roomId the room id
     * @param callback the asynchronous callback called with the response
     */
    @GET("/rooms/{roomId}/state/m.room.name")
    public void roomName(@Path("roomId") String roomId, Callback<RoomState> callback);

    /**
     * Send feedback for an event.
     * @param roomId the room id
     * @param feedback the feedback
     * @param callback the asynchronous callback called with the response
     */
    @POST("/rooms/{roomId}/send/m.room.message.feedback")
    public void sendFeedback(@Path("roomId") String roomId, @Body MessageFeedback feedback, Callback<Event> callback);

    /**
     * Invite a user to the given room.
     * @param roomId the room id
     * @param user a user object that just needs a user id
     * @param callback the asynchronous callback called when finished
     */
    @POST("/rooms/{roomId}/invite")
    public void invite(@Path("roomId") String roomId, @Body User user, Callback<Void> callback);

    /**
     * Join the given room.
     * @param roomId the room id
     * @param callback the asynchronous callback called when finished
     */
    @POST("/rooms/{roomId}/join")
    public void join(@Path("roomId") String roomId, @Body JsonObject content, Callback<Void> callback);

    /**
     * Leave the given room.
     * @param roomId the room id
     * @param callback the asynchronous callback called when finished
     */
    @POST("/rooms/{roomId}/leave")
    public void leave(@Path("roomId") String roomId, @Body JsonObject content, Callback<Void> callback);

    /**
     * Ban a user from the given room.
     * @param roomId the room id
     * @param user the banned user object (userId and reason for ban)
     * @param callback the asynchronous callback called when finished
     */
    @POST("/rooms/{roomId}/ban")
    public void ban(@Path("roomId") String roomId, @Body BannedUser user, Callback<Void> callback);

    /**
     * Change the membership state for a user in a room.
     * @param roomId the room id
     * @param userId the user id
     * @param member object containing the membership field to set
     * @param callback the asynchronous callback called when finished
     */
    @PUT("/rooms/{roomId}/state/m.room.member/{userId}")
    public void roomMember(@Path("roomId") String roomId, @Path("userId") String userId, @Body RoomMember member, Callback<Void> callback);

    /**
     * Get the membership state of a user in a room.
     * @param roomId the room id
     * @param userId the user id
     * @param callback the asynchronous callback called with the response
     */
    @GET("/rooms/{roomId}/state/m.room.member/{userId}")
    public void roomMember(@Path("roomId") String roomId, @Path("userId") String userId, Callback<RoomMember> callback);

    /**
     * Join the room with the given alias.
     * @param roomAliasOrId a room alias (or room id)
     * @param callback the asynchronous callback called with the response
     */
    @POST("/join/{roomAliasOrId}")
    public void joinRoomByAlias(@Path("roomAliasOrId") String roomAliasOrId, Callback<RoomResponse> callback);

    /**
     * Create a room.
     * @param roomState object containing the initial room state (name, topic, visibility, ...)
     * @param callback the asynchronous callback called with the response
     */
    @POST("/createRoom")
    public void createRoom(@Body RoomState roomState, Callback<CreateRoomResponse> callback);

    /**
     * Get a list of the last messages for this room.
     * @param roomId the room id
     * @param dir The direction to return messages from.
     * @param limit the maximum number of messages to retrieve
     * @param callback the asynchronous callback called with the response
     */
    @GET("/rooms/{roomId}/messages")
    public void messages(@Path("roomId") String roomId, @Query("dir") String dir,
                         @Query("limit") int limit, Callback<TokensChunkResponse<Event>> callback);

    /**
     * Get a list of messages starting from a certain point.
     * @param roomId the room id
     * @param dir The direction to return messages from.
     * @param from the token identifying where to start
     * @param limit the maximum number of messages to retrieve
     * @param callback the asynchronous callback called with the response
     */
    @GET("/rooms/{roomId}/messages")
    public void messagesFrom(@Path("roomId") String roomId, @Query("dir") String dir,
                             @Query("from") String from, @Query("limit") int limit,
                             Callback<TokensChunkResponse<Event>> callback);

    /**
     * Get a list of members for this room.
     * @param roomId the room id
     * @param callback the asynchronous callback called with the response
     */
    @GET("/rooms/{roomId}/members")
    public void members(@Path("roomId") String roomId, Callback<TokensChunkResponse<RoomMember>> callback);

    /**
     * Get the current state events for the room.
     * @param roomId the room id
     * @param callback the asynchronous callback called with the response
     */
    @GET("/rooms/{roomId}/state")
    public void state(@Path("roomId") String roomId, Callback<List<Event>> callback);

    /**
     * Get the initial information concerning a specific room.
     * @param roomId the room id
     * @param limit the maximum number of messages to retrieve
     * @param callback the asynchronous callback called with the response
     */
    @GET("/rooms/{roomId}/initialSync")
    public void initialSync(@Path("roomId") String roomId, @Query("limit") int limit, Callback<RoomResponse> callback);
}
