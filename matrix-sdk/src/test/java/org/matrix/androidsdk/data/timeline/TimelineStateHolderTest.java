package org.matrix.androidsdk.data.timeline;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.matrix.androidsdk.MXDataHandler;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.data.store.IMXStore;
import org.matrix.androidsdk.rest.model.Event;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class TimelineStateHolderTest {

    private static final String ROOM_ID = "roomId";

    @Mock
    MXDataHandler mDataHandler;
    @Mock
    IMXStore mIMXStore;

    private TimelineStateHolder mTimelineStateHolder;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mTimelineStateHolder = new TimelineStateHolder(mDataHandler, mIMXStore, ROOM_ID);
    }

    @Test
    public void processStateEvent_WhenDirectionIsForward__ShouldStoreLiveState() {
        final Event event = new Event();
        event.roomId = ROOM_ID;
        event.stateKey = Event.EVENT_TYPE_STATE_ROOM_NAME;
        event.type = Event.EVENT_TYPE_STATE_ROOM_NAME;
        mTimelineStateHolder.processStateEvent(event, EventTimeline.Direction.FORWARDS, true);
        Mockito.verify(mIMXStore, Mockito.times(1)).storeLiveStateForRoom(Mockito.anyString());
    }

    @Test
    public void processStateEvent_WhenNoStateKeyIsGiven__ShouldNotBeProcessed() {
        final Event event = new Event();
        event.roomId = ROOM_ID;
        event.type = Event.EVENT_TYPE_STATE_ROOM_NAME;
        Assert.assertFalse(mTimelineStateHolder.processStateEvent(event, EventTimeline.Direction.FORWARDS, true));
    }

    @Test
    public void processStateEvent_WithConformingEvent__ShouldBeProcessed() {
        final Event event = new Event();
        event.roomId = ROOM_ID;
        event.type = Event.EVENT_TYPE_STATE_ROOM_NAME;
        event.stateKey = Event.EVENT_TYPE_STATE_ROOM_NAME;
        Assert.assertTrue(mTimelineStateHolder.processStateEvent(event, EventTimeline.Direction.FORWARDS, true));
    }

    @Test
    public void processStateEvent_WhenDirectionIsForward__ShouldUseState() {
        final Event event = new Event();
        event.roomId = ROOM_ID;
        event.stateKey = Event.EVENT_TYPE_STATE_ROOM_NAME;
        event.type = Event.EVENT_TYPE_STATE_ROOM_NAME;
        final RoomState state = Mockito.spy(mTimelineStateHolder.getState());
        mTimelineStateHolder.setState(state);
        mTimelineStateHolder.processStateEvent(event, EventTimeline.Direction.FORWARDS, true);
        Mockito.verify(state).applyState(event, true, mIMXStore);
    }

    @Test
    public void processStateEvent_WhenDirectionIsBackward__ShouldUseBackState() {
        final Event event = new Event();
        event.roomId = ROOM_ID;
        event.stateKey = Event.EVENT_TYPE_STATE_ROOM_NAME;
        event.type = Event.EVENT_TYPE_STATE_ROOM_NAME;
        final RoomState backState = Mockito.spy(mTimelineStateHolder.getBackState());
        mTimelineStateHolder.setBackState(backState);
        mTimelineStateHolder.processStateEvent(event, EventTimeline.Direction.BACKWARDS, false);
        Mockito.verify(backState).applyState(event, false, null);
    }

    @Test
    public void deepCopyState_WhenDirectionIsForward__ShouldCopyState() {
        final RoomState state = Mockito.spy(new RoomState());
        final RoomState backState = Mockito.spy(new RoomState());
        mTimelineStateHolder.setState(state);
        mTimelineStateHolder.setBackState(backState);
        mTimelineStateHolder.deepCopyState(EventTimeline.Direction.FORWARDS);
        Mockito.verify(state).deepCopy();
        Mockito.verify(backState, Mockito.never()).deepCopy();
    }

    @Test
    public void deepCopyState_WhenDirectionIsBackward__ShouldCopyBackState() {
        final RoomState state = Mockito.spy(new RoomState());
        final RoomState backState = Mockito.spy(new RoomState());
        mTimelineStateHolder.setState(state);
        mTimelineStateHolder.setBackState(backState);
        mTimelineStateHolder.deepCopyState(EventTimeline.Direction.BACKWARDS);
        Mockito.verify(backState).deepCopy();
        Mockito.verify(state, Mockito.never()).deepCopy();
    }
}
