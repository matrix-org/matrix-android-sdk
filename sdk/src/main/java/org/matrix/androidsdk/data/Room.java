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

import org.matrix.androidsdk.rest.model.RoomMember;

import java.util.Collection;

public class Room {

    private String mRoomId;
    private String mPaginationToken;
    private RoomState mRoomState = new RoomState();
    private RoomState mOldState = new RoomState();

    public void setRoomId(String roomId) {
        mRoomId = roomId;
        mRoomState.roomId = roomId;
    }

    public RoomState getRoomState() {
        return mRoomState;
    }

    public RoomState getOldRoomState() {
        return mOldState;
    }

    public Collection<RoomMember> getMembers() {
        return mRoomState.getMembers();
    }

    public void setMember(String userId, RoomMember member) {
        mRoomState.setMember(userId, member);
    }

    public RoomMember getMember(String userId) {
        return mRoomState.getMember(userId);
    }

    public String getRoomId() {
        return this.mRoomId;
    }

    public String getTopic() {
        return this.mRoomState.topic;
    }

    public String getPaginationToken() {
        return this.mPaginationToken;
    }

    public void setPaginationToken(String token) {
        mPaginationToken = token;
    }

    public String getName() {
        if (this.mRoomState.name != null) {
            return this.mRoomState.name;
        }
        else if (this.mRoomState.roomAliasName != null) {
            return this.mRoomState.roomAliasName;
        }
        else if (this.mRoomState.aliases != null && this.mRoomState.aliases.size() > 0) {
            return this.mRoomState.aliases.get(0);
        }
        else {
            return this.mRoomId;
        }
    }
}
