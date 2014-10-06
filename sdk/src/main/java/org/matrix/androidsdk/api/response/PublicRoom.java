package org.matrix.androidsdk.api.response;

import java.util.List;

/**
 * Class representing the objects returned by /publicRooms call.
 */
public class PublicRoom {
    public String roomId;
    public String name;
    public List<String> aliases;
    public String topic;
    public int numJoinedMembers;
}
