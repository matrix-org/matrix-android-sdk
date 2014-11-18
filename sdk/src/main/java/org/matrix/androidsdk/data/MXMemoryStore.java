package org.matrix.androidsdk.data;

import android.util.Log;

import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.TokensChunkResponse;
import org.matrix.androidsdk.rest.model.User;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
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
    private Map<String, String> mRoomTokens = new ConcurrentHashMap<String, String>();

    private Map<String, RoomSummary> mRoomSummaries = new ConcurrentHashMap<String, RoomSummary>();

    @Override
    public Collection<Room> getRooms() {
        return mRooms.values();
    }

    public Collection<User> getUsers() {
        return mUsers.values();
    }

    @Override
    public Room getRoom(String roomId) {
        return mRooms.get(roomId);
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

    private LinkedHashSet<Event> getRoomEvents(String roomId) {
        LinkedHashSet<Event> events = mRoomEvents.get(roomId);
        if (events == null) {
            events = new LinkedHashSet<Event>();
            mRoomEvents.put(roomId, events);
        }
        return events;
    }

    @Override
    public void storeLiveRoomEvent(Event event) {
        LinkedHashSet<Event> events = mRoomEvents.get(event.roomId);
        if (events != null) {
            // If we don't have any information on this room - a pagination token, namely - we don't store the event but instead
            // wait for the first pagination request to set things right
            events.add(event);
        }
        storeSummary(event.roomId, event);
    }

    @Override
    public void storeRoomEvents(String roomId, TokensChunkResponse<Event> eventsResponse, Room.EventDirection direction) {
        if (direction == Room.EventDirection.FORWARDS) { // TODO: Implement backwards direction
            LinkedHashSet<Event> events = mRoomEvents.get(roomId);
            if (events == null) {
                events = new LinkedHashSet<Event>();
                mRoomEvents.put(roomId, events);
                mRoomTokens.put(roomId, eventsResponse.start);
            }
            events.addAll(eventsResponse.chunk);

            // Store summary
            storeSummary(roomId, eventsResponse.chunk.get(eventsResponse.chunk.size() - 1));
        }
    }

    @Override
    public void storeSummary(String roomId, Event event) {
        Room room = mRooms.get(roomId);
        if (room != null) { // Should always be the case
            RoomSummary summary = mRoomSummaries.get(roomId);
            if (summary == null) {
                summary = new RoomSummary();
            }
            summary.setLatestEvent(event);
            summary.setMembers(room.getMembers());
            summary.setName(room.getName());
            summary.setRoomId(room.getRoomId());
            summary.setTopic(room.getTopic());

            mRoomSummaries.put(roomId, summary);
        }
    }

    @Override
    public TokensChunkResponse<Event> getRoomEvents(String roomId, String token) {
        // For now, we return everything we have for the original null token request
        // For older requests (providing a token), returning null for now
        if (token == null) {
            LinkedHashSet<Event> events = mRoomEvents.get(roomId);
            if (events == null) {
                return null;
            }
            TokensChunkResponse<Event> response = new TokensChunkResponse<Event>();
            response.chunk = new ArrayList<Event>(events);
            // We want a chunk that goes from most recent to least
            Collections.reverse(response.chunk);
            response.end = mRoomTokens.get(roomId);
            return response;
        }
        return null;
    }

    @Override
    public Collection<RoomSummary> getSummaries() {
        return mRoomSummaries.values();
    }
}
