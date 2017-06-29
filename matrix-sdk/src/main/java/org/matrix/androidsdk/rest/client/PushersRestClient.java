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

package org.matrix.androidsdk.rest.client;

import org.matrix.androidsdk.util.Log;

import org.matrix.androidsdk.HomeserverConnectionConfig;
import org.matrix.androidsdk.RestClient;
import org.matrix.androidsdk.data.Pusher;
import org.matrix.androidsdk.rest.api.PushersApi;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.RestAdapterCallback;
import org.matrix.androidsdk.rest.model.PushersResponse;

import java.util.HashMap;

/**
 * REST client for the Pushers API.
 */
public class PushersRestClient extends RestClient<PushersApi> {
    private static final String LOG_TAG = "PushersRestClient";

    private static final String PUSHER_KIND_HTTP = "http";
    private static final String DATA_KEY_HTTP_URL = "url";

    public PushersRestClient(HomeserverConnectionConfig hsConfig) {
        super(hsConfig, PushersApi.class, RestClient.URI_API_PREFIX_PATH_R0, true);
    }

    /** Add a new HTTP pusher.
     * @param pushkey the pushkey
     * @param appId the appplication id
     * @param profileTag the profile tag
     * @param lang the language
     * @param appDisplayName a human-readable application name
     * @param deviceDisplayName a human-readable device name
     * @param url the URL that should be used to send notifications
     * @param append append the pusher
     */
    public void addHttpPusher(
            final String pushkey, final String appId,
            final String profileTag, final String lang,
            final String appDisplayName, final String deviceDisplayName,
            final String url, boolean append, final ApiCallback<Void> callback) {
        manageHttpPusher(pushkey, appId, profileTag, lang, appDisplayName, deviceDisplayName, url, append, callback, true);
    }

    /** remove a new HTTP pusher.
     * @param pushkey the pushkey
     * @param appId the appplication id
     * @param profileTag the profile tag
     * @param lang the language
     * @param appDisplayName a human-readable application name
     * @param deviceDisplayName a human-readable device name
     * @param url the URL that should be used to send notifications
     */
    public void removeHttpPusher(
            final String pushkey, final String appId,
            final String profileTag, final String lang,
            final String appDisplayName, final String deviceDisplayName,
            final String url, final ApiCallback<Void> callback) {
        manageHttpPusher(pushkey, appId, profileTag, lang, appDisplayName, deviceDisplayName, url, false, callback, false);
    }


    /** add/remove a new HTTP pusher.
     * @param pushkey the pushkey
     * @param appId the appplication id
     * @param profileTag the profile tag
     * @param lang the language
     * @param appDisplayName a human-readable application name
     * @param deviceDisplayName a human-readable device name
     * @param url the URL that should be used to send notifications
     * @param addPusher true to add the pusher / false to remove it
     */
    private void manageHttpPusher(
            final String pushkey, final String appId,
            final String profileTag, final String lang,
            final String appDisplayName, final String deviceDisplayName,
            final String url, final Boolean append, final ApiCallback<Void> callback, final boolean addPusher) {
        Pusher pusher = new Pusher();
        pusher.pushkey = pushkey;
        pusher.appId = appId;
        pusher.profileTag = profileTag;
        pusher.lang = lang;
        pusher.kind = addPusher ? PUSHER_KIND_HTTP : null;
        pusher.appDisplayName= appDisplayName;
        pusher.deviceDisplayName = deviceDisplayName;
        pusher.data = new HashMap<>();
        pusher.data.put(DATA_KEY_HTTP_URL, url);
        if (addPusher) {
            pusher.append = append;
        }

        final String description = "manageHttpPusher";

        mApi.set(pusher).enqueue(new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                try {
                    manageHttpPusher(pushkey, appId, profileTag, lang, appDisplayName, deviceDisplayName, url, append, callback, addPusher);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "## manageHttpPusher() failed" + e.getMessage());
                }
            }
        }));
    }

    /**
     * Retrieve the pushers list
     * @param callback the callback
     */
    public void getPushers(final ApiCallback<PushersResponse> callback) {
        final String description = "getPushers";

        mApi.get().enqueue(new RestAdapterCallback<PushersResponse>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                try {
                    getPushers(callback);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "## getPushers() failed" + e.getMessage());
                }
            }
        }));
    }
}
