/*
 * Copyright 2019 New Vector Ltd
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
import org.matrix.androidsdk.rest.api.OpenIdApi;
import org.matrix.androidsdk.rest.callback.RestAdapterCallback;
import org.matrix.androidsdk.rest.model.openid.RequestOpenIdTokenResponse;

import java.util.HashMap;

public class OpenIdRestClient extends RestClient<OpenIdApi> {
    /**
     * {@inheritDoc}
     */
    public OpenIdRestClient(HomeServerConnectionConfig hsConfig) {
        super(hsConfig, OpenIdApi.class, RestClient.URI_API_PREFIX_PATH_R0, JsonUtils.getGson(false));
    }

    /**
     * Gets a bearer token from the homeserver that the user can
     * present to a third party in order to prove their ownership
     * of the Matrix account they are logged into.
     *
     * @param userId   the user id
     * @param callback the asynchronous callback called when finished
     */
    public void requestToken(final String userId, final ApiCallback<RequestOpenIdTokenResponse> callback) {
        final String description = "openIdToken userId : " + userId;

        mApi.requestToken(userId, new HashMap<>())
                .enqueue(new RestAdapterCallback<RequestOpenIdTokenResponse>(description, mUnsentEventsManager, callback,
                        new RestAdapterCallback.RequestRetryCallBack() {
                            @Override
                            public void onRetry() {
                                requestToken(userId, callback);
                            }
                        }));
    }
}
