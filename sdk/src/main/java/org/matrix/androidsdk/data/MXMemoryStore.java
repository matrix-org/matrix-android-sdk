package org.matrix.androidsdk.data;

import android.util.Log;

import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.User;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An in-memory IMXStore.
 */
public class MXMemoryStore implements IMXStore {

    private Map<String, Room> mRooms = new ConcurrentHashMap<String, Room>();
    private Map<String, User> mUsers = new ConcurrentHashMap<String, User>();
    // room id -> set of events for this room (linked so insertion order is preserved)
    private Map<String, LinkedHashSet<Event>> mRoomEvents = new ConcurrentHashMap<String, LinkedHashSet<Event>>();

    @Override
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

    @Override
    public void storeRoomEvent(Event event) {
        LinkedHashSet<Event> events = mRoomEvents.get(event.roomId);
        if (events == null) {
            events = new LinkedHashSet<Event>();
            mRoomEvents.put(event.roomId, events);
        }
        events.add(event);
    }

    @Override
    public Collection<Event> getRoomEvents(String roomId, int limit) {
        LinkedHashSet<Event> events = mRoomEvents.get(roomId);
        if (events == null) {
            return new ArrayList<Event>();
        }
        return events;
    }

    @Override
    public void updateRoomState(Room room, String stateEventType) {
        storeRoom(room);
    }

    @Override
    public Collection<RoomSummary> getSummaries() {
        return null;
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
