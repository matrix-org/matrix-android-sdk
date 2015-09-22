/* 
 * Copyright 2015 OpenMarket Ltd
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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.matrix.androidsdk.HomeserverConnectionConfig;
import org.matrix.androidsdk.RestClient;
import org.matrix.androidsdk.rest.api.BingRulesApi;
import org.matrix.androidsdk.rest.api.CallRulesApi;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.login.Credentials;

import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;

public class CallRestClient extends RestClient<CallRulesApi> {

    /**
     * {@inheritDoc}
     */
    public CallRestClient(HomeserverConnectionConfig hsConfig, Credentials credentials) {
        super(hsConfig, credentials, CallRulesApi.class, RestClient.URI_API_PREFIX, false);
    }

    public void getTurnServer(final ApiCallback<JsonObject> callback) {
        mApi.getTurnServer(new Callback<JsonObject>() {
            @Override
            public void success(JsonObject turnServer, Response response) {
                callback.onSuccess(turnServer);
            }

            @Override
            public void failure(RetrofitError error) {
                callback.onUnexpectedError(error);
            }
        });
    }
}
