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

import android.support.test.InstrumentationRegistry;

import junit.framework.Assert;

import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.RestClient;
import org.matrix.androidsdk.common.CommonTestHelper;
import org.matrix.androidsdk.common.TestApiCallback;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomSummary;

import java.util.concurrent.CountDownLatch;

@FixMethodOrder(MethodSorters.JVM)
public class RoomSummaryTest {

    private CommonTestHelper mTestHelper = new CommonTestHelper();
    private LazyLoadingTestHelper mLazyLoadingTestHelper = new LazyLoadingTestHelper(mTestHelper);


    @BeforeClass
    public static void init() {
        MXSession.initUserAgent(InstrumentationRegistry.getContext(), null);
    }

    @Test
    public void RoomSummary_CheckMembership_LazyLoadedMembers() throws Exception {
        RoomSummary_CheckMembership(true);
    }

    @Test
    public void RoomSummary_CheckMembership_LoadAllMembers() throws Exception {
        RoomSummary_CheckMembership(false);
    }

    private void RoomSummary_CheckMembership(boolean withLazyLoading) throws Exception {
        final LazyLoadingScenarioData data = mLazyLoadingTestHelper.createScenario(withLazyLoading);
        mTestHelper.syncSession(data.aliceSession, false);
        final Room aliceRoom = data.aliceSession.getDataHandler().getRoom(data.roomId);
        final RoomSummary aliceRoomSummary = aliceRoom.getRoomSummary();
        Assert.assertNotNull(aliceRoomSummary);
        Assert.assertTrue(aliceRoomSummary.isJoined());

        mTestHelper.syncSession(data.samSession, false);
        final Room samRoom = data.samSession.getDataHandler().getRoom(data.roomId);
        final RoomSummary samRoomSummary = samRoom.getRoomSummary();
        Assert.assertNotNull(samRoomSummary);
        Assert.assertTrue(samRoomSummary.isJoined());
        mLazyLoadingTestHelper.clearAllSessions(data);
    }

    @Test
    public void RoomSummary_MemberCount_LazyLoadedMembers() throws Exception {
        RoomSummary_MemberCount(true);
    }

    @Test
    public void RoomSummary_MemberCount_LoadAllMembers() throws Exception {
        RoomSummary_MemberCount(false);
    }

    private void RoomSummary_MemberCount(boolean withLazyLoading) throws Exception {
        final LazyLoadingScenarioData data = mLazyLoadingTestHelper.createScenario(withLazyLoading);
        mTestHelper.syncSession(data.aliceSession, false);
        final RoomSummary roomSummary = data.aliceSession.getDataHandler().getStore().getSummary(data.roomId);
        Assert.assertNotNull(roomSummary);

        if (withLazyLoading) {
            Assert.assertEquals(3, roomSummary.getNumberOfJoinedMembers());
            Assert.assertEquals(1, roomSummary.getNumberOfInvitedMembers());
        } else {
            // Without lazy loading the room summary does not contains this data yet
            Assert.assertEquals(0, roomSummary.getNumberOfJoinedMembers());
            Assert.assertEquals(0, roomSummary.getNumberOfInvitedMembers());
        }
        mLazyLoadingTestHelper.clearAllSessions(data);
    }

    @Test
    public void RoomSummary_CheckRoomSummaryIsNullAfterLeavingFromAnotherDevice_LazyLoadedMembers() throws Exception {
        RoomSummary_CheckRoomSummaryIsNullAfterLeavingFromAnotherDevice(true);
    }

    @Test
    public void RoomSummary_CheckRoomSummaryIsNullAfterLeavingFromAnotherDevice_LoadAllMembers() throws Exception {
        RoomSummary_CheckRoomSummaryIsNullAfterLeavingFromAnotherDevice(false);
    }

    private void RoomSummary_CheckRoomSummaryIsNullAfterLeavingFromAnotherDevice(boolean withLazyLoading) throws Exception {
        final LazyLoadingScenarioData data = mLazyLoadingTestHelper.createScenario(withLazyLoading);
        final CountDownLatch lock = new CountDownLatch(1);
        data.aliceSession.getDataHandler().getDataRetriever().getRoomsRestClient().leaveRoom(data.roomId, new TestApiCallback<Void>(lock));
        mTestHelper.await(lock);
        mTestHelper.syncSession(data.bobSession, false);
        final Room bobRoom = data.bobSession.getDataHandler().getRoom(data.roomId);
        mTestHelper.sendTextMessage(bobRoom, "New bob message", 50);
        mTestHelper.syncSession(data.aliceSession, false);
        final RoomSummary roomSummary = data.aliceSession.getDataHandler().getStore().getSummary(data.roomId);
        Assert.assertNull(roomSummary);
        mLazyLoadingTestHelper.clearAllSessions(data);
    }

}
