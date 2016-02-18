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
import org.matrix.androidsdk.rest.model.ReceiptData;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.TokensChunkResponse;
import org.matrix.androidsdk.rest.model.User;

import java.util.Collection;
import java.util.List;

/**
 * An interface for storing and retrieving Matrix objects.
 */
public interface IMXStore {

    interface MXStoreListener {
        /**
         * Called when the store is initialized
         */
        void onStoreReady(String accountId);

        /**
         * Called when the store initialization fails.
         */
        void onStoreCorrupted(String accountId, String description);
    }

    /**
     * Save changes in the store.
     * If the store uses permanent storage like database or file, it is the optimised time
     * to commit the last changes.
     */
    void commit();

    /**
     * Open the store.
     */
    void open();

    /**
     * Close the store.
     * Any pending operation must be complete in this call.
     */
    void close();

    /**
     * Clear the store.
     * Any pending operation must be complete in this call.
     */
    void clear();

    /**
     * Indicate if the MXStore implementation stores data permanently.
     * Permanent storage allows the SDK to make less requests at the startup.
     * @return true if permanent.
     */
    boolean isPermanent();

    /**
     * Check if the initial load is performed.
     * @return true if it is ready.
     */
    boolean isReady();

    /**
     * @return true if the store is corrupted.
     */
    boolean isCorrupted();

    /**
     * Returns to disk usage size in bytes.
     * @return disk usage size
     */
    long diskUsage();

    /**
     * Returns the latest known event stream token
     * @return the event stream token
     */
    String getEventStreamToken();

    /**
     * Set the event stream token.
     * @param token the event stream token
     */
    void setEventStreamToken(String token);

    /**
     * Define a MXStore listener.
     * @param listener
     */
    void setMXStoreListener(MXStoreListener listener);

    /**
     * profile information
     */
    String displayName();
    void setDisplayName(String displayName);
    String avatarURL();
    void setAvatarURL(String avatarURL);

    /**
     * getters.
     */
    Collection<Room> getRooms();
    Room getRoom(String roomId);
    Collection<User> getUsers();
    User getUser(String userId);
    void storeUser(User user);
    void updateUserWithRoomMemberEvent(RoomMember roomMember);
    void storeRoom(Room room);

    /**
     * Store a block of room events either live or from pagination.
     * @param roomId the room id
     * @param eventsResponse The events to be stored.
     * @param direction the direction; forwards for live, backwards for pagination
     */
    void storeRoomEvents(String roomId, TokensChunkResponse<Event> eventsResponse, Room.EventDirection direction);

    /**
     * Store the back token of a room.
     * @param roomId the room id.
     * @param backToken the back token
     */
    void storeBackToken(String roomId, String backToken);

    /**
     * Store a live room event.
     * @param event The event to be stored.
     */
    void storeLiveRoomEvent(Event event);

    /**
     * @param eventId the id of the event to retrieve.
     * @param roomId the id of the room.
     * @return true if the event exists in the store.
     */
    Boolean doesEventExist(String eventId, String roomId);

    /**
     * Delete an event
     * @param event The event to be deleted.
     */
    void deleteEvent(Event event);

    /**
     * Remove all sent messages in a room.
     * @param roomId the id of the room.
     * @param keepUnsent set to true to do not delete the unsent message
     */
    void deleteAllRoomMessages(String roomId, Boolean keepUnsent);

    /**
     * Delete the room from the storage.
     * The room data and its reference will be deleted.
     * @param roomId the roomId.
     */
    void deleteRoom(String roomId);

    /**
     * Delete the room data from the storage;
     * The room data are cleared but the getRoom returned object will be the same.
     * @param roomId the roomId.
     */
    void deleteRoomData(String roomId);

    /**
     * Retrieve all non-state room events for this room.
     * @param roomId The room ID
     * @return A collection of events. null if there is no cached event.
     */
    Collection<Event> getRoomMessages(final String roomId);

