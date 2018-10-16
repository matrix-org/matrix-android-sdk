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

        EventDisplay eventDisplay = new EventDisplay(context, event, null);

        CharSequence textualDisplay = eventDisplay.getTextualDisplay();

        Assert.assertTrue(textualDisplay instanceof String);
        Assert.assertEquals("message", eventDisplay.getTextualDisplay());
    }

    @Test
    public void EventDisplay_formattedMessage_Simple_text() {
        EventDisplay eventDisplay = createEventDisplayWithFormattedBody("message");

        CharSequence textualDisplay = eventDisplay.getTextualDisplay();

        Assert.assertTrue(textualDisplay instanceof SpannableStringBuilder);
        Assert.assertEquals("message", textualDisplay.toString());
    }

    @Test
    public void EventDisplay_formattedMessage_italic_text() {
        EventDisplay eventDisplay = createEventDisplayWithFormattedBody("<i>italic</i>");

        CharSequence textualDisplay = eventDisplay.getTextualDisplay();

        Assert.assertTrue(textualDisplay instanceof SpannableStringBuilder);
        Assert.assertEquals("italic", textualDisplay.toString());
    }

    @Test
    public void EventDisplay_formattedMessage_bold_text() {
        EventDisplay eventDisplay = createEventDisplayWithFormattedBody("<strong>bold</strong>");

        CharSequence textualDisplay = eventDisplay.getTextualDisplay();

        Assert.assertTrue(textualDisplay instanceof SpannableStringBuilder);
        Assert.assertEquals("bold", textualDisplay.toString());
    }

    /**
     * This test check a strange behavior we have: current result is "lis"
     */
    @Test
    public void EventDisplay_formattedMessage_li_text() {
        EventDisplay eventDisplay = createEventDisplayWithFormattedBody("<ol><li>list</li></ol>");

        CharSequence textualDisplay = eventDisplay.getTextualDisplay();

        Assert.assertTrue(textualDisplay instanceof SpannableStringBuilder);

        Assert.assertEquals("list", textualDisplay.toString());
    }

    /* ==========================================================================================
     * Private
     * ========================================================================================== */

    private EventDisplay createEventDisplayWithFormattedBody(String formattedBody) {
        Context context = InstrumentationRegistry.getContext();

        Event event = new Event();
        event.type = Event.EVENT_TYPE_MESSAGE;

        event.content = new JsonObject();
        ((JsonObject) event.content).addProperty("format", Message.FORMAT_MATRIX_HTML);
        ((JsonObject) event.content).addProperty("formatted_body", formattedBody);

        EventDisplay eventDisplay = new EventDisplay(context, event, null);

        return eventDisplay;
    }

}
