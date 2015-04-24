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

import android.util.Log;

import com.google.gson.JsonObject;

import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.TokensChunkResponse;
import org.matrix.androidsdk.rest.model.User;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An in-memory IMXStore.
 */
public class MXMemoryStore implements IMXStore {

    private Map<String, Room> mRooms = new ConcurrentHashMap<String, Room>();
    private Map<String, User> mUsers = new ConcurrentHashMap<String, User>();
    // room id -> map of (event_id -> event) events for this room (linked so insertion order is preserved)
    private Map<String, LinkedHashMap<String, Event>> mRoomEvents = new ConcurrentHashMap<String, LinkedHashMap<String, Event>>();
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
        if (null != roomId) {
            return mRooms.get(roomId);
        } else {
            return null;
        }
    }

    @Override
    public User getUser(String userId) {
        if (null != userId) {
            return mUsers.get(userId);
        } else {
            return null;
        }
    }

    @Override
    public void storeUser(User user) {
        if ((null != user) && (null != user.userId)) {
            mUsers.put(user.userId, user);
        }
    }

    @Override
    public void storeRoom(Room room) {
        if ((null != room) && (null != room.getRoomId())) {
            mRooms.put(room.getRoomId(), room);
        }
    }

    @Override
    public Event getOldestEvent(String roomId) {
        if (null != roomId) {
            LinkedHashMap<String, Event> events = mRoomEvents.get(roomId);

            if (events != null) {
                Iterator<Event> it = events.values().iterator();
                if (it.hasNext()) {
                    return it.next();
                }
            }
        }
        return null;
    }

    /**
     * Get the latest event from the given room (to update summary for example)
     * @param roomId the room id
     * @return the event
     */
    public Event getLatestEvent(String roomId) {
        if (null != roomId) {
            LinkedHashMap<String, Event> events = mRoomEvents.get(roomId);

            if (events != null) {
                Iterator<Event> it = events.values().iterator();
                if (it.hasNext()) {
                    Event lastEvent = null;

                    while (it.hasNext()) {
                        lastEvent = it.next();
                    }

                    return lastEvent;
                }
            }
        }
        return null;
    }

    @Override
    public void storeLiveRoomEvent(Event event) {
        if ((null != event) && (null != event.roomId)) {
            LinkedHashMap<String, Event> events = mRoomEvents.get(event.roomId);
            if (events != null) {
                // If we don't have any information on this room - a pagination token, namely - we don't store the event but instead
                // wait for the first pagination request to set things right
                events.put(event.eventId, event);
            }
        }
    }

    @Override
    public void deleteEvent(Event event) {
        if ((null != event) && (null != event.roomId)) {
            LinkedHashMap<String, Event> events = mRoomEvents.get(event.roomId);
            if ((events != null) && (event.eventId != null)) {
                events.remove(event.eventId);
            }
        }
    }

    @Override
    public void deleteRoom(String roomId) {
    	// ssnity check
        if (null != roomId) {
            mRooms.remove(roomId);
            mRoomEvents.remove(roomId);
            mRoomTokens.remove(roomId);
            mRoomSummaries.remove(roomId);
        }
    }

    @Override
    public void storeRoomEvents(String roomId, TokensChunkResponse<Event> eventsResponse, Room.EventDirection direction) {
        if (null != roomId) {
            LinkedHashMap<String, Event> events = mRoomEvents.get(roomId);
            if (events == null) {
                events = new LinkedHashMap<String, Event>();
                mRoomEvents.put(roomId, events);
            }

            if (direction == Room.EventDirection.FORWARDS) {
                mRoomTokens.put(roomId, eventsResponse.start);

                for (Event event : eventsResponse.chunk) {
                    events.put(event.eventId, event);
                }

            } else { // BACKWARD
                Collection<Event> eventsList = events.values();

                // no stored events
                if (events.size() == 0) {
                    // insert the catchup events in reverse order
                    for (int index = eventsResponse.chunk.size() - 1; index >= 0; index--) {
                        Event backEvent = eventsResponse.chunk.get(index);
                        events.put(backEvent.eventId, backEvent);
                    }

                    // define a token
                    mRoomTokens.put(roomId, eventsResponse.start);
                } else {
                    LinkedHashMap<String, Event> events2 = new LinkedHashMap<String, Event>();

                    // insert the catchup events in reverse order
                    for (int index = eventsResponse.chunk.size() - 1; index >= 0; index--) {
                        Event backEvent = eventsResponse.chunk.get(index);
                        events2.put(backEvent.eventId, backEvent);
                    }

                    // add the previous added Events
                    for (Event event : eventsList) {
                        events2.put(event.eventId, event);
                    }

                    // store the new list
                    mRoomEvents.put(roomId, events2);
                }
            }
        }
    }

    @Override
    public void updateEventContent(String roomId, String eventId, JsonObject newContent) {
        if (null != roomId) {
            LinkedHashMap<String, Event> events = mRoomEvents.get(roomId);
            if (events != null) {
                Event event = events.get(eventId);
                if (event != null) {
                    event.content = newContent;
                }
            }
        }
    }

    @Override
    public void storeSummary(String matrixId, String roomId, Event event, RoomState roomState, String selfUserId) {
        if (null !=roomId) {
            Room room = mRooms.get(roomId);
            if ((room != null) && (event != null)) { // Should always be true
                RoomSummary summary = mRoomSummaries.get(roomId);
                if (summary == null) {
                    summary = new RoomSummary();
                }
                summary.setMatrixId(matrixId);
                summary.setLatestEvent(event);
                summary.setLatestRoomState(roomState);
                summary.setMembers(room.getMembers());
                summary.setName(room.getName(selfUserId));
                summary.setRoomId(room.getRoomId());
                summary.setTopic(room.getTopic());

                mRoomSummaries.put(roomId, summary);
            }
        }
    }

    @Override
    public TokensChunkResponse<Event> getRoomEvents(String roomId, String token) {
        // For now, we return everything we have for the original null token request
        // For older requests (providing a token), returning null for now
        if ((null != roomId) && (token == null)) {
            LinkedHashMap<String, Event> events = mRoomEvents.get(roomId);
            if (events == null) {
                return null;
            }
            TokensChunkResponse<Event> response = new TokensChunkResponse<Event>();
            response.chunk = new ArrayList<Event>(events.values());
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

    @Override
    public RoomSummary getSummary(String roomId) {
        return mRoomSummaries.get(roomId);
    }

    /**
     * Return the list of latest unsent events.
     * The provided events are the unsent ones since the last sent one.
     * They are ordered.
     * @param roomId the room id
     * @return list of unsent events
     */
    public Collection<Event> getLatestUnsentEvents(String roomId) {
        if (null == roomId) {
            return null;
        }

        ArrayList<Event> unsentRoomEvents = new ArrayList<Event>();
        LinkedHashMap<String, Event> events = mRoomEvents.get(roomId);

        // contain some events
        if ((null != events) && (events.size() > 0)) {
            ArrayList<Event>eventsList = new ArrayList(events.values());

            for(int index = events.size()-1; index >= 0; index--) {
                Event event = eventsList.get(index);

                if (event.mSentState == Event.SentState.WAITING_RETRY) {
                    unsentRoomEvents.add(event);
                } else {
                    //break;
                }
            }

            Collections.reverse(unsentRoomEvents);
        }

        return unsentRoomEvents;
    }
}
