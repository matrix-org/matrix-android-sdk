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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.matrix.androidsdk.util.JsonUtils;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Generic event class with all possible fields for events.
 */
public class Event implements java.io.Serializable {

    public enum SentState {
        UNSENT,  // the event has not been sent
        SENDING, // the event is currently sending
        WAITING_RETRY, // the event is going to be resent asap
        SENT,    // the event has been sent
        UNDELIVERABLE   // The event failed to be sent
    }

    // when there is no more message to be paginated in a room
    // the server returns a null token.
    // defines by a non null one to ben able tp store it.
    public static final String PAGINATE_BACK_TOKEN_END = "PAGINATE_BACK_TOKEN_END";

    public static final String EVENT_TYPE_PRESENCE = "m.presence";
    public static final String EVENT_TYPE_MESSAGE = "m.room.message";
    public static final String EVENT_TYPE_FEEDBACK = "m.room.message.feedback";
    public static final String EVENT_TYPE_TYPING = "m.typing";
    public static final String EVENT_TYPE_REDACTION = "m.room.redaction";
    public static final String EVENT_TYPE_RECEIPT = "m.receipt";
    public static final String EVENT_TYPE_TAGS = "m.tag";

    // State events
    public static final String EVENT_TYPE_STATE_ROOM_NAME = "m.room.name";
    public static final String EVENT_TYPE_STATE_ROOM_TOPIC = "m.room.topic";
    public static final String EVENT_TYPE_STATE_ROOM_AVATAR = "m.room.avatar";
    public static final String EVENT_TYPE_STATE_ROOM_MEMBER = "m.room.member";
    public static final String EVENT_TYPE_STATE_ROOM_THIRD_PARTY_INVITE = "m.room.third_party_invite";
    public static final String EVENT_TYPE_STATE_ROOM_CREATE = "m.room.create";
    public static final String EVENT_TYPE_STATE_ROOM_JOIN_RULES = "m.room.join_rules";
    public static final String EVENT_TYPE_STATE_ROOM_GUEST_ACCESS = "m.room.guest_access";
    public static final String EVENT_TYPE_STATE_ROOM_POWER_LEVELS = "m.room.power_levels";
    public static final String EVENT_TYPE_STATE_ROOM_ALIASES = "m.room.aliases";
    public static final String EVENT_TYPE_STATE_CANONICAL_ALIAS = "m.room.canonical_alias";
    public static final String EVENT_TYPE_STATE_HISTORY_VISIBILITY = "m.room.history_visibility";

    // call events
    public static final String EVENT_TYPE_CALL_INVITE = "m.call.invite";
    public static final String EVENT_TYPE_CALL_CANDIDATES = "m.call.candidates";
    public static final String EVENT_TYPE_CALL_ANSWER = "m.call.answer";
    public static final String EVENT_TYPE_CALL_HANGUP = "m.call.hangup";

    public String type;
    public transient JsonElement content = null;
    private String contentAsString = null;

    public transient JsonElement prev_content = null;
    private String prev_content_as_string = null;

    public String eventId;
    public String roomId;
    // former Sync V1 sender name
    public String userId;
    // Sync V2 sender name
    public String sender;
    public long originServerTs;
    public Long age;

    // Specific to state events
    public String stateKey;

    // Contains optional extra information about the event.
    public UnsignedData unsigned;

    // Specific to redactions
    public String redacts;

    // A subset of the state of the room at the time of the invite, if membership is invite
    public List<Event> invite_room_state;

    // store the exception triggered when unsent
    public Exception unsentException = null;
    public MatrixError unsentMatrixError = null;

    // sent state
    public SentState mSentState = SentState.SENT;

    // save the token to back paginate
    // the room history could have been reduced to save memory.
    // so store the token from each event.
    public String mToken;

    // The file cache uses the token as a pagination marker.
    // When the user paginates, the file cache paginate until to find X events or an event with a token.
    // This token must be used to perform a server catchup.
    public boolean mIsInternalPaginationToken;

    // store the linked matrix id
    private String mMatrixId;

    // the time raw offset (time zone management)
    private long mTimeZoneRawOffset = 0;

    private long getTimeZoneOffset() {
        return TimeZone.getDefault().getRawOffset();
    }

