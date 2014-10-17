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

import org.matrix.androidsdk.data.IMXStore;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.listeners.IMXEventListener;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.TokensChunkResponse;
import org.matrix.androidsdk.rest.model.User;

import java.util.ArrayList;
import java.util.List;

/**
 * The data handler provides a layer to help manage matrix input and output.
 * <ul>
 * <li>It handles common API calls</li>
 * <li>Stores the data in its storage layer</li>
 * <li>Provides the means for an app to get callbacks for data changes</li>
 * </ul>
 */
public class MXDataHandler implements IMXEventListener {
    private static final String LOG_TAG = "MXData";

    private List<IMXEventListener> mEventListeners = new ArrayList<IMXEventListener>();

    private Gson mGson;
    private IMXStore mStore;
    private volatile boolean mInitialSyncComplete = false;

    /**
     * Default constructor.
     * @param store the data storage implementation.
     */
    public MXDataHandler(IMXStore store) {
        // The JSON -> object mapper
        mGson = new GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .create();
        mStore = store;
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

    public void handleEvents(List<? extends Event> events) {
        handleEvents(events, false);
    }

    public void handleEvents(List<? extends Event> events, boolean isTrackedNonStateEvents) {
        for (Event event : events) {
            handleEvent(event, isTrackedNonStateEvents);
        }
    }

    public void handleTokenResponse(String roomId, TokensChunkResponse<Event> response) {
        Log.i(LOG_TAG, roomId + " has "+response.chunk.size()+" events. Token="+response.start);
        // Handle messages
        handleEvents(response.chunk, true);

        // handle token
        Room room = mStore.getRoom(roomId);
        room.setPaginationToken(response.start);
        mStore.updateRoomState(room, null);
    }

    public IMXStore getStore() {
        return mStore;
    }

    private void handleEvent(Event event, boolean isTrackedNonStateEvents) {
        // Some state events are 'tracked' in the message history in that they are displayed in a
        // similar way to m.room.messages. We need to know if this event is from tracked events
        // (e.g. /messages API or /initialSync > rooms > messages > chunk) or not, so we know if
        // it should be added to the list of events for this room. Typically, events are NOT
        // tracked. m.room.message events are ALWAYS tracked.
        if (isTrackedNonStateEvents || Event.EVENT_TYPE_MESSAGE.equals(event.type)) {
            mStore.storeRoomEvent(event);
        }

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
            this.onUserPresenceUpdated(user);
        }
        // Room events
        else if (Event.EVENT_TYPE_MESSAGE.equals(event.type)) {
            Room room = mStore.getRoom(event.roomId);
            this.onMessageReceived(room, event);
        }
        // Room state events
        else if (Event.EVENT_TYPE_STATE_ROOM_NAME.equals(event.type)) {
            RoomState roomState = mGson.fromJson(event.content, RoomState.class);
            Room room = mStore.getRoom(event.roomId);
            String oldName = room.getRoomState().name;
            room.getRoomState().name = roomState.name;
            updateRoomState(room, event, oldName, roomState.name);
        }
        else if (Event.EVENT_TYPE_STATE_ROOM_TOPIC.equals(event.type)) {
            RoomState roomState = mGson.fromJson(event.content, RoomState.class);
            Room room = mStore.getRoom(event.roomId);
            String oldTopic = room.getRoomState().topic;
            room.getRoomState().topic = roomState.topic;
            updateRoomState(room, event, oldTopic, roomState.topic);
        }
        else if (Event.EVENT_TYPE_STATE_ROOM_CREATE.equals(event.type)) {
            RoomState roomState = mGson.fromJson(event.content, RoomState.class);
            Room room = mStore.getRoom(event.roomId);
            String oldCreator = room.getRoomState().creator;
            room.getRoomState().creator = roomState.creator;
            updateRoomState(room, event, oldCreator, roomState.creator);
        }
        else if (Event.EVENT_TYPE_STATE_ROOM_JOIN_RULES.equals(event.type)) {
            RoomState roomState = mGson.fromJson(event.content, RoomState.class);
            Room room = mStore.getRoom(event.roomId);
            String oldJoinRules = room.getRoomState().joinRule;
            room.getRoomState().joinRule = roomState.joinRule;
            updateRoomState(room, event, oldJoinRules, roomState.joinRule);
        }
        else if (Event.EVENT_TYPE_STATE_ROOM_ALIASES.equals(event.type)) {
            RoomState roomState = mGson.fromJson(event.content, RoomState.class);
            Room room = mStore.getRoom(event.roomId);
            List<String> oldRoomAliases = room.getRoomState().aliases;
            room.getRoomState().aliases = roomState.aliases;
            updateRoomState(room, event, oldRoomAliases, roomState.aliases);
        }
        else if (Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(event.type)) {
            RoomMember member = mGson.fromJson(event.content, RoomMember.class);
            member.userId = event.userId;
            try {
                if (RoomMember.MEMBERSHIP_INVITE.equals(event.content.getAsJsonPrimitive("membership").getAsString())) {
                    member.userId = event.stateKey;
                }
            }
            catch (Exception e) {
                Log.e(LOG_TAG, "Bad m.room.member: "+event.content+" :: "+e);
            }
            Room room = mStore.getRoom(event.roomId);
            RoomMember oldMember = room.getMember(event.userId);
            room.setMember(member.userId, member);
            updateRoomState(room, event, oldMember, member);
        }

        this.onEventReceived(event);
    }

    private void updateRoomState(Room room, Event event, Object oldVal, Object newVal) {
        mStore.updateRoomState(room, event.type);
        this.onRoomStateUpdated(room, event, oldVal, newVal);
    }

    // Proxy IMXEventListener callbacks to everything in mEventListeners

    @Override
    public void onUserPresenceUpdated(User user) {
        for (IMXEventListener listener : mEventListeners) {
            listener.onUserPresenceUpdated(user);
        }
    }

    @Override
    public void onMessageReceived(Room room, Event event) {
        for (IMXEventListener listener : mEventListeners) {
            listener.onMessageReceived(room, event);
        }
    }

    @Override
    public void onEventReceived(Event event) {
        for (IMXEventListener listener : mEventListeners) {
            listener.onEventReceived(event);
        }
    }

    @Override
    public void onRoomStateUpdated(Room room, Event event, Object oldVal, Object newVal) {
        for (IMXEventListener listener : mEventListeners) {
            listener.onRoomStateUpdated(room, event, oldVal, newVal);
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
