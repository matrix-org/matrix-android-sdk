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
import android.os.AsyncTask;
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
import org.matrix.androidsdk.rest.client.RoomsRestClient;
import org.matrix.androidsdk.rest.client.UrlPostTask;
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
import java.util.concurrent.RejectedExecutionException;

/**
 * Class representing a room and the interactions we have with it.
 */
public class Room {

    private static final String LOG_TAG = "Room";

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

    // gson parszer
    private final Gson gson = new GsonBuilder().create();

    // This is used to block live events and history requests until the state is fully processed and ready
    private boolean mIsReady = false;

    /**
     * Default room creator
     */
    public Room() {
        mLiveTimeline = new EventTimeline(this, true);
    }

    /**
     * Init the room fields.
     * @param roomId the room id
     * @param dataHandler the data handler
     */
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
     * @return the used datahandler
     */
    public MXDataHandler getDataHandler() {
        return mDataHandler;
    }

    //================================================================================
    // Sync events
    //================================================================================

    /**
     * Manage list of ephemeral events
     * @param events the ephemeral events
     */
    private void handleEphemeralEvents(List<Event> events) {
        for (Event event : events) {

            // ensure that the room Id is defined
            event.roomId = getRoomId();

            try {
                if (Event.EVENT_TYPE_RECEIPT.equals(event.type)) {
                    if (event.roomId != null) {
                        List<String> senders = handleReceiptEvent(event);

                        if ((null != senders) && (senders.size() > 0)) {
                            mDataHandler.onReceiptEvent(event.roomId, senders);
                        }
                    }
                } else if (Event.EVENT_TYPE_TYPING.equals(event.type)) {
                    mDataHandler.onLiveEvent(event, getState());
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "ephemeral event failed " + e.getLocalizedMessage());
            }
        }
    }

    /**
     * Handle the events of a joined room.
     * @param roomSync the sync events list.
     * @param isInitialSync true if the room is initialized by a global initial sync.
     */
    public void handleJoinedRoomSync(RoomSync roomSync, boolean isInitialSync) {
        if (null != mOnInitialSyncCallback) {
            Log.d(LOG_TAG, "initial sync handleJoinedRoomSync " + getRoomId());
        } else {
            Log.d(LOG_TAG, "handleJoinedRoomSync " + getRoomId());
        }

        mIsSyncing = true;

        synchronized (this) {
            mLiveTimeline.handleJoinedRoomSync(roomSync, isInitialSync);

            // ephemeral events
            if ((null != roomSync.ephemeral) && (null != roomSync.ephemeral.events)) {
                handleEphemeralEvents(roomSync.ephemeral.events);
            }

            // Handle account data events (if any)
            if (null != roomSync.accountData) {
                handleAccountDataEvents(roomSync.accountData.events);
            }
        }

        // the user joined the room
        // With V2 sync, the server sends the events to init the room.
        if (null != mOnInitialSyncCallback) {
            try {
                Log.d(LOG_TAG, "handleJoinedRoomSync " + getRoomId() + " :  the initial sync is done");

                mOnInitialSyncCallback.onSuccess(null);
            } catch (Exception e) {
                Log.e(LOG_TAG, "handleJoinedRoomSync : onSuccess failed" + e.getLocalizedMessage());
            }
            mOnInitialSyncCallback = null;
        }

        mIsSyncing = false;

        if (mRefreshUnreadAfterSync) {
            refreshUnreadCounter();
            mRefreshUnreadAfterSync = false;
        }
    }

    /**
     * Handle the invitation room events
     * @param invitedRoomSync the invitation room events.
     */
    public void handleInvitedRoomSync(InvitedRoomSync invitedRoomSync) {
        mLiveTimeline.handleInvitedRoomSync(invitedRoomSync);
    }