    /**
     * Default constructor
     */
    public Event() {
        type = null;
        content = null;
        prev_content = null;
        mIsInternalPaginationToken = false;

        userId = roomId = eventId = null;
        originServerTs = 0;
        age = null;

        mTimeZoneRawOffset = getTimeZoneOffset();

        stateKey = null;
        redacts = null;

        unsentMatrixError = null;
        unsentException = null;

        mMatrixId = null;

        mSentState = SentState.SENT;
    }

    public String getSender() {
        return (null == sender) ? userId : sender;
    }

    public void setSender(String aSender) {
        sender = userId = aSender;
    }

    public void setMatrixId(String aMatrixId) {
        mMatrixId = aMatrixId;
    }

    public String getMatrixId() {
        return mMatrixId;
    }

    static final long MAX_ORIGIN_SERVER_TS = 1L << 50L;

    public boolean isValidOriginServerTs() {
        long a = MAX_ORIGIN_SERVER_TS;
        return originServerTs < MAX_ORIGIN_SERVER_TS;
    }

    public long getOriginServerTs() {
        return originServerTs;
    }

    public void updateContent(JsonElement newContent) {
        content = newContent;
        contentAsString = null;
    }

    static DateFormat mDateFormat = null;
    static long mFormatterRawOffset = 1234;

    public String formattedOriginServerTs() {
        // avoid displaying weird orign ts
        if (!isValidOriginServerTs()) {
            return " ";
        } else {
            // the formatter must be updated if the timezone has been updated
            // else the formatted string are wrong (does not use the current timezone)
            if ((null == mDateFormat) || (mFormatterRawOffset != getTimeZoneOffset())) {
                mDateFormat = new SimpleDateFormat("MMM d HH:mm", Locale.getDefault());
                mFormatterRawOffset = getTimeZoneOffset();
            }

            return mDateFormat.format(new Date(getOriginServerTs()));
        }
    }

    public void setOriginServerTs(long anOriginServer) {
        originServerTs = anOriginServer;
    }

    public JsonObject getContentAsJsonObject() {
        if ((null != content) && content.isJsonObject()) {
            return content.getAsJsonObject();
        }
        return null;
    }

    public JsonObject getPrevContentAsJsonObject() {
        if ((null != unsigned) && (null != unsigned.prev_content)) {
            // avoid getting two value for the same thing
            if (null == prev_content) {
                prev_content = unsigned.prev_content;
            }
            unsigned.prev_content = null;
        }

        if ((null != prev_content) && prev_content.isJsonObject()) {
            return prev_content.getAsJsonObject();
        }
        return null;
    }

    public EventContent getEventContent() {
        if (null != content) {
            return JsonUtils.toEventContent(content);
        }
        return null;
    }

    public EventContent getPrevContent() {
        if (null != getPrevContentAsJsonObject()) {
            return JsonUtils.toEventContent(getPrevContentAsJsonObject());
        }
        return null;
    }

    public long getAge() {
        if (null != age) {
            return age;
        } else if ((null != unsigned) && (null != unsigned.age)) {
            age = unsigned.age;

            return age;
        }

        return Long.MAX_VALUE;
    }

    public String getRedacts() {
        if (null != redacts) {
            return redacts;
        } else  if ((null != unsigned) && (null != unsigned.redacted_because)) {
            redacts = unsigned.redacted_because.redacts;
            return redacts;
        }

        return null;
    }

    /**
     * Create an event from a message.
     *
     * @param message  the event content
     * @param anUserId the event user Id
     * @param aRoomId  the vent room Id
     */
    public Event(Message message, String anUserId, String aRoomId) {
        type = Event.EVENT_TYPE_MESSAGE;
        content = JsonUtils.toJson(message);
        originServerTs = System.currentTimeMillis();
        sender = userId = anUserId;
        roomId = aRoomId;
        mSentState = Event.SentState.SENDING;
        createDummyEventId();
    }

    /**
     * Create an event from a content and a type.
     *
     * @param aType  the event type
     * @param aContent the event content
     * @param anUserId the event user Id
     * @param aRoomId  the vent room Id
     */
    public Event(String aType, JsonObject aContent, String anUserId, String aRoomId) {
        type = aType;
        content = aContent;
        originServerTs = System.currentTimeMillis();
        sender = userId = anUserId;
        roomId = aRoomId;
        mSentState = Event.SentState.SENDING;
        createDummyEventId();
    }

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

