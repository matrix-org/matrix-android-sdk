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


package org.matrix.androidsdk;

import android.content.Context;
import android.net.Uri;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.text.TextUtils;

import com.google.gson.JsonObject;

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.matrix.androidsdk.crypto.MXCryptoAlgorithms;
import org.matrix.androidsdk.crypto.data.MXDeviceInfo;
import org.matrix.androidsdk.crypto.data.MXOlmSessionResult;
import org.matrix.androidsdk.crypto.data.MXUsersDevicesMap;
import org.matrix.androidsdk.data.IMXStore;
import org.matrix.androidsdk.data.MXFileStore;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.Message;
import org.matrix.androidsdk.rest.model.login.Credentials;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class CryptoTest {

    private static final List<String> messagesFromAlice = Arrays.asList("0 - Hello I'm Alice!", "4 - Go!");
    private static final List<String> messagesFromBob = Arrays.asList("1 - Hello I'm Bob!", "2 - Isn't life grand?", "3 - Let's go to the opera.");

    private static final String MXTESTS_BOB  = "mxBob";
    private static final String MXTESTS_BOB_PWD = "bobbob";

    private static final String MXTESTS_ALICE = "mxAlice";
    private static final String MXTESTS_ALICE_PWD = "alicealice";


    @Test
    public void test01_testCryptoNoDeviceId() throws Exception {
        Context context = InstrumentationRegistry.getContext();
        createBobAccount();

        assert (null == mBobSession.getCrypto());
        mBobSession.getCredentials().deviceId = null;

        mBobSession.setCryptoEnabled(true);
        assert (null != mBobSession.getCrypto());
        assert (null != mBobSession.getCredentials().deviceId);

        mBobSession.clear(context);
    }

    @Test
    public void test02_testCryptoPersistenceInStore() throws Exception {
        Context context = InstrumentationRegistry.getContext();

        createBobAccount();
        mBobSession.getCredentials().deviceId = "BobDevice";

        assert (null == mBobSession.getCrypto());
        mBobSession.setCryptoEnabled(true);

        assert (null != mBobSession.getCrypto());

        final String deviceCurve25519Key = mBobSession.getCrypto().getOlmDevice().getDeviceCurve25519Key();
        final String deviceEd25519Key = mBobSession.getCrypto().getOlmDevice().getDeviceEd25519Key();

        final List<MXDeviceInfo> myUserDevices = mBobSession.getCrypto().storedDevicesForUser(mBobSession.getMyUserId());

        assert (null != myUserDevices);
        assert (1 == myUserDevices.size());


        final Credentials bobCredentials = mBobSession.getCredentials();

        Uri uri = Uri.parse(CryptoTestHelper.TESTS_HOME_SERVER_URL);
        HomeserverConnectionConfig hs = new HomeserverConnectionConfig(uri);
        hs.setCredentials(bobCredentials);

        IMXStore store =  new MXFileStore(hs, context);

        MXSession bobSession2 = new MXSession(hs, new MXDataHandler(store, bobCredentials, new MXDataHandler.InvalidTokenListener() {
            @Override
            public void onTokenCorrupted() {
            }
        }), context);


        bobSession2.getDataHandler().getStore().open();
        bobSession2.startEventStream(null);

        mLock = new CountDownLatch(1);
        bobSession2.getDataHandler().addListener(new MXEventListener() {
            @Override
            public void onStoreReady() {
                mLock.countDown();
            }
        });

        mLock.await(100000, TimeUnit.DAYS.MILLISECONDS);


        assert (bobSession2.isCryptoEnabled());
        assert (null != bobSession2.getCrypto());
        assert (TextUtils.equals(deviceCurve25519Key, bobSession2.getCrypto().getOlmDevice().getDeviceCurve25519Key()));
        assert (TextUtils.equals(deviceEd25519Key, bobSession2.getCrypto().getOlmDevice().getDeviceEd25519Key()));

        List<MXDeviceInfo> myUserDevices2 = bobSession2.getCrypto().storedDevicesForUser(bobSession2.getMyUserId());
        assert (1 == myUserDevices2.size());
        assert (TextUtils.equals(myUserDevices2.get(0).deviceId, myUserDevices.get(0).deviceId));

        mBobSession.clear(context);
        bobSession2.clear(context);
    }

    @Test
    public void test03_testKeysUploadAndDownload() throws Exception {
        Context context = InstrumentationRegistry.getContext();
        final HashMap<String, Object> results = new HashMap<>();

        createAliceAccount();
        mAliceSession.getCredentials().deviceId = "AliceDevice";
        mAliceSession.setCryptoEnabled(true);

        mLock = new CountDownLatch(1);
        mAliceSession.getCrypto().uploadKeys(10, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                results.put("uploadKeys", "uploadKeys");
                mLock.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {
                mLock.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                mLock.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                mLock.countDown();
            }
        });

        mLock.await(10000, TimeUnit.DAYS.MILLISECONDS);
        assert(results.containsKey("uploadKeys"));

        createBobAccount();
        mBobSession.setCryptoEnabled(true);
        mBobSession.getCredentials().deviceId = "BobDevice";

        mLock = new CountDownLatch(1);
        mBobSession.getCrypto().downloadKeys(Arrays.asList(mAliceSession.getMyUserId()), false, new ApiCallback<MXUsersDevicesMap<MXDeviceInfo>>() {
            @Override
            public void onSuccess(MXUsersDevicesMap<MXDeviceInfo> info) {
                results.put("downloadKeys", info);
                mLock.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {
                mLock.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                mLock.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                mLock.countDown();
            }
        });

        mLock.await(10000, TimeUnit.DAYS.MILLISECONDS);
        assert(results.containsKey("downloadKeys"));
        MXUsersDevicesMap<MXDeviceInfo> usersDevicesInfoMap = (MXUsersDevicesMap<MXDeviceInfo> )results.get("downloadKeys");

        assert (2 == usersDevicesInfoMap.userIds().size());

        assert (1 == usersDevicesInfoMap.deviceIdsForUser(mAliceSession.getMyUserId()).size());


        MXDeviceInfo aliceDeviceFromBobPOV = usersDevicesInfoMap.objectForDevice("AliceDevice", mAliceSession.getMyUserId());
        assert (null != aliceDeviceFromBobPOV);
        assert (TextUtils.equals(aliceDeviceFromBobPOV.fingerprint(), mAliceSession.getCrypto().getOlmDevice().getDeviceEd25519Key()));

        // Continue testing other methods
        assert (null != mBobSession.getCrypto().deviceWithIdentityKey(mAliceSession.getCrypto().getOlmDevice().getDeviceCurve25519Key(), mAliceSession.getMyUserId(), MXCryptoAlgorithms.MXCRYPTO_ALGORITHM_OLM));
        assert (aliceDeviceFromBobPOV.mVerified == MXDeviceInfo.DEVICE_VERIFICATION_UNVERIFIED);

        mBobSession.getCrypto().setDeviceVerification(MXDeviceInfo.DEVICE_VERIFICATION_BLOCKED, aliceDeviceFromBobPOV.deviceId, mAliceSession.getMyUserId());
        assert (aliceDeviceFromBobPOV.mVerified == MXDeviceInfo.DEVICE_VERIFICATION_BLOCKED);

        Credentials bobCredentials = mBobSession.getCredentials();

        Uri uri = Uri.parse(CryptoTestHelper.TESTS_HOME_SERVER_URL);
        HomeserverConnectionConfig hs = new HomeserverConnectionConfig(uri);
        hs.setCredentials(bobCredentials);

        IMXStore store =  new MXFileStore(hs, context);

        MXSession bobSession2 = new MXSession(hs, new MXDataHandler(store, bobCredentials, new MXDataHandler.InvalidTokenListener() {
            @Override
            public void onTokenCorrupted() {
            }
        }), context);


        bobSession2.getDataHandler().getStore().open();
        bobSession2.startEventStream(null);

        mLock = new CountDownLatch(1);
        bobSession2.getDataHandler().addListener(new MXEventListener() {
            @Override
            public void onStoreReady() {
                mLock.countDown();
            }
        });

        mLock.await(100000, TimeUnit.DAYS.MILLISECONDS);

        MXDeviceInfo aliceDeviceFromBobPOV2 = bobSession2.getCrypto().deviceWithIdentityKey(mAliceSession.getCrypto().getOlmDevice().getDeviceCurve25519Key(), mAliceSession.getMyUserId(), MXCryptoAlgorithms.MXCRYPTO_ALGORITHM_OLM);

        assert (null != aliceDeviceFromBobPOV2);
        assert (TextUtils.equals(aliceDeviceFromBobPOV2.fingerprint(), mAliceSession.getCrypto().getOlmDevice().getDeviceEd25519Key()));
        assert (aliceDeviceFromBobPOV2.mVerified == MXDeviceInfo.DEVICE_VERIFICATION_BLOCKED);

        // Download again alice device
        mLock = new CountDownLatch(1);
        bobSession2.getCrypto().downloadKeys(Arrays.asList(mAliceSession.getMyUserId()), true, new ApiCallback<MXUsersDevicesMap<MXDeviceInfo>>() {
            @Override
            public void onSuccess(MXUsersDevicesMap<MXDeviceInfo> info) {
                results.put("downloadKeys2", info);
                mLock.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {
                mLock.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                mLock.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                mLock.countDown();
            }
        });
        mLock.await(10000, TimeUnit.DAYS.MILLISECONDS);
        assert(results.containsKey("downloadKeys2"));

        MXDeviceInfo aliceDeviceFromBobPOV3 = bobSession2.getCrypto().deviceWithIdentityKey(mAliceSession.getCrypto().getOlmDevice().getDeviceCurve25519Key(), mAliceSession.getMyUserId(), MXCryptoAlgorithms.MXCRYPTO_ALGORITHM_OLM);

        assert (null != aliceDeviceFromBobPOV3);
        assert (TextUtils.equals(aliceDeviceFromBobPOV3.fingerprint(), mAliceSession.getCrypto().getOlmDevice().getDeviceCurve25519Key()));
        assert(aliceDeviceFromBobPOV3.mVerified == MXDeviceInfo.DEVICE_VERIFICATION_BLOCKED);

        mAliceSession.clear(context);
        mBobSession.clear(context);
        bobSession2.clear(context);

    }

    @Test
    public void test04_testEnsureOlmSessionsForUsers() throws Exception {
        Context context = InstrumentationRegistry.getContext();

        createAliceAccount();
        final HashMap<String, Object> results = new HashMap<>();

        mAliceSession.getCredentials().deviceId = "AliceDevice";
        mAliceSession.setCryptoEnabled(true);

        mLock = new CountDownLatch(1);
        mAliceSession.getCrypto().uploadKeys(10, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                results.put("uploadKeys", "uploadKeys");
                mLock.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {
                mLock.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                mLock.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                mLock.countDown();
            }
        });
        mLock.await(10000, TimeUnit.DAYS.MILLISECONDS);
        assert(results.containsKey("uploadKeys"));

        createBobAccount();
        mBobSession.setCryptoEnabled(true);

        mLock = new CountDownLatch(1);
        mBobSession.getCrypto().downloadKeys(Arrays.asList(mBobSession.getMyUserId(), mAliceSession.getMyUserId()), false, new ApiCallback<MXUsersDevicesMap<MXDeviceInfo>>() {
            @Override
            public void onSuccess(MXUsersDevicesMap<MXDeviceInfo> map) {
                results.put("downloadKeys", map);
                mLock.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {
                mLock.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                mLock.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                mLock.countDown();
            }
        });

        mLock.await(10000, TimeUnit.DAYS.MILLISECONDS);
        assert(results.containsKey("downloadKeys"));


        mLock = new CountDownLatch(1);
        mBobSession.getCrypto().ensureOlmSessionsForUsers(Arrays.asList(mBobSession.getMyUserId(), mAliceSession.getMyUserId()), new ApiCallback<MXUsersDevicesMap<MXOlmSessionResult>>() {
            @Override
            public void onSuccess(MXUsersDevicesMap<MXOlmSessionResult> info) {
                results.put("ensureOlmSessionsForUsers", info);
                mLock.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {
                mLock.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                mLock.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                mLock.countDown();
            }
        });

        mLock.await(10000, TimeUnit.DAYS.MILLISECONDS);
        assert(results.containsKey("ensureOlmSessionsForUsers"));

        MXUsersDevicesMap<MXOlmSessionResult> result = (MXUsersDevicesMap<MXOlmSessionResult>)results.get("ensureOlmSessionsForUsers");

        assert (result.userIds().size() == 1);

        MXOlmSessionResult sessionWithAliceDevice = result.objectForDevice("AliceDevice", mAliceSession.getMyUserId());

        assert (null != sessionWithAliceDevice);
        assert (null != sessionWithAliceDevice.mSessionId);
        assert (TextUtils.equals(sessionWithAliceDevice.mDevice.deviceId, "AliceDevice"));


        Credentials bobCredentials = mBobSession.getCredentials();

        Uri uri = Uri.parse(CryptoTestHelper.TESTS_HOME_SERVER_URL);
        HomeserverConnectionConfig hs = new HomeserverConnectionConfig(uri);
        hs.setCredentials(bobCredentials);

        IMXStore store =  new MXFileStore(hs, context);

        MXSession bobSession2 = new MXSession(hs, new MXDataHandler(store, bobCredentials, new MXDataHandler.InvalidTokenListener() {
            @Override
            public void onTokenCorrupted() {
            }
        }), context);


        bobSession2.getDataHandler().getStore().open();
        bobSession2.startEventStream(null);

        mLock = new CountDownLatch(1);
        bobSession2.getDataHandler().addListener(new MXEventListener() {
            @Override
            public void onStoreReady() {
                mLock.countDown();
            }
        });
        mLock.await(10000, TimeUnit.DAYS.MILLISECONDS);

        mLock = new CountDownLatch(1);
        bobSession2.getCrypto().ensureOlmSessionsForUsers(Arrays.asList(bobSession2.getMyUserId(), mAliceSession.getMyUserId()), new ApiCallback<MXUsersDevicesMap<MXOlmSessionResult>>() {
            @Override
            public void onSuccess(MXUsersDevicesMap<MXOlmSessionResult> info) {
                results.put("ensureOlmSessionsForUsers2", info);
                mLock.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {
                mLock.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                mLock.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                mLock.countDown();
            }
        });
        mLock.await(10000, TimeUnit.DAYS.MILLISECONDS);
        assert (results.containsKey("ensureOlmSessionsForUsers2"));

        MXUsersDevicesMap<MXOlmSessionResult> result2 = (MXUsersDevicesMap<MXOlmSessionResult>)results.get("ensureOlmSessionsForUsers2");

        MXOlmSessionResult sessionWithAliceDevice2 = result2.objectForDevice("AliceDevice", mAliceSession.getMyUserId());
        assert (null != sessionWithAliceDevice2);
        assert (null != sessionWithAliceDevice2.mSessionId);
        assert (TextUtils.equals(sessionWithAliceDevice2.mSessionId, "AliceDevice"));

        mBobSession.clear(context);
        mAliceSession.clear(context);
        bobSession2.clear(context);
    }

    @Test
    public void test05_testRoomIsEncrypted() throws Exception {
        Context context = InstrumentationRegistry.getContext();
        final HashMap<String, Object> results = new HashMap<>();

        createBobAccount();
        mBobSession.setCryptoEnabled(true);

        mRoomId = null;

        mLock = new CountDownLatch(1);
        mBobSession.createRoom(new ApiCallback<String>() {
            @Override
            public void onSuccess(String info) {
                mRoomId = info;
                mLock.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {
                mLock.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                mLock.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                mLock.countDown();
            }
        });

        mLock.await(10000, TimeUnit.DAYS.MILLISECONDS);
        assert (null != mRoomId);

        Room room = mBobSession.getDataHandler().getRoom(mRoomId);

        assert (!room.isEncrypted());

        mLock = new CountDownLatch(1);
        room.enableEncryptionWithAlgorithm(MXCryptoAlgorithms.MXCRYPTO_ALGORITHM_MEGOLM, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                results.put("enableEncryptionWithAlgorithm", "enableEncryptionWithAlgorithm");
                mLock.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {
                mLock.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                mLock.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                mLock.countDown();
            }
        });
        mLock.await(10000, TimeUnit.DAYS.MILLISECONDS);
        assert (results.containsKey("enableEncryptionWithAlgorithm"));

        assert (room.isEncrypted());

        mBobSession.clear(context);
    }

    @Test
    public void test06_testAliceInACryptedRoom() throws Exception {
        Context context = InstrumentationRegistry.getContext();
        final HashMap<String, Object> results = new HashMap<>();

        doE2ETestWithAliceInARoom();

        final String message = "Hello myself!";

        Room roomFromAlicePOV = mAliceSession.getDataHandler().getRoom(mRoomId);

        assert (roomFromAlicePOV.isEncrypted());

        mLock = new CountDownLatch(2);

        MXEventListener eventListener = new MXEventListener() {
            @Override
            public void onLiveEvent(Event event, RoomState roomState) {
                try {
                    if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE)) {
                        if (checkEncryptedEvent(event, mRoomId, message, mAliceSession)) {
                            results.put("onLiveEvent", "onLiveEvent");
                            mLock.countDown();
                        }
                    }
                } catch (Exception e) {
                }
            }
        };

        roomFromAlicePOV.addEventListener(eventListener);

        roomFromAlicePOV.sendEvent(buildTextEvent(message, mAliceSession), new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                mLock.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {
                mLock.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                mLock.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                mLock.countDown();
            }
        });

        mLock.await(10000, TimeUnit.DAYS.MILLISECONDS);
        assert (results.containsKey("onLiveEvent"));

        mBobSession.clear(context);
    }

    @Test
    public void test07_testAliceAndBobInACryptedRoom() throws Exception {
        Context context = InstrumentationRegistry.getContext();
        final HashMap<String, Object> results = new HashMap<>();

        doE2ETestWithAliceAndBobInARoom(true);

        final String messageFromAlice = "Hello I'm Alice!";

        Room roomFromBobPOV = mBobSession.getDataHandler().getRoom(mRoomId);
        Room roomFromAlicePOV =  mAliceSession.getDataHandler().getRoom(mRoomId);

        assert roomFromBobPOV.isEncrypted();
        assert roomFromAlicePOV.isEncrypted();

        mLock = new CountDownLatch(2);

        MXEventListener eventListener = new MXEventListener() {
            @Override
            public void onLiveEvent(Event event, RoomState roomState) {
                try {
                    if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE)) {
                        if (checkEncryptedEvent(event, mRoomId, messageFromAlice, mAliceSession)) {
                            results.put("onLiveEvent", "onLiveEvent");
                            mLock.countDown();
                        }
                    }
                } catch (Exception e) {
                }
            }
        };

        roomFromBobPOV.addEventListener(eventListener);

        roomFromAlicePOV.sendEvent(buildTextEvent(messageFromAlice, mAliceSession), new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                mLock.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {
                mLock.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                mLock.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                mLock.countDown();
            }
        });

        mLock.await(10000, TimeUnit.DAYS.MILLISECONDS);
        assert (results.containsKey("onLiveEvent"));

        mBobSession.clear(context);

    }

    //==============================================================================================================
    // private test routines
    //==============================================================================================================

    private MXSession mBobSession;
    private MXSession mAliceSession;
    private String mRoomId;
    private CountDownLatch mLock;
    private int mMessagesCount;

    public void createBobAccount() throws Exception {
        Context context = InstrumentationRegistry.getContext();
        mBobSession = null;
        mBobSession = CryptoTestHelper.createAccountAndSync(context, MXTESTS_BOB + System.currentTimeMillis() + UUID.randomUUID().toString(), MXTESTS_BOB_PWD, true);
        assert (null != mBobSession);
    }

    public void createAliceAccount() throws Exception {
        Context context = InstrumentationRegistry.getContext();
        mAliceSession = null;
        mAliceSession = CryptoTestHelper.createAccountAndSync(context, MXTESTS_ALICE + System.currentTimeMillis() + UUID.randomUUID().toString(), MXTESTS_ALICE_PWD, true);
        assert (null != mAliceSession);
    }

    private void doE2ETestWithBobAndAlice() throws Exception {
        createBobAccount();
        mBobSession.setCryptoEnabled(true);

        createAliceAccount();
        mAliceSession.setCryptoEnabled(true);
    }

    private void doE2ETestWithAliceInARoom() throws Exception {
        final HashMap<String, Object> params = new HashMap<>();

        createAliceAccount();
        mAliceSession.setCryptoEnabled(true);

        mRoomId = null;
        mLock = new CountDownLatch(1);
        mAliceSession.createRoom(new ApiCallback<String>() {
            @Override
            public void onSuccess(String roomId) {
                mRoomId = roomId;
                mLock.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {
                mLock.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                mLock.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                mLock.countDown();
            }
        });

        mLock.await(10000, TimeUnit.DAYS.MILLISECONDS);
        assert(null != mRoomId);

        Room room = mAliceSession.getDataHandler().getRoom(mRoomId);

        mLock = new CountDownLatch(1);
        room.enableEncryptionWithAlgorithm(MXCryptoAlgorithms.MXCRYPTO_ALGORITHM_MEGOLM, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                params.put("enableEncryptionWithAlgorithm", "enableEncryptionWithAlgorithm");
                mLock.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {
                mLock.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                mLock.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                mLock.countDown();
            }
        });
        mLock.await(10000, TimeUnit.DAYS.MILLISECONDS);
        assert(params.containsKey("enableEncryptionWithAlgorithm"));
    }

    private void doE2ETestWithAliceAndBobInARoom(boolean cryptedBob) throws Exception {
        final HashMap<String, String> statuses = new HashMap<>();

        doE2ETestWithAliceInARoom();

        Room room = mAliceSession.getDataHandler().getRoom(mRoomId);

        createBobAccount();
        mBobSession.setCryptoEnabled(true);

        mLock = new CountDownLatch(2);

        MXEventListener bobEventListener = new MXEventListener() {
            @Override
            public void onNewRoom(String roomId) {
                if (TextUtils.equals(roomId, mRoomId)) {
                    mLock.countDown();
                    statuses.put("onNewRoom", "onNewRoom");
                }
            }
        };

        mBobSession.getDataHandler().addListener(bobEventListener);

        room.invite(mBobSession.getMyUserId(), new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                statuses.put("invite", "invite");
                mLock.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {
                mLock.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                mLock.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                mLock.countDown();
            }
        });

        mLock.await(10000, TimeUnit.DAYS.MILLISECONDS);

        assert(statuses.containsKey("invite") && statuses.containsKey("onNewRoom"));

        mBobSession.getDataHandler().removeListener(bobEventListener);

        mLock = new CountDownLatch(1);

        mBobSession.joinRoom(mRoomId, new ApiCallback<String>() {
            @Override
            public void onSuccess(String info) {
                statuses.put("joinRoom", "joinRoom");
                mLock.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {
                mLock.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                mLock.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                mLock.countDown();
            }
        });

        mLock.await(10000, TimeUnit.DAYS.MILLISECONDS);
        assert(statuses.containsKey("joinRoom"));
    }


    private Event buildTextEvent(String text, MXSession session) {
        Message message = new Message();
        message.msgtype = Message.MSGTYPE_TEXT;
        message.body = text;

        return new Event(message, session.getCredentials().userId, mRoomId);
    }

    private void doE2ETestWithAliceAndBobInARoomWithCryptedMessages(boolean cryptedBob) throws Exception {
        doE2ETestWithAliceAndBobInARoom(cryptedBob);

        final Room roomFromBobPOV = mBobSession.getDataHandler().getRoom(mRoomId);
        final Room roomFromAlicePOV = mAliceSession.getDataHandler().getRoom(mRoomId);

        mMessagesCount = 0;


        MXEventListener bobEventsListener = new MXEventListener() {
            @Override
            public void onLiveEvent(Event event, RoomState roomState) {
                if (TextUtils.equals(event.type, Event.EVENT_TYPE_MESSAGE)) {
                    mMessagesCount++;
                    mLock.countDown();
                }
            }
        };

        roomFromBobPOV.addEventListener(bobEventsListener);

        ApiCallback<Void> callback = new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                mLock.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {

            }

            @Override
            public void onMatrixError(MatrixError e) {

            }

            @Override
            public void onUnexpectedError(Exception e) {

            }
        };

        mLock = new CountDownLatch(2);
        roomFromAlicePOV.sendEvent(buildTextEvent(messagesFromAlice.get(0), mAliceSession), callback);
        mLock.await(10000, TimeUnit.DAYS.MILLISECONDS);
        assert(mMessagesCount == 1);

        mLock = new CountDownLatch(2);
        roomFromBobPOV.sendEvent(buildTextEvent(messagesFromBob.get(0), mBobSession), callback);
        mLock.await(10000, TimeUnit.DAYS.MILLISECONDS);
        assert(mMessagesCount == 2);

        mLock = new CountDownLatch(2);
        roomFromBobPOV.sendEvent(buildTextEvent(messagesFromBob.get(1), mBobSession), callback);
        mLock.await(10000, TimeUnit.DAYS.MILLISECONDS);
        assert(mMessagesCount == 3);

        mLock = new CountDownLatch(2);
        roomFromBobPOV.sendEvent(buildTextEvent(messagesFromBob.get(2), mBobSession), callback);
        mLock.await(10000, TimeUnit.DAYS.MILLISECONDS);
        assert(mMessagesCount == 4);

        mLock = new CountDownLatch(2);
        roomFromAlicePOV.sendEvent(buildTextEvent(messagesFromAlice.get(1), mAliceSession), callback);
        mLock.await(10000, TimeUnit.DAYS.MILLISECONDS);
        assert(mMessagesCount == 5);
    }

    private boolean checkEncryptedEvent(Event event, String roomId, String clearMessage, MXSession senderSession) throws Exception {

        assert(TextUtils.equals(event.getWireType(), Event.EVENT_TYPE_MESSAGE_ENCRYPTED));
        assert(null != event.getWireContent());

        JsonObject eventWireContent = event.getWireContent().getAsJsonObject();

        assert(null == eventWireContent.get("body"));
        assert(TextUtils.equals(eventWireContent.get("algorithm").getAsString(), MXCryptoAlgorithms.MXCRYPTO_ALGORITHM_MEGOLM));

        assert(null != eventWireContent.get("ciphertext"));
        assert(null != eventWireContent.get("session_id"));
        assert(null != eventWireContent.get("sender_key"));

        assert(TextUtils.equals(eventWireContent.get("device_id").getAsString(), senderSession.getCredentials().deviceId));

        assert(event.eventId != null);
        assert(TextUtils.equals(event.roomId, roomId));
        assert(TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE));
        assert(2000 == event.age);

        JsonObject eventContent = event.getContentAsJsonObject();
        assert(TextUtils.equals(eventContent.get("body").getAsString(), clearMessage));
        assert(TextUtils.equals(event.sender, senderSession.getMyUserId()));

        return true;
    }
}
