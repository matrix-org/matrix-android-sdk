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
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.text.TextUtils;

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.matrix.androidsdk.crypto.MXCryptoAlgorithms;
import org.matrix.androidsdk.crypto.data.MXDeviceInfo;
import org.matrix.androidsdk.crypto.data.MXKey;
import org.matrix.androidsdk.crypto.data.MXUsersDevicesMap;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.crypto.KeysQueryResponse;
import org.matrix.androidsdk.rest.model.crypto.KeysUploadResponse;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class CryptoRestTest {

    private static final String MXTESTS_BOB  = "mxBob";
    private static final String MXTESTS_BOB_PWD = "bobbob";

    private static final String MXTESTS_ALICE = "mxAlice";
    private static final String MXTESTS_ALICE_PWD = "alicealice";

    private MXSession mBobSession;
    private MXSession mAliceSession;

    private void createBobAccount() throws Exception {
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

    public void test01_testDeviceKeys() throws Exception {
        Context context = InstrumentationRegistry.getContext();

        createBobAccount();
        final HashMap<String, Object> results = new HashMap<>();

        String ed25519key = "wV5E3EUSHpHuoZLljNzojlabjGdXT3Mz7rugG9zgbkI";

        MXDeviceInfo bobDevice = new MXDeviceInfo("dev1");
        bobDevice.userId = mBobSession.getMyUserId();
        bobDevice.algorithms = Arrays.asList(MXCryptoAlgorithms.MXCRYPTO_ALGORITHM_OLM);

        HashMap<String, String> keysMap = new HashMap();
        keysMap.put("ed25519:" + bobDevice.deviceId, ed25519key);
        bobDevice.keys = keysMap;

        final CountDownLatch lock0 = new CountDownLatch(1);
        mBobSession.getCryptoRestClient().uploadKeys(bobDevice.JSONDictionary(), null, "dev1", new ApiCallback<KeysUploadResponse>() {
            @Override
            public void onSuccess(KeysUploadResponse keysUploadResponse) {
                results.put("keysUploadResponse", keysUploadResponse);
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

        KeysUploadResponse keysUploadResponse = (KeysUploadResponse)results.get("keysUploadResponse");

        assertTrue (null != keysUploadResponse);
        assertTrue(null != keysUploadResponse.oneTimeKeyCounts);

        assertTrue(0 == keysUploadResponse.oneTimeKeyCounts.size());
        assertTrue(0 == keysUploadResponse.oneTimeKeyCountsForAlgorithm("deded"));

        final CountDownLatch lock1 = new CountDownLatch(1);
        mBobSession.getCryptoRestClient().downloadKeysForUsers(Arrays.asList(mBobSession.getMyUserId()), null, new ApiCallback<KeysQueryResponse>() {
            @Override
            public void onSuccess(KeysQueryResponse keysQueryResponse) {
                results.put("keysQueryResponse", keysQueryResponse);
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
        KeysQueryResponse keysQueryResponse = (KeysQueryResponse)results.get("keysQueryResponse");

        assertTrue (null != keysQueryResponse);
        assertTrue (null != keysQueryResponse.deviceKeys);

        MXUsersDevicesMap<MXDeviceInfo> deviceInfos = new MXUsersDevicesMap<>(keysQueryResponse.deviceKeys);

        assertTrue (null != deviceInfos.getUserIds());
        assertTrue (1 == deviceInfos.getUserIds().size());

        List<String> deviceIds = deviceInfos.getUserDeviceIds(mBobSession.getMyUserId());
        assertTrue (null != deviceIds);
        assertTrue (1 == deviceIds.size());

        MXDeviceInfo bobDevice2 = deviceInfos.getObject("dev1", mBobSession.getMyUserId());
        assertTrue (null != bobDevice2);
        assertTrue(TextUtils.equals(bobDevice2.deviceId, "dev1"));
        assertTrue(TextUtils.equals(bobDevice2.userId, mBobSession.getMyUserId()));

        mBobSession.clear(context);
    }

    @Test
    public void test02_testOneTimeKeys() throws Exception {
        Context context = InstrumentationRegistry.getContext();

        createBobAccount();

        final HashMap<String, Object> results = new HashMap<>();
        final HashMap<String, Object> otks = new HashMap<>();

        otks.put("curve25519:AAAABQ", "ueuHES/Q0P1MZ4J3IUpC8iQTkgQNX66ZpxVLUaTDuB8");
        otks.put("curve25519:AAAABA", "PmyaaB68Any+za9CuZXzFsQZW31s/TW6XbAB9akEpQs");

        final CountDownLatch lock1 = new CountDownLatch(1);
        mBobSession.getCryptoRestClient().uploadKeys(null, otks, "dev1", new ApiCallback<KeysUploadResponse>() {
            @Override
            public void onSuccess(KeysUploadResponse keysUploadResponse) {
                results.put("keysUploadResponse", keysUploadResponse);
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

        KeysUploadResponse keysUploadResponse = (KeysUploadResponse)results.get("keysUploadResponse");
        assertTrue (null != keysUploadResponse);
        assertTrue (null != keysUploadResponse.oneTimeKeyCounts);
        assertTrue (1 == keysUploadResponse.oneTimeKeyCounts.size());

        assertTrue (2 == keysUploadResponse.oneTimeKeyCountsForAlgorithm("curve25519"));
        assertTrue (0 == keysUploadResponse.oneTimeKeyCountsForAlgorithm("deded"));

        mBobSession.clear(context);
    }

    @Test
    public void test03_testClaimOneTimeKeysForUsersDevices() throws Exception {
        Context context = InstrumentationRegistry.getContext();

        createBobAccount();
        createAliceAccount();

        final HashMap<String, Object> results = new HashMap<>();
        final HashMap<String, Object> otks = new HashMap<>();

        {
            HashMap<String, Object> map = new HashMap<>();
            map.put("key", "ueuHES/Q0P1MZ4J3IUpC8iQTkgQNX66ZpxVLUaTDuB8");

            HashMap<String, String> signaturesubMap = new HashMap<>();
            signaturesubMap.put("ed25519:deviceId1", "signature1");
            HashMap<String, Object> signatureMap = new HashMap<>();
            signatureMap.put("@user1", signaturesubMap);
            map.put("signatures", signatureMap);

            otks.put("curve25519:AAAABQ", map);
        }

        {
            HashMap<String, Object> map = new HashMap<>();
            map.put("key", "PmyaaB68Any+za9CuZXzFsQZW31s/TW6XbAB9akEpQs");

            HashMap<String, String> signaturesubMap = new HashMap<>();
            signaturesubMap.put("ed25519:deviceId2", "signature2");
            HashMap<String, Object> signatureMap = new HashMap<>();
            signatureMap.put("@user2", signaturesubMap);
            map.put("signatures", signatureMap);

            otks.put("curve25519:AAAABA", map);
        }

        final CountDownLatch lock1 = new CountDownLatch(1);
        mBobSession.getCryptoRestClient().uploadKeys(null, otks, "dev1", new ApiCallback<KeysUploadResponse>() {
            @Override
            public void onSuccess(KeysUploadResponse keysUploadResponse) {
                results.put("keysUploadResponse", keysUploadResponse);
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

        KeysUploadResponse bobKeysUploadResponse = (KeysUploadResponse)results.get("keysUploadResponse");
        assertTrue (null != bobKeysUploadResponse);

        MXUsersDevicesMap<String> usersDevicesKeyTypesMap = new MXUsersDevicesMap<>();
        usersDevicesKeyTypesMap.setObject("curve25519", mBobSession.getMyUserId(), "dev1");

        final CountDownLatch lock2 = new CountDownLatch(1);
        mAliceSession.getCryptoRestClient().claimOneTimeKeysForUsersDevices(usersDevicesKeyTypesMap, new ApiCallback<MXUsersDevicesMap<MXKey>>() {
            @Override
            public void onSuccess(MXUsersDevicesMap<MXKey> usersDevicesMap) {
                results.put("usersDevicesMap", usersDevicesMap);
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

        MXUsersDevicesMap<MXKey> oneTimeKeys = (MXUsersDevicesMap<MXKey>)results.get("usersDevicesMap");
        assertTrue (null != oneTimeKeys);
        assertTrue (null !=  oneTimeKeys.getMap());
        assertTrue (1 ==  oneTimeKeys.getMap().size());

        MXKey bobOtk = oneTimeKeys.getObject("dev1", mBobSession.getMyUserId());
        assertTrue (null != bobOtk);

        assertTrue(TextUtils.equals(bobOtk.type, MXKey.KEY_CURVE_25519_TYPE));
        assertTrue(TextUtils.equals(bobOtk.keyId, "AAAABA"));
        assertTrue(TextUtils.equals(bobOtk.getKeyFullId(), "curve25519:AAAABA"));
        assertTrue(TextUtils.equals(bobOtk.value, "PmyaaB68Any+za9CuZXzFsQZW31s/TW6XbAB9akEpQs"));
        assertTrue(null != bobOtk.signatures);

        ArrayList<String> keys = new ArrayList<>(bobOtk.signatures.keySet());
        assertTrue(keys.size() == 1);


        mBobSession.clear(context);
        mAliceSession.clear(context);
    }
}
