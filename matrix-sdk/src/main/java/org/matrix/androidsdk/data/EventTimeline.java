/*
 * Copyright 2016 OpenMarket Ltd
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

import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import org.matrix.androidsdk.MXDataHandler;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.EventContext;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.Sync.RoomSync;
import org.matrix.androidsdk.rest.model.Sync.InvitedRoomSync;
import org.matrix.androidsdk.rest.model.TokensChunkResponse;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 *  A `EventTimeline` instance represents a contiguous sequence of events in a room.
 *
 * There are two kinds of timeline:
 *
 * - live timelines: they receive live events from the events stream. You can paginate
 * backwards but not forwards.
 * All (live or backwards) events they receive are stored in the store of the current
 * MXSession.
 *
 * - past timelines: they start in the past from an `initialEventId`. They are filled
 * with events on calls of [MXEventTimeline paginate] in backwards or forwards direction.
 * Events are stored in a in-memory store (MXMemoryStore).
 */
public class EventTimeline {

    private static final String LOG_TAG = "EventTimeline";

    /**
     * The initial event id used to initialise the timeline.
     * null in case of live timeline.
     */
    private String mInitialEventId;

    /**
     * Indicate if this timeline is a live one.
     */
    private boolean mIsLiveTimeline;

    /**
     * The state of the room at the top most recent event of the timeline.
     */
    private RoomState mState = new RoomState();

    /**
     * The historical state of the room when paginating back.
     */
    private RoomState mBackState = new RoomState();

    /**
     * The state that was in the `state` property before it changed.
     * It is cached because it costs time to recompute it from the current state.
     * This is particularly noticeable for rooms with a lot of members (ie a lot of
     * room members state events).
     */
    public RoomState mPreviousState;

    /**
     * The associated room.
     */
    private Room mRoom;


    private String mRoomId;

    /**
     * The store.
     */
    public IMXStore mStore;

    /**
     * MXStore does only back pagination. So, the forward pagination token for
     * past timelines is managed locally.
     */
    private String mForwardsPaginationToken;
    private boolean mHasReachedHomeServerForwardsPaginationEnd;


    public MXDataHandler mDataHandler;

    /**
     * Reset the back state so that future history requests start over from live.
     * Must be called when opening a room if interested in history.
     */
    public void initHistory() {
        mBackState = mState.deepCopy();
        mCanStillPaginate = true;
        mIsPaginating = false;

        mDataHandler.getDataRetriever().cancelHistoryRequest(mRoomId);
    }

    public EventTimeline(Room room) {
        mRoom = room;
    }

    public void setRoomId(String roomId) {
        mRoomId = roomId;
        mState.roomId = roomId;
        mBackState.roomId = roomId;
    }

    /**
     * Create a timeline instance for a room.
     * @param session the session.
     * @param aRoom the room associated to the timeline
     * @param initialEventId the initial event for the timeline. A null value will create a live timeline.
     */
    public EventTimeline(MXDataHandler dataHandler, Room aRoom, String initialEventId) {

        mDataHandler = dataHandler;
        mRoom = aRoom;
        mRoomId = aRoom.getRoomId();
        mInitialEventId = initialEventId;

        mState = new RoomState();

        // Is it a past or live timeline?
        if (!TextUtils.isEmpty(initialEventId)) {
            // Events for a past timeline are stored in memory
            mStore = new MXMemoryStore(dataHandler.getCredentials());
        } else {
            mIsLiveTimeline = true;
            mStore = dataHandler.getStore();
        }
    }


    public void setDataHandler(MXDataHandler dataHandler) {
        mStore = dataHandler.getStore();
        mDataHandler = dataHandler;
        mState.setDataHandler(dataHandler);
        mBackState.setDataHandler(dataHandler);
    }

    /**
     * @return The state of the room at the top most recent event of the timeline.
     */
    public RoomState getState() {
        return mState;
    }

    public void setState(RoomState state) {
        mState = state;
    }

    public RoomState getBackState() {
        return mBackState;
    }

    public void deepCopyState() {
        mState = mState.deepCopy();
    }

