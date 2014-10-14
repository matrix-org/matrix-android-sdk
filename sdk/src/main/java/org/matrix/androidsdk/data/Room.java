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

import org.matrix.androidsdk.api.response.Event;
import org.matrix.androidsdk.api.response.RoomMember;

import java.util.HashMap;
import java.util.Map;

public class Room {

    private String mRoomId;
    private RoomState mRoomState = new RoomState();
    private Map<String, Event> mMessages = new HashMap<String, Event>();
    private Map<String, RoomMember> mMembers = new HashMap<String, RoomMember>();

    public void setRoomId(String roomId) {
        mRoomId = roomId;
        mRoomState.roomId = roomId;
    }

    public RoomState getRoomState() {
        return mRoomState;
    }

    public void setRoomState(RoomState roomState) {
        this.mRoomState = roomState;
    }

    public Map<String, Event> getMessages() {
        return mMessages;
    }

    public void setMessages(Map<String, Event> messages) {
        mMessages = messages;
    }

    public Map<String, RoomMember> getMembers() {
        return mMembers;
    }

    public void setMembers(Map<String, RoomMember> members) {
        mMembers = members;
    }

    public void addMessage(Event message) {
        mMessages.put(message.eventId, message);
    }

    public void setMember(String userId, RoomMember member) {
        mMembers.put(userId, member);
    }

    public String getRoomId() {
        return this.mRoomId;
    }

    public String getTopic() {
        return this.mRoomState.topic;
    }

    public String getName() {
        if (this.mRoomState.name != null) {
            return this.mRoomState.name;
        }
        else if (this.mRoomState.roomAliasName != null) {
            return this.mRoomState.roomAliasName;
        }
        else {
            return this.mRoomId;
        }
    }
}
