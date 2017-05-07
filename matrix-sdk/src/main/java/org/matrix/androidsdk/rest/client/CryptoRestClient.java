/* 
 * Copyright 2016 OpenMarket Ltd
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

import org.matrix.androidsdk.rest.model.KeyChangesResponse;
import org.matrix.androidsdk.util.Log;

import org.matrix.androidsdk.HomeserverConnectionConfig;
import org.matrix.androidsdk.RestClient;
import org.matrix.androidsdk.crypto.data.MXKey;
import org.matrix.androidsdk.crypto.data.MXUsersDevicesMap;
import org.matrix.androidsdk.rest.api.CryptoApi;

import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.RestAdapterCallback;
import org.matrix.androidsdk.rest.model.DeleteDeviceParams;
import org.matrix.androidsdk.rest.model.DevicesListResponse;
import org.matrix.androidsdk.rest.model.crypto.KeysClaimResponse;
import org.matrix.androidsdk.rest.model.crypto.KeysQueryResponse;
import org.matrix.androidsdk.rest.model.crypto.KeysUploadResponse;
import org.matrix.androidsdk.util.JsonUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import retrofit2.Response;

public class CryptoRestClient extends RestClient<CryptoApi> {

    private static final String LOG_TAG = "CryptoRestClient";

    /**
     * {@inheritDoc}
     */
    public CryptoRestClient(HomeserverConnectionConfig hsConfig) {
        super(hsConfig, CryptoApi.class, URI_API_PREFIX_PATH_UNSTABLE, false, false);
    }

    /**
     * Upload device and/or one-time keys.
     * @param deviceKeys the device keys to send.
     * @param oneTimeKeys the one-time keys to send.
     * @param deviceId he explicit device_id to use for upload (default is to use the same as that used during auth).
     * @param callback the asynchronous callback
     */
    public void uploadKeys(final Map<String, Object> deviceKeys, final Map<String, Object> oneTimeKeys, final String deviceId, final ApiCallback<KeysUploadResponse> callback) {
        final String description = "uploadKeys";

        String encodedDeviceId = JsonUtils.convertToUTF8(deviceId);
        HashMap<String, Object> params = new HashMap<>();

        if (null != deviceKeys) {
            params.put("device_keys", deviceKeys);
        }

        if (null != oneTimeKeys) {
            params.put("one_time_keys", oneTimeKeys);
        }

        if (!TextUtils.isEmpty(encodedDeviceId)) {
            mApi.uploadKeys(encodedDeviceId, params).enqueue(new RestAdapterCallback<KeysUploadResponse>(description, null, callback, new RestAdapterCallback.RequestRetryCallBack() {
                @Override
                public void onRetry() {
                    try {
                        uploadKeys(deviceKeys, oneTimeKeys, deviceId, callback);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "resend uploadKeys : failed " + e.getMessage());
                    }
                }
            }));
        } else {
            mApi.uploadKeys(params).enqueue(new RestAdapterCallback<KeysUploadResponse>(description, null, callback, new RestAdapterCallback.RequestRetryCallBack() {
                @Override
                public void onRetry() {
                    try {
                        uploadKeys(deviceKeys, oneTimeKeys, deviceId, callback);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "resend uploadKeys : failed " + e.getMessage());
                    }
                }
            }));
        }
    }

    /**
     * Download device keys.
     * @param userIds list of users to get keys for.
     * @param token the up-to token
     * @param callback the asynchronous callback
     */
    public void downloadKeysForUsers(final List<String> userIds, final String token, final ApiCallback<KeysQueryResponse> callback) {
        final String description = "downloadKeysForUsers";

        HashMap<String, Map<String, Object>> downloadQuery = new HashMap<>();

        if (null != userIds) {
            for(String userId : userIds) {
                downloadQuery.put(userId, new HashMap<String, Object>());
            }
        }

        HashMap<String, Object> parameters = new HashMap<>();
        parameters.put("device_keys", downloadQuery);

        if (!TextUtils.isEmpty(token)) {
            parameters.put("token", token);
        }

        mApi.downloadKeysForUsers(parameters).enqueue(new RestAdapterCallback<KeysQueryResponse>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                try {
                    downloadKeysForUsers(userIds, token, callback);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "resend downloadKeysForUsers : failed " + e.getMessage());
                }
            }
        }));
    }

    /**
     * Claim one-time keys.
     * @param usersDevicesKeyTypesMap a list of users, devices and key types to retrieve keys for.
     * @param callback the asynchronous callback
     */
    public void claimOneTimeKeysForUsersDevices(final MXUsersDevicesMap<String> usersDevicesKeyTypesMap , final ApiCallback<MXUsersDevicesMap<MXKey>> callback) {
        final String description = "claimOneTimeKeysForUsersDevices";

        HashMap<String, Object> params = new HashMap<>();
        params.put("one_time_keys", usersDevicesKeyTypesMap.getMap());

        mApi.claimOneTimeKeysForUsersDevices(params).enqueue(new RestAdapterCallback<KeysClaimResponse>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                try {
                    claimOneTimeKeysForUsersDevices(usersDevicesKeyTypesMap, callback);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "resend claimOneTimeKeysForUsersDevices : failed " + e.getMessage());
                }
            }
        }) {
            @Override
            public void success(KeysClaimResponse keysClaimResponse, Response response) {
                onEventSent();

                HashMap<String, Map<String, MXKey>> map = new HashMap();

                if (null != keysClaimResponse.oneTimeKeys) {
                    for(String userId : keysClaimResponse.oneTimeKeys.keySet()) {
                        Map<String, Map<String, Map<String, Object>>> mapByUserId = keysClaimResponse.oneTimeKeys.get(userId);

                        HashMap<String, MXKey> keysMap = new HashMap<>();

                        for(String deviceId : mapByUserId.keySet()) {
                            try {
                                keysMap.put(deviceId, new MXKey(mapByUserId.get(deviceId)));
                            } catch (Exception e) {
                                Log.e(LOG_TAG, "## claimOneTimeKeysForUsersDevices : fail to create a MXKey " + e.getMessage());
                            }
                        }

                        if (keysMap.size() != 0) {
                            map.put(userId, keysMap);
                        }
                    }
                }

                callback.onSuccess(new MXUsersDevicesMap<>(map));
            }
        });
    }

    /**
     * Send an event to a specific list of devices
     * @param eventType the type of event to send
     * @param contentMap content to send. Map from user_id to device_id to content dictionary.
     * @param callback the asynchronous callback.
     */
    public void sendToDevice(final String eventType, final MXUsersDevicesMap<Map<String, Object>> contentMap, final ApiCallback<Void> callback) {
        final String description = "sendToDevice " + eventType;

        HashMap<String, Object> content = new HashMap<>();
        content.put("messages", contentMap.getMap());

        Random rand = new Random();

        mApi.sendToDevice(eventType, rand.nextInt(Integer.MAX_VALUE), content).enqueue(new RestAdapterCallback<Void>(description, null, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                sendToDevice(eventType, contentMap, callback);
            }
        }));
    }

    /**
     * Retrieves the devices informaty
     * @param callback the asynchronous callback.
     */
    public void getDevices(final ApiCallback<DevicesListResponse> callback) {
        final String description = "getDevicesListInfo";

        mApi.getDevices().enqueue(new RestAdapterCallback<DevicesListResponse>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                try {
                    getDevices(callback);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "resend getDevices : failed " + e.getMessage());
                }
            }
        }));
    }

    /**
     * Delete a device.
     * @param deviceId the device id
     * @param params the deletion parameters
     * @param callback the asynchronous callback
     */
    public void deleteDevice(final String deviceId, final DeleteDeviceParams params, final ApiCallback<Void> callback) {
        final String description = "deleteDevice";

        mApi.deleteDevice(deviceId, params).enqueue(new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                try {
                    deleteDevice(deviceId, params, callback);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "resend deleteDevice : failed " + e.getMessage());
                }
            }
        }));
    }

    /**
     * Set a device name.
     * @param deviceId the device id
     * @param deviceName the device name
     * @param callback the asynchronous callback
     */
    public void setDeviceName(final String deviceId, final String deviceName, final ApiCallback<Void> callback) {
        final String description = "setDeviceName";

        HashMap<String, String> params = new HashMap<>();
        params.put("display_name", TextUtils.isEmpty(deviceName) ? "" : deviceName);

        mApi.updateDeviceInfo(deviceId, params).enqueue(new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                try {
                    setDeviceName(deviceId, deviceName, callback);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "resend setDeviceName : failed " + e.getMessage());
                }
            }
        }));
    }

    /**
     * Get the update devices list from two sync token.
     * @param from the start token.
     * @param to the up-to token.
     * @param callback the asynchronous callback
     */
    public void getKeyChanges(final String from, final String to, final ApiCallback<KeyChangesResponse> callback) {
        final String description = "getKeyChanges";

        mApi.getKeyChanges(from, to).enqueue(new RestAdapterCallback<KeyChangesResponse>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                try {
                    getKeyChanges(from, to, callback);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "resend getKeyChanges : failed " + e.getMessage());
                }
            }
        }));
    }
}
