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

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.json.JSONObject;
import org.matrix.androidsdk.HomeserverConnectionConfig;
import org.matrix.androidsdk.RestClient;
import org.matrix.androidsdk.rest.api.EventsApi;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.RestAdapterCallback;
import org.matrix.androidsdk.rest.json.ConditionDeserializer;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.InitialSyncResponse;
import org.matrix.androidsdk.rest.model.PublicRoom;
import org.matrix.androidsdk.rest.model.Search.SearchParams;
import org.matrix.androidsdk.rest.model.Search.SearchResponse;
import org.matrix.androidsdk.rest.model.Search.SearchResult;
import org.matrix.androidsdk.rest.model.Search.SearchRoomEventCategoryParams;
import org.matrix.androidsdk.rest.model.TokensChunkResponse;
import org.matrix.androidsdk.rest.model.bingrules.Condition;

import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import retrofit.client.Response;

/**
 * Class used to make requests to the events API.
 */
public class EventsRestClient extends RestClient<EventsApi> {

    public static final int EVENT_STREAM_TIMEOUT_MS = 30000;

    /**
     * {@inheritDoc}
     */
    public EventsRestClient(HomeserverConnectionConfig hsConfig) {
        super(hsConfig, EventsApi.class, RestClient.URI_API_PREFIX, false);
    }

    protected EventsRestClient(EventsApi api) {
        mApi = api;
    }

    /**
     * Get the list of the home server's public rooms.
     * @param callback callback to provide the list of public rooms on success
     */
    public void loadPublicRooms(final ApiCallback<List<PublicRoom>> callback) {
        final String description = "loadPublicRooms";

        mApi.publicRooms(new RestAdapterCallback<TokensChunkResponse<PublicRoom>>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
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
        initialSyncWithLimit(callback, 10);
    }

    /**
     * Get initial information about the user's rooms, messages, other users.
     * @param callback callback to provide the information
     * @param limit the number of messages per room
     */
    public void initialSyncWithLimit(final ApiCallback<InitialSyncResponse> callback, final int limit) {
        final String description = "initialSyncWithLimit";

        mApi.initialSync(limit, new RestAdapterCallback<InitialSyncResponse>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                initialSyncWithLimit(callback, limit);
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

    /**
     * Search a text in room messages.
     * @param text the text to search for.
     * @param rooms a list of rooms to search in. nil means all rooms the user is in.
     * @param beforeLimit the number of events to get before the matching results.
     * @param afterLimit the number of events to get after the matching results.
     * @param nextBatch the token to pass for doing pagination from a previous response.
     * @param callback the request callback
     */
     public void searchMessageText(final String text, final List<String> rooms, final int beforeLimit, final int afterLimit, final String nextBatch, final ApiCallback<SearchResponse> callback) {
         SearchParams searchParams = new SearchParams();
         SearchRoomEventCategoryParams searchEventParams = new SearchRoomEventCategoryParams();

         searchEventParams.search_term = text;
         searchEventParams.order_by = "recent";

         searchEventParams.event_context = new HashMap<String, Object>();
         searchEventParams.event_context.put("before_limit", beforeLimit);
         searchEventParams.event_context.put("after_limit", afterLimit);
         searchEventParams.event_context.put("include_profile", true);

         if (null != rooms) {
             searchEventParams.filter = new HashMap<String, Object>();
             searchEventParams.filter.put("rooms", rooms);
         }

         searchParams.search_categories = new HashMap<String, Object>();
         searchParams.search_categories.put("room_events", searchEventParams);

         final String description = "searchMessageText";

         // don't retry to send the request
         // if the search fails, stop it
         mApi.search(searchParams, nextBatch, new RestAdapterCallback<SearchResponse>(description, null, callback, new RestAdapterCallback.RequestRetryCallBack() {
             @Override
             public void onRetry() {
                 searchMessageText(text, rooms, beforeLimit, afterLimit, nextBatch, callback);
             }
         }));
     }
}
