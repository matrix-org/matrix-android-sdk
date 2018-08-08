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
package org.matrix.androidsdk.rest.model.bingrules;

import com.google.gson.JsonParser;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.matrix.androidsdk.rest.model.Event;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class EventMatchConditionTest {

    private static final String TEST_ROOM_ID = "!testroomid:matrix.org";
    private static final String TEST_USER_ID = "@testuserid:matrix.org";

    private EventMatchCondition condition = new EventMatchCondition();

    private Event event = new Event();

    @Before
    public void setUp() {
        event.roomId = TEST_ROOM_ID;
        event.userId = TEST_USER_ID;
        String contentJson = "{'msgtype': 'm.text', 'body': 'Nice body!', 'other_field': 'other_value'}";
        event.content = new JsonParser().parse(contentJson);
    }

    @Test
    public void testRoomMatch() {
        condition.key = "room_id";
        condition.pattern = TEST_ROOM_ID;

        Assert.assertTrue(condition.isSatisfied(event));

        event.roomId = "!otherroomid:matrix.org";

        Assert.assertFalse(condition.isSatisfied(event));
    }

    @Test
    public void testSender() {
        condition.key = "user_id";
        condition.pattern = TEST_USER_ID;

        Assert.assertTrue(condition.isSatisfied(event));

        event.userId = "!otheruserid:matrix.org";

        Assert.assertFalse(condition.isSatisfied(event));
    }

    @Test
    public void testContentBody() {
        condition.key = "content.body";
        condition.pattern = "Nice body!";
        Assert.assertTrue(condition.isSatisfied(event));

        condition.pattern = "Nice";
        Assert.assertTrue(condition.isSatisfied(event));

        condition.pattern = "body";
        Assert.assertTrue(condition.isSatisfied(event));

        condition.pattern = "bo?y"; // Contains a special character so leading and trailing * are not implicit
        Assert.assertFalse(condition.isSatisfied(event));

        condition.pattern = "*bo?y?";
        Assert.assertTrue(condition.isSatisfied(event));

        condition.pattern = "b*y"; // Same as above
        Assert.assertFalse(condition.isSatisfied(event));

        condition.pattern = "*b*y*";
        Assert.assertTrue(condition.isSatisfied(event));

        condition.pattern = "nice";
        Assert.assertTrue(condition.isSatisfied(event)); // Lowercase N

        condition.pattern = "ice";
        Assert.assertFalse(condition.isSatisfied(event)); // partial match

        condition.pattern = "dog";
        Assert.assertFalse(condition.isSatisfied(event));
    }

    @Test
    public void testRandomField() {
        condition.key = "content.other_field";
        condition.pattern = "other_value";
        Assert.assertTrue(condition.isSatisfied(event));

        condition.pattern = "*value";
        Assert.assertTrue(condition.isSatisfied(event));

        condition.pattern = "value";
        Assert.assertFalse(condition.isSatisfied(event));

        condition.pattern = "?value";
        Assert.assertFalse(condition.isSatisfied(event));
    }

    @Test
    public void testAtRoom() {
        condition.key = "content.body";
        condition.pattern = "@room";

        // True cases
        event.content = new JsonParser().parse("{'msgtype': 'm.text', 'body': '@room'}");
        Assert.assertTrue(condition.isSatisfied(event));

        event.content = new JsonParser().parse("{'msgtype': 'm.text', 'body': '@room:'}");
        Assert.assertTrue(condition.isSatisfied(event));

        event.content = new JsonParser().parse("{'msgtype': 'm.text', 'body': '@room: '}");
        Assert.assertTrue(condition.isSatisfied(event));

        event.content = new JsonParser().parse("{'msgtype': 'm.text', 'body': 'hello @room'}");
        Assert.assertTrue(condition.isSatisfied(event));

        event.content = new JsonParser().parse("{'msgtype': 'm.text', 'body': 'hello @room after'}");
        Assert.assertTrue(condition.isSatisfied(event));

        // False cases
        event.content = new JsonParser().parse("{'msgtype': 'm.text', 'body': '_@room'}");
        Assert.assertFalse(condition.isSatisfied(event));

        event.content = new JsonParser().parse("{'msgtype': 'm.text', 'body': '@room_'}");
        Assert.assertFalse(condition.isSatisfied(event));

        event.content = new JsonParser().parse("{'msgtype': 'm.text', 'body': '_@room_'}");
        Assert.assertFalse(condition.isSatisfied(event));
    }
}
