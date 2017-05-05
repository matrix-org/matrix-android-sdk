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

import retrofit.Callback;
import retrofit.http.Body;
import retrofit.http.GET;
import retrofit.http.POST;
import retrofit.http.PUT;
import retrofit.http.Path;
import retrofit.http.Query;
import retrofit2.http.HTTP;

import org.matrix.androidsdk.rest.model.ChangePasswordParams;
import org.matrix.androidsdk.rest.model.DeleteDeviceAuth;
import org.matrix.androidsdk.rest.model.DeleteDeviceParams;
import org.matrix.androidsdk.rest.model.DevicesListResponse;
import org.matrix.androidsdk.rest.model.KeyChangesResponse;
import org.matrix.androidsdk.rest.model.PidResponse;
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

    /**
     * Delete a device.
     * @param deviceId the device id
     * @param params the deletion parameters
     * @param callback the callback
     */
    @HTTP(path = "/devices/{device_id}", method = "DELETE", hasBody = true)
    void deleteDevice(@Path("device_id")String deviceId, @Body DeleteDeviceParams params, Callback<Void> callback);

    /**
     * Update the device information.
     * @param deviceId the device id
     * @param params the params
     * @param callback the asynchronous callback
     */
    @PUT("/devices/{device_id}")
    void updateDeviceInfo(@Path("device_id")String deviceId, @Body Map<String, String> params, Callback<Void> callback);

    /**
     * Get the update devices list from two sync token.
     * @param oldToken the start token.
     * @param newToken the up-to token.
     * @param callback the asynchronous callback
     */
    @GET("/keys/changes")
    void getKeyChanges(@Query("from") String oldToken, @Query("to") String newToken, Callback<KeyChangesResponse> callback);
}
