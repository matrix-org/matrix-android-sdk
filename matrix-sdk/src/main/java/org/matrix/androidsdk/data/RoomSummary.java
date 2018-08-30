/*
 * Copyright 2015 OpenMarket Ltd
 * Copyright 2017 Vector Creations Ltd
 * Copyright 2018 New Vector Ltd
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

import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.EventContent;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.message.Message;
import org.matrix.androidsdk.util.Log;

import java.util.HashSet;
import java.util.Set;

/**
 * Stores summarised information about the room.
 */
public class RoomSummary implements java.io.Serializable {
    private static final String LOG_TAG = RoomSummary.class.getSimpleName();

    private static final long serialVersionUID = -3683013938626566489L;

    private String mRoomId = null;
    private String mName = null;
    private String mTopic = null;
    private Event mLatestReceivedEvent = null;

    // the room state is only used to check
    // 1- the invitation status
    // 2- the members display name
    private transient RoomState mLatestRoomState = null;

    // defines the latest read message
    private String mReadReceiptEventId;

    // the read marker event id
    private String mReadMarkerEventId;

    private Set<String> mRoomTags;

    // counters
    public int mUnreadEventsCount;
    public int mNotificationCount;
    public int mHighlightsCount;

    // invitation status
    // retrieved at initial sync
    // the roomstate is not always known
    private String mInviterUserId = null;

    // retrieved from the roomState
    private boolean mIsInvited = false;
    private String mInviterName = null;

    private String mMatrixId = null;


    public RoomSummary() {
    }

    /**
     * Create a room summary
     *
     * @param fromSummary the summary source
     * @param event       the latest event of the room
     * @param roomState   the room state - used to display the event
     * @param userId      our own user id - used to display the room name
     */
    public RoomSummary(@Nullable RoomSummary fromSummary,
                       Event event,
                       RoomState roomState,
                       String userId) {
        setMatrixId(userId);

        if (null != roomState) {
            setRoomId(roomState.roomId);
        }

        if ((null == getRoomId()) && (null != event)) {
            setRoomId(event.roomId);
        }

        setLatestReceivedEvent(event, roomState);

        // if no summary is provided
        if (null == fromSummary) {
            if (null != event) {
                setReadMarkerEventId(event.eventId);
                setReadReceiptEventId(event.eventId);
            }

            if (null != roomState) {
                setHighlightCount(roomState.getHighlightCount());
                setNotificationCount(roomState.getHighlightCount());
            }
            setUnreadEventsCount(Math.max(getHighlightCount(), getNotificationCount()));
        } else {
            // else use the provided summary data
            setReadMarkerEventId(fromSummary.getReadMarkerEventId());
            setReadReceiptEventId(fromSummary.getReadReceiptEventId());
            setUnreadEventsCount(fromSummary.getUnreadEventsCount());
            setHighlightCount(fromSummary.getHighlightCount());
            setNotificationCount(fromSummary.getNotificationCount());
        }
    }

