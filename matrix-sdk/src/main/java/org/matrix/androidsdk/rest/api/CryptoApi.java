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

import retrofit.Callback;
import retrofit.http.Body;
import retrofit.http.GET;
import retrofit.http.POST;
import retrofit.http.PUT;
import retrofit.http.Path;

import org.matrix.androidsdk.rest.model.DevicesListResponse;
import org.matrix.androidsdk.rest.model.crypto.KeysClaimResponse;
import org.matrix.androidsdk.rest.model.crypto.KeysQueryResponse;
import org.matrix.androidsdk.rest.model.crypto.KeysUploadResponse;

import java.util.Map;

public interface CryptoApi {

    /**
     * Upload device and/or one-time keys.
     * @param params the params.
     * @param callback the asynchronous callback
     */
    @POST("/keys/upload")
    void uploadKeys(@Body Map<String, Object> params, Callback<KeysUploadResponse> callback);

    /**
     * Upload device and/or one-time keys.
     * @param deviceId the deviceId
     * @param params the params.
     * @param callback the asynchronous callback
     */
    @POST("/keys/upload/{deviceId}")
    void uploadKeys(@Path("deviceId") String deviceId, @Body Map<String, Object> params, Callback<KeysUploadResponse> callback);

    /**
     * Download device keys.
     * @param params the params.
     * @param callback the asynchronous callback
     */
    @POST("/keys/query")
    void downloadKeysForUsers(@Body Map<String, Object> params, Callback<KeysQueryResponse> callback);

    /**
     * Claim one-time keys.
     * @param params the params.
     * @param callback the asynchronous callback
     */
    @POST("/keys/claim")
    void claimOneTimeKeysForUsersDevices(@Body Map<String, Object> params, Callback<KeysClaimResponse> callback);

    /**
     * Send an event to a specific list of devices
     * @param eventType the type of event to send
     * @param randomTransactionId the random path item
     * @param params the params
     * @param callback the asynchronous callback
     */
    @PUT("/sendToDevice/{eventType}/{random}")
    void sendToDevice(@Path("eventType") String eventType, @Path("random") int randomTransactionId, @Body Map<String, Object> params, Callback<Void> callback);


    /**
     * Get the devices list
     */
    @GET("/devices")
    void getDevices(Callback<DevicesListResponse> callback);

}
