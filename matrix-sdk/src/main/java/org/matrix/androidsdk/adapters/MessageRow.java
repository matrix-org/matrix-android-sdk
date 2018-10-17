/*
 * Copyright 2014 OpenMarket Ltd
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
package org.matrix.androidsdk.adapters;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ParagraphStyle;
import android.text.style.QuoteSpan;

import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.RoomCreateContent;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.util.EventDisplay;

/**
 * this class defines a MessagesAdapter Item.
 */
public class MessageRow {

    // the linked event
    @NonNull
    private Event mEvent;

    // the room state
    @Nullable
    private final RoomState mRoomState;

    // Cache of the sender display name
    private final String mSenderDisplayName;

    // Cache of the computed text
    private SpannableString mText;

    @Nullable
    private RoomCreateContent.Predecessor mRoomCreateContentPredecessor;

    @Nullable
    private final RoomMember mSender;

    /**
     * Constructor
     *
     * @param event     the event.
     * @param roomState the room state
     */
    public MessageRow(@NonNull Event event, @Nullable RoomState roomState) {
        mEvent = event;
        mRoomState = roomState;

        if (roomState == null) {
            // Use the id of the sender as display name
            mSenderDisplayName = event.getSender();
            mRoomCreateContentPredecessor = null;
            mSender = null;
        } else {
            mSenderDisplayName = roomState.getMemberName(event.getSender());
            if (roomState.getRoomCreateContent() != null) {
                mRoomCreateContentPredecessor = roomState.getRoomCreateContent().predecessor;
            }
            mSender = roomState.getMember(event.getSender());
        }
    }

    /**
     * @return the event.
     */
    @NonNull
    public Event getEvent() {
        return mEvent;
    }

    /**
     * Update the linked event.
     *
     * @param event the event.
     */
    public void updateEvent(@NonNull Event event) {
        mEvent = event;

        // invalidate our cache
        mText = null;
    }

    /**
     * Get the text of the event
     *
     * @param style
     * @param display
     * @return
     */
    public Spannable getText(ParagraphStyle style, EventDisplay display) {
        if (mText == null) {
            CharSequence textualDisplay = display.getTextualDisplay(mEvent, mRoomState);

            mText = new SpannableString((null == textualDisplay) ? "" : textualDisplay);

            // Change to BlockQuote Spannable to customize it
            replaceQuoteSpans(mText, style);
        }

        return mText;
    }

    public String getSenderDisplayName() {
        return mSenderDisplayName;
    }

    @Nullable
    public RoomCreateContent.Predecessor getRoomCreateContentPredecessor() {
        return mRoomCreateContentPredecessor;
    }

    @Nullable
    public RoomMember getSender() {
        return mSender;
    }

    /* ==========================================================================================
     * Private
     * ========================================================================================== */

    /**
     * Replace all QuoteSpan instances by instances of VectorQuoteSpan
     *
     * @param spannable
     */
    private void replaceQuoteSpans(Spannable spannable, ParagraphStyle style) {
        QuoteSpan[] quoteSpans = spannable.getSpans(0, spannable.length(), QuoteSpan.class);
        for (QuoteSpan quoteSpan : quoteSpans) {
            int start = spannable.getSpanStart(quoteSpan);
            int end = spannable.getSpanEnd(quoteSpan);
            int flags = spannable.getSpanFlags(quoteSpan);
            spannable.removeSpan(quoteSpan);
            spannable.setSpan(style, start, end, flags);
        }
    }
}
