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

import android.text.TextUtils;
import android.util.Log;

import com.google.gson.JsonObject;

import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.ReceiptData;
import org.matrix.androidsdk.rest.model.TokensChunkResponse;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.rest.model.login.Credentials;

import java.security.spec.KeySpec;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An in-memory IMXStore.
 */
public class MXMemoryStore implements IMXStore {

    private static final String LOG_TAG = "MXMemoryStore";

    protected Map<String, Room> mRooms;
    protected Map<String, User> mUsers;
    // room id -> map of (event_id -> event) events for this room (linked so insertion order is preserved)
    protected Map<String, LinkedHashMap<String, Event>> mRoomEvents;
    // room id -> list of event Ids
    protected Map<String, ArrayList<String>> mRoomEventIds;

    protected Map<String, String> mRoomTokens;

    protected Map<String, RoomSummary> mRoomSummaries;
    protected Map<String, RoomAccountData> mRoomAccountData;

    // dict of dict of MXReceiptData indexed by userId
    protected Map<String, Map<String, ReceiptData>> mReceiptsByRoomId;


    protected Credentials mCredentials;

    protected String mEventStreamToken = null;

    // Meta data about the store. It is defined only if the passed MXCredentials contains all information.
    // When nil, nothing is stored on the file system.
    protected MXFileStoreMetaData mMetadata = null;

    protected void initCommon(){
        mRooms = new ConcurrentHashMap<String, Room>();
        mUsers = new ConcurrentHashMap<String, User>();
        mRoomEvents = new ConcurrentHashMap<String, LinkedHashMap<String, Event>>();
        mRoomEventIds = new ConcurrentHashMap<String, ArrayList<String>>();
        mRoomTokens = new ConcurrentHashMap<String, String>();
        mRoomSummaries = new ConcurrentHashMap<String, RoomSummary>();
        mReceiptsByRoomId = new ConcurrentHashMap<String, Map<String, ReceiptData>>();
        mRoomAccountData = new ConcurrentHashMap<String, RoomAccountData>();
        mEventStreamToken = null;
    }

    public MXMemoryStore() {
        initCommon();
    }

    /**
     * Default constructor
     * @param credentials the expected credentials
     */
    public MXMemoryStore(Credentials credentials) {
        initCommon();
        mCredentials = credentials;

        mMetadata = new MXFileStoreMetaData();
    }

    /**
     * Save changes in the store.
     * If the store uses permanent storage like database or file, it is the optimised time
     * to commit the last changes.
     */
    @Override
    public void commit() {
    }

    /**
     * Open the store.
     */
    public void open() {
    }

    /**
     * Close the store.
     * Any pending operation must be complete in this call.
     */
    @Override
    public void close() {
    }

    /**
     * Clear the store.
     * Any pending operation must be complete in this call.
     */
    @Override
    public void clear() {
        initCommon();
    }

    /**
     * Indicate if the MXStore implementation stores data permanently.
     * Permanent storage allows the SDK to make less requests at the startup.
     * @return true if permanent.
     */
    @Override
    public boolean isPermanent() {
        return false;
    }

    /**
     * Check if the initial load is performed.
     * @return true if it is ready.
     */
    @Override
    public boolean isReady() {
        return true;
    }

    /**
     * @return true if the store is corrupted.
     */
    @Override
    public boolean isCorrupted() {
        return false;
    }

    /**
     * Returns to disk usage size in bytes.
     * @return disk usage size
     */
    @Override
    public long diskUsage() {
        return 0;
    }

    /**
     * Returns the latest known event stream token
     * @return the event stream token
     */
    @Override
    public String getEventStreamToken() {
        return mEventStreamToken;
    }

    /**
     * Set the event stream token.
     * @param token the event stream token
     */
    @Override
    public void setEventStreamToken(String token) {
        mMetadata.mEventStreamToken = token;
        mEventStreamToken = token;
    }

