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

import android.content.Context;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.matrix.androidsdk.MXDataHandler;
import org.matrix.androidsdk.listeners.IMXEventListener;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.BannedUser;
import org.matrix.androidsdk.rest.model.ContentResponse;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.FileInfo;
import org.matrix.androidsdk.rest.model.FileMessage;
import org.matrix.androidsdk.rest.model.ImageInfo;
import org.matrix.androidsdk.rest.model.ImageMessage;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.Message;
import org.matrix.androidsdk.rest.model.PowerLevels;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.RoomResponse;
import org.matrix.androidsdk.rest.model.TokensChunkResponse;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.util.ContentManager;
import org.matrix.androidsdk.util.ImageUtils;
import org.matrix.androidsdk.util.JsonUtils;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class representing a room and the interactions we have with it.
 */
public class Room {

    private static final String LOG_TAG = "Room";
    private static final int MAX_RATE_LIMIT_MS = 20000;
    // 3 mins
    private static final int MAX_MESSAGE_TIME_LIFE_MS = 180000;

    private static final int MAX_EVENT_COUNT_PER_PAGINATION = 20;

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

    // the storage events are buffered to provide a small bunch of events
    // the storage can provide a big bunch which slows down the UI.
    private class BufferedEvent {
        public Event mEvent;
        public RoomState mState;

        public BufferedEvent(Event event, RoomState state) {
            mEvent = event;
            mState = state;
        }
    }

    // avoid adding to many events
    // the room history request can provide more than exxpected event.
    private ArrayList<BufferedEvent> mBufferedEvents = new ArrayList<BufferedEvent>();

    private String mRoomId;
    private RoomState mLiveState = new RoomState();
    private RoomState mBackState = new RoomState();

    private DataRetriever mDataRetriever;
    private MXDataHandler mDataHandler;
    private ContentManager mContentManager;

    private String mMyUserId = null;

    // Map to keep track of the listeners the client adds vs. the ones we actually register to the global data handler.
    // This is needed to find the right one when removing the listener.
    private Map<IMXEventListener, IMXEventListener> mEventListeners = new HashMap<IMXEventListener, IMXEventListener>();

    private boolean isPaginating = false;
    private boolean canStillPaginate = true;
    private int mLatestChunkSize = 0;
    // This is used to block live events and history requests until the state is fully processed and ready
    private boolean mIsReady = false;

    private boolean isResendingEvents = false;
    private boolean checkUnsentMessages = false;

    private boolean mIsLeaving = false;

    // userIds list
    private ArrayList<String>mTypingUsers = new ArrayList<String>();

    public String getRoomId() {
        return this.mRoomId;
    }

    public void setRoomId(String roomId) {
        mRoomId = roomId;
        mLiveState.roomId = roomId;
        mBackState.roomId = roomId;
    }

    public void setReadyState(Boolean isReady) {
        mIsReady = isReady;
    }

    public RoomState getLiveState() {
        return mLiveState;
    }

    public void setLiveState(RoomState liveState) {
        mLiveState = liveState;
    }

    public boolean isLeaving() {
        return mIsLeaving;
    }

    public Collection<RoomMember> getMembers() {
        return mLiveState.getMembers();
    }

