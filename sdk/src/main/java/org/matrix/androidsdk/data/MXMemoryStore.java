package org.matrix.androidsdk.data;

import org.matrix.androidsdk.api.response.User;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An in-memory IMXStore.
 */
public class MXMemoryStore implements IMXStore {

    private Map<String, Room> mRooms = new ConcurrentHashMap<String, Room>();
    private Map<String, User> mUsers = new ConcurrentHashMap<String, User>();

    public Collection<Room> getRooms() {
        return mRooms.values();
    }

    public Collection<User> getUsers() {
        return mUsers.values();
    }

    @Override
    public Room getRoom(String roomId) {
        return getOrCreateRoom(roomId);
    }

    @Override
    public User getUser(String userId) {
        return mUsers.get(userId);
    }

    @Override
    public void storeUser(User user) {
        mUsers.put(user.userId, user);
    }

    @Override
    public void storeRoom(Room room) {
        mRooms.put(room.getRoomId(), room);
    }

    private Room addRoom(String roomId) {
        Room room = new Room();
        room.setRoomId(roomId);
        this.storeRoom(room);
        return room;
    }

    private Room getOrCreateRoom(String roomId) {
        Room room = mRooms.get(roomId);
        if (room == null) {
            room = addRoom(roomId);
        }
        return room;
    }
}
