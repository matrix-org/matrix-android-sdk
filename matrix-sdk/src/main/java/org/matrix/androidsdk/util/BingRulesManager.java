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

import org.matrix.androidsdk.MXDataHandler;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.client.BingRulesRestClient;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.bingrules.BingRule;
import org.matrix.androidsdk.rest.model.bingrules.BingRuleSet;
import org.matrix.androidsdk.rest.model.bingrules.BingRulesResponse;
import org.matrix.androidsdk.rest.model.bingrules.Condition;
import org.matrix.androidsdk.rest.model.bingrules.ContainsDisplayNameCondition;
import org.matrix.androidsdk.rest.model.bingrules.ContentRule;
import org.matrix.androidsdk.rest.model.bingrules.EventMatchCondition;
import org.matrix.androidsdk.rest.model.bingrules.RoomMemberCountCondition;

import java.util.ArrayList;
import java.util.List;

/**
 * Object that gets and processes bing rules from the server.
 */
public class BingRulesManager {

    public static interface onBingRuleUpdateListener {
        public void onBingRuleUpdateSuccess();
        public void onBingRuleUpdateFailure(String errorMessage);
    }

    private BingRulesRestClient mApiClient;
    private String mMyUserId;
    private MXDataHandler mDataHandler;

    private BingRuleSet mRulesSet = null;
    private List<BingRule> mRules = new ArrayList<BingRule>();

    private BingRule mDefaultBingRule = new BingRule(true);

    private boolean isReady = false;

    public BingRulesManager(MXSession session) {
        mApiClient = session.getBingRulesApiClient();
        mMyUserId = session.getCredentials().userId;
        mDataHandler = session.getDataHandler();
    }

    public boolean isReady() {
        return isReady;
    }

    /**
     * Load the bing rules from the server.
     * @param callback an async callback called when the rules are loaded
     */
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

    /**
     * Update the rule enable status.
     * @param kind the rule kind.
     * @param ruleId the rule ID.
     * @param status the new enable status.
     * @param callback an async callback.
     */
    public void updateEnableRuleStatus(String kind, String ruleId, boolean status, final ApiCallback<Void> callback) {
        mApiClient.updateEnableRuleStatus(kind, ruleId, status, callback);
    }


    /**
     * Returns the first notifiable bing rule which fulfills its condition with this event.
     * @param event the event
     * @return the first matched bing rule, null if none
     */
    public BingRule fulfilledBingRule(Event event) {
        // sanity check
        if (null == event) {
            return null;
        }

        if (!isReady) {
            return null;
        }

        // do not trigger notification for oneself messages
        if ((null != event.userId) && (event.userId.equals(mMyUserId))) {
            return null;
        }

        if (mRules != null) {
            // Go down the rule list until we find a match
            for (BingRule bingRule : mRules) {
                if (bingRule.isEnabled && eventMatchesConditions(event, bingRule.conditions)) {
                    for (JsonElement action : bingRule.actions) {
                        if (action.isJsonPrimitive()) {
                            String actionString = action.getAsString();

                            if (BingRule.ACTION_NOTIFY.equals(actionString) || BingRule.ACTION_COALESCE.equals(actionString)) {
                                return bingRule;
                            } else if (BingRule.ACTION_DONT_NOTIFY.equals(actionString)) {
                                return null;
                            }
                        }
                        // FIXME: Support other actions
                    }
                    // No supported actions were found, just bing
                    return mDefaultBingRule;
                }
            }

            // no rules are fulfilled
            return null;
        } else {
            // The default is to bing
            return mDefaultBingRule;
        }
    }

    private boolean eventMatchesConditions(Event event, List<Condition> conditions) {
        if ((conditions != null) && (event != null)) {
            for (Condition condition : conditions) {
                if (condition instanceof EventMatchCondition) {
                    if (!((EventMatchCondition) condition).isSatisfied(event)) {
                        return false;
                    }
                }
                else if (condition instanceof ContainsDisplayNameCondition) {
                    if (event.roomId != null) {
                        Room room = mDataHandler.getRoom(event.roomId);

                        // sanity checks
                        if ((null != room) && (null != room.getMember(mMyUserId))) {
                            // Best way to get your display name for now
                            String myDisplayName = room.getMember(mMyUserId).displayname;
                            if (!((ContainsDisplayNameCondition) condition).isSatisfied(event, myDisplayName)) {
                                return false;
                            }
                        }
                    }
                }
                else if (condition instanceof RoomMemberCountCondition) {
                    if (event.roomId != null) {
                        Room room = mDataHandler.getRoom(event.roomId);
                        if (!((RoomMemberCountCondition) condition).isSatisfied(room)) {
                            return false;
                        }
                    }
                }
                // FIXME: Handle device rules
            }
        }
        return true;
    }

    private void buildRules(BingRulesResponse bingRulesResponse) {
        mRules.clear();
        addRules(bingRulesResponse.global);
    }

    public  BingRuleSet pushRules() {
        return mRulesSet;
    }

