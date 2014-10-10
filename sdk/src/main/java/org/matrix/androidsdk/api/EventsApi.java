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
package org.matrix.androidsdk.api;

import com.google.gson.JsonObject;

import org.matrix.androidsdk.api.response.Event;
import org.matrix.androidsdk.api.response.InitialSyncResponse;
import org.matrix.androidsdk.api.response.PublicRoom;
import org.matrix.androidsdk.api.response.TokensChunkResponse;

import retrofit.Callback;
import retrofit.http.GET;
import retrofit.http.Path;
import retrofit.http.Query;

/**
 * The events API.
 */
public interface EventsApi {

    /**
     * Wait and return the next event that comes down.
     * @param from the token starting from which we scan for events
     * @param timeout a timeout value
     * @return the next event or just the same token in case of timeout
     */
    @GET("/events")
    public TokensChunkResponse<Event> events(@Query("from") String from, @Query("timeout") int timeout);

    /**
     * Get information about a single event.
     * @param eventId The event ID to get
     * @param callback The asynchronous callback to call when finished
     * @return the event information
     */
    @GET("/events/{eventId}")
    public void events(@Path("eventId") String eventId, Callback<Event> callback);

    /**
     * Get the list of public rooms for this home server.
     * @param callback The asynchronous callback to call when finished
     */
    @GET("/publicRooms")
    public void publicRooms(Callback<TokensChunkResponse<PublicRoom>> callback);

    /**
     * Perform the initial sync to find the rooms that concern the user, the participants' presence, etc.
     * @param limit the limit of the amount of messages to return per room
     * @param callback The asynchronous callback to call when finished
     * @return the rooms, presence, states information
     */
    @GET("/initialSync")
    public void initialSync(@Query("limit") int limit,
                                           Callback<InitialSyncResponse> callback);
}
