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

import android.net.Uri;

import com.google.gson.JsonObject;

import org.matrix.androidsdk.HomeserverConnectionConfig;
import org.matrix.androidsdk.RestClient;
import org.matrix.androidsdk.rest.api.LoginApi;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.RestAdapterCallback;
import org.matrix.androidsdk.rest.model.login.Credentials;
import org.matrix.androidsdk.rest.model.login.PasswordLoginParams;
import org.matrix.androidsdk.rest.model.login.TokenLoginParams;

import java.util.UUID;

import retrofit.client.Response;

/**
 * Class used to make requests to the login API.
 */
public class LoginRestClient extends RestClient<LoginApi> {

    private Uri mHsUri;

    /**
     * Public constructor.
     * @param hsConfig the home server connection config
     */
    public LoginRestClient(HomeserverConnectionConfig hsConfig) {
        super(hsConfig, LoginApi.class, RestClient.URI_API_PREFIX, false);
        mHsUri = hsConfig.getHomeserverUri();
    }

    /**
     * Attempt a user/password log in.
     * @param user the user name
     * @param password the password
     * @param callback the callback success and failure callback
     */
    public void loginWithPassword(final String user, final String password, final ApiCallback<Credentials> callback) {
        final String description = "loginWithPassword user : " + user;

        PasswordLoginParams params = new PasswordLoginParams();
        params.user = user;
        params.password = password;

        mApi.login(params, new RestAdapterCallback<JsonObject>(description, mUnsentEventsManager, callback,

                new RestAdapterCallback.RequestRetryCallBack() {
                    @Override
                    public void onRetry() {
                        loginWithPassword(user, password, callback);
                    }
                }

                ) {
            @Override
            public void success(JsonObject jsonObject, Response response) {
                mCredentials = gson.fromJson(jsonObject, Credentials.class);
                callback.onSuccess(mCredentials);
            }
        });
    }

    /**
     * Attempt a user/token log in.
     * @param user the user name
     * @param token the token
     * @param callback the callback success and failure callback
     */
    public void loginWithToken(final String user, final String token, final ApiCallback<Credentials> callback) {
         loginWithToken(user, token, UUID.randomUUID().toString(), callback);
    }

    /**
     * Attempt a user/token log in.
     * @param user the user name
     * @param token the token
     * @param txn_id the client transactio id to include in the request
     * @param callback the callback success and failure callback
     */
    public void loginWithToken(final String user, final String token, final String txn_id, final ApiCallback<Credentials> callback) {
        final String description = "loginWithPassword user : " + user;

        TokenLoginParams params = new TokenLoginParams();
        params.user = user;
        params.token = token;
        params.txn_id = txn_id;

        mApi.login(params, new RestAdapterCallback<JsonObject>(description, mUnsentEventsManager, callback,

                new RestAdapterCallback.RequestRetryCallBack() {
                    @Override
                    public void onRetry() {
                        loginWithToken(user, token, txn_id, callback);
                    }
                }

        ) {
            @Override
            public void success(JsonObject jsonObject, Response response) {
                mCredentials = gson.fromJson(jsonObject, Credentials.class);
                callback.onSuccess(mCredentials);
            }
        });
    }
}