    /**
     * Process a state event to keep the internal live and back states up to date.
     * @param event the state event
     * @param direction the direction; ie. forwards for live state, backwards for back state
     * @return true if the event has been processed.
     */
    public boolean processStateEvent(Event event, Room.EventDirection direction) {
        RoomState affectedState = (direction ==  Room.EventDirection.FORWARDS) ? mState : mBackState;
        Boolean isProcessed = affectedState.applyState(event, direction);

        if ((isProcessed) && (direction == Room.EventDirection.FORWARDS)) {
            mStore.storeLiveStateForRoom(mRoomId);
        }

        return isProcessed;
    }

    /**
     *
     * @param roomSync
     * @param isInitialSync
     * @return true if it is an initial sync
     */
    public boolean  handleJoinedRoomSync(RoomSync roomSync, boolean isInitialSync) {
        String membership = null;
        String myUserId = mDataHandler.getMyUser().user_id;
        RoomSummary currentSummary = null;

        RoomMember selfMember = mState.getMember(mDataHandler.getMyUser().user_id);

        if (null != selfMember) {
            membership = selfMember.membership;
        }

        boolean isRoomInitialSync = (null == membership) || TextUtils.equals(membership, RoomMember.MEMBERSHIP_INVITE);

        // Check whether the room was pending on an invitation.
        if (TextUtils.equals(membership, RoomMember.MEMBERSHIP_INVITE)) {
            // Reset the storage of this room. An initial sync of the room will be done with the provided 'roomSync'.
            Log.d(LOG_TAG, "handleJoinedRoomSync: clean invited room from the store " + mRoomId);
            mStore.deleteRoomData(mRoomId);

            // clear the states
            RoomState state = new RoomState();
            state.roomId = mRoomId;
            state.setDataHandler(mDataHandler);

            this.mBackState = this.mState = state;
        }

        if ((null != roomSync.state) && (null != roomSync.state.events) && (roomSync.state.events.size() > 0)) {
            // Build/Update first the room state corresponding to the 'start' of the timeline.
            // Note: We consider it is not required to clone the existing room state here, because no notification is posted for these events.

            // Build/Update first the room state corresponding to the 'start' of the timeline.
            // Note: We consider it is not required to clone the existing room state here, because no notification is posted for these events.
            if (mDataHandler.isActive()) {
                for (Event event : roomSync.state.events) {
                    try {
                        processStateEvent(event,  Room.EventDirection.FORWARDS);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "processStateEvent failed " + e.getLocalizedMessage());
                    }
                }

                mRoom.setReadyState(true);
            }

            // if it is an initial sync, the live state is initialized here
            // so the back state must also be initialized
            if (isRoomInitialSync) {
                this.mBackState = this.mState.deepCopy();
            }
        }

        // Handle now timeline.events, the room state is updated during this step too (Note: timeline events are in chronological order)
        if (null != roomSync.timeline) {
            if (roomSync.timeline.limited) {
                if (!isRoomInitialSync) {
                    currentSummary = mStore.getSummary(mRoomId);

                    // define a summary if some messages are left
                    // the unsent messages are often displayed messages.
                    Event oldestEvent = mStore.getOldestEvent(mRoomId);

                    // Flush the existing messages for this room by keeping state events.
                    mStore.deleteAllRoomMessages(mRoomId, true);

                    if (oldestEvent != null) {
                        if (RoomSummary.isSupportedEvent(oldestEvent)) {
                            mStore.storeSummary(oldestEvent.roomId, oldestEvent, mState, myUserId);
                        }
                    }
                }

                // In case of limited timeline, update token where to start back pagination
                mStore.storeBackToken(mRoomId, roomSync.timeline.prevBatch);
                // reset the state back token
                // because it does not make anymore sense
                // by setting at null, the events cache will be cleared when a requesthistory will be called
                mBackState.setToken(null);
                // reset the back paginate lock
                mCanStillPaginate = true;
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
                Collection<Event> events = mStore.getRoomMessages(mRoomId);

                if (null != events) {
                    ArrayList<Event> unsentEvents = new ArrayList<Event>();

                    for (Event event : events) {
                        if (event.mSentState != Event.SentState.SENT) {
                            unsentEvents.add(event);
                        }
                    }

                    if (unsentEvents.size() > 0) {
                        for (Event event : unsentEvents) {
                            event.mSentState = Event.SentState.UNDELIVERABLE;
                            event.originServerTs = System.currentTimeMillis();
                            mStore.deleteEvent(event);
                            mStore.storeLiveRoomEvent(event);
                        }

                        // update the store
                        mStore.commit();
                    }
                }
            }
        }

