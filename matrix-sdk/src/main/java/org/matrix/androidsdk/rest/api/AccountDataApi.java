/* 
 * Copyright 2015 OpenMarket Ltd
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

import java.util.Map;

import retrofit.Callback;
import retrofit.http.Body;
import retrofit.http.POST;
import retrofit.http.PUT;
import retrofit.http.Path;

public interface AccountDataApi {

    /**
     * Set some account_data for the client.
     *
     * @param userId   the user id
     * @param type     the type
     * @param params   the put params
     * @param callback the asynchronous callback called when finished
     */
    @PUT("/user/{userId}/account_data/{type}")
    void setAccountData(@Path("userId") String userId, @Path("type") String type, @Body Object params, Callback<Void> callback);

    /**
     * Gets a bearer token from the homeserver that the user can
     * present to a third party in order to prove their ownership
     * of the Matrix account they are logged into.
     *
     * @param userId   the user id
     * @param body     the body content
     * @param callback the asynchronous callback called when finished
     */
    @POST("/user/{userId}/openid/request_token")
    void openIdToken(@Path("userId") String userId, @Body Map<Object, Object> body, Callback<Map<Object, Object>> callback);
}
