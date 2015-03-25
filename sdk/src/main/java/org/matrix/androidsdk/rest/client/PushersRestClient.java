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

import org.matrix.androidsdk.RestClient;
import org.matrix.androidsdk.data.Pusher;
import org.matrix.androidsdk.rest.api.PushersApi;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.RestAdapterCallback;
import org.matrix.androidsdk.rest.model.login.Credentials;

import java.net.URL;
import java.util.HashMap;

/**
 * REST client for the Pushers API.
 */
public class PushersRestClient extends RestClient<PushersApi> {

    private static final String PUSHER_KIND_HTTP = "http";
    private static final String DATA_KEY_HTTP_URL = "url";

    public PushersRestClient(Credentials credentials) {
        super(credentials, PushersApi.class, RestClient.URI_API_PREFIX);
    }

    /** Add a new HTTP pusher.
     * @param pushkey the pushkey
     * @param appId the appplication id
     * @param profileTag the profile tag
     * @param lang the language
     * @param appDisplayName a human-readable application name
     * @param deviceDisplayName a human-readable device name
     * @param url the URL that should be used to send notifications
     */
    public void addHttpPusher(
            String pushkey, String appId,
            String profileTag, String lang,
            String appDisplayName, String deviceDisplayName,
            String url) {
        Pusher pusher = new Pusher();
        pusher.pushkey = pushkey;
        pusher.appId = appId;
        pusher.profileTag = profileTag;
        pusher.lang = lang;
        pusher.kind = PUSHER_KIND_HTTP;
        pusher.appDisplayName= appDisplayName;
        pusher.deviceDisplayName = deviceDisplayName;
        pusher.data = new HashMap<String, String>();
        pusher.data.put(DATA_KEY_HTTP_URL, url);

        mApi.set(pusher);
    }
}
