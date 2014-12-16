package org.matrix.androidsdk.data;

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
     * Store the summary for the given room id.
     * @param roomId the room id
     * @param event the latest event of the room
     * @param roomState the room state - used to display the event
     * @param selfUserId our own user id - used to display the room name
     */
    public void storeSummary(String roomId, Event event, RoomState roomState, String selfUserId);
}
