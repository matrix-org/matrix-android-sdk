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

package org.matrix.androidsdk.data;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import org.matrix.androidsdk.MXDataHandler;
import org.matrix.androidsdk.listeners.IMXEventListener;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.BannedUser;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.FileInfo;
import org.matrix.androidsdk.rest.model.FileMessage;
import org.matrix.androidsdk.rest.model.ImageInfo;
import org.matrix.androidsdk.rest.model.ImageMessage;
import org.matrix.androidsdk.rest.model.LocationMessage;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.PowerLevels;
import org.matrix.androidsdk.rest.model.ReceiptData;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.RoomResponse;
import org.matrix.androidsdk.rest.model.Search.SearchEventContext;
import org.matrix.androidsdk.rest.model.Sync.RoomSync;
import org.matrix.androidsdk.rest.model.Sync.InvitedRoomSync;
import org.matrix.androidsdk.rest.model.ThumbnailInfo;
import org.matrix.androidsdk.rest.model.TokensChunkResponse;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.rest.model.VideoInfo;
import org.matrix.androidsdk.rest.model.VideoMessage;
import org.matrix.androidsdk.util.ContentManager;
import org.matrix.androidsdk.util.ImageUtils;
import org.matrix.androidsdk.util.JsonUtils;

import java.io.File;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 * Events are stored in a in-memory store (MXMemoryStore) (@TODO: To be confirmed once they will be implemented). So, they are not permanent.
 */
public class EventTimeline {

    private static final String LOG_TAG = "EventTimeline";

    /**
     * The direction from which an incoming event is considered.
    */
    public enum TimelineDirection {
        /**
         * Forwards when the event is added to the end of the timeline.
         * These events come from the /sync stream or from forwards pagination.
         */
        FORWARDS,

        /**
         * Backwards when the event is added to the start of the timeline.
         * These events come from a back pagination.
         */
        BACKWARDS
    }

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
     * Create a timeline instance for a room.
     * @param room the room associated to the timeline
     * @param initialEventId the initial event for the timeline. A nil value will create a live timeline.
     */
    public EventTimeline(Room room, String initialEventId) {

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
    public boolean canPaginate(TimelineDirection direction) {

        return true;
    }

    /**
     * Reset the pagination so that future calls to paginate start from the most recent
     * event of the timeline.
     */
    public void resetPagination() {

    }


    public void resetPaginationAroundInitialEventWithLimit(int limit, ApiCallback<SearchEventContext>)

/**
 Reset the pagination timelime and start loading the context around its `initialEventId`.
 The retrieved (backwards and forwards) events will be sent to registered listeners.

 @param limit the maximum number of messages to get around the initial event.

 @param success A block object called when the operation succeeds.
 @param failure A block object called when the operation fails.

 @return a MXHTTPOperation instance.
 */
    - (MXHTTPOperation*)resetPaginationAroundInitialEventWithLimit:(NSUInteger)limit
    success:(void(^)())success
    failure:(void (^)(NSError *error))failure;

/**
 Get more messages.
 The retrieved events will be sent to registered listeners.

 Note it is not possible to paginate forwards on a live timeline.

 @param numItems the number of items to get.
 @param direction `MXTimelineDirectionForwards` or `MXTimelineDirectionBackwards`
 @param onlyFromStore if YES, return available events from the store, do not make
 a pagination request to the homeserver.

 @param complete A block object called when the operation is complete.
 @param failure A block object called when the operation fails.

 @return a MXHTTPOperation instance. This instance can be nil if no request
 to the homeserver is required.
 */
    - (MXHTTPOperation*)paginate:(NSUInteger)numItems
    direction:(MXTimelineDirection)direction
    onlyFromStore:(BOOL)onlyFromStore
    complete:(void (^)())complete
    failure:(void (^)(NSError *error))failure;

/**
 Get the number of messages we can still back paginate from the store.
 It provides the count of events available without making a request to the home server.

 @return the count of remaining messages in store.
 */
    - (NSUInteger)remainingMessagesForBackPaginationInStore;


    #pragma mark - Server sync
/**
 For live timeline, update data according to the received /sync response.

 @param roomSync information to sync the room with the home server data
 */
    - (void)handleJoinedRoomSync:(MXRoomSync*)roomSync;

/**
 For live timeline, update invited room state according to the received /sync response.

 @param invitedRoom information to update the room state.
 */
    - (void)handleInvitedRoomSync:(MXInvitedRoomSync *)invitedRoomSync;


    #pragma mark - Events listeners
/**
 Register a listener to events of this timeline.

 @param onEvent the block that will called once a new event has been handled.
 @return a reference to use to unregister the listener
 */
    - (id)listenToEvents:(MXOnRoomEvent)onEvent;

/**
 Register a listener for some types of events.

 @param types an array of event types strings (MXEventTypeString) to listen to.
 @param onEvent the block that will called once a new event has been handled.
 @return a reference to use to unregister the listener
 */
    - (id)listenToEventsOfTypes:(NSArray*)types onEvent:(MXOnRoomEvent)onEvent;

/**
 Unregister a listener.

 @param listener the reference of the listener to remove.
 */
    - (void)removeListener:(id)listener;

/**
 Unregister all listeners.
 */
    - (void)removeAllListeners;

/**
 Notifiy all listeners of the timeline about the given event.

 @param event the event to notify.
 @param the event direction.
 */
    - (void)notifyListeners:(MXEvent*)event direction:(MXTimelineDirection)direction;

    @end


}
