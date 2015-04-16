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

import org.matrix.androidsdk.MXDataHandler;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.InitialSyncResponse;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.RoomResponse;

import java.util.List;

/**
 * Listener for the events thread that sends data back to a data handler.
 */
public class DefaultEventsThreadListener implements EventsThreadListener {
    private MXDataHandler mData;

    public DefaultEventsThreadListener(MXDataHandler data) {
        mData = data;
    }

    @Override
    public void onInitialSyncComplete(InitialSyncResponse response) {
        // Handle presence events
        mData.handleLiveEvents(response.presence);

        // Convert rooms from response
        for (RoomResponse roomResponse : response.rooms) {
            mData.handleInitialRoomResponse(roomResponse);
        }

        mData.onInitialSyncComplete(mData.getUserId());
    }

    @Override
    public void onEventsReceived(List<Event> events) {
        // set the used account
        for(Event event : events) {
            event.setAccountId(mData.getUserId());
        }

        mData.handleLiveEvents(events);
    }
}
