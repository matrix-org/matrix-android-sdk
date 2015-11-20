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
import org.matrix.androidsdk.rest.api.CallRulesApi;
import org.matrix.androidsdk.rest.api.RegistrationApi;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.RestAdapterCallback;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.login.Credentials;
import org.matrix.androidsdk.rest.model.login.TokenRefreshParams;
import org.matrix.androidsdk.rest.model.login.TokenRefreshResponse;

import retrofit.client.Response;

/**
 * Class used to make requests to the registration API.
 */
public class RegistrationRestClient extends RestClient<RegistrationApi> {

    /**
     * {@inheritDoc}
     */
    public RegistrationRestClient(HomeserverConnectionConfig hsConfig) {
        super(hsConfig, RegistrationApi.class, RestClient.URI_API_PREFIX_V2, false);
    }

    /**
     * Attempt a user/password registration.
     * @param callback the callback success and failure callback
     */
    public void refreshTokens( final ApiCallback<Credentials> callback) {
        final String description = "refreshTokens";

        TokenRefreshParams params = new TokenRefreshParams();
        params.refresh_token = mCredentials.refreshToken;

        mApi.tokenrefresh(params, new RestAdapterCallback<TokenRefreshResponse>(description, mUnsentEventsManager, callback, null) {
            @Override
            public void success(TokenRefreshResponse tokenreponse, Response response) {
                mCredentials.refreshToken = tokenreponse.refresh_token;
                mCredentials.accessToken = tokenreponse.access_token;
                if (null != callback) {
                    callback.onSuccess(mCredentials);
                }
            }
            
            /**
             * Called if there is a network error.
             * @param e the exception
             */
            public void onNetworkError(Exception e) {
                if (null != callback) {
                    callback.onNetworkError(e);
                }
            }

            /**
             * Called in case of a Matrix error.
             * @param e the Matrix error
             */
            public void onMatrixError(MatrixError e) {
                if (null != callback) {
                    callback.onMatrixError(e);
                }
            }

            /**
             * Called for some other type of error.
             * @param e the exception
             */
            public void onUnexpectedError(Exception e) {
                if (null != callback) {
                    callback.onUnexpectedError(e);
                }
            }
        });
    }
}
