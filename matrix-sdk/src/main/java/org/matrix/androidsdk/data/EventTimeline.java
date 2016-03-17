/*
 * Copyright 2016 OpenMarket Ltd
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

package org.matrix.androidsdk.data;

import android.text.TextUtils;

import org.matrix.androidsdk.MXDataHandler;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.EventContext;
import org.matrix.androidsdk.rest.model.Sync.RoomSync;
import org.matrix.androidsdk.rest.model.Sync.InvitedRoomSync;

import java.util.List;

/**
 *  A `EventTimeline` instance represents a contiguous sequence of events in a room.
 *
 * There are two kinds of timeline:
 *
 * - live timelines: they receive live events from the events stream. You can paginate
 * backwards but not forwards.
 * All (live or backwards) events they receive are stored in the store of the current
 * MXSession.
 *
 * - past timelines: they start in the past from an `initialEventId`. They are filled
 * with events on calls of [MXEventTimeline paginate] in backwards or forwards direction.
 * Events are stored in a in-memory store (MXMemoryStore).
 */
public class EventTimeline {

    private static final String LOG_TAG = "EventTimeline";

    /**
     * The initial event id used to initialise the timeline.
     * null in case of live timeline.
     */
    private String mInitialEventId;

    /**
     * Indicate if this timeline is a live one.
     */
    private boolean mIsLiveTimeline;

    /**
     * The state of the room at the top most recent event of the timeline.
     */
    private RoomState mState;

    /**
     * The historical state of the room when paginating back.
     */
    private RoomState mBackState;

    /**
     * The state that was in the `state` property before it changed.
     * It is cached because it costs time to recompute it from the current state.
     * This is particularly noticeable for rooms with a lot of members (ie a lot of
     * room members state events).
     */
    public RoomState mPreviousState;

    /**
     * The associated room.
     */
    private Room mRoom;

    /**
     * The store.
     */
    private IMXStore mStore;

    /**
     * MXStore does only back pagination. So, the forward pagination token for
     * past timelines is managed locally.
     */
    private String mForwardsPaginationToken;
    private boolean mHasReachedHomeServerForwardsPaginationEnd;


    DataRetriever mDataRetriever;
    MXDataHandler mDataHandler;

    /**
     * Create a timeline instance for a room.
     * @param session the session.
     * @param aRoom the room associated to the timeline
     * @param initialEventId the initial event for the timeline. A null value will create a live timeline.
     */
    public EventTimeline(DataRetriever dataRetriever, MXDataHandler dataHandler, Room aRoom, String initialEventId) {

        mDataRetriever = dataRetriever;
        mDataHandler = dataHandler;
        mRoom = aRoom;
        mInitialEventId = initialEventId;

        mState = new RoomState();

        // Is it a past or live timeline?
        if (!TextUtils.isEmpty(initialEventId)) {
            // Events for a past timeline are stored in memory
            mStore = new MXMemoryStore(dataHandler.getCredentials());
        } else {
            mIsLiveTimeline = true;
            mStore = dataHandler.getStore();
        }
    }

    /**
     * Initialise the room evenTimeline state.
     * @param stateEvents the state event.
     */
    public void initialiseState(List<Event> stateEvents) {

    }

    //==============================================================================================================
    // pagination methods
    //==============================================================================================================

    /**
     * Check if this timelime can be extended.
     *
     * This returns true if we either have more events, or if we have a pagination
     * token which means we can paginate in that direction. It does not necessarily
     * mean that there are more events available in that direction at this time.
     * canPaginate in forward direction has no meaning for a live timeline.
     * @param direction MXTimelineDirectionBackwards to check if we can paginate backwards. MXTimelineDirectionForwards to check if we can go forwards.
     * @returntrue if we can paginate in the given direction.
     */
    public boolean canPaginate(Room.EventDirection direction) {

        return true;
    }

    /**
     * Reset the pagination so that future calls to paginate start from the most recent
     * event of the timeline.
     */
    public void resetPagination() {

    }

    /**
     * Reset the pagination timelime and start loading the context around its `initialEventId`.
     * The retrieved (backwards and forwards) events will be sent to registered listeners.
     * @param limit the maximum number of messages to get around the initial event.
     * @param callback the operation callbacl
     */

    public void resetPaginationAroundInitialEventWithLimit(int limit, ApiCallback<EventContext> callback) {

    }


    /**
     * Get more messages.
     * The retrieved events will be sent to registered listeners.
     * Note it is not possible to paginate forwards on a live timeline.
     *
     * @param numItems the number of items to get.
     * @param direction `MXTimelineDirectionForwards` or `MXTimelineDirectionBackwards`
     * @param onlyFromStore if YES, return available events from the store, do not make a pagination request to the homeserver.
     * @param callback the operation callback
     */
    public void paginate(int numItems, Room.EventDirection direction, boolean onlyFromStore, ApiCallback<Void> callback) {

    }


    /**
     * Get the number of messages we can still back paginate from the store.
     * It provides the count of events available without making a request to the home server.
     * @return the count of remaining messages in store.
     */
    public int remainingMessagesForBackPaginationInStore() {
        return 0;
    }


    //==============================================================================================================
    // Server sync
    //==============================================================================================================
    /**
     * For live timeline, update data according to the received /sync response.
     *
     * @param roomSync information to sync the room with the home server data
     */
    public void handleJoinedRoomSync(RoomSync roomSync) {

    }
    /**
     * For live timeline, update invited room state according to the received /sync response.
     *
     * @param invitedRoomSync information to update the room state.
     */
    public void handleInvitedRoomSync(InvitedRoomSync invitedRoomSync) {

    }
}
