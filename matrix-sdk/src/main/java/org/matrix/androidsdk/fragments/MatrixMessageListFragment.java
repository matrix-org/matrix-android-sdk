/*
 * Copyright 2015 OpenMarket Ltd
 * Copyright 2017 Vector Creations Ltd
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

package org.matrix.androidsdk.fragments;

import android.app.Activity;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.Toast;

import com.google.gson.JsonObject;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.R;
import org.matrix.androidsdk.adapters.AbstractMessagesAdapter;
import org.matrix.androidsdk.adapters.MessageRow;
import org.matrix.androidsdk.crypto.MXCryptoError;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomMediaMessage;
import org.matrix.androidsdk.data.RoomPreviewData;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.data.RoomSummary;
import org.matrix.androidsdk.data.store.IMXStore;
import org.matrix.androidsdk.data.timeline.EventTimeline;
import org.matrix.androidsdk.data.timeline.EventTimelineFactory;
import org.matrix.androidsdk.db.MXMediaCache;
import org.matrix.androidsdk.listeners.IMXEventListener;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.listeners.MXMediaUploadListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.ReceiptData;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.message.MediaMessage;
import org.matrix.androidsdk.rest.model.message.Message;
import org.matrix.androidsdk.rest.model.search.SearchResponse;
import org.matrix.androidsdk.rest.model.search.SearchResult;
import org.matrix.androidsdk.util.EventDisplay;
import org.matrix.androidsdk.util.JsonUtils;
import org.matrix.androidsdk.util.Log;
import org.matrix.androidsdk.view.AutoScrollDownListView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;


/**
 * UI Fragment containing matrix messages for a given room.
 * Contains {@link MatrixMessagesFragment} as a nested fragment to do the work.
 */
