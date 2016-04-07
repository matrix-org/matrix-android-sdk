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
import org.matrix.androidsdk.rest.api.ProfileApi;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.RestAdapterCallback;
import org.matrix.androidsdk.rest.model.AddThreePidsParams;
import org.matrix.androidsdk.rest.model.AuthParams;
import org.matrix.androidsdk.rest.model.ChangePasswordParams;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.ThirdPartyIdentifier;
import org.matrix.androidsdk.rest.model.ThreePid;
import org.matrix.androidsdk.rest.model.ThreePidCreds;
import org.matrix.androidsdk.rest.model.ThreePidsResponse;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.rest.model.login.Credentials;
import org.matrix.androidsdk.rest.model.login.TokenRefreshParams;
import org.matrix.androidsdk.rest.model.login.TokenRefreshResponse;

import java.util.List;

import retrofit.Callback;
import retrofit.client.Response;
import retrofit.http.GET;

/**
 * Class used to make requests to the profile API.
 */
public class ProfileRestClient extends RestClient<ProfileApi> {

    /**
     * {@inheritDoc}
     */
    public ProfileRestClient(HomeserverConnectionConfig hsConfig) {
        super(hsConfig, ProfileApi.class, RestClient.URI_API_PREFIX_PATH_R0, false);
    }

    /**
     * Get the user's display name.
     * @param userId the user id
     * @param callback the callback to return the name on success
     */
    public void displayname(final String userId, final ApiCallback<String> callback) {
        final String description = "displayname userId : " + userId;

        mApi.displayname(userId, new RestAdapterCallback<User>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                displayname(userId, callback);
            }
        }) {
            @Override
            public void success(User user, Response response) {
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
        final String description = "updateDisplayname newName : " + newName;

        User user = new User();
        user.displayname = newName;

        // don't retry if the network comes back
        // let the user chooses what he want to do
        mApi.displayname(mCredentials.userId, user, new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
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

        mApi.avatarUrl(userId, new RestAdapterCallback<User>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                avatarUrl(userId, callback);
            }
        }) {
            @Override
            public void success(User user, Response response) {
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
        final String description = "updateAvatarUrl newUrl : " + newUrl;

        User user = new User();
        user.setAvatarUrl(newUrl);

        mApi.avatarUrl(mCredentials.userId, user, new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                updateAvatarUrl(newUrl, callback);
            }
        }));
    }

    /**
     * Get the user's display name.
     * @param userId the user id
     * @param oldPassword the former password
     * @param newPassword the new password
     * @param callback the callback
     */
    public void updatePassword(final String userId, final String oldPassword, final String newPassword, final ApiCallback<Void> callback) {
        final String description = "update password : " + userId + " oldPassword " + oldPassword + " newPassword " + newPassword;

        ChangePasswordParams passwordParams = new ChangePasswordParams();

        passwordParams.auth = new AuthParams();
        passwordParams.auth.type = "m.login.password";
        passwordParams.auth.user = userId;
        passwordParams.auth.password = oldPassword;
        passwordParams.new_password = newPassword;

        mApi.updatePassword(passwordParams, new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                try {
                    updatePassword(userId, oldPassword, newPassword, callback);
                } catch (Exception e) {
                }
            }
        }));
    }

    /**
     * Attempt a user/password registration.
     * @param callback the callback success and failure callback
     */
    public void refreshTokens(final ApiCallback<Credentials> callback) {
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

    /**
     * List all 3PIDs linked to the Matrix user account.
     * @param callback the asynchronous callback called with the response
     */
    public void threePIDs(final ApiCallback<List<ThirdPartyIdentifier>> callback) {
        final String description = "threePIDs";

        mApi.threePIDs(new RestAdapterCallback<ThreePidsResponse>(description, mUnsentEventsManager, callback, null) {
            @Override
            public void success(ThreePidsResponse threePidsResponse, Response response) {
                if (null != callback) {
                    callback.onSuccess(threePidsResponse.threepids);
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

        mApi.add3PID(params, new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback,
                new RestAdapterCallback.RequestRetryCallBack() {
                    @Override
                    public void onRetry() {
                        add3PID(pid, bind, callback);
                    }
                }
        ));
    }
}
