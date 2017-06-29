/*
 * Copyright 2014 OpenMarket Ltd
 * Copyright 2017 Vector Creations Ltd
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
import org.matrix.androidsdk.rest.api.EventsApi;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.RestAdapterCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.PublicRoomsFilter;
import org.matrix.androidsdk.rest.model.PublicRoomsParams;
import org.matrix.androidsdk.rest.model.PublicRoomsResponse;
import org.matrix.androidsdk.rest.model.Search.SearchParams;
import org.matrix.androidsdk.rest.model.Search.SearchResponse;
import org.matrix.androidsdk.rest.model.Search.SearchRoomEventCategoryParams;
import org.matrix.androidsdk.rest.model.Sync.SyncResponse;
import org.matrix.androidsdk.rest.model.ThirdPartyProtocol;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Response;

/**
 * Class used to make requests to the events API.
 */
public class EventsRestClient extends RestClient<EventsApi> {

    private static final int EVENT_STREAM_TIMEOUT_MS = 30000;

    private String mSearchPatternIdentifier = null;
    private String mSearchMediaNameIdentifier = null;

    /**
     * {@inheritDoc}
     */
    public EventsRestClient(HomeserverConnectionConfig hsConfig) {
        super(hsConfig, EventsApi.class, "", false);
    }

    protected EventsRestClient(EventsApi api) {
        mApi = api;
    }

