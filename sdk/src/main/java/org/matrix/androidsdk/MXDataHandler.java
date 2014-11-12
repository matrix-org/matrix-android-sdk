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

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

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

    private Gson mGson;
    private IMXStore mStore;
    private Credentials mCredentials;
    private volatile boolean mInitialSyncComplete = false;
    private DataRetriever mDataRetriever;

    /**
     * Default constructor.
     * @param store the data storage implementation.
     */
    public MXDataHandler(IMXStore store, Credentials credentials) {
        // The JSON -> object mapper
        mGson = new GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .create();
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

    public void handleTokenResponse(String roomId, TokensChunkResponse<Event> response) {
        Log.i(LOG_TAG, roomId + " has " + response.chunk.size() + " events. Token=" + response.start);
        // Handle messages
        handleLiveEvents(response.chunk);

        // handle token
        Room room = mStore.getRoom(roomId);
        room.setPaginationToken(response.start);
        mStore.updateRoomState(room, null);
    }

    public void handleInvite(String roomId, String inviterUserId) {
        Room room = mStore.getRoom(roomId);

        // add the inviter and invitee
        RoomMember member = new RoomMember();
        member.membership = RoomMember.MEMBERSHIP_INVITE;
        room.setMember(mCredentials.userId, member);
        RoomMember inviter = new RoomMember();
        inviter.membership = RoomMember.MEMBERSHIP_JOIN;
        room.setMember(inviterUserId, inviter);

        mStore.updateRoomState(room, null);
        onInvitedToRoom(room);
    }

    public IMXStore getStore() {
        return mStore;
    }

    public void handleInitialRoomState(List<? extends Event> events) {
        for (Event event : events) {
            handleInitialRoomStateEvent(event);
        }
    }

    private void handleInitialRoomStateEvent(Event event) {
        Room room = getRoom(event.roomId);
        room.processStateEvent(event, Room.EventDirection.FORWARDS);
    }

    public void handleLiveEvents(List<? extends Event> events) {
        for (Event event : events) {
            handleLiveEvent(event);
        }
    }

    private void handleLiveEvent(Event event) {
        if (Event.EVENT_TYPE_PRESENCE.equals(event.type)) {
            User userPresence = mGson.fromJson(event.content, User.class);
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

        else if (event.roomId != null) {
            Room room = getRoom(event.roomId);
            mStore.storeRoomEvent(event, room.getLiveState().getToken(), Room.EventDirection.FORWARDS);
            // Only start sending room events once the initial sync is complete so we know the room state is stable
            if (mInitialSyncComplete) {
                onLiveEvent(event, room.getLiveState().deepCopy());
            }
            if (event.stateKey != null) {
                room.processStateEvent(event, Room.EventDirection.FORWARDS);
            }
        }
    }

    private Room getRoom(String roomId) {
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

//    @Override
//    public void onMessageEvent(Event event, RoomState roomState, Room.EventDirection direction) {
//        for (IMXEventListener listener : mEventListeners) {
//            listener.onMessageEvent(event, roomState, direction);
//        }
//    }

    @Override
    public void onRoomStateUpdated(Room room, Event event, Object oldVal, Object newVal) {
        for (IMXEventListener listener : mEventListeners) {
            listener.onRoomStateUpdated(room, event, oldVal, newVal);
        }
    }

    @Override
    public void onMessageEvent(Room room, Event event) {
        for (IMXEventListener listener : mEventListeners) {
            listener.onMessageEvent(room, event);
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
    public void onRoomReady(Room room) {
        for (IMXEventListener listener : mEventListeners) {
            listener.onRoomReady(room);
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

        for (Room room : mStore.getRooms()) {
            onRoomReady(room);
        }

        for (IMXEventListener listener : mEventListeners) {
            listener.onInitialSyncComplete();
        }
    }
}
