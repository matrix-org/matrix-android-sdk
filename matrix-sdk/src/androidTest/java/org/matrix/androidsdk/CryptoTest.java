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
import android.os.SystemClock;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.text.TextUtils;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.matrix.androidsdk.crypto.MXCrypto;
import org.matrix.androidsdk.crypto.MXCryptoAlgorithms;
import org.matrix.androidsdk.crypto.MXCryptoError;
import org.matrix.androidsdk.crypto.data.MXDeviceInfo;
import org.matrix.androidsdk.crypto.data.MXOlmSessionResult;
import org.matrix.androidsdk.crypto.data.MXUsersDevicesMap;
import org.matrix.androidsdk.data.EventTimeline;
import org.matrix.androidsdk.data.store.IMXStore;
import org.matrix.androidsdk.data.store.MXFileStore;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.Message;
import org.matrix.androidsdk.rest.model.login.Credentials;

import java.util.ArrayList;
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

    private static final String MXTESTS_SAM = "mxSam";
    private static final String MXTESTS_SAM_PWD = "samsam";
    
    @Test
    public void test01_testCryptoNoDeviceId() throws Exception {
        Context context = InstrumentationRegistry.getContext();
        final HashMap<String, Object> results = new HashMap<>();
        createBobAccount();

        assertTrue (null == mBobSession.getCrypto());
        mBobSession.getCredentials().deviceId = null;

        final CountDownLatch lock1 = new CountDownLatch(1);
        mBobSession.enableCrypto(true, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                results.put("enableCrypto", "enableCrypto");
                lock1.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {
                lock1.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                lock1.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                lock1.countDown();
            }
        });
        lock1.await(1000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue(results.containsKey("enableCrypto"));

        assertTrue (null != mBobSession.getCrypto());
        assertTrue (null != mBobSession.getCredentials().deviceId);

        mBobSession.clear(context);
    }

    @Test
    public void test02_testCryptoPersistenceInStore() throws Exception {
        Context context = InstrumentationRegistry.getContext();
        final HashMap<String, Object> results = new HashMap<>();

        createBobAccount();
        mBobSession.getCredentials().deviceId = "BobDevice";

        assertTrue (null == mBobSession.getCrypto());

        final CountDownLatch lock0 = new CountDownLatch(1);
        mBobSession.enableCrypto(true, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                results.put("enableCrypto", "enableCrypto");
                lock0.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {
                lock0.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                lock0.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                lock0.countDown();
            }
        });
        lock0.await(1000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue(results.containsKey("enableCrypto"));

        assertTrue (null != mBobSession.getCrypto());

        final String deviceCurve25519Key = mBobSession.getCrypto().getOlmDevice().getDeviceCurve25519Key();
        final String deviceEd25519Key = mBobSession.getCrypto().getOlmDevice().getDeviceEd25519Key();

        final List<MXDeviceInfo> myUserDevices = mBobSession.getCrypto().storedDevicesForUser(mBobSession.getMyUserId());

        assertTrue (null != myUserDevices);
        assertTrue (1 == myUserDevices.size());

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


        final CountDownLatch lock1 = new CountDownLatch(1);
        IMXStore.MXStoreListener listener = new  IMXStore.MXStoreListener() {
            @Override
            public void postProcess(String accountId) {
            }

            @Override
            public void onStoreReady(String accountId) {
                results.put("onStoreReady", "onStoreReady");
                lock1.countDown();
            }

            @Override
            public void onStoreCorrupted(String accountId, String description) {
                lock1.countDown();
            }

            @Override
            public void  onStoreOOM(String accountId, String description) {
                lock1.countDown();
            }
        };

        bobSession2.getDataHandler().getStore().addMXStoreListener(listener);

        bobSession2.getDataHandler().getStore().open();
        lock1.await(1000, TimeUnit.DAYS.MILLISECONDS);

        assertTrue (results.containsKey("onStoreReady"));
        assertTrue (bobSession2.isCryptoEnabled());

        final CountDownLatch lock2 = new CountDownLatch(1);

        MXEventListener eventsListener = new MXEventListener() {
            @Override
            public void onInitialSyncComplete() {
                results.put("onInitialSyncComplete", "onInitialSyncComplete");
                lock2.countDown();
            }
        };
        bobSession2.getDataHandler().addListener(eventsListener);
        bobSession2.startEventStream(null);
        lock2.await(1000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue (results.containsKey("onInitialSyncComplete"));

        MXCrypto crypto = bobSession2.getCrypto();
        assertNotNull(crypto);

        assertTrue (TextUtils.equals(deviceCurve25519Key, crypto.getOlmDevice().getDeviceCurve25519Key()));
        assertTrue (TextUtils.equals(deviceEd25519Key, crypto.getOlmDevice().getDeviceEd25519Key()));

        List<MXDeviceInfo> myUserDevices2 = bobSession2.getCrypto().storedDevicesForUser(bobSession2.getMyUserId());
        assertTrue (1 == myUserDevices2.size());
        assertTrue (TextUtils.equals(myUserDevices2.get(0).deviceId, myUserDevices.get(0).deviceId));

        mBobSession.clear(context);
        bobSession2.clear(context);
    }

    @Test
    public void test03_testKeysUploadAndDownload() throws Exception {
        Context context = InstrumentationRegistry.getContext();
        final HashMap<String, Object> results = new HashMap<>();

        createAliceAccount();
        mAliceSession.getCredentials().deviceId = "AliceDevice";

        final CountDownLatch lock0 = new CountDownLatch(1);
        mAliceSession.enableCrypto(true, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                results.put("enableCrypto", "enableCrypto");
            }

            @Override
            public void onNetworkError(Exception e) {
                lock0.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                lock0.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                lock0.countDown();
            }
        });
        lock0.await(1000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue(results.containsKey("enableCrypto"));

        final CountDownLatch lock1 = new CountDownLatch(1);
        mAliceSession.getCrypto().uploadKeys(10, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                results.put("uploadKeys", "uploadKeys");
                lock1.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {
                lock1.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                lock1.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                lock1.countDown();
            }
        });
        lock1.await(1000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue(results.containsKey("uploadKeys"));

        createBobAccount();
        final CountDownLatch lock2 = new CountDownLatch(1);
        mBobSession.getCredentials().deviceId = "BobDevice";
        mBobSession.enableCrypto(true, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                results.put("enableCrypto2", "enableCrypto2");
                lock2.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {
                lock2.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                lock2.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                lock2.countDown();
            }
        });
        lock2.await(1000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue(results.containsKey("enableCrypto2"));

        final CountDownLatch lock3 = new CountDownLatch(1);
        mBobSession.getCrypto().downloadKeys(Arrays.asList(mBobSession.getMyUserId(), mAliceSession.getMyUserId()), false, new ApiCallback<MXUsersDevicesMap<MXDeviceInfo>>() {
            @Override
            public void onSuccess(MXUsersDevicesMap<MXDeviceInfo> info) {
                results.put("downloadKeys", info);
                lock3.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {
                lock3.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                lock3.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                lock3.countDown();
            }
        });

        lock3.await(1000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue(results.containsKey("downloadKeys"));
        MXUsersDevicesMap<MXDeviceInfo> usersDevicesInfoMap = (MXUsersDevicesMap<MXDeviceInfo> )results.get("downloadKeys");

        assertTrue (2 == usersDevicesInfoMap.userIds().size());
        assertTrue (1 == usersDevicesInfoMap.deviceIdsForUser(mAliceSession.getMyUserId()).size());

        MXDeviceInfo aliceDeviceFromBobPOV = usersDevicesInfoMap.objectForDevice("AliceDevice", mAliceSession.getMyUserId());
        assertTrue (null != aliceDeviceFromBobPOV);
        assertTrue (TextUtils.equals(aliceDeviceFromBobPOV.fingerprint(), mAliceSession.getCrypto().getOlmDevice().getDeviceEd25519Key()));

        // Continue testing other methods
        assertTrue (null != mBobSession.getCrypto().deviceWithIdentityKey(mAliceSession.getCrypto().getOlmDevice().getDeviceCurve25519Key(), mAliceSession.getMyUserId(), MXCryptoAlgorithms.MXCRYPTO_ALGORITHM_OLM));
        assertTrue (aliceDeviceFromBobPOV.mVerified == MXDeviceInfo.DEVICE_VERIFICATION_UNVERIFIED);

        mBobSession.getCrypto().setDeviceVerification(MXDeviceInfo.DEVICE_VERIFICATION_BLOCKED, aliceDeviceFromBobPOV.deviceId, mAliceSession.getMyUserId());
        assertTrue (aliceDeviceFromBobPOV.mVerified == MXDeviceInfo.DEVICE_VERIFICATION_BLOCKED);

        // the device informations are saved in background thread so give a breath to save everything
        SystemClock.sleep(1000);

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

        final CountDownLatch lock4 = new CountDownLatch(1);

        IMXStore.MXStoreListener listener = new  IMXStore.MXStoreListener() {
            @Override
            public void postProcess(String accountId) {
            }

            @Override
            public void onStoreReady(String accountId) {
                results.put("onStoreReady", "onStoreReady");
                lock4.countDown();
            }

            @Override
            public void onStoreCorrupted(String accountId, String description) {
                lock4.countDown();
            }

            @Override
            public void  onStoreOOM(String accountId, String description) {
                lock4.countDown();
            }
        };

        bobSession2.getDataHandler().getStore().addMXStoreListener(listener);
        bobSession2.getDataHandler().getStore().open();

        lock4.await(2000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue(results.containsKey("onStoreReady"));

        final CountDownLatch lock4b = new CountDownLatch(1);
        MXEventListener eventListener = new MXEventListener() {
            @Override
            public void onInitialSyncComplete() {
                results.put("onInitialSyncComplete", "onInitialSyncComplete");
                lock4b.countDown();
            }
        };

        bobSession2.getDataHandler().addListener(eventListener);

        bobSession2.startEventStream(null);
        lock4b.await(2000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue(results.containsKey("onInitialSyncComplete"));

        MXDeviceInfo aliceDeviceFromBobPOV2 = bobSession2.getCrypto().deviceWithIdentityKey(mAliceSession.getCrypto().getOlmDevice().getDeviceCurve25519Key(), mAliceSession.getMyUserId(), MXCryptoAlgorithms.MXCRYPTO_ALGORITHM_OLM);

        assertTrue (null != aliceDeviceFromBobPOV2);
        assertTrue (TextUtils.equals(aliceDeviceFromBobPOV2.fingerprint(), mAliceSession.getCrypto().getOlmDevice().getDeviceEd25519Key()));
        assertTrue (aliceDeviceFromBobPOV2.mVerified + " instead of " + MXDeviceInfo.DEVICE_VERIFICATION_BLOCKED, aliceDeviceFromBobPOV2.mVerified == MXDeviceInfo.DEVICE_VERIFICATION_BLOCKED);

        // Download again alice device
        final CountDownLatch lock5 = new CountDownLatch(1);
        bobSession2.getCrypto().downloadKeys(Arrays.asList(mAliceSession.getMyUserId()), true, new ApiCallback<MXUsersDevicesMap<MXDeviceInfo>>() {
            @Override
            public void onSuccess(MXUsersDevicesMap<MXDeviceInfo> info) {
                results.put("downloadKeys2", info);
                lock4.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {
                lock5.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                lock5.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                lock5.countDown();
            }
        });
        lock5.await(1000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue(results.containsKey("downloadKeys2"));

        MXDeviceInfo aliceDeviceFromBobPOV3 = bobSession2.getCrypto().deviceWithIdentityKey(mAliceSession.getCrypto().getOlmDevice().getDeviceCurve25519Key(), mAliceSession.getMyUserId(), MXCryptoAlgorithms.MXCRYPTO_ALGORITHM_OLM);

        assertTrue (null != aliceDeviceFromBobPOV3);
        assertTrue (TextUtils.equals(aliceDeviceFromBobPOV3.fingerprint(), mAliceSession.getCrypto().getOlmDevice().getDeviceEd25519Key()));
        assertTrue(aliceDeviceFromBobPOV3.mVerified == MXDeviceInfo.DEVICE_VERIFICATION_BLOCKED);

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

        final CountDownLatch lock0 = new CountDownLatch(1);

        mAliceSession.enableCrypto(true, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                results.put("enableCryptoAlice", "enableCryptoAlice");
                lock0.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {
                lock0.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                lock0.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                lock0.countDown();
            }
        });

        lock0.await(1000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue(results.containsKey("enableCryptoAlice"));

        final CountDownLatch lock1 = new CountDownLatch(1);
        mAliceSession.getCrypto().uploadKeys(10, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                results.put("uploadKeys", "uploadKeys");
                lock1.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {
                lock1.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                lock1.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                lock1.countDown();
            }
        });
        lock1.await(1000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue(results.containsKey("uploadKeys"));

        createBobAccount();

        final CountDownLatch lock2 = new CountDownLatch(1);
        mBobSession.enableCrypto(true, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                results.put("enableCryptoBob", "enableCryptoAlice");
                lock2.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {
                lock2.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                lock2.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                lock2.countDown();
            }
        });

        lock2.await(1000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue(results.containsKey("enableCryptoBob"));

        final CountDownLatch lock3 = new CountDownLatch(1);
        mBobSession.getCrypto().downloadKeys(Arrays.asList(mBobSession.getMyUserId(), mAliceSession.getMyUserId()), false, new ApiCallback<MXUsersDevicesMap<MXDeviceInfo>>() {
            @Override
            public void onSuccess(MXUsersDevicesMap<MXDeviceInfo> map) {
                results.put("downloadKeys", map);
                lock3.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {
                lock3.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                lock3.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                lock3.countDown();
            }
        });

        lock3.await(1000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue(results.containsKey("downloadKeys"));

        final CountDownLatch lock4 = new CountDownLatch(1);
        mBobSession.getCrypto().ensureOlmSessionsForUsers(Arrays.asList(mBobSession.getMyUserId(), mAliceSession.getMyUserId()), new ApiCallback<MXUsersDevicesMap<MXOlmSessionResult>>() {
            @Override
            public void onSuccess(MXUsersDevicesMap<MXOlmSessionResult> info) {
                results.put("ensureOlmSessionsForUsers", info);
                lock4.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {
                lock4.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                lock4.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                lock4.countDown();
            }
        });

        lock4.await(1000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue(results.containsKey("ensureOlmSessionsForUsers"));

        MXUsersDevicesMap<MXOlmSessionResult> result = (MXUsersDevicesMap<MXOlmSessionResult>)results.get("ensureOlmSessionsForUsers");

        assertTrue (result.userIds().size() == 1);

        MXOlmSessionResult sessionWithAliceDevice = result.objectForDevice("AliceDevice", mAliceSession.getMyUserId());

        assertTrue (null != sessionWithAliceDevice);
        assertTrue (null != sessionWithAliceDevice.mSessionId);
        assertTrue (TextUtils.equals(sessionWithAliceDevice.mDevice.deviceId, "AliceDevice"));

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

        final CountDownLatch lock5 = new CountDownLatch(1);

        IMXStore.MXStoreListener listener = new  IMXStore.MXStoreListener() {
            @Override
            public void postProcess(String accountId) {
            }

            @Override
            public void onStoreReady(String accountId) {
                results.put("onStoreReady", "onStoreReady");
                lock5.countDown();
            }

            @Override
            public void onStoreCorrupted(String accountId, String description) {
                lock5.countDown();
            }

            @Override
            public void  onStoreOOM(String accountId, String description) {
                lock5.countDown();
            }
        };

        bobSession2.getDataHandler().getStore().addMXStoreListener(listener);
        bobSession2.getDataHandler().getStore().open();
        bobSession2.getDataHandler().addListener(new MXEventListener() {
            @Override
            public void onStoreReady() {
                lock5.countDown();
            }
        });
        lock5.await(1000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue(results.containsKey("onStoreReady"));

        final CountDownLatch lock5b = new CountDownLatch(1);
        MXEventListener eventListener = new MXEventListener() {
            @Override
            public void onInitialSyncComplete() {
                results.put("onInitialSyncComplete", "onInitialSyncComplete");
                lock5b.countDown();
            }
        };

        bobSession2.getDataHandler().addListener(eventListener);
        bobSession2.startEventStream(null);

        lock5b.await(1000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue(results.containsKey("onInitialSyncComplete"));

        final CountDownLatch lock6 = new CountDownLatch(1);
        bobSession2.getCrypto().ensureOlmSessionsForUsers(Arrays.asList(bobSession2.getMyUserId(), mAliceSession.getMyUserId()), new ApiCallback<MXUsersDevicesMap<MXOlmSessionResult>>() {
            @Override
            public void onSuccess(MXUsersDevicesMap<MXOlmSessionResult> info) {
                results.put("ensureOlmSessionsForUsers2", info);
                lock6.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {
                lock6.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                lock6.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                lock6.countDown();
            }
        });
        lock6.await(1000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue (results.containsKey("ensureOlmSessionsForUsers2"));

        MXUsersDevicesMap<MXOlmSessionResult> result2 = (MXUsersDevicesMap<MXOlmSessionResult>)results.get("ensureOlmSessionsForUsers2");

        MXOlmSessionResult sessionWithAliceDevice2 = result2.objectForDevice("AliceDevice", mAliceSession.getMyUserId());
        assertTrue (null != sessionWithAliceDevice2);
        assertTrue (null != sessionWithAliceDevice2.mSessionId);
        assertTrue (TextUtils.equals(sessionWithAliceDevice2.mDevice.deviceId, "AliceDevice"));

        mBobSession.clear(context);
        mAliceSession.clear(context);
        bobSession2.clear(context);
    }

    @Test
    public void test05_testRoomIsEncrypted() throws Exception {
        Context context = InstrumentationRegistry.getContext();
        final HashMap<String, Object> results = new HashMap<>();

        createBobAccount();

        final CountDownLatch lock0 = new CountDownLatch(1);
        mBobSession.enableCrypto(true, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                results.put("enableCrypto", "enableCrypto");
                lock0.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {
                lock0.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                lock0.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                lock0.countDown();
            }
        });
        lock0.await(1000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue (results.containsKey("enableCrypto"));
        mRoomId = null;

        final CountDownLatch lock1 = new CountDownLatch(1);
        mBobSession.createRoom(new ApiCallback<String>() {
            @Override
            public void onSuccess(String info) {
                mRoomId = info;
                lock1.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {
                lock1.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                lock1.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                lock1.countDown();
            }
        });

        lock1.await(1000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue (null != mRoomId);

        Room room = mBobSession.getDataHandler().getRoom(mRoomId);

        assertTrue (!room.isEncrypted());

        final CountDownLatch lock2 = new CountDownLatch(1);
        room.enableEncryptionWithAlgorithm(MXCryptoAlgorithms.MXCRYPTO_ALGORITHM_MEGOLM, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                results.put("enableEncryptionWithAlgorithm", "enableEncryptionWithAlgorithm");
                lock2.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {
                lock2.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                lock2.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                lock2.countDown();
            }
        });
        lock2.await(1000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue (results.containsKey("enableEncryptionWithAlgorithm"));

        assertTrue (room.isEncrypted());

        mBobSession.clear(context);
    }

    @Test
    public void test06_testAliceInACryptedRoom() throws Exception {
        Context context = InstrumentationRegistry.getContext();

        doE2ETestWithAliceInARoom();

        final String message = "Hello myself!";

        Room roomFromAlicePOV = mAliceSession.getDataHandler().getRoom(mRoomId);

        assertTrue (roomFromAlicePOV.isEncrypted());

        final CountDownLatch lock1 = new CountDownLatch(1);

        // the IOS client echoes the message
        // the android client does not

        roomFromAlicePOV.sendEvent(buildTextEvent(message, mAliceSession), new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                lock1.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {
                lock1.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                lock1.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                lock1.countDown();
            }
        });

        lock1.await(1000, TimeUnit.DAYS.MILLISECONDS);

        mAliceSession.clear(context);
    }

    @Test
    public void test07_testAliceAndBobInACryptedRoom() throws Exception {
        Context context = InstrumentationRegistry.getContext();
        final HashMap<String, Object> results = new HashMap<>();

        doE2ETestWithAliceAndBobInARoom(true);

        final String messageFromAlice = "Hello I'm Alice!";

        Room roomFromBobPOV = mBobSession.getDataHandler().getRoom(mRoomId);
        Room roomFromAlicePOV =  mAliceSession.getDataHandler().getRoom(mRoomId);

        assertTrue(roomFromBobPOV.isEncrypted());
        assertTrue(roomFromAlicePOV.isEncrypted());

        final CountDownLatch lock1 = new CountDownLatch(2);

        MXEventListener eventListener = new MXEventListener() {
            @Override
            public void onLiveEvent(Event event, RoomState roomState) {
                try {
                    if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE)) {
                        if (checkEncryptedEvent(event, mRoomId, messageFromAlice, mAliceSession)) {
                            results.put("onLiveEvent", "onLiveEvent");
                            lock1.countDown();
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
                lock1.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {
                lock1.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                lock1.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                lock1.countDown();
            }
        });

        lock1.await(2000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue(results.containsKey("onLiveEvent"));

        mBobSession.clear(context);
    }

    @Test
    public void test08_testAliceAndBobInACryptedRoom2() throws Exception {
        doE2ETestWithAliceAndBobInARoom(true);

        final Room roomFromBobPOV = mBobSession.getDataHandler().getRoom(mRoomId);
        final Room roomFromAlicePOV = mAliceSession.getDataHandler().getRoom(mRoomId);

        assertTrue(roomFromBobPOV.isEncrypted());
        assertTrue(roomFromAlicePOV.isEncrypted());

        mReceivedMessagesFromAlice = 0;
        mReceivedMessagesFromBob = 0;

        final ArrayList<CountDownLatch> list = new ArrayList<>();

        MXEventListener bobEventListener = new MXEventListener() {
            @Override
            public void onLiveEvent(Event event, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE) && !TextUtils.equals(event.getSender(), mBobSession.getMyUserId())) {
                    try {
                        if (checkEncryptedEvent(event, mRoomId, messagesFromAlice.get(mReceivedMessagesFromAlice), mAliceSession)) {
                            mReceivedMessagesFromAlice++;
                            list.get(list.size()-1).countDown();
                        }
                    } catch (Exception e) {

                    }
                }
            }
        };

        MXEventListener aliceEventListener = new MXEventListener() {
            @Override
            public void onLiveEvent(Event event, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE) && !TextUtils.equals(event.getSender(), mAliceSession.getMyUserId())) {
                    try {
                        if (checkEncryptedEvent(event, mRoomId, messagesFromBob.get(mReceivedMessagesFromBob), mBobSession)) {
                            mReceivedMessagesFromBob++;
                            list.get(list.size()-1).countDown();
                        }
                    } catch (Exception e) {

                    }
                }
            }
        };

        ApiCallback<Void> callback = new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
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

        roomFromBobPOV.addEventListener(bobEventListener);
        roomFromAlicePOV.addEventListener(aliceEventListener);

        list.add(new CountDownLatch(1));
        roomFromAlicePOV.sendEvent(buildTextEvent(messagesFromAlice.get(mReceivedMessagesFromAlice), mAliceSession), callback);
        list.get(list.size()-1).await(1000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue(1 == mReceivedMessagesFromAlice);

        list.add(new CountDownLatch(1));
        roomFromBobPOV.sendEvent(buildTextEvent(messagesFromBob.get(mReceivedMessagesFromBob), mBobSession), callback);
        list.get(list.size()-1).await(1000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue(1 == mReceivedMessagesFromBob);

        list.add(new CountDownLatch(1));
        roomFromBobPOV.sendEvent(buildTextEvent(messagesFromBob.get(mReceivedMessagesFromBob), mBobSession), callback);
        list.get(list.size()-1).await(1000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue(2 == mReceivedMessagesFromBob);

        list.add(new CountDownLatch(1));
        roomFromBobPOV.sendEvent(buildTextEvent(messagesFromBob.get(mReceivedMessagesFromBob), mBobSession), callback);
        list.get(list.size()-1).await(1000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue(3 == mReceivedMessagesFromBob);

        list.add(new CountDownLatch(1));
        roomFromAlicePOV.sendEvent(buildTextEvent(messagesFromAlice.get(mReceivedMessagesFromAlice), mAliceSession), callback);
        list.get(list.size()-1).await(1000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue(2 == mReceivedMessagesFromAlice);
    }

    @Test
    public void test09_testAliceInACryptedRoomAfterInitialSync() throws Exception {
        Context context = InstrumentationRegistry.getContext();
        final HashMap<String, Object> results = new HashMap<>();

        doE2ETestWithAliceInARoom();

        final String message = "Hello myself!";

        Credentials aliceCredentials = mAliceSession.getCredentials();

        mAliceSession.clear(context);

        Uri uri = Uri.parse(CryptoTestHelper.TESTS_HOME_SERVER_URL);
        HomeserverConnectionConfig hs = new HomeserverConnectionConfig(uri);
        hs.setCredentials(aliceCredentials);

        IMXStore store =  new MXFileStore(hs, context);

        final CountDownLatch lock1 = new CountDownLatch(1);

        final MXSession aliceSession2 = new MXSession(hs, new MXDataHandler(store, aliceCredentials, new MXDataHandler.InvalidTokenListener() {
            @Override
            public void onTokenCorrupted() {
            }
        }), context);

        IMXStore.MXStoreListener listener = new  IMXStore.MXStoreListener() {
            @Override
            public void postProcess(String accountId) {
            }

            @Override
            public void onStoreReady(String accountId) {
                results.put("onStoreReady", "onStoreReady");
                lock1.countDown();
            }

            @Override
            public void onStoreCorrupted(String accountId, String description) {
                lock1.countDown();
            }

            @Override
            public void  onStoreOOM(String accountId, String description) {
                lock1.countDown();
            }
        };

        aliceSession2.getDataHandler().getStore().addMXStoreListener(listener);
        aliceSession2.getDataHandler().getStore().open();
        lock1.await(1000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue (results.containsKey("onStoreReady"));

        final CountDownLatch lock1b = new CountDownLatch(1);
        MXEventListener eventListener = new MXEventListener() {
            @Override
            public void onInitialSyncComplete() {
                results.put("onInitialSyncComplete", "onInitialSyncComplete");
                lock1b.countDown();
            }
        };
        aliceSession2.getDataHandler().addListener(eventListener);
        aliceSession2.startEventStream(null);
        lock1b.await(1000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue (results.containsKey("onInitialSyncComplete"));

        Room roomFromAlicePOV2 = aliceSession2.getDataHandler().getRoom(mRoomId);

        assertTrue(roomFromAlicePOV2.isEncrypted());

        final CountDownLatch lock2 = new CountDownLatch(1);

        if (false) {
            // The android client does not echo its own message
            MXEventListener aliceEventListener = new MXEventListener() {
                @Override
                public void onLiveEvent(Event event, RoomState roomState) {
                    if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE)) {
                        try {
                            if (checkEncryptedEvent(event, mRoomId, message, aliceSession2)) {
                                lock2.countDown();
                            }
                        } catch (Exception e) {
                        }
                    }
                }
            };

            roomFromAlicePOV2.addEventListener(aliceEventListener);
        }

        // the IOS client echoes the message
        // the android client does not
        roomFromAlicePOV2.sendEvent(buildTextEvent(message, aliceSession2), new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                results.put("sendEvent", "sendEvent");
                lock2.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {
                lock2.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                lock2.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                lock2.countDown();
            }
        });

        lock2.await(1000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue(results.containsKey("sendEvent"));

        aliceSession2.clear(context);
    }

    @Test
    public void test10_testAliceDecryptOldMessageWithANewDeviceInACryptedRoom() throws Exception {
        Context context = InstrumentationRegistry.getContext();
        final HashMap<String, Object> results = new HashMap<>();

        doE2ETestWithAliceInARoom();

        String message = "Hello myself!";

        Room roomFromAlicePOV = mAliceSession.getDataHandler().getRoom(mRoomId);

        final CountDownLatch lock1 = new CountDownLatch(1);
        roomFromAlicePOV.sendEvent(buildTextEvent(message, mAliceSession), new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                results.put("sendEvent", "sendEvent");
                lock1.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {
                lock1.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                lock1.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                lock1.countDown();
            }
        });
        lock1.await(1000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue(results.containsKey("sendEvent"));

        Credentials aliceCredentials = mAliceSession.getCredentials();
        Credentials aliceCredentials2 = new Credentials();

        // close the session and clear the data
        mAliceSession.clear(context);

        aliceCredentials2.userId = aliceCredentials.userId;
        aliceCredentials2.homeServer = aliceCredentials.homeServer;
        aliceCredentials2.accessToken = aliceCredentials.accessToken;
        aliceCredentials2.refreshToken = aliceCredentials.refreshToken;
        aliceCredentials2.deviceId = "AliceNewDevice";

        Uri uri = Uri.parse(CryptoTestHelper.TESTS_HOME_SERVER_URL);
        HomeserverConnectionConfig hs = new HomeserverConnectionConfig(uri);
        hs.setCredentials(aliceCredentials2);

        IMXStore store =  new MXFileStore(hs, context);

        MXSession aliceSession2 = new MXSession(hs, new MXDataHandler(store, aliceCredentials2, new MXDataHandler.InvalidTokenListener() {
            @Override
            public void onTokenCorrupted() {
            }
        }), context);

        aliceSession2.enableCryptoWhenStarting();

        final CountDownLatch lock1b = new CountDownLatch(1);
        IMXStore.MXStoreListener listener = new  IMXStore.MXStoreListener() {
            @Override
            public void postProcess(String accountId) {
            }

            @Override
            public void onStoreReady(String accountId) {
                results.put("onStoreReady", "onStoreReady");
                lock1b.countDown();
            }

            @Override
            public void onStoreCorrupted(String accountId, String description) {
                lock1b.countDown();
            }

            @Override
            public void  onStoreOOM(String accountId, String description) {
                lock1b.countDown();
            }
        };

        aliceSession2.getDataHandler().getStore().addMXStoreListener(listener);
        aliceSession2.getDataHandler().getStore().open();
        lock1b.await(1000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue(results.containsKey("onStoreReady"));

        final CountDownLatch lock2 = new CountDownLatch(1);
        MXEventListener eventListener = new MXEventListener() {
            @Override
            public void onInitialSyncComplete() {
                results.put("onInitialSyncComplete", "onInitialSyncComplete");
                lock2.countDown();
            }
        };

        aliceSession2.getDataHandler().addListener(eventListener);
        aliceSession2.startEventStream(null);

        lock2.await(1000, TimeUnit.DAYS.MILLISECONDS);

        assertTrue (results.containsKey("onInitialSyncComplete"));

        Room roomFromAlicePOV2 = aliceSession2.getDataHandler().getRoom(mRoomId);

        assertTrue (null != roomFromAlicePOV2);
        assertTrue(roomFromAlicePOV2.getLiveState().isEncrypted());

        Event event = roomFromAlicePOV2.getDataHandler().getStore().getLatestEvent(mRoomId);
        assertTrue(null != event);
        assertTrue(event.isEncrypted());
        assertTrue(null == event.getClearEvent());
        assertTrue(null != event.getCryptoError());
        assertTrue(TextUtils.equals(event.getCryptoError().errcode, MXCryptoError.UNKNOWN_INBOUND_SESSION_ID_ERROR_CODE));
        aliceSession2.clear(context);
    }

    @Test
    public void test11_testAliceAndBobInACryptedRoomBackPaginationFromMemoryStore() throws Exception {
        Context context = InstrumentationRegistry.getContext();
        final HashMap<String, Object> results = new HashMap();

        doE2ETestWithAliceAndBobInARoomWithCryptedMessages(true);

        Credentials bobCredentials = mBobSession.getCredentials();
        mBobSession.clear(context);

        Uri uri = Uri.parse(CryptoTestHelper.TESTS_HOME_SERVER_URL);
        HomeserverConnectionConfig hs = new HomeserverConnectionConfig(uri);
        hs.setCredentials(bobCredentials);

        IMXStore store =  new MXFileStore(hs, context);

        final CountDownLatch lock1 = new CountDownLatch(1);

        MXSession bobSession2 = new MXSession(hs, new MXDataHandler(store, bobCredentials, new MXDataHandler.InvalidTokenListener() {
            @Override
            public void onTokenCorrupted() {
            }
        }), context);

        MXEventListener eventListener = new MXEventListener() {
            @Override
            public void onInitialSyncComplete() {
                results.put("onInitialSyncComplete", "onInitialSyncComplete");
                lock1.countDown();
            }
        };

        bobSession2.getDataHandler().addListener(eventListener);
        bobSession2.getDataHandler().getStore().open();
        bobSession2.startEventStream(null);

        lock1.await(1000, TimeUnit.DAYS.MILLISECONDS);

        assertTrue (results.containsKey("onInitialSyncComplete"));
        assertTrue (null != bobSession2.getCrypto());

        Room roomFromBobPOV = bobSession2.getDataHandler().getRoom(mRoomId);

        final CountDownLatch lock2 = new CountDownLatch(6);
        final ArrayList<Event> receivedEvents = new ArrayList<>();

        EventTimeline.EventTimelineListener eventTimelineListener = new EventTimeline.EventTimelineListener() {
            public void onEvent(Event event, EventTimeline.Direction direction, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE)) {
                    receivedEvents.add(event);
                    lock2.countDown();
                }
            }
        };

        roomFromBobPOV.getLiveTimeLine().addEventTimelineListener(eventTimelineListener);

        roomFromBobPOV.getLiveTimeLine().backPaginate(new ApiCallback<Integer>() {
            @Override
            public void onSuccess(Integer info) {
                results.put("backPaginate", "backPaginate");
                lock2.countDown();
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
        });

        lock2.await(1000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue(results.containsKey("backPaginate"));
        assertTrue(receivedEvents.size() + " instead of 5",5 == receivedEvents.size());

        checkEncryptedEvent(receivedEvents.get(0), mRoomId, messagesFromAlice.get(1), mAliceSession);

        checkEncryptedEvent(receivedEvents.get(1), mRoomId, messagesFromBob.get(2), mBobSession);

        checkEncryptedEvent(receivedEvents.get(2), mRoomId, messagesFromBob.get(1), mBobSession);

        checkEncryptedEvent(receivedEvents.get(3), mRoomId, messagesFromBob.get(0), mBobSession);

        checkEncryptedEvent(receivedEvents.get(4), mRoomId, messagesFromAlice.get(0), mAliceSession);

        bobSession2.clear(context);
        mAliceSession.clear(context);
    }

    @Test
    public void test12_testAliceAndBobInACryptedRoomBackPaginationFromHomeServer() throws Exception {
        Context context = InstrumentationRegistry.getContext();
        final HashMap<String, Object> results = new HashMap();

        doE2ETestWithAliceAndBobInARoomWithCryptedMessages(true);

        String eventId = mBobSession.getDataHandler().getStore().getLatestEvent(mRoomId).eventId;

        EventTimeline timeline = new EventTimeline(mBobSession.getDataHandler(), mRoomId, eventId);

        final CountDownLatch lock2 = new CountDownLatch(6);
        final ArrayList<Event> receivedEvents = new ArrayList<>();

        EventTimeline.EventTimelineListener eventTimelineListener = new EventTimeline.EventTimelineListener() {
            public void onEvent(Event event, EventTimeline.Direction direction, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE)) {
                    receivedEvents.add(event);
                    lock2.countDown();
                }
            }
        };

        timeline.addEventTimelineListener(eventTimelineListener);
        timeline.backPaginate(new ApiCallback<Integer>() {
            @Override
            public void onSuccess(Integer info) {
                results.put("backPaginate", "backPaginate");
                lock2.countDown();
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
        });

        lock2.await(1000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue(results.containsKey("backPaginate"));
        assertTrue(5 == receivedEvents.size());

        checkEncryptedEvent(receivedEvents.get(0), mRoomId, messagesFromAlice.get(1), mAliceSession);

        checkEncryptedEvent(receivedEvents.get(1), mRoomId, messagesFromBob.get(2), mBobSession);

        checkEncryptedEvent(receivedEvents.get(2), mRoomId, messagesFromBob.get(1), mBobSession);

        checkEncryptedEvent(receivedEvents.get(3), mRoomId, messagesFromBob.get(0), mBobSession);

        checkEncryptedEvent(receivedEvents.get(4), mRoomId, messagesFromAlice.get(0), mAliceSession);

        mBobSession.clear(context);
        mAliceSession.clear(context);
    }

    @Test
    public void test13_testAliceAndNotCryptedBobInACryptedRoom() throws Exception {
        final HashMap<String, Object> results = new HashMap();

        doE2ETestWithAliceAndBobInARoom(false);

        Room roomFromBobPOV = mBobSession.getDataHandler().getRoom(mRoomId);
        Room roomFromAlicePOV = mAliceSession.getDataHandler().getRoom(mRoomId);

        assertTrue(roomFromBobPOV.isEncrypted());
        assertTrue(roomFromAlicePOV.isEncrypted());

        final String messageFromAlice = "Hello I'm Alice!";

        final CountDownLatch lock1 = new CountDownLatch(1);
        MXEventListener bobEventListener = new MXEventListener() {
            @Override
            public void onLiveEvent(Event event, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE_ENCRYPTED)
                        && !TextUtils.equals(event.getSender(), mBobSession.getMyUserId())) {
                    results.put("bobEcho", event);
                    lock1.countDown();
                }
            }
        };

        roomFromBobPOV.addEventListener(bobEventListener);

        roomFromAlicePOV.sendEvent(buildTextEvent(messageFromAlice, mAliceSession), new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
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
        });

        lock1.await(1000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue(results.containsKey("bobEcho"));

        Event event = (Event)results.get("bobEcho");
        assertTrue(event.isEncrypted());
        assertTrue(TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE_ENCRYPTED));
        assertTrue(null != event.getContentAsJsonObject());
        assertTrue(!event.getContentAsJsonObject().has("body"));

        assertTrue(null != event.getCryptoError());
        assertTrue(TextUtils.equals(event.getCryptoError().errcode, MXCryptoError.ENCRYPTING_NOT_ENABLED_ERROR_CODE));

        final CountDownLatch lock2 = new CountDownLatch(1);
        MXEventListener aliceEventListener = new MXEventListener() {
            @Override
            public void onLiveEvent(Event event, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE)
                        && !TextUtils.equals(event.getSender(), mAliceSession.getMyUserId())) {
                    results.put("aliceEcho", event);
                    lock2.countDown();
                }
            }
        };

        roomFromAlicePOV.addEventListener(aliceEventListener);

        roomFromBobPOV.sendEvent(buildTextEvent("Hello I'm Bob!", mBobSession), new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {

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
        });

        lock2.await(1000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue(results.containsKey("aliceEcho"));

        event = (Event)results.get("aliceEcho");
        assertTrue(!event.isEncrypted());
    }

    @Test
    public void test14_testCryptoDeviceBlockAndLeave() throws Exception {
        Context context = InstrumentationRegistry.getContext();
        final HashMap<String, Object> results = new HashMap<>();

        doE2ETestWithAliceAndBobAndSamInARoom();

        final Room roomFromBobPOV = mBobSession.getDataHandler().getRoom(mRoomId);
        final Room roomFromAlicePOV = mAliceSession.getDataHandler().getRoom(mRoomId);
        final Room roomFromSamPOV = mSamSession.getDataHandler().getRoom(mRoomId);

        assertTrue (null != roomFromBobPOV);
        assertTrue (null != roomFromAlicePOV);
        assertTrue (null != roomFromSamPOV);

        final CountDownLatch lock0 = new CountDownLatch(3);
        MXEventListener aliceEventsListener0 = new MXEventListener() {
            @Override
            public void onLiveEvent(Event event, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE)) {
                    results.put("alice0", "alice0");
                    lock0.countDown();
                }
            }
        };
        roomFromAlicePOV.addEventListener(aliceEventsListener0);

        MXEventListener samEventsListener0 = new MXEventListener() {
            @Override
            public void onLiveEvent(Event event, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE)) {
                    results.put("sam0", "sam0");
                    lock0.countDown();
                }
            }
        };
        roomFromSamPOV.addEventListener(samEventsListener0);

        // even if the device blocked, the message must be decrypted until there is a session id rolling
        roomFromBobPOV.sendEvent(buildTextEvent("msg1", mBobSession), new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                results.put("send0", "send0");
                lock0.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {
                lock0.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                lock0.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                lock0.countDown();
            }
        });
        lock0.await(5000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue(results.containsKey("send0") && results.containsKey("alice0") && results.containsKey("sam0"));

        roomFromAlicePOV.removeEventListener(aliceEventsListener0);
        roomFromSamPOV.removeEventListener(samEventsListener0);

        final CountDownLatch lock1 = new CountDownLatch(3);

        MXEventListener bobEventsListener1 = new MXEventListener() {
            @Override
            public void onLiveEvent(Event event, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE)) {
                    results.put("bob1", "bob1");
                    lock1.countDown();
                }
            }
        };
        roomFromBobPOV.addEventListener(bobEventsListener1);

        MXEventListener samEventsListener1 = new MXEventListener() {
            @Override
            public void onLiveEvent(Event event, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE)) {
                    results.put("sam1", "sam1");
                    lock1.countDown();
                }
            }
        };
        roomFromSamPOV.addEventListener(samEventsListener1);

        roomFromAlicePOV.sendEvent(buildTextEvent("msg1", mAliceSession), new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                results.put("send1", "send1");
                lock1.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {
                lock1.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                lock1.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                lock1.countDown();
            }
        });
        lock1.await(1000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue(results.containsKey("send1") && results.containsKey("bob1") && results.containsKey("sam1"));

        List<MXDeviceInfo> list = mBobSession.getCrypto().storedDevicesForUser(mAliceSession.getMyUserId());

        assertTrue(null != list);
        assertTrue(list.size() > 0);

        mBobSession.getCrypto().setDeviceVerification(MXDeviceInfo.DEVICE_VERIFICATION_BLOCKED, list.get(0).deviceId, mAliceSession.getMyUserId());

        final CountDownLatch lock2 = new CountDownLatch(3);
        MXEventListener aliceEventsListener2 = new MXEventListener() {
            @Override
            public void onLiveEvent(Event event, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE_ENCRYPTED)) {
                    results.put("alice2", "alice2");
                    lock2.countDown();
                }
            }
        };
        roomFromAlicePOV.addEventListener(aliceEventsListener2);

        MXEventListener samEventsListener2 = new MXEventListener() {
            @Override
            public void onLiveEvent(Event event, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE)) {
                    results.put("sam2", "sam2");
                    lock2.countDown();
                }
            }
        };
        roomFromSamPOV.addEventListener(samEventsListener2);

        // even if the device blocked, the message must be decrypted until there is a session id rolling
        roomFromBobPOV.sendEvent(buildTextEvent("msg2", mBobSession), new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                results.put("send2", "send2");
                lock2.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {
                lock2.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                lock2.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                lock2.countDown();
            }
        });

        lock2.await(1000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue(results.containsKey("send2") && results.containsKey("alice2") && results.containsKey("sam2"));

        roomFromAlicePOV.removeEventListener(aliceEventsListener2);

        final CountDownLatch lock3 = new CountDownLatch(2);
        MXEventListener bobLeaveEventsListener = new MXEventListener() {
            @Override
            public void onLiveEvent(Event event, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_STATE_ROOM_MEMBER)) {
                    results.put("bobleave", "bobleave");
                    lock3.countDown();
                }
            }
        };
        roomFromBobPOV.addEventListener(bobLeaveEventsListener);

        roomFromSamPOV.leave(new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                results.put("leave", "leave");
                lock3.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {
                lock3.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                lock3.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                lock3.countDown();
            }
        });

        lock3.await(1000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue(results.containsKey("leave") && results.containsKey("bobleave"));

        final CountDownLatch lock4 = new CountDownLatch(2);
        MXEventListener aliceEventsListener3 = new MXEventListener() {
            @Override
            public void onLiveEvent(Event event, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE_ENCRYPTED)) {
                    results.put("alice3", "alice3");
                    lock4.countDown();
                }
            }
        };
        roomFromAlicePOV.addEventListener(aliceEventsListener3);

        // even if the device blocked, the message must be decrypted until there is a session id rolling
        roomFromBobPOV.sendEvent(buildTextEvent("msg3", mBobSession), new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                results.put("send3", "send3");
                lock4.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {
                lock4.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                lock4.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                lock4.countDown();
            }
        });

        lock4.await(1000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue(results.containsKey("send3") && results.containsKey("alice3"));

        mBobSession.clear(context);
        mAliceSession.clear(context);
        mSamSession.clear(context);
    }

    @Test
    public void test15_testReplayAttack() throws Exception {
        final HashMap<String, Object> results = new HashMap<>();

        doE2ETestWithAliceAndBobInARoom(true);

        String messageFromAlice = "Hello I'm Alice!";

        final Room roomFromBobPOV = mBobSession.getDataHandler().getRoom(mRoomId);
        final Room roomFromAlicePOV = mAliceSession.getDataHandler().getRoom(mRoomId);

        assertTrue(roomFromBobPOV.isEncrypted());
        assertTrue(roomFromAlicePOV.isEncrypted());

        final CountDownLatch lock1 = new CountDownLatch(1);
        MXEventListener bobEventListener = new MXEventListener() {
            @Override
            public void onLiveEvent(Event event, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE) && !TextUtils.equals(event.getSender(), mBobSession.getMyUserId())) {
                    results.put("bobEcho", event);

                    event.setClearEvent(null);

                    mBobSession.getDataHandler().decryptEvent(event, roomFromBobPOV.getLiveTimeLine().getTimelineId());
                    results.put("decrypted", event);

                    lock1.countDown();
                }
            }
        };

        roomFromBobPOV.addEventListener(bobEventListener);

        roomFromAlicePOV.sendEvent(buildTextEvent(messageFromAlice, mAliceSession), new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
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
        });

        lock1.await(1000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue(results.containsKey("bobEcho"));
        assertTrue(results.containsKey("decrypted"));

        Event decryptedEvent = (Event)results.get("decrypted");

        assertTrue(null == decryptedEvent.getClearEvent());
        assertTrue(TextUtils.equals(decryptedEvent.getCryptoError().errcode, MXCryptoError.DUPLICATED_MESSAGE_INDEX_ERROR_CODE));

        // Decrypting it with no replay attack mitigation must still work
        mBobSession.getDataHandler().decryptEvent(decryptedEvent, null);
        assertTrue(checkEncryptedEvent(decryptedEvent, mRoomId, messageFromAlice, mAliceSession));
    }

    @Test
    public void test16_testRoomKeyReshare() throws Exception {
        final HashMap<String, Object> results = new HashMap<>();

        doE2ETestWithAliceAndBobInARoom(true);

        String messageFromAlice = "Hello I'm Alice!";

        final Room roomFromBobPOV = mBobSession.getDataHandler().getRoom(mRoomId);
        final Room roomFromAlicePOV = mAliceSession.getDataHandler().getRoom(mRoomId);

        assertTrue(roomFromBobPOV.isEncrypted());
        assertTrue(roomFromAlicePOV.isEncrypted());

        final CountDownLatch lock1 = new CountDownLatch(2);

        MXEventListener bobEventListener = new MXEventListener() {
            @Override
            public void onToDeviceEvent(Event event) {
                if (!results.containsKey("onToDeviceEvent")) {
                    results.put("onToDeviceEvent", event);
                    lock1.countDown();
                }
            }
        };

        mBobSession.getDataHandler().addListener(bobEventListener);

        final ArrayList<Event> receivedEvents = new ArrayList<>();
        EventTimeline.EventTimelineListener eventTimelineListener = new EventTimeline.EventTimelineListener() {
            public void onEvent(Event event, EventTimeline.Direction direction, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE)) {
                    receivedEvents.add(event);
                    lock1.countDown();
                }
            }
        };

        roomFromBobPOV.getLiveTimeLine().addEventTimelineListener(eventTimelineListener);

        roomFromAlicePOV.sendEvent(buildTextEvent(messageFromAlice, mAliceSession), new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
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
        });

        lock1.await(1000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue(results.containsKey("onToDeviceEvent"));
        assertTrue(1 == receivedEvents.size());

        Event event = receivedEvents.get(0);
        assertTrue(checkEncryptedEvent(event, mRoomId, messageFromAlice, mAliceSession));

        // Reinject a modified version of the received room_key event from Alice.
        // From Bob pov, that mimics Alice resharing her keys but with an advanced outbound group session.
        Event toDeviceEvent = (Event)results.get("onToDeviceEvent");
        String sessionId = toDeviceEvent.getContentAsJsonObject().get("session_id").getAsString();
        String newSessionKey = mAliceSession.getCrypto().getOlmDevice().sessionKeyForOutboundGroupSession(sessionId);

        JsonObject content = toDeviceEvent.getClearEvent().getContentAsJsonObject();
        content.add("session_key", new JsonPrimitive(newSessionKey));
        mBobSession.getDataHandler().onToDeviceEvent(toDeviceEvent);


        // We still must be able to decrypt the event
        // ie, the implementation must have ignored the new room key with the advanced outbound group
        // session key
        event.setClearEvent(null);
        event.setKeysClaimed(null);
        event.setKeysProved(null);

        mBobSession.getDataHandler().decryptEvent(event, null);
        assertTrue(checkEncryptedEvent(event, mRoomId, messageFromAlice, mAliceSession));
    }

    //==============================================================================================================
    // private test routines
    //==============================================================================================================

    private MXSession mBobSession;
    private MXSession mAliceSession;
    private MXSession  mSamSession;
    private String mRoomId;
    private int mMessagesCount;
    private int mReceivedMessagesFromAlice;
    private int mReceivedMessagesFromBob;

    public void createBobAccount() throws Exception {
        Context context = InstrumentationRegistry.getContext();
        mBobSession = null;
        mBobSession = CryptoTestHelper.createAccountAndSync(context, MXTESTS_BOB + System.currentTimeMillis() + UUID.randomUUID().toString(), MXTESTS_BOB_PWD, true);
        assertTrue (null != mBobSession);
    }

    public void createAliceAccount() throws Exception {
        Context context = InstrumentationRegistry.getContext();
        mAliceSession = null;
        mAliceSession = CryptoTestHelper.createAccountAndSync(context, MXTESTS_ALICE + System.currentTimeMillis() + UUID.randomUUID().toString(), MXTESTS_ALICE_PWD, true);
        assertTrue (null != mAliceSession);
    }

    public void createSamAccount() throws Exception {
        Context context = InstrumentationRegistry.getContext();
        mSamSession = null;
        mSamSession = CryptoTestHelper.createAccountAndSync(context, MXTESTS_SAM + System.currentTimeMillis() + UUID.randomUUID().toString(), MXTESTS_SAM_PWD, true);
        assertTrue (null != mSamSession);
    }

    private void doE2ETestWithAliceInARoom() throws Exception {
        final HashMap<String, Object> results = new HashMap<>();

        createAliceAccount();

        final CountDownLatch lock0 = new CountDownLatch(1);

        mAliceSession.enableCrypto(true, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                results.put("enableCrypto", "enableCrypto");
                lock0.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {
                lock0.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                lock0.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                lock0.countDown();
            }
        });
        lock0.await(1000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue(results.containsKey("enableCrypto"));

        mRoomId = null;
        final CountDownLatch lock1 = new CountDownLatch(1);

        mAliceSession.createRoom(new ApiCallback<String>() {
            @Override
            public void onSuccess(String roomId) {
                mRoomId = roomId;
                lock1.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {
                lock1.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                lock1.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                lock1.countDown();
            }
        });

        lock1.await(1000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue(null != mRoomId);

        Room room = mAliceSession.getDataHandler().getRoom(mRoomId);

        final CountDownLatch lock2 = new CountDownLatch(1);
        room.enableEncryptionWithAlgorithm(MXCryptoAlgorithms.MXCRYPTO_ALGORITHM_MEGOLM, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                results.put("enableEncryptionWithAlgorithm", "enableEncryptionWithAlgorithm");
                lock2.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {
                lock2.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                lock2.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                lock2.countDown();
            }
        });
        lock2.await(1000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue(results.containsKey("enableEncryptionWithAlgorithm"));
    }

    private void doE2ETestWithAliceAndBobInARoom(boolean cryptedBob) throws Exception {
        final HashMap<String, String> statuses = new HashMap<>();

        doE2ETestWithAliceInARoom();

        Room room = mAliceSession.getDataHandler().getRoom(mRoomId);

        createBobAccount();
        final CountDownLatch lock0 = new CountDownLatch(1);

        mBobSession.enableCrypto(cryptedBob, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                statuses.put("enableCrypto", "enableCrypto");
                lock0.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {
                lock0.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                lock0.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                lock0.countDown();
            }
        });
        lock0.await(1000, TimeUnit.DAYS.MILLISECONDS);

        final CountDownLatch lock1 = new CountDownLatch(2);

        MXEventListener bobEventListener = new MXEventListener() {
            @Override
            public void onNewRoom(String roomId) {
                if (TextUtils.equals(roomId, mRoomId)) {
                    if (!statuses.containsKey("onNewRoom")) {
                        statuses.put("onNewRoom", "onNewRoom");
                        lock1.countDown();
                    }
                }
            }
        };

        mBobSession.getDataHandler().addListener(bobEventListener);

        room.invite(mBobSession.getMyUserId(), new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                statuses.put("invite", "invite");
                lock1.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {
                lock1.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                lock1.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                lock1.countDown();
            }
        });

        lock1.await(1000, TimeUnit.DAYS.MILLISECONDS);

        assertTrue(statuses.containsKey("invite") && statuses.containsKey("onNewRoom"));

        mBobSession.getDataHandler().removeListener(bobEventListener);

        final CountDownLatch lock2 = new CountDownLatch(1);

        mBobSession.joinRoom(mRoomId, new ApiCallback<String>() {
            @Override
            public void onSuccess(String info) {
                statuses.put("joinRoom", "joinRoom");
                lock2.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {
                lock2.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                lock2.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                lock2.countDown();
            }
        });

        lock2.await(1000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue(statuses.containsKey("joinRoom"));

        mBobSession.getDataHandler().removeListener(bobEventListener);
    }

    private void doE2ETestWithAliceAndBobAndSamInARoom() throws Exception {
        final HashMap<String, String> statuses = new HashMap<>();

        doE2ETestWithAliceAndBobInARoom(true);

        Room room = mAliceSession.getDataHandler().getRoom(mRoomId);

        createSamAccount();
        final CountDownLatch lock0 = new CountDownLatch(1);

        mSamSession.enableCrypto(true, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                statuses.put("enableCrypto", "enableCrypto");
                lock0.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {
                lock0.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                lock0.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                lock0.countDown();
            }
        });
        lock0.await(1000, TimeUnit.DAYS.MILLISECONDS);

        final CountDownLatch lock1 = new CountDownLatch(2);

        MXEventListener samEventListener = new MXEventListener() {
            @Override
            public void onNewRoom(String roomId) {
                if (TextUtils.equals(roomId, mRoomId)) {
                    if (!statuses.containsKey("onNewRoom")) {
                        statuses.put("onNewRoom", "onNewRoom");
                        lock1.countDown();
                    }
                }
            }
        };

        mSamSession.getDataHandler().addListener(samEventListener);

        room.invite(mSamSession.getMyUserId(), new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                statuses.put("invite", "invite");
                lock1.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {
                lock1.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                lock1.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                lock1.countDown();
            }
        });

        lock1.await(1000, TimeUnit.DAYS.MILLISECONDS);

        assertTrue(statuses.containsKey("invite") && statuses.containsKey("onNewRoom"));

        mSamSession.getDataHandler().removeListener(samEventListener);

        final CountDownLatch lock2 = new CountDownLatch(1);

        mSamSession.joinRoom(mRoomId, new ApiCallback<String>() {
            @Override
            public void onSuccess(String info) {
                statuses.put("joinRoom", "joinRoom");
                lock2.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {
                lock2.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                lock2.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                lock2.countDown();
            }
        });

        lock2.await(1000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue(statuses.containsKey("joinRoom"));

        mSamSession.getDataHandler().removeListener(samEventListener);
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

        final ArrayList<CountDownLatch> list = new ArrayList<>();

        MXEventListener bobEventsListener = new MXEventListener() {
            @Override
            public void onLiveEvent(Event event, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE) && !TextUtils.equals(event.getSender(), mBobSession.getMyUserId())) {
                    mMessagesCount++;
                    list.get(0).countDown();
                }
            }
        };

        roomFromBobPOV.addEventListener(bobEventsListener);

        ApiCallback<Void> callback = new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                list.get(0).countDown();
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

        CountDownLatch lock = new CountDownLatch(2);
        list.clear();
        list.add(lock);
        roomFromAlicePOV.sendEvent(buildTextEvent(messagesFromAlice.get(0), mAliceSession), callback);
        lock.await(1000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue(mMessagesCount == 1);

        lock = new CountDownLatch(1);
        list.clear();
        list.add(lock);
        roomFromBobPOV.sendEvent(buildTextEvent(messagesFromBob.get(0), mBobSession), callback);
        // android does not echo the messages sent from itself
        mMessagesCount++;
        lock.await(1000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue(mMessagesCount == 2);

        lock = new CountDownLatch(1);
        list.clear();
        list.add(lock);
        roomFromBobPOV.sendEvent(buildTextEvent(messagesFromBob.get(1), mBobSession), callback);
        // android does not echo the messages sent from itself
        mMessagesCount++;
        lock.await(1000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue(mMessagesCount == 3);

        lock = new CountDownLatch(1);
        list.clear();
        list.add(lock);
        roomFromBobPOV.sendEvent(buildTextEvent(messagesFromBob.get(2), mBobSession), callback);
        // android does not echo the messages sent from itself
        mMessagesCount++;
        lock.await(1000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue(mMessagesCount == 4);

        lock = new CountDownLatch(2);
        list.clear();
        list.add(lock);
        roomFromAlicePOV.sendEvent(buildTextEvent(messagesFromAlice.get(1), mAliceSession), callback);
        lock.await(1000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue(mMessagesCount == 5);

        // the crypto store data is saved in background thread
        // so add a delay to let save the data
        SystemClock.sleep(500);
    }

    private boolean checkEncryptedEvent(Event event, String roomId, String clearMessage, MXSession senderSession) throws Exception {

        assertTrue(TextUtils.equals(event.getWireType(), Event.EVENT_TYPE_MESSAGE_ENCRYPTED));
        assertTrue(null != event.getWireContent());

        JsonObject eventWireContent = event.getWireContent().getAsJsonObject();

        assertTrue(null == eventWireContent.get("body"));
        assertTrue(TextUtils.equals(eventWireContent.get("algorithm").getAsString(), MXCryptoAlgorithms.MXCRYPTO_ALGORITHM_MEGOLM));

        assertTrue(null != eventWireContent.get("ciphertext"));
        assertTrue(null != eventWireContent.get("session_id"));
        assertTrue(null != eventWireContent.get("sender_key"));

        assertTrue(TextUtils.equals(eventWireContent.get("device_id").getAsString(), senderSession.getCredentials().deviceId));

        assertTrue(event.eventId != null);
        assertTrue(TextUtils.equals(event.roomId, roomId));
        assertTrue(TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE));
        assertTrue(event.getAge() < 2000);

        JsonObject eventContent = event.getContentAsJsonObject();
        assertTrue(TextUtils.equals(eventContent.get("body").getAsString(), clearMessage));
        assertTrue(TextUtils.equals(event.sender, senderSession.getMyUserId()));

        return true;
    }
}
