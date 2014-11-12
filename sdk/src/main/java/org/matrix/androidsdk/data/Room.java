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

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.matrix.androidsdk.listeners.IMXEventListener;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.TokensChunkResponse;

import java.util.Collection;
import java.util.List;

public class Room {

    /**
     * The direction from which an incoming event is considered.
     * <ul>
     * <li>FORWARDS for events coming down the live event stream</li>
     * <li>BACKWARDS for old events requested through pagination</li>
     * </ul>
     */
    public static enum EventDirection {
        /**
         * The direction for events coming down the live event stream.
         */
        FORWARDS,

        /**
         * The direction for old events requested through pagination.
         */
        BACKWARDS
    }

    private String mRoomId;
    private RoomState mLiveState = new RoomState();
    private RoomState mBackState = new RoomState();

    private Gson mGson;

    private DataRetriever mDataRetriever;
    private IMXEventListener mEventListener;

    public Room() {
        // The JSON -> object mapper
        mGson = new GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .create();
    }

    public void setRoomId(String roomId) {
        mRoomId = roomId;
        mLiveState.roomId = roomId;
        mBackState.roomId = roomId;
    }

    public RoomState getLiveState() {
        return mLiveState;
    }

    public Collection<RoomMember> getMembers() {
        return mLiveState.getMembers();
    }

    public void setMember(String userId, RoomMember member) {
        mLiveState.setMember(userId, member);
    }

    public RoomMember getMember(String userId) {
        return mLiveState.getMember(userId);
    }

    public String getRoomId() {
        return this.mRoomId;
    }

    public String getTopic() {
        return this.mLiveState.topic;
    }

    public String getPaginationToken() {
        return mBackState.getToken();
    }

    public void setPaginationToken(String token) {
        mBackState.setToken(token);
    }

    public String getName() {
        if (this.mLiveState.name != null) {
            return this.mLiveState.name;
        }
        else if (this.mLiveState.roomAliasName != null) {
            return this.mLiveState.roomAliasName;
        }
        else if (this.mLiveState.aliases != null && this.mLiveState.aliases.size() > 0) {
            return this.mLiveState.aliases.get(0);
        }
        else {
            return this.mRoomId;
        }
    }

    public void setDataRetriever(DataRetriever dataRetriever) {
        mDataRetriever = dataRetriever;
    }

    public void setEventListener(IMXEventListener eventListener) {
        mEventListener = eventListener;
    }

    public void requestPagination() {
        if (mBackState == null) {
            mBackState = mLiveState.deepCopy();
        }

        mDataRetriever.requestRoomPagination(mRoomId, mBackState.getToken(), new DataRetriever.PaginationCallback() {
            @Override
            public void onComplete(TokensChunkResponse<Event> response) {
                for (Event event : response.chunk) {
                    if (event.stateKey != null) {
                        processStateEvent(event, EventDirection.BACKWARDS);
                    }
                    mEventListener.onBackEvent(event, mBackState.deepCopy());
                }
            }
        });
    }

    public void processStateEvent(Event event, EventDirection direction) {
        RoomState affectedState = (direction == EventDirection.FORWARDS) ? mLiveState : mBackState;

        if (Event.EVENT_TYPE_STATE_ROOM_NAME.equals(event.type)) {
            RoomState roomState = mGson.fromJson(event.content, RoomState.class);
            affectedState.name = roomState.name;
        }
        else if (Event.EVENT_TYPE_STATE_ROOM_TOPIC.equals(event.type)) {
            RoomState roomState = mGson.fromJson(event.content, RoomState.class);
            affectedState.topic = roomState.topic;
        }
        else if (Event.EVENT_TYPE_STATE_ROOM_CREATE.equals(event.type)) {
            RoomState roomState = mGson.fromJson(event.content, RoomState.class);
            affectedState.creator = roomState.creator;
        }
        else if (Event.EVENT_TYPE_STATE_ROOM_JOIN_RULES.equals(event.type)) {
            RoomState roomState = mGson.fromJson(event.content, RoomState.class);
            affectedState.joinRule = roomState.joinRule;
        }
        else if (Event.EVENT_TYPE_STATE_ROOM_ALIASES.equals(event.type)) {
            RoomState roomState = mGson.fromJson(event.content, RoomState.class);
            affectedState.aliases = roomState.aliases;
        }
        else if (Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(event.type)) {
            RoomMember member = mGson.fromJson(event.content, RoomMember.class);
            String userId = event.userId;
            if (RoomMember.MEMBERSHIP_INVITE.equals(member.membership)) {
                userId = event.stateKey;
            }
            affectedState.setMember(userId, member);
        }
    }
}