public abstract class MatrixMessageListFragment<MessagesAdapter extends AbstractMessagesAdapter> extends Fragment
        implements MatrixMessagesFragment.MatrixMessagesListener {

    // search interface
    public interface OnSearchResultListener {
        void onSearchSucceed(int nbrMessages);

        void onSearchFailed();
    }

    // room preview interface
    public interface IRoomPreviewDataListener {
        RoomPreviewData getRoomPreviewData();
    }

    // be warned when a message sending failed or succeeded
    public interface IEventSendingListener {
        /**
         * A message has been successfully sent.
         *
         * @param event the event
         */
        void onMessageSendingSucceeded(Event event);

        /**
         * A message sending has failed.
         *
         * @param event the event
         */
        void onMessageSendingFailed(Event event);

        /**
         * An event has been successfully redacted by the user.
         *
         * @param event the event
         */
        void onMessageRedacted(Event event);

        /**
         * An event sending failed because some unknown devices have been detected
         *
         * @param event the event
         * @param error the crypto error
         */
        void onUnknownDevices(Event event, MXCryptoError error);

        /**
         * An event sending failed because of consent not given
         *
         * @param event       the event
         * @param matrixError the MatrixError (contains message text and URL)
         */
        void onConsentNotGiven(Event event, MatrixError matrixError);

    }

    // scroll listener
    public interface IOnScrollListener {
        /**
         * The events list has been scrolled.
         *
         * @param firstVisibleItem the index of the first visible cell
         * @param visibleItemCount the number of visible cells
         * @param totalItemCount   the number of items in the list adaptor
         */
        void onScroll(int firstVisibleItem, int visibleItemCount, int totalItemCount);

        /**
         * Tell if the latest event is fully displayed
         *
         * @param isDisplayed true if the latest event is fully displayed
         */
        void onLatestEventDisplay(boolean isDisplayed);


        /**
         * See {@link AbsListView.OnScrollListener#onScrollStateChanged(AbsListView, int)}
         *
         * @param scrollState the scrollstate
         */
        void onScrollStateChanged(int scrollState);
    }

    // fragment parameters
    private static final String ARG_MATRIX_ID = "MatrixMessageListFragment.ARG_MATRIX_ID";
    private static final String ARG_ROOM_ID = "MatrixMessageListFragment.ARG_ROOM_ID";
    private static final String ARG_LAYOUT_ID = "MatrixMessageListFragment.ARG_LAYOUT_ID";
    protected static final String ARG_EVENT_ID = "MatrixMessageListFragment.ARG_EVENT_ID";
    protected static final String ARG_PREVIEW_MODE_ID = "MatrixMessageListFragment.ARG_PREVIEW_MODE_ID";

    // default preview mode
    public static final String PREVIEW_MODE_READ_ONLY = "PREVIEW_MODE_READ_ONLY";
    public static final String PREVIEW_MODE_UNREAD_MESSAGE = "PREVIEW_MODE_UNREAD_MESSAGE";

    private static final String LOG_TAG = "MatrixMsgsListFrag";

    private static final int UNDEFINED_VIEW_Y_POS = -12345678;

    public static Bundle getArguments(String matrixId, String roomId, int layoutResId) {
        Bundle args = new Bundle();
        args.putString(ARG_MATRIX_ID, matrixId);
        args.putString(ARG_ROOM_ID, roomId);
        args.putInt(ARG_LAYOUT_ID, layoutResId);
        return args;
    }

    private MatrixMessagesFragment mMatrixMessagesFragment;
    protected MessagesAdapter mAdapter;
    public AutoScrollDownListView mMessageListView;
    protected Handler mUiHandler;
    protected MXSession mSession;
    protected String mMatrixId;
    protected Room mRoom;
    protected String mPattern = null;
    protected boolean mIsMediaSearch;
    protected String mNextBatch = null;
    private boolean mDisplayAllEvents = true;
    public boolean mCheckSlideToHide = false;

    private boolean mIsScrollListenerSet;

    // timeline management
    protected final boolean mIsLive = true;

    // From Fragment parameters
    protected String mRoomId;

    // by default the
    protected EventTimeline mEventTimeLine;
    protected String mEventId;
    // TS of the even id we want to scroll to
    // Used when the event will not be in adapter because event is not displayed
    protected long mEventOriginServerTs;

    // pagination statuses
    protected boolean mIsInitialSyncing = true;
    protected boolean mIsBackPaginating = false;
    protected boolean mIsFwdPaginating = false;

    // lock the pagination while refreshing the list view to avoid having twice or thrice refreshes sequence.
    private boolean mLockBackPagination = false;
    private boolean mLockFwdPagination = true;

    private final Map<String, Timer> mPendingRelaunchTimersByEventId = new HashMap<>();

    // scroll to to the dedicated index when the device has been rotated
    private int mFirstVisibleRow = -1;

    // scroll to the index when loaded
    private int mScrollToIndex = -1;

    // y pos of the first visible row
    private int mFirstVisibleRowY = UNDEFINED_VIEW_Y_POS;

    // Id of the dummy event that should become the read marker when server returns the real ID
    private String mFutureReadMarkerEventId;

    // used to retrieve the preview data
    protected IRoomPreviewDataListener mRoomPreviewDataListener;

    // be warned that an event sending has failed.
    protected IEventSendingListener mEventSendingListener;

    // listen when the events list is scrolled.
    protected IOnScrollListener mActivityOnScrollListener;

    // the history filling is suspended when the fragment is not active
    // because there is no way to detect if enough data were retrieved
    private boolean mFillHistoryOnResume;

    /**
     * Get MxMediaCache
     *
     * @return
     */
    public abstract MXMediaCache getMXMediaCache();

    /**
     * Get MxSession
     *
     * @param matrixId
     * @return
     */
    public abstract MXSession getSession(String matrixId);

    public MXSession getSession() {
        return mSession;
    }

    /**
     * @return an UI handler
     */
    private Handler getUiHandler() {
        if (null == mUiHandler) {
            mUiHandler = new Handler(Looper.getMainLooper());
        }

        return mUiHandler;
    }

    private final IMXEventListener mEventsListener = new MXEventListener() {

        @Override
        public void onEventSentStateUpdated(Event event) {
            getUiHandler().post(new Runnable() {
                @Override
                public void run() {
                    mAdapter.notifyDataSetChanged();
                }
            });
        }

        private boolean mRefreshAfterEventsDecryption;

        @Override
        public void onEventDecrypted(final Event event) {
            getUiHandler().post(new Runnable() {
                @Override
                public void run() {
                    // avoid refreshing the whole list for each event
                    // they are often refreshed by bunches.
                    if (mRefreshAfterEventsDecryption) {
                        Log.d(LOG_TAG, "## onEventDecrypted " + event.eventId + " : there is a pending refresh");
                    } else {
                        Log.d(LOG_TAG, "## onEventDecrypted " + event.eventId);

                        mRefreshAfterEventsDecryption = true;
                        getUiHandler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                Log.d(LOG_TAG, "## onEventDecrypted : refresh the list");
                                mRefreshAfterEventsDecryption = false;
                                mAdapter.notifyDataSetChanged();
                            }
                        }, 500);
                    }
                }
            });
        }
    };

    private final RoomMediaMessage.EventCreationListener mEventCreationListener = new RoomMediaMessage.EventCreationListener() {
        @Override
        public void onEventCreated(RoomMediaMessage roomMediaMessage) {
            add(roomMediaMessage);
        }

        @Override
        public void onEventCreationFailed(RoomMediaMessage roomMediaMessage, String errorMessage) {
            displayMessageSendingFailed(errorMessage);
        }

        @Override
        public void onEncryptionFailed(RoomMediaMessage roomMediaMessage) {
            displayEncryptionAlert();
        }
    };

    /**
     * Customize the scrolls behaviour.
     * -> scroll over the top triggers a back pagination
     * -> scroll over the bottom triggers a forward pagination
     */
    protected final AbsListView.OnScrollListener mScrollListener = new AbsListView.OnScrollListener() {
        @Override
        public void onScrollStateChanged(AbsListView view, int scrollState) {
            mCheckSlideToHide = (scrollState == SCROLL_STATE_TOUCH_SCROLL);

            //check only when the user scrolls the content
            if (scrollState == SCROLL_STATE_TOUCH_SCROLL) {

                int firstVisibleRow = mMessageListView.getFirstVisiblePosition();
                int lastVisibleRow = mMessageListView.getLastVisiblePosition();
                int count = mMessageListView.getCount();

                if ((lastVisibleRow + 10) >= count) {
                    Log.d(LOG_TAG, "onScrollStateChanged - forwardPaginate");
                    forwardPaginate();
                } else if (firstVisibleRow < 10) {
                    Log.d(LOG_TAG, "onScrollStateChanged - request history");
                    backPaginate(false);
                }
            }

            if (null != mActivityOnScrollListener) {
                try {
                    mActivityOnScrollListener.onScrollStateChanged(scrollState);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "## manageScrollListener : onScrollStateChanged failed " + e.getMessage(), e);
                }
            }
        }

        /**
         * Warns that the list has been scrolled.
         * @param view the list view
         * @param firstVisibleItem the first visible indew
         * @param visibleItemCount the number of visible items
         * @param totalItemCount the total number of items
         */
        private void manageScrollListener(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
            if (null != mActivityOnScrollListener) {
                try {
                    mActivityOnScrollListener.onScroll(firstVisibleItem, visibleItemCount, totalItemCount);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "## manageScrollListener : onScroll failed " + e.getMessage(), e);
                }

                boolean isLatestEventDisplayed;

                // test if the latest message is not displayed
                if ((firstVisibleItem + visibleItemCount) < totalItemCount) {
                    // the latest event is not displayed
                    isLatestEventDisplayed = false;
                } else {
                    View childView = view.getChildAt(visibleItemCount - 1);

                    // test if the bottom of the latest item is equals to the list height
                    isLatestEventDisplayed = (null != childView) && ((childView.getTop() + childView.getHeight()) <= view.getHeight());
                }

                try {
                    mActivityOnScrollListener.onLatestEventDisplay(isLatestEventDisplayed);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "## manageScrollListener : onLatestEventDisplay failed " + e.getMessage(), e);
                }
            }
        }

        @Override
        public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
            // store the current Y pos to jump to the right pos when backpaginating
            mFirstVisibleRowY = UNDEFINED_VIEW_Y_POS;
            View v = mMessageListView.getChildAt((visibleItemCount == mMessageListView.getChildCount()) ? 0 : firstVisibleItem);

            if (null != v) {
                mFirstVisibleRowY = v.getTop();
            }

            if ((firstVisibleItem < 10) && (visibleItemCount != totalItemCount) && (0 != visibleItemCount)) {
                if (!mLockBackPagination) {
                    Log.d(LOG_TAG, "onScroll - backPaginate firstVisibleItem " + firstVisibleItem + " visibleItemCount "
                            + visibleItemCount + " totalItemCount " + totalItemCount);
                }
                backPaginate(false);
            } else if ((firstVisibleItem + visibleItemCount + 10) >= totalItemCount) {
                if (!mLockFwdPagination) {
                    Log.d(LOG_TAG, "onScroll - forwardPaginate firstVisibleItem " + firstVisibleItem + " visibleItemCount "
                            + visibleItemCount + " totalItemCount " + totalItemCount);
                }
                forwardPaginate();
            }

            manageScrollListener(view, firstVisibleItem, visibleItemCount, totalItemCount);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(LOG_TAG, "onCreate");

        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.d(LOG_TAG, "onCreateView");

        View defaultView = super.onCreateView(inflater, container, savedInstanceState);
        Bundle args = getArguments();

        // for dispatching data to add to the adapter we need to be on the main thread
        mUiHandler = new Handler(Looper.getMainLooper());

        mMatrixId = args.getString(ARG_MATRIX_ID);
        mSession = getSession(mMatrixId);

        if (null == mSession) {
            if (null != getActivity()) {
                Log.e(LOG_TAG, "Must have valid default MXSession.");
                getActivity().finish();
                return defaultView;
            }

            throw new RuntimeException("Must have valid default MXSession.");
        }

        if (null == getMXMediaCache()) {
            if (null != getActivity()) {
                Log.e(LOG_TAG, "Must have valid default MediaCache.");
                getActivity().finish();
                return defaultView;
            }

            throw new RuntimeException("Must have valid default MediaCache.");
        }

        mRoomId = args.getString(ARG_ROOM_ID);

        View v = inflater.inflate(args.getInt(ARG_LAYOUT_ID), container, false);
        mMessageListView = v.findViewById(R.id.listView_messages);
        mIsScrollListenerSet = false;

        if (mAdapter == null) {
            // only init the adapter if it wasn't before, so we can preserve messages/position.
            mAdapter = createMessagesAdapter();

            if (null == mAdapter) {
                throw new RuntimeException("Must have valid default MessagesAdapter.");
            }
        } else if (null != savedInstanceState) {
            mFirstVisibleRow = savedInstanceState.getInt("FIRST_VISIBLE_ROW", -1);
        }

        mAdapter.setIsPreviewMode(false);

        if (null == mEventTimeLine) {
            mEventId = args.getString(ARG_EVENT_ID);

            final String previewMode = args.getString(ARG_PREVIEW_MODE_ID);
            // the fragment displays the history around a message
            if (!TextUtils.isEmpty(mEventId)) {
                mEventTimeLine = EventTimelineFactory.pastTimeline(mSession.getDataHandler(), mRoomId, mEventId);
                mRoom = mEventTimeLine.getRoom();
                if (PREVIEW_MODE_UNREAD_MESSAGE.equals(previewMode)) {
                    mAdapter.setIsUnreadViewMode(true);
                }
            }
            // display a room preview
            else if (PREVIEW_MODE_READ_ONLY.equals(previewMode)) {
                mAdapter.setIsPreviewMode(true);
                mEventTimeLine = EventTimelineFactory.inMemoryTimeline(mSession.getDataHandler(), mRoomId);
                mRoom = mEventTimeLine.getRoom();
            }
            // standard case
            else {
                if (!TextUtils.isEmpty(mRoomId)) {
                    mRoom = mSession.getDataHandler().getRoom(mRoomId);
                    mEventTimeLine = mRoom.getTimeline();
                }
            }
        }
        mMessageListView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                onListTouch(event);
                return false;
            }
        });

        mDisplayAllEvents = isDisplayAllEvents();

        // Ensure all RoomMember are loaded (ignore error)
        // mRoom can be null for global search
        if (mRoom != null) {
            mRoom.getMembersAsync(new SimpleApiCallback<List<RoomMember>>() {
                @Override
                public void onSuccess(List<RoomMember> info) {
                    if (isAdded()) {
                        mAdapter.setLiveRoomMembers(info);
                    }
                }
            });
        }

        return v;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        if (null != mMessageListView) {
            int selected = mMessageListView.getFirstVisiblePosition();

            // ListView always returns the previous index while filling from bottom
            if (selected > 0) {
                selected++;
            }

            outState.putInt("FIRST_VISIBLE_ROW", selected);
        }
    }

    /**
     * Called when the fragment is no longer in use.  This is called
     * after {@link #onStop()} and before {@link #onDetach()}.
     */
    @Override
    public void onDestroy() {
        super.onDestroy();

        // remove listeners to prevent memory leak
        if (null != mMatrixMessagesFragment) {
            mMatrixMessagesFragment.setMatrixMessagesListener(null);
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        Bundle args = getArguments();
        FragmentManager fm = getChildFragmentManager();
        mMatrixMessagesFragment = (MatrixMessagesFragment) fm.findFragmentByTag(getMatrixMessagesFragmentTag());

        if (mMatrixMessagesFragment == null) {
            Log.d(LOG_TAG, "onActivityCreated create");

            // this fragment controls all the logic for handling messages / API calls
            mMatrixMessagesFragment = createMessagesFragmentInstance(args.getString(ARG_ROOM_ID));
            fm.beginTransaction().add(mMatrixMessagesFragment, getMatrixMessagesFragmentTag()).commit();
        } else {
            Log.d(LOG_TAG, "onActivityCreated - reuse");
        }

        // Set the listener
        mMatrixMessagesFragment.setMatrixMessagesListener(this);

        // Set the session
        mMatrixMessagesFragment.setMXSession(getSession());

        mMatrixMessagesFragment.mKeepRoomHistory = (-1 != mFirstVisibleRow);
    }

    @Override
    public void onPause() {
        super.onPause();

        mEventSendingListener = null;
        mActivityOnScrollListener = null;

        // clear maps
        mEventSendingListener = null;
        mActivityOnScrollListener = null;

        // check if the session has not been logged out
        if (null != mRoom) {
            mRoom.removeEventListener(mEventsListener);
        }

        cancelCatchingRequests();
    }

    @Override
    public void onResume() {
        super.onResume();

        Activity activity = getActivity();

        if (activity instanceof IEventSendingListener) {
            mEventSendingListener = (IEventSendingListener) activity;
        }

        if (activity instanceof IOnScrollListener) {
            mActivityOnScrollListener = (IOnScrollListener) activity;
        }

        // sanity check
        if ((null != mRoom) && mEventTimeLine.isLiveTimeline()) {
            Room room = mSession.getDataHandler().getRoom(mRoom.getRoomId(), false);

            if (null != room) {
                room.addEventListener(mEventsListener);
            } else {
                Log.e(LOG_TAG, "the room " + mRoom.getRoomId() + " does not exist anymore");
            }
        }

        // a room history filling was suspended because the fragment was not active
        if (mFillHistoryOnResume) {
            mFillHistoryOnResume = false;
            backPaginate(true);
        }
    }

    //==============================================================================================================
    // general methods
    //==============================================================================================================

    /**
     * Create the messageFragment.
     * Should be overridden.
     *
     * @param roomId the roomID
     * @return the MatrixMessagesFragment
     */
    public MatrixMessagesFragment createMessagesFragmentInstance(String roomId) {
        return MatrixMessagesFragment.newInstance(roomId);
    }

    /**
     * @return the fragment tag to use to restore the matrix messages fragment
     */
    protected String getMatrixMessagesFragmentTag() {
        return "org.matrix.androidsdk.RoomActivity.TAG_FRAGMENT_MATRIX_MESSAGES";
    }

    /**
     * Create the messages adapter.
     *
     * @return the messages adapter.
     */
    public abstract MessagesAdapter createMessagesAdapter();

    /**
     * The user scrolls the list.
     * Apply an expected behaviour
     *
     * @param event the scroll event
     */
    public void onListTouch(MotionEvent event) {
    }

    /**
     * Scroll the listview to a dedicated index when the list is loaded.
     *
     * @param index the index
     */
    public void scrollToIndexWhenLoaded(int index) {
        mScrollToIndex = index;
    }

    /**
     * @return true to display all the events else the unknown events will be hidden.
     */
    public boolean isDisplayAllEvents() {
        return true;
    }

    /**
     * @return the max thumbnail width
     */
    public int getMaxThumbnailWidth() {
        return mAdapter.getMaxThumbnailWidth();
    }

    /**
     * @return the max thumbnail height
     */
    public int getMaxThumbnailHeight() {
        return mAdapter.getMaxThumbnailHeight();
    }

    /**
     * Notify the adapter that some bing rules could have been updated.
     */
    public void onBingRulesUpdate() {
        mAdapter.onBingRulesUpdate();
    }

    /**
     * Scroll the listView to the last item.
     *
     * @param delayMs the delay before jumping to the latest event.
     */
    public void scrollToBottom(int delayMs) {
        mMessageListView.postDelayed(new Runnable() {
            @Override
            public void run() {
                mMessageListView.setSelection(mAdapter.getCount() - 1);
                // stop any scroll inertia after the jump
                mMessageListView.smoothScrollBy(0, 0);
            }
        }, Math.max(delayMs, 0));
    }

    /**
     * Scroll the listview to the last item.
     */
    public void scrollToBottom() {
        scrollToBottom(300);
    }

    /**
     * Provides the event for a dedicated row.
     *
     * @param row the row
     * @return the event
     */
    public Event getEvent(int row) {
        Event event = null;

        if (mAdapter.getCount() > row) {
            event = mAdapter.getItem(row).getEvent();
        }

        return event;
    }

    /**
     * Test if the read marker must be updated with the new message
     *
     * @param newMessageRow        the new message row
     * @param currentReadMarkerRow the current read marker row
     * @return true if the read marker can be updated
     */
    private boolean canUpdateReadMarker(MessageRow newMessageRow, MessageRow currentReadMarkerRow) {
        return (currentReadMarkerRow != null
                && mAdapter.getPosition(newMessageRow) == mAdapter.getPosition(currentReadMarkerRow) + 1
                && newMessageRow.getEvent().getOriginServerTs() > currentReadMarkerRow.getEvent().originServerTs);
    }

    /**
     * Provides the read "marked row".
     * The closest row is provided if it is not displayed
     *
     * @return the currentReadMarkerRow
     */
    private MessageRow getReadMarkerMessageRow(MessageRow newMessageRow) {
        final String currentReadMarkerEventId = mRoom.getReadMarkerEventId();
        MessageRow currentReadMarkerRow = mAdapter.getMessageRow(currentReadMarkerEventId);

        if (null == currentReadMarkerRow) {
            // crash reported by GA
            try {
                Event readMarkedEvent = mSession.getDataHandler().getStore().getEvent(currentReadMarkerEventId, mRoom.getRoomId());

                // the read marked event might be a non displayable event
                if ((null != readMarkedEvent) && !canAddEvent(readMarkedEvent)) {
                    // retrieve the previous displayed event
                    currentReadMarkerRow = mAdapter.getClosestRowFromTs(readMarkedEvent.eventId, readMarkedEvent.getOriginServerTs());

                    // the undisplayable event might be in the middle of two displayable events
                    // or it is the last known event
                    if ((null != currentReadMarkerRow) && !canUpdateReadMarker(newMessageRow, currentReadMarkerRow)) {
                        currentReadMarkerRow = null;
                    }

                    // use the next one
                    if (null == currentReadMarkerRow) {
                        currentReadMarkerRow = mAdapter.getClosestRowBeforeTs(readMarkedEvent.eventId, readMarkedEvent.getOriginServerTs());
                    }
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "## getReadMarkerMessageRow() failed : " + e.getMessage(), e);
            }
        }

        return currentReadMarkerRow;
    }

    // create a dummy message row for the message
    // It is added to the Adapter
    // return the created Message
    private MessageRow addMessageRow(RoomMediaMessage roomMediaMessage) {
        // a message row can only be added if there is a defined room
        if (null != mRoom) {
            Event event = roomMediaMessage.getEvent();
            MessageRow newMessageRow = new MessageRow(event, mRoom.getState());
            mAdapter.add(newMessageRow);

            // Move read marker if necessary
            MessageRow currentReadMarkerRow = getReadMarkerMessageRow(newMessageRow);

            if (canUpdateReadMarker(newMessageRow, currentReadMarkerRow)) {
                View childView = mMessageListView.getChildAt(mMessageListView.getChildCount() - 1);

                // Previous message was the last read
                if ((null != childView) && (childView.getTop() >= 0)) {
                    // New message is fully visible, keep reference to move the read marker once server echo is received
                    mFutureReadMarkerEventId = event.eventId;
                    mAdapter.resetReadMarker();
                }
            }

            scrollToBottom();

            getSession().getDataHandler().getStore().commit();
            return newMessageRow;
        } else {
            return null;
        }
    }

    /**
     * Redact an event from its event id.
     *
     * @param eventId the event id.
     */
    protected void redactEvent(final String eventId) {
        // Do nothing on success, the event will be hidden when the redaction event comes down the event stream
        mMatrixMessagesFragment.redact(eventId, new ApiCallback<Event>() {
            @Override
            public void onSuccess(final Event redactedEvent) {
                if (null != redactedEvent) {
                    getUiHandler().post(new Runnable() {
                        @Override
                        public void run() {
                            // create a dummy redacted event to manage the redaction.
                            // some redacted events are not removed from the history but they are pruned.

                            Event redacterEvent = new Event();
                            redacterEvent.roomId = redactedEvent.roomId;
                            redacterEvent.redacts = redactedEvent.eventId;
                            redacterEvent.setType(Event.EVENT_TYPE_REDACTION);

                            onEvent(redacterEvent, EventTimeline.Direction.FORWARDS, mRoom.getState());

                            if (null != mEventSendingListener) {
                                try {
                                    mEventSendingListener.onMessageRedacted(redactedEvent);
                                } catch (Exception e) {
                                    Log.e(LOG_TAG, "redactEvent fails : " + e.getMessage(), e);
                                }
                            }
                        }
                    });
                }
            }

            private void onError() {
                if (null != getActivity()) {
                    Toast.makeText(getActivity(), R.string.could_not_redact, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onNetworkError(Exception e) {
                onError();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                onError();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                onError();
            }
        });
    }

    /**
     * Tells if an event is supported by the fragment.
     *
     * @param event the event to test
     * @return true it is supported.
     */
    protected boolean canAddEvent(final Event event) {
        final String type = event.getType();
        return mDisplayAllEvents
                || Event.EVENT_TYPE_MESSAGE.equals(type)
                || Event.EVENT_TYPE_MESSAGE_ENCRYPTED.equals(type)
                || Event.EVENT_TYPE_MESSAGE_ENCRYPTION.equals(type)
                || Event.EVENT_TYPE_STATE_ROOM_NAME.equals(type)
                || Event.EVENT_TYPE_STATE_ROOM_TOPIC.equals(type)
                || Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(type)
                || Event.EVENT_TYPE_STATE_ROOM_THIRD_PARTY_INVITE.equals(type)
                || Event.EVENT_TYPE_STATE_HISTORY_VISIBILITY.equals(type)
                || Event.EVENT_TYPE_STICKER.equals(type)
                || Event.EVENT_TYPE_STATE_ROOM_CREATE.equals(type)
                || (event.isCallEvent() && !Event.EVENT_TYPE_CALL_CANDIDATES.equals(type));
    }

    //==============================================================================================================
    // Messages sending method.
    //==============================================================================================================

    /**
     * Warns that a message sending has failed.
     *
     * @param event the event
     */
    private void onMessageSendingFailed(Event event) {
        if (null != mEventSendingListener) {
            try {
                mEventSendingListener.onMessageSendingFailed(event);
            } catch (Exception e) {
                Log.e(LOG_TAG, "onMessageSendingFailed failed " + e.getMessage(), e);
            }
        }
    }

    /**
     * Warns that a message sending succeeds.
     *
     * @param event the event
     */
    private void onMessageSendingSucceeded(Event event) {
        if (null != mEventSendingListener) {
            try {
                mEventSendingListener.onMessageSendingSucceeded(event);
            } catch (Exception e) {
                Log.e(LOG_TAG, "onMessageSendingSucceeded failed " + e.getMessage(), e);
            }
        }
    }

    /**
     * Warns that a message sending failed because some unknown devices have been detected.
     *
     * @param event       the event
     * @param cryptoError the crypto error
     */
    private void onUnknownDevices(Event event, MXCryptoError cryptoError) {
        if (null != mEventSendingListener) {
            try {
                mEventSendingListener.onUnknownDevices(event, cryptoError);
            } catch (Exception e) {
                Log.e(LOG_TAG, "onUnknownDevices failed " + e.getMessage(), e);
            }
        }
    }

    /**
     * Warns that a message sending failed because user consent has not been given.
     *
     * @param event       the event
     * @param matrixError the MatrixError
     */
    private void onConsentNotGiven(Event event, MatrixError matrixError) {
        if (null != mEventSendingListener) {
            try {
                mEventSendingListener.onConsentNotGiven(event, matrixError);
            } catch (Exception e) {
                Log.e(LOG_TAG, "onConsentNotGiven failed " + e.getMessage(), e);
            }
        }
    }

    /**
     * Add a media item in the room.
     */
    private void add(final RoomMediaMessage roomMediaMessage) {
        MessageRow messageRow = addMessageRow(roomMediaMessage);

        // add sanity check
        if (null == messageRow) {
            return;
        }

        final Event event = messageRow.getEvent();

        if (!event.isUndelivered()) {
            ApiCallback<Void> callback = new ApiCallback<Void>() {
                @Override
                public void onSuccess(Void info) {
                    getUiHandler().post(new Runnable() {
                        @Override
                        public void run() {
                            onMessageSendingSucceeded(event);
                        }
                    });
                }

                private void commonFailure(final Event event) {
                    getUiHandler().post(new Runnable() {
                        @Override
                        public void run() {
                            Activity activity = getActivity();

                            if (null != activity) {
                                // display the error message only if the message cannot be resent
                                if ((null != event.unsentException) && (event.isUndelivered())) {
                                    if (event.unsentException instanceof IOException) {
                                        Toast.makeText(activity, activity.getString(R.string.unable_to_send_message) + " : "
                                                + activity.getString(R.string.network_error), Toast.LENGTH_LONG).show();
                                    } else {
                                        Toast.makeText(activity, activity.getString(R.string.unable_to_send_message) + " : "
                                                + event.unsentException.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                                    }
                                } else if (null != event.unsentMatrixError) {
                                    String localised = (event.unsentMatrixError instanceof MXCryptoError) ?
                                            ((MXCryptoError) event.unsentMatrixError).getDetailedErrorDescription()
                                            : event.unsentMatrixError.getLocalizedMessage();
                                    Toast.makeText(activity, activity.getString(R.string.unable_to_send_message) + " : "
                                            + localised, Toast.LENGTH_LONG).show();
                                }

                                mAdapter.notifyDataSetChanged();
                                onMessageSendingFailed(event);
                            }
                        }
                    });
                }

                @Override
                public void onNetworkError(final Exception e) {
                    commonFailure(event);
                }

                @Override
                public void onMatrixError(final MatrixError e) {
                    // do not display toast if the sending failed because of unknown device (e2e issue)
                    if (event.mSentState == Event.SentState.FAILED_UNKNOWN_DEVICES) {
                        getUiHandler().post(new Runnable() {
                            @Override
                            public void run() {
                                mAdapter.notifyDataSetChanged();
                                onUnknownDevices(event, (MXCryptoError) e);
                            }
                        });
                    } else if (MatrixError.M_CONSENT_NOT_GIVEN.equals(e.errcode)) {
                        getUiHandler().post(new Runnable() {
                            @Override
                            public void run() {
                                mAdapter.notifyDataSetChanged();
                                onConsentNotGiven(event, e);
                            }
                        });
                    } else {
                        commonFailure(event);
                    }
                }

                @Override
                public void onUnexpectedError(final Exception e) {
                    commonFailure(event);
                }
            };

            roomMediaMessage.setEventSendingCallback(callback);
        }
    }

    /**
     * Send a text message.
     *
     * @param body the text message to send.
     */
    public void sendTextMessage(String body) {
        sendTextMessage(Message.MSGTYPE_TEXT, body, null);
    }

    /**
     * Send a formatted text message.
     *
     * @param body          the unformatted text message
     * @param formattedBody the formatted text message (optional)
     * @param format        the format
     */
    public void sendTextMessage(String body, String formattedBody, String format) {
        mRoom.sendTextMessage(body, formattedBody, format, mEventCreationListener);
    }

    /**
     * Send a formatted text message, replying to a event Id if not null.
     *
     * @param body          the unformatted text message
     * @param formattedBody the formatted text message (optional)
     * @param replyToEvent  the event to reply to (optional)
     * @param format        the format
     */
    public void sendTextMessage(String body,
                                String formattedBody,
                                @Nullable Event replyToEvent,
                                String format) {
        mRoom.sendTextMessage(body, formattedBody, format, replyToEvent, mEventCreationListener);
    }

    /**
     * Send an emote
     *
     * @param emote          the emote
     * @param formattedEmote the formatted text message (optional)
     * @param format         the format
     */
    public void sendEmote(String emote, String formattedEmote, String format) {
        mRoom.sendEmoteMessage(emote, formattedEmote, format, mEventCreationListener);
    }

    /**
     * Send a sticker message to the room
     *
     * @param event
     */
    public void sendStickerMessage(Event event) {
        mRoom.sendStickerMessage(event, mEventCreationListener);
    }

    /**
     * The media upload fails.
     *
     * @param serverResponseCode the response code.
     * @param serverErrorMessage the error message.
     * @param messageRow         the messageRow
     */
    private void commonMediaUploadError(int serverResponseCode, final String serverErrorMessage, final MessageRow messageRow) {
        // warn the user that the media upload fails
        if (serverResponseCode == 500) {
            messageRow.getEvent().mSentState = Event.SentState.WAITING_RETRY;

            try {
                Timer relaunchTimer = new Timer();
                relaunchTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        if (mPendingRelaunchTimersByEventId.containsKey(messageRow.getEvent().eventId)) {
                            mPendingRelaunchTimersByEventId.remove(messageRow.getEvent().eventId);

                            Handler handler = new Handler(Looper.getMainLooper());
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    resend(messageRow.getEvent());
                                }
                            });
                        }
                    }
                }, 1000);
                mPendingRelaunchTimersByEventId.put(messageRow.getEvent().eventId, relaunchTimer);
            } catch (Throwable throwable) {
                Log.e(LOG_TAG, "relaunchTimer.schedule failed " + throwable.getMessage(), throwable);
            }
        } else {
            messageRow.getEvent().mSentState = Event.SentState.UNDELIVERED;
            onMessageSendingFailed(messageRow.getEvent());
            mAdapter.notifyDataSetChanged();

            if (null != getActivity()) {
                Toast.makeText(getActivity(),
                        (null != serverErrorMessage) ? serverErrorMessage : getString(R.string.message_failed_to_upload),
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Display an encryption alert
     */
    private void displayEncryptionAlert() {
        if (null != getActivity()) {
            new AlertDialog.Builder(getActivity())
                    .setMessage("Fail to encrypt?")
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            // continue with delete
                        }
                    })
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .show();
        }
    }

    /**
     * The failure reason
     *
     * @param errorMessage the message
     */
    private void displayMessageSendingFailed(String errorMessage) {
        if (null != getActivity()) {
            new AlertDialog.Builder(getActivity())
                    .setMessage(errorMessage)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            // continue with delete
                        }
                    })
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .show();
        }
    }


    /**
     * Send a media message in this room
     *
     * @param roomMediaMessage the media message to send
     */
    public void sendMediaMessage(final RoomMediaMessage roomMediaMessage) {
        mRoom.sendMediaMessage(roomMediaMessage, getMaxThumbnailWidth(), getMaxThumbnailHeight(), mEventCreationListener);

        roomMediaMessage.setMediaUploadListener(new MXMediaUploadListener() {
            @Override
            public void onUploadStart(String uploadId) {
                onMessageSendingSucceeded(roomMediaMessage.getEvent());
                mAdapter.notifyDataSetChanged();
            }

            @Override
            public void onUploadCancel(String uploadId) {
                onMessageSendingFailed(roomMediaMessage.getEvent());
                mAdapter.notifyDataSetChanged();
            }

            @Override
            public void onUploadError(final String uploadId, final int serverResponseCode, final String serverErrorMessage) {
                commonMediaUploadError(serverResponseCode, serverErrorMessage, mAdapter.getMessageRow(roomMediaMessage.getEvent().eventId));
            }

            @Override
            public void onUploadComplete(final String uploadId, final String contentUri) {
                Log.d(LOG_TAG, "Uploaded to " + contentUri);
            }
        });
    }

    //==============================================================================================================
    // Unsent messages management
    //==============================================================================================================


    /**
     * Delete the unsent (undeliverable messages).
     */
    public void deleteUnsentEvents() {
        List<Event> unsent = mRoom.getUnsentEvents();

        mRoom.deleteEvents(unsent);

        for (Event event : unsent) {
            mAdapter.removeEventById(event.eventId);
        }

        mAdapter.notifyDataSetChanged();
    }

    /**
     * Resend the unsent messages
     */
    public void resendUnsentMessages() {
        // check if the call is done in the right thread
        if (Looper.getMainLooper().getThread() != Thread.currentThread()) {
            Handler handler = new Handler(Looper.getMainLooper());

            handler.post(new Runnable() {
                @Override
                public void run() {
                    resendUnsentMessages();
                }
            });

            return;
        }

        List<Event> unsent = mRoom.getUnsentEvents();

        for (Event unsentMessage : unsent) {
            resend(unsentMessage);
        }
    }

    /**
     * Resend an event.
     *
     * @param event the event to resend.
     */
    protected void resend(final Event event) {
        // sanity check
        // should never happen but got it in a GA issue
        if (null == event.eventId) {
            Log.e(LOG_TAG, "resend : got an event with a null eventId");
            return;
        }

        // check if the call is done in the right thread
        if (Looper.getMainLooper().getThread() != Thread.currentThread()) {
            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(new Runnable() {
                @Override
                public void run() {
                    resend(event);
                }
            });
            return;
        }

        // update the timestamp
        event.originServerTs = System.currentTimeMillis();

        // remove the event
        getSession().getDataHandler().deleteRoomEvent(event);
        mAdapter.removeEventById(event.eventId);
        mPendingRelaunchTimersByEventId.remove(event.eventId);

        // send it again
        final Message message = JsonUtils.toMessage(event.getContent());
        final RoomMediaMessage roomMediaMessage = new RoomMediaMessage(new Event(message, mSession.getMyUserId(), mRoom.getRoomId()));

        // Restore the previous eventId, to use the same TransactionId when sending again the event
        roomMediaMessage.getEvent().eventId = event.eventId;

        if (message instanceof MediaMessage) {
            sendMediaMessage(roomMediaMessage);
        } else {
            // default case : text / emote
            // skip the upload progress
            mRoom.sendMediaMessage(roomMediaMessage, getMaxThumbnailWidth(), getMaxThumbnailHeight(), mEventCreationListener);
        }
    }

    //==============================================================================================================
    // UI stuff
    //==============================================================================================================

    /**
     * Display a spinner to warn the user that a back pagination is in progress.
     */
    public void showLoadingBackProgress() {
    }

    /**
     * Dismiss the back pagination progress.
     */
    public void hideLoadingBackProgress() {
    }

    /**
     * Display a spinner to warn the user that a forward pagination is in progress.
     */
    public void showLoadingForwardProgress() {
    }

    /**
     * Dismiss the forward pagination progress.
     */
    public void hideLoadingForwardProgress() {
    }

    /**
     * Display a spinner to warn the user that the initialization is in progress.
     */
    public void showInitLoading() {
    }

    /**
     * Dismiss the initialization spinner.
     */
    public void hideInitLoading() {
    }

    /**
     * Refresh the messages list.
     */
    public void refresh() {
        mAdapter.notifyDataSetChanged();
    }

    //==============================================================================================================
    // pagination methods
    //==============================================================================================================

    /**
     * Manage the request history error cases.
     *
     * @param error the error object.
     */
    private void onPaginateRequestError(final Object error) {
        Activity activity = getActivity();

        if (null != activity) {
            if (error instanceof Exception) {
                Log.e(LOG_TAG, "Network error: " + ((Exception) error).getMessage(), (Exception) error);
                Toast.makeText(activity, activity.getString(R.string.network_error), Toast.LENGTH_SHORT).show();

            } else if (error instanceof MatrixError) {
                final MatrixError matrixError = (MatrixError) error;
                Log.e(LOG_TAG, "Matrix error" + " : " + matrixError.errcode + " - " + matrixError.getMessage());
                Toast.makeText(activity, activity.getString(R.string.matrix_error) + " : " + matrixError.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
            }

            hideLoadingBackProgress();
            hideLoadingForwardProgress();
            Log.d(LOG_TAG, "requestHistory failed " + error);
            mIsBackPaginating = false;
        }
    }

    /**
     * Start a forward pagination
     */
    private void forwardPaginate() {
        if (mLockFwdPagination) {
            Log.d(LOG_TAG, "The forward pagination is locked.");
            return;
        }

        if ((null == mEventTimeLine) || mEventTimeLine.isLiveTimeline()) {
            //Log.d(LOG_TAG, "The forward pagination is not supported for the live timeline.");
            return;
        }

        if (mIsFwdPaginating) {
            Log.d(LOG_TAG, "A forward pagination is in progress, please wait.");
            return;
        }

        // reject any forward paginate if the fragment is not active
        // it might happen in some race conditions
        // eg the forward pagination response is managed just after putting the app in foreground
        if (!isResumed()) {
            Log.d(LOG_TAG, "ignore forward pagination because the fragment is not active");
            return;
        }

        showLoadingForwardProgress();

        final int countBeforeUpdate = mAdapter.getCount();

        mIsFwdPaginating = mEventTimeLine.forwardPaginate(new ApiCallback<Integer>() {
            /**
             * the forward pagination is ended.
             */
            private void onEndOfPagination(String errorMessage) {
                if (null != errorMessage) {
                    Log.e(LOG_TAG, "forwardPaginate fails : " + errorMessage);
                }

                mIsFwdPaginating = false;
                hideLoadingForwardProgress();
            }

            @Override
            public void onSuccess(Integer count) {
                final int firstPos = mMessageListView.getFirstVisiblePosition();

                mLockBackPagination = true;

                // retrieve
                if (0 != count) {
                    // trick to avoid that the list jump to the latest item.
                    mMessageListView.lockSelectionOnResize();
                    mAdapter.notifyDataSetChanged();

                    // keep the first position while refreshing the list
                    mMessageListView.setSelection(firstPos);

                    mMessageListView.post(new Runnable() {
                        @Override
                        public void run() {
                            // Scroll the list down to where it was before adding rows to the top
                            int diff = mAdapter.getCount() - countBeforeUpdate;
                            Log.d(LOG_TAG, "forwardPaginate ends with " + diff + " new items.");

                            onEndOfPagination(null);
                            mLockBackPagination = false;
                        }
                    });
                } else {
                    Log.d(LOG_TAG, "forwardPaginate ends : nothing to add");
                    onEndOfPagination(null);
                    mLockBackPagination = false;
                }
            }

            @Override
            public void onNetworkError(Exception e) {
                onEndOfPagination(e.getLocalizedMessage());
            }

            @Override
            public void onMatrixError(MatrixError e) {
                onEndOfPagination(e.getLocalizedMessage());
            }

            @Override
            public void onUnexpectedError(Exception e) {
                onEndOfPagination(e.getLocalizedMessage());
            }
        });

        if (mIsFwdPaginating) {
            Log.d(LOG_TAG, "forwardPaginate starts");
            showLoadingForwardProgress();
        } else {
            hideLoadingForwardProgress();
            Log.d(LOG_TAG, "forwardPaginate nothing to do");
        }
    }

    /**
     * Set the scroll listener to mMessageListView
     */
    protected void setMessageListViewScrollListener() {
        // ensure that the listener is set only once
        // else it triggers an inifinite loop with backPaginate.
        if (!mIsScrollListenerSet) {
            mIsScrollListenerSet = true;
            mMessageListView.setOnScrollListener(mScrollListener);
        }
    }

    /**
     * Trigger a back pagination.
     *
     * @param fillHistory true to try to fill the listview height.
     */
    public void backPaginate(final boolean fillHistory) {
        if (mIsBackPaginating) {
            Log.d(LOG_TAG, "backPaginate is in progress : please wait");
            return;
        }

        if (mIsInitialSyncing) {
            Log.d(LOG_TAG, "backPaginate : an initial sync is in progress");
            return;
        }

        if (mLockBackPagination) {
            Log.d(LOG_TAG, "backPaginate : The back pagination is locked.");
            return;
        }

        // search mode
        // The search mode uses remote requests only
        // i.e the eventtimeline is not used.
        // so the dedicated method must manage the back pagination
        if (!TextUtils.isEmpty(mPattern)) {
            Log.d(LOG_TAG, "backPaginate with pattern " + mPattern);
            requestSearchHistory();
            return;
        }

        if (!mMatrixMessagesFragment.canBackPaginate()) {
            Log.d(LOG_TAG, "backPaginate : cannot back paginating again");
            setMessageListViewScrollListener();
            return;
        }

        if (!isResumed()) {
            Log.d(LOG_TAG, "backPaginate : the fragment is not anymore active");
            mFillHistoryOnResume = true;
            return;
        }

        final int countBeforeUpdate = mAdapter.getCount();

        mIsBackPaginating = mMatrixMessagesFragment.backPaginate(new SimpleApiCallback<Integer>(getActivity()) {
            @Override
            public void onSuccess(final Integer count) {

                // Scroll the list down to where it was before adding rows to the top
                mMessageListView.post(new Runnable() {
                    @Override
                    public void run() {
                        mLockFwdPagination = true;

                        final int countDiff = mAdapter.getCount() - countBeforeUpdate;
                        final int firstVisiblePosition = mMessageListView.getFirstVisiblePosition();

                        Log.d(LOG_TAG, "backPaginate : ends with " + countDiff + " new items (total : " + mAdapter.getCount() + ")");

                        // check if some messages have been added
                        if (0 != countDiff) {
                            // trick to avoid that the list jump to the latest item.
                            mMessageListView.lockSelectionOnResize();
                            mAdapter.notifyDataSetChanged();
                            final int expectedPos = fillHistory ? (mAdapter.getCount() - 1) : (firstVisiblePosition + countDiff);

                            Log.d(LOG_TAG, "backPaginate : expect to jump to " + expectedPos);

                            if (fillHistory || (UNDEFINED_VIEW_Y_POS == mFirstVisibleRowY)) {
                                // do not use count because some messages are not displayed
                                // so we compute the new pos
                                mMessageListView.setSelection(expectedPos);
                            } else {
                                mMessageListView.setSelectionFromTop(expectedPos, -mFirstVisibleRowY);
                            }

                            mMessageListView.post(new Runnable() {
                                @Override
                                public void run() {
                                    Log.d(LOG_TAG, "backPaginate : jump to " + mMessageListView.getFirstVisiblePosition());
                                }
                            });
                        }

                        // Test if a back pagination can be done.
                        // countDiff == 0 is not relevant
                        // because the server can return an empty chunk
                        // but the start and the end tokens are not equal.
                        // It seems often happening with the room visibility feature
                        if (mMatrixMessagesFragment.canBackPaginate()) {
                            Log.d(LOG_TAG, "backPaginate again");

                            mMessageListView.post(new Runnable() {
                                @Override
                                public void run() {
                                    mLockFwdPagination = false;
                                    mIsBackPaginating = false;

                                    mMessageListView.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            // back paginate until getting something to display
                                            if (0 == countDiff) {
                                                Log.d(LOG_TAG, "backPaginate again because there was nothing in the current chunk");
                                                backPaginate(fillHistory);
                                            } else if (fillHistory) {
                                                if ((mMessageListView.getVisibility() == View.VISIBLE) && mMessageListView.getFirstVisiblePosition() < 10) {
                                                    Log.d(LOG_TAG, "backPaginate : fill history");
                                                    backPaginate(fillHistory);
                                                } else {
                                                    Log.d(LOG_TAG, "backPaginate : history should be filled");
                                                    hideLoadingBackProgress();
                                                    mIsInitialSyncing = false;
                                                    setMessageListViewScrollListener();
                                                }
                                            } else {
                                                hideLoadingBackProgress();
                                            }
                                        }
                                    });
                                }
                            });
                        } else {
                            Log.d(LOG_TAG, "no more backPaginate");
                            setMessageListViewScrollListener();
                            hideLoadingBackProgress();
                            mIsBackPaginating = false;
                            mLockFwdPagination = false;
                        }
                    }
                });
            }

            // the request will be auto restarted when a valid network will be found
            @Override
            public void onNetworkError(Exception e) {
                onPaginateRequestError(e);
            }

            @Override
            public void onMatrixError(MatrixError e) {
                onPaginateRequestError(e);
            }

            @Override
            public void onUnexpectedError(Exception e) {
                onPaginateRequestError(e);
            }
        });

        if (mIsBackPaginating && (null != getActivity())) {
            Log.d(LOG_TAG, "backPaginate : starts");
            showLoadingBackProgress();
        } else {
            Log.d(LOG_TAG, "requestHistory : nothing to do");
        }
    }

    /**
     * Cancel the catching requests.
     */
    public void cancelCatchingRequests() {
        mPattern = null;

        if (null != mEventTimeLine) {
            mEventTimeLine.cancelPaginationRequests();
        }

        mIsInitialSyncing = false;
        mIsBackPaginating = false;
        mIsFwdPaginating = false;

        mLockBackPagination = false;
        mLockFwdPagination = false;

        hideInitLoading();
        hideLoadingBackProgress();
        hideLoadingForwardProgress();
    }

    /**
     * Scroll to the given row
     *
     * @param messageRow the message row.
     * @param isLastRead true if the row is the latest read one.
     */
    public void scrollToRow(final MessageRow messageRow, boolean isLastRead) {
        final int distanceFromTop = (int) (getResources().getDisplayMetrics().density * 100);
        final int lastReadRowIndex = mAdapter.getPosition(messageRow);
        // Scroll to the first unread row if possible, last read otherwise
        final int targetRow = isLastRead && lastReadRowIndex < mMessageListView.getCount() - 1
                ? lastReadRowIndex + 1 : lastReadRowIndex;
        // Scroll to the last read so we can see the beginning of the first unread (in majority of cases)
        mMessageListView.setSelectionFromTop(targetRow, distanceFromTop);
    }

    //==============================================================================================================
    // MatrixMessagesFragment methods
    //==============================================================================================================

    @Override
    public void onEvent(final Event event, final EventTimeline.Direction direction, final RoomState roomState) {
        if (null == event) {
            Log.e(LOG_TAG, "## onEvent() : null event");
            return;
        }

        if (TextUtils.equals(event.eventId, mEventId)) {
            // Save timestamp in case this event will not be added in adapter
            mEventOriginServerTs = event.getOriginServerTs();
        }

        if (direction == EventTimeline.Direction.FORWARDS) {
            if (Event.EVENT_TYPE_REDACTION.equals(event.getType())) {
                MessageRow messageRow = mAdapter.getMessageRow(event.getRedactedEventId());

                if (null != messageRow) {
                    Event prunedEvent = mSession.getDataHandler().getStore().getEvent(event.getRedactedEventId(), event.roomId);

                    if (null == prunedEvent) {
                        mAdapter.removeEventById(event.getRedactedEventId());
                    } else {
                        messageRow.updateEvent(prunedEvent);
                        JsonObject content = messageRow.getEvent().getContentAsJsonObject();

                        boolean hasToRemoved = (null == content) || (null == content.entrySet()) || (0 == content.entrySet().size());

                        // test if the event is displayable
                        // GA issue : the activity can be null
                        if (!hasToRemoved && (null != getActivity())) {
                            EventDisplay eventDisplay = new EventDisplay(getActivity());
                            hasToRemoved = TextUtils.isEmpty(eventDisplay.getTextualDisplay(prunedEvent, roomState));
                        }

                        // event is removed if it has no more content.
                        if (hasToRemoved) {
                            mAdapter.removeEventById(prunedEvent.eventId);
                        }
                    }

                    mAdapter.notifyDataSetChanged();
                }
            } else {
                if (canAddEvent(event)) {
                    // refresh the listView only when it is a live timeline or a search
                    MessageRow newMessageRow = new MessageRow(event, roomState);
                    mAdapter.add(newMessageRow, (null == mEventTimeLine) || mEventTimeLine.isLiveTimeline());

                    // Move read marker if necessary
                    if (isResumed() && mEventTimeLine != null && mEventTimeLine.isLiveTimeline()) {
                        MessageRow currentReadMarkerRow = getReadMarkerMessageRow(newMessageRow);

                        if (canUpdateReadMarker(newMessageRow, currentReadMarkerRow)) {
                            if (0 == mMessageListView.getChildCount()) {
                                mMessageListView.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        // check if the previous one was displayed
                                        View childView = mMessageListView.getChildAt(mMessageListView.getChildCount() - 2);

                                        // Previous message was the last read
                                        if ((null != childView) && (childView.getTop() >= 0)) {
                                            // Move read marker to the newly sent message
                                            mRoom.setReadMakerEventId(event.eventId);
                                            mAdapter.resetReadMarker();
                                        }
                                    }
                                });
                            } else {
                                View childView = mMessageListView.getChildAt(mMessageListView.getChildCount() - 1);

                                // Previous message was the last read
                                if ((null != childView) && (childView.getTop() >= 0)) {
                                    // Move read marker to the newly sent message
                                    mRoom.setReadMakerEventId(event.eventId);
                                    mAdapter.resetReadMarker();
                                }
                            }
                        }
                    }
                }
            }
        } else {
            if (canAddEvent(event)) {
                mAdapter.addToFront(new MessageRow(event, roomState));
            }
        }
    }

    @Override
    public void onEventSent(final Event event, final String prevEventId) {
        // detect if a message was sent but not yet added to the adapter
        // For example, the quick reply does not use the fragment to send messages
        // Thus, the messages are not added to the adapter.
        // onEvent is not called because the server event echo manages an event sent by itself
        if ((null == mAdapter.getMessageRow(event.eventId)) && canAddEvent(event)) {
            if (null != mAdapter.getMessageRow(prevEventId)) {
                mAdapter.updateEventById(event, prevEventId);
            } else {
                // refresh the listView only when it is a live timeline or a search
                mAdapter.add(new MessageRow(event, mRoom.getState()), true);
            }

            if (mFutureReadMarkerEventId != null && prevEventId.equals(mFutureReadMarkerEventId)) {
                mFutureReadMarkerEventId = null;
                // Move read marker to the newly sent message
                mRoom.setReadMakerEventId(event.eventId);
                RoomSummary summary = mRoom.getDataHandler().getStore().getSummary(mRoom.getRoomId());

                if (summary != null) {
                    String readReceiptEventId = summary.getReadReceiptEventId();
                    // Inform adapter of the new read marker position
                    mAdapter.updateReadMarker(event.eventId, readReceiptEventId);
                }
            }
        } else {
            MessageRow row = mAdapter.getMessageRow(prevEventId);
            if (null != row) {
                mAdapter.remove(row);
            }
        }
    }

    @Override
    public void onLiveEventsChunkProcessed() {
        // NOP
    }

    @Override
    public void onReceiptEvent(List<String> senderIds) {
        // avoid useless refresh
        boolean shouldRefresh = true;

        try {
            IMXStore store = mSession.getDataHandler().getStore();
            int firstPos = mMessageListView.getFirstVisiblePosition();
            int lastPos = mMessageListView.getLastVisiblePosition();

            List<String> senders = new ArrayList<>();
            List<String> eventIds = new ArrayList<>();

            for (int index = firstPos; index <= lastPos; index++) {
                Event event = mAdapter.getItem(index).getEvent();

                if ((null != event.getSender()) && (null != event.eventId)) {
                    senders.add(event.getSender());
                    eventIds.add(event.eventId);
                }
            }

            shouldRefresh = false;

            // check if the receipt will trigger a refresh
            for (String sender : senderIds) {
                if (!TextUtils.equals(sender, mSession.getMyUserId())) {
                    ReceiptData receipt = store.getReceipt(mRoom.getRoomId(), sender);

                    // sanity check
                    if (null != receipt) {
                        // test if the event is displayed
                        int pos = eventIds.indexOf(receipt.eventId);

                        // if displayed
                        if (pos >= 0) {
                            // the sender is not displayed as a reader (makes sense...)
                            shouldRefresh = !TextUtils.equals(senders.get(pos), sender);

                            if (shouldRefresh) {
                                break;
                            }
                        }
                    }
                }
            }

        } catch (Exception e) {
            Log.e(LOG_TAG, "onReceiptEvent failed with " + e.getMessage(), e);
        }

        if (shouldRefresh) {
            mAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onInitialMessagesLoaded() {
        Log.d(LOG_TAG, "onInitialMessagesLoaded");

        // Jump to the bottom of the list
        getUiHandler().post(new Runnable() {
            @Override
            public void run() {
                // should never happen but reported by GA
                if (null == mMessageListView) {
                    return;
                }

                hideLoadingBackProgress();

                if (null == mMessageListView.getAdapter()) {
                    mMessageListView.setAdapter(mAdapter);
                }

                if ((null == mEventTimeLine) || mEventTimeLine.isLiveTimeline()) {

                    if (mAdapter.getCount() > 0) {
                        // refresh the list only at the end of the sync
                        // else the one by one message refresh gives a weird UX
                        // The application is almost frozen during the
                        mAdapter.notifyDataSetChanged();

                        if (mScrollToIndex >= 0) {
                            mMessageListView.setSelection(mScrollToIndex);
                            mScrollToIndex = -1;
                        } else {
                            mMessageListView.setSelection(mAdapter.getCount() - 1);
                        }
                    }

                    // fill the page
                    mMessageListView.post(new Runnable() {
                        @Override
                        public void run() {
                            if ((mMessageListView.getVisibility() == View.VISIBLE) && mMessageListView.getFirstVisiblePosition() < 10) {
                                Log.d(LOG_TAG, "onInitialMessagesLoaded : fill history");
                                backPaginate(true);
                            } else {
                                Log.d(LOG_TAG, "onInitialMessagesLoaded : history should be filled");
                                mIsInitialSyncing = false;
                                setMessageListViewScrollListener();
                            }
                        }
                    });
                } else {
                    Log.d(LOG_TAG, "onInitialMessagesLoaded : default behaviour");

                    if ((0 != mAdapter.getCount()) && (mScrollToIndex > 0)) {
                        mAdapter.notifyDataSetChanged();
                        mMessageListView.setSelection(mScrollToIndex);
                        mScrollToIndex = -1;

                        mMessageListView.post(new Runnable() {
                            @Override
                            public void run() {
                                mIsInitialSyncing = false;
                                setMessageListViewScrollListener();
                            }
                        });

                    } else {
                        mIsInitialSyncing = false;
                        setMessageListViewScrollListener();
                    }
                }
            }
        });
    }

    @Override
    public EventTimeline getEventTimeLine() {
        return mEventTimeLine;
    }

    @Override
    public void onTimelineInitialized() {
        if (getActivity() == null || getActivity().isFinishing()) {
            return;
        }

        mMessageListView.post(new Runnable() {
            @Override
            public void run() {
                // reported by a rageshake
                if (null == getActivity()) {
                    Log.e(LOG_TAG, "## onTimelineInitialized : the fragment is not anymore attached to an activity");
                    return;
                }

                mLockFwdPagination = false;
                mIsInitialSyncing = false;
                // search the event pos in the adapter
                // some events are not displayed so the added events count cannot be used.
                int eventPos = 0;

                if (mAdapter.isUnreadViewMode() && mAdapter.getMessageRow(mEventId) == null) {
                    // Event is not in adapter, try to find the closest one
                    final MessageRow closestRowAfter = mAdapter.getClosestRowFromTs(mEventId, mEventOriginServerTs);
                    final int closestRowAfterPos = mAdapter.getPosition(closestRowAfter);

                    MessageRow closestRowBefore = closestRowAfter;
                    if (closestRowAfterPos > 0) {
                        closestRowBefore = mAdapter.getItem(closestRowAfterPos - 1);
                    }

                    if (closestRowBefore != null) {
                        mAdapter.updateReadMarker(closestRowBefore.getEvent().eventId, null);
                    }

                    mAdapter.notifyDataSetChanged();
                    mMessageListView.setAdapter(mAdapter);

                    if (closestRowBefore != null) {
                        scrollToRow(closestRowBefore, true);
                    }
                } else {
                    for (; eventPos < mAdapter.getCount(); eventPos++) {
                        if (TextUtils.equals(mAdapter.getItem(eventPos).getEvent().eventId, mEventId)) {
                            break;
                        }
                    }

                    mAdapter.notifyDataSetChanged();
                    mMessageListView.setAdapter(mAdapter);

                    // center the message
                    if (mAdapter.isUnreadViewMode()) {
                        // In unread view mode, mEventId is the last read so set selection to the first unread
                        scrollToRow(mAdapter.getMessageRow(mEventId), true);
                    } else {
                        View parentView = (View) mMessageListView.getParent();
                        mMessageListView.setSelectionFromTop(eventPos, parentView.getHeight() / 2);
                    }
                }
            }
        });
    }

    @Override
    public RoomPreviewData getRoomPreviewData() {
        if (null != getActivity()) {
            // test if the listener has bee retrieved
            if (null == mRoomPreviewDataListener) {
                try {
                    mRoomPreviewDataListener = (IRoomPreviewDataListener) getActivity();
                } catch (ClassCastException e) {
                    Log.e(LOG_TAG, "getRoomPreviewData failed with " + e.getMessage(), e);
                }
            }

            if (null != mRoomPreviewDataListener) {
                return mRoomPreviewDataListener.getRoomPreviewData();
            }
        }

        return null;
    }

    @Override
    public void onRoomFlush() {
        mAdapter.clear();
    }

    //==============================================================================================================
    // search methods
    //==============================================================================================================

    /**
     * Cancel the current search
     */
    protected void cancelSearch() {
        mPattern = null;
    }

    /**
     * Search the pattern on a pagination server side.
     */
    public void requestSearchHistory() {
        // there is no more server message
        if (TextUtils.isEmpty(mNextBatch)) {
            mIsBackPaginating = false;
            return;
        }

        mIsBackPaginating = true;

        final int firstPos = mMessageListView.getFirstVisiblePosition();
        final String fPattern = mPattern;
        final int countBeforeUpdate = mAdapter.getCount();

        showLoadingBackProgress();

        List<String> roomIds = null;

        if (null != mRoom) {
            roomIds = Arrays.asList(mRoom.getRoomId());
        }

        ApiCallback<SearchResponse> callback = new ApiCallback<SearchResponse>() {
            @Override
            public void onSuccess(final SearchResponse searchResponse) {
                // check that the pattern was not modified before the end of the search
                if (TextUtils.equals(mPattern, fPattern)) {
                    List<SearchResult> searchResults = searchResponse.searchCategories.roomEvents.results;

                    // is there any result to display
                    if (0 != searchResults.size()) {
                        mAdapter.setNotifyOnChange(false);

                        for (SearchResult searchResult : searchResults) {
                            MessageRow row = new MessageRow(searchResult.result, (null == mRoom) ? null : mRoom.getState());
                            mAdapter.insert(row, 0);
                        }

                        mNextBatch = searchResponse.searchCategories.roomEvents.nextBatch;

                        // Scroll the list down to where it was before adding rows to the top
                        getUiHandler().post(new Runnable() {
                            @Override
                            public void run() {
                                final int expectedFirstPos = firstPos + (mAdapter.getCount() - countBeforeUpdate);

                                // trick to avoid that the list jump to the latest item.
                                mMessageListView.lockSelectionOnResize();
                                mAdapter.notifyDataSetChanged();

                                // do not use count because some messages are not displayed
                                // so we compute the new pos
                                mMessageListView.setSelection(expectedFirstPos);

                                mMessageListView.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        mIsBackPaginating = false;

                                        // fill the history
                                        if (mMessageListView.getFirstVisiblePosition() <= 2) {
                                            requestSearchHistory();
                                        }
                                    }
                                });
                            }
                        });
                    } else {
                        mIsBackPaginating = false;
                    }

                    hideLoadingBackProgress();
                }

            }

            private void onError() {
                mIsBackPaginating = false;
                hideLoadingBackProgress();
            }

            // the request will be auto restarted when a valid network will be found
            @Override
            public void onNetworkError(Exception e) {
                Log.e(LOG_TAG, "Network error: " + e.getMessage(), e);
                onError();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                Log.e(LOG_TAG, "Matrix error" + " : " + e.errcode + " - " + e.getMessage());
                onError();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                Log.e(LOG_TAG, "onUnexpectedError error" + e.getMessage(), e);
                onError();
            }
        };


        if (mIsMediaSearch) {
            mSession.searchMediaByName(mPattern, roomIds, mNextBatch, callback);
        } else {
            mSession.searchMessagesByText(mPattern, roomIds, mNextBatch, callback);
        }
    }

    /**
     * Manage the search response.
     *
     * @param searchResponse         the search response
     * @param onSearchResultListener the search result listener
     */
    protected void onSearchResponse(final SearchResponse searchResponse, final OnSearchResultListener onSearchResultListener) {
        List<SearchResult> searchResults = searchResponse.searchCategories.roomEvents.results;
        List<MessageRow> messageRows = new ArrayList<>(searchResults.size());

        for (SearchResult searchResult : searchResults) {
            RoomState roomState = null;

            if (null != mRoom) {
                roomState = mRoom.getState();
            }

            if (null == roomState) {
                Room room = mSession.getDataHandler().getStore().getRoom(searchResult.result.roomId);

                if (null != room) {
                    roomState = room.getState();
                }
            }

            boolean isValidMessage = false;

            if ((null != searchResult.result) && (null != searchResult.result.getContent())) {
                JsonObject object = searchResult.result.getContentAsJsonObject();

                if (null != object) {
                    isValidMessage = (0 != object.entrySet().size());
                }
            }

            if (isValidMessage) {
                messageRows.add(new MessageRow(searchResult.result, roomState));
            }
        }

        Collections.reverse(messageRows);

        mAdapter.clear();
        mAdapter.addAll(messageRows);

        mNextBatch = searchResponse.searchCategories.roomEvents.nextBatch;

        if (null != onSearchResultListener) {
            try {
                onSearchResultListener.onSearchSucceed(messageRows.size());
            } catch (Exception e) {
                Log.e(LOG_TAG, "onSearchResponse failed with " + e.getMessage(), e);
            }
        }
    }

    /**
     * Search a pattern in the messages.
     *
     * @param pattern                the pattern to search
     * @param onSearchResultListener the search callback
     */
    public void searchPattern(final String pattern, final OnSearchResultListener onSearchResultListener) {
        searchPattern(pattern, false, onSearchResultListener);
    }

    /**
     * Search a pattern in the messages.
     *
     * @param pattern                the pattern to search (filename for a media message)
     * @param isMediaSearch          true if is it is a media search.
     * @param onSearchResultListener the search callback
     */
    public void searchPattern(final String pattern, boolean isMediaSearch, final OnSearchResultListener onSearchResultListener) {
        if (!TextUtils.equals(mPattern, pattern)) {
            mPattern = pattern;
            mIsMediaSearch = isMediaSearch;
            mAdapter.setSearchPattern(mPattern);

            // something to search
            if (!TextUtils.isEmpty(mPattern)) {
                List<String> roomIds = null;

                // sanity checks
                if (null != mRoom) {
                    roomIds = Arrays.asList(mRoom.getRoomId());
                }

                //
                ApiCallback<SearchResponse> searchCallback = new ApiCallback<SearchResponse>() {
                    @Override
                    public void onSuccess(final SearchResponse searchResponse) {
                        getUiHandler().post(new Runnable() {
                            @Override
                            public void run() {
                                // check that the pattern was not modified before the end of the search
                                if (TextUtils.equals(mPattern, pattern)) {
                                    onSearchResponse(searchResponse, onSearchResultListener);
                                }
                            }
                        });
                    }

                    private void onError() {
                        getUiHandler().post(new Runnable() {
                            @Override
                            public void run() {
                                if (null != onSearchResultListener) {
                                    try {
                                        onSearchResultListener.onSearchFailed();
                                    } catch (Exception e) {
                                        Log.e(LOG_TAG, "onSearchResultListener failed with " + e.getMessage(), e);
                                    }
                                }
                            }
                        });
                    }

                    // the request will be auto restarted when a valid network will be found
                    @Override
                    public void onNetworkError(Exception e) {
                        Log.e(LOG_TAG, "Network error: " + e.getMessage(), e);
                        onError();
                    }

                    @Override
                    public void onMatrixError(MatrixError e) {
                        Log.e(LOG_TAG, "Matrix error" + " : " + e.errcode + " - " + e.getMessage());
                        onError();
                    }

                    @Override
                    public void onUnexpectedError(Exception e) {
                        Log.e(LOG_TAG, "onUnexpectedError error" + e.getMessage(), e);
                        onError();
                    }
                };

                if (isMediaSearch) {
                    mSession.searchMediaByName(mPattern, roomIds, null, searchCallback);

                } else {
                    mSession.searchMessagesByText(mPattern, roomIds, null, searchCallback);
                }
            }
        }
    }
}
