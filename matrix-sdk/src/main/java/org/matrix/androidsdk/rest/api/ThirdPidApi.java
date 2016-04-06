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
package org.matrix.androidsdk.rest.api;

import retrofit.Callback;
import retrofit.http.Body;
import retrofit.http.GET;
import retrofit.http.POST;
import retrofit.http.Query;

import org.matrix.androidsdk.rest.model.AddThreePidsParams;
import org.matrix.androidsdk.rest.model.PidResponse;
import org.matrix.androidsdk.rest.model.RequestEmailValidationResponse;

public interface ThirdPidApi {

    /**
     * Get the 3rd party id from the
     * @param address the address.
     * @param medium the medium.
     * @param callback the asynchronous callback called with the response
     */
    @GET("/lookup")
    void lookup3Pid(@Query("address") String address, @Query("medium") String medium, Callback<PidResponse> callback);


    /**
     * Request an email validation
     * @param params the email validations params
     * @param callback the asynchronous callback called with the response
     */
    @POST("/validate/email/requestToken")
    void requestEmailValidation(@Query("clientSecret") String clientSecret, @Query("email") String email, @Query("sendAttempt") Integer sendAttempt, Callback<RequestEmailValidationResponse> callback);

    /**
     * Add an 3Pids to an user
     * @param params the params
     * @param callback the asynchronous callback called with the response
     */
    @POST("/account/3pid")
    void add3PID(@Body AddThreePidsParams params, Callback<Void> callback);
}
