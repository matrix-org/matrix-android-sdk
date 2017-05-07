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

import org.matrix.androidsdk.rest.model.DeleteThreePidParams;
import org.matrix.androidsdk.util.Log;

import org.matrix.androidsdk.HomeserverConnectionConfig;
import org.matrix.androidsdk.RestClient;
import org.matrix.androidsdk.rest.api.ProfileApi;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.RestAdapterCallback;
import org.matrix.androidsdk.rest.model.AddThreePidsParams;
import org.matrix.androidsdk.rest.model.AuthParams;
import org.matrix.androidsdk.rest.model.ChangePasswordParams;
import org.matrix.androidsdk.rest.model.ForgetPasswordParams;
import org.matrix.androidsdk.rest.model.ForgetPasswordResponse;
import org.matrix.androidsdk.rest.model.ThirdPartyIdentifier;
import org.matrix.androidsdk.rest.model.ThreePid;
import org.matrix.androidsdk.rest.model.ThreePidCreds;
import org.matrix.androidsdk.rest.model.AccountThreePidsResponse;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.rest.model.login.Credentials;
import org.matrix.androidsdk.rest.model.login.TokenRefreshParams;
import org.matrix.androidsdk.rest.model.login.TokenRefreshResponse;

import java.util.List;
import java.util.Map;

import retrofit2.Response;

/**
 * Class used to make requests to the profile API.
 */
public class ProfileRestClient extends RestClient<ProfileApi> {
    private static final String LOG_TAG = "ProfileRestClient";

    /**
     * {@inheritDoc}
     */
    public ProfileRestClient(HomeserverConnectionConfig hsConfig) {
        super(hsConfig, ProfileApi.class, "", false);
    }

    /**
     * Get the user's display name.
     * @param userId the user id
     * @param callback the callback to return the name on success
     */
    public void displayname(final String userId, final ApiCallback<String> callback) {
        final String description = "display name userId : " + userId;

        mApi.displayname(userId).enqueue(new RestAdapterCallback<User>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                displayname(userId, callback);
            }
        }) {
            @Override
            public void success(User user, Response<User> response) {
                onEventSent();
                callback.onSuccess(user.displayname);
            }
        });
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

        // don't retry if the network comes back
        // let the user chooses what he want to do
        mApi.displayname(mCredentials.userId, user).enqueue(new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                updateDisplayname(newName, callback);
            }
        }));
    }

    /**
     * Get the user's avatar URL.
     * @param userId the user id
     * @param callback the callback to return the URL on success
     */
    public void avatarUrl(final String userId, final ApiCallback<String> callback) {
        final String description = "avatarUrl userId : " + userId;

        mApi.avatarUrl(userId).enqueue(new RestAdapterCallback<User>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
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

        mApi.avatarUrl(mCredentials.userId, user).enqueue(new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                updateAvatarUrl(newUrl, callback);
            }
        }));
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

        mApi.updatePassword(passwordParams).enqueue(new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                try {
                    updatePassword(userId, oldPassword, newPassword, callback);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "## updatePassword() failed" + e.getMessage());
                }
            }
        }));
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

        mApi.updatePassword(passwordParams).enqueue(new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                try {
                    resetPassword(newPassword, threepid_creds, callback);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "## resetPassword() failed" + e.getMessage());
                }
            }
        }));
    }

    /**
     * Reset the password server side.
     * @param email the email to send the password reset.
     * @param callback the callback
     */
    public void forgetPassword(final String email, final ApiCallback<ThreePid> callback) {
        final String description = "forget password";

        if (!TextUtils.isEmpty(email)) {
            final ThreePid pid = new ThreePid(email, ThreePid.MEDIUM_EMAIL);

            final ForgetPasswordParams forgetPasswordParams = new ForgetPasswordParams();
            forgetPasswordParams.email = email;
            forgetPasswordParams.client_secret = pid.clientSecret;
            forgetPasswordParams.send_attempt = 1;
            forgetPasswordParams.id_server = mHsConfig.getIdentityServerUri().getHost();

            mApi.forgetPassword(forgetPasswordParams).enqueue(new RestAdapterCallback<ForgetPasswordResponse>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                @Override
                public void onRetry() {
                    try {
                        forgetPassword(email, callback);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "## forgetPassword() failed" + e.getMessage());
                    }
                }
            }) {
                @Override
                public void success(ForgetPasswordResponse forgetPasswordResponse, Response response) {
                    onEventSent();

                    pid.sid = forgetPasswordResponse.sid;
                    callback.onSuccess(pid);
                }
            });
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

        mApi.tokenrefresh(params).enqueue(new RestAdapterCallback<TokenRefreshResponse>(description, mUnsentEventsManager, callback, null) {
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
    }

    /**
     * List all 3PIDs linked to the Matrix user account.
     * @param callback the asynchronous callback called with the response
     */
    public void threePIDs(final ApiCallback<List<ThirdPartyIdentifier>> callback) {
        final String description = "threePIDs";

        mApi.threePIDs().enqueue(new RestAdapterCallback<AccountThreePidsResponse>(description, mUnsentEventsManager, callback, null) {
            @Override
            public void success(AccountThreePidsResponse threePidsResponse, Response response) {
                onEventSent();
                if (null != callback) {
                    callback.onSuccess(threePidsResponse.threepids);
                }
            }
        });
    }


    /**
     * Add an 3Pids to an user
     * @param pid the 3Pid to add
     * @param bind bind the email
     * @param callback the asynchronous callback called with the response
     */
    public void add3PID(final ThreePid pid, final boolean bind, final ApiCallback<Void>callback) {
        final String description = "add3PID";

        AddThreePidsParams params = new AddThreePidsParams();

        params.three_pid_creds = new ThreePidCreds();

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

        mApi.add3PID(params).enqueue(new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback,
                new RestAdapterCallback.RequestRetryCallBack() {
                    @Override
                    public void onRetry() {
                        add3PID(pid, bind, callback);
                    }
                }
        ));
    }

    /**
     * Delete a 3pid of the user
     *
     * @param pid      the 3Pid to delete
     * @param callback the asynchronous callback called with the response
     */
    public void delete3PID(final ThirdPartyIdentifier pid, final ApiCallback<Void> callback) {
        final String description = "delete3PID";

        final DeleteThreePidParams params = new DeleteThreePidParams();
        params.medium = pid.medium;
        params.address = pid.address;

        mApi.delete3PID(params).enqueue(new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback,
                new RestAdapterCallback.RequestRetryCallBack() {
                    @Override
                    public void onRetry() {
                        delete3PID(pid, callback);
                    }
                })
        );
    }
}