    /**
     * Test if the event can be summarized.
     * Some event types are not yet supported.
     *
     * @param event the event to test.
     * @return true if the event can be summarized
     */
    public static boolean isSupportedEvent(Event event) {
        String type = event.getType();
        boolean isSupported = false;

        // check if the msgtype is supported
        if (TextUtils.equals(Event.EVENT_TYPE_MESSAGE, type)) {
            try {
                JsonObject eventContent = event.getContentAsJsonObject();
                String msgType = "";

                JsonElement element = eventContent.get("msgtype");

                if (null != element) {
                    msgType = element.getAsString();
                }

                isSupported = TextUtils.equals(msgType, Message.MSGTYPE_TEXT) ||
                        TextUtils.equals(msgType, Message.MSGTYPE_EMOTE) ||
                        TextUtils.equals(msgType, Message.MSGTYPE_NOTICE) ||
                        TextUtils.equals(msgType, Message.MSGTYPE_IMAGE) ||
                        TextUtils.equals(msgType, Message.MSGTYPE_AUDIO) ||
                        TextUtils.equals(msgType, Message.MSGTYPE_VIDEO) ||
                        TextUtils.equals(msgType, Message.MSGTYPE_FILE);

                if (!isSupported && !TextUtils.isEmpty(msgType)) {
                    Log.e(LOG_TAG, "isSupportedEvent : Unsupported msg type " + msgType);
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "isSupportedEvent failed " + e.getMessage(), e);
            }
        } else if (TextUtils.equals(Event.EVENT_TYPE_MESSAGE_ENCRYPTED, type)) {
            isSupported = event.hasContentFields();
        } else if (!TextUtils.isEmpty(type)) {
            isSupported = TextUtils.equals(Event.EVENT_TYPE_STATE_ROOM_TOPIC, type) ||
                    TextUtils.equals(Event.EVENT_TYPE_MESSAGE_ENCRYPTED, type) ||
                    TextUtils.equals(Event.EVENT_TYPE_MESSAGE_ENCRYPTION, type) ||
                    TextUtils.equals(Event.EVENT_TYPE_STATE_ROOM_NAME, type) ||
                    TextUtils.equals(Event.EVENT_TYPE_STATE_ROOM_MEMBER, type) ||
                    TextUtils.equals(Event.EVENT_TYPE_STATE_ROOM_CREATE, type) ||
                    TextUtils.equals(Event.EVENT_TYPE_STATE_HISTORY_VISIBILITY, type) ||
                    TextUtils.equals(Event.EVENT_TYPE_STATE_ROOM_THIRD_PARTY_INVITE, type) ||
                    TextUtils.equals(Event.EVENT_TYPE_STICKER, type) ||
                    (event.isCallEvent() && !Event.EVENT_TYPE_CALL_CANDIDATES.equals(type));

            if (!isSupported) {
                // some events are known to be never traced
                // avoid warning when it is not required.
                if (!TextUtils.equals(Event.EVENT_TYPE_TYPING, type) &&
                        !TextUtils.equals(Event.EVENT_TYPE_STATE_ROOM_POWER_LEVELS, type) &&
                        !TextUtils.equals(Event.EVENT_TYPE_STATE_ROOM_JOIN_RULES, type) &&
                        !TextUtils.equals(Event.EVENT_TYPE_STATE_CANONICAL_ALIAS, type) &&
                        !TextUtils.equals(Event.EVENT_TYPE_STATE_ROOM_ALIASES, type)
                        ) {
                    Log.e(LOG_TAG, "isSupportedEvent :  Unsupported event type " + type);
                }
            } else if (TextUtils.equals(Event.EVENT_TYPE_STATE_ROOM_MEMBER, type)) {
                JsonObject eventContentAsJsonObject = event.getContentAsJsonObject();

                if (null != eventContentAsJsonObject) {
                    if (0 == eventContentAsJsonObject.entrySet().size()) {
                        isSupported = false;
                        Log.d(LOG_TAG, "isSupportedEvent : room member with no content is not supported");
                    } else {
                        // do not display the avatar / display name update
                        EventContent prevEventContent = event.getPrevContent();
                        EventContent eventContent = event.getEventContent();

                        String membership = null;
                        String preMembership = null;

                        if (null != prevEventContent) {
                            membership = eventContent.membership;
                        }

                        if (null != prevEventContent) {
                            preMembership = prevEventContent.membership;
                        }

                        isSupported = (null == membership) || !TextUtils.equals(membership, preMembership);

                        if (!isSupported) {
                            Log.d(LOG_TAG, "isSupportedEvent : do not support avatar display name update");
                        }
                    }
                }
            }
        }

        return isSupported;
    }

    /**
     * @return the matrix id
     */
    public String getMatrixId() {
        return mMatrixId;
    }

    /**
     * @return the room id
     */
    public String getRoomId() {
        return mRoomId;
    }

    /**
     * Compute the room summary display name.
     *
     * @return the room summary display name.
     */
    public String getRoomName() {
        String name = mName;

        // when invited, the only received message should be the invitation one
        if (isInvited()) {
            if (null != mLatestReceivedEvent) {
                String inviterName;

                // try to retrieve a display name
                if (null != mLatestRoomState) {
                    inviterName = mLatestRoomState.getMemberName(mLatestReceivedEvent.getSender());
                } else {
                    // use the stored one
                    inviterName = mInviterName;
                }

                if (null != inviterName) {
                    name = inviterName;
                }
            }
        }

        return name;
    }

    /**
     * @return the topic.
     */
    public String getRoomTopic() {
        return mTopic;
    }

    /**
     * @return the room summary event.
     */
    public Event getLatestReceivedEvent() {
        return mLatestReceivedEvent;
    }

    /**
     * @return the dedicated room state.
     */
    public RoomState getLatestRoomState() {
        return mLatestRoomState;
    }

    /**
     * @return true if the current user is invited
     */
    public boolean isInvited() {
        return mIsInvited || (mInviterUserId != null);
    }

    /**
     * @return the inviter user id.
     */
    public String getInviterUserId() {
        return mInviterUserId;
    }

    /**
     * Update the linked matrix id.
     *
     * @param matrixId the new matrix id.
     */
    public void setMatrixId(String matrixId) {
        mMatrixId = matrixId;
    }

    /**
     * Set the room's {@link org.matrix.androidsdk.rest.model.Event#EVENT_TYPE_STATE_ROOM_TOPIC}.
     *
     * @param topic The topic
     * @return This summary for chaining calls.
     */
    public RoomSummary setTopic(String topic) {
        mTopic = topic;
        return this;
    }

    /**
     * Set the room's {@link org.matrix.androidsdk.rest.model.Event#EVENT_TYPE_STATE_ROOM_NAME}.
     *
     * @param name The name
     * @return This summary for chaining calls.
     */
    public RoomSummary setName(String name) {
        mName = name;
        return this;
    }

