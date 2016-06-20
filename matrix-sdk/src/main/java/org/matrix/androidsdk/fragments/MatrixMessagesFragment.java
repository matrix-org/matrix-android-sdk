/*
 * Copyright 2015 OpenMarket Ltd
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

package org.matrix.androidsdk.fragments;

import android.content.Context;
import android.os.Bundle;
import android.os.Looper;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.EventTimeline;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomPreviewData;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.listeners.IMXEventListener;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.RoomResponse;
import org.matrix.androidsdk.rest.model.Sync.RoomSync;
import org.matrix.androidsdk.rest.model.Sync.RoomSyncState;
import org.matrix.androidsdk.rest.model.Sync.RoomSyncTimeline;
import org.matrix.androidsdk.rest.model.TokensChunkResponse;

import java.util.List;

/**
 * A non-UI fragment containing logic for extracting messages from a room, including handling
 * pagination. For a UI implementation of this, see {@link MatrixMessageListFragment}.
 */
public class MatrixMessagesFragment extends Fragment {
    private static final String LOG_TAG = "MatrixMessagesFragment";

    /**
     * The room ID to get messages for.
     * Fragment argument: String.
     */
    public static final String ARG_ROOM_ID = "org.matrix.androidsdk.fragments.MatrixMessageFragment.ARG_ROOM_ID";

    public static MatrixMessagesFragment newInstance(MXSession session, String roomId, MatrixMessagesListener listener) {
        MatrixMessagesFragment fragment = new MatrixMessagesFragment();
        Bundle args = new Bundle();


        if (null == listener) {
            throw new RuntimeException("Must define a listener.");
        }

        if (null == session) {
            throw new RuntimeException("Must define a session.");
        }

        if (null != roomId) {
            args.putString(ARG_ROOM_ID, roomId);
        }

        fragment.setArguments(args);
        fragment.setMatrixMessagesListener(listener);
        fragment.setMXSession(session);
        return fragment;
    }

    public interface MatrixMessagesListener {
        void onEvent(Event event, EventTimeline.Direction direction, RoomState roomState);
        void onLiveEventsChunkProcessed();
        void onReceiptEvent(List<String> senderIds);
        void onRoomSyncWithLimitedTimeline();

        EventTimeline getEventTimeLine();
        void onTimelineInitialized();

        /**
         * Called when the first batch of messages is loaded.
         */
        void onInitialMessagesLoaded();

        // UI events
        void showInitLoading();
        void hideInitLoading();

        // get the room preview data
        RoomPreviewData getRoomPreviewData();
    }

    // The listener to send messages back
    private MatrixMessagesListener mMatrixMessagesListener;
    // The adapted listener to register to the SDK
    private final IMXEventListener mEventListener = new MXEventListener() {
        @Override
        public void onLiveEventsChunkProcessed() {
            if (null != mMatrixMessagesListener) {
                mMatrixMessagesListener.onLiveEventsChunkProcessed();
            }
        }

        @Override
        public void onReceiptEvent(String roomId, List<String> senderIds) {
            if (null != mMatrixMessagesListener) {
                mMatrixMessagesListener.onReceiptEvent(senderIds);
            }
        }

        @Override
        public void onRoomSyncWithLimitedTimeline(String roomId) {
            if (null != mMatrixMessagesListener) {
                if (mEventTimeline.isLiveTimeline()) {
                    // clear the history
                    mMatrixMessagesListener.onRoomSyncWithLimitedTimeline();

                    // init the timeline
                    mEventTimeline.initHistory();

                    // fill the screen
                    requestInitialHistory();
                }
            }
        }
    };

    private final EventTimeline.EventTimelineListener mEventTimelineListener = new EventTimeline.EventTimelineListener() {
        @Override
        public void onEvent(Event event, EventTimeline.Direction direction, RoomState roomState) {
            if (null != mMatrixMessagesListener) {
                mMatrixMessagesListener.onEvent(event, direction, roomState);
            }
        }
    };

    private Context mContext;
    private MXSession mSession;
    private Room mRoom;
    public boolean mKeepRoomHistory;

    private EventTimeline mEventTimeline;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(LOG_TAG, "onCreate");

        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.d(LOG_TAG, "onCreateView");

        View v = super.onCreateView(inflater, container, savedInstanceState);

        // the requests are done in onCreateView  instead of onActivityCreated to speed up in the events request
        // it saves only few ms but it reduces the white screen flash.
        mContext = getActivity().getApplicationContext();

