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

import android.content.Context;
import android.util.Log;

import org.matrix.androidsdk.data.DataRetriever;
import org.matrix.androidsdk.data.IMXStore;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.data.RoomSummary;
import org.matrix.androidsdk.listeners.IMXEventListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.RoomResponse;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.rest.model.bingrules.BingRule;
import org.matrix.androidsdk.rest.model.login.Credentials;
import org.matrix.androidsdk.util.BingRulesManager;
import org.matrix.androidsdk.util.ContentManager;
import org.matrix.androidsdk.util.JsonUtils;

import java.lang.reflect.Member;
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

    /**
     * Default constructor.
     * @param store the data storage implementation.
     */
    public MXDataHandler(IMXStore store, Credentials credentials) {
        mStore = store;
        mCredentials = credentials;
    }

    /**
     * @return true if the initial sync is completed.
     */
    public boolean isInitialSyncComplete() {
        return mInitialSyncComplete;
    }


    public void setDataRetriever(DataRetriever dataRetriever) {
        mDataRetriever = dataRetriever;
        mDataRetriever.setStore(mStore);
    }

    public void setPushRulesManager(BingRulesManager bingRulesManager) {
        mBingRulesManager = bingRulesManager;
        mBingRulesManager.loadRules(new SimpleApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                MXDataHandler.this.onBingRulesUpdate();
            }
        });
    }

    public void setContentManager(ContentManager contentManager) {
        mContentManager = contentManager;
    }

    public void refreshPushRules() {
        if (null != mBingRulesManager) {
            mBingRulesManager.loadRules(new SimpleApiCallback<Void>() {
                @Override
                public void onSuccess(Void info) {
                    MXDataHandler.this.onBingRulesUpdate();
                }
            });
        }
    }

    public void addListener(IMXEventListener listener) {
        // avoid adding twice
        if (mEventListeners.indexOf(listener) == -1) {
            mEventListeners.add(listener);
        }
        if (mInitialSyncComplete) {
            listener.onInitialSyncComplete();
        }
    }

    public void removeListener(IMXEventListener listener) {
        mEventListeners.remove(listener);
    }

    public void clear() {
        // remove any listener
        mEventListeners.clear();
    }
    /**
     * Handle the room data received from a per-room initial sync
     * @param roomResponse the room response object
     * @param room the associated room
     */
    public void handleInitialRoomResponse(RoomResponse roomResponse, Room room) {
        // Handle state events
        if (roomResponse.state != null) {
            room.processLiveState(roomResponse.state);
        }

        // Handle visibility
        if (roomResponse.visibility != null) {
            room.setVisibility(roomResponse.visibility);
        }

        // Handle messages / pagination token
        if (roomResponse.messages != null) {
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
        }
    }

    /**
     * Handle the room data received from a global initial sync
     * @param roomResponse the room response object
     */
    public void handleInitialRoomResponse(RoomResponse roomResponse) {
        if (roomResponse.roomId != null) {
            Room room = getRoom(roomResponse.roomId);
            handleInitialRoomResponse(roomResponse, room);
        }
    }

    public String getUserId() {
        return mCredentials.userId;
    }

    private void handleInitialSyncInvite(String roomId, String inviterUserId) {
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
        return mStore;
    }

    /**
     * Handle a list of events coming down from the event stream.
     * @param events the live events
     */
    public void handleLiveEvents(List<Event> events) {
        for (Event event : events) {
            handleLiveEvent(event);
        }
    }

    /**
     * Returns the member with userID;
     * @param members the members List
     * @param userID the user ID
     * @return the roomMember if it exists.
     */
    public RoomMember getMember(Collection<RoomMember> members, String userID) {
        for (RoomMember member : members) {
            if (userID.equals(member.getUserId())) {
                return member;
            }
        }
        return null;
    }

    /**
     * Perform an initial room sync (get the metadata + get the first bunch of messages)
     * @param event the member event
     * @param roomState the current roomState
     */
    private void onSelfJoinEvent(final Event event, final RoomState roomState) {
        RoomMember member = JsonUtils.toRoomMember(event.content);

        // join event ?
        if (RoomMember.MEMBERSHIP_JOIN.equals(member.membership)) {
            Collection<RoomMember> members = roomState.getMembers();
            RoomMember myMember = getMember(members, mCredentials.userId);

            // either the user is not in the member list (join a public room for example)
            // or the member is invited
            if ((null == myMember) || RoomMember.MEMBERSHIP_INVITE.equals(myMember.membership)) {
                // inviterUserId is only used when the user is invited to the room found during the initial sync
                RoomSummary roomSummary = getStore().getSummary(event.roomId);
                roomSummary.setInviterUserId(null);

                final Room room = getStore().getRoom(event.roomId);
                room.initialSync(new SimpleApiCallback<Void>() {
                    @Override
                    public void onSuccess(Void info) {
                        room.requestHistory(new SimpleApiCallback<Integer>() {
                            @Override
                            public void onSuccess(Integer info) {
                                onRoomInitialSyncComplete(event.roomId);
                            }
                        });
                    }
                });
            }
        }
    }

    /**
     * Handle events coming down from the event stream.
     * @param event the live event
     */
    private void handleLiveEvent(Event event) {
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
            this.onPresenceUpdate(event, user);
        }

        // Room event
        else if (event.roomId != null) {
            final Room room = getRoom(event.roomId);
            // The room state we send with the callback is the one before the current event was processed
            RoomState beforeState = room.getLiveState().deepCopy();

            if (event.stateKey != null) {
                room.processStateEvent(event, Room.EventDirection.FORWARDS);
            }

            storeLiveRoomEvent(event);

            onLiveEvent(event, beforeState);

            BingRule bingRule;

            // check if the room has been joined
            // the initial sync + the first requestHistory call is done here
            // instead of being done in the application
            if (Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(event.type) && event.userId.equals(mCredentials.userId)) {
                onSelfJoinEvent(event, beforeState);
            }

            // If the bing rules apply, bing
            if (!Event.EVENT_TYPE_TYPING.equals(event.type)
                    && (mBingRulesManager != null) && (null != (bingRule = mBingRulesManager.fulfilledBingRule(event)))) {
                onBingEvent(event, beforeState, bingRule);
            }
        }

        else {
            Log.e(LOG_TAG, "Unknown live event type: " + event.type);
        }
    }

    /**
     * Get the room object for the corresponding room id. Creates and initializes the object if there is none.
     * @param roomId the room id
     * @return the corresponding room
     */
    public Room getRoom(String roomId) {
        Room room = mStore.getRoom(roomId);
        if (room == null) {
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
                mStore.storeLiveRoomEvent(event);
                mStore.storeSummary(getUserId(), event.roomId, event, beforeState, mCredentials.userId);
            }
        }
    }

    /**
     * Delete an event.
     * @param event The event to be stored.
     */
    public void deleteRoomEvent(Event event) {
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
        return mStore.getUser(userId);
    }

    // Proxy IMXEventListener callbacks to everything in mEventListeners

    @Override
    public void onPresenceUpdate(Event event, User user) {
        for (IMXEventListener listener : mEventListeners) {
            try {
                listener.onPresenceUpdate(event, user);
            } catch (Exception e) {
            }
        }
    }

    @Override
    public void onLiveEvent(Event event, RoomState roomState) {
        for (IMXEventListener listener : mEventListeners) {
            try {
                listener.onLiveEvent(event, roomState);
            } catch (Exception e) {
            }
        }
    }

    @Override
    public void onBackEvent(Event event, RoomState roomState) {
        for (IMXEventListener listener : mEventListeners) {
            try {
                listener.onBackEvent(event, roomState);
            } catch (Exception e) {
            }
        }
    }

    @Override
    public void onBingEvent(Event event, RoomState roomState, BingRule bingRule) {
        for (IMXEventListener listener : mEventListeners) {
            try {
                listener.onBingEvent(event, roomState, bingRule);
            } catch (Exception e) {
            }
        }
    }

    @Override
    public void onDeleteEvent(Event event) {
        for (IMXEventListener listener : mEventListeners) {
            try {
                listener.onDeleteEvent(event);
            } catch (Exception e) {
            }
        }
    }

    @Override
    public void onResentEvent(Event event) {
        for (IMXEventListener listener : mEventListeners) {
            try {
                listener.onResentEvent(event);
            } catch (Exception e) {
            }
        }
    }

    @Override
         public void onResendingEvent(Event event) {
        for (IMXEventListener listener : mEventListeners) {
            try {
                listener.onResendingEvent(event);
            } catch (Exception e) {
            }
        }
    }

    @Override
    public void onBingRulesUpdate() {
        for (IMXEventListener listener : mEventListeners) {
            try {
                listener.onBingRulesUpdate();
            } catch (Exception e) {
            }
        }
    }

    @Override
    public void onInitialSyncComplete() {
        mInitialSyncComplete = true;

        for (IMXEventListener listener : mEventListeners) {
            try {
                listener.onInitialSyncComplete();
            } catch (Exception e) {
            }
        }
    }

    public void onRoomInitialSyncComplete(String roomId) {
        for (IMXEventListener listener : mEventListeners) {
            try {
                listener.onRoomInitialSyncComplete(roomId);
            } catch (Exception e) {
            }
        }
    }

    public void onRoomInternalUpdate(String roomId) {
        for (IMXEventListener listener : mEventListeners) {
            try {
                listener.onRoomInternalUpdate(roomId);
            } catch (Exception e) {
            }
        }
    }
}