        if (isRoomInitialSync) {
            // any request history can be triggered by now.
            mRoom.setReadyState(true);
        }
        // Finalize initial sync
        else {

            if ((null != roomSync.timeline) && roomSync.timeline.limited) {
                // The room has been synced with a limited timeline
                mDataHandler.onRoomSyncWithLimitedTimeline(mRoomId);
            }
        }
        // wait the end of the events chunk processing to detect if the user leaves the room
        // The timeline events could contain a leave event followed by a join.
        // so, the user does not leave.
        // The handleLiveEvent used to warn the client that a room was left where as it should not
        selfMember = mState.getMember(myUserId);

        if (null != selfMember) {
            membership = selfMember.membership;

            if (TextUtils.equals(membership, RoomMember.MEMBERSHIP_LEAVE) || TextUtils.equals(membership, RoomMember.MEMBERSHIP_BAN)) {
                // check if the room still exists.
                if (null != mStore.getRoom(mRoomId)) {
                    mStore.deleteRoom(mRoomId);
                    mDataHandler.onLeaveRoom(mRoomId);
                }
            }
        }

        // check if the summary is defined
        // after a sync, the room summary might not be defined because the latest message did not generate a room summary/
        if (null != mStore.getRoom(mRoomId)) {
            RoomSummary summary = mStore.getSummary(mRoomId);

            // if there is no defined summary
            // we have to create a new one
            if (null == summary) {
                // define a summary if some messages are left
                // the unsent messages are often displayed messages.
                Event oldestEvent = mStore.getOldestEvent(mRoomId);

                // if there is an oldest event, use it to set a summary
                if (oldestEvent != null) {
                    if (RoomSummary.isSupportedEvent(oldestEvent)) {
                        mStore.storeSummary(oldestEvent.roomId, oldestEvent, mState, myUserId);
                        mStore.commit();
                    }
                }
                // use the latest known event
                else if (null != currentSummary) {
                    mStore.storeSummary(mRoomId, currentSummary.getLatestEvent(), mState, myUserId);
                    mStore.commit();
                }
                // try to build a summary from the state events
                else if ((null != roomSync.state) && (null != roomSync.state.events) && (roomSync.state.events.size() > 0)) {
                    ArrayList<Event> events = new ArrayList<Event>(roomSync.state.events);

                    Collections.reverse(events);

                    for (Event event : events) {
                        event.roomId = mRoomId;
                        if (RoomSummary.isSupportedEvent(event)) {
                            summary = mStore.storeSummary(event.roomId, event, mState, myUserId);

                            // Watch for potential room name changes
                            if (Event.EVENT_TYPE_STATE_ROOM_NAME.equals(event.type)
                                    || Event.EVENT_TYPE_STATE_ROOM_ALIASES.equals(event.type)
                                    || Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(event.type)) {


                                if (null != summary) {
                                    summary.setName(mRoom.getName(myUserId));
                                }
                            }

                            mStore.commit();
                            break;
                        }
                    }
                }
            }
        }

        if (null != roomSync.unreadNotifications) {
            int notifCount = 0;
            int highlightCount = 0;

            if (null != roomSync.unreadNotifications.highlightCount) {
                highlightCount = roomSync.unreadNotifications.highlightCount;
            }

            if (null != roomSync.unreadNotifications.notificationCount) {
                notifCount = roomSync.unreadNotifications.notificationCount;
            }

            boolean isUpdated = (notifCount != mState.mNotificationCount) || (mState.mHighlightCount != highlightCount);

            if (isUpdated) {
                mState.mNotificationCount = notifCount;
                mState.mHighlightCount = highlightCount;
                mStore.storeLiveStateForRoom(mRoomId);
            }
        }

