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

/**
 * A no-op class implementing {@link IMXEventListener} so listeners can just implement the methods
 * that they require.
 */
public class MXEventListener implements IMXEventListener {

    @Override
    public void onPresenceUpdate(Event event, User user) {

    }

    @Override
    public void onLiveEvent(Event event, RoomState roomState) {

    }

    @Override
    public void onBackEvent(Event event, RoomState roomState) {

    }

    @Override
    public void onBingEvent(Event event, RoomState roomState) {

    }

    @Override
    public void onBingRulesUpdate() {

    }

    @Override
    public void onInitialSyncComplete() {

    }
}
