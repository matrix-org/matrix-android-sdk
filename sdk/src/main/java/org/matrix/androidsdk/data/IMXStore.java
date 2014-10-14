package org.matrix.androidsdk.data;

import org.matrix.androidsdk.api.response.User;

/**
 * An interface for storing and retrieving Matrix objects.
 */
public interface IMXStore {
    public Room getRoom(String roomId);
    public User getUser(String userId);
    public void storeUser(User user);
    public void storeRoom(Room room);
}
