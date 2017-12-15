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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.matrix.androidsdk.rest.model.Event;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.*;

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

        assertTrue(condition.isSatisfied(event));

        event.roomId = "!otherroomid:matrix.org";

        assertFalse(condition.isSatisfied(event));
    }

    @Test
    public void testSender() {
        condition.key = "user_id";
        condition.pattern = TEST_USER_ID;

        assertTrue(condition.isSatisfied(event));

        event.userId = "!otheruserid:matrix.org";

        assertFalse(condition.isSatisfied(event));
    }

    @Test
    public void testContentBody() {
        condition.key = "content.body";
        condition.pattern = "Nice body!";
        assertTrue(condition.isSatisfied(event));

        condition.pattern = "Nice";
        assertTrue(condition.isSatisfied(event));

        condition.pattern = "body";
        assertTrue(condition.isSatisfied(event));

        condition.pattern = "bo?y"; // Contains a special character so leading and trailing * are not implicit
        assertFalse(condition.isSatisfied(event));

        condition.pattern = "*bo?y?";
        assertTrue(condition.isSatisfied(event));

        condition.pattern = "b*y"; // Same as above
        assertFalse(condition.isSatisfied(event));

        condition.pattern = "*b*y*";
        assertTrue(condition.isSatisfied(event));

        condition.pattern = "nice";
        assertTrue(condition.isSatisfied(event)); // Lowercase N

        condition.pattern = "ice";
        assertFalse(condition.isSatisfied(event)); // partial match

        condition.pattern = "dog";
        assertFalse(condition.isSatisfied(event));
    }

    @Test
    public void testRandomField() {
        condition.key = "content.other_field";
        condition.pattern = "other_value";
        assertTrue(condition.isSatisfied(event));

        condition.pattern = "*value";
        assertTrue(condition.isSatisfied(event));

        condition.pattern = "value";
        assertFalse(condition.isSatisfied(event));

        condition.pattern = "?value";
        assertFalse(condition.isSatisfied(event));
    }
}