    /**
     * Define a MXStore listener.
     * @param listener
     */
    @Override
    public void setMXStoreListener(MXStoreListener listener) {
    }

    /**
     * profile information
     */
    public String displayName() {
        return mMetadata.mUserDisplayName;
    }

    public void setDisplayName(String displayName) {
        if (!TextUtils.equals(mMetadata.mUserDisplayName, displayName)) {
            mMetadata.mUserDisplayName = displayName;

            if (null != displayName) {
                mMetadata.mUserDisplayName.trim();
            }

            // update the cached oneself User
            User myUser = getUser(mMetadata.mUserId);

            if (null != myUser) {
                myUser.displayname = mMetadata.mUserDisplayName;
            }

            Log.d(LOG_TAG, "setDisplayName : commit");
            commit();
        }
    }

    public String avatarURL() {
        return mMetadata.mUserAvatarUrl;
    }

    public void setAvatarURL(String avatarURL) {
        if (!TextUtils.equals(mMetadata.mUserAvatarUrl, avatarURL)) {
            mMetadata.mUserAvatarUrl = avatarURL;

            // update the cached oneself User
            User myUser = getUser(mMetadata.mUserId);

            if (null != myUser) {
                myUser.avatarUrl = avatarURL;
            }

            Log.d(LOG_TAG, "setAvatarURL : commit");
            commit();
        }
    }

    @Override
    public Collection<Room> getRooms() {
        return new ArrayList<Room>(mRooms.values());
    }

    @Override
    public Collection<User> getUsers() {
        return new ArrayList<User>(mUsers.values());
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
        Event event = null;

        if (null != roomId) {
            synchronized (mRoomEvents) {
                LinkedHashMap<String, Event> events = mRoomEvents.get(roomId);

                if (events != null) {
                    Iterator<Event> it = events.values().iterator();
                    if (it.hasNext()) {
                        event = it.next();
                    }
                }
            }
        }

        return event;
    }

    /**
     * Get the latest event from the given room (to update summary for example)
     * @param roomId the room id
     * @return the event
     */
    public Event getLatestEvent(String roomId) {
        Event event = null;

        if (null != roomId) {
            synchronized (mRoomEvents) {
                LinkedHashMap<String, Event> events = mRoomEvents.get(roomId);

                if (events != null) {
                    Iterator<Event> it = events.values().iterator();
                    if (it.hasNext()) {
                        Event lastEvent = null;

                        while (it.hasNext()) {
                            lastEvent = it.next();
                        }

                        event = lastEvent;
                    }
                }
            }
        }
        return event;
    }

    /**
     * Count the number of events after the provided events id
     * @param roomId the room id.
     * @param eventId the event id to find.
     * @return the events count after this event if
     */
    public int eventsCountAfter(String roomId, String eventId) {
        return eventsAfter(roomId, eventId,  mCredentials.userId, null).size();
    }

    @Override
    public void storeLiveRoomEvent(Event event) {
        if ((null != event) && (null != event.roomId)) {
            synchronized (mRoomEvents) {
                // check if the message is already defined
                if (!doesEventExist(event.eventId, event.roomId)) {
                    LinkedHashMap<String, Event> events = mRoomEvents.get(event.roomId);
                    if (events != null) {
                        // If we don't have any information on this room - a pagination token, namely - we don't store the event but instead
                        // wait for the first pagination request to set things right
                        events.put(event.eventId, event);
                    }
                }
            }
        }
    }

    @Override
    public Boolean doesEventExist(String eventId, String roomId) {
        Boolean res = false;

        if (!TextUtils.isEmpty(eventId) && !TextUtils.isEmpty(roomId)) {
            ArrayList<String> eventIds = mRoomEventIds.get(roomId);

            if (null == eventIds) {
                eventIds = new ArrayList<String>();
                mRoomEventIds.put(roomId, eventIds);
            }

            res = eventIds.indexOf(eventId) >= 0;
        }

        return res;
    }

