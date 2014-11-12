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
package org.matrix.androidsdk.listeners;

import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.User;

public interface IMXEventListener {

    /**
     * User presence was updated.
     * @param event The presence event.
     * @param user The new user value.
     */
    public void onPresenceUpdate(Event event, User user);

    /**
     * An m.room.message was received.
     * @param event The event representing the message.
//     * @param roomState The state of the room at the time of the message.
//     * @param direction The event direction.
     */
//    public void onMessageEvent(Event event, RoomState roomState, Room.EventDirection direction);
    public void onMessageEvent(Room room, Event event);

    public void onLiveEvent(Event event, RoomState roomState);

    public void onBackEvent(Event event, RoomState roomState);

    public void onRoomReady(Room room);

    /**
     * Room state has been updated via a room state event.
     * @param room The room which has been updated (post-update).
     * @param event The event which updated this room.
     * @param oldVal The previous state value if the event is a known state event, else null.
     * @param newVal The new state value if the event is a known state event.
     */
    public void onRoomStateUpdated(Room room, Event event, Object oldVal, Object newVal);

    /**
     * The initial sync is complete and the store can be queried for current state.
     */
    public void onInitialSyncComplete();

    /**
     * Called when the user is invited to a room via /initialSync.
     * @param room The room the user has been invited to.
     */
    public void onInvitedToRoom(Room room);
}
