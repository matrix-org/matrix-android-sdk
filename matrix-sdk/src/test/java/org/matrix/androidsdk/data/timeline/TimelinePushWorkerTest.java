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

package org.matrix.androidsdk.data.timeline;

import com.google.gson.Gson;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.matrix.androidsdk.MXDataHandler;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.bingrules.BingRule;
import org.matrix.androidsdk.util.BingRulesManager;
import org.matrix.androidsdk.util.JsonUtils;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

import java.util.HashMap;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
public class TimelinePushWorkerTest {

    @Mock
    MXDataHandler mDataHandler;
    @Mock
    BingRulesManager mBingRulesManager;
    private TimelinePushWorker mTimelinePushWorker;


    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        Mockito.when(mDataHandler.getBingRulesManager()).thenReturn(mBingRulesManager);
        mTimelinePushWorker = new TimelinePushWorker(mDataHandler);
    }

    @Test
    public void triggerPush_WhenEventBingRuleDoesNotAllowNotify_ShouldNotTriggerPush() {
        final BingRule bingRule = Mockito.mock(BingRule.class);
        Mockito.when(bingRule.shouldNotify()).thenReturn(false);
        Mockito.when(mBingRulesManager.fulfilledBingRule(Mockito.any(Event.class))).thenReturn(bingRule);
        final Event event = new Event();
        final RoomState roomState = new RoomState();
        mTimelinePushWorker.triggerPush(roomState, event);
        Mockito.verify(mDataHandler, Mockito.never()).onBingEvent(event, roomState, bingRule);
    }

    @Test
    public void triggerPush_WhenEventHasNoLifetime_ShouldTriggerPush() {
        final BingRule bingRule = Mockito.mock(BingRule.class);
        Mockito.when(bingRule.shouldNotify()).thenReturn(true);
        Mockito.when(mBingRulesManager.fulfilledBingRule(Mockito.any(Event.class))).thenReturn(bingRule);
        final Event event = new Event();
        final RoomState roomState = new RoomState();
        mTimelinePushWorker.triggerPush(roomState, event);
        Mockito.verify(mDataHandler).onBingEvent(event, roomState, bingRule);
    }

    @Test
    public void triggerPush_WhenMaxLifetimeIsReached_ShouldNotTriggerPush() {
        final Gson gson = JsonUtils.getBasicGson();
        final BingRule bingRule = Mockito.mock(BingRule.class);
        Mockito.when(bingRule.shouldNotify()).thenReturn(true);
        Mockito.when(mBingRulesManager.fulfilledBingRule(Mockito.any(Event.class))).thenReturn(bingRule);
        final Event event = new Event();
        Map<String, String> contentMap = new HashMap<>();
        contentMap.put("lifetime", "10000");
        event.content = gson.toJsonTree(contentMap);
        event.originServerTs = System.currentTimeMillis() - 50000;
        final RoomState roomState = new RoomState();
        mTimelinePushWorker.triggerPush(roomState, event);
        Mockito.verify(mDataHandler, Mockito.never()).onBingEvent(event, roomState, bingRule);
    }

    @Test
    public void triggerPush_WhenCallTimeoutIsReached_ShouldNotTriggerPush() {
        final BingRule bingRule = Mockito.mock(BingRule.class);
        Mockito.when(bingRule.shouldNotify()).thenReturn(true);
        Mockito.when(mBingRulesManager.fulfilledBingRule(Mockito.any(Event.class))).thenReturn(bingRule);
        final Event event = new Event();
        event.type = Event.EVENT_TYPE_CALL_INVITE;
        event.originServerTs = System.currentTimeMillis() - 124000;
        final RoomState roomState = new RoomState();
        mTimelinePushWorker.triggerPush(roomState, event);
        Mockito.verify(mDataHandler, Mockito.never()).onBingEvent(event, roomState, bingRule);
    }

    @Test
    public void triggerPush_WhenCallTimeoutIsNotReached_ShouldTriggerPush() {
        final BingRule bingRule = Mockito.mock(BingRule.class);
        Mockito.when(bingRule.shouldNotify()).thenReturn(true);
        Mockito.when(mBingRulesManager.fulfilledBingRule(Mockito.any(Event.class))).thenReturn(bingRule);
        final Event event = new Event();
        event.type = Event.EVENT_TYPE_CALL_INVITE;
        event.originServerTs = System.currentTimeMillis() - 8000;
        final RoomState roomState = new RoomState();
        mTimelinePushWorker.triggerPush(roomState, event);
        Mockito.verify(mDataHandler).onBingEvent(event, roomState, bingRule);
    }
}
