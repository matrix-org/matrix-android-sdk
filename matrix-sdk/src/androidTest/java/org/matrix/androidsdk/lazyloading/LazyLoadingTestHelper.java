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
import android.support.test.InstrumentationRegistry;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.common.CommonTestHelper;
import org.matrix.androidsdk.common.TestApiCallback;
import org.matrix.androidsdk.common.TestConstants;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.rest.model.Event;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
     * - Alice, Bob in a room
     * - Charlie joins the room
     * - Dave is invited
     * - Alice sends 50 messages
     * - Bob sends one message
     * - Alice sends 50 messages
     * - Alice makes an initial /sync with lazy-loading enabled or not
     *
     * @return initialized data
     */
    public LazyLoadingScenarioData createScenario() throws Exception {
        MXSession bobSession = mTestHelper.createBobAccount(true, false);
        MXSession aliceSession = mTestHelper.createAliceAccount(true, false);
        MXSession samSession = mTestHelper.createSamAccount(true, false);

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
        latch.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);

        final String roomId = results.get("roomId");
        final Room bobRoom = bobSession.getDataHandler().getRoom(roomId);

        //update name and join rules
        latch = new CountDownLatch(1);
        bobRoom.updateName("LazyLoading Test Room", new TestApiCallback<Void>(latch));
        latch.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);

        latch = new CountDownLatch(1);
        bobRoom.updateJoinRules(RoomState.JOIN_RULE_PUBLIC, new TestApiCallback<Void>(latch));
        latch.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);

        // sam join
        latch = new CountDownLatch(1);
        samSession.joinRoom(roomId, new TestApiCallback<String>(latch));
        latch.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);

        //alice join
        latch = new CountDownLatch(1);
        aliceSession.joinRoom(roomId, new TestApiCallback<String>(latch));
        latch.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);

        final Room aliceRoom = aliceSession.getDataHandler().getStore().getRoom(roomId);

        //invite dave
        latch = new CountDownLatch(1);
        bobRoom.invite("@dave:localhost:8480", new TestApiCallback<Void>(latch));
        latch.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);

        // Send messages
        final List<Event> aliceFirstMessages = mTestHelper.sendTextMessage(aliceRoom, "Alice message", 50);
        final List<Event> bobMessages = mTestHelper.sendTextMessage(bobRoom, "Bob message", 1);
        final List<Event> aliceLastMessages = mTestHelper.sendTextMessage(aliceRoom, "Alice message", 50);
        final String bobMessageId = bobMessages.isEmpty() ? null : bobMessages.get(0).eventId;

        // Clear Alice session and open new one
        final Context context = InstrumentationRegistry.getContext();
        aliceSession.clear(context);
        bobSession.clear(context);
        samSession.clear(context);

        aliceSession = mTestHelper.logIntoAliceAccount(aliceId, false, false);
        bobSession = mTestHelper.logIntoBobAccount(bobId, false, false);
        samSession = mTestHelper.logIntoSamAccount(samId, false, false);
        return new LazyLoadingScenarioData(aliceSession, bobSession, samSession, roomId, bobMessageId);

    }

}
