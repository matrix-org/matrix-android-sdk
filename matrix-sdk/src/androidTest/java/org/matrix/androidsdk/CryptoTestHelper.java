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
import android.net.Uri;
import android.support.annotation.Nullable;

import org.junit.Assert;
import org.matrix.androidsdk.data.store.IMXStore;
import org.matrix.androidsdk.data.store.MXFileStore;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.client.LoginRestClient;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.login.Credentials;
import org.matrix.androidsdk.rest.model.login.RegistrationFlowResponse;
import org.matrix.androidsdk.rest.model.login.RegistrationParams;
import org.matrix.androidsdk.util.JsonUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class CryptoTestHelper {
    private static final String TESTS_HOME_SERVER_URL = "http://10.0.2.2:8080";

    /**
     * Create a Home server configuration, with Http connection allowed for test
     *
     * @param credentials
     * @return
     */
    public static HomeServerConnectionConfig createHomeServerConfig(@Nullable Credentials credentials) {
        HomeServerConnectionConfig hs = new HomeServerConnectionConfig(Uri.parse(TESTS_HOME_SERVER_URL));

        hs.allowHttpConnection();

        hs.setCredentials(credentials);

        return hs;
    }

    /**
     * Create an account and a dedicated session
     *
     * @param context      the context
     * @param userName     the account username
     * @param password     the password
     * @param startSession true to perform an initial sync
     * @throws Exception an exception if the account creation failed
     */
    public static MXSession createAccountAndSync(Context context, String userName, String password, boolean startSession) throws Exception {
        HomeServerConnectionConfig hs = createHomeServerConfig(null);

        LoginRestClient loginRestClient = new LoginRestClient(hs);

        final Map<String, Object> params = new HashMap<>();
        RegistrationParams registrationParams = new RegistrationParams();

        CountDownLatch lock = new CountDownLatch(1);

        // get the registration session id
        loginRestClient.register(registrationParams, new TestApiCallback<Credentials>(lock) {
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

                super.onMatrixError(e);
            }
        });

        lock.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);

        String session = (String) params.get("session");

        Assert.assertNotNull(session);

        registrationParams.username = userName;
        registrationParams.password = password;
        Map<String, Object> authParams = new HashMap<>();
        authParams.put("session", session);
        authParams.put("type", LoginRestClient.LOGIN_FLOW_TYPE_DUMMY);

        registrationParams.auth = authParams;

        lock = new CountDownLatch(1);
        loginRestClient.register(registrationParams, new TestApiCallback<Credentials>(lock) {
            @Override
            public void onSuccess(Credentials credentials) {
                params.put("credentials", credentials);
                super.onSuccess(credentials);
            }
        });

        lock.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);

        Credentials credentials = (Credentials) params.get("credentials");

        Assert.assertNotNull(credentials);

        hs.setCredentials(credentials);

        IMXStore store = new MXFileStore(hs, context);

        MXSession mxSession = new MXSession.Builder(hs, new MXDataHandler(store, credentials), context)
                .build();

        if (!startSession) {
            return mxSession;
        }

        mxSession.getDataHandler().getStore().open();
        mxSession.startEventStream(null);

        final CountDownLatch lock2 = new CountDownLatch(1);
        mxSession.getDataHandler().addListener(new MXEventListener() {
            @Override
            public void onInitialSyncComplete(String toToken) {
                params.put("isInit", true);
                lock2.countDown();
            }
        });

        lock2.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);

        Assert.assertTrue(params.containsKey("isInit"));

        return mxSession;
    }

    /**
     * Start an account login
     *
     * @param context  the context
     * @param userName the account username
     * @param password the password
     * @throws Exception an exception if the account cannot be synced
     */
    public static MXSession logAccountAndSync(Context context, String userName, String password) throws Exception {
        HomeServerConnectionConfig hs = createHomeServerConfig(null);

        LoginRestClient loginRestClient = new LoginRestClient(hs);

        final Map<String, Object> params = new HashMap<>();

        CountDownLatch lock = new CountDownLatch(1);

        // get the registration session id
        loginRestClient.loginWithUser(userName, password, new TestApiCallback<Credentials>(lock) {
            @Override
            public void onSuccess(Credentials credentials) {
                params.put("credentials", credentials);
                super.onSuccess(credentials);
            }
        });

        lock.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);

        Credentials credentials = (Credentials) params.get("credentials");

        Assert.assertNotNull(credentials);

        hs.setCredentials(credentials);

        IMXStore store = new MXFileStore(hs, context);

        MXSession mxSession = new MXSession.Builder(hs, new MXDataHandler(store, credentials), context)
                .build();

        mxSession.enableCryptoWhenStarting();

        final CountDownLatch lock2 = new CountDownLatch(2);
        mxSession.getDataHandler().addListener(new MXEventListener() {
            @Override
            public void onInitialSyncComplete(String toToken) {
                params.put("isInit", true);
                lock2.countDown();
            }

            @Override
            public void onCryptoSyncComplete() {
                params.put("onCryptoSyncComplete", true);
                lock2.countDown();
            }
        });

        mxSession.getDataHandler().getStore().open();
        mxSession.startEventStream(null);

        lock2.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);

        Assert.assertTrue(params.containsKey("isInit"));
        Assert.assertTrue(params.containsKey("onCryptoSyncComplete"));

        return mxSession;
    }
}
