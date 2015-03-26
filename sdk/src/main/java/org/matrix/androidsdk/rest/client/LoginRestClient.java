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
import android.util.Log;

import com.google.gson.JsonObject;

import org.matrix.androidsdk.RestClient;
import org.matrix.androidsdk.rest.api.LoginApi;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.RestAdapterCallback;
import org.matrix.androidsdk.rest.model.login.Credentials;
import org.matrix.androidsdk.rest.model.login.PasswordLoginParams;

import retrofit.client.Response;

/**
 * Class used to make requests to the login API.
 */
public class LoginRestClient extends RestClient<LoginApi> {

    private Uri mHsUri;

    /**
     * Public constructor.
     * @param hsUri the home server URI
     */
    public LoginRestClient(Uri hsUri) {
        super(hsUri, LoginApi.class, RestClient.URI_API_PREFIX);
        mHsUri = hsUri;
    }

    /**
     * Attempt a user/password log in.
     * @param user the user name
     * @param password the password
     * @param callback the callback success and failure callback
     */
    public void loginWithPassword(final String user, final String password, final ApiCallback<Credentials> callback) {
        PasswordLoginParams params = new PasswordLoginParams();
        params.user = user;
        params.password = password;

        mApi.login(params, new RestAdapterCallback<JsonObject>(mUnsentEventsManager, callback,

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
                // Override the home server
                mCredentials.homeServer = mHsUri.toString();
                callback.onSuccess(mCredentials);
            }
        });
    }
}
