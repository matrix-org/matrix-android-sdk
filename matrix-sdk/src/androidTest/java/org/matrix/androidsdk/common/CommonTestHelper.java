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

package org.matrix.androidsdk.common;

import android.content.Context;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.test.InstrumentationRegistry;

import org.junit.Assert;
import org.matrix.androidsdk.HomeServerConnectionConfig;
import org.matrix.androidsdk.MXDataHandler;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomMediaMessage;
import org.matrix.androidsdk.data.store.IMXStore;
import org.matrix.androidsdk.data.store.MXFileStore;
import org.matrix.androidsdk.data.store.MXStoreListener;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.client.LoginRestClient;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.login.AuthParams;
import org.matrix.androidsdk.rest.model.login.Credentials;
import org.matrix.androidsdk.rest.model.login.RegistrationFlowResponse;
import org.matrix.androidsdk.rest.model.login.RegistrationParams;
import org.matrix.androidsdk.rest.model.message.Message;
import org.matrix.androidsdk.util.JsonUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;


/**
 * This class exposes methods to be used in common cases
 * Registration, login, Sync, Sending messages...
 */
public class CommonTestHelper {

    public MXSession createAccount(final String userNamePrefix, final SessionTestParams testParams) throws InterruptedException {
        return createAccount(userNamePrefix, TestConstants.PASSWORD, testParams);
    }

    public MXSession logIntoAccount(final String userId, final SessionTestParams testParams) throws InterruptedException {
        return logIntoAccount(userId, TestConstants.PASSWORD, testParams);
    }

    /**
     * Create a Home server configuration, with Http connection allowed for test
     *
     * @param credentials
     * @return
     */
    public HomeServerConnectionConfig createHomeServerConfig(@Nullable Credentials credentials) {
        final HomeServerConnectionConfig hs = new HomeServerConnectionConfig.Builder()
                .withHomeServerUri(Uri.parse(TestConstants.TESTS_HOME_SERVER_URL))
                .withCredentials(credentials)
                .build();
        return hs;
    }

