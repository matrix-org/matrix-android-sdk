/*
 * Copyright 2014 OpenMarket Ltd
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
package org.matrix.androidsdk;

import android.util.Log;

import org.matrix.androidsdk.call.MXCallsManager;
import org.matrix.androidsdk.data.DataRetriever;
import org.matrix.androidsdk.data.IMXStore;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.data.RoomSummary;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.listeners.IMXEventListener;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.RoomResponse;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.rest.model.bingrules.BingRule;
import org.matrix.androidsdk.rest.model.bingrules.BingRuleSet;
import org.matrix.androidsdk.rest.model.login.Credentials;
import org.matrix.androidsdk.util.BingRulesManager;
import org.matrix.androidsdk.util.ContentManager;
import org.matrix.androidsdk.util.JsonUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The data handler provides a layer to help manage matrix input and output.
 * <ul>
 * <li>Handles events</li>
 * <li>Stores the data in its storage layer</li>
 * <li>Provides the means for an app to get callbacks for data changes</li>
 * </ul>
 */
public class MXDataHandler implements IMXEventListener {
    private static final String LOG_TAG = "MXData";

    private List<IMXEventListener> mEventListeners = new ArrayList<IMXEventListener>();

    private IMXStore mStore;
    private Credentials mCredentials;
    private volatile boolean mInitialSyncComplete = false;
    private DataRetriever mDataRetriever;
    private BingRulesManager mBingRulesManager;
    private ContentManager mContentManager;
    private MXCallsManager mCallsManager;
    private MXMediasCache mMediasCache;

    private Boolean mIsActive = true;

    /**
     * Default constructor.
     * @param store the data storage implementation.
     */
    public MXDataHandler(IMXStore store, Credentials credentials) {
        mStore = store;
        mCredentials = credentials;
    }

    private void checkIfActive() {
        synchronized (this) {
            if (!mIsActive) {
                throw new AssertionError("Should not used a MXDataHandler");
            }
        }
    }

    private Boolean isActive() {
        synchronized (this) {
            return mIsActive;
        }
    }

    /**
     * @return true if the initial sync is completed.
     */
    public boolean isInitialSyncComplete() {
        checkIfActive();
        return mInitialSyncComplete;
    }

    public void setDataRetriever(DataRetriever dataRetriever) {
        checkIfActive();
        mDataRetriever = dataRetriever;
        mDataRetriever.setStore(mStore);
    }

