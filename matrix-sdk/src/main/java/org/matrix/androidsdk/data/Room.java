/*
 * Copyright 2014 OpenMarket Ltd
 * Copyright 2017 Vector Creations Ltd
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
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Pair;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import org.matrix.androidsdk.MXDataHandler;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.call.MXCallsManager;
import org.matrix.androidsdk.crypto.MXCryptoError;
import org.matrix.androidsdk.crypto.data.MXEncryptEventContentResult;
import org.matrix.androidsdk.data.store.IMXStore;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.listeners.IMXEventListener;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.client.AccountDataRestClient;
import org.matrix.androidsdk.rest.client.RoomsRestClient;
import org.matrix.androidsdk.rest.client.UrlPostTask;
import org.matrix.androidsdk.rest.model.BannedUser;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.PowerLevels;
import org.matrix.androidsdk.rest.model.ReceiptData;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.TokensChunkResponse;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.rest.model.message.FileInfo;
import org.matrix.androidsdk.rest.model.message.FileMessage;
import org.matrix.androidsdk.rest.model.message.ImageInfo;
import org.matrix.androidsdk.rest.model.message.ImageMessage;
import org.matrix.androidsdk.rest.model.message.LocationMessage;
import org.matrix.androidsdk.rest.model.message.Message;
import org.matrix.androidsdk.rest.model.message.ThumbnailInfo;
import org.matrix.androidsdk.rest.model.message.VideoInfo;
import org.matrix.androidsdk.rest.model.message.VideoMessage;
import org.matrix.androidsdk.rest.model.sync.InvitedRoomSync;
import org.matrix.androidsdk.rest.model.sync.RoomResponse;
import org.matrix.androidsdk.rest.model.sync.RoomSync;
import org.matrix.androidsdk.util.ImageUtils;
import org.matrix.androidsdk.util.JsonUtils;
import org.matrix.androidsdk.util.Log;

import java.io.File;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Class representing a room and the interactions we have with it.
 */
public class Room {

    private static final String LOG_TAG = Room.class.getSimpleName();

    // Account data
    private RoomAccountData mAccountData = new RoomAccountData();

    // handler
    private MXDataHandler mDataHandler;

    // store
    private IMXStore mStore;

    private String mMyUserId = null;

    // Map to keep track of the listeners the client adds vs. the ones we actually register to the global data handler.
    // This is needed to find the right one when removing the listener.
    private final Map<IMXEventListener, IMXEventListener> mEventListeners = new HashMap<>();

    // the user is leaving the room
    private boolean mIsLeaving = false;

    // the room is syncing
    private boolean mIsSyncing;

    // the unread messages count must be refreshed when the current sync is done.
    private boolean mRefreshUnreadAfterSync = false;

    // the time line
    private EventTimeline mLiveTimeline;

    // initial sync callback.
    private ApiCallback<Void> mOnInitialSyncCallback;

    // gson parser
    private final Gson gson = new GsonBuilder().create();

    // This is used to block live events and history requests until the state is fully processed and ready
    private boolean mIsReady = false;

    // call conference user id
    private String mCallConferenceUserId;

    // true when the current room is a left one
    private boolean mIsLeft;

    /**
     * Default room creator
     */
    public Room() {
        mLiveTimeline = new EventTimeline(this, true);
    }

    /**
     * Init the room fields.
     *
     * @param store       the store.
     * @param roomId      the room id
     * @param dataHandler the data handler
     */
    public void init(IMXStore store, String roomId, MXDataHandler dataHandler) {
        mLiveTimeline.setRoomId(roomId);
        mDataHandler = dataHandler;
        mStore = store;

        if (null != mDataHandler) {
            mMyUserId = mDataHandler.getUserId();
            mLiveTimeline.setDataHandler(mStore, dataHandler);
        }
    }

    /**
     * @return the used datahandler
     */
    public MXDataHandler getDataHandler() {
        return mDataHandler;
    }

    /**
     * @return the store in which the room is stored
     */
    public IMXStore getStore() {
        if (null == mStore) {
            if (null != mDataHandler) {
                mStore = mDataHandler.getStore(getRoomId());
            }

            if (null == mStore) {
                Log.e(LOG_TAG, "## getStore() : cannot retrieve the store of " + getRoomId());
            }
        }

        return mStore;
    }

    /**
     * Tells if the room is a call conference one
     * i.e. this room has been created to manage the call conference
     *
     * @return true if it is a call conference room.
     */
    public boolean isConferenceUserRoom() {
        return getLiveState().isConferenceUserRoom();
    }

    /**
     * Set this room as a conference user room
     *
     * @param isConferenceUserRoom true when it is an user conference room.
     */
    public void setIsConferenceUserRoom(boolean isConferenceUserRoom) {
        getLiveState().setIsConferenceUserRoom(isConferenceUserRoom);
    }

    /**
     * Test if there is an ongoing conference call.
     *
     * @return true if there is one.
     */
    public boolean isOngoingConferenceCall() {
        RoomMember conferenceUser = getLiveState().getMember(MXCallsManager.getConferenceUserId(getRoomId()));
        return (null != conferenceUser) && TextUtils.equals(conferenceUser.membership, RoomMember.MEMBERSHIP_JOIN);
    }

    /**
     * Defines that the current room is a left one
     *
     * @param isLeft true when the current room is a left one
     */
    public void setIsLeft(boolean isLeft) {
        mIsLeft = isLeft;
        mLiveTimeline.setIsHistorical(isLeft);
    }

    /**
     * @return true if the current room is an left one
     */
    public boolean isLeft() {
        return mIsLeft;
    }

    //================================================================================
    // Sync events
    //================================================================================

    /**
     * Manage list of ephemeral events
     *
     * @param events the ephemeral events
     */
    private void handleEphemeralEvents(List<Event> events) {
        for (Event event : events) {

            // ensure that the room Id is defined
            event.roomId = getRoomId();

            try {
                if (Event.EVENT_TYPE_RECEIPT.equals(event.getType())) {
                    if (event.roomId != null) {
                        List<String> senders = handleReceiptEvent(event);

                        if ((null != senders) && (senders.size() > 0)) {
                            mDataHandler.onReceiptEvent(event.roomId, senders);
                        }
                    }
                } else if (Event.EVENT_TYPE_TYPING.equals(event.getType())) {
                    JsonObject eventContent = event.getContentAsJsonObject();

                    if (eventContent.has("user_ids")) {
                        synchronized (Room.this) {
                            mTypingUsers = null;

                            try {
                                mTypingUsers = (new Gson()).fromJson(eventContent.get("user_ids"), new TypeToken<List<String>>() {
                                }.getType());
                            } catch (Exception e) {
                                Log.e(LOG_TAG, "## handleEphemeralEvents() : exception " + e.getMessage());
                            }

                            // avoid null list
                            if (null == mTypingUsers) {
                                mTypingUsers = new ArrayList<>();
                            }
                        }
                    }

                    mDataHandler.onLiveEvent(event, getState());
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "ephemeral event failed " + e.getMessage());
            }
        }
    }

