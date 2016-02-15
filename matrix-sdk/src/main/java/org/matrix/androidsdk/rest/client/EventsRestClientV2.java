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

import android.text.TextUtils;

import org.matrix.androidsdk.HomeserverConnectionConfig;
import org.matrix.androidsdk.RestClient;
import org.matrix.androidsdk.rest.api.EventsApiV2;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.RestAdapterCallback;
import org.matrix.androidsdk.rest.model.SyncV2.SyncResponse;

import java.util.HashMap;

/**
 * Class used to make requests to the events API.
 */
public class EventsRestClientV2 extends RestClient<EventsApiV2> {
    public static final int EVENT_STREAM_TIMEOUT_SECONDS = 30;

    private static final String LOG_TAG = "EventsRestClientV2";

    /**
     * {@inheritDoc}
     */
    public EventsRestClientV2(HomeserverConnectionConfig hsConfig) {
        super(hsConfig, EventsApiV2.class, RestClient.URI_API_PREFIX_V2_ALPHA, false);
    }

    protected EventsRestClientV2(EventsApiV2 api) {
        mApi = api;
    }

    /**
     * Synchronise the client's state and receive new messages. Based on server sync C-S v2 API.

     * Synchronise the client's state with the latest state on the server.
     * Client's use this API when they first log in to get an initial snapshot
     * of the state on the server, and then continue to call this API to get
     * incremental deltas to the state, and to receive new messages.

     * @param token the token to stream from (nil in case of initial sync).
     * @param serverTimeout the maximum time in ms to wait for an event.
     * @param clientTimeout the maximum time in ms the SDK must wait for the server response.
     * @param setPresence  the optional parameter which controls whether the client is automatically
     * marked as online by polling this API. If this parameter is omitted then the client is
     * automatically marked as online when it uses this API. Otherwise if
     * the parameter is set to "offline" then the client is not marked as
     * being online when it uses this API.
     * @param filterId the ID of a filter created using the filter API (optional).
     * @param callback The request callback
     */
    public void syncFromToken(final String token, final int serverTimeout, final int clientTimeout, final String setPresence, final String filterId, final ApiCallback<SyncResponse> callback) {
        HashMap<String, Object> params = new HashMap<String, Object>();
        int timeout = EVENT_STREAM_TIMEOUT_SECONDS;

        if (!TextUtils.isEmpty(token)) {
            params.put("since", token);
        }

        if (-1 != serverTimeout) {
            timeout = serverTimeout;
        }

        if (!TextUtils.isEmpty(setPresence)) {
            params.put("set_presence", setPresence);
        }

        if (!TextUtils.isEmpty(filterId)) {
            params.put("filter", filterId);
        }

        params.put("timeout", timeout);


        final String description = "syncFromToken";

        // Disable retry because it interferes with clientTimeout
        // Let the client manage retries on events streams
        mApi.sync(params, new RestAdapterCallback<SyncResponse>(description, null, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                syncFromToken(token, serverTimeout, clientTimeout, setPresence, filterId, callback);
            }
        }));
    }
}
