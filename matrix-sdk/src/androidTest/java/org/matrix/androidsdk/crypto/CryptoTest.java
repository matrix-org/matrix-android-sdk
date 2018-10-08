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


package org.matrix.androidsdk.crypto;

import android.content.Context;
import android.os.SystemClock;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.text.TextUtils;
import android.util.Pair;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.matrix.androidsdk.HomeServerConnectionConfig;
import org.matrix.androidsdk.MXDataHandler;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.common.CommonTestHelper;
import org.matrix.androidsdk.common.SessionAndRoomId;
import org.matrix.androidsdk.common.SessionTestParams;
import org.matrix.androidsdk.common.TestApiCallback;
import org.matrix.androidsdk.common.TestConstants;
import org.matrix.androidsdk.common.Triple;
import org.matrix.androidsdk.crypto.data.MXDeviceInfo;
import org.matrix.androidsdk.crypto.data.MXOlmSessionResult;
import org.matrix.androidsdk.crypto.data.MXUsersDevicesMap;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.data.store.IMXStore;
import org.matrix.androidsdk.data.store.MXFileStore;
import org.matrix.androidsdk.data.store.MXStoreListener;
import org.matrix.androidsdk.data.timeline.EventTimeline;
import org.matrix.androidsdk.data.timeline.EventTimelineFactory;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.RoomDirectoryVisibility;
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
import java.util.concurrent.CountDownLatch;

