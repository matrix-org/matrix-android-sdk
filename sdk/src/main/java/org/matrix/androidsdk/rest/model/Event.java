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
package org.matrix.androidsdk.rest.model;

import com.google.gson.JsonObject;

/**
 * Generic event class with all possible fields for events.
 */
public class Event {
    public static final String EVENT_TYPE_PRESENCE = "m.presence";
    public static final String EVENT_TYPE_MESSAGE = "m.room.message";
    public static final String EVENT_TYPE_FEEDBACK = "m.room.message.feedback";
    public static final String EVENT_TYPE_STATE_ROOM_NAME = "m.room.name";
    public static final String EVENT_TYPE_STATE_ROOM_TOPIC = "m.room.topic";
    public static final String EVENT_TYPE_STATE_ROOM_MEMBER = "m.room.member";
    public static final String EVENT_TYPE_STATE_ROOM_CREATE = "m.room.create";
    public static final String EVENT_TYPE_STATE_ROOM_JOIN_RULES = "m.room.join_rules";
    public static final String EVENT_TYPE_STATE_ROOM_POWER_LEVELS = "m.room.power_levels";
    public static final String EVENT_TYPE_STATE_ROOM_ADD_STATE_LEVEL = "m.room.add_state_level";
    public static final String EVENT_TYPE_STATE_ROOM_SEND_EVENT_LEVEL = "m.room.send_event_level";
    public static final String EVENT_TYPE_STATE_ROOM_OPS_LEVELS = "m.room.ops_levels";
    public static final String EVENT_TYPE_STATE_ROOM_ALIASES = "m.room.aliases";

    public String type;
    public JsonObject content;

    public String eventId;
    public String roomId;
    public String userId;
    public long origin_server_ts;
    public long age;

    // Specific to state events
    public String stateKey;
    public JsonObject prevContent;
}
