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
package org.matrix.androidsdk.rest.client;

import org.matrix.androidsdk.HomeserverConnectionConfig;
import org.matrix.androidsdk.RestClient;
import org.matrix.androidsdk.rest.api.BingRulesApi;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.DefaultRetrofit2CallbackWrapper;
import org.matrix.androidsdk.rest.model.bingrules.BingRule;
import org.matrix.androidsdk.rest.model.bingrules.BingRulesResponse;

public class BingRulesRestClient extends RestClient<BingRulesApi> {

    /**
     * {@inheritDoc}
     */
    public BingRulesRestClient(HomeserverConnectionConfig hsConfig) {
        super(hsConfig, BingRulesApi.class, RestClient.URI_API_PREFIX_PATH_R0, false);
    }

    public void getAllBingRules(final ApiCallback<BingRulesResponse> callback) {
        mApi
            .getAllBingRules()
            .enqueue(new DefaultRetrofit2CallbackWrapper<>(callback));
    }

    public void updateEnableRuleStatus(
        String Kind, String ruleId, boolean status, final ApiCallback<Void> callback
    ) {
        mApi
            .updateEnableRuleStatus(Kind, ruleId, status)
            .enqueue(new DefaultRetrofit2CallbackWrapper<>(callback));
    }

    public void deleteRule(String Kind, String ruleId, final ApiCallback<Void> callback) {
        mApi.deleteRule(Kind, ruleId).enqueue(new DefaultRetrofit2CallbackWrapper<>(callback));
    }

    public void addRule(BingRule rule, final ApiCallback<Void> callback) {
        mApi.addRule(rule.kind, rule.ruleId, rule).enqueue(new DefaultRetrofit2CallbackWrapper<>(callback));
    }
}
