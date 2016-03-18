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
import android.text.TextUtils;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import org.matrix.androidsdk.MXDataHandler;
import org.matrix.androidsdk.listeners.IMXEventListener;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.BannedUser;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.FileInfo;
import org.matrix.androidsdk.rest.model.FileMessage;
import org.matrix.androidsdk.rest.model.ImageInfo;
import org.matrix.androidsdk.rest.model.ImageMessage;
import org.matrix.androidsdk.rest.model.LocationMessage;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.PowerLevels;
import org.matrix.androidsdk.rest.model.ReceiptData;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.RoomResponse;
import org.matrix.androidsdk.rest.model.Sync.RoomSync;
import org.matrix.androidsdk.rest.model.Sync.InvitedRoomSync;
import org.matrix.androidsdk.rest.model.ThumbnailInfo;
import org.matrix.androidsdk.rest.model.TokensChunkResponse;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.rest.model.VideoInfo;
import org.matrix.androidsdk.rest.model.VideoMessage;
import org.matrix.androidsdk.util.ImageUtils;
import org.matrix.androidsdk.util.JsonUtils;

import java.io.File;

import java.lang.reflect.Type;
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

    //private RoomState mLiveState = new RoomState();
    //private RoomState mBackState = new RoomState();
    private RoomAccountData mAccountData = new RoomAccountData();

    private MXDataHandler mDataHandler;
    private IMXStore mStore;

    private String mMyUserId = null;

    // Map to keep track of the listeners the client adds vs. the ones we actually register to the global data handler.
    // This is needed to find the right one when removing the listener.
    private Map<IMXEventListener, IMXEventListener> mEventListeners = new HashMap<IMXEventListener, IMXEventListener>();

    public boolean mIsPaginating = false;
    public boolean mCanStillPaginate = true;
    public boolean mIsLastChunk;
    // the server provides a token even for the first room message (which should never change it is the creator message)
    // so requestHistory always triggers a remote request which returns an empty json.
    //  try to avoid such behaviour
    private String mTopToken;
    // This is used to block live events and history requests until the state is fully processed and ready
    private boolean mIsReady = false;

    private boolean mIsLeaving = false;

    private boolean mIsSyncing;

    private EventTimeline mLiveTimeline;

    private ApiCallback<Void> mOnInitialSyncCallback;

    private Gson gson = new GsonBuilder().create();

    // userIds list
    private ArrayList<String>mTypingUsers = new ArrayList<String>();

    public Room() {
        mLiveTimeline = new EventTimeline(this);
    }

    public String getRoomId() {
        return mLiveTimeline.getState().roomId;
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

    public RoomState getState() {
        return mLiveTimeline.getState();
    }

    // TODO remove it when complete
    public RoomState getLiveState() {
        return getState();
    }

    public RoomState getBackState() {
        return mLiveTimeline.getBackState();
    }

    /*public void setLiveState(RoomState liveState) {
        mLiveState = liveState;
    }*/

    public boolean isLeaving() {
        return mIsLeaving;
    }

    public Collection<RoomMember> getMembers() {
        return getState().getMembers();
    }

    public EventTimeline getLiveTimeLine() {
        return mLiveTimeline;
    }

    /**
     * @return the list of online members in a room.
     */
    public Collection<RoomMember> getOnlineMembers() {
        Collection<RoomMember> members = getState().getMembers();
        ArrayList<RoomMember> activeMembers = new ArrayList<RoomMember>();

        for(RoomMember member : members) {
            if (TextUtils.equals(member.membership, RoomMember.MEMBERSHIP_JOIN)) {
                User user =  mStore.getUser(member.getUserId());

                if ((null != user) && user.isActive()) {
                    activeMembers.add(member);
                }
            }
        }

        return activeMembers;
    }

    /**
     * @return the list of active members in a room ie joined or invited ones.
     */
    public Collection<RoomMember> getActiveMembers() {
        Collection<RoomMember> members = getState().getMembers();
        ArrayList<RoomMember> activeMembers = new ArrayList<RoomMember>();

        for(RoomMember member : members) {
            if (TextUtils.equals(member.membership, RoomMember.MEMBERSHIP_JOIN) ||TextUtils.equals(member.membership, RoomMember.MEMBERSHIP_INVITE)) {
                activeMembers.add(member);
            }
        }

        return activeMembers;
    }

    public void setMember(String userId, RoomMember member) {
        getState().setMember(userId, member);
    }

    public RoomMember getMember(String userId) {
        return getState().getMember(userId);
    }

    public String getTopic() {
        return this.getState().topic;
    }

    public String getName(String selfUserId) {
        return getState().getDisplayName(selfUserId);
    }

    public String getVisibility() {
        return getState().visibility;
    }

    public void setVisibility(String visibility) {
        getState().visibility = visibility;
    }

    /**
     * @return true if the user is invited to the room
     */
    public boolean isInvited() {
        // Is it an initial sync for this room ?
        RoomState state = getState();
        String membership = null;

        RoomMember selfMember = state.getMember(mMyUserId);

        if (null != selfMember) {
            membership = selfMember.membership;
        }

        return TextUtils.equals(membership, RoomMember.MEMBERSHIP_INVITE);
    }


    public void init(String roomId, MXDataHandler dataHandler) {
        mLiveTimeline.setRoomId(roomId);

        mDataHandler = dataHandler;

        if (null != mDataHandler) {
            mStore = mDataHandler.getStore();
            mMyUserId = mDataHandler.getUserId();
            mLiveTimeline.setDataHandler(dataHandler);
        }
    }



    /**
     * cancel any remote request
     */
    public void cancelRemoteHistoryRequest() {
        mDataHandler.getDataRetriever().cancelRemoteHistoryRequest(getRoomId());
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
                public void onSuccess(final Event serverResponseEvent) {
                    // remove the tmp event
                    mStore.deleteEvent(event);

                    // update the event with the server response
                    event.mSentState = Event.SentState.SENT;
                    event.eventId = serverResponseEvent.eventId;
                    event.originServerTs = System.currentTimeMillis();

                    // the message echo is not yet echoed
                    if (!mStore.doesEventExist(serverResponseEvent.eventId, getRoomId())) {
                        mStore.storeLiveRoomEvent(event);
                    }

                    // send the dedicated read receipt asap
                    sendReadReceipt();

                    mStore.commit();
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

                    if (TextUtils.equals(MatrixError.FORBIDDEN, e.errcode) || TextUtils.equals(MatrixError.UNKNOWN_TOKEN, e.errcode)) {
                        mDataHandler.onInvalidToken();
                    } else {
                        try {
                            callback.onMatrixError(e);
                        } catch (Exception anException) {
                            Log.e(LOG_TAG, "sendEvent exception " + anException.getMessage());
                        }
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
            mDataHandler.getDataRetriever().getRoomsRestClient().sendMessage(event.originServerTs + "", getRoomId(), JsonUtils.toMessage(event.content), localCB);
        } else {
            mDataHandler.getDataRetriever().getRoomsRestClient().sendEvent(getRoomId(), event.type, event.content.getAsJsonObject(), localCB);
        }
    }

    /**
     * Redact an event from the room.
     * @param eventId the event's id
     * @param callback the callback with the created event
     */
    public void redact(String eventId, ApiCallback<Event> callback) {
        mDataHandler.getDataRetriever().getRoomsRestClient().redact(getRoomId(), eventId, callback);
    }

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

    //================================================================================
    // History request
    //================================================================================

    /**
     * Request older messages. They will come down the onBackEvent callback.
     * @param callback callback to implement to be informed that the pagination request has been completed. Can be null.
     * @return true if request starts
     */
    public boolean requestHistory(final ApiCallback<Integer> callback) {
        if (mIsPaginating // One at a time please
                || !getState().canBackPaginated(mMyUserId) // history_visibility flag management
                || !mCanStillPaginate // If we have already reached the end of history
                || !mIsReady) { // If the room is not finished being set up

            Log.d(LOG_TAG, "cannot requestHistory " + mIsPaginating + " " + !getState().canBackPaginated(mMyUserId) + " " + !mCanStillPaginate + " " + !mIsReady);

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

        mDataHandler.getDataRetriever().requestRoomHistory(getRoomId(), getBackState().getToken(), new SimpleApiCallback<TokensChunkResponse<Event>>(callback) {
            @Override
            public void onSuccess(TokensChunkResponse<Event> response) {
                if (mDataHandler.isActive()) {

                    Log.d(LOG_TAG, "requestHistory : " + response.chunk.size() + " are retrieved.");

                    if (response.chunk.size() > 0) {
                        getBackState().setToken(response.end);

                        RoomSummary summary = mStore.getSummary(getRoomId());
                        Boolean shouldCommitStore = false;

                        // the room state is copied to have a state snapshot
                        // but copy it only if there is a state update
                        RoomState stateCopy = getBackState().deepCopy();

                        for (Event event : response.chunk) {
                            boolean processedEvent = true;

                            if (event.stateKey != null) {
                                processedEvent = mLiveTimeline.processStateEvent(event, EventDirection.BACKWARDS);

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
                                    summary = mStore.storeSummary(getRoomId(), event, getState(), mMyUserId);
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
     * Shorthand for {@link #requestHistory(org.matrix.androidsdk.rest.callback.ApiCallback)} with a null callback.
     * @return true if the request starts
     */
    public boolean requestHistory() {
        return requestHistory(null);
    }

    /**
     * Request events to the server. The local cache is not used.
     * The events will not be saved in the local storage.
     *
     * @param token the token to go back from.
     * @param paginationCount the number of events to retrieve.
     * @param callback the onComplete callback
     */
    public void requestServerRoomHistory(final String token, final int paginationCount, final ApiCallback<TokensChunkResponse<Event>> callback) {
        mDataHandler.getDataRetriever().requestServerRoomHistory(getRoomId(), token, paginationCount, new SimpleApiCallback<TokensChunkResponse<Event>>(callback) {
            @Override
            public void onSuccess(TokensChunkResponse<Event> info) {
                callback.onSuccess(info);
            }
        });
    }


    private void handleInitialSyncInvite(String inviterUserId) {
        if (!mDataHandler.isActive()) {
            Log.e(LOG_TAG, "handleInitialSyncInvite : the session is not anymore active");
            return;
        }

        // add yourself
        RoomMember member = new RoomMember();
        member.membership = RoomMember.MEMBERSHIP_INVITE;
        setMember(mMyUserId, member);

        // and the inviter
        member = new RoomMember();
        member.membership = RoomMember.MEMBERSHIP_JOIN;
        setMember(inviterUserId, member);

        // Build a fake invite event
        Event inviteEvent = new Event();
        inviteEvent.roomId = getRoomId();
        inviteEvent.stateKey = mMyUserId;
        inviteEvent.setSender(inviterUserId);
        inviteEvent.type = Event.EVENT_TYPE_STATE_ROOM_MEMBER;
        inviteEvent.setOriginServerTs(System.currentTimeMillis()); // This is where it's fake
        inviteEvent.content = JsonUtils.toJson(member);

        mStore.storeSummary(getRoomId(), inviteEvent, null, mMyUserId);

        // Set the inviter ID
        RoomSummary roomSummary = mStore.getSummary(getRoomId());
        if (null != roomSummary) {
            roomSummary.setInviterUserId(inviterUserId);
        }
    }

    /**
     * Handle the room data received from a per-room initial sync
     * @param roomResponse the room response object
     */
    public void handleInitialRoomResponse(RoomResponse roomResponse) {
        if (!mDataHandler.isActive()) {
            Log.e(LOG_TAG, "handleInitialRoomResponse : the session is not anymore active");
            return;
        }

        // Handle state events
        if (roomResponse.state != null) {
            for (Event event : roomResponse.state) {
                try {
                    mLiveTimeline.processStateEvent(event, Room.EventDirection.FORWARDS);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "processStateEvent failed " + e.getLocalizedMessage());
                }
            }
        }

        // Handle visibility
        if (roomResponse.visibility != null) {
            setVisibility(roomResponse.visibility);
        }

        // Handle messages / pagination token
        if ((roomResponse.messages != null) && (roomResponse.messages.chunk.size() > 0)) {
            mStore.storeRoomEvents(getRoomId(), roomResponse.messages, Room.EventDirection.FORWARDS);

            int index = roomResponse.messages.chunk.size() - 1;

            while (index >= 0) {
                // To store the summary, we need the last event and the room state from just before
                Event lastEvent = roomResponse.messages.chunk.get(index);

                if (RoomSummary.isSupportedEvent(lastEvent)) {
                    RoomState beforeLiveRoomState = getState().deepCopy();
                    beforeLiveRoomState.applyState(lastEvent, Room.EventDirection.BACKWARDS);

                    mStore.storeSummary(getRoomId(), lastEvent, getState(), mMyUserId);

                    index = -1;
                } else {
                    index--;
                }
            }
        }

        // Handle presence
        if ((roomResponse.presence != null) && (roomResponse.presence.size() > 0)) {
            mDataHandler.handleLiveEvents(roomResponse.presence);
        }

        // receipts
        if ((roomResponse.receipts != null) && (roomResponse.receipts.size() > 0)) {
            mDataHandler.handleLiveEvents(roomResponse.receipts);
        }

        // account data
        if ((roomResponse.accountData != null) && (roomResponse.accountData.size() > 0)) {
            // the room id is not defined in the events
            // so as the room is defined here, avoid calling handleLiveEvents
            handleAccountDataEvents(roomResponse.accountData);
        }

        // Handle the special case where the room is an invite
        if (RoomMember.MEMBERSHIP_INVITE.equals(roomResponse.membership)) {
            handleInitialSyncInvite(roomResponse.inviter);
        } else {
            mDataHandler.onRoomInitialSyncComplete(getRoomId());
        }
    }

    /**
     * Perform a room-level initial sync to get latest messages and pagination token.
     * @param callback the async callback
     */
    public void initialSync(final ApiCallback<Void> callback) {
        mDataHandler.getDataRetriever().getRoomsRestClient().initialSync(getRoomId(), new SimpleApiCallback<RoomResponse>(callback) {
                @Override
                public void onSuccess(RoomResponse roomInfo) {
                    // check if the SDK was not logged out
                    if (mDataHandler.isActive()) {
                        handleInitialRoomResponse(roomInfo);

                        Log.d(LOG_TAG, "initialSync : commit");
                        mStore.commit();
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
        mDataHandler.getDataRetriever().getRoomsRestClient().joinRoom(getRoomId(), new SimpleApiCallback<RoomResponse>(callback) {
            @Override
            public void onSuccess(final RoomResponse aReponse) {
                try {
                    // the join request did not get the room initial history
                    if (getState().getMember(mMyUserId) == null) {
                        // wait the server sends the events chunk before calling the callback
                        mOnInitialSyncCallback = callback;
                    } else {
                        // already got the initial sync
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
     * Invite an user to this room.
     * @param userId the user id
     * @param callback the callback for when done
     */
    public void invite(String userId, ApiCallback<Void> callback) {
        mDataHandler.getDataRetriever().getRoomsRestClient().inviteToRoom(getRoomId(), userId, callback);
    }

    /**
     * Invite an user to a room based on their email address to this room.
     * @param email the email adress
     * @param callback the callback for when done
     */
    public void inviteByEmail(String email, ApiCallback<Void> callback) {
        mDataHandler.getDataRetriever().getRoomsRestClient().inviteByEmailToRoom(getRoomId(), email, callback);
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
        mDataHandler.getDataRetriever().getRoomsRestClient().inviteToRoom(getRoomId(), userIds.get(index), new ApiCallback<Void>() {
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
        mDataHandler.onRoomInternalUpdate(getRoomId());

        mDataHandler.getDataRetriever().getRoomsRestClient().leaveRoom(getRoomId(), new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                if (mDataHandler.isActive()) {
                    Room.this.mIsLeaving = false;

                    // delete references to the room
                    mStore.deleteRoom(getRoomId());
                    Log.d(LOG_TAG, "leave : commit");
                    mStore.commit();

                    try {
                        callback.onSuccess(info);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "leave exception " + e.getMessage());
                    }

                    mDataHandler.onLeaveRoom(getRoomId());
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

                mDataHandler.onRoomInternalUpdate(getRoomId());
            }

            @Override
            public void onMatrixError(MatrixError e) {
                Room.this.mIsLeaving = false;

                try {
                    callback.onMatrixError(e);
                } catch (Exception anException) {
                    Log.e(LOG_TAG, "leave exception " + anException.getMessage());
                }

                mDataHandler.onRoomInternalUpdate(getRoomId());
            }

            @Override
            public void onUnexpectedError(Exception e) {
                Room.this.mIsLeaving = false;

                try {
                    callback.onUnexpectedError(e);
                } catch (Exception anException) {
                    Log.e(LOG_TAG, "leave exception " + anException.getMessage());
                }

                mDataHandler.onRoomInternalUpdate(getRoomId());
            }
        });
    }

    /**
     * Kick a user from the room.
     * @param userId the user id
     * @param callback the async callback
     */
    public void kick(String userId, ApiCallback<Void> callback) {
        mDataHandler.getDataRetriever().getRoomsRestClient().kickFromRoom(getRoomId(), userId, callback);
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
        mDataHandler.getDataRetriever().getRoomsRestClient().banFromRoom(getRoomId(), user, callback);
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
        PowerLevels powerLevels = getState().getPowerLevels().deepCopy();
        powerLevels.setUserPowerLevel(userId, powerLevel);
        mDataHandler.getDataRetriever().getRoomsRestClient().updatePowerLevels(getRoomId(), powerLevels, callback);
    }

    /**
     * Update the room's name.
     * @param name the new name
     * @param callback the async callback
     */
    public void updateName(final String name, final ApiCallback<Void> callback) {
        mDataHandler.getDataRetriever().getRoomsRestClient().updateName(getRoomId(), name, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                getState().name = name;
                mStore.storeLiveStateForRoom(getRoomId());

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
        mDataHandler.getDataRetriever().getRoomsRestClient().updateTopic(getRoomId(), topic, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                getState().topic = topic;
                mStore.storeLiveStateForRoom(getRoomId());

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
        mDataHandler.getDataRetriever().getRoomsRestClient().updateCanonicalAlias(getRoomId(), canonicalAlias, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                getState().roomAliasName = canonicalAlias;
                mStore.storeLiveStateForRoom(getRoomId());

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
        String res = getState().getAvatarUrl();

        // detect if it is a room with no more than 2 members (i.e. an alone or a 1:1 chat)
        if (null == res) {
            Collection<RoomMember> members = getState().getMembers();

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
        mDataHandler.getDataRetriever().getRoomsRestClient().updateAvatarUrl(getRoomId(), avatarUrl, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                getState().url = avatarUrl;
                mStore.storeLiveStateForRoom(getRoomId());

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
        mDataHandler.getDataRetriever().getRoomsRestClient().updateHistoryVisibility(getRoomId(), visibility, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                getState().visibility = visibility;
                mStore.storeLiveStateForRoom(getRoomId());

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
        boolean isUpdated = mStore.storeReceipt(receiptData, getRoomId());

        // check oneself receipts
        // if there is an update, it means that the messages have been read from andother client
        // it requires to update the summary to display valid information.
        if (isUpdated && TextUtils.equals(mMyUserId, receiptData.userId)) {
            RoomSummary summary = mStore.getSummary(getRoomId());
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
        RoomSummary summary = mStore.getSummary(getRoomId());
        Event event = mStore.getLatestEvent(getRoomId());

        if ((null != event) && (null != summary)) {
            // any update
            if (!TextUtils.equals(summary.getReadReceiptToken(), event.eventId)) {
                mDataHandler.getDataRetriever().getRoomsRestClient().sendReadReceipt(getRoomId(), event.eventId, null);
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
        RoomSummary summary = mStore.getSummary(getRoomId());

        if (summary.setReadReceiptToken(token, ts)) {
            mStore.flushSummary(summary);
            mStore.commit();
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
        return mStore.isEventRead(getRoomId(), mMyUserId, eventId);
    }

    //================================================================================
    // Unread event count management
    //================================================================================

    /**
     * @return the number of unread messages that match the push notification rules.
     */
    public int getNotificationCount() {
        return getState().mNotificationCount;
    }

    /**
     * @return the number of highlighted events.
     */
    public int gettHighlightCount() {
        return getState().mHighlightCount;
    }

    /**
     *  refresh the unread events counts.
     */
    public void refreshUnreadCounter() {
        // avoid refreshing the unread counter while processing a bunch of messages.
        if (!mIsSyncing) {
            RoomSummary summary = mStore.getSummary(getRoomId());

            if (null != summary) {
                int prevValue = summary.getUnreadEventsCount();
                int newValue = mStore.eventsCountAfter(getRoomId(), summary.getReadReceiptToken());

                if (prevValue != newValue) {
                    summary.setUnreadEventsCount(newValue);
                    mStore.flushSummary(summary);
                    mStore.commit();
                }
            }
        }
    }

    /**
     * @return the unread messages count.
     */
    public int getUnreadEventsCount() {
        RoomSummary summary = mStore.getSummary(getRoomId());

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
            mDataHandler.getDataRetriever().getRoomsRestClient().sendTypingNotification(getRoomId(), mMyUserId, isTyping, timeout, callback);
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
    // Unsent events
    //================================================================================

    /**
     * Returns the unsent messages except the sending ones.
     * @return the unsent messages list.
     */
    public ArrayList<Event> getUnsentEvents() {
        Collection<Event> events = mStore.getLatestUnsentEvents(getRoomId());

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

            mStore.storeAccountData(getRoomId(), mAccountData);
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
            mDataHandler.getDataRetriever().getRoomsRestClient().addTag(getRoomId(), tag, order, callback);
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
            mDataHandler.getDataRetriever().getRoomsRestClient().removeTag(getRoomId(), tag, callback);
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

    public void handleJoinedRoomSync(RoomSync roomSync, boolean isInitialSync) {
        mIsSyncing = true;

        boolean isRoomInitialSync = mLiveTimeline.handleJoinedRoomSync(roomSync, isInitialSync);

        if ((null != roomSync.ephemeral) && (null != roomSync.ephemeral.events)) {
            // Handle here ephemeral events (if any)
            for (Event event : roomSync.ephemeral.events) {
                // the roomId is not defined.
                event.roomId = getRoomId();
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

        // the user joined the room
        // With V2 sync, the server sends the events to init the room.
        if (null != mOnInitialSyncCallback) {
            try {
                mOnInitialSyncCallback.onSuccess(null);
            } catch (Exception e) {
            }
            mOnInitialSyncCallback = null;
        }


        mIsSyncing = false;
    }

    public void handleInvitedRoomSync(InvitedRoomSync invitedRoomSync) {
        // Handle the state events as live events (the room state will be updated, and the listeners (if any) will be notified).

        if ((null != invitedRoomSync) && (null != invitedRoomSync.inviteState) && (null != invitedRoomSync.inviteState.events)) {

           for(Event event : invitedRoomSync.inviteState.events) {
                // Add a fake event id if none in order to be able to store the event
                if (null == event.eventId) {
                    event.eventId = getRoomId() + "-" + System.currentTimeMillis() + "-" + event.hashCode();
                }

                // the roomId is not defined.
                event.roomId = getRoomId();
                mDataHandler.handleLiveEvent(event);
            }
        }
    }

    //==============================================================================================================
    // Room events dispatcher
    //==============================================================================================================

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
                if (TextUtils.equals(getRoomId(), event.roomId) && mIsReady) {

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
                if (TextUtils.equals(getRoomId(), event.roomId)) {
                    try {
                        eventListener.onBackEvent(event, roomState);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "onBackEvent exception " + e.getMessage());
                    }
                }
            }

            @Override
            public void onSentEvent(Event event) {
                // Filter out events for other rooms
                if (TextUtils.equals(getRoomId(), event.roomId)) {
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
                if (TextUtils.equals(getRoomId(), event.roomId)) {
                    try {
                        eventListener.onFailedSendingEvent(event);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "onFailedSendingEvent exception " + e.getMessage());
                    }
                }
            }

            @Override
            public void onRoomInitialSyncComplete(String roomId) {
                // Filter out events for other rooms
                if (TextUtils.equals(getRoomId(), roomId)) {
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
                if (TextUtils.equals(getRoomId(), roomId)) {
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
                if (TextUtils.equals(getRoomId(), roomId)) {
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
                if (TextUtils.equals(getRoomId(), roomId)) {
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
                if (TextUtils.equals(getRoomId(), roomId)) {
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
                if (TextUtils.equals(getRoomId(), roomId)) {
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
                if (TextUtils.equals(getRoomId(), roomId)) {
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
                if (TextUtils.equals(getRoomId(), roomId)) {
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


}
