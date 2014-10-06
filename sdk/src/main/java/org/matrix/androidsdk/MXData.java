package org.matrix.androidsdk;

import org.matrix.androidsdk.data.Room;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * All cached data.
 */
public class MXData {
    private Map<String, Room> mRooms = new HashMap<String, Room>();
//    private Map<String, User> mUsers;



    public void addRooms(List<Room> rooms) {
        for (Room room : rooms) {
            mRooms.put(room.getRoomId(), room);
        }
    }
}
