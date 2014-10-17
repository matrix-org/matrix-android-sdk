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

import android.util.Log;

import org.matrix.androidsdk.MXDataHandler;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.InitialSyncResponse;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.RoomResponse;

import java.util.List;

/**
 * Listener for the events thread.
 */
public class DefaultEventsThreadListener implements EventsThreadListener {
    private MXDataHandler mData;

    public DefaultEventsThreadListener(MXDataHandler data) {
        mData = data;
    }

    @Override
    public void onInitialSyncComplete(InitialSyncResponse response) {
        // Handle presence events
        mData.handleEvents(response.presence);

        // Convert rooms from response
        for (RoomResponse roomResponse : response.rooms) {
            if (roomResponse.state != null) {
                // Handle state events
                mData.handleEvents(roomResponse.state);
            }

            if (roomResponse.messages != null && roomResponse.roomId != null) {
                // handle messages / pagination token
                mData.handleTokenResponse(roomResponse.roomId, roomResponse.messages);
            }

            if (RoomMember.MEMBERSHIP_INVITE.equals(roomResponse.membership)) {
                mData.handleInvite(roomResponse.roomId, roomResponse.inviter);
            }
        }

        mData.onInitialSyncComplete();
    }

    @Override
    public void onEventsReceived(List<Event> events) {
        mData.handleEvents(events);
    }
}
