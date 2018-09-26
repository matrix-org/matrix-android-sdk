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

import static org.matrix.androidsdk.rest.model.Event.EVENT_TYPE_STATE_ROOM_NAME;

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
    public void State_WhenProcessForward__ShouldStoreLiveState() {
        final Event event = new Event();
        event.roomId = ROOM_ID;
        event.stateKey = EVENT_TYPE_STATE_ROOM_NAME;
        event.type = EVENT_TYPE_STATE_ROOM_NAME;
        mTimelineStateHolder.processStateEvent(event, EventTimeline.Direction.FORWARDS);
        Mockito.verify(mIMXStore, Mockito.times(1)).storeLiveStateForRoom(Mockito.anyString());
    }

    @Test
    public void State_WhenProcessWithNoStateKey__ShouldNotBeProcessed() {
        final Event event = new Event();
        event.roomId = ROOM_ID;
        event.type = EVENT_TYPE_STATE_ROOM_NAME;
        Assert.assertFalse(mTimelineStateHolder.processStateEvent(event, EventTimeline.Direction.FORWARDS));
    }

    @Test
    public void State_WhenProcessForward__ShouldUseState() {
        final Event event = new Event();
        event.roomId = ROOM_ID;
        event.stateKey = EVENT_TYPE_STATE_ROOM_NAME;
        event.type = EVENT_TYPE_STATE_ROOM_NAME;
        final RoomState state = Mockito.spy(mTimelineStateHolder.getState());
        mTimelineStateHolder.setState(state);
        mTimelineStateHolder.processStateEvent(event, EventTimeline.Direction.FORWARDS);
        Mockito.verify(state).applyState(mIMXStore, event, EventTimeline.Direction.FORWARDS);
    }

    @Test
    public void State_WhenProcessBackward__ShouldUseBackState() {
        final Event event = new Event();
        event.roomId = ROOM_ID;
        event.stateKey = EVENT_TYPE_STATE_ROOM_NAME;
        event.type = EVENT_TYPE_STATE_ROOM_NAME;
        final RoomState backState = Mockito.spy(mTimelineStateHolder.getBackState());
        mTimelineStateHolder.setBackState(backState);
        mTimelineStateHolder.processStateEvent(event, EventTimeline.Direction.BACKWARDS);
        Mockito.verify(backState).applyState(mIMXStore, event, EventTimeline.Direction.BACKWARDS);
    }

    @Test
    public void State_WhenDeepCopyForward__ShouldUseState() {
        final RoomState state = Mockito.spy(new RoomState());
        final RoomState backState = Mockito.spy(new RoomState());
        mTimelineStateHolder.setState(state);
        mTimelineStateHolder.setBackState(backState);
        mTimelineStateHolder.deepCopyState(EventTimeline.Direction.FORWARDS);
        Mockito.verify(state).deepCopy();
        Mockito.verify(backState, Mockito.never()).deepCopy();
    }

    @Test
    public void State_WhenDeepCopyBackward__ShouldUseBackState() {
        final RoomState state = Mockito.spy(new RoomState());
        final RoomState backState = Mockito.spy(new RoomState());
        mTimelineStateHolder.setState(state);
        mTimelineStateHolder.setBackState(backState);
        mTimelineStateHolder.deepCopyState(EventTimeline.Direction.BACKWARDS);
        Mockito.verify(backState).deepCopy();
        Mockito.verify(state, Mockito.never()).deepCopy();
    }


}
