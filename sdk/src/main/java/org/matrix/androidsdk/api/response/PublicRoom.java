package org.matrix.androidsdk.api.response;

import java.util.List;

/**
 * Class representing the objects returned by /publicRooms call.
 */
public class PublicRoom {
    private String roomId;
    private String name;
    private List<String> aliases;
    private String topic;
    private int numJoinedMembers;

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getAliases() {
        return aliases;
    }

    public void setAliases(List<String> aliases) {
        this.aliases = aliases;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public int getNumJoinedMembers() {
        return numJoinedMembers;
    }

    public void setNumJoinedMembers(int numJoinedMembers) {
        this.numJoinedMembers = numJoinedMembers;
    }
}
