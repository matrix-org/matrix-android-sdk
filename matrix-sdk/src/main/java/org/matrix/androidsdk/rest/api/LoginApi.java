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

import com.google.gson.JsonObject;

import org.matrix.androidsdk.rest.model.login.LoginFlowResponse;
import org.matrix.androidsdk.rest.model.login.LoginParams;
import org.matrix.androidsdk.rest.model.login.RegistrationParams;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

/**
 * The login REST API.
 */
public interface LoginApi {

    /**
     * Get the different login flows supported by the server.
     */
    @GET("login")
    Call<LoginFlowResponse> login();


    /**
     * Try to create an account
     */
    @POST("register")
    Call<JsonObject> register(@Body RegistrationParams params);

    /**
     * Pass params to the server for the current login phase.
     * @param loginParams the login parameters
     */
    @POST("login")
    Call<JsonObject> login(@Body LoginParams loginParams);

    /**
     * Invalidate the access token, so that it can no longer be used for authorization.
     */
    @POST("logout")
    Call<JsonObject> logout();
}
