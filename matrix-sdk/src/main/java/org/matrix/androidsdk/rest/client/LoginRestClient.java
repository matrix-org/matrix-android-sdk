/*
 * Copyright 2014 OpenMarket Ltd
 * Copyright 2017 Vector Creations Ltd
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

import android.os.Build;
import androidx.annotation.Nullable;
import android.text.TextUtils;

import org.matrix.androidsdk.HomeServerConnectionConfig;
import org.matrix.androidsdk.RestClient;
import org.matrix.androidsdk.core.VersionsUtilKt;
import org.matrix.androidsdk.core.JsonUtils;
import org.matrix.androidsdk.core.callback.ApiCallback;
import org.matrix.androidsdk.core.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.api.LoginApi;
import org.matrix.androidsdk.rest.callback.RestAdapterCallback;
import org.matrix.androidsdk.rest.model.Versions;
import org.matrix.androidsdk.rest.model.login.Credentials;
import org.matrix.androidsdk.rest.model.login.LoginFlow;
import org.matrix.androidsdk.rest.model.login.LoginFlowResponse;
import org.matrix.androidsdk.rest.model.login.LoginParams;
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
    public static final String LOGIN_FLOW_TYPE_TERMS = "m.login.terms";
    public static final String LOGIN_FLOW_TYPE_DUMMY = "m.login.dummy";
    public static final String LOGIN_FLOW_TYPE_SSO = "m.login.sso";
    public static final String LOGIN_FLOW_TYPE_CAS = "m.login.cas";

    /**
     * Public constructor.
     *
     * @param hsConfig the home server connection config
     */
    public LoginRestClient(HomeServerConnectionConfig hsConfig) {
        super(hsConfig, LoginApi.class, "", JsonUtils.getGson(false));
    }

    /**
     * Get Versions supported by the server and other server capabilities
     *
     * @param callback the callback
     */
    public void getVersions(final ApiCallback<Versions> callback) {
        final String description = "getVersions";

        mApi.versions()
                .enqueue(new RestAdapterCallback<Versions>(description, mUnsentEventsManager, callback, null));
    }

    /**
     * Ask if the `id_access_token` parameter can be safely passed to the homeserver.
     * Some homeservers may trigger errors if they are not prepared for the new parameter.
     *
     * @param callback the callback.
     */
    public void doesServerAcceptIdentityAccessToken(final ApiCallback<Boolean> callback) {
        getVersions(new SimpleApiCallback<Versions>(callback) {
            @Override
            public void onSuccess(Versions info) {
                callback.onSuccess(VersionsUtilKt.doesServerAcceptIdentityAccessToken(info));
            }
        });
    }

    /**
     * Ask the home server require identity server param
     *
     * @param callback the callback, true if the `id_server` parameter is required when registering with an 3pid,
     *                 adding a 3pid or resetting password.
     */
    public void doesServerRequireIdentityServerParam(final ApiCallback<Boolean> callback) {
        getVersions(new SimpleApiCallback<Versions>(callback) {
            @Override
            public void onSuccess(Versions info) {
                callback.onSuccess(VersionsUtilKt.doesServerRequireIdentityServerParam(info));
            }
        });
    }

    public void doesServerSeparatesAddAndBind(final ApiCallback<Boolean> callback) {
        getVersions(new SimpleApiCallback<Versions>(callback) {
            @Override
            public void onSuccess(Versions info) {
                callback.onSuccess(VersionsUtilKt.doesServerSeparatesAddAndBind(info));
            }
        });
    }


    /**
     * Retrieve the login supported flows.
     * It should be done to check before displaying a default login form.
     *
     * @param callback the callback success and failure callback
     */
    public void getSupportedLoginFlows(final ApiCallback<List<LoginFlow>> callback) {
        final String description = "geLoginSupportedFlows";

        mApi.login()
                .enqueue(new RestAdapterCallback<LoginFlowResponse>(description, mUnsentEventsManager, callback,
                        new RestAdapterCallback.RequestRetryCallBack() {
                            @Override
                            public void onRetry() {
                                getSupportedLoginFlows(callback);
                            }
                        }) {
                    @Override
                    public void success(LoginFlowResponse loginFlowResponse, Response response) {
                        onEventSent();
                        callback.onSuccess(loginFlowResponse.flows);
                    }
                });
    }

    /**
     * Request an account creation
     *
     * @param params   the registration parameters
     * @param callback the callback
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

        mApi.register(params)
                .enqueue(new RestAdapterCallback<Credentials>(description, mUnsentEventsManager, new SimpleApiCallback<Credentials>(callback) {
                    @Override
                    public void onSuccess(Credentials info) {
                        setAccessToken(info.accessToken);
                        callback.onSuccess(info);
                    }
                }, new RestAdapterCallback.RequestRetryCallBack() {
                    @Override
                    public void onRetry() {
                        register(params, callback);
                    }
                }));
    }

    /**
     * Attempt to login with username/password
     *
     * @param user     the username
     * @param password the password
     * @param callback the callback success and failure callback
     */
    public void loginWithUser(final String user, final String password, final ApiCallback<Credentials> callback) {
        loginWithUser(user, password, null, null, callback);
    }

    /**
     * Attempt to login with username/password
     *
     * @param user       the username
     * @param password   the password
     * @param deviceName the device name
     * @param deviceId   the device id, used for e2e encryption
     * @param callback   the callback success and failure callback
     */
    public void loginWithUser(final String user,
                              final String password,
                              final String deviceName,
                              @Nullable final String deviceId,
                              final ApiCallback<Credentials> callback) {
        final String description = "loginWithUser : " + user;

        PasswordLoginParams params = new PasswordLoginParams();
        params.setUserIdentifier(user, password);
        params.setDeviceName(deviceName);
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
        loginWith3Pid(medium, address, password, null, null, callback);
    }

    /**
     * Attempt to login with 3pid/password
     *
     * @param medium     the medium of the 3pid
     * @param address    the address of the 3pid
     * @param password   the password
     * @param deviceName the device name
     * @param deviceId   the device id, used for e2e encryption
     * @param callback   the callback success and failure callback
     */
    public void loginWith3Pid(final String medium,
                              final String address,
                              final String password,
                              final String deviceName,
                              @Nullable final String deviceId,
                              final ApiCallback<Credentials> callback) {
        final String description = "loginWith3pid : " + address;

        PasswordLoginParams params = new PasswordLoginParams();
        params.setThirdPartyIdentifier(medium, address, password);
        params.setDeviceName(deviceName);
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
        loginWithPhoneNumber(phoneNumber, countryCode, password, null, null, callback);
    }

    /**
     * Attempt to login with phone number/password
     *
     * @param phoneNumber the phone number
     * @param countryCode the ISO country code
     * @param password    the password
     * @param deviceName  the device name
     * @param deviceId    the device id, used for e2e encryption
     * @param callback    the callback success and failure callback
     */
    public void loginWithPhoneNumber(final String phoneNumber,
                                     final String countryCode,
                                     final String password,
                                     final String deviceName,
                                     @Nullable final String deviceId,
                                     final ApiCallback<Credentials> callback) {
        final String description = "loginWithPhoneNumber : " + phoneNumber;

        PasswordLoginParams params = new PasswordLoginParams();
        params.setPhoneIdentifier(phoneNumber, countryCode, password);
        params.setDeviceName(deviceName);
        params.setDeviceId(deviceId);

        login(params, callback, description);
    }

    /**
     * Make a login request.
     *
     * @param params   custom login params
     * @param callback the asynchronous callback
     */
    public void login(LoginParams params, final ApiCallback<Credentials> callback) {
        login(params, callback, "login with a " + params.getClass().getSimpleName() + " object");
    }

    /**
     * Make login request
     *
     * @param params      login params
     * @param callback    the asynchronous callback
     * @param description the request description
     */
    private void login(final LoginParams params, final ApiCallback<Credentials> callback, final String description) {
        mApi.login(params)
                .enqueue(new RestAdapterCallback<Credentials>(description, mUnsentEventsManager, new SimpleApiCallback<Credentials>(callback) {
                    @Override
                    public void onSuccess(Credentials info) {
                        setAccessToken(info.accessToken);
                        callback.onSuccess(info);
                    }
                }, new RestAdapterCallback.RequestRetryCallBack() {
                    @Override
                    public void onRetry() {
                        login(params, callback, description);
                    }
                }));
    }

    /**
     * Attempt a user/token log in.
     *
     * @param user       the user name
     * @param token      the token
     * @param deviceName the device name
     * @param callback   the callback success and failure callback
     */
    public void loginWithToken(final String user, final String token, final String deviceName, final ApiCallback<Credentials> callback) {
        loginWithToken(user, token, UUID.randomUUID().toString(), deviceName, callback);
    }

    /**
     * Attempt a user/token log in.
     *
     * @param user       the user name
     * @param token      the token
     * @param txn_id     the client transaction id to include in the request
     * @param deviceName the device name
     * @param callback   the callback success and failure callback
     */
    public void loginWithToken(final String user, final String token, final String txn_id, String deviceName, final ApiCallback<Credentials> callback) {
        // privacy
        //final String description = "loginWithPassword user : " + user;
        final String description = "loginWithPassword user";

        TokenLoginParams params = new TokenLoginParams();
        params.user = user;
        params.token = token;
        params.txn_id = txn_id;

        if ((null != deviceName) && !TextUtils.isEmpty(deviceName.trim())) {
            params.initial_device_display_name = deviceName.trim();
        } else {
            params.initial_device_display_name = Build.MODEL.trim();
        }

        mApi.login(params)
                .enqueue(new RestAdapterCallback<Credentials>(description, mUnsentEventsManager, new SimpleApiCallback<Credentials>(callback) {
                    @Override
                    public void onSuccess(Credentials info) {
                        setAccessToken(info.accessToken);
                        callback.onSuccess(info);
                    }
                }, new RestAdapterCallback.RequestRetryCallBack() {
                    @Override
                    public void onRetry() {
                        loginWithToken(user, token, txn_id, callback);
                    }
                }));
    }

    /**
     * Invalidate the access token, so that it can no longer be used for authorization.
     *
     * @param callback the callback success and failure callback
     */
    public void logout(final ApiCallback<Void> callback) {
        // privacy
        final String description = "logout user";

        mApi.logout()
                .enqueue(new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                    @Override
                    public void onRetry() {
                        logout(callback);
                    }
                }));
    }
}
