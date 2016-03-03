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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.matrix.androidsdk.HomeserverConnectionConfig;
import org.matrix.androidsdk.RestClient;
import org.matrix.androidsdk.rest.api.EventsApi;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.RestAdapterCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.InitialSyncResponse;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.Message;
import org.matrix.androidsdk.rest.model.PublicRoom;
import org.matrix.androidsdk.rest.model.Search.SearchEventContext;
import org.matrix.androidsdk.rest.model.Search.SearchGroup;
import org.matrix.androidsdk.rest.model.Search.SearchParams;
import org.matrix.androidsdk.rest.model.Search.SearchResponse;
import org.matrix.androidsdk.rest.model.Search.SearchResult;
import org.matrix.androidsdk.rest.model.Search.SearchRoomEventCategoryParams;
import org.matrix.androidsdk.rest.model.Search.SearchRoomEventResults;
import org.matrix.androidsdk.rest.model.TokensChunkResponse;
import org.w3c.dom.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import retrofit.client.Response;

/**
 * Class used to make requests to the events API.
 */
public class EventsRestClient extends RestClient<EventsApi> {

    public static final int EVENT_STREAM_TIMEOUT_MS = 30000;

    private String mSearchPattern = null;
    private String mSearchMediaName = null;

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
     *
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
     *
     * @param callback callback to provide the information
     */
    public void initialSync(final ApiCallback<InitialSyncResponse> callback) {
        initialSyncWithLimit(callback, 10);
    }

    /**
     * Get initial information about the user's rooms, messages, other users.
     *
     * @param callback callback to provide the information
     * @param limit    the number of messages per room
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
     *
     * @param fromToken the token provided by the previous call's response
     * @return a list of events
     */
    public TokensChunkResponse<Event> events(String fromToken) {
        return events(fromToken, EVENT_STREAM_TIMEOUT_MS);
    }

