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
     * A live room event was received.
     * @param event the event
     * @param roomState the room state right before the event
     */
    public void onLiveEvent(Event event, RoomState roomState);

    /**
     * A back room event was received.
     * @param event the event
     * @param roomState the room state right before the event
     */
    public void onBackEvent(Event event, RoomState roomState);

    /**
     * A received event fulfills the bing rules
     *
     * @param event the event
     * @param roomState the room state right before the event
     */
    public void onBingEvent(Event event, RoomState roomState);

    /**
     * The bing rules have been updated
     *
     */
    public void onBingRulesUpdate();

    /**
     * The initial sync is complete and the store can be queried for current state.
     */
    public void onInitialSyncComplete();
}
