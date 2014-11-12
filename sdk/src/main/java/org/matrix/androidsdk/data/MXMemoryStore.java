package org.matrix.androidsdk.data;

import android.util.Log;

import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.TokensChunkResponse;
import org.matrix.androidsdk.rest.model.User;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
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
    public void storeRoomEvent(Event event, String token, Room.EventDirection direction) {
        LinkedHashSet<Event> events = mRoomEvents.get(event.roomId);
        if (events == null) {
            events = new LinkedHashSet<Event>();
            mRoomEvents.put(event.roomId, events);
        }
        events.add(event);
    }

    @Override
    public TokensChunkResponse<Event> getRoomEvents(String roomId, String token) {
        LinkedHashSet<Event> events = mRoomEvents.get(roomId);
        TokensChunkResponse<Event> response = new TokensChunkResponse<Event>();
        if (events == null) {
            response.chunk = new ArrayList<Event>();
            return response;
        }
        response.chunk = new ArrayList<Event>(events);
        // We want a chunk that goes from most recent to least
        Collections.reverse(response.chunk);
        return response;
    }

    @Override
    public void updateRoomState(Room room, String stateEventType) {
        storeRoom(room);
    }

    @Override
    public Collection<RoomSummary> getSummaries() {
        ArrayList<RoomSummary> summaries = new ArrayList<RoomSummary>();

        for (Room room : getRooms()) {
            RoomSummary summary = new RoomSummary();
            Iterator<Event> it = mRoomEvents.get(room.getRoomId()).iterator();
            while (it.hasNext()) {
                summary.setLatestEvent(it.next());
            }
            summary.setMembers(room.getMembers());
            summary.setName(room.getName());
            summary.setRoomId(room.getRoomId());
            summary.setTopic(room.getTopic());

            summaries.add(summary);
        }

        return summaries;
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
