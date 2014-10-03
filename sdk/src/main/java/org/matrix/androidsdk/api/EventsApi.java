package org.matrix.androidsdk.api;

import com.google.gson.JsonObject;

import org.matrix.androidsdk.api.response.PublicRoom;
import org.matrix.androidsdk.api.response.TokensChunkResponse;

import java.util.List;

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
    public JsonObject events(@Query("from") String from, @Query("timeout") int timeout);

    /**
     * Get information about a single event.
     * @param eventId The event ID to get
     * @return the event information
     */
    @GET("/events/{eventId}")
    public JsonObject events(@Path("eventId") String eventId);

    /**
     * Get the list of public rooms for this home server.
     * @param callback An asynchronous callback to call when finished
     */
    @GET("/publicRooms")
    public void publicRooms(Callback<TokensChunkResponse<List<PublicRoom>>> callback);

    /**
     * Perform the initial sync to find the rooms that concern the user, the participants' presence, etc.
     * @param limit the limit of the amount of messages to return per room
     * @return the rooms, presence, states information
     */
    @GET("/initialSync")
    public JsonObject initialSync(@Query("limit") int limit);
}
