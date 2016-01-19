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
import android.graphics.Color;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.Log;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import org.matrix.androidsdk.R;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.Message;
import org.matrix.androidsdk.rest.model.RoomMember;

public class EventDisplay {

    private static final String LOG_TAG = "EventDisplay";

    private Event mEvent;
    private Context mContext;
    private boolean mPrependAuthor;
    private RoomState mRoomState;

    public EventDisplay(Context context, Event event, RoomState roomState) {
        mContext = context.getApplicationContext();
        mEvent = event;
        mRoomState = roomState;
    }

    /**
     * <p>Prepend the text with the author's name if they have not been mentioned in the text.</p>
     * This will prepend text messages with the author's name. This will NOT prepend things like
     * emote, room topic changes, etc which already mention the author's name in the message.
     * @param prepend
     */
    public void setPrependMessagesWithAuthor(boolean prepend) {
        mPrependAuthor = prepend;
    }

    private static String getUserDisplayName(String userId, RoomState roomState, boolean desambigious) {
        if (null != roomState) {
            SpannableStringBuilder span = roomState.getMemberName(userId, desambigious ? Color.BLACK : null);

            if (null != span) {
                return span.toString();
            } else {
                return null;
            }

        } else {
            return userId;
        }
    }
    /**
     * Get the textual body for this event.
     * @return The text or null if it isn't possible.
     */
    public CharSequence getTextualDisplay() {
        return getTextualDisplay(false);
    }

    public CharSequence getTextualDisplay(boolean desambigious) {
        CharSequence text = null;
        try {
            JsonObject eventContent = mEvent.getContentAsJsonObject();

            String userDisplayName = getUserDisplayName(mEvent.getSender(), mRoomState, desambigious);

            if (mEvent.isCallEvent()) {
                if (Event.EVENT_TYPE_CALL_INVITE.equals(mEvent.type)) {
                    return mContext.getString(R.string.call_invitation);
                } else if (Event.EVENT_TYPE_CALL_ANSWER.equals(mEvent.type)) {
                    return mContext.getString(R.string.call_answered);
                } else if (Event.EVENT_TYPE_CALL_HANGUP.equals(mEvent.type)) {
                    return mContext.getString(R.string.call_hungup);
                } else {
                    return mEvent.type;
                }
            } else if (Event.EVENT_TYPE_RECEIPT.equals(mEvent.type)) {
                // the read receipt should not be displayed
                text = "Read Receipt";
            } else if (Event.EVENT_TYPE_MESSAGE.equals(mEvent.type)) {
                String msgtype = (null != eventContent.get("msgtype")) ? eventContent.get("msgtype").getAsString() : "";

                if (TextUtils.equals(msgtype, Message.MSGTYPE_IMAGE)) {
                    text = mContext.getString(R.string.summary_user_sent_image, userDisplayName);
                } else {
                    // all m.room.message events should support the 'body' key fallback, so use it.
                    text = eventContent.get("body") == null ? null : eventContent.get("body").getAsString();

                    // check for html formatting
                    if (eventContent.has("formatted_body") && eventContent.has("format")) {
                        String format = eventContent.getAsJsonPrimitive("format").getAsString();
                        if ("org.matrix.custom.html".equals(format)) {
                            text = Html.fromHtml(eventContent.getAsJsonPrimitive("formatted_body").getAsString());
                        }
                    }

                    if (TextUtils.equals(msgtype, Message.MSGTYPE_EMOTE)) {
                        text = "* " + userDisplayName +  " " + text;
                    } else if (mPrependAuthor) {
                        text = mContext.getString(R.string.summary_message, userDisplayName, text);
                    }
                }
            }
            else if (Event.EVENT_TYPE_STATE_ROOM_TOPIC.equals(mEvent.type)) {
                // pretty print 'XXX changed the topic to YYYY'
                text = mContext.getString(R.string.notice_topic_changed,
                        userDisplayName, eventContent.getAsJsonPrimitive("topic").getAsString());
            }
            else if (Event.EVENT_TYPE_STATE_ROOM_NAME.equals(mEvent.type)) {
                // pretty print 'XXX changed the room name to YYYY'
                text = mContext.getString(R.string.notice_room_name_changed,
                        userDisplayName, eventContent.getAsJsonPrimitive("name").getAsString());
            }
            else if (Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(mEvent.type)) {
                // m.room.member is used to represent at least 3 different changes in state: membership,
                // avatar pic url and display name. We need to figure out which thing changed to display
                // the right text.
                JsonObject prevState = mEvent.getPrevContentAsJsonObject();

                if (prevState == null) {
                    // if there is no previous state, it has to be an invite or a join as they are the first
                    // m.room.member events for a user.
                    text = getMembershipNotice(mContext, mEvent, mRoomState);
                }
                else {
                    // check if the membership changed
                    if (hasStringValueChanged(mEvent, "membership")) {
                        text = getMembershipNotice(mContext, mEvent, mRoomState);
                    }
                    // check if avatar url changed
                    else if (hasStringValueChanged(mEvent, "avatar_url")) {
                        text = getAvatarChangeNotice(mEvent, desambigious);
                    }
                    // check if the display name changed.
                    else if (hasStringValueChanged(mEvent, "displayname")) {
                        text = getDisplayNameChangeNotice(mEvent);
                    }
                    else {
                        // assume it is a membership notice
                        // some other members could also play with the application
                        text = getMembershipNotice(mContext, mEvent, mRoomState);
                    }
                }
            }
        }
        catch (Exception e) {
            Log.e(LOG_TAG, "getTextualDisplay() " + e);
        }

        return text;
    }