    public void setIntenalPaginationToken(String token) {
        mToken = token;
        mIsInternalPaginationToken = true;
    }

    public boolean isIntenalPaginationToken() {
        return  mIsInternalPaginationToken;
    }

    public boolean hasToken() {
        return (null != mToken) && !mIsInternalPaginationToken;
    }

    public boolean isCallEvent() {
        return  EVENT_TYPE_CALL_INVITE.equals(type) ||
                EVENT_TYPE_CALL_CANDIDATES.equals(type) ||
                EVENT_TYPE_CALL_ANSWER.equals(type) ||
                EVENT_TYPE_CALL_HANGUP.equals(type);
    }

    /**
     * Make a deep copy of this room state object.
     *
     * @return the copy
     */
    public Event deepCopy() {
        Event copy = new Event();
        copy.type = type;
        copy.content = content;
        copy.contentAsString = contentAsString;

        copy.eventId = eventId;
        copy.roomId = roomId;
        copy.userId = userId;
        copy.sender = sender;
        copy.originServerTs = originServerTs;
        copy.mTimeZoneRawOffset = mTimeZoneRawOffset;
        copy.age = age;

        copy.stateKey = stateKey;
        copy.prev_content = prev_content;

        copy.unsigned = unsigned;
        copy.invite_room_state = invite_room_state;
        copy.redacts = redacts;

        copy.mSentState = mSentState;

        copy.unsentException = unsentException;
        copy.unsentMatrixError = unsentMatrixError;

        copy.mMatrixId = mMatrixId;
        copy.mToken = mToken;
        copy.mIsInternalPaginationToken = mIsInternalPaginationToken;

        return copy;
    }

    /**
     * Check if the current event can resent.
     *
     * @return true if it can be resent.
     */
    public boolean canBeResent() {
        return (mSentState == SentState.WAITING_RETRY) || (mSentState == SentState.UNDELIVERABLE);
    }

    /**
     * Check if the current event is sending.
     *
     * @return true if it is sending.
     */
    public boolean isSending() {
        return (mSentState == SentState.SENDING) || (mSentState == SentState.WAITING_RETRY);
    }

    /**
     * Check if the current event failed to be sent
     *
     * @return true if the event failed to be sent.
     */
    public boolean isUndeliverable() {
        return (mSentState == SentState.UNDELIVERABLE);
    }

    /**
     * Check if the current event is sent.
     *
     * @return true if it is sent.
     */
    public boolean isSent() {
        return (mSentState == SentState.SENT);
    }

    @Override
    public java.lang.String toString() {

        // build the string by hand
        String text = "{\n";

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
        text += "  \"originServerTs\": " + originServerTs + ",\n";
        text += "  \"roomId\": \"" + roomId + "\",\n";
        text += "  \"type\": \"" + type + "\",\n";
        text += "  \"userId\": \"" + userId + "\"\n";
        text += "  \"sender\": \"" + sender + "\"\n";


        text += "  \"\n\n Sent state : ";

        if (mSentState == SentState.UNSENT) {
            text += "UNSENT";
        } else if (mSentState == SentState.SENDING) {
            text += "SENDING";
        } else if (mSentState == SentState.WAITING_RETRY) {
            text += "WAITING_RETRY";
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
            text += "\n\n Matrix reason: " + unsentMatrixError.getLocalizedMessage() + "\n";
        }

        text += "}";

        return text;
    }

    public void prepareSerialization() {
        if ((null != content) && (null == contentAsString)) {
            contentAsString = content.toString();
        }

        if ((null != getPrevContentAsJsonObject()) && (null == prev_content_as_string)) {
            prev_content_as_string = getPrevContentAsJsonObject().toString();
        }

        if ((null != unsigned) && (null != unsigned.prev_content)) {
            unsigned.prev_content = null;
        }
    }

    public void finalizeDeserialization() {
        if ((null != contentAsString) && (null == content)) {
            try {
                content = new JsonParser().parse(contentAsString).getAsJsonObject();
            } catch (Exception e) {
            }
        }

        if ((null != prev_content_as_string) && (null == prev_content)) {
            try {
                prev_content = new JsonParser().parse(prev_content_as_string).getAsJsonObject();
            } catch (Exception e) {
            }
        }
    }
}