    public Collection<RoomMember> getActiveMembers() {
        Collection<RoomMember> members = mLiveState.getMembers();
        ArrayList<RoomMember> activeMembers = new ArrayList<RoomMember>();

        for(RoomMember member : members) {
            if (!member.hasLeft()) {
                activeMembers.add(member);
            }
        }

        return activeMembers;
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

    public String getName(String selfUserId) {
        return mLiveState.getDisplayName(selfUserId);
    }

    public String getVisibility() {
        return mLiveState.visibility;
    }

    public void setVisibility(String visibility) {
        mLiveState.visibility = visibility;
    }

    public void setMyUserId(String userId) { mMyUserId = userId; }

    public void setContentManager(ContentManager contentManager) {
        mContentManager = contentManager;
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
        mLiveState.setDataHandler(mDataHandler);
        mBackState.setDataHandler(mDataHandler);
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
                    try {
                        eventListener.onPresenceUpdate(event, user);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "onPresenceUpdate exception " + e.getMessage());
                    }
                }
            }

            @Override
            public void onLiveEvent(Event event, RoomState roomState) {
                // Filter out events for other rooms and events while we are joining (before the room is ready)
                if (mRoomId.equals(event.roomId) && mIsReady) {

                    if (event.type.equals(Event.EVENT_TYPE_TYPING)) {
                        // Typing notifications events are not room messages nor room state events
                        // They are just volatile information

                        if (event.content.has("user_ids")) {
                            mTypingUsers = null;

                            try {
                                mTypingUsers =  (new Gson()).fromJson(event.content.get("user_ids"), new TypeToken<List<String>>(){}.getType());
                            } catch (Exception e) {
                                Log.e(LOG_TAG, "onLiveEvent exception " + e.getMessage());
                            }

                            // avoid null list
                            if (null == mTypingUsers) {
                                mTypingUsers = new ArrayList<String>();
                            }
                        }
                    }

                    try {
                        eventListener.onLiveEvent(event, roomState);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "onLiveEvent exception " + e.getMessage());
                    }
                }
            }

            @Override
            public void onLiveEventsChunkProcessed() {
                try {
                    eventListener.onLiveEventsChunkProcessed();
                } catch (Exception e) {
                    Log.e(LOG_TAG, "onLiveEventsChunkProcessed exception " + e.getMessage());
                }
            }

            @Override
            public void onBackEvent(Event event, RoomState roomState) {
                // Filter out events for other rooms
                if (mRoomId.equals(event.roomId)) {
                    try {
                        eventListener.onBackEvent(event, roomState);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "onBackEvent exception " + e.getMessage());
                    }
                }
            }

            @Override
            public void onDeleteEvent(Event event) {
                // Filter out events for other rooms
                if (mRoomId.equals(event.roomId)) {
                    try {
                        eventListener.onDeleteEvent(event);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "onDeleteEvent exception " + e.getMessage());
                    }
                }
            }

            @Override
            public void onResendingEvent(Event event) {
                // Filter out events for other rooms
                if (mRoomId.equals(event.roomId)) {
                    try {
                        eventListener.onResendingEvent(event);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "onResendingEvent exception " + e.getMessage());
                    }
                }
            }

            @Override
            public void onResentEvent(Event event) {
                // Filter out events for other rooms
                if (mRoomId.equals(event.roomId)) {
                    try {
                        eventListener.onResentEvent(event);
                    }
                    catch (Exception e) {
                        Log.e(LOG_TAG, "onResentEvent exception " + e.getMessage());
                    }
                }
            }
            
            @Override
            public void onRoomInitialSyncComplete(String roomId) {
                // Filter out events for other rooms
                if (mRoomId.equals(roomId)) {
                    try {
                        eventListener.onRoomInitialSyncComplete(roomId);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "onRoomInitialSyncComplete exception " + e.getMessage());
                    }
                }
            }

            @Override
            public void onRoomInternalUpdate(String roomId) {
                // Filter out events for other rooms
                if (mRoomId.equals(roomId)) {
                    try {
                        eventListener.onRoomInternalUpdate(roomId);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "onRoomInternalUpdate exception " + e.getMessage());
                    }
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
        mDataHandler.removeListener(mEventListeners.get(eventListener));
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
     * @return true if the event has been processed.
     */
    public boolean processStateEvent(Event event, EventDirection direction) {
        RoomState affectedState = (direction == EventDirection.FORWARDS) ? mLiveState : mBackState;
        Boolean isProcessed = affectedState.applyState(event, direction);

        if ((isProcessed) && (direction == EventDirection.FORWARDS)) {
            mDataHandler.getStore().storeLiveStateForRoom(mRoomId);
        }

        return isProcessed;
    }

    /**
     * Process the live state events for the room. Only once this is done is the room considered ready to pass on events.
     * @param stateEvents the state events describing the state of the room
     */
    public void processLiveState(List<Event> stateEvents) {
        for (Event event : stateEvents) {
            processStateEvent(event, EventDirection.FORWARDS);
        }
        mIsReady = true;

        // check if they are some pending events
        //resendUnsentEvents();
    }

    /**
     * Send an event content to the room.
     * The event is updated with the data provided by the server
     * The provided event contains the error description.
     * @param event the message
     * @param callback the callback with the created event
     */
    public void sendEvent(final Event event, final ApiCallback<Void> callback) {
        // wait that the room is synced before sending messages
        if (!mIsReady || !selfJoined()) {
            event.mSentState = Event.SentState.WAITING_RETRY;
            try {
                callback.onNetworkError(null);
            } catch (Exception e) {
                Log.e(LOG_TAG, "sendEvent exception " + e.getMessage());
            }
            return;
        }

        final ApiCallback<Event> localCB = new ApiCallback<Event>() {
                @Override
                public void onSuccess(Event serverResponseEvent) {
                    // update the event with the server response
                    event.mSentState = Event.SentState.WAITING_ECHO;
                    event.eventId = serverResponseEvent.eventId;

                    try {
                        callback.onSuccess(null);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "sendEvent exception " + e.getMessage());
                    }
                }

                @Override
                public void onNetworkError(Exception e) {
                    event.mSentState = Event.SentState.UNDELIVERABLE;
                    event.unsentException = e;

                    try {
                        callback.onNetworkError(e);
                    } catch (Exception anException) {
                        Log.e(LOG_TAG, "sendEvent exception " + anException.getMessage());
                    }
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    event.mSentState = Event.SentState.UNDELIVERABLE;
                    event.unsentMatrixError = e;

                    try {
                        callback.onMatrixError(e);
                    } catch (Exception anException) {
                        Log.e(LOG_TAG, "sendEvent exception " + anException.getMessage());
                    }
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    event.mSentState = Event.SentState.UNDELIVERABLE;
                    event.unsentException = e;

                    try {
                        callback.onUnexpectedError(e);
                    } catch (Exception anException) {
                        Log.e(LOG_TAG, "sendEvent exception " + anException.getMessage());
                    }
                }
            };

        event.mSentState = Event.SentState.SENDING;

        if (Event.EVENT_TYPE_MESSAGE.equals(event.type)) {
            mDataRetriever.getRoomsRestClient().sendMessage(event.originServerTs + "", mRoomId, JsonUtils.toMessage(event.content), localCB);
        } else {
            mDataRetriever.getRoomsRestClient().sendEvent(mRoomId, event.type, event.content, localCB);
        }
    }

    /**
     * Send MAX_EVENT_COUNT_PER_PAGINATION events to the caller.
     * @param callback the callback.
     */
    private void manageEvents(final ApiCallback<Integer> callback) {
        int count = Math.min(mBufferedEvents.size(), MAX_EVENT_COUNT_PER_PAGINATION);

        for(int i = 0; i < count; i++) {
            BufferedEvent bufferedEvent = mBufferedEvents.get(0);
            mBufferedEvents.remove(0);
            mDataHandler.onBackEvent(bufferedEvent.mEvent, bufferedEvent.mState);
        }

        if ((mBufferedEvents.size() == 0) && (0 == mLatestChunkSize)) {
            canStillPaginate = false;
        }

        if (callback != null) {
            try {
                callback.onSuccess(count);
            } catch (Exception e) {
                Log.e(LOG_TAG, "requestHistory exception " + e.getMessage());
            }
        }
        isPaginating = false;
        mDataHandler.getStore().commit();
    }

    /**
     * Request older messages. They will come down the onBackEvent callback.
     * @param callback callback to implement to be informed that the pagination request has been completed. Can be null.
     * @return true if request starts
     */
    public boolean requestHistory(final ApiCallback<Integer> callback) {
        if (isPaginating // One at a time please
                || !canStillPaginate // If we have already reached the end of history
                || !mIsReady) { // If the room is not finished being set up
            return false;
        }
        isPaginating = true;

        // enough buffered data
        if (mBufferedEvents.size() >= MAX_EVENT_COUNT_PER_PAGINATION) {
            final android.os.Handler handler = new android.os.Handler();

            // call the callback with a delay (and on the UI thread).
            // to reproduce the same behaviour as a network request.
            Runnable r = new Runnable() {
                @Override
                public void run() {
                    handler.post(new Runnable() {
                        public void run() {
                            manageEvents(callback);
                        }
                    });
                }
            };

            Thread t = new Thread(r);
            t.start();

            return true;
        }

        mDataRetriever.requestRoomHistory(mRoomId, mBackState.getToken(), new SimpleApiCallback<TokensChunkResponse<Event>>(callback) {
            @Override
            public void onSuccess(TokensChunkResponse<Event> response) {
                mBackState.setToken(response.end);

                // the roomstate is copied to have a state snapshot
                // but copy it only if there is a state update
                RoomState stateCopy = mBackState.deepCopy();

                for (Event event : response.chunk) {
                    Boolean processedEvent = true;

                    if (event.stateKey != null) {
                        processedEvent = processStateEvent(event, EventDirection.BACKWARDS);

                        if (processedEvent) {
                            // new state event -> copy the room state
                            stateCopy = mBackState.deepCopy();
                        }
                    }

                    // warn the listener only if the message is processed.
                    // it should avoid duplicated events.
                    if (processedEvent) {
                        mBufferedEvents.add(new BufferedEvent(event, stateCopy));
                    }
                }

                mLatestChunkSize = response.chunk.size();
                manageEvents(callback);
            }

            @Override
            public void onMatrixError(MatrixError e) {
                // When we've retrieved all the messages from a room, the pagination token is some invalid value
                if (MatrixError.UNKNOWN.equals(e.errcode)) {
                    canStillPaginate = false;
                }
                isPaginating = false;

                super.onMatrixError(e);
            }

            @Override
            public void onNetworkError(Exception e) {
                isPaginating = false;
                super.onNetworkError(e);
            }

            @Override
            public void onUnexpectedError(Exception e) {
                isPaginating = false;
                super.onUnexpectedError(e);
            }
        });

        return true;
    }

    /**
     * Shorthand for {@link #requestHistory(org.matrix.androidsdk.rest.callback.ApiCallback)} with a null callback.
     * @return true if the request starts
     */
    public boolean requestHistory() {
        return requestHistory(null);
    }

    /**
     * Join the room. If successful, the room's current state will be loaded before calling back onComplete.
     * @param callback the callback for when done
     */
    public void join(final ApiCallback<Void> callback) {
        mDataRetriever.getRoomsRestClient().joinRoom(mRoomId, new SimpleApiCallback<RoomResponse>(callback) {
            @Override
            public void onSuccess(final RoomResponse aReponse) {
                try {
                    // Once we've joined, we run an initial sync on the room to have all of its information
                    initialSync(callback);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "join exception " + e.getMessage());
                }
            }

            @Override
            public void onNetworkError(Exception e) {
                callback.onNetworkError(e);
            }

            @Override
            public void onMatrixError(MatrixError e) {
                callback.onMatrixError(e);
            }

            @Override
            public void onUnexpectedError(Exception e) {
                callback.onUnexpectedError(e);
            }
        });
    }

    /**
     * Perform a room-level initial sync to get latest messages and pagination token.
     * @param callback the async callback
     */
    public void initialSync(final ApiCallback<Void> callback) {
        mDataRetriever.getRoomsRestClient().initialSync(mRoomId, new SimpleApiCallback<RoomResponse>(callback) {
            @Override
            public void onSuccess(RoomResponse roomInfo) {
                mDataHandler.handleInitialRoomResponse(roomInfo, Room.this);

                mDataHandler.getStore().commit();
                if (callback != null) {
                    try {
                        callback.onSuccess(null);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "initialSync exception " + e.getMessage());
                    }
                }
            }
        });
    }

    /**
     * Shorthand for {@link #join(org.matrix.androidsdk.rest.callback.ApiCallback)} with a null callback.
     */
    public void join() {
        join(null);
    }

    /**
     * Invite a user to this room.
     * @param userId the user id
     * @param callback the callback for when done
     */
    public void invite(String userId, ApiCallback<Void> callback) {
        mDataRetriever.getRoomsRestClient().inviteToRoom(mRoomId, userId, callback);
    }

    /**
     * Invite some users to this room.
     * @param userIds the user ids
     * @param callback the callback for when done
     */
    public void invite(ArrayList<String> userIds, ApiCallback<Void> callback) {
        invite(userIds, 0, callback);
    }

    /**
     * Invite an indexed user to this room.
     * @param userIds the user ids list
     * @param index the user id index
     * @param callback the callback for when done
     */
    private void invite(final ArrayList<String> userIds, final int index, final ApiCallback<Void> callback) {
        // add sanity checks
        if ((null == userIds) || (index >= userIds.size())) {
            return;
        }
        mDataRetriever.getRoomsRestClient().inviteToRoom(mRoomId, userIds.get(index), new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                // invite the last user
                if ((index + 1) == userIds.size()) {
                    try {
                        callback.onSuccess(info);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "invite exception " + e.getMessage());
                    }
                } else {
                    invite(userIds, index + 1, callback);
                }
            }

            @Override
            public void onNetworkError(Exception e) {
                try {
                    callback.onNetworkError(e);
                } catch (Exception anException) {
                    Log.e(LOG_TAG, "invite exception " + anException.getMessage());
                }
            }

            @Override
            public void onMatrixError(MatrixError e) {
                try {
                    callback.onMatrixError(e);
                } catch (Exception anException) {
                    Log.e(LOG_TAG, "invite exception " + anException.getMessage());
                }
            }

            @Override
            public void onUnexpectedError(Exception e) {
                try {
                    callback.onUnexpectedError(e);
                } catch (Exception anException) {
                    Log.e(LOG_TAG, "invite exception " + anException.getMessage());
                }
            }
        });
    }

    /**
     * Leave the room.
     * @param callback the callback for when done
     */
    public void leave(final ApiCallback<Void> callback) {
        this.mIsLeaving = true;
        mDataHandler.onRoomInternalUpdate(mRoomId);

        mDataRetriever.getRoomsRestClient().leaveRoom(mRoomId, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                Room.this.mIsLeaving = false;

                // delete references to the room
                mDataHandler.getStore().deleteRoom(mRoomId);
                mDataHandler.getStore().commit();

                try {
                    callback.onSuccess(info);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "leave exception " + e.getMessage());
                }
            }

            @Override
            public void onNetworkError(Exception e) {
                Room.this.mIsLeaving = false;

                try {
                    callback.onNetworkError(e);
                } catch (Exception anException) {
                    Log.e(LOG_TAG, "leave exception " + anException.getMessage());
                }

                mDataHandler.onRoomInternalUpdate(mRoomId);
            }

            @Override
            public void onMatrixError(MatrixError e) {
                Room.this.mIsLeaving = false;

                try {
                    callback.onMatrixError(e);
                } catch (Exception anException) {
                    Log.e(LOG_TAG, "leave exception " + anException.getMessage());
                }

                mDataHandler.onRoomInternalUpdate(mRoomId);
            }

            @Override
            public void onUnexpectedError(Exception e) {
                Room.this.mIsLeaving = false;

                try {
                    callback.onUnexpectedError(e);
                } catch (Exception anException) {
                    Log.e(LOG_TAG, "leave exception " + anException.getMessage());
                }

                mDataHandler.onRoomInternalUpdate(mRoomId);
            }
        });
    }

    /**
     * Kick a user from the room.
     * @param userId the user id
     * @param callback the async callback
     */
    public void kick(String userId, ApiCallback<Void> callback) {
        mDataRetriever.getRoomsRestClient().kickFromRoom(mRoomId, userId, callback);
    }

    /**
     * Ban a user from the room.
     * @param userId the user id
     * @param reason ban readon
     * @param callback the async callback
     */
    public void ban(String userId, String reason, ApiCallback<Void> callback) {
        BannedUser user = new BannedUser();
        user.userId = userId;
        if (!TextUtils.isEmpty(reason)) {
            user.reason = reason;
        }
        mDataRetriever.getRoomsRestClient().banFromRoom(mRoomId, user, callback);
    }

    /**
     * Update the power level of the user userId
     * @param userId the user id
     * @param powerLevel the new power level
     * @param callback the callback with the created event
     */
    public void updateUserPowerLevels(String userId, int powerLevel, ApiCallback<Void> callback) {
        PowerLevels powerLevels = getLiveState().getPowerLevels().deepCopy();
        powerLevels.setUserPowerLevel(userId, powerLevel);
        mDataRetriever.getRoomsRestClient().updatePowerLevels(mRoomId, powerLevels, callback);
    }

    /**
     * Unban a user.
     * @param userId the user id
     * @param callback the async callback
     */
    public void unban(String userId, ApiCallback<Void> callback) {
        // Unbanning is just setting a member's state to left, like kick
        kick(userId, callback);
    }

    /**
     * Update the room's name.
     * @param name the new name
     * @param callback the async callback
     */
    public void updateName(String name, ApiCallback<Void> callback) {
        mDataRetriever.getRoomsRestClient().updateName(getRoomId(), name, callback);
    }

    /**
     * Update the room's topic.
     * @param topic the new topic
     * @param callback the async callback
     */
    public void updateTopic(String topic, ApiCallback<Void> callback) {
        mDataRetriever.getRoomsRestClient().updateTopic(getRoomId(), topic, callback);
    }

    /**
     * Redact an event from the room.
     * @param eventId the event's id
     * @param callback the callback with the created event
     */
    public void redact(String eventId, ApiCallback<Event> callback) {
        mDataRetriever.getRoomsRestClient().redact(getRoomId(), eventId, callback);
    }

    /**
     * Get typing users
     * @return the userIds list
     */
    public ArrayList<String> getTypingUsers() {
        return (null == mTypingUsers) ? new ArrayList<String>() : mTypingUsers;
    }

    /**
     * @return true if the user joined the room
     */
    public boolean selfJoined() {
        RoomMember roomMember = getMember(mMyUserId);

        // send the event only if the user has joined the room.
        return ((null != roomMember) && RoomMember.MEMBERSHIP_JOIN.equals(roomMember.membership));
    }

    /**
     * Send a typing notification
     * @param isTyping typing status
     * @param timeout the typing timeout
     */
    public void sendTypingNotification(boolean isTyping, int timeout, ApiCallback<Void> callback) {
        // send the event only if the user has joined the room.
        if (selfJoined()) {
            mDataRetriever.getRoomsRestClient().sendTypingNotification(mRoomId, mMyUserId, isTyping, timeout, callback);
        }
    }


    /**
     * Fills the fileMessage fileInfo.
     * @param context Application context for the content resolver.
     * @param fileMessage The fileMessage to fill.
     * @param fileUri The file uri.
     * @param mimeType The mimeType
     */
    public static void fillFileInfo(Context context, FileMessage fileMessage, Uri fileUri, String mimeType) {
        try {
            FileInfo fileInfo = new FileInfo();

            String filename = fileUri.getPath();
            File file = new File(filename);

            fileInfo.mimetype = mimeType;
            fileInfo.size = file.length();

            fileMessage.info = fileInfo;

        } catch (Exception e) {
        }
    }

    /**
     * Fills the imageMessage imageInfo.
     * @param context Application context for the content resolver.
     * @param imageMessage The imageMessage to fill.
     * @param imageUri The fullsize image uri.
     * @param mimeType The image mimeType
     * @return The orientation value, which may be {@link ExifInterface#ORIENTATION_UNDEFINED}.
     */
    public static void fillImageInfo(Context context, ImageMessage imageMessage, Uri imageUri, String mimeType) {
        try {
            ImageInfo imageInfo = new ImageInfo();

            String filename = imageUri.getPath();
            File file = new File(filename);

            ExifInterface exifMedia = new ExifInterface(filename);
            String sWidth = exifMedia.getAttribute(ExifInterface.TAG_IMAGE_WIDTH);
            String sHeight = exifMedia.getAttribute(ExifInterface.TAG_IMAGE_LENGTH);

            // the image rotation is replaced by orientation
            // imageInfo.rotation = ImageUtils.getRotationAngleForBitmap(context, imageUri);
            imageInfo.orientation = ImageUtils.getOrientationForBitmap(context, imageUri);

            int width = 0;
            int height = 0;

            // extract the Exif info
            if ((null != sWidth) && (null != sHeight)) {

                if ( (imageInfo.orientation  == ExifInterface.ORIENTATION_TRANSPOSE) ||
                     (imageInfo.orientation  == ExifInterface.ORIENTATION_ROTATE_90) ||
                     (imageInfo.orientation  == ExifInterface.ORIENTATION_TRANSVERSE) ||
                     (imageInfo.orientation  == ExifInterface.ORIENTATION_ROTATE_270)) {
                    height = Integer.parseInt(sWidth);
                    width = Integer.parseInt(sHeight);
                } else {
                    width = Integer.parseInt(sWidth);
                    height = Integer.parseInt(sHeight);
                }
            }

            // there is no exif info or the size is invalid
            if ((0 == width) || (0 == height)) {
                try {
                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(imageUri.getPath(), opts);

                    // don't need to load the bitmap in memory
                    if ((opts.outHeight > 0) && (opts.outWidth > 0)) {
                        width = opts.outWidth;
                        height = opts.outHeight;
                    }

                } catch (Exception e) {
                }
            }

            // valid image size ?
            if ((0 != width) || (0 != height)) {
                imageInfo.w = width;
                imageInfo.h = height;
            }

            imageInfo.mimetype = mimeType;
            imageInfo.size = file.length();

            imageMessage.info = imageInfo;

        } catch (Exception e) {
        }
    }

    /**
     * Resend the unsent messages.
     */
    public void resendUnsentEvents() {
        resendUnsentEvents(-1);
    }

    /**
     * Returns the unsent messages except the sending ones.
     * @return the unsent messages list.
     */
    private ArrayList<Event> getUnsentEvents() {
        Collection<Event> events = mDataHandler.getStore().getLatestUnsentEvents(mRoomId);

        ArrayList<Event> eventsList = new ArrayList<Event>(events);
        ArrayList<Event> unsentEvents = new ArrayList<Event>();

        // check if some events are already sending
        // to avoid send them twice
        // some network issues could happen
        // eg connected send some unsent messages but do not send all of them
        // deconnected -> connected : some messages could be sent twice
        for (Event event : eventsList) {
            if (event.mSentState == Event.SentState.WAITING_RETRY) {
                event.mSentState = Event.SentState.SENDING;
                unsentEvents.add(event);
            }
        }

        return unsentEvents;
    }

    /**
     * Check the undeliverable events.
     * Warn the application if some are found.
     */
    private void checkUndeliverableEvents() {
        ArrayList<Event> unsentEvents = getUnsentEvents();

        for(Event event : unsentEvents) {
            if ((System.currentTimeMillis() - event.getOriginServerTs()) > MAX_MESSAGE_TIME_LIFE_MS) {
                event.mSentState = Event.SentState.UNDELIVERABLE;
                mDataHandler.onResentEvent(event);
            }
        }
    }

    /**
     * Resend the unsent messages during a time  interval.
     * @param timeInterval define the time interval in ms to resend the messages to avoid application lock.
     */
    public void resendUnsentEvents(int timeInterval) {
        resendEventsList(getUnsentEvents(), 0,(timeInterval > 0) ? (System.currentTimeMillis() + timeInterval) : Long.MAX_VALUE);
    }

    /**
     * Resend events list.
     * Wait that the event is resent before sending the next one
     * to keep the genuine order
     */
    private void resendEventsList(final ArrayList<Event> evensList, final int index, final long maxTime) {

        if ((evensList.size() > 0) && (index < evensList.size()) && (System.currentTimeMillis() < maxTime)) {
            final Event unsentEvent = evensList.get(index);

            // is the event too old to be resent ?
            if ((System.currentTimeMillis() - unsentEvent.getOriginServerTs()) > MAX_MESSAGE_TIME_LIFE_MS) {

                unsentEvent.mSentState = Event.SentState.UNDELIVERABLE;

                // warn that the event has been resent
                mDataHandler.onResentEvent(unsentEvent);

                // send the next one
                Room.this.resendEventsList(evensList, index + 1, maxTime);

            } else {
                unsentEvent.mSentState = Event.SentState.SENDING;
                mDataHandler.onResendingEvent(unsentEvent);

                boolean hasPreviousTask = false;
                final Message message = JsonUtils.toMessage(unsentEvent.content);

                if (message instanceof ImageMessage) {
                    final ImageMessage imageMessage = (ImageMessage) message;

                    if (imageMessage.isLocalContent()) {
                        // delete the previous image message
                        mDataHandler.deleteRoomEvent(unsentEvent);
                        mDataHandler.onDeleteEvent(unsentEvent);

                        final Event newEvent = new Event(message, mMyUserId, mRoomId);
                        evensList.set(index, newEvent);

                        mDataHandler.storeLiveRoomEvent(unsentEvent);
                        mDataHandler.onLiveEvent(newEvent, getLiveState());

                        String filename;
                        // try to parse it
                        try {
                            Uri uri = Uri.parse(imageMessage.url);
                            filename = uri.getPath();
                            FileInputStream fis = new FileInputStream(new File(filename));

                            hasPreviousTask = true;

                            if (null != fis) {
                                mContentManager.uploadContent(fis, imageMessage.info.mimetype, imageMessage.url, imageMessage.body, new ContentManager.UploadCallback() {
                                    @Override
                                    public void onUploadProgress(String anUploadId, int percentageProgress) {
                                    }

                                    @Override
                                    public void onUploadComplete(final String anUploadId, final ContentResponse uploadResponse, final int serverResponseCode,  final String serverErrorMessage) {
                                        ImageMessage uploadedMessage = (ImageMessage) JsonUtils.toMessage(newEvent.content);

                                        uploadedMessage.thumbnailUrl = imageMessage.thumbnailUrl;

                                        if ((null != uploadResponse) && (null != uploadResponse.contentUri)) {
                                            uploadedMessage.url = uploadResponse.contentUri;
                                        } else {
                                            uploadedMessage.url = imageMessage.url;
                                        }

                                        uploadedMessage.info = imageMessage.info;
                                        uploadedMessage.body = imageMessage.body;

                                        // update the content
                                        newEvent.content = JsonUtils.toJson(uploadedMessage);

                                        // send the body
                                        Room.this.resendEventsList(evensList, index, maxTime);
                                    }
                                });
                            }
                        } catch (Exception e) {

                        }
                    }
                }

                // no pending request
                if (!hasPreviousTask) {
                    final Event unsentEventCopy = unsentEvent.deepCopy();

                    mDataHandler.onResendingEvent(unsentEvent);

                    sendEvent(unsentEvent, new ApiCallback<Void>() {
                        private Event storeUnsentMessage() {
                            Event dummyEvent = new Event(message, mMyUserId, mRoomId);
                            // create a dummy identifier
                            dummyEvent.createDummyEventId();
                            mDataHandler.storeLiveRoomEvent(dummyEvent);

                            return dummyEvent;
                        }

                        private void common(Event sentEvent, Exception exception, MatrixError matrixError) {
                            // replace the resent event
                            mDataHandler.deleteRoomEvent(unsentEventCopy);
                            mDataHandler.onDeleteEvent(unsentEventCopy);

                            // with a new one
                            unsentEvent.eventId = sentEvent.eventId;
                            unsentEvent.mSentState = sentEvent.mSentState;
                            unsentEvent.unsentException = exception;
                            unsentEvent.unsentMatrixError = matrixError;

                            mDataHandler.onLiveEvent(unsentEvent, getLiveState());

                            // send the next one
                            Room.this.resendEventsList(evensList, index + 1, maxTime);
                        }

                        @Override
                        public void onSuccess(Void info) {
                            common(unsentEvent, null, null);
                        }

                        @Override
                        public void onNetworkError(Exception e) {
                            common(storeUnsentMessage(), e, null);
                        }

                        @Override
                        public void onMatrixError(MatrixError e) {
                            common(storeUnsentMessage(), null, e);
                        }

                        @Override
                        public void onUnexpectedError(Exception e) {
                            common(storeUnsentMessage(), e, null);
                        }
                    });
                }
            }
        } else {
            boolean mustCheckUnsent = false;

            synchronized (this) {
                isResendingEvents = false;
                mustCheckUnsent = checkUnsentMessages;
                checkUnsentMessages = false;
            }

            // the timeout should be done on each request but assume
            // that the requests are sent in pools so the timeout is valid for it.
            if ((mustCheckUnsent) && (System.currentTimeMillis() < maxTime)) {
                resendUnsentEvents(MAX_RATE_LIMIT_MS);
            } else {
                Event lastEvent = null;

                for(int subindex = index; subindex < evensList.size(); subindex++) {
                    lastEvent = evensList.get(subindex);
                    lastEvent.mSentState = Event.SentState.UNDELIVERABLE;
                }

                if (null != lastEvent) {
                    mDataHandler.onLiveEvent(lastEvent, getLiveState());
                }
            }

            if ((evensList.size() > 0)) {
                mDataHandler.onLiveEventsChunkProcessed();
            }
        }
    }

    /**
     * Test if a call can be performed in this room.
     * @return true if a call can be performed.
     */
    public Boolean canPerformCall() {
        return 1 == callees().size();
    }

    /**
     * @return a list of callable members.
     */
    public ArrayList<RoomMember> callees() {
        ArrayList<RoomMember> res = new ArrayList<RoomMember>();

        Collection<RoomMember> members = getMembers();

        for(RoomMember m : members) {
            if (RoomMember.MEMBERSHIP_JOIN.equals(m.membership) && !mMyUserId.equals(m.getUserId())) {
                res.add(m);
            }
        }

        return res;
    }
}
