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

/**
 * subclass representing a subset of the state of the room at the time of the invite, if membership is invite
 */
public class StrippedState implements java.io.Serializable {

    /**
     * The content for the event.
     */
    EventContent content;

    /**
     * The type for the event. One of: ["m.room.join_rules", "m.room.canonical_alias", "m.room.avatar", "m.room.name"]
     */
    String type;

    /**
     * The state_key for the event
     */
    String state_key;
}