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
package org.matrix.androidsdk.rest.json;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import org.matrix.androidsdk.rest.model.bingrules.Condition;
import org.matrix.androidsdk.rest.model.bingrules.ContainsDisplayNameCondition;
import org.matrix.androidsdk.rest.model.bingrules.DeviceCondition;
import org.matrix.androidsdk.rest.model.bingrules.EventMatchCondition;
import org.matrix.androidsdk.rest.model.bingrules.RoomMemberCountCondition;

import java.lang.reflect.Type;

public class ConditionDeserializer implements JsonDeserializer<Condition> {
    @Override
    public Condition deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();
        JsonElement kindElement = jsonObject.get("kind");
        if (kindElement != null) {
            String kind = kindElement.getAsString();

            if (Condition.KIND_EVENT_MATCH.equals(kind)) {
                return context.deserialize(json, EventMatchCondition.class);
            }
            if (Condition.KIND_DEVICE.equals(kind)) {
                return context.deserialize(json, DeviceCondition.class);
            }
            if (Condition.KIND_CONTAINS_DISPLAY_NAME.equals(kind)) {
                return context.deserialize(json, ContainsDisplayNameCondition.class);
            }
            if (Condition.KIND_ROOM_MEMBER_COUNT.equals(kind)) {
                return context.deserialize(json, RoomMemberCountCondition.class);
            }
        }
        return null;
    }
}