    /**
     * Set the room's ID..
     *
     * @param roomId The room ID
     * @return This summary for chaining calls.
     */
    public RoomSummary setRoomId(String roomId) {
        mRoomId = roomId;
        return this;
    }

    /**
     * Set the latest tracked event (e.g. the latest m.room.message)
     *
     * @param event     The most-recent event.
     * @param roomState The room state
     * @return This summary for chaining calls.
     */
    public RoomSummary setLatestReceivedEvent(Event event, RoomState roomState) {
        setLatestReceivedEvent(event);
        setLatestRoomState(roomState);

        if (null != roomState) {
            setName(roomState.getDisplayName(getMatrixId()));
            setTopic(roomState.topic);
        }
        return this;
    }

    /**
     * Set the latest tracked event (e.g. the latest m.room.message)
     *
     * @param event The most-recent event.
     * @return This summary for chaining calls.
     */
    public RoomSummary setLatestReceivedEvent(Event event) {
        mLatestReceivedEvent = event;
        return this;
    }

    /**
     * Set the latest tracked event (e.g. the latest m.room.message)
     *
     * @param roomState The room state of the latest event.
     * @return This summary for chaining calls.
     */
    public RoomSummary setLatestRoomState(RoomState roomState) {
        mLatestRoomState = roomState;

        // check for the invitation status
        if (null != mLatestRoomState) {
            RoomMember member = mLatestRoomState.getMember(mMatrixId);
            mIsInvited = (null != member) && RoomMember.MEMBERSHIP_INVITE.equals(member.membership);
        }
        // when invited, the only received message should be the invitation one
        if (mIsInvited) {
            mInviterName = null;

            if (null != mLatestReceivedEvent) {
                mInviterName = mInviterUserId = mLatestReceivedEvent.getSender();

                // try to retrieve a display name
                if (null != mLatestRoomState) {
                    mInviterName = mLatestRoomState.getMemberName(mLatestReceivedEvent.getSender());
                }
            }
        } else {
            mInviterUserId = mInviterName = null;
        }

        return this;
    }

    /**
     * Set the read receipt event Id
     *
     * @param eventId the read receipt event id.
     */
    public void setReadReceiptEventId(String eventId) {
        Log.d(LOG_TAG, "## setReadReceiptEventId() : " + eventId + " roomId " + getRoomId());
        mReadReceiptEventId = eventId;
    }

    /**
     * @return the read receipt event id
     */
    public String getReadReceiptEventId() {
        return mReadReceiptEventId;
    }

    /**
     * Set the read marker event Id
     *
     * @param eventId the read marker event id.
     */
    public void setReadMarkerEventId(String eventId) {
        Log.d(LOG_TAG, "## setReadMarkerEventId() : " + eventId + " roomId " + getRoomId());

        if (TextUtils.isEmpty(eventId)) {
            Log.e(LOG_TAG, "## setReadMarkerEventId') : null mReadMarkerEventId, in " + getRoomId());
        }

        mReadMarkerEventId = eventId;
    }

    /**
     * @return the read receipt event id
     */
    public String getReadMarkerEventId() {
        if (TextUtils.isEmpty(mReadMarkerEventId)) {
            Log.e(LOG_TAG, "## getReadMarkerEventId') : null mReadMarkerEventId, in " + getRoomId());
            mReadMarkerEventId = getReadReceiptEventId();
        }

        return mReadMarkerEventId;
    }

    /**
     * Update the unread message counter
     *
     * @param count the unread events count.
     */
    public void setUnreadEventsCount(int count) {
        Log.d(LOG_TAG, "## setUnreadEventsCount() : " + count + " roomId " + getRoomId());
        mUnreadEventsCount = count;
    }

    /**
     * @return the unread events count
     */
    public int getUnreadEventsCount() {
        return mUnreadEventsCount;
    }

    /**
     * Update the notification counter
     *
     * @param count the notification counter
     */
    public void setNotificationCount(int count) {
        Log.d(LOG_TAG, "## setNotificationCount() : " + count + " roomId " + getRoomId());
        mNotificationCount = count;
    }

    /**
     * @return the notification count
     */
    public int getNotificationCount() {
        return mNotificationCount;
    }

    /**
     * Update the highlight counter
     *
     * @param count the highlight counter
     */
    public void setHighlightCount(int count) {
        Log.d(LOG_TAG, "## setHighlightCount() : " + count + " roomId " + getRoomId());
        mHighlightsCount = count;
    }

    /**
     * @return the highlight count
     */
    public int getHighlightCount() {
        return mHighlightsCount;
    }

    /**
     * @return the room tags
     */
    public Set<String> getRoomTags() {
        return mRoomTags;
    }

    /**
     * Update the room tags
     *
     * @param roomTags the room tags
     */
    public void setRoomTags(final Set<String> roomTags) {
        if (roomTags != null) {
            // wraps the set into a serializable one
            mRoomTags = new HashSet<>(roomTags);
        } else {
            mRoomTags = new HashSet<>();
        }
    }
}
