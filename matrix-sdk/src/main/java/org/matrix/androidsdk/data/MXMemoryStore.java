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

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import org.matrix.androidsdk.crypto.MXDeviceInfo;
import org.matrix.androidsdk.crypto.MXUsersDevicesMap;
import org.matrix.androidsdk.crypto.algorithms.data.MXOlmInboundGroupSession;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.ReceiptData;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.ThirdPartyIdentifier;
import org.matrix.androidsdk.rest.model.TokensChunkResponse;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.rest.model.login.Credentials;
import org.matrix.olm.OlmAccount;
import org.matrix.olm.OlmSession;

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

    private static final Object mRoomEventsLock = new Object();

    // room id -> map of (event_id -> event) events for this room (linked so insertion order is preserved)
    protected Map<String, LinkedHashMap<String, Event>> mRoomEvents;
    // room id -> list of event Ids
    protected Map<String, ArrayList<String>> mRoomEventIds;

    protected Map<String, String> mRoomTokens;

    protected Map<String, RoomSummary> mRoomSummaries;
    protected Map<String, RoomAccountData> mRoomAccountData;

    // dict of dict of MXReceiptData indexed by userId
    private final Object mReceiptsByRoomIdLock = new Object();
    protected Map<String, Map<String, ReceiptData>> mReceiptsByRoomId;

    // common context
    private static Context mSharedContext = null;

    // the context
    protected Context mContext;

    //
    private final HashMap<String, Event> mTemporaryEventsList = new HashMap<>();

    protected Credentials mCredentials;

    protected String mEventStreamToken = null;

    protected MXStoreListener mListener = null;

    // Meta data about the store. It is defined only if the passed MXCredentials contains all information.
    // When nil, nothing is stored on the file system.
    protected MXFileStoreMetaData mMetadata = null;

    // Crypto
    // The olm account
    protected OlmAccount mOlmAccount;

    // All users devices keys
    protected MXUsersDevicesMap<MXDeviceInfo> mUsersDevicesInfoMap;

    // The algorithms used in rooms
    protected HashMap<String, String> mRoomsAlgorithms;

    // The olm sessions (<device identity key> -> (<olm session id> -> <olm session>)
    protected HashMap<String /*deviceKey*/,
              HashMap<String /*olmSessionId*/,OlmSession>> mOlmSessions;

    private boolean mEndToEndDeviceAnnounced;


    /**
     * Initialization method.
     */
    protected void initCommon(){
        mRooms = new ConcurrentHashMap<>();
        mUsers = new ConcurrentHashMap<>();
        mRoomEvents = new ConcurrentHashMap<>();
        mRoomEventIds = new ConcurrentHashMap<>();
        mRoomTokens = new ConcurrentHashMap<>();
        mRoomSummaries = new ConcurrentHashMap<>();
        mReceiptsByRoomId = new ConcurrentHashMap<>();
        mRoomAccountData = new ConcurrentHashMap<>();
        mEventStreamToken = null;

        mUsersDevicesInfoMap = new MXUsersDevicesMap<>(null);
        mRoomsAlgorithms = new HashMap<>();
        mOlmSessions = new HashMap<>();
    }

    public MXMemoryStore() {
        initCommon();
    }

    /**
     * Set the application context
     * @param context the context
     */
    protected void setContext(Context context) {
        if (null == mSharedContext) {
            if (null != context) {
                mSharedContext = context.getApplicationContext();
            } else {
                throw new RuntimeException("MXMemoryStore : context cannot be null");
            }
        }

        mContext = mSharedContext;
    }

    /**
     * Default constructor
     * @param credentials the expected credentials
     */
    public MXMemoryStore(Credentials credentials, Context context) {
        initCommon();

        setContext(context);
        mCredentials = credentials;

        mMetadata = new MXFileStoreMetaData();
    }

    @Override
    public Context getContext() {
        return mContext;
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
     * @param listener the listener
     */
    @Override
    public void setMXStoreListener(MXStoreListener listener) {
        mListener = listener;
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
                mMetadata.mUserDisplayName = mMetadata.mUserDisplayName.trim();
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
        return new ArrayList<>(mRooms.values());
    }

    @Override
    public Collection<User> getUsers() {
        return new ArrayList<>(mUsers.values());
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
            try {
                mUsers.put(user.user_id, user);
            } catch (OutOfMemoryError e) {
                dispatchOOM(e);
            }
        }
    }

    /**
     * Update the user information from a room member.
     * @param roomMember the room member.
     */
    public void updateUserWithRoomMemberEvent(RoomMember roomMember) {
        try {
            if (null != roomMember) {
                User user = getUser(roomMember.getUserId());

                // if the user does not exist, create it
                if (null == user) {
                    user = new User();
                    user.user_id = roomMember.getUserId();
                    storeUser(user);
                }

                // update the display name and the avatar url.
                // the leave and ban events have no displayname and no avatar url.
                if (TextUtils.equals(roomMember.membership, RoomMember.MEMBERSHIP_JOIN)) {
                    boolean hasUpdates = !TextUtils.equals(user.displayname, roomMember.displayname) || !TextUtils.equals(user.getAvatarUrl(), roomMember.avatarUrl);

                    if (hasUpdates) {
                        // invite event does not imply that the user uses the application.
                        // but if the presence is set to 0, it means that the user information is not initialized
                        if (user.getLatestPresenceTs() < roomMember.getOriginServerTs()) {
                            // if the user joined the room, it implies that he used the application
                            user.displayname = roomMember.displayname;
                            user.setAvatarUrl(roomMember.avatarUrl);
                            user.setLatestPresenceTs(roomMember.getOriginServerTs());
                        }
                    }
                }
            }
        } catch (OutOfMemoryError e) {
            dispatchOOM(e);
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
            synchronized (mRoomEventsLock) {
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
            synchronized (mRoomEventsLock) {
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
        try {
            if ((null != event) && (null != event.roomId)) {
                synchronized (mRoomEventsLock) {
                    // check if the message is already defined
                    if (!doesEventExist(event.eventId, event.roomId)) {
                        LinkedHashMap<String, Event> events = mRoomEvents.get(event.roomId);

                        // create the list it does not exist
                        if (null == events) {
                            events = new LinkedHashMap<>();
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
        } catch (OutOfMemoryError e) {
            dispatchOOM(e);
        }
    }

    @Override
    public boolean doesEventExist(String eventId, String roomId) {
        boolean res = false;

        if (!TextUtils.isEmpty(eventId) && !TextUtils.isEmpty(roomId)) {
            ArrayList<String> eventIds = mRoomEventIds.get(roomId);

            if (null == eventIds) {
                eventIds = new ArrayList<>();
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
            synchronized (mRoomEventsLock) {
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
            synchronized (mRoomEventsLock) {

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
            synchronized (mRoomEventsLock) {
                mRooms.remove(roomId);
            }
        }
    }

    @Override
    public void deleteRoomData(String roomId) {
        // sanity check
        if (null != roomId) {
            synchronized (mRoomEventsLock) {
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
            synchronized (mRoomEventsLock) {

                if (keepUnsent) {
                    LinkedHashMap<String, Event> eventMap = mRoomEvents.get(roomId);

                    if (null != eventMap) {
                        ArrayList<String> eventIds = mRoomEventIds.get(roomId);
                        ArrayList<Event> events = new ArrayList<>(eventMap.values());

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
        try {
            if (null != roomId) {
                synchronized (mRoomEventsLock) {
                    LinkedHashMap<String, Event> events = mRoomEvents.get(roomId);
                    if (events == null) {
                        events = new LinkedHashMap<>();
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
                            LinkedHashMap<String, Event> events2 = new LinkedHashMap<>();

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
        } catch (OutOfMemoryError e) {
            dispatchOOM(e);
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
    public void flushSummary(RoomSummary summary) {
    }

    @Override
    public void flushSummaries() {
    }

    @Override
    public RoomSummary storeSummary(String roomId, Event event, RoomState roomState, String selfUserId) {
        RoomSummary summary = null;

        try {
            if (null != roomId) {
                Room room = mRooms.get(roomId);
                if ((room != null) && (event != null)) { // Should always be true
                    summary = mRoomSummaries.get(roomId);
                    if (summary == null) {
                        summary = new RoomSummary();
                    }
                    summary.setMatrixId(mCredentials.userId);
                    summary.setLatestReceivedEvent(event);
                    summary.setLatestRoomState(roomState);
                    summary.setName(room.getName(selfUserId));
                    summary.setRoomId(room.getRoomId());
                    summary.setTopic(room.getTopic());

                    mRoomSummaries.put(roomId, summary);
                }
            }
        } catch (OutOfMemoryError e) {
            dispatchOOM(e);
        }

        return summary;
    }

    @Override
    public void storeAccountData(String roomId, RoomAccountData accountData) {
        try {
            if (null != roomId) {
                Room room = mRooms.get(roomId);

                // sanity checks
                if ((room != null) && (null != accountData)) {
                    mRoomAccountData.put(roomId, accountData);
                }
            }
        } catch (OutOfMemoryError e) {
            dispatchOOM(e);
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

        synchronized (mRoomEventsLock) {
            LinkedHashMap<String, Event> events = mRoomEvents.get(roomId);

            if (null != events) {
                collection = new ArrayList<>(events.values());
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

            synchronized (mRoomEventsLock) {
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

            TokensChunkResponse<Event> response = new TokensChunkResponse<>();

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

        ArrayList<Event> unsentRoomEvents = new ArrayList<>();

        synchronized (mRoomEventsLock) {
            LinkedHashMap<String, Event> events = mRoomEvents.get(roomId);

            // contain some events
            if ((null != events) && (events.size() > 0)) {
                ArrayList<Event> eventsList = new ArrayList<>(events.values());

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

        ArrayList<Event> undeliverableRoomEvents = new ArrayList<>();

        synchronized (mRoomEventsLock) {
            LinkedHashMap<String, Event> events = mRoomEvents.get(roomId);

            // contain some events
            if ((null != events) && (events.size() > 0)) {
                ArrayList<Event> eventsList = new ArrayList<>(events.values());

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
        ArrayList<ReceiptData> receipts = new ArrayList<>();

        synchronized (mReceiptsByRoomIdLock) {
            if (mReceiptsByRoomId.containsKey(roomId)) {
                String myUserID = mCredentials.userId;

                Map<String, ReceiptData> receiptsByUserId = mReceiptsByRoomId.get(roomId);

                // copy the user id list to avoid having update while looping
                ArrayList<String> userIds = new ArrayList<>(receiptsByUserId.keySet());

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
     * Store the receipt for an user in a room.
     * The receipt validity is checked i.e the receipt is not for an already read message.
     * @param receipt The event
     * @param roomId The roomId
     * @return true if the receipt has been stored
     */
    public boolean storeReceipt(ReceiptData receipt, String roomId) {
        try {
            // sanity check
            if (TextUtils.isEmpty(roomId) || (null == receipt)) {
                return false;
            }

            Map<String, ReceiptData> receiptsByUserId;

            //Log.d(LOG_TAG, "## storeReceipt() : roomId " + roomId + " userId " + receipt.userId + " eventId " + receipt.eventId + " originServerTs " + receipt.originServerTs);

            synchronized (mReceiptsByRoomIdLock) {
                if (!mReceiptsByRoomId.containsKey(roomId)) {
                    receiptsByUserId = new HashMap<>();
                    mReceiptsByRoomId.put(roomId, receiptsByUserId);
                } else {
                    receiptsByUserId = mReceiptsByRoomId.get(roomId);
                }
            }

            ReceiptData curReceipt = null;

            if (receiptsByUserId.containsKey(receipt.userId)) {
                curReceipt = receiptsByUserId.get(receipt.userId);
            }

            if (null == curReceipt) {
                //Log.d(LOG_TAG, "## storeReceipt() : there was no receipt from this user");
                receiptsByUserId.put(receipt.userId, receipt);
                return true;
            }

            if (TextUtils.equals(receipt.eventId, curReceipt.eventId)) {
                //Log.d(LOG_TAG, "## storeReceipt() : receipt for the same event");
                return false;
            }

            if (receipt.originServerTs < curReceipt.originServerTs) {
                //Log.d(LOG_TAG, "## storeReceipt() : the receipt is older that the current one");
                return false;
            }

            // check if the read receipt is not for an already read message
            if (TextUtils.equals(receipt.userId, mCredentials.userId)) {
                synchronized (mReceiptsByRoomIdLock) {
                    LinkedHashMap<String, Event> eventsMap = mRoomEvents.get(roomId);

                    // test if the event is know
                    if (eventsMap.containsKey(receipt.eventId)) {
                        ArrayList<String> eventIds = new ArrayList<>(eventsMap.keySet());

                        int curEventPos = eventIds.indexOf(curReceipt.eventId);
                        int newEventPos = eventIds.indexOf(receipt.eventId);

                        if (curEventPos >= newEventPos) {
                            Log.d(LOG_TAG, "## storeReceipt() : the read message is already read (cur pos " + curEventPos + " receipt event pos " + newEventPos + ")");
                            return false;
                        }
                    }
                }
            }

            //Log.d(LOG_TAG, "## storeReceipt() : updated");
            receiptsByUserId.put(receipt.userId, receipt);
        } catch (OutOfMemoryError e) {
            dispatchOOM(e);
        }

        return true;
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
            synchronized (mReceiptsByRoomIdLock) {
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
    private List<Event> eventsAfter(String roomId, String eventId, String excludedUserId, List<String> allowedTypes) {
        // events list
        ArrayList<Event> events = new ArrayList<>();

        // sanity check
        if (null != roomId) {
            synchronized (mRoomEventsLock) {
                LinkedHashMap<String, Event> roomEvents = mRoomEvents.get(roomId);

                if (roomEvents != null) {
                    List<Event> linkedEvents = new ArrayList<>(roomEvents.values());

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
            Log.d(LOG_TAG, "eventsAfter " + roomId + " - eventId " + eventId + " : " + events.size() + " unreads");

            // too many traces
            /*int index = 0;

            for(Event event : events) {
                Log.d(LOG_TAG, "- Event " + index + " : " + event.eventId);
                index++;
            }*/
        }

        return events;
    }

    /**
     * Check if an event has been read by an user.
     * @param roomId the room Id
     * @param userId the user id
     * @param eventIdTotest the event id
     * @return true if the user has read the message.
     */
    public boolean isEventRead(String roomId, String userId, String eventIdTotest) {
        boolean res = false;

        // sanity check
        if ((null != roomId) && (null != userId)) {
            synchronized (mReceiptsByRoomIdLock) {
                if (mReceiptsByRoomId.containsKey(roomId) && mRoomEvents.containsKey(roomId)) {
                    Map<String, ReceiptData> receiptsByUserId = mReceiptsByRoomId.get(roomId);
                    LinkedHashMap<String, Event> eventsMap = mRoomEvents.get(roomId);

                    // check if the event is known
                    if (eventsMap.containsKey(eventIdTotest) && receiptsByUserId.containsKey(userId)) {
                        ReceiptData data = receiptsByUserId.get(userId);
                        ArrayList<String> eventIds = new ArrayList<>(eventsMap.keySet());

                        // the message has been read if it was sent before the latest read one
                        res = eventIds.indexOf(eventIdTotest) <=  eventIds.indexOf(data.eventId);
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

        synchronized (mReceiptsByRoomIdLock) {
            if (mReceiptsByRoomId.containsKey(roomId)) {
                Map<String, ReceiptData> receiptsByUserId = mReceiptsByRoomId.get(roomId);

                if (receiptsByUserId.containsKey(mCredentials.userId)) {
                    ReceiptData data = receiptsByUserId.get(mCredentials.userId);

                    res = eventsAfter(roomId, data.eventId, mCredentials.userId, types);
                }
            }
        }

        if (null == res) {
            res = new ArrayList<>();
        }

        return res;
    }

    /**
     * Dispatch oom error to the listener.
     * @param e the error
     */
    protected void dispatchOOM(OutOfMemoryError e) {
        Log.e(LOG_TAG, "## dispatchOOM() : " + e.getMessage());

        if (null != mListener) {
            mListener.onStoreOOM(mCredentials.userId, e.getLocalizedMessage());
        }
    }

    //==============================================================================================================
    // Crypto
    //==============================================================================================================

    @Override
    public void storeEndToEndAccount(OlmAccount account) {
        mOlmAccount = account;
    }

    @Override
    public OlmAccount endToEndAccount() {
        return mOlmAccount;
    }

    @Override
    public void storeEndToEndDeviceAnnounced() {
        mEndToEndDeviceAnnounced = true;
    }

    @Override
    public boolean endToEndDeviceAnnounced() {
        return mEndToEndDeviceAnnounced;
    }

    @Override
    public void storeEndToEndDeviceForUser(String userId, MXDeviceInfo device) {
        mUsersDevicesInfoMap.setObject(device, userId, device.deviceId);
    }

    @Override
    public MXDeviceInfo endToEndDeviceWithDeviceId(String deviceId, String userId) {
        return mUsersDevicesInfoMap.objectForDevice(deviceId, userId);
    }

    @Override
    public void storeEndToEndDevicesForUser(String userId, Map<String, MXDeviceInfo> devices) {
        mUsersDevicesInfoMap.setObjects(devices, userId);
    }

    @Override
    public Map<String, MXDeviceInfo> endToEndDevicesForUser(String userId) {
        if (!TextUtils.isEmpty(userId)) {
            return mUsersDevicesInfoMap.getMap().get(userId);
        } else {
            return null;
        }
    }

    @Override
    public void storeEndToEndAlgorithmForRoom(String roomId, String algorithm) {
        if (!TextUtils.isEmpty(roomId)) {
            if (null == algorithm) {
                mRoomsAlgorithms.remove(roomId);
            } else {
                mRoomsAlgorithms.put(roomId, algorithm);
            }
        }
    }

    @Override
    public String endToEndAlgorithmForRoom(String roomId) {
        if (!TextUtils.isEmpty(roomId)) {
            return mRoomsAlgorithms.get(roomId);
        }

        return null;
    }

    @Override
    public void storeEndToEndSession(OlmSession session, String deviceKey) {
        if (!TextUtils.isEmpty(deviceKey) && (null != session)) {
            HashMap<String, OlmSession> subMap = mOlmSessions.get(deviceKey);

            if (null == subMap) {
                subMap = new HashMap<>();
                mOlmSessions.put(deviceKey, subMap);
            }

            subMap.put(session.sessionIdentifier(), session);
        }
    }

    @Override
    public Map<String, OlmSession> endToEndSessionsWithDevice(String deviceKey) {
        if (!TextUtils.isEmpty(deviceKey)) {
            return mOlmSessions.get(deviceKey);
        }

        return null;
    }

    @Override
    public void storeEndToEndInboundGroupSession(MXOlmInboundGroupSession session) {
        // TODO : not yet implemented
    }

    @Override
    public MXOlmInboundGroupSession endToEndInboundGroupSessionWithId(String sessionId, String senderKey) {
        // TODO : not yet implemented
        return null;
    }
}
