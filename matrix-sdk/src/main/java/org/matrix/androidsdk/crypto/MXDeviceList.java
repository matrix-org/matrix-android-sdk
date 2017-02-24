/*
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

package org.matrix.androidsdk.crypto;

import android.text.TextUtils;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.crypto.data.MXDeviceInfo;
import org.matrix.androidsdk.crypto.data.MXUsersDevicesMap;
import org.matrix.androidsdk.data.cryptostore.IMXCryptoStore;
import org.matrix.androidsdk.data.cryptostore.MXFileCryptoStore;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.crypto.KeysQueryResponse;
import org.matrix.androidsdk.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MXDeviceList {
    private static final String LOG_TAG = "MXDeviceList";

    // keys in progress
    private final HashSet<String> mUserKeyDownloadsInProgress = new HashSet<>();

    // pending request
    private final HashSet<String> mPendingUsersWithNewDevices = new HashSet<>();

    // HS not ready for retry
    private final HashSet<String> mNotReadyToRetryHS = new HashSet<>();

    // download keys queue
    class DownloadKeysPromise {
        // list of remain pending device keys
        final List<String> mPendingUserIdsList;

        // the unfiltered user ids list
        final List<String> mUserIdsList;

        // the request callback
        final ApiCallback<MXUsersDevicesMap<MXDeviceInfo>> mCallback;

        /**
         * Creator
         *
         * @param userIds  the user ids list
         * @param callback the asynchronous callback
         */
        DownloadKeysPromise(List<String> userIds, ApiCallback<MXUsersDevicesMap<MXDeviceInfo>> callback) {
            mPendingUserIdsList = new ArrayList<>(userIds);
            mUserIdsList = new ArrayList<>(userIds);
            mCallback = callback;
        }
    }

    // pending queues list
    private final List<DownloadKeysPromise> mDownloadKeysQueues = new ArrayList<>();

    private final MXCrypto mxCrypto;

    private final MXSession mxSession;

    /**
     * Constructor
     *
     * @param crypto the crypto session
     */
    public MXDeviceList(MXSession session, MXCrypto crypto) {
        mxSession = session;
        mxCrypto = crypto;
    }

    /**
     * Tells if the keys downloads for an user id is either in progress or pending
     *
     * @param userId the user id
     * @return true if teh keys download is either in progress or pending
     */
    private boolean isKeysDownloading(String userId) {
        if (null != userId) {
            return mUserKeyDownloadsInProgress.contains(userId) || mPendingUsersWithNewDevices.contains(userId);
        }

        return false;
    }

    /**
     * Add new pending users with new devices
     *
     * @param userIds the user ids list
     */
    public void addPendingUsersWithNewDevices(List<String> userIds) {
        synchronized (mPendingUsersWithNewDevices) {
            mPendingUsersWithNewDevices.addAll(userIds);
        }
    }

    /**
     * Provides new pending users with new devices
     *
     * @return the the user ids list
     */
    private List<String> getPendingUsersWithNewDevices() {
        final List<String> users;

        synchronized (mPendingUsersWithNewDevices) {
            users = new ArrayList<>(mPendingUsersWithNewDevices);

            // We've kicked off requests to these users: remove their
            // pending flag for now.
            mPendingUsersWithNewDevices.clear();
        }

        return users;
    }

    /**
     * Tells if the key downloads should be tried
     *
     * @param userId the userId
     * @return true if the keys download can be retrieved
     */
    private boolean canRetryKeysDownload(String userId) {
        boolean res = false;

        if (!TextUtils.isEmpty(userId) && userId.contains(":")) {
            try {
                synchronized (mNotReadyToRetryHS) {
                    res = !mNotReadyToRetryHS.contains(userId.substring(userId.lastIndexOf(":") + 1));
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "## canRetryKeysDownload() failed : " + e.getMessage());
            }
        }

        return res;
    }

    /**
     * Add a download keys promise
     *
     * @param userIds  the user ids list
     * @param callback the asynchronous callback
     * @return the filtered user ids list i.e the one which require a remote request
     */
    public List<String> addDownloadKeysPromise(List<String> userIds, ApiCallback<MXUsersDevicesMap<MXDeviceInfo>> callback) {
        if (null != userIds) {
            List<String> filteredUserIds = new ArrayList<>(userIds);

            synchronized (mUserKeyDownloadsInProgress) {
                filteredUserIds.removeAll(mUserKeyDownloadsInProgress);
                mUserKeyDownloadsInProgress.addAll(userIds);
            }

            synchronized (mPendingUsersWithNewDevices) {
                mPendingUsersWithNewDevices.removeAll(userIds);
            }

            mDownloadKeysQueues.add(new DownloadKeysPromise(userIds, callback));

            return filteredUserIds;
        } else {
            return null;
        }
    }

    /**
     * Clear the unavailable server lists
     */
    public void clearUnavailableServersList() {
        synchronized (mNotReadyToRetryHS) {
            mNotReadyToRetryHS.clear();
        }
    }

    /**
     * The keys download failed
     *
     * @param userIds the user ids list
     */
    private void onKeysDownloadFailed(final List<String> userIds) {
        if (null != userIds) {
            synchronized (mUserKeyDownloadsInProgress) {
                mUserKeyDownloadsInProgress.removeAll(userIds);
            }

            synchronized (mPendingUsersWithNewDevices) {
                mPendingUsersWithNewDevices.addAll(userIds);
            }
        }
    }

    /**
     * The keys download succeeded.
     *
     * @param userIds
     */
    private void onKeysDownloadSucceed(List<String> userIds, Map<String, Map<String, Object>> failures) {
        if (null != failures) {
            Set<String> keys = failures.keySet();

            for (String k : keys) {
                Map<String, Object> value = failures.get(k);

                if (value.containsKey("status")) {
                    Object statusCodeAsVoid = value.get("status");
                    int statusCode = 0;

                    if (statusCodeAsVoid instanceof Double) {
                        statusCode = ((Double) statusCodeAsVoid).intValue();
                    } else if (statusCodeAsVoid instanceof Integer) {
                        statusCode = ((Integer) statusCodeAsVoid).intValue();
                    }

                    if (statusCode == 503) {
                        synchronized (mNotReadyToRetryHS) {
                            mNotReadyToRetryHS.add(k);
                        }
                    }
                }
            }
        }

        if (null != userIds) {
            if (mDownloadKeysQueues.size() > 0) {
                ArrayList<DownloadKeysPromise> promisesToRemove = new ArrayList<>();

                for (DownloadKeysPromise promise : mDownloadKeysQueues) {
                    promise.mPendingUserIdsList.removeAll(userIds);

                    if (promise.mPendingUserIdsList.size() == 0) {
                        // private members
                        final MXUsersDevicesMap<MXDeviceInfo> usersDevicesInfoMap = new MXUsersDevicesMap<>();

                        for (String userId : promise.mUserIdsList) {
                            Map<String, MXDeviceInfo> devices = mxCrypto.getCryptoStore().getUserDevices(userId);

                            if (null == devices) {
                                synchronized (mPendingUsersWithNewDevices) {
                                    if (canRetryKeysDownload(userId)) {
                                        mPendingUsersWithNewDevices.add(userId);
                                        Log.e(LOG_TAG, "failed to retry the devices of " + userId + " : retry later");
                                    } else {
                                        Log.e(LOG_TAG, "failed to retry the devices of " + userId + " : the HS is not available");
                                    }
                                }
                            } else {
                                // And the response result
                                usersDevicesInfoMap.setObjects(devices, userId);
                            }
                        }

                        if (!mxCrypto.hasBeenReleased()) {
                            final ApiCallback<MXUsersDevicesMap<MXDeviceInfo>> callback = promise.mCallback;

                            if (null != callback) {
                                mxCrypto.getUIHandler().post(new Runnable() {
                                    @Override
                                    public void run() {
                                        callback.onSuccess(usersDevicesInfoMap);
                                    }
                                });
                            }
                        }
                        promisesToRemove.add(promise);
                    }
                }
                mDownloadKeysQueues.removeAll(promisesToRemove);
            }

            mUserKeyDownloadsInProgress.removeAll(userIds);
        }
    }

    /**
     * Download the device keys for a list of users and stores the keys in the MXStore.
     * It must be called in getEncryptingThreadHandler() thread.
     * The callback is called in the UI thread.
     *
     * @param userIds       The users to fetch.
     * @param forceDownload Always download the keys even if cached.
     * @param callback      the asynchronous callback
     */
    public void downloadKeys(List<String> userIds, boolean forceDownload, final ApiCallback<MXUsersDevicesMap<MXDeviceInfo>> callback) {
        Log.d(LOG_TAG, "## downloadKeys() : forceDownload " + forceDownload + " : " + userIds);

        // Map from userid -> deviceid -> DeviceInfo
        final MXUsersDevicesMap<MXDeviceInfo> stored = new MXUsersDevicesMap<>();

        // List of user ids we need to download keys for
        final ArrayList<String> downloadUsers;

        if (forceDownload) {
            downloadUsers = (null == userIds) ? new ArrayList<String>() : new ArrayList<>(userIds);
        } else {
            downloadUsers = new ArrayList<>();

            if (null != userIds) {
                IMXCryptoStore store = mxCrypto.getCryptoStore();

                for (String userId : userIds) {

                    Map<String, MXDeviceInfo> devices = store.getUserDevices(userId);

                    if (null == devices) {
                        downloadUsers.add(userId);
                    } else {
                        // the keys download won't be triggered twice
                        // but the callback requires the dedicated keys
                        if (isKeysDownloading(userId)) {
                            downloadUsers.add(userId);
                        } else {
                            stored.setObjects(devices, userId);
                        }
                    }
                }
            }
        }

        if (0 == downloadUsers.size()) {
            Log.d(LOG_TAG, "## doKeyDownloadForUsers() : no new user device");

            if (null != callback) {
                mxCrypto.getUIHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onSuccess(stored);
                    }
                });
            }
        } else {
            Log.d(LOG_TAG, "## doKeyDownloadForUsers() : starts");
            final long t0 = System.currentTimeMillis();

            doKeyDownloadForUsers(downloadUsers, new ApiCallback<MXUsersDevicesMap<MXDeviceInfo>>() {
                public void onSuccess(MXUsersDevicesMap<MXDeviceInfo> usersDevicesInfoMap) {
                    Log.d(LOG_TAG, "## downloadKeys() : doKeyDownloadForUsers succeeds after " + (System.currentTimeMillis() - t0) + " ms");

                    usersDevicesInfoMap.addEntriesFromMap(stored);

                    if (null != callback) {
                        callback.onSuccess(usersDevicesInfoMap);
                    }
                }

                @Override
                public void onNetworkError(Exception e) {
                    Log.e(LOG_TAG, "## downloadKeys() : doKeyDownloadForUsers onNetworkError " + e.getMessage());
                    if (null != callback) {
                        callback.onNetworkError(e);
                    }
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    Log.e(LOG_TAG, "## downloadKeys() : doKeyDownloadForUsers onMatrixError " + e.getLocalizedMessage());
                    if (null != callback) {
                        callback.onMatrixError(e);
                    }
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    Log.e(LOG_TAG, "## downloadKeys() : doKeyDownloadForUsers onUnexpectedError " + e.getMessage());
                    if (null != callback) {
                        callback.onUnexpectedError(e);
                    }
                }
            });
        }
    }

    /**
     * Download the devices keys for a set of users.
     * It must be called in getEncryptingThreadHandler() thread.
     * The callback is called in the UI thread.
     *
     * @param downloadUsers the user ids list
     * @param callback      the asynchronous callback
     */
    private void doKeyDownloadForUsers(final List<String> downloadUsers, final ApiCallback<MXUsersDevicesMap<MXDeviceInfo>> callback) {
        Log.d(LOG_TAG, "## doKeyDownloadForUsers() : doKeyDownloadForUsers " + downloadUsers);

        // get the user ids which did not already trigger a keys download
        final List<String> filteredUsers = addDownloadKeysPromise(downloadUsers, callback);

        // if there is no new keys request
        if (0 == filteredUsers.size()) {
            // trigger nothing
            return;
        }

        mxSession.getCryptoRestClient().downloadKeysForUsers(filteredUsers, mxSession.getDataHandler().getStore().getEventStreamToken(), new ApiCallback<KeysQueryResponse>() {
            @Override
            public void onSuccess(final KeysQueryResponse keysQueryResponse) {
                mxCrypto.getEncryptingThreadHandler().post(new Runnable() {
                    @Override
                    public void run() {

                        Log.d(LOG_TAG, "## doKeyDownloadForUsers() : Got keys for " + filteredUsers.size() + " users");
                        MXDeviceInfo myDevice = mxCrypto.getMyDevice();
                        IMXCryptoStore cryptoStore = mxCrypto.getCryptoStore();

                        for (String userId : filteredUsers) {
                            Map<String, MXDeviceInfo> devices = keysQueryResponse.deviceKeys.get(userId);

                            Log.d(LOG_TAG, "## doKeyDownloadForUsers() : Got keys for " + userId + " : " + devices);

                            if (null != devices) {
                                HashMap<String, MXDeviceInfo> mutabledevices = new HashMap<>(devices);
                                ArrayList<String> deviceIds = new ArrayList<>(mutabledevices.keySet());

                                for (String deviceId : deviceIds) {
                                    // the user has been logged out
                                    if (null == cryptoStore) {
                                        break;
                                    }

                                    // Get the potential previously store device keys for this device
                                    MXDeviceInfo previouslyStoredDeviceKeys = cryptoStore.getUserDevice(deviceId, userId);
                                    MXDeviceInfo deviceInfo = mutabledevices.get(deviceId);

                                    // in some race conditions (like unit tests)
                                    // the self device must be seen as verified
                                    if (TextUtils.equals(deviceInfo.deviceId, myDevice.deviceId) &&
                                            TextUtils.equals(userId, myDevice.userId)) {
                                        deviceInfo.mVerified = MXDeviceInfo.DEVICE_VERIFICATION_VERIFIED;
                                    }

                                    // Validate received keys
                                    if (!validateDeviceKeys(deviceInfo, userId, deviceId, previouslyStoredDeviceKeys)) {
                                        // New device keys are not valid. Do not store them
                                        mutabledevices.remove(deviceId);

                                        if (null != previouslyStoredDeviceKeys) {
                                            // But keep old validated ones if any
                                            mutabledevices.put(deviceId, previouslyStoredDeviceKeys);
                                        }
                                    } else if (null != previouslyStoredDeviceKeys) {
                                        // The verified status is not sync'ed with hs.
                                        // This is a client side information, valid only for this client.
                                        // So, transfer its previous value
                                        mutabledevices.get(deviceId).mVerified = previouslyStoredDeviceKeys.mVerified;
                                    }
                                }

                                // Update the store
                                // Note that devices which aren't in the response will be removed from the stores
                                cryptoStore.storeUserDevices(userId, mutabledevices);
                            }
                        }

                        onKeysDownloadSucceed(filteredUsers, keysQueryResponse.failures);
                    }
                });
            }

            @Override
            public void onNetworkError(Exception e) {
                mxCrypto.getEncryptingThreadHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        onKeysDownloadFailed(filteredUsers);
                    }
                });

                Log.e(LOG_TAG, "##doKeyDownloadForUsers() : onNetworkError " + e.getMessage());
                if (null != callback) {
                    callback.onNetworkError(e);
                }
            }

            @Override
            public void onMatrixError(MatrixError e) {
                Log.e(LOG_TAG, "##doKeyDownloadForUsers() : onMatrixError " + e.getMessage());

                mxCrypto.getEncryptingThreadHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        onKeysDownloadFailed(filteredUsers);
                    }
                });

                if (null != callback) {
                    callback.onMatrixError(e);
                }
            }

            @Override
            public void onUnexpectedError(Exception e) {
                Log.e(LOG_TAG, "##doKeyDownloadForUsers() : onUnexpectedError " + e.getMessage());

                mxCrypto.getEncryptingThreadHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        onKeysDownloadFailed(filteredUsers);
                    }
                });

                if (null != callback) {
                    callback.onUnexpectedError(e);
                }
            }
        });
    }

    /**
     * Validate device keys.
     * This method must called on getEncryptingThreadHandler() thread.
     *
     * @param deviceKeys                 the device keys to validate.
     * @param userId                     the id of the user of the device.
     * @param deviceId                   the id of the device.
     * @param previouslyStoredDeviceKeys the device keys we received before for this device
     * @return true if succeeds
     */
    private boolean validateDeviceKeys(MXDeviceInfo deviceKeys, String userId, String deviceId, MXDeviceInfo previouslyStoredDeviceKeys) {
        if ((null == deviceKeys) || (null == deviceKeys.keys)) {
            // no keys?
            return false;
        }

        // Check that the user_id and device_id in the received deviceKeys are correct
        if (!TextUtils.equals(deviceKeys.userId, userId)) {
            Log.e(LOG_TAG, "## validateDeviceKeys() : Mismatched user_id " + deviceKeys.userId + " from " + userId + ":" + deviceId);
            return false;
        }

        if (!TextUtils.equals(deviceKeys.deviceId, deviceId)) {
            Log.e(LOG_TAG, "## validateDeviceKeys() : Mismatched device_id " + deviceKeys.deviceId + " from " + userId + ":" + deviceId);
            return false;
        }

        String signKeyId = "ed25519:" + deviceKeys.deviceId;
        String signKey = deviceKeys.keys.get(signKeyId);

        if (null == signKey) {
            Log.e(LOG_TAG, "## validateDeviceKeys() : Device " + userId + ":" + deviceKeys.deviceId + " has no ed25519 key");
            return false;
        }

        Map<String, String> signatureMap = deviceKeys.signatures.get(userId);

        if (null == signatureMap) {
            Log.e(LOG_TAG, "## validateDeviceKeys() : Device " + userId + ":" + deviceKeys.deviceId + " has no map for " + userId);
            return false;
        }

        String signature = signatureMap.get(signKeyId);

        if (null == signature) {
            Log.e(LOG_TAG, "## validateDeviceKeys() : Device " + userId + ":" + deviceKeys.deviceId + " is not signed");
            return false;
        }

        boolean isVerified = false;
        String errorMessage = null;

        try {
            isVerified =  mxCrypto.getOlmDevice().verifySignature(signKey, deviceKeys.signalableJSONDictionary(), signature);
        } catch (Exception e) {
            errorMessage = e.getMessage();
        }


        if (!isVerified) {
            Log.e(LOG_TAG, "## validateDeviceKeys() : Unable to verify signature on device " + userId + ":" + deviceKeys.deviceId + " with error " + errorMessage);
            return false;
        }

        if (null != previouslyStoredDeviceKeys) {
            if (!TextUtils.equals(previouslyStoredDeviceKeys.fingerprint(), signKey)) {
                // This should only happen if the list has been MITMed; we are
                // best off sticking with the original keys.
                //
                // Should we warn the user about it somehow?
                Log.e(LOG_TAG, "## validateDeviceKeys() : WARNING:Ed25519 key for device " + userId + ":" + deviceKeys.deviceId + " has changed");
                return false;
            }
        }

        return true;
    }

    /**
     * Start device queries for any users who sent us an m.new_device recently
     * This method must be called on getEncryptingThreadHandler() thread.
     */
    public void refreshOutdatedDeviceLists() {
        final List<String> users = getPendingUsersWithNewDevices();

        if (users.size() == 0) {
            return;
        }

        doKeyDownloadForUsers(users, new ApiCallback<MXUsersDevicesMap<MXDeviceInfo>>() {
            @Override
            public void onSuccess(final MXUsersDevicesMap<MXDeviceInfo> response) {
                mxCrypto.getEncryptingThreadHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        Log.d(LOG_TAG, "## refreshOutdatedDeviceLists() : done");
                    }
                });
            }

            private void onError(String error) {
                Log.e(LOG_TAG, "## refreshOutdatedDeviceLists() : ERROR updating device keys for users " + users + " : " + error);
            }

            @Override
            public void onNetworkError(final Exception e) {
                onError(e.getMessage());
            }

            @Override
            public void onMatrixError(final MatrixError e) {
                onError(e.getMessage());
            }

            @Override
            public void onUnexpectedError(final Exception e) {
                onError(e.getMessage());
            }
        });
    }

}