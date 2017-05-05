/*
 * Copyright 2014 OpenMarket Ltd
 * Copyright 2017 Vector Creations Ltd
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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.rest.model.BannedUser;
import org.matrix.androidsdk.rest.model.CreateRoomParams;
import org.matrix.androidsdk.rest.model.CreateRoomResponse;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.EventContext;
import org.matrix.androidsdk.rest.model.message.Message;
import org.matrix.androidsdk.rest.model.PowerLevels;
import org.matrix.androidsdk.rest.model.ReportContentParams;
import org.matrix.androidsdk.rest.model.RoomAliasDescription;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.sync.RoomResponse;
import org.matrix.androidsdk.rest.model.TokensChunkResponse;
import org.matrix.androidsdk.rest.model.Typing;
import org.matrix.androidsdk.rest.model.User;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * The rooms REST API.
 */
public interface RoomsApi {

    /**
     * Send an event about a room.
     *
     * @param txId      the transaction Id
     * @param roomId    the room id
     * @param eventType the event type
<<<<<<< HEAD
     * @param content   the event content
     * @param callback  the asynchronous callback called with the response
=======
     * @param content the event content
>>>>>>> Migrate API calls from Retrofit 1 to Retrofit 2
     */
    @PUT("rooms/{roomId}/send/{eventType}/{txId}")
    Call<Event> send(@Path("txId") String txId, @Path("roomId") String roomId, @Path("eventType") String eventType, @Body JsonObject content);

    /**
     * Send a message to the specified room.
<<<<<<< HEAD
     *
     * @param txId     the transaction Id
     * @param txId     the transactionId
     * @param roomId   the room id
     * @param message  the message
     * @param callback the asynchronous callback called with the response
=======
     * @param txId the transactionId
     * @param roomId the room id
     * @param message the message
>>>>>>> Migrate API calls from Retrofit 1 to Retrofit 2
     */
    @PUT("rooms/{roomId}/send/m.room.message/{txId}")
    Call<Event> sendMessage(@Path("txId") String txId, @Path("roomId") String roomId, @Body Message message);

    /**
     * Set the room topic.
<<<<<<< HEAD
     *
     * @param roomId   the room id
     * @param state    state object containing the new topic in the topic field
     * @param callback the asynchronous callback called with the response
=======
     * @param roomId the room id
     * @param state state object containing the new topic in the topic field
>>>>>>> Migrate API calls from Retrofit 1 to Retrofit 2
     */
    @PUT("rooms/{roomId}/state/m.room.topic")
    Call<Void> setRoomTopic(@Path("roomId") String roomId, @Body RoomState state);

    /**
     * Set the room name.
<<<<<<< HEAD
     *
     * @param roomId   the room id
     * @param state    state object containing the new room name in the name field
     * @param callback the asynchronous callback called when finished
=======
     * @param roomId the room id
     * @param state state object containing the new room name in the name field
>>>>>>> Migrate API calls from Retrofit 1 to Retrofit 2
     */
    @PUT("rooms/{roomId}/state/m.room.name")
    Call<Void> setRoomName(@Path("roomId") String roomId, @Body RoomState state);

    /**
     * Set the canonical alias name.
<<<<<<< HEAD
     *
     * @param roomId   the room id
     * @param state    state object containing the new canonical alias in the name field
     * @param callback the asynchronous callback called when finished
=======
     * @param roomId the room id
     * @param state state object containing the new canonical alias in the name field
>>>>>>> Migrate API calls from Retrofit 1 to Retrofit 2
     */
    @PUT("rooms/{roomId}/state/m.room.canonical_alias")
    Call<Void> setCanonicalAlias(@Path("roomId") String roomId, @Body RoomState state);

