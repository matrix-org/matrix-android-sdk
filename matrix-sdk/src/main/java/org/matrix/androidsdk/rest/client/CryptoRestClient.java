/* 
 * Copyright 2016 OpenMarket Ltd
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
import android.util.Log;

import org.matrix.androidsdk.HomeserverConnectionConfig;
import org.matrix.androidsdk.RestClient;
import org.matrix.androidsdk.crypto.data.MXDeviceInfo;
import org.matrix.androidsdk.crypto.data.MXKey;
import org.matrix.androidsdk.crypto.data.MXUsersDevicesMap;
import org.matrix.androidsdk.rest.api.CryptoApi;

import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.RestAdapterCallback;
import org.matrix.androidsdk.rest.model.crypto.KeysClaimResponse;
import org.matrix.androidsdk.rest.model.crypto.KeysQueryResponse;
import org.matrix.androidsdk.rest.model.crypto.KeysUploadResponse;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import retrofit.client.Response;

public class CryptoRestClient extends RestClient<CryptoApi> {

    private static final String LOG_TAG = "CryptoRestClient";

    /**
     * {@inheritDoc}
     */
    public CryptoRestClient(HomeserverConnectionConfig hsConfig) {
        super(hsConfig, CryptoApi.class, URI_API_PREFIX_PATH_UNSTABLE, false, true);
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

        String encodedDeviceId = null;

        if (!TextUtils.isEmpty(deviceId)) {
            try {
                encodedDeviceId = URLEncoder.encode(deviceId, "utf-8");
            } catch (Exception e) {
                Log.e(LOG_TAG, "## uploadKeys() : URLEncoder.encode fails " + e.getMessage());
            }
        }

        HashMap<String, Object> params = new HashMap<>();

        if (null != deviceKeys) {
            params.put("device_keys", deviceKeys);
        }

        if (null != oneTimeKeys) {
            params.put("one_time_keys", oneTimeKeys);
        }

        if (!TextUtils.isEmpty(encodedDeviceId)) {
            mApi.uploadKeys(encodedDeviceId, params, new RestAdapterCallback<KeysUploadResponse>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
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
            mApi.uploadKeys(params, new RestAdapterCallback<KeysUploadResponse>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
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
     * @param callback the asynchronous callback
     */
    public void downloadKeysForUsers(final List<String> userIds , final ApiCallback<MXUsersDevicesMap<MXDeviceInfo>> callback) {
        final String description = "downloadKeysForUsers";

        HashMap<String, List<String>> downloadQuery = new HashMap<>();

        if (null != userIds) {
            for(String userId : userIds) {
                downloadQuery.put(userId, new ArrayList<String>());
            }
        }

        HashMap<String, Object> parameters = new HashMap<>();
        parameters.put("device_keys", downloadQuery);

        mApi.downloadKeysForUsers(parameters, new RestAdapterCallback<KeysQueryResponse>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                try {
                    downloadKeysForUsers(userIds, callback);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "resend downloadKeysForUsers : failed " + e.getMessage());
                }
            }
        }) {
            @Override
            public void success(KeysQueryResponse keysQueryResponse, Response response) {
                onEventSent();
                callback.onSuccess(new MXUsersDevicesMap<>(keysQueryResponse.deviceKeys));
            }
        });
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

        mApi.claimOneTimeKeysForUsersDevices(params, new RestAdapterCallback<KeysClaimResponse>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
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
                callback.onSuccess(new MXUsersDevicesMap<>(keysClaimResponse.oneTimeKeys));
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

        mApi.sendToDevice(eventType, rand.nextInt(Integer.MAX_VALUE), content, new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                sendToDevice(eventType, contentMap, callback);
            }
        }));
    }
}
