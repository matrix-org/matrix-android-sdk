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

import android.net.Uri;
import android.util.Log;

import org.matrix.androidsdk.RestClient;
import org.matrix.androidsdk.listeners.IMXNetworkEventListener;
import org.matrix.androidsdk.network.NetworkConnectivityReceiver;
import org.matrix.androidsdk.rest.api.EventsApi;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.RestAdapterCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.InitialSyncResponse;
import org.matrix.androidsdk.rest.model.PublicRoom;
import org.matrix.androidsdk.rest.model.TokensChunkResponse;
import org.matrix.androidsdk.rest.model.login.Credentials;

import java.util.List;

import retrofit.RestAdapter;
import retrofit.client.Response;

/**
 * Class used to make requests to the events API.
 */
public class EventsRestClient extends RestClient<EventsApi> {

    protected static final int EVENT_STREAM_TIMEOUT_MS = 30000;

    /**
     * {@inheritDoc}
     */
    public EventsRestClient(Credentials credentials) {
        super(credentials, EventsApi.class, RestClient.URI_API_PREFIX);
    }

    protected EventsRestClient(EventsApi api) {
        mApi = api;
    }

    /**
     * Get the list of the home server's public rooms.
     * @param callback callback to provide the list of public rooms on success
     */
    public void loadPublicRooms(final ApiCallback<List<PublicRoom>> callback) {
        mApi.publicRooms(new RestAdapterCallback<TokensChunkResponse<PublicRoom>>(mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                loadPublicRooms(callback);
            }
        }) {
            @Override
            public void success(TokensChunkResponse<PublicRoom> typedResponse, Response response) {
                callback.onSuccess(typedResponse.chunk);
            }
        });
    }

    /**
     * Get initial information about the user's rooms, messages, other users.
     * @param callback callback to provide the information
     */
    public void initialSync(final ApiCallback<InitialSyncResponse> callback) {
        mApi.initialSync(10, new RestAdapterCallback<InitialSyncResponse>(mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                initialSync(callback);
            }
        }));
    }

    /**
     * {@link #events(String, int)} with a default timeout.
     * @param fromToken the token provided by the previous call's response
     * @return a list of events
     */
    public TokensChunkResponse<Event> events(String fromToken) {
        return events(fromToken, EVENT_STREAM_TIMEOUT_MS);
    }

    /**
     * Long poll for the next events. To be called repeatedly to listen to the events stream.
     * @param fromToken the token provided by the previous call's response
     * @param timeoutMs max time before the server sends a response
     * @return a list of events
     */
    public TokensChunkResponse<Event> events(String fromToken, int timeoutMs) {
        return mApi.events(fromToken, timeoutMs);
    }
}
