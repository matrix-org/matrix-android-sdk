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

import android.support.test.runner.AndroidJUnit4;

import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.matrix.androidsdk.common.CommonTestHelper;
import org.matrix.androidsdk.common.TestConstants;
import org.matrix.androidsdk.data.EventTimeline;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class RoomStateTest {

    private CommonTestHelper mTestHelper = new CommonTestHelper();
    private LazyLoadingTestHelper mLazyLoadingTestHelper = new LazyLoadingTestHelper(mTestHelper);

    @Test
    public void RoomState_InitialSync_ShouldLoadAllMembers() throws Exception {
        RoomState_InitialSync(false);
    }

    private void RoomState_InitialSync(final boolean withLazyLoading) throws Exception {
        final LazyLoadingScenarioData data = mLazyLoadingTestHelper.createScenario();
        mTestHelper.syncSession(data.aliceSession, false);
        final Room aliceRoom = data.aliceSession.getDataHandler().getRoom(data.roomId);
        if (withLazyLoading) {
            Assert.assertEquals(1, aliceRoom.getMembers().size());
            Assert.assertEquals(1, aliceRoom.getJoinedMembers().size());
        } else {
            Assert.assertEquals(4, aliceRoom.getMembers().size());
            Assert.assertEquals(3, aliceRoom.getJoinedMembers().size());
        }
    }

    @Test
    public void RoomState_IncomingMessage_ShouldLoadAllMembers() throws Exception {
        RoomState_IncomingMessage(false);
    }

    private void RoomState_IncomingMessage(final boolean withLazyLoading) throws Exception {
        final LazyLoadingScenarioData data = mLazyLoadingTestHelper.createScenario();
        mTestHelper.syncSession(data.aliceSession, false);
        mTestHelper.syncSession(data.samSession, false);
        final Room aliceRoom = data.aliceSession.getDataHandler().getRoom(data.roomId);
        final CountDownLatch lock = new CountDownLatch(1);
        aliceRoom.getLiveTimeLine().addEventTimelineListener(new EventTimeline.EventTimelineListener() {
            @Override
            public void onEvent(Event event, EventTimeline.Direction direction, RoomState roomState) {
                lock.countDown();
            }
        });
        final Room samRoom = data.samSession.getDataHandler().getRoom(data.roomId);
        mTestHelper.sendTextMessage(samRoom, "A message from Sam", 1);
        lock.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        if (withLazyLoading) {
            Assert.assertEquals(2, aliceRoom.getMembers().size());
            Assert.assertEquals(2, aliceRoom.getJoinedMembers().size());
        } else {
            Assert.assertEquals(4, aliceRoom.getMembers().size());
            Assert.assertEquals(3, aliceRoom.getJoinedMembers().size());
        }
    }

    @Test
    public void RoomState_BackPaginate_ShouldLoadAllMembers() throws Exception {
        RoomState_BackPaginate(false);
    }

    public void RoomState_BackPaginate(final boolean withLazyLoading) throws Exception {
        final LazyLoadingScenarioData data = mLazyLoadingTestHelper.createScenario();
        mTestHelper.syncSession(data.aliceSession, false);
        final Room aliceRoom = data.aliceSession.getDataHandler().getRoom(data.roomId);
        final CountDownLatch lock = new CountDownLatch(1);
        final EventTimeline liveTimeline = aliceRoom.getLiveTimeLine();
        liveTimeline.addEventTimelineListener(new EventTimeline.EventTimelineListener() {
            int messageCount = 0;

            @Override
            public void onEvent(Event event, EventTimeline.Direction direction, RoomState roomState) {
                messageCount++;
                if (messageCount == 50) {
                    if (withLazyLoading) {
                        Assert.assertNull(liveTimeline.getState().getMember(data.bobSession.getMyUserId()));
                        Assert.assertNull(liveTimeline.getState().getMember(data.samSession.getMyUserId()));
                        Assert.assertNull(roomState.getMember(data.bobSession.getMyUserId()));
                    }
                    Assert.assertNotNull(liveTimeline.getState().getMember(data.aliceSession.getMyUserId()));
                } else if (messageCount == 51) {
                    if (withLazyLoading) {
                        Assert.assertNull(roomState.getMember(data.samSession.getMyUserId()));
                        Assert.assertNull(liveTimeline.getState().getMember(data.samSession.getMyUserId()));
                    }
                    Assert.assertNotNull(liveTimeline.getState().getMember(data.bobSession.getMyUserId()));
                } else if (messageCount >= 110) {
                    Assert.assertNotNull(liveTimeline.getState().getMember(data.aliceSession.getMyUserId()));
                    Assert.assertNotNull(liveTimeline.getState().getMember(data.bobSession.getMyUserId()));
                    Assert.assertNotNull(liveTimeline.getState().getMember(data.samSession.getMyUserId()));
                    lock.countDown();
                }
            }
        });
        recursiveBackPaginate(liveTimeline, 0, 30, 120);
        boolean handled = lock.await(TestConstants.AWAIT_TIME_OUT_MILLIS, TimeUnit.MILLISECONDS);
        Assert.assertEquals(true, handled);
    }

    /**
     * This method run backPagination until the maxEventCount is reached.
     *
     * @param liveTimeline      the timeline upon which back pagination is launched
     * @param currentEventCount the current event count, start at 0
     * @param eventCountStep    the step between each method invoke
     * @param maxEventCount     the max event count to reach before stopping
     */
    private void recursiveBackPaginate(final EventTimeline liveTimeline, final int currentEventCount, final int eventCountStep, final int maxEventCount) {
        liveTimeline.backPaginate(eventCountStep, new SimpleApiCallback<Integer>() {
            @Override
            public void onSuccess(Integer info) {
                super.onSuccess(info);
                int realStep = Math.min(eventCountStep, info);
                int newEventCount = currentEventCount + realStep;
                if (newEventCount < maxEventCount) {
                    recursiveBackPaginate(liveTimeline, newEventCount, eventCountStep, maxEventCount);
                }
            }
        });
    }

}
