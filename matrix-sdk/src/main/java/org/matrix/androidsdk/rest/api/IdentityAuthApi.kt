/*
 * Copyright 2019 The Matrix.org Foundation C.I.C.
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
package org.matrix.androidsdk.rest.api

import org.matrix.androidsdk.rest.model.identityserver.IdentityAccountResponse
import org.matrix.androidsdk.rest.model.identityserver.IdentityServerRegisterResponse
import org.matrix.androidsdk.rest.model.openid.RequestOpenIdTokenResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * Ref:
 * - https://github.com/matrix-org/matrix-doc/blob/master/proposals/1961-integrations-auth.md
 * - https://github.com/matrix-org/matrix-doc/blob/dbkr/tos_2/proposals/2140-terms-of-service-2.md#is-register-api
 */
interface IdentityAuthApi {

    /**
     * Check that we can use the identity server. You'll get a 403 if this is not the case
     */
    @GET("account")
    fun checkAccount(): Call<IdentityAccountResponse>

    /**
     * register to the server
     *
     * @param body the body content
     */
    @POST("account/register")
    fun register(@Body requestOpenIdTokenResponse: RequestOpenIdTokenResponse): Call<IdentityServerRegisterResponse>

    /**
     * unregister to the server
     * Authenticated method
     */
    @POST("account/logout")
    fun logout(): Call<Unit>

}