    /**
     * Set the history visibility.
<<<<<<< HEAD
     *
     * @param roomId   the room id
     * @param state    state object containing the new history visibility in the name field
     * @param callback the asynchronous callback called when finished
=======
     * @param roomId the room id
     * @param state state object containing the new history visibility in the name field
>>>>>>> Migrate API calls from Retrofit 1 to Retrofit 2
     */
    @PUT("rooms/{roomId}/state/m.room.history_visibility")
    Call<Void> setHistoryVisibility(@Path("roomId") String roomId, @Body RoomState state);

    /**
     * Set the join rule for the given room.
<<<<<<< HEAD
     *
     * @param roomId   the room id where to apply the request
     * @param state    state object containing the new join rule in its {@link RoomState#join_rule} field
     * @param callback the asynchronous callback called when finished
=======
     * @param roomId the room id where to apply the request
     * @param state state object containing the new join rule in its {@link RoomState#join_rule} field
>>>>>>> Migrate API calls from Retrofit 1 to Retrofit 2
     */
    @PUT("rooms/{roomId}/state/m.room.join_rules")
    Call<Void> setJoinRules(@Path("roomId") String roomId, @Body RoomState state);

    /**
     * Set the guest access rule for the given room.
<<<<<<< HEAD
     *
     * @param roomId   the room id where to apply the request
     * @param state    state object containing the new guest access rule in its {@link RoomState#guest_access} field
     * @param callback the asynchronous callback called when finished
=======
     * @param roomId the room id where to apply the request
     * @param state state object containing the new guest access rule in its {@link RoomState#guest_access} field
>>>>>>> Migrate API calls from Retrofit 1 to Retrofit 2
     */
    @PUT("rooms/{roomId}/state/m.room.guest_access")
    Call<Void> setGuestAccess(@Path("roomId") String roomId, @Body RoomState state);

    /**
     * Update the power levels
     *
     * @param roomId      the room id
     * @param powerLevels the new power levels
<<<<<<< HEAD
     * @param callback    the asynchronous callback called when finished
=======
>>>>>>> Migrate API calls from Retrofit 1 to Retrofit 2
     */
    @PUT("rooms/{roomId}/state/m.room.power_levels")
    Call<Void> setPowerLevels(@Path("roomId") String roomId, @Body PowerLevels powerLevels);

    /**
     * Send a generic state events
<<<<<<< HEAD
     *
     * @param roomId         the room id.
     * @param stateEventType the state event type
     * @param params         the request parameters
     * @param callback       the callback
     */
    @PUT("/rooms/{roomId}/state/{state_event_type}")
    void sendStateEvent(@Path("roomId") String roomId, @Path("state_event_type") String stateEventType, @Body Map<String, Object> params, Callback<Void> callback);

    /**
     * Send a generic state events
     *
     * @param roomId         the room id.
     * @param stateEventType the state event type
     * @param stateKey       the state keys
     * @param params         the request parameters
     * @param callback       the callback
     */
    @PUT("/rooms/{roomId}/state/{state_event_type}/{stateKey}")
    void sendStateEvent(@Path("roomId") String roomId, @Path("state_event_type") String stateEventType, @Path("stateKey") String stateKey, @Body Map<String, Object> params, Callback<Void> callback);

    /**
     * Looks up the contents of a state event in a room
     *
     * @param roomId    the room id
     * @param eventType the event type
     * @param callback  the callback
     */
    @GET("rooms/{roomId}/state/{eventType}")
    void getStateEvent(@Path("roomId") String roomId, @Path("eventType") String eventType, Callback<JsonElement> callback);

    /**
     * Looks up the contents of a state event in a room
     *
     * @param roomId    the room id
     * @param eventType the event type
     * @param stateKey  the key of the state to look up
     * @param callback  the callback
     */
    @GET("rooms/{roomId}/state/{eventType}/{stateKey}")
    void getStateEvent(@Path("roomId") String roomId, @Path("eventType") String eventType, @Path("stateKey") String stateKey, Callback<JsonElement> callback);

