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

import org.matrix.androidsdk.rest.model.User;

<<<<<<< HEAD
import retrofit.Callback;
import retrofit.http.GET;
import retrofit.http.Path;
=======
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.PUT;
import retrofit2.http.Path;
>>>>>>> Migrate API calls from Retrofit 1 to Retrofit 2

/**
 * The presence REST API.
 */
public interface PresenceApi {
<<<<<<< HEAD
    /**
     * Get a user's presence state.
     *
     * @param userId   the user id
     * @param callback the asynchronous callback called with the response
=======

    /**
     * Set this user's presence state.
     * @param userId the user id
     * @param userPresence a User object with possibly the presence and statusMsg fields
     */
    @PUT("presence/{userId}/status")
    Call<Void> presenceStatus(@Path("userId") String userId, @Body User userPresence);

    /**
     * Get a user's presence state.
     * @param userId the user id
>>>>>>> Migrate API calls from Retrofit 1 to Retrofit 2
     */
    @GET("presence/{userId}/status")
    Call<User> presenceStatus(@Path("userId") String userId);
}