    /**
     * Store an outgoing event.
     * @param event the event.
     */
    public void storeOutgoingEvent(Event event) {
        mLiveTimeline.storeOutgoingEvent(event);
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

    public RoomState getBackState() {
        return mLiveTimeline.getBackState();
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
     * @return the list of online members in a room.
     */
    public Collection<RoomMember> getOnlineMembers() {
        Collection<RoomMember> members = getState().getMembers();
        ArrayList<RoomMember> activeMembers = new ArrayList<>();

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
        ArrayList<RoomMember> activeMembers = new ArrayList<>();

        for(RoomMember member : members) {
            if (TextUtils.equals(member.membership, RoomMember.MEMBERSHIP_JOIN) ||TextUtils.equals(member.membership, RoomMember.MEMBERSHIP_INVITE)) {
                activeMembers.add(member);
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
        ArrayList<RoomMember> joinedMembersList = new ArrayList<>();

        for(RoomMember member : membersList) {
            if (TextUtils.equals(member.membership, RoomMember.MEMBERSHIP_JOIN)) {
                joinedMembersList.add(member);
            }
        }

        return joinedMembersList;
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

    //================================================================================
    // Join
    //================================================================================

    /**
     * Defines the initial sync callback
     * @param callback the new callback.
     */
    public void setOnInitialSyncCallback(ApiCallback<Void> callback) {
        mOnInitialSyncCallback = callback;
    }

    /**
     * Join a room with an url to post before joined the room.
     * @param alias the room alias
     * @param thirdPartySignedUrl the thirdPartySigned url
     * @param callback the callback
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
                        map = new Gson().fromJson(object, new TypeToken<HashMap<String, Object>>() {}.getType());
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "joinWithThirdPartySigned :  Gson().fromJson failed" + e.getLocalizedMessage());
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
            } catch (RejectedExecutionException rejectedExecutionException) {
                Log.e(LOG_TAG, "joinWithThirdPartySigned : task.executeOnExecutor failed" + rejectedExecutionException.getLocalizedMessage());
            } catch (Exception e) {
                Log.e(LOG_TAG, "joinWithThirdPartySigned : task.executeOnExecutor failed" + e.getLocalizedMessage());
            }
        }
    }

    /**
     * Join the room. If successful, the room's current state will be loaded before calling back onComplete.
     * @param callback the callback for when done
     */
    public void join(final ApiCallback<Void> callback) {
        join(null, null, callback);
    }

    /**
     * Join the room. If successful, the room's current state will be loaded before calling back onComplete.
     * @param roomAlias the room alias
     * @param callback the callback for when done
     */
    public void join(String roomAlias, ApiCallback<Void> callback) {
        join(roomAlias, null, callback);
    }

    /**
     * Join the room. If successful, the room's current state will be loaded before calling back onComplete.
     * @param extraParams the join extra params
     * @param callback the callback for when done
     */
    public void join(HashMap<String, Object> extraParams, final ApiCallback<Void> callback) {
        join(null, extraParams, callback);
    }

    /**
     * Join the room. If successful, the room's current state will be loaded before calling back onComplete.
     * @param roomAlias the room alias
     * @param extraParams the join extra params
     * @param callback the callback for when done
     */
    public void join(String roomAlias, HashMap<String, Object> extraParams, final ApiCallback<Void> callback) {
        Log.d(LOG_TAG, "Join the room " + getRoomId() + " with alias " + roomAlias);

        // disable the room alias management until the server manages the rooms federation properly
        // cf https://github.com/matrix-org/synapse/issues/816
        mDataHandler.getDataRetriever().getRoomsRestClient().joinRoom(/*(null != roomAlias) ? roomAlias :*/ getRoomId(), extraParams, new SimpleApiCallback<RoomResponse>(callback) {
            @Override
            public void onSuccess(final RoomResponse aResponse) {
                try {
                    boolean isRoomMember;

                    synchronized (this) {
                        isRoomMember = (getState().getMember(mMyUserId) != null);
                    }

                    // the join request did not get the room initial history
                    if (!isRoomMember) {
                        Log.d(LOG_TAG, "the room " + getRoomId() + " is joined but wait after initial sync");

                        // wait the server sends the events chunk before calling the callback
                        setOnInitialSyncCallback(callback);
                    } else {
                        Log.d(LOG_TAG, "the room " + getRoomId() + " is joined : the initial sync has been done");
                        // already got the initial sync
                        callback.onSuccess(null);
                    }
                } catch (Exception e) {
                    Log.e(LOG_TAG, "join exception " + e.getMessage());
                }
            }

            @Override
            public void onNetworkError(Exception e) {
                Log.e(LOG_TAG, "join onNetworkError " + e.getLocalizedMessage());
                callback.onNetworkError(e);
            }

            @Override
            public void onMatrixError(MatrixError e) {
                Log.e(LOG_TAG, "join onMatrixError " + e.getLocalizedMessage());
                callback.onMatrixError(e);
            }

            @Override
            public void onUnexpectedError(Exception e) {
                Log.e(LOG_TAG, "join onUnexpectedError " + e.getLocalizedMessage());
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
     * Update the room's history visibility
     * @param historyVisibility the visibility (should be one of RoomState.HISTORY_VISIBILITY_XX values)
     * @param callback the async callback
     */
    public void updateHistoryVisibility(final String historyVisibility, final ApiCallback<Void> callback) {
        mDataHandler.getDataRetriever().getRoomsRestClient().updateHistoryVisibility(getRoomId(), historyVisibility, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                getState().history_visibility = historyVisibility;
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
    public void updateDirectoryVisibility(final String visibility, final ApiCallback<Void> callback) {
        mDataHandler.getDataRetriever().getRoomsRestClient().updateDirectoryVisibility(getRoomId(), visibility, new ApiCallback<Void>() {
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

    /**
     * Get the directory visibility of the room (see {@link #updateDirectoryVisibility(String, ApiCallback)}).
     * The directory visibility indicates if the room is listed among the directory list.
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
     * @param aRule the join rule: {@link RoomState#JOIN_RULE_PUBLIC} or {@link RoomState#JOIN_RULE_INVITE}
     * @param aCallBackResp the async callback
     */
    public void updateJoinRules(final String aRule, final ApiCallback<Void> aCallBackResp) {
        mDataHandler.getDataRetriever().getRoomsRestClient().updateJoinRules(getRoomId(), aRule, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                getState().join_rule = aRule;
                mStore.storeLiveStateForRoom(getRoomId());

                if (null != aCallBackResp) {
                    aCallBackResp.onSuccess(info);
                }
            }

            @Override
            public void onNetworkError(Exception e) {
                if (null != aCallBackResp) {
                    aCallBackResp.onNetworkError(e);
                }
            }

            @Override
            public void onMatrixError(MatrixError e) {
                if (null != aCallBackResp) {
                    aCallBackResp.onMatrixError(e);
                }
            }

            @Override
            public void onUnexpectedError(Exception e) {
                if (null != aCallBackResp) {
                    aCallBackResp.onUnexpectedError(e);
                }
            }
        });
    }

    /**
     * Update the guest access rule of the room.
     * To deny guest access to the room, aGuestAccessRule must be set to {@link RoomState#GUEST_ACCESS_FORBIDDEN}.
     * @param aGuestAccessRule the guest access rule: {@link RoomState#GUEST_ACCESS_CAN_JOIN} or {@link RoomState#GUEST_ACCESS_FORBIDDEN}
     * @param callback the async callback
     */
    public void updateGuestAccess(final String aGuestAccessRule, final ApiCallback<Void> callback) {
        mDataHandler.getDataRetriever().getRoomsRestClient().updateGuestAccess(getRoomId(), aGuestAccessRule, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                getState().guest_access = aGuestAccessRule;
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
    public boolean handleReceiptData(ReceiptData receiptData) {
        boolean isUpdated = mStore.storeReceipt(receiptData, getRoomId());

        // check oneself receipts
        // if there is an update, it means that the messages have been read from another client
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
        ArrayList<String> senderIDs = new ArrayList<>();

        try {
            // the receipts dictionnaries
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
            Log.e(LOG_TAG, "handleReceiptEvent : failed" + e.getLocalizedMessage());
        }

        return senderIDs;
    }

    /**
     * Clear the unread message counters
     * @param summary the room summary
     */
    private void clearUnreadCounters(RoomSummary summary) {
        // reset the notification count
        getLiveState().setHighlightCount(0);
        getLiveState().setNotificationCount(0);
        mStore.storeLiveStateForRoom(getRoomId());

        // flush the summary
        summary.setUnreadEventsCount(0);
        mStore.flushSummary(summary);

        mStore.commit();
    }

    /**
     * Send the read receipt to the latest room message id.
     * @param aRespCallback asynchronous response callback
     * @return true if the read receipt has been sent, false otherwise
     */
    public boolean sendReadReceipt(final ApiCallback<Void> aRespCallback) {
        final RoomSummary summary = mStore.getSummary(getRoomId());
        final Event event = mStore.getLatestEvent(getRoomId());
        boolean isSendReadReceiptSent = false;
        Log.d(LOG_TAG,"## sendReadReceipt(): roomId="+getRoomId());

        if ((null != event) && (null != summary)) {
            // any update
            if (!TextUtils.equals(summary.getReadReceiptToken(), event.eventId)) {
                Log.d(LOG_TAG,"## sendReadReceipt(): send a read receipt to " + event.eventId);

                isSendReadReceiptSent = true;
                mDataHandler.getDataRetriever().getRoomsRestClient().sendReadReceipt(getRoomId(), event.eventId, new ApiCallback<Void>() {
                    @Override
                    public void onSuccess(Void info) {
                        Log.d(LOG_TAG,"## sendReadReceipt(): succeeds");

                        // save the up to date status only if the operation succeeds
                        setReadReceiptToken(event.eventId, System.currentTimeMillis());

                        if(null != aRespCallback) {
                            aRespCallback.onSuccess(info);
                        }
                    }

                    @Override
                    public void onNetworkError(Exception e) {
                        Log.e(LOG_TAG, "sendReadReceipt failed " + e.getLocalizedMessage());

                        if(null != aRespCallback) {
                            aRespCallback.onNetworkError(e);
                        }
                    }

                    @Override
                    public void onMatrixError(MatrixError e) {
                        Log.e(LOG_TAG, "sendReadReceipt failed " + e.getLocalizedMessage());

                        if(null != aRespCallback) {
                            aRespCallback.onMatrixError(e);
                        }
                    }

                    @Override
                    public void onUnexpectedError(Exception e) {
                        Log.e(LOG_TAG, "sendReadReceipt failed " + e.getLocalizedMessage());

                        if(null != aRespCallback) {
                            aRespCallback.onUnexpectedError(e);
                        }
                    }
                });
                // offline management
                // clear counters until the network comes back.
                // the user does not need to know that there is a task in progress
                clearUnreadCounters(summary);
            } else {
                Log.d(LOG_TAG,"## sendReadReceipt(): already up to date");
                clearUnreadCounters(summary);
            }
        } else {
            Log.d(LOG_TAG,"## sendReadReceipt(): invalid params event " + event + " summary " + summary);
        }

        return isSendReadReceiptSent;
    }

    /**
     * Update the read receipt token.
     * @param token the new token
     * @param ts the token ts
     * @return true if the token is refreshed
     */
    public boolean setReadReceiptToken(String token, long ts) {
        RoomSummary summary = mStore.getSummary(getRoomId());

        Log.d(LOG_TAG, "setReadReceiptToken " + token + " - " +ts);

        if (summary.setReadReceiptToken(token, ts)) {
            Log.d(LOG_TAG, "setReadReceiptToken : update the summary");
            clearUnreadCounters(summary);
            return true;
        } else {
            Log.d(LOG_TAG, "setReadReceiptToken : not the latest message " + summary.getReadReceiptToken() + " - " + summary.getReadReceiptTs());
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
        return getState().getNotificationCount();
    }

    /**
     * @return the number of highlighted events.
     */
    public int getHighlightCount() {
        return getState().getHighlightCount();
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
        } else {
            // wait the sync end before computing is again
            mRefreshUnreadAfterSync = true;
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

    // userIds list
    private ArrayList<String>mTypingUsers = new ArrayList<>();

    /**
     * Get typing users
     * @return the userIds list
     */
    public ArrayList<String> getTypingUsers() {

        ArrayList<String> typingUsers;

        synchronized (Room.this) {
            typingUsers = (null == mTypingUsers) ? new ArrayList<String>() : new ArrayList<>(mTypingUsers);
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

                thumbInfo.size = Long.valueOf(thumbnailFile.length());
                thumbInfo.mimetype = thumbMimeType;
                locationMessage.thumbnail_info = thumbInfo;
            } catch (Exception e) {
                Log.e(LOG_TAG, "fillLocationInfo : failed" + e.getLocalizedMessage());
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
                Log.e(LOG_TAG, "fillVideoInfo : MediaPlayer.create failed" + e.getLocalizedMessage());
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
            Log.e(LOG_TAG, "fillVideoInfo : failed" + e.getLocalizedMessage());
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
            Log.e(LOG_TAG, "fillFileInfo : failed" + e.getLocalizedMessage());
        }
    }

    /**
     * Fills the imageMessage imageInfo.
     * @param context Application context for the content resolver.
     * @param imageMessage The imageMessage to fill.
     * @param imageUri The full size image uri.
     * @param mimeType The image mimeType
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
                    Log.e(LOG_TAG, "fillImageInfo : failed" + e.getLocalizedMessage());
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
            Log.e(LOG_TAG, "fillImageInfo : failed" + e.getLocalizedMessage());
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

        ArrayList<Event> eventsList = new ArrayList<>(events);
        ArrayList<Event> unsentEvents = new ArrayList<>();

        // check if some events are already sending
        // to avoid send them twice
        // some network issues could happen
        // eg connected send some unsent messages but do not send all of them
        // disconnected -> connected : some messages could be sent twice
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
    public boolean canPerformCall() {
        return 1 == callees().size();
    }

    /**
     * @return a list of callable members.
     */
    public ArrayList<RoomMember> callees() {
        ArrayList<RoomMember> res = new ArrayList<>();

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
                    mDataHandler.onRoomTagEvent(getRoomId());
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

    //==============================================================================================================
    // Room events dispatcher
    //==============================================================================================================

    /**
     * Add an event listener to this room. Only events relative to the room will come down.
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
                                    mTypingUsers = new ArrayList<>();
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

        // GA crash
        if (null != mDataHandler) {
            mDataHandler.addListener(globalListener);
        }
    }

    /**
     * Remove an event listener.
     * @param eventListener the event listener to remove
     */
    public void removeEventListener(IMXEventListener eventListener) {
        // sanity check
        if ((null != eventListener) && (null != mDataHandler) && (null != mEventListeners)) {
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
                sendReadReceipt(null);

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

                if (TextUtils.equals(MatrixError.UNKNOWN_TOKEN, e.errcode)) {
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
     * Redact an event from the room.
     * @param eventId the event's id
     * @param callback the callback with the created event
     */
    public void report(String eventId, int score, String reason, ApiCallback<Void> callback) {
        mDataHandler.getDataRetriever().getRoomsRestClient().report(getRoomId(), eventId, score, reason, callback);
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
     * @param email the email address
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
                if (mDataHandler.isAlive()) {
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
     * @param reason ban reason
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

}
