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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.RestClient;
import org.matrix.androidsdk.TestsHelper;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.client.MXRestExecutor;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.util.MXOsHandler;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.internal.ShadowExtractor;
import org.robolectric.shadows.ShadowLooper;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

// use RobolectricTestRunner else the java classes have to be mocked
@RunWith(RobolectricTestRunner.class)
public class CryptoTest {

    /*
     * Out of the box, the tests are supposed to be run with the iOS simulator attacking
     * a test home server running on the same Mac machine.
     * The reason is that the simulator can access to the home server running on the Mac
     * via localhost. So everyone can use a localhost HS url that works everywhere.
     * Here, we use one of the home servers launched by the  ./demo/start.sh --no-rate-limit script
     */
    private static final String MXTestsAliceDisplayName = "mxAlice";
    private static final String MXTestsAliceAvatarURL = "mxc://matrix.org/kciiXusgZFKuNLIfLqmmttIQ";

    private static final String MXTESTS_BOB  = "mxBob";
    private static final String MXTESTS_BOB_PWD = "bobbob";

    private static final String MXTESTS_ALICE = "mxAlice";
    private static final String MXTESTS_ALICE_PWD ="alicealice";

    private String mBobUserName = "";
    private MXSession mBobSession;

    private String mAliceUserName = "";
    private MXSession mAliceSession;


    private CountDownLatch mLock;
    private String password = null;

    @Test
    public void initTests() {
        MXOsHandler.mPostListener = new MXOsHandler.IPostListener() {
            @Override
            public void onPost(Looper looper) {
                ShadowLooper shadowLooper = (ShadowLooper)ShadowExtractor.extract(looper);

                if (null != shadowLooper) {
                    shadowLooper.idle();
                }
            }
        };

        RestClient.mcallbackExecutor = new MXRestExecutor();
        RestClient.mHttpExecutor = new MXRestExecutor();
    }

    @Test
    public void createBobAccount() throws Exception {
        Context context = RuntimeEnvironment.application;
        mBobUserName = MXTESTS_BOB + System.currentTimeMillis() + this.hashCode();

        mLock = new CountDownLatch(1);
        TestsHelper.createAccountAndSync(context, mBobUserName, MXTESTS_BOB_PWD, new ApiCallback<MXSession>() {
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

        mLock.await(1000, TimeUnit.DAYS.MILLISECONDS);
        assert (null != mBobSession);
    }
}
