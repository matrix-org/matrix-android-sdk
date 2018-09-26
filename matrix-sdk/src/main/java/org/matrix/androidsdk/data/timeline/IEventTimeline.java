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
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.data.store.IMXStore;
import org.matrix.androidsdk.rest.model.Event;

interface IEventTimeline {
    void setIsHistorical(boolean isHistorical);

    boolean isHistorical();

    void initHistory();

    Room getRoom();

    IMXStore getStore();

    boolean isLiveTimeline();

    String getTimelineId();

    void handleLiveEvent(Event event, boolean checkRedactedStateEvent, boolean withPush);

    boolean canBackPaginate();

    void setCanBackPaginate(boolean canBackPaginate);

    /**
     * The direction from which an incoming event is considered.
     */
    enum Direction {
        /**
         * Forwards when the event is added to the end of the timeline.
         * These events come from the /sync stream or from forwards pagination.
         */
        FORWARDS,

        /**
         * Backwards when the event is added to the start of the timeline.
         * These events come from a back pagination.
         */
        BACKWARDS
    }

    interface EventTimelineListener {

        /**
         * Call when an event has been handled in the timeline.
         *
         * @param event     the event.
         * @param direction the direction.
         * @param roomState the room state
         */
        void onEvent(Event event, Direction direction, RoomState roomState);
    }
}
