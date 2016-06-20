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

import android.text.TextUtils;
import android.util.Log;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.bingrules.BingRule;

import java.util.regex.Pattern;

/**
 * Utility methods for events.
 */
public class EventUtils {
    private static final String LOG_TAG = "EventUtils";

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
        if ((null == event) || (null == session)) {
            Log.e(LOG_TAG, "shouldNotify invalid params");
            return false;
        }

        // Only room events trigger notifications
        if (null == event.roomId) {
            Log.e(LOG_TAG, "shouldNotify null room ID");
            return false;
        }

        if (null == event.getSender()) {
            Log.e(LOG_TAG, "shouldNotify null room ID");
            return false;
        }

        // No notification if the user is currently viewing the room
        if (TextUtils.equals(event.roomId, activeRoomID)) {
            return false;
        }

        if (shouldHighlight(session, event)) {
            return true;
        }

        Room room = session.getDataHandler().getRoom(event.roomId);
        return RoomState.DIRECTORY_VISIBILITY_PRIVATE.equals(room.getVisibility())
                && !TextUtils.equals(event.getSender(), session.getCredentials().userId);
    }

    /**
     * Returns whether a string contains an occurrence of another, as a standalone word, regardless of case.
     * @param subString the string to search for
     * @param longString the string to search in
     * @return whether a match was found
     */
    public static boolean caseInsensitiveFind(String subString, String longString) {
        // add sanity checks
        if (TextUtils.isEmpty(subString) || TextUtils.isEmpty(longString)) {
            return false;
        }

        Pattern pattern = Pattern.compile("(\\W|^)" + subString + "(\\W|$)", Pattern.CASE_INSENSITIVE);
        return pattern.matcher(longString).find();
    }
}
