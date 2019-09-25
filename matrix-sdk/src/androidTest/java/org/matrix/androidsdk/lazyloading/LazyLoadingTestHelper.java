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
package org.matrix.androidsdk.lazyloading;

import android.content.Context;
import androidx.test.InstrumentationRegistry;

import junit.framework.Assert;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.common.CommonTestHelper;
import org.matrix.androidsdk.common.SessionTestParams;
import org.matrix.androidsdk.common.TestApiCallback;
import org.matrix.androidsdk.common.TestConstants;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.rest.model.Event;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 *
 */
public class LazyLoadingTestHelper {

    private final CommonTestHelper mTestHelper;

    public LazyLoadingTestHelper(CommonTestHelper mCommonTestHelper) {
        mTestHelper = mCommonTestHelper;
    }

    /**
     * Create a base scenario for all lazy loading tests
     * Common initial conditions:
     * - Bob create a public room named "LazyLoading Test Room"
     * - Sam and Alice join the room
     * - Dave is invited
     * - Alice sends 50 messages
     * - Bob sends 1 message
     * - Alice sends 50 messages
     * - Alice makes an initial /sync with lazy-loading enabled or not
     *
     * @param withLazyLoading true to enable lazy loading for Alice, Bob and Sam accounts
     * @return initialized data
     */
    public LazyLoadingScenarioData createScenario(boolean withLazyLoading) throws Exception {

        final SessionTestParams createSessionParams = new SessionTestParams(true);
        MXSession aliceSession = mTestHelper.createAccount(TestConstants.USER_ALICE, createSessionParams);
        MXSession bobSession = mTestHelper.createAccount(TestConstants.USER_BOB, createSessionParams);
        MXSession samSession = mTestHelper.createAccount(TestConstants.USER_SAM, createSessionParams);

        final String aliceId = aliceSession.getMyUserId();
        final String bobId = bobSession.getMyUserId();
        final String samId = samSession.getMyUserId();

        final Map<String, String> results = new HashMap<>();
        CountDownLatch latch = new CountDownLatch(1);
        bobSession.createRoom(new TestApiCallback<String>(latch) {
            @Override
            public void onSuccess(String info) {
                results.put("roomId", info);
                super.onSuccess(info);
            }
        });
        mTestHelper.await(latch);

        final String roomId = results.get("roomId");
        final Room bobRoom = bobSession.getDataHandler().getRoom(roomId);

        //update name and join rules
        latch = new CountDownLatch(1);
        bobRoom.updateName("LazyLoading Test Room", new TestApiCallback<Void>(latch));
        mTestHelper.await(latch);

        latch = new CountDownLatch(1);
        bobRoom.updateJoinRules(RoomState.JOIN_RULE_PUBLIC, new TestApiCallback<Void>(latch));
        mTestHelper.await(latch);

        // sam join
        latch = new CountDownLatch(1);
        samSession.joinRoom(roomId, new TestApiCallback<String>(latch));
        mTestHelper.await(latch);

        //alice join
        latch = new CountDownLatch(1);
        aliceSession.joinRoom(roomId, new TestApiCallback<String>(latch));
        mTestHelper.await(latch);

        final Room aliceRoom = aliceSession.getDataHandler().getStore().getRoom(roomId);

        //invite dave
        latch = new CountDownLatch(1);
        bobRoom.invite(bobSession,"@dave:localhost:8480", new TestApiCallback<Void>(latch));
        mTestHelper.await(latch);

        // Send messages
        final List<Event> aliceFirstMessages = mTestHelper.sendTextMessage(aliceRoom, "Alice message", 50);
        final List<Event> bobMessages = mTestHelper.sendTextMessage(bobRoom, "Bob message", 1);
        final List<Event> aliceLastMessages = mTestHelper.sendTextMessage(aliceRoom, "Alice message", 50);

        Assert.assertEquals(50, aliceFirstMessages.size());
        Assert.assertEquals(1, bobMessages.size());
        Assert.assertEquals(50, aliceLastMessages.size());

        final String bobMessageId = bobMessages.isEmpty() ? null : bobMessages.get(0).eventId;

        // Clear sessions and open new ones
        final Context context = InstrumentationRegistry.getContext();
        aliceSession.clear(context);
        bobSession.clear(context);
        samSession.clear(context);


        final SessionTestParams logSessionParams = new SessionTestParams(false, false, withLazyLoading);
        aliceSession = mTestHelper.logIntoAccount(aliceId, logSessionParams);
        bobSession = mTestHelper.logIntoAccount(bobId, logSessionParams);
        samSession = mTestHelper.logIntoAccount(samId, logSessionParams);
        return new LazyLoadingScenarioData(aliceSession, bobSession, samSession, roomId, bobMessageId);
    }

    /**
     * Clear all non null sessions in lazy loading scenario data
     *
     * @param data
     */
    public void clearAllSessions(LazyLoadingScenarioData data) {
        List<MXSession> sessionsToClear = new ArrayList<>();
        if (data.aliceSession != null) {
            sessionsToClear.add(data.aliceSession);
        }
        if (data.bobSession != null) {
            sessionsToClear.add(data.bobSession);
        }
        if (data.samSession != null) {
            sessionsToClear.add(data.samSession);
        }

        mTestHelper.clearAllSessions(sessionsToClear);
    }
}
