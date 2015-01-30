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

public class BingRule {
    public static final String ACTION_NOTIFY = "notify";
    public static final String ACTION_DONT_NOTIFY = "dont_notify";

    public String ruleId;
    public List<Condition> conditions;
    public List<String> actions;

    public void addCondition(Condition condition) {
        if (conditions == null) {
            conditions = new ArrayList<Condition>();
        }
        conditions.add(condition);
    }
}
