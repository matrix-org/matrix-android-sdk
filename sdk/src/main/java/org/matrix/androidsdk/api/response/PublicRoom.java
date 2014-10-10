package org.matrix.androidsdk.api.response;

import org.matrix.androidsdk.data.IRoom;

import java.util.List;

/**
 * Class representing the objects returned by /publicRooms call.
 */
public class PublicRoom implements IRoom {
    public String roomId;
    public String name;
    public List<String> aliases;
    public String topic;
    public int numJoinedMembers;

    @Override
    public String getRoomId() {
        return this.roomId;
    }

    @Override
    public String getTopic() {
        return this.topic;
    }

    @Override
    public String getName() {
        return this.name;
    }
}
