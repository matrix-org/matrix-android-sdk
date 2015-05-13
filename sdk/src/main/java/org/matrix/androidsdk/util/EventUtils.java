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

import java.util.regex.Pattern;

/**
 * Utility methods for events.
 */
public class EventUtils {

    /**
     * Whether the given event should be highlighted in its chat room.
     * @param context the context
     * @param event the event
     * @return whether the event is important and should be highlighted
     */
    public static boolean shouldHighlight(MXSession session, Context context, Event event) {
        if (!Event.EVENT_TYPE_MESSAGE.equals(event.type)) {
            return false;
        }

        String myUserId = session.getCredentials().userId;

        // Don't highlight the user's own messages
        if (event.userId.equals(myUserId)) {
            return false;
        }

        Message msg = JsonUtils.toMessage(event.content);
        String body = msg.body;

        // Extract "bob" from "@bob:matrix.org"
        String namePart = myUserId.substring(1, myUserId.indexOf(':'));
        if (caseInsensitiveFind(namePart, body)) {
            return true;
        }
        Room room = session.getDataHandler().getRoom(event.roomId);
        RoomMember myMember = room.getMember(myUserId);

        // myMember should never be null
        if ((null != myMember) && caseInsensitiveFind(myMember.displayname, body)) {
            return true;
        }

        return false;
    }

    /**
     * Returns whether a string contains an occurrence of another, as a standalone word, regardless of case.
     * @param subString the string to search for
     * @param longString the string to search in
     * @return whether a match was found
     */
    private static boolean caseInsensitiveFind(String subString, String longString) {
        Pattern pattern = Pattern.compile("(\\W|^)" + subString + "(\\W|$)", Pattern.CASE_INSENSITIVE);
        return pattern.matcher(longString).find();
    }

    /**
     * Whether the given event should trigger a notification.
     * @param session the current matrix session
     * @param context the context
     * @param event the event
     * @param activeRoomID the RoomID of disaplyed roomActivity
     * @return true if the event should trigger a notification
     */
    public static boolean shouldNotify(MXSession session, Context context, Event event, String activeRoomID) {
        // Only room events trigger notifications
        if (event.roomId == null) {
            return false;
        }

        // No notification if the user is currently viewing the room
        if (event.roomId.equals(activeRoomID)) {
            return false;
        }

        if (shouldHighlight(session, context, event)) {
            return true;
        }

        if (!Event.EVENT_TYPE_MESSAGE.equals(event.type)) {
            return false;
        }

        Room room = session.getDataHandler().getRoom(event.roomId);
        if (RoomState.VISIBILITY_PRIVATE.equals(room.getVisibility())
                && !event.userId.equals(session.getCredentials().userId)) {
            return true;
        }
        return false;
    }
}
