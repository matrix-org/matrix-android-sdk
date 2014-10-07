package org.matrix.androidsdk.data;

import org.matrix.androidsdk.api.response.Event;
import org.matrix.androidsdk.api.response.RoomMember;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by JOACHIMR on 03/10/2014.
 */
public class Room {

    private String mRoomId;
    private RoomState mRoomState = new RoomState();
    private Map<String, Event> mMessages = new HashMap<String, Event>();
    private Map<String, RoomMember> mMembers = new HashMap<String, RoomMember>();

    public String getRoomId() {
        return mRoomId;
    }

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
}