    /**
     * This methods init the event stream and check for initial sync
     *
     * @param session    the session to sync
     * @param withCrypto true if crypto is enabled and should be checked
     */
    public void syncSession(@Nonnull final MXSession session, final boolean withCrypto) throws InterruptedException {
        final Map<String, Boolean> params = new HashMap<>();
        final int sizeOfLock = withCrypto ? 2 : 1;
        final CountDownLatch lock2 = new CountDownLatch(sizeOfLock);
        session.getDataHandler().addListener(new MXEventListener() {
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
        session.getDataHandler().getStore().open();
        session.startEventStream(null);

        await(lock2);
        Assert.assertTrue(params.containsKey("isInit"));
        if (withCrypto) {
            Assert.assertTrue(params.containsKey("onCryptoSyncComplete"));
        }
    }

    /**
     * Sends text messages in a room
     *
     * @param room         the room where to send the messages
     * @param message      the message to send
     * @param nbOfMessages the number of time the message will be sent
     * @throws Exception
     */
    public List<Event> sendTextMessage(@Nonnull final Room room, @Nonnull final String message, final int nbOfMessages) throws Exception {
        final List<Event> sentEvents = new ArrayList<>(nbOfMessages);
        final CountDownLatch latch = new CountDownLatch(nbOfMessages);
        final MXEventListener onEventSentListener = new MXEventListener() {
            @Override
            public void onEventSent(Event event, String prevEventId) {
                latch.countDown();
            }
        };
        room.addEventListener(onEventSentListener);
        for (int i = 0; i < nbOfMessages; i++) {
            room.sendTextMessage(message + " #" + (i + 1), null, Message.FORMAT_MATRIX_HTML, new RoomMediaMessage.EventCreationListener() {
                @Override
                public void onEventCreated(RoomMediaMessage roomMediaMessage) {
                    final Event sentEvent = roomMediaMessage.getEvent();
                    sentEvents.add(sentEvent);
                }

                @Override
                public void onEventCreationFailed(RoomMediaMessage roomMediaMessage, String errorMessage) {

                }

                @Override
                public void onEncryptionFailed(RoomMediaMessage roomMediaMessage) {

                }
            });
        }
        await(latch);
        room.removeEventListener(onEventSentListener);

        // Check that all events has been created
        Assert.assertEquals(nbOfMessages, sentEvents.size());

        return sentEvents;
    }


    // PRIVATE METHODS *****************************************************************************

    /**
     * Creates a unique account
     *
     * @param userNamePrefix the user name prefix
     * @param password       the password
     * @param testParams     test params about the session
     * @return the session associated with the newly created account
     */
    private MXSession createAccount(@NonNull final String userNamePrefix,
                                    @NonNull final String password,
                                    @NonNull final SessionTestParams testParams) throws InterruptedException {
        final Context context = InstrumentationRegistry.getContext();
        final MXSession session = createAccountAndSync(
                context,
                userNamePrefix + "_" + System.currentTimeMillis() + UUID.randomUUID(),
                password,
                testParams
        );
        Assert.assertNotNull(session);
        return session;
    }

    /**
     * Logs into an existing account
     *
     * @param userId     the userId to log in
     * @param password   the password to log in
     * @param testParams test params about the session
     * @return the session associated with the existing account
     */
    private MXSession logIntoAccount(@NonNull final String userId,
                                     @NonNull final String password,
                                     @NonNull final SessionTestParams testParams) throws InterruptedException {
        final Context context = InstrumentationRegistry.getContext();
        final MXSession session = logAccountAndSync(context, userId, password, testParams);
        Assert.assertNotNull(session);
        return session;
    }

    /**
     * Create an account and a dedicated session
     *
     * @param context           the context
     * @param userName          the account username
     * @param password          the password
     * @param sessionTestParams parameters for the test
     */
    private MXSession createAccountAndSync(Context context,
                                           String userName,
                                           String password,
                                           SessionTestParams sessionTestParams) throws InterruptedException {
        final HomeServerConnectionConfig hs = createHomeServerConfig(null);

        final LoginRestClient loginRestClient = new LoginRestClient(hs);

        final Map<String, Object> params = new HashMap<>();
        final RegistrationParams registrationParams = new RegistrationParams();

        CountDownLatch lock = new CountDownLatch(1);

        // get the registration session id
        loginRestClient.register(registrationParams, new TestApiCallback<Credentials>(lock, false) {
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

        await(lock);

        final String session = (String) params.get("session");

        Assert.assertNotNull(session);

        registrationParams.username = userName;
        registrationParams.password = password;
        AuthParams authParams = new AuthParams(LoginRestClient.LOGIN_FLOW_TYPE_DUMMY);
        authParams.session = session;

        registrationParams.auth = authParams;

        lock = new CountDownLatch(1);
        loginRestClient.register(registrationParams, new TestApiCallback<Credentials>(lock) {
            @Override
            public void onSuccess(Credentials credentials) {
                params.put("credentials", credentials);
                super.onSuccess(credentials);
            }
        });

        await(lock);

        Credentials credentials = (Credentials) params.get("credentials");

        Assert.assertNotNull(credentials);

        hs.setCredentials(credentials);

        IMXStore store = new MXFileStore(hs, false, context);

        MXDataHandler dataHandler = new MXDataHandler(store, credentials);
        dataHandler.setLazyLoadingEnabled(sessionTestParams.getWithLazyLoading());

        MXSession mxSession = new MXSession.Builder(hs, dataHandler, context)
                .withLegacyCryptoStore(sessionTestParams.getWithLegacyCryptoStore())
                .build();

        if (sessionTestParams.getWithCryptoEnabled()) {
            mxSession.enableCryptoWhenStarting();
        }
        if (sessionTestParams.getWithInitialSync()) {
            syncSession(mxSession, sessionTestParams.getWithCryptoEnabled());
        }
        return mxSession;
    }

    /**
     * Start an account login
     *
     * @param context           the context
     * @param userName          the account username
     * @param password          the password
     * @param sessionTestParams session test params
     */
    private MXSession logAccountAndSync(final Context context,
                                        final String userName,
                                        final String password,
                                        final SessionTestParams sessionTestParams) throws InterruptedException {
        final HomeServerConnectionConfig hs = createHomeServerConfig(null);
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

        await(lock);

        final Credentials credentials = (Credentials) params.get("credentials");

        Assert.assertNotNull(credentials);

        hs.setCredentials(credentials);

        final IMXStore store = new MXFileStore(hs, false, context);

        MXDataHandler mxDataHandler = new MXDataHandler(store, credentials);
        mxDataHandler.setLazyLoadingEnabled(sessionTestParams.getWithLazyLoading());

        final MXSession mxSession = new MXSession.Builder(hs, mxDataHandler, context)
                .withLegacyCryptoStore(sessionTestParams.getWithLegacyCryptoStore())
                .build();

        if (sessionTestParams.getWithCryptoEnabled()) {
            mxSession.enableCryptoWhenStarting();
        }
        if (sessionTestParams.getWithInitialSync()) {
            syncSession(mxSession, sessionTestParams.getWithCryptoEnabled());
        }
        return mxSession;
    }

    /**
     * Await for a latch and ensure the result is true
     *
     * @param latch
     * @throws InterruptedException
     */
    public void await(CountDownLatch latch) throws InterruptedException {
        Assert.assertTrue(latch.await(TestConstants.getTimeOutMillis(), TimeUnit.MILLISECONDS));
    }

    /**
     * Clear all provided sessions
     *
     * @param sessions the sessions to clear
     */
    public void clearAllSessions(List<MXSession> sessions) {
        final Context context = InstrumentationRegistry.getContext();

        for (MXSession session : sessions) {
            session.clear(context);
        }
    }

    /**
     * Clone a session.
     * It simulate that the user launches again the application with the same Credentials, contrary to login which will create a new DeviceId
     *
     * @param from the session to clone
     * @return the duplicated session
     */
    @NonNull
    public MXSession createNewSession(@NonNull MXSession from, SessionTestParams sessionTestParams) throws InterruptedException {
        final Context context = InstrumentationRegistry.getContext();

        Credentials credentials = from.getCredentials();
        HomeServerConnectionConfig hs = createHomeServerConfig(credentials);
        MXFileStore store = new MXFileStore(hs, false, context);
        MXDataHandler dataHandler = new MXDataHandler(store, credentials);
        dataHandler.setLazyLoadingEnabled(sessionTestParams.getWithLazyLoading());
        store.setDataHandler(dataHandler);
        MXSession session2 = new MXSession.Builder(hs, dataHandler, context)
                .withLegacyCryptoStore(sessionTestParams.getWithLegacyCryptoStore())
                .build();

        final Map<String, Object> results = new HashMap<>();

        final CountDownLatch lock = new CountDownLatch(1);
        MXStoreListener listener = new MXStoreListener() {
            @Override
            public void postProcess(String accountId) {
                results.put("postProcess", "postProcess " + accountId);
            }

            @Override
            public void onStoreReady(String accountId) {
                results.put("onStoreReady", "onStoreReady");
                lock.countDown();
            }

            @Override
            public void onStoreCorrupted(String accountId, String description) {
                results.put("onStoreCorrupted", description);
                lock.countDown();
            }

            @Override
            public void onStoreOOM(String accountId, String description) {
                results.put("onStoreOOM", "onStoreOOM");
                lock.countDown();
            }
        };

        store.addMXStoreListener(listener);
        store.open();

        await(lock);

        Assert.assertTrue(results.toString(), results.containsKey("onStoreReady"));

        return session2;
    }
}
