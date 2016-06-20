/*
 * Copyright 2015 OpenMarket Ltd
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

import android.text.TextUtils;
import android.util.Log;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.Message;
import org.matrix.androidsdk.rest.model.RoomMember;

import java.util.Collection;

/**
 * Stores summarised information about the room.
 */
public class RoomSummary implements java.io.Serializable {

    private static final String LOG_TAG = "RoomSummary";

    private String mRoomId = null;
    private String mName = null;
    private String mTopic = null;
    private Event mLatestEvent = null;

    // the room state is only used to check
    // 1- the invitation status
    // 2- the members display name
    private transient RoomState mLatestRoomState = null;

    // save the latest read receipt token
    // null if there is no known one
    private String mReadReceiptToken;
    private long mReadReceiptTs;

    private int mUnreadEventsCount;

    // invitation status
    // retrieved at initial sync
    // the roomstate is not always known
    private String mInviterUserId = null;
    // retrieved from the roomState
    private boolean mIsInvited = false;
    private String mInviterName = null;

    private String mMatrixId = null;

    private boolean mIsHighlighted = false;

    public RoomSummary() {}

    public RoomSummary(String roomId, String name, String topic, Event msg,
                       Collection<RoomMember> members) {
        mLatestEvent = msg;
        mRoomId = roomId;
        mName = name;
        mTopic = topic;

        mReadReceiptToken = null;
        mReadReceiptTs = -1;
    }

    /**
     * Test if the event can be summarized.
     * Some event types are not yet supported.
     * @param event the event to test.
     * @return true if the event can be summarized
     */
    public static boolean isSupportedEvent(Event event) {
        String type = event.type;
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

                isSupported = TextUtils.equals(msgType, Message.MSGTYPE_TEXT)||
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

            }
        } else if (!TextUtils.isEmpty(type)){
            isSupported = TextUtils.equals(Event.EVENT_TYPE_STATE_ROOM_TOPIC, type) ||
                    TextUtils.equals(Event.EVENT_TYPE_STATE_ROOM_NAME, type) ||
                    TextUtils.equals(Event.EVENT_TYPE_STATE_ROOM_MEMBER, type) ||
                    TextUtils.equals(Event.EVENT_TYPE_STATE_ROOM_CREATE, type) ||
                    TextUtils.equals(Event.EVENT_TYPE_STATE_HISTORY_VISIBILITY, type) ||
                    TextUtils.equals(Event.EVENT_TYPE_STATE_ROOM_THIRD_PARTY_INVITE, type) ||
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
            }
        }

        return isSupported;
    }

    public String getMatrixId() {
        return mMatrixId;
    }

    public String getRoomId() {
        return mRoomId;
    }

    public String getRoomName() {
        String name = mName;

        // when invited, the only received message should be the invitation one
        if (isInvited()) {
            if (null != mLatestEvent) {
                String inviterName;

                // try to retrieve a display name
                if (null != mLatestRoomState) {
                    inviterName = mLatestRoomState.getMemberName(mLatestEvent.getSender());
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
    public Event getLatestEvent() {
        return mLatestEvent;
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

    public String getInviterUserId() {
        return mInviterUserId;
    }

    public void setMatrixId(String matrixId) {
        mMatrixId = matrixId;
    }

    /**
     * Set the room's {@link org.matrix.androidsdk.rest.model.Event#EVENT_TYPE_STATE_ROOM_TOPIC}.
     * @param topic The topic
     * @return This summary for chaining calls.
     */
    public RoomSummary setTopic(String topic) {
        mTopic = topic;
        return this;
    }

    /**
     * Set the room's {@link org.matrix.androidsdk.rest.model.Event#EVENT_TYPE_STATE_ROOM_NAME}.
     * @param name The name
     * @return This summary for chaining calls.
     */
    public RoomSummary setName(String name) {
        mName = name;
        return this;
    }

    /**
     * Set the room's ID..
     * @param roomId The room ID
     * @return This summary for chaining calls.
     */
    public RoomSummary setRoomId(String roomId) {
        mRoomId = roomId;
        return this;
    }

    /**
     * Set the latest tracked event (e.g. the latest m.room.message)
     * @param event The most-recent event.
     * @return This summary for chaining calls.
     */
    public RoomSummary setLatestEvent(Event event) {
        mLatestEvent = event;
        return this;
    }

    /**
     * Set the latest tracked event (e.g. the latest m.room.message)
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

            if (null != mLatestEvent) {
                mInviterName = mInviterUserId = mLatestEvent.getSender();

                // try to retrieve a display name
                if (null != mLatestRoomState) {
                    mInviterName = mLatestRoomState.getMemberName(mLatestEvent.getSender());
                }
            }
        } else {
            mInviterUserId = mInviterName = null;
        }

        return this;
    }

    /**
     * @return true if the room summay must be highlighted
     */
    public boolean isHighlighted() {
        return mIsHighlighted || isInvited();
    }

    /**
     * Set the highlight status.
     * @param isHighlighted the new highlight status.
     * @return true if there is an update
     */
    public boolean setHighlighted(boolean isHighlighted) {
        boolean isUpdated = (mIsHighlighted != isHighlighted);

        mIsHighlighted = isHighlighted;

        return isUpdated;
    }

    /**
     * Set the user ID of the person who invited the user to this room.
     * @param inviterUserId The user ID of the inviter
     * @return This summary for chaining calls.
     */
    public RoomSummary setInviterUserId(String inviterUserId) {
        mInviterUserId = inviterUserId;
        return this;
    }

    /**
     * Tries to update the read receipts
     * @param token the latest token
     * @param ts the ts
     * @return true if the update succeeds
     */
    public boolean setReadReceiptToken(String token, long ts) {
        if ((ts > mReadReceiptTs) && !TextUtils.equals(token, mReadReceiptToken)) {
            mReadReceiptToken = token;
            mReadReceiptTs = ts;
            return true;
        }

        return false;
    }

    public String getReadReceiptToken() {
        return mReadReceiptToken;
    }

    public long getReadReceiptTs() {
        return mReadReceiptTs;
    }

    public void setUnreadEventsCount(int count) {
        mUnreadEventsCount = count;

        if (0 == mUnreadEventsCount) {
            setHighlighted(false);
        }
    }

    public int getUnreadEventsCount() {
        return mUnreadEventsCount;
    }
}
