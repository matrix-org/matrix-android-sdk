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

import android.text.TextUtils;

import org.matrix.androidsdk.HomeServerConnectionConfig;
import org.matrix.androidsdk.RestClient;
import org.matrix.androidsdk.rest.api.ProfileApi;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.RestAdapterCallback;
import org.matrix.androidsdk.rest.model.pid.AccountThreePidsResponse;
import org.matrix.androidsdk.rest.model.pid.AddThreePidsParams;
import org.matrix.androidsdk.rest.model.AuthParams;
import org.matrix.androidsdk.rest.model.ChangePasswordParams;
import org.matrix.androidsdk.rest.model.pid.DeleteDeviceParams;
import org.matrix.androidsdk.rest.model.pid.DeleteThreePidParams;
import org.matrix.androidsdk.rest.model.ForgetPasswordParams;
import org.matrix.androidsdk.rest.model.ForgetPasswordResponse;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.rest.model.login.Credentials;
import org.matrix.androidsdk.rest.model.login.TokenRefreshParams;
import org.matrix.androidsdk.rest.model.login.TokenRefreshResponse;
import org.matrix.androidsdk.util.Log;

import java.util.List;
import java.util.Map;

import retrofit.client.Response;

/**
 * Class used to make requests to the profile API.
 */
public class ProfileRestClient extends RestClient<ProfileApi> {
    private static final String LOG_TAG = ProfileRestClient.class.getSimpleName();

    /**
     * {@inheritDoc}
     */
    public ProfileRestClient(HomeServerConnectionConfig hsConfig) {
        super(hsConfig, ProfileApi.class, "", false);
    }

