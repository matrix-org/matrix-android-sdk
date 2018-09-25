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

import android.support.annotation.NonNull;

import org.matrix.androidsdk.MXDataHandler;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.data.RoomSummary;
import org.matrix.androidsdk.data.store.IMXStore;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.ReceiptData;

class TimelineEventSaver {

    private final IEventTimeline mEventTimeline;
    private final TimelineStateHolder mTimelineStateHolder;

    TimelineEventSaver(@NonNull final IEventTimeline eventTimeline, @NonNull final TimelineStateHolder timelineStateHolder) {
        mEventTimeline = eventTimeline;
        mTimelineStateHolder = timelineStateHolder;
    }

    /**
     * * Store a live room event.
     *
     * @param event the event to be stored.
     */

    public void storeEvent(Event event) {
        final IMXStore store = mEventTimeline.getStore();
        final Room room = mEventTimeline.getRoom();
        final MXDataHandler dataHandler = room.getDataHandler();
        final String myUserId = dataHandler.getCredentials().userId;

        // create dummy read receipt for any incoming event
        // to avoid not synchronized read receipt and event
        if ((null != event.getSender()) && (null != event.eventId)) {
            room.handleReceiptData(new ReceiptData(event.getSender(), event.eventId, event.originServerTs));
        }
        store.storeLiveRoomEvent(event);
        if (RoomSummary.isSupportedEvent(event)) {
            final RoomState roomState = mTimelineStateHolder.getState();
            RoomSummary summary = store.getSummary(event.roomId);
            if (null == summary) {
                summary = new RoomSummary(summary, event, roomState, myUserId);
            } else {
                summary.setLatestReceivedEvent(event, roomState);
            }
            store.storeSummary(summary);
        }
    }

}
