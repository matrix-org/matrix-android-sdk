/* 
 * Copyright 2014 OpenMarket Ltd
 * Copyright 2017 Vector Creations Ltd
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
package org.matrix.androidsdk.rest.json;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.matrix.androidsdk.rest.model.bingrules.Condition;
import org.matrix.androidsdk.rest.model.bingrules.ContainsDisplayNameCondition;
import org.matrix.androidsdk.rest.model.bingrules.DeviceCondition;
import org.matrix.androidsdk.rest.model.bingrules.EventMatchCondition;
import org.matrix.androidsdk.rest.model.bingrules.RoomMemberCountCondition;
import org.matrix.androidsdk.util.JsonUtils;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Modifier;

import static org.junit.Assert.*;

/**
 * Class for unit testing the ConditionDeserializer.
 */

@RunWith(RobolectricTestRunner.class)
public class ConditionDeserializerTest {

    private Gson gson = new GsonBuilder()
            .setFieldNamingStrategy(new JsonUtils.MatrixFieldNamingStrategy())
            .excludeFieldsWithModifiers(Modifier.PRIVATE, Modifier.STATIC)
            .registerTypeAdapter(Condition.class, new ConditionDeserializer())
            .create();

    @Test
    public void testEventMatchCondition() {
        String conditionJson = "{'kind': 'event_match', 'key': 'key1', 'pattern': 'pattern1'}";
        Condition condition = gson.fromJson(conditionJson, Condition.class);

        assertTrue(condition instanceof EventMatchCondition);

        EventMatchCondition eventMatchCondition = (EventMatchCondition) condition;
        assertEquals("key1", eventMatchCondition.key);
        assertEquals("pattern1", eventMatchCondition.pattern);
    }

    @Test
    public void testDeviceCondition() {
        String conditionJson = "{'kind': 'device', 'profile_tag': 'proftag1'}";
        Condition condition = gson.fromJson(conditionJson, Condition.class);

        assertTrue(condition instanceof DeviceCondition);

        DeviceCondition deviceCondition = (DeviceCondition) condition;
        assertEquals("proftag1", deviceCondition.profileTag);
    }

    @Test
    public void testRoomMemberCountCondition() {
        String conditionJson = "{'kind': 'room_member_count', 'is': 'is1'}";
        Condition condition = gson.fromJson(conditionJson, Condition.class);

        assertTrue(condition instanceof RoomMemberCountCondition);

        RoomMemberCountCondition roomMemberCountConditionCondition = (RoomMemberCountCondition) condition;
        assertEquals("is1", roomMemberCountConditionCondition.is);
    }

    @Test
    public void testContainsDisplayNameCondition() {
        String conditionJson = "{'kind': 'contains_display_name'}";
        Condition condition = gson.fromJson(conditionJson, Condition.class);

        assertTrue(condition instanceof ContainsDisplayNameCondition);
    }

    @Test
    public void testUnknownKind() {
        String conditionJson = "{'kind': 'strange_unknown_kind'}";
        Condition condition = gson.fromJson(conditionJson, Condition.class);

        assertNotNull(condition);
    }

    @Test
    public void testNoKind() {
        String conditionJson = "{'some_other_field': 'some_value'}";
        Condition condition = gson.fromJson(conditionJson, Condition.class);

        assertNull(condition);
    }
}