    @Override
    public void deleteEvent(Event event) {
        if ((null != event) && (null != event.roomId)) {
            synchronized (mRoomEvents) {
                LinkedHashMap<String, Event> events = mRoomEvents.get(event.roomId);
                if ((events != null) && (event.eventId != null)) {
                    events.remove(event.eventId);
                }
            }
        }
    }

    @Override
    public void deleteRoom(String roomId) {
    	// sanity check
        if (null != roomId) {
            synchronized (mRoomEvents) {
                mRooms.remove(roomId);
                mRoomEvents.remove(roomId);
                mRoomTokens.remove(roomId);
                mRoomSummaries.remove(roomId);
                mRoomAccountData.remove(roomId);
                mReceiptsByRoomId.remove(roomId);
            }
        }
    }

    /**
     * Remove all sent messages in a room.
     * @param roomId the id of the room.
     * @param keepUnsent set to true to do not delete the unsent message
     */
    public void deleteAllRoomMessages(String roomId, Boolean keepUnsent) {
        // sanity check
        if (null != roomId) {
            synchronized (mRoomEvents) {

                if (keepUnsent) {
                    ArrayList<String> eventIds = mRoomEventIds.get(roomId);
                    LinkedHashMap<String, Event> eventMap = mRoomEvents.get(roomId);

                    if (null != eventMap) {
                        ArrayList<Event> events = new ArrayList<Event>(eventMap.values());

                        for (Event event : events) {
                            if (event.mSentState == Event.SentState.SENT) {
                                if (null != event.eventId) {
                                    eventMap.remove(event.eventId);
                                    eventIds.remove(event.eventId);
                                }
                            }
                        }
                    }
                } else {
                    mRoomEventIds.remove(roomId);
                    mRoomEvents.remove(roomId);
                }

                mRoomSummaries.remove(roomId);
            }
        }
    }

