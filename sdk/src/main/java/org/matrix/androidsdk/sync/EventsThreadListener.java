package org.matrix.androidsdk.sync;

import org.matrix.androidsdk.MXData;
import org.matrix.androidsdk.api.response.Event;
import org.matrix.androidsdk.api.response.InitialSyncResponse;
import org.matrix.androidsdk.api.response.Message;
import org.matrix.androidsdk.api.response.RoomResponse;
import org.matrix.androidsdk.api.response.TokensChunkResponse;
import org.matrix.androidsdk.data.Room;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by JOACHIMR on 02/10/2014.
 */
public class EventsThreadListener implements EventsThread.EventsThreadListener {
    MXData mData;

    public EventsThreadListener(MXData data) {
        mData = data;
    }

    @Override
    public void onInitialSyncComplete(InitialSyncResponse response) {
        // Convert rooms from response
        List<Room> rooms = new ArrayList<Room>();
        for (RoomResponse roomResponse : response.rooms) {
            Room room = new Room();
            room.setRoomId(roomResponse.roomId);

            for (Message message : roomResponse.messages.chunk) {
                room.addMessage(message);
            }
            rooms.add(room);
        }
        mData.addRooms(rooms);
    }

    @Override
    public void onEventsReceived(List<Event> events) {

    }
}