    /**
     * Invite a user to the given room.
     *
     * @param roomId   the room id
     * @param user     a user object that just needs a user id
     * @param callback the asynchronous callback called when finished
=======
     * @param roomId the room id.
     * @param stateEvent the state event type
     * @param params the request parameters
     */
    @PUT("rooms/{roomId}/state/{state_event}")
    Call<Void> sendStateEvent(@Path("roomId") String roomId, @Path("state_event") String stateEvent, @Body Map<String, Object> params);

    /**
     * Invite a user to the given room.
     * @param roomId the room id
     * @param user a user object that just needs a user id
>>>>>>> Migrate API calls from Retrofit 1 to Retrofit 2
     */
    @POST("rooms/{roomId}/invite")
    Call<Void> invite(@Path("roomId") String roomId, @Body User user);

    /**
     * Trigger an invitation with a parameters set.
<<<<<<< HEAD
     *
     * @param roomId   the room id
     * @param params   the parameters
     * @param callback the asynchronous callback called when finished
=======
     * @param roomId the room id
     * @param params the parameters
>>>>>>> Migrate API calls from Retrofit 1 to Retrofit 2
     */
    @POST("rooms/{roomId}/invite")
    Call<Void> invite(@Path("roomId") String roomId, @Body HashMap<String, String> params);

    /**
     * Join the given room.
<<<<<<< HEAD
     *
     * @param roomId   the room id
     * @param content  the request body
     * @param callback the asynchronous callback called when finished
=======
     * @param roomId the room id
>>>>>>> Migrate API calls from Retrofit 1 to Retrofit 2
     */
    @POST("rooms/{roomId}/join")
    Call<Void> join(@Path("roomId") String roomId, @Body JsonObject content);

    /**
     * Join the room with a room id or an alias.
     *
     * @param roomAliasOrId a room alias (or room id)
<<<<<<< HEAD
     * @param params        the extra join param
     * @param callback      the asynchronous callback called with the response
=======
     * @param params the extra join param
>>>>>>> Migrate API calls from Retrofit 1 to Retrofit 2
     */
    @POST("join/{roomAliasOrId}")
    Call<RoomResponse> joinRoomByAliasOrId(@Path("roomAliasOrId") String roomAliasOrId, @Body HashMap<String, Object> params);

    /**
     * Leave the given room.
<<<<<<< HEAD
     *
     * @param roomId   the room id
     * @param content  the request body
     * @param callback the asynchronous callback called when finished
=======
     * @param roomId the room id
>>>>>>> Migrate API calls from Retrofit 1 to Retrofit 2
     */
    @POST("rooms/{roomId}/leave")
    Call<Void> leave(@Path("roomId") String roomId, @Body JsonObject content);

    /**
     * Forget the given room.
     *
     * @param roomId   the room id
     * @param content  the request body
     * @param callback the asynchronous callback called when finished
     */
    @POST("/rooms/{roomId}/forget")
    void forget(@Path("roomId") String roomId, @Body JsonObject content, Callback<Void> callback);

    /**
     * Ban a user from the given room.
<<<<<<< HEAD
     *
     * @param roomId   the room id
     * @param user     the banned user object (userId and reason for ban)
     * @param callback the asynchronous callback called when finished
=======
     * @param roomId the room id
     * @param user the banned user object (userId and reason for ban)
>>>>>>> Migrate API calls from Retrofit 1 to Retrofit 2
     */
    @POST("rooms/{roomId}/ban")
    Call<Void> ban(@Path("roomId") String roomId, @Body BannedUser user);

    /**
     * unban a user from the given room.
<<<<<<< HEAD
     *
     * @param roomId   the room id
     * @param user     the banned user object (userId and reason for unban)
     * @param callback the asynchronous callback called when finished
=======
     * @param roomId the room id
     * @param user the banned user object (userId and reason for unban)
>>>>>>> Migrate API calls from Retrofit 1 to Retrofit 2
     */
    @POST("rooms/{roomId}/unban")
    Call<Void> unban(@Path("roomId") String roomId, @Body BannedUser user);

