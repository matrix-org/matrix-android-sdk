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

package org.matrix.androidsdk.crypto;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.text.TextUtils;

import org.matrix.androidsdk.crypto.data.MXOlmInboundGroupSession2;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.Sync.SyncResponse;
import org.matrix.androidsdk.util.Log;

import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.crypto.algorithms.IMXDecrypting;
import org.matrix.androidsdk.crypto.algorithms.IMXEncrypting;
import org.matrix.androidsdk.crypto.data.MXDeviceInfo;
import org.matrix.androidsdk.crypto.data.MXEncryptEventContentResult;
import org.matrix.androidsdk.crypto.data.MXKey;
import org.matrix.androidsdk.crypto.data.MXOlmSessionResult;
import org.matrix.androidsdk.crypto.data.MXUsersDevicesMap;
import org.matrix.androidsdk.data.cryptostore.IMXCryptoStore;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.listeners.IMXNetworkEventListener;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.network.NetworkConnectivityReceiver;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.EventContent;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.NewDeviceContent;
import org.matrix.androidsdk.rest.model.RoomKeyContent;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.crypto.KeysUploadResponse;
import org.matrix.androidsdk.util.JsonUtils;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

/**
 * A `MXCrypto` class instance manages the end-to-end crypto for a MXSession instance.
 * <p>
 * Messages posted by the user are automatically redirected to MXCrypto in order to be encrypted
 * before sending.
 * In the other hand, received events goes through MXCrypto for decrypting.
 * MXCrypto maintains all necessary keys and their sharing with other devices required for the crypto.
 * Specially, it tracks all room membership changes events in order to do keys updates.
 */
public class MXCrypto {
    private static final String LOG_TAG = "MXCrypto";

    // max number of keys to upload at once
    // Creating keys can be an expensive operation so we limit the
    // number we generate in one go to avoid blocking the application
    // for too long.
    private static final int ONE_TIME_KEY_GENERATION_MAX_NUMBER = 5;

    // frequency with which to check & upload one-time keys
    private static final long ONE_TIME_KEY_UPLOAD_PERIOD = 60 * 1000; // one minute

    // The Matrix session.
    private final MXSession mSession;

    // the crypto store
    public IMXCryptoStore mCryptoStore;

    // MXEncrypting instance for each room.
    private final HashMap<String, IMXEncrypting> mRoomEncryptors;

    // A map from algorithm to MXDecrypting instance, for each room
    private final HashMap<String, /* room id */
            HashMap<String /* algorithm */, IMXDecrypting>> mRoomDecryptors;

    // Our device keys
    private MXDeviceInfo mMyDevice;

    // The libolm wrapper.
    private MXOlmDevice mOlmDevice;

    private Map<String, Map<String, String>> mLastPublishedOneTimeKeys;

    // the encryption is starting
    private boolean mIsStarting;

    // tell if the crypto is started
    private boolean mIsStarted;

    // the crypto background threads
    private HandlerThread mEncryptingHandlerThread = null;
    private Handler mEncryptingHandler = null;

    private HandlerThread mDecryptingHandlerThread = null;
    private Handler mDecryptingHandler = null;

    // the UI thread
    private Handler mUIHandler = null;

    private NetworkConnectivityReceiver mNetworkConnectivityReceiver;

    private final MXDeviceList mDevicesList;

    private final IMXNetworkEventListener mNetworkListener = new IMXNetworkEventListener() {
        @Override
        public void onNetworkConnectionUpdate(boolean isConnected) {
            if (isConnected && !isStarted()) {
                Log.d(LOG_TAG, "Start MXCrypto because a network connection has been retrieved ");
                start(false, null);
            }
        }
    };

