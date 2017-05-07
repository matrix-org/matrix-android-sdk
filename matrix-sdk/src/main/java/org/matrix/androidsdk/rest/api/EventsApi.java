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
package org.matrix.androidsdk.rest.api;

import org.matrix.androidsdk.rest.model.PublicRoomsParams;
import org.matrix.androidsdk.rest.model.PublicRoomsResponse;
import org.matrix.androidsdk.rest.model.Search.SearchParams;
import org.matrix.androidsdk.rest.model.Search.SearchResponse;
import org.matrix.androidsdk.rest.model.Sync.SyncResponse;

import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;
import retrofit2.http.QueryMap;

/**
 * The events API.
 */
public interface EventsApi {
    /**
     * Perform the initial sync to find the rooms that concern the user, the participants' presence, etc.
     * @param params the GET params.
     */
    @GET("/sync")
    Call<SyncResponse> sync(@QueryMap Map<String, Object> params);

    /**
     * Get the list of public rooms.
     * @param publicRoomsParams the request params
     */
    @POST("/publicRooms")
    Call<PublicRoomsResponse> publicRooms(@Body PublicRoomsParams publicRoomsParams);

    /**
     * Perform a search.
     * @param searchParams the search params.
     */
    @POST("/search")
    Call<SearchResponse> search(@Body SearchParams searchParams, @Query("next_batch") String nextBatch);
}
