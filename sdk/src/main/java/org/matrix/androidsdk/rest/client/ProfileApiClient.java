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
package org.matrix.androidsdk.rest.client;

import org.matrix.androidsdk.MXApiClient;
import org.matrix.androidsdk.api.ProfileApi;
import org.matrix.androidsdk.api.response.User;
import org.matrix.androidsdk.api.response.login.Credentials;

import retrofit.RestAdapter;
import retrofit.client.Response;

/**
 * Class used to make requests to the profile API.
 */
public class ProfileApiClient extends MXApiClient {

    private ProfileApi mApi;

    /**
     * Public constructor.
     * @param credentials the user's credentials
     */
    public ProfileApiClient(Credentials credentials) {
        super(credentials);
    }

    @Override
    protected void initApi(RestAdapter restAdapter) {
        mApi = restAdapter.create(ProfileApi.class);
    }

    /**
     * Protected setter for injection by unit tests.
     * @param api the api object
     */
    protected void setApi(ProfileApi api) {
        mApi = api;
    }

    /**
     * Get the user's display name.
     * @param userId the user id
     * @param callback the callback to return the name on success
     */
    public void displayname(String userId, final ApiCallback<String> callback) {
        mApi.displayname(userId, new DefaultCallback<User>() {
            @Override
            public void success(User user, Response response) {
                callback.onSuccess(user.displayname);
            }
        });
    }

    /**
     * Update this user's own display name.
     * @param newName the new name
     * @param callback the callback if the call succeeds
     */
    public void updateDisplayname(String newName, final ApiCallback<Void> callback) {
        User user = new User();
        user.displayname = newName;

        mApi.displayname(mCredentials.userId, user, new DefaultCallback<Void>() {
            @Override
            public void success(Void aVoid, Response response) {
                callback.onSuccess(aVoid);
            }
        });
    }

    /**
     * Get the user's avatar URL.
     * @param userId the user id
     * @param callback the callback to return the URL on success
     */
    public void avatarUrl(String userId, final ApiCallback<String> callback) {
        mApi.avatarUrl(userId, new DefaultCallback<User>() {
            @Override
            public void success(User user, Response response) {
                callback.onSuccess(user.avatarUrl);
            }
        });
    }

    /**
     * Update this user's own avatar URL.
     * @param newUrl the new name
     * @param callback the callback if the call succeeds
     */
    public void updateAvatarUrl(String newUrl, final ApiCallback<Void> callback) {
        User user = new User();
        user.avatarUrl = newUrl;

        mApi.avatarUrl(mCredentials.userId, user, new DefaultCallback<Void>() {
            @Override
            public void success(Void aVoid, Response response) {
                callback.onSuccess(aVoid);
            }
        });
    }
}
