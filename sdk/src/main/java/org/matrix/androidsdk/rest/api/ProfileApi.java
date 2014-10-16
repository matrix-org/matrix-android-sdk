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

import retrofit.Callback;
import retrofit.http.Body;
import retrofit.http.GET;
import retrofit.http.PUT;
import retrofit.http.Path;

/**
 * The profile REST API.
 */
public interface ProfileApi {

    /**
     * Update a user's display name.
     * @param userId the user id
     * @param user the user object containing the new display name
     * @param callback the asynchronous callback to call when finished
     */
    @PUT("/profile/{userId}/displayname")
    public void displayname(@Path("userId") String userId, @Body User user, Callback<Void> callback);

    /**
     * Get a user's display name.
     * @param userId the user id
     * @param callback the asynchronous callback called with the response
     */
    @GET("/profile/{userId}/displayname")
    public void displayname(@Path("userId") String userId, Callback<User> callback);

    /**
     * Update a user's avatar URL.
     * @param userId the user id
     * @param user the user object containing the new avatar url
     * @param callback the asynchronous callback to call when finished
     */
    @PUT("/profile/{userId}/avatar_url")
    public void avatarUrl(@Path("userId") String userId, @Body User user, Callback<Void> callback);

    /**
     * Get a user's avatar URL.
     * @param userId the user id
     * @param callback the asynchronous callback called with the response
     */
    @GET("/profile/{userId}/avatar_url")
    public void avatarUrl(@Path("userId") String userId, Callback<User> callback);
}