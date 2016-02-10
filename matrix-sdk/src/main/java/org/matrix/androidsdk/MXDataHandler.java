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

import android.os.HandlerThread;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import com.google.gson.JsonObject;

import org.matrix.androidsdk.call.MXCallsManager;
import org.matrix.androidsdk.data.DataRetriever;
import org.matrix.androidsdk.data.IMXStore;
import org.matrix.androidsdk.data.MyUser;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.data.RoomSummary;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.listeners.IMXEventListener;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.client.PresenceRestClient;
import org.matrix.androidsdk.rest.client.ProfileRestClient;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.EventContent;
import org.matrix.androidsdk.rest.model.ReceiptData;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.RoomResponse;
import org.matrix.androidsdk.rest.model.SyncV2.SyncResponse;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.rest.model.bingrules.BingRule;
import org.matrix.androidsdk.rest.model.bingrules.BingRuleSet;
import org.matrix.androidsdk.rest.model.login.Credentials;
import org.matrix.androidsdk.util.BingRulesManager;
import org.matrix.androidsdk.util.ContentManager;
import org.matrix.androidsdk.util.JsonUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import android.os.Handler;

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

    private IMXStore mStore;
    private Credentials mCredentials;
    private volatile boolean mInitialSyncComplete = false;
    private DataRetriever mDataRetriever;
    private BingRulesManager mBingRulesManager;
    private ContentManager mContentManager;
    private MXCallsManager mCallsManager;
    private MXMediasCache mMediasCache;

    private ProfileRestClient mProfileRestClient;
    private PresenceRestClient mPresenceRestClient;

    private MyUser mMyUser;

    private HandlerThread mSyncHandlerThread;
    private Handler mSyncHandler;
    private Handler mUiHandler;

    private Boolean mIsActive = true;

    /**
     * Default constructor.
     * @param store the data storage implementation.
     */
    public MXDataHandler(IMXStore store, Credentials credentials) {
        mStore = store;
        mCredentials = credentials;

        mUiHandler = new Handler(Looper.getMainLooper());

        mSyncHandlerThread = new HandlerThread("MXDataHandler" + mCredentials.userId, Thread.MIN_PRIORITY);
        mSyncHandlerThread.start();
        mSyncHandler = new Handler(mSyncHandlerThread.getLooper());
    }

    // some setters
    public void setProfileRestClient(ProfileRestClient profileRestClient) {
        mProfileRestClient = profileRestClient;
    }

    public void setPresenceRestClient(PresenceRestClient presenceRestClient) {
        mPresenceRestClient = presenceRestClient;
    }

    private void checkIfActive() {
        synchronized (this) {
            if (!mIsActive) {
                throw new AssertionError("Should not used a MXDataHandler");
            }
        }
    }

    public Boolean isActive() {
        synchronized (this) {
            return mIsActive;
        }
    }

    /**
     * Get the session's current user. The MyUser object provides methods for updating user properties which are not possible for other users.
     * @return the session's MyUser object
     */
    public MyUser getMyUser() {
        checkIfActive();

        IMXStore store = getStore();

        // MyUser is initialized as late as possible to have a better chance at having the info in storage,
        // which should be the case if this is called after the initial sync
        if (mMyUser == null) {
            mMyUser = new MyUser(store.getUser(mCredentials.userId));
            mMyUser.setProfileRestClient(mProfileRestClient);
            mMyUser.setPresenceRestClient(mPresenceRestClient);
            mMyUser.setDataHandler(this);

            // assume the profile is not yet initialized
            if (null == store.displayName()) {
                store.setAvatarURL(mMyUser.getAvatarUrl());
                store.setDisplayName(mMyUser.displayname);
            } else {
                // use the latest user information
                // The user could have updated his profile in offline mode and kill the application.
                mMyUser.displayname = store.displayName();
                mMyUser.setAvatarUrl(store.avatarURL());
            }

            // Handle the case where the user is null by loading the user information from the server
            mMyUser.userId = mCredentials.userId;
        } else {
            // assume the profile is not yet initialized
            if ((null == store.displayName()) && (null != mMyUser.displayname)) {
                // setAvatarURL && setDisplayName perform a commit if it is required.
                store.setAvatarURL(mMyUser.getAvatarUrl());
                store.setDisplayName(mMyUser.displayname);
            } else if (!TextUtils.equals(mMyUser.displayname, store.displayName())) {
                mMyUser.displayname = store.displayName();
                mMyUser.setAvatarUrl(store.avatarURL());
            }
        }

        // check if there is anything to refresh
        mMyUser.refreshUserInfos(null);

        return mMyUser;
    }

    /**
     * @return true if the initial sync is completed.
     */
    public boolean isInitialSyncComplete() {
        checkIfActive();
        return mInitialSyncComplete;
    }

    public void setDataRetriever(DataRetriever dataRetriever) {
        checkIfActive();
        mDataRetriever = dataRetriever;
        mDataRetriever.setStore(mStore);
    }

    public void setPushRulesManager(BingRulesManager bingRulesManager) {
        if (isActive()) {
            mBingRulesManager = bingRulesManager;
            mBingRulesManager.loadRules(new SimpleApiCallback<Void>() {
                @Override
                public void onSuccess(Void info) {
                    MXDataHandler.this.onBingRulesUpdate();
                }
            });
        }
    }

    public void setContentManager(ContentManager contentManager) {
        checkIfActive();
        mContentManager = contentManager;
    }

    public void setCallsManager(MXCallsManager callsManager) {
        checkIfActive();
        mCallsManager = callsManager;
    }

    public void setMediasCache(MXMediasCache mediasCache) {
        checkIfActive();
        mMediasCache = mediasCache;
    }

    public BingRuleSet pushRules() {
        if (isActive() && (null != mBingRulesManager)) {
            return mBingRulesManager.pushRules();
        }

        return null;
    }

    public void refreshPushRules() {
        if (isActive() && (null != mBingRulesManager)) {
            mBingRulesManager.loadRules(new SimpleApiCallback<Void>() {
                @Override
                public void onSuccess(Void info) {
                    MXDataHandler.this.onBingRulesUpdate();
                }
            });
        }
    }

    public BingRulesManager getBingRulesManager() {
        checkIfActive();
        return mBingRulesManager;
    }

    public void addListener(IMXEventListener listener) {
        if (mIsActive) {
            synchronized (this) {
                // avoid adding twice
                if (mEventListeners.indexOf(listener) == -1) {
                    mEventListeners.add(listener);
                }
            }

            if (mInitialSyncComplete) {
                listener.onInitialSyncComplete();
            }
        }
    }

    public void removeListener(IMXEventListener listener) {
        if (mIsActive) {
            synchronized (this) {
                mEventListeners.remove(listener);
            }
        }
    }

    public void clear() {
        synchronized (this) {
            mIsActive = false;
            // remove any listener
            mEventListeners.clear();
        }

        // clear the store
        mStore.close();
        mStore.clear();

        if (null != mSyncHandlerThread) {
            mSyncHandlerThread.quit();
            mSyncHandlerThread = null;
        }
    }
    /**
     * Handle the room data received from a per-room initial sync
     * @param roomResponse the room response object
     * @param room the associated room
     */
    public void handleInitialRoomResponse(RoomResponse roomResponse, Room room) {
        if (!isActive()) {
            Log.e(LOG_TAG, "handleInitialRoomResponse : the session is not anymore active");
            return;
        }

        // Handle state events
        if (roomResponse.state != null) {
            room.processLiveState(roomResponse.state);
        }

        // Handle visibility
        if (roomResponse.visibility != null) {
            room.setVisibility(roomResponse.visibility);
        }

        // Handle messages / pagination token
        if ((roomResponse.messages != null) && (roomResponse.messages.chunk.size() > 0)) {
            mStore.storeRoomEvents(room.getRoomId(), roomResponse.messages, Room.EventDirection.FORWARDS);

            int index = roomResponse.messages.chunk.size() - 1;

            while (index >= 0) {
                // To store the summary, we need the last event and the room state from just before
                Event lastEvent = roomResponse.messages.chunk.get(index);

                if (RoomSummary.isSupportedEvent(lastEvent)) {
                    RoomState beforeLiveRoomState = room.getLiveState().deepCopy();
                    beforeLiveRoomState.applyState(lastEvent, Room.EventDirection.BACKWARDS);

                    mStore.storeSummary(room.getRoomId(), lastEvent, room.getLiveState(), mCredentials.userId);

                    index = -1;
                } else {
                    index--;
                }
            }
        }

        // Handle presence
        if ((roomResponse.presence != null) && (roomResponse.presence.size() > 0)) {
            handleLiveEvents(roomResponse.presence);
        }

        // receipts
        if ((roomResponse.receipts != null) && (roomResponse.receipts.size() > 0)) {
            handleLiveEvents(roomResponse.receipts);
        }

        // account data
        if ((roomResponse.accountData != null) && (roomResponse.accountData.size() > 0)) {
            // the room id is not defined in the events
            // so as the room is defined here, avoid calling handleLiveEvents
            room.handleAccountDataEvents(roomResponse.accountData);
        }

        // Handle the special case where the room is an invite
        if (RoomMember.MEMBERSHIP_INVITE.equals(roomResponse.membership)) {
            handleInitialSyncInvite(room.getRoomId(), roomResponse.inviter);
        } else {
            onRoomInitialSyncComplete(room.getRoomId());
        }
    }

    /**
     * Handle the room data received from a global initial sync
     * @param roomResponse the room response object
     */
    public void handleInitialRoomResponse(RoomResponse roomResponse) {
        if (!isActive()) {
            Log.e(LOG_TAG, "handleInitialRoomResponse : the session is not anymore active");
            return;
        }

        if (roomResponse.roomId != null) {
            Room room = getRoom(roomResponse.roomId);
            handleInitialRoomResponse(roomResponse, room);
        }
    }

    /**
     * Update the missing data fields loaded from a permanent storage.
     */
    public void checkPermanentStorageData() {
        if (!isActive()) {
            Log.e(LOG_TAG, "checkPermanentStorageData : the session is not anymore active");        
            return;
        }

        if (mStore.isPermanent()) {
            // When the data are extracted from a persistent storage,
            // some fields are not retrieved :
            // They are used to retrieve some data
            // so add the missing links.

            Collection<Room> rooms =  mStore.getRooms();

            for(Room room : rooms) {
                room.setDataHandler(this);
                room.setDataRetriever(mDataRetriever);
                room.setMyUserId(mCredentials.userId);
                room.setContentManager(mContentManager);
            }

            Collection<RoomSummary> summaries = mStore.getSummaries();
            for(RoomSummary summary : summaries) {
                if (null != summary.getLatestRoomState()) {
                    summary.getLatestRoomState().setDataHandler(this);
                }
            }
        }
    }

    public String getUserId() {
        if (isActive()) {
            return mCredentials.userId;
        } else {
            return "dummy";
        }
    }

    private void handleInitialSyncInvite(String roomId, String inviterUserId) {
        if (!isActive()) {
            Log.e(LOG_TAG, "handleInitialSyncInvite : the session is not anymore active");
            return;
        }

        Room room = getRoom(roomId);

        // add yourself
        RoomMember member = new RoomMember();
        member.membership = RoomMember.MEMBERSHIP_INVITE;
        room.setMember(mCredentials.userId, member);

        // and the inviter
        member = new RoomMember();
        member.membership = RoomMember.MEMBERSHIP_JOIN;
        room.setMember(inviterUserId, member);

        // Build a fake invite event
        Event inviteEvent = new Event();
        inviteEvent.roomId = roomId;
        inviteEvent.stateKey = mCredentials.userId;
        inviteEvent.setSender(inviterUserId);
        inviteEvent.type = Event.EVENT_TYPE_STATE_ROOM_MEMBER;
        inviteEvent.setOriginServerTs(System.currentTimeMillis()); // This is where it's fake
        inviteEvent.content = JsonUtils.toJson(member);

        mStore.storeSummary(roomId, inviteEvent, null, mCredentials.userId);

        // Set the inviter ID
        RoomSummary roomSummary = mStore.getSummary(roomId);
        if (null != roomSummary) {
            roomSummary.setInviterUserId(inviterUserId);
        }
    }

    public IMXStore getStore() {
        if (isActive()) {
            return mStore;
        } else {
            Log.e(LOG_TAG, "getStore : the session is not anymore active");
            return null;
        }
    }

    /**
     * Handle a list of events coming down from the event stream.
     * @param events the live events
     */
    public void handleLiveEvents(List<Event> events) {
        if (!isActive()) {
            Log.e(LOG_TAG, "handleLiveEvents : the session is not anymore active");
            return;
        }

        // check if there is something to do
        if (0 != events.size()) {
            Log.d(LOG_TAG, "++ handleLiveEvents : got " + events.size() + " events.");

            for (Event event : events) {
                try {
                    handleLiveEvent(event);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "handleLiveEvent cannot process event " + e + " " + e.getStackTrace());
                }
            }

            Log.d(LOG_TAG, "-- handleLiveEvents : " + events.size() + " events are processed.");

            try {
                onLiveEventsChunkProcessed();
            } catch (Exception e) {
                Log.e(LOG_TAG, "onLiveEventsChunkProcessed failed " + e + " " + e.getStackTrace());
            }

            mUiHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        // check if an incoming call has been received
                        mCallsManager.checkPendingIncomingCalls();
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "checkPendingIncomingCalls failed " + e + " " + e.getStackTrace());
                    }
                }
            });
        }
    }

    /**
     * Returns the member with userID;
     * @param members the members List
     * @param userID the user ID
     * @return the roomMember if it exists.
     */
    public RoomMember getMember(Collection<RoomMember> members, String userID) {
        if (isActive()) {
            for (RoomMember member : members) {
                if (TextUtils.equals(userID, member.getUserId())) {
                    return member;
                }
            }
        } else {
            Log.e(LOG_TAG, "getMember : the session is not anymore active");
        }
        return null;
    }

    /**
     * Check if the room should be joined
     * @param event
     * @param roomState
     * @return true of the room should be self joined.
     */
    private Boolean shouldSelfJoin(final Event event, final RoomState roomState) {
        if (isActive()) {
            RoomMember member = JsonUtils.toRoomMember(event.content);

            // join event ?
            if (RoomMember.MEMBERSHIP_JOIN.equals(member.membership)) {
                Collection<RoomMember> members = roomState.getMembers();
                RoomMember myMember = getMember(members, mCredentials.userId);

                return ((null == myMember) || RoomMember.MEMBERSHIP_INVITE.equals(myMember.membership));
            }
        } else {
            Log.e(LOG_TAG, "shouldSelfJoin : the session is not anymore active");
        }
        return false;
    }

    /**
     * Perform an initial room sync (get the metadata + get the first bunch of messages)
     * @param roomId the roomid of the room to join.
     */
    private void selfJoin(final String roomId) {
        if (isActive()) {
            // inviterUserId is only used when the user is invited to the room found during the initial sync
            RoomSummary roomSummary = getStore().getSummary(roomId);
            roomSummary.setInviterUserId(null);

            final Room room = getStore().getRoom(roomId);
            room.initialSync(new SimpleApiCallback<Void>() {
                @Override
                public void onSuccess(Void info) {
                    room.requestHistory(new SimpleApiCallback<Integer>() {
                        @Override
                        public void onSuccess(Integer info) {
                            onRoomInitialSyncComplete(roomId);
                        }
                    });
                }
            });
        } else {
            Log.e(LOG_TAG, "selfJoin : the session is not anymore active");
        }
    }

    /**
     * Handle events coming down from the event stream.
     * @param event the live event
     * */
    public void handleLiveEvent(Event event) {
        handleLiveEvent(event, true);
    }

    /**
     * Handle events coming down from the event stream.
     * @param event the live event
     * @param withPush set to true to trigger pushes when it is required
     * */
    public void handleLiveEvent(Event event, boolean withPush) {
        if (!isActive()) {
            Log.e(LOG_TAG, "handleLiveEvent : the session is not anymore active");
            return;
        }

        // Presence event
        if (Event.EVENT_TYPE_PRESENCE.equals(event.type)) {
            User userPresence = JsonUtils.toUser(event.content);
            User user = mStore.getUser(userPresence.userId);

            if (user == null) {
                user = userPresence;
                user.lastActiveReceived();
                user.setDataHandler(this);
                mStore.storeUser(user);
            }
            else {
                user.presence = userPresence.presence;
                user.lastActiveAgo = userPresence.lastActiveAgo;
                user.lastActiveReceived();
            }

            // check if the current user has been updated
            if (mCredentials.userId.equals(user.userId)) {
                mStore.setAvatarURL(user.getAvatarUrl());
                mStore.setDisplayName(user.displayname);
            }

            this.onPresenceUpdate(event, user);

        } else if (Event.EVENT_TYPE_RECEIPT.equals(event.type)) {
            if (event.roomId != null) {
                final Room room = getRoom(event.roomId);

                // sanity check
                if (null != room) {
                    List<String> senders = room.handleReceiptEvent(event);

                    if ((senders.size() > 0) && (mUpdatedRoomIdList.indexOf(event.roomId) < 0)) {
                        mUpdatedRoomIdList.add(event.roomId);
                    }

                    if (null != senders) {
                        onReceiptEvent(event.roomId, senders);
                    }
                }
            }
        }
        // dispatch the call events to the calls manager
        else if (event.isCallEvent()) {
            mCallsManager.handleCallEvent(event);
        }
        // room tags
        else if (Event.EVENT_TYPE_TAGS.equals(event.type)) {
            if (event.roomId != null) {
                final Room room = getRoom(event.roomId);

                // sanity check
                if (null != room) {
                    room.handleAccountDataEvents(Arrays.asList(event));
                }
            }
        } else {

            // avoid processing event twice
            if (getStore().doesEventExist(event.eventId, event.roomId)) {
                Log.e(LOG_TAG, "handleLiveEvent : teh event " + event.eventId + " in " + event.roomId + " already exist.");
                return;
            }

            // Room event
            if (event.roomId != null) {
                final Room room = getRoom(event.roomId);

                String selfJoinRoomId = null;

                // check if the room has been joined
                // the initial sync + the first requestHistory call is done here
                // instead of being done in the application
                if (Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(event.type) && TextUtils.equals(event.getSender(), mCredentials.userId)) {

                    // check if the user updates his profile from another device.
                    MyUser myUser = getMyUser();

                    EventContent eventContent = JsonUtils.toEventContent(event.getContentAsJsonObject());

                    boolean hasAccountInfoUpdated = false;

                    if (!TextUtils.equals(eventContent.displayname, myUser.displayname)) {
                        hasAccountInfoUpdated = true;
                        myUser.displayname = eventContent.displayname;
                    }

                    if (!TextUtils.equals(eventContent.avatar_url, myUser.getAvatarUrl())) {
                        hasAccountInfoUpdated = true;
                        myUser.setAvatarUrl(eventContent.avatar_url);
                    }

                    if (hasAccountInfoUpdated) {
                        onAccountInfoUpdate(myUser);
                    }

                    if (shouldSelfJoin(event, room.getLiveState())) {
                        selfJoinRoomId = event.roomId;
                    }
                }

                if (event.stateKey != null) {
                    // copy the live state before applying any update
                    room.setLiveState(room.getLiveState().deepCopy());
                    // check if the event has been processed
                    if (!room.processStateEvent(event, Room.EventDirection.FORWARDS)) {
                        // not processed -> do not warn the application
                        // assume that the event is a duplicated one.
                        return;
                    }
                }

                storeLiveRoomEvent(event);
                onLiveEvent(event, room.getLiveState());

                if (null != selfJoinRoomId && MXSession.useSyncV1()) {
                    selfJoin(selfJoinRoomId);
                }

                // trigger pushes when it is required
                if (withPush) {
                    BingRule bingRule;
                    boolean outOfTimeEvent = false;
                    JsonObject eventContent = event.getContentAsJsonObject();

                    if (eventContent.has("lifetime")) {
                        long maxlifetime = eventContent.get("lifetime").getAsLong();
                        long eventLifeTime = System.currentTimeMillis() - event.getOriginServerTs();

                        outOfTimeEvent = eventLifeTime > maxlifetime;
                    }

                    // If the bing rules apply, bing
                    if (!Event.EVENT_TYPE_TYPING.equals(event.type)
                            && !Event.EVENT_TYPE_RECEIPT.equals(event.type)
                            && !outOfTimeEvent
                            && (mBingRulesManager != null)
                            && (null != (bingRule = mBingRulesManager.fulfilledBingRule(event)))
                            && bingRule.shouldNotify()) {
                        Log.d(LOG_TAG, "handleLiveEvent : onBingEvent");
                        onBingEvent(event, room.getLiveState(), bingRule);
                    }
                }
            } else {
                Log.e(LOG_TAG, "Unknown live event type: " + event.type);
            }
        }
    }

    /**
     * Check a room exists with the dedicated roomId
     * @param roomId the room ID
     * @return true it exists.
     */
    public Boolean doesRoomExist(String roomId) {
        return (null != roomId) && (null != mStore.getRoom(roomId));
    }

    /**
     * Get the room object for the corresponding room id. Creates and initializes the object if there is none.
     * @param roomId the room id
     * @return the corresponding room
     */
    public Room getRoom(String roomId) {
        return getRoom(roomId, true);
    }

    /**
     * Get the room object for the corresponding room id.
     * @param roomId the room id
     * @param create create the room it does not exist.
     * @return the corresponding room
     */
    public Room getRoom(String roomId, boolean create) {
        if (!isActive()) {
            Log.e(LOG_TAG, "getRoom : the session is not anymore active");
            return null;
        }

        // sanity check
        if (TextUtils.isEmpty(roomId)) {
            return null;
        }

        Room room = mStore.getRoom(roomId);
        if ((room == null) && create) {
            room = new Room();
            room.setRoomId(roomId);
            room.setDataHandler(this);
            room.setDataRetriever(mDataRetriever);
            room.setMyUserId(mCredentials.userId);
            room.setContentManager(mContentManager);
            mStore.storeRoom(room);
        }
        return room;
    }

    /**
     * Store a live room event.
     * @param event The event to be stored.
     */
    public void storeLiveRoomEvent(Event event) {
        if (!isActive()) {
            Log.e(LOG_TAG, "storeLiveRoomEvent : the session is not anymore active");
            return;
        }

        Room room = getRoom(event.roomId);

        // sanity check
        if (null != room) {
            boolean store = false;
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
            }  else if (!Event.EVENT_TYPE_TYPING.equals(event.type) && !Event.EVENT_TYPE_RECEIPT.equals(event.type)) {
                // the candidate events are not stored.
                store = !event.isCallEvent() || !Event.EVENT_TYPE_CALL_CANDIDATES.equals(event.type);

                // thread issue
                // if the user leaves a room,
                // the server echo could try to delete the room file
                if (Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(event.type) && mCredentials.userId.equals(event.stateKey)) {
                    String membership = event.content.getAsJsonObject().getAsJsonPrimitive("membership").getAsString();

                    if (RoomMember.MEMBERSHIP_LEAVE.equals(membership) || RoomMember.MEMBERSHIP_BAN.equals(membership)) {
                        store = false;
                        // check if the room still exists.
                        if (null != this.getStore().getRoom(event.roomId)) {
                            this.getStore().deleteRoom(event.roomId);
                            this.onLeaveRoom(event.roomId);
                        }
                    }
                }
            }

            if (store) {
                // create dummy read receipt for any incoming event
                // to avoid not synchronized read receipt and event
                if ((null != event.getSender()) && (null != event.eventId)) {
                    room.handleReceiptData(new ReceiptData(event.getSender(), event.eventId, event.originServerTs));
                }

                mStore.storeLiveRoomEvent(event);

                if (RoomSummary.isSupportedEvent(event)) {
                    RoomSummary summary = mStore.storeSummary(event.roomId, event, room.getLiveState(), mCredentials.userId);

                    // Watch for potential room name changes
                    if (Event.EVENT_TYPE_STATE_ROOM_NAME.equals(event.type)
                            || Event.EVENT_TYPE_STATE_ROOM_ALIASES.equals(event.type)
                            || Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(event.type)) {


                        if (null != summary) {
                            summary.setName(room.getName(mCredentials.userId));
                        }
                    }
                }
            }

            // warn the listener that a new room has been created
            if (Event.EVENT_TYPE_STATE_ROOM_CREATE.equals(event.type)) {
                this.onNewRoom(event.roomId);
            }

            // warn the listeners that a room has been joined
            if (Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(event.type) && mCredentials.userId.equals(event.stateKey)) {
                String membership = event.content.getAsJsonObject().getAsJsonPrimitive("membership").getAsString();

                if (RoomMember.MEMBERSHIP_JOIN.equals(membership)) {
                    this.onJoinRoom(event.roomId);
                } else if (RoomMember.MEMBERSHIP_INVITE.equals(membership)) {
                    this.onNewRoom(event.roomId);
                }
            }
        }
    }

    /**
     * Delete an event.
     * @param event The event to be stored.
     */
    public void deleteRoomEvent(Event event) {
        if (isActive()) {
            Room room = getRoom(event.roomId);

            if (null != room) {
                mStore.deleteEvent(event);
                Event lastEvent = mStore.getLatestEvent(event.roomId);
                RoomState beforeLiveRoomState = room.getLiveState().deepCopy();

                mStore.storeSummary(event.roomId, lastEvent, beforeLiveRoomState, mCredentials.userId);
            }
        } else {
            Log.e(LOG_TAG, "deleteRoomEvent : the session is not anymore active");
        }
    }

    /**
     * Return an user from his id.
     * @param userId the user id;.
     * @return the user.
     */
    public User getUser(String userId) {
        if (!isActive()) {
            Log.e(LOG_TAG, "getUser : the session is not anymore active");
            return null;
        } else {
            return mStore.getUser(userId);
        }
    }

    //================================================================================
    // Sync V2
    //================================================================================

    public void onSyncV2Complete(final SyncResponse syncResponse, final boolean isInitialSync) {
        // perform the sync in background
        // to avoid UI thread lags.
        mSyncHandler.post(new Runnable() {
            @Override
            public void run() {
               manageV2Response(syncResponse, isInitialSync);
            }
        });
    }

    private void manageV2Response(final SyncResponse syncResponse, final boolean isInitialSync) {
        boolean isEmptyResponse = true;

        // sanity check
        if (null != syncResponse) {
            Log.d(LOG_TAG, "onSyncV2Complete");

            // sanity check
            if (null != syncResponse.rooms) {

                // joined rooms events
                if ((null != syncResponse.rooms.join) && (syncResponse.rooms.join.size() > 0)) {
                    Log.d(LOG_TAG, "Received " + syncResponse.rooms.join.size() + " joined rooms");

                    Set<String> roomIds = syncResponse.rooms.join.keySet();

                    // Handle first joined rooms
                    for (String roomId : roomIds) {
                        getRoom(roomId).handleJoinedRoomSync(syncResponse.rooms.join.get(roomId), isInitialSync);
                    }

                    isEmptyResponse = false;
                }

                // invited room management
                if ((null != syncResponse.rooms.invite) && (syncResponse.rooms.invite.size() > 0)) {
                    Log.d(LOG_TAG, "Received " + syncResponse.rooms.invite.size() + " invited rooms");

                    Set<String> roomIds = syncResponse.rooms.invite.keySet();

                    for (String roomId : roomIds) {
                        getRoom(roomId).handleInvitedRoomSync(syncResponse.rooms.invite.get(roomId));
                    }

                    isEmptyResponse = false;
                }

                // left room management
                if ((null != syncResponse.rooms.leave) && (syncResponse.rooms.leave.size() > 0)) {
                    Log.d(LOG_TAG, "Received " + syncResponse.rooms.leave.size() + " left rooms");

                    Set<String> roomIds = syncResponse.rooms.leave.keySet();

                    for (String roomId : roomIds) {
                        // RoomSync leftRoomSync = syncResponse.rooms.leave.get(roomId);

                        // Presently we remove the existing room from the rooms list.
                        // FIXME SYNCV2 Archive/Display the left rooms!
                        // For that create 'handleArchivedRoomSync' method

                        // Retrieve existing room
                        // check if the room still exists.
                        if (null != this.getStore().getRoom(roomId)) {
                            this.getStore().deleteRoom(roomId);
                            onLeaveRoom(roomId);
                        }
                    }

                    isEmptyResponse = false;
                }
            }

            // Handle presence of other users
            if ((null != syncResponse.presence) && (null != syncResponse.presence.events)) {
                for (Event presenceEvent : syncResponse.presence.events) {
                    handleLiveEvent(presenceEvent);
                }
            }

            if (!isEmptyResponse) {
                getStore().setEventStreamToken(syncResponse.nextBatch);
                getStore().commit();
            }
        }

        if (isInitialSync) {
            onInitialSyncComplete();
        } else {
            try {
                onLiveEventsChunkProcessed();
            } catch (Exception e) {
                Log.e(LOG_TAG, "onLiveEventsChunkProcessed failed " + e + " " + e.getStackTrace());
            }

            try {
                // check if an incoming call has been received
                mCallsManager.checkPendingIncomingCalls();
            } catch (Exception e) {
                Log.e(LOG_TAG, "checkPendingIncomingCalls failed " + e + " " + e.getStackTrace());
            }
        }
    }

    /**
     * Refresh the unread summary counters of the updated rooms.
     */
    private void refreshUnreadCounters() {
        // refresh the unread counter
        for(String roomId : mUpdatedRoomIdList) {
            Room room = mStore.getRoom(roomId);

            if (null != room) {
                room.refreshUnreadCounter();
            }
        }

        mUpdatedRoomIdList.clear();
    }

    //================================================================================
    // Listeners management
    //================================================================================

    // Proxy IMXEventListener callbacks to everything in mEventListeners
    List<IMXEventListener> getListenersSnapshot() {
        ArrayList<IMXEventListener> eventListeners;

        synchronized (this) {
            eventListeners = new ArrayList<IMXEventListener>(mEventListeners);
        }

        return eventListeners;
    }

    public void onStoreReady() {
        final List<IMXEventListener> eventListeners = getListenersSnapshot();

        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                for (IMXEventListener listener : eventListeners) {
                    try {
                        listener.onStoreReady();
                    } catch (Exception e) {
                    }
                }
            }
        });
    }

    @Override
    public void onAccountInfoUpdate(final MyUser myUser) {
        final List<IMXEventListener> eventListeners = getListenersSnapshot();

        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                for (IMXEventListener listener : eventListeners) {
                    try {
                        listener.onAccountInfoUpdate(myUser);
                    } catch (Exception e) {
                    }
                }
            }
        });
    }

    @Override
    public void onPresenceUpdate(final Event event, final User user) {
        final List<IMXEventListener> eventListeners = getListenersSnapshot();

        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                for (IMXEventListener listener : eventListeners) {
                    try {
                        listener.onPresenceUpdate(event, user);
                    } catch (Exception e) {
                    }
                }
            }
        });
    }

    private ArrayList<String> mUpdatedRoomIdList = new ArrayList<String>();

    @Override
    public void onLiveEvent(final Event event, final RoomState roomState) {
        //
        if (!TextUtils.equals(Event.EVENT_TYPE_TYPING, event.type) && !TextUtils.equals(Event.EVENT_TYPE_RECEIPT, event.type) && !TextUtils.equals(Event.EVENT_TYPE_TYPING, event.type)) {
            if (mUpdatedRoomIdList.indexOf(roomState.roomId) < 0) {
                mUpdatedRoomIdList.add(roomState.roomId);
            }
        }

        final List<IMXEventListener> eventListeners = getListenersSnapshot();

        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                for (IMXEventListener listener : eventListeners) {
                    try {
                        listener.onLiveEvent(event, roomState);
                    } catch (Exception e) {
                    }
                }
            }
        });
    }

    @Override
    public void onLiveEventsChunkProcessed() {
        refreshUnreadCounters();

        final List<IMXEventListener> eventListeners = getListenersSnapshot();

        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                for (IMXEventListener listener : eventListeners) {
                    try {
                        listener.onLiveEventsChunkProcessed();
                    } catch (Exception e) {
                    }
                }
            }
        });
    }

    @Override
    public void onBackEvent(final Event event, final RoomState roomState) {
        final List<IMXEventListener> eventListeners = getListenersSnapshot();

        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                for (IMXEventListener listener : eventListeners) {
                    try {
                        listener.onBackEvent(event, roomState);
                    } catch (Exception e) {
                    }
                }
            }
        });
    }

    @Override
    public void onBingEvent(final Event event, final RoomState roomState, final BingRule bingRule) {
        final List<IMXEventListener> eventListeners = getListenersSnapshot();

        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                for (IMXEventListener listener : eventListeners) {
                    try {
                        listener.onBingEvent(event, roomState, bingRule);
                    } catch (Exception e) {
                    }
                }
            }
        });
    }

    @Override
    public void onDeleteEvent(final Event event) {
        final List<IMXEventListener> eventListeners = getListenersSnapshot();

        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                for (IMXEventListener listener : eventListeners) {
                    try {
                        listener.onDeleteEvent(event);
                    } catch (Exception e) {
                    }
                }
            }
        });
    }

    @Override
    public void onResentEvent(final Event event) {
        final List<IMXEventListener> eventListeners = getListenersSnapshot();

        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                for (IMXEventListener listener : eventListeners) {
                    try {
                        listener.onResentEvent(event);
                    } catch (Exception e) {
                    }
                }
            }
        });
    }

    @Override
    public void onResendingEvent(final Event event) {
        final List<IMXEventListener> eventListeners = getListenersSnapshot();

        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                for (IMXEventListener listener : eventListeners) {
                    try {
                        listener.onResendingEvent(event);
                    } catch (Exception e) {
                    }
                }
            }
        });
    }

    @Override
    public void onBingRulesUpdate() {
        final List<IMXEventListener> eventListeners = getListenersSnapshot();

        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                for (IMXEventListener listener : eventListeners) {
                    try {
                        listener.onBingRulesUpdate();
                    } catch (Exception e) {
                    }
                }
            }
        });
    }

    @Override
    public void onInitialSyncComplete() {
        mInitialSyncComplete = true;

        refreshUnreadCounters();

        final List<IMXEventListener> eventListeners = getListenersSnapshot();

        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                for (IMXEventListener listener : eventListeners) {
                    try {
                        listener.onInitialSyncComplete();
                    } catch (Exception e) {
                    }
                }
            }
        });
    }

    @Override
    public void onPresencesSyncComplete() {
        final List<IMXEventListener> eventListeners = getListenersSnapshot();

        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                for (IMXEventListener listener : eventListeners) {
                    try {
                        listener.onPresencesSyncComplete();
                    } catch (Exception e) {
                    }
                }
            }
        });
    }

    public void onNewRoom(final String roomId) {
        final List<IMXEventListener> eventListeners = getListenersSnapshot();

        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                for (IMXEventListener listener : eventListeners) {
                    try {
                        listener.onNewRoom(roomId);
                    } catch (Exception e) {
                    }
                }
            }
        });
    }

    public void onJoinRoom(final String roomId) {
        final List<IMXEventListener> eventListeners = getListenersSnapshot();

        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                for (IMXEventListener listener : eventListeners) {
                    try {
                        listener.onJoinRoom(roomId);
                    } catch (Exception e) {
                    }
                }
            }
        });
    }

    public void onRoomInitialSyncComplete(final String roomId) {
        final List<IMXEventListener> eventListeners = getListenersSnapshot();

        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                for (IMXEventListener listener : eventListeners) {
                    try {
                        listener.onRoomInitialSyncComplete(roomId);
                    } catch (Exception e) {
                    }
                }
            }
        });
    }

    public void onRoomInternalUpdate(final String roomId) {
        final List<IMXEventListener> eventListeners = getListenersSnapshot();

        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                for (IMXEventListener listener : eventListeners) {
                    try {
                        listener.onRoomInternalUpdate(roomId);
                    } catch (Exception e) {
                    }
                }
            }
        });
    }

    public void onLeaveRoom(final String roomId) {
        final List<IMXEventListener> eventListeners = getListenersSnapshot();

        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                for (IMXEventListener listener : eventListeners) {
                    try {
                        listener.onLeaveRoom(roomId);
                    } catch (Exception e) {
                    }
                }
            }
        });
    }

    public void onReceiptEvent(final String roomId, final List<String> senderIds) {
        final List<IMXEventListener> eventListeners = getListenersSnapshot();

        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                for (IMXEventListener listener : eventListeners) {
                    try {
                        listener.onReceiptEvent(roomId, senderIds);
                    } catch (Exception e) {
                    }
                }
            }
        });
    }

    public void onRoomTagEvent(final String roomId) {
        final List<IMXEventListener> eventListeners = getListenersSnapshot();

        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                for (IMXEventListener listener : eventListeners) {
                    try {
                        listener.onRoomTagEvent(roomId);
                    } catch (Exception e) {
                    }
                }
            }
        });
    }

    public void onRoomSyncWithLimitedTimeline(final String roomId) {
        final List<IMXEventListener> eventListeners = getListenersSnapshot();

        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                for (IMXEventListener listener : eventListeners) {
                    try {
                        listener.onRoomSyncWithLimitedTimeline(roomId);
                    } catch (Exception e) {
                    }
                }
            }
        });
    }
}
