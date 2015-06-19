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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class BingRule {
    public static final String ACTION_NOTIFY = "notify";
    public static final String ACTION_DONT_NOTIFY = "dont_notify";
    public static final String ACTION_COALESCE = "coalesce";
    private static final String ACTION_PARAMETER_SET_TWEAK = "set_tweak";
    private static final String ACTION_SET_TWEAK_SOUND_VALUE = "sound";

    public String ruleId = null;
    public List<Condition> conditions = null;
    public List<JsonElement> actions = null;
    @SerializedName("default")
    public boolean isDefault = false;

    @SerializedName("enabled")
    public boolean isEnabled = true;

    public BingRule(boolean isDefaultValue) {
        this.isDefault = isDefaultValue;
    }

    public BingRule() {
        this.isDefault = false;
    }

    public void addCondition(Condition condition) {
        if (conditions == null) {
            conditions = new ArrayList<Condition>();
        }
        conditions.add(condition);
    }

    public boolean shouldPlaySound() {
        boolean playSound = false;

        if (null != actions) {
            try {
                for (JsonElement json : actions) {
                    if (json.isJsonObject()) {
                        JsonObject object = json.getAsJsonObject();

                        if (object.has(ACTION_PARAMETER_SET_TWEAK)) {
                            playSound = object.get(ACTION_PARAMETER_SET_TWEAK).getAsString().equals(ACTION_SET_TWEAK_SOUND_VALUE);
                            break;
                        }
                    }
                }
            } catch (Exception e) {

            }
        }
        return playSound;
    }
}
