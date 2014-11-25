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
package org.matrix.androidsdk.data;

import com.google.gson.JsonObject;

import org.matrix.androidsdk.MXDataHandler;
import org.matrix.androidsdk.RestClient;
import org.matrix.androidsdk.listeners.IMXEventListener;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.RoomResponse;
import org.matrix.androidsdk.rest.model.TokensChunkResponse;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.util.JsonUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class representing a room and the interactions we have with it.
 */
public class Room {

    /**
     * The direction from which an incoming event is considered.
     * <ul>
     * <li>FORWARDS for events coming down the live event stream</li>
     * <li>BACKWARDS for old events requested through pagination</li>
     * </ul>
     */
    public static enum EventDirection {
        /**
         * The direction for events coming down the live event stream.
         */
        FORWARDS,

        /**
         * The direction for old events requested through pagination.
         */
        BACKWARDS
    }

    /**
     * Callback to implement to be informed when an operation is complete (pagination, ...).
     */
    public static interface OnCompleteCallback {
        /** The operation is complete. */
        public void onComplete();
    }

    /**
     * Callback to implement to be informed when an history request is complete.
     */
    public static interface HistoryCompleteCallback {
        /** The operation is complete. */
        public void onComplete(int count);
    }

    private String mRoomId;
    private RoomState mLiveState = new RoomState();
    private RoomState mBackState = new RoomState();

    private DataRetriever mDataRetriever;
    private MXDataHandler mDataHandler;

    // Map to keep track of the listeners the client adds vs. the ones we actually register to the global data handler.
    // This is needed to find the right one when removing the listener.
    private Map<IMXEventListener, IMXEventListener> mEventListeners = new HashMap<IMXEventListener, IMXEventListener>();

    private boolean isPaginating = false;
    private boolean canStillPaginate = true;
    // This is used to block live events until the state is fully processed and ready
    private boolean isReady = false;

    public String getRoomId() {
        return this.mRoomId;
    }

    public void setRoomId(String roomId) {
        mRoomId = roomId;
        mLiveState.roomId = roomId;
        mBackState.roomId = roomId;
    }

    public RoomState getLiveState() {
        return mLiveState;
    }

    public Collection<RoomMember> getMembers() {
        return mLiveState.getMembers();
    }

    public void setMember(String userId, RoomMember member) {
        mLiveState.setMember(userId, member);
    }

    public RoomMember getMember(String userId) {
        return mLiveState.getMember(userId);
    }

    public String getTopic() {
        return this.mLiveState.topic;
    }

    public String getName() {
        if (this.mLiveState.name != null) {
            return this.mLiveState.name;
        }
        else if (this.mLiveState.roomAliasName != null) {
            return this.mLiveState.roomAliasName;
        }
        else if (this.mLiveState.aliases != null && this.mLiveState.aliases.size() > 0) {
            return this.mLiveState.aliases.get(0);
        }
        else {
            return this.mRoomId;
        }
    }

    /**
     * Set the data retriever for storage/server requests.
     * @param dataRetriever should be the main DataRetriever object
     */
    public void setDataRetriever(DataRetriever dataRetriever) {
        mDataRetriever = dataRetriever;
    }

    /**
     * Set the event listener to send back events to. This is typically the DataHandler for dispatching the events to listeners.
     * @param dataHandler should be the main data handler for dispatching back events to registered listeners.
     */
    public void setDataHandler(MXDataHandler dataHandler) {
        mDataHandler = dataHandler;
    }

    /**
     * Add an event listener to this room. Only events relative to the room will come down.
     * @param eventListener the event listener to add
     */
    public void addEventListener(final IMXEventListener eventListener) {
        // Create a global listener that we'll add to the data handler
        IMXEventListener globalListener = new MXEventListener() {
            @Override
            public void onPresenceUpdate(Event event, User user) {
                // Only pass event through if the user is a member of the room
                if (getMember(user.userId) != null) {
                    eventListener.onPresenceUpdate(event, user);
                }
            }

            @Override
            public void onLiveEvent(Event event, RoomState roomState) {
                // Filter out events for other rooms and events while we are joining (before the room is ready)
                if (mRoomId.equals(event.roomId) && isReady) {
                    eventListener.onLiveEvent(event, roomState);
                }
            }

            @Override
            public void onBackEvent(Event event, RoomState roomState) {
                // Filter out events for other rooms
                if (mRoomId.equals(event.roomId)) {
                    eventListener.onBackEvent(event, roomState);
                }
            }
        };
        mEventListeners.put(eventListener, globalListener);
        mDataHandler.addListener(globalListener);
    }

    /**
     * Remove an event listener.
     * @param eventListener the event listener to remove
     */
    public void removeEventListener(IMXEventListener eventListener) {
        mDataHandler.addListener(mEventListeners.get(eventListener));
        mEventListeners.remove(eventListener);
    }

