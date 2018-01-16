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
import org.matrix.androidsdk.rest.model.Message;
import org.matrix.androidsdk.rest.model.PowerLevels;
import org.matrix.androidsdk.rest.model.ReportContentParams;
import org.matrix.androidsdk.rest.model.RoomAliasDescription;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.RoomResponse;
import org.matrix.androidsdk.rest.model.TokensChunkResponse;
import org.matrix.androidsdk.rest.model.Typing;
import org.matrix.androidsdk.rest.model.User;

import java.util.HashMap;
import java.util.Map;

import retrofit.Callback;
import retrofit.http.Body;
import retrofit.http.DELETE;
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
     *
     * @param txId      the transaction Id
     * @param roomId    the room id
     * @param eventType the event type
     * @param content   the event content
     * @param callback  the asynchronous callback called with the response
     */
    @PUT("/rooms/{roomId}/send/{eventType}/{txId}")
    void send(@Path("txId") String txId, @Path("roomId") String roomId, @Path("eventType") String eventType, @Body JsonObject content, Callback<Event> callback);

    /**
     * Send a message to the specified room.
     *
     * @param txId     the transaction Id
     * @param txId     the transactionId
     * @param roomId   the room id
     * @param message  the message
     * @param callback the asynchronous callback called with the response
     */
    @PUT("/rooms/{roomId}/send/m.room.message/{txId}")
    void sendMessage(@Path("txId") String txId, @Path("roomId") String roomId, @Body Message message, Callback<Event> callback);

    /**
     * Set the room topic.
     *
     * @param roomId   the room id
     * @param state    state object containing the new topic in the topic field
     * @param callback the asynchronous callback called with the response
     */
    @PUT("/rooms/{roomId}/state/m.room.topic")
    void setRoomTopic(@Path("roomId") String roomId, @Body RoomState state, Callback<Void> callback);

    /**
     * Set the room name.
     *
     * @param roomId   the room id
     * @param state    state object containing the new room name in the name field
     * @param callback the asynchronous callback called when finished
     */
    @PUT("/rooms/{roomId}/state/m.room.name")
    void setRoomName(@Path("roomId") String roomId, @Body RoomState state, Callback<Void> callback);

    /**
     * Set the canonical alias name.
     *
     * @param roomId   the room id
     * @param state    state object containing the new canonical alias in the name field
     * @param callback the asynchronous callback called when finished
     */
    @PUT("/rooms/{roomId}/state/m.room.canonical_alias")
    void setCanonicalAlias(@Path("roomId") String roomId, @Body RoomState state, Callback<Void> callback);

    /**
     * Set the history visibility.
     *
     * @param roomId   the room id
     * @param state    state object containing the new history visibility in the name field
     * @param callback the asynchronous callback called when finished
     */
    @PUT("/rooms/{roomId}/state/m.room.history_visibility")
    void setHistoryVisibility(@Path("roomId") String roomId, @Body RoomState state, Callback<Void> callback);

    /**
     * Set the join rule for the given room.
     *
     * @param roomId   the room id where to apply the request
     * @param state    state object containing the new join rule in its {@link RoomState#join_rule} field
     * @param callback the asynchronous callback called when finished
     */
    @PUT("/rooms/{roomId}/state/m.room.join_rules")
    void setJoinRules(@Path("roomId") String roomId, @Body RoomState state, Callback<Void> callback);

    /**
     * Set the guest access rule for the given room.
     *
     * @param roomId   the room id where to apply the request
     * @param state    state object containing the new guest access rule in its {@link RoomState#guest_access} field
     * @param callback the asynchronous callback called when finished
     */
    @PUT("/rooms/{roomId}/state/m.room.guest_access")
    void setGuestAccess(@Path("roomId") String roomId, @Body RoomState state, Callback<Void> callback);

    /**
     * Update the power levels
     *
     * @param roomId      the room id
     * @param powerLevels the new power levels
     * @param callback    the asynchronous callback called when finished
     */
    @PUT("/rooms/{roomId}/state/m.room.power_levels")
    void setPowerLevels(@Path("roomId") String roomId, @Body PowerLevels powerLevels, Callback<Void> callback);

    /**
     * Send a generic state events
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
     */
    @POST("/rooms/{roomId}/invite")
    void invite(@Path("roomId") String roomId, @Body User user, Callback<Void> callback);

    /**
     * Trigger an invitation with a parameters set.
     *
     * @param roomId   the room id
     * @param params   the parameters
     * @param callback the asynchronous callback called when finished
     */
    @POST("/rooms/{roomId}/invite")
    void invite(@Path("roomId") String roomId, @Body HashMap<String, String> params, Callback<Void> callback);

    /**
     * Join the given room.
     *
     * @param roomId   the room id
     * @param content  the request body
     * @param callback the asynchronous callback called when finished
     */
    @POST("/rooms/{roomId}/join")
    void join(@Path("roomId") String roomId, @Body JsonObject content, Callback<Void> callback);

    /**
     * Join the room with a room id or an alias.
     *
     * @param roomAliasOrId a room alias (or room id)
     * @param params        the extra join param
     * @param callback      the asynchronous callback called with the response
     */
    @POST("/join/{roomAliasOrId}")
    void joinRoomByAliasOrId(@Path("roomAliasOrId") String roomAliasOrId, @Body HashMap<String, Object> params, Callback<RoomResponse> callback);

    /**
     * Leave the given room.
     *
     * @param roomId   the room id
     * @param content  the request body
     * @param callback the asynchronous callback called when finished
     */
    @POST("/rooms/{roomId}/leave")
    void leave(@Path("roomId") String roomId, @Body JsonObject content, Callback<Void> callback);

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
     *
     * @param roomId   the room id
     * @param user     the banned user object (userId and reason for ban)
     * @param callback the asynchronous callback called when finished
     */
    @POST("/rooms/{roomId}/ban")
    void ban(@Path("roomId") String roomId, @Body BannedUser user, Callback<Void> callback);

    /**
     * unban a user from the given room.
     *
     * @param roomId   the room id
     * @param user     the banned user object (userId and reason for unban)
     * @param callback the asynchronous callback called when finished
     */
    @POST("/rooms/{roomId}/unban")
    void unban(@Path("roomId") String roomId, @Body BannedUser user, Callback<Void> callback);

    /**
     * Change the membership state for a user in a room.
     *
     * @param roomId   the room id
     * @param userId   the user id
     * @param member   object containing the membership field to set
     * @param callback the asynchronous callback called when finished
     */
    @PUT("/rooms/{roomId}/state/m.room.member/{userId}")
    void updateRoomMember(@Path("roomId") String roomId, @Path("userId") String userId, @Body RoomMember member, Callback<Void> callback);

    /**
     * Update the typing notification
     *
     * @param roomId   the room id
     * @param userId   the user id
     * @param typing   the typing notification
     * @param callback the asynchronous callback called when finished
     */
    @PUT("/rooms/{roomId}/typing/{userId}")
    void setTypingNotification(@Path("roomId") String roomId, @Path("userId") String userId, @Body Typing typing, Callback<Void> callback);

    /**
     * Create a room.
     *
     * @param createRoomRequest the creation room request
     * @param callback  the asynchronous callback called with the response
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
     */
    @GET("/rooms/{roomId}/initialSync")
    void initialSync(@Path("roomId") String roomId, @Query("limit") int limit, Callback<RoomResponse> callback);

    /**
     * Get the context surrounding an event.
     *
     * @param roomId   the room id
     * @param eventId  the event Id
     * @param limit    the maximum number of messages to retrieve
     * @param callback the asynchronous callback called with the response
     */
    @GET("/rooms/{roomId}/context/{eventId}")
    void getContextOfEvent(@Path("roomId") String roomId, @Path("eventId") String eventId, @Query("limit") int limit, Callback<EventContext> callback);

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
     *
     * @param roomId   the room id
     * @param eventId  the event id of the event to redact
     * @param reason   the reason
     * @param callback the asynchronous callback called with the response
     */
    @POST("/rooms/{roomId}/redact/{eventId}")
    void redactEvent(@Path("roomId") String roomId, @Path("eventId") String eventId, @Body JsonObject reason, Callback<Event> callback);

    /**
     * Report an event content.
     *
     * @param roomId   the room id
     * @param eventId  the event id of the event to redact
     * @param param    the request parameters
     * @param callback the asynchronous callback called with the response
     */
    @POST("/rooms/{roomId}/report/{eventId}")
    void reportEvent(@Path("roomId") String roomId, @Path("eventId") String eventId, @Body ReportContentParams param, Callback<Void> callback);

    /**
     * Set the room avatar url.
     *
     * @param roomId   the room id
     * @param params   the put params.
     * @param callback the asynchronous callback called when finished
     */
    @PUT("/rooms/{roomId}/state/m.room.avatar")
    void setRoomAvatarUrl(@Path("roomId") String roomId, @Body HashMap<String, String> params, Callback<Void> callback);

    /**
     * Send a read receipt.
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
     */
    @PUT("/user/{userId}/rooms/{roomId}/tags/{tag}")
    void addTag(@Path("userId") String userId, @Path("roomId") String roomId, @Path("tag") String tag, @Body HashMap<String, Object> content,
                Callback<Void> callback);

    /**
     * Remove a tag to a room
     *
     * @param userId   the userId
     * @param roomId   the room id
     * @param tag      the new room tag
     * @param callback the asynchronous callback called with the response
     */
    @DELETE("/user/{userId}/rooms/{roomId}/tags/{tag}")
    void removeTag(@Path("userId") String userId, @Path("roomId") String roomId, @Path("tag") String tag,
                   Callback<Void> callback);

    /**
     * Get the room ID associated to the room alias.
     *
     * @param roomAlias the room alias.
     * @param callback  the asynchronous callback called with the response
     */
    @GET("/directory/room/{roomAlias}")
    void getRoomIdByAlias(@Path("roomAlias") String roomAlias, Callback<RoomAliasDescription> callback);

    /**
     * Associate a room alias with a room ID.
     *
     * @param roomAlias   the room alias.
     * @param description the alias description containing the room ID
     * @param callback    the asynchronous callback called with the response
     */
    @PUT("/directory/room/{roomAlias}")
    void setRoomIdByAlias(@Path("roomAlias") String roomAlias, @Body RoomAliasDescription description, Callback<Void> callback);

    /**
     * Get the room ID corresponding to this room alias.
     *
     * @param roomAlias the room alias.
     * @param callback  the asynchronous callback called with the response
     */
    @DELETE("/directory/room/{roomAlias}")
    void removeRoomAlias(@Path("roomAlias") String roomAlias, Callback<Void> callback);

    /**
     * Set the visibility of the given room in the list directory. If the visibility is set to public, the room
     * name is listed among the directory list.
     *
     * @param roomId   the room id where to apply the request
     * @param state    state object containing the new guest access rule in its {@link RoomState#visibility} field
     * @param callback the asynchronous callback called when finished
     */
    @PUT("/directory/list/room/{roomId}")
    void setRoomDirectoryVisibility(@Path("roomId") String roomId, @Body RoomState state, Callback<Void> callback);

    /**
     * Get the visibility of the given room in the list directory.
     *
     * @param roomId   the room id where to apply the request
     * @param callback the asynchronous callback response containing the {@link RoomState#visibility} value
     */
    @GET("/directory/list/room/{roomId}")
    void getRoomDirectoryVisibility(@Path("roomId") String roomId, Callback<RoomState> callback);
}
