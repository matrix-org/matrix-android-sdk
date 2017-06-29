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
package org.matrix.androidsdk.rest.api;

import org.matrix.androidsdk.RestClient;
import org.matrix.androidsdk.rest.model.AccountThreePidsResponse;
import org.matrix.androidsdk.rest.model.AddThreePidsParams;
import org.matrix.androidsdk.rest.model.ChangePasswordParams;
import org.matrix.androidsdk.rest.model.DeleteThreePidParams;
import org.matrix.androidsdk.rest.model.ForgetPasswordParams;
import org.matrix.androidsdk.rest.model.ForgetPasswordResponse;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.rest.model.login.TokenRefreshParams;
import org.matrix.androidsdk.rest.model.login.TokenRefreshResponse;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;


/**
 * The profile REST API.
 */
public interface ProfileApi {

    /**
     * Update a user's display name.
     *
     * @param userId   the user id
     * @param user     the user object containing the new display name
     */
    @PUT(RestClient.URI_API_PREFIX_PATH_R0 + "profile/{userId}/displayname")
    Call<Void> displayname(@Path("userId") String userId, @Body User user);

    /**
     * Get a user's display name.
     *
     * @param userId   the user id
     */
    @GET(RestClient.URI_API_PREFIX_PATH_R0 + "profile/{userId}/displayname")
    Call<User> displayname(@Path("userId") String userId);

    /**
     * Update a user's avatar URL.
     *
     * @param userId   the user id
     * @param user     the user object containing the new avatar url
     */
    @PUT(RestClient.URI_API_PREFIX_PATH_R0 + "profile/{userId}/avatar_url")
    Call<Void> avatarUrl(@Path("userId") String userId, @Body User user);

    /**
     * Get a user's avatar URL.
     *
     * @param userId   the user id
     */
    @GET(RestClient.URI_API_PREFIX_PATH_R0 + "profile/{userId}/avatar_url")
    Call<User> avatarUrl(@Path("userId") String userId);

    /**
     * Update the password
     *
     * @param passwordParams the new password
     */
    @POST(RestClient.URI_API_PREFIX_PATH_R0 + "account/password")
    Call<Void> updatePassword(@Body ChangePasswordParams passwordParams);

    /**
     * Reset the password server side.
     *
     * @param params   the forget password params
     */
    @POST(RestClient.URI_API_PREFIX_PATH_R0 + "account/password/email/requestToken")
    Call<ForgetPasswordResponse> forgetPassword(@Body ForgetPasswordParams params);

    /**
     * Pass params to the server for the token refresh phase.
     *
     * @param refreshParams the refresh token parameters
     */
    @POST(RestClient.URI_API_PREFIX_PATH_R0 + "tokenrefresh")
    Call<TokenRefreshResponse> tokenrefresh(@Body TokenRefreshParams refreshParams);

    /**
     * List all 3PIDs linked to the Matrix user account.
     */
    @GET(RestClient.URI_API_PREFIX_PATH_R0 + "account/3pid")
    Call<AccountThreePidsResponse> threePIDs();

    /**
     * Add an 3Pid to a user
     *
     * @param params   the params
     */
    @POST(RestClient.URI_API_PREFIX_PATH_R0 + "account/3pid")
    Call<Void> add3PID(@Body AddThreePidsParams params);

    /**
     * Delete a 3Pid of a user
     *
     * @param params   the params
     */
    @POST(RestClient.URI_API_PREFIX_PATH_UNSTABLE + "account/3pid/delete")
    Call<Void> delete3PID(@Body DeleteThreePidParams params);
}
