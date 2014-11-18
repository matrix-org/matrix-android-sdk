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

import org.matrix.androidsdk.data.DataRetriever;
import org.matrix.androidsdk.data.IMXStore;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.listeners.IMXEventListener;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.TokensChunkResponse;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.rest.model.login.Credentials;
import org.matrix.androidsdk.util.JsonUtils;

import java.util.ArrayList;
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

    /**
     * Default constructor.
     * @param store the data storage implementation.
     */
    public MXDataHandler(IMXStore store, Credentials credentials) {
        mStore = store;
        mCredentials = credentials;
    }

    public void setDataRetriever(DataRetriever dataRetriever) {
        mDataRetriever = dataRetriever;
        mDataRetriever.setStore(mStore);
    }

    public void addListener(IMXEventListener listener) {
        mEventListeners.add(listener);
        if (mInitialSyncComplete) {
            listener.onInitialSyncComplete();
        }
    }

    public void removeListener(IMXEventListener listener) {
        mEventListeners.remove(listener);
    }

    public void handleInvite(String roomId, String inviterUserId) {
        Room room = getRoom(roomId);

        // add the inviter and invitee
        RoomMember member = new RoomMember();
        member.membership = RoomMember.MEMBERSHIP_INVITE;
        room.setMember(mCredentials.userId, member);
        RoomMember inviter = new RoomMember();
        inviter.membership = RoomMember.MEMBERSHIP_JOIN;
        room.setMember(inviterUserId, inviter);

        mStore.storeSummary(roomId, null);
        onInvitedToRoom(room);
    }

    public IMXStore getStore() {
        return mStore;
    }

    /**
     * Process an initial room state block of events.
     * @param roomId the room id
     * @param events the room state events
     */
    public void handleInitialRoomState(String roomId, List<? extends Event> events) {
        Room room = getRoom(roomId);
        for (Event event : events) {
            room.processStateEvent(event, Room.EventDirection.FORWARDS);
        }
    }

    /**
     * Handle the room messages returned from the initial sync.
     * @param roomId the room id
     * @param response the object containing the tokens and messages
     */
    public void handleInitialRoomMessages(String roomId, TokensChunkResponse<Event> response) {
        Log.i(LOG_TAG, roomId + " has " + response.chunk.size() + " events. Token=" + response.start);

        mStore.storeRoomEvents(roomId, response, Room.EventDirection.FORWARDS);
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
                mStore.storeUser(user);
            }
            else {
                user.presence = userPresence.presence;
                user.lastActiveAgo = userPresence.lastActiveAgo;
            }
            this.onPresenceUpdate(event, user);
        }

        // Room event
        else if (event.roomId != null) {
            Room room = getRoom(event.roomId);
            mStore.storeLiveRoomEvent(event);
            onLiveEvent(event, room.getLiveState().deepCopy());
            if (event.stateKey != null) {
                room.processStateEvent(event, Room.EventDirection.FORWARDS);
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
            room.setEventListener(this);
            room.setDataRetriever(mDataRetriever);
            mStore.storeRoom(room);
        }
        return room;
    }

    // Proxy IMXEventListener callbacks to everything in mEventListeners

    @Override
    public void onPresenceUpdate(Event event, User user) {
        for (IMXEventListener listener : mEventListeners) {
            listener.onPresenceUpdate(event, user);
        }
    }

    @Override
    public void onLiveEvent(Event event, RoomState roomState) {
        for (IMXEventListener listener : mEventListeners) {
            listener.onLiveEvent(event, roomState);
        }
    }

    @Override
    public void onBackEvent(Event event, RoomState roomState) {
        for (IMXEventListener listener : mEventListeners) {
            listener.onBackEvent(event, roomState);
        }
    }

    @Override
    public void onInvitedToRoom(Room room) {
        for (IMXEventListener listener : mEventListeners) {
            listener.onInvitedToRoom(room);
        }
    }

    @Override
    public void onInitialSyncComplete() {
        mInitialSyncComplete = true;

        for (IMXEventListener listener : mEventListeners) {
            listener.onInitialSyncComplete();
        }
    }
}
