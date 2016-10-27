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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.matrix.androidsdk.crypto.MXCrypto;
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

    @Test
    public void test01_testCryptoNoDeviceId() throws Exception {
        Context context = InstrumentationRegistry.getContext();
        createBobAccount();

        assertTrue (null == mBobSession.getCrypto());
        mBobSession.getCredentials().deviceId = null;

        mBobSession.setCryptoEnabled(true);
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
        mBobSession.setCryptoEnabled(true);

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


        final CountDownLatch lock1 = new CountDownLatch(1);;
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
        bobSession2.startEventStream(null);

        lock1.await(10000, TimeUnit.DAYS.MILLISECONDS);

        assertTrue (results.containsKey("onStoreReady"));

        assertTrue (bobSession2.isCryptoEnabled());

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
        mAliceSession.setCryptoEnabled(true);

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
        lock1.await(10000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue(results.containsKey("uploadKeys"));

        createBobAccount();
        mBobSession.getCredentials().deviceId = "BobDevice";
        mBobSession.setCryptoEnabled(true);
        SystemClock.sleep(1000);


        final CountDownLatch lock2 = new CountDownLatch(1);
        mBobSession.getCrypto().downloadKeys(Arrays.asList(mBobSession.getMyUserId(), mAliceSession.getMyUserId()), false, new ApiCallback<MXUsersDevicesMap<MXDeviceInfo>>() {
            @Override
            public void onSuccess(MXUsersDevicesMap<MXDeviceInfo> info) {
                results.put("downloadKeys", info);
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

        lock2.await(10000, TimeUnit.DAYS.MILLISECONDS);
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

        final CountDownLatch lock3 = new CountDownLatch(1);

        IMXStore.MXStoreListener listener = new  IMXStore.MXStoreListener() {
            @Override
            public void postProcess(String accountId) {
            }

            @Override
            public void onStoreReady(String accountId) {
                results.put("onStoreReady", "onStoreReady");
                lock3.countDown();
            }

            @Override
            public void onStoreCorrupted(String accountId, String description) {
                lock3.countDown();
            }

            @Override
            public void  onStoreOOM(String accountId, String description) {
                lock3.countDown();
            }
        };

        bobSession2.getDataHandler().getStore().addMXStoreListener(listener);

        bobSession2.getDataHandler().getStore().open();
        bobSession2.startEventStream(null);

        lock3.await(10000, TimeUnit.DAYS.MILLISECONDS);

        assertTrue(results.containsKey("onStoreReady"));

        MXDeviceInfo aliceDeviceFromBobPOV2 = bobSession2.getCrypto().deviceWithIdentityKey(mAliceSession.getCrypto().getOlmDevice().getDeviceCurve25519Key(), mAliceSession.getMyUserId(), MXCryptoAlgorithms.MXCRYPTO_ALGORITHM_OLM);

        assertTrue (null != aliceDeviceFromBobPOV2);
        assertTrue (TextUtils.equals(aliceDeviceFromBobPOV2.fingerprint(), mAliceSession.getCrypto().getOlmDevice().getDeviceEd25519Key()));
        assertTrue (aliceDeviceFromBobPOV2.mVerified == MXDeviceInfo.DEVICE_VERIFICATION_BLOCKED);

        // Download again alice device
        final CountDownLatch lock4 = new CountDownLatch(1);
        bobSession2.getCrypto().downloadKeys(Arrays.asList(mAliceSession.getMyUserId()), true, new ApiCallback<MXUsersDevicesMap<MXDeviceInfo>>() {
            @Override
            public void onSuccess(MXUsersDevicesMap<MXDeviceInfo> info) {
                results.put("downloadKeys2", info);
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
        lock4.await(100000, TimeUnit.DAYS.MILLISECONDS);
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
        mAliceSession.setCryptoEnabled(true);
        SystemClock.sleep(1000);

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
        lock1.await(10000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue(results.containsKey("uploadKeys"));

        createBobAccount();
        mBobSession.setCryptoEnabled(true);

        // wait that uploadKeys is done
        SystemClock.sleep(1000);

        final CountDownLatch lock2 = new CountDownLatch(1);
        mBobSession.getCrypto().downloadKeys(Arrays.asList(mBobSession.getMyUserId(), mAliceSession.getMyUserId()), false, new ApiCallback<MXUsersDevicesMap<MXDeviceInfo>>() {
            @Override
            public void onSuccess(MXUsersDevicesMap<MXDeviceInfo> map) {
                results.put("downloadKeys", map);
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

        lock2.await(10000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue(results.containsKey("downloadKeys"));

        final CountDownLatch lock3 = new CountDownLatch(1);
        mBobSession.getCrypto().ensureOlmSessionsForUsers(Arrays.asList(mBobSession.getMyUserId(), mAliceSession.getMyUserId()), new ApiCallback<MXUsersDevicesMap<MXOlmSessionResult>>() {
            @Override
            public void onSuccess(MXUsersDevicesMap<MXOlmSessionResult> info) {
                results.put("ensureOlmSessionsForUsers", info);
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

        lock3.await(10000, TimeUnit.DAYS.MILLISECONDS);
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
        bobSession2.startEventStream(null);


        bobSession2.getDataHandler().addListener(new MXEventListener() {
            @Override
            public void onStoreReady() {
                lock4.countDown();
            }
        });
        lock4.await(10000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue(results.containsKey("onStoreReady"));

        final CountDownLatch lock5 = new CountDownLatch(1);
        bobSession2.getCrypto().ensureOlmSessionsForUsers(Arrays.asList(bobSession2.getMyUserId(), mAliceSession.getMyUserId()), new ApiCallback<MXUsersDevicesMap<MXOlmSessionResult>>() {
            @Override
            public void onSuccess(MXUsersDevicesMap<MXOlmSessionResult> info) {
                results.put("ensureOlmSessionsForUsers2", info);
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
        lock5.await(10000, TimeUnit.DAYS.MILLISECONDS);
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
        mBobSession.setCryptoEnabled(true);
        mRoomId = null;

        final CountDownLatch lock = new CountDownLatch(1);
        mBobSession.createRoom(new ApiCallback<String>() {
            @Override
            public void onSuccess(String info) {
                mRoomId = info;
                lock.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {
                lock.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                lock.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                lock.countDown();
            }
        });

        lock.await(60000, TimeUnit.DAYS.MILLISECONDS);
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
        lock2.await(1000000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue (results.containsKey("enableEncryptionWithAlgorithm"));

        assertTrue (room.isEncrypted());

        mBobSession.clear(context);
    }

    @Test
    public void test06_testAliceInACryptedRoom() throws Exception {
        Context context = InstrumentationRegistry.getContext();
        final HashMap<String, Object> results = new HashMap<>();

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

        lock1.await(10000, TimeUnit.DAYS.MILLISECONDS);
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
        list.get(list.size()-1).await(10000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue(1 == mReceivedMessagesFromAlice);

        list.add(new CountDownLatch(1));
        roomFromBobPOV.sendEvent(buildTextEvent(messagesFromBob.get(mReceivedMessagesFromBob), mBobSession), callback);
        list.get(list.size()-1).await(10000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue(1 == mReceivedMessagesFromBob);

        list.add(new CountDownLatch(1));
        roomFromBobPOV.sendEvent(buildTextEvent(messagesFromBob.get(mReceivedMessagesFromBob), mBobSession), callback);
        list.get(list.size()-1).await(10000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue(2 == mReceivedMessagesFromBob);

        list.add(new CountDownLatch(1));
        roomFromBobPOV.sendEvent(buildTextEvent(messagesFromBob.get(mReceivedMessagesFromBob), mBobSession), callback);
        list.get(list.size()-1).await(10000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue(3 == mReceivedMessagesFromBob);

        list.add(new CountDownLatch(1));
        roomFromAlicePOV.sendEvent(buildTextEvent(messagesFromAlice.get(mReceivedMessagesFromAlice), mAliceSession), callback);
        list.get(list.size()-1).await(10000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue(2 == mReceivedMessagesFromAlice);
    }

    //==============================================================================================================
    // private test routines
    //==============================================================================================================

    private MXSession mBobSession;
    private MXSession mAliceSession;
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

        lock1.await(10000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue(null != mRoomId);

        Room room = mAliceSession.getDataHandler().getRoom(mRoomId);

        final CountDownLatch lock2 = new CountDownLatch(1);
        room.enableEncryptionWithAlgorithm(MXCryptoAlgorithms.MXCRYPTO_ALGORITHM_MEGOLM, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                params.put("enableEncryptionWithAlgorithm", "enableEncryptionWithAlgorithm");
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
        lock2.await(10000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue(params.containsKey("enableEncryptionWithAlgorithm"));
    }

    private void doE2ETestWithAliceAndBobInARoom(boolean cryptedBob) throws Exception {
        final HashMap<String, String> statuses = new HashMap<>();

        doE2ETestWithAliceInARoom();

        Room room = mAliceSession.getDataHandler().getRoom(mRoomId);

        createBobAccount();
        mBobSession.setCryptoEnabled(cryptedBob);

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

        lock1.await(30000, TimeUnit.DAYS.MILLISECONDS);

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

        lock2.await(30000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue(statuses.containsKey("joinRoom"));

        mBobSession.getDataHandler().removeListener(bobEventListener);
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
                if (TextUtils.equals(event.type, Event.EVENT_TYPE_MESSAGE)) {
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
        lock.await(10000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue(mMessagesCount == 1);

        lock = new CountDownLatch(2);
        list.clear();
        list.add(lock);
        roomFromBobPOV.sendEvent(buildTextEvent(messagesFromBob.get(0), mBobSession), callback);
        lock.await(10000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue(mMessagesCount == 2);

        lock = new CountDownLatch(2);
        list.clear();
        list.add(lock);
        roomFromBobPOV.sendEvent(buildTextEvent(messagesFromBob.get(1), mBobSession), callback);
        lock.await(10000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue(mMessagesCount == 3);

        lock = new CountDownLatch(2);
        list.clear();
        list.add(lock);
        roomFromBobPOV.sendEvent(buildTextEvent(messagesFromBob.get(2), mBobSession), callback);
        lock.await(10000, TimeUnit.DAYS.MILLISECONDS);
        assertTrue(mMessagesCount == 4);

        lock = new CountDownLatch(2);
        list.clear();
        list.add(lock);
        roomFromAlicePOV.sendEvent(buildTextEvent(messagesFromAlice.get(1), mAliceSession), callback);
        lock.await(10000, TimeUnit.DAYS.MILLISECONDS);
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
        assertTrue(event.getAge() < 2000);

        JsonObject eventContent = event.getContentAsJsonObject();
        assertTrue(TextUtils.equals(eventContent.get("body").getAsString(), clearMessage));
        assertTrue(TextUtils.equals(event.sender, senderSession.getMyUserId()));

        return true;
    }
}
