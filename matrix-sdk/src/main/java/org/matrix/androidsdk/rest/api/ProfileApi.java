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
import org.matrix.androidsdk.rest.model.RequestEmailValidationParams;
import org.matrix.androidsdk.rest.model.RequestEmailValidationResponse;
import org.matrix.androidsdk.rest.model.RequestPhoneNumberValidationParams;
import org.matrix.androidsdk.rest.model.RequestPhoneNumberValidationResponse;
import org.matrix.androidsdk.rest.model.pid.AccountThreePidsResponse;
import org.matrix.androidsdk.rest.model.pid.AddThreePidsParams;
import org.matrix.androidsdk.rest.model.ChangePasswordParams;
import org.matrix.androidsdk.rest.model.pid.DeleteThreePidParams;
import org.matrix.androidsdk.rest.model.ForgetPasswordParams;
import org.matrix.androidsdk.rest.model.ForgetPasswordResponse;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.rest.model.login.TokenRefreshParams;
import org.matrix.androidsdk.rest.model.login.TokenRefreshResponse;

import retrofit.Callback;
import retrofit.http.Body;
import retrofit.http.GET;
import retrofit.http.POST;
import retrofit.http.PUT;
import retrofit.http.Path;

/**
 * The profile REST API.
 */
public interface ProfileApi {

    /**
     * Update a user's display name.
     *
     * @param userId   the user id
     * @param user     the user object containing the new display name
     * @param callback the asynchronous callback to call when finished
     */
    @PUT(RestClient.URI_API_PREFIX_PATH_R0 + "/profile/{userId}/displayname")
    void displayname(@Path("userId") String userId, @Body User user, Callback<Void> callback);

    /**
     * Get a user's display name.
     *
     * @param userId   the user id
     * @param callback the asynchronous callback called with the response
     */
    @GET(RestClient.URI_API_PREFIX_PATH_R0 + "/profile/{userId}/displayname")
    void displayname(@Path("userId") String userId, Callback<User> callback);

    /**
     * Update a user's avatar URL.
     *
     * @param userId   the user id
     * @param user     the user object containing the new avatar url
     * @param callback the asynchronous callback to call when finished
     */
    @PUT(RestClient.URI_API_PREFIX_PATH_R0 + "/profile/{userId}/avatar_url")
    void avatarUrl(@Path("userId") String userId, @Body User user, Callback<Void> callback);

    /**
     * Get a user's avatar URL.
     *
     * @param userId   the user id
     * @param callback the asynchronous callback called with the response
     */
    @GET(RestClient.URI_API_PREFIX_PATH_R0 + "/profile/{userId}/avatar_url")
    void avatarUrl(@Path("userId") String userId, Callback<User> callback);

    /**
     * Update the password
     *
     * @param passwordParams the new password
     * @param callback       the asynchronous callback to call when finished
     */
    @POST(RestClient.URI_API_PREFIX_PATH_R0 + "/account/password")
    void updatePassword(@Body ChangePasswordParams passwordParams, Callback<Void> callback);

    /**
     * Reset the password server side.
     *
     * @param params   the forget password params
     * @param callback the asynchronous callback to call when finished
     */
    @POST(RestClient.URI_API_PREFIX_PATH_R0 + "/account/password/email/requestToken")
    void forgetPassword(@Body ForgetPasswordParams params, Callback<ForgetPasswordResponse> callback);

    /**
     * Pass params to the server for the token refresh phase.
     *
     * @param refreshParams the refresh token parameters
     * @param callback      the asynchronous callback called with the response
     */
    @POST(RestClient.URI_API_PREFIX_PATH_R0 + "/tokenrefresh")
    void tokenrefresh(@Body TokenRefreshParams refreshParams, Callback<TokenRefreshResponse> callback);

    /**
     * List all 3PIDs linked to the Matrix user account.
     *
     * @param callback the asynchronous callback called with the response
     */
    @GET(RestClient.URI_API_PREFIX_PATH_R0 + "/account/3pid")
    void threePIDs(Callback<AccountThreePidsResponse> callback);

    /**
     * Add an 3Pid to a user
     *
     * @param params   the params
     * @param callback the asynchronous callback called with the response
     */
    @POST(RestClient.URI_API_PREFIX_PATH_R0 + "/account/3pid")
    void add3PID(@Body AddThreePidsParams params, Callback<Void> callback);

    /**
     * Delete a 3Pid of a user
     *
     * @param params   the params
     * @param callback the asynchronous callback called with the response
     */
    @POST(RestClient.URI_API_PREFIX_PATH_UNSTABLE + "/account/3pid/delete")
    void delete3PID(@Body DeleteThreePidParams params, Callback<Void> callback);

    /**
     * Request a validation token for an email
     * Note: Proxies the identity server API validate/email/requestToken, but first checks that
     * the given email address is not already associated with an account on this Home Server.
     *
     * @param params   the parameters
     * @param callback the asynchronous callback
     */
    @POST(RestClient.URI_API_PREFIX_PATH_R0 + "/account/3pid/email/requestToken")
    void requestEmailValidation(@Body RequestEmailValidationParams params, Callback<RequestEmailValidationResponse> callback);

    /**
     * Request a validation token for an email being added during registration process
     * Note: Proxies the identity server API validate/email/requestToken, but first checks that
     * the given email address is not already associated with an account on this Home Server.
     *
     * @param params   the parameters
     * @param callback the asynchronous callback
     */
    @POST(RestClient.URI_API_PREFIX_PATH_R0 + "/register/email/requestToken")
    void requestEmailValidationForRegistration(@Body RequestEmailValidationParams params, Callback<RequestEmailValidationResponse> callback);

    /**
     * Request a validation token for a phone number
     * Note: Proxies the identity server API validate/msisdn/requestToken, but first checks that
     * the given phone number is not already associated with an account on this Home Server.
     *
     * @param params   the parameters
     * @param callback the asynchronous callback
     */
    @POST(RestClient.URI_API_PREFIX_PATH_R0 + "/account/3pid/msisdn/requestToken")
    void requestPhoneNumberValidation(@Body RequestPhoneNumberValidationParams params, Callback<RequestPhoneNumberValidationResponse> callback);

    /**
     * Request a validation token for a phone number being added during registration process
     * Note: Proxies the identity server API validate/msisdn/requestToken, but first checks that
     * the given phone number is not already associated with an account on this Home Server.
     *
     * @param params   the parameters
     * @param callback the asynchronous callback
     */
    @POST(RestClient.URI_API_PREFIX_PATH_R0 + "/register/msisdn/requestToken")
    void requestPhoneNumberValidationForRegistration(@Body RequestPhoneNumberValidationParams params, Callback<RequestPhoneNumberValidationResponse> callback);
}