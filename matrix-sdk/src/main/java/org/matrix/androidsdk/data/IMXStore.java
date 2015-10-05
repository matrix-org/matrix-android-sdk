/*
 * Copyright 2015 OpenMarket Ltd
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

package org.matrix.androidsdk.data;

import com.google.gson.JsonObject;

import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.TokensChunkResponse;
import org.matrix.androidsdk.rest.model.User;

import java.util.Collection;

/**
 * An interface for storing and retrieving Matrix objects.
 */
public interface IMXStore {

    public interface MXStoreListener {
        /**
         * Called when the store is initialized
         */
        public void onStoreReady(String accountId);

        /**
         * Called when the store initialization fails.
         */
        public void onStoreCorrupted(String accountId);
    }

    /**
     * Save changes in the store.
     * If the store uses permanent storage like database or file, it is the optimised time
     * to commit the last changes.
     */
    public void commit();

    /**
     * Open the store.
     */
    public void open();

    /**
     * Close the store.
     * Any pending operation must be complete in this call.
     */
    public void close();

    /**
     * Clear the store.
     * Any pending operation must be complete in this call.
     */
    public void clear();

    /**
     * Indicate if the MXStore implementation stores data permanently.
     * Permanent storage allows the SDK to make less requests at the startup.
     * @return true if permanent.
     */
    public boolean isPermanent();

    /**
     * Check if the initial load is performed.
     * @return true if it is ready.
     */
    public boolean isReady();

    /**
     * Returns to disk usage size in bytes.
     * @return disk usage size
     */
    public long diskUsage();

    /**
     * Returns the latest known event stream token
     * @return the event stream token
     */
    public String getEventStreamToken();

    /**
     * Set the event stream token.
     * @param token the event stream token
     */
    public void setEventStreamToken(String token);

    /**
     * Define a MXStore listener.
     * @param listener
     */
    public void setMXStoreListener(MXStoreListener listener);

    /**
     * profile information
     */
    public String displayName();
    public void setDisplayName(String displayName);
    public String avatarURL();
    public void setAvatarURL(String avatarURL);

    /**
     * getters.
     */
    public Collection<Room> getRooms();
    public Room getRoom(String roomId);
    public User getUser(String userId);
    public void storeUser(User user);
    public void storeRoom(Room room);

    /**
     * Store a block of room events either live or from pagination.
     * @param roomId the room id
     * @param eventsResponse The events to be stored.
     * @param direction the direction; forwards for live, backwards for pagination
     */
    public void storeRoomEvents(String roomId, TokensChunkResponse<Event> eventsResponse, Room.EventDirection direction);

    /**
     * Store a live room event.
     * @param event The event to be stored.
     */
    public void storeLiveRoomEvent(Event event);

    /**
     * Delete an event
     * @param event The event to be deleted.
     */
    public void deleteEvent(Event event);

    /**
     * Delete the room data
     * @param roomId the roomId.
     */
    public void deleteRoom(String roomId);

    /**
     * Retrieve all non-state room events for this room.
     * @param roomId The room ID
     * @return A collection of events. null if there is no cached event.
     */
    public Collection<Event> getRoomMessages(final String roomId);

    /**
     * Retrieve all non-state room events for this room.
     * @param roomId The room ID
     * @param fromToken the token
     * @param limit the maximum number of messages to retrieve.
     * @return A collection of events. null if there is no cached event.
     */
    public TokensChunkResponse<Event> getEarlierMessages(final String roomId, final String fromToken, final int limit);
    /**
     * Get the oldest event from the given room (to prevent pagination overlap).
     * @param roomId the room id
     * @return the event
     */
    public Event getOldestEvent(String roomId);

    /**
     * Get the latest event from the given room (to update summary for example)
     * @param roomId the room id
     * @return the event
     */
    public Event getLatestEvent(String roomId);

    /**
     * Get the latest message event from the given room
     * @param roomId the room id
     * @return the message event
     */
    public Event getLatestMessageEvent(String roomId);

    /**
     * Count the number of events after the provided events id
     * @param roomId the room id.
     * @param eventId the event id to find.
     * @return the events count after this event if
     */
    public int eventsCountAfter(String roomId, String eventId);

    /**
     * Update an existing event. If the event is not stored, nothing is done.
     * @param roomId the event's room id
     * @param eventId the event's event id
     * @param newContent the new content
     * @return true if the event has been successfully replaced.
     */
    public boolean updateEventContent(String roomId, String eventId, JsonObject newContent);

    // Design note: This is part of the store interface so the concrete implementation can leverage
    //              how they are storing the data to do this in an efficient manner (e.g. SQL JOINs)
    //              compared to calling getRooms() then getRoomEvents(roomId, limit=1) for each room
    //              (which forces single SELECTs)
    /**
     * <p>Retrieve a list of all the room summaries stored.</p>
     * Typically this method will be called when generating a 'Recent Activity' list.
     * @return A collection of room summaries.
     */
    public Collection<RoomSummary> getSummaries();

    /**
     * Get the stored summary for the given room.
     * @param roomId the room id
     * @return the summary for the room
     */
    public RoomSummary getSummary(String roomId);

    /**
     * Flush a room summmary
     * @param summary
     */
    public void flushSummary(RoomSummary summary);

    /**
     * Flush the room summmaries
     */
    public void flushSummaries();

    /**
     * Store the summary for the given room id.
     * @param matrixId the matrix id
     * @param roomId the room id
     * @param event the latest event of the room
     * @param roomState the room state - used to display the event
     * @param selfUserId our own user id - used to display the room name
     */
    public void storeSummary(String matrixId, String roomId, Event event, RoomState roomState, String selfUserId);

    /**
     * Store the room liveState.
     * @param roomId roomId the id of the room.
     */
    public void storeLiveStateForRoom(String roomId);

    /**
     * Return the list of latest unsent events.
     * The provided events are the unsent ones since the last sent one.
     * They are ordered.
     * @param roomId the room id
     * @return list of unsent events
     */
    public Collection<Event> getLatestUnsentEvents(String roomId);
}
