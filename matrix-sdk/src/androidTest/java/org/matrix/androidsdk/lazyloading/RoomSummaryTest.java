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
import org.junit.Test;
import org.matrix.androidsdk.RestClient;
import org.matrix.androidsdk.common.CommonTestHelper;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.data.RoomSummary;

public class RoomSummaryTest {

    private CommonTestHelper mTestHelper = new CommonTestHelper();
    private LazyLoadingTestHelper mLazyLoadingTestHelper = new LazyLoadingTestHelper(mTestHelper);


    @BeforeClass
    public static void init() {
        RestClient.initUserAgent(InstrumentationRegistry.getContext());
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
        Assert.assertEquals(3, roomSummary.getNumberOfJoinedMembers());
        Assert.assertEquals(1, roomSummary.getNumberOfInvitedMembers());
    }

    @Test
    public void RoomSummary_DisplayNameFromHeroes_LazyLoadedMembers() throws Exception {
        RoomSummary_DisplayNameFromHeroes(true);
    }

    @Test
    public void RoomSummary_DisplayNameFromHeroes_LoadAllMembers() throws Exception {
        RoomSummary_DisplayNameFromHeroes(false);
    }

    private void RoomSummary_DisplayNameFromHeroes(boolean withLazyLoading) throws Exception {
        final LazyLoadingScenarioData data = mLazyLoadingTestHelper.createScenario(withLazyLoading);
        mTestHelper.syncSession(data.aliceSession, false);
        final Room room = data.aliceSession.getDataHandler().getStore().getRoom(data.roomId);
        final RoomState roomState = room.getState();

        Assert.assertNotNull(roomState.getMember(data.bobSession.getMyUserId()));
        Assert.assertNotNull(roomState.getMember(data.aliceSession.getMyUserId()));
        Assert.assertNotNull(roomState.getMember(data.samSession.getMyUserId()));
        Assert.assertEquals(withLazyLoading, roomState.name != null);
    }


}