    @Override
    public void storeRoomEvents(String roomId, TokensChunkResponse<Event> eventsResponse, Room.EventDirection direction) {
        if (null != roomId) {
            synchronized (mRoomEvents) {
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
    }

    @Override
    public boolean updateEventContent(String roomId, String eventId, JsonObject newContent) {
        if (null != roomId) {
            LinkedHashMap<String, Event> events = mRoomEvents.get(roomId);
            if (events != null) {
                Event event = null;

                synchronized (mRoomEvents) {
                    event = events.get(eventId);
                }

                if (event != null) {
                    event.content = newContent;
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public void flushSummary(RoomSummary summary) {
    }

    @Override
    public void flushSummaries() {
    }

    @Override
    public void storeSummary(String roomId, Event event, RoomState roomState, String selfUserId) {
        if (null != roomId) {
            Room room = mRooms.get(roomId);
            if ((room != null) && (event != null)) { // Should always be true
                RoomSummary summary = mRoomSummaries.get(roomId);
                if (summary == null) {
                    summary = new RoomSummary();
                }
                summary.setMatrixId(mCredentials.userId);
                summary.setLatestEvent(event);
                summary.setLatestRoomState(roomState);
                summary.setName(room.getName(selfUserId));
                summary.setRoomId(room.getRoomId());
                summary.setTopic(room.getTopic());

                mRoomSummaries.put(roomId, summary);
            }
        }
    }

    @Override
    public void storeAccountData(String roomId, RoomAccountData accountData) {
        if (null != roomId) {
            Room room = mRooms.get(roomId);

            // sanity checks
            if ((room != null) && (null != accountData)) {
                mRoomAccountData.put(roomId, accountData);
            }
        }
    }

    @Override
    public void storeLiveStateForRoom(String roomId) {
    }

    /**
     * Retrieve all non-state room events for this room.
     * @param roomId The room ID
     * @return A collection of events. null if there is no cached event.
     */
    public Collection<Event> getRoomMessages(final String roomId) {
        // sanity check
        if (null == roomId) {
            return null;
        }

        LinkedHashMap<String, Event> events = mRoomEvents.get(roomId);

        // unknown room ?
        if (null == events) {
            return null;
        }

        return new ArrayList<Event>(events.values());
    }

    @Override
    public TokensChunkResponse<Event> getEarlierMessages(final String roomId, final String fromToken, final int limit)  {
        // For now, we return everything we have for the original null token request
        // For older requests (providing a token), returning null for now
        if (null != roomId) {
            LinkedHashMap<String, Event> events = mRoomEvents.get(roomId);
            if ((events == null) || (events.size() == 0)) {
                return null;
            }

            // check if the token is known in the sublist
            ArrayList<Event> eventsList = new ArrayList<>(events.values());
            ArrayList<Event> subEventsList = new ArrayList<>();

            // search from the latest to the oldest events
            Collections.reverse(eventsList);

            TokensChunkResponse<Event> response = new TokensChunkResponse<Event>();

            // start the latest event and there is enough events to provide to the caller ?
            if ((null == fromToken) && (eventsList.size() <= limit)) {
                subEventsList = eventsList;
            } else {
                int index = 0;

                if (null != fromToken) {
                    // search if token is one of the stored events
                    for (; (index < eventsList.size()) && (!TextUtils.equals(fromToken,eventsList.get(index).mToken)); index++)
                        ;

                    index++;
                }

                // found it ?
                if (index < eventsList.size()) {
                    for(;index < eventsList.size(); index++) {
                        Event event = eventsList.get(index);
                        subEventsList.add(event);

                        // loop until to find an event with a token
                        if ((subEventsList.size() >= limit) &&  (event.mToken != null)) {
                            break;
                        }
                    }
                }
            }

            // unknown token
            if (subEventsList.size() == 0) {
                return null;
            }

            response.chunk = subEventsList;

            Event firstEvent = subEventsList.get(0);
            Event lastEvent = subEventsList.get(subEventsList.size()-1);

            response.start = firstEvent.mToken;
            response.end = lastEvent.mToken;

            // unknown last event token, use the latest known one
            if (response.end == null) {
                response.end = mRoomTokens.get(roomId);
            }

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

        synchronized (mRoomEvents) {
            LinkedHashMap<String, Event> events = mRoomEvents.get(roomId);

            // contain some events
            if ((null != events) && (events.size() > 0)) {
                ArrayList<Event> eventsList = new ArrayList(events.values());

                for (int index = events.size() - 1; index >= 0; index--) {
                    Event event = eventsList.get(index);

                    if (event.mSentState == Event.SentState.WAITING_RETRY) {
                        unsentRoomEvents.add(event);
                    } else {
                        //break;
                    }
                }

                Collections.reverse(unsentRoomEvents);
            }
        }

        return unsentRoomEvents;
    }

    /**
     * Returns the receipts list for an event in a dedicated room.
     * if sort is set to YES, they are sorted from the latest to the oldest ones.
     * @param roomId The room Id.
     * @param eventId The event Id.
     * @param excludeSelf exclude the oneself read receipts.
     * @param sort to sort them from the latest to the oldest
     * @return the receipts for an event in a dedicated room.
     */
    public List<ReceiptData> getEventReceipts(String roomId, String eventId, boolean excludeSelf, boolean sort) {
        ArrayList<ReceiptData> receipts = new ArrayList<ReceiptData>();

        if (mReceiptsByRoomId.containsKey(roomId)) {
            String myUserID = mCredentials.userId;

            Map<String, ReceiptData> receiptsByUserId = mReceiptsByRoomId.get(roomId);

            // copy the user id list to avoid having update while looping
            ArrayList<String> userIds = new ArrayList<String>(receiptsByUserId.keySet());

            for(String userId : userIds) {

                if (receiptsByUserId.containsKey(userId) && (!excludeSelf || !TextUtils.equals(myUserID, userId))) {
                    ReceiptData receipt = receiptsByUserId.get(userId);

                    if (TextUtils.equals(receipt.eventId, eventId)) {
                        receipts.add(receipt);
                    }
                }
            }
        }

        if (sort && (receipts.size() > 0)) {
            Collections.sort(receipts, ReceiptData.descComparator);
        }

        return receipts;
    }

    /**
     * Store the receipt for an user in a room
     * @param receipt The event
     * @param roomId The roomId
     * @return true if the receipt has been stored
     */
    public boolean storeReceipt(ReceiptData receipt, String roomId) {
        Map<String, ReceiptData> receiptsByUserId = null;

        if (!mReceiptsByRoomId.containsKey(roomId)) {
            receiptsByUserId = new HashMap<String, ReceiptData>();
            mReceiptsByRoomId.put(roomId, receiptsByUserId);
        } else {
            receiptsByUserId = mReceiptsByRoomId.get(roomId);
        }

        ReceiptData curReceipt = null;

        if (receiptsByUserId.containsKey(receipt.userId)) {
            curReceipt = receiptsByUserId.get(receipt.userId);
        }

        // not yet defined or a new event
        if ((null == curReceipt) || (!TextUtils.equals(receipt.eventId,curReceipt.eventId) && (receipt.originServerTs > curReceipt.originServerTs))) {
            receiptsByUserId.put(receipt.userId, receipt);
            return true;
        }

        return false;
    }

    /**
     * Return a list of stored events after the parameter one.
     * It could the ones sent by the user excludedUserId.
     * A filter can be applied to ignore some event (Event.EVENT_TYPE_...).
     *
     * @param roomId the roomId
     * @param eventId the start event Id.
     * @param excludedUserId the excluded user id
     * @param allowedTypes the filtered event type (null to allow anyone)
     * @return the evnts list
     */
    protected List<Event> eventsAfter(String roomId, String eventId, String excludedUserId, List<String> allowedTypes) {
        // events list
        ArrayList<Event> events = new ArrayList<Event>();

        // sanity check
        if ((null != roomId) && (null != eventId)) {
            synchronized (mRoomEvents) {
                LinkedHashMap<String, Event> roomEvents = mRoomEvents.get(roomId);

                if (roomEvents != null) {
                    Boolean gotIt = false;
                    Iterator<Event> it = roomEvents.values().iterator();
                    if (it.hasNext()) {
                        Event lastEvent;

                        while (it.hasNext()) {
                            lastEvent = it.next();

                            if (null == lastEvent.getSender()) {
                                Log.e(LOG_TAG, "Weird event with no user Id " + lastEvent);
                            } else if (gotIt) {
                                boolean isNotTypeFiltered = (null == allowedTypes) || (allowedTypes.indexOf(lastEvent.type) < 0);
                                boolean isNotSenderFiltered = (null == excludedUserId) || !TextUtils.equals(excludedUserId, lastEvent.getSender());

                                if (isNotTypeFiltered && isNotSenderFiltered) {
                                    events.add(lastEvent);
                                }
                            } else {
                                gotIt = TextUtils.equals(lastEvent.eventId, eventId);
                            }
                        }
                    }
                }
            }
        }

        return events;
    }

    /**
     * Provides the unread events list.
     * @param roomId the room id.
     * @param types an array of event types strings (Event.EVENT_TYPE_XXX).
     * @return the unread events list.
     */
    public List<Event> unreadEvents(String roomId, List<String> types) {
        if (mReceiptsByRoomId.containsKey(roomId)) {
            Map<String, ReceiptData> receiptsByUserId = mReceiptsByRoomId.get(roomId);

            if (receiptsByUserId.containsKey(mCredentials.userId)) {
                ReceiptData data = receiptsByUserId.get(mCredentials.userId);

                return eventsAfter(roomId, data.eventId, mCredentials.userId, types);
            }
        }

        return new ArrayList<Event>();
    }
}