@RunWith(AndroidJUnit4.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class CryptoTest {

    private CommonTestHelper mTestHelper = new CommonTestHelper();

    private final SessionTestParams defaultSessionParams = SessionTestParams.newBuilder()
            .withInitialSync(true)
            .build();
    private final SessionTestParams encryptedSessionParams = SessionTestParams.newBuilder()
            .withInitialSync(true)
            .withCryptoEnabled(true)
            .build();

    private static final String LOG_TAG = "CryptoTest";

    private static final List<String> messagesFromAlice = Arrays.asList("0 - Hello I'm Alice!", "4 - Go!");
    private static final List<String> messagesFromBob = Arrays.asList("1 - Hello I'm Bob!", "2 - Isn't life grand?", "3 - Let's go to the opera.");

    @Test
    public void test01_testCryptoNoDeviceId() throws Exception {
        Log.e(LOG_TAG, "test01_testCryptoNoDeviceId");

        Context context = InstrumentationRegistry.getContext();
        final Map<String, Object> results = new HashMap<>();
        MXSession bobSession = mTestHelper.createAccount(TestConstants.USER_BOB, defaultSessionParams);

        Assert.assertNull(bobSession.getCrypto());
        bobSession.getCredentials().deviceId = null;

        CountDownLatch lock1 = new CountDownLatch(1);
        bobSession.enableCrypto(true, new TestApiCallback<Void>(lock1) {
            @Override
            public void onSuccess(Void info) {
                results.put("enableCrypto", "enableCrypto");
                super.onSuccess(info);
            }
        });
        mTestHelper.await(lock1);
        Assert.assertTrue(results.containsKey("enableCrypto"));

        Assert.assertNotNull(bobSession.getCrypto());
        Assert.assertNotNull(bobSession.getCredentials().deviceId);

        bobSession.clear(context);
    }

    @Test
    public void test02_testCryptoPersistenceInStore() throws Exception {
        Log.e(LOG_TAG, "test02_testCryptoPersistenceInStore");
        Context context = InstrumentationRegistry.getContext();
        final Map<String, Object> results = new HashMap<>();

        MXSession bobSession = mTestHelper.createAccount(TestConstants.USER_BOB, defaultSessionParams);
        bobSession.getCredentials().deviceId = "BobDevice";

        Assert.assertNull(bobSession.getCrypto());

        CountDownLatch lock0 = new CountDownLatch(1);
        bobSession.enableCrypto(true, new TestApiCallback<Void>(lock0) {
            @Override
            public void onSuccess(Void info) {
                results.put("enableCrypto", "enableCrypto");
                super.onSuccess(info);
            }
        });
        mTestHelper.await(lock0);
        Assert.assertTrue(results.containsKey("enableCrypto"));

        Assert.assertNotNull(bobSession.getCrypto());

        SystemClock.sleep(1000);

        final String deviceCurve25519Key = bobSession.getCrypto().getOlmDevice().getDeviceCurve25519Key();
        final String deviceEd25519Key = bobSession.getCrypto().getOlmDevice().getDeviceEd25519Key();

        final List<MXDeviceInfo> myUserDevices = bobSession.getCrypto().getUserDevices(bobSession.getMyUserId());

        Assert.assertNotNull(myUserDevices);
        Assert.assertEquals(1, myUserDevices.size());

        final Credentials bobCredentials = bobSession.getCredentials();

        HomeServerConnectionConfig hs = mTestHelper.createHomeServerConfig(bobCredentials);

        IMXStore store = new MXFileStore(hs, false, context);

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
        mTestHelper.await(lock1);

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
        mTestHelper.await(lock2);
        Assert.assertTrue(results.containsKey("onInitialSyncComplete"));
        Assert.assertTrue(results.containsKey("onCryptoSyncComplete"));

        MXCrypto crypto = bobSession2.getCrypto();
        Assert.assertNotNull(crypto);

        Assert.assertEquals(deviceCurve25519Key, crypto.getOlmDevice().getDeviceCurve25519Key());
        Assert.assertEquals(deviceEd25519Key, crypto.getOlmDevice().getDeviceEd25519Key());

        List<MXDeviceInfo> myUserDevices2 = bobSession2.getCrypto().getUserDevices(bobSession2.getMyUserId());
        Assert.assertEquals(1, myUserDevices2.size());
        Assert.assertEquals(myUserDevices2.get(0).deviceId, myUserDevices.get(0).deviceId);

        bobSession.clear(context);
        bobSession2.clear(context);
    }

    @Test
    public void test03_testKeysUploadAndDownload() throws Exception {
        Log.e(LOG_TAG, "test03_testKeysUploadAndDownload");

        Context context = InstrumentationRegistry.getContext();
        final Map<String, Object> results = new HashMap<>();

        MXSession aliceSession = mTestHelper.createAccount(TestConstants.USER_ALICE, defaultSessionParams);
        aliceSession.getCredentials().deviceId = "AliceDevice";

        CountDownLatch lock0 = new CountDownLatch(1);
        aliceSession.enableCrypto(true, new TestApiCallback<Void>(lock0) {
            @Override
            public void onSuccess(Void info) {
                results.put("enableCrypto", "enableCrypto");
                super.onSuccess(info);
            }
        });
        mTestHelper.await(lock0);
        Assert.assertTrue(results.containsKey("enableCrypto"));

        MXSession bobSession = mTestHelper.createAccount(TestConstants.USER_BOB, defaultSessionParams);
        CountDownLatch lock2 = new CountDownLatch(1);
        bobSession.getCredentials().deviceId = "BobDevice";
        bobSession.enableCrypto(true, new TestApiCallback<Void>(lock2) {
            @Override
            public void onSuccess(Void info) {
                results.put("enableCrypto2", "enableCrypto2");
                super.onSuccess(info);
            }
        });
        mTestHelper.await(lock2);
        Assert.assertTrue(results.containsKey("enableCrypto2"));

        CountDownLatch lock3 = new CountDownLatch(1);
        bobSession.getCrypto()
                .getDeviceList()
                .downloadKeys(Arrays.asList(bobSession.getMyUserId(), aliceSession.getMyUserId()),
                        false, new TestApiCallback<MXUsersDevicesMap<MXDeviceInfo>>(lock3) {
                            @Override
                            public void onSuccess(MXUsersDevicesMap<MXDeviceInfo> info) {
                                results.put("downloadKeys", info);
                                super.onSuccess(info);
                            }
                        });

        mTestHelper.await(lock3);
        Assert.assertTrue(results.containsKey("downloadKeys"));
        MXUsersDevicesMap<MXDeviceInfo> usersDevicesInfoMap = (MXUsersDevicesMap<MXDeviceInfo>) results.get("downloadKeys");

        Assert.assertEquals(2, usersDevicesInfoMap.getUserIds().size());
        Assert.assertEquals(1, usersDevicesInfoMap.getUserDeviceIds(aliceSession.getMyUserId()).size());

        MXDeviceInfo aliceDeviceFromBobPOV = usersDevicesInfoMap.getObject("AliceDevice", aliceSession.getMyUserId());
        Assert.assertNotNull(aliceDeviceFromBobPOV);
        Assert.assertEquals(aliceDeviceFromBobPOV.fingerprint(), aliceSession.getCrypto().getOlmDevice().getDeviceEd25519Key());

        // Continue testing other methods
        Assert.assertNotNull(bobSession.getCrypto().deviceWithIdentityKey(aliceSession.getCrypto().getOlmDevice().getDeviceCurve25519Key(),
                aliceSession.getMyUserId(), MXCryptoAlgorithms.MXCRYPTO_ALGORITHM_OLM));
        Assert.assertTrue(aliceDeviceFromBobPOV.isUnknown());

        CountDownLatch lock3a = new CountDownLatch(1);
        bobSession.getCrypto().setDevicesKnown(Arrays.asList(aliceDeviceFromBobPOV),
                new TestApiCallback<Void>(lock3a) {
                    @Override
                    public void onSuccess(Void info) {
                        results.put("setDevicesKnown", info);
                        super.onSuccess(info);
                    }
                }
        );
        mTestHelper.await(lock3a);
        Assert.assertTrue(results.containsKey("setDevicesKnown"));
        Assert.assertTrue(aliceDeviceFromBobPOV.isUnverified());

        CountDownLatch lock3b = new CountDownLatch(1);
        bobSession.getCrypto().setDeviceVerification(
                MXDeviceInfo.DEVICE_VERIFICATION_BLOCKED,
                aliceDeviceFromBobPOV.deviceId,
                aliceSession.getMyUserId(),
                new TestApiCallback<Void>(lock3b) {
                    @Override
                    public void onSuccess(Void info) {
                        results.put("setDeviceVerification1", info);
                        super.onSuccess(info);
                    }
                }
        );
        mTestHelper.await(lock3b);
        Assert.assertTrue(results.containsKey("setDeviceVerification1"));
        Assert.assertTrue(aliceDeviceFromBobPOV.isBlocked());

        Credentials bobCredentials = bobSession.getCredentials();

        HomeServerConnectionConfig hs = mTestHelper.createHomeServerConfig(bobCredentials);

        IMXStore store = new MXFileStore(hs, false, context);

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

        mTestHelper.await(lock4);
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
        mTestHelper.await(lock4b);
        Assert.assertTrue(results.containsKey("onInitialSyncComplete"));
        Assert.assertTrue(results.containsKey("onCryptoSyncComplete"));

        MXDeviceInfo aliceDeviceFromBobPOV2 = bobSession2.getCrypto()
                .deviceWithIdentityKey(aliceSession.getCrypto().getOlmDevice().getDeviceCurve25519Key(),
                        aliceSession.getMyUserId(), MXCryptoAlgorithms.MXCRYPTO_ALGORITHM_OLM);

        Assert.assertNotNull(aliceDeviceFromBobPOV2);
        Assert.assertEquals(aliceDeviceFromBobPOV2.fingerprint(), aliceSession.getCrypto().getOlmDevice().getDeviceEd25519Key());
        Assert.assertTrue(aliceDeviceFromBobPOV2.mVerified + " instead of " + MXDeviceInfo.DEVICE_VERIFICATION_BLOCKED,
                aliceDeviceFromBobPOV2.mVerified == MXDeviceInfo.DEVICE_VERIFICATION_BLOCKED);

        // Download again alice device
        CountDownLatch lock5 = new CountDownLatch(1);
        bobSession2.getCrypto().getDeviceList().downloadKeys(Arrays.asList(aliceSession.getMyUserId()), true,
                new TestApiCallback<MXUsersDevicesMap<MXDeviceInfo>>(lock5) {
                    @Override
                    public void onSuccess(MXUsersDevicesMap<MXDeviceInfo> info) {
                        results.put("downloadKeys2", info);
                        super.onSuccess(info);
                    }
                });
        mTestHelper.await(lock5);
        Assert.assertTrue(results.containsKey("downloadKeys2"));

        MXDeviceInfo aliceDeviceFromBobPOV3 = bobSession2.getCrypto().deviceWithIdentityKey(aliceSession.getCrypto().getOlmDevice().getDeviceCurve25519Key(),
                aliceSession.getMyUserId(), MXCryptoAlgorithms.MXCRYPTO_ALGORITHM_OLM);

        Assert.assertNotNull(aliceDeviceFromBobPOV3);
        Assert.assertEquals(aliceDeviceFromBobPOV3.fingerprint(), aliceSession.getCrypto().getOlmDevice().getDeviceEd25519Key());
        Assert.assertTrue(aliceDeviceFromBobPOV3.isBlocked());

        aliceSession.clear(context);
        bobSession.clear(context);
        bobSession2.clear(context);
    }

    @Test
    public void test04_testEnsureOlmSessionsForUsers() throws Exception {
        Log.e(LOG_TAG, "test04_testEnsureOlmSessionsForUsers");

        Context context = InstrumentationRegistry.getContext();

        MXSession aliceSession = mTestHelper.createAccount(TestConstants.USER_ALICE, defaultSessionParams);
        final Map<String, Object> results = new HashMap<>();

        aliceSession.getCredentials().deviceId = "AliceDevice";

        CountDownLatch lock0 = new CountDownLatch(1);

        aliceSession.enableCrypto(true, new TestApiCallback<Void>(lock0) {
            @Override
            public void onSuccess(Void info) {
                results.put("enableCryptoAlice", "enableCryptoAlice");
                super.onSuccess(info);
            }
        });

        mTestHelper.await(lock0);
        Assert.assertTrue(results.containsKey("enableCryptoAlice"));

        MXSession bobSession = mTestHelper.createAccount(TestConstants.USER_BOB, defaultSessionParams);

        CountDownLatch lock2 = new CountDownLatch(1);
        bobSession.enableCrypto(true, new TestApiCallback<Void>(lock2) {
            @Override
            public void onSuccess(Void info) {
                results.put("enableCryptoBob", "enableCryptoAlice");
                super.onSuccess(info);
            }
        });

        mTestHelper.await(lock2);
        Assert.assertTrue(results.containsKey("enableCryptoBob"));

        CountDownLatch lock3 = new CountDownLatch(1);
        bobSession.getCrypto().getDeviceList().downloadKeys(Arrays.asList(bobSession.getMyUserId(), aliceSession.getMyUserId()), false,
                new TestApiCallback<MXUsersDevicesMap<MXDeviceInfo>>(lock3) {
                    @Override
                    public void onSuccess(MXUsersDevicesMap<MXDeviceInfo> map) {
                        results.put("downloadKeys", map);
                        super.onSuccess(map);
                    }
                });

        mTestHelper.await(lock3);
        Assert.assertTrue(results.containsKey("downloadKeys"));

        CountDownLatch lock4 = new CountDownLatch(1);
        bobSession.getCrypto().ensureOlmSessionsForUsers(Arrays.asList(bobSession.getMyUserId(), aliceSession.getMyUserId()),
                new TestApiCallback<MXUsersDevicesMap<MXOlmSessionResult>>(lock4) {
                    @Override
                    public void onSuccess(MXUsersDevicesMap<MXOlmSessionResult> info) {
                        results.put("ensureOlmSessionsForUsers", info);
                        super.onSuccess(info);
                    }
                });

        mTestHelper.await(lock4);
        Assert.assertTrue(results.containsKey("ensureOlmSessionsForUsers"));

        MXUsersDevicesMap<MXOlmSessionResult> result = (MXUsersDevicesMap<MXOlmSessionResult>) results.get("ensureOlmSessionsForUsers");

        Assert.assertEquals(1, result.getUserIds().size());

        MXOlmSessionResult sessionWithAliceDevice = result.getObject("AliceDevice", aliceSession.getMyUserId());

        Assert.assertNotNull(sessionWithAliceDevice);
        Assert.assertNotNull(sessionWithAliceDevice.mSessionId);
        Assert.assertEquals("AliceDevice", sessionWithAliceDevice.mDevice.deviceId);

        Credentials bobCredentials = bobSession.getCredentials();

        HomeServerConnectionConfig hs = mTestHelper.createHomeServerConfig(bobCredentials);

        IMXStore store = new MXFileStore(hs, false, context);

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
        mTestHelper.await(lock5);
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

        mTestHelper.await(lock5b);
        Assert.assertTrue(results.containsKey("onInitialSyncComplete"));
        Assert.assertTrue(results.containsKey("onCryptoSyncComplete"));

        CountDownLatch lock6 = new CountDownLatch(1);
        bobSession2.getCrypto().ensureOlmSessionsForUsers(Arrays.asList(bobSession2.getMyUserId(), aliceSession.getMyUserId()),
                new TestApiCallback<MXUsersDevicesMap<MXOlmSessionResult>>(lock6) {
                    @Override
                    public void onSuccess(MXUsersDevicesMap<MXOlmSessionResult> info) {
                        results.put("ensureOlmSessionsForUsers2", info);
                        super.onSuccess(info);
                    }
                });
        mTestHelper.await(lock6);
        Assert.assertTrue(results.containsKey("ensureOlmSessionsForUsers2"));

        MXUsersDevicesMap<MXOlmSessionResult> result2 = (MXUsersDevicesMap<MXOlmSessionResult>) results.get("ensureOlmSessionsForUsers2");

        MXOlmSessionResult sessionWithAliceDevice2 = result2.getObject("AliceDevice", aliceSession.getMyUserId());
        Assert.assertNotNull(sessionWithAliceDevice2);
        Assert.assertNotNull(sessionWithAliceDevice2.mSessionId);
        Assert.assertEquals("AliceDevice", sessionWithAliceDevice2.mDevice.deviceId);

        bobSession.clear(context);
        aliceSession.clear(context);
        bobSession2.clear(context);
    }

    @Test
    public void test05_testRoomIsEncrypted() throws Exception {
        Log.e(LOG_TAG, "test05_testRoomIsEncrypted");

        Context context = InstrumentationRegistry.getContext();
        final Map<String, Object> results = new HashMap<>();

        MXSession bobSession = mTestHelper.createAccount(TestConstants.USER_BOB, defaultSessionParams);

        CountDownLatch lock0 = new CountDownLatch(1);
        bobSession.enableCrypto(true, new TestApiCallback<Void>(lock0) {
            @Override
            public void onSuccess(Void info) {
                results.put("enableCrypto", "enableCrypto");
                super.onSuccess(info);
            }
        });
        mTestHelper.await(lock0);
        Assert.assertTrue(results.containsKey("enableCrypto"));

        final String[] roomId = {null};

        CountDownLatch lock1 = new CountDownLatch(1);
        bobSession.createRoom(new TestApiCallback<String>(lock1) {
            @Override
            public void onSuccess(String info) {
                roomId[0] = info;
                super.onSuccess(info);
            }
        });

        mTestHelper.await(lock1);
        Assert.assertNotNull(roomId[0]);

        Room room = bobSession.getDataHandler().getRoom(roomId[0]);

        Assert.assertFalse(room.isEncrypted());

        CountDownLatch lock2 = new CountDownLatch(1);
        room.enableEncryptionWithAlgorithm(MXCryptoAlgorithms.MXCRYPTO_ALGORITHM_MEGOLM, new TestApiCallback<Void>(lock2) {
            @Override
            public void onSuccess(Void info) {
                results.put("enableEncryptionWithAlgorithm", "enableEncryptionWithAlgorithm");
                super.onSuccess(info);
            }
        });
        mTestHelper.await(lock2);
        Assert.assertTrue(results.containsKey("enableEncryptionWithAlgorithm"));

        Assert.assertTrue(room.isEncrypted());

        bobSession.clear(context);
    }

    @Test
    public void test06_testAliceInAEncryptedRoom() throws Exception {
        Log.e(LOG_TAG, "test06_testAliceInAEncryptedRoom");

        Context context = InstrumentationRegistry.getContext();

        SessionAndRoomId sessionAndRoomId = doE2ETestWithAliceInARoom();
        MXSession aliceSession = sessionAndRoomId.first;
        String aliceRoomId = sessionAndRoomId.second;

        final String message = "Hello myself!";

        Room roomFromAlicePOV = aliceSession.getDataHandler().getRoom(aliceRoomId);

        Assert.assertTrue(roomFromAlicePOV.isEncrypted());

        CountDownLatch lock1 = new CountDownLatch(1);

        // the IOS client echoes the message
        // the android client does not

        roomFromAlicePOV.sendEvent(buildTextEvent(message, aliceSession, aliceRoomId), new TestApiCallback<Void>(lock1));

        mTestHelper.await(lock1);

        aliceSession.clear(context);
    }

    @Test
    public void test07_testAliceAndBobInAEncryptedRoom() throws Exception {
        Log.e(LOG_TAG, "test07_testAliceAndBobInAEncryptedRoom");

        Context context = InstrumentationRegistry.getContext();
        final Map<String, Object> results = new HashMap<>();

        Pair<SessionAndRoomId, MXSession> pair = doE2ETestWithAliceAndBobInARoom(true);
        final MXSession aliceSession = pair.first.first;
        final String aliceRoomId = pair.first.second;
        final MXSession bobSession = pair.second;

        final String messageFromAlice = "Hello I'm Alice!";

        Room roomFromBobPOV = bobSession.getDataHandler().getRoom(aliceRoomId);
        Room roomFromAlicePOV = aliceSession.getDataHandler().getRoom(aliceRoomId);

        Assert.assertTrue(roomFromBobPOV.isEncrypted());
        Assert.assertTrue(roomFromAlicePOV.isEncrypted());

        CountDownLatch lock1 = new CountDownLatch(1);

        roomFromAlicePOV.sendEvent(buildTextEvent(messageFromAlice, aliceSession, aliceRoomId), new TestApiCallback<Void>(lock1) {
            @Override
            public void onMatrixError(MatrixError e) {
                results.put("sendEventError", e);
                super.onMatrixError(e);
            }
        });

        mTestHelper.await(lock1);
        Assert.assertTrue(results.containsKey("sendEventError"));
        MXCryptoError error = (MXCryptoError) results.get("sendEventError");
        Assert.assertEquals(MXCryptoError.UNKNOWN_DEVICES_CODE, error.errcode);
        MXUsersDevicesMap<MXDeviceInfo> unknownDevices = (MXUsersDevicesMap<MXDeviceInfo>) error.mExceptionData;
        List<String> deviceInfos = unknownDevices.getUserDeviceIds(bobSession.getMyUserId());
        Assert.assertEquals(1, deviceInfos.size());
        Assert.assertEquals(deviceInfos.get(0), bobSession.getCrypto().getMyDevice().deviceId);

        CountDownLatch lock2 = new CountDownLatch(1);
        aliceSession.getCrypto().setDevicesKnown(Arrays.asList(bobSession.getCrypto().getMyDevice()), new TestApiCallback<Void>(lock2) {
            @Override
            public void onSuccess(Void info) {
                results.put("setDevicesKnown", "setDevicesKnown");
                super.onSuccess(info);
            }
        });

        mTestHelper.await(lock2);
        Assert.assertTrue(results.containsKey("setDevicesKnown"));

        final CountDownLatch lock3 = new CountDownLatch(3);

        MXEventListener eventListener = new MXEventListener() {
            @Override
            public void onLiveEvent(Event event, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE)) {
                    checkEncryptedEvent(event, aliceRoomId, messageFromAlice, aliceSession);

                    results.put("onLiveEvent", "onLiveEvent");
                    lock3.countDown();
                }
            }
        };

        bobSession.getDataHandler().addListener(new MXEventListener() {
            @Override
            public void onToDeviceEvent(Event event) {
                results.put("onToDeviceEvent", event);
                lock3.countDown();
            }
        });

        roomFromBobPOV.addEventListener(eventListener);

        roomFromAlicePOV.sendEvent(buildTextEvent(messageFromAlice, aliceSession, aliceRoomId), new TestApiCallback<Void>(lock3));

        mTestHelper.await(lock3);
        Assert.assertTrue(results.containsKey("onToDeviceEvent"));
        Assert.assertTrue(results.containsKey("onLiveEvent"));

        Assert.assertEquals(MXDeviceList.TRACKING_STATUS_UP_TO_DATE, bobSession.getCrypto().getDeviceTrackingStatus(bobSession.getMyUserId()));
        Assert.assertEquals(MXDeviceList.TRACKING_STATUS_UP_TO_DATE, bobSession.getCrypto().getDeviceTrackingStatus(aliceSession.getMyUserId()));

        Assert.assertEquals(MXDeviceList.TRACKING_STATUS_UP_TO_DATE, aliceSession.getCrypto().getDeviceTrackingStatus(bobSession.getMyUserId()));
        Assert.assertEquals(MXDeviceList.TRACKING_STATUS_UP_TO_DATE, aliceSession.getCrypto().getDeviceTrackingStatus(aliceSession.getMyUserId()));

        bobSession.clear(context);
    }

    @Test
    public void test08_testAliceAndBobInAEncryptedRoom2() throws Exception {
        Log.e(LOG_TAG, "test08_testAliceAndBobInAEncryptedRoom2");

        Pair<SessionAndRoomId, MXSession> pair = doE2ETestWithAliceAndBobInARoom(true);
        final MXSession aliceSession = pair.first.first;
        final String aliceRoomId = pair.first.second;
        final MXSession bobSession = pair.second;

        bobSession.getCrypto().setWarnOnUnknownDevices(false);
        aliceSession.getCrypto().setWarnOnUnknownDevices(false);

        final Room roomFromBobPOV = bobSession.getDataHandler().getRoom(aliceRoomId);
        final Room roomFromAlicePOV = aliceSession.getDataHandler().getRoom(aliceRoomId);

        Assert.assertTrue(roomFromBobPOV.isEncrypted());
        Assert.assertTrue(roomFromAlicePOV.isEncrypted());

        final int[] nbReceivedMessagesFromAlice = {0};
        final int[] nbReceivedMessagesFromBob = {0};

        final List<CountDownLatch> list = new ArrayList<>();

        MXEventListener bobEventListener = new MXEventListener() {
            @Override
            public void onLiveEvent(Event event, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE) && !TextUtils.equals(event.getSender(), bobSession.getMyUserId())) {
                    checkEncryptedEvent(event, aliceRoomId, messagesFromAlice.get(nbReceivedMessagesFromAlice[0]), aliceSession);

                    nbReceivedMessagesFromAlice[0]++;
                    list.get(list.size() - 1).countDown();
                }
            }
        };

        MXEventListener aliceEventListener = new MXEventListener() {
            @Override
            public void onLiveEvent(Event event, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE) && !TextUtils.equals(event.getSender(), aliceSession.getMyUserId())) {
                    checkEncryptedEvent(event, aliceRoomId, messagesFromBob.get(nbReceivedMessagesFromBob[0]), bobSession);
                    nbReceivedMessagesFromBob[0]++;

                    list.get(list.size() - 1).countDown();
                }
            }
        };

        ApiCallback<Void> callback = new SimpleApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                // Ignored
            }
        };

        roomFromBobPOV.addEventListener(bobEventListener);
        roomFromAlicePOV.addEventListener(aliceEventListener);

        list.add(new CountDownLatch(2));
        final Map<String, Object> results = new HashMap<>();

        bobSession.getDataHandler().addListener(new MXEventListener() {
            @Override
            public void onToDeviceEvent(Event event) {
                results.put("onToDeviceEvent", event);
                list.get(0).countDown();
            }
        });

        roomFromAlicePOV.sendEvent(buildTextEvent(messagesFromAlice.get(nbReceivedMessagesFromAlice[0]), aliceSession, aliceRoomId), callback);
        mTestHelper.await(list.get(list.size() - 1));
        Assert.assertTrue(results.containsKey("onToDeviceEvent"));
        Assert.assertEquals(1, nbReceivedMessagesFromAlice[0]);

        list.add(new CountDownLatch(1));
        roomFromBobPOV.sendEvent(buildTextEvent(messagesFromBob.get(nbReceivedMessagesFromBob[0]), bobSession, aliceRoomId), callback);
        mTestHelper.await(list.get(list.size() - 1));
        Assert.assertEquals(1, nbReceivedMessagesFromBob[0]);

        list.add(new CountDownLatch(1));
        roomFromBobPOV.sendEvent(buildTextEvent(messagesFromBob.get(nbReceivedMessagesFromBob[0]), bobSession, aliceRoomId), callback);
        mTestHelper.await(list.get(list.size() - 1));
        Assert.assertEquals(2, nbReceivedMessagesFromBob[0]);

        list.add(new CountDownLatch(1));
        roomFromBobPOV.sendEvent(buildTextEvent(messagesFromBob.get(nbReceivedMessagesFromBob[0]), bobSession, aliceRoomId), callback);
        mTestHelper.await(list.get(list.size() - 1));
        Assert.assertEquals(3, nbReceivedMessagesFromBob[0]);

        list.add(new CountDownLatch(1));
        roomFromAlicePOV.sendEvent(buildTextEvent(messagesFromAlice.get(nbReceivedMessagesFromAlice[0]), aliceSession, aliceRoomId), callback);
        mTestHelper.await(list.get(list.size() - 1));
        Assert.assertEquals(2, nbReceivedMessagesFromAlice[0]);
    }

    @Test
    public void test09_testAliceInAEncryptedRoomAfterInitialSync() throws Exception {
        Log.e(LOG_TAG, "test09_testAliceInAEncryptedRoomAfterInitialSync");

        Context context = InstrumentationRegistry.getContext();
        final Map<String, Object> results = new HashMap<>();

        SessionAndRoomId sessionAndRoomId = doE2ETestWithAliceInARoom();
        MXSession aliceSession = sessionAndRoomId.first;
        final String aliceRoomId = sessionAndRoomId.second;

        aliceSession.getCrypto().setWarnOnUnknownDevices(false);

        final String message = "Hello myself!";

        Credentials aliceCredentials = aliceSession.getCredentials();

        aliceSession.clear(context);

        HomeServerConnectionConfig hs = mTestHelper.createHomeServerConfig(aliceCredentials);

        IMXStore store = new MXFileStore(hs, false, context);

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
        mTestHelper.await(lock1);
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
        mTestHelper.await(lock1b);
        Assert.assertTrue(results.containsKey("onInitialSyncComplete"));
        Assert.assertTrue(results.containsKey("onCryptoSyncComplete"));

        Room roomFromAlicePOV2 = aliceSession2.getDataHandler().getRoom(aliceRoomId);

        Assert.assertTrue(roomFromAlicePOV2.isEncrypted());

        final CountDownLatch lock2 = new CountDownLatch(1);

        if (false) {
            // The android client does not echo its own message
            MXEventListener aliceEventListener = new MXEventListener() {
                @Override
                public void onLiveEvent(Event event, RoomState roomState) {
                    if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE)) {
                        checkEncryptedEvent(event, aliceRoomId, message, aliceSession2);

                        lock2.countDown();
                    }
                }
            };

            roomFromAlicePOV2.addEventListener(aliceEventListener);
        }

        // the IOS client echoes the message
        // the android client does not
        roomFromAlicePOV2.sendEvent(buildTextEvent(message, aliceSession2, aliceRoomId), new TestApiCallback<Void>(lock2) {
            @Override
            public void onSuccess(Void info) {
                results.put("sendEvent", "sendEvent");
                super.onSuccess(info);
            }
        });

        mTestHelper.await(lock2);
        Assert.assertTrue(results.containsKey("sendEvent"));

        aliceSession2.clear(context);
    }

    @Test
    public void test10_testAliceDecryptOldMessageWithANewDeviceInAEncryptedRoom() throws Exception {
        Log.e(LOG_TAG, "test10_testAliceDecryptOldMessageWithANewDeviceInAEncryptedRoom");

        Context context = InstrumentationRegistry.getContext();
        final Map<String, Object> results = new HashMap<>();

        SessionAndRoomId sessionAndRoomId = doE2ETestWithAliceInARoom();
        MXSession aliceSession = sessionAndRoomId.first;
        String aliceRoomId = sessionAndRoomId.second;

        aliceSession.getCrypto().setWarnOnUnknownDevices(false);

        String message = "Hello myself!";

        Room roomFromAlicePOV = aliceSession.getDataHandler().getRoom(aliceRoomId);

        CountDownLatch lock1 = new CountDownLatch(1);
        roomFromAlicePOV.sendEvent(buildTextEvent(message, aliceSession, aliceRoomId), new TestApiCallback<Void>(lock1) {
            @Override
            public void onSuccess(Void info) {
                results.put("sendEvent", "sendEvent");
                super.onSuccess(info);
            }
        });
        mTestHelper.await(lock1);
        Assert.assertTrue(results.containsKey("sendEvent"));

        Credentials aliceCredentials = aliceSession.getCredentials();
        Credentials aliceCredentials2 = new Credentials();

        // close the session and clear the data
        aliceSession.clear(context);

        aliceCredentials2.userId = aliceCredentials.userId;
        aliceCredentials2.homeServer = aliceCredentials.homeServer;
        aliceCredentials2.accessToken = aliceCredentials.accessToken;
        aliceCredentials2.refreshToken = aliceCredentials.refreshToken;
        aliceCredentials2.deviceId = "AliceNewDevice";

        HomeServerConnectionConfig hs = mTestHelper.createHomeServerConfig(aliceCredentials2);

        IMXStore store = new MXFileStore(hs,false, context);

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
        mTestHelper.await(lock1b);
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

        mTestHelper.await(lock2);

        Assert.assertTrue(results.containsKey("onInitialSyncComplete"));
        Assert.assertTrue(results.containsKey("onCryptoSyncComplete"));

        Room roomFromAlicePOV2 = aliceSession2.getDataHandler().getRoom(aliceRoomId);

        Assert.assertNotNull(roomFromAlicePOV2);
        Assert.assertTrue(roomFromAlicePOV2.getState().isEncrypted());

        Event event = roomFromAlicePOV2.getDataHandler().getStore().getLatestEvent(aliceRoomId);
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
        final Map<String, Object> results = new HashMap<>();

        Pair<SessionAndRoomId, MXSession> pair = doE2ETestWithAliceAndBobInARoomWithEncryptedMessages(true);
        MXSession aliceSession = pair.first.first;
        final String aliceRoomId = pair.first.second;
        MXSession bobSession = pair.second;

        Credentials bobCredentials = bobSession.getCredentials();
        bobSession.clear(context);

        HomeServerConnectionConfig hs = mTestHelper.createHomeServerConfig(bobCredentials);

        IMXStore store = new MXFileStore(hs, false, context);

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

        mTestHelper.await(lock1);

        Assert.assertTrue(results.containsKey("onInitialSyncComplete"));
        Assert.assertTrue(results.containsKey("onCryptoSyncComplete"));

        Assert.assertNotNull(bobSession2.getCrypto());

        Room roomFromBobPOV = bobSession2.getDataHandler().getRoom(aliceRoomId);

        final CountDownLatch lock2 = new CountDownLatch(6);
        final List<Event> receivedEvents = new ArrayList<>();

        EventTimeline.Listener eventTimelineListener = new EventTimeline.Listener() {
            public void onEvent(Event event, EventTimeline.Direction direction, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE)) {
                    receivedEvents.add(event);
                    lock2.countDown();
                }
            }
        };

        roomFromBobPOV.getTimeline().addEventTimelineListener(eventTimelineListener);

        roomFromBobPOV.getTimeline().backPaginate(new TestApiCallback<Integer>(lock2) {
            @Override
            public void onSuccess(Integer info) {
                results.put("backPaginate", "backPaginate");
                super.onSuccess(info);
            }
        });

        mTestHelper.await(lock2);
        Assert.assertTrue(results.containsKey("backPaginate"));
        Assert.assertEquals(receivedEvents.size() + " instead of 5", 5, receivedEvents.size());

        checkEncryptedEvent(receivedEvents.get(0), aliceRoomId, messagesFromAlice.get(1), aliceSession);

        checkEncryptedEvent(receivedEvents.get(1), aliceRoomId, messagesFromBob.get(2), bobSession);

        checkEncryptedEvent(receivedEvents.get(2), aliceRoomId, messagesFromBob.get(1), bobSession);

        checkEncryptedEvent(receivedEvents.get(3), aliceRoomId, messagesFromBob.get(0), bobSession);

        checkEncryptedEvent(receivedEvents.get(4), aliceRoomId, messagesFromAlice.get(0), aliceSession);

        bobSession2.clear(context);
        aliceSession.clear(context);
    }

    @Test
    public void test12_testAliceAndBobInAEncryptedRoomBackPaginationFromHomeServer() throws Exception {
        Log.e(LOG_TAG, "test12_testAliceAndBobInAEncryptedRoomBackPaginationFromHomeServer");

        Context context = InstrumentationRegistry.getContext();
        final Map<String, Object> results = new HashMap<>();

        Pair<SessionAndRoomId, MXSession> pair = doE2ETestWithAliceAndBobInARoomWithEncryptedMessages(true);
        MXSession aliceSession = pair.first.first;
        final String aliceRoomId = pair.first.second;
        MXSession bobSession = pair.second;

        String eventId = bobSession.getDataHandler().getStore().getLatestEvent(aliceRoomId).eventId;

        EventTimeline timeline = EventTimelineFactory.pastTimeline(bobSession.getDataHandler(), aliceRoomId, eventId);

        final CountDownLatch lock2 = new CountDownLatch(6);
        final List<Event> receivedEvents = new ArrayList<>();

        EventTimeline.Listener eventTimelineListener = new EventTimeline.Listener() {
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

        mTestHelper.await(lock2);
        Assert.assertTrue(results.containsKey("backPaginate"));
        Assert.assertEquals(5, receivedEvents.size());

        checkEncryptedEvent(receivedEvents.get(0), aliceRoomId, messagesFromAlice.get(1), aliceSession);

        checkEncryptedEvent(receivedEvents.get(1), aliceRoomId, messagesFromBob.get(2), bobSession);

        checkEncryptedEvent(receivedEvents.get(2), aliceRoomId, messagesFromBob.get(1), bobSession);

        checkEncryptedEvent(receivedEvents.get(3), aliceRoomId, messagesFromBob.get(0), bobSession);

        checkEncryptedEvent(receivedEvents.get(4), aliceRoomId, messagesFromAlice.get(0), aliceSession);

        bobSession.clear(context);
        aliceSession.clear(context);
    }

    @Test
    public void test13_testAliceAndNotEncryptedBobInACryptedRoom() throws Exception {
        Log.e(LOG_TAG, "test13_testAliceAndNotEncryptedBobInACryptedRoom");

        final Map<String, Object> results = new HashMap<>();

        Pair<SessionAndRoomId, MXSession> pair = doE2ETestWithAliceAndBobInARoom(false);
        final MXSession aliceSession = pair.first.first;
        final String aliceRoomId = pair.first.second;
        final MXSession bobSession = pair.second;

        aliceSession.getCrypto().setWarnOnUnknownDevices(false);

        Room roomFromBobPOV = bobSession.getDataHandler().getRoom(aliceRoomId);
        Room roomFromAlicePOV = aliceSession.getDataHandler().getRoom(aliceRoomId);

        Assert.assertTrue(roomFromBobPOV.isEncrypted());
        Assert.assertTrue(roomFromAlicePOV.isEncrypted());

        final String messageFromAlice = "Hello I'm Alice!";

        final CountDownLatch lock1 = new CountDownLatch(1);
        MXEventListener bobEventListener = new MXEventListener() {
            @Override
            public void onLiveEvent(Event event, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE_ENCRYPTED)
                        && !TextUtils.equals(event.getSender(), bobSession.getMyUserId())) {
                    results.put("bobEcho", event);
                    lock1.countDown();
                }
            }
        };

        roomFromBobPOV.addEventListener(bobEventListener);

        roomFromAlicePOV.sendEvent(buildTextEvent(messageFromAlice, aliceSession, aliceRoomId), new SimpleApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                // Ignore
            }
        });

        mTestHelper.await(lock1);
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
                        && !TextUtils.equals(event.getSender(), aliceSession.getMyUserId())) {
                    results.put("aliceEcho", event);
                    lock2.countDown();
                }
            }
        };

        roomFromAlicePOV.addEventListener(aliceEventListener);

        roomFromBobPOV.sendEvent(buildTextEvent("Hello I'm Bob!", bobSession, aliceRoomId), new SimpleApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                // ignore
            }
        });

        mTestHelper.await(lock2);
        Assert.assertTrue(results.containsKey("aliceEcho"));

        event = (Event) results.get("aliceEcho");
        Assert.assertFalse(event.isEncrypted());
    }

    @Test
    public void test14_testCryptoDeviceBlockAndLeave() throws Exception {
        Log.e(LOG_TAG, "test14_testCryptoDeviceBlockAndLeave");

        Context context = InstrumentationRegistry.getContext();
        final Map<String, Object> results = new HashMap<>();

        Triple<SessionAndRoomId, MXSession, MXSession> triple = doE2ETestWithAliceAndBobAndSamInARoom();
        MXSession aliceSession = triple.first.first;
        final String aliceRoomId = triple.first.second;
        MXSession bobSession = triple.second;
        MXSession samSession = triple.third;

        bobSession.getCrypto().setWarnOnUnknownDevices(false);
        aliceSession.getCrypto().setWarnOnUnknownDevices(false);
        samSession.getCrypto().setWarnOnUnknownDevices(false);

        final Room roomFromBobPOV = bobSession.getDataHandler().getRoom(aliceRoomId);
        final Room roomFromAlicePOV = aliceSession.getDataHandler().getRoom(aliceRoomId);
        final Room roomFromSamPOV = samSession.getDataHandler().getRoom(aliceRoomId);

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
        roomFromBobPOV.sendEvent(buildTextEvent("msg1", bobSession, aliceRoomId), new TestApiCallback<Void>(lock0) {
            @Override
            public void onSuccess(Void info) {
                results.put("send0", "send0");
                super.onSuccess(info);
            }
        });
        mTestHelper.await(lock0);
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

        roomFromAlicePOV.sendEvent(buildTextEvent("msg1", aliceSession, aliceRoomId), new TestApiCallback<Void>(lock1) {
            @Override
            public void onSuccess(Void info) {
                results.put("send1", "send1");
                super.onSuccess(info);
            }
        });
        mTestHelper.await(lock1);
        Assert.assertTrue(results + "", results.containsKey("send1") && results.containsKey("bob1") && results.containsKey("sam1"));

        List<MXDeviceInfo> list = bobSession.getCrypto().getUserDevices(aliceSession.getMyUserId());

        Assert.assertNotNull(list);
        Assert.assertTrue(list.size() > 0);

        CountDownLatch lock1b = new CountDownLatch(1);
        bobSession.getCrypto().setDeviceVerification(MXDeviceInfo.DEVICE_VERIFICATION_BLOCKED, list.get(0).deviceId, aliceSession.getMyUserId(),
                new TestApiCallback<Void>(lock1b) {
                    @Override
                    public void onSuccess(Void info) {
                        results.put("setDeviceVerification10", "setDeviceVerification10");
                        super.onSuccess(info);
                    }
                });
        mTestHelper.await(lock1b);
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
        roomFromBobPOV.sendEvent(buildTextEvent("msg2", bobSession, aliceRoomId), new TestApiCallback<Void>(lock2) {
            @Override
            public void onSuccess(Void info) {
                results.put("send2", "send2");
                super.onSuccess(info);
            }
        });

        mTestHelper.await(lock2);
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

        mTestHelper.await(lock3);
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
        roomFromBobPOV.sendEvent(buildTextEvent("msg3", bobSession, aliceRoomId), new TestApiCallback<Void>(lock4) {
            @Override
            public void onSuccess(Void info) {
                results.put("send3", "send3");
                super.onSuccess(info);
            }
        });

        mTestHelper.await(lock4);
        Assert.assertTrue(results.containsKey("send3") && results.containsKey("alice3"));

        bobSession.clear(context);
        aliceSession.clear(context);
        samSession.clear(context);
    }

    @Test
    public void test15_testReplayAttack() throws Exception {
        Log.e(LOG_TAG, "test15_testReplayAttack");
        final Map<String, Object> results = new HashMap<>();

        Pair<SessionAndRoomId, MXSession> pair = doE2ETestWithAliceAndBobInARoom(true);
        MXSession aliceSession = pair.first.first;
        final String aliceRoomId = pair.first.second;
        final MXSession bobSession = pair.second;

        bobSession.getCrypto().setWarnOnUnknownDevices(false);
        aliceSession.getCrypto().setWarnOnUnknownDevices(false);

        String messageFromAlice = "Hello I'm Alice!";

        final Room roomFromBobPOV = bobSession.getDataHandler().getRoom(aliceRoomId);
        final Room roomFromAlicePOV = aliceSession.getDataHandler().getRoom(aliceRoomId);

        Assert.assertTrue(roomFromBobPOV.isEncrypted());
        Assert.assertTrue(roomFromAlicePOV.isEncrypted());

        final CountDownLatch lock1 = new CountDownLatch(2);
        MXEventListener bobEventListener = new MXEventListener() {
            @Override
            public void onLiveEvent(Event event, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE) && !TextUtils.equals(event.getSender(), bobSession.getMyUserId())) {
                    results.put("bobEcho", event);

                    event.setClearData(null);

                    bobSession.getDataHandler().decryptEvent(event, roomFromBobPOV.getTimeline().getTimelineId());
                    results.put("decrypted", event);

                    lock1.countDown();
                }
            }
        };

        roomFromBobPOV.addEventListener(bobEventListener);

        bobSession.getDataHandler().addListener(new MXEventListener() {
            @Override
            public void onToDeviceEvent(Event event) {
                results.put("onToDeviceEvent", event);
                lock1.countDown();
            }
        });

        roomFromAlicePOV.sendEvent(buildTextEvent(messageFromAlice, aliceSession, aliceRoomId), new SimpleApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                // Ignore
            }
        });

        mTestHelper.await(lock1);
        Assert.assertTrue(results.containsKey("onToDeviceEvent"));
        Assert.assertTrue(results.containsKey("bobEcho"));
        Assert.assertTrue(results.containsKey("decrypted"));

        Event decryptedEvent = (Event) results.get("decrypted");

        Assert.assertNull(decryptedEvent.getClearEvent());
        Assert.assertEquals(MXCryptoError.DUPLICATED_MESSAGE_INDEX_ERROR_CODE, decryptedEvent.getCryptoError().errcode);

        // Decrypting it with no replay attack mitigation must still work
        bobSession.getDataHandler().decryptEvent(decryptedEvent, null);
        checkEncryptedEvent(decryptedEvent, aliceRoomId, messageFromAlice, aliceSession);
    }

    @Test
    public void test16_testRoomKeyReshare() throws Exception {
        Log.e(LOG_TAG, "test16_testRoomKeyReshare");

        final Map<String, Object> results = new HashMap<>();

        Pair<SessionAndRoomId, MXSession> pair = doE2ETestWithAliceAndBobInARoom(true);
        MXSession aliceSession = pair.first.first;
        final String aliceRoomId = pair.first.second;
        MXSession bobSession = pair.second;

        bobSession.getCrypto().setWarnOnUnknownDevices(false);
        aliceSession.getCrypto().setWarnOnUnknownDevices(false);

        String messageFromAlice = "Hello I'm Alice!";

        final Room roomFromBobPOV = bobSession.getDataHandler().getRoom(aliceRoomId);
        final Room roomFromAlicePOV = aliceSession.getDataHandler().getRoom(aliceRoomId);

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

        bobSession.getDataHandler().addListener(bobEventListener);

        final List<Event> receivedEvents = new ArrayList<>();
        EventTimeline.Listener eventTimelineListener = new EventTimeline.Listener() {
            public void onEvent(Event event, EventTimeline.Direction direction, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE)) {
                    receivedEvents.add(event);
                    lock1.countDown();
                }
            }
        };

        roomFromBobPOV.getTimeline().addEventTimelineListener(eventTimelineListener);

        roomFromAlicePOV.sendEvent(buildTextEvent(messageFromAlice, aliceSession, aliceRoomId), new SimpleApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                // Ignore
            }
        });

        mTestHelper.await(lock1);
        Assert.assertTrue(results.containsKey("onToDeviceEvent"));
        Assert.assertEquals(1, receivedEvents.size());

        Event event = receivedEvents.get(0);
        checkEncryptedEvent(event, aliceRoomId, messageFromAlice, aliceSession);

        // Reinject a modified version of the received room_key event from Alice.
        // From Bob pov, that mimics Alice resharing her keys but with an advanced outbound group session.
        Event toDeviceEvent = (Event) results.get("onToDeviceEvent");
        String sessionId = toDeviceEvent.getContentAsJsonObject().get("session_id").getAsString();
        String newSessionKey = aliceSession.getCrypto().getOlmDevice().getSessionKey(sessionId);

        JsonObject content = toDeviceEvent.getClearEvent().getContentAsJsonObject();
        content.add("session_key", new JsonPrimitive(newSessionKey));
        bobSession.getDataHandler().onToDeviceEvent(toDeviceEvent);


        // We still must be able to decrypt the event
        // ie, the implementation must have ignored the new room key with the advanced outbound group
        // session key
        event.setClearData(null);

        bobSession.getDataHandler().decryptEvent(event, null);
        checkEncryptedEvent(event, aliceRoomId, messageFromAlice, aliceSession);
    }

    @Test
    public void test17_testLateRoomKey() throws Exception {
        Log.e(LOG_TAG, "test17_testLateRoomKey");

        final Map<String, Object> results = new HashMap<>();

        Pair<SessionAndRoomId, MXSession> pair = doE2ETestWithAliceAndBobInARoom(true);
        MXSession aliceSession = pair.first.first;
        final String aliceRoomId = pair.first.second;
        MXSession bobSession = pair.second;

        bobSession.getCrypto().setWarnOnUnknownDevices(false);
        aliceSession.getCrypto().setWarnOnUnknownDevices(false);

        String messageFromAlice = "Hello I'm Alice!";

        final Room roomFromBobPOV = bobSession.getDataHandler().getRoom(aliceRoomId);
        final Room roomFromAlicePOV = aliceSession.getDataHandler().getRoom(aliceRoomId);

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

        bobSession.getDataHandler().addListener(bobEventListener);

        final List<Event> receivedEvents = new ArrayList<>();
        EventTimeline.Listener eventTimelineListener = new EventTimeline.Listener() {
            public void onEvent(Event event, EventTimeline.Direction direction, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE)) {
                    receivedEvents.add(event);
                    lock1.countDown();
                }
            }
        };

        roomFromBobPOV.getTimeline().addEventTimelineListener(eventTimelineListener);

        roomFromAlicePOV.sendEvent(buildTextEvent(messageFromAlice, aliceSession, aliceRoomId), new SimpleApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                // Ignore
            }
        });

        mTestHelper.await(lock1);
        Assert.assertTrue(results.containsKey("onToDeviceEvent"));
        Assert.assertEquals(1, receivedEvents.size());

        Event event = receivedEvents.get(0);
        checkEncryptedEvent(event, aliceRoomId, messageFromAlice, aliceSession);

        // Reinject a modified version of the received room_key event from Alice.
        // From Bob pov, that mimics Alice resharing her keys but with an advanced outbound group session.
        Event toDeviceEvent = (Event) results.get("onToDeviceEvent");
        String sessionId = toDeviceEvent.getContentAsJsonObject().get("session_id").getAsString();
        String senderKey = toDeviceEvent.senderKey();

        // remove the session
        bobSession.getCrypto().getOlmDevice().removeInboundGroupSession(sessionId, senderKey);

        event.setClearData(null);

        // check that the message cannot be decrypted
        Assert.assertFalse(bobSession.getDataHandler().decryptEvent(event, null));
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
        bobSession.getDataHandler().onToDeviceEvent(toDeviceEvent);

        // the message should be decrypted later
        mTestHelper.await(lock2);
        Assert.assertTrue(results.containsKey("onEventDecrypted"));
        Assert.assertEquals(1, receivedEvents.size());

        checkEncryptedEvent(receivedEvents.get(0), aliceRoomId, messageFromAlice, aliceSession);
        Assert.assertNull(receivedEvents.get(0).getCryptoError());
    }

    @Test
    public void test18_testAliceAndBobWithNewDevice() throws Exception {
        Log.e(LOG_TAG, "test18_testAliceAndBobWithNewDevice");

        Context context = InstrumentationRegistry.getContext();
        final Map<String, Object> results = new HashMap<>();
        Pair<SessionAndRoomId, MXSession> pair = doE2ETestWithAliceAndBobInARoom(true);
        MXSession aliceSession = pair.first.first;
        final String aliceRoomId = pair.first.second;
        MXSession bobSession = pair.second;

        bobSession.getCrypto().setWarnOnUnknownDevices(false);
        aliceSession.getCrypto().setWarnOnUnknownDevices(false);

        final Room roomFromBobPOV = bobSession.getDataHandler().getRoom(aliceRoomId);
        final Room roomFromAlicePOV = aliceSession.getDataHandler().getRoom(aliceRoomId);

        String bobDeviceId1 = bobSession.getCredentials().deviceId;

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

        bobSession.getDataHandler().addListener(bobEventListener);

        final List<Event> receivedEvents = new ArrayList<>();
        EventTimeline.Listener eventTimelineListener = new EventTimeline.Listener() {
            public void onEvent(Event event, EventTimeline.Direction direction, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE)) {
                    receivedEvents.add(event);
                    lock1.countDown();
                }
            }
        };

        roomFromBobPOV.getTimeline().addEventTimelineListener(eventTimelineListener);

        String aliceMessage1 = "Hello I'm Alice!";

        roomFromAlicePOV.sendEvent(buildTextEvent(aliceMessage1, aliceSession, aliceRoomId), new SimpleApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                // Ignore
            }
        });

        mTestHelper.await(lock1);
        Assert.assertTrue(results.containsKey("onToDeviceEvent"));
        Assert.assertEquals(1, receivedEvents.size());

        Event event = receivedEvents.get(0);
        checkEncryptedEvent(event, aliceRoomId, aliceMessage1, aliceSession);

        // logout
        CountDownLatch lock2 = new CountDownLatch(1);
        String bobId = bobSession.getCredentials().userId;
        bobSession.logout(context, new TestApiCallback<Void>(lock2) {
            @Override
            public void onSuccess(Void info) {
                results.put("logout", "logout");
                super.onSuccess(info);
            }
        });
        mTestHelper.await(lock2);
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

        aliceSession.getDataHandler().addListener(aliceEventListener);

        // login with a new device id
        MXSession bobSession2 = mTestHelper.logIntoAccount(bobSession.getMyUserId(), encryptedSessionParams);

        String bobDeviceId2 = bobSession2.getCredentials().deviceId;
        Assert.assertNotEquals(bobDeviceId2, bobDeviceId1);

        // before sending a message, wait that the device event is received.
        mTestHelper.await(lock3);
        Assert.assertTrue(results.containsKey("onToDeviceEvent2"));

        SystemClock.sleep(1000);

        final Room roomFromBobPOV2 = bobSession2.getDataHandler().getRoom(aliceRoomId);
        Assert.assertNotNull(roomFromBobPOV2);

        final List<Event> receivedEvents4 = new ArrayList<>();
        final CountDownLatch lock4 = new CountDownLatch(1);

        EventTimeline.Listener eventTimelineListener4 = new EventTimeline.Listener() {
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

        roomFromBobPOV2.getTimeline().addEventTimelineListener(eventTimelineListener4);

        String aliceMessage2 = "Hello I'm still Alice!";
        roomFromAlicePOV.sendEvent(buildTextEvent(aliceMessage2, aliceSession, aliceRoomId), new SimpleApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                // Ignore
            }
        });

        mTestHelper.await(lock4);
        Assert.assertEquals("received event of type " + results.get("event4"), 1, receivedEvents4.size());

        event = receivedEvents4.get(0);
        checkEncryptedEvent(event, aliceRoomId, aliceMessage2, aliceSession);
    }

    @Test
    public void test19_testAliceWithNewDeviceAndBobWithNewDevice() throws Exception {
        Log.e(LOG_TAG, "test19_testAliceWithNewDeviceAndBobWithNewDevice");

        Context context = InstrumentationRegistry.getContext();
        final Map<String, Object> results = new HashMap<>();
        Pair<SessionAndRoomId, MXSession> pair = doE2ETestWithAliceAndBobInARoom(true);
        MXSession aliceSession = pair.first.first;
        final String aliceRoomId = pair.first.second;
        MXSession bobSession = pair.second;

        bobSession.getCrypto().setWarnOnUnknownDevices(false);
        aliceSession.getCrypto().setWarnOnUnknownDevices(false);

        final Room roomFromBobPOV = bobSession.getDataHandler().getRoom(aliceRoomId);
        final Room roomFromAlicePOV = aliceSession.getDataHandler().getRoom(aliceRoomId);

        String bobUserId1 = bobSession.getMyUserId();
        String aliceUserId1 = aliceSession.getMyUserId();

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

        bobSession.getDataHandler().addListener(bobEventListener);

        final List<Event> receivedEvents = new ArrayList<>();
        EventTimeline.Listener eventTimelineListener = new EventTimeline.Listener() {
            public void onEvent(Event event, EventTimeline.Direction direction, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE)) {
                    receivedEvents.add(event);
                    lock1.countDown();
                }
            }
        };

        roomFromBobPOV.getTimeline().addEventTimelineListener(eventTimelineListener);

        String aliceMessage1 = "Hello I'm Alice!";

        roomFromAlicePOV.sendEvent(buildTextEvent(aliceMessage1, aliceSession, aliceRoomId), new SimpleApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                // Ignore
            }
        });

        mTestHelper.await(lock1);
        Assert.assertTrue(results.containsKey("onToDeviceEvent"));
        Assert.assertEquals(1, receivedEvents.size());

        Event event = receivedEvents.get(0);
        checkEncryptedEvent(event, aliceRoomId, aliceMessage1, aliceSession);

        // logout
        CountDownLatch lock2 = new CountDownLatch(1);
        bobSession.logout(context, new TestApiCallback<Void>(lock2) {
            @Override
            public void onSuccess(Void info) {
                results.put("boblogout", "boblogout");
                super.onSuccess(info);
            }
        });
        mTestHelper.await(lock2);
        Assert.assertTrue(results.containsKey("boblogout"));

        CountDownLatch lock3 = new CountDownLatch(1);
        aliceSession.logout(context, new TestApiCallback<Void>(lock3) {
            @Override
            public void onSuccess(Void info) {
                results.put("alicelogout", "alicelogout");
                super.onSuccess(info);
            }
        });
        mTestHelper.await(lock3);
        Assert.assertTrue(results.containsKey("alicelogout"));

        MXSession bobSession2 = mTestHelper.logIntoAccount(bobSession.getMyUserId(), encryptedSessionParams);
        Assert.assertNotNull(bobSession2);
        bobSession2.getCrypto().setWarnOnUnknownDevices(false);

        MXSession aliceSession2 = mTestHelper.logIntoAccount(aliceSession.getMyUserId(), encryptedSessionParams);
        Assert.assertNotNull(aliceSession2);
        aliceSession2.getCrypto().setWarnOnUnknownDevices(false);

        Room roomFromBob2POV = bobSession2.getDataHandler().getRoom(aliceRoomId);
        Room roomFromAlice2POV = aliceSession2.getDataHandler().getRoom(aliceRoomId);

        Assert.assertTrue(roomFromBob2POV.isEncrypted());
        event = bobSession2.getDataHandler().getStore().getLatestEvent(aliceRoomId);
        Assert.assertNotNull(event);
        Assert.assertTrue(event.isEncrypted());
        Assert.assertNull(event.getClearEvent());
        Assert.assertNotNull(event.getCryptoError());
        Assert.assertEquals(MXCryptoError.UNKNOWN_INBOUND_SESSION_ID_ERROR_CODE, event.getCryptoError().errcode);

        final CountDownLatch lock4 = new CountDownLatch(1);
        final List<Event> receivedEvents2 = new ArrayList<>();
        EventTimeline.Listener eventTimelineListener2 = new EventTimeline.Listener() {
            public void onEvent(Event event, EventTimeline.Direction direction, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE)) {
                    receivedEvents2.add(event);
                    lock4.countDown();
                }
            }
        };
        roomFromBob2POV.getTimeline().addEventTimelineListener(eventTimelineListener2);

        String messageFromAlice2 = "Hello I'm still Alice!";
        roomFromAlice2POV.sendEvent(buildTextEvent(messageFromAlice2, aliceSession2, aliceRoomId), new SimpleApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                // Ignore
            }
        });

        mTestHelper.await(lock4);
        Assert.assertEquals(1, receivedEvents2.size());

        event = receivedEvents2.get(0);
        checkEncryptedEvent(event, aliceRoomId, messageFromAlice2, aliceSession2);
    }

    @Test
    public void test20_testAliceAndBlockedBob() throws Exception {
        Log.e(LOG_TAG, "test20_testAliceAndBlockedBob");
        final Map<String, String> results = new HashMap<>();

        Pair<SessionAndRoomId, MXSession> pair = doE2ETestWithAliceAndBobInARoom(true);
        MXSession aliceSession = pair.first.first;
        final String aliceRoomId = pair.first.second;
        MXSession bobSession = pair.second;

        bobSession.getCrypto().setWarnOnUnknownDevices(false);
        aliceSession.getCrypto().setWarnOnUnknownDevices(false);

        final Room roomFromBobPOV = bobSession.getDataHandler().getRoom(aliceRoomId);
        final Room roomFromAlicePOV = aliceSession.getDataHandler().getRoom(aliceRoomId);

        Assert.assertTrue(roomFromBobPOV.isEncrypted());
        Assert.assertTrue(roomFromAlicePOV.isEncrypted());

        final CountDownLatch lock1 = new CountDownLatch(1);

        final List<Event> receivedEvents = new ArrayList<>();
        EventTimeline.Listener eventTimelineListener = new EventTimeline.Listener() {
            public void onEvent(Event event, EventTimeline.Direction direction, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE)) {
                    receivedEvents.add(event);
                    lock1.countDown();
                }
            }
        };

        roomFromBobPOV.getTimeline().addEventTimelineListener(eventTimelineListener);

        String aliceMessage1 = "Hello I'm Alice!";

        roomFromAlicePOV.sendEvent(buildTextEvent(aliceMessage1, aliceSession, aliceRoomId), new SimpleApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                // Ignore
            }
        });

        mTestHelper.await(lock1);
        Assert.assertEquals(1, receivedEvents.size());

        Event event = receivedEvents.get(0);
        checkEncryptedEvent(event, aliceRoomId, aliceMessage1, aliceSession);

        // block the bob's device
        CountDownLatch lock1b = new CountDownLatch(1);
        aliceSession.getCrypto()
                .setDeviceVerification(MXDeviceInfo.DEVICE_VERIFICATION_BLOCKED, bobSession.getCredentials().deviceId, bobSession.getMyUserId(),
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
        EventTimeline.Listener eventTimelineListener2 = new EventTimeline.Listener() {
            public void onEvent(Event event, EventTimeline.Direction direction, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE_ENCRYPTED)) {
                    receivedEvents2.add(event);
                    lock2.countDown();
                }
            }
        };

        roomFromBobPOV.getTimeline().addEventTimelineListener(eventTimelineListener2);

        String aliceMessage2 = "Hello I'm still Alice!";

        roomFromAlicePOV.sendEvent(buildTextEvent(aliceMessage2, aliceSession, aliceRoomId), new SimpleApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                // Ignore
            }
        });

        mTestHelper.await(lock2);
        Assert.assertEquals(1, receivedEvents2.size());

        event = receivedEvents2.get(0);
        Assert.assertNull(event.getClearEvent());
        Assert.assertNotNull(event.getCryptoError());
        Assert.assertEquals(MXCryptoError.UNKNOWN_INBOUND_SESSION_ID_ERROR_CODE, event.getCryptoError().errcode);

        // unblock the bob's device
        CountDownLatch lock2b = new CountDownLatch(1);
        aliceSession.getCrypto()
                .setDeviceVerification(MXDeviceInfo.DEVICE_VERIFICATION_UNVERIFIED, bobSession.getCredentials().deviceId, bobSession.getMyUserId(),
                        new TestApiCallback<Void>(lock2b) {
                            @Override
                            public void onSuccess(Void info) {
                                results.put("setDeviceVerification40", "setDeviceVerification40");
                                super.onSuccess(info);
                            }
                        });
        mTestHelper.await(lock2b);
        Assert.assertTrue(results.containsKey("setDeviceVerification40"));
        ///
        final CountDownLatch lock3 = new CountDownLatch(1);

        final List<Event> receivedEvents3 = new ArrayList<>();
        EventTimeline.Listener eventTimelineListener3 = new EventTimeline.Listener() {
            public void onEvent(Event event, EventTimeline.Direction direction, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE)) {
                    receivedEvents3.add(event);
                    lock3.countDown();
                }
            }
        };

        roomFromBobPOV.getTimeline().addEventTimelineListener(eventTimelineListener3);

        String aliceMessage3 = "Hello I'm still Alice and you can read this!";

        roomFromAlicePOV.sendEvent(buildTextEvent(aliceMessage3, aliceSession, aliceRoomId), new SimpleApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                // Ignore
            }
        });

        mTestHelper.await(lock3);
        Assert.assertEquals(1, receivedEvents3.size());

        event = receivedEvents3.get(0);
        checkEncryptedEvent(event, aliceRoomId, aliceMessage3, aliceSession);
    }


    @Test
    public void test21_testDownloadKeysWithUnreachableHS() throws Exception {
        Log.e(LOG_TAG, "test21_testDownloadKeysWithUnreachableHS");

        final Map<String, Object> results = new HashMap<>();
        Pair<SessionAndRoomId, MXSession> pair = doE2ETestWithAliceAndBobInARoom(true);
        MXSession aliceSession = pair.first.first;
        final String aliceRoomId = pair.first.second;
        MXSession bobSession = pair.second;

        bobSession.getCrypto().setWarnOnUnknownDevices(false);
        aliceSession.getCrypto().setWarnOnUnknownDevices(false);

        final Room roomFromBobPOV = bobSession.getDataHandler().getRoom(aliceRoomId);
        final Room roomFromAlicePOV = aliceSession.getDataHandler().getRoom(aliceRoomId);

        Assert.assertTrue(roomFromBobPOV.isEncrypted());
        Assert.assertTrue(roomFromAlicePOV.isEncrypted());

        CountDownLatch lock1 = new CountDownLatch(1);
        aliceSession.getCrypto().getDeviceList()
                .downloadKeys(Arrays.asList(bobSession.getMyUserId(), "@pppppppppppp:matrix.org"), false,
                        new TestApiCallback<MXUsersDevicesMap<MXDeviceInfo>>(lock1) {
                            @Override
                            public void onSuccess(MXUsersDevicesMap<MXDeviceInfo> info) {
                                results.put("downloadKeys", info);
                                super.onSuccess(info);
                            }
                        });

        mTestHelper.await(lock1);
        Assert.assertTrue(results.containsKey("downloadKeys"));

        MXUsersDevicesMap<MXDeviceInfo> usersDevicesInfoMap = (MXUsersDevicesMap<MXDeviceInfo>) results.get("downloadKeys");

        // We can get info only get for Bob
        Assert.assertEquals(1, usersDevicesInfoMap.getMap().size());

        List<String> bobDevices = usersDevicesInfoMap.getUserDeviceIds(bobSession.getMyUserId());

        Assert.assertNotNull(bobDevices);
    }

    @Test
    public void test22_testDownloadKeysForUserWithNoDevice() throws Exception {
        Log.e(LOG_TAG, "test22_testDownloadKeysForUserWithNoDevice");

        final Map<String, Object> results = new HashMap<>();
        Pair<SessionAndRoomId, MXSession> pair = doE2ETestWithAliceAndBobInARoom(false);
        MXSession aliceSession = pair.first.first;
        final String aliceRoomId = pair.first.second;
        MXSession bobSession = pair.second;

        aliceSession.getCrypto().setWarnOnUnknownDevices(false);

        CountDownLatch lock1 = new CountDownLatch(1);
        aliceSession.getCrypto().getDeviceList().downloadKeys(Arrays.asList(bobSession.getMyUserId()), false,
                new TestApiCallback<MXUsersDevicesMap<MXDeviceInfo>>(lock1) {
                    @Override
                    public void onSuccess(MXUsersDevicesMap<MXDeviceInfo> info) {
                        results.put("downloadKeys", info);
                        super.onSuccess(info);
                    }
                });

        mTestHelper.await(lock1);
        Assert.assertTrue(results.containsKey("downloadKeys"));

        MXUsersDevicesMap<MXDeviceInfo> usersDevicesInfoMap = (MXUsersDevicesMap<MXDeviceInfo>) results.get("downloadKeys");

        // MXCrypto.downloadKeys should return @[] for Bob to distinguish him from an unknown user
        List<String> bobDevices = usersDevicesInfoMap.getUserDeviceIds(bobSession.getMyUserId());
        Assert.assertNotNull(bobDevices);
        Assert.assertTrue(bobDevices.isEmpty());

        // try again
        // it should not failed
        CountDownLatch lock2 = new CountDownLatch(1);
        aliceSession.getCrypto().getDeviceList().downloadKeys(Arrays.asList(bobSession.getMyUserId()), false,
                new TestApiCallback<MXUsersDevicesMap<MXDeviceInfo>>(lock2) {
                    @Override
                    public void onSuccess(MXUsersDevicesMap<MXDeviceInfo> info) {
                        results.put("downloadKeys2", info);
                        super.onSuccess(info);
                    }
                });

        mTestHelper.await(lock2);
        Assert.assertTrue(results.containsKey("downloadKeys2"));
    }

    @Test
    public void test23_testFirstMessageSentWhileSessionWasPaused() throws Exception {
        Log.e(LOG_TAG, "test23_testFirstMessageSentWhileSessionWasPaused");
        Context context = InstrumentationRegistry.getContext();
        final String messageFromAlice = "Hello I'm Alice!";

        final Map<String, Object> results = new HashMap<>();
        Pair<SessionAndRoomId, MXSession> pair = doE2ETestWithAliceAndBobInARoom(true);
        final MXSession aliceSession = pair.first.first;
        final String aliceRoomId = pair.first.second;
        MXSession bobSession = pair.second;

        bobSession.getCrypto().setWarnOnUnknownDevices(false);
        aliceSession.getCrypto().setWarnOnUnknownDevices(false);

        final Room roomFromBobPOV = bobSession.getDataHandler().getRoom(aliceRoomId);
        final Room roomFromAlicePOV = aliceSession.getDataHandler().getRoom(aliceRoomId);

        Assert.assertTrue(roomFromBobPOV.isEncrypted());
        Assert.assertTrue(roomFromAlicePOV.isEncrypted());

        bobSession.pauseEventStream();

        // wait that the bob session is really suspended
        SystemClock.sleep(30000);

        CountDownLatch lock0 = new CountDownLatch(1);

        roomFromAlicePOV.sendEvent(buildTextEvent(messageFromAlice, aliceSession, aliceRoomId), new TestApiCallback<Void>(lock0) {
            @Override
            public void onSuccess(Void info) {
                results.put("sendEvent", "sendEvent");
                super.onSuccess(info);
            }
        });

        mTestHelper.await(lock0);
        Assert.assertTrue(results.containsKey("sendEvent"));

        final CountDownLatch lock2 = new CountDownLatch(2);
        MXEventListener eventListener = new MXEventListener() {
            @Override
            public void onLiveEvent(Event event, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE)) {
                    checkEncryptedEvent(event, aliceRoomId, messageFromAlice, aliceSession);

                    results.put("onLiveEvent", "onLiveEvent");
                    lock2.countDown();
                }
            }
        };

        roomFromBobPOV.addEventListener(eventListener);

        bobSession.getDataHandler().addListener(new MXEventListener() {
            @Override
            public void onToDeviceEvent(Event event) {
                results.put("onToDeviceEvent", event);
                lock2.countDown();
            }
        });

        bobSession.resumeEventStream();

        mTestHelper.await(lock2);
        Assert.assertTrue(results.containsKey("onToDeviceEvent"));
        Assert.assertTrue(results.containsKey("onLiveEvent"));

        bobSession.clear(context);
        aliceSession.clear(context);
    }

    @Test
    public void test24_testExportImport() throws Exception {
        Log.e(LOG_TAG, "test24_testExportImport");
        Context context = InstrumentationRegistry.getContext();
        final Map<String, Object> results = new HashMap<>();

        SessionAndRoomId sessionAndRoomId = doE2ETestWithAliceInARoom();
        MXSession aliceSession = sessionAndRoomId.first;
        String aliceRoomId = sessionAndRoomId.second;

        aliceSession.getCrypto().setWarnOnUnknownDevices(false);

        String message = "Hello myself!";
        String password = "hello";

        Room roomFromAlicePOV = aliceSession.getDataHandler().getRoom(aliceRoomId);

        CountDownLatch lock1 = new CountDownLatch(1);
        roomFromAlicePOV.sendEvent(buildTextEvent(message, aliceSession, aliceRoomId), new TestApiCallback<Void>(lock1) {
            @Override
            public void onSuccess(Void info) {
                results.put("sendEvent", "sendEvent");
                super.onSuccess(info);
            }
        });
        mTestHelper.await(lock1);
        Assert.assertTrue(results.containsKey("sendEvent"));

        Credentials aliceCredentials = aliceSession.getCredentials();
        Credentials aliceCredentials2 = new Credentials();

        CountDownLatch lock1a = new CountDownLatch(1);
        aliceSession.getCrypto().exportRoomKeys(password, new TestApiCallback<byte[]>(lock1a) {
            @Override
            public void onSuccess(byte[] info) {
                results.put("exportRoomKeys", info);
                super.onSuccess(info);
            }
        });

        mTestHelper.await(lock1a);
        Assert.assertTrue(results.containsKey("exportRoomKeys"));

        // close the session and clear the data
        aliceSession.clear(context);

        aliceCredentials2.userId = aliceCredentials.userId;
        aliceCredentials2.homeServer = aliceCredentials.homeServer;
        aliceCredentials2.accessToken = aliceCredentials.accessToken;
        aliceCredentials2.refreshToken = aliceCredentials.refreshToken;
        aliceCredentials2.deviceId = "AliceNewDevice";

        HomeServerConnectionConfig hs = mTestHelper.createHomeServerConfig(aliceCredentials2);

        IMXStore store = new MXFileStore(hs, false, context);

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
        mTestHelper.await(lock1b);
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

        mTestHelper.await(lock2);

        Assert.assertTrue(results.containsKey("onInitialSyncComplete"));
        Assert.assertTrue(results.containsKey("onCryptoSyncComplete"));

        Room roomFromAlicePOV2 = aliceSession2.getDataHandler().getRoom(aliceRoomId);

        Assert.assertNotNull(roomFromAlicePOV2);
        Assert.assertTrue(roomFromAlicePOV2.getState().isEncrypted());

        Event event = roomFromAlicePOV2.getDataHandler().getStore().getLatestEvent(aliceRoomId);
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
        mTestHelper.await(lock3);
        Assert.assertFalse(results.containsKey("importRoomKeys"));
        Assert.assertTrue(results.containsKey("importRoomKeys_failed"));

        // check that the message cannot be decrypted
        event = roomFromAlicePOV2.getDataHandler().getStore().getLatestEvent(aliceRoomId);
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
        mTestHelper.await(lock4);
        Assert.assertTrue(results.containsKey("importRoomKeys"));

        // check that the message CAN be decrypted
        event = roomFromAlicePOV2.getDataHandler().getStore().getLatestEvent(aliceRoomId);
        Assert.assertNotNull(event);
        Assert.assertTrue(event.isEncrypted());
        Assert.assertNotNull(event.getClearEvent());
        Assert.assertNull(event.getCryptoError());
        checkEncryptedEvent(event, aliceRoomId, message, aliceSession);

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

        MXSession aliceSession = mTestHelper.createAccount(TestConstants.USER_ALICE, defaultSessionParams);
        MXSession bobSession = mTestHelper.createAccount(TestConstants.USER_BOB, defaultSessionParams);

        CountDownLatch lock_1 = new CountDownLatch(2);

        aliceSession.enableCrypto(true, new TestApiCallback<Void>(lock_1));
        bobSession.enableCrypto(true, new TestApiCallback<Void>(lock_1));

        mTestHelper.await(lock_1);
        Assert.assertNotNull(aliceSession.getCrypto());
        Assert.assertNotNull(bobSession.getCrypto());

        aliceSession.getCrypto().setWarnOnUnknownDevices(false);
        bobSession.getCrypto().setWarnOnUnknownDevices(false);

        CountDownLatch lock0 = new CountDownLatch(1);
        aliceSession.createRoom(null, null, RoomDirectoryVisibility.DIRECTORY_VISIBILITY_PUBLIC, null, null,
                new TestApiCallback<String>(lock0) {
                    @Override
                    public void onSuccess(String roomId) {
                        results.put("roomId", roomId);
                        super.onSuccess(roomId);
                    }
                });
        mTestHelper.await(lock0);
        Assert.assertTrue(results.containsKey("roomId"));
        String aliceRoomId = (String) results.get("roomId");

        Room roomFromAlicePOV = aliceSession.getDataHandler().getRoom(aliceRoomId);

        CountDownLatch lock1 = new CountDownLatch(1);
        roomFromAlicePOV.enableEncryptionWithAlgorithm(MXCryptoAlgorithms.MXCRYPTO_ALGORITHM_MEGOLM, new TestApiCallback<Void>(lock1) {
            @Override
            public void onSuccess(Void info) {
                results.put("enableEncryptionWithAlgorithm", "enableEncryptionWithAlgorithm");
                super.onSuccess(info);
            }
        });
        mTestHelper.await(lock1);
        Assert.assertTrue(results.containsKey("enableEncryptionWithAlgorithm"));

        CountDownLatch lock2 = new CountDownLatch(1);
        bobSession.joinRoom(aliceRoomId, new TestApiCallback<String>(lock2) {
            @Override
            public void onSuccess(String info) {
                results.put("joinRoom", "joinRoom");
                super.onSuccess(info);
            }
        });
        mTestHelper.await(lock2);
        Assert.assertTrue(results.containsKey("joinRoom"));

        Room roomFromBobPOV = bobSession.getDataHandler().getRoom(aliceRoomId);
        final CountDownLatch lock3 = new CountDownLatch(1);
        final List<Event> receivedEvents = new ArrayList<>();
        EventTimeline.Listener eventTimelineListener = new EventTimeline.Listener() {
            public void onEvent(Event event, EventTimeline.Direction direction, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE)) {
                    receivedEvents.add(event);
                    lock3.countDown();
                }
            }
        };

        roomFromBobPOV.getTimeline().addEventTimelineListener(eventTimelineListener);
        roomFromAlicePOV.sendEvent(buildTextEvent(messageFromAlice, aliceSession, aliceRoomId), new SimpleApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                // Ignore
            }
        });

        mTestHelper.await(lock3);
        Assert.assertEquals(1, receivedEvents.size());

        Event event = receivedEvents.get(0);
        checkEncryptedEvent(event, aliceRoomId, messageFromAlice, aliceSession);

        CountDownLatch lock4 = new CountDownLatch(1);
        roomFromBobPOV.leave(new TestApiCallback<Void>(lock4) {
            @Override
            public void onSuccess(Void info) {
                results.put("leave", "leave");
                super.onSuccess(info);
            }
        });
        mTestHelper.await(lock4);
        Assert.assertTrue(results.containsKey("leave"));

        // Make Bob come back to the room with a new device
        Credentials bobCredentials = bobSession.getCredentials();
        bobSession.clear(context);

        MXSession bobSession2 = mTestHelper.logIntoAccount(bobSession.getMyUserId(), encryptedSessionParams);
        Assert.assertNotNull(bobSession2);
        Assert.assertTrue(bobSession2.isCryptoEnabled());
        Assert.assertNotEquals(bobSession2.getCrypto().getMyDevice().deviceId, bobCredentials.deviceId);
        bobSession2.getCrypto().setWarnOnUnknownDevices(false);

        CountDownLatch lock5 = new CountDownLatch(1);
        bobSession2.joinRoom(aliceRoomId, new TestApiCallback<String>(lock5) {
            @Override
            public void onSuccess(String info) {
                results.put("joinRoom2", "joinRoom2");
                super.onSuccess(info);
            }
        });
        mTestHelper.await(lock5);
        Assert.assertTrue(results.containsKey("joinRoom2"));

        Room roomFromBobPOV2 = bobSession2.getDataHandler().getRoom(aliceRoomId);

        final CountDownLatch lock6 = new CountDownLatch(1);
        final List<Event> receivedEvents2 = new ArrayList<>();
        EventTimeline.Listener eventTimelineListener2 = new EventTimeline.Listener() {
            public void onEvent(Event event, EventTimeline.Direction direction, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE)) {
                    receivedEvents2.add(event);
                    lock6.countDown();
                }
            }
        };

        roomFromBobPOV2.getTimeline().addEventTimelineListener(eventTimelineListener2);
        roomFromAlicePOV.sendEvent(buildTextEvent(message2FromAlice, aliceSession, aliceRoomId), new SimpleApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                // Ignore
            }
        });

        mTestHelper.await(lock6);
        Assert.assertEquals(1, receivedEvents2.size());

        event = receivedEvents2.get(0);
        checkEncryptedEvent(event, aliceRoomId, message2FromAlice, aliceSession);

        bobSession2.clear(context);
        aliceSession.clear(context);
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

        Triple<SessionAndRoomId, MXSession, MXSession> triple = doE2ETestWithAliceAndBobAndSamInARoom();
        final MXSession aliceSession = triple.first.first;
        final String aliceRoomId = triple.first.second;
        MXSession bobSession = triple.second;
        MXSession samSession = triple.third;

        final String messageFromAlice = "Hello I'm Alice!";

        Room roomFromBobPOV = bobSession.getDataHandler().getRoom(aliceRoomId);
        Room roomFromAlicePOV = aliceSession.getDataHandler().getRoom(aliceRoomId);
        Room roomFromSamPOV = samSession.getDataHandler().getRoom(aliceRoomId);

        Assert.assertTrue(roomFromBobPOV.isEncrypted());
        Assert.assertTrue(roomFromAlicePOV.isEncrypted());
        Assert.assertTrue(roomFromSamPOV.isEncrypted());

        CountDownLatch lock1 = new CountDownLatch(1);

        roomFromAlicePOV.sendEvent(buildTextEvent(messageFromAlice, aliceSession, aliceRoomId), new TestApiCallback<Void>(lock1) {
            @Override
            public void onMatrixError(MatrixError e) {
                results.put("sendEventError", e);
                super.onMatrixError(e);
            }
        });

        mTestHelper.await(lock1);
        Assert.assertTrue(results.containsKey("sendEventError"));
        MXCryptoError error = (MXCryptoError) results.get("sendEventError");
        Assert.assertEquals(MXCryptoError.UNKNOWN_DEVICES_CODE, error.errcode);
        MXUsersDevicesMap<MXDeviceInfo> unknownDevices = (MXUsersDevicesMap<MXDeviceInfo>) error.mExceptionData;

        // only one bob device
        List<String> deviceInfos = unknownDevices.getUserDeviceIds(bobSession.getMyUserId());
        Assert.assertEquals(1, deviceInfos.size());
        Assert.assertTrue(deviceInfos.contains(bobSession.getCrypto().getMyDevice().deviceId));

        // only one Sam device
        deviceInfos = unknownDevices.getUserDeviceIds(samSession.getMyUserId());
        Assert.assertEquals(1, deviceInfos.size());
        Assert.assertTrue(deviceInfos.contains(samSession.getCrypto().getMyDevice().deviceId));

        CountDownLatch lock2 = new CountDownLatch(1);
        aliceSession.getCrypto().setDevicesKnown(Arrays.asList(bobSession.getCrypto().getMyDevice(), samSession.getCrypto().getMyDevice()),
                new TestApiCallback<Void>(lock2) {
                    @Override
                    public void onSuccess(Void info) {
                        results.put("setDevicesKnown", "setDevicesKnown");
                        super.onSuccess(info);
                    }
                });

        mTestHelper.await(lock2);
        Assert.assertTrue(results.containsKey("setDevicesKnown"));

        final CountDownLatch lock3 = new CountDownLatch(5);

        MXEventListener eventListenerBob1 = new MXEventListener() {
            @Override
            public void onLiveEvent(Event event, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE)) {
                    checkEncryptedEvent(event, aliceRoomId, messageFromAlice, aliceSession);

                    results.put("onLiveEventBob1", "onLiveEvent");
                    lock3.countDown();
                }
            }
        };

        MXEventListener eventListenerSam1 = new MXEventListener() {
            @Override
            public void onLiveEvent(Event event, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE)) {
                    checkEncryptedEvent(event, aliceRoomId, messageFromAlice, aliceSession);

                    results.put("onLiveEventSam1", "onLiveEvent");
                    lock3.countDown();
                }
            }
        };

        bobSession.getDataHandler().addListener(new MXEventListener() {
            @Override
            public void onToDeviceEvent(Event event) {
                results.put("onToDeviceEventBob", event);
                lock3.countDown();
            }
        });

        samSession.getDataHandler().addListener(new MXEventListener() {
            @Override
            public void onToDeviceEvent(Event event) {
                results.put("onToDeviceEventSam", event);
                lock3.countDown();
            }
        });

        roomFromBobPOV.addEventListener(eventListenerBob1);
        roomFromSamPOV.addEventListener(eventListenerSam1);

        roomFromAlicePOV.sendEvent(buildTextEvent(messageFromAlice, aliceSession, aliceRoomId), new TestApiCallback<Void>(lock3) {
            @Override
            public void onSuccess(Void info) {
                lock3.countDown();
            }
        });

        mTestHelper.await(lock3);
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
                    checkEncryptedEvent(event, aliceRoomId, activeMessage.get(0), aliceSession);

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
                    checkEncryptedEvent(event, aliceRoomId, activeMessage.get(0), aliceSession);

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
        aliceSession.getCrypto().setGlobalBlacklistUnverifiedDevices(true, new TestApiCallback<Void>(lock4) {
            @Override
            public void onSuccess(Void info) {
                results.put("setGlobalBlacklistUnverifiedDevicesTrue", "setGlobalBlacklistUnverifiedDevices");
                super.onSuccess(info);
            }
        });
        mTestHelper.await(lock4);
        Assert.assertTrue(results.containsKey("setGlobalBlacklistUnverifiedDevicesTrue"));

        // ensure that there is no received message
        results.clear();
        CountDownLatch lock5 = new CountDownLatch(3);
        activeLock.clear();
        activeLock.add(lock5);

        activeMessage.clear();
        activeMessage.add("message 1");

        roomFromAlicePOV.sendEvent(buildTextEvent(activeMessage.get(0), aliceSession, aliceRoomId), new TestApiCallback<Void>(lock5));

        mTestHelper.await(lock5);
        Assert.assertFalse(results.containsKey("eventListenerBob2"));
        Assert.assertFalse(results.containsKey("eventListenerSam2"));
        Assert.assertTrue(results.containsKey("eventListenerEncyptedBob2"));
        Assert.assertTrue(results.containsKey("eventListenerEncyptedSam2"));

        CountDownLatch lock6 = new CountDownLatch(1);
        aliceSession.getCrypto().setGlobalBlacklistUnverifiedDevices(false, new TestApiCallback<Void>(lock6) {
            @Override
            public void onSuccess(Void info) {
                results.put("setGlobalBlacklistUnverifiedDevicesfalse", "setGlobalBlacklistUnverifiedDevices");
                super.onSuccess(info);
            }
        });
        mTestHelper.await(lock6);
        Assert.assertTrue(results.containsKey("setGlobalBlacklistUnverifiedDevicesfalse"));

        // ensure that the messages are received
        results.clear();
        CountDownLatch lock7 = new CountDownLatch(3);
        activeLock.clear();
        activeLock.add(lock7);

        activeMessage.clear();
        activeMessage.add("message 2");

        roomFromAlicePOV.sendEvent(buildTextEvent(activeMessage.get(0), aliceSession, aliceRoomId), new TestApiCallback<Void>(lock7));

        mTestHelper.await(lock7);
        Assert.assertTrue(results.containsKey("eventListenerBob2"));
        Assert.assertTrue(results.containsKey("eventListenerSam2"));
        Assert.assertFalse(results.containsKey("eventListenerEncyptedBob2"));
        Assert.assertFalse(results.containsKey("eventListenerEncyptedSam2"));

        // verify the bob device
        CountDownLatch lock8 = new CountDownLatch(1);
        aliceSession.getCrypto().setDeviceVerification(MXDeviceInfo.DEVICE_VERIFICATION_VERIFIED,
                bobSession.getCrypto().getMyDevice().deviceId,
                bobSession.getMyUserId(), new TestApiCallback<Void>(lock8) {
                    @Override
                    public void onSuccess(Void info) {
                        results.put("setDeviceVerificationBob", "setDeviceVerificationBob");
                        super.onSuccess(info);
                    }
                }
        );
        mTestHelper.await(lock8);
        Assert.assertTrue(results.containsKey("setDeviceVerificationBob"));

        CountDownLatch lock9 = new CountDownLatch(1);
        aliceSession.getCrypto().setRoomBlacklistUnverifiedDevices(roomFromAlicePOV.getRoomId(), new TestApiCallback<Void>(lock9) {
            @Override
            public void onSuccess(Void info) {
                results.put("setRoomBlacklistUnverifiedDevices", "setRoomBlacklistUnverifiedDevices");
                super.onSuccess(info);
            }
        });
        mTestHelper.await(lock9);
        Assert.assertTrue(results.containsKey("setRoomBlacklistUnverifiedDevices"));

        // ensure that the messages are received
        results.clear();
        CountDownLatch lock10 = new CountDownLatch(3);
        activeLock.clear();
        activeLock.add(lock10);

        activeMessage.clear();
        activeMessage.add("message 3");

        roomFromAlicePOV.sendEvent(buildTextEvent(activeMessage.get(0), aliceSession, aliceRoomId), new TestApiCallback<Void>(lock10));

        mTestHelper.await(lock10);
        Assert.assertTrue(results.containsKey("eventListenerBob2"));
        Assert.assertFalse(results.containsKey("eventListenerSam2"));
        Assert.assertFalse(results.containsKey("eventListenerEncyptedBob2"));
        Assert.assertTrue(results.containsKey("eventListenerEncyptedSam2"));

        CountDownLatch lock11 = new CountDownLatch(1);
        aliceSession.getCrypto().setRoomUnblacklistUnverifiedDevices(roomFromAlicePOV.getRoomId(), new TestApiCallback<Void>(lock11) {
            @Override
            public void onSuccess(Void info) {
                results.put("setRoomUnblacklistUnverifiedDevices", "setRoomUnblacklistUnverifiedDevices");
                super.onSuccess(info);
            }
        });
        mTestHelper.await(lock11);
        Assert.assertTrue(results.containsKey("setRoomUnblacklistUnverifiedDevices"));

        // ensure that the messages are received
        results.clear();
        CountDownLatch lock12 = new CountDownLatch(3);
        activeLock.clear();
        activeLock.add(lock12);

        activeMessage.clear();
        activeMessage.add("message 3");

        roomFromAlicePOV.sendEvent(buildTextEvent(activeMessage.get(0), aliceSession, aliceRoomId), new TestApiCallback<Void>(lock12));

        mTestHelper.await(lock12);
        Assert.assertTrue(results.containsKey("eventListenerBob2"));
        Assert.assertTrue(results.containsKey("eventListenerSam2"));
        Assert.assertFalse(results.containsKey("eventListenerEncyptedBob2"));
        Assert.assertFalse(results.containsKey("eventListenerEncyptedSam2"));

        bobSession.clear(context);
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

        MXSession aliceSession = mTestHelper.createAccount(TestConstants.USER_ALICE, defaultSessionParams);
        MXSession bobSession = mTestHelper.createAccount(TestConstants.USER_BOB, defaultSessionParams);

        CountDownLatch lock00b = new CountDownLatch(2);
        aliceSession.enableCrypto(true, new TestApiCallback<Void>(lock00b) {
            @Override
            public void onSuccess(Void info) {
                results.put("enableCrypto1", "enableCrypto1");
                super.onSuccess(info);
            }
        });

        bobSession.enableCrypto(true, new TestApiCallback<Void>(lock00b) {
            @Override
            public void onSuccess(Void info) {
                results.put("enableCrypto2", "enableCrypto2");
                super.onSuccess(info);
            }
        });
        mTestHelper.await(lock00b);
        Assert.assertTrue(results.containsKey("enableCrypto2"));
        Assert.assertTrue(results.containsKey("enableCrypto1"));

        aliceSession.getCrypto().setWarnOnUnknownDevices(false);
        bobSession.getCrypto().setWarnOnUnknownDevices(false);

        CountDownLatch lock0 = new CountDownLatch(1);
        aliceSession.createRoom(null, null, RoomDirectoryVisibility.DIRECTORY_VISIBILITY_PUBLIC, null, null,
                new TestApiCallback<String>(lock0) {
                    @Override
                    public void onSuccess(String roomId) {
                        results.put("roomId", roomId);
                        super.onSuccess(roomId);
                    }
                });
        mTestHelper.await(lock0);
        Assert.assertTrue(results.containsKey("roomId"));
        String aliceRoomId = (String) results.get("roomId");

        CountDownLatch lock1 = new CountDownLatch(1);
        bobSession.joinRoom(aliceRoomId, new TestApiCallback<String>(lock1) {
            @Override
            public void onSuccess(String info) {
                results.put("joinRoom", "joinRoom");
                super.onSuccess(info);
            }
        });
        mTestHelper.await(lock1);
        Assert.assertTrue(results.containsKey("joinRoom"));

        Room roomFromAlicePOV = aliceSession.getDataHandler().getRoom(aliceRoomId);

        CountDownLatch lock2 = new CountDownLatch(1);
        roomFromAlicePOV.sendEvent(buildTextEvent(messageFromAlice, aliceSession, aliceRoomId), new TestApiCallback<Void>(lock2) {
            @Override
            public void onSuccess(Void info) {
                results.put("sendEvent1", "sendEvent1");
                super.onSuccess(info);
            }
        });
        mTestHelper.await(lock2);
        Assert.assertTrue(results.containsKey("sendEvent1"));

        // Make Bob come back to the room with a new device
        Credentials bobCredentials = bobSession.getCredentials();
        bobSession.clear(context);

        MXSession bobSession2 = mTestHelper.logIntoAccount(bobSession.getMyUserId(), encryptedSessionParams);
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
        mTestHelper.await(lock3);
        Assert.assertTrue(results.containsKey("enableEncryptionWithAlgorithm"));

        Room roomFromBobPOV2 = bobSession2.getDataHandler().getRoom(aliceRoomId);

        final CountDownLatch lock4 = new CountDownLatch(1);
        final List<Event> receivedEvents2 = new ArrayList<>();
        EventTimeline.Listener eventTimelineListener2 = new EventTimeline.Listener() {
            public void onEvent(Event event, EventTimeline.Direction direction, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE)) {
                    receivedEvents2.add(event);
                    lock4.countDown();
                } else if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE_ENCRYPTED)) {
                    lock4.countDown();
                }
            }
        };

        roomFromBobPOV2.getTimeline().addEventTimelineListener(eventTimelineListener2);
        roomFromAlicePOV.sendEvent(buildTextEvent(message2FromAlice, aliceSession, aliceRoomId), new SimpleApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                // Ignore
            }
        });

        mTestHelper.await(lock4);
        Assert.assertEquals(1, receivedEvents2.size());

        Event event = receivedEvents2.get(0);
        checkEncryptedEvent(event, aliceRoomId, message2FromAlice, aliceSession);

        bobSession2.clear(context);
        aliceSession.clear(context);
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
        final Map<String, Object> results = new HashMap<>();

        Pair<SessionAndRoomId, MXSession> pair = doE2ETestWithAliceAndBobInARoomWithEncryptedMessages(true);
        MXSession aliceSession = pair.first.first;
        final String aliceRoomId = pair.first.second;
        final MXSession bobSession = pair.second;

        // - Bob leaves the room, so stops getting updates
        CountDownLatch lock1 = new CountDownLatch(1);

        final Room bobLeftRoom = bobSession.getDataHandler().getRoom(aliceRoomId);
        bobLeftRoom.leave(new TestApiCallback<Void>(lock1) {
            @Override
            public void onSuccess(Void info) {
                results.put("lock1", "lock1");
                super.onSuccess(info);
            }
        });

        mTestHelper.await(lock1);
        Assert.assertTrue(results.containsKey("lock1"));

        // - Alice adds a new device
        final MXSession aliceSession2 = mTestHelper.logIntoAccount(aliceSession.getMyUserId(), encryptedSessionParams);
        Assert.assertNotNull(aliceSession2);

        // - Alice and Bob start sharing a room again
        final String[] aliceRoomId2 = {null};

        CountDownLatch lock3 = new CountDownLatch(1);
        aliceSession2.createRoom(null, null, RoomDirectoryVisibility.DIRECTORY_VISIBILITY_PUBLIC, null, null,
                new TestApiCallback<String>(lock3) {
                    @Override
                    public void onSuccess(String info) {
                        aliceRoomId2[0] = info;
                        super.onSuccess(info);
                    }
                });
        mTestHelper.await(lock3);
        Assert.assertNotNull(aliceRoomId2[0]);

        Room roomFromAlicePOV = aliceSession2.getDataHandler().getRoom(aliceRoomId2[0]);
        CountDownLatch lock4 = new CountDownLatch(1);
        roomFromAlicePOV.enableEncryptionWithAlgorithm(MXCryptoAlgorithms.MXCRYPTO_ALGORITHM_MEGOLM, new TestApiCallback<Void>(lock4) {
            @Override
            public void onSuccess(Void info) {
                results.put("lock4", "lock4");
                super.onSuccess(info);
            }
        });
        mTestHelper.await(lock4);
        Assert.assertTrue(results.containsKey("lock4"));

        CountDownLatch lock5 = new CountDownLatch(1);
        bobSession.joinRoom(aliceRoomId2[0], new TestApiCallback<String>(lock5) {
            @Override
            public void onSuccess(String info) {
                results.put("lock5", "lock5");
                super.onSuccess(info);
            }
        });
        mTestHelper.await(lock5);
        Assert.assertTrue(results.containsKey("lock5"));

        // - Bob has an out of date list of Alice's devices
        Room roomFromBobPOV = bobSession.getDataHandler().getRoom(aliceRoomId2[0]);
        final String messageFromBob = "Hello Alice with new device!";
        final CountDownLatch lock6 = new CountDownLatch(2);
        MXEventListener eventListener = new MXEventListener() {
            @Override
            public void onLiveEvent(Event event, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE)) {
                    checkEncryptedEvent(event, aliceRoomId2[0], messageFromBob, bobSession);

                    results.put("lock6", "lock6");
                    lock6.countDown();
                }
            }
        };

        roomFromAlicePOV.addEventListener(eventListener);

        roomFromBobPOV.sendEvent(buildTextEvent(messageFromBob, bobSession, aliceRoomId2[0]), new TestApiCallback<Void>(lock6));

        mTestHelper.await(lock6);
        Assert.assertTrue(results.containsKey("lock6"));

        bobSession.clear(context);
        aliceSession.clear(context);
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

        Pair<SessionAndRoomId, MXSession> pair = doE2ETestWithAliceAndBobInARoom(true);
        final MXSession aliceSession = pair.first.first;
        final String aliceRoomId = pair.first.second;
        final MXSession bobSession = pair.second;

        bobSession.getCrypto().setWarnOnUnknownDevices(false);
        aliceSession.getCrypto().setWarnOnUnknownDevices(false);

        final Room roomFromBobPOV = bobSession.getDataHandler().getRoom(aliceRoomId);
        final Room roomFromAlicePOV = aliceSession.getDataHandler().getRoom(aliceRoomId);

        Assert.assertTrue(roomFromBobPOV.isEncrypted());
        Assert.assertTrue(roomFromAlicePOV.isEncrypted());

        final List<Event> bobReceivedEvents = new ArrayList<>();
        final List<Event> aliceReceivedEvents = new ArrayList<>();

        final int[] nbReceivedMessagesFromAlice = {0};
        final int[] nbReceivedMessagesFromBob = {0};

        final List<CountDownLatch> list = new ArrayList<>();

        MXEventListener bobEventListener = new MXEventListener() {
            @Override
            public void onLiveEvent(Event event, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE) && !TextUtils.equals(event.getSender(), bobSession.getMyUserId())) {
                    bobReceivedEvents.add(event);

                    checkEncryptedEvent(event, aliceRoomId, messagesFromAlice.get(nbReceivedMessagesFromAlice[0]), aliceSession);

                    nbReceivedMessagesFromAlice[0]++;
                    list.get(list.size() - 1).countDown();
                }
            }
        };

        MXEventListener aliceEventListener = new MXEventListener() {
            @Override
            public void onLiveEvent(Event event, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE) && !TextUtils.equals(event.getSender(), aliceSession.getMyUserId())) {
                    aliceReceivedEvents.add(event);

                    try {
                        // "In reply to" format for body
                        String expectedMessage = "> <" + aliceSession.getMyUserId() + "> "
                                + messagesFromAlice.get(nbReceivedMessagesFromAlice[0] - 1)
                                + "\n\n"
                                + messagesFromBob.get(nbReceivedMessagesFromBob[0]);


                        checkEncryptedEvent(event, aliceRoomId, expectedMessage, bobSession);

                        nbReceivedMessagesFromBob[0]++;

                        list.get(list.size() - 1).countDown();
                    } catch (Exception e) {

                    }
                }
            }
        };

        ApiCallback<Void> callback = new SimpleApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                // Ignore
            }
        };

        roomFromBobPOV.addEventListener(bobEventListener);
        roomFromAlicePOV.addEventListener(aliceEventListener);

        list.add(new CountDownLatch(2));
        final Map<String, Object> results = new HashMap<>();

        bobSession.getDataHandler().addListener(new MXEventListener() {
            @Override
            public void onToDeviceEvent(Event event) {
                results.put("onToDeviceEvent", event);
                list.get(0).countDown();
            }
        });

        // Alice sends a first event
        roomFromAlicePOV.sendEvent(buildTextEvent(messagesFromAlice.get(nbReceivedMessagesFromAlice[0]), aliceSession, aliceRoomId), callback);
        mTestHelper.await(list.get(list.size() - 1));
        Assert.assertTrue(results.containsKey("onToDeviceEvent"));
        Assert.assertEquals(1, nbReceivedMessagesFromAlice[0]);

        // Bob reply to Alice event
        Assert.assertTrue(roomFromBobPOV.canReplyTo(bobReceivedEvents.get(0)));

        list.add(new CountDownLatch(1));
        roomFromBobPOV.sendTextMessage(messagesFromBob.get(nbReceivedMessagesFromBob[0]), null, Message.MSGTYPE_TEXT, bobReceivedEvents.get(0), null);
        mTestHelper.await(list.get(list.size() - 1));
        Assert.assertEquals(1, nbReceivedMessagesFromBob[0]);

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


    /**
     * @return alice session
     * @throws Exception
     */
    private SessionAndRoomId doE2ETestWithAliceInARoom() throws Exception {
        final Map<String, Object> results = new HashMap<>();
        MXSession aliceSession = mTestHelper.createAccount(TestConstants.USER_ALICE, defaultSessionParams);
        CountDownLatch lock0 = new CountDownLatch(1);

        aliceSession.enableCrypto(true, new TestApiCallback<Void>(lock0) {
            @Override
            public void onSuccess(Void info) {
                results.put("enableCrypto", "enableCrypto");
                super.onSuccess(info);
            }
        });
        mTestHelper.await(lock0);
        Assert.assertTrue(results.containsKey("enableCrypto"));

        final String[] roomId = {null};
        CountDownLatch lock1 = new CountDownLatch(1);

        aliceSession.createRoom(new TestApiCallback<String>(lock1) {
            @Override
            public void onSuccess(String createdRoomId) {
                roomId[0] = createdRoomId;
                super.onSuccess(createdRoomId);
            }
        });

        mTestHelper.await(lock1);
        Assert.assertNotNull(roomId[0]);

        Room room = aliceSession.getDataHandler().getRoom(roomId[0]);

        CountDownLatch lock2 = new CountDownLatch(1);
        room.enableEncryptionWithAlgorithm(MXCryptoAlgorithms.MXCRYPTO_ALGORITHM_MEGOLM, new TestApiCallback<Void>(lock2) {
            @Override
            public void onSuccess(Void info) {
                results.put("enableEncryptionWithAlgorithm", "enableEncryptionWithAlgorithm");
                super.onSuccess(info);
            }
        });
        mTestHelper.await(lock2);
        Assert.assertTrue(results.containsKey("enableEncryptionWithAlgorithm"));

        return new SessionAndRoomId(aliceSession, roomId[0]);
    }

    /**
     * @param cryptedBob
     * @return alice and bob sessions
     * @throws Exception
     */
    private Pair<SessionAndRoomId, MXSession> doE2ETestWithAliceAndBobInARoom(boolean cryptedBob) throws Exception {
        final Map<String, String> statuses = new HashMap<>();

        SessionAndRoomId sessionAndRoomId = doE2ETestWithAliceInARoom();
        MXSession aliceSession = sessionAndRoomId.first;
        final String aliceRoomId = sessionAndRoomId.second;

        Room room = aliceSession.getDataHandler().getRoom(aliceRoomId);

        MXSession bobSession = mTestHelper.createAccount(TestConstants.USER_BOB, defaultSessionParams);
        CountDownLatch lock0 = new CountDownLatch(1);

        bobSession.enableCrypto(cryptedBob, new TestApiCallback<Void>(lock0) {
            @Override
            public void onSuccess(Void info) {
                statuses.put("enableCrypto", "enableCrypto");
                super.onSuccess(info);
            }
        });
        mTestHelper.await(lock0);

        final CountDownLatch lock1 = new CountDownLatch(2);

        MXEventListener bobEventListener = new MXEventListener() {
            @Override
            public void onNewRoom(String roomId) {
                if (TextUtils.equals(roomId, aliceRoomId)) {
                    if (!statuses.containsKey("onNewRoom")) {
                        statuses.put("onNewRoom", "onNewRoom");
                        lock1.countDown();
                    }
                }
            }
        };

        bobSession.getDataHandler().addListener(bobEventListener);

        room.invite(bobSession.getMyUserId(), new TestApiCallback<Void>(lock1) {
            @Override
            public void onSuccess(Void info) {
                statuses.put("invite", "invite");
                super.onSuccess(info);
            }
        });

        mTestHelper.await(lock1);

        Assert.assertTrue(statuses.containsKey("invite") && statuses.containsKey("onNewRoom"));

        bobSession.getDataHandler().removeListener(bobEventListener);

        final CountDownLatch lock2 = new CountDownLatch(2);

        bobSession.joinRoom(aliceRoomId, new TestApiCallback<String>(lock2) {
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

        mTestHelper.await(lock2);
        Assert.assertTrue(statuses + "", statuses.containsKey("joinRoom"));
        Assert.assertTrue(statuses + "", statuses.containsKey("AliceJoin"));

        bobSession.getDataHandler().removeListener(bobEventListener);

        return new Pair<>(sessionAndRoomId, bobSession);
    }

    /**
     * @return Alice, Bbob and sam session
     * @throws Exception
     */
    private Triple<SessionAndRoomId, MXSession, MXSession> doE2ETestWithAliceAndBobAndSamInARoom() throws Exception {
        final Map<String, String> statuses = new HashMap<>();

        Pair<SessionAndRoomId, MXSession> pair = doE2ETestWithAliceAndBobInARoom(true);
        MXSession aliceSession = pair.first.first;
        final String aliceRoomId = pair.first.second;

        Room room = aliceSession.getDataHandler().getRoom(aliceRoomId);

        MXSession samSession = mTestHelper.createAccount(TestConstants.USER_SAM, defaultSessionParams);
        CountDownLatch lock0 = new CountDownLatch(1);

        samSession.enableCrypto(true, new TestApiCallback<Void>(lock0) {
            @Override
            public void onSuccess(Void info) {
                statuses.put("enableCrypto", "enableCrypto");
                super.onSuccess(info);
            }
        });
        mTestHelper.await(lock0);

        final CountDownLatch lock1 = new CountDownLatch(2);

        MXEventListener samEventListener = new MXEventListener() {
            @Override
            public void onNewRoom(String roomId) {
                if (TextUtils.equals(roomId, aliceRoomId)) {
                    if (!statuses.containsKey("onNewRoom")) {
                        statuses.put("onNewRoom", "onNewRoom");
                        lock1.countDown();
                    }
                }
            }
        };

        samSession.getDataHandler().addListener(samEventListener);

        room.invite(samSession.getMyUserId(), new TestApiCallback<Void>(lock1) {
            @Override
            public void onSuccess(Void info) {
                statuses.put("invite", "invite");
                super.onSuccess(info);
            }
        });

        mTestHelper.await(lock1);

        Assert.assertTrue(statuses.containsKey("invite") && statuses.containsKey("onNewRoom"));

        samSession.getDataHandler().removeListener(samEventListener);

        CountDownLatch lock2 = new CountDownLatch(1);

        samSession.joinRoom(aliceRoomId, new TestApiCallback<String>(lock2) {
            @Override
            public void onSuccess(String info) {
                statuses.put("joinRoom", "joinRoom");
                super.onSuccess(info);
            }
        });

        mTestHelper.await(lock2);
        Assert.assertTrue(statuses.containsKey("joinRoom"));

        // wait the initial sync
        SystemClock.sleep(1000);

        samSession.getDataHandler().removeListener(samEventListener);

        return new Triple<>(pair, samSession);
    }

    private Event buildTextEvent(String text, MXSession session, String roomId) {
        Message message = new Message();
        message.msgtype = Message.MSGTYPE_TEXT;
        message.body = text;

        return new Event(message, session.getCredentials().userId, roomId);
    }

    /**
     * @param cryptedBob
     * @return Alice and Bob sessions
     * @throws Exception
     */
    private Pair<SessionAndRoomId, MXSession> doE2ETestWithAliceAndBobInARoomWithEncryptedMessages(boolean cryptedBob) throws Exception {
        Pair<SessionAndRoomId, MXSession> pair = doE2ETestWithAliceAndBobInARoom(cryptedBob);
        final MXSession aliceSession = pair.first.first;
        final String aliceRoomId = pair.first.second;
        final MXSession bobSession = pair.second;

        if (null != bobSession.getCrypto()) {
            bobSession.getCrypto().setWarnOnUnknownDevices(false);
        }

        if (null != aliceSession.getCrypto()) {
            aliceSession.getCrypto().setWarnOnUnknownDevices(false);
        }

        final Room roomFromBobPOV = bobSession.getDataHandler().getRoom(aliceRoomId);
        final Room roomFromAlicePOV = aliceSession.getDataHandler().getRoom(aliceRoomId);

        final int[] messagesCount = {0};

        final List<CountDownLatch> list = new ArrayList<>();

        MXEventListener bobEventsListener = new MXEventListener() {
            @Override
            public void onLiveEvent(Event event, RoomState roomState) {
                if (TextUtils.equals(event.getType(), Event.EVENT_TYPE_MESSAGE) && !TextUtils.equals(event.getSender(), bobSession.getMyUserId())) {
                    messagesCount[0]++;
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

        bobSession.getDataHandler().addListener(new MXEventListener() {
            @Override
            public void onToDeviceEvent(Event event) {
                results.put("onToDeviceEvent", event);
                list.get(0).countDown();
            }
        });

        roomFromAlicePOV.sendEvent(buildTextEvent(messagesFromAlice.get(0), aliceSession, aliceRoomId), callback);
        mTestHelper.await(lock);
        Assert.assertTrue(results.containsKey("onToDeviceEvent"));
        Assert.assertEquals(1, messagesCount[0]);

        lock = new CountDownLatch(1);
        list.clear();
        list.add(lock);
        roomFromBobPOV.sendEvent(buildTextEvent(messagesFromBob.get(0), bobSession, aliceRoomId), callback);
        // android does not echo the messages sent from itself
        messagesCount[0]++;
        mTestHelper.await(lock);
        Assert.assertEquals(2, messagesCount[0]);

        lock = new CountDownLatch(1);
        list.clear();
        list.add(lock);
        roomFromBobPOV.sendEvent(buildTextEvent(messagesFromBob.get(1), bobSession, aliceRoomId), callback);
        // android does not echo the messages sent from itself
        messagesCount[0]++;
        mTestHelper.await(lock);
        Assert.assertEquals(3, messagesCount[0]);

        lock = new CountDownLatch(1);
        list.clear();
        list.add(lock);
        roomFromBobPOV.sendEvent(buildTextEvent(messagesFromBob.get(2), bobSession, aliceRoomId), callback);
        // android does not echo the messages sent from itself
        messagesCount[0]++;
        mTestHelper.await(lock);
        Assert.assertEquals(4, messagesCount[0]);

        lock = new CountDownLatch(2);
        list.clear();
        list.add(lock);
        roomFromAlicePOV.sendEvent(buildTextEvent(messagesFromAlice.get(1), aliceSession, aliceRoomId), callback);
        mTestHelper.await(lock);
        Assert.assertEquals(5, messagesCount[0]);

        return pair;
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
