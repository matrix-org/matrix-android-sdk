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

import android.util.Log;

import org.matrix.androidsdk.HomeserverConnectionConfig;
import org.matrix.androidsdk.RestClient;
import org.matrix.androidsdk.rest.api.ProfileApiV2;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.RestAdapterCallback;
import org.matrix.androidsdk.rest.model.AuthParams;
import org.matrix.androidsdk.rest.model.ChangePasswordParams;
/**
 * Class used to make requests to the profile API V2.
 */
public class ProfileRestClientV2 extends RestClient<ProfileApiV2> {

    /**
     * {@inheritDoc}
     */
    public ProfileRestClientV2(HomeserverConnectionConfig hsConfig) {
        super(hsConfig, ProfileApiV2.class, RestClient.URI_API_PREFIX_V2_ALPHA, false);
    }

    /**
     * Get the user's display name.
     * @param userId the user id
     * @param oldPassword the former password
     * @param newPassword the new password
     * @param callback the callback
     */
    public void updatePassword(final String userId, final String oldPassword, final String newPassword, final ApiCallback<Void> callback) {
        final String description = "update password : " + userId + " oldPassword " + oldPassword + " newPassword " + newPassword;

        ChangePasswordParams passwordParams = new ChangePasswordParams();

        passwordParams.auth = new AuthParams();
        passwordParams.auth.type = "m.login.password";
        passwordParams.auth.user = userId;
        passwordParams.auth.password = oldPassword;
        passwordParams.new_password = newPassword;

        mApi.updatePassword(passwordParams, new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                try {
                    updatePassword(userId, oldPassword, newPassword, callback);
                } catch (Exception e) {
                }
            }
        }));
    }
}
