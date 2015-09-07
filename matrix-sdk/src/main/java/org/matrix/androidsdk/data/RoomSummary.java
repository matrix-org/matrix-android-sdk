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

import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.Message;
import org.matrix.androidsdk.rest.model.PublicRoom;
import org.matrix.androidsdk.rest.model.RoomMember;

import java.util.Collection;

/**
 * Stores summarised information about the room.
 */
public class RoomSummary implements java.io.Serializable {

    private String mRoomId = null;
    private String mName = null;
    private String mTopic = null;
    private Event mLatestEvent = null;

    // the room state is only used to check
    // 1- the invitation status
    // 2- the members display name
    private transient RoomState mLatestRoomState = null;

    // invitation status
    // retrieved at initial sync
    // the roomstate is not always known
    private String mInviterUserId = null;
    // retrieved from the roomState
    private Boolean mIsInvited = false;
    private String mInviterName = null;

    private String mMatrixId = null;

    private boolean mIsHighlighted = false;
    private int mUnreadMessagesCount = 0;

    public RoomSummary() {}

    public RoomSummary(String roomId, String name, String topic, Event msg,
                       Collection<RoomMember> members) {
        mLatestEvent = msg;
        mRoomId = roomId;
        mName = name;
        mTopic = topic;
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
                    inviterName = mLatestRoomState.getMemberName(mLatestEvent.userId);
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
                mInviterName = mInviterUserId = mLatestEvent.userId;

                // try to retrieve a display name
                if (null != mLatestRoomState) {
                    mInviterName = mLatestRoomState.getMemberName(mLatestEvent.userId);
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
    public Boolean isHighlighted() {
        return mIsHighlighted || isInvited();
    }

    /**
     * Set the highlight status.
     * @param isHighlighted the new highlight status.
     * @return true if there is an update
     */
    public boolean setHighlighted(Boolean isHighlighted) {
        boolean isUpdated = (mIsHighlighted != isHighlighted);

        mIsHighlighted = isHighlighted;

        return isUpdated;
    }

    /**
     * reset the unread messages counter.
     * @return true if there is an update
     */
    public boolean resetUnreadMessagesCount() {
        if (0 != mUnreadMessagesCount) {
            mUnreadMessagesCount = 0;
            return true;
        }

        return false;
    }

    /**
     * increment the unread messages counter.
     */
    public void incrementUnreadMessagesCount() {
        mUnreadMessagesCount++;
    }

    /**
     * @return the unread messages counter.
     */
    public int getUnreadMessagesCount() {
        return mUnreadMessagesCount;
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
}