    /**
     * Change the membership state for a user in a room.
<<<<<<< HEAD
     *
     * @param roomId   the room id
     * @param userId   the user id
     * @param member   object containing the membership field to set
     * @param callback the asynchronous callback called when finished
=======
     * @param roomId the room id
     * @param userId the user id
     * @param member object containing the membership field to set
>>>>>>> Migrate API calls from Retrofit 1 to Retrofit 2
     */
    @PUT("rooms/{roomId}/state/m.room.member/{userId}")
    Call<Void> updateRoomMember(@Path("roomId") String roomId, @Path("userId") String userId, @Body RoomMember member);

    /**
     * Update the typing notification
<<<<<<< HEAD
     *
     * @param roomId   the room id
     * @param userId   the user id
     * @param typing   the typing notification
     * @param callback the asynchronous callback called when finished
=======
     * @param roomId the room id
     * @param userId the user id
     * @param typing the typing notification
>>>>>>> Migrate API calls from Retrofit 1 to Retrofit 2
     */
    @PUT("rooms/{roomId}/typing/{userId}")
    Call<Void> setTypingNotification(@Path("roomId") String roomId, @Path("userId") String userId, @Body Typing typing);

    /**
     * Create a room.
<<<<<<< HEAD
     *
     * @param createRoomRequest the creation room request
     * @param callback          the asynchronous callback called with the response
     */
    @POST("/createRoom")
    void createRoom(@Body CreateRoomParams createRoomRequest, Callback<CreateRoomResponse> callback);

    /**
     * Get a list of messages starting from a reference..
     *
     * @param roomId   the room id
     * @param dir      The direction to return messages from.
     * @param from     the token identifying where to start
     * @param limit    the maximum number of messages to retrieve
     * @param callback the asynchronous callback called with the response
     */
    @GET("/rooms/{roomId}/messages")
    void getRoomMessagesFrom(@Path("roomId") String roomId, @Query("dir") String dir,
                             @Query("from") String from, @Query("limit") int limit,
                             Callback<TokensChunkResponse<Event>> callback);

    /**
     * Get the initial information concerning a specific room.
     *
     * @param roomId   the room id
     * @param limit    the maximum number of messages to retrieve
     * @param callback the asynchronous callback called with the response
=======
     * @param roomState object containing the initial room state (name, topic, visibility, ...)
     */
    @POST("createRoom")
    Call<CreateRoomResponse> createRoom(@Body RoomState roomState);

    /**
     * Create a room.
     * @param params parameters to create the room
     */
    @POST("createRoom")
    Call<CreateRoomResponse> createRoom(@Body Map<String, Object> params);

    /**
     * Get a list of messages starting from a reference..
     * @param roomId the room id
     * @param dir The direction to return messages from.
     * @param from the token identifying where to start
     * @param limit the maximum number of messages to retrieve
     */
    @GET("rooms/{roomId}/messages")
    Call<TokensChunkResponse<Event>> getRoomMessagesFrom(@Path("roomId") String roomId, @Query("dir") String dir,
                                                         @Query("from") String from, @Query("limit") int limit);

    /**
     * Get the initial information concerning a specific room.
     * @param roomId the room id
     * @param limit the maximum number of messages to retrieve
>>>>>>> Migrate API calls from Retrofit 1 to Retrofit 2
     */
    @GET("rooms/{roomId}/initialSync")
    Call<RoomResponse> initialSync(@Path("roomId") String roomId, @Query("limit") int limit);

    /**
     * Get the context surrounding an event.
<<<<<<< HEAD
     *
     * @param roomId   the room id
     * @param eventId  the event Id
     * @param limit    the maximum number of messages to retrieve
     * @param callback the asynchronous callback called with the response
=======
     * @param roomId the room id
     * @param eventId the event Id
     * @param limit the maximum number of messages to retrieve
>>>>>>> Migrate API calls from Retrofit 1 to Retrofit 2
     */
    @GET("rooms/{roomId}/context/{eventId}")
    Call<EventContext> getContextOfEvent(@Path("roomId") String roomId, @Path("eventId") String eventId, @Query("limit") int limit);