    /**
     * Long poll for the next events. To be called repeatedly to listen to the events stream.
     *
     * @param fromToken the token provided by the previous call's response
     * @param timeoutMs max time before the server sends a response
     * @return a list of events
     */
    public TokensChunkResponse<Event> events(String fromToken, int timeoutMs) {
        return mApi.events(fromToken, timeoutMs);
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

        mSearchPattern = text;

        // don't retry to send the request
        // if the search fails, stop it
        mApi.search(searchParams, nextBatch, new RestAdapterCallback<SearchResponse>(description, null, new ApiCallback<SearchResponse>() {
            @Override
            public void onSuccess(SearchResponse response) {
                if (TextUtils.equals(mSearchPattern, text)) {
                    if (null != callback) {
                        callback.onSuccess(response);
                    }

                    mSearchPattern = null;
                }
            }

            @Override
            public void onNetworkError(Exception e) {
                if (TextUtils.equals(mSearchPattern, text)) {
                    if (null != callback) {
                        callback.onNetworkError(e);
                    }

                    mSearchPattern = null;
                }
            }

            @Override
            public void onMatrixError(MatrixError e) {
                if (TextUtils.equals(mSearchPattern, text)) {
                    if (null != callback) {
                        callback.onMatrixError(e);
                    }

                    mSearchPattern = null;
                }
            }

            @Override
            public void onUnexpectedError(Exception e) {
                if (TextUtils.equals(mSearchPattern, text)) {
                    if (null != callback) {
                        callback.onUnexpectedError(e);
                    }

                    mSearchPattern = null;
                }
            }

        }, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                searchMessageText(text, rooms, beforeLimit, afterLimit, nextBatch, callback);
            }
        }));
    }

    /**
     * Search a media from its name.
     *
     * @param name          the text to search for.
     * @param rooms         a list of rooms to search in. nil means all rooms the user is in.
     * @param messageTypes  a list of medias type (m.image, m.video...)
     * @param beforeLimit   the number of events to get before the matching results.
     * @param afterLimit    the number of events to get after the matching results.
     * @param nextBatch     the token to pass for doing pagination from a previous response.
     * @param callback      the request callback
     */
    public void searchMediaName(final String name, final List<String> rooms, final List<String> messageTypes, final int beforeLimit, final int afterLimit, String nextBatch, final ApiCallback<SearchResponse> callback) {
        mediaSearch(null, name, rooms, messageTypes, beforeLimit, afterLimit, nextBatch, callback);
    }

    /**
     * Recursive method to search a media by its name.
     * It does not seem to have a msgtype filter in the current Server APi.
     * So, the reponses are merged until to find at least 10 medias or there is no more message
     * @param response      the recursive response.
     * @param name          the file name to search
     * @param rooms         the rooms list to search in
     * @param messageTypes  the supported media types.
     * @param beforeLimit   the number of events to get before the matching results.
     * @param afterLimit    the number of events to get after the matching results.
     * @param nextBatch     the token to pass for doing pagination from a previous response.
     * @param callback      the request callback
     */
    private void mediaSearch(final SearchResponse response, final String name, final List<String> rooms, final List<String> messageTypes, final int beforeLimit, final int afterLimit, final String nextBatch, final ApiCallback<SearchResponse> callback) {

        SearchParams searchParams = new SearchParams();
        SearchRoomEventCategoryParams searchEventParams = new SearchRoomEventCategoryParams();

        searchEventParams.search_term = name;
        searchEventParams.order_by = "recent";

        searchEventParams.event_context = new HashMap<String, Object>();
        searchEventParams.event_context.put("before_limit", beforeLimit);
        searchEventParams.event_context.put("after_limit", afterLimit);
        searchEventParams.event_context.put("include_profile", true);

        searchEventParams.filter = new HashMap<String, Object>();

        if (null != rooms) {
            searchEventParams.filter.put("rooms", rooms);
        }

        ArrayList<String> types = new ArrayList<String>();
        types.add("m.room.message");
        searchEventParams.filter.put("types", rooms);

        searchParams.search_categories = new HashMap<String, Object>();
        searchParams.search_categories.put("room_events", searchEventParams);

        mSearchMediaName = name;

        final String description = "mediaSearch";

        // don't retry to send the request
        // if the search fails, stop it
        mApi.search(searchParams, nextBatch, new RestAdapterCallback<SearchResponse>(description, null, new ApiCallback<SearchResponse>() {
            @Override
            public void onSuccess(SearchResponse newSearchResponse) {
                if (TextUtils.equals(mSearchMediaName, name)) {
                    // no more message with the pattern
                    if ((null == newSearchResponse.searchCategories.roomEvents.results)
                            || (0 ==newSearchResponse.searchCategories.roomEvents.results.size())) {
                        callback.onSuccess(response);
                        mSearchMediaName = null;
                    } else {

                        // merge the responses
                        SearchResponse mergedResponse = mergeAndFilterResponse(response, newSearchResponse, messageTypes);

                        // at least matched event ?
                        if (mergedResponse.searchCategories.roomEvents.results.size() >= 10) {
                            // weel done
                            callback.onSuccess(mergedResponse);
                            mSearchMediaName = null;
                        } else {
                            // search again
                            mediaSearch(mergedResponse, name, rooms, messageTypes, beforeLimit, afterLimit, mergedResponse.searchCategories.roomEvents.nextBatch, callback);
                        }
                    }
                }
            }

            @Override
            public void onNetworkError(Exception e) {
                if (TextUtils.equals(mSearchMediaName, name)) {
                    callback.onNetworkError(e);
                    mSearchMediaName = null;
                }
            }

            @Override
            public void onMatrixError(MatrixError e) {
                if (TextUtils.equals(mSearchMediaName, name)) {
                    callback.onMatrixError(e);
                    mSearchMediaName = null;
                }
            }

            @Override
            public void onUnexpectedError(Exception e) {
                if (TextUtils.equals(mSearchMediaName, name)) {
                    callback.onUnexpectedError(e);
                    mSearchMediaName = null;
                }
            }

        }, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                mediaSearch(response, name, rooms, messageTypes, beforeLimit, afterLimit, nextBatch, callback);
            }
        }));
    }

    /**
     *
     * @param response
     * @param responseToMerge
     * @param supportedMediasList
     * @return
     */
    private SearchResponse mergeAndFilterResponse(SearchResponse response, SearchResponse responseToMerge, List<String> supportedMediasList) {
        SearchRoomEventResults roomEventsToMerge = responseToMerge.searchCategories.roomEvents;

        // filter first by media type ?
        if (responseToMerge.searchCategories.roomEvents.results.size() > 0) {
            // check first if the message
            ArrayList<SearchResult> filteredResultList = new ArrayList<SearchResult>();

            for(SearchResult result : roomEventsToMerge.results) {
                boolean isSupported = false;

                Event event = result.result;

                // should always be true
                if (Event.EVENT_TYPE_MESSAGE.equals(event.type)) {
                    JsonObject eventContent = event.getContentAsJsonObject();
                    String msgType = "";

                    JsonElement element = eventContent.get("msgtype");

                    if (null != element) {
                        msgType = element.getAsString();
                    }

                    if (!TextUtils.isEmpty(msgType)) {
                        isSupported = supportedMediasList.indexOf(msgType) >= 0;
                    }
                }

                if (isSupported) {
                    filteredResultList.add(result);
                }
                responseToMerge.searchCategories.roomEvents.results = filteredResultList;
            }

            if (null != response) {
                // merge the events
                ArrayList<SearchResult> searchResults = new ArrayList<SearchResult>();
                searchResults.addAll(response.searchCategories.roomEvents.results);
                searchResults.addAll(responseToMerge.searchCategories.roomEvents.results);
                responseToMerge.searchCategories.roomEvents.results = searchResults;

                // merge the states
                HashMap<String, List<Event>> states = response.searchCategories.roomEvents.state;
                HashMap<String, List<Event>> statesToMerge = responseToMerge.searchCategories.roomEvents.state;

                if ((null != states) && (null != statesToMerge)) {
                    for (String key : states.keySet()) {
                        if (!statesToMerge.containsKey(statesToMerge)) {
                            statesToMerge.put(key, states.get(key));
                        }
                    }
                } else if (null == statesToMerge) {
                    responseToMerge.searchCategories.roomEvents.state = response.searchCategories.roomEvents.state;
                }
            }
        }

        responseToMerge.searchCategories.roomEvents.count = null;
        responseToMerge.searchCategories.roomEvents.groups = null;

        return responseToMerge;
    }

    /**
     * Cancel any pending file search request
     */
    public void cancelSearchMediaName() {
        mSearchMediaName = null;
    }

    /**
     * Cancel any pending search request
     */
    public void cancelSearchMessageText() {
        mSearchPattern = null;
    }
}