    /**
     * Handle the events of a joined room.
     *
     * @param roomSync            the sync events list.
     * @param isGlobalInitialSync true if the room is initialized by a global initial sync.
     */
    public void handleJoinedRoomSync(RoomSync roomSync, boolean isGlobalInitialSync) {
        if (null != mOnInitialSyncCallback) {
            Log.d(LOG_TAG, "initial sync handleJoinedRoomSync " + getRoomId());
        } else {
            Log.d(LOG_TAG, "handleJoinedRoomSync " + getRoomId());
        }

        mIsSyncing = true;

        synchronized (this) {
            mLiveTimeline.handleJoinedRoomSync(roomSync, isGlobalInitialSync);

            // ephemeral events
            if ((null != roomSync.ephemeral) && (null != roomSync.ephemeral.events)) {
                handleEphemeralEvents(roomSync.ephemeral.events);
            }

            // Handle account data events (if any)
            if ((null != roomSync.accountData) && (null != roomSync.accountData.events) && (roomSync.accountData.events.size() > 0)) {
                if (isGlobalInitialSync) {
                    Log.d(LOG_TAG, "## handleJoinedRoomSync : received " + roomSync.accountData.events.size() + " account data events");
                }

                handleAccountDataEvents(roomSync.accountData.events);
            }
        }

        // the user joined the room
        // With V2 sync, the server sends the events to init the room.
        if ((null != mOnInitialSyncCallback) && !isWaitingInitialSync()) {
            Log.d(LOG_TAG, "handleJoinedRoomSync " + getRoomId() + " :  the initial sync is done");
            final ApiCallback<Void> fOnInitialSyncCallback = mOnInitialSyncCallback;

            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    // to initialise the notification counters
                    markAllAsRead(null);

                    try {
                        fOnInitialSyncCallback.onSuccess(null);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "handleJoinedRoomSync : onSuccess failed" + e.getMessage());
                    }
                }
            });

            mOnInitialSyncCallback = null;


        }

        mIsSyncing = false;

        if (mRefreshUnreadAfterSync) {
            if (!isGlobalInitialSync) {
                refreshUnreadCounter();
            } // else -> it will be done at the end of the sync
            mRefreshUnreadAfterSync = false;
        }
    }

    /**
     * Handle the invitation room events
     *
     * @param invitedRoomSync the invitation room events.
     */
    public void handleInvitedRoomSync(InvitedRoomSync invitedRoomSync) {
        mLiveTimeline.handleInvitedRoomSync(invitedRoomSync);
    }

    /**
     * Store an outgoing event.
     *
     * @param event the event.
     */
    public void storeOutgoingEvent(Event event) {
        mLiveTimeline.storeOutgoingEvent(event);
    }

    /**
     * Request events to the server. The local cache is not used.
     * The events will not be saved in the local storage.
     *
     * @param token           the token to go back from.
     * @param paginationCount the number of events to retrieve.
     * @param callback        the onComplete callback
     */
    public void requestServerRoomHistory(final String token, final int paginationCount, final ApiCallback<TokensChunkResponse<Event>> callback) {
        mDataHandler.getDataRetriever().requestServerRoomHistory(getRoomId(), token, paginationCount, new SimpleApiCallback<TokensChunkResponse<Event>>(callback) {
            @Override
            public void onSuccess(TokensChunkResponse<Event> info) {
                callback.onSuccess(info);
            }
        });
    }

    /**
     * cancel any remote request
     */
    public void cancelRemoteHistoryRequest() {
        mDataHandler.getDataRetriever().cancelRemoteHistoryRequest(getRoomId());
    }

    //================================================================================
    // Getters / setters
    //================================================================================

    public String getRoomId() {
        return mLiveTimeline.getState().roomId;
    }

    public void setAccountData(RoomAccountData accountData) {
        this.mAccountData = accountData;
    }

    public RoomAccountData getAccountData() {
        return this.mAccountData;
    }

    public RoomState getState() {
        return mLiveTimeline.getState();
    }

    public RoomState getLiveState() {
        return getState();
    }

    public boolean isLeaving() {
        return mIsLeaving;
    }

    public Collection<RoomMember> getMembers() {
        return getState().getMembers();
    }

    public EventTimeline getLiveTimeLine() {
        return mLiveTimeline;
    }

    public void setLiveTimeline(EventTimeline eventTimeline) {
        mLiveTimeline = eventTimeline;
    }

    public void setReadyState(boolean isReady) {
        mIsReady = isReady;
    }

    public boolean isReady() {
        return mIsReady;
    }

    /**
     * @return the list of active members in a room ie joined or invited ones.
     */
    public Collection<RoomMember> getActiveMembers() {
        Collection<RoomMember> members = getState().getMembers();
        List<RoomMember> activeMembers = new ArrayList<>();
        String conferenceUserId = MXCallsManager.getConferenceUserId(getRoomId());

        for (RoomMember member : members) {
            if (!TextUtils.equals(member.getUserId(), conferenceUserId)) {
                if (TextUtils.equals(member.membership, RoomMember.MEMBERSHIP_JOIN) || TextUtils.equals(member.membership, RoomMember.MEMBERSHIP_INVITE)) {
                    activeMembers.add(member);
                }
            }
        }

        return activeMembers;
    }

    /**
     * Get the list of the members who have joined the room.
     *
     * @return the list the joined members of the room.
     */
    public Collection<RoomMember> getJoinedMembers() {
        Collection<RoomMember> membersList = getState().getMembers();
        List<RoomMember> joinedMembersList = new ArrayList<>();

        for (RoomMember member : membersList) {
            if (TextUtils.equals(member.membership, RoomMember.MEMBERSHIP_JOIN)) {
                joinedMembersList.add(member);
            }
        }

        return joinedMembersList;
    }

    public RoomMember getMember(String userId) {
        return getState().getMember(userId);
    }

    // member event caches
    private final Map<String, Event> mMemberEventByEventId = new HashMap<>();

    public void getMemberEvent(final String userId, final ApiCallback<Event> callback) {
        final Event event;
        final RoomMember member = getMember(userId);

        if ((null != member) && (null != member.getOriginalEventId())) {
            event = mMemberEventByEventId.get(member.getOriginalEventId());

            if (null == event) {
                mDataHandler.getDataRetriever().getRoomsRestClient().getEvent(getRoomId(), member.getOriginalEventId(), new ApiCallback<Event>() {
                    @Override
                    public void onSuccess(Event event) {
                        if (null != event) {
                            mMemberEventByEventId.put(event.eventId, event);
                        }
                        callback.onSuccess(event);
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
                return;
            }
        } else {
            event = null;
        }

        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                callback.onSuccess(event);
            }
        });
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

    /**
     * @return true if the user is invited in a direct chat room
     */
    public boolean isDirectChatInvitation() {
        if (isInvited()) {
            // Is it an initial sync for this room ?
            RoomState state = getState();

            RoomMember selfMember = state.getMember(mMyUserId);

            if ((null != selfMember) && (null != selfMember.is_direct)) {
                return selfMember.is_direct;
            }
        }

        return false;
    }

    //================================================================================
    // Join
    //================================================================================

    /**
     * Defines the initial sync callback
     *
     * @param callback the new callback.
     */
    public void setOnInitialSyncCallback(ApiCallback<Void> callback) {
        mOnInitialSyncCallback = callback;
    }

    /**
     * Join a room with an url to post before joined the room.
     *
     * @param alias               the room alias
     * @param thirdPartySignedUrl the thirdPartySigned url
     * @param callback            the callback
     */
    public void joinWithThirdPartySigned(final String alias, final String thirdPartySignedUrl, final ApiCallback<Void> callback) {
        if (null == thirdPartySignedUrl) {
            join(alias, callback);
        } else {
            String url = thirdPartySignedUrl + "&mxid=" + mMyUserId;
            UrlPostTask task = new UrlPostTask();

            task.setListener(new UrlPostTask.IPostTaskListener() {
                @Override
                public void onSucceed(JsonObject object) {
                    HashMap<String, Object> map = null;

                    try {
                        map = new Gson().fromJson(object, new TypeToken<HashMap<String, Object>>() {
                        }.getType());
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "joinWithThirdPartySigned :  Gson().fromJson failed" + e.getMessage());
                    }

                    if (null != map) {
                        HashMap<String, Object> joinMap = new HashMap<>();
                        joinMap.put("third_party_signed", map);
                        join(alias, joinMap, callback);
                    } else {
                        join(callback);
                    }
                }

                @Override
                public void onError(String errorMessage) {
                    Log.d(LOG_TAG, "joinWithThirdPartySigned failed " + errorMessage);

                    // cannot validate the url
                    // try without validating the url
                    join(callback);
                }
            });

            // avoid crash if there are too many running task
            try {
                task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, url);
            } catch (final Exception e) {
                task.cancel(true);
                Log.e(LOG_TAG, "joinWithThirdPartySigned : task.executeOnExecutor failed" + e.getMessage());

                (new android.os.Handler(Looper.getMainLooper())).post(new Runnable() {
                    @Override
                    public void run() {
                        if (null != callback) {
                            callback.onUnexpectedError(e);
                        }
                    }
                });
            }
        }
    }

    /**
     * Join the room. If successful, the room's current state will be loaded before calling back onComplete.
     *
     * @param callback the callback for when done
     */
    public void join(final ApiCallback<Void> callback) {
        join(null, null, callback);
    }

    /**
     * Join the room. If successful, the room's current state will be loaded before calling back onComplete.
     *
     * @param roomAlias the room alias
     * @param callback  the callback for when done
     */
    private void join(String roomAlias, ApiCallback<Void> callback) {
        join(roomAlias, null, callback);
    }

    /**
     * Join the room. If successful, the room's current state will be loaded before calling back onComplete.
     *
     * @param roomAlias   the room alias
     * @param extraParams the join extra params
     * @param callback    the callback for when done
     */
    private void join(final String roomAlias, final HashMap<String, Object> extraParams, final ApiCallback<Void> callback) {
        Log.d(LOG_TAG, "Join the room " + getRoomId() + " with alias " + roomAlias);

        mDataHandler.getDataRetriever().getRoomsRestClient().joinRoom((null != roomAlias) ? roomAlias : getRoomId(), extraParams, new SimpleApiCallback<RoomResponse>(callback) {
            @Override
            public void onSuccess(final RoomResponse aResponse) {
                try {
                    // the join request did not get the room initial history
                    if (isWaitingInitialSync()) {
                        Log.d(LOG_TAG, "the room " + getRoomId() + " is joined but wait after initial sync");

                        // wait the server sends the events chunk before calling the callback
                        setOnInitialSyncCallback(callback);
                    } else {
                        Log.d(LOG_TAG, "the room " + getRoomId() + " is joined : the initial sync has been done");
                        // to initialise the notification counters
                        markAllAsRead(null);
                        // already got the initial sync
                        callback.onSuccess(null);
                    }
                } catch (Exception e) {
                    Log.e(LOG_TAG, "join exception " + e.getMessage());
                }
            }

            @Override
            public void onNetworkError(Exception e) {
                Log.e(LOG_TAG, "join onNetworkError " + e.getMessage());
                callback.onNetworkError(e);
            }

            @Override
            public void onMatrixError(MatrixError e) {
                Log.e(LOG_TAG, "join onMatrixError " + e.getMessage());

                if (MatrixError.UNKNOWN.equals(e.errcode) && TextUtils.equals("No known servers", e.error)) {
                    // minging kludge until https://matrix.org/jira/browse/SYN-678 is fixed
                    // 'Error when trying to join an empty room should be more explicit
                    e.error = getStore().getContext().getString(org.matrix.androidsdk.R.string.room_error_join_failed_empty_room);
                }

                // if the alias is not found
                // try with the room id
                if ((e.mStatus == 404) && !TextUtils.isEmpty(roomAlias)) {
                    Log.e(LOG_TAG, "Retry without the room alias");
                    join(null, extraParams, callback);
                    return;
                }

                callback.onMatrixError(e);
            }

            @Override
            public void onUnexpectedError(Exception e) {
                Log.e(LOG_TAG, "join onUnexpectedError " + e.getMessage());
                callback.onUnexpectedError(e);
            }
        });
    }

    /**
     * @return true if the user joined the room
     */
    private boolean selfJoined() {
        RoomMember roomMember = getMember(mMyUserId);

        // send the event only if the user has joined the room.
        return ((null != roomMember) && RoomMember.MEMBERSHIP_JOIN.equals(roomMember.membership));
    }

    /**
     * @return true if the user is not yet an active member of the room
     */
    public boolean isWaitingInitialSync() {
        RoomMember roomMember = getMember(mMyUserId);
        return ((null == roomMember) || RoomMember.MEMBERSHIP_INVITE.equals(roomMember.membership));
    }

    //================================================================================
    // Room info (liveState) update
    //================================================================================

    /**
     * This class dispatches the error to the dedicated callbacks.
     * If the operation succeeds, the room state is saved because calling the callback.
     */
    private class RoomInfoUpdateCallback<T> implements ApiCallback<T> {
        private final ApiCallback<T> mCallback;

        /**
         * Constructor
         */
        public RoomInfoUpdateCallback(ApiCallback<T> callback) {
            mCallback = callback;
        }

        @Override
        public void onSuccess(T info) {
            getStore().storeLiveStateForRoom(getRoomId());

            if (null != mCallback) {
                mCallback.onSuccess(info);
            }
        }

        @Override
        public void onNetworkError(Exception e) {
            if (null != mCallback) {
                mCallback.onNetworkError(e);
            }
        }

        @Override
        public void onMatrixError(final MatrixError e) {
            if (null != mCallback) {
                mCallback.onMatrixError(e);
            }
        }

        @Override
        public void onUnexpectedError(final Exception e) {
            if (null != mCallback) {
                mCallback.onUnexpectedError(e);
            }
        }
    }

    /**
     * Update the power level of the user userId
     *
     * @param userId     the user id
     * @param powerLevel the new power level
     * @param callback   the callback with the created event
     */
    public void updateUserPowerLevels(String userId, int powerLevel, ApiCallback<Void> callback) {
        PowerLevels powerLevels = getState().getPowerLevels().deepCopy();
        powerLevels.setUserPowerLevel(userId, powerLevel);
        mDataHandler.getDataRetriever().getRoomsRestClient().updatePowerLevels(getRoomId(), powerLevels, callback);
    }

    /**
     * Update the room's name.
     *
     * @param aRoomName the new name
     * @param callback  the async callback
     */
    public void updateName(String aRoomName, final ApiCallback<Void> callback) {
        final String fRoomName = TextUtils.isEmpty(aRoomName) ? null : aRoomName;

        mDataHandler.getDataRetriever().getRoomsRestClient().updateRoomName(getRoomId(), fRoomName, new RoomInfoUpdateCallback<Void>(callback) {
            @Override
            public void onSuccess(Void info) {
                getState().name = fRoomName;
                super.onSuccess(info);
            }
        });
    }

    /**
     * Update the room's topic.
     *
     * @param aTopic   the new topic
     * @param callback the async callback
     */
    public void updateTopic(final String aTopic, final ApiCallback<Void> callback) {
        final String fTopic = TextUtils.isEmpty(aTopic) ? null : aTopic;

        mDataHandler.getDataRetriever().getRoomsRestClient().updateTopic(getRoomId(), fTopic, new RoomInfoUpdateCallback<Void>(callback) {
            @Override
            public void onSuccess(Void info) {
                getState().topic = fTopic;
                super.onSuccess(info);
            }
        });
    }

    /**
     * Update the room's main alias.
     *
     * @param aCanonicalAlias the canonical alias
     * @param callback        the async callback
     */
    public void updateCanonicalAlias(final String aCanonicalAlias, final ApiCallback<Void> callback) {
        final String fCanonicalAlias = TextUtils.isEmpty(aCanonicalAlias) ? null : aCanonicalAlias;

        mDataHandler.getDataRetriever().getRoomsRestClient().updateCanonicalAlias(getRoomId(), fCanonicalAlias, new RoomInfoUpdateCallback<Void>(callback) {
            @Override
            public void onSuccess(Void info) {
                getState().roomAliasName = fCanonicalAlias;
                super.onSuccess(info);
            }
        });
    }

    /**
     * Provides the room aliases list.
     * The result is never null.
     *
     * @return the room aliases list.
     */
    public List<String> getAliases() {
        return getLiveState().getAliases();
    }

    /**
     * Remove a room alias.
     *
     * @param alias    the alias to remove
     * @param callback the async callback
     */
    public void removeAlias(final String alias, final ApiCallback<Void> callback) {
        final List<String> updatedAliasesList = new ArrayList<>(getAliases());

        // nothing to do
        if (TextUtils.isEmpty(alias) || (updatedAliasesList.indexOf(alias) < 0)) {
            if (null != callback) {
                callback.onSuccess(null);
            }
            return;
        }

        mDataHandler.getDataRetriever().getRoomsRestClient().removeRoomAlias(alias, new RoomInfoUpdateCallback<Void>(callback) {
            @Override
            public void onSuccess(Void info) {
                getState().removeAlias(alias);
                super.onSuccess(info);
            }
        });
    }

    /**
     * Try to add an alias to the aliases list.
     *
     * @param alias    the alias to add.
     * @param callback the the async callback
     */
    public void addAlias(final String alias, final ApiCallback<Void> callback) {
        final List<String> updatedAliasesList = new ArrayList<>(getAliases());

        // nothing to do
        if (TextUtils.isEmpty(alias) || (updatedAliasesList.indexOf(alias) >= 0)) {
            if (null != callback) {
                callback.onSuccess(null);
            }
            return;
        }

        mDataHandler.getDataRetriever().getRoomsRestClient().setRoomIdByAlias(getRoomId(), alias, new RoomInfoUpdateCallback<Void>(callback) {
            @Override
            public void onSuccess(Void info) {
                getState().addAlias(alias);
                super.onSuccess(info);
            }
        });
    }

    /**
     * Add a group to the related ones
     *
     * @param groupId  the group id to add
     * @param callback the asynchronous callback
     */
    public void addRelatedGroup(final String groupId, final ApiCallback<Void> callback) {
        List<String> nextGroupIdsList = new ArrayList<>(getLiveState().getRelatedGroups());

        if (!nextGroupIdsList.contains(groupId)) {
            nextGroupIdsList.add(groupId);
        }

        updateRelatedGroups(nextGroupIdsList, callback);
    }

    /**
     * Remove a group id from the related ones.
     *
     * @param groupId  the group id
     * @param callback the asynchronous callback
     */
    public void removeRelatedGroup(final String groupId, final ApiCallback<Void> callback) {
        List<String> nextGroupIdsList = new ArrayList<>(getLiveState().getRelatedGroups());
        nextGroupIdsList.remove(groupId);

        updateRelatedGroups(nextGroupIdsList, callback);
    }

    /**
     * Update the related group ids list
     *
     * @param groupIds the new related groups
     * @param callback the asynchronous callback
     */
    public void updateRelatedGroups(final List<String> groupIds, final ApiCallback<Void> callback) {
        HashMap<String, Object> params = new HashMap<>();
        params.put("groups", groupIds);

        mDataHandler.getDataRetriever().getRoomsRestClient().sendStateEvent(getRoomId(), Event.EVENT_TYPE_STATE_RELATED_GROUPS, null, params, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                getLiveState().groups = groupIds;
                getDataHandler().getStore().storeLiveStateForRoom(getRoomId());

                if (null != callback) {
                    callback.onSuccess(null);
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
            List<RoomMember> members = new ArrayList<>(getState().getMembers());

            if (members.size() == 1) {
                res = members.get(0).getAvatarUrl();
            } else if (members.size() == 2) {
                RoomMember m1 = members.get(0);
                RoomMember m2 = members.get(1);

                res = TextUtils.equals(m1.getUserId(), mMyUserId) ? m2.getAvatarUrl() : m1.getAvatarUrl();
            }
        }

        return res;
    }

    /**
     * The call avatar is the same as the room avatar except there are only 2 JOINED members.
     * In this case, it returns the avtar of the other joined member.
     *
     * @return the call avatar URL.
     */
    public String getCallAvatarUrl() {
        String avatarURL;

        List<RoomMember> joinedMembers = new ArrayList<>(getJoinedMembers());

        // 2 joined members case
        if (2 == joinedMembers.size()) {
            // use other member avatar.
            if (TextUtils.equals(mMyUserId, joinedMembers.get(0).getUserId())) {
                avatarURL = joinedMembers.get(1).getAvatarUrl();
            } else {
                avatarURL = joinedMembers.get(0).getAvatarUrl();
            }
        } else {
            //
            avatarURL = getAvatarUrl();
        }

        return avatarURL;
    }

    /**
     * Update the room avatar URL.
     *
     * @param avatarUrl the new avatar URL
     * @param callback  the async callback
     */
    public void updateAvatarUrl(final String avatarUrl, final ApiCallback<Void> callback) {
        mDataHandler.getDataRetriever().getRoomsRestClient().updateAvatarUrl(getRoomId(), avatarUrl, new RoomInfoUpdateCallback<Void>(callback) {
            @Override
            public void onSuccess(Void info) {
                getState().url = avatarUrl;
                super.onSuccess(info);
            }
        });
    }

    /**
     * Update the room's history visibility
     *
     * @param historyVisibility the visibility (should be one of RoomState.HISTORY_VISIBILITY_XX values)
     * @param callback          the async callback
     */
    public void updateHistoryVisibility(final String historyVisibility, final ApiCallback<Void> callback) {
        mDataHandler.getDataRetriever().getRoomsRestClient().updateHistoryVisibility(getRoomId(), historyVisibility, new RoomInfoUpdateCallback<Void>(callback) {
            @Override
            public void onSuccess(Void info) {
                getState().history_visibility = historyVisibility;
                super.onSuccess(info);
            }
        });
    }

    /**
     * Update the directory's visibility
     *
     * @param visibility the visibility (should be one of RoomState.HISTORY_VISIBILITY_XX values)
     * @param callback   the async callback
     */
    public void updateDirectoryVisibility(final String visibility, final ApiCallback<Void> callback) {
        mDataHandler.getDataRetriever().getRoomsRestClient().updateDirectoryVisibility(getRoomId(), visibility, new RoomInfoUpdateCallback<Void>(callback) {
            @Override
            public void onSuccess(Void info) {
                getState().visibility = visibility;
                super.onSuccess(info);
            }
        });
    }

    /**
     * Get the directory visibility of the room (see {@link #updateDirectoryVisibility(String, ApiCallback)}).
     * The directory visibility indicates if the room is listed among the directory list.
     *
     * @param roomId   the user Id.
     * @param callback the callback returning the visibility response value.
     */
    public void getDirectoryVisibility(final String roomId, final ApiCallback<String> callback) {
        RoomsRestClient roomRestApi = mDataHandler.getDataRetriever().getRoomsRestClient();

        if (null != roomRestApi) {
            roomRestApi.getDirectoryVisibility(roomId, new ApiCallback<RoomState>() {
                @Override
                public void onSuccess(RoomState roomState) {
                    RoomState currentRoomState = getState();
                    if (null != currentRoomState) {
                        currentRoomState.visibility = roomState.visibility;
                    }

                    if (null != callback) {
                        callback.onSuccess(roomState.visibility);
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
    }

    /**
     * Update the join rule of the room.
     *
     * @param aRule         the join rule: {@link RoomState#JOIN_RULE_PUBLIC} or {@link RoomState#JOIN_RULE_INVITE}
     * @param aCallBackResp the async callback
     */
    public void updateJoinRules(final String aRule, final ApiCallback<Void> aCallBackResp) {
        mDataHandler.getDataRetriever().getRoomsRestClient().updateJoinRules(getRoomId(), aRule, new RoomInfoUpdateCallback<Void>(aCallBackResp) {
            @Override
            public void onSuccess(Void info) {
                getState().join_rule = aRule;
                super.onSuccess(info);
            }
        });
    }

    /**
     * Update the guest access rule of the room.
     * To deny guest access to the room, aGuestAccessRule must be set to {@link RoomState#GUEST_ACCESS_FORBIDDEN}.
     *
     * @param aGuestAccessRule the guest access rule: {@link RoomState#GUEST_ACCESS_CAN_JOIN} or {@link RoomState#GUEST_ACCESS_FORBIDDEN}
     * @param callback         the async callback
     */
    public void updateGuestAccess(final String aGuestAccessRule, final ApiCallback<Void> callback) {
        mDataHandler.getDataRetriever().getRoomsRestClient().updateGuestAccess(getRoomId(), aGuestAccessRule, new RoomInfoUpdateCallback<Void>(callback) {
            @Override
            public void onSuccess(Void info) {
                getState().guest_access = aGuestAccessRule;
                super.onSuccess(info);
            }
        });
    }

    //================================================================================
    // Read receipts events
    //================================================================================

    /**
     * @return the call conference user id
     */
    private String getCallConferenceUserId() {
        if (null == mCallConferenceUserId) {
            mCallConferenceUserId = MXCallsManager.getConferenceUserId(getRoomId());
        }

        return mCallConferenceUserId;
    }

    /**
     * Handle a receiptData.
     *
     * @param receiptData the receiptData.
     * @return true if there a store update.
     */
    public boolean handleReceiptData(ReceiptData receiptData) {
        if (!TextUtils.equals(receiptData.userId, getCallConferenceUserId()) && (null != getStore())) {
            boolean isUpdated = getStore().storeReceipt(receiptData, getRoomId());

            // check oneself receipts
            // if there is an update, it means that the messages have been read from another client
            // it requires to update the summary to display valid information.
            if (isUpdated && TextUtils.equals(mMyUserId, receiptData.userId)) {
                RoomSummary summary = getStore().getSummary(getRoomId());

                if (null != summary) {
                    summary.setReadReceiptEventId(receiptData.eventId);
                    getStore().flushSummary(summary);
                }

                refreshUnreadCounter();
            }

            return isUpdated;
        } else {
            return false;
        }
    }

    /**
     * Handle receipt event.
     *
     * @param event the event receipts.
     * @return the sender user IDs list.
     */
    private List<String> handleReceiptEvent(Event event) {
        List<String> senderIDs = new ArrayList<>();

        try {
            // the receipts dictionnaries
            // key   : $EventId
            // value : dict key $UserId
            //              value dict key ts
            //                    dict value ts value
            Type type = new TypeToken<HashMap<String, HashMap<String, HashMap<String, HashMap<String, Object>>>>>() {
            }.getType();
            HashMap<String, HashMap<String, HashMap<String, HashMap<String, Object>>>> receiptsDict = gson.fromJson(event.getContent(), type);

            for (String eventId : receiptsDict.keySet()) {
                HashMap<String, HashMap<String, HashMap<String, Object>>> receiptDict = receiptsDict.get(eventId);

                for (String receiptType : receiptDict.keySet()) {
                    // only the read receipts are managed
                    if (TextUtils.equals(receiptType, "m.read")) {
                        HashMap<String, HashMap<String, Object>> userIdsDict = receiptDict.get(receiptType);

                        for (String userID : userIdsDict.keySet()) {
                            HashMap<String, Object> paramsDict = userIdsDict.get(userID);

                            for (String paramName : paramsDict.keySet()) {
                                if (TextUtils.equals("ts", paramName)) {
                                    Double value = (Double) paramsDict.get(paramName);
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
            Log.e(LOG_TAG, "handleReceiptEvent : failed" + e.getMessage());
        }

        return senderIDs;
    }

    /**
     * Clear the unread message counters
     *
     * @param summary the room summary
     */
    private void clearUnreadCounters(RoomSummary summary) {
        Log.d(LOG_TAG, "## clearUnreadCounters " + getRoomId());

        // reset the notification count
        getLiveState().setHighlightCount(0);
        getLiveState().setNotificationCount(0);

        if (null != getStore()) {
            getStore().storeLiveStateForRoom(getRoomId());

            // flush the summary
            if (null != summary) {
                summary.setUnreadEventsCount(0);
                summary.setHighlightCount(0);
                summary.setNotificationCount(0);
                getStore().flushSummary(summary);
            }

            getStore().commit();
        }
    }

    /**
     * @return the read marker event id
     */
    public String getReadMarkerEventId() {
        if (null == getStore()) {
            return null;
        }

        RoomSummary summary = getStore().getSummary(getRoomId());

        if (null != summary) {
            return (null != summary.getReadMarkerEventId()) ? summary.getReadMarkerEventId() : summary.getReadReceiptEventId();
        } else {
            return null;
        }
    }

    /**
     * Mark all the messages as read.
     * It also move the read marker to the latest known messages
     *
     * @param aRespCallback the asynchronous callback
     * @return true if the request is sent, false otherwise
     */
    public boolean markAllAsRead(final ApiCallback<Void> aRespCallback) {
        return markAllAsRead(true, aRespCallback);
    }

    /**
     * Mark all the messages as read.
     * It also move the read marker to the latest known messages if updateReadMarker is set to true
     *
     * @param updateReadMarker true to move the read marker to the latest known event
     * @param aRespCallback    the asynchronous callback
     * @return true if the request is sent, false otherwise
     */
    private boolean markAllAsRead(boolean updateReadMarker, final ApiCallback<Void> aRespCallback) {
        final Event lastEvent = (null != getStore()) ? getStore().getLatestEvent(getRoomId()) : null;
        boolean res = sendReadMarkers(updateReadMarker ? ((null != lastEvent) ? lastEvent.eventId : null) : getReadMarkerEventId(), null, aRespCallback);

        if (!res) {
            RoomSummary summary = (null != getStore()) ? getStore().getSummary(getRoomId()) : null;

            if (null != summary) {
                if ((0 != summary.getUnreadEventsCount()) ||
                        (0 != summary.getHighlightCount()) ||
                        (0 != summary.getNotificationCount())) {
                    Log.e(LOG_TAG, "## markAllAsRead() : the summary events counters should be cleared for " + getRoomId() + " should have been cleared");

                    Event latestEvent = getStore().getLatestEvent(getRoomId());
                    summary.setLatestReceivedEvent(latestEvent);

                    if (null != latestEvent) {
                        summary.setReadReceiptEventId(latestEvent.eventId);
                    } else {
                        summary.setReadReceiptEventId(null);
                    }

                    summary.setUnreadEventsCount(0);
                    summary.setHighlightCount(0);
                    summary.setNotificationCount(0);
                    getStore().flushSummary(summary);
                }
            } else {
                Log.e(LOG_TAG, "## sendReadReceipt() : no summary for " + getRoomId());
            }

            if ((0 != getLiveState().getNotificationCount()) || (0 != getLiveState().getHighlightCount())) {
                Log.e(LOG_TAG, "## markAllAsRead() : the notification messages count for " + getRoomId() + " should have been cleared");

                getLiveState().setNotificationCount(0);
                getLiveState().setHighlightCount(0);

                if (null != getStore()) {
                    getStore().storeLiveStateForRoom(getRoomId());
                }
            }
        }

        return res;
    }

    /**
     * Update the read marker event Id
     *
     * @param readMarkerEventId the read marker even id
     */
    public void setReadMakerEventId(final String readMarkerEventId) {
        RoomSummary summary = (null != getStore()) ? getStore().getSummary(getRoomId()) : null;
        if (summary != null && !readMarkerEventId.equals(summary.getReadMarkerEventId())) {
            sendReadMarkers(readMarkerEventId, summary.getReadReceiptEventId(), null);
        }
    }

    /**
     * Send a read receipt to the latest known event
     */
    public void sendReadReceipt() {
        markAllAsRead(false, null);
    }

    /**
     * Send the read receipt to the latest room message id.
     *
     * @param event         send a read receipt to a provided event
     * @param aRespCallback asynchronous response callback
     * @return true if the read receipt has been sent, false otherwise
     */
    public boolean sendReadReceipt(Event event, final ApiCallback<Void> aRespCallback) {
        String eventId = (null != event) ? event.eventId : null;
        Log.d(LOG_TAG, "## sendReadReceipt() : eventId " + eventId + " in room " + getRoomId());
        return sendReadMarkers(null, eventId, aRespCallback);
    }

    /**
     * Forget the current read marker
     * This will update the read marker to match the read receipt
     *
     * @param callback the asynchronous callback
     */
    public void forgetReadMarker(final ApiCallback<Void> callback) {
        final RoomSummary summary = (null != getStore()) ? getStore().getSummary(getRoomId()) : null;
        final String currentReadReceipt = (null != summary) ? summary.getReadReceiptEventId() : null;

        if (null != summary) {
            Log.d(LOG_TAG, "## forgetReadMarker() : update the read marker to " + currentReadReceipt + " in room " + getRoomId());
            summary.setReadMarkerEventId(currentReadReceipt);
            getStore().flushSummary(summary);
        }

        setReadMarkers(currentReadReceipt, currentReadReceipt, callback);
    }

    /**
     * Send the read markers
     *
     * @param aReadMarkerEventId  the new read marker event id (if null use the latest known event id)
     * @param aReadReceiptEventId the new read receipt event id (if null use the latest known event id)
     * @param aRespCallback       asynchronous response callback
     * @return true if the request is sent, false otherwise
     */
    public boolean sendReadMarkers(final String aReadMarkerEventId, final String aReadReceiptEventId, final ApiCallback<Void> aRespCallback) {
        final Event lastEvent = (null != getStore()) ? getStore().getLatestEvent(getRoomId()) : null;

        // reported by GA
        if (null == lastEvent) {
            Log.e(LOG_TAG, "## sendReadMarkers(): no last event");
            return false;
        }

        Log.d(LOG_TAG, "## sendReadMarkers(): readMarkerEventId " + aReadMarkerEventId + " readReceiptEventId " + aReadReceiptEventId + " in room " + getRoomId());

        boolean hasUpdate = false;

        String readMarkerEventId = aReadMarkerEventId;
        if (!TextUtils.isEmpty(aReadMarkerEventId)) {
            if (!MXSession.isMessageId(aReadMarkerEventId)) {
                Log.e(LOG_TAG, "## sendReadMarkers() : invalid event id " + readMarkerEventId);
                // Read marker is invalid, ignore it
                readMarkerEventId = null;
            } else {
                // Check if the read marker is updated
                RoomSummary summary = getStore().getSummary(getRoomId());
                if ((null != summary) && !TextUtils.equals(readMarkerEventId, summary.getReadMarkerEventId())) {
                    // Make sure the new read marker event is newer than the current one
                    final Event newReadMarkerEvent = getStore().getEvent(readMarkerEventId, getRoomId());
                    final Event currentReadMarkerEvent = getStore().getEvent(summary.getReadMarkerEventId(), getRoomId());
                    if (newReadMarkerEvent == null || currentReadMarkerEvent == null
                            || newReadMarkerEvent.getOriginServerTs() > currentReadMarkerEvent.getOriginServerTs()) {
                        // Event is not in store (assume it is in the past), or is older than current one
                        Log.d(LOG_TAG, "## sendReadMarkers(): set new read marker event id " + readMarkerEventId + " in room " + getRoomId());
                        summary.setReadMarkerEventId(readMarkerEventId);
                        getStore().flushSummary(summary);
                        hasUpdate = true;
                    }
                }
            }
        }

        final String readReceiptEventId = (null == aReadReceiptEventId) ? lastEvent.eventId : aReadReceiptEventId;
        // check if the read receipt event id is already read
        if ((null != getStore()) && !getStore().isEventRead(getRoomId(), getDataHandler().getUserId(), readReceiptEventId)) {
            // check if the event id update is allowed
            if (handleReceiptData(new ReceiptData(mMyUserId, readReceiptEventId, System.currentTimeMillis()))) {
                // Clear the unread counters if the latest message is displayed
                // We don't try to compute the unread counters for oldest messages :
                // ---> it would require too much time.
                // The counters are cleared to avoid displaying invalid values
                // when the device is offline.
                // The read receipts will be sent later
                // (asap there is a valid network connection)
                if (TextUtils.equals(lastEvent.eventId, readReceiptEventId)) {
                    clearUnreadCounters(getStore().getSummary(getRoomId()));
                }
                hasUpdate = true;
            }
        }

        if (hasUpdate) {
            setReadMarkers(readMarkerEventId, readReceiptEventId, aRespCallback);
        }

        return hasUpdate;
    }

    /**
     * Send the request to update the read marker and read receipt.
     *
     * @param aReadMarkerEventId  the read marker event id
     * @param aReadReceiptEventId the read receipt event id
     * @param callback            the asynchronous callback
     */
    private void setReadMarkers(final String aReadMarkerEventId, final String aReadReceiptEventId, final ApiCallback<Void> callback) {
        Log.d(LOG_TAG, "## setReadMarkers(): readMarkerEventId " + aReadMarkerEventId + " readReceiptEventId " + aReadMarkerEventId);

        // check if the message ids are valid
        final String readMarkerEventId = MXSession.isMessageId(aReadMarkerEventId) ? aReadMarkerEventId : null;
        final String readReceiptEventId = MXSession.isMessageId(aReadReceiptEventId) ? aReadReceiptEventId : null;

        // if there is nothing to do
        if (TextUtils.isEmpty(readMarkerEventId) && TextUtils.isEmpty(readReceiptEventId)) {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    if (null != callback) {
                        callback.onSuccess(null);
                    }
                }
            });
        } else {
            mDataHandler.getDataRetriever().getRoomsRestClient().sendReadMarker(getRoomId(), readMarkerEventId, readReceiptEventId, new ApiCallback<Void>() {
                @Override
                public void onSuccess(Void info) {
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
    }

    /**
     * Check if an event has been read.
     *
     * @param eventId the event id
     * @return true if the message has been read
     */
    public boolean isEventRead(String eventId) {
        if (null != getStore()) {
            return getStore().isEventRead(getRoomId(), mMyUserId, eventId);
        } else {
            return false;
        }
    }

    //================================================================================
    // Unread event count management
    //================================================================================

    /**
     * @return the number of unread messages that match the push notification rules.
     */
    public int getNotificationCount() {
        return getState().getNotificationCount();
    }

    /**
     * @return the number of highlighted events.
     */
    public int getHighlightCount() {
        return getState().getHighlightCount();
    }

    /**
     * refresh the unread events counts.
     */
    public void refreshUnreadCounter() {
        // avoid refreshing the unread counter while processing a bunch of messages.
        if (!mIsSyncing) {
            RoomSummary summary = (null != getStore()) ? getStore().getSummary(getRoomId()) : null;

            if (null != summary) {
                int prevValue = summary.getUnreadEventsCount();
                int newValue = getStore().eventsCountAfter(getRoomId(), summary.getReadReceiptEventId());

                if (prevValue != newValue) {
                    summary.setUnreadEventsCount(newValue);
                    getStore().flushSummary(summary);
                }
            }
        } else {
            // wait the sync end before computing is again
            mRefreshUnreadAfterSync = true;
        }
    }

    //================================================================================
    // typing events
    //================================================================================

    // userIds list
    private List<String> mTypingUsers = new ArrayList<>();

    /**
     * Get typing users
     *
     * @return the userIds list
     */
    public List<String> getTypingUsers() {
        List<String> typingUsers;

        synchronized (Room.this) {
            typingUsers = (null == mTypingUsers) ? new ArrayList<String>() : new ArrayList<>(mTypingUsers);
        }

        return typingUsers;
    }

    /**
     * Send a typing notification
     *
     * @param isTyping typing status
     * @param timeout  the typing timeout
     * @param callback asynchronous callback
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
     *
     * @param context         the context
     * @param locationMessage the location message
     * @param thumbnailUri    the thumbnail uri
     * @param thumbMimeType   the thumbnail mime type
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

                thumbInfo.size = Long.valueOf(thumbnailFile.length());
                thumbInfo.mimetype = thumbMimeType;
                locationMessage.thumbnail_info = thumbInfo;
            } catch (Exception e) {
                Log.e(LOG_TAG, "fillLocationInfo : failed" + e.getMessage());
            }
        }
    }

    /**
     * Fills the VideoMessage info.
     *
     * @param context       Application context for the content resolver.
     * @param videoMessage  The VideoMessage to fill.
     * @param fileUri       The file uri.
     * @param videoMimeType The mimeType
     * @param thumbnailUri  the thumbnail uri
     * @param thumbMimeType the thumbnail mime type
     */
    public static void fillVideoInfo(Context context, VideoMessage videoMessage, Uri fileUri, String videoMimeType, Uri thumbnailUri, String thumbMimeType) {
        try {
            VideoInfo videoInfo = new VideoInfo();
            File file = new File(fileUri.getPath());

            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            retriever.setDataSource(file.getAbsolutePath());

            Bitmap bmp = retriever.getFrameAtTime();
            videoInfo.h = bmp.getHeight();
            videoInfo.w = bmp.getWidth();
            videoInfo.mimetype = videoMimeType;

            try {
                MediaPlayer mp = MediaPlayer.create(context, fileUri);
                if (null != mp) {
                    videoInfo.duration = Long.valueOf(mp.getDuration());
                    mp.release();
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "fillVideoInfo : MediaPlayer.create failed" + e.getMessage());
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

                thumbInfo.size = Long.valueOf(thumbnailFile.length());
                thumbInfo.mimetype = thumbMimeType;
                videoInfo.thumbnail_info = thumbInfo;
            }

            videoMessage.info = videoInfo;
        } catch (Exception e) {
            Log.e(LOG_TAG, "fillVideoInfo : failed" + e.getMessage());
        }
    }

    /**
     * Fills the fileMessage fileInfo.
     *
     * @param context     Application context for the content resolver.
     * @param fileMessage The fileMessage to fill.
     * @param fileUri     The file uri.
     * @param mimeType    The mimeType
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
            Log.e(LOG_TAG, "fillFileInfo : failed" + e.getMessage());
        }
    }


    /**
     * Update or create an ImageInfo for an image uri.
     *
     * @param context     Application context for the content resolver.
     * @param anImageInfo the imageInfo to fill, null to create a new one
     * @param imageUri    The full size image uri.
     * @param mimeType    The image mimeType
     * @return the filled image info
     */
    public static ImageInfo getImageInfo(Context context, ImageInfo anImageInfo, Uri imageUri, String mimeType) {
        ImageInfo imageInfo = (null == anImageInfo) ? new ImageInfo() : anImageInfo;

        try {
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

                if ((imageInfo.orientation == ExifInterface.ORIENTATION_TRANSPOSE) ||
                        (imageInfo.orientation == ExifInterface.ORIENTATION_ROTATE_90) ||
                        (imageInfo.orientation == ExifInterface.ORIENTATION_TRANSVERSE) ||
                        (imageInfo.orientation == ExifInterface.ORIENTATION_ROTATE_270)) {
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
                    Log.e(LOG_TAG, "fillImageInfo : failed" + e.getMessage());
                } catch (OutOfMemoryError oom) {
                    Log.e(LOG_TAG, "fillImageInfo : oom");
                }
            }

            // valid image size ?
            if ((0 != width) || (0 != height)) {
                imageInfo.w = width;
                imageInfo.h = height;
            }

            imageInfo.mimetype = mimeType;
            imageInfo.size = file.length();
        } catch (Exception e) {
            Log.e(LOG_TAG, "fillImageInfo : failed" + e.getMessage());
            imageInfo = null;
        }

        return imageInfo;
    }

    /**
     * Fills the imageMessage imageInfo.
     *
     * @param context      Application context for the content resolver.
     * @param imageMessage The imageMessage to fill.
     * @param imageUri     The full size image uri.
     * @param mimeType     The image mimeType
     */
    public static void fillImageInfo(Context context, ImageMessage imageMessage, Uri imageUri, String mimeType) {
        imageMessage.info = getImageInfo(context, imageMessage.info, imageUri, mimeType);
    }

    /**
     * Fills the imageMessage imageInfo.
     *
     * @param context      Application context for the content resolver.
     * @param imageMessage The imageMessage to fill.
     * @param thumbUri     The thumbnail uri
     * @param mimeType     The image mimeType
     */
    public static void fillThumbnailInfo(Context context, ImageMessage imageMessage, Uri thumbUri, String mimeType) {
        ImageInfo imageInfo = getImageInfo(context, null, thumbUri, mimeType);

        if (null != imageInfo) {
            if (null == imageMessage.info) {
                imageMessage.info = new ImageInfo();
            }

            imageMessage.info.thumbnailInfo = new ThumbnailInfo();
            imageMessage.info.thumbnailInfo.w = imageInfo.w;
            imageMessage.info.thumbnailInfo.h = imageInfo.h;
            imageMessage.info.thumbnailInfo.size = imageInfo.size;
            imageMessage.info.thumbnailInfo.mimetype = imageInfo.mimetype;
        }
    }

    //================================================================================
    // Call
    //================================================================================

    /**
     * Test if a call can be performed in this room.
     *
     * @return true if a call can be performed.
     */
    public boolean canPerformCall() {
        return getActiveMembers().size() > 1;
    }

    /**
     * @return a list of callable members.
     */
    public List<RoomMember> callees() {
        List<RoomMember> res = new ArrayList<>();

        Collection<RoomMember> members = getMembers();

        for (RoomMember m : members) {
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
     *
     * @param accountDataEvents the account events.
     */
    private void handleAccountDataEvents(List<Event> accountDataEvents) {
        if ((null != accountDataEvents) && (accountDataEvents.size() > 0)) {
            // manage the account events
            for (Event accountDataEvent : accountDataEvents) {
                String eventType = accountDataEvent.getType();

                if (eventType.equals(Event.EVENT_TYPE_READ_MARKER)) {
                    RoomSummary summary = (null != getStore()) ? getStore().getSummary(getRoomId()) : null;

                    if (null != summary) {
                        Event event = JsonUtils.toEvent(accountDataEvent.getContent());

                        if (null != event && !TextUtils.equals(event.eventId, summary.getReadMarkerEventId())) {
                            Log.d(LOG_TAG, "## handleAccountDataEvents() : update the read marker to " + event.eventId + " in room " + getRoomId());

                            if (TextUtils.isEmpty(event.eventId)) {
                                Log.e(LOG_TAG, "## handleAccountDataEvents() : null event id " + accountDataEvent.getContent());
                            }

                            summary.setReadMarkerEventId(event.eventId);

                            getStore().flushSummary(summary);
                            mDataHandler.onReadMarkerEvent(getRoomId());
                        }
                    }
                } else {
                    try {
                        mAccountData.handleTagEvent(accountDataEvent);

                        if (accountDataEvent.getType().equals(Event.EVENT_TYPE_TAGS)) {
                            mDataHandler.onRoomTagEvent(getRoomId());
                        }

                        if (accountDataEvent.getType().equals(Event.EVENT_TYPE_URL_PREVIEW)) {
                            JsonObject jsonObject = accountDataEvent.getContentAsJsonObject();

                            if (jsonObject.has(AccountDataRestClient.ACCOUNT_DATA_KEY_URL_PREVIEW_DISABLE)) {
                                boolean disabled = jsonObject.get(AccountDataRestClient.ACCOUNT_DATA_KEY_URL_PREVIEW_DISABLE).getAsBoolean();

                                Set<String> roomIdsWithoutURLPreview = mDataHandler.getStore().getRoomsWithoutURLPreviews();

                                if (disabled) {
                                    roomIdsWithoutURLPreview.add(getRoomId());
                                } else {
                                    roomIdsWithoutURLPreview.remove(getRoomId());
                                }

                                mDataHandler.getStore().setRoomsWithoutURLPreview(roomIdsWithoutURLPreview);
                            }
                        }

                    } catch (Exception e) {
                        Log.e(LOG_TAG, "## handleAccountDataEvents() : room " + getRoomId() + " failed " + e.getMessage());
                    }
                }
            }

            if (null != getStore()) {
                getStore().storeAccountData(getRoomId(), mAccountData);
            }
        }
    }

    /**
     * Add a tag to a room.
     * Use this method to update the order of an existing tag.
     *
     * @param tag      the new tag to add to the room.
     * @param order    the order.
     * @param callback the operation callback
     */
    private void addTag(String tag, Double order, final ApiCallback<Void> callback) {
        // sanity check
        if ((null != tag) && (null != order)) {
            mDataHandler.getDataRetriever().getRoomsRestClient().addTag(getRoomId(), tag, order, callback);
        } else {
            if (null != callback) {
                callback.onSuccess(null);
            }
        }
    }

    /**
     * Remove a tag to a room.
     *
     * @param tag      the new tag to add to the room.
     * @param callback the operation callback.
     */
    private void removeTag(String tag, final ApiCallback<Void> callback) {
        // sanity check
        if (null != tag) {
            mDataHandler.getDataRetriever().getRoomsRestClient().removeTag(getRoomId(), tag, callback);
        } else {
            if (null != callback) {
                callback.onSuccess(null);
            }
        }
    }

    /**
     * Remove a tag and add another one.
     *
     * @param oldTag      the tag to remove.
     * @param newTag      the new tag to add. Nil can be used. Then, no new tag will be added.
     * @param newTagOrder the order of the new tag.
     * @param callback    the operation callback.
     */
    public void replaceTag(final String oldTag, final String newTag, final Double newTagOrder, final ApiCallback<Void> callback) {
        // remove tag
        if ((null != oldTag) && (null == newTag)) {
            removeTag(oldTag, callback);
        }
        // define a tag or define a new order
        else if (((null == oldTag) && (null != newTag)) || TextUtils.equals(oldTag, newTag)) {
            addTag(newTag, newTagOrder, callback);
        } else {
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

    //==============================================================================================================
    // URL preview
    //==============================================================================================================

    /**
     * Tells if the URL preview has been allowed by the user.
     *
     * @return @return true if allowed.
     */
    public boolean isURLPreviewAllowedByUser() {
        return !getDataHandler().getStore().getRoomsWithoutURLPreviews().contains(getRoomId());
    }

    /**
     * Update the user enabled room url preview
     *
     * @param status   the new status
     * @param callback the asynchronous callback
     */
    public void setIsURLPreviewAllowedByUser(boolean status, ApiCallback<Void> callback) {
        mDataHandler.getDataRetriever().getRoomsRestClient().updateURLPreviewStatus(getRoomId(), status, callback);
    }

    //==============================================================================================================
    // Room events dispatcher
    //==============================================================================================================

    /**
     * Add an event listener to this room. Only events relative to the room will come down.
     *
     * @param eventListener the event listener to add
     */
    public void addEventListener(final IMXEventListener eventListener) {
        // sanity check
        if (null == eventListener) {
            Log.e(LOG_TAG, "addEventListener : eventListener is null");
            return;
        }

        // GA crash : should never happen but got it.
        if (null == mDataHandler) {
            Log.e(LOG_TAG, "addEventListener : mDataHandler is null");
            return;
        }

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
                    try {
                        eventListener.onLiveEvent(event, roomState);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "onLiveEvent exception " + e.getMessage());
                    }
                }
            }

            @Override
            public void onLiveEventsChunkProcessed(String fromToken, String toToken) {
                try {
                    eventListener.onLiveEventsChunkProcessed(fromToken, toToken);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "onLiveEventsChunkProcessed exception " + e.getMessage());
                }
            }

            @Override
            public void onEventSentStateUpdated(Event event) {
                // Filter out events for other rooms
                if (TextUtils.equals(getRoomId(), event.roomId)) {
                    try {
                        eventListener.onEventSentStateUpdated(event);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "onEventSentStateUpdated exception " + e.getMessage());
                    }
                }
            }

            @Override
            public void onEventDecrypted(Event event) {
                // Filter out events for other rooms
                if (TextUtils.equals(getRoomId(), event.roomId)) {
                    try {
                        eventListener.onEventDecrypted(event);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "onDecryptedEvent exception " + e.getMessage());
                    }
                }
            }

            @Override
            public void onEventSent(final Event event, final String prevEventId) {
                // Filter out events for other rooms
                if (TextUtils.equals(getRoomId(), event.roomId)) {
                    try {
                        eventListener.onEventSent(event, prevEventId);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "onEventSent exception " + e.getMessage());
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
            public void onNotificationCountUpdate(String roomId) {
                // Filter out events for other rooms
                if (TextUtils.equals(getRoomId(), roomId)) {
                    try {
                        eventListener.onNotificationCountUpdate(roomId);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "onNotificationCountUpdate exception " + e.getMessage());
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
            public void onReadMarkerEvent(String roomId) {
                // Filter out events for other rooms
                if (TextUtils.equals(getRoomId(), roomId)) {
                    try {
                        eventListener.onReadMarkerEvent(roomId);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "onReadMarkerEvent exception " + e.getMessage());
                    }
                }
            }

            @Override
            public void onRoomFlush(String roomId) {
                // Filter out events for other rooms
                if (TextUtils.equals(getRoomId(), roomId)) {
                    try {
                        eventListener.onRoomFlush(roomId);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "onRoomFlush exception " + e.getMessage());
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

            @Override
            public void onRoomKick(String roomId) {
                // Filter out events for other rooms
                if (TextUtils.equals(getRoomId(), roomId)) {
                    try {
                        eventListener.onRoomKick(roomId);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "onRoomKick exception " + e.getMessage());
                    }
                }
            }
        };

        mEventListeners.put(eventListener, globalListener);

        // GA crash
        if (null != mDataHandler) {
            mDataHandler.addListener(globalListener);
        }
    }

    /**
     * Remove an event listener.
     *
     * @param eventListener the event listener to remove
     */
    public void removeEventListener(IMXEventListener eventListener) {
        // sanity check
        if ((null != eventListener) && (null != mDataHandler)) {
            mDataHandler.removeListener(mEventListeners.get(eventListener));
            mEventListeners.remove(eventListener);
        }
    }

    //==============================================================================================================
    // Send methods
    //==============================================================================================================

    /**
     * Send an event content to the room.
     * The event is updated with the data provided by the server
     * The provided event contains the error description.
     *
     * @param event    the message
     * @param callback the callback with the created event
     */
    public void sendEvent(final Event event, final ApiCallback<Void> callback) {
        // wait that the room is synced before sending messages
        if (!mIsReady || !selfJoined()) {
            mDataHandler.updateEventState(event, Event.SentState.WAITING_RETRY);
            try {
                callback.onNetworkError(null);
            } catch (Exception e) {
                Log.e(LOG_TAG, "sendEvent exception " + e.getMessage());
            }
            return;
        }

        final String prevEventId = event.eventId;

        final ApiCallback<Event> localCB = new ApiCallback<Event>() {
            @Override
            public void onSuccess(final Event serverResponseEvent) {
                if (null != getStore()) {
                    // remove the tmp event
                    getStore().deleteEvent(event);
                }

                // replace the tmp event id by the final one
                boolean isReadMarkerUpdated = TextUtils.equals(getReadMarkerEventId(), event.eventId);

                // update the event with the server response
                event.eventId = serverResponseEvent.eventId;
                event.originServerTs = System.currentTimeMillis();
                mDataHandler.updateEventState(event, Event.SentState.SENT);

                // the message echo is not yet echoed
                if ((null != getStore()) && !getStore().doesEventExist(serverResponseEvent.eventId, getRoomId())) {
                    getStore().storeLiveRoomEvent(event);
                }

                // send the dedicated read receipt asap
                markAllAsRead(isReadMarkerUpdated, null);

                if (null != getStore()) {
                    getStore().commit();
                }
                mDataHandler.onEventSent(event, prevEventId);

                try {
                    callback.onSuccess(null);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "sendEvent exception " + e.getMessage());
                }
            }

            @Override
            public void onNetworkError(Exception e) {
                event.unsentException = e;
                mDataHandler.updateEventState(event, Event.SentState.UNDELIVERABLE);
                try {
                    callback.onNetworkError(e);
                } catch (Exception anException) {
                    Log.e(LOG_TAG, "sendEvent exception " + anException.getMessage());
                }
            }

            @Override
            public void onMatrixError(MatrixError e) {
                event.unsentMatrixError = e;
                mDataHandler.updateEventState(event, Event.SentState.UNDELIVERABLE);

                if (MatrixError.isConfigurationErrorCode(e.errcode)) {
                    mDataHandler.onConfigurationError(e.errcode);
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
                event.unsentException = e;
                mDataHandler.updateEventState(event, Event.SentState.UNDELIVERABLE);
                try {
                    callback.onUnexpectedError(e);
                } catch (Exception anException) {
                    Log.e(LOG_TAG, "sendEvent exception " + anException.getMessage());
                }
            }
        };

        if (isEncrypted() && (null != mDataHandler.getCrypto())) {
            mDataHandler.updateEventState(event, Event.SentState.ENCRYPTING);

            // Encrypt the content before sending
            mDataHandler.getCrypto().encryptEventContent(event.getContent().getAsJsonObject(), event.getType(), this, new ApiCallback<MXEncryptEventContentResult>() {
                @Override
                public void onSuccess(MXEncryptEventContentResult encryptEventContentResult) {
                    // update the event content with the encrypted data
                    event.type = encryptEventContentResult.mEventType;
                    event.updateContent(encryptEventContentResult.mEventContent.getAsJsonObject());
                    mDataHandler.decryptEvent(event, null);

                    // sending in progress
                    mDataHandler.updateEventState(event, Event.SentState.SENDING);
                    mDataHandler.getDataRetriever().getRoomsRestClient().sendEventToRoom(event.originServerTs + "", getRoomId(), encryptEventContentResult.mEventType, encryptEventContentResult.mEventContent.getAsJsonObject(), localCB);
                }

                @Override
                public void onNetworkError(Exception e) {
                    event.unsentException = e;
                    mDataHandler.updateEventState(event, Event.SentState.UNDELIVERABLE);

                    if (null != callback) {
                        callback.onNetworkError(e);
                    }
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    // update the sent state if the message encryption failed because there are unknown devices.
                    if ((e instanceof MXCryptoError) && TextUtils.equals(((MXCryptoError) e).errcode, MXCryptoError.UNKNOWN_DEVICES_CODE)) {
                        event.mSentState = Event.SentState.FAILED_UNKNOWN_DEVICES;
                    } else {
                        event.mSentState = Event.SentState.UNDELIVERABLE;
                    }
                    event.unsentMatrixError = e;
                    mDataHandler.onEventSentStateUpdated(event);

                    if (null != callback) {
                        callback.onMatrixError(e);
                    }
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    event.unsentException = e;
                    mDataHandler.updateEventState(event, Event.SentState.UNDELIVERABLE);

                    if (null != callback) {
                        callback.onUnexpectedError(e);
                    }
                }
            });
        } else {
            mDataHandler.updateEventState(event, Event.SentState.SENDING);

            if (Event.EVENT_TYPE_MESSAGE.equals(event.getType())) {
                mDataHandler.getDataRetriever().getRoomsRestClient().sendMessage(event.originServerTs + "", getRoomId(), JsonUtils.toMessage(event.getContent()), localCB);
            } else {
                mDataHandler.getDataRetriever().getRoomsRestClient().sendEventToRoom(event.originServerTs + "", getRoomId(), event.getType(), event.getContent().getAsJsonObject(), localCB);
            }
        }
    }

    /**
     * Cancel the event sending.
     * Any media upload will be cancelled too.
     * The event becomes undeliverable.
     *
     * @param event the message
     */
    public void cancelEventSending(final Event event) {
        if (null != event) {
            if ((Event.SentState.UNSENT == event.mSentState) ||
                    (Event.SentState.SENDING == event.mSentState) ||
                    (Event.SentState.WAITING_RETRY == event.mSentState) ||
                    (Event.SentState.ENCRYPTING == event.mSentState)) {

                // the message cannot be sent anymore
                mDataHandler.updateEventState(event, Event.SentState.UNDELIVERABLE);
            }

            List<String> urls = event.getMediaUrls();
            MXMediasCache cache = mDataHandler.getMediasCache();

            for (String url : urls) {
                cache.cancelUpload(url);
                cache.cancelDownload(cache.downloadIdFromUrl(url));
            }
        }
    }

    /**
     * Redact an event from the room.
     *
     * @param eventId  the event's id
     * @param callback the callback with the redacted event
     */
    public void redact(final String eventId, final ApiCallback<Event> callback) {
        mDataHandler.getDataRetriever().getRoomsRestClient().redactEvent(getRoomId(), eventId, new ApiCallback<Event>() {
            @Override
            public void onSuccess(Event event) {
                Event redactedEvent = (null != getStore()) ? getStore().getEvent(eventId, getRoomId()) : null;

                // test if the redacted event has been echoed
                // it it was not echoed, the event must be pruned to remove useless data
                // the room summary will be updated when the server will echo the redacted event
                if ((null != redactedEvent) && ((null == redactedEvent.unsigned) || (null == redactedEvent.unsigned.redacted_because))) {
                    redactedEvent.prune(null);
                    getStore().storeLiveRoomEvent(redactedEvent);
                    getStore().commit();
                }

                if (null != callback) {
                    callback.onSuccess(redactedEvent);
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
     * Redact an event from the room.
     *
     * @param eventId  the event's id
     * @param score    the score
     * @param reason   the redaction reason
     * @param callback the callback with the created event
     */
    public void report(String eventId, int score, String reason, ApiCallback<Void> callback) {
        mDataHandler.getDataRetriever().getRoomsRestClient().reportEvent(getRoomId(), eventId, score, reason, callback);
    }

    //================================================================================
    // Member actions
    //================================================================================

    /**
     * Invite an user to this room.
     *
     * @param userId   the user id
     * @param callback the callback for when done
     */
    public void invite(String userId, ApiCallback<Void> callback) {
        if (null != userId) {
            invite(Collections.singletonList(userId), callback);
        }
    }

    /**
     * Invite an user to a room based on their email address to this room.
     *
     * @param email    the email address
     * @param callback the callback for when done
     */
    public void inviteByEmail(String email, ApiCallback<Void> callback) {
        if (null != email) {
            invite(Collections.singletonList(email), callback);
        }
    }

    /**
     * Invite users to this room.
     * The identifiers are either ini Id or email address.
     *
     * @param identifiers the identifiers list
     * @param callback    the callback for when done
     */
    public void invite(List<String> identifiers, ApiCallback<Void> callback) {
        if (null != identifiers) {
            invite(identifiers.iterator(), callback);
        }
    }

    /**
     * Invite some users to this room.
     *
     * @param identifiers the identifiers iterator
     * @param callback    the callback for when done
     */
    private void invite(final Iterator<String> identifiers, final ApiCallback<Void> callback) {
        if (!identifiers.hasNext()) {
            callback.onSuccess(null);
            return;
        }

        final ApiCallback<Void> localCallback = new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                invite(identifiers, callback);
            }

            @Override
            public void onNetworkError(Exception e) {
                Log.e(LOG_TAG, "## invite failed " + e.getMessage());
                if (null != callback) {
                    callback.onNetworkError(e);
                }
            }

            @Override
            public void onMatrixError(MatrixError e) {
                Log.e(LOG_TAG, "## invite failed " + e.getMessage());
                if (null != callback) {
                    callback.onMatrixError(e);
                }
            }

            @Override
            public void onUnexpectedError(Exception e) {
                Log.e(LOG_TAG, "## invite failed " + e.getMessage());
                if (null != callback) {
                    callback.onUnexpectedError(e);
                }
            }
        };

        String identifier = identifiers.next();

        if (android.util.Patterns.EMAIL_ADDRESS.matcher(identifier).matches()) {
            mDataHandler.getDataRetriever().getRoomsRestClient().inviteByEmailToRoom(getRoomId(), identifier, localCallback);
        } else {
            mDataHandler.getDataRetriever().getRoomsRestClient().inviteUserToRoom(getRoomId(), identifier, localCallback);
        }
    }

    /**
     * Leave the room.
     *
     * @param callback the callback for when done
     */
    public void leave(final ApiCallback<Void> callback) {
        this.mIsLeaving = true;
        mDataHandler.onRoomInternalUpdate(getRoomId());

        mDataHandler.getDataRetriever().getRoomsRestClient().leaveRoom(getRoomId(), new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                if (mDataHandler.isAlive()) {
                    Room.this.mIsLeaving = false;

                    // delete references to the room
                    mDataHandler.deleteRoom(getRoomId());

                    if (null != getStore()) {
                        Log.d(LOG_TAG, "leave : commit");
                        getStore().commit();
                    }

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
                // the room was not anymore defined server side
                // race condition ?
                if (e.mStatus == 404) {
                    onSuccess(null);
                } else {
                    Room.this.mIsLeaving = false;

                    try {
                        callback.onMatrixError(e);
                    } catch (Exception anException) {
                        Log.e(LOG_TAG, "leave exception " + anException.getMessage());
                    }

                    mDataHandler.onRoomInternalUpdate(getRoomId());
                }
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
     * Forget the room.
     *
     * @param callback the callback for when done
     */
    public void forget(final ApiCallback<Void> callback) {
        mDataHandler.getDataRetriever().getRoomsRestClient().forgetRoom(getRoomId(), new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                if (mDataHandler.isAlive()) {
                    // don't call onSuccess.deleteRoom because it moves an existing room to historical store
                    IMXStore store = mDataHandler.getStore(getRoomId());

                    if (null != store) {
                        store.deleteRoom(getRoomId());
                        store.commit();
                    }

                    try {
                        callback.onSuccess(info);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "forget exception " + e.getMessage());
                    }
                }
            }

            @Override
            public void onNetworkError(Exception e) {
                try {
                    callback.onNetworkError(e);
                } catch (Exception anException) {
                    Log.e(LOG_TAG, "forget exception " + anException.getMessage());
                }
            }

            @Override
            public void onMatrixError(MatrixError e) {
                try {
                    callback.onMatrixError(e);
                } catch (Exception anException) {
                    Log.e(LOG_TAG, "forget exception " + anException.getMessage());
                }
            }

            @Override
            public void onUnexpectedError(Exception e) {
                try {
                    callback.onUnexpectedError(e);
                } catch (Exception anException) {
                    Log.e(LOG_TAG, "forget exception " + anException.getMessage());
                }
            }
        });
    }


    /**
     * Kick a user from the room.
     *
     * @param userId   the user id
     * @param callback the async callback
     */
    public void kick(String userId, ApiCallback<Void> callback) {
        mDataHandler.getDataRetriever().getRoomsRestClient().kickFromRoom(getRoomId(), userId, callback);
    }

    /**
     * Ban a user from the room.
     *
     * @param userId   the user id
     * @param reason   ban reason
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
     *
     * @param userId   the user id
     * @param callback the async callback
     */
    public void unban(String userId, ApiCallback<Void> callback) {
        BannedUser user = new BannedUser();
        user.userId = userId;

        mDataHandler.getDataRetriever().getRoomsRestClient().unbanFromRoom(getRoomId(), user, callback);
    }

    //================================================================================
    // Encryption
    //================================================================================

    private ApiCallback<Void> mRoomEncryptionCallback;

    private final MXEventListener mEncryptionListener = new MXEventListener() {
        @Override
        public void onLiveEvent(Event event, RoomState roomState) {
            if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE_ENCRYPTION)) {
                if (null != mRoomEncryptionCallback) {
                    mRoomEncryptionCallback.onSuccess(null);
                    mRoomEncryptionCallback = null;
                }
            }
        }
    };

    /**
     * @return if the room content is encrypted
     */
    public boolean isEncrypted() {
        return getLiveState().isEncrypted();
    }

    /**
     * Enable the encryption.
     *
     * @param algorithm the used algorithm
     * @param callback  the asynchronous callback
     */
    public void enableEncryptionWithAlgorithm(final String algorithm, final ApiCallback<Void> callback) {
        // ensure that the crypto has been update
        if (null != mDataHandler.getCrypto() && !TextUtils.isEmpty(algorithm)) {
            HashMap<String, Object> params = new HashMap<>();
            params.put("algorithm", algorithm);

            if (null != callback) {
                mRoomEncryptionCallback = callback;
                addEventListener(mEncryptionListener);
            }

            mDataHandler.getDataRetriever().getRoomsRestClient().sendStateEvent(getRoomId(), Event.EVENT_TYPE_MESSAGE_ENCRYPTION, null, params, new ApiCallback<Void>() {
                @Override
                public void onSuccess(Void info) {
                    // Wait for the event coming back from the hs
                }

                @Override
                public void onNetworkError(Exception e) {
                    if (null != callback) {
                        callback.onNetworkError(e);
                        removeEventListener(mEncryptionListener);
                    }
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    if (null != callback) {
                        callback.onMatrixError(e);
                        removeEventListener(mEncryptionListener);
                    }
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    if (null != callback) {
                        callback.onUnexpectedError(e);
                        removeEventListener(mEncryptionListener);
                    }
                }
            });
        } else if (null != callback) {
            if (null == mDataHandler.getCrypto()) {
                callback.onMatrixError(new MXCryptoError(MXCryptoError.ENCRYPTING_NOT_ENABLED_ERROR_CODE, MXCryptoError.ENCRYPTING_NOT_ENABLED_REASON, MXCryptoError.ENCRYPTING_NOT_ENABLED_REASON));
            } else {
                callback.onMatrixError(new MXCryptoError(MXCryptoError.MISSING_FIELDS_ERROR_CODE, MXCryptoError.UNABLE_TO_ENCRYPT, MXCryptoError.MISSING_FIELDS_REASON));
            }
        }
    }

    //==============================================================================================================
    // Room events helper
    //==============================================================================================================

    private RoomMediaMessagesSender mRoomMediaMessagesSender;

    /**
     * Init the mRoomMediaMessagesSender instance
     */
    private void initRoomMediaMessagesSender() {
        if (null == mRoomMediaMessagesSender) {
            mRoomMediaMessagesSender = new RoomMediaMessagesSender(getStore().getContext(), mDataHandler, this);
        }
    }

    /**
     * Send a text message asynchronously.
     *
     * @param text              the unformatted text
     * @param HTMLFormattedText the HTML formatted text
     * @param format            the formatted text format
     * @param listener          the event creation listener
     */
    public void sendTextMessage(String text, String HTMLFormattedText, String format, RoomMediaMessage.EventCreationListener listener) {
        sendTextMessage(text, HTMLFormattedText, format, Message.MSGTYPE_TEXT, listener);
    }

    /**
     * Send an emote message asynchronously.
     *
     * @param text              the unformatted text
     * @param HTMLFormattedText the HTML formatted text
     * @param format            the formatted text format
     * @param listener          the event creation listener
     */
    public void sendEmoteMessage(String text, String HTMLFormattedText, String format, final RoomMediaMessage.EventCreationListener listener) {
        sendTextMessage(text, HTMLFormattedText, format, Message.MSGTYPE_EMOTE, listener);
    }

    /**
     * Send a text message asynchronously.
     *
     * @param text              the unformatted text
     * @param HTMLFormattedText the HTML formatted text
     * @param format            the formatted text format
     * @param msgType           the message type
     * @param listener          the event creation listener
     */
    private void sendTextMessage(String text, String HTMLFormattedText, String format, String msgType, final RoomMediaMessage.EventCreationListener listener) {
        initRoomMediaMessagesSender();

        RoomMediaMessage roomMediaMessage = new RoomMediaMessage(text, HTMLFormattedText, format);
        roomMediaMessage.setMessageType(msgType);
        roomMediaMessage.setEventCreationListener(listener);

        mRoomMediaMessagesSender.send(roomMediaMessage);
    }

    /**
     * Send an media message asynchronously.
     *
     * @param roomMediaMessage   the media message to send.
     * @param maxThumbnailWidth  the max thumbnail width
     * @param maxThumbnailHeight the max thumbnail height
     * @param listener           the event creation listener
     */
    public void sendMediaMessage(final RoomMediaMessage roomMediaMessage, final int maxThumbnailWidth, final int maxThumbnailHeight, final RoomMediaMessage.EventCreationListener listener) {
        initRoomMediaMessagesSender();

        roomMediaMessage.setThumnailSize(new Pair<>(maxThumbnailWidth, maxThumbnailHeight));
        roomMediaMessage.setEventCreationListener(listener);

        mRoomMediaMessagesSender.send(roomMediaMessage);
    }

    /**
     * Send a sticker message.
     *
     * @param event
     * @param listener
     */
    public void sendStickerMessage(Event event, final RoomMediaMessage.EventCreationListener listener) {
        initRoomMediaMessagesSender();

        RoomMediaMessage roomMediaMessage = new RoomMediaMessage(event);
        roomMediaMessage.setMessageType(Event.EVENT_TYPE_STICKER);
        roomMediaMessage.setEventCreationListener(listener);

        mRoomMediaMessagesSender.send(roomMediaMessage);
    }

    //==============================================================================================================
    // Unsent events management
    //==============================================================================================================

    /**
     * Provides the unsent messages list.
     *
     * @return the unsent events list
     */
    public List<Event> getUnsentEvents() {
        List<Event> unsent = new ArrayList<>();

        if (null != getStore()) {
            List<Event> undeliverableEvents = getStore().getUndeliverableEvents(getRoomId());
            List<Event> unknownDeviceEvents = getStore().getUnknownDeviceEvents(getRoomId());

            if (null != undeliverableEvents) {
                unsent.addAll(undeliverableEvents);
            }

            if (null != unknownDeviceEvents) {
                unsent.addAll(unknownDeviceEvents);
            }
        }

        return unsent;
    }

    /**
     * Delete an events list.
     *
     * @param events the events list
     */
    public void deleteEvents(List<Event> events) {
        if ((null != getStore()) && (null != events) && events.size() > 0) {
            // reset the timestamp
            for (Event event : events) {
                getStore().deleteEvent(event);
            }

            // update the summary
            Event latestEvent = getStore().getLatestEvent(getRoomId());

            // if there is an oldest event, use it to set a summary
            if (latestEvent != null) {
                if (RoomSummary.isSupportedEvent(latestEvent)) {
                    RoomSummary summary = getStore().getSummary(getRoomId());

                    if (null != summary) {
                        summary.setLatestReceivedEvent(latestEvent, getState());
                    } else {
                        summary = new RoomSummary(null, latestEvent, getState(), mDataHandler.getUserId());
                    }

                    getStore().storeSummary(summary);
                }
            }

            getStore().commit();
        }
    }

    /**
     * Tell if room is Direct Chat
     *
     * @return true if is direct chat
     */
    public boolean isDirect() {
        return mDataHandler.getDirectChatRoomIdsList().contains(getRoomId());
    }
}
