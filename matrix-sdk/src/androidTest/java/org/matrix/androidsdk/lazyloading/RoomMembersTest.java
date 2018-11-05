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

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.RestClient;
import org.matrix.androidsdk.common.CommonTestHelper;
import org.matrix.androidsdk.common.TestApiCallback;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.rest.model.RoomMember;

import java.util.List;
import java.util.concurrent.CountDownLatch;

@FixMethodOrder(MethodSorters.JVM)
public class RoomMembersTest {

    private CommonTestHelper mTestHelper = new CommonTestHelper();
    private LazyLoadingTestHelper mLazyLoadingTestHelper = new LazyLoadingTestHelper(mTestHelper);

    @BeforeClass
    public static void init() {
        MXSession.initUserAgent(InstrumentationRegistry.getContext(), null);
    }

    @Test
    public void RoomMembers_CheckTotalCountAsync_ShouldLoadAllMembers() throws Exception {
        RoomMembers_CheckTotalCountAsync(false);
    }

    @Test
    public void RoomMembers_CheckTotalCountAsync_LazyLoadedMembers() throws Exception {
        RoomMembers_CheckTotalCountAsync(true);
    }

    private void RoomMembers_CheckTotalCountAsync(final boolean withLazyLoading) throws Exception {
        final LazyLoadingScenarioData data = mLazyLoadingTestHelper.createScenario(withLazyLoading);
        mTestHelper.syncSession(data.aliceSession, false);
        final Room aliceRoom = data.aliceSession.getDataHandler().getRoom(data.roomId);
        final CountDownLatch lock = new CountDownLatch(1);
        aliceRoom.getMembersAsync(new TestApiCallback<List<RoomMember>>(lock) {
            @Override
            public void onSuccess(List<RoomMember> roomMembers) {
                Assert.assertEquals(4, roomMembers.size());
                super.onSuccess(roomMembers);
            }
        });
        mTestHelper.await(lock);
        mLazyLoadingTestHelper.clearAllSessions(data);
    }

    @Test
    public void RoomMembers_CheckActiveCountAsync_ShouldLoadAllMembers() throws Exception {
        RoomMembers_CheckActiveCountAsync(false);
    }

    @Test
    public void RoomMembers_CheckActiveCountAsync_LazyLoadedMembers() throws Exception {
        RoomMembers_CheckActiveCountAsync(true);
    }

    private void RoomMembers_CheckActiveCountAsync(final boolean withLazyLoading) throws Exception {
        final LazyLoadingScenarioData data = mLazyLoadingTestHelper.createScenario(withLazyLoading);
        mTestHelper.syncSession(data.aliceSession, false);
        final Room aliceRoom = data.aliceSession.getDataHandler().getRoom(data.roomId);
        final CountDownLatch lock = new CountDownLatch(1);
        aliceRoom.getActiveMembersAsync(new TestApiCallback<List<RoomMember>>(lock) {
            @Override
            public void onSuccess(List<RoomMember> roomMembers) {
                Assert.assertEquals(4, roomMembers.size());
                super.onSuccess(roomMembers);
            }
        });
        mTestHelper.await(lock);
        mLazyLoadingTestHelper.clearAllSessions(data);
    }

    @Test
    public void RoomMembers_CheckJoinedCountAsync_ShouldLoadAllMembers() throws Exception {
        RoomMembers_CheckJoinedCountAsync(false);
    }

    @Test
    public void RoomMembers_CheckJoinedCountAsync_LazyLoadedMembers() throws Exception {
        RoomMembers_CheckJoinedCountAsync(true);
    }

    private void RoomMembers_CheckJoinedCountAsync(final boolean withLazyLoading) throws Exception {
        final LazyLoadingScenarioData data = mLazyLoadingTestHelper.createScenario(withLazyLoading);
        mTestHelper.syncSession(data.aliceSession, false);
        final Room aliceRoom = data.aliceSession.getDataHandler().getRoom(data.roomId);
        final CountDownLatch lock = new CountDownLatch(1);
        aliceRoom.getJoinedMembersAsync(new TestApiCallback<List<RoomMember>>(lock) {
            @Override
            public void onSuccess(List<RoomMember> roomMembers) {
                Assert.assertEquals(3, roomMembers.size());
                super.onSuccess(roomMembers);
            }
        });
        mTestHelper.await(lock);
        mLazyLoadingTestHelper.clearAllSessions(data);
    }

    @Test
    public void RoomMembers_CheckAlreadyLoadedCount_ShouldLoadAllMembers() throws Exception {
        RoomMembers_CheckAlreadyLoadedCount(false);
    }

    @Test
    public void RoomMembers_CheckAlreadyLoadedCount_LazyLoadedMembers() throws Exception {
        RoomMembers_CheckAlreadyLoadedCount(true);
    }

    private void RoomMembers_CheckAlreadyLoadedCount(final boolean withLazyLoading) throws Exception {
        final LazyLoadingScenarioData data = mLazyLoadingTestHelper.createScenario(withLazyLoading);
        mTestHelper.syncSession(data.aliceSession, false);
        final Room aliceRoom = data.aliceSession.getDataHandler().getRoom(data.roomId);
        final List<RoomMember> members = aliceRoom.getState().getLoadedMembers();
        if (withLazyLoading) {
            Assert.assertEquals(1, members.size());
        } else {
            Assert.assertEquals(4, members.size());
        }
        mLazyLoadingTestHelper.clearAllSessions(data);
    }

}
