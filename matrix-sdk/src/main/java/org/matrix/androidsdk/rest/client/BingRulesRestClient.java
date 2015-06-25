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

import org.matrix.androidsdk.RestClient;
import org.matrix.androidsdk.rest.api.BingRulesApi;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.bingrules.BingRule;
import org.matrix.androidsdk.rest.model.bingrules.BingRulesResponse;
import org.matrix.androidsdk.rest.model.login.Credentials;

import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;

public class BingRulesRestClient extends RestClient<BingRulesApi> {

    /**
     * {@inheritDoc}
     */
    public BingRulesRestClient(Credentials credentials) {
        super(credentials, BingRulesApi.class, RestClient.URI_API_PREFIX, false);
    }

    public void getAllBingRules(final ApiCallback<BingRulesResponse> callback) {
        //mApi.getAllBingRules(new RestAdapterCallback<BingRulesResponse>(callback));
        mApi.getAllBingRules(new Callback<BingRulesResponse>() {
            @Override
            public void success(BingRulesResponse bingRulesResponse, Response response) {
                callback.onSuccess(bingRulesResponse);
            }

            @Override
            public void failure(RetrofitError error) {
                callback.onUnexpectedError(error);
            }
        });
    }

    public BingRulesResponse getAllBingRules() {
        return mApi.getAllBingRules();
    }

    public void updateEnableRuleStatus(String Kind, String ruleId, boolean status, final ApiCallback<Void> callback) {
        mApi.updateEnableRuleStatus(Kind, ruleId, status, new Callback<Void> () {
            @Override
            public void success (Void voidObject, Response response){
                callback.onSuccess(voidObject);
            }

            @Override
            public void failure (RetrofitError error){
                callback.onUnexpectedError(error);
            }
        });
    }

    public void deleteRule(String Kind, String ruleId, final ApiCallback<Void> callback) {
        mApi.deleteRule(Kind, ruleId, new Callback<Void>() {
            @Override
            public void success(Void voidObject, Response response) {
                callback.onSuccess(voidObject);
            }

            @Override
            public void failure(RetrofitError error) {
                callback.onUnexpectedError(error);
            }
        });
    }

    public void addRule(BingRule rule, final ApiCallback<Void> callback) {
        mApi.addRule(rule.kind, rule.ruleId, rule, new Callback<Void>() {
            @Override
            public void success(Void voidObject, Response response) {
                callback.onSuccess(voidObject);
            }

            @Override
            public void failure(RetrofitError error) {
                callback.onUnexpectedError(error);
            }
        });
    }

}
