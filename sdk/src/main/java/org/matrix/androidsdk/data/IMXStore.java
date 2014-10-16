package org.matrix.androidsdk.data;

import org.matrix.androidsdk.rest.model.User;

import java.util.Collection;

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
     * Update room state.
     * @param room The room to update.
     * @param stateEventType The state event type which was updated.
     */
    public void updateRoomState(Room room, String stateEventType);
}
