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
import android.net.Uri;
import android.os.Looper;
import android.util.Log;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.matrix.androidsdk.HomeserverConnectionConfig;
import org.matrix.androidsdk.MXDataHandler;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.RestClient;
import org.matrix.androidsdk.data.IMXStore;
import org.matrix.androidsdk.data.MXFileStore;
import org.matrix.androidsdk.data.MXMemoryStore;
import org.matrix.androidsdk.listeners.IMXEventListener;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.client.LoginRestClient;
import org.matrix.androidsdk.rest.client.MXRestExecutor;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.login.Credentials;
import org.matrix.androidsdk.rest.model.login.LoginFlow;
import org.matrix.androidsdk.rest.model.login.RegistrationFlowResponse;
import org.matrix.androidsdk.rest.model.login.RegistrationParams;
import org.matrix.androidsdk.util.JsonUtils;
import org.matrix.androidsdk.util.MXOsHandler;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.internal.ShadowExtractor;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.util.Scheduler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
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
    private static final String MXTestsHomeServerURL = "http://localhost:8080";

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


    private CountDownLatch mLock = new CountDownLatch(1);
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
        Uri uri = Uri.parse(MXTestsHomeServerURL);
        HomeserverConnectionConfig hs = new HomeserverConnectionConfig(uri);
        LoginRestClient loginRestClient = new LoginRestClient(hs);

        final HashMap<String, Object> params = new HashMap<>();

        mBobUserName = MXTESTS_BOB + System.currentTimeMillis();

        RegistrationParams registrationParams = new RegistrationParams();

        // get the registration session id
        loginRestClient.register(registrationParams, new ApiCallback<Credentials>() {
            @Override
            public void onSuccess(Credentials credentials) {
                mLock.countDown();
            }

            @Override
            public void onNetworkError(Exception e) {
                mLock.countDown();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                // detect if a parameter is expected
                RegistrationFlowResponse registrationFlowResponse = null;

                // when a response is not completed the server returns an error message
                if ((null != e.mStatus) && (e.mStatus == 401)) {
                    try {
                        registrationFlowResponse = JsonUtils.toRegistrationFlowResponse(e.mErrorBodyAsString);
                    } catch (Exception castExcept) {
                    }
                }

                // check if the server response can be casted
                if (null != registrationFlowResponse) {
                    params.put("session", registrationFlowResponse.session);
                }
                mLock.countDown();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                mLock.countDown();
            }
        });

        mLock.await(1000, TimeUnit.DAYS.MILLISECONDS);

        String session = (String)params.get("session");
        assert (null != session);

        registrationParams.username = mBobUserName;
        registrationParams.password = MXTESTS_BOB_PWD;
        HashMap<String, Object> authParams = new HashMap<>();
        authParams.put("session", session);
        authParams.put("type", LoginRestClient.LOGIN_FLOW_TYPE_DUMMY);

        registrationParams.auth = authParams;

        loginRestClient.register(registrationParams, new ApiCallback<Credentials>() {
            @Override
            public void onSuccess(Credentials credentials) {
                params.put("credentials", credentials);
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

        Credentials credentials = (Credentials)params.get("credentials");
        assert (null != credentials);

        hs.setCredentials(credentials);

        IMXStore store =  new MXFileStore(hs, context);

        mBobSession = new MXSession(hs, new MXDataHandler(store, credentials, new MXDataHandler.InvalidTokenListener() {
            @Override
            public void onTokenCorrupted() {
            }
        }), context);

        mBobSession.getDataHandler().addListener(new MXEventListener() {
            @Override
            public void onInitialSyncComplete() {
                params.put("isInit", true);
                mLock.countDown();
            }
        });

        mLock.await(1000, TimeUnit.DAYS.MILLISECONDS);

        assert (params.containsKey("isInit"));
    }
}
