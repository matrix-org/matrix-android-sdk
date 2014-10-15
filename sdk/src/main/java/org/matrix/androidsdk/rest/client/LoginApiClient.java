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

import org.matrix.androidsdk.MXApiClient;
import org.matrix.androidsdk.api.LoginApi;
import org.matrix.androidsdk.api.response.MatrixError;
import org.matrix.androidsdk.api.response.login.Credentials;
import org.matrix.androidsdk.api.response.login.PasswordLoginParams;

import retrofit.Callback;
import retrofit.RestAdapter;
import retrofit.RetrofitError;
import retrofit.client.Response;

/**
 * Class used to make requests to the login API.
 */
public class LoginApiClient extends MXApiClient {

    LoginApi mApi;

    /**
     * Public constructor.
     * @param hsUri the home server URI
     */
    public LoginApiClient(Uri hsUri) {
        super(hsUri);
    }

    @Override
    protected void initApi(RestAdapter restAdapter) {
        mApi = restAdapter.create(LoginApi.class);
    }

    /**
     * Protected setter for injection by unit tests.
     * @param api the api object
     */
    protected void setApi(LoginApi api) {
        mApi = api;
    }

    /**
     * Callback to return the user's credentials on success, a matrix error on error.
     */
    public interface LoginCallback {

        /**
         * Called when the user was successfully logged in.
         * @param credentials the user's credentials
         */
        public void onLoggedIn(Credentials credentials);

        /**
         * Called on login error.
         * @param error the error
         */
        public void onError(MatrixError error);
    }

    /**
     * Attempt a user/password log in.
     * @param user the user name
     * @param password the password
     * @param callback the callback success and failure callback
     */
    public void loginWithPassword(String user, String password, final LoginCallback callback) {
        PasswordLoginParams params = new PasswordLoginParams();
        params.user = user;
        params.password = password;

        mApi.login(params, new Callback<JsonObject>() {
            @Override
            public void success(JsonObject jsonObject, Response response) {
                mCredentials = gson.fromJson(jsonObject, Credentials.class);
                callback.onLoggedIn(mCredentials);
            }

            @Override
            public void failure(RetrofitError error) {
                callback.onError((MatrixError) error.getBodyAs(MatrixError.class));
            }
        });
    }
}
