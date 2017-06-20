/* 
 * Copyright 2014 OpenMarket Ltd
 * Copyright 2017 Vector Creations Ltd
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

import android.os.Build;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.google.gson.JsonObject;

import org.matrix.androidsdk.HomeserverConnectionConfig;
import org.matrix.androidsdk.RestClient;
import org.matrix.androidsdk.rest.api.LoginApi;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.RestAdapterCallback;
import org.matrix.androidsdk.rest.model.login.Credentials;
import org.matrix.androidsdk.rest.model.login.LoginFlow;
import org.matrix.androidsdk.rest.model.login.LoginFlowResponse;
import org.matrix.androidsdk.rest.model.login.PasswordLoginParams;
import org.matrix.androidsdk.rest.model.login.RegistrationParams;
import org.matrix.androidsdk.rest.model.login.TokenLoginParams;

import java.util.List;
import java.util.UUID;

import retrofit2.Response;

/**
 * Class used to make requests to the login API.
 */
public class LoginRestClient extends RestClient<LoginApi> {

    public static final String LOGIN_FLOW_TYPE_PASSWORD = "m.login.password";
    public static final String LOGIN_FLOW_TYPE_OAUTH2 = "m.login.oauth2";
    public static final String LOGIN_FLOW_TYPE_EMAIL_CODE = "m.login.email.code";
    public static final String LOGIN_FLOW_TYPE_EMAIL_URL = "m.login.email.url";
    public static final String LOGIN_FLOW_TYPE_EMAIL_IDENTITY = "m.login.email.identity";
    public static final String LOGIN_FLOW_TYPE_MSISDN = "m.login.msisdn";
    public static final String LOGIN_FLOW_TYPE_RECAPTCHA = "m.login.recaptcha";
    public static final String LOGIN_FLOW_TYPE_DUMMY = "m.login.dummy";

    /**
     * Public constructor.
     * @param hsConfig the home server connection config
     */
    public LoginRestClient(HomeserverConnectionConfig hsConfig) {
        super(hsConfig, LoginApi.class, RestClient.URI_API_PREFIX_PATH_R0, false);
    }

    /**
     * Retrieve the login supported flows.
     * It should be done to check before displaying a default login form.
     * @param callback the callback success and failure callback
     */
    public void getSupportedLoginFlows(final ApiCallback<List<LoginFlow>> callback) {
        final String description = "geLoginSupportedFlows";

        mApi.login().enqueue(new RestAdapterCallback<LoginFlowResponse>(description, mUnsentEventsManager, callback,
                new RestAdapterCallback.RequestRetryCallBack() {
                    @Override
                    public void onRetry() {
                        getSupportedLoginFlows(callback);
                    }
                }
        ) {
            @Override
            public void success(LoginFlowResponse loginFlowResponse, Response response) {
                onEventSent();
                callback.onSuccess(loginFlowResponse.flows);
            }
        });
    }

    /**
     * Request an account creation
     * @param params the registration parameters
     * @param callback the callbacks
     */
    public void register(final RegistrationParams params, final ApiCallback<Credentials> callback) {
        final String description = "register";

        // define a default device name only there is a password
        if (!TextUtils.isEmpty(params.password) && TextUtils.isEmpty(params.initial_device_display_name)) {
            params.initial_device_display_name = Build.MODEL.trim();

            // Temporary flag to notify the server that we support msisdn flow. Used to prevent old app
            // versions to end up in fallback because the HS returns the msisdn flow which they don't support
            // Only send it if we send any params at all (the password param is
            // mandatory, so if we send any params, we'll send the password param)
            params.x_show_msisdn = true;
        } else if (params.password == null && params.username == null && params.auth == null) {
            // Happens when we call the method to get flows, also add flag in that case
            params.x_show_msisdn = true;
        }

        mApi.register(params).enqueue(new RestAdapterCallback<JsonObject>(description, mUnsentEventsManager, callback,
                new RestAdapterCallback.RequestRetryCallBack() {
                    @Override
                    public void onRetry() {
                        register(params, callback);
                    }
                }
        ) {
            @Override
            public void success(JsonObject jsonObject, Response response) {
                onEventSent();
                mCredentials = gson.fromJson(jsonObject, Credentials.class);
                callback.onSuccess(mCredentials);
            }
        });
    }

