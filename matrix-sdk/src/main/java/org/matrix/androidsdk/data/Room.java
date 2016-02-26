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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Looper;
import android.text.BoringLayout;
import android.text.TextUtils;
import android.util.Log;
import android.widget.ExpandableListAdapter;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import org.matrix.androidsdk.MXDataHandler;
import org.matrix.androidsdk.MXSession;
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
import org.matrix.androidsdk.rest.model.LocationMessage;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.Message;
import org.matrix.androidsdk.rest.model.PowerLevels;
import org.matrix.androidsdk.rest.model.ReceiptData;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.RoomResponse;
import org.matrix.androidsdk.rest.model.SyncV2.InvitedRoomSync;
import org.matrix.androidsdk.rest.model.SyncV2.RoomSync;
import org.matrix.androidsdk.rest.model.ThumbnailInfo;
import org.matrix.androidsdk.rest.model.TokensChunkResponse;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.rest.model.VideoInfo;
import org.matrix.androidsdk.rest.model.VideoMessage;
import org.matrix.androidsdk.util.ContentManager;
import org.matrix.androidsdk.util.ImageUtils;
import org.matrix.androidsdk.util.JsonUtils;
import org.w3c.dom.Text;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
    public enum EventDirection {
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
    public class SnapshotedEvent {
        public Event mEvent;
        public RoomState mState;

        public SnapshotedEvent(Event event, RoomState state) {
            mEvent = event;
            mState = state;
        }
    }

    // avoid adding to many events
    // the room history request can provide more than exxpected event.
    private ArrayList<SnapshotedEvent> mSnapshotedEvents = new ArrayList<SnapshotedEvent>();

    private String mRoomId;
    private RoomState mLiveState = new RoomState();
    private RoomState mBackState = new RoomState();
    private RoomAccountData mAccountData = new RoomAccountData();

    private DataRetriever mDataRetriever;
    private MXDataHandler mDataHandler;
    private ContentManager mContentManager;

    private String mMyUserId = null;

    // Map to keep track of the listeners the client adds vs. the ones we actually register to the global data handler.
    // This is needed to find the right one when removing the listener.
    private Map<IMXEventListener, IMXEventListener> mEventListeners = new HashMap<IMXEventListener, IMXEventListener>();

    private boolean isPaginating = false;
    private boolean canStillPaginate = true;
    private boolean mIsLastChunk;
    // the server provides a token even for the first room message (which should never change it is the creator message)
    // so requestHistory always triggers a remote request which returns an empty json.
    //  try to avoid such behaviour
    private String mTopToken;
    // This is used to block live events and history requests until the state is fully processed and ready
    private boolean mIsReady = false;

    private android.os.Handler handler = new android.os.Handler(Looper.getMainLooper());

    private boolean isResendingEvents = false;
    private boolean checkUnsentMessages = false;

    private boolean mIsLeaving = false;

    private boolean mIsV2Syncing;

    private ApiCallback<Void> mOnInitialSyncCallback;

    private Gson gson = new GsonBuilder().create();

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

    public void setAccountData(RoomAccountData accountData) {
        this.mAccountData = accountData;
    }

    public RoomAccountData getAccountData() {
        return this.mAccountData;
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
     * @return true if the user is invited to the room
     */
    public boolean isInvited() {
        // Is it an initial sync for this room ?
        RoomState liveState = getLiveState();
        String membership = null;

        RoomMember selfMember = liveState.getMember(mMyUserId);

        if (null != selfMember) {
            membership = selfMember.membership;
        }

        return TextUtils.equals(membership, RoomMember.MEMBERSHIP_INVITE);
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
        mLiveState.refreshUsersList();
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
                if (getMember(user.user_id) != null) {
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
                if (TextUtils.equals(mRoomId, event.roomId) && mIsReady) {

                    if (TextUtils.equals(event.type, Event.EVENT_TYPE_TYPING)) {
                        // Typing notifications events are not room messages nor room state events
                        // They are just volatile information

                        JsonObject eventContent = event.getContentAsJsonObject();

                        if (eventContent.has("user_ids")) {
                            synchronized (Room.this) {
                                mTypingUsers = null;

                                try {
                                    mTypingUsers = (new Gson()).fromJson(eventContent.get("user_ids"), new TypeToken<List<String>>() {
                                    }.getType());
                                } catch (Exception e) {
                                    Log.e(LOG_TAG, "onLiveEvent exception " + e.getMessage());
                                }

                                // avoid null list
                                if (null == mTypingUsers) {
                                    mTypingUsers = new ArrayList<String>();
                                }
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
                if (TextUtils.equals(mRoomId, event.roomId)) {
                    try {
                        eventListener.onBackEvent(event, roomState);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "onBackEvent exception " + e.getMessage());
                    }
                }
            }

            @Override
            public void onSendingEvent(Event event) {
                // Filter out events for other rooms
                if (TextUtils.equals(mRoomId, event.roomId)) {
                    try {
                        eventListener.onSendingEvent(event);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "onSendingEvent exception " + e.getMessage());
                    }
                }
            }

            @Override
            public void onSentEvent(Event event) {
                // Filter out events for other rooms
                if (TextUtils.equals(mRoomId, event.roomId)) {
                    try {
                        eventListener.onSentEvent(event);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "onSentEvent exception " + e.getMessage());
                    }
                }
            }


            @Override
            public void onFailedSendingEvent(Event event) {
                // Filter out events for other rooms
                if (TextUtils.equals(mRoomId, event.roomId)) {
                    try {
                        eventListener.onFailedSendingEvent(event);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "onFailedSendingEvent exception " + e.getMessage());
                    }
                }
            }

            @Override
            public void onDeleteEvent(Event event) {
                // Filter out events for other rooms
                if (TextUtils.equals(mRoomId, event.roomId)) {
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
                if (TextUtils.equals(mRoomId, event.roomId)) {
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
                if (TextUtils.equals(mRoomId, roomId)) {
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
                if (TextUtils.equals(mRoomId, roomId)) {
                    try {
                        eventListener.onRoomInternalUpdate(roomId);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "onRoomInternalUpdate exception " + e.getMessage());
                    }
                }
            }

            @Override
            public void onNewRoom(String roomId) {
                // Filter out events for other rooms
                if (TextUtils.equals(mRoomId, roomId)) {
                    try {
                        eventListener.onNewRoom(roomId);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "onNewRoom exception " + e.getMessage());
                    }
                }
            }

            @Override
            public void onJoinRoom(String roomId) {
                // Filter out events for other rooms
                if (TextUtils.equals(mRoomId, roomId)) {
                    try {
                        eventListener.onJoinRoom(roomId);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "onJoinRoom exception " + e.getMessage());
                    }
                }
            }

            @Override
            public void onReceiptEvent(String roomId, List<String> senderIds) {
                // Filter out events for other rooms
                if (TextUtils.equals(mRoomId, roomId)) {
                    try {
                        eventListener.onReceiptEvent(roomId, senderIds);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "onReceiptEvent exception " + e.getMessage());
                    }
                }
            }

            @Override
            public void onRoomTagEvent(String roomId) {
                // Filter out events for other rooms
                if (TextUtils.equals(mRoomId, roomId)) {
                    try {
                        eventListener.onRoomTagEvent(roomId);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "onRoomTagEvent exception " + e.getMessage());
                    }
                }
            }

            @Override
            public void onRoomSyncWithLimitedTimeline(String roomId) {
                // Filter out events for other rooms
                if (TextUtils.equals(mRoomId, roomId)) {
                    try {
                        eventListener.onRoomSyncWithLimitedTimeline(roomId);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "onRoomSyncWithLimitedTimeline exception " + e.getMessage());
                    }
                }
            }

            @Override
            public void onLeaveRoom(String roomId) {
                // Filter out events for other rooms
                if (TextUtils.equals(mRoomId, roomId)) {
                    try {
                        eventListener.onLeaveRoom(roomId);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "onLeaveRoom exception " + e.getMessage());
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
        isPaginating = false;

        mDataRetriever.cancelHistoryRequest(mRoomId);
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
        if (mDataHandler.isActive()) {
            for (Event event : stateEvents) {
                try {
                    processStateEvent(event, EventDirection.FORWARDS);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "processLiveState failed " + e.getLocalizedMessage());
                }
            }
            mIsReady = true;
        }
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

                    mDataHandler.onSentEvent(event);

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

                    mDataHandler.onSendingEvent(event);

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

                    mDataHandler.onSendingEvent(event);

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

                    mDataHandler.onSendingEvent(event);

                    try {
                        callback.onUnexpectedError(e);
                    } catch (Exception anException) {
                        Log.e(LOG_TAG, "sendEvent exception " + anException.getMessage());
                    }
                }
            };

        event.mSentState = Event.SentState.SENDING;

        mDataHandler.onSendingEvent(event);

        if (Event.EVENT_TYPE_MESSAGE.equals(event.type)) {
            mDataRetriever.getRoomsRestClient().sendMessage(event.originServerTs + "", mRoomId, JsonUtils.toMessage(event.content), localCB);
        } else {
            mDataRetriever.getRoomsRestClient().sendEvent(mRoomId, event.type, event.content.getAsJsonObject(), localCB);
        }
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
     * Send MAX_EVENT_COUNT_PER_PAGINATION events to the caller.
     * @param callback the callback.
     */
    private void manageEvents(final ApiCallback<Integer> callback) {
        // check if the SDK was not logged out
        if (!mDataHandler.isActive()) {
            return;
        }

        int count = Math.min(mSnapshotedEvents.size(), MAX_EVENT_COUNT_PER_PAGINATION);

        for(int i = 0; i < count; i++) {
            SnapshotedEvent snapshotedEvent = mSnapshotedEvents.get(0);
            mSnapshotedEvents.remove(0);
            mDataHandler.onBackEvent(snapshotedEvent.mEvent, snapshotedEvent.mState);
        }

        if ((mSnapshotedEvents.size() < MAX_EVENT_COUNT_PER_PAGINATION) && mIsLastChunk) {
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
        Log.d(LOG_TAG, "manageEvents : commit");
        mDataHandler.getStore().commit();
    }

    //================================================================================
    // History request
    //================================================================================

    /**
     * Request older messages. They will come down the onBackEvent callback.
     * @param callback callback to implement to be informed that the pagination request has been completed. Can be null.
     * @return true if request starts
     */
    public boolean requestHistory(final ApiCallback<Integer> callback) {
        if (isPaginating // One at a time please
                || !mLiveState.canBackPaginated(mMyUserId) // history_visibility flag management
                || !canStillPaginate // If we have already reached the end of history
                || !mIsReady) { // If the room is not finished being set up

            Log.d(LOG_TAG, "cannot requestHistory " + isPaginating + " " + !mLiveState.canBackPaginated(mMyUserId) + " " + !canStillPaginate + " " + !mIsReady);

            return false;
        }
        isPaginating = true;

        // restart the pagination
        if (null == mBackState.getToken()) {
            mSnapshotedEvents.clear();
        }

        // enough buffered data
        if (mSnapshotedEvents.size() >= MAX_EVENT_COUNT_PER_PAGINATION) {
            final android.os.Handler handler = new android.os.Handler(Looper.getMainLooper());

            // call the callback with a delay
            // to reproduce the same behaviour as a network request.
            Runnable r = new Runnable() {
                @Override
                public void run() {
                    handler.postDelayed(new Runnable() {
                        public void run() {
                            manageEvents(callback);
                        }
                    }, 100);
                }
            };

            Thread t = new Thread(r);
            t.start();

            return true;
        }

        final String fromToken = mBackState.getToken();

        mDataRetriever.requestRoomHistory(mRoomId, mBackState.getToken(), new SimpleApiCallback<TokensChunkResponse<Event>>(callback) {
            @Override
            public void onSuccess(TokensChunkResponse<Event> response) {
                if (mDataHandler.isActive()) {

                    if (response.chunk.size() > 0) {
                        mBackState.setToken(response.end);

                        RoomSummary summary = mDataHandler.getStore().getSummary(mRoomId);
                        Boolean shouldCommitStore = false;

                        // the room state is copied to have a state snapshot
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
                                // update the summary is the event has been received after the oldest known event
                                // it might happen after a timeline update (hole in the chat history)
                                if ((null != summary) && (summary.getLatestEvent().originServerTs < event.originServerTs) && RoomSummary.isSupportedEvent(event)) {
                                    summary =  mDataHandler.getStore().storeSummary(mRoomId, event, getLiveState(), mMyUserId);
                                    shouldCommitStore = true;
                                }

                                mSnapshotedEvents.add(new SnapshotedEvent(event, stateCopy));
                            }
                        }

                        if (shouldCommitStore) {
                            mDataHandler.getStore().commit();
                        }
                    }

                    // assume it is the first room message
                    if (0 == response.chunk.size()) {
                        // save its token to avoid useless request
                        mTopToken = fromToken;
                    }

                    mIsLastChunk = (0 == response.chunk.size()) || TextUtils.isEmpty(response.end) || TextUtils.equals(response.end, mTopToken);

                    if (mIsLastChunk) {
                        Log.d(LOG_TAG, "is last chunck" + (0 == response.chunk.size()) + " " + TextUtils.isEmpty(response.end)  + " " + TextUtils.equals(response.end, mTopToken));
                    }

                    manageEvents(callback);
                }
            }

            @Override
            public void onMatrixError(MatrixError e) {
                // When we've retrieved all the messages from a room, the pagination token is some invalid value
                if (MatrixError.UNKNOWN.equals(e.errcode)) {
                    canStillPaginate = false;
                }
                isPaginating = false;

                if (null != callback) {
                    callback.onMatrixError(e);
                } else {
                    super.onMatrixError(e);
                }
            }

            @Override
            public void onNetworkError(Exception e) {
                isPaginating = false;

                if (null != callback) {
                    callback.onNetworkError(e);
                } else {
                    super.onNetworkError(e);
                }
            }

            @Override
            public void onUnexpectedError(Exception e) {
                isPaginating = false;

                if (null != callback) {
                    callback.onUnexpectedError(e);
                } else {
                    super.onUnexpectedError(e);
                }
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
     * Perform a room-level initial sync to get latest messages and pagination token.
     * @param callback the async callback
     */
    public void initialSync(final ApiCallback<Void> callback) {
            mDataRetriever.getRoomsRestClient().initialSync(mRoomId, new SimpleApiCallback<RoomResponse>(callback) {
                @Override
                public void onSuccess(RoomResponse roomInfo) {
                    // check if the SDK was not logged out
                    if (mDataHandler.isActive()) {
                        mDataHandler.handleInitialRoomResponse(roomInfo, Room.this);

                        Log.d(LOG_TAG, "initialSync : commit");
                        mDataHandler.getStore().commit();
                        if (callback != null) {
                            try {
                                callback.onSuccess(null);
                            } catch (Exception e) {
                                Log.e(LOG_TAG, "initialSync exception " + e.getMessage());
                            }
                        }
                    }
                }
            });
    }

    //================================================================================
    // Join
    //================================================================================

    /**
     * Join the room. If successful, the room's current state will be loaded before calling back onComplete.
     * @param callback the callback for when done
     */
    public void join(final ApiCallback<Void> callback) {
        mDataRetriever.getRoomsRestClient().joinRoom(mRoomId, new SimpleApiCallback<RoomResponse>(callback) {
            @Override
            public void onSuccess(final RoomResponse aReponse) {
                try {
                    if (MXSession.useSyncV2()) {
                        // wait the server sends the events chunk before calling the callback
                        mOnInitialSyncCallback = callback;
                    } else {
                        // Once we've joined, we run an initial sync on the room to have all of its information
                        initialSync(callback);
                    }

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
     * Shorthand for {@link #join(org.matrix.androidsdk.rest.callback.ApiCallback)} with a null callback.
     */
    public void join() {
        join(null);
    }

    /**
     * @return true if the user joined the room
     */
    public boolean selfJoined() {
        RoomMember roomMember = getMember(mMyUserId);

        // send the event only if the user has joined the room.
        return ((null != roomMember) && RoomMember.MEMBERSHIP_JOIN.equals(roomMember.membership));
    }

    //================================================================================
    // Member actions
    //================================================================================

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
                if (mDataHandler.isActive()) {
                    Room.this.mIsLeaving = false;

                    // delete references to the room
                    mDataHandler.getStore().deleteRoom(mRoomId);
                    Log.d(LOG_TAG, "leave : commit");
                    mDataHandler.getStore().commit();

                    try {
                        callback.onSuccess(info);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "leave exception " + e.getMessage());
                    }

                    mDataHandler.onLeaveRoom(mRoomId);
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
     * Unban a user.
     * @param userId the user id
     * @param callback the async callback
     */
    public void unban(String userId, ApiCallback<Void> callback) {
        // Unbanning is just setting a member's state to left, like kick
        kick(userId, callback);
    }

    //================================================================================
    // Room info (liveState) update
    //================================================================================

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
     * Update the room's name.
     * @param name the new name
     * @param callback the async callback
     */
    public void updateName(final String name, final ApiCallback<Void> callback) {
        mDataRetriever.getRoomsRestClient().updateName(getRoomId(), name, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                mLiveState.name = name;
                mDataHandler.getStore().storeLiveStateForRoom(mRoomId);

                if (null != callback) {
                    callback.onSuccess(info);
                }
            }

            @Override
            public void onNetworkError(Exception e) {
                if (null != callback) {
                    callback.onNetworkError(e);
                }
            }

            @Override
            public void onMatrixError(MatrixError e) {
                if (null != callback) {
                    callback.onMatrixError(e);
                }
            }

            @Override
            public void onUnexpectedError(Exception e) {
                if (null != callback) {
                    callback.onUnexpectedError(e);
                }
            }
        });
    }

    /**
     * Update the room's topic.
     * @param topic the new topic
     * @param callback the async callback
     */
    public void updateTopic(final String topic, final ApiCallback<Void> callback) {
        mDataRetriever.getRoomsRestClient().updateTopic(getRoomId(), topic, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                mLiveState.topic = topic;
                mDataHandler.getStore().storeLiveStateForRoom(mRoomId);

                if (null != callback) {
                    callback.onSuccess(info);
                }
            }

            @Override
            public void onNetworkError(Exception e) {
                if (null != callback) {
                    callback.onNetworkError(e);
                }
            }

            @Override
            public void onMatrixError(MatrixError e) {
                if (null != callback) {
                    callback.onMatrixError(e);
                }
            }

            @Override
            public void onUnexpectedError(Exception e) {
                if (null != callback) {
                    callback.onUnexpectedError(e);
                }
            }
        });
    }

    /**
     * Update the room's main alias.
     * @param canonicalAlias the canonical alias
     * @param callback the async callback
     */
    public void updateCanonicalAlias(final String canonicalAlias, final ApiCallback<Void> callback) {
        mDataRetriever.getRoomsRestClient().updateCanonicalAlias(getRoomId(), canonicalAlias, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                mLiveState.roomAliasName = canonicalAlias;
                mDataHandler.getStore().storeLiveStateForRoom(mRoomId);

                if (null != callback) {
                    callback.onSuccess(info);
                }
            }

            @Override
            public void onNetworkError(Exception e) {
                if (null != callback) {
                    callback.onNetworkError(e);
                }
            }

            @Override
            public void onMatrixError(MatrixError e) {
                if (null != callback) {
                    callback.onMatrixError(e);
                }
            }

            @Override
            public void onUnexpectedError(Exception e) {
                if (null != callback) {
                    callback.onUnexpectedError(e);
                }
            }
        });
    }

    /**
     * @return the room avatar URL. If there is no defined one, use the members one (1:1 chat only).
     */
    public String getAvatarUrl() {
        String res = mLiveState.getAvatarUrl();

        // detect if it is a room with no more than 2 members (i.e. an alone or a 1:1 chat)
        if (null == res) {
            Collection<RoomMember> members = mLiveState.getMembers();

            if (members.size() < 3) {
                // use the member avatar only it is an active member
                for (RoomMember roomMember : members) {
                    if (TextUtils.equals(RoomMember.MEMBERSHIP_JOIN, roomMember.membership) && ((members.size() == 1) || !TextUtils.equals(mMyUserId, roomMember.getUserId()))) {
                        res = roomMember.avatarUrl;
                        break;
                    }
                }
            }
        }

        return res;
    }


    /**
     * Update the room avatar URL.
     * @param avatarUrl the new avatar URL
     * @param callback the async callback
     */
    public void updateAvatarUrl(final String avatarUrl, final ApiCallback<Void> callback) {
        mDataRetriever.getRoomsRestClient().updateAvatarUrl(getRoomId(), avatarUrl, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                mLiveState.url = avatarUrl;
                mDataHandler.getStore().storeLiveStateForRoom(mRoomId);

                if (null != callback) {
                    callback.onSuccess(info);
                }
            }

            @Override
            public void onNetworkError(Exception e) {
                if (null != callback) {
                    callback.onNetworkError(e);
                }
            }

            @Override
            public void onMatrixError(MatrixError e) {
                if (null != callback) {
                    callback.onMatrixError(e);
                }
            }

            @Override
            public void onUnexpectedError(Exception e) {
                if (null != callback) {
                    callback.onUnexpectedError(e);
                }
            }
        });
    }

    /**
     * Update the room's visibility
     * @param visibility the visibility (should be one of RoomState.HISTORY_VISIBILITY_XX values)
     * @param callback the async callback
     */
    public void updateHistoryVisibility(final String visibility, final ApiCallback<Void> callback) {
        mDataRetriever.getRoomsRestClient().updateHistoryVisibility(getRoomId(), visibility, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                mLiveState.visibility = visibility;
                mDataHandler.getStore().storeLiveStateForRoom(mRoomId);

                if (null != callback) {
                    callback.onSuccess(info);
                }
            }

            @Override
            public void onNetworkError(Exception e) {
                if (null != callback) {
                    callback.onNetworkError(e);
                }
            }

            @Override
            public void onMatrixError(MatrixError e) {
                if (null != callback) {
                    callback.onMatrixError(e);
                }
            }

            @Override
            public void onUnexpectedError(Exception e) {
                if (null != callback) {
                    callback.onUnexpectedError(e);
                }
            }
        });
    }

    //================================================================================
    // Read receipts events
    //================================================================================

    /**
     * Handle a receiptData.
     * @param receiptData the receiptData.
     * @return true if there a store update.
     */
    public Boolean handleReceiptData(ReceiptData receiptData) {
        Boolean isUpdated = mDataHandler.getStore().storeReceipt(receiptData, mRoomId);

        // check oneself receipts
        // if there is an update, it means that the messages have been read from andother client
        // it requires to update the summary to display valid information.
        if (isUpdated && TextUtils.equals(mMyUserId, receiptData.userId)) {
            RoomSummary summary = mDataHandler.getStore().getSummary(mRoomId);
            if (null != summary) {
                summary.setReadReceiptToken(receiptData.eventId, receiptData.originServerTs);
            }
            refreshUnreadCounter();
        }

        return isUpdated;
    }

    /**
     * Handle receipt event.
     * @param event the event receipts.
     * @return the sender user IDs list.
     */
    public List<String> handleReceiptEvent(Event event) {
        ArrayList<String> senderIDs = new ArrayList<String>();

        try {
            // the receipts dicts
            // key   : $EventId
            // value : dict key $UserId
            //              value dict key ts
            //                    dict value ts value
            Type type = new TypeToken<HashMap<String, HashMap<String, HashMap<String, HashMap<String, Object>>>>>(){}.getType();
            HashMap<String, HashMap<String, HashMap<String, HashMap<String, Object>>>> receiptsDict = gson.fromJson(event.content, type);

            for (String eventId : receiptsDict.keySet() ) {
                HashMap<String, HashMap<String, HashMap<String, Object>>> receiptDict = receiptsDict.get(eventId);

                for (String receiptType : receiptDict.keySet()) {
                    // only the read receipts are managed
                    if (TextUtils.equals(receiptType, "m.read")) {
                        HashMap<String, HashMap<String, Object>> userIdsDict = receiptDict.get(receiptType);

                        for(String userID : userIdsDict.keySet()) {
                            HashMap<String, Object> paramsDict = userIdsDict.get(userID);

                            for(String paramName : paramsDict.keySet()) {
                                if (TextUtils.equals("ts", paramName)) {
                                    Double value = (Double)paramsDict.get(paramName);
                                    long ts = value.longValue();

                                    if (handleReceiptData(new ReceiptData(userID, eventId, ts))) {
                                        senderIDs.add(userID);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
        }

        return senderIDs;
    }

    /**
     * Send the read receipt to the latest room message id.
     */
    public void sendReadReceipt() {
        RoomSummary summary = mDataHandler.getStore().getSummary(mRoomId);
        Event event = mDataHandler.getStore().getLatestEvent(getRoomId());

        if ((null != event) && (null != summary)) {
            // any update
            if (!TextUtils.equals(summary.getReadReceiptToken(), event.eventId)) {
                mDataRetriever.getRoomsRestClientV2().sendReadReceipt(getRoomId(), event.eventId, null);
                setReadReceiptToken(event.eventId, System.currentTimeMillis());
            }
        }
    }

    /**
     * Update the read receipt token.
     * @param token the new token
     * @param ts the token ts
     * @return true if the token is refreshed
     */
    public boolean setReadReceiptToken(String token, long ts) {
        RoomSummary summary = mDataHandler.getStore().getSummary(mRoomId);

        if (summary.setReadReceiptToken(token, ts)) {
            mDataHandler.getStore().flushSummary(summary);
            mDataHandler.getStore().commit();
            refreshUnreadCounter();
            return true;
        }

        return false;
    }

    /**
     * Check if an event has been read.
     * @param eventId the event id
     * @return true if the message has been read
     */
    public boolean isEventRead(String eventId) {
        return mDataHandler.getStore().isEventRead(mRoomId, mMyUserId, eventId);
    }

    //================================================================================
    // Unread event count management
    //================================================================================

    /**
     *  refresh the unread events counts.
     */
    public void refreshUnreadCounter() {
        // avoid refreshing the unread counter while processing a bunch of messages.
        if (!mIsV2Syncing) {
            RoomSummary summary = mDataHandler.getStore().getSummary(mRoomId);

            if (null != summary) {
                int prevValue = summary.getUnreadEventsCount();
                int newValue = mDataHandler.getStore().eventsCountAfter(getRoomId(), summary.getReadReceiptToken());

                if (prevValue != newValue) {
                    summary.setUnreadEventsCount(newValue);
                    mDataHandler.getStore().flushSummary(summary);
                    mDataHandler.getStore().commit();
                }
            }
        }
    }

    /**
     * @return the unread messages count.
     */
    public int getUnreadEventsCount() {
        RoomSummary summary = mDataHandler.getStore().getSummary(mRoomId);

        if (null != summary) {
            return summary.getUnreadEventsCount();
        }
        return 0;
    }

    //================================================================================
    // typing events
    //================================================================================

    /**
     * Get typing users
     * @return the userIds list
     */
    public ArrayList<String> getTypingUsers() {

        ArrayList<String> typingUsers;

        synchronized (Room.this) {
            typingUsers = (null == mTypingUsers) ? new ArrayList<String>() : new ArrayList<String>(mTypingUsers);
        }

        return typingUsers;
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

    //================================================================================
    // Medias events
    //================================================================================

    /**
     * Fill the locationInfo
     * @param context the context
     * @param locationMessage the location message
     * @param thumbnailUri the thumbnail uri
     * @param thumbMimeType the thumbnail mime type
     */
    public static void fillLocationInfo(Context context, LocationMessage locationMessage, Uri thumbnailUri, String thumbMimeType) {
        if (null != thumbnailUri) {
            try {
                locationMessage.thumbnail_url = thumbnailUri.toString();

                ThumbnailInfo thumbInfo = new ThumbnailInfo();
                File thumbnailFile = new File(thumbnailUri.getPath());

                ExifInterface exifMedia = new ExifInterface(thumbnailUri.getPath());
                String sWidth = exifMedia.getAttribute(ExifInterface.TAG_IMAGE_WIDTH);
                String sHeight = exifMedia.getAttribute(ExifInterface.TAG_IMAGE_LENGTH);

                if (null != sWidth) {
                    thumbInfo.w = Integer.parseInt(sWidth);
                }

                if (null != sHeight) {
                    thumbInfo.h = Integer.parseInt(sHeight);
                }

                thumbInfo.size = new Long(thumbnailFile.length());
                thumbInfo.mimetype = thumbMimeType;
                locationMessage.thumbnail_info = thumbInfo;
            } catch (Exception e) {
            }
        }
    }

    /**
     * Fills the VideoMessage info.
     * @param context Application context for the content resolver.
     * @param videoMessage The VideoMessage to fill.
     * @param fileUri The file uri.
     * @param videoMimeType The mimeType
     * @param thumbnailUri the thumbnail uri
     * @param thumbMimeType the thumbnail mime type
     */
    public static void fillVideoInfo(Context context, VideoMessage videoMessage, Uri fileUri, String videoMimeType, Uri thumbnailUri, String thumbMimeType) {
        try {
            VideoInfo videoInfo = new VideoInfo();

            File file = new File(fileUri.getPath());

            MediaMetadataRetriever retriever = new  MediaMetadataRetriever();
            Bitmap bmp = null;
            retriever.setDataSource(file.getAbsolutePath());
            bmp = retriever.getFrameAtTime();
            videoInfo.h = bmp.getHeight();
            videoInfo.w = bmp.getWidth();
            videoInfo.mimetype = videoMimeType;

            try {
                MediaPlayer mp = MediaPlayer.create(context, fileUri);
                if (null != mp) {
                    videoInfo.duration = new Long(mp.getDuration());
                    mp.release();
                }
            } catch (Exception e) {
            }
            videoInfo.size = file.length();

            // thumbnail
            if (null != thumbnailUri) {
                videoInfo.thumbnail_url = thumbnailUri.toString();

                ThumbnailInfo thumbInfo = new ThumbnailInfo();
                File thumbnailFile = new File(thumbnailUri.getPath());

                ExifInterface exifMedia = new ExifInterface(thumbnailUri.getPath());
                String sWidth = exifMedia.getAttribute(ExifInterface.TAG_IMAGE_WIDTH);
                String sHeight = exifMedia.getAttribute(ExifInterface.TAG_IMAGE_LENGTH);

                if (null != sWidth) {
                    thumbInfo.w = Integer.parseInt(sWidth);
                }

                if (null != sHeight) {
                    thumbInfo.h = Integer.parseInt(sHeight);
                }

                thumbInfo.size = new Long(thumbnailFile.length());
                thumbInfo.mimetype = thumbMimeType;
                videoInfo.thumbnail_info = thumbInfo;
            }

            videoMessage.info = videoInfo;
        } catch (Exception e) {
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

    //================================================================================
    // Unsent events management
    //================================================================================

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
    public ArrayList<Event> getUnsentEvents() {
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
     * Resend a list of events
     * @param evensList the events list
     */
    public void resendEvents(Collection<Event> evensList) {
        if (null != evensList) {
            // reset the timestamp
            for (Event event : evensList) {
                event.originServerTs = System.currentTimeMillis();
                event.mSentState = Event.SentState.SENDING;
            }

            resendEventsList(new ArrayList<Event>(evensList), 0, Long.MAX_VALUE);
        }
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
                                    public void onUploadStart(String uploadId) {
                                    }

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
                    unsentEvent.originServerTs = System.currentTimeMillis();

                    sendEvent(unsentEvent, new ApiCallback<Void>() {
                        private Event storeUnsentMessage() {
                            Event dummyEvent = new Event(message, mMyUserId, mRoomId);
                            // create a dummy identifier
                            dummyEvent.createDummyEventId();
                            mDataHandler.storeLiveRoomEvent(dummyEvent);

                            return dummyEvent;
                        }

                        private void common(final Event sentEvent, final Exception exception, final MatrixError matrixError) {
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    // replace the resent event
                                    mDataHandler.deleteRoomEvent(unsentEventCopy);
                                    mDataHandler.onDeleteEvent(unsentEventCopy);

                                    // with a new one
                                    unsentEvent.eventId = sentEvent.eventId;
                                    unsentEvent.mSentState = sentEvent.mSentState;
                                    unsentEvent.unsentException = exception;
                                    unsentEvent.unsentMatrixError = matrixError;
                                    // don't wait after the echo
                                    unsentEvent.mSentState = Event.SentState.SENT;

                                    mDataHandler.onLiveEvent(unsentEvent, getLiveState());

                                    // send the next one
                                    Room.this.resendEventsList(evensList, index + 1, maxTime);
                                }
                            });
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
            boolean mustCheckUnsent;

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

    //================================================================================
    // Call
    //================================================================================

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

    //================================================================================
    // Account data management
    //================================================================================

    /**
     * Handle private user data events.
     * @param accountDataEvents the account events.
     */
    public void handleAccountDataEvents(List<Event> accountDataEvents) {
        if ((null != accountDataEvents) && (accountDataEvents.size() > 0)) {
            // manage the account events
            for (Event accountDataEvent : accountDataEvents) {
                mAccountData.handleEvent(accountDataEvent);

                if (accountDataEvent.type.equals(Event.EVENT_TYPE_TAGS)) {
                    mDataHandler.onRoomTagEvent(accountDataEvent.roomId);
                }
            }

            mDataHandler.getStore().storeAccountData(mRoomId, mAccountData);
        }
    }

    /**
     * Add a tag to a room.
     * Use this method to update the order of an existing tag.
     *
     * @param tag the new tag to add to the room.
     * @param order the order.
     * @param callback the operation callback
     */
    public void addTag(String tag, Double order, final ApiCallback<Void> callback) {
        // sanity check
        if ((null != tag) && (null != order)) {
            mDataRetriever.getRoomsRestClientV2().addTag(mRoomId, tag, order, callback);
        } else {
            if (null != callback) {
                // warn that something was wrong
                callback.onUnexpectedError(null);
            }
        }
    }

    /**
     * Remove a tag to a room.
     *
     * @param tag the new tag to add to the room.
     * @param callback the operation callback.
     */
    public void removeTag(String tag, final ApiCallback<Void> callback) {
        // sanity check
        if (null != tag) {
            mDataRetriever.getRoomsRestClientV2().removeTag(mRoomId, tag, callback);
        } else {
            if (null != callback) {
                // warn that something was wrong
                callback.onUnexpectedError(null);
            }
        }
    }

    /**
     * Remove a tag and add another one.
     *
     * @param oldTag the tag to remove.
     * @param newTag the new tag to add. Nil can be used. Then, no new tag will be added.
     * @param newTagOrder the order of the new tag.
     * @param callback the operation callback.
     */
    public void replaceTag(final String oldTag, final String newTag, final Double newTagOrder, final ApiCallback<Void> callback) {

        // remove tag
        if ((null != oldTag) && (null == newTag)) {
            removeTag(oldTag, callback);
        }
        // define a tag or define a new order
        else if (((null == oldTag) && (null != newTag)) || TextUtils.equals(oldTag, newTag)) {
            addTag(newTag, newTagOrder, callback);
        }
        else {
            removeTag(oldTag, new ApiCallback<Void>() {
                @Override
                public void onSuccess(Void info) {
                    addTag(newTag, newTagOrder, callback);
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
    }

    //================================================================================
    // Sync V2
    //================================================================================

    public void handleJoinedRoomSync(RoomSync roomSync, Boolean isInitialSync) {
        // Is it an initial sync for this room ?
        RoomState liveState = getLiveState();
        String membership = null;
        RoomSummary currentSummary = null;

        mIsV2Syncing = true;

        RoomMember selfMember = liveState.getMember(mMyUserId);

        if (null != selfMember) {
            membership = selfMember.membership;
        }

        boolean isRoomInitialSync = (null == membership) || TextUtils.equals(membership, RoomMember.MEMBERSHIP_INVITE);

        // Check whether the room was pending on an invitation.
        if (TextUtils.equals(membership, RoomMember.MEMBERSHIP_INVITE)) {
            // Reset the storage of this room. An initial sync of the room will be done with the provided 'roomSync'.
            Log.d(LOG_TAG, "handleJoinedRoomSync: clean invited room from the store " + mRoomId);
            mDataHandler.getStore().deleteRoomData(mRoomId);

            // clear the states
            RoomState state = new RoomState();
            state.roomId = mRoomId;
            state.setDataHandler(mDataHandler);

            this.mBackState = this.mLiveState = state;
        }

        if ((null != roomSync.state) && (null != roomSync.state.events) && (roomSync.state.events.size() > 0)) {
            // Build/Update first the room state corresponding to the 'start' of the timeline.
            // Note: We consider it is not required to clone the existing room state here, because no notification is posted for these events.
            processLiveState(roomSync.state.events);
        }

        // Handle now timeline.events, the room state is updated during this step too (Note: timeline events are in chronological order)
        if (null != roomSync.timeline) {
            if (roomSync.timeline.limited) {
                if (!isRoomInitialSync) {
                    currentSummary =  mDataHandler.getStore().getSummary(mRoomId);

                    // Flush the existing messages for this room by keeping state events.
                    mDataHandler.getStore().deleteAllRoomMessages(mRoomId, true);

                    // define a summary if some messages are left
                    // the unsent messages are often displayed messages.
                    Event oldestEvent = mDataHandler.getStore().getOldestEvent(mRoomId);
                    if (oldestEvent != null) {
                        if (RoomSummary.isSupportedEvent(oldestEvent)) {
                            mDataHandler.getStore().storeSummary(oldestEvent.roomId, oldestEvent, getLiveState(), mMyUserId);
                        }
                    }
                }

                // In case of limited timeline, update token where to start back pagination
                mDataHandler.getStore().storeBackToken(mRoomId, roomSync.timeline.prevBatch);
                // reset the state back token
                // because it does not make anymore sense
                // by setting at null, the events cache will be cleared when a requesthistory will be called
                mBackState.setToken(null);
                // reset the back paginate lock
                canStillPaginate = true;
            }

            // any event ?
            if ((null != roomSync.timeline.events) && (roomSync.timeline.events.size() > 0)) {
                List<Event> events = roomSync.timeline.events;

                // Here the events are handled in forward direction (see [handleLiveEvent:]).
                // They will be added at the end of the stored events, so we keep the chronological order.
                for (Event event : events) {
                    // the roomId is not defined.
                    event.roomId = mRoomId;
                    try {
                        // Make room data digest the live event
                        mDataHandler.handleLiveEvent(event, !isInitialSync && !isRoomInitialSync);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "timeline event failed " + e.getLocalizedMessage());
                    }
                }
            }

            if (roomSync.timeline.limited) {
                // the unsent / undeliverable event mus be pushed to the history bottom
                Collection<Event> events = mDataHandler.getStore().getRoomMessages(mRoomId);

                if (null != events) {
                    ArrayList<Event> unsentEvents = new ArrayList<Event>();

                    for(Event event : events) {
                        if (event.mSentState != Event.SentState.SENT) {
                            unsentEvents.add(event);
                        }
                    }

                    if (unsentEvents.size() > 0) {
                        for (Event event : unsentEvents) {
                            event.mSentState = Event.SentState.UNDELIVERABLE;
                            event.originServerTs = System.currentTimeMillis();
                            mDataHandler.getStore().deleteEvent(event);
                            mDataHandler.getStore().storeLiveRoomEvent(event);
                        }

                        // update the store
                        mDataHandler.getStore().commit();
                    }
                }
            }
        }

        if (isRoomInitialSync) {
            // any request history can be triggered by now.
            mIsReady = true;
        }
        // Finalize initial sync
        else {

            if ((null != roomSync.timeline) && roomSync.timeline.limited) {
                // The room has been synced with a limited timeline
                mDataHandler.onRoomSyncWithLimitedTimeline(mRoomId);
            }
        }

        if ((null != roomSync.ephemeral) && (null != roomSync.ephemeral.events)) {
            // Handle here ephemeral events (if any)
            for (Event event : roomSync.ephemeral.events) {
                // the roomId is not defined.
                event.roomId = mRoomId;
                try {
                    // Make room data digest the live event
                    mDataHandler.handleLiveEvent(event, !isInitialSync && !isRoomInitialSync);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "ephemeral event failed " + e.getLocalizedMessage());
                }
            }
        }

        // Handle account data events (if any)
        if (null != roomSync.accountData) {
            handleAccountDataEvents(roomSync.accountData.events);
        }

        // wait the end of the events chunk processing to detect if the user leaves the room
        // The timeline events could contain a leave event followed by a join.
        // so, the user does not leave.
        // The handleLiveEvent used to warn the client that a room was left where as it should not
        selfMember = liveState.getMember(mMyUserId);

        if (null != selfMember) {
            membership = selfMember.membership;

            if (TextUtils.equals(membership, RoomMember.MEMBERSHIP_LEAVE) || TextUtils.equals(membership, RoomMember.MEMBERSHIP_BAN)) {
                // check if the room still exists.
                if (null != mDataHandler.getStore().getRoom(mRoomId)) {
                    mDataHandler.getStore().deleteRoom(mRoomId);
                    mDataHandler.onLeaveRoom(mRoomId);
                }
            }
        }

        // check if the summary is defined
        // after a sync, the room summary might not be defined because the latest message did not generate a room summary/
        if (null != mDataHandler.getStore().getRoom(mRoomId)) {
            RoomSummary summary = mDataHandler.getStore().getSummary(mRoomId);

            // if there is no defined summary
            // we have to create a new one
            if (null == summary) {
                // define a summary if some messages are left
                // the unsent messages are often displayed messages.
                Event oldestEvent = mDataHandler.getStore().getOldestEvent(mRoomId);

                // if there is an oldest event, use it to set a summary
                if (oldestEvent != null) {
                    if (RoomSummary.isSupportedEvent(oldestEvent)) {
                        mDataHandler.getStore().storeSummary(oldestEvent.roomId, oldestEvent, getLiveState(), mMyUserId);
                        mDataHandler.getStore().commit();
                    }
                }
                // use the latest known event
                else if (null != currentSummary) {
                    mDataHandler.getStore().storeSummary(mRoomId, currentSummary.getLatestEvent(), getLiveState(), mMyUserId);
                    mDataHandler.getStore().commit();
                }
                // try to build a summary from the state events
                else if ((null != roomSync.state) && (null != roomSync.state.events) && (roomSync.state.events.size() > 0)) {
                    ArrayList<Event> events = new ArrayList<Event>(roomSync.state.events);

                    Collections.reverse(events);

                    for(Event event : events) {
                        event.roomId = mRoomId;
                        if (RoomSummary.isSupportedEvent(event)) {
                            summary = mDataHandler.getStore().storeSummary(event.roomId, event, getLiveState(), mMyUserId);

                            // Watch for potential room name changes
                            if (Event.EVENT_TYPE_STATE_ROOM_NAME.equals(event.type)
                                    || Event.EVENT_TYPE_STATE_ROOM_ALIASES.equals(event.type)
                                    || Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(event.type)) {


                                if (null != summary) {
                                    summary.setName(getName(mMyUserId));
                                }
                            }

                            mDataHandler.getStore().commit();
                            break;
                        }
                    }
                }
            }
        }

        // the user joined the room
        // With V2 sync, the server sends the events to init the room.
        if (null != mOnInitialSyncCallback) {
            try {
                mOnInitialSyncCallback.onSuccess(null);
            } catch (Exception e) {
            }
            mOnInitialSyncCallback = null;
        }

        mIsV2Syncing = false;
    }

    public void handleInvitedRoomSync(InvitedRoomSync invitedRoomSync) {
        // Handle the state events as live events (the room state will be updated, and the listeners (if any) will be notified).

        if ((null != invitedRoomSync) && (null != invitedRoomSync.inviteState) && (null != invitedRoomSync.inviteState.events)) {

           for(Event event : invitedRoomSync.inviteState.events) {
                // Add a fake event id if none in order to be able to store the event
                if (null == event.eventId) {
                    event.eventId = mRoomId + "-" + System.currentTimeMillis() + "-" + event.hashCode();
                }

                // the roomId is not defined.
                event.roomId = mRoomId;
                mDataHandler.handleLiveEvent(event);
            }
        }
    }
}
