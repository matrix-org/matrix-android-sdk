package org.matrix.androidsdk.data;

import org.matrix.androidsdk.api.response.Event;
import org.matrix.androidsdk.api.response.RoomMember;

import java.util.HashMap;
import java.util.Map;

public class Room implements IRoom {

    private String mRoomId;
    private RoomState mRoomState = new RoomState();
    private Map<String, Event> mMessages = new HashMap<String, Event>();
    private Map<String, RoomMember> mMembers = new HashMap<String, RoomMember>();

    public void setRoomId(String roomId) {
        mRoomId = roomId;
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

    @Override
    public String getRoomId() {
        return this.mRoomId;
    }

    @Override
    public String getTopic() {
        return this.mRoomState.topic;
    }

    @Override
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
