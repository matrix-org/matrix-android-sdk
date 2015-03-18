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
package org.matrix.androidsdk.rest.model;

import android.widget.Toast;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;

/**
 * Generic event class with all possible fields for events.
 */
public class Event {

    public enum SentState {
        UNSENT,  // the event has not been sent
        SENDING, // the event is currently sending
        WAITING_RETRY, // the event is going to be resent asap
        WAITING_ECHO, // the event is sent to the server but it does not aknowledge it.
        SENT,    // the event has been sent
        UNDELIVERABLE   // The event failed to be sent
    }

    public static final String EVENT_TYPE_PRESENCE = "m.presence";
    public static final String EVENT_TYPE_MESSAGE = "m.room.message";
    public static final String EVENT_TYPE_FEEDBACK = "m.room.message.feedback";
    public static final String EVENT_TYPE_TYPING = "m.typing";
    public static final String EVENT_TYPE_REDACTION = "m.room.redaction";

    // State events
    public static final String EVENT_TYPE_STATE_ROOM_NAME = "m.room.name";
    public static final String EVENT_TYPE_STATE_ROOM_TOPIC = "m.room.topic";
    public static final String EVENT_TYPE_STATE_ROOM_MEMBER = "m.room.member";
    public static final String EVENT_TYPE_STATE_ROOM_CREATE = "m.room.create";
    public static final String EVENT_TYPE_STATE_ROOM_JOIN_RULES = "m.room.join_rules";
    public static final String EVENT_TYPE_STATE_ROOM_POWER_LEVELS = "m.room.power_levels";
    public static final String EVENT_TYPE_STATE_ROOM_ALIASES = "m.room.aliases";

    public String type;
    public JsonObject content;

    public String eventId;
    public String roomId;
    public String userId;
    public long originServerTs;
    public long age;

    // Specific to state events
    public String stateKey;
    public JsonObject prevContent;

    // Specific to redactions
    public String redacts;

    // store the exception triggered when unsent
    public Exception unsentException = null;
    public MatrixError unsentMatrixError = null;

    // sent state
    public SentState mSentState = SentState.SENT;

    /**
     * Some events are not sent by the server.
     * They are temporary stored until to get the server response.
     */
    public void createDummyEventId() {
        eventId = roomId + "-" + originServerTs;
        age = Long.MAX_VALUE;
    }

    public boolean isDummyEvent() {
        return (roomId + "-" + originServerTs).equals(eventId);
    }

    /**
     * Make a deep copy of this room state object.
     * @return the copy
     */
    public Event deepCopy() {
        Event copy = new Event();
        copy.type = type;
        copy.content = content;

        copy.eventId = eventId;
        copy.roomId = roomId;
        copy.userId = userId;
        copy.originServerTs = originServerTs;
        copy.age = age;

        copy.stateKey = stateKey;
        copy.prevContent = prevContent;

        copy.redacts = redacts;

        copy.mSentState = mSentState;

        copy.unsentException = unsentException;
        copy.unsentMatrixError = unsentMatrixError;
        return copy;
    }

    /**
     * Check if the current event can resent.
     * @return true if it can be resent.
     */
    public boolean canBeResent() {
        return (mSentState == SentState.WAITING_RETRY) || (mSentState == SentState.UNDELIVERABLE);
    }

    /**
     * Check if the current event is sending.
     * @return true if it is sending.
     */
    public boolean isSending() {
        return (mSentState == SentState.SENDING) || (mSentState == SentState.WAITING_RETRY) || (mSentState == SentState.WAITING_ECHO);
    }

    /**
     * Check if the current event failed to be sent
     * @return true if the event failed to be sent.
     */
    public boolean isUndeliverable() {
        return (mSentState == SentState.UNDELIVERABLE);
    }

    /**
     * Check if the current event has not been acknowledged.
     * @return true if the event has not been acknowledged.
     */
    public boolean isWaitingForEcho () {
        return (mSentState == SentState.WAITING_ECHO);
    }

    /**
     * Check if the current event is sent.
     * @return true if it is sent.
     */
    public boolean isSent() {
        return (mSentState == SentState.SENT);
    }

    @Override
    public java.lang.String toString() {

        // build the string by hand
        String text = "{\n" ;

        text += "  \"age\" : " + age + ",\n";

        text += "  \"content\" {\n";

        if (null != content) {
            if (content.isJsonArray()) {
                for (JsonElement e : content.getAsJsonArray()) {
                    text += "   " + e.toString() + "\n,";
                }
            } else if (content.isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : content.getAsJsonObject().entrySet()) {
                    text += "    \"" + e.getKey() + ": " + e.getValue().toString() + ",\n";
                }
            } else {
                text += content.toString();
            }
        }

        text += "  },\n";

        text += "  \"eventId\": \"" + eventId + "\",\n";
        text += "  \"originServerTs\": " + originServerTs +",\n";
        text += "  \"roomId\": \"" + roomId + "\",\n";
        text += "  \"type\": \"" + type + "\",\n";
        text += "  \"userId\": \"" + userId + "\"\n";

        text += "  \"\n\n Sent state : ";

        if (mSentState == SentState.UNSENT) {
            text += "UNSENT";
        } else if (mSentState == SentState.SENDING) {
            text += "SENDING";
        } else if (mSentState == SentState.WAITING_RETRY) {
            text += "WAITING_RETRY";
        } else if (mSentState == SentState.WAITING_ECHO) {
            text += "WAITING_ECHO";
        } else if (mSentState == SentState.SENT) {
            text += "SENT";
        } else if (mSentState == SentState.UNDELIVERABLE) {
            text += "UNDELIVERABLE";
        }

        text += "\n\n";

        if (null != unsentException) {
            text += "\n\n Exception reason: " + unsentException.getMessage() + "\n";
        }

        if (null != unsentMatrixError) {
            text += "\n\n Matrix reason: " + unsentMatrixError.error + "\n";
        }

        text += "}";

        return text;
    }
}
