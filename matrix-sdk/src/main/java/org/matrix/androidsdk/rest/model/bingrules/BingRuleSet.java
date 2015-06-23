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

import java.util.ArrayList;
import java.util.List;

public class BingRuleSet {

    public List<BingRule> override;
    public List<ContentRule> content;
    public List<BingRule> room;
    public List<BingRule> sender;
    public List<BingRule> underride;

    private BingRule findRule(List<BingRule> rules, String ruleID) {
        for(BingRule rule : rules) {
            if (ruleID.equals(rule.ruleId)) {
                return rule;
            }
        }
        return null;
    }

    private BingRule findContentRule(List<ContentRule> rules, String ruleID) {
        for(BingRule rule : rules) {
            if (ruleID.equals(rule.ruleId)) {
                return rule;
            }
        }
        return null;
    }

    /**
     * Find a rule from its ruleID.
     * @param ruleId a RULE_ID_XX value
     * @return the matched bing rule or null it doesn't exist.
     */
    public BingRule findRule(String ruleId) {
        // sanity check
        if (null != ruleId) {
            if (BingRule.RULE_ID_CONTAIN_USER_NAME.equals(ruleId)) {
                return findContentRule(content, ruleId);
            } else if (BingRule.RULE_ID_DISABLE_ALL.equals(ruleId) || (BingRule.RULE_ID_SUPPRESS_BOTS_NOTIFICATIONS.equals(ruleId))) {
                return findRule(override, ruleId);
            } else {
                return findRule(underride, ruleId);
            }
        }

        return null;
    }

    public List<BingRule> getContent() {
        ArrayList<BingRule> res = new ArrayList<BingRule>();

        if (null != content) {
            for(BingRule rule : content) {
                if (!rule.ruleId.startsWith(".m.")) {
                    res.add(rule);
                }
            }
        }

        return res;
    }

    public List<BingRule> getRoom() {
        if (null == room) {
            return new ArrayList<BingRule>();
        } else {
            return room;
        }
    }

    public List<BingRule> getSender() {
        if (null == sender) {
            return new ArrayList<BingRule>();
        } else {
            return sender;
        }
    }

}
