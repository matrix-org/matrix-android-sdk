/*
 * Copyright 2016 OpenMarket Ltd
 * Copyright 2018 New Vector Ltd
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
package org.matrix.androidsdk.core;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import org.matrix.androidsdk.R;
import org.matrix.androidsdk.call.MXCallsManager;
import org.matrix.androidsdk.crypto.MXCryptoError;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.interfaces.HtmlToolbox;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.EventContent;
import org.matrix.androidsdk.rest.model.RedactedBecause;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.message.Message;
import org.matrix.androidsdk.rest.model.pid.RoomThirdPartyInvite;

/**
 * Class helper to stringify an event
 */
public class EventDisplay {
    private static final String LOG_TAG = EventDisplay.class.getSimpleName();

    private static final String MESSAGE_IN_REPLY_TO_FIRST_PART = "<blockquote>";
    private static final String MESSAGE_IN_REPLY_TO_LAST_PART = "</a>";
    private static final String MESSAGE_IN_REPLY_TO_HREF_TAG_END = "\">";


    // members
    protected final Context mContext;

    @Nullable
    protected final HtmlToolbox mHtmlToolbox;

    protected boolean mPrependAuthor;

    // let the application defines if the redacted events must be displayed
    public static final boolean mDisplayRedactedEvents = false;

    // constructor
    public EventDisplay(Context context) {
        this(context, null);
    }

    // constructor
    public EventDisplay(Context context, @Nullable HtmlToolbox htmlToolbox) {
        mContext = context.getApplicationContext();
        mHtmlToolbox = htmlToolbox;
    }

    /**
     * <p>Prepend the text with the author's name if they have not been mentioned in the text.</p>
     * This will prepend text messages with the author's name. This will NOT prepend things like
     * emote, room topic changes, etc which already mention the author's name in the message.
     *
     * @param prepend true to prepend the message author.
     */
    public void setPrependMessagesWithAuthor(boolean prepend) {
        mPrependAuthor = prepend;
    }

    /**
     * Compute an "human readable" name for an user Id.
     *
     * @param userId    the user id
     * @param roomState the room state
     * @return the user display name
     */
    protected static String getUserDisplayName(String userId, RoomState roomState) {
        if (null != roomState) {
            return roomState.getMemberName(userId);
        } else {
            return userId;
        }
    }

    /**
     * Stringify the linked event.
     *
     * @return The text or null if it isn't possible.
     */
    public CharSequence getTextualDisplay(Event event, RoomState roomState) {
        return getTextualDisplay(null, event, roomState);
    }