        String roomId = getArguments().getString(ARG_ROOM_ID);

        // this code should never be called
        // but we've got some crashes when the session was null
        // so try to find it from the fragments call stack.
        if (null == mSession) {
            List<Fragment> fragments = null;
            FragmentManager fm = getActivity().getSupportFragmentManager();

            if (null != fm) {
                fragments = fm.getFragments();
            }

            if (null != fragments) {
                for (Fragment fragment : fragments) {
                    if (fragment instanceof MatrixMessageListFragment) {
                        mMatrixMessagesListener = (MatrixMessageListFragment) fragment;
                        mSession = ((MatrixMessageListFragment) fragment).getSession();
                    }
                }
            }
        }

        if (mSession == null) {
            throw new RuntimeException("Must have valid default MXSession.");
        }
        // get the timelime
        if (null == mEventTimeline) {
            mEventTimeline = mMatrixMessagesListener.getEventTimeLine();
        } else {
            mEventTimeline.addEventTimelineListener(mEventTimelineListener);
            // the room has already been initialized
            sendInitialMessagesLoaded();
            return v;
        }

        if (null != mEventTimeline) {
            mEventTimeline.addEventTimelineListener(mEventTimelineListener);
            mRoom = mEventTimeline.getRoom();
        }

        // retrieve the room.
        if (null == mRoom) {
            // check if this room has been joined, if not, join it then get messages.
            mRoom = mSession.getDataHandler().getRoom(roomId);
        }

        // GA reported some weird room content
        // so ensure that the room fields are properly initialized
        mSession.getDataHandler().checkRoom(mRoom);

        // display the message history around a dedicated message
        if ((null != mEventTimeline) &&  !mEventTimeline.isLiveTimeline() && (null != mEventTimeline.getInitialEventId())) {
            initializeTimeline();
        }
        else {
            boolean joinedRoom = false;
            // does the room already exist ?
            if ((mRoom != null) && (null != mEventTimeline)) {
                // init the history
                mEventTimeline.initHistory();

                // check if some required fields are initialized
                // else, the joining could have been half broken (network error)
                if (null != mRoom.getState().creator) {
                    RoomMember self = mRoom.getMember(mSession.getCredentials().userId);
                    if (self != null && RoomMember.MEMBERSHIP_JOIN.equals(self.membership)) {
                        joinedRoom = true;
                    }
                }

                mRoom.addEventListener(mEventListener);

                // room preview mode
                // i.e display the room messages without joining the room
                if (!mEventTimeline.isLiveTimeline()) {
                    previewRoom();
                }
                // join the room is not yet joined
                else if (!joinedRoom) {
                    Log.d(LOG_TAG, "Joining room >> " + roomId);
                    joinRoom();
                }
                else {
                    // the room is already joined
                    // fill the messages list
                    requestInitialHistory();
                }
            } else {
                sendInitialMessagesLoaded();
            }
        }

