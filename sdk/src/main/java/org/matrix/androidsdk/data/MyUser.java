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
package org.matrix.androidsdk.data;

import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.client.ProfileRestClient;
import org.matrix.androidsdk.rest.model.User;

/**
 * Class representing the current user.
 */
public class MyUser extends User {

    private ProfileRestClient mProfileRestClient;
    private boolean isReady;

    public MyUser(User user) {
        clone(user);
    }

    public void setProfileRestClient(ProfileRestClient restClient) {
        mProfileRestClient = restClient;
    }

    /**
     * Update the user's display name.
     * @param displayName the new name
     * @param callback the async callback
     */
    public void updateDisplayName(String displayName, ApiCallback<Void> callback) {
        mProfileRestClient.updateDisplayname(displayName, callback);
    }

    /**
     * Update the user's avatar URL.
     * @param avatarUrl the new avatar URL
     * @param callback the async callback
     */
    public void updateAvatarUrl(String avatarUrl, ApiCallback<Void> callback) {
        mProfileRestClient.updateAvatarUrl(avatarUrl, callback);
    }
}
