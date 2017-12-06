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

import org.matrix.androidsdk.data.store.IMXStore;
import org.matrix.androidsdk.data.store.MXFileStore;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.client.LoginRestClient;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.login.Credentials;
import org.matrix.androidsdk.rest.model.login.RegistrationFlowResponse;
import org.matrix.androidsdk.rest.model.login.RegistrationParams;
import org.matrix.androidsdk.util.JsonUtils;

import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class TestsHelper {
    public static final String TESTS_HOME_SERVER_URL = "http://localhost:8080";

    private static CountDownLatch mLock;

    /**
     * Create an account and a dedicated session
     * @param context the context
     * @param userName the account username
     * @param password the password
     * @param startSession true to perform an initial sync
     * @param callback the callback
     * @throws Exception an exception if the account cannot be created
     */
    public static void createAccountAndSync(Context context, String userName, String password, boolean startSession, ApiCallback<MXSession> callback) throws Exception {
        RestClient.mUseMXExececutor = true;

        Uri uri = Uri.parse(TESTS_HOME_SERVER_URL);
        HomeServerConnectionConfig hs = new HomeServerConnectionConfig(uri);
        LoginRestClient loginRestClient = new LoginRestClient(hs);

        final HashMap<String, Object> params = new HashMap<>();
        RegistrationParams registrationParams = new RegistrationParams();

        mLock = new CountDownLatch(1);

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

        mLock.await(1000, TimeUnit.MILLISECONDS);

        String session = (String)params.get("session");

        if (null == session) {
            callback.onUnexpectedError(null);
        }

        registrationParams.username = userName;
        registrationParams.password = password;
        HashMap<String, Object> authParams = new HashMap<>();
        authParams.put("session", session);
        authParams.put("type", LoginRestClient.LOGIN_FLOW_TYPE_DUMMY);

        registrationParams.auth = authParams;

        mLock = new CountDownLatch(1);
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

        mLock.await(1000, TimeUnit.MILLISECONDS);

        Credentials credentials = (Credentials)params.get("credentials");

        if (null == credentials) {
            callback.onMatrixError(null);
            return;
        }

        hs.setCredentials(credentials);

        IMXStore store =  new MXFileStore(hs, context);

        MXSession mxSession = new MXSession(hs, new MXDataHandler(store, credentials), context);


        if (!startSession) {
            callback.onSuccess(mxSession);
            return;
        }

        mxSession.getDataHandler().getStore().open();
        mxSession.startEventStream(null);

        mLock = new CountDownLatch(1);
        mxSession.getDataHandler().addListener(new MXEventListener() {
            @Override
            public void onInitialSyncComplete(String toToken) {
                params.put("isInit", true);
                mLock.countDown();
            }
        });

        mLock.await(10000, TimeUnit.MILLISECONDS);

        if (params.containsKey("isInit")) {
            callback.onSuccess(mxSession);
        } else {
            callback.onMatrixError(null);
        }
    }
}