    /**
     * Reset the back state so that future history requests start over from live.
     * Must be called when opening a room if interested in history.
     */
    public void initHistory() {
        mBackState = mLiveState.deepCopy();
        canStillPaginate = true;
    }

    /**
     * Process a state event to keep the internal live and back states up to date.
     * @param event the state event
     * @param direction the direction; ie. forwards for live state, backwards for back state
     */
    public void processStateEvent(Event event, EventDirection direction) {
        RoomState affectedState = (direction == EventDirection.FORWARDS) ? mLiveState : mBackState;
        JsonObject contentToConsider = (direction == EventDirection.FORWARDS) ? event.content : event.prevContent;

        if (Event.EVENT_TYPE_STATE_ROOM_NAME.equals(event.type)) {
            RoomState roomState = JsonUtils.toRoomState(contentToConsider);
            affectedState.name = (roomState == null) ? null : roomState.name;
        }
        else if (Event.EVENT_TYPE_STATE_ROOM_TOPIC.equals(event.type)) {
            RoomState roomState = JsonUtils.toRoomState(contentToConsider);
            affectedState.topic = (roomState == null) ? null : roomState.topic;
        }
        else if (Event.EVENT_TYPE_STATE_ROOM_CREATE.equals(event.type)) {
            RoomState roomState = JsonUtils.toRoomState(contentToConsider);
            affectedState.creator = (roomState == null) ? null : roomState.creator;
        }
        else if (Event.EVENT_TYPE_STATE_ROOM_JOIN_RULES.equals(event.type)) {
            RoomState roomState = JsonUtils.toRoomState(contentToConsider);
            affectedState.joinRule = (roomState == null) ? null : roomState.joinRule;
        }
        else if (Event.EVENT_TYPE_STATE_ROOM_ALIASES.equals(event.type)) {
            RoomState roomState = JsonUtils.toRoomState(contentToConsider);
            affectedState.aliases = (roomState == null) ? null : roomState.aliases;
        }
        else if (Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(event.type)) {
            RoomMember member = JsonUtils.toRoomMember(contentToConsider);
            String userId = event.stateKey;
            if (member == null) {
                affectedState.removeMember(userId);
            }
            else {
                affectedState.setMember(userId, member);
            }
        }
    }

    /**
     * Process the live state events for the room. Only once this is done is the room considered ready to pass on events.
     * @param stateEvents the state events describing the state of the room
     */
    public void processLiveState(List<Event> stateEvents) {
        for (Event event : stateEvents) {
            processStateEvent(event, EventDirection.FORWARDS);
        }
        isReady = true;
    }

    /**
     * Request older messages. They will come down the onBackEvent callback.
     * @param callback callback to implement to be informed that the pagination request has been completed. Can be null.
     */
    public void requestHistory(final HistoryCompleteCallback callback) {
        if (isPaginating || !canStillPaginate) return; // One at a time please
        isPaginating = true;
        mDataRetriever.requestRoomHistory(mRoomId, mBackState.getToken(), new DataRetriever.HistoryCallback() {
            @Override
            public void onComplete(TokensChunkResponse<Event> response) {
                if (response == null) {
                    canStillPaginate = false;
                } else {
                    mBackState.setToken(response.end);
                    for (Event event : response.chunk) {
                        if (event.stateKey != null) {
                            processStateEvent(event, EventDirection.BACKWARDS);
                        }
                        mDataHandler.onBackEvent(event, mBackState.deepCopy());
                    }
                    if (response.chunk.size() == 0) {
                        canStillPaginate = false;
                    }
                    if (callback != null) {
                        callback.onComplete(response.chunk.size());
                    }
                }
                isPaginating = false;
            }
        });
    }

    /**
     * Shorthand for {@link #requestHistory(org.matrix.androidsdk.data.Room.HistoryCompleteCallback)} with a null callback.
     */
    public void requestHistory() {
        requestHistory(null);
    }

    /**
     * Join the room. If successful, the room's current state will be loaded before calling back onComplete.
     * @param callback onComplete callback
     */
    public void join(final OnCompleteCallback callback) {
        mDataRetriever.getRoomsRestClient().joinRoom(mRoomId, new RestClient.SimpleApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                // Once we've joined, we run an initial sync on the room to have all of its information
                mDataRetriever.getRoomsRestClient().initialSync(mRoomId, new RestClient.SimpleApiCallback<RoomResponse>() {
                    @Override
                    public void onSuccess(RoomResponse roomInfo) {
                        mDataHandler.handleInitialRoomResponse(roomInfo, Room.this);
                        callback.onComplete();
                    }
                });
            }
        });
    }
}
