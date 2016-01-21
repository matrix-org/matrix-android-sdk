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

import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.rest.model.bingrules.BingRule;

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
     * The live events from a chunk are performed.
     */
    public void onLiveEventsChunkProcessed();

    /**
     * A back room event was received.
     * @param event the event
     * @param roomState the room state right before the event
     */
    public void onBackEvent(Event event, RoomState roomState);

    /**
     * A received event fulfills the bing rules
     * The first matched bing rule is provided in paramater to perform
     * dedicated action like playing a notification sound.
     *
     * @param event the event
     * @param roomState the room state right before the event
     * @param bingRule the bing rule
     */
    public void onBingEvent(Event event, RoomState roomState, BingRule bingRule);

    /**
     * An event has been deleted
     *
     * @param event the event
     */
    public void onDeleteEvent(Event event);

    /**
     * An event is automatically resending.
     *
     * @param event the event
     */
    public void onResendingEvent(Event event);

    /**
     * An event has been automatically resent
     *
     * @param event the event
     */
    public void onResentEvent(Event event);

    /**
     * The bing rules have been updated
     */
    public void onBingRulesUpdate();

    /**
     * The initial sync is complete and the store can be queried for current state.
     */
    public void onInitialSyncComplete();

    /**
     * User presences was synchronized..
     */
    public void onPresencesSyncComplete();

    /**
     * A new room has been created.
     *
     * @param roomId the roomID
     */
    public void onNewRoom(String roomId);

    /**
     * The user joined a room.
     *
     * @param roomId the roomID
     */
    public void onJoinRoom(String roomId);

    /**
     * The room initial sync is completed.
     * It is triggered after retrieving the room info and performing a first requestHistory
     *
     * @param roomId the roomID
     */
    public void onRoomInitialSyncComplete(String roomId);

    /**
     * The room data has been internally updated.
     * It could be triggered when a request failed.
     *
     * @param roomId the roomID
     */
    public void onRoomInternalUpdate(String roomId);

    /**
     * The user left the room.
     *
     * @param roomId the roomID
     */
    public void onLeaveRoom(String roomId);

    /**
     * A receipt event has been received.
     * It could be triggered when a request failed.
     *
     * @param roomId the roomID
     */
    public void onReceiptEvent(String roomId);

    /**
     * A Room Tag event has been received.
     *
     * @param roomId the roomID
     */
    public void onRoomTagEvent(String roomId);

    /**
     * A room has been resynced with a limited timeline
     * @param roomId the room Id
     */
    public void onRoomSyncWithLimitedTimeline(String roomId);
}