    /**
     * Retrieves the third party server protocols
     *
     * @param callback the asynchronous callback
     */
    public void getThirdPartyServerProtocols(final ApiCallback<Map<String, ThirdPartyProtocol>> callback) {
        final String description = "getThirdPartyServerProtocols";

        mApi.thirdpartyProtocols(new RestAdapterCallback<Map<String, ThirdPartyProtocol>>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                getThirdPartyServerProtocols(callback);
            }
        }));
    }

    /**
     * Get the public rooms count.
     * The count can be null.
     *
     * @param callback the public rooms count callbacks
     */
    public void getPublicRoomsCount(final ApiCallback<Integer> callback) {
        getPublicRoomsCount(null, null, false, callback);
    }

    /**
     * Get the public rooms count.
     * The count can be null.
     *
     * @param callback the asynchronous callback
     */
    public void getPublicRoomsCount(final String server, final ApiCallback<Integer> callback) {
        getPublicRoomsCount(server, null, false, callback);
    }

    /**
     * Get the public rooms count.
     * The count can be null.
     *
     * @param thirdPartyInstanceId the third party instance id (optional)
     * @param includeAllNetworks   true to search in all the connected network
     * @param callback             the asynchronous callback
     */
    public void getPublicRoomsCount(final String server, final String thirdPartyInstanceId, final boolean includeAllNetworks, final ApiCallback<Integer> callback) {
        loadPublicRooms(server, thirdPartyInstanceId, includeAllNetworks, null, null, 0, new ApiCallback<PublicRoomsResponse>() {
            @Override
            public void onSuccess(PublicRoomsResponse publicRoomsResponse) {
                callback.onSuccess(publicRoomsResponse.total_room_count_estimate);
            }

            @Override
            public void onNetworkError(Exception e) {
                callback.onNetworkError(e);
            }

            @Override
            public void onMatrixError(MatrixError e) {
                callback.onMatrixError(e);
            }

            @Override
            public void onUnexpectedError(Exception e) {
                callback.onUnexpectedError(e);
            }
        });
    }

    /**
     * Get the list of the public rooms.
     *
     * @param server               search on this home server only (null for any one)
     * @param thirdPartyInstanceId the third party instance id (optional)
     * @param includeAllNetworks   true to search in all the connected network
     * @param pattern              the pattern to search
     * @param since                the pagination token
     * @param limit                the maximum number of public rooms
     * @param callback             the public rooms callbacks
     */
    public void loadPublicRooms(final String server, final String thirdPartyInstanceId, final boolean includeAllNetworks, final String pattern, final String since, final int limit, final ApiCallback<PublicRoomsResponse> callback) {
        final String description = "loadPublicRooms";

        PublicRoomsParams publicRoomsParams = new PublicRoomsParams();

        publicRoomsParams.thirdPartyInstanceId = thirdPartyInstanceId;
        publicRoomsParams.includeAllNetworks = includeAllNetworks;
        publicRoomsParams.limit = Math.max(0, limit);
        publicRoomsParams.since = since;

        if (!TextUtils.isEmpty(pattern)) {
            publicRoomsParams.filter = new PublicRoomsFilter();
            publicRoomsParams.filter.generic_search_term = pattern;
        }

        mApi.publicRooms(publicRoomsParams).enqueue(new RestAdapterCallback<PublicRoomsResponse>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                loadPublicRooms(server, thirdPartyInstanceId, includeAllNetworks, pattern, since, limit, callback);
            }
        }));
    }

    /**
     * Synchronise the client's state and receive new messages. Based on server sync C-S v2 API.
     * <p>
     * Synchronise the client's state with the latest state on the server.
     * Client's use this API when they first log in to get an initial snapshot
     * of the state on the server, and then continue to call this API to get
     * incremental deltas to the state, and to receive new messages.
     *
     * @param token         the token to stream from (nil in case of initial sync).
     * @param serverTimeout the maximum time in ms to wait for an event.
     * @param clientTimeout the maximum time in ms the SDK must wait for the server response.
     * @param setPresence   the optional parameter which controls whether the client is automatically
     *                      marked as online by polling this API. If this parameter is omitted then the client is
     *                      automatically marked as online when it uses this API. Otherwise if
     *                      the parameter is set to "offline" then the client is not marked as
     *                      being online when it uses this API.
     * @param filterId      the ID of a filter created using the filter API (optional).
     * @param callback      The request callback
     */
    public void syncFromToken(final String token, final int serverTimeout, final int clientTimeout, final String setPresence, final String filterId, final ApiCallback<SyncResponse> callback) {
        HashMap<String, Object> params = new HashMap<>();
        int timeout = (EVENT_STREAM_TIMEOUT_MS / 1000);

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
        mApi.sync(params).enqueue(new RestAdapterCallback<SyncResponse>(description, null, false, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                syncFromToken(token, serverTimeout, clientTimeout, setPresence, filterId, callback);
            }
        }));
    }

    /**
     * Search a text in room messages.
     *
     * @param text        the text to search for.
     * @param rooms       a list of rooms to search in. nil means all rooms the user is in.
     * @param beforeLimit the number of events to get before the matching results.
     * @param afterLimit  the number of events to get after the matching results.
     * @param nextBatch   the token to pass for doing pagination from a previous response.
     * @param callback    the request callback
     */
    public void searchMessagesByText(final String text, final List<String> rooms, final int beforeLimit, final int afterLimit, final String nextBatch, final ApiCallback<SearchResponse> callback) {
        SearchParams searchParams = new SearchParams();
        SearchRoomEventCategoryParams searchEventParams = new SearchRoomEventCategoryParams();

        searchEventParams.search_term = text;
        searchEventParams.order_by = "recent";

        searchEventParams.event_context = new HashMap<>();
        searchEventParams.event_context.put("before_limit", beforeLimit);
        searchEventParams.event_context.put("after_limit", afterLimit);
        searchEventParams.event_context.put("include_profile", true);

        if (null != rooms) {
            searchEventParams.filter = new HashMap<>();
            searchEventParams.filter.put("rooms", rooms);
        }

        searchParams.search_categories = new HashMap<>();
        searchParams.search_categories.put("room_events", searchEventParams);

        final String description = "searchMessageText";

        final String uid = System.currentTimeMillis() + "";
        mSearchPatternIdentifier = uid + text;

        // don't retry to send the request
        // if the search fails, stop it
        mApi.search(searchParams, nextBatch).enqueue(new RestAdapterCallback<SearchResponse>(description, null, new ApiCallback<SearchResponse>() {
            /**
             * Tells if the current response for the latest request.
             * @return true if it is the response of the latest request.
             */
            private boolean isActiveRequest() {
                return TextUtils.equals(mSearchPatternIdentifier, uid + text);
            }

            @Override
            public void onSuccess(SearchResponse response) {
                if (isActiveRequest()) {
                    if (null != callback) {
                        callback.onSuccess(response);
                    }

                    mSearchPatternIdentifier = null;
                }
            }

            @Override
            public void onNetworkError(Exception e) {
                if (isActiveRequest()) {
                    if (null != callback) {
                        callback.onNetworkError(e);
                    }

                    mSearchPatternIdentifier = null;
                }
            }

            @Override
            public void onMatrixError(MatrixError e) {
                if (isActiveRequest()) {
                    if (null != callback) {
                        callback.onMatrixError(e);
                    }

                    mSearchPatternIdentifier = null;
                }
            }

            @Override
            public void onUnexpectedError(Exception e) {
                if (isActiveRequest()) {
                    if (null != callback) {
                        callback.onUnexpectedError(e);
                    }

                    mSearchPatternIdentifier = null;
                }
            }

        }, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                searchMessagesByText(text, rooms, beforeLimit, afterLimit, nextBatch, callback);
            }
        }));
    }

    /**
     * Search a media from its name.
     *
     * @param name        the text to search for.
     * @param rooms       a list of rooms to search in. nil means all rooms the user is in.
     * @param beforeLimit the number of events to get before the matching results.
     * @param afterLimit  the number of events to get after the matching results.
     * @param nextBatch   the token to pass for doing pagination from a previous response.
     * @param callback    the request callback
     */
    public void searchMediasByText(final String name, final List<String> rooms, final int beforeLimit, final int afterLimit, final String nextBatch, final ApiCallback<SearchResponse> callback) {
        SearchParams searchParams = new SearchParams();
        SearchRoomEventCategoryParams searchEventParams = new SearchRoomEventCategoryParams();

        searchEventParams.search_term = name;
        searchEventParams.order_by = "recent";

        searchEventParams.event_context = new HashMap<>();
        searchEventParams.event_context.put("before_limit", beforeLimit);
        searchEventParams.event_context.put("after_limit", afterLimit);
        searchEventParams.event_context.put("include_profile", true);

        searchEventParams.filter = new HashMap<>();

        if (null != rooms) {
            searchEventParams.filter.put("rooms", rooms);
        }

        ArrayList<String> types = new ArrayList<>();
        types.add(Event.EVENT_TYPE_MESSAGE);
        searchEventParams.filter.put("types", types);

        searchEventParams.filter.put("contains_url", true);

        searchParams.search_categories = new HashMap<>();
        searchParams.search_categories.put("room_events", searchEventParams);

        // other unused filter items
        // not_types
        // not_rooms
        // senders
        // not_senders

        final String uid = System.currentTimeMillis() + "";
        mSearchMediaNameIdentifier = uid + name;

        final String description = "searchMediasByText";

        // don't retry to send the request
        // if the search fails, stop it
        mApi.search(searchParams, nextBatch).enqueue(new RestAdapterCallback<SearchResponse>(description, null, new ApiCallback<SearchResponse>() {

            /**
             * Tells if the current response for the latest request.
             * @return true if it is the response of the latest request.
             */
            private boolean isActiveRequest() {
                return TextUtils.equals(mSearchMediaNameIdentifier, uid + name);
            }

            @Override
            public void onSuccess(SearchResponse newSearchResponse) {
                if (isActiveRequest()) {
                    callback.onSuccess(newSearchResponse);
                    mSearchMediaNameIdentifier = null;
                }
            }

            @Override
            public void onNetworkError(Exception e) {
                if (isActiveRequest()) {
                    callback.onNetworkError(e);
                    mSearchMediaNameIdentifier = null;
                }
            }

            @Override
            public void onMatrixError(MatrixError e) {
                if (isActiveRequest()) {
                    callback.onMatrixError(e);
                    mSearchMediaNameIdentifier = null;
                }
            }

            @Override
            public void onUnexpectedError(Exception e) {
                if (isActiveRequest()) {
                    callback.onUnexpectedError(e);
                    mSearchMediaNameIdentifier = null;
                }
            }

        }, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                searchMediasByText(name, rooms, beforeLimit, afterLimit, nextBatch, callback);
            }
        }));
    }

    /**
     * Cancel any pending file search request
     */
    public void cancelSearchMediasByText() {
        mSearchMediaNameIdentifier = null;
    }

    /**
     * Cancel any pending search request
     */
    public void cancelSearchMessagesByText() {
        mSearchPatternIdentifier = null;
    }
}