    public static String getMembershipNotice(Context context, Event msg, RoomState roomState) {
        return getMembershipNotice(context, msg, roomState, false);
    }

    public static String getMembershipNotice(Context context, Event msg, RoomState roomState, boolean desambigious) {
        JsonObject eventContent = (JsonObject)msg.content;
        String membership = eventContent.getAsJsonPrimitive("membership").getAsString();
        String userDisplayName = null;

        String prevMembership = null;

        if (null != msg.prevContent) {
            prevMembership = msg.getPrevContentAsJsonObject().getAsJsonPrimitive("membership").getAsString();
        }

        // the displayname could be defined in the event
        // use it instead of the getUserDisplayName result
        // the user could have joined the before his roomMember has been created.
        if (eventContent.has("displayname")) {
            userDisplayName =  eventContent.get("displayname") == JsonNull.INSTANCE ? null : eventContent.get("displayname").getAsString();
        }

        // cannot retrieve the display name from the event
        if (null == userDisplayName) {
            // retrieve it by the room members list
            userDisplayName = getUserDisplayName(msg.getSender(), roomState, desambigious);
        }

        if (RoomMember.MEMBERSHIP_INVITE.equals(membership)) {
            return context.getString(R.string.notice_room_invite, userDisplayName, getUserDisplayName(msg.stateKey, roomState, desambigious));
        }
        else if (RoomMember.MEMBERSHIP_JOIN.equals(membership)) {
            return context.getString(R.string.notice_room_join, userDisplayName);
        }
        else if (RoomMember.MEMBERSHIP_LEAVE.equals(membership)) {
            // 2 cases here: this member may have left voluntarily or they may have been "left" by someone else ie. kicked
            if (TextUtils.equals(msg.getSender(), msg.stateKey)) {
                return context.getString(R.string.notice_room_leave, userDisplayName);
            } else if (null != prevMembership) {
                if (prevMembership.equals(RoomMember.MEMBERSHIP_JOIN) || prevMembership.equals(RoomMember.MEMBERSHIP_INVITE)) {
                    return context.getString(R.string.notice_room_kick, userDisplayName, getUserDisplayName(msg.stateKey, roomState, desambigious));
                } else if (prevMembership.equals(RoomMember.MEMBERSHIP_BAN)) {
                    return context.getString(R.string.notice_room_unban, userDisplayName, getUserDisplayName(msg.stateKey, roomState, desambigious));
                }
            }
        }
        else if (RoomMember.MEMBERSHIP_BAN.equals(membership)) {
            return context.getString(R.string.notice_room_ban, userDisplayName, getUserDisplayName(msg.stateKey, roomState, desambigious));
        }
        else {
            // eh?
            Log.e(LOG_TAG, "Unknown membership: "+membership);
        }
        return null;
    }


    private String getAvatarChangeNotice(Event msg, boolean desambigious) {
        // TODO: Pictures!
        return mContext.getString(R.string.notice_avatar_url_changed, getUserDisplayName(msg.getSender(), mRoomState, desambigious));
    }

    private String getDisplayNameChangeNotice(Event msg) {
        return mContext.getString(R.string.notice_display_name_changed,
                msg.getSender(),
                ((JsonObject)msg.content).getAsJsonPrimitive("displayname").getAsString()
        );
    }

    private boolean hasStringValueChanged(Event msg, String key) {
        JsonObject prevContent = msg.getPrevContentAsJsonObject();
        JsonObject eventContent = msg.getContentAsJsonObject();

        if (prevContent.has(key) && eventContent.has(key)) {
            String old = prevContent.get(key) == JsonNull.INSTANCE ? null : prevContent.get(key).getAsString();
            String current = eventContent.get(key) == JsonNull.INSTANCE ? null : eventContent.get(key).getAsString();

            return !TextUtils.equals(old, current);
        }
        else if (!prevContent.has(key) && !eventContent.has(key)) {
            return false; // this key isn't in either prev or current
        }
        else {
            return true; // this key is in one but not the other.
        }
    }
}