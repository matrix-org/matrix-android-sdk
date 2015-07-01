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
package org.matrix.androidsdk.util;

import android.content.Context;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.Message;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.bingrules.BingRule;

import java.util.regex.Pattern;

/**
 * Utility methods for events.
 */
public class EventUtils {

    /**
     * Whether the given event should be highlighted in its chat room.
     * @param session the session.
     * @param event the event
     * @return whether the event is important and should be highlighted
     */
    public static boolean shouldHighlight(MXSession session, Event event) {
        // sanity check
        if ((null == session) || (null == event)) {
            return false;
        }

        // search if the event fulfills a rule
        BingRule rule = session.fulfillRule(event);

        if (null != rule) {
            return rule.shouldHighlight();
        }

        return false;
    }

    /**
     * Whether the given event should trigger a notification.
     * @param session the current matrix session
     * @param event the event
     * @param activeRoomID the RoomID of disaplyed roomActivity
     * @return true if the event should trigger a notification
     */
    public static boolean shouldNotify(MXSession session, Event event, String activeRoomID) {
        // Only room events trigger notifications
        if (event.roomId == null) {
            return false;
        }

        // No notification if the user is currently viewing the room
        if (event.roomId.equals(activeRoomID)) {
            return false;
        }

        if (shouldHighlight(session, event)) {
            return true;
        }

        Room room = session.getDataHandler().getRoom(event.roomId);
        if (RoomState.VISIBILITY_PRIVATE.equals(room.getVisibility())
                && !event.userId.equals(session.getCredentials().userId)) {
            return true;
        }
        return false;
    }
}