    /**
     * Attempt to login with username/password
     *
     * @param user     the username
     * @param password the password
     * @param callback the callback success and failure callback
     */
    public void loginWithUser(final String user, final String password, final ApiCallback<Credentials> callback) {
        loginWithUser(user, password, callback, null);
    }

    public void loginWithUser(final String user, final String password, final ApiCallback<Credentials> callback, @Nullable final String deviceId) {
        final String description = "loginWithUser : " + user;

        PasswordLoginParams params = new PasswordLoginParams();
        params.setUserIdentifier(user, password);
        params.setDeviceId(deviceId);

        login(params, callback, description);
    }

    /**
     * Attempt to login with 3pid/password
     *
     * @param medium   the medium of the 3pid
     * @param address  the address of the 3pid
     * @param password the password
     * @param callback the callback success and failure callback
     */
    public void loginWith3Pid(final String medium, final String address, final String password, final ApiCallback<Credentials> callback) {
        loginWith3Pid(medium, address, password, callback, null);
    }

    public void loginWith3Pid(final String medium, final String address, final String password, final ApiCallback<Credentials> callback, @Nullable final String deviceId) {
        final String description = "loginWith3pid : " + address;

        PasswordLoginParams params = new PasswordLoginParams();
        params.setThirdPartyIdentifier(medium, address, password);
        params.setDeviceId(deviceId);

        login(params, callback, description);
    }

    /**
     * Attempt to login with phone number/password
     *
     * @param phoneNumber the phone number
     * @param countryCode the ISO country code
     * @param password    the password
     * @param callback    the callback success and failure callback
     */
    public void loginWithPhoneNumber(final String phoneNumber, final String countryCode, final String password, final ApiCallback<Credentials> callback) {
        loginWithPhoneNumber(phoneNumber, countryCode, password, callback, null);
    }

    public void loginWithPhoneNumber(final String phoneNumber, final String countryCode, final String password, final ApiCallback<Credentials> callback, @Nullable final String deviceId) {
        final String description = "loginWithPhoneNumber : " + phoneNumber;

        PasswordLoginParams params = new PasswordLoginParams();
        params.setPhoneIdentifier(phoneNumber, countryCode, password);
        params.setDeviceId(deviceId);

        login(params, callback, description);
    }

    /**
     * Make login request
     *
     * @param params login params
     * @param callback
     * @param description
     */
    private void login(final PasswordLoginParams params, final ApiCallback<Credentials> callback, final String description) {
        mApi.login(params).enqueue(new RestAdapterCallback<JsonObject>(description, mUnsentEventsManager, callback,

                new RestAdapterCallback.RequestRetryCallBack() {
                    @Override
                    public void onRetry() {
                        login(params, callback, description);
                    }
                }

        ) {
            @Override
            public void success(JsonObject jsonObject, Response response) {
                onEventSent();
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
        // privacy
        //final String description = "loginWithPassword user : " + user;
        final String description = "loginWithPassword user";

        TokenLoginParams params = new TokenLoginParams();
        params.user = user;
        params.token = token;
        params.txn_id = txn_id;
        params.initial_device_display_name = Build.MODEL.trim();

        mApi.login(params).enqueue(new RestAdapterCallback<JsonObject>(description, mUnsentEventsManager, callback,

                new RestAdapterCallback.RequestRetryCallBack() {
                    @Override
                    public void onRetry() {
                        loginWithToken(user, token, txn_id, callback);
                    }
                }

        ) {
            @Override
            public void success(JsonObject jsonObject, Response response) {
                onEventSent();
                mCredentials = gson.fromJson(jsonObject, Credentials.class);
                callback.onSuccess(mCredentials);
            }
        });
    }

    /**
     * Invalidate the access token, so that it can no longer be used for authorization.
     * @param callback the callback success and failure callback
     */
    public void logout(final ApiCallback<JsonObject> callback) {
        // privacy
        final String description = "logout user";

        mApi.logout().enqueue(new RestAdapterCallback<JsonObject>(description, mUnsentEventsManager, callback,
                new RestAdapterCallback.RequestRetryCallBack() {
                    @Override
                    public void onRetry() {
                        logout(callback);
                    }
                }
        ));
    }

}
