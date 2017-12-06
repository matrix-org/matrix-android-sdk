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
package org.matrix.androidsdk.adapters;

import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.rest.model.Event;

// this class defines a MessagesAdapter Item.
public class MessageRow {

    // the linked event
    private Event mEvent;
    // the room state
    private final RoomState mRoomState;

    /**
     * Constructor
     *
     * @param event     the event.
     * @param roomState the room state
     */
    public MessageRow(Event event, RoomState roomState) {
        this.mEvent = event;
        this.mRoomState = roomState;
    }


    /**
     * @return the event.
     */
    public Event getEvent() {
        return mEvent;
    }

    /**
     * Update the linked event.
     *
     * @param event the event.
     */
    public void updateEvent(Event event) {
        mEvent = event;
    }

    /**
     * @return the room state.
     */
    public RoomState getRoomState() {
        return mRoomState;
    }
}