    private void addRules(BingRuleSet ruleSet) {
        // Replace the list by ArrayList to be able to add/remove rules
        // Add the rule kind in each rule
        // Ensure that the null pointers are replaced by an empty list

        if (ruleSet.override != null) {
            ruleSet.override = new ArrayList<BingRule>(ruleSet.override);
            for(BingRule rule : ruleSet.override) {
                rule.kind = BingRule.KIND_OVERRIDE;
            }
            mRules.addAll(ruleSet.override);
        } else {
            ruleSet.override = new ArrayList<BingRule>(ruleSet.override);
        }

        if (ruleSet.content != null) {
            ruleSet.content = new ArrayList<ContentRule>(ruleSet.content);
            for(BingRule rule : ruleSet.content) {
                rule.kind = BingRule.KIND_CONTENT;
            }
            addContentRules(ruleSet.content);
        } else {
            ruleSet.content = new ArrayList<ContentRule>();
        }

        if (ruleSet.room != null) {
            ruleSet.room = new ArrayList<BingRule>(ruleSet.room);

            for(BingRule rule : ruleSet.room) {
                rule.kind = BingRule.KIND_ROOM;
            }
            addRoomRules(ruleSet.room);
        } else {
            ruleSet.room = new ArrayList<BingRule>();
        }

        if (ruleSet.sender != null) {
            ruleSet.sender = new ArrayList<BingRule>(ruleSet.sender);

            for(BingRule rule : ruleSet.sender) {
                rule.kind = BingRule.KIND_SENDER;
            }
            addSenderRules(ruleSet.sender);
        } else {
            ruleSet.sender = new ArrayList<BingRule>();
        }

        if (ruleSet.underride != null) {
            ruleSet.underride = new ArrayList<BingRule>(ruleSet.underride);
            for(BingRule rule : ruleSet.underride) {
                rule.kind = BingRule.KIND_UNDERRIDE;
            }
            mRules.addAll(ruleSet.underride);
        } else {
            ruleSet.underride = new ArrayList<BingRule>();
        }

        mRulesSet = ruleSet;
    }

    private void addContentRules(List<ContentRule> rules) {
        for (ContentRule rule : rules) {
            EventMatchCondition condition = new EventMatchCondition();
            condition.kind = Condition.KIND_EVENT_MATCH;
            condition.key = "content.body";
            condition.pattern = rule.pattern;

            rule.addCondition(condition);

            mRules.add(rule);
        }
    }

    private void addRoomRules(List<BingRule> rules) {
        for (BingRule rule : rules) {
            EventMatchCondition condition = new EventMatchCondition();
            condition.kind = Condition.KIND_EVENT_MATCH;
            condition.key = "room_id";
            condition.pattern = rule.ruleId;

            rule.addCondition(condition);

            mRules.add(rule);
        }
    }

    private void addSenderRules(List<BingRule> rules) {
        for (BingRule rule : rules) {
            EventMatchCondition condition = new EventMatchCondition();
            condition.kind = Condition.KIND_EVENT_MATCH;
            condition.key = "user_id";
            condition.pattern = rule.ruleId;

            rule.addCondition(condition);

            mRules.add(rule);
        }
    }

    /**
     * Toogle a rule.
     * @param rule the bing rule to toggle.
     * @param listener the rule update listener.
     * @return the matched bing rule or null it doesn't exist.
     */
    public BingRule toogleRule(final BingRule rule, final onBingRuleUpdateListener listener) {

        if (null != rule) {
            updateEnableRuleStatus(rule.kind, rule.ruleId, !rule.isEnabled, new SimpleApiCallback<Void>() {
                @Override
                public void onSuccess(Void info) {
                    rule.isEnabled = !rule.isEnabled;
                    if (listener != null) {
                        listener.onBingRuleUpdateSuccess();
                    }
                }

                private void onError(String message) {
                    if (null != listener) {
                        listener.onBingRuleUpdateFailure(message);
                    }
                }

                /**
                 * Called if there is a network error.
                 * @param e the exception
                 */
                @Override
                public void onNetworkError(Exception e) {
                    onError(e.getLocalizedMessage());
                }

                /**
                 * Called in case of a Matrix error.
                 * @param e the Matrix error
                 */
                @Override
                public void onMatrixError(MatrixError e)  {
                    onError(e.getLocalizedMessage());
                }

                /**
                 * Called for some other type of error.
                 * @param e the exception
                 */
                @Override
                public void onUnexpectedError(Exception e) {
                    onError(e.getLocalizedMessage());
                }
            });

        }

        return rule;
    }

    /**
     * Delete the rule.
     * @param rule the rule to delete.
     * @param listener the rule update listener.
     */
    public void deleteRule(final BingRuleSet bingRuleSet, final BingRule rule, final onBingRuleUpdateListener listener)  {
        mApiClient.deleteRule(rule.kind, rule.ruleId, new SimpleApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                bingRuleSet.remove(rule);
                if (listener != null) {
                    listener.onBingRuleUpdateSuccess();
                }
            }

            private void onError(String message) {
                if (null != listener) {
                    listener.onBingRuleUpdateFailure(message);
                }
            }

            /**
             * Called if there is a network error.
             * @param e the exception
             */
            @Override
            public void onNetworkError(Exception e) {
                onError(e.getLocalizedMessage());
            }

            /**
             * Called in case of a Matrix error.
             * @param e the Matrix error
             */
            @Override
            public void onMatrixError(MatrixError e)  {
                onError(e.getLocalizedMessage());
            }

            /**
             * Called for some other type of error.
             * @param e the exception
             */
            @Override
            public void onUnexpectedError(Exception e) {
                onError(e.getLocalizedMessage());
            }
        });

    }
}
