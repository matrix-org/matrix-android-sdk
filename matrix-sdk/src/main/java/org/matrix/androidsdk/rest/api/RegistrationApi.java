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

import org.matrix.androidsdk.rest.model.login.TokenRefreshParams;
import org.matrix.androidsdk.rest.model.login.TokenRefreshResponse;

import retrofit.Callback;
import retrofit.http.Body;
import retrofit.http.POST;

/**
 * The Registration REST API.
 */
public interface RegistrationApi {

    /**
     * Pass params to the server for the token refresh phase.
     * @param refreshParams the refresh token parameters
     * @param callback the asynchronous callback called with the response
     */
    @POST("/tokenrefresh")
    void tokenrefresh(@Body TokenRefreshParams refreshParams, Callback<TokenRefreshResponse> callback);

}