        return isRoomInitialSync;
    }

    /**
     * Initialise the room evenTimeline state.
     * @param stateEvents the state event.
     */
    public void initialiseState(List<Event> stateEvents) {

    }


    //================================================================================
    // History request
    //================================================================================

    private static final int MAX_EVENT_COUNT_PER_PAGINATION = 20;

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

    /**
     * Send MAX_EVENT_COUNT_PER_PAGINATION events to the caller.
     * @param callback the callback.
     */
    private void manageEvents(final ApiCallback<Integer> callback) {
        // check if the SDK was not logged out
        if (!mDataHandler.isActive()) {
            Log.d(LOG_TAG, "manageEvents : mDataHandler is not anymore active.");

            return;
        }

        int count = Math.min(mSnapshotedEvents.size(), MAX_EVENT_COUNT_PER_PAGINATION);

        for(int i = 0; i < count; i++) {
            SnapshotedEvent snapshotedEvent = mSnapshotedEvents.get(0);
            mSnapshotedEvents.remove(0);
            mDataHandler.onBackEvent(snapshotedEvent.mEvent, snapshotedEvent.mState);
        }

        if ((mSnapshotedEvents.size() < MAX_EVENT_COUNT_PER_PAGINATION) && mIsLastChunk) {
            mIsPaginating = false;
        }

        if (callback != null) {
            try {
                callback.onSuccess(count);
            } catch (Exception e) {
                Log.e(LOG_TAG, "requestHistory exception " + e.getMessage());
            }
        }

        mIsPaginating = false;
        Log.d(LOG_TAG, "manageEvents : commit");
        mStore.commit();
    }

    public boolean mIsPaginating = false;
    public boolean mCanStillPaginate = true;
    public boolean mIsLastChunk;


    // the server provides a token even for the first room message (which should never change it is the creator message)
    // so requestHistory always triggers a remote request which returns an empty json.
    //  try to avoid such behaviour
    private String mTopToken;

    /**
     * Request older messages. They will come down the onBackEvent callback.
     * @param callback callback to implement to be informed that the pagination request has been completed. Can be null.
     * @return true if request starts
     */
    public boolean backPaginate(final ApiCallback<Integer> callback) {
        final String myUserId = mDataHandler.getUserId();

        if (mIsPaginating // One at a time please
                || !mState.canBackPaginated(myUserId) // history_visibility flag management
                || !mCanStillPaginate // If we have already reached the end of history
                || !mRoom.isReady()) { // If the room is not finished being set up

            Log.d(LOG_TAG, "cannot requestHistory " + mIsPaginating + " " + !getState().canBackPaginated(myUserId) + " " + !mCanStillPaginate + " " + !mRoom.isReady());

            return false;
        }
        mIsPaginating = true;

        // restart the pagination
        if (null == getBackState().getToken()) {
            mSnapshotedEvents.clear();
        }

        Log.d(LOG_TAG, "requestHistory starts");

        // enough buffered data
        if (mSnapshotedEvents.size() >= MAX_EVENT_COUNT_PER_PAGINATION) {
            final android.os.Handler handler = new android.os.Handler(Looper.getMainLooper());

            Log.d(LOG_TAG, "requestHistory : the events are already loaded.");

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

        final String fromToken = getBackState().getToken();

        mDataHandler.getDataRetriever().paginate(mRoomId, getBackState().getToken(), Room.EventDirection.BACKWARDS, new SimpleApiCallback<TokensChunkResponse<Event>>(callback) {
            @Override
            public void onSuccess(TokensChunkResponse<Event> response) {
                if (mDataHandler.isActive()) {

                    Log.d(LOG_TAG, "requestHistory : " + response.chunk.size() + " are retrieved.");

                    if (response.chunk.size() > 0) {
                        getBackState().setToken(response.end);

                        RoomSummary summary = mStore.getSummary(mRoomId);
                        Boolean shouldCommitStore = false;

                        // the room state is copied to have a state snapshot
                        // but copy it only if there is a state update
                        RoomState stateCopy = getBackState().deepCopy();

                        for (Event event : response.chunk) {
                            boolean processedEvent = true;

                            if (event.stateKey != null) {
                                processedEvent = processStateEvent(event, Room.EventDirection.BACKWARDS);

                                if (processedEvent) {
                                    // new state event -> copy the room state
                                    stateCopy = getBackState().deepCopy();
                                }
                            }

                            // warn the listener only if the message is processed.
                            // it should avoid duplicated events.
                            if (processedEvent) {
                                // update the summary is the event has been received after the oldest known event
                                // it might happen after a timeline update (hole in the chat history)
                                if ((null != summary) && (summary.getLatestEvent().originServerTs < event.originServerTs) && RoomSummary.isSupportedEvent(event)) {
                                    summary = mStore.storeSummary(mRoomId, event, getState(), myUserId);
                                    shouldCommitStore = true;
                                }

                                mSnapshotedEvents.add(new SnapshotedEvent(event, stateCopy));
                            }
                        }

                        if (shouldCommitStore) {
                            mStore.commit();
                        }
                    }

                    // assume it is the first room message
                    if (0 == response.chunk.size()) {
                        // save its token to avoid useless request
                        mTopToken = fromToken;
                    }

                    mIsLastChunk = (0 == response.chunk.size()) || TextUtils.isEmpty(response.end) || TextUtils.equals(response.end, mTopToken);

                    if (mIsLastChunk) {
                        Log.d(LOG_TAG, "is last chunck" + (0 == response.chunk.size()) + " " + TextUtils.isEmpty(response.end) + " " + TextUtils.equals(response.end, mTopToken));
                    }

                    manageEvents(callback);
                } else {
                    Log.d(LOG_TAG, "mDataHandler is not active.");
                }
            }

            @Override
            public void onMatrixError(MatrixError e) {
                Log.d(LOG_TAG, "requestRoomHistory onMatrixError");

                // When we've retrieved all the messages from a room, the pagination token is some invalid value
                if (MatrixError.UNKNOWN.equals(e.errcode)) {
                    mCanStillPaginate = false;
                }
                mIsPaginating = false;

                if (null != callback) {
                    callback.onMatrixError(e);
                } else {
                    super.onMatrixError(e);
                }
            }

            @Override
            public void onNetworkError(Exception e) {
                Log.d(LOG_TAG, "requestRoomHistory onNetworkError");

                mIsPaginating = false;

                if (null != callback) {
                    callback.onNetworkError(e);
                } else {
                    super.onNetworkError(e);
                }
            }

            @Override
            public void onUnexpectedError(Exception e) {
                Log.d(LOG_TAG, "requestRoomHistory onUnexpectedError");

                mIsPaginating = false;

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
     *
     * @param direction
     * @param callback
     * @return true if the operation succeeds
     */
    public boolean paginate(Room.EventDirection direction,  final ApiCallback<Integer> callback) {
        if (Room.EventDirection.BACKWARDS == direction) {

            return backPaginate(callback);
        }

        // TODO implement forward pagination
        return false;
    }

    //==============================================================================================================
    // pagination methods
    //==============================================================================================================

    /**
     * Check if this timelime can be extended.
     *
     * This returns true if we either have more events, or if we have a pagination
     * token which means we can paginate in that direction. It does not necessarily
     * mean that there are more events available in that direction at this time.
     * canPaginate in forward direction has no meaning for a live timeline.
     * @param direction MXTimelineDirectionBackwards to check if we can paginate backwards. MXTimelineDirectionForwards to check if we can go forwards.
     * @returntrue if we can paginate in the given direction.
     */
    public boolean canPaginate(Room.EventDirection direction) {

        return true;
    }

    /**
     * Reset the pagination so that future calls to paginate start from the most recent
     * event of the timeline.
     */
    public void resetPagination() {

    }

    /**
     * Reset the pagination timelime and start loading the context around its `initialEventId`.
     * The retrieved (backwards and forwards) events will be sent to registered listeners.
     * @param limit the maximum number of messages to get around the initial event.
     * @param callback the operation callbacl
     */

    public void resetPaginationAroundInitialEventWithLimit(int limit, ApiCallback<EventContext> callback) {

    }


    /**
     * Get more messages.
     * The retrieved events will be sent to registered listeners.
     * Note it is not possible to paginate forwards on a live timeline.
     *
     * @param numItems the number of items to get.
     * @param direction `MXTimelineDirectionForwards` or `MXTimelineDirectionBackwards`
     * @param onlyFromStore if YES, return available events from the store, do not make a pagination request to the homeserver.
     * @param callback the operation callback
     */
    public void paginate(int numItems, Room.EventDirection direction, boolean onlyFromStore, ApiCallback<Void> callback) {

    }


    /**
     * Get the number of messages we can still back paginate from the store.
     * It provides the count of events available without making a request to the home server.
     * @return the count of remaining messages in store.
     */
    public int remainingMessagesForBackPaginationInStore() {
        return 0;
    }


    //==============================================================================================================
    // Server sync
    //==============================================================================================================
    /**
     * For live timeline, update data according to the received /sync response.
     *
     * @param roomSync information to sync the room with the home server data
     */
    public void handleJoinedRoomSync(RoomSync roomSync) {

    }
    /**
     * For live timeline, update invited room state according to the received /sync response.
     *
     * @param invitedRoomSync information to update the room state.
     */
    public void handleInvitedRoomSync(InvitedRoomSync invitedRoomSync) {

    }
}
