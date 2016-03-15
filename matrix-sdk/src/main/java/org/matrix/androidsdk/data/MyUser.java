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

import android.text.TextUtils;

import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.client.PresenceRestClient;
import org.matrix.androidsdk.rest.client.ProfileRestClient;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.User;

/**
 * Class representing the logged-in user.
 */
public class MyUser extends User {

    private ProfileRestClient mProfileRestClient;
    private PresenceRestClient mPresenceRestClient;

    private boolean mIsAvatarRefreshed = false;
    private boolean mIsDislayNameRefreshed = false;

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
                MyUser.this.setAvatarUrl(avatarUrl);
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

    //================================================================================
    // Refresh
    //================================================================================

    /**
     * Refresh the user data if it is required
     * @param callback callback when the job is done.
     */
    public void refreshUserInfos(final ApiCallback<Void> callback) {
        if (!mIsDislayNameRefreshed) {
            refreshUserDisplayname(callback);
        }

        if (!mIsAvatarRefreshed) {
            refreshUserAvatarUrl(callback);
        }

        if (null != callback) {
            callback.onSuccess(null);
        }
    }

    /**
     * Refresh the avatar url
     * @param callback callback when the job is done.
     */
    private void refreshUserAvatarUrl(final ApiCallback<Void> callback) {
        mProfileRestClient.avatarUrl(user_id, new SimpleApiCallback<String>() {
            @Override
            public void onSuccess(String anAvatarUrl) {
                if (MyUser.this.mDataHandler.isActive()) {
                    MyUser.this.setAvatarUrl(anAvatarUrl);
                    MyUser.this.mDataHandler.getStore().setAvatarURL(anAvatarUrl);
                    mIsAvatarRefreshed = true;
                    MyUser.this.mDataHandler.getStore().storeUser(MyUser.this);

                    if (null != callback) {
                        callback.onSuccess(null);
                    }
                }
            }

            private void onError() {
                if (MyUser.this.mDataHandler.isActive()) {
                    // will try later
                    if (null != callback) {
                        callback.onSuccess(null);
                    }
                }
            }

            @Override
            public void onNetworkError(Exception e) {
                onError();
            }

            @Override
            public void onMatrixError(final MatrixError e) {
                onError();
            }

            @Override
            public void onUnexpectedError(final Exception e) {
                onError();
            }
        });
    }

    /**
     * Refresh the displayname.
     * @param callback callback callback when the job is done.
     */
    private void refreshUserDisplayname(final ApiCallback<Void> callback) {
        mProfileRestClient.displayname(user_id, new SimpleApiCallback<String>() {
            @Override
            public void onSuccess(String aDisplayname) {
                if (MyUser.this.mDataHandler.isActive()) {
                    displayname = aDisplayname;
                    MyUser.this.mDataHandler.getStore().setDisplayName(aDisplayname);

                    mIsDislayNameRefreshed = true;
                }
            }

            private void onError() {
                if (MyUser.this.mDataHandler.isActive()) {
                    // will try later
                    if (!mIsAvatarRefreshed) {
                        refreshUserAvatarUrl(callback);
                    } else {
                        if (null != callback) {
                            callback.onSuccess(null);
                        }
                    }
                }
            }

            @Override
            public void onNetworkError(Exception e) {
                onError();
            }

            @Override
            public void onMatrixError(final MatrixError e) {
                onError();
            }

            @Override
            public void onUnexpectedError(final Exception e) {
                onError();
            }
        });
    }

}
