/* 
 * Copyright 2014 OpenMarket Ltd
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
package org.matrix.androidsdk.sync;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.ApiFailureCallback;
import org.matrix.androidsdk.rest.client.EventsRestClient;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.InitialSyncResponse;
import org.matrix.androidsdk.rest.model.TokensChunkResponse;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

import retrofit.RetrofitError;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Class for unit testing EventsThread.
 */
@RunWith(RobolectricTestRunner.class)
public class EventsThreadTest {

    // The events thread we're testing
    private EventsThread eventsThread;

    @Mock
    private EventsRestClient mockRestClient;
    @Mock
    private EventsThreadListener mockListener;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @After
    public void tearDown() {
        eventsThread.kill(); // Which hopefully works
    }

    /**
     * Set up normal behavior from initial sync.
     */
    private void setUpNormalInitialSync() {
        doAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                ApiCallback callback = (ApiCallback) invocation.getArguments()[0];
                callback.onSuccess(new InitialSyncResponse());
                return "onSuccess";
            }
        }).when(mockRestClient).initialSync(any(ApiCallback.class));
    }

    /**
     * Set up normal behavior from the events call.
     */
    private void setUpNormalEvents() {
        when(mockRestClient.events(anyString())).thenReturn(new TokensChunkResponse<Event>());
    }

    /**
     * Test the normal flow: initial sync + multiple events calls.
     */
    @Test
    public void testNormalFlow() {
        setUpNormalInitialSync();
        setUpNormalEvents();

        eventsThread = new EventsThread(mockRestClient, mockListener);
        eventsThread.start();

        // Verify the call to the rest client
        verify(mockRestClient, timeout(1000)).initialSync(any(ApiCallback.class));
        // Verify that the listener got notified for the initial sync
        verify(mockListener, timeout(1000)).onInitialSyncComplete(any(InitialSyncResponse.class));

        // Verify the call to the rest client
        verify(mockRestClient, timeout(1000).atLeast(2)).events(anyString());
        // Verify that the listener got notified with events at least a couple of times
        verify(mockListener, timeout(1000).atLeast(2)).onEventsReceived(any(List.class));
    }

    /**
     * Set up an initial sync that triggers a network error.
     */
    private void setUpNetworkErrorInitialSync() {
        // First invoke a network error, then success
        doAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                ApiCallback callback = (ApiCallback) invocation.getArguments()[0];
                callback.onNetworkError(new Exception());
                return "onNetworkError";
            }
        }).doAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                ApiCallback callback = (ApiCallback) invocation.getArguments()[0];
                callback.onSuccess(new InitialSyncResponse());
                return "onSuccess";
            }
        }).when(mockRestClient).initialSync(any(ApiCallback.class));
    }

    /**
     * Test getting a network error during the initial sync.
     */
    @Test
    public void testInitialSyncNetworkError() {
        setUpNetworkErrorInitialSync();

        eventsThread = new EventsThread(mockRestClient, mockListener);
        eventsThread.start();

        // Verify that it recovers from the error and moves on
        verify(mockListener, timeout(11000)).onInitialSyncComplete(any(InitialSyncResponse.class));
    }

    /**
     * Test getting a network error during the initial sync, having specified a failure callback.
     */
    @Test
    public void testInitialSyncNetworkErrorWithErrorListener() {
        setUpNetworkErrorInitialSync();

        eventsThread = new EventsThread(mockRestClient, mockListener);

        ApiFailureCallback mockFailureCallback = mock(ApiFailureCallback.class);
        eventsThread.setFailureCallback(mockFailureCallback);

        eventsThread.start();

        // Verify that it recovers from the error and moves on
        verify(mockListener, timeout(11000)).onInitialSyncComplete(any(InitialSyncResponse.class));
        // Verify that the failure listener gets the error
        verify(mockFailureCallback).onNetworkError(any(Exception.class));
    }

    /**
     * Set up the mock rest client to trigger a network error on the events call.
     */
    private void setUpNetworkErrorEvents() {
        RetrofitError mockNetworkError = mock(RetrofitError.class);
        when(mockNetworkError.isNetworkError()).thenReturn(true);
        when(mockRestClient.events(anyString()))
                .thenThrow(mockNetworkError)
                .thenReturn(new TokensChunkResponse<Event>());
    }

    /**
     * Test getting a network error during the events call.
     */
    @Test
    public void testEventsNetworkError() {
        setUpNormalInitialSync();
        setUpNetworkErrorEvents();

        eventsThread = new EventsThread(mockRestClient, mockListener);
        eventsThread.start();

        // Verify that we get events after the waiting period
        verify(mockListener, timeout(11000).atLeastOnce()).onEventsReceived(any(List.class));
    }

    /**
     * Test getting a network error during the events call, having specified a failure callback.
     */
    @Test
    public void testEventsNetworkErrorWithErrorListener() {
        setUpNormalInitialSync();
        setUpNetworkErrorEvents();

        eventsThread = new EventsThread(mockRestClient, mockListener);

        ApiFailureCallback mockFailureCallback = mock(ApiFailureCallback.class);
        eventsThread.setFailureCallback(mockFailureCallback);

        eventsThread.start();

        // --> the error callback is only triggered after 3 mins.
        //verify(mockFailureCallback, timeout(1000)).onNetworkError(any(Exception.class));
        // Verify that we get events after the waiting period
        verify(mockListener, timeout(11000).atLeastOnce()).onEventsReceived(any(List.class));
    }

    /**
     * Test pausing and resuming the thread.
     * @throws InterruptedException if Thread.sleep() does
     */
    @Test
    public void testPauseResume() throws InterruptedException {
        setUpNormalInitialSync();
        setUpNormalEvents();

        eventsThread = new EventsThread(mockRestClient, mockListener);
        eventsThread.start();

        // Wait a second, then pause
        Thread.sleep(1000);
        eventsThread.pause();
        // Wait a little more while it pauses
        Thread.sleep(100);

        reset(mockRestClient);

        // Wait a second and then verify that no events have come in.
        Thread.sleep(1000);
        verifyZeroInteractions(mockRestClient);

        // Unpause and verify that events come in again
        eventsThread.unpause();
        verify(mockRestClient, timeout(1000).atLeastOnce()).events(anyString());
    }

    /**
     * Test killing the thread.
     * @throws InterruptedException if Thread.sleep() does
     */
    @Test
    public void testKill() throws InterruptedException {
        setUpNormalInitialSync();
        setUpNormalEvents();

        eventsThread = new EventsThread(mockRestClient, mockListener);
        eventsThread.start();

        // Wait a second, then kill
        Thread.sleep(1000);
        eventsThread.kill();
        // Wait a little more while it finishes
        Thread.sleep(100);

        assertFalse(eventsThread.isAlive());
    }
}
