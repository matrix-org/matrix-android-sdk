package org.matrix.androidsdk.data;

import org.matrix.androidsdk.api.response.Message;
import org.matrix.androidsdk.api.response.RoomResponse;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by JOACHIMR on 03/10/2014.
 */
public class Room {

    private String mRoomId;
    private Map<String, Message> mMessages = new HashMap<String, Message>();
    private Map<String, RoomMember> mMembers = new HashMap<String, RoomMember>();

    public String getRoomId() {
        return mRoomId;
    }

    public void setRoomId(String roomId) {
        mRoomId = roomId;
    }

    public Map<String, Message> getMessages() {
        return mMessages;
    }

    public void setMessages(Map<String, Message> messages) {
        mMessages = messages;
    }

    public Map<String, RoomMember> getMembers() {
        return mMembers;
    }

    public void setMembers(Map<String, RoomMember> members) {
        mMembers = members;
    }

    public void addMessage(Message message) {
        mMessages.put(message.eventId, message);
    }
}
