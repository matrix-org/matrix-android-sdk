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
package org.matrix.androidsdk.rest.model.bingrules;

import com.google.gson.JsonParser;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.matrix.androidsdk.rest.model.Event;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class ContainsDisplayNameConditionTest {

    private ContainsDisplayNameCondition condition = new ContainsDisplayNameCondition();

    private Event event;
    private String displayName = "Bob";

    @Before
    public void setUp() {
        event = new Event();
        event.type = Event.EVENT_TYPE_MESSAGE;
    }

    private void setEventMessage(String type, String rest) {
        String contentJson = "{'msgtype': '" + type + "', " + rest + "}";
        event.content = new JsonParser().parse(contentJson);
    }

    private void setEventTextMessageBody(String body) {
        setEventMessage("m.text", "'body': '" + body + "'");
    }

    @Test
    public void testTextMessages() {
        setEventTextMessageBody("Bob");
        Assert.assertTrue(condition.isSatisfied(event, displayName));

        setEventTextMessageBody("bob");
        Assert.assertTrue(condition.isSatisfied(event, displayName));

        setEventTextMessageBody("Hi Bob!");
        Assert.assertTrue(condition.isSatisfied(event, displayName));

        setEventTextMessageBody("Hi Bobby!");
        Assert.assertFalse(condition.isSatisfied(event, displayName));

        setEventTextMessageBody("Hi MrBob");
        Assert.assertFalse(condition.isSatisfied(event, displayName));

        setEventTextMessageBody("Hi Robert!");
        Assert.assertFalse(condition.isSatisfied(event, displayName));
    }

    @Test
    public void testOtherMessageTypes() {
        setEventMessage("m.image", "'body': 'Bob.jpeg'");
        Assert.assertTrue(condition.isSatisfied(event, displayName));

        setEventMessage("m.image", "'url': 'Bob'");
        Assert.assertFalse(condition.isSatisfied(event, displayName));

        setEventMessage("m.notice", "'body': 'Bob did something or other'");
        Assert.assertTrue(condition.isSatisfied(event, displayName));

        setEventMessage("m.emote", "'body': 'is angry with Bob'");
        Assert.assertTrue(condition.isSatisfied(event, displayName));
    }

    @Test
    public void testOtherEventType() {
        event.type = Event.EVENT_TYPE_TYPING;
        setEventTextMessageBody("Bob");
        Assert.assertFalse(condition.isSatisfied(event, displayName));
    }
}
