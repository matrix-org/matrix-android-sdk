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
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.ThirdPartyIdentifier;
import org.matrix.androidsdk.rest.model.TokensChunkResponse;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.rest.model.login.Credentials;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    //
    protected HashMap<String, Event> mTemporaryEventsList = new HashMap<String, Event>();

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
                myUser.setAvatarUrl(avatarURL);
            }

            Log.d(LOG_TAG, "setAvatarURL : commit");
            commit();
        }
    }

    public List<ThirdPartyIdentifier> thirdPartyIdentifiers() {
        return mMetadata.mThirdPartyIdentifiers;
    }

    public void setThirdPartyIdentifiers(List<ThirdPartyIdentifier> identifiers) {
        mMetadata.mThirdPartyIdentifiers = identifiers;

        Log.d(LOG_TAG, "setThirdPartyIdentifiers : commit");
        commit();
    }

    public List<String> getIgnoredUserIdsList() {
        return mMetadata.mIgnoredUsers;
    }

    public void setIgnoredUserIdsList(List<String> users) {
        mMetadata.mIgnoredUsers = users;
        Log.d(LOG_TAG, "setIgnoredUserIdsList : commit");
        commit();
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
        if ((null != user) && (null != user.user_id)) {
            mUsers.put(user.user_id, user);
        }
    }

    public void updateUserWithRoomMemberEvent(RoomMember roomMember) {
        if (null != roomMember) {
            User user = getUser(roomMember.getUserId());

            if (null == user) {
                user = new User();
                user.user_id = roomMember.getUserId();
                storeUser(user);
            }

            if (!TextUtils.equals(user.displayname, roomMember.displayname) || !TextUtils.equals(user.getAvatarUrl(), roomMember.avatarUrl)) {
                user.displayname = roomMember.displayname;
                user.setAvatarUrl(roomMember.avatarUrl);
            }
        }
    }

    @Override
    public void storeRoom(Room room) {
        if ((null != room) && (null != room.getRoomId())) {
            mRooms.put(room.getRoomId(), room);
            storeBackToken(room.getRoomId(), "");
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

                    // create the list it does not exist
                    if (null == events) {
                        events = new LinkedHashMap<String, Event>();
                        mRoomEvents.put(event.roomId, events);
                    } else if (!event.isDummyEvent() && (mTemporaryEventsList.size() > 0)) {
                        // remove any waiting echo event
                        String dummyKey = null;

                        for (String key : mTemporaryEventsList.keySet()) {
                            Event eventToCheck = mTemporaryEventsList.get(key);
                            if (TextUtils.equals(eventToCheck.eventId, event.eventId)) {
                                dummyKey = key;
                                break;
                            }
                        }

                        if (null != dummyKey) {
                            events.remove(dummyKey);
                            mTemporaryEventsList.remove(dummyKey);
                        }
                    }

                    // If we don't have any information on this room - a pagination token, namely - we don't store the event but instead
                    // wait for the first pagination request to set things right
                    events.put(event.eventId, event);

                    // add to the list of known events
                    ArrayList<String> eventIds = mRoomEventIds.get(event.roomId);
                    eventIds.add(event.eventId);

                    if (event.isDummyEvent()) {
                        mTemporaryEventsList.put(event.eventId, event);
                    }
                }
            }
        }
    }

    @Override
    public boolean doesEventExist(String eventId, String roomId) {
        boolean res = false;

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
    public Event getEvent(String eventId, String roomId) {
        Event event = null;

        if (doesEventExist(eventId,roomId)) {
            synchronized (mRoomEvents) {
                LinkedHashMap<String, Event> events = mRoomEvents.get(roomId);

                if (events != null) {
                    event = events.get(eventId);
                }
            }
        }

        return event;
    }

    @Override
    public void deleteEvent(Event event) {
        if ((null != event) && (null != event.roomId) && (event.eventId != null)) {
            synchronized (mRoomEvents) {

                LinkedHashMap<String, Event> events = mRoomEvents.get(event.roomId);
                if (events != null) {
                    events.remove(event.eventId);
                }

                ArrayList<String> ids = mRoomEventIds.get(event.roomId);
                if (null != ids) {
                    ids.remove(event.eventId);
                }
            }
        }
    }

    @Override
    public void deleteRoom(String roomId) {
    	// sanity check
        if (null != roomId) {
            deleteRoomData(roomId);
            synchronized (mRoomEvents) {
                mRooms.remove(roomId);
            }
        }
    }

    @Override
    public void deleteRoomData(String roomId) {
        // sanity check
        if (null != roomId) {
            synchronized (mRoomEvents) {
                mRoomEvents.remove(roomId);
                mRoomEventIds.remove(roomId);
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
    public void deleteAllRoomMessages(String roomId, boolean keepUnsent) {
        // sanity check
        if (null != roomId) {
            synchronized (mRoomEvents) {

                if (keepUnsent) {
                    LinkedHashMap<String, Event> eventMap = mRoomEvents.get(roomId);

                    if (null != eventMap) {
                        ArrayList<String> eventIds = mRoomEventIds.get(roomId);
                        ArrayList<Event> events = new ArrayList<Event>(eventMap.values());

                        for (Event event : events) {
                            if (event.mSentState == Event.SentState.SENT) {
                                if (null != event.eventId) {
                                    eventMap.remove(event.eventId);

                                    // sanity check
                                    if (null != eventIds) {
                                        eventIds.remove(event.eventId);
                                    }
                                }
                            }
                        }
                    } else {
                        mRoomEventIds.remove(roomId);
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
    public void storeRoomEvents(String roomId, TokensChunkResponse<Event> eventsResponse, EventTimeline.Direction direction) {
        if (null != roomId) {
            synchronized (mRoomEvents) {
                LinkedHashMap<String, Event> events = mRoomEvents.get(roomId);
                if (events == null) {
                    events = new LinkedHashMap<String, Event>();
                    mRoomEvents.put(roomId, events);
                }

                if (direction == EventTimeline.Direction.FORWARDS) {
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

    /**
     * Store the back token of a room.
     * @param roomId the room id.
     * @param backToken the back token
     */
    public void storeBackToken(String roomId, String backToken) {
        if ((null != roomId) && (null != backToken)) {
            mRoomTokens.put(roomId, backToken);
        }
    }

    @Override
    public boolean updateEventContent(String roomId, String eventId, JsonObject newContent) {
        if (null != roomId) {
            LinkedHashMap<String, Event> events = mRoomEvents.get(roomId);
            if (events != null) {
                Event eventToUpdate;

                synchronized (mRoomEvents) {
                    eventToUpdate = events.get(eventId);
                }

                if (eventToUpdate != null) {
                    eventToUpdate.updateContent(newContent);
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
    public RoomSummary storeSummary(String roomId, Event event, RoomState roomState, String selfUserId) {
        RoomSummary summary = null;

        if (null != roomId) {
            Room room = mRooms.get(roomId);
            if ((room != null) && (event != null)) { // Should always be true
                summary = mRoomSummaries.get(roomId);
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

        return summary;
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

        Collection<Event> collection = null;

        synchronized (mRoomEvents) {
            LinkedHashMap<String, Event> events = mRoomEvents.get(roomId);

            if (null != events) {
                collection = new ArrayList<Event>(events.values());
            }
        }

        return collection;
    }

    @Override
    public TokensChunkResponse<Event> getEarlierMessages(final String roomId, final String fromToken, final int limit)  {
        // For now, we return everything we have for the original null token request
        // For older requests (providing a token), returning null for now
        if (null != roomId) {
            ArrayList<Event> eventsList;

            synchronized (mRoomEvents) {
                LinkedHashMap<String, Event> events =mRoomEvents.get(roomId);
                if ((events == null) || (events.size() == 0)) {
                    return null;
                }

                // reach the end of the stored items
                if (TextUtils.equals(mRoomTokens.get(roomId), fromToken)) {
                    return null;
                }

                // check if the token is known in the sublist
                eventsList = new ArrayList<>(events.values());
            }


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

            // unknown last event token, use the latest known one
            if ((null == lastEvent.mToken) && !TextUtils.isEmpty(mRoomTokens.get(roomId))) {
                lastEvent.mToken = mRoomTokens.get(roomId);
            }

            response.end = lastEvent.mToken;

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
     * Return the list of undeliverable events
     * @param roomId the room id
     * @return  list of undeliverable events
     */
    public Collection<Event> getUndeliverableEvents(String roomId) {
        if (null == roomId) {
            return null;
        }

        ArrayList<Event> undeliverableRoomEvents = new ArrayList<Event>();

        synchronized (mRoomEvents) {
            LinkedHashMap<String, Event> events = mRoomEvents.get(roomId);

            // contain some events
            if ((null != events) && (events.size() > 0)) {
                ArrayList<Event> eventsList = new ArrayList(events.values());

                for (int index = events.size() - 1; index >= 0; index--) {
                    Event event = eventsList.get(index);

                    if (event.isUndeliverable()) {
                        undeliverableRoomEvents.add(event);
                    }
                }

                Collections.reverse(undeliverableRoomEvents);
            }
        }

        return undeliverableRoomEvents;
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

        synchronized (mReceiptsByRoomId) {
            if (mReceiptsByRoomId.containsKey(roomId)) {
                String myUserID = mCredentials.userId;

                Map<String, ReceiptData> receiptsByUserId = mReceiptsByRoomId.get(roomId);

                // copy the user id list to avoid having update while looping
                ArrayList<String> userIds = new ArrayList<String>(receiptsByUserId.keySet());

                for (String userId : userIds) {

                    if (receiptsByUserId.containsKey(userId) && (!excludeSelf || !TextUtils.equals(myUserID, userId))) {
                        ReceiptData receipt = receiptsByUserId.get(userId);

                        if (TextUtils.equals(receipt.eventId, eventId)) {
                            receipts.add(receipt);
                        }
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
        Map<String, ReceiptData> receiptsByUserId;

        synchronized (mReceiptsByRoomId) {
            if (!mReceiptsByRoomId.containsKey(roomId)) {
                receiptsByUserId = new HashMap<String, ReceiptData>();
                mReceiptsByRoomId.put(roomId, receiptsByUserId);
            } else {
                receiptsByUserId = mReceiptsByRoomId.get(roomId);
            }
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
     * Get the receipt for an user in a dedicated room.
     * @param roomId the room id.
     * @param userId the user id.
     * @return the dedicated receipt
     */
    public ReceiptData getReceipt(String roomId, String userId) {
        ReceiptData res = null;

        // sanity checks
        if (!TextUtils.isEmpty(roomId) && !TextUtils.isEmpty(userId)) {
            synchronized (mReceiptsByRoomId) {
                if (mReceiptsByRoomId.containsKey(roomId)) {
                    Map<String, ReceiptData> receipts = mReceiptsByRoomId.get(roomId);
                    res = receipts.get(userId);
                }
            }
        }

        return res;
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
        if (null != roomId) {
            synchronized (mRoomEvents) {
                LinkedHashMap<String, Event> roomEvents = mRoomEvents.get(roomId);

                if (roomEvents != null) {
                    List<Event> linkedEvents = new ArrayList<Event>(roomEvents.values());

                    // Check messages from the most recent
                    for (int i = linkedEvents.size() - 1; i >= 0 ; i--) {
                        Event event = linkedEvents.get(i);

                        if ((null == eventId) || !TextUtils.equals(event.eventId, eventId)) {
                            // Keep events matching filters
                            if ((null == allowedTypes || (allowedTypes.indexOf(event.type) >= 0)) && !TextUtils.equals(event.getSender(), excludedUserId)) {
                                events.add(event);
                            }
                        } else {
                            // We are done
                            break;
                        }
                    }

                    // filter the unread messages
                    // some messages are not defined as unreadable
                    for(int index = 0; index < events.size(); index++) {
                        Event event = events.get(index);

                        if (TextUtils.equals(event.getSender(), mCredentials.userId) || TextUtils.equals(event.type, Event.EVENT_TYPE_STATE_ROOM_MEMBER)) {
                            events.remove(index);
                            index--;
                        }
                    }

                    Collections.reverse(events);
                }
            }
        }

        // display the unread events
        if (0 == events.size()) {
            Log.d(LOG_TAG, "eventsAfter " + roomId + " - eventId " + eventId + " : no unread");
        } else {
            Log.d(LOG_TAG, "eventsAfter " + roomId + " - eventId " + eventId + " : " + events.size() + " unreads : ");

            int index = 0;

            for(Event event : events) {
                Log.d(LOG_TAG, "- Event " + index + " : " + event.eventId);
                index++;
            }
        }

        return events;
    }

    /**
     * Check if an event has been read by an user.
     * @param roomId the room Id
     * @param userId the user id
     * @param eventId the event id
     * @return true if the user has read the message.
     */
    public boolean isEventRead(String roomId, String userId, String eventId) {
        boolean res = false;

        // sanity check
        if ((null != roomId) && (null != userId)) {
            synchronized (mReceiptsByRoomId) {
                if (mReceiptsByRoomId.containsKey(roomId) && mRoomEvents.containsKey(roomId)) {
                    Map<String, ReceiptData> receiptsByUserId = mReceiptsByRoomId.get(roomId);
                    LinkedHashMap<String, Event> events = mRoomEvents.get(roomId);

                    // check if the event is known
                    if (events.containsKey(eventId) && receiptsByUserId.containsKey(userId)) {
                        Event event = events.get(eventId);
                        ReceiptData data = receiptsByUserId.get(userId);

                        res = event.originServerTs < data.originServerTs;
                    } else if (receiptsByUserId.containsKey(userId)) {
                        // the event is not known so assume it is has been flushed
                        res = true;
                    }
                }
            }
        }

        return res;
    }

    /**
     * Provides the unread events list.
     * @param roomId the room id.
     * @param types an array of event types strings (Event.EVENT_TYPE_XXX).
     * @return the unread events list.
     */
    public List<Event> unreadEvents(String roomId, List<String> types) {
        List<Event> res = null;

        synchronized (mReceiptsByRoomId) {
            if (mReceiptsByRoomId.containsKey(roomId)) {
                Map<String, ReceiptData> receiptsByUserId = mReceiptsByRoomId.get(roomId);

                if (receiptsByUserId.containsKey(mCredentials.userId)) {
                    ReceiptData data = receiptsByUserId.get(mCredentials.userId);

                    res = eventsAfter(roomId, data.eventId, mCredentials.userId, types);
                }
            }
        }

        if (null == res) {
            res = new ArrayList<Event>();
        }

        return res;
    }
}