        return v;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if ((null != mRoom) && (null != mEventTimeline)) {
            if (mEventTimeline.isLiveTimeline()) {
                mRoom.removeEventListener(mEventListener);
            }

            mEventTimeline.removeEventTimelineListener(mEventTimelineListener);
        }
    }

    /**
     * Warn the listener that this fragment is ready.
     */
    private void sendInitialMessagesLoaded() {
        final android.os.Handler handler = new android.os.Handler(Looper.getMainLooper());

        // add a delay to avoid calling MatrixListFragment before it is fully initialized
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (null != mMatrixMessagesListener) {
                    mMatrixMessagesListener.onInitialMessagesLoaded();
                }
            }
        }, 100);
    }

    /**
     * Trigger a room preview i.e trigger an initial sync before filling the message list.
     */
    private void previewRoom() {
        Log.d(LOG_TAG, "Make a room preview of " + mRoom.getRoomId());

        if (null != mMatrixMessagesListener) {
            RoomPreviewData roomPreviewData = mMatrixMessagesListener.getRoomPreviewData();

            if (null != roomPreviewData) {
                if  (null != roomPreviewData.getRoomResponse()) {
                    Log.d(LOG_TAG, "A preview data is provided with sync reponse");
                    RoomResponse roomResponse = roomPreviewData.getRoomResponse();

                    // initialize the timeline with the initial sync response
                    RoomSync roomSync = new RoomSync();
                    roomSync.state = new RoomSyncState();
                    roomSync.state.events = roomResponse.state;

                    roomSync.timeline = new RoomSyncTimeline();
                    roomSync.timeline.events = roomResponse.messages.chunk;
                    roomSync.timeline.limited = true;
                    roomSync.timeline.prevBatch = roomResponse.messages.end;

                    mEventTimeline.handleJoinedRoomSync(roomSync, true);

                    Log.d(LOG_TAG, "The room preview is done -> fill the room history");
                    requestInitialHistory();
                } else {
                    Log.d(LOG_TAG, "A preview data is provided with no sync reponse : assume that it is not possible to get a room preview");

                    if (null != getActivity()) {
                        if (null != mMatrixMessagesListener) {
                            mMatrixMessagesListener.hideInitLoading();
                        }
                    }
                }

                return;
            }
        }

        mSession.getRoomsApiClient().initialSync(mRoom.getRoomId(), new ApiCallback<RoomResponse>() {
            @Override
            public void onSuccess(RoomResponse roomResponse) {
                // initialize the timeline with the initial sync response
                RoomSync roomSync = new RoomSync();
                roomSync.state = new RoomSyncState();
                roomSync.state.events = roomResponse.state;

                roomSync.timeline = new RoomSyncTimeline();
                roomSync.timeline.events = roomResponse.messages.chunk;

                mEventTimeline.handleJoinedRoomSync(roomSync, true);

                Log.d(LOG_TAG, "The room preview is done -> fill the room history");
                requestInitialHistory();
            }

            private void onError(String errorMessage) {
                Log.e(LOG_TAG, "The room preview of " + mRoom.getRoomId() + "failed " + errorMessage);

                if (null != getActivity()) {
                    if (null != mMatrixMessagesListener) {
                        mMatrixMessagesListener.hideInitLoading();
                    }
                }
            }

            @Override
            public void onNetworkError(Exception e) {
                onError(e.getLocalizedMessage());
            }

            @Override
            public void onMatrixError(MatrixError e) {
                onError(e.getLocalizedMessage());
            }

            @Override
            public void onUnexpectedError(Exception e) {
                onError(e.getLocalizedMessage());
            }
        });
    }

    /**
     * Initialize the timeline to fill the screen
     */
    private void initializeTimeline() {
        Log.d(LOG_TAG, "initializeTimeline");

        if (null != mMatrixMessagesListener) {
            mMatrixMessagesListener.showInitLoading();
        }

        mEventTimeline.resetPaginationAroundInitialEvent(30 * 2, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                Log.d(LOG_TAG, "initializeTimeline is done");

                if (null != getActivity()) {
                    if (null != mMatrixMessagesListener) {
                        mMatrixMessagesListener.hideInitLoading();
                        mMatrixMessagesListener.onTimelineInitialized();
                    }
                    sendInitialMessagesLoaded();
                }
            }

            private void onError(String errorMessage) {
                Log.d(LOG_TAG, "initializeTimeline fails " + errorMessage);

                if (null != getActivity()) {
                    Toast.makeText(mContext, errorMessage, Toast.LENGTH_SHORT).show();

                    if (null != mMatrixMessagesListener) {
                        mMatrixMessagesListener.hideInitLoading();
                        mMatrixMessagesListener.onTimelineInitialized();
                    }
                }
            }

            @Override
            public void onNetworkError(Exception e) {
                onError(e.getLocalizedMessage());
            }

            @Override
            public void onMatrixError(MatrixError e) {
                onError(e.getLocalizedMessage());
            }

            @Override
            public void onUnexpectedError(Exception e) {
                onError(e.getLocalizedMessage());
            }
        });
    }

    /**
     * Request messages in this room upon entering.
     */
    protected void requestInitialHistory() {
        if (null != mMatrixMessagesListener) {
            mMatrixMessagesListener.showInitLoading();
        }

        Log.d(LOG_TAG, "requestInitialHistory " + mRoom.getRoomId());

        // the initial sync will be retrieved when a network connection will be found
        backPaginate(new SimpleApiCallback<Integer>(getActivity()) {
            @Override
            public void onSuccess(Integer info) {
                Log.d(LOG_TAG, "requestInitialHistory onSuccess");

                if (null != getActivity()) {
                    if (null != mMatrixMessagesListener) {
                        mMatrixMessagesListener.hideInitLoading();
                        mMatrixMessagesListener.onTimelineInitialized();
                        mMatrixMessagesListener.onInitialMessagesLoaded();
                    }
                }
            }

            private void onError(String errorMessage) {
                Log.e(LOG_TAG, "requestInitialHistory failed" + errorMessage);
                if (null != getActivity()) {
                    Toast.makeText(mContext, errorMessage, Toast.LENGTH_LONG).show();

                    if (null != mMatrixMessagesListener) {
                        mMatrixMessagesListener.hideInitLoading();
                    }
                }
            }

            @Override
            public void onNetworkError(Exception e) {
                onError(e.getLocalizedMessage());
            }

            @Override
            public void onMatrixError(MatrixError e) {
                onError(e.getLocalizedMessage());
            }

            @Override
            public void onUnexpectedError(Exception e) {
                onError(e.getLocalizedMessage());
            }
        });
    }

    //==============================================================================================================
    // Setters / getters
    //==============================================================================================================

    /**
     * Set the listener which will be informed of matrix messages. This setter is provided so either
     * a Fragment or an Activity can directly receive callbacks.
     * @param listener the listener for this fragment
     */
    public void setMatrixMessagesListener(MatrixMessagesListener listener) {
        mMatrixMessagesListener = listener;
    }

    /**
     * Set the MX session
     * @param session
     */
    public void setMXSession(MXSession session) {
        mSession = session;
    }

    //==============================================================================================================
    // Room / timeline actions
    //==============================================================================================================

    /**
     * Request earlier messages in this room.
     * @param callback the callback
     * @return true if the request is really started
     */
    public boolean backPaginate(ApiCallback<Integer> callback) {
        if (null != mEventTimeline) {
            return mEventTimeline.backPaginate(callback);
        } else {
            return false;
        }
    }

    /**
     * Request the next events in the timelinex
     * @param callback the callback
     * @return true if the request is really started
     */
    public boolean forwardPaginate(ApiCallback<Integer> callback) {
        if ((null != mEventTimeline) && mEventTimeline.isLiveTimeline()) {
            return mEventTimeline.forwardPaginate(callback);
        } else {
            return false;
        }
    }

    /**
     * Send an event in a room
     * @param event the event
     * @param callback the callback
     */
    public void sendEvent(Event event, ApiCallback<Void> callback) {
        if (null != mRoom) {
            mRoom.sendEvent(event, callback);
        }
    }

    /**
     * Redact an event.
     * @param eventId the event Id
     * @param callback the callback.
     */
    public void redact(String eventId, ApiCallback<Event> callback) {
        if (null != mRoom) {
            mRoom.redact(eventId, callback);
        }
    }

    /**
     * Join the room.
     */
    private void joinRoom() {
        if (null != mMatrixMessagesListener) {
            mMatrixMessagesListener.showInitLoading();
        }

        Log.d(LOG_TAG, "joinRoom " + mRoom.getRoomId());

        mRoom.join(new SimpleApiCallback<Void>(getActivity()) {
            @Override
            public void onSuccess(Void info) {
                Log.d(LOG_TAG, "joinRoom succeeds");

                if (null != getActivity()) {
                    if (null != mMatrixMessagesListener) {
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    mMatrixMessagesListener.hideInitLoading();
                                    mMatrixMessagesListener.onInitialMessagesLoaded();
                                } catch (Exception e) {
                                    Log.e(LOG_TAG, "joinRoom callback fails " + e.getLocalizedMessage());
                                }
                            }
                        });
                    }
                }
            }

            private void onError(String errorMessage) {
                Log.e(LOG_TAG, "joinRoom error: " + errorMessage);
                if (null != getActivity()) {
                    Toast.makeText(mContext, errorMessage, Toast.LENGTH_SHORT).show();
                    if (null != mMatrixMessagesListener) {

                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mMatrixMessagesListener.hideInitLoading();
                            }
                        });
                    }
                }
            }

            // the request will be automatically restarted when a valid network will be found
            @Override
            public void onNetworkError(Exception e) {
                Log.e(LOG_TAG, "joinRoom Network error: " + e.getLocalizedMessage());
                onError(e.getLocalizedMessage());
            }

            @Override
            public void onMatrixError(MatrixError e) {
                Log.e(LOG_TAG, "joinRoom onMatrixError : " + e.getLocalizedMessage());
                onError(e.getLocalizedMessage());
            }

            @Override
            public void onUnexpectedError(Exception e) {
                Log.e(LOG_TAG, "joinRoom Override : " + e.getLocalizedMessage());
                onError(e.getLocalizedMessage());
            }
        });
    }
}
