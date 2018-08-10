/*
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
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.test.InstrumentationRegistry;

import org.junit.Assert;
import org.matrix.androidsdk.HomeServerConnectionConfig;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.rest.model.login.Credentials;

import java.util.UUID;

/**
 * This class is the parent class test for integration test cases
 * It exposes methods to create and log into Alice, Bob and Sam accounts.
 */
public class AbsIntegrationTest {


    private TestHelper mTestHelper = new TestHelper();

    protected HomeServerConnectionConfig createHomeServerConfig(@Nullable Credentials credentials) {
        return mTestHelper.createHomeServerConfig(credentials);
    }

    protected MXSession createBobAccount(boolean withInitialSync, boolean enableCrypto) {
        return createAccount(TestConstants.BOB_USER_ID, TestConstants.BOB_PWD, withInitialSync, enableCrypto);
    }

    protected MXSession createAliceAccount(boolean withInitialSync, boolean enableCrypto) {
        return createAccount(TestConstants.ALICE_USER_ID, TestConstants.ALICE_PWD, withInitialSync, enableCrypto);
    }

    protected MXSession createSamAccount(boolean withInitialSync, boolean enableCrypto) {
        return createAccount(TestConstants.SAM_USER_ID, TestConstants.SAM_PWD, withInitialSync, enableCrypto);
    }

    protected MXSession logIntoBobAccount(final String bobUserId, final boolean withInitialSync, boolean enableCrypto) {
        return logIntoAccount(bobUserId, TestConstants.BOB_PWD, withInitialSync, enableCrypto);
    }

    protected MXSession logIntoAliceAccount(final String aliceUserId, final boolean withInitialSync, boolean enableCrypto) {
        return logIntoAccount(aliceUserId, TestConstants.ALICE_PWD, withInitialSync, enableCrypto);
    }

    protected MXSession logIntoSamAccount(final String samUserId, final boolean withInitialSync, boolean enableCrypto) {
        return logIntoAccount(samUserId, TestConstants.SAM_PWD, withInitialSync, enableCrypto);
    }

    // PRIVATE METHODS *****************************************************************************

    /**
     * Creates a unique account
     *
     * @param userId          the base userId
     * @param password        the password
     * @param withInitialSync true to perform an initial sync
     * @param enableCrypto    true to set enableCryptoWhenStarting
     * @return the session associated with the newly created account
     */
    private MXSession createAccount(@NonNull final String userId,
                                    @NonNull final String password,
                                    final boolean withInitialSync,
                                    final boolean enableCrypto) {
        final Context context = InstrumentationRegistry.getContext();
        final MXSession session = mTestHelper.createAccountAndSync(
                context,
                userId + System.currentTimeMillis() + UUID.randomUUID(),
                password,
                withInitialSync,
                enableCrypto
        );
        Assert.assertNotNull(session);
        return session;
    }

    /**
     * Logs into an existing account
     *
     * @param userId          the userId to log in
     * @param password        the password to log in
     * @param withInitialSync true to perform an initial sync
     * @param enableCrypto    true to set enableCryptoWhenStarting
     * @return the session associated with the existing account
     */
    private MXSession logIntoAccount(@NonNull final String userId,
                                     @NonNull final String password,
                                     final boolean withInitialSync,
                                     final boolean enableCrypto) {
        final Context context = InstrumentationRegistry.getContext();
        final MXSession session = mTestHelper.logAccountAndSync(context, userId, password, withInitialSync, enableCrypto);
        Assert.assertNotNull(session);
        return session;
    }
}
