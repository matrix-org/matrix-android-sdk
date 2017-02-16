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

package org.matrix.androidsdk.crypto;

import android.text.TextUtils;

import org.matrix.androidsdk.crypto.data.MXDeviceInfo;
import org.matrix.androidsdk.crypto.data.MXUsersDevicesMap;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.util.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MXDownloadKeysPromisesManager {
    private static final String LOG_TAG = "MXDwldPromiseManager";

    // keys in progress
    private final HashSet<String> mUserKeyDownloadsInProgress = new HashSet<>();

    // pending request
    private final HashSet<String> mPendingUsersWithNewDevices = new HashSet<>();

    // HS not ready for retry
    private final HashSet<String> mNotReadyToRetryhHS = new HashSet<>();

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

    /**
     * Constructor
     *
     * @param crypto the crypto session
     */
    public MXDownloadKeysPromisesManager(MXCrypto crypto) {
        mxCrypto = crypto;
    }

    /**
     * Tells if the keys downloads for an user id is either in progress or pending
     *
     * @param userId the user id
     * @return true if teh keys download is either in progress or pending
     */
    public boolean isKeysDownloading(String userId) {
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
    public List<String> getPendingUsersWithNewDevices() {
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
    public boolean canRetryKeysDownload(String userId) {
        boolean res = false;

        if (!TextUtils.isEmpty(userId) && userId.contains(":")) {
            try {
                synchronized (mNotReadyToRetryhHS) {
                    res = !mNotReadyToRetryhHS.contains(userId.substring(userId.lastIndexOf(":") + 1));
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
        synchronized (mNotReadyToRetryhHS) {
            mNotReadyToRetryhHS.clear();
        }
    }

    /**
     * The keys download failed
     *
     * @param userIds the user ids list
     */
    public void onKeysDownloadFailed(final List<String> userIds) {
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
    public void onKeysDownloadSucceed(List<String> userIds, Map<String, Map<String, Object>> failures) {

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
                        synchronized (mNotReadyToRetryhHS) {
                            mNotReadyToRetryhHS.add(k);
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
}