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

package org.matrix.androidsdk.crypto;

import android.content.Context;
import android.os.Looper;
import android.text.TextUtils;

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.TestsHelper;
import org.matrix.androidsdk.crypto.data.MXDeviceInfo;
import org.matrix.androidsdk.crypto.data.MXKey;
import org.matrix.androidsdk.crypto.data.MXUsersDevicesMap;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.crypto.KeysQueryResponse;
import org.matrix.androidsdk.rest.model.crypto.KeysUploadResponse;
import org.matrix.androidsdk.util.MXOsHandler;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.internal.ShadowExtractor;
import org.robolectric.shadows.ShadowLooper;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

// use RobolectricTestRunner else the java classes have to be mocked
@RunWith(RobolectricTestRunner.class)
@Config(manifest=Config.NONE)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class CryptoRestTest {

    private static final String MXTESTS_BOB  = "mxBob";
    private static final String MXTESTS_BOB_PWD = "bobbob";

    private static final String MXTESTS_ALICE = "mxAlice";
    private static final String MXTESTS_ALICE_PWD = "alicealice";

    private MXSession mBobSession;
    private MXSession mAliceSession;

    private CountDownLatch mLock;

    @Test
    public void test00_initTests() {
        MXOsHandler.mPostListener = new MXOsHandler.IPostListener() {
            @Override
            public void onPost(Looper looper) {
                if (looper == Looper.getMainLooper()) {
                    ShadowLooper.runUiThreadTasks();
                } else {
                    ShadowLooper shadowLooper = (ShadowLooper) ShadowExtractor.extract(looper);

                    if (null != shadowLooper) {
                        shadowLooper.idle();
                    }
                }
            }
        };
    }

    public void createBobAccount() throws Exception {
        Context context = RuntimeEnvironment.application;

        mLock = new CountDownLatch(1);
        TestsHelper.createAccountAndSync(context, MXTESTS_BOB + System.currentTimeMillis() + UUID.randomUUID().toString(), MXTESTS_BOB_PWD, false, new ApiCallback<MXSession>() {
            @Override
            public void onSuccess(MXSession session) {
                mBobSession = session;
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
        assert (null != mBobSession);
    }

    public void createAliceAccount() throws Exception {
        Context context = RuntimeEnvironment.application;

        mLock = new CountDownLatch(1);
        TestsHelper.createAccountAndSync(context, MXTESTS_ALICE + System.currentTimeMillis() + UUID.randomUUID().toString(), MXTESTS_ALICE_PWD, false, new ApiCallback<MXSession>() {
            @Override
            public void onSuccess(MXSession session) {
                mAliceSession = session;
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
        assert (null != mAliceSession);
    }

    @Test
    public void test01_testDeviceKeys() throws Exception {
        createBobAccount();
        final HashMap<String, Object> results = new HashMap<>();

        String ed25519key = "wV5E3EUSHpHuoZLljNzojlabjGdXT3Mz7rugG9zgbkI";

        MXDeviceInfo bobDevice = new MXDeviceInfo("dev1");
        bobDevice.userId = mBobSession.getMyUserId();
        bobDevice.algorithms = Arrays.asList(MXCryptoAlgorithms.MXCRYPTO_ALGORITHM_OLM);

        HashMap<String, String> keysMap = new HashMap();
        keysMap.put("ed25519:" + bobDevice.deviceId, ed25519key);
        bobDevice.keys = keysMap;

        mLock = new CountDownLatch(1);
        mBobSession.getCryptoRestClient().uploadKeys(bobDevice.JSONDictionary(), null, "dev1", new ApiCallback<KeysUploadResponse>() {
            @Override
            public void onSuccess(KeysUploadResponse keysUploadResponse) {
                results.put("keysUploadResponse", keysUploadResponse);
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
        mLock.await(1000, TimeUnit.DAYS.MILLISECONDS);

        KeysUploadResponse keysUploadResponse = (KeysUploadResponse)results.get("keysUploadResponse");

        assert (null != keysUploadResponse);
        assert(null != keysUploadResponse.oneTimeKeyCounts);

        assert(0 == keysUploadResponse.oneTimeKeyCounts.size());
        assert(0 == keysUploadResponse.oneTimeKeyCountsForAlgorithm("deded"));

        mLock = new CountDownLatch(1);
        mBobSession.getCryptoRestClient().downloadKeysForUsers(Arrays.asList(mBobSession.getMyUserId()), new ApiCallback<KeysQueryResponse>() {
            @Override
            public void onSuccess(KeysQueryResponse keysQueryResponse) {
                results.put("keysQueryResponse", keysQueryResponse);
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

        mLock.await(1000, TimeUnit.DAYS.MILLISECONDS);
        KeysQueryResponse keysQueryResponse = (KeysQueryResponse)results.get("keysQueryResponse");

        assert (null != keysQueryResponse);
        assert (null != keysQueryResponse.deviceKeys);

        MXUsersDevicesMap<MXDeviceInfo> deviceInfos = new MXUsersDevicesMap<>(keysQueryResponse.deviceKeys);

        assert (null != deviceInfos.userIds());
        assert (1 == deviceInfos.userIds().size());

        Set<String> deviceIds = deviceInfos.deviceIdsForUser(mBobSession.getMyUserId());
        assert (null != deviceIds);
        assert (1 == deviceIds.size());

        MXDeviceInfo bobDevice2 = deviceInfos.objectForDevice("dev1", mBobSession.getMyUserId());
        assert (null != bobDevice2);
        assert(TextUtils.equals(bobDevice2.deviceId, "dev1"));
        assert(TextUtils.equals(bobDevice2.userId, mBobSession.getMyUserId()));

        mBobSession.clear(RuntimeEnvironment.application);
    }

    @Test
    public void test02_testOneTimeKeys() throws Exception {
        createBobAccount();

        final HashMap<String, Object> results = new HashMap<>();
        final HashMap<String, String> otks = new HashMap<>();

        final Map<String, Map<String, Map<String, Object>> allo;

        otks.put("curve25519:AAAABQ", "ueuHES/Q0P1MZ4J3IUpC8iQTkgQNX66ZpxVLUaTDuB8");
        otks.put("curve25519:AAAABA", "PmyaaB68Any+za9CuZXzFsQZW31s/TW6XbAB9akEpQs");

        mLock = new CountDownLatch(1);
        mBobSession.getCryptoRestClient().uploadKeys(null, otks, "dev1", new ApiCallback<KeysUploadResponse>() {
            @Override
            public void onSuccess(KeysUploadResponse keysUploadResponse) {
                results.put("keysUploadResponse", keysUploadResponse);
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

        mLock.await(1000, TimeUnit.DAYS.MILLISECONDS);

        KeysUploadResponse keysUploadResponse = (KeysUploadResponse)results.get("keysUploadResponse");
        assert (null != keysUploadResponse);
        assert (null != keysUploadResponse.oneTimeKeyCounts);
        assert (1 == keysUploadResponse.oneTimeKeyCounts.size());

        assert (2 == keysUploadResponse.oneTimeKeyCountsForAlgorithm("curve25519"));
        assert (0 == keysUploadResponse.oneTimeKeyCountsForAlgorithm("deded"));

        mBobSession.clear(RuntimeEnvironment.application);
    }

    @Test
    public void test03_testClaimOneTimeKeysForUsersDevices() throws Exception {
        createBobAccount();
        createAliceAccount();

        final HashMap<String, Object> results = new HashMap<>();
        final HashMap<String, String> otks = new HashMap<>();

        otks.put("curve25519:AAAABQ", "ueuHES/Q0P1MZ4J3IUpC8iQTkgQNX66ZpxVLUaTDuB8");
        otks.put("curve25519:AAAABA", "PmyaaB68Any+za9CuZXzFsQZW31s/TW6XbAB9akEpQs");

        mLock = new CountDownLatch(1);
        mBobSession.getCryptoRestClient().uploadKeys(null, otks, "dev1", new ApiCallback<KeysUploadResponse>() {
            @Override
            public void onSuccess(KeysUploadResponse keysUploadResponse) {
                results.put("keysUploadResponse", keysUploadResponse);
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

        mLock.await(1000, TimeUnit.DAYS.MILLISECONDS);

        KeysUploadResponse bobKeysUploadResponse = (KeysUploadResponse)results.get("keysUploadResponse");
        assert (null != bobKeysUploadResponse);

        MXUsersDevicesMap<String> usersDevicesKeyTypesMap = new MXUsersDevicesMap<>(null);
        usersDevicesKeyTypesMap.setObject("curve25519", mBobSession.getMyUserId(), "dev1");

        mLock = new CountDownLatch(1);
        mAliceSession.getCryptoRestClient().claimOneTimeKeysForUsersDevices(usersDevicesKeyTypesMap, new ApiCallback<MXUsersDevicesMap<MXKey>>() {
            @Override
            public void onSuccess(MXUsersDevicesMap<MXKey> usersDevicesMap) {
                results.put("usersDevicesMap", usersDevicesMap);
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

        mLock.await(1000, TimeUnit.DAYS.MILLISECONDS);
        MXUsersDevicesMap<MXKey> oneTimeKeys = (MXUsersDevicesMap<MXKey>)results.get("usersDevicesMap");
        assert (null != oneTimeKeys);
        assert (null !=  oneTimeKeys.getMap());
        assert (1 ==  oneTimeKeys.getMap().size());

        MXKey bobOtk = oneTimeKeys.objectForDevice("dev1", mBobSession.getMyUserId());
        assert (null != bobOtk);

        assert(TextUtils.equals(bobOtk.type, MXKey.KEY_CURVE_25519_TYPE));
        assert(TextUtils.equals(bobOtk.keyId, "AAAABA"));
        assert(TextUtils.equals(bobOtk.getKeyFullId(), "curve25519:AAAABA"));
        assert(TextUtils.equals(bobOtk.value, "PmyaaB68Any+za9CuZXzFsQZW31s/TW6XbAB9akEpQs"));



        mBobSession.clear(RuntimeEnvironment.application);
        mAliceSession.clear(RuntimeEnvironment.application);
    }
}
