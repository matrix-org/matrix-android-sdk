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


package org.matrix.androidsdk;

import android.content.Context;
import android.net.Uri;
import android.os.SystemClock;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.text.TextUtils;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.matrix.androidsdk.crypto.MXCrypto;
import org.matrix.androidsdk.crypto.MXCryptoAlgorithms;
import org.matrix.androidsdk.crypto.MXCryptoError;
import org.matrix.androidsdk.crypto.MXDeviceList;
import org.matrix.androidsdk.crypto.data.MXDeviceInfo;
import org.matrix.androidsdk.crypto.data.MXOlmSessionResult;
import org.matrix.androidsdk.crypto.data.MXUsersDevicesMap;
import org.matrix.androidsdk.data.EventTimeline;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.data.store.IMXStore;
import org.matrix.androidsdk.data.store.MXFileStore;
import org.matrix.androidsdk.data.store.MXStoreListener;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.message.Message;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.login.Credentials;
import org.matrix.androidsdk.util.JsonUtils;
import org.matrix.androidsdk.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class CryptoTest {

    private static final String LOG_TAG = "CryptoTest";

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
        Log.e(LOG_TAG, "test01_testCryptoNoDeviceId");

        Context context = InstrumentationRegistry.getContext();
        final HashMap<String, Object> results = new HashMap<>();
        createBobAccount();

        assertTrue(null == mBobSession.getCrypto());
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
        lock1.await(1000, TimeUnit.MILLISECONDS);
        assertTrue(results.containsKey("enableCrypto"));

        assertTrue (null != mBobSession.getCrypto());
        assertTrue (null != mBobSession.getCredentials().deviceId);

        mBobSession.clear(context);
    }

    @Test
    public void test02_testCryptoPersistenceInStore() throws Exception {
        Log.e(LOG_TAG, "test02_testCryptoPersistenceInStore");
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
        lock0.await(1000, TimeUnit.MILLISECONDS);
        assertTrue(results.containsKey("enableCrypto"));

        assertTrue (null != mBobSession.getCrypto());

        SystemClock.sleep(1000);

        final String deviceCurve25519Key = mBobSession.getCrypto().getOlmDevice().getDeviceCurve25519Key();
        final String deviceEd25519Key = mBobSession.getCrypto().getOlmDevice().getDeviceEd25519Key();

        final List<MXDeviceInfo> myUserDevices = mBobSession.getCrypto().getUserDevices(mBobSession.getMyUserId());

        assertTrue (null != myUserDevices);
        assertTrue (1 == myUserDevices.size());

        final Credentials bobCredentials = mBobSession.getCredentials();

        Uri uri = Uri.parse(CryptoTestHelper.TESTS_HOME_SERVER_URL);
        HomeServerConnectionConfig hs = new HomeServerConnectionConfig(uri);
        hs.setCredentials(bobCredentials);

        IMXStore store =  new MXFileStore(hs, context);

        MXSession bobSession2 = new MXSession(hs, new MXDataHandler(store, bobCredentials), context);


        final CountDownLatch lock1 = new CountDownLatch(1);
        MXStoreListener listener = new  MXStoreListener() {
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
        lock1.await(1000, TimeUnit.MILLISECONDS);

        assertTrue (results.containsKey("onStoreReady"));
        assertTrue (bobSession2.isCryptoEnabled());

        final CountDownLatch lock2 = new CountDownLatch(2);

        MXEventListener eventsListener = new MXEventListener() {
            @Override
            public void onInitialSyncComplete(String toToken) {
                results.put("onInitialSyncComplete", "onInitialSyncComplete");
                lock2.countDown();
            }

            @Override
            public void onCryptoSyncComplete() {
                results.put("onCryptoSyncComplete", "onCryptoSyncComplete");
                lock2.countDown();
            }
        };
        bobSession2.getDataHandler().addListener(eventsListener);
        bobSession2.startEventStream(null);
        lock2.await(1000, TimeUnit.MILLISECONDS);
        assertTrue (results.containsKey("onInitialSyncComplete"));
        assertTrue (results.containsKey("onCryptoSyncComplete"));

        MXCrypto crypto = bobSession2.getCrypto();
        assertNotNull(crypto);

        assertTrue (TextUtils.equals(deviceCurve25519Key, crypto.getOlmDevice().getDeviceCurve25519Key()));
        assertTrue (TextUtils.equals(deviceEd25519Key, crypto.getOlmDevice().getDeviceEd25519Key()));

        List<MXDeviceInfo> myUserDevices2 = bobSession2.getCrypto().getUserDevices(bobSession2.getMyUserId());
        assertTrue (1 == myUserDevices2.size());
        assertTrue (TextUtils.equals(myUserDevices2.get(0).deviceId, myUserDevices.get(0).deviceId));

        mBobSession.clear(context);
        bobSession2.clear(context);
    }

    @Test
    public void test03_testKeysUploadAndDownload() throws Exception {
        Log.e(LOG_TAG, "test03_testKeysUploadAndDownload");

        Context context = InstrumentationRegistry.getContext();
        final HashMap<String, Object> results = new HashMap<>();

        createAliceAccount();
        mAliceSession.getCredentials().deviceId = "AliceDevice";

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
        lock0.await(1000, TimeUnit.MILLISECONDS);
        assertTrue(results.containsKey("enableCrypto"));

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
        lock2.await(1000, TimeUnit.MILLISECONDS);
        assertTrue(results.containsKey("enableCrypto2"));

        final CountDownLatch lock3 = new CountDownLatch(1);
        mBobSession.getCrypto().getDeviceList().downloadKeys(Arrays.asList(mBobSession.getMyUserId(), mAliceSession.getMyUserId()), false, new ApiCallback<MXUsersDevicesMap<MXDeviceInfo>>() {
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

        lock3.await(1000, TimeUnit.MILLISECONDS);
        assertTrue(results.containsKey("downloadKeys"));
        MXUsersDevicesMap<MXDeviceInfo> usersDevicesInfoMap = (MXUsersDevicesMap<MXDeviceInfo> )results.get("downloadKeys");

        assertTrue (2 == usersDevicesInfoMap.getUserIds().size());
        assertTrue (1 == usersDevicesInfoMap.getUserDeviceIds(mAliceSession.getMyUserId()).size());

        MXDeviceInfo aliceDeviceFromBobPOV = usersDevicesInfoMap.getObject("AliceDevice", mAliceSession.getMyUserId());
        assertTrue (null != aliceDeviceFromBobPOV);
        assertTrue (TextUtils.equals(aliceDeviceFromBobPOV.fingerprint(), mAliceSession.getCrypto().getOlmDevice().getDeviceEd25519Key()));

        // Continue testing other methods
        assertTrue (null != mBobSession.getCrypto().deviceWithIdentityKey(mAliceSession.getCrypto().getOlmDevice().getDeviceCurve25519Key(), mAliceSession.getMyUserId(), MXCryptoAlgorithms.MXCRYPTO_ALGORITHM_OLM));
        assertTrue (aliceDeviceFromBobPOV.isUnknown());

        final CountDownLatch lock3a = new CountDownLatch(1);
        mBobSession.getCrypto().setDevicesKnown(Arrays.asList(aliceDeviceFromBobPOV),
                new ApiCallback<Void>() {
                    @Override
                    public void onSuccess(Void info) {
                        results.put("setDevicesKnown", info);
                        lock3a.countDown();
                    }

                    @Override
                    public void onNetworkError(Exception e) {
                        lock3a.countDown();
                    }

                    @Override
                    public void onMatrixError(MatrixError e) {
                        lock3a.countDown();
                    }

                    @Override
                    public void onUnexpectedError(Exception e) {
                        lock3a.countDown();
                    }
                }
        );
        lock3a.await(1000, TimeUnit.MILLISECONDS);
        assertTrue(results.containsKey("setDevicesKnown"));
        assertTrue (aliceDeviceFromBobPOV.isUnverified());

        final CountDownLatch lock3b = new CountDownLatch(1);
        mBobSession.getCrypto().setDeviceVerification(
                MXDeviceInfo.DEVICE_VERIFICATION_BLOCKED,
                aliceDeviceFromBobPOV.deviceId,
                mAliceSession.getMyUserId(),
                new ApiCallback<Void>() {
                    @Override
                    public void onSuccess(Void info) {
                        results.put("setDeviceVerification1", info);
                        lock3b.countDown();
                    }

                    @Override
                    public void onNetworkError(Exception e) {
                        lock3b.countDown();
                    }

                    @Override
                    public void onMatrixError(MatrixError e) {
                        lock3b.countDown();
                    }

                    @Override
                    public void onUnexpectedError(Exception e) {
                        lock3b.countDown();
                    }
                }
        );
        lock3b.await(1000, TimeUnit.MILLISECONDS);
        assertTrue(results.containsKey("setDeviceVerification1"));
        assertTrue (aliceDeviceFromBobPOV.isBlocked());

        Credentials bobCredentials = mBobSession.getCredentials();

        Uri uri = Uri.parse(CryptoTestHelper.TESTS_HOME_SERVER_URL);
        HomeServerConnectionConfig hs = new HomeServerConnectionConfig(uri);
        hs.setCredentials(bobCredentials);

        IMXStore store =  new MXFileStore(hs, context);

        MXSession bobSession2 = new MXSession(hs, new MXDataHandler(store, bobCredentials), context);

        final CountDownLatch lock4 = new CountDownLatch(1);

        MXStoreListener listener = new MXStoreListener() {
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

        lock4.await(2000, TimeUnit.MILLISECONDS);
        assertTrue(results.containsKey("onStoreReady"));

        final CountDownLatch lock4b = new CountDownLatch(2);
        MXEventListener eventListener = new MXEventListener() {
            @Override
            public void onInitialSyncComplete(String toToken) {
                results.put("onInitialSyncComplete", "onInitialSyncComplete");
                lock4b.countDown();
            }

            @Override
            public void onCryptoSyncComplete() {
                results.put("onCryptoSyncComplete", "onCryptoSyncComplete");
                lock4b.countDown();
            }
        };

        bobSession2.getDataHandler().addListener(eventListener);

        bobSession2.startEventStream(null);
        lock4b.await(2000, TimeUnit.MILLISECONDS);
        assertTrue(results.containsKey("onInitialSyncComplete"));
        assertTrue(results.containsKey("onCryptoSyncComplete"));

        MXDeviceInfo aliceDeviceFromBobPOV2 = bobSession2.getCrypto().deviceWithIdentityKey(mAliceSession.getCrypto().getOlmDevice().getDeviceCurve25519Key(), mAliceSession.getMyUserId(), MXCryptoAlgorithms.MXCRYPTO_ALGORITHM_OLM);

        assertTrue (null != aliceDeviceFromBobPOV2);
        assertTrue (TextUtils.equals(aliceDeviceFromBobPOV2.fingerprint(), mAliceSession.getCrypto().getOlmDevice().getDeviceEd25519Key()));
        assertTrue (aliceDeviceFromBobPOV2.mVerified + " instead of " + MXDeviceInfo.DEVICE_VERIFICATION_BLOCKED, aliceDeviceFromBobPOV2.mVerified == MXDeviceInfo.DEVICE_VERIFICATION_BLOCKED);

        // Download again alice device
        final CountDownLatch lock5 = new CountDownLatch(1);
        bobSession2.getCrypto().getDeviceList().downloadKeys(Arrays.asList(mAliceSession.getMyUserId()), true, new ApiCallback<MXUsersDevicesMap<MXDeviceInfo>>() {
            @Override
            public void onSuccess(MXUsersDevicesMap<MXDeviceInfo> info) {
                results.put("downloadKeys2", info);
                lock5.countDown();
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
        lock5.await(1000, TimeUnit.MILLISECONDS);
        assertTrue(results.containsKey("downloadKeys2"));

        MXDeviceInfo aliceDeviceFromBobPOV3 = bobSession2.getCrypto().deviceWithIdentityKey(mAliceSession.getCrypto().getOlmDevice().getDeviceCurve25519Key(), mAliceSession.getMyUserId(), MXCryptoAlgorithms.MXCRYPTO_ALGORITHM_OLM);

        assertTrue (null != aliceDeviceFromBobPOV3);
        assertTrue (TextUtils.equals(aliceDeviceFromBobPOV3.fingerprint(), mAliceSession.getCrypto().getOlmDevice().getDeviceEd25519Key()));
        assertTrue(aliceDeviceFromBobPOV3.isBlocked());

        mAliceSession.clear(context);
        mBobSession.clear(context);
        bobSession2.clear(context);
    }

    @Test
    public void test04_testEnsureOlmSessionsForUsers() throws Exception {
        Log.e(LOG_TAG, "test04_testEnsureOlmSessionsForUsers");

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

        lock0.await(1000, TimeUnit.MILLISECONDS);
        assertTrue(results.containsKey("enableCryptoAlice"));

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

        lock2.await(1000, TimeUnit.MILLISECONDS);
        assertTrue(results.containsKey("enableCryptoBob"));

        final CountDownLatch lock3 = new CountDownLatch(1);
        mBobSession.getCrypto().getDeviceList().downloadKeys(Arrays.asList(mBobSession.getMyUserId(), mAliceSession.getMyUserId()), false, new ApiCallback<MXUsersDevicesMap<MXDeviceInfo>>() {
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

        lock3.await(1000, TimeUnit.MILLISECONDS);
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

        lock4.await(1000, TimeUnit.MILLISECONDS);
        assertTrue(results.containsKey("ensureOlmSessionsForUsers"));

        MXUsersDevicesMap<MXOlmSessionResult> result = (MXUsersDevicesMap<MXOlmSessionResult>)results.get("ensureOlmSessionsForUsers");

        assertTrue (result.getUserIds().size() == 1);

        MXOlmSessionResult sessionWithAliceDevice = result.getObject("AliceDevice", mAliceSession.getMyUserId());

        assertTrue (null != sessionWithAliceDevice);
        assertTrue (null != sessionWithAliceDevice.mSessionId);
        assertTrue (TextUtils.equals(sessionWithAliceDevice.mDevice.deviceId, "AliceDevice"));

        Credentials bobCredentials = mBobSession.getCredentials();

        Uri uri = Uri.parse(CryptoTestHelper.TESTS_HOME_SERVER_URL);
        HomeServerConnectionConfig hs = new HomeServerConnectionConfig(uri);
        hs.setCredentials(bobCredentials);

        IMXStore store =  new MXFileStore(hs, context);

        MXSession bobSession2 = new MXSession(hs, new MXDataHandler(store, bobCredentials), context);

        final CountDownLatch lock5 = new CountDownLatch(1);

        MXStoreListener listener = new MXStoreListener() {
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
        lock5.await(1000, TimeUnit.MILLISECONDS);
        assertTrue(results.containsKey("onStoreReady"));

        final CountDownLatch lock5b = new CountDownLatch(2);
        MXEventListener eventListener = new MXEventListener() {
            @Override
            public void onInitialSyncComplete(String toToken) {
                results.put("onInitialSyncComplete", "onInitialSyncComplete");
                lock5b.countDown();
            }

            @Override
            public void onCryptoSyncComplete() {
                results.put("onCryptoSyncComplete", "onCryptoSyncComplete");
                lock5b.countDown();
            }
        };

        bobSession2.getDataHandler().addListener(eventListener);
        bobSession2.startEventStream(null);

        lock5b.await(1000, TimeUnit.MILLISECONDS);
        assertTrue(results.containsKey("onInitialSyncComplete"));
        assertTrue(results.containsKey("onCryptoSyncComplete"));

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
        lock6.await(1000, TimeUnit.MILLISECONDS);
        assertTrue (results.containsKey("ensureOlmSessionsForUsers2"));

        MXUsersDevicesMap<MXOlmSessionResult> result2 = (MXUsersDevicesMap<MXOlmSessionResult>)results.get("ensureOlmSessionsForUsers2");

        MXOlmSessionResult sessionWithAliceDevice2 = result2.getObject("AliceDevice", mAliceSession.getMyUserId());
        assertTrue (null != sessionWithAliceDevice2);
        assertTrue (null != sessionWithAliceDevice2.mSessionId);
        assertTrue (TextUtils.equals(sessionWithAliceDevice2.mDevice.deviceId, "AliceDevice"));

        mBobSession.clear(context);
        mAliceSession.clear(context);
        bobSession2.clear(context);
    }

    @Test
    public void test05_testRoomIsEncrypted() throws Exception {
        Log.e(LOG_TAG, "test05_testRoomIsEncrypted");

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
        lock0.await(1000, TimeUnit.MILLISECONDS);
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

        lock1.await(1000, TimeUnit.MILLISECONDS);
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
        lock2.await(1000, TimeUnit.MILLISECONDS);
        assertTrue (results.containsKey("enableEncryptionWithAlgorithm"));

        assertTrue (room.isEncrypted());

        mBobSession.clear(context);
    }

    @Test
    public void test06_testAliceInACryptedRoom() throws Exception {
        Log.e(LOG_TAG, "test06_testAliceInACryptedRoom");

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

        lock1.await(1000, TimeUnit.MILLISECONDS);

        mAliceSession.clear(context);
    }

    @Test
    public void test07_testAliceAndBobInACryptedRoom() throws Exception {
        Log.e(LOG_TAG, "test07_testAliceAndBobInACryptedRoom");

        Context context = InstrumentationRegistry.getContext();
        final HashMap<String, Object> results = new HashMap<>();

        doE2ETestWithAliceAndBobInARoom(true);

        final String messageFromAlice = "Hello I'm Alice!";

        Room roomFromBobPOV = mBobSession.getDataHandler().getRoom(mRoomId);
        Room roomFromAlicePOV =  mAliceSession.getDataHandler().getRoom(mRoomId);

        assertTrue(roomFromBobPOV.isEncrypted());
        assertTrue(roomFromAlicePOV.isEncrypted());

        final CountDownLatch lock1 = new CountDownLatch(1);

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
                results.put("sendEventError", e);
                lock1.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                lock1.countDown();
            }
        });

        lock1.await(2000, TimeUnit.MILLISECONDS);
        assertTrue(results.containsKey("sendEventError"));
        MXCryptoError error = (MXCryptoError)results.get("sendEventError");
        assertTrue(TextUtils.equals(error.errcode, MXCryptoError.UNKNOWN_DEVICES_CODE));
        MXUsersDevicesMap<MXDeviceInfo> unknownDevices = (MXUsersDevicesMap<MXDeviceInfo> )error.mExceptionData;
        List<String> deviceInfos = unknownDevices.getUserDeviceIds(mBobSession.getMyUserId());
        assertTrue(1 == deviceInfos.size());
        assertTrue(TextUtils.equals(deviceInfos.get(0), mBobSession.getCrypto().getMyDevice().deviceId));

        final CountDownLatch lock2 = new CountDownLatch(1);
        mAliceSession.getCrypto().setDevicesKnown(Arrays.asList(mBobSession.getCrypto().getMyDevice()), new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                results.put("setDevicesKnown", "setDevicesKnown");
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

        lock2.await(2000, TimeUnit.MILLISECONDS);
        assertTrue(results.containsKey("setDevicesKnown"));

        final CountDownLatch lock3 = new CountDownLatch(3);

        MXEventListener eventListener = new MXEventListener() {
            @Override
            public void onLiveEvent(Event event, RoomState roomState) {
                try {
                    if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE)) {
                        if (checkEncryptedEvent(event, mRoomId, messageFromAlice, mAliceSession)) {
                            results.put("onLiveEvent", "onLiveEvent");
                            lock3.countDown();
                        }
                    }
                } catch (Exception e) {
                }
            }
        };

        mBobSession.getDataHandler().addListener(new MXEventListener() {
            @Override
            public void onToDeviceEvent(Event event) {
                results.put("onToDeviceEvent", event);
                lock3.countDown();
            }
        });

        roomFromBobPOV.addEventListener(eventListener);

        roomFromAlicePOV.sendEvent(buildTextEvent(messageFromAlice, mAliceSession), new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
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

        lock3.await(2000, TimeUnit.MILLISECONDS);
        assertTrue(results.containsKey("onToDeviceEvent"));
        assertTrue(results.containsKey("onLiveEvent"));

        assertTrue(mBobSession.getCrypto().getDeviceTrackingStatus(mBobSession.getMyUserId()) == MXDeviceList.TRACKING_STATUS_UP_TO_DATE);
        assertTrue(mBobSession.getCrypto().getDeviceTrackingStatus(mAliceSession.getMyUserId()) == MXDeviceList.TRACKING_STATUS_UP_TO_DATE);

        assertTrue(mAliceSession.getCrypto().getDeviceTrackingStatus(mBobSession.getMyUserId()) == MXDeviceList.TRACKING_STATUS_UP_TO_DATE);
        assertTrue(mAliceSession.getCrypto().getDeviceTrackingStatus(mAliceSession.getMyUserId()) == MXDeviceList.TRACKING_STATUS_UP_TO_DATE);

        mBobSession.clear(context);
    }

    @Test
    public void test08_testAliceAndBobInACryptedRoom2() throws Exception {
        Log.e(LOG_TAG, "test08_testAliceAndBobInACryptedRoom2");

        doE2ETestWithAliceAndBobInARoom(true);

        mBobSession.getCrypto().setWarnOnUnknownDevices(false);
        mAliceSession.getCrypto().setWarnOnUnknownDevices(false);

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
                        }

                        list.get(list.size()-1).countDown();
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

        list.add(new CountDownLatch(2));
        final HashMap<String, Object> results = new HashMap<>();

        mBobSession.getDataHandler().addListener(new MXEventListener() {
            @Override
            public void onToDeviceEvent(Event event) {
                results.put("onToDeviceEvent", event);
                list.get(0).countDown();
            }
        });

        roomFromAlicePOV.sendEvent(buildTextEvent(messagesFromAlice.get(mReceivedMessagesFromAlice), mAliceSession), callback);
        list.get(list.size()-1).await(1000, TimeUnit.MILLISECONDS);
        assertTrue(results.containsKey("onToDeviceEvent"));
        assertTrue(1 == mReceivedMessagesFromAlice);

        list.add(new CountDownLatch(1));
        roomFromBobPOV.sendEvent(buildTextEvent(messagesFromBob.get(mReceivedMessagesFromBob), mBobSession), callback);
        list.get(list.size()-1).await(1000, TimeUnit.MILLISECONDS);
        assertTrue(1 == mReceivedMessagesFromBob);

        list.add(new CountDownLatch(1));
        roomFromBobPOV.sendEvent(buildTextEvent(messagesFromBob.get(mReceivedMessagesFromBob), mBobSession), callback);
        list.get(list.size()-1).await(1000, TimeUnit.MILLISECONDS);
        assertTrue(2 == mReceivedMessagesFromBob);

        list.add(new CountDownLatch(1));
        roomFromBobPOV.sendEvent(buildTextEvent(messagesFromBob.get(mReceivedMessagesFromBob), mBobSession), callback);
        list.get(list.size()-1).await(1000, TimeUnit.MILLISECONDS);
        assertTrue(3 == mReceivedMessagesFromBob);

        list.add(new CountDownLatch(1));
        roomFromAlicePOV.sendEvent(buildTextEvent(messagesFromAlice.get(mReceivedMessagesFromAlice), mAliceSession), callback);
        list.get(list.size()-1).await(1000, TimeUnit.MILLISECONDS);
        assertTrue(2 == mReceivedMessagesFromAlice);
    }

    @Test
    public void test09_testAliceInACryptedRoomAfterInitialSync() throws Exception {
        Log.e(LOG_TAG, "test09_testAliceInACryptedRoomAfterInitialSync");

        Context context = InstrumentationRegistry.getContext();
        final HashMap<String, Object> results = new HashMap<>();

        doE2ETestWithAliceInARoom();

        mAliceSession.getCrypto().setWarnOnUnknownDevices(false);

        final String message = "Hello myself!";

        Credentials aliceCredentials = mAliceSession.getCredentials();

        mAliceSession.clear(context);

        Uri uri = Uri.parse(CryptoTestHelper.TESTS_HOME_SERVER_URL);
        HomeServerConnectionConfig hs = new HomeServerConnectionConfig(uri);
        hs.setCredentials(aliceCredentials);

        IMXStore store =  new MXFileStore(hs, context);

        final CountDownLatch lock1 = new CountDownLatch(1);

        final MXSession aliceSession2 = new MXSession(hs, new MXDataHandler(store, aliceCredentials), context);

        MXStoreListener listener = new MXStoreListener() {
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
        lock1.await(1000, TimeUnit.MILLISECONDS);
        assertTrue (results.containsKey("onStoreReady"));

        final CountDownLatch lock1b = new CountDownLatch(2);
        MXEventListener eventListener = new MXEventListener() {
            @Override
            public void onInitialSyncComplete(String toToken) {
                results.put("onInitialSyncComplete", "onInitialSyncComplete");
                lock1b.countDown();
            }

            @Override
            public void onCryptoSyncComplete() {
                results.put("onCryptoSyncComplete", "onCryptoSyncComplete");
                lock1b.countDown();
            }
        };

        aliceSession2.getDataHandler().addListener(eventListener);
        aliceSession2.startEventStream(null);
        lock1b.await(1000, TimeUnit.MILLISECONDS);
        assertTrue (results.containsKey("onInitialSyncComplete"));
        assertTrue (results.containsKey("onCryptoSyncComplete"));

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

        lock2.await(1000, TimeUnit.MILLISECONDS);
        assertTrue(results.containsKey("sendEvent"));

        aliceSession2.clear(context);
    }

    @Test
    public void test10_testAliceDecryptOldMessageWithANewDeviceInACryptedRoom() throws Exception {
        Log.e(LOG_TAG, "test10_testAliceDecryptOldMessageWithANewDeviceInACryptedRoom");

        Context context = InstrumentationRegistry.getContext();
        final HashMap<String, Object> results = new HashMap<>();

        doE2ETestWithAliceInARoom();

        mAliceSession.getCrypto().setWarnOnUnknownDevices(false);

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
        lock1.await(1000, TimeUnit.MILLISECONDS);
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
        HomeServerConnectionConfig hs = new HomeServerConnectionConfig(uri);
        hs.setCredentials(aliceCredentials2);

        IMXStore store =  new MXFileStore(hs, context);

        MXSession aliceSession2 = new MXSession(hs, new MXDataHandler(store, aliceCredentials2), context);

        aliceSession2.enableCryptoWhenStarting();

        final CountDownLatch lock1b = new CountDownLatch(1);
        MXStoreListener listener = new MXStoreListener() {
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
        lock1b.await(1000, TimeUnit.MILLISECONDS);
        assertTrue(results.containsKey("onStoreReady"));

        final CountDownLatch lock2 = new CountDownLatch(2);
        MXEventListener eventListener = new MXEventListener() {
            @Override
            public void onInitialSyncComplete(String toToken) {
                results.put("onInitialSyncComplete", "onInitialSyncComplete");
                lock2.countDown();
            }

            @Override
            public void onCryptoSyncComplete() {
                results.put("onCryptoSyncComplete", "onCryptoSyncComplete");
                lock2.countDown();
            }
        };

        aliceSession2.getDataHandler().addListener(eventListener);
        aliceSession2.startEventStream(null);

        lock2.await(1000, TimeUnit.MILLISECONDS);

        assertTrue (results.containsKey("onInitialSyncComplete"));
        assertTrue (results.containsKey("onCryptoSyncComplete"));

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
        Log.e(LOG_TAG, "test11_testAliceAndBobInACryptedRoomBackPaginationFromMemoryStore");

        Context context = InstrumentationRegistry.getContext();
        final HashMap<String, Object> results = new HashMap();

        doE2ETestWithAliceAndBobInARoomWithCryptedMessages(true);

        Credentials bobCredentials = mBobSession.getCredentials();
        mBobSession.clear(context);

        Uri uri = Uri.parse(CryptoTestHelper.TESTS_HOME_SERVER_URL);
        HomeServerConnectionConfig hs = new HomeServerConnectionConfig(uri);
        hs.setCredentials(bobCredentials);

        IMXStore store =  new MXFileStore(hs, context);

        final CountDownLatch lock1 = new CountDownLatch(2);

        MXSession bobSession2 = new MXSession(hs, new MXDataHandler(store, bobCredentials), context);

        MXEventListener eventListener = new MXEventListener() {
            @Override
            public void onInitialSyncComplete(String toToken) {
                results.put("onInitialSyncComplete", "onInitialSyncComplete");
                lock1.countDown();
            }

            @Override
            public void onCryptoSyncComplete() {
                results.put("onCryptoSyncComplete", "onCryptoSyncComplete");
                lock1.countDown();
            }
        };

        bobSession2.getDataHandler().addListener(eventListener);
        bobSession2.getDataHandler().getStore().open();
        bobSession2.startEventStream(null);

        lock1.await(1000, TimeUnit.MILLISECONDS);

        assertTrue (results.containsKey("onInitialSyncComplete"));
        assertTrue (results.containsKey("onCryptoSyncComplete"));

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

        lock2.await(1000, TimeUnit.MILLISECONDS);
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
        Log.e(LOG_TAG, "test12_testAliceAndBobInACryptedRoomBackPaginationFromHomeServer");

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

        lock2.await(1000, TimeUnit.MILLISECONDS);
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
        Log.e(LOG_TAG, "test13_testAliceAndNotCryptedBobInACryptedRoom");

        final HashMap<String, Object> results = new HashMap();

        doE2ETestWithAliceAndBobInARoom(false);

        mAliceSession.getCrypto().setWarnOnUnknownDevices(false);

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

        lock1.await(1000, TimeUnit.MILLISECONDS);
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

        lock2.await(1000, TimeUnit.MILLISECONDS);
        assertTrue(results.containsKey("aliceEcho"));

        event = (Event)results.get("aliceEcho");
        assertTrue(!event.isEncrypted());
    }

    @Test
    public void test14_testCryptoDeviceBlockAndLeave() throws Exception {
        Log.e(LOG_TAG, "test14_testCryptoDeviceBlockAndLeave");

        Context context = InstrumentationRegistry.getContext();
        final HashMap<String, Object> results = new HashMap<>();

        doE2ETestWithAliceAndBobAndSamInARoom();

        mBobSession.getCrypto().setWarnOnUnknownDevices(false);
        mAliceSession.getCrypto().setWarnOnUnknownDevices(false);
        mSamSession.getCrypto().setWarnOnUnknownDevices(false);

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
        lock0.await(1000, TimeUnit.MILLISECONDS);
        assertTrue(results + "", results.containsKey("send0") && results.containsKey("alice0") && results.containsKey("sam0"));

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
                }  else if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE_ENCRYPTED)) {
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
        lock1.await(30000, TimeUnit.MILLISECONDS);
        assertTrue(results + "", results.containsKey("send1") && results.containsKey("bob1") && results.containsKey("sam1"));

        List<MXDeviceInfo> list = mBobSession.getCrypto().getUserDevices(mAliceSession.getMyUserId());

        assertTrue(null != list);
        assertTrue(list.size() > 0);

        final CountDownLatch lock1b = new CountDownLatch(1);
        mBobSession.getCrypto().setDeviceVerification(MXDeviceInfo.DEVICE_VERIFICATION_BLOCKED, list.get(0).deviceId, mAliceSession.getMyUserId(), new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                results.put("setDeviceVerification10", "setDeviceVerification10");
                lock1b.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {
                lock1b.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                lock1b.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                lock1b.countDown();
            }
        });
        lock1b.await(1000, TimeUnit.MILLISECONDS);
        assertTrue(results.containsKey("setDeviceVerification10"));

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

        lock2.await(1000, TimeUnit.MILLISECONDS);
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

        lock3.await(1000, TimeUnit.MILLISECONDS);
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

        lock4.await(1000, TimeUnit.MILLISECONDS);
        assertTrue(results.containsKey("send3") && results.containsKey("alice3"));

        mBobSession.clear(context);
        mAliceSession.clear(context);
        mSamSession.clear(context);
    }

    @Test
    public void test15_testReplayAttack() throws Exception {
        Log.e(LOG_TAG, "test15_testReplayAttack");
        final HashMap<String, Object> results = new HashMap<>();

        doE2ETestWithAliceAndBobInARoom(true);

        mBobSession.getCrypto().setWarnOnUnknownDevices(false);
        mAliceSession.getCrypto().setWarnOnUnknownDevices(false);

        String messageFromAlice = "Hello I'm Alice!";

        final Room roomFromBobPOV = mBobSession.getDataHandler().getRoom(mRoomId);
        final Room roomFromAlicePOV = mAliceSession.getDataHandler().getRoom(mRoomId);

        assertTrue(roomFromBobPOV.isEncrypted());
        assertTrue(roomFromAlicePOV.isEncrypted());

        final CountDownLatch lock1 = new CountDownLatch(2);
        MXEventListener bobEventListener = new MXEventListener() {
            @Override
            public void onLiveEvent(Event event, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE) && !TextUtils.equals(event.getSender(), mBobSession.getMyUserId())) {
                    results.put("bobEcho", event);

                    event.setClearData(null);

                    mBobSession.getDataHandler().decryptEvent(event, roomFromBobPOV.getLiveTimeLine().getTimelineId());
                    results.put("decrypted", event);

                    lock1.countDown();
                }
            }
        };

        roomFromBobPOV.addEventListener(bobEventListener);

        mBobSession.getDataHandler().addListener(new MXEventListener() {
            @Override
            public void onToDeviceEvent(Event event) {
                results.put("onToDeviceEvent", event);
                lock1.countDown();
            }
        });

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

        lock1.await(1000, TimeUnit.MILLISECONDS);
        assertTrue(results.containsKey("onToDeviceEvent"));
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
        Log.e(LOG_TAG, "test16_testRoomKeyReshare");

        final HashMap<String, Object> results = new HashMap<>();

        doE2ETestWithAliceAndBobInARoom(true);

        mBobSession.getCrypto().setWarnOnUnknownDevices(false);
        mAliceSession.getCrypto().setWarnOnUnknownDevices(false);

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

        lock1.await(2000, TimeUnit.MILLISECONDS);
        assertTrue(results.containsKey("onToDeviceEvent"));
        assertTrue(1 == receivedEvents.size());

        Event event = receivedEvents.get(0);
        assertTrue(checkEncryptedEvent(event, mRoomId, messageFromAlice, mAliceSession));

        // Reinject a modified version of the received room_key event from Alice.
        // From Bob pov, that mimics Alice resharing her keys but with an advanced outbound group session.
        Event toDeviceEvent = (Event)results.get("onToDeviceEvent");
        String sessionId = toDeviceEvent.getContentAsJsonObject().get("session_id").getAsString();
        String newSessionKey = mAliceSession.getCrypto().getOlmDevice().getSessionKey(sessionId);

        JsonObject content = toDeviceEvent.getClearEvent().getContentAsJsonObject();
        content.add("session_key", new JsonPrimitive(newSessionKey));
        mBobSession.getDataHandler().onToDeviceEvent(toDeviceEvent);


        // We still must be able to decrypt the event
        // ie, the implementation must have ignored the new room key with the advanced outbound group
        // session key
        event.setClearData(null);

        mBobSession.getDataHandler().decryptEvent(event, null);
        assertTrue(checkEncryptedEvent(event, mRoomId, messageFromAlice, mAliceSession));
    }

    @Test
    public void test17_testLateRoomKey() throws Exception {
        Log.e(LOG_TAG, "test17_testLateRoomKey");

        final HashMap<String, Object> results = new HashMap<>();

        doE2ETestWithAliceAndBobInARoom(true);

        mBobSession.getCrypto().setWarnOnUnknownDevices(false);
        mAliceSession.getCrypto().setWarnOnUnknownDevices(false);

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

        lock1.await(2000, TimeUnit.MILLISECONDS);
        assertTrue(results.containsKey("onToDeviceEvent"));
        assertTrue(1 == receivedEvents.size());

        Event event = receivedEvents.get(0);
        assertTrue(checkEncryptedEvent(event, mRoomId, messageFromAlice, mAliceSession));

        // Reinject a modified version of the received room_key event from Alice.
        // From Bob pov, that mimics Alice resharing her keys but with an advanced outbound group session.
        Event toDeviceEvent = (Event)results.get("onToDeviceEvent");
        String sessionId = toDeviceEvent.getContentAsJsonObject().get("session_id").getAsString();
        String senderKey = toDeviceEvent.senderKey();

        // remove the session
        mBobSession.getCrypto().getOlmDevice().removeInboundGroupSession(sessionId, senderKey);

        event.setClearData(null);

        // check that the message cannot be decrypted
        assertTrue(!mBobSession.getDataHandler().decryptEvent(event, null));
        // check the error code
        assertTrue(TextUtils.equals(event.getCryptoError().errcode, MXCryptoError.UNKNOWN_INBOUND_SESSION_ID_ERROR_CODE));

        receivedEvents.clear();

        final CountDownLatch lock2 = new CountDownLatch(1);
        roomFromBobPOV.addEventListener(new MXEventListener() {
            @Override
            public void onEventDecrypted(Event event) {
                results.put("onEventDecrypted", "onEventDecrypted");
                receivedEvents.add(event);
                lock2.countDown();
            }
        });

        event.setClearData(null);

        // reinject the session key
        mBobSession.getDataHandler().onToDeviceEvent(toDeviceEvent);

        // the message should be decrypted later
        lock2.await(1000, TimeUnit.MILLISECONDS);
        assertTrue(results.containsKey("onEventDecrypted"));
        assertTrue(1 == receivedEvents.size());

        assertTrue(checkEncryptedEvent(receivedEvents.get(0), mRoomId, messageFromAlice, mAliceSession));
        assertTrue(null == receivedEvents.get(0).getCryptoError());
    }

    @Test
    public void test18_testAliceAndBobWithNewDevice() throws Exception {
        Log.e(LOG_TAG, "test18_testAliceAndBobWithNewDevice");

        Context context = InstrumentationRegistry.getContext();
        final HashMap<String, Object> results = new HashMap<>();
        doE2ETestWithAliceAndBobInARoom(true);

        mBobSession.getCrypto().setWarnOnUnknownDevices(false);
        mAliceSession.getCrypto().setWarnOnUnknownDevices(false);

        final Room roomFromBobPOV = mBobSession.getDataHandler().getRoom(mRoomId);
        final Room roomFromAlicePOV = mAliceSession.getDataHandler().getRoom(mRoomId);

        String bobDeviceId1 = mBobSession.getCredentials().deviceId;

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

        String aliceMessage1 = "Hello I'm Alice!";

        roomFromAlicePOV.sendEvent(buildTextEvent(aliceMessage1, mAliceSession), new ApiCallback<Void>() {
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

        lock1.await(2000, TimeUnit.MILLISECONDS);
        assertTrue(results.containsKey("onToDeviceEvent"));
        assertTrue(1 == receivedEvents.size());

        Event event = receivedEvents.get(0);
        assertTrue(checkEncryptedEvent(event, mRoomId, aliceMessage1, mAliceSession));

        // logout
        final CountDownLatch lock2 = new CountDownLatch(1);
        String bobId = mBobSession.getCredentials().userId;
        mBobSession.logout(context, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                results.put("logout", "logout");
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
        lock2.await(1000, TimeUnit.MILLISECONDS);
        assertTrue(results.containsKey("logout"));

        final CountDownLatch lock3 = new CountDownLatch(1);
        MXEventListener aliceEventListener = new MXEventListener() {
            @Override
            public void onToDeviceEvent(Event event) {
                if (!results.containsKey("onToDeviceEvent2")) {
                    results.put("onToDeviceEvent2", event);
                    lock3.countDown();
                }
            }
        };

        mAliceSession.getDataHandler().addListener(aliceEventListener);

        // login with a new device id
        MXSession bobSession2 = CryptoTestHelper.logAccountAndSync(context, bobId, MXTESTS_BOB_PWD);

        String bobDeviceId2 = bobSession2.getCredentials().deviceId;
        assertTrue(!TextUtils.equals(bobDeviceId2, bobDeviceId1));

        // before sending a message, wait that the device event is received.
        lock3.await(10000, TimeUnit.MILLISECONDS);
        assertTrue(results.containsKey("onToDeviceEvent2"));

        SystemClock.sleep(1000);

        final Room roomFromBobPOV2 = bobSession2.getDataHandler().getRoom(mRoomId);
        assertTrue(null != roomFromBobPOV2);

        final ArrayList<Event> receivedEvents4 = new ArrayList<>();
        final CountDownLatch lock4 = new CountDownLatch(1);

        EventTimeline.EventTimelineListener eventTimelineListener4 = new EventTimeline.EventTimelineListener() {
            public void onEvent(Event event, EventTimeline.Direction direction, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE)) {
                    receivedEvents4.add(event);
                    lock4.countDown();
                } else {
                    results.put("event4", event.getType() + "");
                    lock4.countDown();
                }
            }
        };

        roomFromBobPOV2.getLiveTimeLine().addEventTimelineListener(eventTimelineListener4);

        String aliceMessage2 = "Hello I'm still Alice!";
        roomFromAlicePOV.sendEvent(buildTextEvent(aliceMessage2, mAliceSession), new ApiCallback<Void>() {
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

        lock4.await(5000, TimeUnit.MILLISECONDS);
        assertTrue("received event of type " + results.get("event4"), 1 == receivedEvents4.size());

        event = receivedEvents4.get(0);
        assertTrue(checkEncryptedEvent(event, mRoomId, aliceMessage2, mAliceSession));
    }

    @Test
    public void test19_testAliceWithNewDeviceAndBobWithNewDevice() throws Exception {
        Log.e(LOG_TAG, "test19_testAliceWithNewDeviceAndBobWithNewDevice");

        Context context = InstrumentationRegistry.getContext();
        final HashMap<String, Object> results = new HashMap<>();
        doE2ETestWithAliceAndBobInARoom(true);

        mBobSession.getCrypto().setWarnOnUnknownDevices(false);
        mAliceSession.getCrypto().setWarnOnUnknownDevices(false);

        final Room roomFromBobPOV = mBobSession.getDataHandler().getRoom(mRoomId);
        final Room roomFromAlicePOV = mAliceSession.getDataHandler().getRoom(mRoomId);

        String bobUserId1 = mBobSession.getMyUserId();
        String aliceUserId1 = mAliceSession.getMyUserId();

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

        String aliceMessage1 = "Hello I'm Alice!";

        roomFromAlicePOV.sendEvent(buildTextEvent(aliceMessage1, mAliceSession), new ApiCallback<Void>() {
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

        lock1.await(2000, TimeUnit.MILLISECONDS);
        assertTrue(results.containsKey("onToDeviceEvent"));
        assertTrue(1 == receivedEvents.size());

        Event event = receivedEvents.get(0);
        assertTrue(checkEncryptedEvent(event, mRoomId, aliceMessage1, mAliceSession));

        // logout
        final CountDownLatch lock2 = new CountDownLatch(1);
        mBobSession.logout(context, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                results.put("boblogout", "boblogout");
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
        lock2.await(1000, TimeUnit.MILLISECONDS);
        assertTrue(results.containsKey("boblogout"));

        final CountDownLatch lock3 = new CountDownLatch(1);
        mAliceSession.logout(context, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                results.put("alicelogout", "alicelogout");
                lock3.countDown();
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
        lock3.await(1000, TimeUnit.MILLISECONDS);
        assertTrue(results.containsKey("alicelogout"));

        MXSession bobSession2 = CryptoTestHelper.logAccountAndSync(context, bobUserId1, MXTESTS_BOB_PWD);
        assertTrue(null != bobSession2);
        bobSession2.getCrypto().setWarnOnUnknownDevices(false);

        MXSession aliceSession2 = CryptoTestHelper.logAccountAndSync(context, aliceUserId1, MXTESTS_ALICE_PWD);
        assertTrue(null != aliceSession2);
        aliceSession2.getCrypto().setWarnOnUnknownDevices(false);

        Room roomFromBob2POV = bobSession2.getDataHandler().getRoom(mRoomId);
        Room roomFromAlice2POV = aliceSession2.getDataHandler().getRoom(mRoomId);

        assertTrue(roomFromBob2POV.isEncrypted());
        event = bobSession2.getDataHandler().getStore().getLatestEvent(mRoomId);
        assertTrue(null != event);
        assertTrue(event.isEncrypted());
        assertTrue(null == event.getClearEvent());
        assertTrue(null != event.getCryptoError());
        assertTrue(TextUtils.equals(event.getCryptoError().errcode, MXCryptoError.UNKNOWN_INBOUND_SESSION_ID_ERROR_CODE));

        final CountDownLatch lock4 = new CountDownLatch(1);
        final ArrayList<Event> receivedEvents2 = new ArrayList<>();
        EventTimeline.EventTimelineListener eventTimelineListener2 = new EventTimeline.EventTimelineListener() {
            public void onEvent(Event event, EventTimeline.Direction direction, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE)) {
                    receivedEvents2.add(event);
                    lock4.countDown();
                }
            }
        };
        roomFromBob2POV.getLiveTimeLine().addEventTimelineListener(eventTimelineListener2);

        String messageFromAlice2 = "Hello I'm still Alice!";
        roomFromAlice2POV.sendEvent(buildTextEvent(messageFromAlice2, aliceSession2), new ApiCallback<Void>() {
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

        lock4.await(1000, TimeUnit.MILLISECONDS);
        assertTrue(1 == receivedEvents2.size());

        event = receivedEvents2.get(0);
        assertTrue(checkEncryptedEvent(event, mRoomId, messageFromAlice2, aliceSession2));
    }

    @Test
    public void test20_testAliceAndBlockedBob() throws Exception {
        Log.e(LOG_TAG, "test20_testAliceAndBlockedBob");
        final HashMap<String, String> results = new HashMap<>();

        doE2ETestWithAliceAndBobInARoom(true);

        mBobSession.getCrypto().setWarnOnUnknownDevices(false);
        mAliceSession.getCrypto().setWarnOnUnknownDevices(false);

        final Room roomFromBobPOV = mBobSession.getDataHandler().getRoom(mRoomId);
        final Room roomFromAlicePOV = mAliceSession.getDataHandler().getRoom(mRoomId);

        assertTrue(roomFromBobPOV.isEncrypted());
        assertTrue(roomFromAlicePOV.isEncrypted());

        final CountDownLatch lock1 = new CountDownLatch(1);

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

        String aliceMessage1 = "Hello I'm Alice!";

        roomFromAlicePOV.sendEvent(buildTextEvent(aliceMessage1, mAliceSession), new ApiCallback<Void>() {
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

        lock1.await(1000, TimeUnit.MILLISECONDS);
        assertTrue(1 == receivedEvents.size());

        Event event = receivedEvents.get(0);
        assertTrue(checkEncryptedEvent(event, mRoomId, aliceMessage1, mAliceSession));

        // block the bob's device
        final CountDownLatch lock1b = new CountDownLatch(1);
        mAliceSession.getCrypto().setDeviceVerification(MXDeviceInfo.DEVICE_VERIFICATION_BLOCKED, mBobSession.getCredentials().deviceId, mBobSession.getMyUserId(), new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                results.put("setDeviceVerification20", "setDeviceVerification20");
                lock1b.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {
                lock1b.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                lock1b.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                lock1b.countDown();
            }
        });
        lock1b.await();
        assertTrue(results.containsKey("setDeviceVerification20"));

        ///
        final CountDownLatch lock2 = new CountDownLatch(1);

        final ArrayList<Event> receivedEvents2 = new ArrayList<>();
        EventTimeline.EventTimelineListener eventTimelineListener2 = new EventTimeline.EventTimelineListener() {
            public void onEvent(Event event, EventTimeline.Direction direction, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE_ENCRYPTED)) {
                    receivedEvents2.add(event);
                    lock2.countDown();
                }
            }
        };

        roomFromBobPOV.getLiveTimeLine().addEventTimelineListener(eventTimelineListener2);

        String aliceMessage2 = "Hello I'm still Alice!";

        roomFromAlicePOV.sendEvent(buildTextEvent(aliceMessage2, mAliceSession), new ApiCallback<Void>() {
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

        lock2.await(1000, TimeUnit.MILLISECONDS);
        assertTrue(1 == receivedEvents2.size());

        event = receivedEvents2.get(0);
        assertTrue(null == event.getClearEvent());
        assertTrue(null != event.getCryptoError());
        assertTrue(TextUtils.equals(event.getCryptoError().errcode, MXCryptoError.UNKNOWN_INBOUND_SESSION_ID_ERROR_CODE));

        // unblock the bob's device
        final CountDownLatch lock2b = new CountDownLatch(1);
        mAliceSession.getCrypto().setDeviceVerification(MXDeviceInfo.DEVICE_VERIFICATION_UNVERIFIED, mBobSession.getCredentials().deviceId, mBobSession.getMyUserId(), new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                results.put("setDeviceVerification40", "setDeviceVerification40");
                lock2b.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {
                lock2b.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                lock2b.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                lock2b.countDown();
            }
        });
        lock2b.await(1000, TimeUnit.MILLISECONDS);
        assertTrue(results.containsKey("setDeviceVerification40"));
        ///
        final CountDownLatch lock3 = new CountDownLatch(1);

        final ArrayList<Event> receivedEvents3 = new ArrayList<>();
        EventTimeline.EventTimelineListener eventTimelineListener3 = new EventTimeline.EventTimelineListener() {
            public void onEvent(Event event, EventTimeline.Direction direction, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE)) {
                    receivedEvents3.add(event);
                    lock3.countDown();
                }
            }
        };

        roomFromBobPOV.getLiveTimeLine().addEventTimelineListener(eventTimelineListener3);

        String aliceMessage3 = "Hello I'm still Alice and you can read this!";

        roomFromAlicePOV.sendEvent(buildTextEvent(aliceMessage3, mAliceSession), new ApiCallback<Void>() {
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

        lock3.await(1000, TimeUnit.MILLISECONDS);
        assertTrue(1 == receivedEvents3.size());

        event = receivedEvents3.get(0);
        assertTrue(checkEncryptedEvent(event, mRoomId, aliceMessage3, mAliceSession));
    }


    @Test
    public void test21_testDownloadKeysWithUnreachableHS() throws Exception {
        Log.e(LOG_TAG, "test21_testDownloadKeysWithUnreachableHS");

        final HashMap<String, Object> results = new HashMap<>();
        doE2ETestWithAliceAndBobInARoom(true);

        mBobSession.getCrypto().setWarnOnUnknownDevices(false);
        mAliceSession.getCrypto().setWarnOnUnknownDevices(false);

        final Room roomFromBobPOV = mBobSession.getDataHandler().getRoom(mRoomId);
        final Room roomFromAlicePOV = mAliceSession.getDataHandler().getRoom(mRoomId);

        assertTrue(roomFromBobPOV.isEncrypted());
        assertTrue(roomFromAlicePOV.isEncrypted());

        final CountDownLatch lock1 = new CountDownLatch(1);
        mAliceSession.getCrypto().getDeviceList().downloadKeys(Arrays.asList(mBobSession.getMyUserId(), "@pppppppppppp:matrix.org"), false, new ApiCallback<MXUsersDevicesMap<MXDeviceInfo>>() {
            @Override
            public void onSuccess(MXUsersDevicesMap<MXDeviceInfo> info) {
                results.put("downloadKeys", info);
                lock1.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {
                results.put("downloadKeysError", e);
                lock1.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                results.put("downloadKeysError", e);
                lock1.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                results.put("downloadKeysError", e);
                lock1.countDown();
            }
        });

        lock1.await(40000, TimeUnit.MILLISECONDS);
        assertTrue(results + "", results.containsKey("downloadKeys"));

        MXUsersDevicesMap<MXDeviceInfo> usersDevicesInfoMap = (MXUsersDevicesMap<MXDeviceInfo>)results.get("downloadKeys");

        // We can get info only get for Bob
        assertTrue(usersDevicesInfoMap.getMap().size() == 1);

        List<String> bobDevices = usersDevicesInfoMap.getUserDeviceIds(mBobSession.getMyUserId());

        assertTrue(null != bobDevices);
    }

    @Test
    public void test22_testDownloadKeysForUserWithNoDevice() throws Exception {
        Log.e(LOG_TAG, "test22_testDownloadKeysForUserWithNoDevice");

        final HashMap<String, Object> results = new HashMap<>();
        doE2ETestWithAliceAndBobInARoom(false);

        mAliceSession.getCrypto().setWarnOnUnknownDevices(false);

        final CountDownLatch lock1 = new CountDownLatch(1);
        mAliceSession.getCrypto().getDeviceList().downloadKeys(Arrays.asList(mBobSession.getMyUserId()), false, new ApiCallback<MXUsersDevicesMap<MXDeviceInfo>>() {
            @Override
            public void onSuccess(MXUsersDevicesMap<MXDeviceInfo> info) {
                results.put("downloadKeys", info);
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

        lock1.await(1000, TimeUnit.MILLISECONDS);
        assertTrue(results.containsKey("downloadKeys"));

        MXUsersDevicesMap<MXDeviceInfo> usersDevicesInfoMap = (MXUsersDevicesMap<MXDeviceInfo>)results.get("downloadKeys");

        // MXCrypto.downloadKeys should return @[] for Bob to distinguish him from an unknown user
        List<String> bobDevices = usersDevicesInfoMap.getUserDeviceIds(mBobSession.getMyUserId());
        assertTrue(null != bobDevices);
        assertTrue(0 == bobDevices.size());

        // try again
        // it should not failed
        final CountDownLatch lock2 = new CountDownLatch(1);
        mAliceSession.getCrypto().getDeviceList().downloadKeys(Arrays.asList(mBobSession.getMyUserId()), false, new ApiCallback<MXUsersDevicesMap<MXDeviceInfo>>() {
            @Override
            public void onSuccess(MXUsersDevicesMap<MXDeviceInfo> info) {
                results.put("downloadKeys2", info);
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

        lock2.await(1000, TimeUnit.MILLISECONDS);
        assertTrue(results.containsKey("downloadKeys2"));
    }

    @Test
    public void test23_testFirstMessageSentWhileSessionWasPaused() throws Exception {
        Log.e(LOG_TAG, "test23_testFirstMessageSentWhileSessionWasPaused");
        Context context = InstrumentationRegistry.getContext();
        final String messageFromAlice = "Hello I'm Alice!";

        final HashMap<String, Object> results = new HashMap<>();
        doE2ETestWithAliceAndBobInARoom(true);

        mBobSession.getCrypto().setWarnOnUnknownDevices(false);
        mAliceSession.getCrypto().setWarnOnUnknownDevices(false);

        final Room roomFromBobPOV = mBobSession.getDataHandler().getRoom(mRoomId);
        final Room roomFromAlicePOV = mAliceSession.getDataHandler().getRoom(mRoomId);

        assertTrue(roomFromBobPOV.isEncrypted());
        assertTrue(roomFromAlicePOV.isEncrypted());

        mBobSession.pauseEventStream();

        // wait that the bob session is really suspended
        SystemClock.sleep(30000);

        final CountDownLatch lock0 = new CountDownLatch(1);

        roomFromAlicePOV.sendEvent(buildTextEvent(messageFromAlice, mAliceSession), new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                results.put("sendEvent", "sendEvent");
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

        lock0.await(2000, TimeUnit.MILLISECONDS);
        assertTrue(results.containsKey("sendEvent"));

        final CountDownLatch lock2 = new CountDownLatch(2);
        MXEventListener eventListener = new MXEventListener() {
            @Override
            public void onLiveEvent(Event event, RoomState roomState) {
                try {
                    if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE)) {
                        if (checkEncryptedEvent(event, mRoomId, messageFromAlice, mAliceSession)) {
                            results.put("onLiveEvent", "onLiveEvent");
                            lock2.countDown();
                        }
                    }
                } catch (Exception e) {
                }
            }
        };

        roomFromBobPOV.addEventListener(eventListener);

        mBobSession.getDataHandler().addListener(new MXEventListener() {
            @Override
            public void onToDeviceEvent(Event event) {
                results.put("onToDeviceEvent", event);
                lock2.countDown();
            }
        });

        mBobSession.resumeEventStream();

        lock2.await(10000, TimeUnit.MILLISECONDS);
        assertTrue(results.containsKey("onToDeviceEvent"));
        assertTrue(results.containsKey("onLiveEvent"));

        mBobSession.clear(context);
        mAliceSession.clear(context);
    }

    @Test
    public void test24_testExportImport() throws Exception {
        Log.e(LOG_TAG, "test24_testExportImport");
        Context context = InstrumentationRegistry.getContext();
        final HashMap<String, Object> results = new HashMap<>();

        doE2ETestWithAliceInARoom();

        mAliceSession.getCrypto().setWarnOnUnknownDevices(false);

        String message = "Hello myself!";
        String password = "hello";

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
        lock1.await(1000, TimeUnit.MILLISECONDS);
        assertTrue(results.containsKey("sendEvent"));

        Credentials aliceCredentials = mAliceSession.getCredentials();
        Credentials aliceCredentials2 = new Credentials();

        final CountDownLatch lock1a = new CountDownLatch(1);
        mAliceSession.getCrypto().exportRoomKeys(password, new ApiCallback<byte[]>() {
            @Override
            public void onSuccess(byte[] info) {
                results.put("exportRoomKeys", info);
                lock1a.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {
                lock1a.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                lock1a.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                lock1a.countDown();
            }
        });

        lock1a.await(10000, TimeUnit.MILLISECONDS);
        assertTrue(results.containsKey("exportRoomKeys"));

        // close the session and clear the data
        mAliceSession.clear(context);

        aliceCredentials2.userId = aliceCredentials.userId;
        aliceCredentials2.homeServer = aliceCredentials.homeServer;
        aliceCredentials2.accessToken = aliceCredentials.accessToken;
        aliceCredentials2.refreshToken = aliceCredentials.refreshToken;
        aliceCredentials2.deviceId = "AliceNewDevice";

        Uri uri = Uri.parse(CryptoTestHelper.TESTS_HOME_SERVER_URL);
        HomeServerConnectionConfig hs = new HomeServerConnectionConfig(uri);
        hs.setCredentials(aliceCredentials2);

        IMXStore store =  new MXFileStore(hs, context);

        MXSession aliceSession2 = new MXSession(hs, new MXDataHandler(store, aliceCredentials2), context);

        aliceSession2.enableCryptoWhenStarting();

        final CountDownLatch lock1b = new CountDownLatch(1);
        MXStoreListener listener = new MXStoreListener() {
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
        lock1b.await(1000, TimeUnit.MILLISECONDS);
        assertTrue(results.containsKey("onStoreReady"));

        final CountDownLatch lock2 = new CountDownLatch(2);
        MXEventListener eventListener = new MXEventListener() {
            @Override
            public void onInitialSyncComplete(String toToken) {
                results.put("onInitialSyncComplete", "onInitialSyncComplete");
                lock2.countDown();
            }

            @Override
            public void onCryptoSyncComplete() {
                results.put("onCryptoSyncComplete", "onCryptoSyncComplete");
                lock2.countDown();
            }
        };

        aliceSession2.getDataHandler().addListener(eventListener);
        aliceSession2.startEventStream(null);

        lock2.await(1000, TimeUnit.MILLISECONDS);

        assertTrue (results.containsKey("onInitialSyncComplete"));
        assertTrue (results.containsKey("onCryptoSyncComplete"));

        Room roomFromAlicePOV2 = aliceSession2.getDataHandler().getRoom(mRoomId);

        assertTrue (null != roomFromAlicePOV2);
        assertTrue(roomFromAlicePOV2.getLiveState().isEncrypted());

        Event event = roomFromAlicePOV2.getDataHandler().getStore().getLatestEvent(mRoomId);
        assertTrue(null != event);
        assertTrue(event.isEncrypted());
        assertTrue(null == event.getClearEvent());
        assertTrue(null != event.getCryptoError());
        assertTrue(TextUtils.equals(event.getCryptoError().errcode, MXCryptoError.UNKNOWN_INBOUND_SESSION_ID_ERROR_CODE));

        // import the e2e keys
        // test with a wrong password
        final CountDownLatch lock3 = new CountDownLatch(1);
        aliceSession2.getCrypto().importRoomKeys((byte[]) results.get("exportRoomKeys"), "wrong password", new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                results.put("importRoomKeys", "importRoomKeys");
                lock3.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {
                results.put("importRoomKeys_failed", "importRoomKeys_failed");
                lock3.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                results.put("importRoomKeys_failed", "importRoomKeys_failed");
                lock3.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                results.put("importRoomKeys_failed", "importRoomKeys_failed");
                lock3.countDown();
            }
        });
        lock3.await(10000, TimeUnit.MILLISECONDS);
        assertTrue(!results.containsKey("importRoomKeys"));
        assertTrue(results.containsKey("importRoomKeys_failed"));

        // check that the message cannot be decrypted
        event = roomFromAlicePOV2.getDataHandler().getStore().getLatestEvent(mRoomId);
        assertTrue(null != event);
        assertTrue(event.isEncrypted());
        assertTrue(null == event.getClearEvent());
        assertTrue(null != event.getCryptoError());
        assertTrue(TextUtils.equals(event.getCryptoError().errcode, MXCryptoError.UNKNOWN_INBOUND_SESSION_ID_ERROR_CODE));

        final CountDownLatch lock4 = new CountDownLatch(1);
        aliceSession2.getCrypto().importRoomKeys((byte[]) results.get("exportRoomKeys"), password, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                results.put("importRoomKeys", "importRoomKeys");
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
        lock4.await(10000, TimeUnit.MILLISECONDS);
        assertTrue(results.containsKey("importRoomKeys"));

        // check that the message CAN be decrypted
        event = roomFromAlicePOV2.getDataHandler().getStore().getLatestEvent(mRoomId);
        assertTrue(null != event);
        assertTrue(event.isEncrypted());
        assertTrue(null != event.getClearEvent());
        assertTrue(null == event.getCryptoError());
        assertTrue(checkEncryptedEvent(event, mRoomId, message, mAliceSession));

        aliceSession2.clear(context);
    }

    @Test
    // issue https://github.com/vector-im/riot-web/issues/2305
    public void test25_testLeftAndJoinedBob() throws Exception {
        Log.e(LOG_TAG, "test25_testLeftAndJoinedBob");
        Context context = InstrumentationRegistry.getContext();

        final String messageFromAlice = "Hello I'm Alice!";
        final String message2FromAlice = "I'm still Alice!";

        final HashMap<String, Object> results = new HashMap<>();

        createAliceAccount();
        createBobAccount();

        final CountDownLatch lock_1 = new CountDownLatch(2);
        mAliceSession.enableCrypto(true, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                lock_1.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {
                lock_1.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                lock_1.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                lock_1.countDown();
            }
        });

        mBobSession.enableCrypto(true, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                lock_1.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {
                lock_1.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                lock_1.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                lock_1.countDown();
            }
        });

        lock_1.await(2000, TimeUnit.MILLISECONDS);
        assertTrue(null !=  mAliceSession.getCrypto());
        assertTrue(null !=  mBobSession.getCrypto());

        mAliceSession.getCrypto().setWarnOnUnknownDevices(false);
        mBobSession.getCrypto().setWarnOnUnknownDevices(false);

        final CountDownLatch lock0 = new CountDownLatch(1);
        mAliceSession.createRoom(null, null, RoomState.DIRECTORY_VISIBILITY_PUBLIC, null, RoomState.GUEST_ACCESS_CAN_JOIN, RoomState.HISTORY_VISIBILITY_SHARED, null, new ApiCallback<String>() {
            @Override
            public void onSuccess(String roomId) {
                results.put("roomId", roomId);
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
        lock0.await(2000, TimeUnit.MILLISECONDS);
        assertTrue(results.containsKey("roomId"));
        mRoomId = (String) results.get("roomId");

        Room roomFromAlicePOV = mAliceSession.getDataHandler().getRoom(mRoomId);

        final CountDownLatch lock1 = new CountDownLatch(1);
        roomFromAlicePOV.enableEncryptionWithAlgorithm(MXCryptoAlgorithms.MXCRYPTO_ALGORITHM_MEGOLM, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                results.put("enableEncryptionWithAlgorithm", "enableEncryptionWithAlgorithm");
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
        lock1.await(2000, TimeUnit.MILLISECONDS);
        assertTrue(results.containsKey("enableEncryptionWithAlgorithm"));

        final CountDownLatch lock2 = new CountDownLatch(1);
        mBobSession.joinRoom(mRoomId, new ApiCallback<String>() {
            @Override
            public void onSuccess(String info) {
                results.put("joinRoom", "joinRoom");
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
        lock2.await(2000, TimeUnit.MILLISECONDS);
        assertTrue(results.containsKey("joinRoom"));

        Room roomFromBobPOV = mBobSession.getDataHandler().getRoom(mRoomId);
        final CountDownLatch lock3 = new CountDownLatch(1);
        final ArrayList<Event> receivedEvents = new ArrayList<>();
        EventTimeline.EventTimelineListener eventTimelineListener = new EventTimeline.EventTimelineListener() {
            public void onEvent(Event event, EventTimeline.Direction direction, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE)) {
                    receivedEvents.add(event);
                    lock3.countDown();
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

        lock3.await(2000, TimeUnit.MILLISECONDS);
        assertTrue(1 == receivedEvents.size());

        Event event = receivedEvents.get(0);
        assertTrue(checkEncryptedEvent(event, mRoomId, messageFromAlice, mAliceSession));

        final CountDownLatch lock4 = new CountDownLatch(1);
        roomFromBobPOV.leave(new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                results.put("leave", "leave");
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
        lock4.await(2000, TimeUnit.MILLISECONDS);
        assertTrue(results.containsKey("leave"));

        // Make Bob come back to the room with a new device
        Credentials bobCredentials = mBobSession.getCredentials();
        mBobSession.clear(context);

        MXSession bobSession2 = CryptoTestHelper.logAccountAndSync(context, bobCredentials.userId, MXTESTS_BOB_PWD);
        assertTrue(null != bobSession2);
        assertTrue(bobSession2.isCryptoEnabled());
        assertTrue(!TextUtils.equals(bobSession2.getCrypto().getMyDevice().deviceId, bobCredentials.deviceId));
        bobSession2.getCrypto().setWarnOnUnknownDevices(false);

        final CountDownLatch lock5 = new CountDownLatch(1);
        bobSession2.joinRoom(mRoomId, new ApiCallback<String>() {
            @Override
            public void onSuccess(String info) {
                results.put("joinRoom2", "joinRoom2");
                lock5.countDown();
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
        lock5.await(5000, TimeUnit.MILLISECONDS);
        assertTrue(results.containsKey("joinRoom2"));

        Room roomFromBobPOV2 = bobSession2.getDataHandler().getRoom(mRoomId);

        final CountDownLatch lock6 = new CountDownLatch(1);
        final ArrayList<Event> receivedEvents2 = new ArrayList<>();
        EventTimeline.EventTimelineListener eventTimelineListener2 = new EventTimeline.EventTimelineListener() {
            public void onEvent(Event event, EventTimeline.Direction direction, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE)) {
                    receivedEvents2.add(event);
                    lock6.countDown();
                }
            }
        };

        roomFromBobPOV2.getLiveTimeLine().addEventTimelineListener(eventTimelineListener2);
        roomFromAlicePOV.sendEvent(buildTextEvent(message2FromAlice, mAliceSession), new ApiCallback<Void>() {
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

        lock6.await(5000, TimeUnit.MILLISECONDS);
        assertTrue(1 == receivedEvents2.size());

        event = receivedEvents2.get(0);
        assertTrue(checkEncryptedEvent(event, mRoomId, message2FromAlice, mAliceSession));

        bobSession2.clear(context);
        mAliceSession.clear(context);
    }

    @Test
    // Bob, Alice and Sam are in an enctypted room
    // Alice sends a message
    // The message sending fails because of unknown devices (Bob and Sam ones)
    // Alice marks the Bob and Sam devices as known (UNVERIFIED)
    // Alice sends another message
    // Checks that the Bob and Sam devices receive the message and can decrypt it.
    // Alice black lists the unverified devices
    // Alice sends a message
    // checks that the Sam and the Bob devices receive the message but it cannot be decrypted
    // Alice unblack-lists the unverified devices
    // Alice sends a message
    // checks that the Sam and the Bob devices receive the message and it can be decrypted on the both devices
    // Alice verifies the Bob device and black lists the unverified devices in the current room.
    // Alice sends a message
    // Check that the message can be decrypted by Bob's device but not by Sam's device
    // Alice unblack-lists the unverified devices in the current room
    // Alice sends a message
    // Check that the message can be decrypted by the Bob's device and the Sam's device
    public void test26_testBlackListUnverifiedDevices() throws Exception {
        Log.e(LOG_TAG, "test26_testBlackListUnverifiedDevices");

        Context context = InstrumentationRegistry.getContext();
        final HashMap<String, Object> results = new HashMap<>();

        doE2ETestWithAliceAndBobAndSamInARoom();

        final String messageFromAlice = "Hello I'm Alice!";

        Room roomFromBobPOV = mBobSession.getDataHandler().getRoom(mRoomId);
        Room roomFromAlicePOV =  mAliceSession.getDataHandler().getRoom(mRoomId);
        Room roomFromSamPOV = mSamSession.getDataHandler().getRoom(mRoomId);

        assertTrue(roomFromBobPOV.isEncrypted());
        assertTrue(roomFromAlicePOV.isEncrypted());
        assertTrue(roomFromSamPOV.isEncrypted());

        final CountDownLatch lock1 = new CountDownLatch(1);

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
                results.put("sendEventError", e);
                lock1.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                lock1.countDown();
            }
        });

        lock1.await(3000, TimeUnit.MILLISECONDS);
        assertTrue(results.containsKey("sendEventError"));
        MXCryptoError error = (MXCryptoError)results.get("sendEventError");
        assertTrue(TextUtils.equals(error.errcode, MXCryptoError.UNKNOWN_DEVICES_CODE));
        MXUsersDevicesMap<MXDeviceInfo> unknownDevices = (MXUsersDevicesMap<MXDeviceInfo> )error.mExceptionData;

        // only one bob device
        List<String> deviceInfos = unknownDevices.getUserDeviceIds(mBobSession.getMyUserId());
        assertTrue(1 == deviceInfos.size());
        assertTrue(deviceInfos.contains(mBobSession.getCrypto().getMyDevice().deviceId));

        // only one Sam device
        deviceInfos = unknownDevices.getUserDeviceIds(mSamSession.getMyUserId());
        assertTrue(1 == deviceInfos.size());
        assertTrue(deviceInfos.contains(mSamSession.getCrypto().getMyDevice().deviceId));

        final CountDownLatch lock2 = new CountDownLatch(1);
        mAliceSession.getCrypto().setDevicesKnown(Arrays.asList(mBobSession.getCrypto().getMyDevice(), mSamSession.getCrypto().getMyDevice()), new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                results.put("setDevicesKnown", "setDevicesKnown");
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

        lock2.await(3000, TimeUnit.MILLISECONDS);
        assertTrue(results.containsKey("setDevicesKnown"));

        final CountDownLatch lock3 = new CountDownLatch(5);

        MXEventListener eventListenerBob1 = new MXEventListener() {
            @Override
            public void onLiveEvent(Event event, RoomState roomState) {
                try {
                    if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE)) {
                        if (checkEncryptedEvent(event, mRoomId, messageFromAlice, mAliceSession)) {
                            results.put("onLiveEventBob1", "onLiveEvent");
                            lock3.countDown();
                        }
                    }
                } catch (Exception e) {
                }
            }
        };

        MXEventListener eventListenerSam1 = new MXEventListener() {
            @Override
            public void onLiveEvent(Event event, RoomState roomState) {
                try {
                    if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE)) {
                        if (checkEncryptedEvent(event, mRoomId, messageFromAlice, mAliceSession)) {
                            results.put("onLiveEventSam1", "onLiveEvent");
                            lock3.countDown();
                        }
                    }
                } catch (Exception e) {
                }
            }
        };

        mBobSession.getDataHandler().addListener(new MXEventListener() {
            @Override
            public void onToDeviceEvent(Event event) {
                results.put("onToDeviceEventBob", event);
                lock3.countDown();
            }
        });

        mSamSession.getDataHandler().addListener(new MXEventListener() {
            @Override
            public void onToDeviceEvent(Event event) {
                results.put("onToDeviceEventSam", event);
                lock3.countDown();
            }
        });

        roomFromBobPOV.addEventListener(eventListenerBob1);
        roomFromSamPOV.addEventListener(eventListenerSam1);

        roomFromAlicePOV.sendEvent(buildTextEvent(messageFromAlice, mAliceSession), new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
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

        lock3.await(3000, TimeUnit.MILLISECONDS);
        assertTrue(results.containsKey("onToDeviceEventBob"));
        assertTrue(results.containsKey("onToDeviceEventSam"));
        assertTrue(results.containsKey("onLiveEventBob1"));
        assertTrue(results.containsKey("onLiveEventSam1"));

        roomFromBobPOV.removeEventListener(eventListenerBob1);
        roomFromSamPOV.removeEventListener(eventListenerSam1);

        // play with the device black listing
        final List<CountDownLatch> activeLock = new ArrayList<>();
        final List<String> activeMessage = new ArrayList<>();

        MXEventListener eventListenerBob2 = new MXEventListener() {
            @Override
            public void onLiveEvent(Event event, RoomState roomState) {
                try {
                    if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE)) {
                        if (checkEncryptedEvent(event, mRoomId, activeMessage.get(0), mAliceSession)) {
                            results.put("eventListenerBob2", "onLiveEvent");
                            activeLock.get(0).countDown();
                        }
                    } else if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE_ENCRYPTED)) {
                        results.put("eventListenerEncyptedBob2", "onLiveEvent");
                        activeLock.get(0).countDown();
                    }
                } catch (Exception e) {
                }
            }
        };

        MXEventListener eventListenerSam2 = new MXEventListener() {
            @Override
            public void onLiveEvent(Event event, RoomState roomState) {
                try {
                    if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE)) {
                        if (checkEncryptedEvent(event, mRoomId, activeMessage.get(0), mAliceSession)) {
                            results.put("eventListenerSam2", "onLiveEvent");
                            activeLock.get(0).countDown();
                        }
                    } else if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE_ENCRYPTED)) {
                        results.put("eventListenerEncyptedSam2", "onLiveEvent");
                        activeLock.get(0).countDown();
                    }
                } catch (Exception e) {
                }
            }
        };

        roomFromBobPOV.addEventListener(eventListenerBob2);
        roomFromSamPOV.addEventListener(eventListenerSam2);

        final CountDownLatch lock4 = new CountDownLatch(1);
        mAliceSession.getCrypto().setGlobalBlacklistUnverifiedDevices(true, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                results.put("setGlobalBlacklistUnverifiedDevicesTrue", "setGlobalBlacklistUnverifiedDevices");
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
        lock4.await(3000, TimeUnit.MILLISECONDS);
        assertTrue(results.containsKey("setGlobalBlacklistUnverifiedDevicesTrue"));

        // ensure that there is no received message
        results.clear();
        final CountDownLatch lock5 = new CountDownLatch(3);
        activeLock.clear();
        activeLock.add(lock5);

        activeMessage.clear();
        activeMessage.add("message 1");

        roomFromAlicePOV.sendEvent(buildTextEvent(activeMessage.get(0), mAliceSession), new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                lock5.countDown();
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

        lock5.await(3000, TimeUnit.MILLISECONDS);
        assertTrue(!results.containsKey("eventListenerBob2"));
        assertTrue(!results.containsKey("eventListenerSam2"));
        assertTrue(results.containsKey("eventListenerEncyptedBob2"));
        assertTrue(results.containsKey("eventListenerEncyptedSam2"));

        final CountDownLatch lock6 = new CountDownLatch(1);
        mAliceSession.getCrypto().setGlobalBlacklistUnverifiedDevices(false, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                results.put("setGlobalBlacklistUnverifiedDevicesfalse", "setGlobalBlacklistUnverifiedDevices");
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
        lock6.await(3000, TimeUnit.MILLISECONDS);
        assertTrue(results.containsKey("setGlobalBlacklistUnverifiedDevicesfalse"));

        // ensure that the messages are received
        results.clear();
        final CountDownLatch lock7 = new CountDownLatch(3);
        activeLock.clear();
        activeLock.add(lock7);

        activeMessage.clear();
        activeMessage.add("message 2");

        roomFromAlicePOV.sendEvent(buildTextEvent(activeMessage.get(0), mAliceSession), new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                lock7.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {
                lock7.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                lock7.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                lock7.countDown();
            }
        });

        lock7.await(3000, TimeUnit.MILLISECONDS);
        assertTrue(results.containsKey("eventListenerBob2"));
        assertTrue(results.containsKey("eventListenerSam2"));
        assertTrue(!results.containsKey("eventListenerEncyptedBob2"));
        assertTrue(!results.containsKey("eventListenerEncyptedSam2"));

        // verify the bob device
        final CountDownLatch lock8 = new CountDownLatch(3);
        mAliceSession.getCrypto().setDeviceVerification(MXDeviceInfo.DEVICE_VERIFICATION_VERIFIED,
                mBobSession.getCrypto().getMyDevice().deviceId,
                mBobSession.getMyUserId(), new ApiCallback<Void>() {
                    @Override
                    public void onSuccess(Void info) {
                        results.put("setDeviceVerificationBob", "setDeviceVerificationBob");
                        lock8.countDown();
                    }

                    @Override
                    public void onNetworkError(Exception e) {
                        lock8.countDown();
                    }

                    @Override
                    public void onMatrixError(MatrixError e) {
                        lock8.countDown();
                    }

                    @Override
                    public void onUnexpectedError(Exception e) {
                        lock8.countDown();
                    }
                }
        );
        lock8.await(3000, TimeUnit.MILLISECONDS);
        assertTrue(results.containsKey("setDeviceVerificationBob"));

        final CountDownLatch lock9 = new CountDownLatch(3);
        mAliceSession.getCrypto().setRoomBlacklistUnverifiedDevices(roomFromAlicePOV.getRoomId(), new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                results.put("setRoomBlacklistUnverifiedDevices", "setRoomBlacklistUnverifiedDevices");
                lock9.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {
                lock9.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                lock9.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                lock9.countDown();
            }
        });
        lock9.await(3000, TimeUnit.MILLISECONDS);
        assertTrue(results.containsKey("setRoomBlacklistUnverifiedDevices"));

        // ensure that the messages are received
        results.clear();
        final CountDownLatch lock10 = new CountDownLatch(3);
        activeLock.clear();
        activeLock.add(lock10);

        activeMessage.clear();
        activeMessage.add("message 3");

        roomFromAlicePOV.sendEvent(buildTextEvent(activeMessage.get(0), mAliceSession), new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                lock10.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {
                lock10.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                lock10.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                lock10.countDown();
            }
        });

        lock10.await(3000, TimeUnit.MILLISECONDS);
        assertTrue(results.containsKey("eventListenerBob2"));
        assertTrue(!results.containsKey("eventListenerSam2"));
        assertTrue(!results.containsKey("eventListenerEncyptedBob2"));
        assertTrue(results.containsKey("eventListenerEncyptedSam2"));

        final CountDownLatch lock11 = new CountDownLatch(3);
        mAliceSession.getCrypto().setRoomUnblacklistUnverifiedDevices(roomFromAlicePOV.getRoomId(), new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                results.put("setRoomUnblacklistUnverifiedDevices", "setRoomUnblacklistUnverifiedDevices");
                lock11.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {
                lock11.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                lock11.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                lock11.countDown();
            }
        });
        lock11.await(3000, TimeUnit.MILLISECONDS);
        assertTrue(results.containsKey("setRoomUnblacklistUnverifiedDevices"));

        // ensure that the messages are received
        results.clear();
        final CountDownLatch lock12 = new CountDownLatch(3);
        activeLock.clear();
        activeLock.add(lock12);

        activeMessage.clear();
        activeMessage.add("message 3");

        roomFromAlicePOV.sendEvent(buildTextEvent(activeMessage.get(0), mAliceSession), new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                lock12.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {
                lock12.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                lock12.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                lock12.countDown();
            }
        });

        lock12.await(3000, TimeUnit.MILLISECONDS);
        assertTrue(results.containsKey("eventListenerBob2"));
        assertTrue(results.containsKey("eventListenerSam2"));
        assertTrue(!results.containsKey("eventListenerEncyptedBob2"));
        assertTrue(!results.containsKey("eventListenerEncyptedSam2"));

        mBobSession.clear(context);
    }

    @Test
    // Test for https://github.com/matrix-org/matrix-js-sdk/pull/359
    // - Alice sends a message to Bob to a non encrypted room
    // - Bob logs in with a new device
    // - Alice turns the crypto ON in the room
    // - Alice sends a message
    // -> Bob must be able to decrypt this message
    public void test27_testEnableEncryptionAfterNonCryptedMessages() throws Exception {
        Log.e(LOG_TAG, "test27_testEnableEncryptionAfterNonCryptedMessages");
        Context context = InstrumentationRegistry.getContext();
        final HashMap<String, Object> results = new HashMap<>();

        final String messageFromAlice = "Hello I'm Alice!";
        final String message2FromAlice = "I'm still Alice!";

        createAliceAccount();
        createBobAccount();

        final CountDownLatch lock00b = new CountDownLatch(2);
        mAliceSession.enableCrypto(true, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                results.put("enableCrypto1", "enableCrypto1");
                lock00b.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {
                lock00b.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                lock00b.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                lock00b.countDown();
            }
        });

        mBobSession.enableCrypto(true, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                results.put("enableCrypto2", "enableCrypto2");
                lock00b.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {
                lock00b.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                lock00b.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                lock00b.countDown();
            }
        }) ;
        lock00b.await(5000, TimeUnit.MILLISECONDS);
        assertTrue(results.containsKey("enableCrypto2"));
        assertTrue(results.containsKey("enableCrypto1"));

        mAliceSession.getCrypto().setWarnOnUnknownDevices(false);
        mBobSession.getCrypto().setWarnOnUnknownDevices(false);

        final CountDownLatch lock0 = new CountDownLatch(1);
        mAliceSession.createRoom(null, null, RoomState.DIRECTORY_VISIBILITY_PUBLIC, null, RoomState.GUEST_ACCESS_CAN_JOIN, RoomState.HISTORY_VISIBILITY_SHARED, null, new ApiCallback<String>() {
            @Override
            public void onSuccess(String roomId) {
                results.put("roomId", roomId);
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
        lock0.await(2000, TimeUnit.MILLISECONDS);
        assertTrue(results.containsKey("roomId"));
        mRoomId = (String) results.get("roomId");

        final CountDownLatch lock1 = new CountDownLatch(1);
        mBobSession.joinRoom(mRoomId, new ApiCallback<String>() {
            @Override
            public void onSuccess(String info) {
                results.put("joinRoom", "joinRoom");
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
        lock1.await(2000, TimeUnit.MILLISECONDS);
        assertTrue(results.containsKey("joinRoom"));

        Room roomFromAlicePOV = mAliceSession.getDataHandler().getRoom(mRoomId);

        final CountDownLatch lock2 = new CountDownLatch(1);
        roomFromAlicePOV.sendEvent(buildTextEvent(messageFromAlice, mAliceSession), new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                results.put("sendEvent1", "sendEvent1");
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
        lock2.await(2000, TimeUnit.MILLISECONDS);
        assertTrue(results.containsKey("sendEvent1"));

        // Make Bob come back to the room with a new device
        Credentials bobCredentials = mBobSession.getCredentials();
        mBobSession.clear(context);

        MXSession bobSession2 = CryptoTestHelper.logAccountAndSync(context, bobCredentials.userId, MXTESTS_BOB_PWD);
        assertTrue(null != bobSession2);
        assertTrue(bobSession2.isCryptoEnabled());
        assertTrue(!TextUtils.equals(bobSession2.getCrypto().getMyDevice().deviceId, bobCredentials.deviceId));
        bobSession2.getCrypto().setWarnOnUnknownDevices(false);

        final CountDownLatch lock3 = new CountDownLatch(1);
        roomFromAlicePOV.enableEncryptionWithAlgorithm(MXCryptoAlgorithms.MXCRYPTO_ALGORITHM_MEGOLM, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                results.put("enableEncryptionWithAlgorithm", "enableEncryptionWithAlgorithm");
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
        lock3.await(5000, TimeUnit.MILLISECONDS);
        assertTrue(results.containsKey("enableEncryptionWithAlgorithm"));

        Room roomFromBobPOV2 = bobSession2.getDataHandler().getRoom(mRoomId);

        final CountDownLatch lock4 = new CountDownLatch(1);
        final ArrayList<Event> receivedEvents2 = new ArrayList<>();
        EventTimeline.EventTimelineListener eventTimelineListener2 = new EventTimeline.EventTimelineListener() {
            public void onEvent(Event event, EventTimeline.Direction direction, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE)) {
                    receivedEvents2.add(event);
                    lock4.countDown();
                } else  if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE_ENCRYPTED)) {
                    lock4.countDown();
                }
            }
        };

        roomFromBobPOV2.getLiveTimeLine().addEventTimelineListener(eventTimelineListener2);
        roomFromAlicePOV.sendEvent(buildTextEvent(message2FromAlice, mAliceSession), new ApiCallback<Void>() {
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

        lock4.await(5000, TimeUnit.MILLISECONDS);
        assertTrue(1 == receivedEvents2.size());

        Event event = receivedEvents2.get(0);
        assertTrue(checkEncryptedEvent(event, mRoomId, message2FromAlice, mAliceSession));

        bobSession2.clear(context);
        mAliceSession.clear(context);
    }

    // Test for https://github.com/vector-im/riot-web/issues/4983
    // - Alice and Bob share an e2e room; Bob tracks Alice's devices
    // - Bob leaves the room, so stops getting updates
    // - Alice adds a new device
    // - Alice and Bob start sharing a room again
    // - Bob has an out of date list of Alice's devices
    @Test
    public void test28_testLeftBobAndAliceWithNewDevice() throws Exception {
        Log.e(LOG_TAG, "test28_testLeftBobAndAliceWithNewDevice");
        Context context = InstrumentationRegistry.getContext();
        final HashMap<String, Object> results = new HashMap();

        doE2ETestWithAliceAndBobInARoomWithCryptedMessages(true);

        // - Bob leaves the room, so stops getting updates
        final CountDownLatch lock1 = new CountDownLatch(1);

        final Room bobLeftRoom = mBobSession.getDataHandler().getRoom(mRoomId);
        bobLeftRoom.leave(new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                results.put("lock1", "lock1");
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

        lock1.await(1000, TimeUnit.MILLISECONDS);
        assertTrue (results.containsKey("lock1"));

        // - Alice adds a new device
        final MXSession aliceSession2 = CryptoTestHelper.logAccountAndSync(context, mAliceSession.getMyUserId(), MXTESTS_ALICE_PWD);
        assertTrue (null != aliceSession2);

        // - Alice and Bob start sharing a room again
        final CountDownLatch lock3 = new CountDownLatch(1);
        aliceSession2.createRoom(null, null, RoomState.DIRECTORY_VISIBILITY_PUBLIC, null, RoomState.GUEST_ACCESS_CAN_JOIN, RoomState.HISTORY_VISIBILITY_SHARED, null, new ApiCallback<String>() {
            @Override
            public void onSuccess(String info) {
                mRoomId = info;
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
        lock3.await(1000, TimeUnit.MILLISECONDS);
        assertTrue (null != mRoomId);

        Room roomFromAlicePOV = aliceSession2.getDataHandler().getRoom(mRoomId);
        final CountDownLatch lock4 = new CountDownLatch(1);
        roomFromAlicePOV.enableEncryptionWithAlgorithm(MXCryptoAlgorithms.MXCRYPTO_ALGORITHM_MEGOLM, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                results.put("lock4", "lock4");
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
        lock4.await(1000, TimeUnit.MILLISECONDS);
        assertTrue (results.containsKey("lock4"));

        final CountDownLatch lock5 = new CountDownLatch(1);
        mBobSession.joinRoom(mRoomId, new ApiCallback<String>() {
            @Override
            public void onSuccess(String info) {
                results.put("lock5", "lock5");
                lock5.countDown();
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
        lock5.await(1000, TimeUnit.MILLISECONDS);
        assertTrue (results.containsKey("lock5"));

        // - Bob has an out of date list of Alice's devices
        Room roomFromBobPOV = mBobSession.getDataHandler().getRoom(mRoomId);
        final String messageFromBob = "Hello Alice with new device!";
        final CountDownLatch lock6 = new CountDownLatch(2);
        MXEventListener eventListener = new MXEventListener() {
            @Override
            public void onLiveEvent(Event event, RoomState roomState) {
                try {
                    if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE)) {
                        if (checkEncryptedEvent(event, mRoomId, messageFromBob, mBobSession)) {
                            results.put("lock6", "lock6");
                            lock6.countDown();
                        }
                    }
                } catch (Exception e) {
                }
            }
        };

        roomFromAlicePOV.addEventListener(eventListener);

        roomFromBobPOV.sendEvent(buildTextEvent(messageFromBob, mBobSession), new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
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

        lock6.await(5000, TimeUnit.MILLISECONDS);
        assertTrue(results.containsKey("lock6"));

        mBobSession.clear(context);
        mAliceSession.clear(context);
        aliceSession2.clear(context);
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
        lock0.await(1000, TimeUnit.MILLISECONDS);
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

        lock1.await(1000, TimeUnit.MILLISECONDS);
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
        lock2.await(1000, TimeUnit.MILLISECONDS);
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
        lock0.await(1000, TimeUnit.MILLISECONDS);

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

        lock1.await(1000, TimeUnit.MILLISECONDS);

        assertTrue(statuses.containsKey("invite") && statuses.containsKey("onNewRoom"));

        mBobSession.getDataHandler().removeListener(bobEventListener);

        final CountDownLatch lock2 = new CountDownLatch(2);

        mBobSession.joinRoom(mRoomId, new ApiCallback<String>() {
            @Override
            public void onSuccess(String info) {
                statuses.put("joinRoom", "joinRoom");
                lock2.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {
                statuses.put("onNetworkError", e.getMessage());
                lock2.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                statuses.put("onMatrixError", e.getMessage());
                lock2.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                statuses.put("onUnexpectedError", e.getMessage());
                lock2.countDown();
            }
        });

        room.addEventListener(new MXEventListener() {
            @Override
            public void onLiveEvent(Event event, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_STATE_ROOM_MEMBER)) {
                    JsonObject contentToConsider = event.getContentAsJsonObject();
                    RoomMember member = JsonUtils.toRoomMember(contentToConsider);

                    if (TextUtils.equals(member.membership, RoomMember.MEMBERSHIP_JOIN)) {
                        statuses.put("AliceJoin", "AliceJoin");
                        lock2.countDown();
                    }
                }
            }
        });

        lock2.await(2000, TimeUnit.MILLISECONDS);
        assertTrue(statuses + "", statuses.containsKey("joinRoom"));
        assertTrue(statuses + "", statuses.containsKey("AliceJoin"));

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
        lock0.await(1000, TimeUnit.MILLISECONDS);

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

        lock1.await(1000, TimeUnit.MILLISECONDS);

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

        lock2.await(1000, TimeUnit.MILLISECONDS);
        assertTrue(statuses.containsKey("joinRoom"));

        // wait the initial sync
        SystemClock.sleep(1000);

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

        if (null != mBobSession.getCrypto()) {
            mBobSession.getCrypto().setWarnOnUnknownDevices(false);
        }

        if (null != mAliceSession.getCrypto()) {
            mAliceSession.getCrypto().setWarnOnUnknownDevices(false);
        }

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

        final HashMap<String, Object> results = new HashMap<>();

        CountDownLatch lock = new CountDownLatch(3);
        list.clear();
        list.add(lock);

        mBobSession.getDataHandler().addListener(new MXEventListener() {
            @Override
            public void onToDeviceEvent(Event event) {
                results.put("onToDeviceEvent", event);
                list.get(0).countDown();
            }
        });

        roomFromAlicePOV.sendEvent(buildTextEvent(messagesFromAlice.get(0), mAliceSession), callback);
        lock.await(1000, TimeUnit.MILLISECONDS);
        assertTrue(results.containsKey("onToDeviceEvent"));
        assertTrue(mMessagesCount == 1);

        lock = new CountDownLatch(1);
        list.clear();
        list.add(lock);
        roomFromBobPOV.sendEvent(buildTextEvent(messagesFromBob.get(0), mBobSession), callback);
        // android does not echo the messages sent from itself
        mMessagesCount++;
        lock.await(1000, TimeUnit.MILLISECONDS);
        assertTrue(mMessagesCount == 2);

        lock = new CountDownLatch(1);
        list.clear();
        list.add(lock);
        roomFromBobPOV.sendEvent(buildTextEvent(messagesFromBob.get(1), mBobSession), callback);
        // android does not echo the messages sent from itself
        mMessagesCount++;
        lock.await(1000, TimeUnit.MILLISECONDS);
        assertTrue(mMessagesCount == 3);

        lock = new CountDownLatch(1);
        list.clear();
        list.add(lock);
        roomFromBobPOV.sendEvent(buildTextEvent(messagesFromBob.get(2), mBobSession), callback);
        // android does not echo the messages sent from itself
        mMessagesCount++;
        lock.await(1000, TimeUnit.MILLISECONDS);
        assertTrue(mMessagesCount == 4);

        lock = new CountDownLatch(2);
        list.clear();
        list.add(lock);
        roomFromAlicePOV.sendEvent(buildTextEvent(messagesFromAlice.get(1), mAliceSession), callback);
        lock.await(1000, TimeUnit.MILLISECONDS);
        assertTrue(mMessagesCount == 5);
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
        assertTrue(event.getAge() < 10000);

        JsonObject eventContent = event.getContentAsJsonObject();
        assertTrue(TextUtils.equals(eventContent.get("body").getAsString(), clearMessage));
        assertTrue(TextUtils.equals(event.sender, senderSession.getMyUserId()));

        return true;
    }
}