    /**
     * Retrieve an event from its room id / events id
     *
     * @param roomId   the room id
     * @param eventId  the event Id
     * @param callback the asynchronous callback called with the response
     */
    @GET("/rooms/{roomId}/event/{eventId}")
    void getEvent(@Path("roomId") String roomId, @Path("eventId") String eventId, Callback<Event> callback);

    /**
     * Retrieve an event from its event id
     *
     * @param eventId  the event Id
     * @param callback the asynchronous callback called with the response
     */
    @GET("/events/{eventId}")
    void getEvent(@Path("eventId") String eventId, Callback<Event> callback);

    /**
     * Redact an event from the room>.
<<<<<<< HEAD
     *
     * @param roomId   the room id
     * @param eventId  the event id of the event to redact
     * @param reason   the reason
     * @param callback the asynchronous callback called with the response
=======
     * @param roomId the room id
     * @param eventId the event id of the event to redact
>>>>>>> Migrate API calls from Retrofit 1 to Retrofit 2
     */
    @POST("rooms/{roomId}/redact/{eventId}")
    Call<Event> redactEvent(@Path("roomId") String roomId, @Path("eventId") String eventId, @Body JsonObject reason);

    /**
     * Report an event content.
<<<<<<< HEAD
     *
     * @param roomId   the room id
     * @param eventId  the event id of the event to redact
     * @param param    the request parameters
     * @param callback the asynchronous callback called with the response
=======
     * @param roomId the room id
     * @param eventId the event id of the event to redact
>>>>>>> Migrate API calls from Retrofit 1 to Retrofit 2
     */
    @POST("rooms/{roomId}/report/{eventId}")
    Call<Void> reportEvent(@Path("roomId") String roomId, @Path("eventId") String eventId, @Body ReportContentParams param);

    /**
     * Set the room avatar url.
<<<<<<< HEAD
     *
     * @param roomId   the room id
     * @param params   the put params.
     * @param callback the asynchronous callback called when finished
=======
     * @param roomId the room id
     * @param params the put params.
>>>>>>> Migrate API calls from Retrofit 1 to Retrofit 2
     */
    @PUT("rooms/{roomId}/state/m.room.avatar")
    Call<Void> setRoomAvatarUrl(@Path("roomId") String roomId, @Body HashMap<String, String> params);

    /**
     * Send a read receipt.
<<<<<<< HEAD
     *
     * @param roomId   the room id
     * @param EventId  the latest eventId
     * @param content  the event content
     * @param callback the asynchronous callback called with the response
     */
    @POST("/rooms/{roomId}/receipt/m.read/{eventId}")
    void sendReadReceipt(@Path("roomId") String roomId, @Path("eventId") String EventId, @Body JsonObject content,
                         Callback<Void> callback);

    /**
     * Send read markers.
     *
     * @param roomId   the room id
     * @param markers  the read markers
     * @param callback the asynchronous callback called with the response
     */
    @POST("/rooms/{roomId}/read_markers")
    void sendReadMarker(@Path("roomId") String roomId, @Body Map<String, String> markers, Callback<Void> callback);

    /**
     * Add a tag to a room
     *
     * @param userId   the userId
     * @param roomId   the room id
     * @param tag      the new room tag
     * @param content  the event content
     * @param callback the asynchronous callback called with the response
=======
     * @param roomId the room id
     * @param EventId the latest eventId
     * @param content the event content
     */
    @POST("rooms/{roomId}/receipt/m.read/{eventId}")
    Call<Void> sendReadReceipt(@Path("roomId") String roomId, @Path("eventId") String EventId, @Body JsonObject content);
    /**
     * Add a tag to a room
     * @param userId the userId
     * @param roomId the room id
     * @param tag the new room tag
     * @param content the event content
>>>>>>> Migrate API calls from Retrofit 1 to Retrofit 2
     */
    @PUT("user/{userId}/rooms/{roomId}/tags/{tag}")
    Call<Void> addTag(@Path("userId") String userId, @Path("roomId") String roomId, @Path("tag") String tag, @Body HashMap<String, Object> content);

