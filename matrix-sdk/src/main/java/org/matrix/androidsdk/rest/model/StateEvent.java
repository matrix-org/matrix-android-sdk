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
package org.matrix.androidsdk.rest.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Class representing a state event content
 * Usually, only one field is not null, depending of the type of the Event.
 */
public class StateEvent {
    public String name;

    public String topic;

    @SerializedName("join_rule")
    public String joinRule;

    @SerializedName("guest_access")
    public String guestAccess;

    @SerializedName("alias")
    public String canonicalAlias;

    public List<String> aliases;

    public String algorithm;

    @SerializedName("history_visibility")
    public String historyVisibility;

    public String url;

    public List<String> groups;
}