/*
 * Copyright 2016 OpenMarket Ltd
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
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.matrix.androidsdk.crypto.MXCryptoAlgorithms;
import org.matrix.androidsdk.crypto.data.MXDeviceInfo;
import org.matrix.androidsdk.crypto.data.MXKey;
import org.matrix.androidsdk.crypto.data.MXUsersDevicesMap;
import org.matrix.androidsdk.rest.model.crypto.KeysQueryResponse;
import org.matrix.androidsdk.rest.model.crypto.KeysUploadResponse;

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
public class CryptoRestTest {

    private static final String MXTESTS_BOB = "mxBob";
    private static final String MXTESTS_BOB_PWD = "bobbob";

    private static final String MXTESTS_ALICE = "mxAlice";
    private static final String MXTESTS_ALICE_PWD = "alicealice";

    private MXSession mBobSession;
    private MXSession mAliceSession;

    private void createBobAccount() throws Exception {
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

    @Test
    public void test01_testDeviceKeys() throws Exception {
        Context context = InstrumentationRegistry.getContext();

        createBobAccount();
        final Map<String, Object> results = new HashMap<>();

        String ed25519key = "wV5E3EUSHpHuoZLljNzojlabjGdXT3Mz7rugG9zgbkI";

        MXDeviceInfo bobDevice = new MXDeviceInfo("dev1");
        bobDevice.userId = mBobSession.getMyUserId();
        bobDevice.algorithms = Arrays.asList(MXCryptoAlgorithms.MXCRYPTO_ALGORITHM_OLM);

        Map<String, String> keysMap = new HashMap();
        keysMap.put("ed25519:" + bobDevice.deviceId, ed25519key);
        bobDevice.keys = keysMap;

        CountDownLatch lock0 = new CountDownLatch(1);
        mBobSession.getCryptoRestClient().uploadKeys(bobDevice.JSONDictionary(), null, "dev1", new TestApiCallback<KeysUploadResponse>(lock0) {
            @Override
            public void onSuccess(KeysUploadResponse keysUploadResponse) {
                results.put("keysUploadResponse", keysUploadResponse);
                super.onSuccess(keysUploadResponse);
            }
        });
        lock0.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);

        KeysUploadResponse keysUploadResponse = (KeysUploadResponse) results.get("keysUploadResponse");

        Assert.assertNotNull(keysUploadResponse);
        Assert.assertNotNull(keysUploadResponse.oneTimeKeyCounts);

        Assert.assertTrue(keysUploadResponse.oneTimeKeyCounts.isEmpty());
        Assert.assertEquals(0, keysUploadResponse.oneTimeKeyCountsForAlgorithm("deded"));

        CountDownLatch lock1 = new CountDownLatch(1);
        mBobSession.getCryptoRestClient().downloadKeysForUsers(Arrays.asList(mBobSession.getMyUserId()), null, new TestApiCallback<KeysQueryResponse>(lock1) {
            @Override
            public void onSuccess(KeysQueryResponse keysQueryResponse) {
                results.put("keysQueryResponse", keysQueryResponse);
                super.onSuccess(keysQueryResponse);
            }
        });

        lock1.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        KeysQueryResponse keysQueryResponse = (KeysQueryResponse) results.get("keysQueryResponse");

        Assert.assertNotNull(keysQueryResponse);
        Assert.assertNotNull(keysQueryResponse.deviceKeys);

        MXUsersDevicesMap<MXDeviceInfo> deviceInfos = new MXUsersDevicesMap<>(keysQueryResponse.deviceKeys);

        Assert.assertNotNull(deviceInfos.getUserIds());
        Assert.assertEquals(1, deviceInfos.getUserIds().size());

        List<String> deviceIds = deviceInfos.getUserDeviceIds(mBobSession.getMyUserId());
        Assert.assertNotNull(deviceIds);
        Assert.assertEquals(1, deviceIds.size());

        MXDeviceInfo bobDevice2 = deviceInfos.getObject("dev1", mBobSession.getMyUserId());
        Assert.assertNotNull(bobDevice2);
        Assert.assertEquals("dev1", bobDevice2.deviceId);
        Assert.assertEquals(bobDevice2.userId, mBobSession.getMyUserId());

        mBobSession.clear(context);
    }

    @Test
    public void test02_testOneTimeKeys() throws Exception {
        Context context = InstrumentationRegistry.getContext();

        createBobAccount();

        final Map<String, Object> results = new HashMap<>();
        final Map<String, Object> otks = new HashMap<>();

        otks.put("curve25519:AAAABQ", "ueuHES/Q0P1MZ4J3IUpC8iQTkgQNX66ZpxVLUaTDuB8");
        otks.put("curve25519:AAAABA", "PmyaaB68Any+za9CuZXzFsQZW31s/TW6XbAB9akEpQs");

        CountDownLatch lock1 = new CountDownLatch(1);
        mBobSession.getCryptoRestClient().uploadKeys(null, otks, "dev1", new TestApiCallback<KeysUploadResponse>(lock1) {
            @Override
            public void onSuccess(KeysUploadResponse keysUploadResponse) {
                results.put("keysUploadResponse", keysUploadResponse);
                super.onSuccess(keysUploadResponse);
            }
        });
        lock1.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);

        KeysUploadResponse keysUploadResponse = (KeysUploadResponse) results.get("keysUploadResponse");
        Assert.assertNotNull(keysUploadResponse);
        Assert.assertNotNull(keysUploadResponse.oneTimeKeyCounts);
        Assert.assertEquals(1, keysUploadResponse.oneTimeKeyCounts.size());

        Assert.assertEquals(2, keysUploadResponse.oneTimeKeyCountsForAlgorithm("curve25519"));
        Assert.assertEquals(0, keysUploadResponse.oneTimeKeyCountsForAlgorithm("deded"));

        mBobSession.clear(context);
    }

    @Test
    public void test03_testClaimOneTimeKeysForUsersDevices() throws Exception {
        Context context = InstrumentationRegistry.getContext();

        createBobAccount();
        createAliceAccount();

        final Map<String, Object> results = new HashMap<>();
        final Map<String, Object> otks = new HashMap<>();

        {
            Map<String, Object> map = new HashMap<>();
            map.put("key", "ueuHES/Q0P1MZ4J3IUpC8iQTkgQNX66ZpxVLUaTDuB8");

            Map<String, String> signaturesubMap = new HashMap<>();
            signaturesubMap.put("ed25519:deviceId1", "signature1");
            Map<String, Object> signatureMap = new HashMap<>();
            signatureMap.put("@user1", signaturesubMap);
            map.put("signatures", signatureMap);

            otks.put("curve25519:AAAABQ", map);
        }

        {
            Map<String, Object> map = new HashMap<>();
            map.put("key", "PmyaaB68Any+za9CuZXzFsQZW31s/TW6XbAB9akEpQs");

            Map<String, String> signaturesubMap = new HashMap<>();
            signaturesubMap.put("ed25519:deviceId2", "signature2");
            Map<String, Object> signatureMap = new HashMap<>();
            signatureMap.put("@user2", signaturesubMap);
            map.put("signatures", signatureMap);

            otks.put("curve25519:AAAABA", map);
        }

        CountDownLatch lock1 = new CountDownLatch(1);
        mBobSession.getCryptoRestClient().uploadKeys(null, otks, "dev1", new TestApiCallback<KeysUploadResponse>(lock1) {
            @Override
            public void onSuccess(KeysUploadResponse keysUploadResponse) {
                results.put("keysUploadResponse", keysUploadResponse);
                super.onSuccess(keysUploadResponse);
            }
        });

        lock1.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);

        KeysUploadResponse bobKeysUploadResponse = (KeysUploadResponse) results.get("keysUploadResponse");
        Assert.assertNotNull(bobKeysUploadResponse);

        MXUsersDevicesMap<String> usersDevicesKeyTypesMap = new MXUsersDevicesMap<>();
        usersDevicesKeyTypesMap.setObject("curve25519", mBobSession.getMyUserId(), "dev1");

        CountDownLatch lock2 = new CountDownLatch(1);
        mAliceSession.getCryptoRestClient().claimOneTimeKeysForUsersDevices(usersDevicesKeyTypesMap, new TestApiCallback<MXUsersDevicesMap<MXKey>>(lock2) {
            @Override
            public void onSuccess(MXUsersDevicesMap<MXKey> usersDevicesMap) {
                results.put("usersDevicesMap", usersDevicesMap);
                super.onSuccess(usersDevicesMap);
            }
        });

        lock2.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);

        MXUsersDevicesMap<MXKey> oneTimeKeys = (MXUsersDevicesMap<MXKey>) results.get("usersDevicesMap");
        Assert.assertNotNull(oneTimeKeys);
        Assert.assertNotNull(oneTimeKeys.getMap());
        Assert.assertEquals(1, oneTimeKeys.getMap().size());

        MXKey bobOtk = oneTimeKeys.getObject("dev1", mBobSession.getMyUserId());
        Assert.assertNotNull(bobOtk);

        Assert.assertEquals(MXKey.KEY_CURVE_25519_TYPE, bobOtk.type);
        Assert.assertEquals("AAAABA", bobOtk.keyId);
        Assert.assertEquals("curve25519:AAAABA", bobOtk.getKeyFullId());
        Assert.assertEquals("PmyaaB68Any+za9CuZXzFsQZW31s/TW6XbAB9akEpQs", bobOtk.value);
        Assert.assertNotNull(bobOtk.signatures);

        List<String> keys = new ArrayList<>(bobOtk.signatures.keySet());
        Assert.assertEquals(1, keys.size());

        mBobSession.clear(context);
        mAliceSession.clear(context);
    }
}
