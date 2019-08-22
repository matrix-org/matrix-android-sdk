/*
 * Copyright 2019 New Vector Ltd
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
package org.matrix.androidsdk.rest.client

import org.matrix.androidsdk.HomeServerConnectionConfig
import org.matrix.androidsdk.RestClient
import org.matrix.androidsdk.core.callback.ApiCallback
import org.matrix.androidsdk.rest.api.IdentityAuthApi
import org.matrix.androidsdk.rest.callback.RestAdapterCallback
import org.matrix.androidsdk.rest.model.identityserver.IdentityServerRegisterResponse
import org.matrix.androidsdk.rest.model.openid.RequestOpenIdTokenResponse

class IdentityAuthRestClient(hsConfig: HomeServerConnectionConfig) :
        RestClient<IdentityAuthApi>(hsConfig, IdentityAuthApi::class.java, URI_IDENTITY_PATH_V2, false, true) {

    fun register(openIdTokenResponse: RequestOpenIdTokenResponse, callback: ApiCallback<IdentityServerRegisterResponse>) {
        mApi.register(openIdTokenResponse).enqueue(RestAdapterCallback("register", null, callback, null))
    }

    fun checkAccount(token: String, callback: ApiCallback<Unit>) {
        setAccessToken(token)

        mApi.checkAccount().enqueue(RestAdapterCallback("checkAccount", null, callback, null))
    }

}