    /**
     * Stringify the linked event.
     *
     * @param displayNameColor the display name highlighted color.
     * @return The text or null if it isn't possible.
     */
    public CharSequence getTextualDisplay(Integer displayNameColor, Event event, RoomState roomState) {
        CharSequence text = null;

        try {
            JsonObject jsonEventContent = event.getContentAsJsonObject();

            String userDisplayName = getUserDisplayName(event.getSender(), roomState);
            String eventType = event.getType();

            if (event.isCallEvent()) {
                if (Event.EVENT_TYPE_CALL_INVITE.equals(eventType)) {
                    boolean isVideo = false;
                    // detect call type from the sdp
                    try {
                        JsonObject offer = jsonEventContent.get("offer").getAsJsonObject();
                        JsonElement sdp = offer.get("sdp");
                        String sdpValue = sdp.getAsString();
                        isVideo = sdpValue.contains("m=video");
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "getTextualDisplay : " + e.getMessage(), e);
                    }

                    if (isVideo) {
                        return mContext.getString(R.string.notice_placed_video_call, userDisplayName);
                    } else {
                        return mContext.getString(R.string.notice_placed_voice_call, userDisplayName);
                    }
                } else if (Event.EVENT_TYPE_CALL_ANSWER.equals(eventType)) {
                    return mContext.getString(R.string.notice_answered_call, userDisplayName);
                } else if (Event.EVENT_TYPE_CALL_HANGUP.equals(eventType)) {
                    return mContext.getString(R.string.notice_ended_call, userDisplayName);
                } else {
                    return eventType;
                }
            } else if (Event.EVENT_TYPE_STATE_HISTORY_VISIBILITY.equals(eventType)) {
                CharSequence subpart;
                String historyVisibility = (null != jsonEventContent.get("history_visibility")) ?
                        jsonEventContent.get("history_visibility").getAsString() : RoomState.HISTORY_VISIBILITY_SHARED;

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
            } else if (Event.EVENT_TYPE_RECEIPT.equals(eventType)) {
                // the read receipt should not be displayed
                text = "Read Receipt";
            } else if (Event.EVENT_TYPE_MESSAGE.equals(eventType)) {
                final String msgtype = (null != jsonEventContent.get("msgtype")) ? jsonEventContent.get("msgtype").getAsString() : "";
                // all m.room.message events should support the 'body' key fallback, so use it.

                text = jsonEventContent.has("body") ? jsonEventContent.get("body").getAsString() : null;
                // check for html formatting
                if (jsonEventContent.has("formatted_body") && jsonEventContent.has("format")) {
                    text = getFormattedMessage(mContext, jsonEventContent, roomState, mHtmlToolbox);
                }
                // avoid empty image name
                if (TextUtils.equals(msgtype, Message.MSGTYPE_IMAGE) && TextUtils.isEmpty(text)) {
                    text = mContext.getString(R.string.summary_user_sent_image, userDisplayName);
                } else if (TextUtils.equals(msgtype, Message.MSGTYPE_EMOTE)) {
                    text = "* " + userDisplayName + " " + text;
                } else if (TextUtils.isEmpty(text)) {
                    text = "";
                } else if (mPrependAuthor) {
                    text = new SpannableStringBuilder(mContext.getString(R.string.summary_message, userDisplayName, text));

                    if (null != displayNameColor) {
                        ((SpannableStringBuilder) text).setSpan(new ForegroundColorSpan(displayNameColor),
                                0, userDisplayName.length() + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        ((SpannableStringBuilder) text).setSpan(new StyleSpan(Typeface.BOLD),
                                0, userDisplayName.length() + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                }
            } else if (Event.EVENT_TYPE_STICKER.equals(eventType)) {
                // all m.stickers events should support the 'body' key fallback, so use it.
                text = jsonEventContent.has("body") ? jsonEventContent.get("body").getAsString() : null;

                if (TextUtils.isEmpty(text)) {
                    text = mContext.getString(R.string.summary_user_sent_sticker, userDisplayName);
                }

            } else if (Event.EVENT_TYPE_MESSAGE_ENCRYPTION.equals(eventType)) {
                text = mContext.getString(R.string.notice_end_to_end, userDisplayName, event.getWireEventContent().algorithm);
            } else if (Event.EVENT_TYPE_MESSAGE_ENCRYPTED.equals(eventType)) {
                // don't display
                if (event.isRedacted()) {
                    String redactedInfo = getRedactionMessage(mContext, event, roomState);

                    if (TextUtils.isEmpty(redactedInfo)) {
                        return null;
                    } else {
                        return redactedInfo;
                    }
                } else {
                    String message = null;


                    if (null != event.getCryptoError()) {
                        String errorDescription;

                        MXCryptoError error = event.getCryptoError();

                        if (TextUtils.equals(error.errcode, MXCryptoError.UNKNOWN_INBOUND_SESSION_ID_ERROR_CODE)) {
                            errorDescription = mContext.getResources().getString(R.string.notice_crypto_error_unkwown_inbound_session_id);
                        } else {
                            errorDescription = error.getLocalizedMessage();
                        }

                        message = mContext.getString(R.string.notice_crypto_unable_to_decrypt, errorDescription);
                    }

                    if (TextUtils.isEmpty(message)) {
                        message = mContext.getString(R.string.encrypted_message);
                    }

                    SpannableString spannableStr = new SpannableString(message);
                    spannableStr.setSpan(new StyleSpan(Typeface.ITALIC), 0, message.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    text = spannableStr;
                }
            } else if (Event.EVENT_TYPE_STATE_ROOM_TOPIC.equals(eventType)) {
                String topic = jsonEventContent.getAsJsonPrimitive("topic").getAsString();

                if (event.isRedacted()) {
                    String redactedInfo = getRedactionMessage(mContext, event, roomState);

                    if (TextUtils.isEmpty(redactedInfo)) {
                        return null;
                    }

                    topic = redactedInfo;
                }

                if (!TextUtils.isEmpty(topic)) {
                    text = mContext.getString(R.string.notice_room_topic_changed, userDisplayName, topic);
                } else {
                    text = mContext.getString(R.string.notice_room_topic_removed, userDisplayName);
                }
            } else if (Event.EVENT_TYPE_STATE_ROOM_NAME.equals(eventType)) {
                JsonPrimitive nameAsJson = jsonEventContent.getAsJsonPrimitive("name");
                String roomName = (null == nameAsJson) ? null : nameAsJson.getAsString();

                if (event.isRedacted()) {
                    String redactedInfo = getRedactionMessage(mContext, event, roomState);

                    if (TextUtils.isEmpty(redactedInfo)) {
                        return null;
                    }

                    roomName = redactedInfo;
                }

                if (!TextUtils.isEmpty(roomName)) {
                    text = mContext.getString(R.string.notice_room_name_changed, userDisplayName, roomName);
                } else {
                    text = mContext.getString(R.string.notice_room_name_removed, userDisplayName);
                }
            } else if (Event.EVENT_TYPE_STATE_ROOM_THIRD_PARTY_INVITE.equals(eventType)) {
                RoomThirdPartyInvite invite = JsonUtils.toRoomThirdPartyInvite(event.getContent());
                String displayName = invite.display_name;

                if (event.isRedacted()) {
                    String redactedInfo = getRedactionMessage(mContext, event, roomState);

                    if (TextUtils.isEmpty(redactedInfo)) {
                        return null;
                    }

                    displayName = redactedInfo;
                }

                if (displayName != null) {
                    text = mContext.getString(R.string.notice_room_third_party_invite, userDisplayName, displayName);
                } else {
                    // Consider the invite has been revoked
                    JsonObject prevContent = event.getPrevContentAsJsonObject();
                    if (prevContent != null) {
                        text = mContext.getString(R.string.notice_room_third_party_revoked_invite, userDisplayName, prevContent.get("display_name"));
                    } else {
                        text = null;
                    }
                }
            } else if (Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(eventType)) {
                text = getMembershipNotice(mContext, event, roomState);
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "getTextualDisplay() " + e.getMessage(), e);
        }

        return text;
    }

    /**
     * Compute the redact text for an event.
     *
     * @param context   the context
     * @param event     the event
     * @param roomState the room state
     * @return the redacted event text
     */
    @Nullable
    public static String getRedactionMessage(Context context, Event event, RoomState roomState) {
        // test if the redacted event must be displayed.
        if (!mDisplayRedactedEvents) {
            return null;
        }

        String result = null;

        // Check first whether the event has been redacted
        if (event != null
                && event.isRedacted()
                && event.unsigned != null
                && event.unsigned.redacted_because != null
                && roomState != null) {
            RedactedBecause redactedBecause = event.unsigned.redacted_because;

            String redactedBy = redactedBecause.sender;
            String redactedReason = null;

            if (redactedBecause.content != null) {
                redactedReason = redactedBecause.content.reason;
            }

            if (TextUtils.isEmpty(redactedBy)) {
                // No by
                if (TextUtils.isEmpty(redactedReason)) {
                    // no reason
                    result = context.getString(R.string.notice_event_redacted);
                } else {
                    // reason
                    result = context.getString(R.string.notice_event_redacted_with_reason, redactedReason);
                }
            } else {
                // by
                if (TextUtils.isEmpty(redactedReason)) {
                    // no reason
                    result = context.getString(R.string.notice_event_redacted_by, redactedBy);
                } else {
                    // by and reason
                    result = context.getString(R.string.notice_event_redacted_by_with_reason, redactedBy, redactedReason);
                }
            }
        }

        return result;
    }

    /**
     * Compute the sender display name
     *
     * @param event            the event
     * @param eventContent     the event content
     * @param prevEventContent the prev event content
     * @param roomState        the room state
     * @return the "human readable" display name
     */
    protected static String senderDisplayNameForEvent(Event event, EventContent eventContent, EventContent prevEventContent, RoomState roomState) {
        String senderDisplayName = event.getSender();

        if (!event.isRedacted()) {
            if (null != roomState) {
                // Consider first the current display name defined in provided room state
                // (Note: this room state is supposed to not take the new event into account)
                senderDisplayName = roomState.getMemberName(event.getSender());
            }

            // Check whether this sender name is updated by the current event (This happens in case of new joined member)
            if ((null != eventContent) && TextUtils.equals(RoomMember.MEMBERSHIP_JOIN, eventContent.membership)) {
                // detect if it is displayname update
                // a display name update is detected when the previous state was join and there was a displayname
                if (!TextUtils.isEmpty(eventContent.displayname)
                        || ((null != prevEventContent)
                        && TextUtils.equals(RoomMember.MEMBERSHIP_JOIN, prevEventContent.membership)
                        && !TextUtils.isEmpty(prevEventContent.displayname))) {
                    senderDisplayName = eventContent.displayname;
                }
            }
        }

        return senderDisplayName;
    }

    /**
     * Build a membership notice text from its dedicated event.
     *
     * @param context   the context.
     * @param event     the event.
     * @param roomState the room state.
     * @return the membership text.
     */
    public static String getMembershipNotice(Context context, Event event, RoomState roomState) {
        JsonObject content = event.getContentAsJsonObject();

        // don't support redacted membership event
        if ((null == content) || (content.entrySet().size() == 0)) {
            return null;
        }

        EventContent eventContent = JsonUtils.toEventContent(event.getContentAsJsonObject());
        EventContent prevEventContent = event.getPrevContent();

        String senderDisplayName = senderDisplayNameForEvent(event, eventContent, prevEventContent, roomState);
        String prevUserDisplayName = null;

        String prevMembership = null;

        if (null != prevEventContent) {
            prevMembership = prevEventContent.membership;
        }

        if ((null != prevEventContent)) {
            prevUserDisplayName = prevEventContent.displayname;
        }

        // use by default the provided display name
        String targetDisplayName = eventContent.displayname;

        // if it is not provided, use the stateKey value
        // and try to retrieve a valid display name
        if (null == targetDisplayName) {
            targetDisplayName = event.stateKey;
            if ((null != targetDisplayName) && (null != roomState) && !event.isRedacted()) {
                targetDisplayName = roomState.getMemberName(targetDisplayName);
            }
        }

        // Check whether the sender has updated his profile (the membership is then unchanged)
        if (TextUtils.equals(prevMembership, eventContent.membership)) {
            String redactedInfo = getRedactionMessage(context, event, roomState);

            // Is redacted event?
            if (event.isRedacted()) {

                // Here the event is ignored (no display)
                if (null == redactedInfo) {
                    return null;
                }

                return context.getString(R.string.notice_profile_change_redacted, senderDisplayName, redactedInfo);
            } else {
                String displayText = "";

                if (!TextUtils.equals(senderDisplayName, prevUserDisplayName)) {
                    if (TextUtils.isEmpty(prevUserDisplayName)) {
                        if (!TextUtils.equals(event.getSender(), senderDisplayName)) {
                            displayText = context.getString(R.string.notice_display_name_set, event.getSender(), senderDisplayName);
                        }
                    } else if (TextUtils.isEmpty(senderDisplayName)) {
                        displayText = context.getString(R.string.notice_display_name_removed, event.getSender(), prevUserDisplayName);
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

                if (!TextUtils.equals(prevAvatar, avatar)) {
                    if (!TextUtils.isEmpty(displayText)) {
                        displayText = displayText + " " + context.getString(R.string.notice_avatar_changed_too);
                    } else {
                        displayText = context.getString(R.string.notice_avatar_url_changed, senderDisplayName);
                    }
                }

                return displayText;
            }
        } else if (RoomMember.MEMBERSHIP_INVITE.equals(eventContent.membership)) {
            if (null != eventContent.third_party_invite) {
                return context.getString(R.string.notice_room_third_party_registered_invite, targetDisplayName, eventContent.third_party_invite.display_name);
            } else {
                String selfUserId = null;

                if ((null != roomState) && (null != roomState.getDataHandler())) {
                    selfUserId = roomState.getDataHandler().getUserId();
                }

                if (TextUtils.equals(event.stateKey, selfUserId)) {
                    return context.getString(R.string.notice_room_invite_you, senderDisplayName);
                }

                if (null == event.stateKey) {
                    return context.getString(R.string.notice_room_invite_no_invitee, senderDisplayName);
                }

                // conference call case
                if (targetDisplayName.equals(MXCallsManager.getConferenceUserId(event.roomId))) {
                    return context.getString(R.string.notice_requested_voip_conference, senderDisplayName);
                }

                return context.getString(R.string.notice_room_invite, senderDisplayName, targetDisplayName);
            }
        } else if (RoomMember.MEMBERSHIP_JOIN.equals(eventContent.membership)) {
            // conference call case
            if (TextUtils.equals(event.sender, MXCallsManager.getConferenceUserId(event.roomId))) {
                return context.getString(R.string.notice_voip_started);
            }

            return context.getString(R.string.notice_room_join, senderDisplayName);
        } else if (RoomMember.MEMBERSHIP_LEAVE.equals(eventContent.membership)) {
            // conference call case
            if (TextUtils.equals(event.sender, MXCallsManager.getConferenceUserId(event.roomId))) {
                return context.getString(R.string.notice_voip_finished);
            }

            // 2 cases here: this member may have left voluntarily or they may have been "left" by someone else ie. kicked
            if (TextUtils.equals(event.getSender(), event.stateKey)) {
                if ((null != prevEventContent) && TextUtils.equals(prevEventContent.membership, RoomMember.MEMBERSHIP_INVITE)) {
                    return context.getString(R.string.notice_room_reject, senderDisplayName);
                } else {

                    // use the latest known displayname
                    if ((null == eventContent.displayname) && (null != prevUserDisplayName)) {
                        senderDisplayName = prevUserDisplayName;
                    }

                    return context.getString(R.string.notice_room_leave, senderDisplayName);
                }

            } else if (null != prevMembership) {
                if (prevMembership.equals(RoomMember.MEMBERSHIP_INVITE)) {
                    return context.getString(R.string.notice_room_withdraw, senderDisplayName, targetDisplayName);
                } else if (prevMembership.equals(RoomMember.MEMBERSHIP_JOIN)) {
                    return context.getString(R.string.notice_room_kick, senderDisplayName, targetDisplayName);
                } else if (prevMembership.equals(RoomMember.MEMBERSHIP_BAN)) {
                    return context.getString(R.string.notice_room_unban, senderDisplayName, targetDisplayName);
                }
            }
        } else if (RoomMember.MEMBERSHIP_BAN.equals(eventContent.membership)) {
            return context.getString(R.string.notice_room_ban, senderDisplayName, targetDisplayName);
        } else if (RoomMember.MEMBERSHIP_KICK.equals(eventContent.membership)) {
            return context.getString(R.string.notice_room_kick, senderDisplayName, targetDisplayName);
        } else {
            Log.e(LOG_TAG, "Unknown membership: " + eventContent.membership);
        }
        return null;
    }


    /**
     * @param context          the context
     * @param jsonEventContent the current jsonEventContent
     * @param roomState        the room state
     * @param htmlToolbox      an optional htmlToolbox to manage html images and tag
     * @return the formatted message as CharSequence
     */
    private CharSequence getFormattedMessage(@NonNull final Context context,
                                             @NonNull final JsonObject jsonEventContent,
                                             @NonNull final RoomState roomState,
                                             @Nullable final HtmlToolbox htmlToolbox) {
        final String format = jsonEventContent.getAsJsonPrimitive("format").getAsString();
        CharSequence text = null;
        if (Message.FORMAT_MATRIX_HTML.equals(format)) {
            String htmlBody = jsonEventContent.getAsJsonPrimitive("formatted_body").getAsString();
            if (htmlToolbox != null) {
                htmlBody = htmlToolbox.convert(htmlBody);
            }
            // Special treatment for "In reply to" message
            if (jsonEventContent.has("m.relates_to")) {
                final JsonElement relatesTo = jsonEventContent.get("m.relates_to");
                if (relatesTo.isJsonObject()) {
                    if (relatesTo.getAsJsonObject().has("m.in_reply_to")) {
                        // Note: <mx-reply> tag has been removed by HtmlToolbox.convert()
                        // Replace <blockquote><a href=\"__permalink__\">In reply to</a>
                        // By <blockquote>['In reply to' from resources]
                        // To disable the link and to localize the "In reply to" string
                        if (htmlBody.startsWith(MESSAGE_IN_REPLY_TO_FIRST_PART)) {
                            final int index = htmlBody.indexOf(MESSAGE_IN_REPLY_TO_LAST_PART);
                            if (index != -1) {
                                String bodyRest = htmlBody.substring(index + MESSAGE_IN_REPLY_TO_LAST_PART.length());

                                // find indices of displayName/mxid
                                int indexMxidStart = bodyRest.indexOf(MESSAGE_IN_REPLY_TO_HREF_TAG_END);
                                int indexMxidStop = bodyRest.indexOf(MESSAGE_IN_REPLY_TO_LAST_PART);

                                if (indexMxidStart != 1 && indexMxidStop != -1) {
                                    indexMxidStart = indexMxidStart + MESSAGE_IN_REPLY_TO_HREF_TAG_END.length();
                                    // extract potential mxid (could also be display name)
                                    String mxid = bodyRest.substring(indexMxidStart, indexMxidStop);
                                    // convert mxid to display name
                                    String userDisplayName = roomState.getMemberName(mxid);
                                    // reconstruct message
                                    if (!userDisplayName.equals(mxid)) {
                                        bodyRest = bodyRest.substring(0, indexMxidStart) + userDisplayName + bodyRest.substring(indexMxidStop);
                                    }
                                }

                                htmlBody = MESSAGE_IN_REPLY_TO_FIRST_PART
                                        + context.getString(R.string.message_reply_to_prefix)
                                        + bodyRest;
                            }
                        }
                    }
                }
            }
            // some markers are not supported so fallback on an ascii display until to find the right way to manage them
            // an issue has been created https://github.com/vector-im/vector-android/issues/38
            // BMA re-enable <ol> and <li> support (https://github.com/vector-im/riot-android/issues/2184)
            if (!TextUtils.isEmpty(htmlBody)) {
                final Html.ImageGetter imageGetter;
                final Html.TagHandler tagHandler;
                if (htmlToolbox != null) {
                    imageGetter = htmlToolbox.getImageGetter();
                    tagHandler = htmlToolbox.getTagHandler(htmlBody);
                } else {
                    imageGetter = null;
                    tagHandler = null;
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    text = Html.fromHtml(htmlBody,
                            Html.FROM_HTML_SEPARATOR_LINE_BREAK_LIST_ITEM | Html.FROM_HTML_SEPARATOR_LINE_BREAK_LIST,
                            imageGetter, tagHandler);
                } else {
                    text = Html.fromHtml(htmlBody, imageGetter, tagHandler);
                }
                // fromHtml formats quotes (> character) with two newlines at the end
                // remove any newlines at the end of the CharSequence
                while (text.length() > 0 && text.charAt(text.length() - 1) == '\n') {
                    text = text.subSequence(0, text.length() - 1);
                }
            }
        }
        return text;
    }
}