    /**
     * Retrieve all non-state room events for this room.
     * @param roomId The room ID
     * @param fromToken the token
     * @param limit the maximum number of messages to retrieve.
     * @return A collection of events. null if there is no cached event.
     */
    TokensChunkResponse<Event> getEarlierMessages(final String roomId, final String fromToken, final int limit);
    /**
     * Get the oldest event from the given room (to prevent pagination overlap).
     * @param roomId the room id
     * @return the event
     */
    Event getOldestEvent(String roomId);

    /**
     * Get the latest event from the given room (to update summary for example)
     * @param roomId the room id
     * @return the event
     */
    Event getLatestEvent(String roomId);

    /**
     * Count the number of events after the provided events id
     * @param roomId the room id.
     * @param eventId the event id to find.
     * @return the events count after this event if
     */
    int eventsCountAfter(String roomId, String eventId);

    /**
     * Update an existing event. If the event is not stored, nothing is done.
     * @param roomId the event's room id
     * @param eventId the event's event id
     * @param newContent the new content
     * @return true if the event has been successfully replaced.
     */
    boolean updateEventContent(String roomId, String eventId, JsonObject newContent);

    // Design note: This is part of the store interface so the concrete implementation can leverage
    //              how they are storing the data to do this in an efficient manner (e.g. SQL JOINs)
    //              compared to calling getRooms() then getRoomEvents(roomId, limit=1) for each room
    //              (which forces single SELECTs)
    /**
     * <p>Retrieve a list of all the room summaries stored.</p>
     * Typically this method will be called when generating a 'Recent Activity' list.
     * @return A collection of room summaries.
     */
    Collection<RoomSummary> getSummaries();

    /**
     * Get the stored summary for the given room.
     * @param roomId the room id
     * @return the summary for the room
     */
    RoomSummary getSummary(String roomId);

    /**
     * Flush a room summmary
     * @param summary
     */
    void flushSummary(RoomSummary summary);

    /**
     * Flush the room summmaries
     */
    void flushSummaries();

    /**
     * Store the summary for the given room id.
     * @param roomId the room id
     * @param event the latest event of the room
     * @param roomState the room state - used to display the event
     * @param selfUserId our own user id - used to display the room name
     * @return the new RoomSummary.
     */
    RoomSummary storeSummary(String roomId, Event event, RoomState roomState, String selfUserId);

    /**
     * Store the room liveState.
     * @param roomId roomId the id of the room.
     */
    void storeLiveStateForRoom(String roomId);

    /**
     * Return the list of latest unsent events.
     * The provided events are the unsent ones since the last sent one.
     * They are ordered.
     * @param roomId the room id
     * @return list of unsent events
     */
    Collection<Event> getLatestUnsentEvents(String roomId);

    /**
     * Returns the receipts list for an event in a dedicated room.
     * if sort is set to YES, they are sorted from the latest to the oldest ones.
     * @param roomId The room Id.
     * @param eventId The event Id.
     * @param excludeSelf exclude the oneself read receipts.
     * @param sort to sort them from the latest to the oldest
     * @return the receipts for an event in a dedicated room.
     */
    List<ReceiptData> getEventReceipts(String roomId, String eventId, boolean excludeSelf, boolean sort);

    /**
     * Store the receipt for an user in a room
     * @param receipt The event
     * @param roomId The roomId
     * @return true if the receipt has been stored
     */
    boolean storeReceipt(ReceiptData receipt, String roomId);

    /**
     * Provides the unread events list.
     * @param roomId the room id.
     * @param types an array of event types strings (Event.EVENT_TYPE_XXX).
     * @return the unread events list.
     */
    List<Event> unreadEvents(String roomId, List<String> types);

    /**
     * Check if an event has been read by an user.
     * @param roomId the room Id
     * @param userId the user id
     * @param eventId the event id
     * @return true if the user has read the message.
     */
    boolean isEventRead(String roomId, String userId, String eventId);

    /**
     * Store the user data for a room.
     *
     * @param roomId The room Id.
     * @param accountData the account data.
     */
    void storeAccountData(String roomId, RoomAccountData accountData);
}
