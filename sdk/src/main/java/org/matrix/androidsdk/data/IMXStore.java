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
import java.util.List;

/**
 * An interface for storing and retrieving Matrix objects.
 */
public interface IMXStore {
    public Collection<Room> getRooms();
    public Room getRoom(String roomId);
    public User getUser(String userId);
    public void storeUser(User user);
    public void storeRoom(Room room);

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
     * Store a block of room events either live or from pagination.
     * @param roomId the room id
     * @param eventsResponse The events to be stored.
     * @param direction the direction; forwards for live, backwards for pagination
     */
    public void storeRoomEvents(String roomId, TokensChunkResponse<Event> eventsResponse, Room.EventDirection direction);

    /**
     * Retrieve all non-state room events for this room.
     * @param roomId The room ID
     * @param token the associated token
     * @return A collection of events.
     */
    public TokensChunkResponse<Event> getRoomEvents(String roomId, String token);

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
     * Update an existing event. If the event is not stored, nothing is done.
     * @param roomId the event's room id
     * @param eventId the event's event id
     * @param newContent the new content
     */
    public void updateEventContent(String roomId, String eventId, JsonObject newContent);

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
     * Store the summary for the given room id.
     * @param roomId the room id
     * @param event the latest event of the room
     * @param roomState the room state - used to display the event
     * @param selfUserId our own user id - used to display the room name
     */
    public void storeSummary(String roomId, Event event, RoomState roomState, String selfUserId);

    /**
     * Return the list of latest unsent events.
     * The provided events are the unsent ones since the last sent one.
     * They are ordered.
     * @param roomId the room id
     * @return list of unsent events
     */
    public Collection<Event> getLatestUnsentEvents(String roomId);
}
