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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.matrix.androidsdk.common.CommonTestHelper;
import org.matrix.androidsdk.common.TestConstants;
import org.matrix.androidsdk.data.EventTimeline;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.Event;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class RoomStateTest {

    private CommonTestHelper mTestHelper = new CommonTestHelper();
    private LazyLoadingTestHelper mLazyLoadingTestHelper = new LazyLoadingTestHelper(mTestHelper);

    @Test
    public void RoomState_InitialSync_ShouldLoadAllMembers() throws Exception {
        RoomState_InitialSync(false);
    }

    @Test
    public void RoomState_InitialSync_ShouldLoadAllMembers_LL() throws Exception {
        RoomState_InitialSync(true);
    }

    private void RoomState_InitialSync(final boolean withLazyLoading) throws Exception {
        final LazyLoadingScenarioData data = mLazyLoadingTestHelper.createScenario(withLazyLoading);
        mTestHelper.syncSession(data.aliceSession, false);
        final Room aliceRoom = data.aliceSession.getDataHandler().getRoom(data.roomId);

        if (withLazyLoading) {
            Assert.assertEquals(1, aliceRoom.getState().getLoadedMembers().size());
        } else {
            Assert.assertEquals(4, aliceRoom.getState().getLoadedMembers().size());
        }

        Assert.assertEquals(1, aliceRoom.getNumberOfInvitedMembers());
        Assert.assertEquals(3, aliceRoom.getNumberOfJoinedMembers());
    }

    @Test
    public void RoomState_IncomingMessage_ShouldLoadAllMembers() throws Exception {
        RoomState_IncomingMessage(false);
    }

    @Test
    public void RoomState_IncomingMessage_ShouldLoadAllMembers_LL() throws Exception {
        RoomState_IncomingMessage(true);
    }

    private void RoomState_IncomingMessage(final boolean withLazyLoading) throws Exception {
        final LazyLoadingScenarioData data = mLazyLoadingTestHelper.createScenario(withLazyLoading);
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
        mTestHelper.await(lock);

        if (withLazyLoading) {
            // Sam is now loaded
            Assert.assertEquals(2, aliceRoom.getState().getLoadedMembers().size());
        } else {
            Assert.assertEquals(4, aliceRoom.getState().getLoadedMembers().size());
        }

        Assert.assertEquals(1, aliceRoom.getNumberOfInvitedMembers());
        Assert.assertEquals(3, aliceRoom.getNumberOfJoinedMembers());
    }

    @Test
    public void RoomState_BackPaginate_ShouldLoadAllMembers() throws Exception {
        RoomState_BackPaginate(false);
    }

    @Test
    public void RoomState_BackPaginate_ShouldLoadAllMembers_LL() throws Exception {
        RoomState_BackPaginate(true);
    }

    private void RoomState_BackPaginate(final boolean withLazyLoading) throws Exception {
        final LazyLoadingScenarioData data = mLazyLoadingTestHelper.createScenario(withLazyLoading);
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
                    Assert.assertNotNull(liveTimeline.getState().getMember(data.aliceSession.getMyUserId()));

                    // With LazyLoading, Bob and Sam are not known by Alice yet
                    Assert.assertEquals(withLazyLoading, liveTimeline.getState().getMember(data.bobSession.getMyUserId()) == null);
                    Assert.assertEquals(withLazyLoading, liveTimeline.getState().getMember(data.samSession.getMyUserId()) == null);
                    Assert.assertEquals(withLazyLoading, roomState.getMember(data.bobSession.getMyUserId()) == null);
                    Assert.assertEquals(withLazyLoading, roomState.getMember(data.samSession.getMyUserId()) == null);
                } else if (messageCount == 51) {
                    // Bob is known now
                    Assert.assertNotNull(liveTimeline.getState().getMember(data.bobSession.getMyUserId()));

                    // With LazyLoading, Sam is not known by Alice yet
                    Assert.assertEquals(withLazyLoading, liveTimeline.getState().getMember(data.samSession.getMyUserId()) == null);
                    Assert.assertEquals(withLazyLoading, roomState.getMember(data.samSession.getMyUserId()) == null);
                } else if (messageCount >= 110) {
                    // All users are known now
                    Assert.assertNotNull(liveTimeline.getState().getMember(data.aliceSession.getMyUserId()));
                    Assert.assertNotNull(liveTimeline.getState().getMember(data.bobSession.getMyUserId()));
                    Assert.assertNotNull(liveTimeline.getState().getMember(data.samSession.getMyUserId()));

                    lock.countDown();
                }
            }
        });
        recursiveBackPaginate(liveTimeline, 0, 30, 120);
        mTestHelper.await(lock);
    }

    @Test
    public void RoomState_Permalink_ShouldLoadAllMembers() throws Exception {
        RoomState_Permalink(false);
    }

    @Test
    public void RoomState_Permalink_ShouldLoadAllMembers_LL() throws Exception {
        RoomState_Permalink(true);
    }

    private void RoomState_Permalink(final boolean withLazyLoading) throws Exception {
        final LazyLoadingScenarioData data = mLazyLoadingTestHelper.createScenario(withLazyLoading);
        mTestHelper.syncSession(data.aliceSession, false);
        final Room aliceRoom = data.aliceSession.getDataHandler().getRoom(data.roomId);
        final Event lastEvent = aliceRoom.getDataHandler().getStore().getLatestEvent(data.roomId);
        final EventTimeline eventTimeline = new EventTimeline(data.aliceSession.getDataHandler(), lastEvent.roomId, lastEvent.eventId);
        final CountDownLatch lock = new CountDownLatch(1);
        eventTimeline.resetPaginationAroundInitialEvent(10, new SimpleApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                lock.countDown();
            }
        });
        mTestHelper.await(lock);
        final RoomState roomState = eventTimeline.getState();

        if (withLazyLoading) {
            Assert.assertEquals(1, roomState.getLoadedMembers().size());
        } else {
            Assert.assertEquals(4, roomState.getLoadedMembers().size());
        }

        Assert.assertEquals(1, aliceRoom.getNumberOfInvitedMembers());
        Assert.assertEquals(3, aliceRoom.getNumberOfJoinedMembers());
    }

    @Test
    public void RoomState_PermalinkWithBackPagination_ShouldLoadAllMembers() throws Exception {
        RoomState_PermalinkWithBackPagination(false);
    }

    @Test
    public void RoomState_PermalinkWithBackPagination_ShouldLoadAllMembers_LL() throws Exception {
        RoomState_PermalinkWithBackPagination(true);
    }

    private void RoomState_PermalinkWithBackPagination(final boolean withLazyLoading) throws Exception {
        final LazyLoadingScenarioData data = mLazyLoadingTestHelper.createScenario(withLazyLoading);
        mTestHelper.syncSession(data.aliceSession, false);
        final Room aliceRoom = data.aliceSession.getDataHandler().getRoom(data.roomId);
        final CountDownLatch lock = new CountDownLatch(1);
        final Event lastEvent = aliceRoom.getDataHandler().getStore().getLatestEvent(data.roomId);
        final EventTimeline eventTimeline = new EventTimeline(data.aliceSession.getDataHandler(), lastEvent.roomId, lastEvent.eventId);
        eventTimeline.addEventTimelineListener(new EventTimeline.EventTimelineListener() {
            int messageCount = 0;

            @Override
            public void onEvent(Event event, EventTimeline.Direction direction, RoomState roomState) {
                messageCount++;
                if (messageCount == 50) {
                    Assert.assertNotNull(eventTimeline.getState().getMember(data.aliceSession.getMyUserId()));

                    // With LazyLoading, Bob and Sam are not known by Alice yet
                    Assert.assertEquals(withLazyLoading, eventTimeline.getState().getMember(data.bobSession.getMyUserId()) == null);
                    Assert.assertEquals(withLazyLoading, eventTimeline.getState().getMember(data.samSession.getMyUserId()) == null);
                    Assert.assertEquals(withLazyLoading, roomState.getMember(data.bobSession.getMyUserId()) == null);
                    Assert.assertEquals(withLazyLoading, roomState.getMember(data.samSession.getMyUserId()) == null);
                } else if (messageCount == 51) {
                    // Bob is known now
                    Assert.assertNotNull(eventTimeline.getState().getMember(data.bobSession.getMyUserId()));

                    // With LazyLoading, Sam is not known by Alice yet
                    Assert.assertEquals(withLazyLoading, eventTimeline.getState().getMember(data.samSession.getMyUserId()) == null);
                    Assert.assertEquals(withLazyLoading, roomState.getMember(data.samSession.getMyUserId()) == null);
                } else if (messageCount >= 110) {
                    // All users are known now
                    Assert.assertNotNull(eventTimeline.getState().getMember(data.aliceSession.getMyUserId()));
                    Assert.assertNotNull(eventTimeline.getState().getMember(data.bobSession.getMyUserId()));
                    Assert.assertNotNull(eventTimeline.getState().getMember(data.samSession.getMyUserId()));

                    lock.countDown();
                }
            }
        });
        eventTimeline.resetPaginationAroundInitialEvent(0, new SimpleApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                recursiveBackPaginate(eventTimeline, 0, 30, 120);
            }
        });
        mTestHelper.await(lock);
    }

    @Test
    public void RoomState_PermalinkWithForwardPagination_ShouldLoadAllMembers() throws Exception {
        RoomState_PermalinkWithForwardPagination(false);
    }

    @Test
    public void RoomState_PermalinkWithForwardPagination_ShouldLoadAllMembers_LL() throws Exception {
        RoomState_PermalinkWithForwardPagination(true);
    }

    // Test lazy loaded members sent by the HS when paginating forward
    // - Come back to Bob message
    // - We should only know Bob membership
    // - Paginate forward to get Alice next message
    // - We should know Alice membership now
    private void RoomState_PermalinkWithForwardPagination(final boolean withLazyLoading) throws Exception {
        final LazyLoadingScenarioData data = mLazyLoadingTestHelper.createScenario(withLazyLoading);
        mTestHelper.syncSession(data.aliceSession, false);
        final CountDownLatch lock = new CountDownLatch(1);
        final EventTimeline eventTimeline = new EventTimeline(data.aliceSession.getDataHandler(), data.roomId, data.bobMessageId);
        eventTimeline.addEventTimelineListener(new EventTimeline.EventTimelineListener() {
            int messageCount = 0;

            @Override
            public void onEvent(Event event, EventTimeline.Direction direction, RoomState roomState) {
                messageCount++;
                if (messageCount == 1) {
                    // We received the Event from bob
                    Assert.assertEquals(event.sender, data.bobSession.getMyUserId());

                    // Bob is known
                    Assert.assertNotNull(eventTimeline.getState().getMember(data.bobSession.getMyUserId()));

                    // With LazyLoading, Alice and Sam are not known by Alice yet
                    Assert.assertEquals(withLazyLoading, eventTimeline.getState().getMember(data.aliceSession.getMyUserId()) == null);
                    Assert.assertEquals(withLazyLoading, eventTimeline.getState().getMember(data.samSession.getMyUserId()) == null);
                } else if (messageCount == 2) {
                    // We received the Event from Alice
                    Assert.assertEquals(event.sender, data.aliceSession.getMyUserId());

                    // Alice and Bob are known
                    Assert.assertNotNull(eventTimeline.getState().getMember(data.aliceSession.getMyUserId()));
                    Assert.assertNotNull(eventTimeline.getState().getMember(data.bobSession.getMyUserId()));

                    // With LazyLoading, Sam is not known by Alice yet
                    Assert.assertEquals(withLazyLoading, eventTimeline.getState().getMember(data.samSession.getMyUserId()) == null);

                    lock.countDown();
                }
            }
        });
        eventTimeline.resetPaginationAroundInitialEvent(0, new SimpleApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                eventTimeline.forwardPaginate(new SimpleApiCallback<Integer>() {
                    @Override
                    public void onSuccess(Integer info) {
                        // Ignore
                    }
                });
            }
        });
        mTestHelper.await(lock);
    }

    /**
     * This method run backPagination until the maxEventCount is reached.
     *
     * @param timeline          the timeline upon which back pagination is made
     * @param currentEventCount the current event count, start at 0
     * @param eventCountStep    the step between each method invoke
     * @param maxEventCount     the max event count to reach before stopping
     */
    private void recursiveBackPaginate(final EventTimeline timeline, final int currentEventCount, final int eventCountStep, final int maxEventCount) {
        timeline.backPaginate(eventCountStep, new SimpleApiCallback<Integer>() {
            @Override
            public void onSuccess(Integer info) {
                int realStep = Math.min(eventCountStep, info);
                int newEventCount = currentEventCount + realStep;
                if (newEventCount < maxEventCount) {
                    recursiveBackPaginate(timeline, newEventCount, eventCountStep, maxEventCount);
                }
            }
        });
    }
}
