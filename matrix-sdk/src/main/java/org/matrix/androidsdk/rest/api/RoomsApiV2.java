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
import org.matrix.androidsdk.rest.model.PowerLevels;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.RoomResponse;
import org.matrix.androidsdk.rest.model.TokensChunkResponse;
import org.matrix.androidsdk.rest.model.Typing;
import org.matrix.androidsdk.rest.model.User;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;

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
public interface RoomsApiV2 {

    /**
     * Send a read receipt.
     * @param roomId the room id
     * @param EventId the latest eventid
     * @param content the event content
     * @param callback the asynchronous callback called with the response
     */
    @POST("/rooms/{roomId}/receipt/m.read/{eventId}")
    public void sendReadReceipt(@Path("roomId") String roomId, @Path("eventId") String EventId, @Body JsonObject content,
                     Callback<Void> callback);

    /**
     * Add a tag to a room
     * @param userId the userId
     * @param roomId the room id
     * @param tag the new room tag
     * @param content the event content
     * @param callback the asynchronous callback called with the response
     */
    @PUT("/user/{userId}/rooms/{roomId}/tags/{tag}")
    public void addTag(@Path("userId") String userId, @Path("roomId") String roomId, @Path("tag") String tag,  @Body HashMap<String, Object> content,
                                Callback<Void> callback);

    /**
     * Remove a tag to a room
     * @param userId the userId
     * @param roomId the room id
     * @param tag the new room tag
     * @param callback the asynchronous callback called with the response
     */
    @DELETE("/user/{userId}/rooms/{roomId}/tags/{tag}")
    public void removeTag(@Path("userId") String userId, @Path("roomId") String roomId, @Path("tag") String tag,
                       Callback<Void> callback);
}
