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

import com.google.gson.JsonObject;

import org.matrix.androidsdk.MXDataHandler;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.EventContent;
import org.matrix.androidsdk.rest.model.EventContext;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.ReceiptData;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.Sync.RoomSync;
import org.matrix.androidsdk.rest.model.Sync.InvitedRoomSync;
import org.matrix.androidsdk.rest.model.TokensChunkResponse;
import org.matrix.androidsdk.rest.model.bingrules.BingRule;
import org.matrix.androidsdk.util.BingRulesManager;
import org.matrix.androidsdk.util.JsonUtils;

import java.util.ArrayList;
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
     * The direction from which an incoming event is considered.
     */
    public enum Direction {
        /**
         * Forwards when the event is added to the end of the timeline.
         * These events come from the /sync stream or from forwards pagination.
         */
        FORWARDS,

        /**
         * Backwards when the event is added to the start of the timeline.
         * These events come from a back pagination.
         */
        BACKWARDS
    }

    public interface EventTimelineListener {

        /**
         * Call when an event has been handled in the timeline.
         * @param event the event.
         * @param direction the direction.
         * @param roomState the room state
         */
        void onEvent(Event event, Direction direction, RoomState roomState);
    }

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
     * The associated room.
     */
    private Room mRoom;

    /**
     * the room Id
     */
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

    /**
     * The data hanler : used to retrieve data from the store or to trigger REST requests.
     */
    public MXDataHandler mDataHandler;

    /**
     * Pending request statuses
     */
    public boolean mIsBackPaginating = false;
    public boolean mIsForewardPaginating = false;

    /**
     * true if the back history has been retrieved.
     */
    public boolean mCanBackPaginate = true;

    /**
     * true if the last back chunck has been received
     */
    public boolean mIsLastBackChunk;

    /**
     * the server provides a token even for the first room message (which should never change it is the creator message).
     * so requestHistory always triggers a remote request which returns an empty json.
     * try to avoid such behaviour
     */
    private String mBackwardTopToken = "not yet found";

    /**
     * Constructor from room.
     * @param room the linked room.
     * @param isLive true if it is a live EventTimeline
     */
    public EventTimeline(Room room, boolean isLive) {
        mRoom = room;
        mIsLiveTimeline = isLive;
    }

    /**
     * Constructor from a room Id
     * @param dataHandler the data handler
     * @param roomId the room Id
     */
    public EventTimeline(MXDataHandler dataHandler, String roomId) {
        this(dataHandler, roomId, null);
    }

    /**
     * Constructor from room and event Id
     * @param dataHandler the data handler
     * @param roomId the room Id
     * @param eventId the event id.
     */
    public EventTimeline(MXDataHandler dataHandler, String roomId, String eventId) {
        mInitialEventId = eventId;
        mDataHandler = dataHandler;

        mStore = new MXMemoryStore(dataHandler.getCredentials());
        mRoom = mDataHandler.getRoom(mStore, roomId, true);
        mRoom.setLiveTimeline(this);
        mRoom.setReadyState(true);
        setRoomId(roomId);
    }

    /**
     * @return the dedicated room
     */
    public Room getRoom() {
        return mRoom;
    }

    /**
     * @return the used store
     */
    public IMXStore getStore() {
        return mStore;
    }

    /**
     * @return the initial event id.
     */
    public String getInitialEventId() {
        return mInitialEventId;
    }

    /**
     * @return true if this timeline is the live one
     */
    public boolean isLiveTimeline() {
        return mIsLiveTimeline;
    }

    /**
     * Set the room Id
     * @param roomId the new room id.
     */
    public void setRoomId(String roomId) {
        mRoomId = roomId;
        mState.roomId = roomId;
        mBackState.roomId = roomId;
    }

    /**
     * Set the data handler.
     * @param dataHandler the data handler.
     */
    public void setDataHandler(MXDataHandler dataHandler) {
        mStore = dataHandler.getStore();
        mDataHandler = dataHandler;
        mState.setDataHandler(dataHandler);
        mBackState.setDataHandler(dataHandler);
    }

    /**
     * Reset the back state so that future history requests start over from live.
     * Must be called when opening a room if interested in history.
     */
    public void initHistory() {
        mBackState = mState.deepCopy();
        mCanBackPaginate = true;

        mIsBackPaginating = false;
        mIsForewardPaginating = false;

        if (null != mDataHandler.getDataRetriever()) {
            mDataHandler.getDataRetriever().cancelHistoryRequest(mRoomId);
        }
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

    /**
     * Make a
     * @param direction
     * @return
     */
    public RoomState deepCopyState(Direction direction) {
        if (direction == Direction.FORWARDS) {
            return (mState = mState.deepCopy());
        } else {
            return (mBackState = mBackState.deepCopy());
        }
    }

    /**
     * Process a state event to keep the internal live and back states up to date.
     * @param event the state event
     * @param direction the direction; ie. forwards for live state, backwards for back state
     * @return true if the event has been processed.
     */
    public boolean processStateEvent(Event event, Direction direction) {
        RoomState affectedState = (direction ==  Direction.FORWARDS) ? mState : mBackState;
        boolean isProcessed = affectedState.applyState(event, direction);

        if ((isProcessed) && (direction == Direction.FORWARDS)) {
            mStore.storeLiveStateForRoom(mRoomId);
        }

        return isProcessed;
    }

    /**
     * Handle the invitation room events
     * @param invitedRoomSync the invitation room events.
     */
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
                handleLiveEvent(event, true);
            }
        }
    }
    /**
     * Manage the joined room events.
     * @param roomSync the roomSync.
     * @param isInitialSync true if the sync has been triggered by a global initial sync
     * @return true if it is an initial sync
     */
    public void handleJoinedRoomSync(RoomSync roomSync, boolean isInitialSync) {
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
            if (mDataHandler.isAlive()) {
                for (Event event : roomSync.state.events) {
                    try {
                        processStateEvent(event, Direction.FORWARDS);
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

                // if the prev batch is set to null
                // it implies there is no more data on server side.
                if (null == roomSync.timeline.prevBatch) {
                    roomSync.timeline.prevBatch = Event.PAGINATE_BACK_TOKEN_END;
                }

                // In case of limited timeline, update token where to start back pagination
                mStore.storeBackToken(mRoomId, roomSync.timeline.prevBatch);
                // reset the state back token
                // because it does not make anymore sense
                // by setting at null, the events cache will be cleared when a requesthistory will be called
                mBackState.setToken(null);
                // reset the back paginate lock
                mCanBackPaginate = true;
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
                        // digest the forward event
                        handleLiveEvent(event, !isInitialSync && !isRoomInitialSync);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "timeline event failed " + e.getLocalizedMessage());
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

        // the EventTimeLine is used when displaying a room preview
        // so, the following items should only be called when it is a live one.
        if (mIsLiveTimeline) {
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

                boolean isUpdated = (notifCount != mState.getNotificationCount()) || (mState.getHighlightCount() != highlightCount);

                if (isUpdated) {
                    mState.setNotificationCount(notifCount);
                    mState.setHighlightCount(highlightCount);
                    mStore.storeLiveStateForRoom(mRoomId);
                }
            }
        }
    }

    /**
     * Store an outgoing event.
     * @param event the event to store
     */
    public void storeOutgoingEvent(Event event) {
        if (mIsLiveTimeline) {
            storeEvent(event);
        }
    }

    /**
     * Store the event and update the dedicated room summary
     * @param event the event to store
     */
    private void storeEvent(Event event) {
        String myUserId = mDataHandler.getCredentials().userId;

        // create dummy read receipt for any incoming event
        // to avoid not synchronized read receipt and event
        if ((null != event.getSender()) && (null != event.eventId)) {
            mRoom.handleReceiptData(new ReceiptData(event.getSender(), event.eventId, event.originServerTs));
        }

        mStore.storeLiveRoomEvent(event);

        if (RoomSummary.isSupportedEvent(event)) {
            RoomSummary summary = mStore.storeSummary(event.roomId, event, mState, myUserId);

            // Watch for potential room name changes
            if (Event.EVENT_TYPE_STATE_ROOM_NAME.equals(event.type)
                    || Event.EVENT_TYPE_STATE_ROOM_ALIASES.equals(event.type)
                    || Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(event.type)) {
                if (null != summary) {
                    summary.setName(mRoom.getName(myUserId));
                }
            }
        }
    }

    /**
     * Store a live room event.
     * @param event The event to be stored.
     */
    private void storeLiveRoomEvent(Event event) {
        boolean store = false;
        String myUserId = mDataHandler.getCredentials().userId;

        if (Event.EVENT_TYPE_REDACTION.equals(event.type)) {
            if (event.getRedacts() != null) {
                mStore.updateEventContent(event.roomId, event.getRedacts(), event.getContentAsJsonObject());

                // search the latest displayable event
                // to replace the summary text
                ArrayList<Event> events = new ArrayList<Event>(mStore.getRoomMessages(event.roomId));
                for(int index = events.size() - 1; index >= 0; index--) {
                    Event anEvent = events.get(index);

                    if (RoomSummary.isSupportedEvent(anEvent)) {
                        store = true;
                        event = anEvent;
                        break;
                    }
                }
            }
        }  else {
            // the candidate events are not stored.
            store = !event.isCallEvent() || !Event.EVENT_TYPE_CALL_CANDIDATES.equals(event.type);

            // thread issue
            // if the user leaves a room,
            if (Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(event.type) && myUserId.equals(event.stateKey)) {
                String membership = event.content.getAsJsonObject().getAsJsonPrimitive("membership").getAsString();

                if (RoomMember.MEMBERSHIP_LEAVE.equals(membership) || RoomMember.MEMBERSHIP_BAN.equals(membership)) {
                    store = false;
                    // delete the room and warn the listener of the leave event only at the end of the events chunk processing
                }
            }
        }

        if (store) {
            storeEvent(event);
        }

        // warn the listener that a new room has been created
        if (Event.EVENT_TYPE_STATE_ROOM_CREATE.equals(event.type)) {
            mDataHandler.onNewRoom(event.roomId);
        }

        // warn the listeners that a room has been joined
        if (Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(event.type) && myUserId.equals(event.stateKey)) {
            String membership = event.content.getAsJsonObject().getAsJsonPrimitive("membership").getAsString();

            if (RoomMember.MEMBERSHIP_JOIN.equals(membership)) {
                mDataHandler.onJoinRoom(event.roomId);
            } else if (RoomMember.MEMBERSHIP_INVITE.equals(membership)) {
                mDataHandler.onNewRoom(event.roomId);
            }
        }
    }

    /**
     * Trigger a push if there is a dedicated push rules which implies it.
     * @param event the event
     */
    private void triggerPush(Event event) {
        BingRule bingRule;
        boolean outOfTimeEvent = false;
        JsonObject eventContent = event.getContentAsJsonObject();
        if (eventContent.has("lifetime")) {
            long maxlifetime = eventContent.get("lifetime").getAsLong();
            long eventLifeTime = System.currentTimeMillis() - event.getOriginServerTs();

            outOfTimeEvent = eventLifeTime > maxlifetime;
        }

        BingRulesManager bingRulesManager = mDataHandler.getBingRulesManager();

        // If the bing rules apply, bing
        if (!outOfTimeEvent
                // some events are not bingable
                && !TextUtils.equals(event.type, Event.EVENT_TYPE_PRESENCE)
                && !TextUtils.equals(event.type, Event.EVENT_TYPE_TYPING)
                && !TextUtils.equals(event.type, Event.EVENT_TYPE_REDACTION)
                && !TextUtils.equals(event.type, Event.EVENT_TYPE_RECEIPT)
                && !TextUtils.equals(event.type, Event.EVENT_TYPE_TAGS)
                && (bingRulesManager != null)
                && (null != (bingRule = bingRulesManager.fulfilledBingRule(event)))
                && bingRule.shouldNotify()) {
            Log.d(LOG_TAG, "handleLiveEvent : onBingEvent");
            mDataHandler.onBingEvent(event, mState, bingRule);
        }
    }

    /**
     * Handle events coming down from the event stream.
     * @param event the live event
     * @param withPush set to true to trigger pushes when it is required
     * */
    private void handleLiveEvent(Event event, boolean withPush) {
        MyUser myUser = mDataHandler.getMyUser();

        // dispatch the call events to the calls manager
        if (event.isCallEvent()) {
            mDataHandler.getCallsManager().handleCallEvent(event);

            if (withPush) {
                triggerPush(event);
            }
        } else {
            Event storedEvent = mStore.getEvent(event.eventId, event.roomId);

            // avoid processing event twice
            if (null != storedEvent) {

                // an event has been echoed
                if (storedEvent.getAge() == Long.MAX_VALUE) {
                    mStore.deleteEvent(storedEvent);
                    mStore.storeLiveRoomEvent(event);
                    mStore.commit();

                    Log.e(LOG_TAG, "handleLiveEvent : the event " + event.eventId + " in " + event.roomId + " has been echoed");

                } else {
                    Log.e(LOG_TAG, "handleLiveEvent : the event " + event.eventId + " in " + event.roomId + " already exist.");
                }

                return;
            }

            // Room event
            if (event.roomId != null) {
                // check if the room has been joined
                // the initial sync + the first requestHistory call is done here
                // instead of being done in the application
                if (Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(event.type) && TextUtils.equals(event.getSender(), mDataHandler.getUserId())) {
                    EventContent eventContent = JsonUtils.toEventContent(event.getContentAsJsonObject());
                    EventContent prevEventContent = event.getPrevContent();

                    String prevMembership = null;

                    if (null != prevEventContent) {
                        prevMembership = prevEventContent.membership;
                    }

                    boolean isRedactedEvent = (event.unsigned != null) &&  (event.unsigned.redacted_because != null);

                    // if the membership is the same, assume that the user
                    if (!isRedactedEvent && TextUtils.equals(prevMembership, eventContent.membership)) {
                        // check if the user updates his profile from another device.

                        boolean hasAccountInfoUpdated = false;

                        if (!TextUtils.equals(eventContent.displayname, myUser.displayname)) {
                            hasAccountInfoUpdated = true;
                            myUser.displayname = eventContent.displayname;
                            mStore.setDisplayName(myUser.displayname);
                        }

                        if (!TextUtils.equals(eventContent.avatar_url, myUser.getAvatarUrl())) {
                            hasAccountInfoUpdated = true;
                            myUser.setAvatarUrl(eventContent.avatar_url);
                            mStore.setAvatarURL(myUser.avatar_url);
                        }

                        if (hasAccountInfoUpdated) {
                            mDataHandler.onAccountInfoUpdate(myUser);
                        }
                    }
                }

                if (event.stateKey != null) {
                    // copy the live state before applying any update
                    deepCopyState(Direction.FORWARDS);

                    // check if the event has been processed
                    if (!processStateEvent(event, Direction.FORWARDS)) {
                        // not processed -> do not warn the application
                        // assume that the event is a duplicated one.
                        return;
                    }
                }

                storeLiveRoomEvent(event);

                // warn the listeners
                // general listeners
                mDataHandler.onLiveEvent(event, mState);

                // timeline listeners
                onEvent(event, Direction.FORWARDS, mState);

                // trigger pushes when it is required
                if (withPush) {
                    triggerPush(event);
                }
            } else {
                Log.e(LOG_TAG, "Unknown live event type: " + event.type);
            }
        }
    }


    //================================================================================
    // History request
    //================================================================================

    private static final int MAX_EVENT_COUNT_PER_PAGINATION = 30;

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
    private void manageBackEvents(final ApiCallback<Integer> callback) {
        // check if the SDK was not logged out
        if (!mDataHandler.isAlive()) {
            Log.d(LOG_TAG, "manageEvents : mDataHandler is not anymore active.");

            return;
        }

        int count = Math.min(mSnapshotedEvents.size(), MAX_EVENT_COUNT_PER_PAGINATION);

        for(int i = 0; i < count; i++) {
            SnapshotedEvent snapshotedEvent = mSnapshotedEvents.get(0);
            mSnapshotedEvents.remove(0);
            onEvent(snapshotedEvent.mEvent, Direction.BACKWARDS, snapshotedEvent.mState);
        }

        Log.d(LOG_TAG, "manageEvents : commit");
        mStore.commit();

        if ((mSnapshotedEvents.size() < MAX_EVENT_COUNT_PER_PAGINATION) && mIsLastBackChunk) {
            mCanBackPaginate = false;
        }

        if (callback != null) {
            try {
                callback.onSuccess(count);
            } catch (Exception e) {
                Log.e(LOG_TAG, "requestHistory exception " + e.getMessage());
            }
        }

        mIsBackPaginating = false;
    }

    /**
     * Add some events in a dedicated direction.
     * @param events the events list
     * @param direction the direction
     * @param callback the callback.
     */
    private void addPaginationEvents(List<Event> events, Direction direction, final ApiCallback<Integer> callback) {
        final String myUserId = mDataHandler.getUserId();
        RoomSummary summary = mStore.getSummary(mRoomId);
        boolean shouldCommitStore = false;

        // the backward events have a dedicated management to avoid providing too many events for each request
        for (Event event : events) {
            boolean processedEvent = true;

            if (event.stateKey != null) {
                deepCopyState(direction);
                processedEvent = processStateEvent(event, direction);
            }

            if (processedEvent) {
                // warn the listener only if the message is processed.
                // it should avoid duplicated events.
                if (direction == Direction.BACKWARDS) {
                    if (mIsLiveTimeline) {
                        // update the summary is the event has been received after the oldest known event
                        // it might happen after a timeline update (hole in the chat history)
                        if ((null != summary) && (summary.getLatestEvent().originServerTs < event.originServerTs) && RoomSummary.isSupportedEvent(event)) {
                            summary = mStore.storeSummary(mRoomId, event, getState(), myUserId);
                            shouldCommitStore = true;
                        }
                    }
                    mSnapshotedEvents.add(new SnapshotedEvent(event, getBackState()));
                    // onEvent will be called in manageBackEvents
                } else {
                    onEvent(event, Direction.FORWARDS, getState());
                }
            }
        }

        if (shouldCommitStore) {
            mStore.commit();
        }

        if (direction == Direction.BACKWARDS) {
            manageBackEvents(callback);
        } else {
            if (null != callback) {
                callback.onSuccess(events.size());
            }
        }
    }

    /**
     * Request older messages. They will come down the onBackEvent callback.
     * @param callback callback to implement to be informed that the pagination request has been completed. Can be null.
     * @return true if request starts
     */
    public boolean backPaginate(final ApiCallback<Integer> callback) {
        final String myUserId = mDataHandler.getUserId();

        if (mIsBackPaginating // One at a time please
                || !mState.canBackPaginated(myUserId) // history_visibility flag management
                || !mCanBackPaginate // If we have already reached the end of history
                || !mRoom.isReady()) { // If the room is not finished being set up

            Log.d(LOG_TAG, "cannot requestHistory " + mIsBackPaginating + " " + !getState().canBackPaginated(myUserId) + " " + !mCanBackPaginate + " " + !mRoom.isReady());

            return false;
        }

        Log.d(LOG_TAG, "backPaginate starts");

        // restart the pagination
        if (null == getBackState().getToken()) {
            mSnapshotedEvents.clear();
        }

        final String fromBackToken = getBackState().getToken();

        mIsBackPaginating = true;

        // enough buffered data
        if ((mSnapshotedEvents.size() >= MAX_EVENT_COUNT_PER_PAGINATION) || TextUtils.equals(fromBackToken, mBackwardTopToken) || TextUtils.equals(fromBackToken, Event.PAGINATE_BACK_TOKEN_END)) {

            mIsLastBackChunk = TextUtils.equals(fromBackToken, mBackwardTopToken) || TextUtils.equals(fromBackToken, Event.PAGINATE_BACK_TOKEN_END);

            final android.os.Handler handler = new android.os.Handler(Looper.getMainLooper());

            if ((mSnapshotedEvents.size() >= MAX_EVENT_COUNT_PER_PAGINATION)) {
                Log.d(LOG_TAG, "backPaginate : the events are already loaded.");
            } else {
                Log.d(LOG_TAG, "backPaginate : reach the history top");
            }

            // call the callback with a delay
            // to reproduce the same behaviour as a network request.
            Runnable r = new Runnable() {
                @Override
                public void run() {
                    handler.postDelayed(new Runnable() {
                        public void run() {
                            manageBackEvents(callback);
                        }
                    }, 0);
                }
            };

            Thread t = new Thread(r);
            t.start();

            return true;
        }

        mDataHandler.getDataRetriever().paginate(mStore, mRoomId, getBackState().getToken(), Direction.BACKWARDS, new SimpleApiCallback<TokensChunkResponse<Event>>(callback) {
            @Override
            public void onSuccess(TokensChunkResponse<Event> response) {
                if (mDataHandler.isAlive()) {

                    Log.d(LOG_TAG, "backPaginate : " + response.chunk.size() + " events are retrieved.");

                    mIsLastBackChunk = ((0 == response.chunk.size()) && TextUtils.equals(response.end, response.start)) || (null == response.end);

                    if (mIsLastBackChunk && (null != response.end)) {
                        // save its token to avoid useless request
                        mBackwardTopToken = fromBackToken;
                    } else {
                        // the server returns a null pagination token when there is no more available data
                        if (null == response.end) {
                            getBackState().setToken(Event.PAGINATE_BACK_TOKEN_END);
                        } else {
                            getBackState().setToken(response.end);
                        }
                    }

                    addPaginationEvents(response.chunk, Direction.BACKWARDS, callback);

                } else {
                    Log.d(LOG_TAG, "mDataHandler is not active.");
                }
            }

            @Override
            public void onMatrixError(MatrixError e) {
                Log.d(LOG_TAG, "backPaginate onMatrixError");

                // When we've retrieved all the messages from a room, the pagination token is some invalid value
                if (MatrixError.UNKNOWN.equals(e.errcode)) {
                    mCanBackPaginate = false;
                }
                mIsBackPaginating = false;

                if (null != callback) {
                    callback.onMatrixError(e);
                } else {
                    super.onMatrixError(e);
                }
            }

            @Override
            public void onNetworkError(Exception e) {
                Log.d(LOG_TAG, "backPaginate onNetworkError");

                mIsBackPaginating = false;

                if (null != callback) {
                    callback.onNetworkError(e);
                } else {
                    super.onNetworkError(e);
                }
            }

            @Override
            public void onUnexpectedError(Exception e) {
                Log.d(LOG_TAG, "backPaginate onUnexpectedError");

                mIsBackPaginating = false;

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
     * Request older messages. They will come down the onBackEvent callback.
     * @param callback callback to implement to be informed that the pagination request has been completed. Can be null.
     * @return true if request starts
     */
    public boolean forwardPaginate(final ApiCallback<Integer> callback) {
        if (mIsLiveTimeline) {
            Log.d(LOG_TAG, "Cannot forward paginate on Live timeline");
            return false;
        }

        if (mIsForewardPaginating || mHasReachedHomeServerForwardsPaginationEnd)  {
            Log.d(LOG_TAG, "forwardPaginate " + mIsForewardPaginating + " mHasReachedHomeServerForwardsPaginationEnd " + mHasReachedHomeServerForwardsPaginationEnd);
            return false;
        }

        mIsForewardPaginating = true;

        mDataHandler.getDataRetriever().paginate(mStore, mRoomId, mForwardsPaginationToken, Direction.FORWARDS, new SimpleApiCallback<TokensChunkResponse<Event>>(callback) {
            @Override
            public void onSuccess(TokensChunkResponse<Event> response) {
                if (mDataHandler.isAlive()) {
                    Log.d(LOG_TAG, "forwardPaginate : " + response.chunk.size() + " are retrieved.");

                    mHasReachedHomeServerForwardsPaginationEnd = (0 == response.chunk.size()) && TextUtils.equals(response.end, response.start);
                    mForwardsPaginationToken = response.end;

                    addPaginationEvents(response.chunk, Direction.FORWARDS, callback);

                    mIsForewardPaginating = false;
                } else {
                    Log.d(LOG_TAG, "mDataHandler is not active.");
                }
            }

            @Override
            public void onMatrixError(MatrixError e) {
                mIsForewardPaginating = false;
                if (null != callback) {
                    callback.onMatrixError(e);
                } else {
                    super.onMatrixError(e);
                }
            }

            @Override
            public void onNetworkError(Exception e) {
                mIsForewardPaginating = false;
                if (null != callback) {
                    callback.onNetworkError(e);
                } else {
                    super.onNetworkError(e);
                }
            }

            @Override
            public void onUnexpectedError(Exception e) {
                mIsForewardPaginating = false;
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
    public boolean paginate(Direction direction, final ApiCallback<Integer> callback) {
        if (Direction.BACKWARDS == direction) {
            return backPaginate(callback);
        } else {
            return forwardPaginate(callback);
        }
    }

    /**
     * Cancel any pending pagination requests
     */
    public void cancelPaginationRequest() {
        mDataHandler.getDataRetriever().cancelHistoryRequest(mRoomId);
        mIsBackPaginating = false;
        mIsForewardPaginating = false;
    }

    //==============================================================================================================
    // pagination methods
    //==============================================================================================================

    /**
     * Reset the pagination timelime and start loading the context around its `initialEventId`.
     * The retrieved (backwards and forwards) events will be sent to registered listeners.
     * @param limit the maximum number of messages to get around the initial event.
     * @param callback the operation callbacl
     */
    public void resetPaginationAroundInitialEvent(int limit, final ApiCallback<Void> callback) {
        // Reset the store
        mStore.deleteRoomData(mRoomId);

        mForwardsPaginationToken = null;
        mHasReachedHomeServerForwardsPaginationEnd = false;

        mDataHandler.getDataRetriever().getRoomsRestClient().contextOfEvent(mRoomId, mInitialEventId, limit, new ApiCallback<EventContext>() {
            @Override
            public void onSuccess(EventContext eventContext) {
                // And fill the timelime with received data
                for(Event event : eventContext.state) {
                    processStateEvent(event, Direction.FORWARDS);
                }

                mState.setToken(eventContext.start);

                // init the room states
                initHistory();

                // selected event
                storeLiveRoomEvent(eventContext.event);
                onEvent(eventContext.event, Direction.BACKWARDS, mState);

                // add events before
                addPaginationEvents(eventContext.eventsBefore, Direction.BACKWARDS, new ApiCallback<Integer>() {
                    @Override
                    public void onSuccess(Integer info) {
                        Log.d(LOG_TAG, "addPaginationEvents succeeds");
                    }

                    @Override
                    public void onNetworkError(Exception e) {
                        Log.e(LOG_TAG, "addPaginationEvents failed " + e.getLocalizedMessage());
                    }

                    @Override
                    public void onMatrixError(MatrixError e) {
                        Log.e(LOG_TAG, "addPaginationEvents failed " + e.getLocalizedMessage());
                    }

                    @Override
                    public void onUnexpectedError(Exception e) {
                        Log.e(LOG_TAG, "addPaginationEvents failed " + e.getLocalizedMessage());
                    }
                });

                // add events after
                addPaginationEvents(eventContext.eventsAfter, Direction.FORWARDS, new ApiCallback<Integer>() {
                    @Override
                    public void onSuccess(Integer info) {
                        Log.d(LOG_TAG, "addPaginationEvents succeeds");
                    }

                    @Override
                    public void onNetworkError(Exception e) {
                        Log.e(LOG_TAG, "addPaginationEvents failed " + e.getLocalizedMessage());
                    }

                    @Override
                    public void onMatrixError(MatrixError e) {
                        Log.e(LOG_TAG, "addPaginationEvents failed " + e.getLocalizedMessage());
                    }

                    @Override
                    public void onUnexpectedError(Exception e) {
                        Log.e(LOG_TAG, "addPaginationEvents failed " + e.getLocalizedMessage());
                    }
                });

                mForwardsPaginationToken = eventContext.end;

                callback.onSuccess(null);
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

    //==============================================================================================================
    // onEvent listener management.
    //==============================================================================================================

    private ArrayList<EventTimelineListener> mEventTimelineListeners = new ArrayList<EventTimelineListener>();

    /**
     * Add an events listener.
     * @param listener the listener to add.
     */
    public void addEventTimelineListener(EventTimelineListener listener) {
        if (null != listener) {
            synchronized (this) {
                if (-1 == mEventTimelineListeners.indexOf(listener)) {
                    mEventTimelineListeners.add(listener);
                }
            }
        }
    }

    /**
     * Remove an events listener.
     * @param listener the listener to remove.
     */
    public void removeEventTimelineListener(EventTimelineListener listener) {
        if (null != listener) {
            synchronized (this) {
                mEventTimelineListeners.remove(listener);
            }
        }
    }

    /**
     * Dispatch the onEvent callback.
     * @param event the event.
     * @param direction the direction.
     * @param roomState the roomState.
     */
    private void onEvent(Event event, Direction direction, RoomState roomState) {
        ArrayList<EventTimelineListener> listeners;

        synchronized (this) {
            listeners = new ArrayList<EventTimelineListener>(mEventTimelineListeners);
        }

        for(EventTimelineListener listener : listeners) {
            try {
                listener.onEvent(event, direction, roomState);
            } catch (Exception e) {
                Log.e(LOG_TAG,"EventTimeline.onEvent " + listener + " crashes " + e.getLocalizedMessage());
            }
        }
    }


}
