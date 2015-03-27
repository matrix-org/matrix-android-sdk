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

import org.matrix.androidsdk.RestClient;
import org.matrix.androidsdk.rest.api.PresenceApi;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.RestAdapterCallback;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.rest.model.login.Credentials;

/**
 * Class used to make requests to the presence API.
 */
public class PresenceRestClient extends RestClient<PresenceApi> {

    /**
     * {@inheritDoc}
     */
    public PresenceRestClient(Credentials credentials) {
        super(credentials, PresenceApi.class, RestClient.URI_API_PREFIX);
    }

    /**
     * Set this user's presence.
     * @param presence the presence state
     * @param statusMsg a status message
     * @param callback on success callback
     */
    public void setPresence(final String presence, final String statusMsg, final ApiCallback<Void> callback) {
        User userPresence = new User();
        userPresence.presence = presence;
        userPresence.statusMsg = statusMsg;

        mApi.presenceStatus(mCredentials.userId, userPresence, new RestAdapterCallback<Void>(mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                setPresence(presence, statusMsg, callback);
            }
        }));
    }

    /**
     * Get a user's presence state.
     * @param userId the user id
     * @param callback on success callback containing a User object with populated presence and statusMsg fields
     */
    public void getPresence(final String userId, final ApiCallback<User> callback) {
        mApi.presenceStatus(userId, new RestAdapterCallback<User>(mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                getPresence(userId, callback);
            }
        }));
    }
}
