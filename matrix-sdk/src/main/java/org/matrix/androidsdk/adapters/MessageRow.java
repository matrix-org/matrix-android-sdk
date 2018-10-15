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

import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ParagraphStyle;
import android.text.style.QuoteSpan;

import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.RoomCreateContent;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.util.EventDisplay;

// this class defines a MessagesAdapter Item.
public class MessageRow {

    private final boolean mHasPredecessor;
    // the linked event
    private Event mEvent;
    // the room state
    private final RoomState mRoomState;

    // Cache of the user display name
    private final String mUserDisplayName;

    // Cache of the computed text
    private SpannableString mText;
    private RoomCreateContent mRoomCreateContent;

    private final RoomMember mSender;

    /**
     * Constructor
     *
     * @param event     the event.
     * @param roomState the room state
     */
    public MessageRow(Event event, RoomState roomState) {
        mEvent = event;
        mRoomState = roomState;

        mUserDisplayName = (null == roomState) ? event.getSender() : roomState.getMemberName(event.getSender());
        mRoomCreateContent = (null == roomState) ? null : roomState.getRoomCreateContent();
        mSender = roomState.getMember(event.getSender());
        mHasPredecessor = roomState.hasPredecessor();
    }

    /**
     * @return the event.
     */
    public Event getEvent() {
        return mEvent;
    }

    /**
     * Update the linked event.
     *
     * @param event the event.
     */
    public void updateEvent(Event event) {
        mEvent = event;
    }

    public Spannable getText(ParagraphStyle style, EventDisplay display) {
        if (mText == null) {
            CharSequence textualDisplay = display.getTextualDisplay(mEvent, mRoomState);

            mText = new SpannableString((null == textualDisplay) ? "" : textualDisplay);

            // Change to BlockQuote Spannable to customize it
            replaceQuoteSpans(mText, style);

        }

        return mText;
    }

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

    public String getUserDisplayName() {
        return mUserDisplayName;
    }

    public RoomCreateContent getRoomCreateContent() {
        return mRoomCreateContent;
    }

    public RoomMember getSender() {
        return mSender;
    }

    public boolean hasPredecessor() {
        return mHasPredecessor;
    }
}
