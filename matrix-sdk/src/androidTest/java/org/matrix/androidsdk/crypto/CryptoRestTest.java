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

package org.matrix.androidsdk.crypto;

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.common.CommonTestHelper;
import org.matrix.androidsdk.common.SessionTestParams;
import org.matrix.androidsdk.common.TestApiCallback;
import org.matrix.androidsdk.common.TestConstants;
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
import java.util.concurrent.CountDownLatch;

@RunWith(AndroidJUnit4.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class CryptoRestTest {

    private CommonTestHelper mTestHelper = new CommonTestHelper();


    @Test
    public void test01_testDeviceKeys() throws Exception {
        final Context context = InstrumentationRegistry.getContext();
        final SessionTestParams testParams = new SessionTestParams(true);
        final MXSession bobSession = mTestHelper.createAccount(TestConstants.USER_BOB, testParams);
        final Map<String, Object> results = new HashMap<>();

        String ed25519key = "wV5E3EUSHpHuoZLljNzojlabjGdXT3Mz7rugG9zgbkI";

        MXDeviceInfo bobDevice = new MXDeviceInfo("dev1");
        bobDevice.userId = bobSession.getMyUserId();
        bobDevice.algorithms = Arrays.asList(CryptoConstantsKt.MXCRYPTO_ALGORITHM_OLM);

        Map<String, String> keysMap = new HashMap<>();
        keysMap.put("ed25519:" + bobDevice.deviceId, ed25519key);
        bobDevice.keys = keysMap;

        CountDownLatch lock0 = new CountDownLatch(1);
        bobSession.getCryptoRestClient().uploadKeys(bobDevice.JSONDictionary(), null, "dev1", new TestApiCallback<KeysUploadResponse>(lock0) {
            @Override
            public void onSuccess(KeysUploadResponse keysUploadResponse) {
                results.put("keysUploadResponse", keysUploadResponse);
                super.onSuccess(keysUploadResponse);
            }
        });
        mTestHelper.await(lock0);

        KeysUploadResponse keysUploadResponse = (KeysUploadResponse) results.get("keysUploadResponse");

        Assert.assertNotNull(keysUploadResponse);
        Assert.assertNotNull(keysUploadResponse.oneTimeKeyCounts);

        Assert.assertTrue(keysUploadResponse.oneTimeKeyCounts.isEmpty());
        Assert.assertEquals(0, keysUploadResponse.oneTimeKeyCountsForAlgorithm("deded"));

        CountDownLatch lock1 = new CountDownLatch(1);
        bobSession.getCryptoRestClient().downloadKeysForUsers(Arrays.asList(bobSession.getMyUserId()), null, new TestApiCallback<KeysQueryResponse>(lock1) {
            @Override
            public void onSuccess(KeysQueryResponse keysQueryResponse) {
                results.put("keysQueryResponse", keysQueryResponse);
                super.onSuccess(keysQueryResponse);
            }
        });

        mTestHelper.await(lock1);
        KeysQueryResponse keysQueryResponse = (KeysQueryResponse) results.get("keysQueryResponse");

        Assert.assertNotNull(keysQueryResponse);
        Assert.assertNotNull(keysQueryResponse.deviceKeys);

        MXUsersDevicesMap<MXDeviceInfo> deviceInfos = new MXUsersDevicesMap<>(keysQueryResponse.deviceKeys);

        Assert.assertNotNull(deviceInfos.getUserIds());
        Assert.assertEquals(1, deviceInfos.getUserIds().size());

        List<String> deviceIds = deviceInfos.getUserDeviceIds(bobSession.getMyUserId());
        Assert.assertNotNull(deviceIds);
        Assert.assertEquals(1, deviceIds.size());

        MXDeviceInfo bobDevice2 = deviceInfos.getObject("dev1", bobSession.getMyUserId());
        Assert.assertNotNull(bobDevice2);
        Assert.assertEquals("dev1", bobDevice2.deviceId);
        Assert.assertEquals(bobDevice2.userId, bobSession.getMyUserId());
        bobSession.clear(context);
    }

    @Test
    public void test02_testOneTimeKeys() throws Exception {
        Context context = InstrumentationRegistry.getContext();
        final SessionTestParams testParams = new SessionTestParams(true);
        final MXSession bobSession = mTestHelper.createAccount(TestConstants.USER_BOB, testParams);

        final Map<String, Object> results = new HashMap<>();
        final Map<String, Object> otks = new HashMap<>();

        otks.put("curve25519:AAAABQ", "ueuHES/Q0P1MZ4J3IUpC8iQTkgQNX66ZpxVLUaTDuB8");
        otks.put("curve25519:AAAABA", "PmyaaB68Any+za9CuZXzFsQZW31s/TW6XbAB9akEpQs");

        CountDownLatch lock1 = new CountDownLatch(1);
        bobSession.getCryptoRestClient().uploadKeys(null, otks, "dev1", new TestApiCallback<KeysUploadResponse>(lock1) {
            @Override
            public void onSuccess(KeysUploadResponse keysUploadResponse) {
                results.put("keysUploadResponse", keysUploadResponse);
                super.onSuccess(keysUploadResponse);
            }
        });
        mTestHelper.await(lock1);

        KeysUploadResponse keysUploadResponse = (KeysUploadResponse) results.get("keysUploadResponse");
        Assert.assertNotNull(keysUploadResponse);
        Assert.assertNotNull(keysUploadResponse.oneTimeKeyCounts);
        Assert.assertEquals(1, keysUploadResponse.oneTimeKeyCounts.size());

        Assert.assertEquals(2, keysUploadResponse.oneTimeKeyCountsForAlgorithm("curve25519"));
        Assert.assertEquals(0, keysUploadResponse.oneTimeKeyCountsForAlgorithm("deded"));

        bobSession.clear(context);
    }

    @Test
    public void test03_testClaimOneTimeKeysForUsersDevices() throws Exception {
        Context context = InstrumentationRegistry.getContext();
        final SessionTestParams testParams = new SessionTestParams(true);
        final MXSession bobSession = mTestHelper.createAccount(TestConstants.USER_BOB, testParams);
        final MXSession aliceSession = mTestHelper.createAccount(TestConstants.USER_ALICE, testParams);

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
        bobSession.getCryptoRestClient().uploadKeys(null, otks, "dev1", new TestApiCallback<KeysUploadResponse>(lock1) {
            @Override
            public void onSuccess(KeysUploadResponse keysUploadResponse) {
                results.put("keysUploadResponse", keysUploadResponse);
                super.onSuccess(keysUploadResponse);
            }
        });

        mTestHelper.await(lock1);

        KeysUploadResponse bobKeysUploadResponse = (KeysUploadResponse) results.get("keysUploadResponse");
        Assert.assertNotNull(bobKeysUploadResponse);

        MXUsersDevicesMap<String> usersDevicesKeyTypesMap = new MXUsersDevicesMap<>();
        usersDevicesKeyTypesMap.setObject("curve25519", bobSession.getMyUserId(), "dev1");

        CountDownLatch lock2 = new CountDownLatch(1);
        aliceSession.getCryptoRestClient().claimOneTimeKeysForUsersDevices(usersDevicesKeyTypesMap, new TestApiCallback<MXUsersDevicesMap<MXKey>>(lock2) {
            @Override
            public void onSuccess(MXUsersDevicesMap<MXKey> usersDevicesMap) {
                results.put("usersDevicesMap", usersDevicesMap);
                super.onSuccess(usersDevicesMap);
            }
        });

        mTestHelper.await(lock2);

        MXUsersDevicesMap<MXKey> oneTimeKeys = (MXUsersDevicesMap<MXKey>) results.get("usersDevicesMap");
        Assert.assertNotNull(oneTimeKeys);
        Assert.assertNotNull(oneTimeKeys.getMap());
        Assert.assertEquals(1, oneTimeKeys.getMap().size());

        MXKey bobOtk = oneTimeKeys.getObject("dev1", bobSession.getMyUserId());
        Assert.assertNotNull(bobOtk);

        Assert.assertEquals(MXKey.KEY_CURVE_25519_TYPE, bobOtk.type);
        Assert.assertEquals("AAAABA", bobOtk.keyId);
        Assert.assertEquals("curve25519:AAAABA", bobOtk.getKeyFullId());
        Assert.assertEquals("PmyaaB68Any+za9CuZXzFsQZW31s/TW6XbAB9akEpQs", bobOtk.value);
        Assert.assertNotNull(bobOtk.signatures);

        List<String> keys = new ArrayList<>(bobOtk.signatures.keySet());
        Assert.assertEquals(1, keys.size());

        bobSession.clear(context);
        aliceSession.clear(context);
    }
}
