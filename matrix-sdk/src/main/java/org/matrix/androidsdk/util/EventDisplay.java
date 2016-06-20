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
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.Log;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.matrix.androidsdk.MXDataHandler;
import org.matrix.androidsdk.R;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.EventContent;
import org.matrix.androidsdk.rest.model.Message;
import org.matrix.androidsdk.rest.model.RedactedBecause;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.RoomThirdPartyInvite;

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

    private static String getUserDisplayName(String userId, RoomState roomState) {
        if (null != roomState) {
            return roomState.getMemberName(userId);
        } else {
            return userId;
        }
    }
    /**
     * Get the textual body for this event.
     * @return The text or null if it isn't possible.
     */
    public CharSequence getTextualDisplay() {
        return getTextualDisplay(null);
    }

    public CharSequence getTextualDisplay(Integer displayNameColor) {

        CharSequence text = null;
        try {
            JsonObject jsonEventContent = mEvent.getContentAsJsonObject();

            String userDisplayName = getUserDisplayName(mEvent.getSender(), mRoomState);

            if (mEvent.isCallEvent()) {
                if (Event.EVENT_TYPE_CALL_INVITE.equals(mEvent.type)) {
                    boolean isVideo = false;
                    // detect call type from the sdp
                    try {
                        JsonObject offer = jsonEventContent.get("offer").getAsJsonObject();
                        JsonElement sdp = offer.get("sdp");
                        String sdpValue = sdp.getAsString();
                        isVideo = sdpValue.indexOf("m=video") >= 0;
                    } catch (Exception e) {
                    }

                    if (isVideo) {
                        return mContext.getString(R.string.notice_placed_video_call, userDisplayName);
                    } else {
                        return mContext.getString(R.string.notice_placed_voice_call, userDisplayName);
                    }
                } else if (Event.EVENT_TYPE_CALL_ANSWER.equals(mEvent.type)) {
                    return mContext.getString(R.string.notice_answered_call, userDisplayName);
                } else if (Event.EVENT_TYPE_CALL_HANGUP.equals(mEvent.type)) {
                    return mContext.getString(R.string.notice_ended_call, userDisplayName);
                } else {
                    return mEvent.type;
                }
            } else if (Event.EVENT_TYPE_STATE_HISTORY_VISIBILITY.equals(mEvent.type)) {
                CharSequence subpart;
                String historyVisibility = (null != jsonEventContent.get("history_visibility")) ? jsonEventContent.get("history_visibility").getAsString() : RoomState.HISTORY_VISIBILITY_SHARED;

                if (TextUtils.equals(historyVisibility, RoomState.HISTORY_VISIBILITY_SHARED)) {
                    subpart = mContext.getString(R.string.notice_room_visibility_shared);
                } else if (TextUtils.equals(historyVisibility, RoomState.HISTORY_VISIBILITY_INVITED)) {
                    subpart = mContext.getString(R.string.notice_room_visibility_invited);
                } else if (TextUtils.equals(historyVisibility, RoomState.HISTORY_VISIBILITY_JOINED)) {
                    subpart = mContext.getString(R.string.notice_room_visibility_joined);
                } else if (TextUtils.equals(historyVisibility, RoomState.HISTORY_VISIBILITY_WORLD_READABLE)) {
                    subpart = mContext.getString(R.string.notice_room_visibility_world_readable);
                } else {
                    subpart = mContext.getString(R.string.notice_room_visibility_unknown, historyVisibility);
                }

                text = mContext.getString(R.string.notice_made_future_room_visibility, userDisplayName, subpart);
            } else if (Event.EVENT_TYPE_RECEIPT.equals(mEvent.type)) {
                // the read receipt should not be displayed
                text = "Read Receipt";
            } else if (Event.EVENT_TYPE_MESSAGE.equals(mEvent.type)) {
                String msgtype = (null != jsonEventContent.get("msgtype")) ? jsonEventContent.get("msgtype").getAsString() : "";

                if (TextUtils.equals(msgtype, Message.MSGTYPE_IMAGE)) {
                    text = mContext.getString(R.string.summary_user_sent_image, userDisplayName);
                } else {
                    // all m.room.message events should support the 'body' key fallback, so use it.
                    text = jsonEventContent.get("body") == null ? null : jsonEventContent.get("body").getAsString();

                    // check for html formatting
                    if (jsonEventContent.has("formatted_body") && jsonEventContent.has("format")) {
                        String format = jsonEventContent.getAsJsonPrimitive("format").getAsString();
                        if ("org.matrix.custom.html".equals(format)) {
                            String htmlBody = jsonEventContent.getAsJsonPrimitive("formatted_body").getAsString();

                            // some markers are not supported so fallback on an ascii display until to find the right way to manage them
                            // an issue has been created https://github.com/vector-im/vector-android/issues/38
                            if (!TextUtils.isEmpty(htmlBody) && (htmlBody.indexOf("<ol>") < 0) && (htmlBody.indexOf("<li>") < 0)) {
                                text = Html.fromHtml(jsonEventContent.getAsJsonPrimitive("formatted_body").getAsString());
                            }
                        }
                    }

                    if (TextUtils.equals(msgtype, Message.MSGTYPE_EMOTE)) {
                        text = "* " + userDisplayName +  " " + text;
                    } else if (mPrependAuthor) {
                        text = new SpannableStringBuilder(mContext.getString(R.string.summary_message, userDisplayName, text));

                        if (null != displayNameColor) {
                            ((SpannableStringBuilder)text).setSpan(new ForegroundColorSpan(displayNameColor), 0, userDisplayName.length()+1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                            ((SpannableStringBuilder)text).setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, userDisplayName.length()+1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        }
                    }
                }
            }
            else if (Event.EVENT_TYPE_STATE_ROOM_TOPIC.equals(mEvent.type)) {
                // pretty print 'XXX changed the topic to YYYY'
                text = mContext.getString(R.string.notice_topic_changed,
                        userDisplayName, jsonEventContent.getAsJsonPrimitive("topic").getAsString());
            }
            else if (Event.EVENT_TYPE_STATE_ROOM_NAME.equals(mEvent.type)) {
                // pretty print 'XXX changed the room name to YYYY'
                text =  mContext.getString(R.string.notice_room_name_changed,
                        userDisplayName, jsonEventContent.getAsJsonPrimitive("name").getAsString());
            }
            else if (Event.EVENT_TYPE_STATE_ROOM_THIRD_PARTY_INVITE.equals(mEvent.type)) {
                RoomThirdPartyInvite invite = JsonUtils.toRoomThirdPartyInvite(mEvent.content);
                text =  mContext.getString(R.string.notice_room_third_party_invite, userDisplayName, invite.display_name);
            }
            else if (Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(mEvent.type)) {
                text = getMembershipNotice(mContext, mEvent, mRoomState);
            }
        }
        catch (Exception e) {
            Log.e(LOG_TAG, "getTextualDisplay() " + e);
        }

        return text;
    }

    public static String getRedactionMessage(Context context, Event event, RoomState roomState) {
        // Check first whether the event has been redacted
        String redactedInfo = null;

        boolean isRedacted = (event.unsigned != null) &&  (event.unsigned.redacted_because != null);

        if (isRedacted && (null != roomState)) {
            RedactedBecause redactedBecause = event.unsigned.redacted_because;
            String redactedBy = redactedBecause.sender;
            String redactedReason = null;

            if (null != redactedBecause.content) {
                redactedReason = redactedBecause.content.reason;
            }

            if (!TextUtils.isEmpty(redactedReason)) {
                if (!TextUtils.isEmpty(redactedBy)) {
                    redactedBy = context.getString(R.string.notice_event_redacted_by, redactedBy) + context.getString(R.string.notice_event_redacted_reason, redactedReason);
                }
                else {
                    redactedBy = context.getString(R.string.notice_event_redacted_reason, redactedReason);
                }
            }
            else if (!TextUtils.isEmpty(redactedBy)) {
                redactedBy = context.getString(R.string.notice_event_redacted_by, redactedBy);
            }

            redactedInfo = context.getString(R.string.notice_event_redacted, redactedBy);
        }

        return  redactedInfo;
    }

    private static String senderDisplayNameForEvent(Event event, EventContent eventContent, RoomState roomState) {
        String senderDisplayName = event.getSender();

        if (null != roomState) {
            // Consider first the current display name defined in provided room state (Note: this room state is supposed to not take the new event into account)
            senderDisplayName = roomState.getMemberName(event.getSender());
        }

        // Check whether this sender name is updated by the current event (This happens in case of new joined member)
        if (null != eventContent) {
            if (TextUtils.equals("join", eventContent.membership) && !TextUtils.isEmpty(eventContent.displayname)) {
                // Use the actual display name
                senderDisplayName = eventContent.displayname;
            }
        }

        return senderDisplayName;
    }

    public static String getMembershipNotice(Context context, Event event, RoomState roomState) {
        EventContent eventContent = JsonUtils.toEventContent(event.getContentAsJsonObject());
        EventContent prevEventContent = event.getPrevContent();

        String senderDisplayName = senderDisplayNameForEvent(event, eventContent, roomState);
        String prevUserDisplayName = null;

        String prevMembership = null;

        if (null != prevEventContent) {
            prevMembership = prevEventContent.membership;
        }

        if ((null != prevEventContent)) {
            prevUserDisplayName = prevEventContent.displayname;
        }

        String targetDisplayName = event.stateKey;

        if ((null != targetDisplayName) && (null != roomState)) {
            targetDisplayName = roomState.getMemberName(targetDisplayName);
        }

        // Check whether the sender has updated his profile (the membership is then unchanged)
        if (TextUtils.equals(prevMembership, eventContent.membership)) {
            String redactedInfo = EventDisplay.getRedactionMessage(context, event, roomState);

            // Is redacted event?
            if (!TextUtils.isEmpty(redactedInfo)) {
                return context.getString(R.string.notice_profile_change_redacted, senderDisplayName, redactedInfo);
            } else {
                String displayText = "";

                if (!TextUtils.equals(senderDisplayName, prevUserDisplayName)) {
                    if (TextUtils.isEmpty(prevUserDisplayName)) {
                        displayText = context.getString(R.string.notice_display_name_set, event.getSender(), senderDisplayName);
                    } else if (TextUtils.isEmpty(senderDisplayName)) {
                        displayText = context.getString(R.string.notice_display_name_removed, event.getSender());
                    } else {
                        displayText = context.getString(R.string.notice_display_name_changed_from, event.getSender(), prevUserDisplayName, senderDisplayName);
                    }
                }

                // Check whether the avatar has been changed
                String avatar = eventContent.avatar_url;
                String prevAvatar = null;

                if (null != prevEventContent) {
                    prevAvatar = prevEventContent.avatar_url;
                }

                if (!TextUtils.equals(prevAvatar, avatar) && (prevAvatar != avatar)) {
                    if (!TextUtils.isEmpty(displayText)) {
                        displayText = displayText + " " + context.getString(R.string.notice_avatar_changed_too);
                    } else {
                        displayText =  context.getString(R.string.notice_avatar_url_changed, senderDisplayName);
                    }
                }

                return displayText;
            }
        }
        else if (RoomMember.MEMBERSHIP_INVITE.equals(eventContent.membership)) {
            if (null != eventContent.third_party_invite) {
                return context.getString(R.string.notice_room_third_party_registered_invite, eventContent.third_party_invite.display_name, targetDisplayName, senderDisplayName);
            } else {
                MXDataHandler dataHandler = roomState.getDataHandler();

                if (null != dataHandler) {
                    if (TextUtils.equals(event.stateKey, dataHandler.getUserId())) {
                        return context.getString(R.string.notice_room_invite_you, senderDisplayName);
                    }
                }

                return context.getString(R.string.notice_room_invite, senderDisplayName, targetDisplayName);
            }
        }
        else if (RoomMember.MEMBERSHIP_JOIN.equals(eventContent.membership)) {
            return context.getString(R.string.notice_room_join, senderDisplayName);
        }
        else if (RoomMember.MEMBERSHIP_LEAVE.equals(eventContent.membership)) {
            // 2 cases here: this member may have left voluntarily or they may have been "left" by someone else ie. kicked
            if (TextUtils.equals(event.getSender(), event.stateKey)) {
                return context.getString(R.string.notice_room_leave, senderDisplayName);
            } else if (null != prevMembership) {
                if (prevMembership.equals(RoomMember.MEMBERSHIP_JOIN) || prevMembership.equals(RoomMember.MEMBERSHIP_INVITE)) {
                    return context.getString(R.string.notice_room_kick, senderDisplayName, targetDisplayName);
                } else if (prevMembership.equals(RoomMember.MEMBERSHIP_BAN)) {
                    return context.getString(R.string.notice_room_unban, senderDisplayName, targetDisplayName);
                }
            }
        }
        else if (RoomMember.MEMBERSHIP_BAN.equals(eventContent.membership)) {
            return context.getString(R.string.notice_room_ban, senderDisplayName, targetDisplayName);
        }
        else {
            // eh?
            Log.e(LOG_TAG, "Unknown membership: " + eventContent.membership);
        }
        return null;
    }


    private String getAvatarChangeNotice(Event msg, boolean desambigious) {
        // TODO: Pictures!
        return mContext.getString(R.string.notice_avatar_url_changed, getUserDisplayName(msg.getSender(), mRoomState));
    }

    private String getDisplayNameChangeNotice(Event msg) {
        return mContext.getString(R.string.notice_display_name_changed,
                msg.getSender(),
                ((JsonObject)msg.content).getAsJsonPrimitive("displayname").getAsString()
        );
    }
}