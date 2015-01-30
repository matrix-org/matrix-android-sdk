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
package org.matrix.androidsdk.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.client.BingRulesRestClient;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.bingrules.BingRule;
import org.matrix.androidsdk.rest.model.bingrules.BingRuleSet;
import org.matrix.androidsdk.rest.model.bingrules.BingRulesResponse;
import org.matrix.androidsdk.rest.model.bingrules.Condition;
import org.matrix.androidsdk.rest.model.bingrules.ContentRule;

import java.util.ArrayList;
import java.util.List;

public class BingRulesManager {

    private BingRulesRestClient mApiClient;

    private List<BingRule> mRules = new ArrayList<BingRule>();

    private boolean isReady = false;

    public BingRulesManager(BingRulesRestClient apiClient) {
        mApiClient = apiClient;
    }

    public boolean isReady() {
        return isReady;
    }

    public void loadRules(final ApiCallback<Void> callback) {
        isReady = false;
        mApiClient.getAllBingRules(new SimpleApiCallback<BingRulesResponse>(callback) {
            @Override
            public void onSuccess(BingRulesResponse info) {
                buildRules(info);
                isReady = true;
                if (callback != null) {
                    callback.onSuccess(null);
                }
            }
        });
    }

    public boolean shouldBing(Event event) {
        if (!isReady) {
            return false;
        }
        if (mRules != null) {
            // Go down the rule list until we find a match
            for (BingRule bingRule : mRules) {
                if (eventMatchesConditions(event, bingRule.conditions)) {
                    for (String action : bingRule.actions) {
                        if (BingRule.ACTION_NOTIFY.equals(action)) {
                            return true;
                        }
                        else if (BingRule.ACTION_DONT_NOTIFY.equals(action)) {
                            return false;
                        }
                        // FIXME: Support other actions
                    }
                }
            }
        }
        // The default is to bing
        return true;
    }

    private boolean eventMatchesConditions(Event event, List<Condition> conditions) {
        if (conditions != null) {
            for (Condition condition : conditions) {
                if (Condition.KIND_EVENT_MATCH.equals(condition.kind)) {
                    JsonObject eventJson = JsonUtils.toJson(event);
                    String fieldVal = extractField(eventJson, condition.key);
                    String patternRegex = globToRegex(condition.pattern);
                    if (!fieldVal.matches(patternRegex)) {
                        return false;
                    }
                }
            }
        }
        return true;
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

    private void buildRules(BingRulesResponse bingRulesResponse) {
        mRules.clear();
        // FIXME: Handle device rules
        addRules(bingRulesResponse.global);
    }

    private String globToRegex(String glob) {
        return glob.replace("*", ".*").replace("?", ".");
    }

    private void addRules(BingRuleSet ruleSet) {
        mRules.addAll(ruleSet.override);
        addContentRules(ruleSet.content);
        addRoomRules(ruleSet.room);
        addSenderRules(ruleSet.sender);
        mRules.addAll(ruleSet.underride);
    }

    private void addContentRules(List<ContentRule> rules) {
        for (ContentRule rule : rules) {
            Condition condition = new Condition();
            condition.kind = Condition.KIND_EVENT_MATCH;
            condition.key = "content.body";
            condition.pattern = rule.pattern;

            rule.addCondition(condition);

            mRules.add(rule);
        }
    }

    private void addRoomRules(List<BingRule> rules) {
        for (BingRule rule : rules) {
            Condition condition = new Condition();
            condition.kind = Condition.KIND_EVENT_MATCH;
            condition.key = "room_id";
            condition.pattern = rule.ruleId;

            rule.addCondition(condition);

            mRules.add(rule);
        }
    }

    private void addSenderRules(List<BingRule> rules) {
        for (BingRule rule : rules) {
            Condition condition = new Condition();
            condition.kind = Condition.KIND_EVENT_MATCH;
            condition.key = "user_id";
            condition.pattern = rule.ruleId;

            rule.addCondition(condition);

            mRules.add(rule);
        }
    }
}
