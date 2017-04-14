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
package org.matrix.androidsdk.rest.api;

import org.matrix.androidsdk.RestClient;
import org.matrix.androidsdk.rest.model.PublicRoomsParams;
import org.matrix.androidsdk.rest.model.PublicRoomsResponse;
import org.matrix.androidsdk.rest.model.Search.SearchParams;
import org.matrix.androidsdk.rest.model.Search.SearchResponse;
import org.matrix.androidsdk.rest.model.Sync.SyncResponse;
import org.matrix.androidsdk.rest.model.ThirdPartyProtocol;

import java.util.Map;

import retrofit.Callback;
import retrofit.http.Body;
import retrofit.http.GET;
import retrofit.http.POST;
import retrofit.http.Query;
import retrofit.http.QueryMap;

/**
 * The events API.
 */
public interface EventsApi {

    /**
     * Perform the initial sync to find the rooms that concern the user, the participants' presence, etc.
     *
     * @param params   the GET params.
     * @param callback The asynchronous callback to call when finished
     */
    @GET(RestClient.URI_API_PREFIX_PATH_R0 + "/sync")
    void sync(@QueryMap Map<String, Object> params, Callback<SyncResponse> callback);


    /**
     * Get the third party server protocols.
     *
     * @param callback The asynchronous callback to call when finished
     */
    @GET(RestClient.URI_API_PREFIX_PATH_UNSTABLE + "/thirdparty/protocols")
    void thirdpartyProtocols(Callback<Map<String, ThirdPartyProtocol>> callback);

    /**
     * Get the list of public rooms.
     *
     * @param server            the server (might be null)
     * @param publicRoomsParams the request params
     * @param callback          The asynchronous callback to call when finished
     */
    @POST(RestClient.URI_API_PREFIX_PATH_R0 + "/publicRooms")
    void publicRooms(@Query("server") String server, @Body PublicRoomsParams publicRoomsParams, Callback<PublicRoomsResponse> callback);

    /**
     * Perform a search.
     *
     * @param searchParams the search params.
     * @param callback     The search result.
     */
    @POST(RestClient.URI_API_PREFIX_PATH_R0 + "/search")
    void search(@Body SearchParams searchParams, @Query("next_batch") String nextBatch, Callback<SearchResponse> callback);
}
