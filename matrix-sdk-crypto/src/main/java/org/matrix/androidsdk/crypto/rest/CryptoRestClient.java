/*
 * Copyright 2016 OpenMarket Ltd
 * Copyright 2017 Vector Creations Ltd
 * Copyright 2018 New Vector Ltd
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
package org.matrix.androidsdk.crypto.rest;

import android.text.TextUtils;

import org.matrix.androidsdk.HomeServerConnectionConfig;
import org.matrix.androidsdk.RestClient;
import org.matrix.androidsdk.core.Log;
import org.matrix.androidsdk.core.StringUtilsKt;
import org.matrix.androidsdk.core.callback.ApiCallback;
import org.matrix.androidsdk.crypto.api.CryptoApi;
import org.matrix.androidsdk.crypto.data.MXKey;
import org.matrix.androidsdk.crypto.data.MXUsersDevicesMap;
import org.matrix.androidsdk.crypto.model.crypto.KeyChangesResponse;
import org.matrix.androidsdk.crypto.model.crypto.KeysClaimResponse;
import org.matrix.androidsdk.crypto.model.crypto.KeysQueryResponse;
import org.matrix.androidsdk.crypto.model.crypto.KeysUploadResponse;
import org.matrix.androidsdk.crypto.model.rest.DeleteDeviceParams;
import org.matrix.androidsdk.crypto.model.rest.DevicesListResponse;
import org.matrix.androidsdk.crypto.rest.model.crypto.SendToDeviceBody;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import retrofit2.Response;

public class CryptoRestClient extends RestClient<CryptoApi> {

    private static final String LOG_TAG = CryptoRestClient.class.getSimpleName();

    /**
     * {@inheritDoc}
     */
    public CryptoRestClient(HomeServerConnectionConfig homeServerConnectionConfig) {
        super(homeServerConnectionConfig, CryptoApi.class, URI_API_PREFIX_PATH_UNSTABLE);
    }

    /**
     * Upload device and/or one-time keys.
     *
     * @param deviceKeys  the device keys to send.
     * @param oneTimeKeys the one-time keys to send.
     * @param deviceId    he explicit device_id to use for upload (default is to use the same as that used during auth).
     * @param callback    the asynchronous callback
     */
    public void uploadKeys(final Map<String, ?> deviceKeys,
                           final Map<String, ?> oneTimeKeys,
                           final String deviceId,
                           final ApiCallback<KeysUploadResponse> callback) {
        final String description = "uploadKeys";

        String encodedDeviceId = StringUtilsKt.convertToUTF8(deviceId);
        Map<String, Object> params = new HashMap<>();

        if (null != deviceKeys) {
            params.put("device_keys", deviceKeys);
        }

        if (null != oneTimeKeys) {
            params.put("one_time_keys", oneTimeKeys);
        }

        if (!TextUtils.isEmpty(encodedDeviceId)) {
            mApi.uploadKeys(encodedDeviceId, params)
                    .enqueue(new CryptoRestAdapterCallback<KeysUploadResponse>(description, callback,
                            new CryptoRestAdapterCallback.RequestRetryCallBack() {
                                @Override
                                public void onRetry() {
                                    uploadKeys(deviceKeys, oneTimeKeys, deviceId, callback);
                                }
                            }));
        } else {
            mApi.uploadKeys(params)
                    .enqueue(new CryptoRestAdapterCallback<KeysUploadResponse>(description, callback,
                            new CryptoRestAdapterCallback.RequestRetryCallBack() {
                                @Override
                                public void onRetry() {
                                    uploadKeys(deviceKeys, oneTimeKeys, deviceId, callback);
                                }
                            }));
        }
    }

    /**
     * Download device keys.
     *
     * @param userIds  list of users to get keys for.
     * @param token    the up-to token
     * @param callback the asynchronous callback
     */
    public void downloadKeysForUsers(final List<String> userIds, final String token, final ApiCallback<KeysQueryResponse> callback) {
        final String description = "downloadKeysForUsers";

        Map<String, Map<String, Object>> downloadQuery = new HashMap<>();

        if (null != userIds) {
            for (String userId : userIds) {
                downloadQuery.put(userId, new HashMap<String, Object>());
            }
        }

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("device_keys", downloadQuery);

        if (!TextUtils.isEmpty(token)) {
            parameters.put("token", token);
        }

        mApi.downloadKeysForUsers(parameters)
                .enqueue(new CryptoRestAdapterCallback<KeysQueryResponse>(description, callback,
                        new CryptoRestAdapterCallback.RequestRetryCallBack() {
                            @Override
                            public void onRetry() {
                                downloadKeysForUsers(userIds, token, callback);
                            }
                        }));
    }

    /**
     * Claim one-time keys.
     *
     * @param usersDevicesKeyTypesMap a list of users, devices and key types to retrieve keys for.
     * @param callback                the asynchronous callback
     */
    public void claimOneTimeKeysForUsersDevices(
            final MXUsersDevicesMap<String> usersDevicesKeyTypesMap,
            final ApiCallback<MXUsersDevicesMap<MXKey>> callback) {
        final String description = "claimOneTimeKeysForUsersDevices";

        Map<String, Object> params = new HashMap<>();
        params.put("one_time_keys", usersDevicesKeyTypesMap.getMap());

        mApi.claimOneTimeKeysForUsersDevices(params)
                .enqueue(new CryptoRestAdapterCallback<KeysClaimResponse>(description, callback,
                        new CryptoRestAdapterCallback.RequestRetryCallBack() {
                            @Override
                            public void onRetry() {
                                claimOneTimeKeysForUsersDevices(usersDevicesKeyTypesMap, callback);
                            }
                        }) {
                    @Override
                    public void success(KeysClaimResponse keysClaimResponse, Response response) {
                        Map<String, Map<String, MXKey>> map = new HashMap<>();

                        if (null != keysClaimResponse.oneTimeKeys) {
                            for (String userId : keysClaimResponse.oneTimeKeys.keySet()) {
                                Map<String, Map<String, Map<String, Object>>> mapByUserId = keysClaimResponse.oneTimeKeys.get(userId);

                                Map<String, MXKey> keysMap = new HashMap<>();

                                for (String deviceId : mapByUserId.keySet()) {
                                    try {
                                        keysMap.put(deviceId, new MXKey(mapByUserId.get(deviceId)));
                                    } catch (Exception e) {
                                        Log.e(LOG_TAG, "## claimOneTimeKeysForUsersDevices : fail to create a MXKey " + e.getMessage(), e);
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
     *
     * @param eventType  the type of event to send
     * @param contentMap content to send. Map from user_id to device_id to content dictionary.
     * @param callback   the asynchronous callback.
     */
    public void sendToDevice(final String eventType,
                             final MXUsersDevicesMap<Object> contentMap,
                             final ApiCallback<Void> callback) {
        sendToDevice(eventType, contentMap, (new Random()).nextInt(Integer.MAX_VALUE) + "", callback);
    }

    /**
     * Send an event to a specific list of devices
     *
     * @param eventType     the type of event to send
     * @param contentMap    content to send. Map from user_id to device_id to content dictionary.
     * @param transactionId the transactionId
     * @param callback      the asynchronous callback.
     */
    public void sendToDevice(final String eventType,
                             final MXUsersDevicesMap<Object> contentMap,
                             final String transactionId,
                             final ApiCallback<Void> callback) {
        final String description = "sendToDevice " + eventType;

        SendToDeviceBody sendToDeviceBody = new SendToDeviceBody();
        sendToDeviceBody.messages = contentMap.getMap();

        mApi.sendToDevice(eventType, transactionId, sendToDeviceBody)
                .enqueue(new CryptoRestAdapterCallback<Void>(description, callback, new CryptoRestAdapterCallback.RequestRetryCallBack() {
                    @Override
                    public void onRetry() {
                        sendToDevice(eventType, contentMap, callback);
                    }
                }));
    }

    /**
     * Retrieves the devices informaty
     *
     * @param callback the asynchronous callback.
     */
    public void getDevices(final ApiCallback<DevicesListResponse> callback) {
        final String description = "getDevicesListInfo";

        mApi.getDevices()
                .enqueue(new CryptoRestAdapterCallback<DevicesListResponse>(description, callback,
                        new CryptoRestAdapterCallback.RequestRetryCallBack() {
                            @Override
                            public void onRetry() {
                                getDevices(callback);
                            }
                        }));
    }

    /**
     * Delete a device.
     *
     * @param deviceId the device id
     * @param params   the deletion parameters
     * @param callback the asynchronous callback
     */
    public void deleteDevice(final String deviceId, final DeleteDeviceParams params,
                             final ApiCallback<Void> callback) {
        final String description = "deleteDevice";

        mApi.deleteDevice(deviceId, params)
                .enqueue(new CryptoRestAdapterCallback<Void>(description, callback, new CryptoRestAdapterCallback.RequestRetryCallBack() {
                    @Override
                    public void onRetry() {
                        deleteDevice(deviceId, params, callback);
                    }
                }));
    }

    /**
     * Set a device name.
     *
     * @param deviceId   the device id
     * @param deviceName the device name
     * @param callback   the asynchronous callback
     */
    public void setDeviceName(final String deviceId, final String deviceName,
                              final ApiCallback<Void> callback) {
        final String description = "setDeviceName";

        Map<String, String> params = new HashMap<>();
        params.put("display_name", TextUtils.isEmpty(deviceName) ? "" : deviceName);

        mApi.updateDeviceInfo(deviceId, params)
                .enqueue(new CryptoRestAdapterCallback<Void>(description, callback, new CryptoRestAdapterCallback.RequestRetryCallBack() {
                    @Override
                    public void onRetry() {
                        setDeviceName(deviceId, deviceName, callback);
                    }
                }));
    }

    /**
     * Get the update devices list from two sync token.
     *
     * @param from     the start token.
     * @param to       the up-to token.
     * @param callback the asynchronous callback
     */
    public void getKeyChanges(final String from, final String to,
                              final ApiCallback<KeyChangesResponse> callback) {
        final String description = "getKeyChanges";

        mApi.getKeyChanges(from, to)
                .enqueue(new CryptoRestAdapterCallback<KeyChangesResponse>(description, callback,
                        new CryptoRestAdapterCallback.RequestRetryCallBack() {
                            @Override
                            public void onRetry() {
                                getKeyChanges(from, to, callback);
                            }
                        }));
    }
}
