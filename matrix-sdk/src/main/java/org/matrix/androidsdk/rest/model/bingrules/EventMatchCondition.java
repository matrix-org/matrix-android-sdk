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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.util.JsonUtils;

public class EventMatchCondition extends Condition {
    public String key;
    public String pattern;

    public EventMatchCondition() {
        kind = Condition.KIND_EVENT_MATCH;
    }

    /**
     * Returns whether the given event satisfies the condition.
     * @param event the event
     * @return true if the event satisfies the condition
     */
    public boolean isSatisfied(Event event) {
        JsonObject eventJson = JsonUtils.toJson(event);
        // Extract the value that we want to match
        String fieldVal = extractField(eventJson, key);
        if (fieldVal == null) {
            return false;
        }
        // Process the pattern
        String patternRegex = globToRegex(pattern);

        return fieldVal.matches(patternRegex);
    }

    private String extractField(JsonObject jsonObject, String fieldPath) {
        String[] fieldParts = fieldPath.split("\\.");
        JsonElement jsonElement = null;
        for (String field : fieldParts) {
            jsonElement = jsonObject.get(field);
            if (jsonElement == null) {
                return null;
            }
            if (jsonElement.isJsonObject()) {
                jsonObject = (JsonObject) jsonElement;
            }
        }
        return (jsonElement == null) ? null : jsonElement.getAsString();
    }

    private String globToRegex(String glob) {
        String res = glob.replace("*", ".*").replace("?", ".");

        // If no special characters were found (detected here by no replacements having been made),
        // add asterisks and boundaries to both sides
        if (res.equals(glob)) {
            res = ".*\\b" + res + "\\b.*";
        }
        return res;
    }
}
