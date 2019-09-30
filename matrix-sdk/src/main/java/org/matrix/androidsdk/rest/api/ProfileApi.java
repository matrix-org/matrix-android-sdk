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
package org.matrix.androidsdk.rest.api;

import org.matrix.androidsdk.RestClient;
import org.matrix.androidsdk.rest.model.AddThreePidsParams;
import org.matrix.androidsdk.rest.model.ChangePasswordParams;
import org.matrix.androidsdk.rest.model.DeactivateAccountParams;
import org.matrix.androidsdk.rest.model.ForgetPasswordParams;
import org.matrix.androidsdk.rest.model.ForgetPasswordResponse;
import org.matrix.androidsdk.rest.model.RequestEmailValidationParams;
import org.matrix.androidsdk.rest.model.RequestEmailValidationResponse;
import org.matrix.androidsdk.rest.model.RequestOwnershipParams;
import org.matrix.androidsdk.rest.model.RequestPhoneNumberValidationParams;
import org.matrix.androidsdk.rest.model.RequestPhoneNumberValidationResponse;
import org.matrix.androidsdk.rest.model.SuccessResult;
import org.matrix.androidsdk.rest.model.ThreePidCreds;
import org.matrix.androidsdk.rest.model.Unbind3pidParams;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.rest.model.login.TokenRefreshParams;
import org.matrix.androidsdk.rest.model.login.TokenRefreshResponse;
import org.matrix.androidsdk.rest.model.pid.AccountThreePidsResponse;
import org.matrix.androidsdk.rest.model.pid.AddThreePidsParamsPreMSC2290;
import org.matrix.androidsdk.rest.model.pid.DeleteThreePidParams;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Url;


/**
 * The profile REST API.
 */
public interface ProfileApi {

    /**
     * Update a user's display name.
     *
     * @param userId the user id
     * @param user   the user object containing the new display name
     */
    @PUT(RestClient.URI_API_PREFIX_PATH_R0 + "profile/{userId}/displayname")
    Call<Void> displayname(@Path("userId") String userId, @Body User user);

    /**
     * Get a user's display name.
     *
     * @param userId the user id
     */
    @GET(RestClient.URI_API_PREFIX_PATH_R0 + "profile/{userId}/displayname")
    Call<User> displayname(@Path("userId") String userId);

    /**
     * Update a user's avatar URL.
     *
     * @param userId the user id
     * @param user   the user object containing the new avatar url
     */
    @PUT(RestClient.URI_API_PREFIX_PATH_R0 + "profile/{userId}/avatar_url")
    Call<Void> avatarUrl(@Path("userId") String userId, @Body User user);

    /**
     * Get a user's avatar URL.
     *
     * @param userId the user id
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
     * @param params the forget password params
     */
    @POST(RestClient.URI_API_PREFIX_PATH_R0 + "account/password/email/requestToken")
    Call<ForgetPasswordResponse> forgetPassword(@Body ForgetPasswordParams params);

    /**
     * Deactivate the user account
     *
     * @param params the deactivate account params
     */
    @POST(RestClient.URI_API_PREFIX_PATH_R0 + "account/deactivate")
    Call<Void> deactivate(@Body DeactivateAccountParams params);

    /**
     * Pass params to the server for the token refresh phase.
     *
     * @param refreshParams the refresh token parameters
     */
    @POST(RestClient.URI_API_PREFIX_PATH_R0 + "tokenrefresh")
    Call<TokenRefreshResponse> tokenRefresh(@Body TokenRefreshParams refreshParams);

    /**
     * List all 3PIDs linked to the Matrix user account.
     */
    @GET(RestClient.URI_API_PREFIX_PATH_R0 + "account/3pid")
    Call<AccountThreePidsResponse> threePIDs();

    /**
     * Old API end point
     *
     * Add an 3Pid to a user
     * @param params the params
     */
    @Deprecated
    @POST(RestClient.URI_API_PREFIX_PATH_R0 + "account/3pid")
    Call<Void> add3PIDLegacy(@Body AddThreePidsParamsPreMSC2290 params);


    /**
     * MSC2290 to add email to account
     * @param params
     * @return
     */
    @POST(RestClient.URI_API_PREFIX_PATH_UNSTABLE + "account/3pid/add")
    Call<Void> add3PIDMSC2290(@Body AddThreePidsParams params);


    /**
     * MSC2290 to change the binding status of a 3pid
     * @param params
     * @return
     */
    @POST(RestClient.URI_API_PREFIX_PATH_UNSTABLE + "account/3pid/bind")
    Call<Void> bind3PID(@Body ThreePidCreds params);


    /**
     * MSC2290 to change the binding status of a 3pid
     * @param params
     * @return
     */
    @POST(RestClient.URI_API_PREFIX_PATH_UNSTABLE + "account/3pid/unbind")
    Call<Void> unbind3PID(@Body Unbind3pidParams params);


    /**
     * Delete a 3Pid of a user
     *
     * @param params the params
     */
    @POST(RestClient.URI_API_PREFIX_PATH_UNSTABLE + "account/3pid/delete")
    Call<Void> delete3PID(@Body DeleteThreePidParams params);

    /**
     * Request a validation token for an email
     * Note: Proxies the identity server API validate/email/requestToken, but first checks that
     * the given email address is not already associated with an account on this Home Server.
     *
     * @param params the parameters
     */
    @POST(RestClient.URI_API_PREFIX_PATH_R0 + "account/3pid/email/requestToken")
    Call<RequestEmailValidationResponse> requestEmailValidation(@Body RequestEmailValidationParams params);

    /**
     * Request a validation token for an email being added during registration process
     * Note: Proxies the identity server API validate/email/requestToken, but first checks that
     * the given email address is not already associated with an account on this Home Server.
     *
     * @param params the parameters
     */
    @POST(RestClient.URI_API_PREFIX_PATH_R0 + "register/email/requestToken")
    Call<RequestEmailValidationResponse> requestEmailValidationForRegistration(@Body RequestEmailValidationParams params);

    /**
     * Request a validation token for a phone number
     * Note: Proxies the identity server API validate/msisdn/requestToken, but first checks that
     * the given phone number is not already associated with an account on this Home Server.
     *
     * @param params the parameters
     */
    @POST(RestClient.URI_API_PREFIX_PATH_R0 + "account/3pid/msisdn/requestToken")
    Call<RequestPhoneNumberValidationResponse> requestPhoneNumberValidation(@Body RequestPhoneNumberValidationParams params);

    /**
     * Request a validation token for a phone number being added during registration process
     * Note: Proxies the identity server API validate/msisdn/requestToken, but first checks that
     * the given phone number is not already associated with an account on this Home Server.
     *
     * @param params the parameters
     */
    @POST(RestClient.URI_API_PREFIX_PATH_R0 + "register/msisdn/requestToken")
    Call<RequestPhoneNumberValidationResponse> requestPhoneNumberValidationForRegistration(@Body RequestPhoneNumberValidationParams params);


    @POST
    Call<SuccessResult> submitPhoneNumberToken(@Url String submitURL, @Body RequestOwnershipParams params);
}
