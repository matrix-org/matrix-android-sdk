/*
 * Copyright 2018 New Vector Ltd
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

package org.matrix.androidsdk.data.timeline;

import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.sync.InvitedRoomSync;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * This class is responsible for handling the invitation room events from the SyncResponse
 */
class TimelineInvitedRoomSyncHandler {

    private final IEventTimeline mEventTimeline;
    private final InvitedRoomSync mInvitedRoomSync;

    TimelineInvitedRoomSyncHandler(@Nonnull final IEventTimeline eventTimeline,
                                   @Nullable final InvitedRoomSync invitedRoomSync) {
        mEventTimeline = eventTimeline;
        mInvitedRoomSync = invitedRoomSync;
    }

    /**
     * Handle the invitation room events
     */
    public void handle() {
        // Handle the state events as live events (the room state will be updated, and the listeners (if any) will be notified).
        if ((mInvitedRoomSync != null) && (mInvitedRoomSync.inviteState != null) && (mInvitedRoomSync.inviteState.events != null)) {
            final Room room = mEventTimeline.getRoom();
            final String roomId = room.getRoomId();

            for (Event event : mInvitedRoomSync.inviteState.events) {
                // Add a fake event id if none in order to be able to store the event
                if (null == event.eventId) {
                    event.eventId = roomId + "-" + System.currentTimeMillis() + "-" + event.hashCode();
                }

                // The roomId is not defined.
                event.roomId = roomId;
                mEventTimeline.handleLiveEvent(event, false, true);
            }
            // The room related to the pending invite can be considered as ready from now
            room.setReadyState(true);
        }
    }


}
