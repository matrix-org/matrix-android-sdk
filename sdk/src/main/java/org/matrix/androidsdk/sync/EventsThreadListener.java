package org.matrix.androidsdk.sync;

import org.matrix.androidsdk.MXData;
import org.matrix.androidsdk.api.response.Event;
import org.matrix.androidsdk.api.response.InitialSyncResponse;
import org.matrix.androidsdk.api.response.RoomResponse;

import java.util.List;

/**
 * Created by JOACHIMR on 02/10/2014.
 */
public class EventsThreadListener implements EventsThread.EventsThreadListener {
    private MXData mData;

    public EventsThreadListener(MXData data) {
        mData = data;
    }

    @Override
    public void onInitialSyncComplete(InitialSyncResponse response) {
        // Handle presence events
        mData.handleEvents(response.presence);

        // Convert rooms from response
        for (RoomResponse roomResponse : response.rooms) {
            mData.addRoom(roomResponse.roomId);

            // Handle state events
            mData.handleEvents(roomResponse.state);

            // Handle messages
            mData.handleEvents(roomResponse.messages.chunk);
        }
    }

    @Override
    public void onEventsReceived(List<Event> events) {
        mData.handleEvents(events);
    }
}