    /**
     * Remove a tag to a room
<<<<<<< HEAD
     *
     * @param userId   the userId
     * @param roomId   the room id
     * @param tag      the new room tag
     * @param callback the asynchronous callback called with the response
=======
     * @param userId the userId
     * @param roomId the room id
     * @param tag the new room tag
>>>>>>> Migrate API calls from Retrofit 1 to Retrofit 2
     */
    @DELETE("user/{userId}/rooms/{roomId}/tags/{tag}")
    Call<Void> removeTag(@Path("userId") String userId, @Path("roomId") String roomId, @Path("tag") String tag);

    /**
     * Update a dedicated account data field
     *
     * @param userId   the userId
     * @param roomId   the room id
     * @param subPath  the url sub path
     * @param content  the event content
     * @param callback the asynchronous callback called with the response
     */
    @PUT("/user/{userId}/rooms/{roomId}/account_data/{tag}")
    void updateAccountData(@Path("userId") String userId, @Path("roomId") String roomId, @Path("tag") String subPath, @Body HashMap<String, Object> content,
                           Callback<Void> callback);

    /**
     * Get the room ID associated to the room alias.
     *
     * @param roomAlias the room alias.
<<<<<<< HEAD
     * @param callback  the asynchronous callback called with the response
=======
>>>>>>> Migrate API calls from Retrofit 1 to Retrofit 2
     */
    @GET("directory/room/{roomAlias}")
    Call<RoomAliasDescription> getRoomIdByAlias(@Path("roomAlias") String roomAlias);

    /**
     * Associate a room alias with a room ID.
     *
     * @param roomAlias   the room alias.
     * @param description the alias description containing the room ID
<<<<<<< HEAD
     * @param callback    the asynchronous callback called with the response
=======
>>>>>>> Migrate API calls from Retrofit 1 to Retrofit 2
     */
    @PUT("directory/room/{roomAlias}")
    Call<Void> setRoomIdByAlias(@Path("roomAlias") String roomAlias, @Body RoomAliasDescription description);

    /**
     * Get the room ID corresponding to this room alias.
     *
     * @param roomAlias the room alias.
<<<<<<< HEAD
     * @param callback  the asynchronous callback called with the response
=======
>>>>>>> Migrate API calls from Retrofit 1 to Retrofit 2
     */
    @DELETE("directory/room/{roomAlias}")
    Call<Void> removeRoomAlias(@Path("roomAlias") String roomAlias);

    /**
     * Set the visibility of the given room in the list directory. If the visibility is set to public, the room
     * name is listed among the directory list.
<<<<<<< HEAD
     *
     * @param roomId   the room id where to apply the request
     * @param state    state object containing the new guest access rule in its {@link RoomState#visibility} field
     * @param callback the asynchronous callback called when finished
=======
     * @param roomId the room id where to apply the request
     * @param state state object containing the new guest access rule in its {@link RoomState#visibility} field
>>>>>>> Migrate API calls from Retrofit 1 to Retrofit 2
     */
    @PUT("directory/list/room/{roomId}")
    Call<Void> setRoomDirectoryVisibility(@Path("roomId") String roomId, @Body RoomState state);

    /**
     * Get the visibility of the given room in the list directory.
<<<<<<< HEAD
     *
     * @param roomId   the room id where to apply the request
     * @param callback the asynchronous callback response containing the {@link RoomState#visibility} value
=======
     * @param roomId the room id where to apply the request
>>>>>>> Migrate API calls from Retrofit 1 to Retrofit 2
     */
    @GET("directory/list/room/{roomId}")
    Call<RoomState> getRoomDirectoryVisibility(@Path("roomId") String roomId);
}
