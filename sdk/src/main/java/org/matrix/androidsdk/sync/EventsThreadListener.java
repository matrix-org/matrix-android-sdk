/*
 * Copyright 2014 OpenMarket Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.matrix.androidsdk.sync;

import org.matrix.androidsdk.MXData;
import org.matrix.androidsdk.api.response.Event;
import org.matrix.androidsdk.api.response.InitialSyncResponse;
import org.matrix.androidsdk.api.response.RoomResponse;

import java.util.List;

/**
 * Listener for the events thread.
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
