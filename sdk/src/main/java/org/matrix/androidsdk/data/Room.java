package org.matrix.androidsdk.data;

import org.matrix.androidsdk.api.response.IdentifiedEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by JOACHIMR on 03/10/2014.
 */
public class Room {

    private String mRoomId;
    private String mName;
    private List<String> mAliases;
    private String mTopic;
    private String mMembership;
    private String mVisibility;
    private Map<String, IdentifiedEvent> mMessages = new HashMap<String, IdentifiedEvent>();
    private Map<String, RoomMember> mMembers = new HashMap<String, RoomMember>();

    public String getRoomId() {
        return mRoomId;
    }

    public void setRoomId(String roomId) {
        mRoomId = roomId;
    }

    public String getName() {
        return mName;
    }

    public void setName(String name) {
        this.mName = name;
    }

    public List<String> getAliases() {
        return mAliases;
    }

    public void setAliases(List<String> aliases) {
        this.mAliases = aliases;
    }

    public String getTopic() {
        return mTopic;
    }

    public void setTopic(String topic) {
        this.mTopic = topic;
    }

    public Map<String, IdentifiedEvent> getMessages() {
        return mMessages;
    }

    public void setMessages(Map<String, IdentifiedEvent> messages) {
        mMessages = messages;
    }

    public Map<String, RoomMember> getMembers() {
        return mMembers;
    }

    public void setMembers(Map<String, RoomMember> members) {
        mMembers = members;
    }

    public void addMessage(IdentifiedEvent message) {
        mMessages.put(message.eventId, message);
    }
}
