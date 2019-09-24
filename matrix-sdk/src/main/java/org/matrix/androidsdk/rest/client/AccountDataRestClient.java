/*
 * Copyright 2015 OpenMarket Ltd
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
package org.matrix.androidsdk.rest.client;

import org.matrix.androidsdk.HomeServerConnectionConfig;
import org.matrix.androidsdk.RestClient;
import org.matrix.androidsdk.core.JsonUtils;
import org.matrix.androidsdk.core.callback.ApiCallback;
import org.matrix.androidsdk.rest.api.AccountDataApi;
import org.matrix.androidsdk.rest.callback.RestAdapterCallback;

public class AccountDataRestClient extends RestClient<AccountDataApi> {
    /**
     * {@inheritDoc}
     */
    public AccountDataRestClient(HomeServerConnectionConfig hsConfig) {
        super(hsConfig, AccountDataApi.class, RestClient.URI_API_PREFIX_PATH_R0, JsonUtils.getGson(true));
    }

    /**
     * Set some account_data for the client.
     *
     * @param userId   the user id
     * @param type     the account data type.
     * @param params   the put params.
     * @param callback the asynchronous callback called when finished
     */
    public void setAccountData(final String userId, final String type, final Object params, final ApiCallback<Void> callback) {
        // privacy
        //final String description = "setAccountData userId : " + userId + " type " + type + " params " + params;
        final String description = "setAccountData userId : " + userId + " type " + type;

        mApi.setAccountData(userId, type, params)
                .enqueue(new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                    @Override
                    public void onRetry() {
                        setAccountData(userId, type, params, callback);
                    }
                }));
    }

}
