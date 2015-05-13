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
    private RoomState mLatestRoomState = null;
    // only populated if you've been invited.
    private String mInviter = null;
    private String mMatrixId = null;
    public int mUnreadMessagesCount = 0;

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
        return mName;
    }

    public String getRoomTopic() {
        return mTopic;
    }

    public Event getLatestEvent() {
        return mLatestEvent;
    }

    public RoomState getLatestRoomState() {
        return mLatestRoomState;
    }

    public boolean isInvited() {
        return mInviter != null;
    }

    public String getInviterUserId() {
        return mInviter;
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
        return this;
    }

    /**
     * Set the user ID of the person who invited the user to this room.
     * @param inviterUserId The user ID of the inviter
     * @return This summary for chaining calls.
     */
    public RoomSummary setInviterUserId(String inviterUserId) {
        mInviter = inviterUserId;
        return this;
    }
}