    public void setPushRulesManager(BingRulesManager bingRulesManager) {
        checkIfActive();
        mBingRulesManager = bingRulesManager;
        mBingRulesManager.loadRules(new SimpleApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                MXDataHandler.this.onBingRulesUpdate();
            }
        });
    }

    public void setContentManager(ContentManager contentManager) {
        checkIfActive();
        mContentManager = contentManager;
    }

    public void setCallsManager(MXCallsManager callsManager) {
        checkIfActive();
        mCallsManager = callsManager;
    }

    public void setMediasCache(MXMediasCache mediasCache) {
        checkIfActive();
        mMediasCache = mediasCache;
    }

    public BingRuleSet pushRules() {
        checkIfActive();
        if (null != mBingRulesManager) {
            return mBingRulesManager.pushRules();
        }

        return null;
    }

    public void refreshPushRules() {
        checkIfActive();
        if (null != mBingRulesManager) {
            mBingRulesManager.loadRules(new SimpleApiCallback<Void>() {
                @Override
                public void onSuccess(Void info) {
                    MXDataHandler.this.onBingRulesUpdate();
                }
            });
        }
    }

    public BingRulesManager getBingRulesManager() {
        checkIfActive();
        return mBingRulesManager;
    }

    public void addListener(IMXEventListener listener) {
        checkIfActive();
        synchronized (this) {
            // avoid adding twice
            if (mEventListeners.indexOf(listener) == -1) {
                mEventListeners.add(listener);
            }
        }

        if (mInitialSyncComplete) {
            listener.onInitialSyncComplete();
        }
    }

    public void removeListener(IMXEventListener listener) {
        checkIfActive();
        synchronized (this) {
            mEventListeners.remove(listener);
        }
    }

    public void clear() {
        synchronized (this) {
            mIsActive = false;
            // remove any listener
            mEventListeners.clear();
        }

        // clear the store
        mStore.close();
        mStore.clear();
    }
    /**
     * Handle the room data received from a per-room initial sync
     * @param roomResponse the room response object
     * @param room the associated room
     */
    public void handleInitialRoomResponse(RoomResponse roomResponse, Room room) {
        checkIfActive();

        // Handle state events
        if (roomResponse.state != null) {
            room.processLiveState(roomResponse.state);
        }

        // Handle visibility
        if (roomResponse.visibility != null) {
            room.setVisibility(roomResponse.visibility);
        }

        // Handle messages / pagination token
        if ((roomResponse.messages != null) && (roomResponse.messages.chunk.size() > 0)) {
            mStore.storeRoomEvents(room.getRoomId(), roomResponse.messages, Room.EventDirection.FORWARDS);

            // To store the summary, we need the last event and the room state from just before
            Event lastEvent = roomResponse.messages.chunk.get(roomResponse.messages.chunk.size() - 1);
            RoomState beforeLiveRoomState = room.getLiveState().deepCopy();
            beforeLiveRoomState.applyState(lastEvent, Room.EventDirection.BACKWARDS);

            mStore.storeSummary(getUserId(), room.getRoomId(), lastEvent, room.getLiveState(), mCredentials.userId);
        }

        // Handle presence
        if (roomResponse.presence != null) {
            handleLiveEvents(roomResponse.presence);
        }

        // Handle the special case where the room is an invite
        if (RoomMember.MEMBERSHIP_INVITE.equals(roomResponse.membership)) {
            handleInitialSyncInvite(room.getRoomId(), roomResponse.inviter);
        } else {
            onRoomInitialSyncComplete(room.getRoomId());
        }
    }

    /**
     * Handle the room data received from a global initial sync
     * @param roomResponse the room response object
     */
    public void handleInitialRoomResponse(RoomResponse roomResponse) {
        checkIfActive();
        if (roomResponse.roomId != null) {
            Room room = getRoom(roomResponse.roomId);
            handleInitialRoomResponse(roomResponse, room);
        }
    }

    /**
     * Update the missing data fields loaded from a permanent storage.
     */
    public void checkPermanentStorageData() {
        checkIfActive();

        if (mStore.isPermanent()) {
            // When the data are extracted from a persistent storage,
            // some fields are not retrieved :
            // They are used to retrieve some data
            // so add the missing links.

            Collection<Room> rooms =  mStore.getRooms();

            for(Room room : rooms) {
                room.setDataHandler(this);
                room.setDataRetriever(mDataRetriever);
                room.setMyUserId(mCredentials.userId);
                room.setContentManager(mContentManager);
            }

            Collection<RoomSummary> summaries = mStore.getSummaries();
            for(RoomSummary summary : summaries) {
                if (null != summary.getLatestRoomState()) {
                    summary.getLatestRoomState().setDataHandler(this);
                }
            }
        }
    }

    public String getUserId() {
        checkIfActive();
        return mCredentials.userId;
    }

    private void handleInitialSyncInvite(String roomId, String inviterUserId) {
        checkIfActive();

        Room room = getRoom(roomId);

        // add yourself
        RoomMember member = new RoomMember();
        member.membership = RoomMember.MEMBERSHIP_INVITE;
        room.setMember(mCredentials.userId, member);

        // and the inviter
        member = new RoomMember();
        member.membership = RoomMember.MEMBERSHIP_JOIN;
        room.setMember(inviterUserId, member);

        // Build a fake invite event
        Event inviteEvent = new Event();
        inviteEvent.roomId = roomId;
        inviteEvent.stateKey = mCredentials.userId;
        inviteEvent.userId = inviterUserId;
        inviteEvent.type = Event.EVENT_TYPE_STATE_ROOM_MEMBER;
        inviteEvent.setOriginServerTs(System.currentTimeMillis()); // This is where it's fake
        inviteEvent.content = JsonUtils.toJson(member);

        mStore.storeSummary(getUserId(), roomId, inviteEvent, null, mCredentials.userId);

        // Set the inviter ID
        RoomSummary roomSummary = mStore.getSummary(roomId);
        if (null != roomSummary) {
            roomSummary.setInviterUserId(inviterUserId);
        }
    }

    public IMXStore getStore() {
        checkIfActive();

        return mStore;
    }

    /**
     * Handle a list of events coming down from the event stream.
     * @param events the live events
     */
    public void handleLiveEvents(List<Event> events) {
        checkIfActive();

        for (Event event : events) {
            handleLiveEvent(event);
        }

        onLiveEventsChunkProcessed();

        // check if an incoming call has been received
        mCallsManager.checkPendingIncomingCalls();
    }

    /**
     * Returns the member with userID;
     * @param members the members List
     * @param userID the user ID
     * @return the roomMember if it exists.
     */
    public RoomMember getMember(Collection<RoomMember> members, String userID) {
        checkIfActive();

        for (RoomMember member : members) {
            if (userID.equals(member.getUserId())) {
                return member;
            }
        }
        return null;
    }

    /**
     * Check if the room should be joined
     * @param event
     * @param roomState
     * @return true of the room should be self joined.
     */
    private Boolean shouldSelfJoin(final Event event, final RoomState roomState) {
        checkIfActive();

        RoomMember member = JsonUtils.toRoomMember(event.content);

        // join event ?
        if (RoomMember.MEMBERSHIP_JOIN.equals(member.membership)) {
            Collection<RoomMember> members = roomState.getMembers();
            RoomMember myMember = getMember(members, mCredentials.userId);

            return ((null == myMember) || RoomMember.MEMBERSHIP_INVITE.equals(myMember.membership));
        }

        return false;
    }

    /**
     * Perform an initial room sync (get the metadata + get the first bunch of messages)
     * @param roomId the roomid of the room to join.
     */
    private void selfJoin(final String roomId) {
        checkIfActive();

        // inviterUserId is only used when the user is invited to the room found during the initial sync
        RoomSummary roomSummary = getStore().getSummary(roomId);
        roomSummary.setInviterUserId(null);

        final Room room = getStore().getRoom(roomId);
        room.initialSync(new SimpleApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                room.requestHistory(new SimpleApiCallback<Integer>() {
                    @Override
                    public void onSuccess(Integer info) {
                        onRoomInitialSyncComplete(roomId);
                    }
                });
            }
        });
    }

    /**
     * Handle events coming down from the event stream.
     * @param event the live event
     */
    private void handleLiveEvent(Event event) {
        checkIfActive();

        // dispatch the call events to the calls manager
        if (event.isCallEvent()) {
            mCallsManager.handleCallEvent(event);
        }

        // Presence event
        if (Event.EVENT_TYPE_PRESENCE.equals(event.type)) {
            User userPresence = JsonUtils.toUser(event.content);
            User user = mStore.getUser(userPresence.userId);

            if (user == null) {
                user = userPresence;
                user.lastActiveReceived();
                user.setDataHandler(this);
                mStore.storeUser(user);
            }
            else {
                user.presence = userPresence.presence;
                user.lastActiveAgo = userPresence.lastActiveAgo;
                user.lastActiveReceived();
            }

            // check if the current user has been updated
            if (mCredentials.userId.equals(user.userId)) {
                mStore.setAvatarURL(user.avatarUrl);
                mStore.setDisplayName(user.displayname);
            }

            this.onPresenceUpdate(event, user);
        }
        // Room event
        else if (event.roomId != null) {
            final Room room = getRoom(event.roomId);

            String selfJoinRoomId = null;

            // check if the room has been joined
            // the initial sync + the first requestHistory call is done here
            // instead of being done in the application
            if (Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(event.type) && event.userId.equals(mCredentials.userId) && shouldSelfJoin(event, room.getLiveState())) {
                selfJoinRoomId = event.roomId;
            }

            if (event.stateKey != null) {
                // check if the event has been processed
                if (!room.processStateEvent(event, Room.EventDirection.FORWARDS)) {
                    // not processed -> do not warn the application
                    // assume that the event is a duplicated one.
                    return;
                }
            }

            /**
             * Notice there is a tweak here for the member events
             * processStateEvent retrieves the avatar url from Event.prevContent
             * when the member leaves the rooms (the url is not included).
             * The whole content should be applied but it seems enough and more understandable
             * to update only the missing field.
             */

            RoomState liveStateCopy = room.getLiveState().deepCopy();
            storeLiveRoomEvent(event);
            onLiveEvent(event, liveStateCopy);

            if (null != selfJoinRoomId) {
                selfJoin(selfJoinRoomId);
            }

            BingRule bingRule;
            boolean outOfTimeEvent = false;

            if (event.content.has("lifetime")) {
                long maxlifetime = event.content.get("lifetime").getAsLong();
                long eventLifeTime = System.currentTimeMillis() - event.getOriginServerTs();

                outOfTimeEvent = eventLifeTime > maxlifetime;
            }

            // If the bing rules apply, bing
            if (!Event.EVENT_TYPE_TYPING.equals(event.type)
                    && !outOfTimeEvent
                    && (mBingRulesManager != null)
                    && (null != (bingRule = mBingRulesManager.fulfilledBingRule(event)))
                    && bingRule.shouldNotify()) {
                onBingEvent(event, liveStateCopy, bingRule);
            }
        }
        else {
            Log.e(LOG_TAG, "Unknown live event type: " + event.type);
        }
    }

    /**
     * Check a room exists with the dedicated roomId
     * @param roomId the room ID
     * @return true it exists.
     */
    public Boolean doesRoomExist(String roomId) {
        return (null != roomId) && (null != mStore.getRoom(roomId));
    }

    /**
     * Get the room object for the corresponding room id. Creates and initializes the object if there is none.
     * @param roomId the room id
     * @return the corresponding room
     */
    public Room getRoom(String roomId) {
        return getRoom(roomId, true);
    }

    /**
     * Get the room object for the corresponding room id.
     * @param roomId the room id
     * @param create create the room it does not exist.
     * @return the corresponding room
     */
    public Room getRoom(String roomId, boolean create) {
        checkIfActive();

        Room room = mStore.getRoom(roomId);
        if ((room == null) && create) {
            room = new Room();
            room.setRoomId(roomId);
            room.setDataHandler(this);
            room.setDataRetriever(mDataRetriever);
            room.setMyUserId(mCredentials.userId);
            room.setContentManager(mContentManager);
            mStore.storeRoom(room);
        }
        return room;
    }

    /**
     * Store a live room event.
     * @param event The event to be stored.
     */
    public void storeLiveRoomEvent(Event event) {
        checkIfActive();

        Room room = getRoom(event.roomId);

        // sanity check
        if (null != room) {
            // The room state we send with the callback is the one before the current event was processed
            RoomState beforeState = room.getLiveState().deepCopy();

            if (Event.EVENT_TYPE_REDACTION.equals(event.type)) {
                if (event.redacts != null) {
                    mStore.updateEventContent(event.roomId, event.redacts, event.content);
                }
            }  else if (!Event.EVENT_TYPE_TYPING.equals(event.type)) {
                // the candidate events are not stored.
                boolean store = !event.isCallEvent() || !Event.EVENT_TYPE_CALL_CANDIDATES.equals(event.type);

                // thread issue
                // if the user leaves a room,
                // the server scho could try to delete the room file
                if (Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(event.type) && mCredentials.userId.equals(event.userId) && mCredentials.userId.equals(event.stateKey)) {
                    String membership = event.content.getAsJsonPrimitive("membership").getAsString();

                    if (RoomMember.MEMBERSHIP_LEAVE.equals(membership) || RoomMember.MEMBERSHIP_BAN.equals(membership)) {
                        store = false;
                        // check if the room still exists.
                        if (null != this.getStore().getRoom(event.roomId)) {
                            this.getStore().deleteRoom(event.roomId);
                        }
                    }
                }

                if (store) {
                    mStore.storeLiveRoomEvent(event);
                    mStore.storeSummary(getUserId(), event.roomId, event, beforeState, mCredentials.userId);
                }
            }
        }
    }

    /**
     * Delete an event.
     * @param event The event to be stored.
     */
    public void deleteRoomEvent(Event event) {
        checkIfActive();

        Room room = getRoom(event.roomId);

        if (null != room) {
            mStore.deleteEvent(event);
            Event lastEvent = mStore.getLatestEvent(event.roomId);
            RoomState beforeLiveRoomState = room.getLiveState().deepCopy();

            mStore.storeSummary(getUserId(), event.roomId, lastEvent, beforeLiveRoomState, mCredentials.userId);
        }
    }

    /**
     * Return an user from his id.
     * @param userId the user id;.
     * @return the user.
     */
    public User getUser(String userId) {
        checkIfActive();

        return mStore.getUser(userId);
    }

    // Proxy IMXEventListener callbacks to everything in mEventListeners
    List<IMXEventListener> getListenersSnapshot() {
        ArrayList<IMXEventListener> eventListeners;

        synchronized (this) {
            eventListeners = new ArrayList<IMXEventListener>(mEventListeners);
        }

        return eventListeners;
    }

    @Override
    public void onPresenceUpdate(Event event, User user) {
        List<IMXEventListener> eventListeners = getListenersSnapshot();

        for (IMXEventListener listener : eventListeners) {
            try {
                listener.onPresenceUpdate(event, user);
            } catch (Exception e) {
            }
        }
    }

    @Override
    public void onLiveEvent(Event event, RoomState roomState) {
        List<IMXEventListener> eventListeners = getListenersSnapshot();

        for (IMXEventListener listener : eventListeners) {
            try {
                listener.onLiveEvent(event, roomState);
            } catch (Exception e) {
            }
        }
    }

    @Override
    public void onLiveEventsChunkProcessed() {
        List<IMXEventListener> eventListeners = getListenersSnapshot();

        for (IMXEventListener listener : eventListeners) {
            try {
                listener.onLiveEventsChunkProcessed();
            } catch (Exception e) {
            }
        }
    }

    @Override
    public void onBackEvent(Event event, RoomState roomState) {
        List<IMXEventListener> eventListeners = getListenersSnapshot();

        for (IMXEventListener listener : eventListeners) {
            try {
                listener.onBackEvent(event, roomState);
            } catch (Exception e) {
            }
        }
    }

    @Override
    public void onBingEvent(Event event, RoomState roomState, BingRule bingRule) {
        List<IMXEventListener> eventListeners = getListenersSnapshot();

        for (IMXEventListener listener : eventListeners) {
            try {
                listener.onBingEvent(event, roomState, bingRule);
            } catch (Exception e) {
            }
        }
    }

    @Override
    public void onDeleteEvent(Event event) {
        List<IMXEventListener> eventListeners = getListenersSnapshot();

        for (IMXEventListener listener : eventListeners) {
            try {
                listener.onDeleteEvent(event);
            } catch (Exception e) {
            }
        }
    }

    @Override
    public void onResentEvent(Event event) {
        List<IMXEventListener> eventListeners = getListenersSnapshot();

        for (IMXEventListener listener : eventListeners) {
            try {
                listener.onResentEvent(event);
            } catch (Exception e) {
            }
        }
    }

    @Override
    public void onResendingEvent(Event event) {
        List<IMXEventListener> eventListeners = getListenersSnapshot();

        for (IMXEventListener listener : eventListeners) {
            try {
                listener.onResendingEvent(event);
            } catch (Exception e) {
            }
        }
    }

    @Override
    public void onBingRulesUpdate() {
        List<IMXEventListener> eventListeners = getListenersSnapshot();

        for (IMXEventListener listener : eventListeners) {
            try {
                listener.onBingRulesUpdate();
            } catch (Exception e) {
            }
        }
    }

    @Override
    public void onInitialSyncComplete() {
        List<IMXEventListener> eventListeners = getListenersSnapshot();

        mInitialSyncComplete = true;

        for (IMXEventListener listener : eventListeners) {
            try {
                listener.onInitialSyncComplete();
            } catch (Exception e) {
            }
        }
    }

    @Override
    public void onPresencesSyncComplete() {
        List<IMXEventListener> eventListeners = getListenersSnapshot();

        for (IMXEventListener listener : eventListeners) {
            try {
                listener.onPresencesSyncComplete();
            } catch (Exception e) {
            }
        }
    }

    public void onRoomInitialSyncComplete(String roomId) {
        List<IMXEventListener> eventListeners = getListenersSnapshot();

        for (IMXEventListener listener : eventListeners) {
            try {
                listener.onRoomInitialSyncComplete(roomId);
            } catch (Exception e) {
            }
        }
    }

    public void onRoomInternalUpdate(String roomId) {
        List<IMXEventListener> eventListeners = getListenersSnapshot();

        for (IMXEventListener listener : eventListeners) {
            try {
                listener.onRoomInternalUpdate(roomId);
            } catch (Exception e) {
            }
        }
    }
}
