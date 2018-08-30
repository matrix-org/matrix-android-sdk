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


package org.matrix.androidsdk;

import android.content.Context;
import android.os.SystemClock;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.text.TextUtils;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import org.junit.Assert;
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
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.login.Credentials;
import org.matrix.androidsdk.rest.model.message.Message;
import org.matrix.androidsdk.rest.model.message.RelatesTo;
import org.matrix.androidsdk.util.JsonUtils;
import org.matrix.androidsdk.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class CryptoTest {

    private static final String LOG_TAG = "CryptoTest";

    private static final List<String> messagesFromAlice = Arrays.asList("0 - Hello I'm Alice!", "4 - Go!");
    private static final List<String> messagesFromBob = Arrays.asList("1 - Hello I'm Bob!", "2 - Isn't life grand?", "3 - Let's go to the opera.");

    private static final String MXTESTS_BOB = "mxBob";
    private static final String MXTESTS_BOB_PWD = "bobbob";

    private static final String MXTESTS_ALICE = "mxAlice";
    private static final String MXTESTS_ALICE_PWD = "alicealice";

    private static final String MXTESTS_SAM = "mxSam";
    private static final String MXTESTS_SAM_PWD = "samsam";

    @Test
    public void test01_testCryptoNoDeviceId() throws Exception {
        Log.e(LOG_TAG, "test01_testCryptoNoDeviceId");

        Context context = InstrumentationRegistry.getContext();
        final Map<String, Object> results = new HashMap<>();
        createBobAccount();

        Assert.assertNull(mBobSession.getCrypto());
        mBobSession.getCredentials().deviceId = null;

        CountDownLatch lock1 = new CountDownLatch(1);
        mBobSession.enableCrypto(true, new TestApiCallback<Void>(lock1) {
            @Override
            public void onSuccess(Void info) {
                results.put("enableCrypto", "enableCrypto");
                super.onSuccess(info);
            }
        });
        lock1.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("enableCrypto"));

        Assert.assertNotNull(mBobSession.getCrypto());
        Assert.assertNotNull(mBobSession.getCredentials().deviceId);

        mBobSession.clear(context);
    }

    @Test
    public void test02_testCryptoPersistenceInStore() throws Exception {
        Log.e(LOG_TAG, "test02_testCryptoPersistenceInStore");
        Context context = InstrumentationRegistry.getContext();
        final Map<String, Object> results = new HashMap<>();

        createBobAccount();
        mBobSession.getCredentials().deviceId = "BobDevice";

        Assert.assertNull(mBobSession.getCrypto());

        CountDownLatch lock0 = new CountDownLatch(1);
        mBobSession.enableCrypto(true, new TestApiCallback<Void>(lock0) {
            @Override
            public void onSuccess(Void info) {
                results.put("enableCrypto", "enableCrypto");
                super.onSuccess(info);
            }
        });
        lock0.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("enableCrypto"));

        Assert.assertNotNull(mBobSession.getCrypto());

        SystemClock.sleep(1000);

        final String deviceCurve25519Key = mBobSession.getCrypto().getOlmDevice().getDeviceCurve25519Key();
        final String deviceEd25519Key = mBobSession.getCrypto().getOlmDevice().getDeviceEd25519Key();

        final List<MXDeviceInfo> myUserDevices = mBobSession.getCrypto().getUserDevices(mBobSession.getMyUserId());

        Assert.assertNotNull(myUserDevices);
        Assert.assertEquals(1, myUserDevices.size());

        final Credentials bobCredentials = mBobSession.getCredentials();

        HomeServerConnectionConfig hs = CryptoTestHelper.createHomeServerConfig(bobCredentials);

        IMXStore store = new MXFileStore(hs, context);

        MXSession bobSession2 = new MXSession.Builder(hs, new MXDataHandler(store, bobCredentials), context)
                .build();

        final CountDownLatch lock1 = new CountDownLatch(1);
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
            public void onStoreOOM(String accountId, String description) {
                lock1.countDown();
            }
        };

        bobSession2.getDataHandler().getStore().addMXStoreListener(listener);

        bobSession2.getDataHandler().getStore().open();
        lock1.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);

        Assert.assertTrue(results.containsKey("onStoreReady"));
        Assert.assertTrue(bobSession2.isCryptoEnabled());

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
        lock2.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("onInitialSyncComplete"));
        Assert.assertTrue(results.containsKey("onCryptoSyncComplete"));

        MXCrypto crypto = bobSession2.getCrypto();
        Assert.assertNotNull(crypto);

        Assert.assertEquals(deviceCurve25519Key, crypto.getOlmDevice().getDeviceCurve25519Key());
        Assert.assertEquals(deviceEd25519Key, crypto.getOlmDevice().getDeviceEd25519Key());

        List<MXDeviceInfo> myUserDevices2 = bobSession2.getCrypto().getUserDevices(bobSession2.getMyUserId());
        Assert.assertEquals(1, myUserDevices2.size());
        Assert.assertEquals(myUserDevices2.get(0).deviceId, myUserDevices.get(0).deviceId);

        mBobSession.clear(context);
        bobSession2.clear(context);
    }

    @Test
    public void test03_testKeysUploadAndDownload() throws Exception {
        Log.e(LOG_TAG, "test03_testKeysUploadAndDownload");

        Context context = InstrumentationRegistry.getContext();
        final Map<String, Object> results = new HashMap<>();

        createAliceAccount();
        mAliceSession.getCredentials().deviceId = "AliceDevice";

        CountDownLatch lock0 = new CountDownLatch(1);
        mAliceSession.enableCrypto(true, new TestApiCallback<Void>(lock0) {
            @Override
            public void onSuccess(Void info) {
                results.put("enableCrypto", "enableCrypto");
                super.onSuccess(info);
            }
        });
        lock0.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("enableCrypto"));

        createBobAccount();
        CountDownLatch lock2 = new CountDownLatch(1);
        mBobSession.getCredentials().deviceId = "BobDevice";
        mBobSession.enableCrypto(true, new TestApiCallback<Void>(lock2) {
            @Override
            public void onSuccess(Void info) {
                results.put("enableCrypto2", "enableCrypto2");
                super.onSuccess(info);
            }
        });
        lock2.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("enableCrypto2"));

        CountDownLatch lock3 = new CountDownLatch(1);
        mBobSession.getCrypto()
                .getDeviceList()
                .downloadKeys(Arrays.asList(mBobSession.getMyUserId(), mAliceSession.getMyUserId()),
                        false, new TestApiCallback<MXUsersDevicesMap<MXDeviceInfo>>(lock3) {
                            @Override
                            public void onSuccess(MXUsersDevicesMap<MXDeviceInfo> info) {
                                results.put("downloadKeys", info);
                                super.onSuccess(info);
                            }
                        });

        lock3.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("downloadKeys"));
        MXUsersDevicesMap<MXDeviceInfo> usersDevicesInfoMap = (MXUsersDevicesMap<MXDeviceInfo>) results.get("downloadKeys");

        Assert.assertEquals(2, usersDevicesInfoMap.getUserIds().size());
        Assert.assertEquals(1, usersDevicesInfoMap.getUserDeviceIds(mAliceSession.getMyUserId()).size());

        MXDeviceInfo aliceDeviceFromBobPOV = usersDevicesInfoMap.getObject("AliceDevice", mAliceSession.getMyUserId());
        Assert.assertNotNull(aliceDeviceFromBobPOV);
        Assert.assertEquals(aliceDeviceFromBobPOV.fingerprint(), mAliceSession.getCrypto().getOlmDevice().getDeviceEd25519Key());

        // Continue testing other methods
        Assert.assertNotNull(mBobSession.getCrypto().deviceWithIdentityKey(mAliceSession.getCrypto().getOlmDevice().getDeviceCurve25519Key(),
                mAliceSession.getMyUserId(), MXCryptoAlgorithms.MXCRYPTO_ALGORITHM_OLM));
        Assert.assertTrue(aliceDeviceFromBobPOV.isUnknown());

        CountDownLatch lock3a = new CountDownLatch(1);
        mBobSession.getCrypto().setDevicesKnown(Arrays.asList(aliceDeviceFromBobPOV),
                new TestApiCallback<Void>(lock3a) {
                    @Override
                    public void onSuccess(Void info) {
                        results.put("setDevicesKnown", info);
                        super.onSuccess(info);
                    }
                }
        );
        lock3a.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("setDevicesKnown"));
        Assert.assertTrue(aliceDeviceFromBobPOV.isUnverified());

        CountDownLatch lock3b = new CountDownLatch(1);
        mBobSession.getCrypto().setDeviceVerification(
                MXDeviceInfo.DEVICE_VERIFICATION_BLOCKED,
                aliceDeviceFromBobPOV.deviceId,
                mAliceSession.getMyUserId(),
                new TestApiCallback<Void>(lock3b) {
                    @Override
                    public void onSuccess(Void info) {
                        results.put("setDeviceVerification1", info);
                        super.onSuccess(info);
                    }
                }
        );
        lock3b.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("setDeviceVerification1"));
        Assert.assertTrue(aliceDeviceFromBobPOV.isBlocked());

        Credentials bobCredentials = mBobSession.getCredentials();

        HomeServerConnectionConfig hs = CryptoTestHelper.createHomeServerConfig(bobCredentials);

        IMXStore store = new MXFileStore(hs, context);

        MXSession bobSession2 = new MXSession.Builder(hs, new MXDataHandler(store, bobCredentials), context)
                .build();

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
            public void onStoreOOM(String accountId, String description) {
                lock4.countDown();
            }
        };

        bobSession2.getDataHandler().getStore().addMXStoreListener(listener);
        bobSession2.getDataHandler().getStore().open();

        lock4.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("onStoreReady"));

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
        lock4b.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("onInitialSyncComplete"));
        Assert.assertTrue(results.containsKey("onCryptoSyncComplete"));

        MXDeviceInfo aliceDeviceFromBobPOV2 = bobSession2.getCrypto()
                .deviceWithIdentityKey(mAliceSession.getCrypto().getOlmDevice().getDeviceCurve25519Key(),
                        mAliceSession.getMyUserId(), MXCryptoAlgorithms.MXCRYPTO_ALGORITHM_OLM);

        Assert.assertNotNull(aliceDeviceFromBobPOV2);
        Assert.assertEquals(aliceDeviceFromBobPOV2.fingerprint(), mAliceSession.getCrypto().getOlmDevice().getDeviceEd25519Key());
        Assert.assertTrue(aliceDeviceFromBobPOV2.mVerified + " instead of " + MXDeviceInfo.DEVICE_VERIFICATION_BLOCKED,
                aliceDeviceFromBobPOV2.mVerified == MXDeviceInfo.DEVICE_VERIFICATION_BLOCKED);

        // Download again alice device
        CountDownLatch lock5 = new CountDownLatch(1);
        bobSession2.getCrypto().getDeviceList().downloadKeys(Arrays.asList(mAliceSession.getMyUserId()), true,
                new TestApiCallback<MXUsersDevicesMap<MXDeviceInfo>>(lock5) {
                    @Override
                    public void onSuccess(MXUsersDevicesMap<MXDeviceInfo> info) {
                        results.put("downloadKeys2", info);
                        super.onSuccess(info);
                    }
                });
        lock5.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("downloadKeys2"));

        MXDeviceInfo aliceDeviceFromBobPOV3 = bobSession2.getCrypto().deviceWithIdentityKey(mAliceSession.getCrypto().getOlmDevice().getDeviceCurve25519Key(),
                mAliceSession.getMyUserId(), MXCryptoAlgorithms.MXCRYPTO_ALGORITHM_OLM);

        Assert.assertNotNull(aliceDeviceFromBobPOV3);
        Assert.assertEquals(aliceDeviceFromBobPOV3.fingerprint(), mAliceSession.getCrypto().getOlmDevice().getDeviceEd25519Key());
        Assert.assertTrue(aliceDeviceFromBobPOV3.isBlocked());

        mAliceSession.clear(context);
        mBobSession.clear(context);
        bobSession2.clear(context);
    }

    @Test
    public void test04_testEnsureOlmSessionsForUsers() throws Exception {
        Log.e(LOG_TAG, "test04_testEnsureOlmSessionsForUsers");

        Context context = InstrumentationRegistry.getContext();

        createAliceAccount();
        final Map<String, Object> results = new HashMap<>();

        mAliceSession.getCredentials().deviceId = "AliceDevice";

        CountDownLatch lock0 = new CountDownLatch(1);

        mAliceSession.enableCrypto(true, new TestApiCallback<Void>(lock0) {
            @Override
            public void onSuccess(Void info) {
                results.put("enableCryptoAlice", "enableCryptoAlice");
                super.onSuccess(info);
            }
        });

        lock0.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("enableCryptoAlice"));

        createBobAccount();

        CountDownLatch lock2 = new CountDownLatch(1);
        mBobSession.enableCrypto(true, new TestApiCallback<Void>(lock2) {
            @Override
            public void onSuccess(Void info) {
                results.put("enableCryptoBob", "enableCryptoAlice");
                super.onSuccess(info);
            }
        });

        lock2.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("enableCryptoBob"));

        CountDownLatch lock3 = new CountDownLatch(1);
        mBobSession.getCrypto().getDeviceList().downloadKeys(Arrays.asList(mBobSession.getMyUserId(), mAliceSession.getMyUserId()), false,
                new TestApiCallback<MXUsersDevicesMap<MXDeviceInfo>>(lock3) {
                    @Override
                    public void onSuccess(MXUsersDevicesMap<MXDeviceInfo> map) {
                        results.put("downloadKeys", map);
                        super.onSuccess(map);
                    }
                });

        lock3.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("downloadKeys"));

        CountDownLatch lock4 = new CountDownLatch(1);
        mBobSession.getCrypto().ensureOlmSessionsForUsers(Arrays.asList(mBobSession.getMyUserId(), mAliceSession.getMyUserId()),
                new TestApiCallback<MXUsersDevicesMap<MXOlmSessionResult>>(lock4) {
                    @Override
                    public void onSuccess(MXUsersDevicesMap<MXOlmSessionResult> info) {
                        results.put("ensureOlmSessionsForUsers", info);
                        super.onSuccess(info);
                    }
                });

        lock4.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("ensureOlmSessionsForUsers"));

        MXUsersDevicesMap<MXOlmSessionResult> result = (MXUsersDevicesMap<MXOlmSessionResult>) results.get("ensureOlmSessionsForUsers");

        Assert.assertEquals(1, result.getUserIds().size());

        MXOlmSessionResult sessionWithAliceDevice = result.getObject("AliceDevice", mAliceSession.getMyUserId());

        Assert.assertNotNull(sessionWithAliceDevice);
        Assert.assertNotNull(sessionWithAliceDevice.mSessionId);
        Assert.assertEquals("AliceDevice", sessionWithAliceDevice.mDevice.deviceId);

        Credentials bobCredentials = mBobSession.getCredentials();

        HomeServerConnectionConfig hs = CryptoTestHelper.createHomeServerConfig(bobCredentials);

        IMXStore store = new MXFileStore(hs, context);

        MXSession bobSession2 = new MXSession.Builder(hs, new MXDataHandler(store, bobCredentials), context)
                .build();

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
            public void onStoreOOM(String accountId, String description) {
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
        lock5.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("onStoreReady"));

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

        lock5b.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("onInitialSyncComplete"));
        Assert.assertTrue(results.containsKey("onCryptoSyncComplete"));

        CountDownLatch lock6 = new CountDownLatch(1);
        bobSession2.getCrypto().ensureOlmSessionsForUsers(Arrays.asList(bobSession2.getMyUserId(), mAliceSession.getMyUserId()),
                new TestApiCallback<MXUsersDevicesMap<MXOlmSessionResult>>(lock6) {
                    @Override
                    public void onSuccess(MXUsersDevicesMap<MXOlmSessionResult> info) {
                        results.put("ensureOlmSessionsForUsers2", info);
                        super.onSuccess(info);
                    }
                });
        lock6.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("ensureOlmSessionsForUsers2"));

        MXUsersDevicesMap<MXOlmSessionResult> result2 = (MXUsersDevicesMap<MXOlmSessionResult>) results.get("ensureOlmSessionsForUsers2");

        MXOlmSessionResult sessionWithAliceDevice2 = result2.getObject("AliceDevice", mAliceSession.getMyUserId());
        Assert.assertNotNull(sessionWithAliceDevice2);
        Assert.assertNotNull(sessionWithAliceDevice2.mSessionId);
        Assert.assertEquals("AliceDevice", sessionWithAliceDevice2.mDevice.deviceId);

        mBobSession.clear(context);
        mAliceSession.clear(context);
        bobSession2.clear(context);
    }

    @Test
    public void test05_testRoomIsEncrypted() throws Exception {
        Log.e(LOG_TAG, "test05_testRoomIsEncrypted");

        Context context = InstrumentationRegistry.getContext();
        final Map<String, Object> results = new HashMap<>();

        createBobAccount();

        CountDownLatch lock0 = new CountDownLatch(1);
        mBobSession.enableCrypto(true, new TestApiCallback<Void>(lock0) {
            @Override
            public void onSuccess(Void info) {
                results.put("enableCrypto", "enableCrypto");
                super.onSuccess(info);
            }
        });
        lock0.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("enableCrypto"));
        mRoomId = null;

        CountDownLatch lock1 = new CountDownLatch(1);
        mBobSession.createRoom(new TestApiCallback<String>(lock1) {
            @Override
            public void onSuccess(String info) {
                mRoomId = info;
                super.onSuccess(info);
            }
        });

        lock1.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertNotNull(mRoomId);

        Room room = mBobSession.getDataHandler().getRoom(mRoomId);

        Assert.assertFalse(room.isEncrypted());

        CountDownLatch lock2 = new CountDownLatch(1);
        room.enableEncryptionWithAlgorithm(MXCryptoAlgorithms.MXCRYPTO_ALGORITHM_MEGOLM, new TestApiCallback<Void>(lock2) {
            @Override
            public void onSuccess(Void info) {
                results.put("enableEncryptionWithAlgorithm", "enableEncryptionWithAlgorithm");
                super.onSuccess(info);
            }
        });
        lock2.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("enableEncryptionWithAlgorithm"));

        Assert.assertTrue(room.isEncrypted());

        mBobSession.clear(context);
    }

    @Test
    public void test06_testAliceInAEncryptedRoom() throws Exception {
        Log.e(LOG_TAG, "test06_testAliceInAEncryptedRoom");

        Context context = InstrumentationRegistry.getContext();

        doE2ETestWithAliceInARoom();

        final String message = "Hello myself!";

        Room roomFromAlicePOV = mAliceSession.getDataHandler().getRoom(mRoomId);

        Assert.assertTrue(roomFromAlicePOV.isEncrypted());

        CountDownLatch lock1 = new CountDownLatch(1);

        // the IOS client echoes the message
        // the android client does not

        roomFromAlicePOV.sendEvent(buildTextEvent(message, mAliceSession), new TestApiCallback<Void>(lock1));

        lock1.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);

        mAliceSession.clear(context);
    }

    @Test
    public void test07_testAliceAndBobInAEncryptedRoom() throws Exception {
        Log.e(LOG_TAG, "test07_testAliceAndBobInAEncryptedRoom");

        Context context = InstrumentationRegistry.getContext();
        final Map<String, Object> results = new HashMap<>();

        doE2ETestWithAliceAndBobInARoom(true);

        final String messageFromAlice = "Hello I'm Alice!";

        Room roomFromBobPOV = mBobSession.getDataHandler().getRoom(mRoomId);
        Room roomFromAlicePOV = mAliceSession.getDataHandler().getRoom(mRoomId);

        Assert.assertTrue(roomFromBobPOV.isEncrypted());
        Assert.assertTrue(roomFromAlicePOV.isEncrypted());

        CountDownLatch lock1 = new CountDownLatch(1);

        roomFromAlicePOV.sendEvent(buildTextEvent(messageFromAlice, mAliceSession), new TestApiCallback<Void>(lock1) {
            @Override
            public void onMatrixError(MatrixError e) {
                results.put("sendEventError", e);
                super.onMatrixError(e);
            }
        });

        lock1.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("sendEventError"));
        MXCryptoError error = (MXCryptoError) results.get("sendEventError");
        Assert.assertEquals(MXCryptoError.UNKNOWN_DEVICES_CODE, error.errcode);
        MXUsersDevicesMap<MXDeviceInfo> unknownDevices = (MXUsersDevicesMap<MXDeviceInfo>) error.mExceptionData;
        List<String> deviceInfos = unknownDevices.getUserDeviceIds(mBobSession.getMyUserId());
        Assert.assertEquals(1, deviceInfos.size());
        Assert.assertEquals(deviceInfos.get(0), mBobSession.getCrypto().getMyDevice().deviceId);

        CountDownLatch lock2 = new CountDownLatch(1);
        mAliceSession.getCrypto().setDevicesKnown(Arrays.asList(mBobSession.getCrypto().getMyDevice()), new TestApiCallback<Void>(lock2) {
            @Override
            public void onSuccess(Void info) {
                results.put("setDevicesKnown", "setDevicesKnown");
                super.onSuccess(info);
            }
        });

        lock2.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("setDevicesKnown"));

        final CountDownLatch lock3 = new CountDownLatch(3);

        MXEventListener eventListener = new MXEventListener() {
            @Override
            public void onLiveEvent(Event event, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE)) {
                    checkEncryptedEvent(event, mRoomId, messageFromAlice, mAliceSession);

                    results.put("onLiveEvent", "onLiveEvent");
                    lock3.countDown();
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

        roomFromAlicePOV.sendEvent(buildTextEvent(messageFromAlice, mAliceSession), new TestApiCallback<Void>(lock3));

        lock3.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("onToDeviceEvent"));
        Assert.assertTrue(results.containsKey("onLiveEvent"));

        Assert.assertEquals(MXDeviceList.TRACKING_STATUS_UP_TO_DATE, mBobSession.getCrypto().getDeviceTrackingStatus(mBobSession.getMyUserId()));
        Assert.assertEquals(MXDeviceList.TRACKING_STATUS_UP_TO_DATE, mBobSession.getCrypto().getDeviceTrackingStatus(mAliceSession.getMyUserId()));

        Assert.assertEquals(MXDeviceList.TRACKING_STATUS_UP_TO_DATE, mAliceSession.getCrypto().getDeviceTrackingStatus(mBobSession.getMyUserId()));
        Assert.assertEquals(MXDeviceList.TRACKING_STATUS_UP_TO_DATE, mAliceSession.getCrypto().getDeviceTrackingStatus(mAliceSession.getMyUserId()));

        mBobSession.clear(context);
    }

    @Test
    public void test08_testAliceAndBobInAEncryptedRoom2() throws Exception {
        Log.e(LOG_TAG, "test08_testAliceAndBobInAEncryptedRoom2");

        doE2ETestWithAliceAndBobInARoom(true);

        mBobSession.getCrypto().setWarnOnUnknownDevices(false);
        mAliceSession.getCrypto().setWarnOnUnknownDevices(false);

        final Room roomFromBobPOV = mBobSession.getDataHandler().getRoom(mRoomId);
        final Room roomFromAlicePOV = mAliceSession.getDataHandler().getRoom(mRoomId);

        Assert.assertTrue(roomFromBobPOV.isEncrypted());
        Assert.assertTrue(roomFromAlicePOV.isEncrypted());

        mReceivedMessagesFromAlice = 0;
        mReceivedMessagesFromBob = 0;

        final List<CountDownLatch> list = new ArrayList<>();

        MXEventListener bobEventListener = new MXEventListener() {
            @Override
            public void onLiveEvent(Event event, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE) && !TextUtils.equals(event.getSender(), mBobSession.getMyUserId())) {
                    checkEncryptedEvent(event, mRoomId, messagesFromAlice.get(mReceivedMessagesFromAlice), mAliceSession);

                    mReceivedMessagesFromAlice++;
                    list.get(list.size() - 1).countDown();
                }
            }
        };

        MXEventListener aliceEventListener = new MXEventListener() {
            @Override
            public void onLiveEvent(Event event, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE) && !TextUtils.equals(event.getSender(), mAliceSession.getMyUserId())) {
                    checkEncryptedEvent(event, mRoomId, messagesFromBob.get(mReceivedMessagesFromBob), mBobSession);
                    mReceivedMessagesFromBob++;

                    list.get(list.size() - 1).countDown();
                }
            }
        };

        ApiCallback<Void> callback = new VoidApiCallback();

        roomFromBobPOV.addEventListener(bobEventListener);
        roomFromAlicePOV.addEventListener(aliceEventListener);

        list.add(new CountDownLatch(2));
        final Map<String, Object> results = new HashMap<>();

        mBobSession.getDataHandler().addListener(new MXEventListener() {
            @Override
            public void onToDeviceEvent(Event event) {
                results.put("onToDeviceEvent", event);
                list.get(0).countDown();
            }
        });

        roomFromAlicePOV.sendEvent(buildTextEvent(messagesFromAlice.get(mReceivedMessagesFromAlice), mAliceSession), callback);
        list.get(list.size() - 1).await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("onToDeviceEvent"));
        Assert.assertEquals(1, mReceivedMessagesFromAlice);

        list.add(new CountDownLatch(1));
        roomFromBobPOV.sendEvent(buildTextEvent(messagesFromBob.get(mReceivedMessagesFromBob), mBobSession), callback);
        list.get(list.size() - 1).await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertEquals(1, mReceivedMessagesFromBob);

        list.add(new CountDownLatch(1));
        roomFromBobPOV.sendEvent(buildTextEvent(messagesFromBob.get(mReceivedMessagesFromBob), mBobSession), callback);
        list.get(list.size() - 1).await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertEquals(2, mReceivedMessagesFromBob);

        list.add(new CountDownLatch(1));
        roomFromBobPOV.sendEvent(buildTextEvent(messagesFromBob.get(mReceivedMessagesFromBob), mBobSession), callback);
        list.get(list.size() - 1).await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertEquals(3, mReceivedMessagesFromBob);

        list.add(new CountDownLatch(1));
        roomFromAlicePOV.sendEvent(buildTextEvent(messagesFromAlice.get(mReceivedMessagesFromAlice), mAliceSession), callback);
        list.get(list.size() - 1).await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertEquals(2, mReceivedMessagesFromAlice);
    }

    @Test
    public void test09_testAliceInAEncryptedRoomAfterInitialSync() throws Exception {
        Log.e(LOG_TAG, "test09_testAliceInAEncryptedRoomAfterInitialSync");

        Context context = InstrumentationRegistry.getContext();
        final Map<String, Object> results = new HashMap<>();

        doE2ETestWithAliceInARoom();

        mAliceSession.getCrypto().setWarnOnUnknownDevices(false);

        final String message = "Hello myself!";

        Credentials aliceCredentials = mAliceSession.getCredentials();

        mAliceSession.clear(context);

        HomeServerConnectionConfig hs = CryptoTestHelper.createHomeServerConfig(aliceCredentials);

        IMXStore store = new MXFileStore(hs, context);

        final CountDownLatch lock1 = new CountDownLatch(1);

        final MXSession aliceSession2 = new MXSession.Builder(hs, new MXDataHandler(store, aliceCredentials), context)
                .build();

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
            public void onStoreOOM(String accountId, String description) {
                lock1.countDown();
            }
        };

        aliceSession2.getDataHandler().getStore().addMXStoreListener(listener);
        aliceSession2.getDataHandler().getStore().open();
        lock1.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("onStoreReady"));

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
        lock1b.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("onInitialSyncComplete"));
        Assert.assertTrue(results.containsKey("onCryptoSyncComplete"));

        Room roomFromAlicePOV2 = aliceSession2.getDataHandler().getRoom(mRoomId);

        Assert.assertTrue(roomFromAlicePOV2.isEncrypted());

        final CountDownLatch lock2 = new CountDownLatch(1);

        if (false) {
            // The android client does not echo its own message
            MXEventListener aliceEventListener = new MXEventListener() {
                @Override
                public void onLiveEvent(Event event, RoomState roomState) {
                    if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE)) {
                        checkEncryptedEvent(event, mRoomId, message, aliceSession2);

                        lock2.countDown();
                    }
                }
            };

            roomFromAlicePOV2.addEventListener(aliceEventListener);
        }

        // the IOS client echoes the message
        // the android client does not
        roomFromAlicePOV2.sendEvent(buildTextEvent(message, aliceSession2), new TestApiCallback<Void>(lock2) {
            @Override
            public void onSuccess(Void info) {
                results.put("sendEvent", "sendEvent");
                super.onSuccess(info);
            }
        });

        lock2.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("sendEvent"));

        aliceSession2.clear(context);
    }

    @Test
    public void test10_testAliceDecryptOldMessageWithANewDeviceInAEncryptedRoom() throws Exception {
        Log.e(LOG_TAG, "test10_testAliceDecryptOldMessageWithANewDeviceInAEncryptedRoom");

        Context context = InstrumentationRegistry.getContext();
        final Map<String, Object> results = new HashMap<>();

        doE2ETestWithAliceInARoom();

        mAliceSession.getCrypto().setWarnOnUnknownDevices(false);

        String message = "Hello myself!";

        Room roomFromAlicePOV = mAliceSession.getDataHandler().getRoom(mRoomId);

        CountDownLatch lock1 = new CountDownLatch(1);
        roomFromAlicePOV.sendEvent(buildTextEvent(message, mAliceSession), new TestApiCallback<Void>(lock1) {
            @Override
            public void onSuccess(Void info) {
                results.put("sendEvent", "sendEvent");
                super.onSuccess(info);
            }
        });
        lock1.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("sendEvent"));

        Credentials aliceCredentials = mAliceSession.getCredentials();
        Credentials aliceCredentials2 = new Credentials();

        // close the session and clear the data
        mAliceSession.clear(context);

        aliceCredentials2.userId = aliceCredentials.userId;
        aliceCredentials2.homeServer = aliceCredentials.homeServer;
        aliceCredentials2.accessToken = aliceCredentials.accessToken;
        aliceCredentials2.refreshToken = aliceCredentials.refreshToken;
        aliceCredentials2.deviceId = "AliceNewDevice";

        HomeServerConnectionConfig hs = CryptoTestHelper.createHomeServerConfig(aliceCredentials2);

        IMXStore store = new MXFileStore(hs, context);

        MXSession aliceSession2 = new MXSession.Builder(hs, new MXDataHandler(store, aliceCredentials2), context)
                .build();

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
            public void onStoreOOM(String accountId, String description) {
                lock1b.countDown();
            }
        };

        aliceSession2.getDataHandler().getStore().addMXStoreListener(listener);
        aliceSession2.getDataHandler().getStore().open();
        lock1b.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("onStoreReady"));

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

        lock2.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);

        Assert.assertTrue(results.containsKey("onInitialSyncComplete"));
        Assert.assertTrue(results.containsKey("onCryptoSyncComplete"));

        Room roomFromAlicePOV2 = aliceSession2.getDataHandler().getRoom(mRoomId);

        Assert.assertNotNull(roomFromAlicePOV2);
        Assert.assertTrue(roomFromAlicePOV2.getState().isEncrypted());

        Event event = roomFromAlicePOV2.getDataHandler().getStore().getLatestEvent(mRoomId);
        Assert.assertNotNull(event);
        Assert.assertTrue(event.isEncrypted());
        Assert.assertNull(event.getClearEvent());
        Assert.assertNotNull(event.getCryptoError());
        Assert.assertEquals(MXCryptoError.UNKNOWN_INBOUND_SESSION_ID_ERROR_CODE, event.getCryptoError().errcode);
        aliceSession2.clear(context);
    }

    @Test
    public void test11_testAliceAndBobInAEncryptedRoomBackPaginationFromMemoryStore() throws Exception {
        Log.e(LOG_TAG, "test11_testAliceAndBobInAEncryptedRoomBackPaginationFromMemoryStore");

        Context context = InstrumentationRegistry.getContext();
        final Map<String, Object> results = new HashMap();

        doE2ETestWithAliceAndBobInARoomWithEncryptedMessages(true);

        Credentials bobCredentials = mBobSession.getCredentials();
        mBobSession.clear(context);

        HomeServerConnectionConfig hs = CryptoTestHelper.createHomeServerConfig(bobCredentials);

        IMXStore store = new MXFileStore(hs, context);

        final CountDownLatch lock1 = new CountDownLatch(2);

        MXSession bobSession2 = new MXSession.Builder(hs, new MXDataHandler(store, bobCredentials), context)
                .build();

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

        lock1.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);

        Assert.assertTrue(results.containsKey("onInitialSyncComplete"));
        Assert.assertTrue(results.containsKey("onCryptoSyncComplete"));

        Assert.assertNotNull(bobSession2.getCrypto());

        Room roomFromBobPOV = bobSession2.getDataHandler().getRoom(mRoomId);

        final CountDownLatch lock2 = new CountDownLatch(6);
        final List<Event> receivedEvents = new ArrayList<>();

        EventTimeline.EventTimelineListener eventTimelineListener = new EventTimeline.EventTimelineListener() {
            public void onEvent(Event event, EventTimeline.Direction direction, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE)) {
                    receivedEvents.add(event);
                    lock2.countDown();
                }
            }
        };

        roomFromBobPOV.getLiveTimeLine().addEventTimelineListener(eventTimelineListener);

        roomFromBobPOV.getLiveTimeLine().backPaginate(new TestApiCallback<Integer>(lock2) {
            @Override
            public void onSuccess(Integer info) {
                results.put("backPaginate", "backPaginate");
                super.onSuccess(info);
            }
        });

        lock2.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("backPaginate"));
        Assert.assertEquals(receivedEvents.size() + " instead of 5", 5, receivedEvents.size());

        checkEncryptedEvent(receivedEvents.get(0), mRoomId, messagesFromAlice.get(1), mAliceSession);

        checkEncryptedEvent(receivedEvents.get(1), mRoomId, messagesFromBob.get(2), mBobSession);

        checkEncryptedEvent(receivedEvents.get(2), mRoomId, messagesFromBob.get(1), mBobSession);

        checkEncryptedEvent(receivedEvents.get(3), mRoomId, messagesFromBob.get(0), mBobSession);

        checkEncryptedEvent(receivedEvents.get(4), mRoomId, messagesFromAlice.get(0), mAliceSession);

        bobSession2.clear(context);
        mAliceSession.clear(context);
    }

    @Test
    public void test12_testAliceAndBobInAEncryptedRoomBackPaginationFromHomeServer() throws Exception {
        Log.e(LOG_TAG, "test12_testAliceAndBobInAEncryptedRoomBackPaginationFromHomeServer");

        Context context = InstrumentationRegistry.getContext();
        final Map<String, Object> results = new HashMap();

        doE2ETestWithAliceAndBobInARoomWithEncryptedMessages(true);

        String eventId = mBobSession.getDataHandler().getStore().getLatestEvent(mRoomId).eventId;

        EventTimeline timeline = new EventTimeline(mBobSession.getDataHandler(), mRoomId, eventId);

        final CountDownLatch lock2 = new CountDownLatch(6);
        final List<Event> receivedEvents = new ArrayList<>();

        EventTimeline.EventTimelineListener eventTimelineListener = new EventTimeline.EventTimelineListener() {
            public void onEvent(Event event, EventTimeline.Direction direction, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE)) {
                    receivedEvents.add(event);
                    lock2.countDown();
                }
            }
        };

        timeline.addEventTimelineListener(eventTimelineListener);
        timeline.backPaginate(new TestApiCallback<Integer>(lock2) {
            @Override
            public void onSuccess(Integer info) {
                results.put("backPaginate", "backPaginate");
                super.onSuccess(info);
            }
        });

        lock2.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("backPaginate"));
        Assert.assertEquals(5, receivedEvents.size());

        checkEncryptedEvent(receivedEvents.get(0), mRoomId, messagesFromAlice.get(1), mAliceSession);

        checkEncryptedEvent(receivedEvents.get(1), mRoomId, messagesFromBob.get(2), mBobSession);

        checkEncryptedEvent(receivedEvents.get(2), mRoomId, messagesFromBob.get(1), mBobSession);

        checkEncryptedEvent(receivedEvents.get(3), mRoomId, messagesFromBob.get(0), mBobSession);

        checkEncryptedEvent(receivedEvents.get(4), mRoomId, messagesFromAlice.get(0), mAliceSession);

        mBobSession.clear(context);
        mAliceSession.clear(context);
    }

    @Test
    public void test13_testAliceAndNotEncryptedBobInACryptedRoom() throws Exception {
        Log.e(LOG_TAG, "test13_testAliceAndNotEncryptedBobInACryptedRoom");

        final Map<String, Object> results = new HashMap();

        doE2ETestWithAliceAndBobInARoom(false);

        mAliceSession.getCrypto().setWarnOnUnknownDevices(false);

        Room roomFromBobPOV = mBobSession.getDataHandler().getRoom(mRoomId);
        Room roomFromAlicePOV = mAliceSession.getDataHandler().getRoom(mRoomId);

        Assert.assertTrue(roomFromBobPOV.isEncrypted());
        Assert.assertTrue(roomFromAlicePOV.isEncrypted());

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

        roomFromAlicePOV.sendEvent(buildTextEvent(messageFromAlice, mAliceSession), new VoidApiCallback());

        lock1.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("bobEcho"));

        Event event = (Event) results.get("bobEcho");
        Assert.assertTrue(event.isEncrypted());
        Assert.assertEquals(Event.EVENT_TYPE_MESSAGE_ENCRYPTED, event.getType());
        Assert.assertNotNull(event.getContentAsJsonObject());
        Assert.assertFalse(event.getContentAsJsonObject().has("body"));

        Assert.assertNotNull(event.getCryptoError());
        Assert.assertEquals(MXCryptoError.ENCRYPTING_NOT_ENABLED_ERROR_CODE, event.getCryptoError().errcode);

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

        roomFromBobPOV.sendEvent(buildTextEvent("Hello I'm Bob!", mBobSession), new VoidApiCallback());

        lock2.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("aliceEcho"));

        event = (Event) results.get("aliceEcho");
        Assert.assertFalse(event.isEncrypted());
    }

    @Test
    public void test14_testCryptoDeviceBlockAndLeave() throws Exception {
        Log.e(LOG_TAG, "test14_testCryptoDeviceBlockAndLeave");

        Context context = InstrumentationRegistry.getContext();
        final Map<String, Object> results = new HashMap<>();

        doE2ETestWithAliceAndBobAndSamInARoom();

        mBobSession.getCrypto().setWarnOnUnknownDevices(false);
        mAliceSession.getCrypto().setWarnOnUnknownDevices(false);
        mSamSession.getCrypto().setWarnOnUnknownDevices(false);

        final Room roomFromBobPOV = mBobSession.getDataHandler().getRoom(mRoomId);
        final Room roomFromAlicePOV = mAliceSession.getDataHandler().getRoom(mRoomId);
        final Room roomFromSamPOV = mSamSession.getDataHandler().getRoom(mRoomId);

        Assert.assertNotNull(roomFromBobPOV);
        Assert.assertNotNull(roomFromAlicePOV);
        Assert.assertNotNull(roomFromSamPOV);

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
        roomFromBobPOV.sendEvent(buildTextEvent("msg1", mBobSession), new TestApiCallback<Void>(lock0) {
            @Override
            public void onSuccess(Void info) {
                results.put("send0", "send0");
                super.onSuccess(info);
            }
        });
        lock0.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results + "", results.containsKey("send0") && results.containsKey("alice0") && results.containsKey("sam0"));

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
                } else if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE_ENCRYPTED)) {
                    lock1.countDown();
                }
            }
        };
        roomFromSamPOV.addEventListener(samEventsListener1);

        roomFromAlicePOV.sendEvent(buildTextEvent("msg1", mAliceSession), new TestApiCallback<Void>(lock1) {
            @Override
            public void onSuccess(Void info) {
                results.put("send1", "send1");
                super.onSuccess(info);
            }
        });
        lock1.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results + "", results.containsKey("send1") && results.containsKey("bob1") && results.containsKey("sam1"));

        List<MXDeviceInfo> list = mBobSession.getCrypto().getUserDevices(mAliceSession.getMyUserId());

        Assert.assertNotNull(list);
        Assert.assertTrue(list.size() > 0);

        CountDownLatch lock1b = new CountDownLatch(1);
        mBobSession.getCrypto().setDeviceVerification(MXDeviceInfo.DEVICE_VERIFICATION_BLOCKED, list.get(0).deviceId, mAliceSession.getMyUserId(),
                new TestApiCallback<Void>(lock1b) {
                    @Override
                    public void onSuccess(Void info) {
                        results.put("setDeviceVerification10", "setDeviceVerification10");
                        super.onSuccess(info);
                    }
                });
        lock1b.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("setDeviceVerification10"));

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
        roomFromBobPOV.sendEvent(buildTextEvent("msg2", mBobSession), new TestApiCallback<Void>(lock2) {
            @Override
            public void onSuccess(Void info) {
                results.put("send2", "send2");
                super.onSuccess(info);
            }
        });

        lock2.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("send2") && results.containsKey("alice2") && results.containsKey("sam2"));

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

        roomFromSamPOV.leave(new TestApiCallback<Void>(lock3) {
            @Override
            public void onSuccess(Void info) {
                results.put("leave", "leave");
                super.onSuccess(info);
            }
        });

        lock3.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("leave") && results.containsKey("bobleave"));

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
        roomFromBobPOV.sendEvent(buildTextEvent("msg3", mBobSession), new TestApiCallback<Void>(lock4) {
            @Override
            public void onSuccess(Void info) {
                results.put("send3", "send3");
                super.onSuccess(info);
            }
        });

        lock4.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("send3") && results.containsKey("alice3"));

        mBobSession.clear(context);
        mAliceSession.clear(context);
        mSamSession.clear(context);
    }

    @Test
    public void test15_testReplayAttack() throws Exception {
        Log.e(LOG_TAG, "test15_testReplayAttack");
        final Map<String, Object> results = new HashMap<>();

        doE2ETestWithAliceAndBobInARoom(true);

        mBobSession.getCrypto().setWarnOnUnknownDevices(false);
        mAliceSession.getCrypto().setWarnOnUnknownDevices(false);

        String messageFromAlice = "Hello I'm Alice!";

        final Room roomFromBobPOV = mBobSession.getDataHandler().getRoom(mRoomId);
        final Room roomFromAlicePOV = mAliceSession.getDataHandler().getRoom(mRoomId);

        Assert.assertTrue(roomFromBobPOV.isEncrypted());
        Assert.assertTrue(roomFromAlicePOV.isEncrypted());

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

        roomFromAlicePOV.sendEvent(buildTextEvent(messageFromAlice, mAliceSession), new VoidApiCallback());

        lock1.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("onToDeviceEvent"));
        Assert.assertTrue(results.containsKey("bobEcho"));
        Assert.assertTrue(results.containsKey("decrypted"));

        Event decryptedEvent = (Event) results.get("decrypted");

        Assert.assertNull(decryptedEvent.getClearEvent());
        Assert.assertEquals(MXCryptoError.DUPLICATED_MESSAGE_INDEX_ERROR_CODE, decryptedEvent.getCryptoError().errcode);

        // Decrypting it with no replay attack mitigation must still work
        mBobSession.getDataHandler().decryptEvent(decryptedEvent, null);
        checkEncryptedEvent(decryptedEvent, mRoomId, messageFromAlice, mAliceSession);
    }

    @Test
    public void test16_testRoomKeyReshare() throws Exception {
        Log.e(LOG_TAG, "test16_testRoomKeyReshare");

        final Map<String, Object> results = new HashMap<>();

        doE2ETestWithAliceAndBobInARoom(true);

        mBobSession.getCrypto().setWarnOnUnknownDevices(false);
        mAliceSession.getCrypto().setWarnOnUnknownDevices(false);

        String messageFromAlice = "Hello I'm Alice!";

        final Room roomFromBobPOV = mBobSession.getDataHandler().getRoom(mRoomId);
        final Room roomFromAlicePOV = mAliceSession.getDataHandler().getRoom(mRoomId);

        Assert.assertTrue(roomFromBobPOV.isEncrypted());
        Assert.assertTrue(roomFromAlicePOV.isEncrypted());

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

        final List<Event> receivedEvents = new ArrayList<>();
        EventTimeline.EventTimelineListener eventTimelineListener = new EventTimeline.EventTimelineListener() {
            public void onEvent(Event event, EventTimeline.Direction direction, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE)) {
                    receivedEvents.add(event);
                    lock1.countDown();
                }
            }
        };

        roomFromBobPOV.getLiveTimeLine().addEventTimelineListener(eventTimelineListener);

        roomFromAlicePOV.sendEvent(buildTextEvent(messageFromAlice, mAliceSession), new VoidApiCallback());

        lock1.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("onToDeviceEvent"));
        Assert.assertEquals(1, receivedEvents.size());

        Event event = receivedEvents.get(0);
        checkEncryptedEvent(event, mRoomId, messageFromAlice, mAliceSession);

        // Reinject a modified version of the received room_key event from Alice.
        // From Bob pov, that mimics Alice resharing her keys but with an advanced outbound group session.
        Event toDeviceEvent = (Event) results.get("onToDeviceEvent");
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
        checkEncryptedEvent(event, mRoomId, messageFromAlice, mAliceSession);
    }

    @Test
    public void test17_testLateRoomKey() throws Exception {
        Log.e(LOG_TAG, "test17_testLateRoomKey");

        final Map<String, Object> results = new HashMap<>();

        doE2ETestWithAliceAndBobInARoom(true);

        mBobSession.getCrypto().setWarnOnUnknownDevices(false);
        mAliceSession.getCrypto().setWarnOnUnknownDevices(false);

        String messageFromAlice = "Hello I'm Alice!";

        final Room roomFromBobPOV = mBobSession.getDataHandler().getRoom(mRoomId);
        final Room roomFromAlicePOV = mAliceSession.getDataHandler().getRoom(mRoomId);

        Assert.assertTrue(roomFromBobPOV.isEncrypted());
        Assert.assertTrue(roomFromAlicePOV.isEncrypted());

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

        final List<Event> receivedEvents = new ArrayList<>();
        EventTimeline.EventTimelineListener eventTimelineListener = new EventTimeline.EventTimelineListener() {
            public void onEvent(Event event, EventTimeline.Direction direction, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE)) {
                    receivedEvents.add(event);
                    lock1.countDown();
                }
            }
        };

        roomFromBobPOV.getLiveTimeLine().addEventTimelineListener(eventTimelineListener);

        roomFromAlicePOV.sendEvent(buildTextEvent(messageFromAlice, mAliceSession), new VoidApiCallback());

        lock1.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("onToDeviceEvent"));
        Assert.assertEquals(1, receivedEvents.size());

        Event event = receivedEvents.get(0);
        checkEncryptedEvent(event, mRoomId, messageFromAlice, mAliceSession);

        // Reinject a modified version of the received room_key event from Alice.
        // From Bob pov, that mimics Alice resharing her keys but with an advanced outbound group session.
        Event toDeviceEvent = (Event) results.get("onToDeviceEvent");
        String sessionId = toDeviceEvent.getContentAsJsonObject().get("session_id").getAsString();
        String senderKey = toDeviceEvent.senderKey();

        // remove the session
        mBobSession.getCrypto().getOlmDevice().removeInboundGroupSession(sessionId, senderKey);

        event.setClearData(null);

        // check that the message cannot be decrypted
        Assert.assertFalse(mBobSession.getDataHandler().decryptEvent(event, null));
        // check the error code
        Assert.assertEquals(MXCryptoError.UNKNOWN_INBOUND_SESSION_ID_ERROR_CODE, event.getCryptoError().errcode);

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
        lock2.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("onEventDecrypted"));
        Assert.assertEquals(1, receivedEvents.size());

        checkEncryptedEvent(receivedEvents.get(0), mRoomId, messageFromAlice, mAliceSession);
        Assert.assertNull(receivedEvents.get(0).getCryptoError());
    }

    @Test
    public void test18_testAliceAndBobWithNewDevice() throws Exception {
        Log.e(LOG_TAG, "test18_testAliceAndBobWithNewDevice");

        Context context = InstrumentationRegistry.getContext();
        final Map<String, Object> results = new HashMap<>();
        doE2ETestWithAliceAndBobInARoom(true);

        mBobSession.getCrypto().setWarnOnUnknownDevices(false);
        mAliceSession.getCrypto().setWarnOnUnknownDevices(false);

        final Room roomFromBobPOV = mBobSession.getDataHandler().getRoom(mRoomId);
        final Room roomFromAlicePOV = mAliceSession.getDataHandler().getRoom(mRoomId);

        String bobDeviceId1 = mBobSession.getCredentials().deviceId;

        Assert.assertTrue(roomFromBobPOV.isEncrypted());
        Assert.assertTrue(roomFromAlicePOV.isEncrypted());

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

        final List<Event> receivedEvents = new ArrayList<>();
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

        roomFromAlicePOV.sendEvent(buildTextEvent(aliceMessage1, mAliceSession), new VoidApiCallback());

        lock1.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("onToDeviceEvent"));
        Assert.assertEquals(1, receivedEvents.size());

        Event event = receivedEvents.get(0);
        checkEncryptedEvent(event, mRoomId, aliceMessage1, mAliceSession);

        // logout
        CountDownLatch lock2 = new CountDownLatch(1);
        String bobId = mBobSession.getCredentials().userId;
        mBobSession.logout(context, new TestApiCallback<Void>(lock2) {
            @Override
            public void onSuccess(Void info) {
                results.put("logout", "logout");
                super.onSuccess(info);
            }
        });
        lock2.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("logout"));

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
        Assert.assertNotEquals(bobDeviceId2, bobDeviceId1);

        // before sending a message, wait that the device event is received.
        lock3.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("onToDeviceEvent2"));

        SystemClock.sleep(1000);

        final Room roomFromBobPOV2 = bobSession2.getDataHandler().getRoom(mRoomId);
        Assert.assertNotNull(roomFromBobPOV2);

        final List<Event> receivedEvents4 = new ArrayList<>();
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
        roomFromAlicePOV.sendEvent(buildTextEvent(aliceMessage2, mAliceSession), new VoidApiCallback());

        lock4.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertEquals("received event of type " + results.get("event4"), 1, receivedEvents4.size());

        event = receivedEvents4.get(0);
        checkEncryptedEvent(event, mRoomId, aliceMessage2, mAliceSession);
    }

    @Test
    public void test19_testAliceWithNewDeviceAndBobWithNewDevice() throws Exception {
        Log.e(LOG_TAG, "test19_testAliceWithNewDeviceAndBobWithNewDevice");

        Context context = InstrumentationRegistry.getContext();
        final Map<String, Object> results = new HashMap<>();
        doE2ETestWithAliceAndBobInARoom(true);

        mBobSession.getCrypto().setWarnOnUnknownDevices(false);
        mAliceSession.getCrypto().setWarnOnUnknownDevices(false);

        final Room roomFromBobPOV = mBobSession.getDataHandler().getRoom(mRoomId);
        final Room roomFromAlicePOV = mAliceSession.getDataHandler().getRoom(mRoomId);

        String bobUserId1 = mBobSession.getMyUserId();
        String aliceUserId1 = mAliceSession.getMyUserId();

        Assert.assertTrue(roomFromBobPOV.isEncrypted());
        Assert.assertTrue(roomFromAlicePOV.isEncrypted());

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

        final List<Event> receivedEvents = new ArrayList<>();
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

        roomFromAlicePOV.sendEvent(buildTextEvent(aliceMessage1, mAliceSession), new VoidApiCallback());

        lock1.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("onToDeviceEvent"));
        Assert.assertEquals(1, receivedEvents.size());

        Event event = receivedEvents.get(0);
        checkEncryptedEvent(event, mRoomId, aliceMessage1, mAliceSession);

        // logout
        CountDownLatch lock2 = new CountDownLatch(1);
        mBobSession.logout(context, new TestApiCallback<Void>(lock2) {
            @Override
            public void onSuccess(Void info) {
                results.put("boblogout", "boblogout");
                super.onSuccess(info);
            }
        });
        lock2.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("boblogout"));

        CountDownLatch lock3 = new CountDownLatch(1);
        mAliceSession.logout(context, new TestApiCallback<Void>(lock3) {
            @Override
            public void onSuccess(Void info) {
                results.put("alicelogout", "alicelogout");
                super.onSuccess(info);
            }
        });
        lock3.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("alicelogout"));

        MXSession bobSession2 = CryptoTestHelper.logAccountAndSync(context, bobUserId1, MXTESTS_BOB_PWD);
        Assert.assertNotNull(bobSession2);
        bobSession2.getCrypto().setWarnOnUnknownDevices(false);

        MXSession aliceSession2 = CryptoTestHelper.logAccountAndSync(context, aliceUserId1, MXTESTS_ALICE_PWD);
        Assert.assertNotNull(aliceSession2);
        aliceSession2.getCrypto().setWarnOnUnknownDevices(false);

        Room roomFromBob2POV = bobSession2.getDataHandler().getRoom(mRoomId);
        Room roomFromAlice2POV = aliceSession2.getDataHandler().getRoom(mRoomId);

        Assert.assertTrue(roomFromBob2POV.isEncrypted());
        event = bobSession2.getDataHandler().getStore().getLatestEvent(mRoomId);
        Assert.assertNotNull(event);
        Assert.assertTrue(event.isEncrypted());
        Assert.assertNull(event.getClearEvent());
        Assert.assertNotNull(event.getCryptoError());
        Assert.assertEquals(MXCryptoError.UNKNOWN_INBOUND_SESSION_ID_ERROR_CODE, event.getCryptoError().errcode);

        final CountDownLatch lock4 = new CountDownLatch(1);
        final List<Event> receivedEvents2 = new ArrayList<>();
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
        roomFromAlice2POV.sendEvent(buildTextEvent(messageFromAlice2, aliceSession2), new VoidApiCallback());

        lock4.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertEquals(1, receivedEvents2.size());

        event = receivedEvents2.get(0);
        checkEncryptedEvent(event, mRoomId, messageFromAlice2, aliceSession2);
    }

    @Test
    public void test20_testAliceAndBlockedBob() throws Exception {
        Log.e(LOG_TAG, "test20_testAliceAndBlockedBob");
        final Map<String, String> results = new HashMap<>();

        doE2ETestWithAliceAndBobInARoom(true);

        mBobSession.getCrypto().setWarnOnUnknownDevices(false);
        mAliceSession.getCrypto().setWarnOnUnknownDevices(false);

        final Room roomFromBobPOV = mBobSession.getDataHandler().getRoom(mRoomId);
        final Room roomFromAlicePOV = mAliceSession.getDataHandler().getRoom(mRoomId);

        Assert.assertTrue(roomFromBobPOV.isEncrypted());
        Assert.assertTrue(roomFromAlicePOV.isEncrypted());

        final CountDownLatch lock1 = new CountDownLatch(1);

        final List<Event> receivedEvents = new ArrayList<>();
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

        roomFromAlicePOV.sendEvent(buildTextEvent(aliceMessage1, mAliceSession), new VoidApiCallback());

        lock1.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertEquals(1, receivedEvents.size());

        Event event = receivedEvents.get(0);
        checkEncryptedEvent(event, mRoomId, aliceMessage1, mAliceSession);

        // block the bob's device
        CountDownLatch lock1b = new CountDownLatch(1);
        mAliceSession.getCrypto()
                .setDeviceVerification(MXDeviceInfo.DEVICE_VERIFICATION_BLOCKED, mBobSession.getCredentials().deviceId, mBobSession.getMyUserId(),
                        new TestApiCallback<Void>(lock1b) {
                            @Override
                            public void onSuccess(Void info) {
                                results.put("setDeviceVerification20", "setDeviceVerification20");
                                super.onSuccess(info);
                            }
                        });
        lock1b.await();
        Assert.assertTrue(results.containsKey("setDeviceVerification20"));

        ///
        final CountDownLatch lock2 = new CountDownLatch(1);

        final List<Event> receivedEvents2 = new ArrayList<>();
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

        roomFromAlicePOV.sendEvent(buildTextEvent(aliceMessage2, mAliceSession), new VoidApiCallback());

        lock2.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertEquals(1, receivedEvents2.size());

        event = receivedEvents2.get(0);
        Assert.assertNull(event.getClearEvent());
        Assert.assertNotNull(event.getCryptoError());
        Assert.assertEquals(MXCryptoError.UNKNOWN_INBOUND_SESSION_ID_ERROR_CODE, event.getCryptoError().errcode);

        // unblock the bob's device
        CountDownLatch lock2b = new CountDownLatch(1);
        mAliceSession.getCrypto()
                .setDeviceVerification(MXDeviceInfo.DEVICE_VERIFICATION_UNVERIFIED, mBobSession.getCredentials().deviceId, mBobSession.getMyUserId(),
                        new TestApiCallback<Void>(lock2b) {
                            @Override
                            public void onSuccess(Void info) {
                                results.put("setDeviceVerification40", "setDeviceVerification40");
                                super.onSuccess(info);
                            }
                        });
        lock2b.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("setDeviceVerification40"));
        ///
        final CountDownLatch lock3 = new CountDownLatch(1);

        final List<Event> receivedEvents3 = new ArrayList<>();
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

        roomFromAlicePOV.sendEvent(buildTextEvent(aliceMessage3, mAliceSession), new VoidApiCallback());

        lock3.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertEquals(1, receivedEvents3.size());

        event = receivedEvents3.get(0);
        checkEncryptedEvent(event, mRoomId, aliceMessage3, mAliceSession);
    }


    @Test
    public void test21_testDownloadKeysWithUnreachableHS() throws Exception {
        Log.e(LOG_TAG, "test21_testDownloadKeysWithUnreachableHS");

        final Map<String, Object> results = new HashMap<>();
        doE2ETestWithAliceAndBobInARoom(true);

        mBobSession.getCrypto().setWarnOnUnknownDevices(false);
        mAliceSession.getCrypto().setWarnOnUnknownDevices(false);

        final Room roomFromBobPOV = mBobSession.getDataHandler().getRoom(mRoomId);
        final Room roomFromAlicePOV = mAliceSession.getDataHandler().getRoom(mRoomId);

        Assert.assertTrue(roomFromBobPOV.isEncrypted());
        Assert.assertTrue(roomFromAlicePOV.isEncrypted());

        CountDownLatch lock1 = new CountDownLatch(1);
        mAliceSession.getCrypto().getDeviceList()
                .downloadKeys(Arrays.asList(mBobSession.getMyUserId(), "@pppppppppppp:matrix.org"), false,
                        new TestApiCallback<MXUsersDevicesMap<MXDeviceInfo>>(lock1) {
                            @Override
                            public void onSuccess(MXUsersDevicesMap<MXDeviceInfo> info) {
                                results.put("downloadKeys", info);
                                super.onSuccess(info);
                            }
                        });

        lock1.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("downloadKeys"));

        MXUsersDevicesMap<MXDeviceInfo> usersDevicesInfoMap = (MXUsersDevicesMap<MXDeviceInfo>) results.get("downloadKeys");

        // We can get info only get for Bob
        Assert.assertEquals(1, usersDevicesInfoMap.getMap().size());

        List<String> bobDevices = usersDevicesInfoMap.getUserDeviceIds(mBobSession.getMyUserId());

        Assert.assertNotNull(bobDevices);
    }

    @Test
    public void test22_testDownloadKeysForUserWithNoDevice() throws Exception {
        Log.e(LOG_TAG, "test22_testDownloadKeysForUserWithNoDevice");

        final Map<String, Object> results = new HashMap<>();
        doE2ETestWithAliceAndBobInARoom(false);

        mAliceSession.getCrypto().setWarnOnUnknownDevices(false);

        CountDownLatch lock1 = new CountDownLatch(1);
        mAliceSession.getCrypto().getDeviceList().downloadKeys(Arrays.asList(mBobSession.getMyUserId()), false,
                new TestApiCallback<MXUsersDevicesMap<MXDeviceInfo>>(lock1) {
                    @Override
                    public void onSuccess(MXUsersDevicesMap<MXDeviceInfo> info) {
                        results.put("downloadKeys", info);
                        super.onSuccess(info);
                    }
                });

        lock1.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("downloadKeys"));

        MXUsersDevicesMap<MXDeviceInfo> usersDevicesInfoMap = (MXUsersDevicesMap<MXDeviceInfo>) results.get("downloadKeys");

        // MXCrypto.downloadKeys should return @[] for Bob to distinguish him from an unknown user
        List<String> bobDevices = usersDevicesInfoMap.getUserDeviceIds(mBobSession.getMyUserId());
        Assert.assertNotNull(bobDevices);
        Assert.assertTrue(bobDevices.isEmpty());

        // try again
        // it should not failed
        CountDownLatch lock2 = new CountDownLatch(1);
        mAliceSession.getCrypto().getDeviceList().downloadKeys(Arrays.asList(mBobSession.getMyUserId()), false,
                new TestApiCallback<MXUsersDevicesMap<MXDeviceInfo>>(lock2) {
                    @Override
                    public void onSuccess(MXUsersDevicesMap<MXDeviceInfo> info) {
                        results.put("downloadKeys2", info);
                        super.onSuccess(info);
                    }
                });

        lock2.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("downloadKeys2"));
    }

    @Test
    public void test23_testFirstMessageSentWhileSessionWasPaused() throws Exception {
        Log.e(LOG_TAG, "test23_testFirstMessageSentWhileSessionWasPaused");
        Context context = InstrumentationRegistry.getContext();
        final String messageFromAlice = "Hello I'm Alice!";

        final Map<String, Object> results = new HashMap<>();
        doE2ETestWithAliceAndBobInARoom(true);

        mBobSession.getCrypto().setWarnOnUnknownDevices(false);
        mAliceSession.getCrypto().setWarnOnUnknownDevices(false);

        final Room roomFromBobPOV = mBobSession.getDataHandler().getRoom(mRoomId);
        final Room roomFromAlicePOV = mAliceSession.getDataHandler().getRoom(mRoomId);

        Assert.assertTrue(roomFromBobPOV.isEncrypted());
        Assert.assertTrue(roomFromAlicePOV.isEncrypted());

        mBobSession.pauseEventStream();

        // wait that the bob session is really suspended
        SystemClock.sleep(30000);

        CountDownLatch lock0 = new CountDownLatch(1);

        roomFromAlicePOV.sendEvent(buildTextEvent(messageFromAlice, mAliceSession), new TestApiCallback<Void>(lock0) {
            @Override
            public void onSuccess(Void info) {
                results.put("sendEvent", "sendEvent");
                super.onSuccess(info);
            }
        });

        lock0.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("sendEvent"));

        final CountDownLatch lock2 = new CountDownLatch(2);
        MXEventListener eventListener = new MXEventListener() {
            @Override
            public void onLiveEvent(Event event, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE)) {
                    checkEncryptedEvent(event, mRoomId, messageFromAlice, mAliceSession);

                    results.put("onLiveEvent", "onLiveEvent");
                    lock2.countDown();
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

        lock2.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("onToDeviceEvent"));
        Assert.assertTrue(results.containsKey("onLiveEvent"));

        mBobSession.clear(context);
        mAliceSession.clear(context);
    }

    @Test
    public void test24_testExportImport() throws Exception {
        Log.e(LOG_TAG, "test24_testExportImport");
        Context context = InstrumentationRegistry.getContext();
        final Map<String, Object> results = new HashMap<>();

        doE2ETestWithAliceInARoom();

        mAliceSession.getCrypto().setWarnOnUnknownDevices(false);

        String message = "Hello myself!";
        String password = "hello";

        Room roomFromAlicePOV = mAliceSession.getDataHandler().getRoom(mRoomId);

        CountDownLatch lock1 = new CountDownLatch(1);
        roomFromAlicePOV.sendEvent(buildTextEvent(message, mAliceSession), new TestApiCallback<Void>(lock1) {
            @Override
            public void onSuccess(Void info) {
                results.put("sendEvent", "sendEvent");
                super.onSuccess(info);
            }
        });
        lock1.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("sendEvent"));

        Credentials aliceCredentials = mAliceSession.getCredentials();
        Credentials aliceCredentials2 = new Credentials();

        CountDownLatch lock1a = new CountDownLatch(1);
        mAliceSession.getCrypto().exportRoomKeys(password, new TestApiCallback<byte[]>(lock1a) {
            @Override
            public void onSuccess(byte[] info) {
                results.put("exportRoomKeys", info);
                super.onSuccess(info);
            }
        });

        lock1a.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("exportRoomKeys"));

        // close the session and clear the data
        mAliceSession.clear(context);

        aliceCredentials2.userId = aliceCredentials.userId;
        aliceCredentials2.homeServer = aliceCredentials.homeServer;
        aliceCredentials2.accessToken = aliceCredentials.accessToken;
        aliceCredentials2.refreshToken = aliceCredentials.refreshToken;
        aliceCredentials2.deviceId = "AliceNewDevice";

        HomeServerConnectionConfig hs = CryptoTestHelper.createHomeServerConfig(aliceCredentials2);

        IMXStore store = new MXFileStore(hs, context);

        MXSession aliceSession2 = new MXSession.Builder(hs, new MXDataHandler(store, aliceCredentials2), context)
                .build();

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
            public void onStoreOOM(String accountId, String description) {
                lock1b.countDown();
            }
        };

        aliceSession2.getDataHandler().getStore().addMXStoreListener(listener);
        aliceSession2.getDataHandler().getStore().open();
        lock1b.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("onStoreReady"));

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

        lock2.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);

        Assert.assertTrue(results.containsKey("onInitialSyncComplete"));
        Assert.assertTrue(results.containsKey("onCryptoSyncComplete"));

        Room roomFromAlicePOV2 = aliceSession2.getDataHandler().getRoom(mRoomId);

        Assert.assertNotNull(roomFromAlicePOV2);
        Assert.assertTrue(roomFromAlicePOV2.getState().isEncrypted());

        Event event = roomFromAlicePOV2.getDataHandler().getStore().getLatestEvent(mRoomId);
        Assert.assertNotNull(event);
        Assert.assertTrue(event.isEncrypted());
        Assert.assertNull(event.getClearEvent());
        Assert.assertNotNull(event.getCryptoError());
        Assert.assertEquals(MXCryptoError.UNKNOWN_INBOUND_SESSION_ID_ERROR_CODE, event.getCryptoError().errcode);

        // import the e2e keys
        // test with a wrong password
        CountDownLatch lock3 = new CountDownLatch(1);
        aliceSession2.getCrypto().importRoomKeys((byte[]) results.get("exportRoomKeys"), "wrong password", new TestApiCallback<Void>(lock3) {
            @Override
            public void onSuccess(Void info) {
                results.put("importRoomKeys", "importRoomKeys");
                super.onSuccess(info);
            }

            @Override
            public void onNetworkError(Exception e) {
                results.put("importRoomKeys_failed", "importRoomKeys_failed");
                super.onNetworkError(e);
            }

            @Override
            public void onMatrixError(MatrixError e) {
                results.put("importRoomKeys_failed", "importRoomKeys_failed");
                super.onMatrixError(e);
            }

            @Override
            public void onUnexpectedError(Exception e) {
                results.put("importRoomKeys_failed", "importRoomKeys_failed");
                super.onUnexpectedError(e);
            }
        });
        lock3.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertFalse(results.containsKey("importRoomKeys"));
        Assert.assertTrue(results.containsKey("importRoomKeys_failed"));

        // check that the message cannot be decrypted
        event = roomFromAlicePOV2.getDataHandler().getStore().getLatestEvent(mRoomId);
        Assert.assertNotNull(event);
        Assert.assertTrue(event.isEncrypted());
        Assert.assertNull(event.getClearEvent());
        Assert.assertNotNull(event.getCryptoError());
        Assert.assertEquals(MXCryptoError.UNKNOWN_INBOUND_SESSION_ID_ERROR_CODE, event.getCryptoError().errcode);

        CountDownLatch lock4 = new CountDownLatch(1);
        aliceSession2.getCrypto().importRoomKeys((byte[]) results.get("exportRoomKeys"), password, new TestApiCallback<Void>(lock4) {
            @Override
            public void onSuccess(Void info) {
                results.put("importRoomKeys", "importRoomKeys");
                super.onSuccess(info);
            }
        });
        lock4.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("importRoomKeys"));

        // check that the message CAN be decrypted
        event = roomFromAlicePOV2.getDataHandler().getStore().getLatestEvent(mRoomId);
        Assert.assertNotNull(event);
        Assert.assertTrue(event.isEncrypted());
        Assert.assertNotNull(event.getClearEvent());
        Assert.assertNull(event.getCryptoError());
        checkEncryptedEvent(event, mRoomId, message, mAliceSession);

        aliceSession2.clear(context);
    }

    @Test
    // issue https://github.com/vector-im/riot-web/issues/2305
    public void test25_testLeftAndJoinedBob() throws Exception {
        Log.e(LOG_TAG, "test25_testLeftAndJoinedBob");
        Context context = InstrumentationRegistry.getContext();

        final String messageFromAlice = "Hello I'm Alice!";
        final String message2FromAlice = "I'm still Alice!";

        final Map<String, Object> results = new HashMap<>();

        createAliceAccount();
        createBobAccount();

        CountDownLatch lock_1 = new CountDownLatch(2);

        mAliceSession.enableCrypto(true, new TestApiCallback<Void>(lock_1));
        mBobSession.enableCrypto(true, new TestApiCallback<Void>(lock_1));

        lock_1.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertNotNull(mAliceSession.getCrypto());
        Assert.assertNotNull(mBobSession.getCrypto());

        mAliceSession.getCrypto().setWarnOnUnknownDevices(false);
        mBobSession.getCrypto().setWarnOnUnknownDevices(false);

        CountDownLatch lock0 = new CountDownLatch(1);
        mAliceSession.createRoom(null, null, RoomState.DIRECTORY_VISIBILITY_PUBLIC,
                null, RoomState.GUEST_ACCESS_CAN_JOIN,
                null, new TestApiCallback<String>(lock0) {
                    @Override
                    public void onSuccess(String roomId) {
                        results.put("roomId", roomId);
                        super.onSuccess(roomId);
                    }
                });
        lock0.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("roomId"));
        mRoomId = (String) results.get("roomId");

        Room roomFromAlicePOV = mAliceSession.getDataHandler().getRoom(mRoomId);

        CountDownLatch lock1 = new CountDownLatch(1);
        roomFromAlicePOV.enableEncryptionWithAlgorithm(MXCryptoAlgorithms.MXCRYPTO_ALGORITHM_MEGOLM, new TestApiCallback<Void>(lock1) {
            @Override
            public void onSuccess(Void info) {
                results.put("enableEncryptionWithAlgorithm", "enableEncryptionWithAlgorithm");
                super.onSuccess(info);
            }
        });
        lock1.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("enableEncryptionWithAlgorithm"));

        CountDownLatch lock2 = new CountDownLatch(1);
        mBobSession.joinRoom(mRoomId, new TestApiCallback<String>(lock2) {
            @Override
            public void onSuccess(String info) {
                results.put("joinRoom", "joinRoom");
                super.onSuccess(info);
            }
        });
        lock2.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("joinRoom"));

        Room roomFromBobPOV = mBobSession.getDataHandler().getRoom(mRoomId);
        final CountDownLatch lock3 = new CountDownLatch(1);
        final List<Event> receivedEvents = new ArrayList<>();
        EventTimeline.EventTimelineListener eventTimelineListener = new EventTimeline.EventTimelineListener() {
            public void onEvent(Event event, EventTimeline.Direction direction, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE)) {
                    receivedEvents.add(event);
                    lock3.countDown();
                }
            }
        };

        roomFromBobPOV.getLiveTimeLine().addEventTimelineListener(eventTimelineListener);
        roomFromAlicePOV.sendEvent(buildTextEvent(messageFromAlice, mAliceSession), new VoidApiCallback());

        lock3.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertEquals(1, receivedEvents.size());

        Event event = receivedEvents.get(0);
        checkEncryptedEvent(event, mRoomId, messageFromAlice, mAliceSession);

        CountDownLatch lock4 = new CountDownLatch(1);
        roomFromBobPOV.leave(new TestApiCallback<Void>(lock4) {
            @Override
            public void onSuccess(Void info) {
                results.put("leave", "leave");
                super.onSuccess(info);
            }
        });
        lock4.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("leave"));

        // Make Bob come back to the room with a new device
        Credentials bobCredentials = mBobSession.getCredentials();
        mBobSession.clear(context);

        MXSession bobSession2 = CryptoTestHelper.logAccountAndSync(context, bobCredentials.userId, MXTESTS_BOB_PWD);
        Assert.assertNotNull(bobSession2);
        Assert.assertTrue(bobSession2.isCryptoEnabled());
        Assert.assertNotEquals(bobSession2.getCrypto().getMyDevice().deviceId, bobCredentials.deviceId);
        bobSession2.getCrypto().setWarnOnUnknownDevices(false);

        CountDownLatch lock5 = new CountDownLatch(1);
        bobSession2.joinRoom(mRoomId, new TestApiCallback<String>(lock5) {
            @Override
            public void onSuccess(String info) {
                results.put("joinRoom2", "joinRoom2");
                super.onSuccess(info);
            }
        });
        lock5.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("joinRoom2"));

        Room roomFromBobPOV2 = bobSession2.getDataHandler().getRoom(mRoomId);

        final CountDownLatch lock6 = new CountDownLatch(1);
        final List<Event> receivedEvents2 = new ArrayList<>();
        EventTimeline.EventTimelineListener eventTimelineListener2 = new EventTimeline.EventTimelineListener() {
            public void onEvent(Event event, EventTimeline.Direction direction, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE)) {
                    receivedEvents2.add(event);
                    lock6.countDown();
                }
            }
        };

        roomFromBobPOV2.getLiveTimeLine().addEventTimelineListener(eventTimelineListener2);
        roomFromAlicePOV.sendEvent(buildTextEvent(message2FromAlice, mAliceSession), new VoidApiCallback());

        lock6.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertEquals(1, receivedEvents2.size());

        event = receivedEvents2.get(0);
        checkEncryptedEvent(event, mRoomId, message2FromAlice, mAliceSession);

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
        final Map<String, Object> results = new HashMap<>();

        doE2ETestWithAliceAndBobAndSamInARoom();

        final String messageFromAlice = "Hello I'm Alice!";

        Room roomFromBobPOV = mBobSession.getDataHandler().getRoom(mRoomId);
        Room roomFromAlicePOV = mAliceSession.getDataHandler().getRoom(mRoomId);
        Room roomFromSamPOV = mSamSession.getDataHandler().getRoom(mRoomId);

        Assert.assertTrue(roomFromBobPOV.isEncrypted());
        Assert.assertTrue(roomFromAlicePOV.isEncrypted());
        Assert.assertTrue(roomFromSamPOV.isEncrypted());

        CountDownLatch lock1 = new CountDownLatch(1);

        roomFromAlicePOV.sendEvent(buildTextEvent(messageFromAlice, mAliceSession), new TestApiCallback<Void>(lock1) {
            @Override
            public void onMatrixError(MatrixError e) {
                results.put("sendEventError", e);
                super.onMatrixError(e);
            }
        });

        lock1.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("sendEventError"));
        MXCryptoError error = (MXCryptoError) results.get("sendEventError");
        Assert.assertEquals(MXCryptoError.UNKNOWN_DEVICES_CODE, error.errcode);
        MXUsersDevicesMap<MXDeviceInfo> unknownDevices = (MXUsersDevicesMap<MXDeviceInfo>) error.mExceptionData;

        // only one bob device
        List<String> deviceInfos = unknownDevices.getUserDeviceIds(mBobSession.getMyUserId());
        Assert.assertEquals(1, deviceInfos.size());
        Assert.assertTrue(deviceInfos.contains(mBobSession.getCrypto().getMyDevice().deviceId));

        // only one Sam device
        deviceInfos = unknownDevices.getUserDeviceIds(mSamSession.getMyUserId());
        Assert.assertEquals(1, deviceInfos.size());
        Assert.assertTrue(deviceInfos.contains(mSamSession.getCrypto().getMyDevice().deviceId));

        CountDownLatch lock2 = new CountDownLatch(1);
        mAliceSession.getCrypto().setDevicesKnown(Arrays.asList(mBobSession.getCrypto().getMyDevice(), mSamSession.getCrypto().getMyDevice()),
                new TestApiCallback<Void>(lock2) {
                    @Override
                    public void onSuccess(Void info) {
                        results.put("setDevicesKnown", "setDevicesKnown");
                        super.onSuccess(info);
                    }
                });

        lock2.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("setDevicesKnown"));

        final CountDownLatch lock3 = new CountDownLatch(5);

        MXEventListener eventListenerBob1 = new MXEventListener() {
            @Override
            public void onLiveEvent(Event event, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE)) {
                    checkEncryptedEvent(event, mRoomId, messageFromAlice, mAliceSession);

                    results.put("onLiveEventBob1", "onLiveEvent");
                    lock3.countDown();
                }
            }
        };

        MXEventListener eventListenerSam1 = new MXEventListener() {
            @Override
            public void onLiveEvent(Event event, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE)) {
                    checkEncryptedEvent(event, mRoomId, messageFromAlice, mAliceSession);

                    results.put("onLiveEventSam1", "onLiveEvent");
                    lock3.countDown();
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

        roomFromAlicePOV.sendEvent(buildTextEvent(messageFromAlice, mAliceSession), new TestApiCallback<Void>(lock3) {
            @Override
            public void onSuccess(Void info) {
                lock3.countDown();
            }
        });

        lock3.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("onToDeviceEventBob"));
        Assert.assertTrue(results.containsKey("onToDeviceEventSam"));
        Assert.assertTrue(results.containsKey("onLiveEventBob1"));
        Assert.assertTrue(results.containsKey("onLiveEventSam1"));

        roomFromBobPOV.removeEventListener(eventListenerBob1);
        roomFromSamPOV.removeEventListener(eventListenerSam1);

        // play with the device black listing
        final List<CountDownLatch> activeLock = new ArrayList<>();
        final List<String> activeMessage = new ArrayList<>();

        MXEventListener eventListenerBob2 = new MXEventListener() {
            @Override
            public void onLiveEvent(Event event, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE)) {
                    checkEncryptedEvent(event, mRoomId, activeMessage.get(0), mAliceSession);

                    results.put("eventListenerBob2", "onLiveEvent");
                    activeLock.get(0).countDown();
                } else if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE_ENCRYPTED)) {
                    results.put("eventListenerEncyptedBob2", "onLiveEvent");
                    activeLock.get(0).countDown();
                }
            }
        };

        MXEventListener eventListenerSam2 = new MXEventListener() {
            @Override
            public void onLiveEvent(Event event, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE)) {
                    checkEncryptedEvent(event, mRoomId, activeMessage.get(0), mAliceSession);

                    results.put("eventListenerSam2", "onLiveEvent");
                    activeLock.get(0).countDown();
                } else if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE_ENCRYPTED)) {
                    results.put("eventListenerEncyptedSam2", "onLiveEvent");
                    activeLock.get(0).countDown();
                }
            }
        };

        roomFromBobPOV.addEventListener(eventListenerBob2);
        roomFromSamPOV.addEventListener(eventListenerSam2);

        CountDownLatch lock4 = new CountDownLatch(1);
        mAliceSession.getCrypto().setGlobalBlacklistUnverifiedDevices(true, new TestApiCallback<Void>(lock4) {
            @Override
            public void onSuccess(Void info) {
                results.put("setGlobalBlacklistUnverifiedDevicesTrue", "setGlobalBlacklistUnverifiedDevices");
                super.onSuccess(info);
            }
        });
        lock4.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("setGlobalBlacklistUnverifiedDevicesTrue"));

        // ensure that there is no received message
        results.clear();
        CountDownLatch lock5 = new CountDownLatch(3);
        activeLock.clear();
        activeLock.add(lock5);

        activeMessage.clear();
        activeMessage.add("message 1");

        roomFromAlicePOV.sendEvent(buildTextEvent(activeMessage.get(0), mAliceSession), new TestApiCallback<Void>(lock5));

        lock5.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertFalse(results.containsKey("eventListenerBob2"));
        Assert.assertFalse(results.containsKey("eventListenerSam2"));
        Assert.assertTrue(results.containsKey("eventListenerEncyptedBob2"));
        Assert.assertTrue(results.containsKey("eventListenerEncyptedSam2"));

        CountDownLatch lock6 = new CountDownLatch(1);
        mAliceSession.getCrypto().setGlobalBlacklistUnverifiedDevices(false, new TestApiCallback<Void>(lock6) {
            @Override
            public void onSuccess(Void info) {
                results.put("setGlobalBlacklistUnverifiedDevicesfalse", "setGlobalBlacklistUnverifiedDevices");
                super.onSuccess(info);
            }
        });
        lock6.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("setGlobalBlacklistUnverifiedDevicesfalse"));

        // ensure that the messages are received
        results.clear();
        CountDownLatch lock7 = new CountDownLatch(3);
        activeLock.clear();
        activeLock.add(lock7);

        activeMessage.clear();
        activeMessage.add("message 2");

        roomFromAlicePOV.sendEvent(buildTextEvent(activeMessage.get(0), mAliceSession), new TestApiCallback<Void>(lock7));

        lock7.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("eventListenerBob2"));
        Assert.assertTrue(results.containsKey("eventListenerSam2"));
        Assert.assertFalse(results.containsKey("eventListenerEncyptedBob2"));
        Assert.assertFalse(results.containsKey("eventListenerEncyptedSam2"));

        // verify the bob device
        CountDownLatch lock8 = new CountDownLatch(3);
        mAliceSession.getCrypto().setDeviceVerification(MXDeviceInfo.DEVICE_VERIFICATION_VERIFIED,
                mBobSession.getCrypto().getMyDevice().deviceId,
                mBobSession.getMyUserId(), new TestApiCallback<Void>(lock8) {
                    @Override
                    public void onSuccess(Void info) {
                        results.put("setDeviceVerificationBob", "setDeviceVerificationBob");
                        super.onSuccess(info);
                    }
                }
        );
        lock8.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("setDeviceVerificationBob"));

        CountDownLatch lock9 = new CountDownLatch(3);
        mAliceSession.getCrypto().setRoomBlacklistUnverifiedDevices(roomFromAlicePOV.getRoomId(), new TestApiCallback<Void>(lock9) {
            @Override
            public void onSuccess(Void info) {
                results.put("setRoomBlacklistUnverifiedDevices", "setRoomBlacklistUnverifiedDevices");
                super.onSuccess(info);
            }
        });
        lock9.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("setRoomBlacklistUnverifiedDevices"));

        // ensure that the messages are received
        results.clear();
        CountDownLatch lock10 = new CountDownLatch(3);
        activeLock.clear();
        activeLock.add(lock10);

        activeMessage.clear();
        activeMessage.add("message 3");

        roomFromAlicePOV.sendEvent(buildTextEvent(activeMessage.get(0), mAliceSession), new TestApiCallback<Void>(lock10));

        lock10.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("eventListenerBob2"));
        Assert.assertFalse(results.containsKey("eventListenerSam2"));
        Assert.assertFalse(results.containsKey("eventListenerEncyptedBob2"));
        Assert.assertTrue(results.containsKey("eventListenerEncyptedSam2"));

        CountDownLatch lock11 = new CountDownLatch(3);
        mAliceSession.getCrypto().setRoomUnblacklistUnverifiedDevices(roomFromAlicePOV.getRoomId(), new TestApiCallback<Void>(lock11) {
            @Override
            public void onSuccess(Void info) {
                results.put("setRoomUnblacklistUnverifiedDevices", "setRoomUnblacklistUnverifiedDevices");
                super.onSuccess(info);
            }
        });
        lock11.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("setRoomUnblacklistUnverifiedDevices"));

        // ensure that the messages are received
        results.clear();
        CountDownLatch lock12 = new CountDownLatch(3);
        activeLock.clear();
        activeLock.add(lock12);

        activeMessage.clear();
        activeMessage.add("message 3");

        roomFromAlicePOV.sendEvent(buildTextEvent(activeMessage.get(0), mAliceSession), new TestApiCallback<Void>(lock12));

        lock12.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("eventListenerBob2"));
        Assert.assertTrue(results.containsKey("eventListenerSam2"));
        Assert.assertFalse(results.containsKey("eventListenerEncyptedBob2"));
        Assert.assertFalse(results.containsKey("eventListenerEncyptedSam2"));

        mBobSession.clear(context);
    }

    @Test
    // Test for https://github.com/matrix-org/matrix-js-sdk/pull/359
    // - Alice sends a message to Bob to a non encrypted room
    // - Bob logs in with a new device
    // - Alice turns the crypto ON in the room
    // - Alice sends a message
    // -> Bob must be able to decrypt this message
    public void test27_testEnableEncryptionAfterNonEncryptedMessages() throws Exception {
        Log.e(LOG_TAG, "test27_testEnableEncryptionAfterNonEncryptedMessages");
        Context context = InstrumentationRegistry.getContext();
        final Map<String, Object> results = new HashMap<>();

        final String messageFromAlice = "Hello I'm Alice!";
        final String message2FromAlice = "I'm still Alice!";

        createAliceAccount();
        createBobAccount();

        CountDownLatch lock00b = new CountDownLatch(2);
        mAliceSession.enableCrypto(true, new TestApiCallback<Void>(lock00b) {
            @Override
            public void onSuccess(Void info) {
                results.put("enableCrypto1", "enableCrypto1");
                super.onSuccess(info);
            }
        });

        mBobSession.enableCrypto(true, new TestApiCallback<Void>(lock00b) {
            @Override
            public void onSuccess(Void info) {
                results.put("enableCrypto2", "enableCrypto2");
            }
        });
        lock00b.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("enableCrypto2"));
        Assert.assertTrue(results.containsKey("enableCrypto1"));

        mAliceSession.getCrypto().setWarnOnUnknownDevices(false);
        mBobSession.getCrypto().setWarnOnUnknownDevices(false);

        CountDownLatch lock0 = new CountDownLatch(1);
        mAliceSession.createRoom(null, null, RoomState.DIRECTORY_VISIBILITY_PUBLIC,
                null, RoomState.GUEST_ACCESS_CAN_JOIN,
                null, new TestApiCallback<String>(lock0) {
                    @Override
                    public void onSuccess(String roomId) {
                        results.put("roomId", roomId);
                        super.onSuccess(roomId);
                    }
                });
        lock0.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("roomId"));
        mRoomId = (String) results.get("roomId");

        CountDownLatch lock1 = new CountDownLatch(1);
        mBobSession.joinRoom(mRoomId, new TestApiCallback<String>(lock1) {
            @Override
            public void onSuccess(String info) {
                results.put("joinRoom", "joinRoom");
                super.onSuccess(info);
            }
        });
        lock1.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("joinRoom"));

        Room roomFromAlicePOV = mAliceSession.getDataHandler().getRoom(mRoomId);

        CountDownLatch lock2 = new CountDownLatch(1);
        roomFromAlicePOV.sendEvent(buildTextEvent(messageFromAlice, mAliceSession), new TestApiCallback<Void>(lock2) {
            @Override
            public void onSuccess(Void info) {
                results.put("sendEvent1", "sendEvent1");
                super.onSuccess(info);
            }
        });
        lock2.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("sendEvent1"));

        // Make Bob come back to the room with a new device
        Credentials bobCredentials = mBobSession.getCredentials();
        mBobSession.clear(context);

        MXSession bobSession2 = CryptoTestHelper.logAccountAndSync(context, bobCredentials.userId, MXTESTS_BOB_PWD);
        Assert.assertNotNull(bobSession2);
        Assert.assertTrue(bobSession2.isCryptoEnabled());
        Assert.assertNotEquals(bobSession2.getCrypto().getMyDevice().deviceId, bobCredentials.deviceId);
        bobSession2.getCrypto().setWarnOnUnknownDevices(false);

        CountDownLatch lock3 = new CountDownLatch(1);
        roomFromAlicePOV.enableEncryptionWithAlgorithm(MXCryptoAlgorithms.MXCRYPTO_ALGORITHM_MEGOLM, new TestApiCallback<Void>(lock3) {
            @Override
            public void onSuccess(Void info) {
                results.put("enableEncryptionWithAlgorithm", "enableEncryptionWithAlgorithm");
                super.onSuccess(info);
            }
        });
        lock3.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("enableEncryptionWithAlgorithm"));

        Room roomFromBobPOV2 = bobSession2.getDataHandler().getRoom(mRoomId);

        final CountDownLatch lock4 = new CountDownLatch(1);
        final List<Event> receivedEvents2 = new ArrayList<>();
        EventTimeline.EventTimelineListener eventTimelineListener2 = new EventTimeline.EventTimelineListener() {
            public void onEvent(Event event, EventTimeline.Direction direction, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE)) {
                    receivedEvents2.add(event);
                    lock4.countDown();
                } else if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE_ENCRYPTED)) {
                    lock4.countDown();
                }
            }
        };

        roomFromBobPOV2.getLiveTimeLine().addEventTimelineListener(eventTimelineListener2);
        roomFromAlicePOV.sendEvent(buildTextEvent(message2FromAlice, mAliceSession), new VoidApiCallback());

        lock4.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertEquals(1, receivedEvents2.size());

        Event event = receivedEvents2.get(0);
        checkEncryptedEvent(event, mRoomId, message2FromAlice, mAliceSession);

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
        final Map<String, Object> results = new HashMap();

        doE2ETestWithAliceAndBobInARoomWithEncryptedMessages(true);

        // - Bob leaves the room, so stops getting updates
        CountDownLatch lock1 = new CountDownLatch(1);

        final Room bobLeftRoom = mBobSession.getDataHandler().getRoom(mRoomId);
        bobLeftRoom.leave(new TestApiCallback<Void>(lock1) {
            @Override
            public void onSuccess(Void info) {
                results.put("lock1", "lock1");
                super.onSuccess(info);
            }
        });

        lock1.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("lock1"));

        // - Alice adds a new device
        final MXSession aliceSession2 = CryptoTestHelper.logAccountAndSync(context, mAliceSession.getMyUserId(), MXTESTS_ALICE_PWD);
        Assert.assertNotNull(aliceSession2);

        // - Alice and Bob start sharing a room again
        CountDownLatch lock3 = new CountDownLatch(1);
        aliceSession2.createRoom(null, null, RoomState.DIRECTORY_VISIBILITY_PUBLIC,
                null, RoomState.GUEST_ACCESS_CAN_JOIN,
                null, new TestApiCallback<String>(lock3) {
                    @Override
                    public void onSuccess(String info) {
                        mRoomId = info;
                        super.onSuccess(info);
                    }
                });
        lock3.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertNotNull(mRoomId);

        Room roomFromAlicePOV = aliceSession2.getDataHandler().getRoom(mRoomId);
        CountDownLatch lock4 = new CountDownLatch(1);
        roomFromAlicePOV.enableEncryptionWithAlgorithm(MXCryptoAlgorithms.MXCRYPTO_ALGORITHM_MEGOLM, new TestApiCallback<Void>(lock4) {
            @Override
            public void onSuccess(Void info) {
                results.put("lock4", "lock4");
                super.onSuccess(info);
            }
        });
        lock4.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("lock4"));

        CountDownLatch lock5 = new CountDownLatch(1);
        mBobSession.joinRoom(mRoomId, new TestApiCallback<String>(lock5) {
            @Override
            public void onSuccess(String info) {
                results.put("lock5", "lock5");
                super.onSuccess(info);
            }
        });
        lock5.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("lock5"));

        // - Bob has an out of date list of Alice's devices
        Room roomFromBobPOV = mBobSession.getDataHandler().getRoom(mRoomId);
        final String messageFromBob = "Hello Alice with new device!";
        final CountDownLatch lock6 = new CountDownLatch(2);
        MXEventListener eventListener = new MXEventListener() {
            @Override
            public void onLiveEvent(Event event, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE)) {
                    checkEncryptedEvent(event, mRoomId, messageFromBob, mBobSession);

                    results.put("lock6", "lock6");
                    lock6.countDown();
                }
            }
        };

        roomFromAlicePOV.addEventListener(eventListener);

        roomFromBobPOV.sendEvent(buildTextEvent(messageFromBob, mBobSession), new TestApiCallback<Void>(lock6));

        lock6.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("lock6"));

        mBobSession.clear(context);
        mAliceSession.clear(context);
        aliceSession2.clear(context);
    }

    /**
     * We want to test that the relates_to data are well copied from e2e event to clear event
     *
     * @throws Exception
     */
    @Test
    public void test29_testAliceAndBobInAEncryptedRoomWithReplyTo() throws Exception {
        Log.e(LOG_TAG, "test08_testAliceAndBobInAEncryptedRoom2");

        doE2ETestWithAliceAndBobInARoom(true);

        mBobSession.getCrypto().setWarnOnUnknownDevices(false);
        mAliceSession.getCrypto().setWarnOnUnknownDevices(false);

        final Room roomFromBobPOV = mBobSession.getDataHandler().getRoom(mRoomId);
        final Room roomFromAlicePOV = mAliceSession.getDataHandler().getRoom(mRoomId);

        Assert.assertTrue(roomFromBobPOV.isEncrypted());
        Assert.assertTrue(roomFromAlicePOV.isEncrypted());

        final List<Event> bobReceivedEvents = new ArrayList<>();
        final List<Event> aliceReceivedEvents = new ArrayList<>();

        mReceivedMessagesFromAlice = 0;
        mReceivedMessagesFromBob = 0;

        final List<CountDownLatch> list = new ArrayList<>();

        MXEventListener bobEventListener = new MXEventListener() {
            @Override
            public void onLiveEvent(Event event, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE) && !TextUtils.equals(event.getSender(), mBobSession.getMyUserId())) {
                    bobReceivedEvents.add(event);

                    checkEncryptedEvent(event, mRoomId, messagesFromAlice.get(mReceivedMessagesFromAlice), mAliceSession);

                    mReceivedMessagesFromAlice++;
                    list.get(list.size() - 1).countDown();
                }
            }
        };

        MXEventListener aliceEventListener = new MXEventListener() {
            @Override
            public void onLiveEvent(Event event, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE) && !TextUtils.equals(event.getSender(), mAliceSession.getMyUserId())) {
                    aliceReceivedEvents.add(event);

                    try {
                        // "In reply to" format for body
                        String expectedMessage = "> <" + mAliceSession.getMyUserId() + "> "
                                + messagesFromAlice.get(mReceivedMessagesFromAlice - 1)
                                + "\n\n"
                                + messagesFromBob.get(mReceivedMessagesFromBob);


                        checkEncryptedEvent(event, mRoomId, expectedMessage, mBobSession);

                        mReceivedMessagesFromBob++;

                        list.get(list.size() - 1).countDown();
                    } catch (Exception e) {

                    }
                }
            }
        };

        ApiCallback<Void> callback = new VoidApiCallback();

        roomFromBobPOV.addEventListener(bobEventListener);
        roomFromAlicePOV.addEventListener(aliceEventListener);

        list.add(new CountDownLatch(2));
        final Map<String, Object> results = new HashMap<>();

        mBobSession.getDataHandler().addListener(new MXEventListener() {
            @Override
            public void onToDeviceEvent(Event event) {
                results.put("onToDeviceEvent", event);
                list.get(0).countDown();
            }
        });

        // Alice sends a first event
        roomFromAlicePOV.sendEvent(buildTextEvent(messagesFromAlice.get(mReceivedMessagesFromAlice), mAliceSession), callback);
        list.get(list.size() - 1).await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("onToDeviceEvent"));
        Assert.assertEquals(1, mReceivedMessagesFromAlice);

        // Bob reply to Alice event
        Assert.assertTrue(roomFromBobPOV.canReplyTo(bobReceivedEvents.get(0)));

        list.add(new CountDownLatch(1));
        roomFromBobPOV.sendTextMessage(messagesFromBob.get(mReceivedMessagesFromBob), null, Message.MSGTYPE_TEXT, bobReceivedEvents.get(0), null);
        list.get(list.size() - 1).await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertEquals(1, mReceivedMessagesFromBob);

        Event event = aliceReceivedEvents.get(0);
        JsonObject json = event.getContentAsJsonObject();

        Assert.assertNotNull(json);

        // Check that the received event contains a formatted body
        Assert.assertTrue(json.has("formatted_body"));

        // Check that the received event contains the relates to field
        Assert.assertTrue(json.has("m.relates_to"));

        RelatesTo relatesTo = (RelatesTo) JsonUtils.toClass(json.get("m.relates_to"), RelatesTo.class);

        Assert.assertNotNull(relatesTo);

        // Check that the event id matches
        Assert.assertEquals(bobReceivedEvents.get(0).eventId, relatesTo.dict.get("event_id"));
    }

    //==============================================================================================================
    // private test routines
    //==============================================================================================================

    private MXSession mBobSession;
    private MXSession mAliceSession;
    private MXSession mSamSession;
    private String mRoomId;
    private int mMessagesCount;
    private int mReceivedMessagesFromAlice;
    private int mReceivedMessagesFromBob;

    public void createBobAccount() throws Exception {
        Context context = InstrumentationRegistry.getContext();
        mBobSession = null;
        mBobSession = CryptoTestHelper.createAccountAndSync(context,
                MXTESTS_BOB + System.currentTimeMillis() + UUID.randomUUID().toString(), MXTESTS_BOB_PWD, true);
        Assert.assertNotNull(mBobSession);
    }

    public void createAliceAccount() throws Exception {
        Context context = InstrumentationRegistry.getContext();
        mAliceSession = null;
        mAliceSession = CryptoTestHelper.createAccountAndSync(context,
                MXTESTS_ALICE + System.currentTimeMillis() + UUID.randomUUID().toString(), MXTESTS_ALICE_PWD, true);
        Assert.assertNotNull(mAliceSession);
    }

    public void createSamAccount() throws Exception {
        Context context = InstrumentationRegistry.getContext();
        mSamSession = null;
        mSamSession = CryptoTestHelper.createAccountAndSync(context,
                MXTESTS_SAM + System.currentTimeMillis() + UUID.randomUUID().toString(), MXTESTS_SAM_PWD, true);
        Assert.assertNotNull(mSamSession);
    }

    private void doE2ETestWithAliceInARoom() throws Exception {
        final Map<String, Object> results = new HashMap<>();

        createAliceAccount();

        CountDownLatch lock0 = new CountDownLatch(1);

        mAliceSession.enableCrypto(true, new TestApiCallback<Void>(lock0) {
            @Override
            public void onSuccess(Void info) {
                results.put("enableCrypto", "enableCrypto");
                super.onSuccess(info);
            }
        });
        lock0.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("enableCrypto"));

        mRoomId = null;
        CountDownLatch lock1 = new CountDownLatch(1);

        mAliceSession.createRoom(new TestApiCallback<String>(lock1) {
            @Override
            public void onSuccess(String roomId) {
                mRoomId = roomId;
                super.onSuccess(roomId);
            }
        });

        lock1.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertNotNull(mRoomId);

        Room room = mAliceSession.getDataHandler().getRoom(mRoomId);

        CountDownLatch lock2 = new CountDownLatch(1);
        room.enableEncryptionWithAlgorithm(MXCryptoAlgorithms.MXCRYPTO_ALGORITHM_MEGOLM, new TestApiCallback<Void>(lock2) {
            @Override
            public void onSuccess(Void info) {
                results.put("enableEncryptionWithAlgorithm", "enableEncryptionWithAlgorithm");
                super.onSuccess(info);
            }
        });
        lock2.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("enableEncryptionWithAlgorithm"));
    }

    private void doE2ETestWithAliceAndBobInARoom(boolean cryptedBob) throws Exception {
        final Map<String, String> statuses = new HashMap<>();

        doE2ETestWithAliceInARoom();

        Room room = mAliceSession.getDataHandler().getRoom(mRoomId);

        createBobAccount();
        CountDownLatch lock0 = new CountDownLatch(1);

        mBobSession.enableCrypto(cryptedBob, new TestApiCallback<Void>(lock0) {
            @Override
            public void onSuccess(Void info) {
                statuses.put("enableCrypto", "enableCrypto");
                super.onSuccess(info);
            }
        });
        lock0.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);

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

        room.invite(mBobSession.getMyUserId(), new TestApiCallback<Void>(lock1) {
            @Override
            public void onSuccess(Void info) {
                statuses.put("invite", "invite");
                super.onSuccess(info);
            }
        });

        lock1.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);

        Assert.assertTrue(statuses.containsKey("invite") && statuses.containsKey("onNewRoom"));

        mBobSession.getDataHandler().removeListener(bobEventListener);

        final CountDownLatch lock2 = new CountDownLatch(2);

        mBobSession.joinRoom(mRoomId, new TestApiCallback<String>(lock2) {
            @Override
            public void onSuccess(String info) {
                statuses.put("joinRoom", "joinRoom");
                super.onSuccess(info);
            }

            @Override
            public void onNetworkError(Exception e) {
                statuses.put("onNetworkError", e.getMessage());
                super.onNetworkError(e);
            }

            @Override
            public void onMatrixError(MatrixError e) {
                statuses.put("onMatrixError", e.getMessage());
                super.onMatrixError(e);
            }

            @Override
            public void onUnexpectedError(Exception e) {
                statuses.put("onUnexpectedError", e.getMessage());
                super.onUnexpectedError(e);
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

        lock2.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(statuses + "", statuses.containsKey("joinRoom"));
        Assert.assertTrue(statuses + "", statuses.containsKey("AliceJoin"));

        mBobSession.getDataHandler().removeListener(bobEventListener);
    }

    private void doE2ETestWithAliceAndBobAndSamInARoom() throws Exception {
        final Map<String, String> statuses = new HashMap<>();

        doE2ETestWithAliceAndBobInARoom(true);

        Room room = mAliceSession.getDataHandler().getRoom(mRoomId);

        createSamAccount();
        CountDownLatch lock0 = new CountDownLatch(1);

        mSamSession.enableCrypto(true, new TestApiCallback<Void>(lock0) {
            @Override
            public void onSuccess(Void info) {
                statuses.put("enableCrypto", "enableCrypto");
                super.onSuccess(info);
            }
        });
        lock0.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);

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

        room.invite(mSamSession.getMyUserId(), new TestApiCallback<Void>(lock1) {
            @Override
            public void onSuccess(Void info) {
                statuses.put("invite", "invite");
                super.onSuccess(info);
            }
        });

        lock1.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);

        Assert.assertTrue(statuses.containsKey("invite") && statuses.containsKey("onNewRoom"));

        mSamSession.getDataHandler().removeListener(samEventListener);

        CountDownLatch lock2 = new CountDownLatch(1);

        mSamSession.joinRoom(mRoomId, new TestApiCallback<String>(lock2) {
            @Override
            public void onSuccess(String info) {
                statuses.put("joinRoom", "joinRoom");
                super.onSuccess(info);
            }
        });

        lock2.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(statuses.containsKey("joinRoom"));

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

    private void doE2ETestWithAliceAndBobInARoomWithEncryptedMessages(boolean cryptedBob) throws Exception {
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

        final List<CountDownLatch> list = new ArrayList<>();

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

        ApiCallback<Void> callback = new SimpleApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                list.get(0).countDown();
            }
        };

        final Map<String, Object> results = new HashMap<>();

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
        lock.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertTrue(results.containsKey("onToDeviceEvent"));
        Assert.assertEquals(1, mMessagesCount);

        lock = new CountDownLatch(1);
        list.clear();
        list.add(lock);
        roomFromBobPOV.sendEvent(buildTextEvent(messagesFromBob.get(0), mBobSession), callback);
        // android does not echo the messages sent from itself
        mMessagesCount++;
        lock.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertEquals(2, mMessagesCount);

        lock = new CountDownLatch(1);
        list.clear();
        list.add(lock);
        roomFromBobPOV.sendEvent(buildTextEvent(messagesFromBob.get(1), mBobSession), callback);
        // android does not echo the messages sent from itself
        mMessagesCount++;
        lock.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertEquals(3, mMessagesCount);

        lock = new CountDownLatch(1);
        list.clear();
        list.add(lock);
        roomFromBobPOV.sendEvent(buildTextEvent(messagesFromBob.get(2), mBobSession), callback);
        // android does not echo the messages sent from itself
        mMessagesCount++;
        lock.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertEquals(4, mMessagesCount);

        lock = new CountDownLatch(2);
        list.clear();
        list.add(lock);
        roomFromAlicePOV.sendEvent(buildTextEvent(messagesFromAlice.get(1), mAliceSession), callback);
        lock.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertEquals(5, mMessagesCount);
    }

    private void checkEncryptedEvent(Event event, String roomId, String clearMessage, MXSession senderSession) {
        Assert.assertEquals(Event.EVENT_TYPE_MESSAGE_ENCRYPTED, event.getWireType());
        Assert.assertNotNull(event.getWireContent());

        JsonObject eventWireContent = event.getWireContent().getAsJsonObject();
        Assert.assertNotNull(eventWireContent);

        Assert.assertNull(eventWireContent.get("body"));
        Assert.assertEquals(MXCryptoAlgorithms.MXCRYPTO_ALGORITHM_MEGOLM, eventWireContent.get("algorithm").getAsString());

        Assert.assertNotNull(eventWireContent.get("ciphertext"));
        Assert.assertNotNull(eventWireContent.get("session_id"));
        Assert.assertNotNull(eventWireContent.get("sender_key"));

        Assert.assertEquals(senderSession.getCredentials().deviceId, eventWireContent.get("device_id").getAsString());

        Assert.assertNotNull(event.eventId);
        Assert.assertEquals(roomId, event.roomId);
        Assert.assertEquals(Event.EVENT_TYPE_MESSAGE, event.getType());
        Assert.assertTrue(event.getAge() < 10000);

        JsonObject eventContent = event.getContentAsJsonObject();
        Assert.assertNotNull(eventContent);
        Assert.assertEquals(clearMessage, eventContent.get("body").getAsString());
        Assert.assertEquals(senderSession.getMyUserId(), event.sender);
    }
}
