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

import org.matrix.androidsdk.HomeserverConnectionConfig;
import org.matrix.androidsdk.RestClient;
import org.matrix.androidsdk.rest.api.ProfileApi;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.RestAdapterCallback;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.rest.model.login.Credentials;

import retrofit.client.Response;

/**
 * Class used to make requests to the profile API.
 */
public class ProfileRestClient extends RestClient<ProfileApi> {

    /**
     * {@inheritDoc}
     */
    public ProfileRestClient(HomeserverConnectionConfig hsConfig, Credentials credentials) {
        super(hsConfig, credentials, ProfileApi.class, RestClient.URI_API_PREFIX, false);
    }

    /**
     * Get the user's display name.
     * @param userId the user id
     * @param callback the callback to return the name on success
     */
    public void displayname(final String userId, final ApiCallback<String> callback) {
        final String description = "displayname userId : " + userId;

        mApi.displayname(userId, new RestAdapterCallback<User>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                displayname(userId, callback);
            }
        }) {
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
    public void updateDisplayname(final String newName, final ApiCallback<Void> callback) {
        final String description = "updateDisplayname newName : " + newName;

        User user = new User();
        user.displayname = newName;

        // don't retry if the network comes back
        // let the user chooses what he want to do
        mApi.displayname(mCredentials.userId, user, new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                updateDisplayname(newName, callback);
            }
        }));
    }

    /**
     * Get the user's avatar URL.
     * @param userId the user id
     * @param callback the callback to return the URL on success
     */
    public void avatarUrl(final String userId, final ApiCallback<String> callback) {
        final String description = "avatarUrl userId : " + userId;

        mApi.avatarUrl(userId, new RestAdapterCallback<User>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                avatarUrl(userId, callback);
            }
        }) {
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
    public void updateAvatarUrl(final String newUrl, final ApiCallback<Void> callback) {
        final String description = "updateAvatarUrl newUrl : " + newUrl;

        User user = new User();
        user.avatarUrl = newUrl;

        mApi.avatarUrl(mCredentials.userId, user, new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                updateAvatarUrl(newUrl, callback);
            }
        }));
    }
}
