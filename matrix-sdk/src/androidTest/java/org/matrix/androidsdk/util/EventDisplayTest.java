/*
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

package org.matrix.androidsdk.util;

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.text.SpannableStringBuilder;

import com.google.gson.JsonObject;

import org.junit.Assert;
import org.junit.Test;
import org.matrix.androidsdk.interfaces.HtmlToolbox;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.message.Message;

public class EventDisplayTest {

    @Test
    public void EventDisplay_message_Simple_text() {
        Context context = InstrumentationRegistry.getContext();

        Event event = new Event();
        event.type = Event.EVENT_TYPE_MESSAGE;

        event.content = new JsonObject();
        ((JsonObject) event.content).addProperty("body", "message");

        EventDisplay eventDisplay = new EventDisplay(context);

        CharSequence textualDisplay = eventDisplay.getTextualDisplay(event, null);

        Assert.assertTrue(textualDisplay instanceof String);
        Assert.assertEquals("message", textualDisplay);
    }

    @Test
    public void EventDisplay_formattedMessage_Simple_text() {
        EventDisplay eventDisplay = new EventDisplay(InstrumentationRegistry.getContext());
        Event event = createEventWithFormattedBody("message");

        CharSequence textualDisplay = eventDisplay.getTextualDisplay(event, null);

        Assert.assertTrue(textualDisplay instanceof SpannableStringBuilder);
        Assert.assertEquals("message", textualDisplay.toString());
    }

    @Test
    public void EventDisplay_formattedMessage_italic_text() {
        EventDisplay eventDisplay = new EventDisplay(InstrumentationRegistry.getContext());
        Event event = createEventWithFormattedBody("<i>italic</i>");

        CharSequence textualDisplay = eventDisplay.getTextualDisplay(event, null);

        Assert.assertTrue(textualDisplay instanceof SpannableStringBuilder);
        Assert.assertEquals("italic", textualDisplay.toString());
    }

    @Test
    public void EventDisplay_formattedMessage_bold_text() {
        EventDisplay eventDisplay = new EventDisplay(InstrumentationRegistry.getContext());
        Event event = createEventWithFormattedBody("<strong>bold</strong>");

        CharSequence textualDisplay = eventDisplay.getTextualDisplay(event, null);

        Assert.assertTrue(textualDisplay instanceof SpannableStringBuilder);
        Assert.assertEquals("bold", textualDisplay.toString());
    }

    /**
     * This test check list item. It also check the trimming which has been added at the end of
     * {@link EventDisplay#getFormattedMessage(Context, JsonObject, HtmlToolbox)}
     */
    @Test
    public void EventDisplay_formattedMessage_li_text() {
        EventDisplay eventDisplay = new EventDisplay(InstrumentationRegistry.getContext());
        Event event = createEventWithFormattedBody("<ol><li>list</li></ol>");

        CharSequence textualDisplay = eventDisplay.getTextualDisplay(event, null);

        Assert.assertTrue(textualDisplay instanceof SpannableStringBuilder);
        Assert.assertEquals("list", textualDisplay.toString());
    }

    /**
     * This test check list with several items
     */
    @Test
    public void EventDisplay_formattedMessage_lili_text() {
        EventDisplay eventDisplay = new EventDisplay(InstrumentationRegistry.getContext());
        Event event = createEventWithFormattedBody("<ol><li>list</li><li>item</li></ol>");

        CharSequence textualDisplay = eventDisplay.getTextualDisplay(event, null);

        Assert.assertTrue(textualDisplay instanceof SpannableStringBuilder);
        Assert.assertEquals("list\nitem", textualDisplay.toString());
    }

    /**
     * Test blockquote. It also check the trimming which has been added at the end of
     * {@link EventDisplay#getFormattedMessage(Context, JsonObject, HtmlToolbox)}
     */
    @Test
    public void EventDisplay_formattedMessage_blockquote_text() {
        EventDisplay eventDisplay = new EventDisplay(InstrumentationRegistry.getContext());
        Event event = createEventWithFormattedBody("<blockquote>blockquote</blockquote>");

        CharSequence textualDisplay = eventDisplay.getTextualDisplay(event, null);

        Assert.assertTrue(textualDisplay instanceof SpannableStringBuilder);
        Assert.assertEquals("blockquote", textualDisplay.toString());
    }

    /**
     * Test blockquote with text
     */
    @Test
    public void EventDisplay_formattedMessage_blockquoteWithText_text() {
        EventDisplay eventDisplay = new EventDisplay(InstrumentationRegistry.getContext());
        Event event = createEventWithFormattedBody("<blockquote>blockquote</blockquote>message");

        CharSequence textualDisplay = eventDisplay.getTextualDisplay(event, null);

        Assert.assertTrue(textualDisplay instanceof SpannableStringBuilder);
        Assert.assertEquals("blockquote\n\nmessage", textualDisplay.toString());
    }

    // TODO Test other message type

    /* ==========================================================================================
     * Private
     * ========================================================================================== */

    private Event createEventWithFormattedBody(String formattedBody) {
        Event event = new Event();
        event.type = Event.EVENT_TYPE_MESSAGE;

        event.content = new JsonObject();
        ((JsonObject) event.content).addProperty("format", Message.FORMAT_MATRIX_HTML);
        ((JsonObject) event.content).addProperty("formatted_body", formattedBody);

        return event;
    }
}
