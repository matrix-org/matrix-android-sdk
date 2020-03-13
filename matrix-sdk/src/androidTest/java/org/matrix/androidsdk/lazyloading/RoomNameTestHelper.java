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

import junit.framework.Assert;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.common.CommonTestHelper;
import org.matrix.androidsdk.common.SessionTestParams;
import org.matrix.androidsdk.common.TestApiCallback;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import javax.annotation.Nullable;

/**
 *
 */
public class RoomNameTestHelper {

    private final CommonTestHelper mTestHelper;

    public RoomNameTestHelper(CommonTestHelper mCommonTestHelper) {
        mTestHelper = mCommonTestHelper;
    }

    /**
     * Create a base scenario for all room name tests
     *
     * @param nbOfUsers       nb of user to create and to invite in the room (min to 1)
     * @param roomName        initial room name, or null for no room name
     * @param withLazyLoading true to enable lazy loading for alice account
     * @return initialized data
     */
    public RoomNameScenarioData createScenario(final int nbOfUsers,
                                               @Nullable final String roomName,
                                               final boolean withLazyLoading) throws Exception {

        final SessionTestParams createSessionParams = new SessionTestParams(true);

        List<MXSession> createdSessions = new ArrayList<>(nbOfUsers);

        // Create all the accounts
        for (int i = 0; i < nbOfUsers; i++) {
            MXSession session = mTestHelper.createAccount("User_" + i, createSessionParams);

            createdSessions.add(session);
        }

        Assert.assertEquals(nbOfUsers, createdSessions.size());

        // First user create a Room

        final MXSession firstSession = createdSessions.get(0);

        final Map<String, String> results = new HashMap<>();
        CountDownLatch latch = new CountDownLatch(1);
        firstSession.createRoom(new TestApiCallback<String>(latch) {
            @Override
            public void onSuccess(String info) {
                results.put("roomId", info);
                super.onSuccess(info);
            }
        });
        mTestHelper.await(latch);

        final String roomId = results.get("roomId");
        final Room room = firstSession.getDataHandler().getRoom(roomId);

        // Update room name
        if (roomName != null) {
            latch = new CountDownLatch(1);
            room.updateName(roomName, new TestApiCallback<Void>(latch));
            mTestHelper.await(latch);
        }

        //update join rules
        latch = new CountDownLatch(1);
        room.updateJoinRules(RoomState.JOIN_RULE_PUBLIC, new TestApiCallback<Void>(latch));
        mTestHelper.await(latch);

        // TODO Test with invitations
        // all other users join the room
        for (int i = 1; i < nbOfUsers; i++) {
            latch = new CountDownLatch(1);
            createdSessions.get(i).joinRoom(roomId, new TestApiCallback<String>(latch));
            mTestHelper.await(latch);
        }

        //invite dave
        latch = new CountDownLatch(1);
        room.invite(firstSession, "@dave:localhost:8480", new TestApiCallback<Void>(latch));
        mTestHelper.await(latch);

        final SessionTestParams logSessionParams = new SessionTestParams(true, false, withLazyLoading);

        List<MXSession> loggedSessions = new ArrayList<>(nbOfUsers);

        // open new sessions, using the same user ids
        for (MXSession session : createdSessions) {
            MXSession loggedSession = mTestHelper.logIntoAccount(session.getMyUserId(), logSessionParams);

            loggedSessions.add(loggedSession);
        }

        // Clear created sessions (must be done after getting the user ids)
        mTestHelper.clearAllSessions(createdSessions);

        return new RoomNameScenarioData(loggedSessions, roomId);
    }

    /**
     * Clear all sessions in room name scenario data
     *
     * @param data
     */
    public void clearAllSessions(RoomNameScenarioData data) {
        mTestHelper.clearAllSessions(data.userSessions);
    }
}
