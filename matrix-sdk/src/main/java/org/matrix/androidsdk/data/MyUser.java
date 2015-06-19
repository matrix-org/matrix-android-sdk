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
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.client.PresenceRestClient;
import org.matrix.androidsdk.rest.client.ProfileRestClient;
import org.matrix.androidsdk.rest.model.User;

/**
 * Class representing the logged-in user.
 */
public class MyUser extends User {

    private ProfileRestClient mProfileRestClient;
    private PresenceRestClient mPresenceRestClient;

    public MyUser(User user) {
        clone(user);
    }

    public void setProfileRestClient(ProfileRestClient restClient) {
        mProfileRestClient = restClient;
    }

    public void setPresenceRestClient(PresenceRestClient restClient) {
        mPresenceRestClient = restClient;
    }

    /**
     * Update the user's display name.
     * @param displayName the new name
     * @param callback the async callback
     */
    public void updateDisplayName(final String displayName, ApiCallback<Void> callback) {
        mProfileRestClient.updateDisplayname(displayName, new SimpleApiCallback<Void>(callback) {
            @Override
            public void onSuccess(Void info) {
                // Update the object member before calling the given callback
                MyUser.this.displayname = displayName;
                MyUser.this.mDataHandler.getStore().setDisplayName(displayName);
                super.onSuccess(info);
            }
        });
    }

    /**
     * Update the user's avatar URL.
     * @param avatarUrl the new avatar URL
     * @param callback the async callback
     */
    public void updateAvatarUrl(final String avatarUrl, ApiCallback<Void> callback) {
        mProfileRestClient.updateAvatarUrl(avatarUrl, new SimpleApiCallback<Void>(callback) {
            @Override
            public void onSuccess(Void info) {
                // Update the object member before calling the given callback
                MyUser.this.avatarUrl = avatarUrl;
                MyUser.this.mDataHandler.getStore().setAvatarURL(avatarUrl);
                super.onSuccess(info);
            }
        });
    }

    /**
     * Update the user's presence information.
     * @param presence the presence
     * @param statusMsg the status message
     * @param callback the async callback
     */
    public void updatePresence(final String presence, final String statusMsg, ApiCallback<Void> callback) {
        mPresenceRestClient.setPresence(presence, statusMsg, new SimpleApiCallback<Void>(callback) {
            @Override
            public void onSuccess(Void info) {
                // Update the object member before calling the given callback
                MyUser.this.presence = presence;
                MyUser.this.statusMsg = statusMsg;
                super.onSuccess(info);
            }
        });
    }
}