    /**
     * Get the user's display name.
     * @param userId the user id
     * @param callback the callback to return the name on success
     */
    public void displayname(final String userId, final ApiCallback<String> callback) {
        final String description = "display name userId : " + userId;

        try {
            mApi.displayname(userId, new RestAdapterCallback<User>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                @Override
                public void onRetry() {
                    displayname(userId, callback);
                }
            }) {
                @Override
                public void success(User user, Response response) {
                    onEventSent();
                    callback.onSuccess(user.displayname);
                }
            });
        } catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Update this user's own display name.
     * @param newName the new name
     * @param callback the callback if the call succeeds
     */
    public void updateDisplayname(final String newName, final ApiCallback<Void> callback) {
        // privacy
        //final String description = "updateDisplayname newName : " + newName;
        final String description = "update display name";

        User user = new User();
        user.displayname = newName;

        try {
            // don't retry if the network comes back
            // let the user chooses what he want to do
            mApi.displayname(mCredentials.userId, user, new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                @Override
                public void onRetry() {
                    updateDisplayname(newName, callback);
                }
            }));
        } catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Get the user's avatar URL.
     * @param userId the user id
     * @param callback the callback to return the URL on success
     */
    public void avatarUrl(final String userId, final ApiCallback<String> callback) {
        final String description = "avatarUrl userId : " + userId;

        try {
            mApi.avatarUrl(userId, new RestAdapterCallback<User>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                @Override
                public void onRetry() {
                    avatarUrl(userId, callback);
                }
            }) {
                @Override
                public void success(User user, Response response) {
                    onEventSent();
                    callback.onSuccess(user.getAvatarUrl());
                }
            });
        } catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Update this user's own avatar URL.
     * @param newUrl the new name
     * @param callback the callback if the call succeeds
     */
    public void updateAvatarUrl(final String newUrl, final ApiCallback<Void> callback) {
        // privacy
        //final String description = "updateAvatarUrl newUrl : " + newUrl;
        final String description = "updateAvatarUrl";

        User user = new User();
        user.setAvatarUrl(newUrl);

        try {
            mApi.avatarUrl(mCredentials.userId, user, new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                @Override
                public void onRetry() {
                    updateAvatarUrl(newUrl, callback);
                }
            }));
        } catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Update the password
     * @param userId the user id
     * @param oldPassword the former password
     * @param newPassword the new password
     * @param callback the callback
     */
    public void updatePassword(final String userId, final String oldPassword, final String newPassword, final ApiCallback<Void> callback) {
        // privacy
        //final String description = "update password : " + userId + " oldPassword " + oldPassword + " newPassword " + newPassword;
        final String description = "update password";

        ChangePasswordParams passwordParams = new ChangePasswordParams();

        passwordParams.auth = new AuthParams();
        passwordParams.auth.type = "m.login.password";
        passwordParams.auth.user = userId;
        passwordParams.auth.password = oldPassword;
        passwordParams.new_password = newPassword;

        try {
            mApi.updatePassword(passwordParams, new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                @Override
                public void onRetry() {
                    try {
                        updatePassword(userId, oldPassword, newPassword, callback);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "## updatePassword() failed" + e.getMessage());
                    }
                }
            }));
        } catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Reset the password to a new one.
     * @param newPassword the new password
     * @param threepid_creds the three pids.
     * @param callback the callback
     */
    public void resetPassword(final String newPassword, final Map<String, String> threepid_creds, final ApiCallback<Void> callback) {
        // privacy
        //final String description = "Reset password : " + threepid_creds + " newPassword " + newPassword;
        final String description = "Reset password";

        ChangePasswordParams passwordParams = new ChangePasswordParams();

        passwordParams.auth = new AuthParams();
        passwordParams.auth.type = "m.login.email.identity";
        passwordParams.auth.threepid_creds = threepid_creds;
        passwordParams.new_password = newPassword;

        try {
            mApi.updatePassword(passwordParams, new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                resetPassword(newPassword, threepid_creds, callback);
            }
        }));
        } catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Reset the password server side.
     * @param email the email to send the password reset.
     * @param callback the callback
     */
    public void forgetPassword(final String email, final ApiCallback<DeleteDeviceParams.ThreePid> callback) {
        final String description = "forget password";

        if (!TextUtils.isEmpty(email)) {
            final DeleteDeviceParams.ThreePid pid = new DeleteDeviceParams.ThreePid(email, DeleteDeviceParams.ThreePid.MEDIUM_EMAIL);

            final ForgetPasswordParams forgetPasswordParams = new ForgetPasswordParams();
            forgetPasswordParams.email = email;
            forgetPasswordParams.client_secret = pid.clientSecret;
            forgetPasswordParams.send_attempt = 1;
            forgetPasswordParams.id_server = mHsConfig.getIdentityServerUri().getHost();

            try {
                mApi.forgetPassword(forgetPasswordParams, new RestAdapterCallback<ForgetPasswordResponse>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                    @Override
                    public void onRetry() {
                        forgetPassword(email, callback);
                    }
                }) {
                    @Override
                    public void success(ForgetPasswordResponse forgetPasswordResponse, Response response) {
                        onEventSent();

                        pid.sid = forgetPasswordResponse.sid;
                        callback.onSuccess(pid);
                    }
                });
            } catch (Throwable t) {
                callback.onUnexpectedError(new Exception(t));
            }
        }
    }

    /**
     * Attempt a user/password registration.
     * @param callback the callback success and failure callback
     */
    public void refreshTokens(final ApiCallback<Credentials> callback) {
        final String description = "refreshTokens";

        TokenRefreshParams params = new TokenRefreshParams();
        params.refresh_token = mCredentials.refreshToken;

        try {
            mApi.tokenrefresh(params, new RestAdapterCallback<TokenRefreshResponse>(description, mUnsentEventsManager, callback, null) {
                @Override
                public void success(TokenRefreshResponse tokenreponse, Response response) {
                    onEventSent();
                    mCredentials.refreshToken = tokenreponse.refresh_token;
                    mCredentials.accessToken = tokenreponse.access_token;
                    if (null != callback) {
                        callback.onSuccess(mCredentials);
                    }
                }
            });
        } catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * List all 3PIDs linked to the Matrix user account.
     * @param callback the asynchronous callback called with the response
     */
    public void threePIDs(final ApiCallback<List<DeleteDeviceParams.ThirdPartyIdentifier>> callback) {
        final String description = "threePIDs";

        try {
            mApi.threePIDs(new RestAdapterCallback<AccountThreePidsResponse>(description, mUnsentEventsManager, callback, null) {
                @Override
                public void success(AccountThreePidsResponse threePidsResponse, Response response) {
                    onEventSent();
                    if (null != callback) {
                        callback.onSuccess(threePidsResponse.threepids);
                    }
                }
            });
        } catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Request an email validation token.
     *
     * @param address              the email address
     * @param clientSecret         the client secret number
     * @param attempt              the attempt count
     * @param nextLink             the next link
     * @param isDuringRegistration true if it occurs during a registration flow
     * @param callback             the callback
     */
    public void requestEmailValidationToken(final String address, final String clientSecret, final int attempt,
                                            final String nextLink, final boolean isDuringRegistration,
                                            final ApiCallback<DeleteDeviceParams.RequestEmailValidationResponse> callback) {
        final String description = "requestEmailValidationToken";

        DeleteDeviceParams.RequestEmailValidationParams params = new DeleteDeviceParams.RequestEmailValidationParams();
        params.email = address;
        params.clientSecret = clientSecret;
        params.sendAttempt = attempt;
        params.id_server = mHsConfig.getIdentityServerUri().getHost();
        if (!TextUtils.isEmpty(nextLink)) {
            params.next_link = nextLink;
        }

        final RestAdapterCallback<DeleteDeviceParams.RequestEmailValidationResponse> adapterCallback = new RestAdapterCallback<DeleteDeviceParams.RequestEmailValidationResponse>(description, mUnsentEventsManager, callback,
                new RestAdapterCallback.RequestRetryCallBack() {
                    @Override
                    public void onRetry() {
                        requestEmailValidationToken(address, clientSecret, attempt, nextLink, isDuringRegistration, callback);
                    }
                }
        ) {
            @Override
            public void success(DeleteDeviceParams.RequestEmailValidationResponse requestEmailValidationResponse, Response response) {
                onEventSent();
                requestEmailValidationResponse.email = address;
                requestEmailValidationResponse.clientSecret = clientSecret;
                requestEmailValidationResponse.sendAttempt = attempt;

                callback.onSuccess(requestEmailValidationResponse);
            }
        };

        try {
            if (isDuringRegistration) {
                // URL differs in that case
                mApi.requestEmailValidationForRegistration(params, adapterCallback);
            } else {
                mApi.requestEmailValidation(params, adapterCallback);
            }
        } catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Request a phone number validation token.
     *
     * @param phoneNumber          the phone number
     * @param countryCode          the country code of the phone number
     * @param clientSecret         the client secret number
     * @param attempt              the attempt count
     * @param isDuringRegistration true if it occurs during a registration flow
     * @param callback             the callback
     */
    public void requestPhoneNumberValidationToken(final String phoneNumber, final String countryCode,
                                                  final String clientSecret, final int attempt,
                                                  final boolean isDuringRegistration, final ApiCallback<DeleteDeviceParams.RequestPhoneNumberValidationResponse> callback) {
        final String description = "requestPhoneNumberValidationToken";

        DeleteDeviceParams.RequestPhoneNumberValidationParams params = new DeleteDeviceParams.RequestPhoneNumberValidationParams();
        params.phone_number = phoneNumber;
        params.country = countryCode;
        params.clientSecret = clientSecret;
        params.sendAttempt = attempt;
        params.id_server = mHsConfig.getIdentityServerUri().getHost();

        final RestAdapterCallback<DeleteDeviceParams.RequestPhoneNumberValidationResponse> adapterCallback = new RestAdapterCallback<DeleteDeviceParams.RequestPhoneNumberValidationResponse>(description, mUnsentEventsManager, callback,
                new RestAdapterCallback.RequestRetryCallBack() {
                    @Override
                    public void onRetry() {
                        requestPhoneNumberValidationToken(phoneNumber, countryCode, clientSecret, attempt, isDuringRegistration, callback);
                    }
                }
        ) {
            @Override
            public void success(DeleteDeviceParams.RequestPhoneNumberValidationResponse requestPhoneNumberValidationResponse, Response response) {
                onEventSent();
                requestPhoneNumberValidationResponse.clientSecret = clientSecret;
                requestPhoneNumberValidationResponse.sendAttempt = attempt;

                callback.onSuccess(requestPhoneNumberValidationResponse);
            }
        };

        try {
            if (isDuringRegistration) {
                // URL differs in that case
                mApi.requestPhoneNumberValidationForRegistration(params, adapterCallback);
            } else {
                mApi.requestPhoneNumberValidation(params, adapterCallback);
            }
        } catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Add an 3Pids to an user
     * @param pid the 3Pid to add
     * @param bind bind the email
     * @param callback the asynchronous callback called with the response
     */
    public void add3PID(final DeleteDeviceParams.ThreePid pid, final boolean bind, final ApiCallback<Void>callback) {
        final String description = "add3PID";

        AddThreePidsParams params = new AddThreePidsParams();

        params.three_pid_creds = new DeleteDeviceParams.ThreePidCreds();

        String identityServerHost = mHsConfig.getIdentityServerUri().toString();
        if (identityServerHost.startsWith("http://")) {
            identityServerHost = identityServerHost.substring("http://".length());
        } else  if (identityServerHost.startsWith("https://")) {
            identityServerHost = identityServerHost.substring("https://".length());
        }

        params.three_pid_creds.id_server = identityServerHost;
        params.three_pid_creds.sid = pid.sid;
        params.three_pid_creds.client_secret = pid.clientSecret;

        params.bind = bind;

        try {
            mApi.add3PID(params, new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback,
                    new RestAdapterCallback.RequestRetryCallBack() {
                        @Override
                        public void onRetry() {
                            add3PID(pid, bind, callback);
                        }
                    }
            ));
        } catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Delete a 3pid of the user
     *
     * @param pid      the 3Pid to delete
     * @param callback the asynchronous callback called with the response
     */
    public void delete3PID(final DeleteDeviceParams.ThirdPartyIdentifier pid, final ApiCallback<Void> callback) {
        final String description = "delete3PID";

        final DeleteThreePidParams params = new DeleteThreePidParams();
        params.medium = pid.medium;
        params.address = pid.address;

        try {
            mApi.delete3PID(params, new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback,
                    new RestAdapterCallback.RequestRetryCallBack() {
                        @Override
                        public void onRetry() {
                            delete3PID(pid, callback);
                        }
                    })
            );
        } catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }
}
