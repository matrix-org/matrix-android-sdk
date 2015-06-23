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


public class Condition {
    public static final String KIND_EVENT_MATCH = "event_match";
    public static final String KIND_DEVICE = "device";
    public static final String KIND_CONTAINS_DISPLAY_NAME = "contains_display_name";
    public static final String KIND_ROOM_MEMBER_COUNT = "room_member_count";

    public String kind;

    public Condition deepCopy() {
        Condition condition = new Condition();
        condition.kind = kind;

        return condition;
    }
}