    private final MXEventListener mEventListener = new MXEventListener() {
        @Override
        public void onToDeviceEvent(Event event) {
            MXCrypto.this.onToDeviceEvent(event);
        }

        @Override
        public void onLiveEvent(Event event, RoomState roomState) {
            if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE_ENCRYPTION)) {
                onCryptoEvent(event);
            }
        }
    };

    // initialization callbacks
    private final ArrayList<ApiCallback<Void>> mInitializationCallbacks = new ArrayList();

    // Warn the user if some new devices are detected while encrypting a message.
    private boolean mWarnOnUnknownDevices = true;

    // tell if there is a OTK check in progress
    private boolean mOneTimeKeyCheckInProgress = false;

    // last OTK check timestamp
    private long mLastOneTimeKeyCheck = 0;

    /**
     * Constructor
     *
     * @param matrixSession the session
     * @param cryptoStore   the crypto store
     */
    public MXCrypto(MXSession matrixSession, IMXCryptoStore cryptoStore) {
        mSession = matrixSession;
        mCryptoStore = cryptoStore;

        mOlmDevice = new MXOlmDevice(mCryptoStore);
        mRoomEncryptors = new HashMap<>();
        mRoomDecryptors = new HashMap<>();

        String deviceId = mSession.getCredentials().deviceId;
        // deviceId should always be defined
        boolean refreshDevicesList = !TextUtils.isEmpty(deviceId);

        if (TextUtils.isEmpty(deviceId)) {
            // use the stored one
            mSession.getCredentials().deviceId = deviceId = mCryptoStore.getDeviceId();
        }

        if (TextUtils.isEmpty(deviceId)) {
            mSession.getCredentials().deviceId = deviceId = UUID.randomUUID().toString();
            Log.d(LOG_TAG, "Warning: No device id in MXCredentials. An id was created. Think of storing it");
            mCryptoStore.storeDeviceId(deviceId);
        }

        mMyDevice = new MXDeviceInfo(deviceId);
        mMyDevice.userId = mSession.getMyUserId();

        mDevicesList = new MXDeviceList(matrixSession, this);

        HashMap<String, String> keys = new HashMap<>();

        if (!TextUtils.isEmpty(mOlmDevice.getDeviceEd25519Key())) {
            keys.put("ed25519:" + mSession.getCredentials().deviceId, mOlmDevice.getDeviceEd25519Key());
        }

        if (!TextUtils.isEmpty(mOlmDevice.getDeviceCurve25519Key())) {
            keys.put("curve25519:" + mSession.getCredentials().deviceId, mOlmDevice.getDeviceCurve25519Key());
        }

        mMyDevice.keys = keys;

        mMyDevice.algorithms = MXCryptoAlgorithms.sharedAlgorithms().supportedAlgorithms();
        mMyDevice.mVerified = MXDeviceInfo.DEVICE_VERIFICATION_VERIFIED;

        // Add our own deviceinfo to the store
        Map<String, MXDeviceInfo> endToEndDevicesForUser = mCryptoStore.getUserDevices(mSession.getMyUserId());

        HashMap<String, MXDeviceInfo> myDevices;

        if (null != endToEndDevicesForUser) {
            myDevices = new HashMap<>(endToEndDevicesForUser);
        } else {
            myDevices = new HashMap<>();
        }

        myDevices.put(mMyDevice.deviceId, mMyDevice);

        mCryptoStore.storeUserDevices(mSession.getMyUserId(), myDevices);
        mSession.getDataHandler().setCryptoEventsListener(mEventListener);

        mEncryptingHandlerThread = new HandlerThread("MXCrypto_encrypting_" + mSession.getMyUserId(), Thread.MIN_PRIORITY);
        mEncryptingHandlerThread.start();

        mDecryptingHandlerThread = new HandlerThread("MXCrypto_decrypting_" + mSession.getMyUserId(), Thread.MIN_PRIORITY);
        mDecryptingHandlerThread.start();

        mUIHandler = new Handler(Looper.getMainLooper());

        if (refreshDevicesList) {
            // ensure to have the up-to-date devices list
            // got some issues when upgrading from Riot < 0.6.4
            mDevicesList.addPendingUsersWithNewDevices(Arrays.asList(mSession.getMyUserId()));
        }
    }

    /**
     * @return the encrypting thread handler
     */
    public Handler getEncryptingThreadHandler() {
        // mEncryptingHandlerThread was not yet ready
        if (null == mEncryptingHandler) {
            mEncryptingHandler = new Handler(mEncryptingHandlerThread.getLooper());
        }

        // fail to get the handler
        // might happen if the thread is not yet ready
        if (null == mEncryptingHandler) {
            return mUIHandler;
        }

        return mEncryptingHandler;
    }

    /**
     * @return the decrypting thread handler
     */
    private Handler getDecryptingThreadHandler() {
        // mDecryptingHandlerThread was not yet ready
        if (null == mDecryptingHandler) {
            mDecryptingHandler = new Handler(mDecryptingHandlerThread.getLooper());
        }

        // fail to get the handler
        // might happen if the thread is not yet ready
        if (null == mDecryptingHandler) {
            return mUIHandler;
        }

        return mDecryptingHandler;
    }

    /**
     * @return the UI thread handler
     */
    public Handler getUIHandler() {
        return mUIHandler;
    }

    public void setNetworkConnectivityReceiver(NetworkConnectivityReceiver networkConnectivityReceiver) {
        mNetworkConnectivityReceiver = networkConnectivityReceiver;
    }

    /**
     * @return true if some saved data is corrupted
     */
    public boolean isCorrupted() {
        return (null != mCryptoStore) && mCryptoStore.isCorrupted();
    }

    /**
     * @return true if this instance has been released
     */
    public boolean hasBeenReleased() {
        return (null == mOlmDevice);
    }

    /**
     * @return my device info
     */
    public MXDeviceInfo getMyDevice() {
        return mMyDevice;
    }

    /**
     * @return the crypto store
     */
    public IMXCryptoStore getCryptoStore() {
        return mCryptoStore;
    }

    /**
     * @return the deviceList
     */
    public MXDeviceList getDeviceList() {
        return mDevicesList;
    }

    /**
     * Tell if the MXCrypto is started
     *
     * @return true if the crypto is started
     */
    public boolean isStarted() {
        return mIsStarted;
    }

    /**
     * Tells if the MXCrypto is starting.
     *
     * @return true if the crypto is starting
     */
    public boolean isStarting() {
        return mIsStarting;
    }

    /**
     * Start the crypto module.
     * Device keys will be uploaded, then one time keys if there are not enough on the homeserver
     * and, then, if this is the first time, this new device will be announced to all other users
     * devices.
     *
     * @param aCallback the asynchronous callback
     */
    public void start(final boolean isInitialSync, final ApiCallback<Void> aCallback) {
        if ((null != aCallback) && (mInitializationCallbacks.indexOf(aCallback) < 0)) {
            mInitializationCallbacks.add(aCallback);
        }

        if (mIsStarting) {
            return;
        }

        // do not start if there is not network connection
        if ((null != mNetworkConnectivityReceiver) && !mNetworkConnectivityReceiver.isConnected()) {
            // wait that a valid network connection is retrieved
            mNetworkConnectivityReceiver.removeEventListener(mNetworkListener);
            mNetworkConnectivityReceiver.addEventListener(mNetworkListener);
            return;
        }

        mIsStarting = true;

        getEncryptingThreadHandler().post(new Runnable() {
            @Override
            public void run() {
                uploadDeviceKeys(new ApiCallback<KeysUploadResponse>() {
                    private void onError() {
                        getUIHandler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                start(isInitialSync, null);
                            }
                        }, 5);
                    }

                    @Override
                    public void onSuccess(KeysUploadResponse info) {
                        getEncryptingThreadHandler().post(new Runnable() {
                            @Override
                            public void run() {
                                if (!hasBeenReleased()) {
                                    Log.d(LOG_TAG, "###########################################################");
                                    Log.d(LOG_TAG, "uploadDeviceKeys done for " + mSession.getMyUserId());
                                    Log.d(LOG_TAG, "  - device id  : " + mSession.getCredentials().deviceId);
                                    Log.d(LOG_TAG, "  - ed25519    : " + mOlmDevice.getDeviceEd25519Key());
                                    Log.d(LOG_TAG, "  - curve25519 : " + mOlmDevice.getDeviceCurve25519Key());
                                    Log.d(LOG_TAG, "  - oneTimeKeys: " + mLastPublishedOneTimeKeys);     // They are
                                    Log.d(LOG_TAG, "");

                                    getEncryptingThreadHandler().post(new Runnable() {
                                        @Override
                                        public void run() {
                                            maybeUploadOneTimeKeys(new ApiCallback<Void>() {
                                                @Override
                                                public void onSuccess(Void info) {
                                                    getEncryptingThreadHandler().post(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            // Make sure we process to-device messages before generating new one-time-keys #2782
                                                            checkDeviceAnnounced(new ApiCallback<Void>() {
                                                                @Override
                                                                public void onSuccess(Void info) {
                                                                    if (null != mNetworkConnectivityReceiver) {
                                                                        mNetworkConnectivityReceiver.removeEventListener(mNetworkListener);
                                                                    }

                                                                    mIsStarting = false;
                                                                    mIsStarted = true;

                                                                    for (ApiCallback<Void> callback : mInitializationCallbacks) {
                                                                        final ApiCallback<Void> fCallback = callback;
                                                                        getUIHandler().post(new Runnable() {
                                                                            @Override
                                                                            public void run() {
                                                                                fCallback.onSuccess(null);
                                                                            }
                                                                        });
                                                                    }
                                                                    mInitializationCallbacks.clear();

                                                                    if (isInitialSync) {
                                                                        getEncryptingThreadHandler().post(new Runnable() {
                                                                            @Override
                                                                            public void run() {
                                                                                // refresh the devices list for each known room members
                                                                                getDeviceList().invalidateUserDeviceList(getE2eRoomMembers());
                                                                                mDevicesList.refreshOutdatedDeviceLists();
                                                                            }
                                                                        });
                                                                    }
                                                                }

                                                                @Override
                                                                public void onNetworkError(Exception e) {
                                                                    Log.e(LOG_TAG, "## start failed : " + e.getMessage());
                                                                    onError();
                                                                }

                                                                @Override
                                                                public void onMatrixError(MatrixError e) {
                                                                    Log.e(LOG_TAG, "## start failed : " + e.getMessage());
                                                                    onError();
                                                                }

                                                                @Override
                                                                public void onUnexpectedError(Exception e) {
                                                                    Log.e(LOG_TAG, "## start failed : " + e.getMessage());
                                                                    onError();
                                                                }
                                                            });
                                                        }
                                                    });
                                                }

                                                @Override
                                                public void onNetworkError(Exception e) {
                                                    Log.e(LOG_TAG, "## start failed : " + e.getMessage());
                                                    onError();
                                                }

                                                @Override
                                                public void onMatrixError(MatrixError e) {
                                                    Log.e(LOG_TAG, "## start failed : " + e.getMessage());
                                                    onError();
                                                }

                                                @Override
                                                public void onUnexpectedError(Exception e) {
                                                    Log.e(LOG_TAG, "## start failed : " + e.getMessage());
                                                    onError();
                                                }
                                            });
                                        }
                                    });
                                }
                            }
                        });
                    }

                    @Override
                    public void onNetworkError(Exception e) {
                        Log.e(LOG_TAG, "## start failed : " + e.getMessage());
                        onError();
                    }

                    @Override
                    public void onMatrixError(MatrixError e) {
                        Log.e(LOG_TAG, "## start failed : " + e.getMessage());
                        onError();
                    }

                    @Override
                    public void onUnexpectedError(Exception e) {
                        Log.e(LOG_TAG, "## start failed : " + e.getMessage());
                        onError();
                    }
                });
            }
        });
    }

    /**
     * Close the crypto
     */
    public void close() {
        if (null != mEncryptingHandlerThread) {
            mSession.getDataHandler().removeListener(mEventListener);
            getEncryptingThreadHandler().post(new Runnable() {
                @Override
                public void run() {
                    if (null != mOlmDevice) {
                        mOlmDevice.release();
                        mOlmDevice = null;
                    }

                    mMyDevice = null;

                    mCryptoStore.close();
                    mCryptoStore = null;

                    if (null != mEncryptingHandlerThread) {
                        mEncryptingHandlerThread.quit();
                        mEncryptingHandlerThread = null;
                    }
                }
            });

            getDecryptingThreadHandler().post(new Runnable() {
                @Override
                public void run() {
                    if (null != mDecryptingHandlerThread) {
                        mDecryptingHandlerThread.quit();
                        mDecryptingHandlerThread = null;
                    }
                }
            });
        }
    }

    /**
     * @return the olmdevice instance
     */
    public MXOlmDevice getOlmDevice() {
        return mOlmDevice;
    }

    /**
     * A sync response has been received
     *
     * @param syncResponse the syncResponse
     * @param fromToken    the start sync token
     * @param isCatchingUp true if there is a catch-up in progress.
     */
    public void onSyncCompleted(final SyncResponse syncResponse, final String fromToken, final boolean isCatchingUp) {
        getEncryptingThreadHandler().post(new Runnable() {
            @Override
            public void run() {
                if (null != syncResponse.deviceLists) {
                    getDeviceList().invalidateUserDeviceList(syncResponse.deviceLists.changed);
                }

                if (isStarted()) {
                    // Make sure we process to-device messages before generating new one-time-keys #2782
                    mDevicesList.refreshOutdatedDeviceLists();
                }

                if (!isCatchingUp && isStarted()) {
                    maybeUploadOneTimeKeys();
                }
            }
        });
    }

    /**
     * Get the stored device keys for a user.
     *
     * @param userId   the user to list keys for.
     * @param callback the asynchronous callback
     */
    public void getUserDevices(final String userId, final ApiCallback<List<MXDeviceInfo>> callback) {
        getEncryptingThreadHandler().post(new Runnable() {
            @Override
            public void run() {
                final List<MXDeviceInfo> list = getUserDevices(userId);

                if (null != callback) {
                    getUIHandler().post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onSuccess(list);
                        }
                    });
                }
            }
        });
    }

    /**
     * Find a device by curve25519 identity key
     *
     * @param userId    the owner of the device.
     * @param algorithm the encryption algorithm.
     * @param senderKey the curve25519 key to match.
     * @return the device info.
     */
    public MXDeviceInfo deviceWithIdentityKey(final String senderKey, final String userId, final String algorithm) {
        if (!hasBeenReleased()) {
            if (!TextUtils.equals(algorithm, MXCryptoAlgorithms.MXCRYPTO_ALGORITHM_MEGOLM) && !TextUtils.equals(algorithm, MXCryptoAlgorithms.MXCRYPTO_ALGORITHM_OLM)) {
                // We only deal in olm keys
                return null;
            }

            if (!TextUtils.isEmpty(userId)) {
                final ArrayList<MXDeviceInfo> result = new ArrayList<>();
                final CountDownLatch lock = new CountDownLatch(1);

                getDecryptingThreadHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        List<MXDeviceInfo> devices = getUserDevices(userId);

                        if (null != devices) {
                            for (MXDeviceInfo device : devices) {
                                Set<String> keys = device.keys.keySet();

                                for (String keyId : keys) {
                                    if (keyId.startsWith("curve25519:")) {
                                        if (TextUtils.equals(senderKey, device.keys.get(keyId))) {
                                            result.add(device);
                                        }
                                    }
                                }
                            }
                        }

                        lock.countDown();
                    }
                });

                try {
                    lock.await();
                } catch (Exception e) {
                    Log.e(LOG_TAG, "## deviceWithIdentityKey() : failed " + e.getMessage());
                }

                return (result.size() > 0) ? result.get(0) : null;
            }
        }

        // Doesn't match a known device
        return null;
    }

    /**
     * Provides the device information for a device id and an user Id
     *
     * @param userId   the user id
     * @param deviceId the device id
     * @param callback the asynchronous callback
     */
    public void getDeviceInfo(final String userId, final String deviceId, final ApiCallback<MXDeviceInfo> callback) {
        getDecryptingThreadHandler().post(new Runnable() {
            @Override
            public void run() {
                final MXDeviceInfo di;

                if (!TextUtils.isEmpty(userId) && !TextUtils.isEmpty(deviceId)) {
                    di = mCryptoStore.getUserDevice(deviceId, userId);
                } else {
                    di = null;
                }

                if (null != callback) {
                    getUIHandler().post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onSuccess(di);
                        }
                    });
                }
            }
        });
    }

    /**
     * Set the devices as known
     *
     * @param devices  the devices
     * @param callback the as
     */
    public void setDevicesKnown(final List<MXDeviceInfo> devices, final ApiCallback<Void> callback) {
        if (hasBeenReleased()) {
            return;
        }
        getEncryptingThreadHandler().post(new Runnable() {
            @Override
            public void run() {
                // build a devices map
                Map<String, List<String>> devicesIdListByUserId = new HashMap<>();

                for (MXDeviceInfo di : devices) {
                    List<String> deviceIdsList = devicesIdListByUserId.get(di.userId);

                    if (null == deviceIdsList) {
                        deviceIdsList = new ArrayList<>();
                        devicesIdListByUserId.put(di.userId, deviceIdsList);
                    }
                    deviceIdsList.add(di.deviceId);
                }

                Set<String> userIds = devicesIdListByUserId.keySet();

                for (String userId : userIds) {
                    Map<String, MXDeviceInfo> storedDeviceIDs = mCryptoStore.getUserDevices(userId);

                    // sanity checks
                    if (null != storedDeviceIDs) {
                        boolean isUpdated = false;
                        List<String> deviceIds = devicesIdListByUserId.get(userId);

                        for (String deviceId : deviceIds) {
                            MXDeviceInfo device = storedDeviceIDs.get(deviceId);

                            // assume if the device is either verified or blocked
                            // it means that the device is known
                            if ((null != device) && device.isUnknown()) {
                                device.mVerified = MXDeviceInfo.DEVICE_VERIFICATION_UNVERIFIED;
                                isUpdated = true;
                            }
                        }

                        if (isUpdated) {
                            mCryptoStore.storeUserDevices(userId, storedDeviceIDs);
                        }
                    }
                }

                if (null != callback) {
                    getUIHandler().post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onSuccess(null);
                        }
                    });
                }
            }
        });
    }

    /**
     * Update the blocked/verified state of the given device
     *
     * @param verificationStatus the new verification status.
     * @param deviceId           the unique identifier for the device.
     * @param userId             the owner of the device.
     */
    public void setDeviceVerification(final int verificationStatus, final String deviceId, final String userId, final ApiCallback<Void> callback) {
        if (hasBeenReleased()) {
            return;
        }

        final ArrayList<String> userRoomIds = new ArrayList<>();

        Collection<Room> rooms = mSession.getDataHandler().getStore().getRooms();

        for (Room room : rooms) {
            if (room.isEncrypted()) {
                RoomMember roomMember = room.getMember(userId);

                // test if the user joins the room
                if ((null != roomMember) && TextUtils.equals(roomMember.membership, RoomMember.MEMBERSHIP_JOIN)) {
                    userRoomIds.add(room.getRoomId());
                }
            }
        }

        getEncryptingThreadHandler().post(new Runnable() {
            @Override
            public void run() {
                MXDeviceInfo device = mCryptoStore.getUserDevice(deviceId, userId);

                // Sanity check
                if (null == device) {
                    Log.e(LOG_TAG, "## setDeviceVerification() : Unknown device " + userId + ":" + deviceId);
                    if (null != callback) {
                        getUIHandler().post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onSuccess(null);
                            }
                        });
                    }
                    return;
                }

                if (device.mVerified != verificationStatus) {
                    device.mVerified = verificationStatus;
                    mCryptoStore.storeUserDevice(userId, device);
                }

                if (null != callback) {
                    getUIHandler().post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onSuccess(null);
                        }
                    });
                }
            }
        });
    }

    /**
     * Configure a room to use encryption.
     * This method must be called in getEncryptingThreadHandler
     *
     * @param roomId    the room id to enable encryption in.
     * @param algorithm the encryption config for the room.
     * @return true if the operation succeeds.
     */
    private boolean setEncryptionInRoom(String roomId, String algorithm) {
        if (hasBeenReleased()) {
            return false;
        }

        // If we already have encryption in this room, we should ignore this event
        // (for now at least. Maybe we should alert the user somehow?)
        String existingAlgorithm = mCryptoStore.getRoomAlgorithm(roomId);

        if (!TextUtils.isEmpty(existingAlgorithm) && !TextUtils.equals(existingAlgorithm, algorithm)) {
            Log.e(LOG_TAG, "## setEncryptionInRoom() : Ignoring m.room.encryption event which requests a change of config in " + roomId);
            return false;
        }

        Class<IMXEncrypting> encryptingClass = MXCryptoAlgorithms.sharedAlgorithms().encryptorClassForAlgorithm(algorithm);

        if (null == encryptingClass) {
            Log.e(LOG_TAG, "## setEncryptionInRoom() : Unable to encrypt with " + algorithm);
            return false;
        }

        mCryptoStore.storeRoomAlgorithm(roomId, algorithm);

        IMXEncrypting alg;

        try {
            Constructor<?> ctor = encryptingClass.getConstructors()[0];
            alg = (IMXEncrypting) ctor.newInstance();
        } catch (Exception e) {
            Log.e(LOG_TAG, "## setEncryptionInRoom() : fail to load the class");
            return false;
        }

        alg.initWithMatrixSession(mSession, roomId);

        synchronized (mRoomEncryptors) {
            mRoomEncryptors.put(roomId, alg);
        }

        // if encryption was not previously enabled in this room, we will have been
        // ignoring new device events for these users so far. We may well have
        // up-to-date lists for some users, for instance if we were sharing other
        // e2e rooms with them, so there is room for optimisation here, but for now
        // we just invalidate everyone in the room.
        if (null == existingAlgorithm) {
            Log.d(LOG_TAG, "Enabling encryption in " + roomId + " for the first time; invalidating device lists for all users therein");

            Room room = mSession.getDataHandler().getRoom(roomId);
            if (null != room) {
                Collection<RoomMember> members = room.getJoinedMembers();
                List<String> userIds = new ArrayList<>();

                for (RoomMember m : members) {
                    userIds.add(m.getUserId());
                }

                getDeviceList().invalidateUserDeviceList(userIds);
                // the actual refresh happens once we've finished processing the sync,
                // in _onSyncCompleted.
            }
        }

        return true;
    }

    /**
     * Tells if a room is encrypted
     *
     * @param roomId the room id
     * @return true if the room is encrypted
     */
    public boolean isRoomEncrypted(String roomId) {
        boolean res = false;

        if (null != roomId) {
            synchronized (mRoomEncryptors) {
                res = mRoomEncryptors.containsKey(roomId);

                if (!res) {
                    Room room = mSession.getDataHandler().getRoom(roomId);

                    if (null != room) {
                        res = room.getLiveState().isEncrypted();
                    }
                }
            }
        }

        return res;
    }

    /**
     * @return the stored device keys for a user.
     */
    public List<MXDeviceInfo> getUserDevices(final String userId) {
        Map<String, MXDeviceInfo> map = getCryptoStore().getUserDevices(userId);
        return (null != map) ? new ArrayList<>(map.values()) : new ArrayList<MXDeviceInfo>();
    }

    /**
     * Try to make sure we have established olm sessions for the given users.
     * It must be called in getEncryptingThreadHandler() thread.
     * The callback is called in the UI thread.
     *
     * @param users    a list of user ids.
     * @param callback the asynchronous callback
     */
    public void ensureOlmSessionsForUsers(List<String> users, final ApiCallback<MXUsersDevicesMap<MXOlmSessionResult>> callback) {
        Log.d(LOG_TAG, "## ensureOlmSessionsForUsers() : ensureOlmSessionsForUsers " + users);

        HashMap<String /* userId */, ArrayList<MXDeviceInfo>> devicesByUser = new HashMap<>();

        for (String userId : users) {
            devicesByUser.put(userId, new ArrayList<MXDeviceInfo>());

            List<MXDeviceInfo> devices = getUserDevices(userId);

            for (MXDeviceInfo device : devices) {
                String key = device.identityKey();

                if (TextUtils.equals(key, mOlmDevice.getDeviceCurve25519Key())) {
                    // Don't bother setting up session to ourself
                    continue;
                }

                if (device.isVerified()) {
                    // Don't bother setting up sessions with blocked users
                    continue;
                }

                devicesByUser.get(userId).add(device);
            }
        }

        ensureOlmSessionsForDevices(devicesByUser, callback);
    }

    /**
     * Try to make sure we have established olm sessions for the given devices.
     * It must be called in getCryptoHandler() thread.
     * The callback is called in the UI thread.
     *
     * @param devicesByUser a map from userid to list of devices.
     * @param callback      teh asynchronous callback
     */
    public void ensureOlmSessionsForDevices(final HashMap<String, ArrayList<MXDeviceInfo>> devicesByUser, final ApiCallback<MXUsersDevicesMap<MXOlmSessionResult>> callback) {
        ArrayList<MXDeviceInfo> devicesWithoutSession = new ArrayList<>();

        final MXUsersDevicesMap<MXOlmSessionResult> results = new MXUsersDevicesMap<>();

        Set<String> userIds = devicesByUser.keySet();

        for (String userId : userIds) {
            ArrayList<MXDeviceInfo> deviceInfos = devicesByUser.get(userId);

            for (MXDeviceInfo deviceInfo : deviceInfos) {
                String deviceId = deviceInfo.deviceId;
                String key = deviceInfo.identityKey();

                String sessionId = mOlmDevice.getSessionId(key);

                if (TextUtils.isEmpty(sessionId)) {
                    devicesWithoutSession.add(deviceInfo);
                }

                MXOlmSessionResult olmSessionResult = new MXOlmSessionResult(deviceInfo, sessionId);
                results.setObject(olmSessionResult, userId, deviceId);
            }
        }

        if (devicesWithoutSession.size() == 0) {
            if (null != callback) {
                getUIHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onSuccess(results);
                    }
                });
            }
            return;
        }

        // Prepare the request for claiming one-time keys
        MXUsersDevicesMap<String> usersDevicesToClaim = new MXUsersDevicesMap<>();

        final String oneTimeKeyAlgorithm = MXKey.KEY_SIGNED_CURVE_25519_TYPE;

        for (MXDeviceInfo device : devicesWithoutSession) {
            usersDevicesToClaim.setObject(oneTimeKeyAlgorithm, device.userId, device.deviceId);
        }

        // TODO: this has a race condition - if we try to send another message
        // while we are claiming a key, we will end up claiming two and setting up
        // two sessions.
        //
        // That should eventually resolve itself, but it's poor form.

        Log.d(LOG_TAG, "## claimOneTimeKeysForUsersDevices() : " + usersDevicesToClaim);

        mSession.getCryptoRestClient().claimOneTimeKeysForUsersDevices(usersDevicesToClaim, new ApiCallback<MXUsersDevicesMap<MXKey>>() {
            @Override
            public void onSuccess(final MXUsersDevicesMap<MXKey> oneTimeKeys) {
                getEncryptingThreadHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Log.d(LOG_TAG, "## claimOneTimeKeysForUsersDevices() : keysClaimResponse.oneTimeKeys: " + oneTimeKeys);

                            Set<String> userIds = devicesByUser.keySet();

                            for (String userId : userIds) {
                                ArrayList<MXDeviceInfo> deviceInfos = devicesByUser.get(userId);

                                for (MXDeviceInfo deviceInfo : deviceInfos) {

                                    MXKey oneTimeKey = null;

                                    List<String> deviceIds = oneTimeKeys.getUserDeviceIds(userId);

                                    if (null != deviceIds) {
                                        for (String deviceId : deviceIds) {
                                            MXOlmSessionResult olmSessionResult = results.getObject(deviceId, userId);

                                            if (null != olmSessionResult.mSessionId) {
                                                // We already have a result for this device
                                                continue;
                                            }

                                            MXKey key = oneTimeKeys.getObject(deviceId, userId);

                                            if (TextUtils.equals(key.type, oneTimeKeyAlgorithm)) {
                                                oneTimeKey = key;
                                            }

                                            if (null == oneTimeKey) {
                                                Log.d(LOG_TAG, "## ensureOlmSessionsForDevices() : No one-time keys " + oneTimeKeyAlgorithm + " for device " + userId + " : " + deviceId);
                                                continue;
                                            }

                                            // Update the result for this device in results
                                            olmSessionResult.mSessionId = verifyKeyAndStartSession(oneTimeKey, userId, deviceInfo);
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "## ensureOlmSessionsForDevices() " + e.getMessage());
                        }

                        if (!hasBeenReleased()) {
                            if (null != callback) {
                                getUIHandler().post(new Runnable() {
                                    @Override
                                    public void run() {
                                        callback.onSuccess(results);
                                    }
                                });
                            }
                        }
                    }
                });
            }

            @Override
            public void onNetworkError(Exception e) {
                Log.e(LOG_TAG, "## ensureOlmSessionsForUsers(): claimOneTimeKeysForUsersDevices request failed" + e.getMessage());

                if (null != callback) {
                    callback.onNetworkError(e);
                }
            }

            @Override
            public void onMatrixError(MatrixError e) {
                Log.e(LOG_TAG, "## ensureOlmSessionsForUsers(): claimOneTimeKeysForUsersDevices request failed" + e.getLocalizedMessage());

                if (null != callback) {
                    callback.onMatrixError(e);
                }
            }

            @Override
            public void onUnexpectedError(Exception e) {
                Log.e(LOG_TAG, "## ensureOlmSessionsForUsers(): claimOneTimeKeysForUsersDevices request failed" + e.getMessage());

                if (null != callback) {
                    callback.onUnexpectedError(e);
                }
            }
        });
    }

    private String verifyKeyAndStartSession(MXKey oneTimeKey, String userId, MXDeviceInfo deviceInfo) {
        String sessionId = null;

        String deviceId = deviceInfo.deviceId;
        String signKeyId = "ed25519:" + deviceId;
        String signature = oneTimeKey.signatureForUserId(userId, signKeyId);

        if (!TextUtils.isEmpty(signature) && !TextUtils.isEmpty(deviceInfo.fingerprint())) {
            boolean isVerified = false;
            String errorMessage = null;

            try {
                mOlmDevice.verifySignature(deviceInfo.fingerprint(), oneTimeKey.signalableJSONDictionary(), signature);
                isVerified = true;
            } catch (Exception e) {
                errorMessage = e.getMessage();
            }

            // Check one-time key signature
            if (isVerified) {
                sessionId = getOlmDevice().createOutboundSession(deviceInfo.identityKey(), oneTimeKey.value);

                if (!TextUtils.isEmpty(sessionId)) {
                    Log.d(LOG_TAG, "## verifyKeyAndStartSession() : Started new sessionid " + sessionId + " for device " + deviceInfo + "(theirOneTimeKey: " + oneTimeKey.value + ")");
                } else {
                    // Possibly a bad key
                    Log.e(LOG_TAG, "## verifyKeyAndStartSession() : Error starting session with device " + userId + ":" + deviceId);
                }
            } else {
                Log.e(LOG_TAG, "## verifyKeyAndStartSession() : Unable to verify signature on one-time key for device " + userId + ":" + deviceId + " Error " + errorMessage);
            }
        }

        return sessionId;
    }


    /**
     * Encrypt an event content according to the configuration of the room.
     *
     * @param eventContent the content of the event.
     * @param eventType    the type of the event.
     * @param room         the room the event will be sent.
     * @param callback     the asynchronous callback
     */
    public void encryptEventContent(final JsonElement eventContent, final String eventType, final Room room, final ApiCallback<MXEncryptEventContentResult> callback) {
        // wait that the crypto is really started
        if (!isStarted()) {
            Log.d(LOG_TAG, "## encryptEventContent() : wait after e2e init");

            start(false, new ApiCallback<Void>() {
                @Override
                public void onSuccess(Void info) {
                    encryptEventContent(eventContent, eventType, room, callback);
                }

                @Override
                public void onNetworkError(Exception e) {
                    Log.e(LOG_TAG, "## encryptEventContent() : onNetworkError while waiting to start e2e : " + e.getMessage());

                    if (null != callback) {
                        callback.onNetworkError(e);
                    }
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    Log.e(LOG_TAG, "## encryptEventContent() : onMatrixError while waiting to start e2e : " + e.getMessage());

                    if (null != callback) {
                        callback.onMatrixError(e);
                    }
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    Log.e(LOG_TAG, "## encryptEventContent() : onUnexpectedError while waiting to start e2e : " + e.getMessage());

                    if (null != callback) {
                        callback.onUnexpectedError(e);
                    }
                }
            });

            return;
        }

        // just as you are sending a secret message?
        final ArrayList<String> userdIds = new ArrayList<>();

        Collection<RoomMember> joinedMembers = room.getJoinedMembers();

        for (RoomMember m : joinedMembers) {
            userdIds.add(m.getUserId());
        }

        getEncryptingThreadHandler().post(new Runnable() {
            @Override
            public void run() {
                IMXEncrypting alg;

                synchronized (mRoomEncryptors) {
                    alg = mRoomEncryptors.get(room.getRoomId());
                }

                if (null == alg) {
                    String algorithm = room.getLiveState().encryptionAlgorithm();

                    if (null != algorithm) {
                        if (setEncryptionInRoom(room.getRoomId(), algorithm)) {
                            synchronized (mRoomEncryptors) {
                                alg = mRoomEncryptors.get(room.getRoomId());
                            }
                        }
                    }
                }

                if (null != alg) {
                    final long t0 = System.currentTimeMillis();
                    Log.d(LOG_TAG, "## encryptEventContent() starts");

                    alg.encryptEventContent(eventContent, eventType, userdIds, new ApiCallback<JsonElement>() {
                        @Override
                        public void onSuccess(final JsonElement encryptedContent) {
                            Log.d(LOG_TAG, "## encryptEventContent() : succeeds after " + (System.currentTimeMillis() - t0) + " ms");

                            if (null != callback) {
                                callback.onSuccess(new MXEncryptEventContentResult(encryptedContent, Event.EVENT_TYPE_MESSAGE_ENCRYPTED));
                            }
                        }

                        @Override
                        public void onNetworkError(final Exception e) {
                            Log.e(LOG_TAG, "## encryptEventContent() : onNetworkError " + e.getMessage());

                            if (null != callback) {
                                callback.onNetworkError(e);
                            }
                        }

                        @Override
                        public void onMatrixError(final MatrixError e) {
                            Log.e(LOG_TAG, "## encryptEventContent() : onMatrixError " + e.getMessage());

                            if (null != callback) {
                                callback.onMatrixError(e);
                            }
                        }

                        @Override
                        public void onUnexpectedError(final Exception e) {
                            Log.e(LOG_TAG, "## encryptEventContent() : onUnexpectedError " + e.getMessage());

                            if (null != callback) {
                                callback.onUnexpectedError(e);
                            }
                        }
                    });
                } else {
                    final String reason = String.format(MXCryptoError.UNABLE_TO_ENCRYPT_REASON, room.getLiveState().encryptionAlgorithm());
                    Log.e(LOG_TAG, "## encryptEventContent() : " + reason);

                    if (null != callback) {
                        getUIHandler().post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onMatrixError(new MXCryptoError(MXCryptoError.UNABLE_TO_ENCRYPT_ERROR_CODE, MXCryptoError.UNABLE_TO_ENCRYPT, reason));
                            }
                        });
                    }
                }
            }
        });
    }

    /**
     * Decrypt a received event
     *
     * @param event    the raw event.
     * @param timeline the id of the timeline where the event is decrypted. It is used to prevent replay attack.
     * @return true if the decryption was successful.
     */
    public boolean decryptEvent(final Event event, final String timeline) {
        if (null == event) {
            Log.e(LOG_TAG, "## decryptEvent : null event");
            return false;
        }

        final EventContent eventContent = event.getWireEventContent();

        if (null == eventContent) {
            Log.e(LOG_TAG, "## decryptEvent : empty event content");
            return false;
        }

        final ArrayList<Boolean> results = new ArrayList<>();
        final CountDownLatch lock = new CountDownLatch(1);

        getDecryptingThreadHandler().post(new Runnable() {
            @Override
            public void run() {
                boolean result = false;

                IMXDecrypting alg = getRoomDecryptor(event.roomId, eventContent.algorithm);

                if (null == alg) {
                    String reason = String.format(MXCryptoError.UNABLE_TO_DECRYPT_REASON, event.eventId, eventContent.algorithm);

                    Log.e(LOG_TAG, "## decryptEvent() : " + reason);

                    event.setCryptoError(new MXCryptoError(MXCryptoError.UNABLE_TO_DECRYPT_ERROR_CODE, MXCryptoError.UNABLE_TO_DECRYPT, reason));
                } else {
                    result = alg.decryptEvent(event, timeline);

                    if (!result) {
                        Log.e(LOG_TAG, "## decryptEvent() : failed " + event.getCryptoError().getDetailedErrorDescription());
                    }
                }
                results.add(result);
                lock.countDown();
            }
        });

        try {
            lock.await();
        } catch (Exception e) {
            Log.e(LOG_TAG, "## decryptEvent() : failed " + e.getMessage());
        }

        return (results.size() > 0) && results.get(0);
    }

    /**
     * Reset replay attack data for the given timeline.
     *
     * @param timelineId the timeline id
     */
    public void resetReplayAttackCheckInTimeline(final String timelineId) {
        if ((null != timelineId) && (null != getOlmDevice())) {
            getDecryptingThreadHandler().post(new Runnable() {
                @Override
                public void run() {
                    getOlmDevice().resetReplayAttackCheckInTimeline(timelineId);
                }
            });
        }
    }

    /**
     * Encrypt an event payload for a list of devices.
     * This method must be called from the getCryptoHandler() thread.
     *
     * @param payloadFields fields to include in the encrypted payload.
     * @param deviceInfos   list of device infos to encrypt for.
     * @return the content for an m.room.encrypted event.
     */
    public Map<String, Object> encryptMessage(Map<String, Object> payloadFields, List<MXDeviceInfo> deviceInfos) {
        if (hasBeenReleased()) {
            return new HashMap<>();
        }

        HashMap<String, MXDeviceInfo> deviceInfoParticipantKey = new HashMap<>();
        ArrayList<String> participantKeys = new ArrayList<>();

        for (MXDeviceInfo di : deviceInfos) {
            participantKeys.add(di.identityKey());
            deviceInfoParticipantKey.put(di.identityKey(), di);
        }

        HashMap<String, Object> payloadJson = new HashMap<>(payloadFields);

        payloadJson.put("sender", mSession.getMyUserId());
        payloadJson.put("sender_device", mSession.getCredentials().deviceId);

        // Include the Ed25519 key so that the recipient knows what
        // device this message came from.
        // We don't need to include the curve25519 key since the
        // recipient will already know this from the olm headers.
        // When combined with the device keys retrieved from the
        // homeserver signed by the ed25519 key this proves that
        // the curve25519 key and the ed25519 key are owned by
        // the same device.
        HashMap<String, String> keysMap = new HashMap<>();
        keysMap.put("ed25519", mOlmDevice.getDeviceEd25519Key());
        payloadJson.put("keys", keysMap);

        HashMap<String, Object> ciphertext = new HashMap<>();

        for (String deviceKey : participantKeys) {
            String sessionId = mOlmDevice.getSessionId(deviceKey);

            if (!TextUtils.isEmpty(sessionId)) {
                Log.d(LOG_TAG, "Using sessionid " + sessionId + " for device " + deviceKey);
                MXDeviceInfo deviceInfo = deviceInfoParticipantKey.get(deviceKey);

                payloadJson.put("recipient", deviceInfo.userId);

                HashMap<String, String> recipientsKeysMap = new HashMap<>();
                recipientsKeysMap.put("ed25519", deviceInfo.fingerprint());
                payloadJson.put("recipient_keys", recipientsKeysMap);


                String payloadString = JsonUtils.convertToUTF8(JsonUtils.canonicalize(JsonUtils.getGson(false).toJsonTree(payloadJson)).toString());
                ciphertext.put(deviceKey, mOlmDevice.encryptMessage(deviceKey, sessionId, payloadString));
            }
        }

        HashMap<String, Object> res = new HashMap<>();

        res.put("algorithm", MXCryptoAlgorithms.MXCRYPTO_ALGORITHM_OLM);
        res.put("sender_key", mOlmDevice.getDeviceCurve25519Key());
        res.put("ciphertext", ciphertext);

        return res;
    }

    /**
     * Provides the list of e2e rooms
     *
     * @return the list of e2e rooms
     */
    private List<Room> getE2eRooms() {
        List<Room> e2eRooms = new ArrayList<>();

        // sanity checks
        if ((null == mSession.getDataHandler()) || (null == mSession.getDataHandler().getStore())) {
            return e2eRooms;
        }

        List<Room> rooms = new ArrayList<>(mSession.getDataHandler().getStore().getRooms());
        for (Room r : rooms) {
            if (r.isEncrypted()) {
                RoomMember me = r.getMember(mSession.getMyUserId());

                if (null != me) {
                    String membership = me.membership;

                    // ignore any rooms which we have left
                    if (TextUtils.equals(membership, RoomMember.MEMBERSHIP_JOIN) ||
                            TextUtils.equals(membership, RoomMember.MEMBERSHIP_INVITE)) {
                        e2eRooms.add(r);
                    }
                }
            }
        }

        return e2eRooms;
    }

    /**
     * get the users we share an e2e-enabled room with
     *
     * @return {Object<string>} userid->userid map (should be a Set but argh ES6)
     */
    private List<String> getE2eRoomMembers() {
        HashSet<String> list = new HashSet<>();
        List<Room> rooms = getE2eRooms();

        for (Room r : rooms) {
            Collection<RoomMember> activeMembers = r.getActiveMembers();

            for (RoomMember m : activeMembers) {
                // add only the matrix id
                if (MXSession.PATTERN_CONTAIN_MATRIX_USER_IDENTIFIER.matcher(m.getUserId()).matches()) {
                    list.add(m.getUserId());
                }
            }
        }
        return new ArrayList<>(list);
    }

    /**
     * Announce the device to the server.
     * This method must be called from the getCryptoHandler() thread.
     * The callback is called in the UI thread.
     *
     * @param callback the asynchronous callback.
     */
    private void checkDeviceAnnounced(final ApiCallback<Void> callback) {
        if (mCryptoStore.deviceAnnounced()) {
            // Catch up on any m.new_device events which arrived during the initial sync.
            mDevicesList.refreshOutdatedDeviceLists();

            if (null != callback) {
                getUIHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onSuccess(null);
                    }
                });
            }
            return;
        }

        // Catch up on any m.new_device events which arrived during the initial sync.
        // And force download all devices keys  the user already has.
        mDevicesList.addPendingUsersWithNewDevices(Arrays.asList(mMyDevice.userId));
        mDevicesList.refreshOutdatedDeviceLists();

        // We need to tell all the devices in all the rooms we are members of that
        // we have arrived.
        // Build a list of rooms for each user.
        HashMap<String, ArrayList<String>> roomsByUser = new HashMap<>();

        List<Room> rooms = getE2eRooms();

        for (Room room : rooms) {
            // Ignore any rooms which we have left
            RoomMember me = room.getMember(mSession.getMyUserId());

            if ((null == me) || (!TextUtils.equals(me.membership, RoomMember.MEMBERSHIP_JOIN) && !TextUtils.equals(me.membership, RoomMember.MEMBERSHIP_INVITE))) {
                continue;
            }

            Collection<RoomMember> members = room.getLiveState().getMembers();

            for (RoomMember r : members) {
                ArrayList<String> roomIds = roomsByUser.get(r.getUserId());

                if (null == roomIds) {
                    roomIds = new ArrayList<>();
                    roomsByUser.put(r.getUserId(), roomIds);
                }

                roomIds.add(room.getRoomId());
            }
        }

        // Build a per-device message for each user
        MXUsersDevicesMap<Map<String, Object>> contentMap = new MXUsersDevicesMap<>();

        for (String userId : roomsByUser.keySet()) {
            HashMap<String, Map<String, Object>> map = new HashMap<>();

            HashMap<String, Object> submap = new HashMap<>();
            submap.put("device_id", mMyDevice.deviceId);
            submap.put("rooms", roomsByUser.get(userId));

            map.put("*", submap);

            contentMap.setObjects(map, userId);
        }

        if (contentMap.getUserIds().size() > 0) {
            mSession.getCryptoRestClient().sendToDevice(Event.EVENT_TYPE_NEW_DEVICE, contentMap, new ApiCallback<Void>() {
                @Override
                public void onSuccess(Void info) {
                    getEncryptingThreadHandler().post(new Runnable() {
                        @Override
                        public void run() {
                            Log.d(LOG_TAG, "## checkDeviceAnnounced Annoucements done");
                            mCryptoStore.storeDeviceAnnounced();

                            if (null != callback) {
                                getUIHandler().post(new Runnable() {
                                    @Override
                                    public void run() {
                                        callback.onSuccess(null);
                                    }
                                });
                            }
                        }
                    });
                }

                @Override
                public void onNetworkError(Exception e) {
                    Log.e(LOG_TAG, "## checkDeviceAnnounced() : failed " + e.getMessage());
                    if (null != callback) {
                        callback.onNetworkError(e);
                    }
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    Log.e(LOG_TAG, "## checkDeviceAnnounced() : failed " + e.getMessage());
                    if (null != callback) {
                        callback.onMatrixError(e);
                    }
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    Log.e(LOG_TAG, "## checkDeviceAnnounced() : failed " + e.getMessage());
                    if (null != callback) {
                        callback.onUnexpectedError(e);
                    }
                }
            });
        }

        mCryptoStore.storeDeviceAnnounced();
        if (null != callback) {
            getUIHandler().post(new Runnable() {
                @Override
                public void run() {
                    callback.onSuccess(null);
                }
            });
        }
    }

    /**
     * Handle the 'toDevice' event
     *
     * @param event the event
     */
    private void onToDeviceEvent(final Event event) {
        if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_ROOM_KEY)) {
            getDecryptingThreadHandler().post(new Runnable() {
                @Override
                public void run() {
                    onRoomKeyEvent(event);
                }
            });
        } else if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_NEW_DEVICE)) {
            getEncryptingThreadHandler().post(new Runnable() {
                @Override
                public void run() {
                    onNewDeviceEvent(event);
                }
            });
        }
    }

    /**
     * Handle a key event.
     * This method must be called on getDecryptingThreadHandler() thread.
     *
     * @param event the key event.
     */
    private void onRoomKeyEvent(Event event) {
        // sanity check
        if (null == event) {
            Log.e(LOG_TAG, "## onRoomKeyEvent() : null event");
            return;
        }

        RoomKeyContent roomKeyContent = JsonUtils.toRoomKeyContent(event.getContentAsJsonObject());

        String roomId = roomKeyContent.room_id;
        String algorithm = roomKeyContent.algorithm;

        if (TextUtils.isEmpty(roomId) || TextUtils.isEmpty(algorithm)) {
            Log.e(LOG_TAG, "## onRoomKeyEvent() : missing fields");
            return;
        }

        IMXDecrypting alg = getRoomDecryptor(roomId, algorithm);

        if (null == alg) {
            Log.e(LOG_TAG, "## onRoomKeyEvent() : Unable to handle keys for " + algorithm);
            return;
        }

        alg.onRoomKeyEvent(event);
    }

    /**
     * Called when a new device announces itself.
     * This method must be called on getEncryptingThreadHandler() thread.
     *
     * @param event the announcement event.
     */
    private void onNewDeviceEvent(final Event event) {
        String userId = event.getSender();
        final NewDeviceContent newDeviceContent = JsonUtils.toNewDeviceContent(event.getContent());

        if ((null == newDeviceContent.rooms) || (null == newDeviceContent.deviceId)) {
            Log.e(LOG_TAG, "## onNewDeviceEvent() : new_device event missing keys");
            return;
        }

        String deviceId = newDeviceContent.deviceId;
        List<String> rooms = newDeviceContent.rooms;

        Log.d(LOG_TAG, "## onNewDeviceEvent() m.new_device event from " + userId + ":" + deviceId + " for rooms " + rooms);

        if (null != mCryptoStore.getUserDevice(deviceId, userId)) {
            Log.e(LOG_TAG, "## onNewDeviceEvent() : known device; ignoring");
            return;
        }

        mDevicesList.addPendingUsersWithNewDevices(Arrays.asList(userId));
    }

    /**
     * Handle an m.room.encryption event.
     *
     * @param event the encryption event.
     */
    private void onCryptoEvent(final Event event) {
        final EventContent eventContent = event.getWireEventContent();

        getEncryptingThreadHandler().post(new Runnable() {
            @Override
            public void run() {
                setEncryptionInRoom(event.roomId, eventContent.algorithm);
            }
        });
    }

    /**
     * Upload my user's device keys.
     * This method must called on getEncryptingThreadHandler() thread.
     * The callback will called on UI thread.
     *
     * @param callback the asynchronous callback
     */
    private void uploadDeviceKeys(ApiCallback<KeysUploadResponse> callback) {
        // Prepare the device keys data to send
        // Sign it
        String signature = mOlmDevice.signJSON(mMyDevice.signalableJSONDictionary());

        HashMap<String, String> submap = new HashMap<>();
        submap.put("ed25519:" + mMyDevice.deviceId, signature);

        HashMap<String, Map<String, String>> map = new HashMap<>();
        map.put(mSession.getMyUserId(), submap);

        mMyDevice.signatures = map;

        // For now, we set the device id explicitly, as we may not be using the
        // same one as used in login.
        mSession.getCryptoRestClient().uploadKeys(mMyDevice.JSONDictionary(), null, mMyDevice.deviceId, callback);
    }

    /**
     * OTK upload loop
     *
     * @param numberToGenerate the number of key to generate
     * @param callback         the asynchronous callback
     */
    private void uploadLoop(final int numberToGenerate, final ApiCallback<Void> callback) {
        if (numberToGenerate <= 0) {
            // If we don't need to generate any more keys then we are done.
            if (null != callback) {
                getUIHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onSuccess(null);
                    }
                });
            }
            return;
        }

        final int keysThisLoop = Math.min(numberToGenerate, ONE_TIME_KEY_GENERATION_MAX_NUMBER);
        getOlmDevice().generateOneTimeKeys(keysThisLoop);

        uploadOneTimeKeys(new ApiCallback<KeysUploadResponse>() {
            @Override
            public void onSuccess(KeysUploadResponse Response) {
                getEncryptingThreadHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        uploadLoop(numberToGenerate - keysThisLoop, callback);
                    }
                });
            }

            @Override
            public void onNetworkError(Exception e) {
                if (null != callback) {
                    callback.onNetworkError(e);
                }
            }

            @Override
            public void onMatrixError(MatrixError e) {
                if (null != callback) {
                    callback.onMatrixError(e);
                }
            }

            @Override
            public void onUnexpectedError(Exception e) {
                if (null != callback) {
                    callback.onUnexpectedError(e);
                }
            }
        });
    }

    /**
     * Check if the OTK must be uploaded.
     */
    private void maybeUploadOneTimeKeys() {
        maybeUploadOneTimeKeys(null);
    }

    /**
     * Check if the OTK must be uploaded.
     *
     * @param callback the asynchronous callback
     */
    private void maybeUploadOneTimeKeys(final ApiCallback<Void> callback) {
        if (mOneTimeKeyCheckInProgress) {
            getUIHandler().post(new Runnable() {
                @Override
                public void run() {
                    if (null != callback) {
                        callback.onSuccess(null);
                    }
                }
            });
            return;
        }

        if ((System.currentTimeMillis() - mLastOneTimeKeyCheck) < ONE_TIME_KEY_UPLOAD_PERIOD) {
            // we've done a key upload recently.
            getUIHandler().post(new Runnable() {
                @Override
                public void run() {
                    if (null != callback) {
                        callback.onSuccess(null);
                    }
                }
            });
            return;
        }

        mLastOneTimeKeyCheck = System.currentTimeMillis();

        mOneTimeKeyCheckInProgress = true;

        // ask the server how many keys we have
        mSession.getCryptoRestClient().uploadKeys(null, null, mMyDevice.deviceId, new ApiCallback<KeysUploadResponse>() {

            private void uploadKeysDone(String errorMessage) {
                if (null != errorMessage) {
                    Log.e(LOG_TAG, "## maybeUploadOneTimeKeys() : failed " + errorMessage);
                }
                mOneTimeKeyCheckInProgress = false;
            }

            @Override
            public void onSuccess(final KeysUploadResponse keysUploadResponse) {
                getEncryptingThreadHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        if (!hasBeenReleased()) {
                            // We need to keep a pool of one time public keys on the server so that
                            // other devices can start conversations with us. But we can only store
                            // a finite number of private keys in the olm Account object.
                            // To complicate things further then can be a delay between a device
                            // claiming a public one time key from the server and it sending us a
                            // message. We need to keep the corresponding private key locally until
                            // we receive the message.
                            // But that message might never arrive leaving us stuck with duff
                            // private keys clogging up our local storage.
                            // So we need some kind of enginering compromise to balance all of
                            // these factors.
                            long keyCount = keysUploadResponse.oneTimeKeyCountsForAlgorithm("signed_curve25519");

                            // We then check how many keys we can store in the Account object.
                            long maxOneTimeKeys = getOlmDevice().getMaxNumberOfOneTimeKeys();

                            // Try to keep at most half that number on the server. This leaves the
                            // rest of the slots free to hold keys that have been claimed from the
                            // server but we haven't recevied a message for.
                            // If we run out of slots when generating new keys then olm will
                            // discard the oldest private keys first. This will eventually clean
                            // out stale private keys that won't receive a message.
                            int keyLimit = (int) Math.floor(maxOneTimeKeys / 2.0);

                            // We work out how many new keys we need to create to top up the server
                            // If there are too many keys on the server then we don't need to
                            // create any more keys.
                            int numberToGenerate = (int) Math.max(keyLimit - keyCount, 0);

                            uploadLoop(numberToGenerate, new ApiCallback<Void>() {
                                @Override
                                public void onSuccess(Void info) {
                                    Log.d(LOG_TAG, "## maybeUploadOneTimeKeys() : succeeded");
                                    uploadKeysDone(null);

                                    getUIHandler().post(new Runnable() {
                                        @Override
                                        public void run() {
                                            if (null != callback) {
                                                callback.onSuccess(null);
                                            }
                                        }
                                    });
                                }

                                @Override
                                public void onNetworkError(final Exception e) {
                                    uploadKeysDone(e.getMessage());

                                    getUIHandler().post(new Runnable() {
                                        @Override
                                        public void run() {
                                            if (null != callback) {
                                                callback.onNetworkError(e);
                                            }
                                        }
                                    });
                                }

                                @Override
                                public void onMatrixError(final MatrixError e) {
                                    uploadKeysDone(e.getMessage());

                                    getUIHandler().post(new Runnable() {
                                        @Override
                                        public void run() {
                                            if (null != callback) {
                                                callback.onMatrixError(e);
                                            }
                                        }
                                    });
                                }

                                @Override
                                public void onUnexpectedError(final Exception e) {
                                    uploadKeysDone(e.getMessage());
                                    getUIHandler().post(new Runnable() {
                                        @Override
                                        public void run() {
                                            if (null != callback) {
                                                callback.onUnexpectedError(e);
                                            }
                                        }
                                    });
                                }
                            });
                        }
                    }
                });
            }

            @Override
            public void onNetworkError(final Exception e) {
                uploadKeysDone(e.getMessage());

                getUIHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        if (null != callback) {
                            callback.onNetworkError(e);
                        }
                    }
                });
            }

            @Override
            public void onMatrixError(final MatrixError e) {
                uploadKeysDone(e.getMessage());
                getUIHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        if (null != callback) {
                            callback.onMatrixError(e);
                        }
                    }
                });
            }

            @Override
            public void onUnexpectedError(final Exception e) {
                uploadKeysDone(e.getMessage());

                getUIHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        if (null != callback) {
                            callback.onUnexpectedError(e);
                        }
                    }
                });
            }
        });
    }

    /**
     * Upload my user's one time keys.
     * This method must called on getEncryptingThreadHandler() thread.
     * The callback will called on UI thread.
     *
     * @param callback the asynchronous callback
     */
    private void uploadOneTimeKeys(final ApiCallback<KeysUploadResponse> callback) {
        final Map<String, Map<String, String>> oneTimeKeys = mOlmDevice.getOneTimeKeys();
        HashMap<String, Object> oneTimeJson = new HashMap<>();

        Map<String, String> curve25519Map = oneTimeKeys.get("curve25519");

        if (null != curve25519Map) {
            for (String key_id : curve25519Map.keySet()) {
                HashMap<String, Object> k = new HashMap<>();
                k.put("key", curve25519Map.get(key_id));

                // the key is also signed
                String signature = mOlmDevice.signJSON(k);
                HashMap<String, String> submap = new HashMap<>();
                submap.put("ed25519:" + mMyDevice.deviceId, signature);

                HashMap<String, Map<String, String>> map = new HashMap<>();
                map.put(mSession.getMyUserId(), submap);
                k.put("signatures", map);

                oneTimeJson.put("signed_curve25519:" + key_id, k);
            }
        }

        // For now, we set the device id explicitly, as we may not be using the
        // same one as used in login.
        mSession.getCryptoRestClient().uploadKeys(null, oneTimeJson, mMyDevice.deviceId, new ApiCallback<KeysUploadResponse>() {
            @Override
            public void onSuccess(final KeysUploadResponse info) {
                getEncryptingThreadHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        if (!hasBeenReleased()) {
                            mLastPublishedOneTimeKeys = oneTimeKeys;
                            mOlmDevice.markKeysAsPublished();

                            if (null != callback) {
                                getUIHandler().post(new Runnable() {
                                    @Override
                                    public void run() {
                                        callback.onSuccess(info);
                                    }
                                });
                            }
                        }
                    }
                });
            }

            @Override
            public void onNetworkError(Exception e) {
                if (null != callback) {
                    callback.onNetworkError(e);
                }
            }

            @Override
            public void onMatrixError(MatrixError e) {
                if (null != callback) {
                    callback.onMatrixError(e);
                }
            }

            @Override
            public void onUnexpectedError(Exception e) {
                if (null != callback) {
                    callback.onUnexpectedError(e);
                }
            }
        });
    }

    /**
     * Get a decryptor for a given room and algorithm.
     * If we already have a decryptor for the given room and algorithm, return
     * it. Otherwise try to instantiate it.
     *
     * @param roomId    the room id
     * @param algorithm the crypto algorithm
     * @return the decryptor
     */
    private IMXDecrypting getRoomDecryptor(String roomId, String algorithm) {
        // sanity check
        if (TextUtils.isEmpty(algorithm)) {
            Log.e(LOG_TAG, "## getRoomDecryptor() : null algorithm");
            return null;
        }

        if (null == mRoomDecryptors) {
            Log.e(LOG_TAG, "## getRoomDecryptor() : null mRoomDecryptors");
            return null;
        }

        IMXDecrypting alg = null;

        if (!TextUtils.isEmpty(roomId)) {
            synchronized (mRoomDecryptors) {
                if (!mRoomDecryptors.containsKey(roomId)) {
                    mRoomDecryptors.put(roomId, new HashMap<String, IMXDecrypting>());
                }

                alg = mRoomDecryptors.get(roomId).get(algorithm);
            }

            if (null != alg) {
                return alg;
            }
        }

        Class<IMXDecrypting> decryptingClass = MXCryptoAlgorithms.sharedAlgorithms().decryptorClassForAlgorithm(algorithm);

        if (null != decryptingClass) {
            try {
                Constructor<?> ctor = decryptingClass.getConstructors()[0];
                alg = (IMXDecrypting) ctor.newInstance();

                if (null != alg) {
                    alg.initWithMatrixSession(mSession);

                    if (!TextUtils.isEmpty(roomId)) {
                        synchronized (mRoomDecryptors) {
                            mRoomDecryptors.get(roomId).put(algorithm, alg);
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "## getRoomDecryptor() : fail to load the class");
                return null;
            }
        }

        return alg;
    }


    /**
     * Export the crypto keys
     *
     * @param password the password
     * @param callback the exported keys
     */
    public void exportRoomKeys(final String password, final ApiCallback<byte[]> callback) {
        getDecryptingThreadHandler().post(new Runnable() {
            @Override
            public void run() {
                ArrayList<Map<String, Object>> exportedSessions = new ArrayList<>();

                List<MXOlmInboundGroupSession2> inboundGroupSessions = mCryptoStore.getInboundGroupSessions();

                for (MXOlmInboundGroupSession2 session : inboundGroupSessions) {
                    Map<String, Object> map = session.exportKeys();

                    if (null != map) {
                        exportedSessions.add(map);
                    }
                }

                final byte[] encryptedRoomKeys;

                try {
                    String allo = JsonUtils.getGson(false).toJsonTree(exportedSessions).toString();
                    encryptedRoomKeys = MXMegolmExportEncryption.encryptMegolmKeyFile(allo, password);
                } catch (Exception e) {
                    callback.onUnexpectedError(e);
                    return;
                }

                getUIHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onSuccess(encryptedRoomKeys);
                    }
                });
            }
        });
    }

    /**
     * Import the room keys
     *
     * @param roomKeysAsArray the room keys as array.
     * @param password        the password
     * @param callback        the asynchronous callback.
     */
    public void importRoomKeys(final byte[] roomKeysAsArray, final String password, final ApiCallback<Void> callback) {
        getDecryptingThreadHandler().post(new Runnable() {
            @Override
            public void run() {
                long t0 = System.currentTimeMillis();
                String roomKeys;

                try {
                    roomKeys = MXMegolmExportEncryption.decryptMegolmKeyFile(roomKeysAsArray, password);
                } catch (final Exception e) {
                    getUIHandler().post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onUnexpectedError(e);
                        }
                    });
                    return;
                }

                List<Map<String, Object>> importedSessions;

                long t1 = System.currentTimeMillis();

                Log.d(LOG_TAG, "## importRoomKeys starts");

                try {
                    importedSessions = JsonUtils.getGson(false).fromJson(roomKeys, new TypeToken<List<Map<String, Object>>>() {
                    }.getType());
                } catch (final Exception e) {
                    Log.e(LOG_TAG, "## importRoomKeys failed " + e.getMessage());
                    getUIHandler().post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onUnexpectedError(e);
                        }
                    });
                    return;
                }

                long t2 = System.currentTimeMillis();

                Log.d(LOG_TAG, "## importRoomKeys retrieve " + importedSessions.size() + "sessions in " + (t1 - t0) + " ms");

                for (int index = 0; index < importedSessions.size(); index++) {
                    Map<String, Object> map = importedSessions.get(index);

                    MXOlmInboundGroupSession2 session = mOlmDevice.importInboundGroupSession(map);

                    if ((null != session) && mRoomDecryptors.containsKey(session.mRoomId)) {
                        IMXDecrypting decrypting = mRoomDecryptors.get(session.mRoomId).get(map.get("algorithm"));

                        if (null != decrypting) {
                            try {
                                String sessionId = session.mSession.sessionIdentifier();
                                Log.d(LOG_TAG, "## importRoomKeys retrieve mSenderKey " + session.mSenderKey + " sessionId " + sessionId);

                                decrypting.onNewSession(session.mSenderKey, sessionId);
                            } catch (Exception e) {
                                Log.e(LOG_TAG, "## importRoomKeys() : onNewSession failed " + e.getMessage());
                            }
                        }
                    }
                }

                long t3 = System.currentTimeMillis();

                Log.d(LOG_TAG, "## importRoomKeys : done in " + (t3 - t0) + " ms (" + importedSessions.size() + " sessions)");
                Log.d(LOG_TAG, "## importRoomKeys : decryptMegolmKeyFile done in " + (t1 - t0) + " ms");
                Log.d(LOG_TAG, "## importRoomKeys : JSON parsing " + (t2 - t1) + " ms");
                Log.d(LOG_TAG, "## importRoomKeys : sessions import " + (t3 - t2) + " ms");

                getUIHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onSuccess(null);
                    }
                });
            }
        });
    }

    /**
     * Tells if the encryption must fail if some unknown devices are detected.
     *
     * @return true to warn when some unknown devices are detected.
     */
    public boolean warnOnUnknownDevices() {
        return mWarnOnUnknownDevices;
    }

    /**
     * Update the warn status when some unknown devices are detected.
     *
     * @param warn true to warn when some unknown devices are detected.
     */
    public void setWarnOnUnknownDevices(boolean warn) {
        mWarnOnUnknownDevices = warn;
    }

    /**
     * Provides the list of unknown devices
     *
     * @param devicesInRoom the devices map
     * @return the unknown devices map
     */
    public static MXUsersDevicesMap<MXDeviceInfo> getUnknownDevices(MXUsersDevicesMap<MXDeviceInfo> devicesInRoom) {
        MXUsersDevicesMap<MXDeviceInfo> unknownDevices = new MXUsersDevicesMap<>();

        List<String> userIds = devicesInRoom.getUserIds();
        for (String userId : userIds) {
            List<String> deviceIds = devicesInRoom.getUserDeviceIds(userId);
            for (String deviceId : deviceIds) {
                MXDeviceInfo deviceInfo = devicesInRoom.getObject(deviceId, userId);

                if (deviceInfo.isUnknown()) {
                    unknownDevices.setObject(deviceInfo, userId, deviceId);
                }
            }
        }

        return unknownDevices;
    }

    /**
     * Check if the user ids list have some unknown devices.
     * A success means there is no unknown devices.
     * If there are some unknown devices, a MXCryptoError.UNKNOWN_DEVICES_CODE exception is triggered.
     *
     * @param userIds  the user ids list
     * @param callback the asynchronous callback.
     */
    public void checkUnknownDevices(List<String> userIds, final ApiCallback<Void> callback) {
        // force the refresh to ensure that the devices list is up-to-date
        mDevicesList.downloadKeys(userIds, true, new ApiCallback<MXUsersDevicesMap<MXDeviceInfo>>() {
            @Override
            public void onSuccess(MXUsersDevicesMap<MXDeviceInfo> devicesMap) {
                MXUsersDevicesMap<MXDeviceInfo> unknownDevices = MXCrypto.getUnknownDevices(devicesMap);

                if (unknownDevices.getMap().size() == 0) {
                    callback.onSuccess(null);
                } else {
                    // trigger an an unknown devices exception
                    callback.onMatrixError(new MXCryptoError(MXCryptoError.UNKNOWN_DEVICES_CODE, MXCryptoError.UNABLE_TO_ENCRYPT, MXCryptoError.UNKNOWN_DEVICES_REASON, unknownDevices));
                }
            }

            @Override
            public void onNetworkError(Exception e) {
                callback.onNetworkError(e);
            }

            @Override
            public void onMatrixError(MatrixError e) {
                callback.onMatrixError(e);
            }

            @Override
            public void onUnexpectedError(Exception e) {
                callback.onUnexpectedError(e);
            }
        });
    }

    /**
     * Set the global override for whether the client should ever send encrypted
     * messages to unverified devices.
     * If false, it can still be overridden per-room.
     * If true, it overrides the per-room settings.
     *
     * @param block    true to unilaterally blacklist all
     * @param callback the asynchronous callback.
     */
    public void setGlobalBlacklistUnverifiedDevices(final boolean block, final ApiCallback<Void> callback) {
        final String userId = mSession.getMyUserId();
        final ArrayList<String> userRoomIds = new ArrayList<>();

        Collection<Room> rooms = mSession.getDataHandler().getStore().getRooms();

        for (Room room : rooms) {
            if (room.isEncrypted()) {
                RoomMember roomMember = room.getMember(userId);

                // test if the user joins the room
                if ((null != roomMember) && TextUtils.equals(roomMember.membership, RoomMember.MEMBERSHIP_JOIN)) {
                    userRoomIds.add(room.getRoomId());
                }
            }
        }

        getEncryptingThreadHandler().post(new Runnable() {
            @Override
            public void run() {
                mCryptoStore.setGlobalBlacklistUnverifiedDevices(block);
                getUIHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        if (null != callback) {
                            callback.onSuccess(null);
                        }
                    }
                });
            }
        });
    }

    /**
     * Tells whether the client should ever send encrypted messages to unverified devices.
     * The default value is false.
     * This function must be called in the getEncryptingThreadHandler() thread.
     *
     * @return true to unilaterally blacklist all unverified devices.
     */
    public boolean getGlobalBlacklistUnverifiedDevices() {
        return mCryptoStore.getGlobalBlacklistUnverifiedDevices();
    }

    /**
     * Tells whether the client should ever send encrypted messages to unverified devices.
     * The default value is false.
     * messages to unverified devices.
     *
     * @param callback the asynchronous callback
     */
    public void getGlobalBlacklistUnverifiedDevices(final ApiCallback<Boolean> callback) {
        getEncryptingThreadHandler().post(new Runnable() {
            @Override
            public void run() {
                if (null != callback) {
                    final boolean status = getGlobalBlacklistUnverifiedDevices();

                    getUIHandler().post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onSuccess(status);
                        }
                    });
                }
            }
        });
    }

    /**
     * Tells whether the client should encrypt messages only for the verified devices
     * in this room.
     * The default value is false.
     * This function must be called in the getEncryptingThreadHandler() thread.
     *
     * @param roomId the room id
     * @return true if the client should encrypt messages only for the verified devices.
     */
    public boolean isRoomBlacklistUnverifiedDevices(String roomId) {
        if (null != roomId) {
            return mCryptoStore.getRoomsListBlacklistUnverifiedDevices().contains(roomId);
        } else {
            return false;
        }
    }

    /**
     * Tells whether the client should encrypt messages only for the verified devices
     * in this room.
     * The default value is false.
     * This function must be called in the getEncryptingThreadHandler() thread.
     *
     * @param roomId   the room id
     * @param callback the asynchronous callback
     */
    public void isRoomBlacklistUnverifiedDevices(final String roomId, final ApiCallback<Boolean> callback) {
        getEncryptingThreadHandler().post(new Runnable() {
            @Override
            public void run() {
                final boolean status = isRoomBlacklistUnverifiedDevices(roomId);

                getUIHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        if (null != callback) {
                            callback.onSuccess(status);
                        }
                    }
                });
            }
        });
    }

    /**
     * Manages the room black-listing for unverified devices.
     *
     * @param roomId   the room id
     * @param add      true to add the room id to the list, false to remove it.
     * @param callback the asynchronous callback
     */
    private void setRoomBlacklistUnverifiedDevices(final String roomId, final boolean add, final ApiCallback<Void> callback) {
        final Room room = mSession.getDataHandler().getRoom(roomId);

        // sanity check
        if (null == room) {
            getUIHandler().post(new Runnable() {
                @Override
                public void run() {
                    callback.onSuccess(null);
                }
            });

            return;
        }

        getEncryptingThreadHandler().post(new Runnable() {
            @Override
            public void run() {
                List<String> roomIds = mCryptoStore.getRoomsListBlacklistUnverifiedDevices();

                if (add) {
                    if (!roomIds.contains(roomId)) {
                        roomIds.add(roomId);
                    }
                } else {
                    roomIds.remove(roomId);
                }

                mCryptoStore.setRoomsListBlacklistUnverifiedDevices(roomIds);

                getUIHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        if (null != callback) {
                            callback.onSuccess(null);
                        }
                    }
                });
            }
        });
    }


    /**
     * Add this room to the ones which don't encrypt messages to unverified devices.
     *
     * @param roomId   the room id
     * @param callback the asynchronous callback
     */
    public void setRoomBlacklistUnverifiedDevices(final String roomId, final ApiCallback<Void> callback) {
        setRoomBlacklistUnverifiedDevices(roomId, true, callback);
    }

    /**
     * Remove this room to the ones which don't encrypt messages to unverified devices.
     *
     * @param roomId   the room id
     * @param callback the asynchronous callback
     */
    public void setRoomUnblacklistUnverifiedDevices(final String roomId, final ApiCallback<Void> callback) {
        setRoomBlacklistUnverifiedDevices(roomId, false, callback);
    }
}