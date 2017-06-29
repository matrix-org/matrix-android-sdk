/*
 * Copyright 2015 OpenMarket Ltd
 * Copyright 2017 Vector Creations Ltd
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
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Browser;
import android.provider.MediaStore;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.text.TextUtils;

import org.matrix.androidsdk.crypto.MXCryptoError;
import org.matrix.androidsdk.rest.model.AudioMessage;
import org.matrix.androidsdk.util.Log;

import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import com.google.gson.JsonObject;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.R;
import org.matrix.androidsdk.adapters.MessageRow;
import org.matrix.androidsdk.adapters.MessagesAdapter;
import org.matrix.androidsdk.crypto.MXEncryptedAttachments;
import org.matrix.androidsdk.data.EventTimeline;
import org.matrix.androidsdk.data.store.IMXStore;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomPreviewData;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.data.RoomSummary;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.listeners.IMXEventListener;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.listeners.MXMediaUploadListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.FileMessage;
import org.matrix.androidsdk.rest.model.ImageMessage;
import org.matrix.androidsdk.rest.model.LocationMessage;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.Message;
import org.matrix.androidsdk.rest.model.ReceiptData;
import org.matrix.androidsdk.rest.model.Search.SearchResponse;
import org.matrix.androidsdk.rest.model.Search.SearchResult;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.rest.model.VideoMessage;
import org.matrix.androidsdk.rest.model.bingrules.BingRule;
import org.matrix.androidsdk.util.EventDisplay;
import org.matrix.androidsdk.util.JsonUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;


/**
 * UI Fragment containing matrix messages for a given room.
 * Contains {@link MatrixMessagesFragment} as a nested fragment to do the work.
 */
public class MatrixMessageListFragment extends Fragment implements MatrixMessagesFragment.MatrixMessagesListener, MessagesAdapter.MessagesAdapterEventsListener {

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
    }

    protected static final String TAG_FRAGMENT_MESSAGE_OPTIONS = "org.matrix.androidsdk.RoomActivity.TAG_FRAGMENT_MESSAGE_OPTIONS";
    protected static final String TAG_FRAGMENT_MESSAGE_DETAILS = "org.matrix.androidsdk.RoomActivity.TAG_FRAGMENT_MESSAGE_DETAILS";

    // fragment parameters
    public static final String ARG_LAYOUT_ID = "MatrixMessageListFragment.ARG_LAYOUT_ID";
    public static final String ARG_MATRIX_ID = "MatrixMessageListFragment.ARG_MATRIX_ID";
    public static final String ARG_ROOM_ID = "MatrixMessageListFragment.ARG_ROOM_ID";
    public static final String ARG_EVENT_ID = "MatrixMessageListFragment.ARG_EVENT_ID";
    public static final String ARG_PREVIEW_MODE_ID = "MatrixMessageListFragment.ARG_PREVIEW_MODE_ID";

    // default preview mode
    public static final String PREVIEW_MODE_READ_ONLY = "PREVIEW_MODE_READ_ONLY";

    private static final String LOG_TAG = "MatrixMsgsListFrag";

    private static final int UNDEFINED_VIEW_Y_POS = -12345678;

    public static MatrixMessageListFragment newInstance(String matrixId, String roomId, int layoutResId) {
        MatrixMessageListFragment f = new MatrixMessageListFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ROOM_ID, roomId);
        args.putInt(ARG_LAYOUT_ID, layoutResId);
        args.putString(ARG_MATRIX_ID, matrixId);
        return f;
    }

    private MatrixMessagesFragment mMatrixMessagesFragment;
    protected MessagesAdapter mAdapter;
    public ListView mMessageListView;
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

    // by default the
    protected EventTimeline mEventTimeLine;
    protected String mEventId;

    // pagination statuses
    protected boolean mIsInitialSyncing = true;
    protected boolean mIsBackPaginating = false;
    protected boolean mIsFwdPaginating = false;

    // lock the pagination while refreshing the list view to avoid having twice or thrice refreshes sequence.
    private boolean mLockBackPagination = false;
    private boolean mLockFwdPagination = true;

    protected ArrayList<Event> mResendingEventsList;
    private final HashMap<String, Timer> mPendingRelaunchTimersByEventId = new HashMap<>();

    private final HashMap<String, Object> mBingRulesByEventId = new HashMap<>();

    // scroll to to the dedicated index when the device has been rotated
    private int mFirstVisibleRow = -1;

    // scroll to the index when loaded
    private int mScrollToIndex = -1;

    // y pos of the first visible row
    private int mFirstVisibleRowY = UNDEFINED_VIEW_Y_POS;

    // used to retrieve the preview data
    protected IRoomPreviewDataListener mRoomPreviewDataListener;

    // be warned that an event sending has failed.
    protected IEventSendingListener mEventSendingListener;

    // listen when the events list is scrolled.
    protected IOnScrollListener mActivityOnScrollListener;

    public MXMediasCache getMXMediasCache() {
        return null;
    }

    public MXSession getSession(String matrixId) {
        return null;
    }

    public MXSession getSession() {
        // if the session has not been set
        if (null == mSession) {
            // find it out
            mSession = getSession(mMatrixId);
        }

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
        public void onPresenceUpdate(Event event, final User user) {
            // Someone's presence has changed, reprocess the whole list
            getUiHandler().post(new Runnable() {
                @Override
                public void run() {
                    // check first if the userID has sent some messages in the room history
                    boolean refresh = mAdapter.isDisplayedUser(user.user_id);

                    if (refresh) {
                        // check, if the avatar is currently displayed

                        // The Math.min is required because the adapter and mMessageListView could be unsynchronized.
                        // ensure there is no IndexOfOutBound exception.
                        int firstVisibleRow = Math.min(mMessageListView.getFirstVisiblePosition(), mAdapter.getCount());
                        int lastVisibleRow = Math.min(mMessageListView.getLastVisiblePosition(), mAdapter.getCount());

                        refresh = false;

                        for (int i = firstVisibleRow; i <= lastVisibleRow; i++) {
                            MessageRow row = mAdapter.getItem(i);
                            refresh |= TextUtils.equals(user.user_id, row.getEvent().getSender());
                        }
                    }

                    if (refresh) {
                        mAdapter.notifyDataSetChanged();
                    }
                }
            });
        }

        @Override
        public void onBingRulesUpdate() {
            mBingRulesByEventId.clear();
        }

        @Override
        public void onEventEncrypted(Event event) {
            getUiHandler().post(new Runnable() {
                @Override
                public void run() {
                    mAdapter.notifyDataSetChanged();
                }
            });
        }

        @Override
        public void onEventDecrypted(Event event) {
            getUiHandler().post(new Runnable() {
                @Override
                public void run() {
                    mAdapter.notifyDataSetChanged();
                }
            });
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
                } else if (firstVisibleRow < 2) {
                    Log.d(LOG_TAG, "onScrollStateChanged - request history");
                    backPaginate(false);
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
                    Log.e(LOG_TAG, "## manageScrollListener : onScroll failed " + e.getMessage());
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
                    Log.e(LOG_TAG, "## manageScrollListener : onLatestEventDisplay failed " + e.getMessage());
                }
            }
        }

        @Override
        public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
            // store the current Y pos to jump to the right pos when backpaginating
            mFirstVisibleRowY = UNDEFINED_VIEW_Y_POS;
            View v = mMessageListView.getChildAt(firstVisibleItem);
            if (null != v) {
                mFirstVisibleRowY = v.getTop();
            }

            if ((firstVisibleItem < 2) && (visibleItemCount != totalItemCount) && (0 != visibleItemCount)) {
                // Log.d(LOG_TAG, "onScroll - backPaginate");
                backPaginate(false);
            } else if ((firstVisibleItem + visibleItemCount + 10) >= totalItemCount) {
                // Log.d(LOG_TAG, "onScroll - forwardPaginate");
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
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
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

        if (null == getMXMediasCache()) {
            if (null != getActivity()) {
                Log.e(LOG_TAG, "Must have valid default MediasCache.");
                getActivity().finish();
                return defaultView;
            }

            throw new RuntimeException("Must have valid default MediasCache.");
        }

        String roomId = args.getString(ARG_ROOM_ID);

        View v = inflater.inflate(args.getInt(ARG_LAYOUT_ID), container, false);
        mMessageListView = ((ListView) v.findViewById(R.id.listView_messages));
        mIsScrollListenerSet = false;

        if (mAdapter == null) {
            // only init the adapter if it wasn't before, so we can preserve messages/position.
            mAdapter = createMessagesAdapter();

            if (null == getMXMediasCache()) {
                throw new RuntimeException("Must have valid default MessagesAdapter.");
            }
        } else if (null != savedInstanceState) {
            mFirstVisibleRow = savedInstanceState.getInt("FIRST_VISIBLE_ROW", -1);
        }

        mAdapter.setIsPreviewMode(false);

        if (null == mEventTimeLine) {
            mEventId = args.getString(ARG_EVENT_ID);

            // the fragment displays the history around a message
            if (!TextUtils.isEmpty(mEventId)) {
                mEventTimeLine = new EventTimeline(mSession.getDataHandler(), roomId, mEventId);
                mRoom = mEventTimeLine.getRoom();
            }
            // display a room preview
            else if (null != args.getString(ARG_PREVIEW_MODE_ID)) {
                mAdapter.setIsPreviewMode(true);
                mEventTimeLine = new EventTimeline(mSession.getDataHandler(), roomId);
                mRoom = mEventTimeLine.getRoom();
            }
            // standard case
            else {
                if (!TextUtils.isEmpty(roomId)) {
                    mRoom = mSession.getDataHandler().getRoom(roomId);
                    mEventTimeLine = mRoom.getLiveTimeLine();
                }
            }
        }

        // GA reported some weird room content
        // so ensure that the room fields are properly initialized
        mSession.getDataHandler().checkRoom(mRoom);

        // sanity check
        if (null != mRoom) {
            mAdapter.setTypingUsers(mRoom.getTypingUsers());
        }

        mMessageListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                MatrixMessageListFragment.this.onRowClick(position);
            }
        });

        mMessageListView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                onListTouch(event);
                return false;
            }
        });

        mAdapter.setMessagesAdapterEventsListener(this);

        mDisplayAllEvents = isDisplayAllEvents();

        return v;
    }

    /**
     * Called when a fragment is first attached to its activity.
     * {@link #onCreate(Bundle)} will be called after this.
     *
     * @param aHostActivity parent activity
     */
    @Override
    public void onAttach(Activity aHostActivity) {
        super.onAttach(aHostActivity);

        try {
            mEventSendingListener = (IEventSendingListener) aHostActivity;
        } catch (ClassCastException e) {
            // if host activity does not provide the implementation, just ignore it
            Log.w(LOG_TAG, "## onAttach(): host activity does not implement IEventSendingListener " + aHostActivity);
        }

        try {
            mActivityOnScrollListener = (IOnScrollListener) aHostActivity;
        } catch (ClassCastException e) {
            // if host activity does not provide the implementation, just ignore it
            Log.w(LOG_TAG, "## onAttach(): host activity does not implement IOnScrollListener " + aHostActivity);
        }
    }

    /**
     * Called when the fragment is no longer attached to its activity.  This
     * is called after {@link #onDestroy()}.
     */
    @Override
    public void onDetach() {
        super.onDetach();
        mEventSendingListener = null;
        mActivityOnScrollListener = null;
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
        if (null != mAdapter) {
            mAdapter.setMessagesAdapterEventsListener(null);
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        Bundle args = getArguments();
        FragmentManager fm = getActivity().getSupportFragmentManager();
        mMatrixMessagesFragment = (MatrixMessagesFragment) fm.findFragmentByTag(getMatrixMessagesFragmentTag());

        if (mMatrixMessagesFragment == null) {
            Log.d(LOG_TAG, "onActivityCreated create");

            // this fragment controls all the logic for handling messages / API calls
            mMatrixMessagesFragment = createMessagesFragmentInstance(args.getString(ARG_ROOM_ID));
            fm.beginTransaction().add(mMatrixMessagesFragment, getMatrixMessagesFragmentTag()).commit();
        } else {
            Log.d(LOG_TAG, "onActivityCreated - reuse");

            // Reset the listener because this is not done when the system restores the fragment (newInstance is not called)
            mMatrixMessagesFragment.setMatrixMessagesListener(this);
        }

        mMatrixMessagesFragment.mKeepRoomHistory = (-1 != mFirstVisibleRow);
    }

    @Override
    public void onPause() {
        super.onPause();

        //
        mBingRulesByEventId.clear();

        // check if the session has not been logged out
        if (mSession.isAlive() && (null != mRoom) && mIsLive) {
            mRoom.removeEventListener(mEventsListener);
        }

        cancelCatchingRequests();
    }

    @Override
    public void onResume() {
        super.onResume();

        // sanity check
        if ((null != mRoom) && mIsLive) {
            Room room = mSession.getDataHandler().getRoom(mRoom.getRoomId(), false);

            if (null != room) {
                room.addEventListener(mEventsListener);
            } else {
                Log.e(LOG_TAG, "the room " + mRoom.getRoomId() + " does not exist anymore");
            }
        }
    }

    //==============================================================================================================
    // general methods
    //==============================================================================================================

    /**
     * Create the messageFragment.
     * Should be inherited.
     *
     * @param roomId the roomID
     * @return the MatrixMessagesFragment
     */
    public MatrixMessagesFragment createMessagesFragmentInstance(String roomId) {
        return MatrixMessagesFragment.newInstance(getSession(), roomId, this);
    }

    /**
     * @return the fragment tag to use to restore the matrix messages fragment
     */
    protected String getMatrixMessagesFragmentTag() {
        return "org.matrix.androidsdk.RoomActivity.TAG_FRAGMENT_MATRIX_MESSAGES";
    }

    /**
     * Create the messages adapter.
     * This method must be overriden to provide a valid creation
     *
     * @return the messages adapter.
     */
    public MessagesAdapter createMessagesAdapter() {
        return null;
    }

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
     * return true to display all the events.
     * else the unknown events will be hidden.
     */
    public boolean isDisplayAllEvents() {
        return true;
    }

    /**
     * @return the max thumbnail width
     */
    public int getMaxThumbnailWith() {
        return mAdapter.getMaxThumbnailWith();
    }

    /**
     * @return the max thumbnail height
     */
    public int getMaxThumbnailHeight() {
        return mAdapter.getMaxThumbnailHeight();
    }

    /**
     * Notify the fragment that some bing rules could have been updated.
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

    // create a dummy message row for the message
    // It is added to the Adapter
    // return the created Message
    private MessageRow addMessageRow(Message message) {
        // a message row can only be added if there is a defined room
        if (null != mRoom) {
            Event event = new Event(message, mSession.getCredentials().userId, mRoom.getRoomId());
            mRoom.storeOutgoingEvent(event);

            MessageRow messageRow = new MessageRow(event, mRoom.getState());
            mAdapter.add(messageRow);

            scrollToBottom();

            Log.d(LOG_TAG, "AddMessage Row : commit");
            getSession().getDataHandler().getStore().commit();
            return messageRow;
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

                            onEvent(redacterEvent, EventTimeline.Direction.FORWARDS, mRoom.getLiveState());

                            if (null != mEventSendingListener) {
                                try {
                                    mEventSendingListener.onMessageRedacted(redactedEvent);
                                } catch (Exception e) {
                                    Log.e(LOG_TAG, "redactEvent fails : " + e.getMessage());
                                }
                            }
                        }
                    });
                }
            }

            private void onError() {
                if (null != getActivity()) {
                    Toast.makeText(getActivity(), getActivity().getString(R.string.could_not_redact), Toast.LENGTH_SHORT).show();
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
    private boolean canAddEvent(Event event) {
        String type = event.getType();

        return mDisplayAllEvents ||
                Event.EVENT_TYPE_MESSAGE.equals(type) ||
                Event.EVENT_TYPE_MESSAGE_ENCRYPTED.equals(type) ||
                Event.EVENT_TYPE_MESSAGE_ENCRYPTION.equals(type) ||
                Event.EVENT_TYPE_STATE_ROOM_NAME.equals(type) ||
                Event.EVENT_TYPE_STATE_ROOM_TOPIC.equals(type) ||
                Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(type) ||
                Event.EVENT_TYPE_STATE_ROOM_THIRD_PARTY_INVITE.equals(type) ||
                Event.EVENT_TYPE_STATE_HISTORY_VISIBILITY.equals(type) ||
                (event.isCallEvent() && (!Event.EVENT_TYPE_CALL_CANDIDATES.equals(type)))
                ;
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
                Log.e(LOG_TAG, "onMessageSendingFailed failed " + e.getLocalizedMessage());
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
                Log.e(LOG_TAG, "onMessageSendingSucceeded failed " + e.getLocalizedMessage());
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
                Log.e(LOG_TAG, "onUnknownDevices failed " + e.getLocalizedMessage());
            }
        }
    }

    /**
     * Send a message in the room.
     *
     * @param message the message to send.
     */
    private void send(final Message message) {
        send(addMessageRow(message));
    }

    /**
     * Send a message row in the dedicated room.
     *
     * @param messageRow the message row to send.
     */
    private void send(final MessageRow messageRow) {
        // add sanity check
        if (null == messageRow) {
            return;
        }

        final Event event = messageRow.getEvent();

        if (!event.isUndeliverable()) {
            final String prevEventId = event.eventId;

            mMatrixMessagesFragment.sendEvent(event, new ApiCallback<Void>() {
                @Override
                public void onSuccess(Void info) {
                    getUiHandler().post(new Runnable() {
                        @Override
                        public void run() {
                            onMessageSendingSucceeded(event);
                            mAdapter.updateEventById(event, prevEventId);

                            // pending resending ?
                            if ((null != mResendingEventsList) && (mResendingEventsList.size() > 0)) {
                                resend(mResendingEventsList.get(0));
                                mResendingEventsList.remove(0);
                            }
                        }
                    });
                }

                private void commonFailure(final Event event) {
                    if (null != MatrixMessageListFragment.this.getActivity()) {
                        getUiHandler().post(new Runnable() {
                            @Override
                            public void run() {
                                // display the error message only if the message cannot be resent
                                if ((null != event.unsentException) && (event.isUndeliverable())) {
                                    if (event.unsentException instanceof IOException) {
                                        Toast.makeText(getActivity(), getActivity().getString(R.string.unable_to_send_message) + " : " + getActivity().getString(R.string.network_error), Toast.LENGTH_LONG).show();
                                    } else {
                                        Toast.makeText(getActivity(), getActivity().getString(R.string.unable_to_send_message) + " : " + event.unsentException.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                                    }
                                } else if (null != event.unsentMatrixError) {
                                    Toast.makeText(getActivity(), getActivity().getString(R.string.unable_to_send_message) + " : " + event.unsentMatrixError.getLocalizedMessage() + ".", Toast.LENGTH_LONG).show();
                                }

                                mAdapter.notifyDataSetChanged();
                                onMessageSendingFailed(event);
                            }
                        });
                    }
                }

                @Override
                public void onNetworkError(final Exception e) {
                    commonFailure(event);
                }

                @Override
                public void onMatrixError(final MatrixError e) {
                    // do not display toast if the sending failed because of unknown deviced (e2e issue)
                    if (event.mSentState == Event.SentState.FAILED_UNKNOWN_DEVICES) {
                        getUiHandler().post(new Runnable() {
                            @Override
                            public void run() {
                                mAdapter.notifyDataSetChanged();
                                onUnknownDevices(event, (MXCryptoError) e);
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
            });
        }
    }

    /**
     * Send a text message.
     *
     * @param body the text message to send.
     */
    public void sendTextMessage(String body) {
        sendMessage(Message.MSGTYPE_TEXT, body, null, null);
    }

    /**
     * Send a formatted text message.
     *
     * @param body          the unformatted text message
     * @param formattedBody the formatted text message (optional)
     * @param format        the format
     */
    public void sendTextMessage(String body, String formattedBody, String format) {
        sendMessage(Message.MSGTYPE_TEXT, body, formattedBody, format);
    }

    /**
     * Send a message of type msgType with a formatted body
     *
     * @param msgType       the message type
     * @param body          the unformatted text message
     * @param formattedBody the formatted text message (optional)
     * @param format        the format
     */
    private void sendMessage(String msgType, String body, String formattedBody, String format) {
        Message message = new Message();
        message.msgtype = msgType;
        message.body = body;

        if (null != formattedBody) {
            // assume that the formatted body use a custom html format
            message.format = format;
            message.formatted_body = formattedBody;
        }

        send(message);
    }

    /**
     * Send an emote
     *
     * @param emote          the emote
     * @param formattedEmote the formatted text message (optional)
     * @param format         the format
     */
    public void sendEmote(String emote, String formattedEmote, String format) {
        sendMessage(Message.MSGTYPE_EMOTE, emote, formattedEmote, format);
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
            Timer relaunchTimer = new Timer();
            mPendingRelaunchTimersByEventId.put(messageRow.getEvent().eventId, relaunchTimer);
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
        } else {
            messageRow.getEvent().mSentState = Event.SentState.UNDELIVERABLE;
            onMessageSendingFailed(messageRow.getEvent());

            if (null != getActivity()) {
                Toast.makeText(getActivity(),
                        (null != serverErrorMessage) ? serverErrorMessage : getString(R.string.message_failed_to_upload),
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Upload a file content
     *
     * @param mediaUrl      the media URL
     * @param mimeType      the media mime type
     * @param mediaFilename the media filename
     */
    public void uploadFileContent(final String mediaUrl, String mimeType, final String mediaFilename) {
        // create a tmp row
        final FileMessage tmpFileMessage;

        if ((null != mimeType) && mimeType.startsWith("audio/")) {
            tmpFileMessage = new AudioMessage();
        } else {
            tmpFileMessage = new FileMessage();
        }

        tmpFileMessage.url = mediaUrl;
        tmpFileMessage.body = mediaFilename;

        MXEncryptedAttachments.EncryptionResult encryptionResult = null;
        InputStream fileStream = null;

        try {
            Uri uri = Uri.parse(mediaUrl);
            Room.fillFileInfo(getActivity(), tmpFileMessage, uri, mimeType);

            String filename = uri.getPath();
            fileStream = new FileInputStream(new File(filename));

            if (mRoom.isEncrypted() && mSession.isCryptoEnabled() && (null != fileStream)) {
                encryptionResult = MXEncryptedAttachments.encryptAttachment(fileStream, mimeType);
                fileStream.close();
                if (null != encryptionResult) {
                    fileStream = encryptionResult.mEncryptedStream;
                    mimeType = "application/octet-stream";
                } else {
                    displayEncryptionAlert();
                    return;
                }
            }

            if (null == tmpFileMessage.body) {
                tmpFileMessage.body = uri.getLastPathSegment();
            }

        } catch (Exception e) {
            Log.e(LOG_TAG, "uploadFileContent failed with " + e.getLocalizedMessage());
        }

        // remove any displayed MessageRow with this URL
        // to avoid duplicate
        final MessageRow messageRow = addMessageRow(tmpFileMessage);
        messageRow.getEvent().mSentState = Event.SentState.SENDING;

        final MXEncryptedAttachments.EncryptionResult fEncryptionResult = encryptionResult;

        getSession().getMediasCache().uploadContent(fileStream, tmpFileMessage.body, mimeType, mediaUrl, new MXMediaUploadListener() {
            @Override
            public void onUploadStart(String uploadId) {
                getUiHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        onMessageSendingSucceeded(messageRow.getEvent());
                        // display the pie chart.
                        mAdapter.notifyDataSetChanged();
                    }
                });
            }

            @Override
            public void onUploadCancel(String uploadId) {
                getUiHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        onMessageSendingFailed(messageRow.getEvent());
                    }
                });
            }

            @Override
            public void onUploadError(final String uploadId, final int serverResponseCode, final String serverErrorMessage) {
                getUiHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        commonMediaUploadError(serverResponseCode, serverErrorMessage, messageRow);
                    }
                });
            }

            @Override
            public void onUploadComplete(final String uploadId, final String contentUri) {
                getUiHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        // Build the image message
                        FileMessage message = tmpFileMessage.deepCopy();

                        // replace the thumbnail and the media contents by the computed ones
                        getMXMediasCache().saveFileMediaForUrl(contentUri, mediaUrl, tmpFileMessage.getMimeType());

                        if (null != fEncryptionResult) {
                            message.file = fEncryptionResult.mEncryptedFileInfo;
                            message.file.url = contentUri;
                            message.url = null;
                        } else {
                            message.url = contentUri;
                        }

                        // update the event content with the new message info
                        messageRow.getEvent().updateContent(JsonUtils.toJson(message));

                        Log.d(LOG_TAG, "Uploaded to " + contentUri);

                        send(messageRow);
                    }
                });
            }
        });
    }

    /**
     * Compute the video thumbnail
     *
     * @param videoUrl the video url
     * @return the video thumbnail
     */
    public String getVideoThumbnailUrl(final String videoUrl) {
        String thumbUrl = null;
        try {
            Uri uri = Uri.parse(videoUrl);
            Bitmap thumb = ThumbnailUtils.createVideoThumbnail(uri.getPath(), MediaStore.Images.Thumbnails.MINI_KIND);
            thumbUrl = getMXMediasCache().saveBitmap(thumb, null);
        } catch (Exception e) {
            Log.e(LOG_TAG, "getVideoThumbailUrl failed with " + e.getLocalizedMessage());
        }

        return thumbUrl;
    }

    /**
     * Upload a video message
     * The video thumbnail will be computed
     *
     * @param videoUrl      the video url
     * @param body          the message body
     * @param videoMimeType the video mime type
     */
    public void uploadVideoContent(final String videoUrl, final String body, final String videoMimeType) {
        uploadVideoContent(videoUrl, getVideoThumbnailUrl(videoUrl), body, videoMimeType);
    }

    /**
     * Upload a video message
     * The video thumbnail will be computed
     *
     * @param videoUrl      the video url
     * @param thumbUrl      the thumbnail Url
     * @param body          the message body
     * @param videoMimeType the video mime type
     */
    public void uploadVideoContent(final String videoUrl, final String thumbUrl, final String body, final String videoMimeType) {
        // if the video thumbnail cannot be retrieved
        // send it as a file
        if (null == thumbUrl) {
            this.uploadFileContent(videoUrl, videoMimeType, body);
        } else {
            this.uploadVideoContent(null, null, thumbUrl, "image/jpeg", videoUrl, body, videoMimeType);
        }
    }

    /**
     * Upload a video message
     *
     * @param thumbnailUrl      the thumbnail Url
     * @param thumbnailMimeType the thumbnail mime type
     * @param videoUrl          the video url
     * @param body              the message body
     * @param videoMimeType     the video mime type
     */
    public void uploadVideoContent(final VideoMessage sourceVideoMessage, final MessageRow aVideoRow, final String thumbnailUrl, final String thumbnailMimeType, final String videoUrl, final String body, final String videoMimeType) {
        // create a tmp row
        VideoMessage tmpVideoMessage = sourceVideoMessage;
        Uri uri = null;
        Uri thumbUri = null;

        try {
            uri = Uri.parse(videoUrl);
            thumbUri = Uri.parse(thumbnailUrl);
        } catch (Exception e) {
            Log.e(LOG_TAG, "uploadVideoContent failed with " + e.getLocalizedMessage());
        }

        // the video message is not defined
        if (null == tmpVideoMessage) {
            tmpVideoMessage = new VideoMessage();
            tmpVideoMessage.url = videoUrl;
            tmpVideoMessage.body = body;

            try {
                Room.fillVideoInfo(getActivity(), tmpVideoMessage, uri, videoMimeType, thumbUri, thumbnailMimeType);
                if (null == tmpVideoMessage.body) {
                    tmpVideoMessage.body = uri.getLastPathSegment();
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "uploadVideoContent : fillVideoInfo failed " + e.getLocalizedMessage());
            }
        }

        // remove any displayed MessageRow with this URL
        // to avoid duplicate
        final MessageRow videoRow = (null == aVideoRow) ? addMessageRow(tmpVideoMessage) : aVideoRow;
        videoRow.getEvent().mSentState = Event.SentState.SENDING;

        InputStream imageStream = null;
        String filename = "";
        String uploadId = "";
        String mimeType = "";

        MXEncryptedAttachments.EncryptionResult encryptionResult = null;
        try {
            // the thumbnail has been uploaded ?
            if (tmpVideoMessage.isThumbnailLocalContent()) {
                uploadId = thumbnailUrl;
                imageStream = new FileInputStream(new File(thumbUri.getPath()));
                mimeType = thumbnailMimeType;

                if (mRoom.isEncrypted() && mSession.isCryptoEnabled() && (null != imageStream)) {
                    encryptionResult = MXEncryptedAttachments.encryptAttachment(imageStream, thumbnailMimeType);
                    imageStream.close();

                    if (null != encryptionResult) {
                        imageStream = encryptionResult.mEncryptedStream;
                        mimeType = "application/octet-stream";
                    } else {
                        displayEncryptionAlert();
                        return;
                    }
                }
            } else {
                uploadId = videoUrl;
                imageStream = new FileInputStream(new File(uri.getPath()));
                filename = tmpVideoMessage.body;
                mimeType = videoMimeType;

                if (mRoom.isEncrypted() && mSession.isCryptoEnabled() && (null != imageStream)) {
                    encryptionResult = MXEncryptedAttachments.encryptAttachment(imageStream, thumbnailMimeType);
                    imageStream.close();

                    if (null != encryptionResult) {
                        imageStream = encryptionResult.mEncryptedStream;
                        mimeType = "application/octet-stream";
                    } else {
                        displayEncryptionAlert();
                        return;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "uploadVideoContent : media parsing failed " + e.getLocalizedMessage());
        }

        final boolean isContentUpload = TextUtils.equals(uploadId, videoUrl);
        final VideoMessage fVideoMessage = tmpVideoMessage;
        final MXEncryptedAttachments.EncryptionResult fEncryptionResult = encryptionResult;

        getSession().getMediasCache().uploadContent(imageStream, filename, mimeType, uploadId, new MXMediaUploadListener() {
            @Override
            public void onUploadStart(String uploadId) {
                getUiHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        onMessageSendingSucceeded(videoRow.getEvent());
                        mAdapter.notifyDataSetChanged();
                    }
                });
            }


            @Override
            public void onUploadCancel(String uploadId) {
                getUiHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        onMessageSendingFailed(videoRow.getEvent());
                    }
                });
            }

            @Override
            public void onUploadError(final String uploadId, final int serverResponseCode, final String serverErrorMessage) {
                getUiHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        commonMediaUploadError(serverResponseCode, serverErrorMessage, videoRow);
                    }
                });
            }

            @Override
            public void onUploadComplete(final String uploadId, final String contentUri) {
                getUiHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        // the video content has been uploaded
                        if (isContentUpload) {
                            // replace the thumbnail and the media contents by the computed ones
                            getMXMediasCache().saveFileMediaForUrl(contentUri, videoUrl, videoMimeType);

                            if (null == fEncryptionResult) {
                                fVideoMessage.url = contentUri;
                            } else {
                                fEncryptionResult.mEncryptedFileInfo.url = contentUri;
                                fVideoMessage.file = fEncryptionResult.mEncryptedFileInfo;
                                fVideoMessage.url = null;
                            }

                            // update the event content with the new message info
                            videoRow.getEvent().updateContent(JsonUtils.toJson(fVideoMessage));

                            Log.d(LOG_TAG, "Uploaded to " + contentUri);

                            send(videoRow);
                        } else {
                            if (null == fEncryptionResult) {
                                fVideoMessage.info.thumbnail_url = contentUri;
                                getMXMediasCache().saveFileMediaForUrl(contentUri, thumbnailUrl, mAdapter.getMaxThumbnailWith(), mAdapter.getMaxThumbnailHeight(), thumbnailMimeType, true);
                            } else {
                                fEncryptionResult.mEncryptedFileInfo.url = contentUri;
                                fVideoMessage.info.thumbnail_file = fEncryptionResult.mEncryptedFileInfo;
                                fVideoMessage.info.thumbnail_url = null;
                                getMXMediasCache().saveFileMediaForUrl(contentUri, thumbnailUrl, -1, -1, thumbnailMimeType, true);
                            }

                            // update the event content with the new message info
                            videoRow.getEvent().updateContent(JsonUtils.toJson(fVideoMessage));

                            // upload the video
                            uploadVideoContent(fVideoMessage, videoRow, thumbnailUrl, thumbnailMimeType, videoUrl, fVideoMessage.body, videoMimeType);
                        }
                    }
                });
            }
        });
    }

    /**
     * Display an encyption alert
     */
    private void displayEncryptionAlert() {
        if (null != getActivity()) {
            new AlertDialog.Builder(getActivity())
                    .setMessage("Fail to encrypt?")
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            // continue with delete
                        }
                    })
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .show();
        }
    }

    /**
     * upload an image content.
     * It might be triggered from a media selection : imageUri is used to compute thumbnails.
     * Or, it could have been called to resend an image.
     *
     * @param thumbnailUrl  the thumbnail Url
     * @param anImageUrl      the image Uri
     * @param mediaFilename the mediaFilename
     * @param imageMimeType the image mine type
     */
    public void uploadImageContent(ImageMessage imageMessage, final MessageRow aImageRow, final String thumbnailUrl, final String anImageUrl, final String mediaFilename, final String imageMimeType) {
        if (null == imageMessage) {
            imageMessage = new ImageMessage();
            imageMessage.url = anImageUrl;
            imageMessage.thumbnailUrl = thumbnailUrl;
            imageMessage.body = mediaFilename;
        }

        String mimeType = null;
        MXEncryptedAttachments.EncryptionResult encryptionResult = null;
        InputStream imageStream = null;
        String url = null;

        try {
            Uri imageUri = Uri.parse(anImageUrl);

            if (null == imageMessage.info) {
                Room.fillImageInfo(getActivity(), imageMessage, imageUri, imageMimeType);
            }

            if ((null != thumbnailUrl) && (null == imageMessage.thumbnailInfo)) {
                Uri thumbUri = Uri.parse(thumbnailUrl);
                Room.fillThumbnailInfo(getActivity(), imageMessage, thumbUri, "image/jpeg");
            }

            String filename;

            if (imageMessage.isThumbnailLocalContent()) {
                url = thumbnailUrl;
                mimeType = "image/jpeg";
                filename = Uri.parse(thumbnailUrl).getPath();
            } else {
                url = anImageUrl;
                mimeType = imageMimeType;
                filename = imageUri.getPath();
            }

            imageStream = new FileInputStream(new File(filename));

            if (mRoom.isEncrypted() && mSession.isCryptoEnabled() && (null != imageStream)) {
                encryptionResult = MXEncryptedAttachments.encryptAttachment(imageStream, mimeType);
                imageStream.close();

                if (null != encryptionResult) {
                    imageStream = encryptionResult.mEncryptedStream;
                    mimeType = "application/octet-stream";
                } else {
                    displayEncryptionAlert();
                    return;
                }
            }

            imageMessage.body = imageUri.getLastPathSegment();

        } catch (Exception e) {
            Log.e(LOG_TAG, "uploadImageContent failed with " + e.getMessage());
        }

        if (TextUtils.isEmpty(imageMessage.body)) {
            imageMessage.body = "Image";
        }

        // remove any displayed MessageRow with this URL
        // to avoid duplicate
        final String fMimeType = mimeType;
        final MessageRow imageRow = (null == aImageRow) ? addMessageRow(imageMessage) : aImageRow;
        final ImageMessage fImageMessage = imageMessage;
        imageRow.getEvent().mSentState = Event.SentState.SENDING;

        final MXEncryptedAttachments.EncryptionResult fEncryptionResult = encryptionResult;

        getSession().getMediasCache().uploadContent(imageStream, imageMessage.isThumbnailLocalContent() ? ("thumb" + imageMessage.body) : imageMessage.body, mimeType, url, new MXMediaUploadListener() {
            @Override
            public void onUploadStart(String uploadId) {
                getUiHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        onMessageSendingSucceeded(imageRow.getEvent());
                        mAdapter.notifyDataSetChanged();
                    }
                });
            }

            @Override
            public void onUploadCancel(String uploadId) {
                getUiHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        onMessageSendingFailed(imageRow.getEvent());
                    }
                });
            }

            @Override
            public void onUploadError(final String uploadId, final int serverResponseCode, final String serverErrorMessage) {
                getUiHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        commonMediaUploadError(serverResponseCode, serverErrorMessage, imageRow);
                    }
                });
            }

            @Override
            public void onUploadComplete(final String uploadId, final String contentUri) {
                getUiHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        if (fImageMessage.isThumbnailLocalContent()) {
                            if (null != fEncryptionResult) {
                                fImageMessage.info.thumbnail_file = fEncryptionResult.mEncryptedFileInfo;
                                fImageMessage.info.thumbnail_file.url = contentUri;
                                fImageMessage.thumbnailUrl = null;
                                getMXMediasCache().saveFileMediaForUrl(contentUri, thumbnailUrl, -1, -1, "image/jpeg");

                            } else {
                                fImageMessage.thumbnailUrl = contentUri;
                                getMXMediasCache().saveFileMediaForUrl(contentUri, thumbnailUrl, mAdapter.getMaxThumbnailWith(), mAdapter.getMaxThumbnailHeight(), "image/jpeg");
                            }

                            // update the event content with the new message info
                            imageRow.getEvent().updateContent(JsonUtils.toJson(fImageMessage));

                            // upload the high res picture
                            uploadImageContent(fImageMessage, imageRow, contentUri, anImageUrl, mediaFilename, fMimeType);
                        } else {
                            // replace the thumbnail and the media contents by the computed one
                            getMXMediasCache().saveFileMediaForUrl(contentUri, anImageUrl, fImageMessage.getMimeType());

                            if (null != fEncryptionResult) {
                                fImageMessage.file = fEncryptionResult.mEncryptedFileInfo;
                                fImageMessage.file.url = contentUri;
                                fImageMessage.url = null;
                            } else {
                                fImageMessage.url = contentUri;
                            }

                            // update the event content with the new message info
                            imageRow.getEvent().updateContent(JsonUtils.toJson(fImageMessage));

                            Log.d(LOG_TAG, "Uploaded to " + contentUri);

                            send(imageRow);
                        }
                    }
                });
            }
        });
    }

    /**
     * upload an image content.
     * It might be triggered from a media selection : imageUri is used to compute thumbnails.
     * Or, it could have been called to resend an image.
     *
     * @param thumbnailUrl      the thumbnail Url
     * @param thumbnailMimeType the thumbnail mimetype
     * @param geo_uri           the geo_uri
     * @param body              the message body
     */
    public void uploadLocationContent(final String thumbnailUrl, final String thumbnailMimeType, final String geo_uri, final String body) {
        // create a tmp row
        final LocationMessage tmpLocationMessage = new LocationMessage();

        tmpLocationMessage.thumbnail_url = thumbnailUrl;
        tmpLocationMessage.body = body;
        tmpLocationMessage.geo_uri = geo_uri;

        FileInputStream imageStream = null;

        try {
            Uri uri = Uri.parse(thumbnailUrl);
            Room.fillLocationInfo(getActivity(), tmpLocationMessage, uri, thumbnailMimeType);

            String filename = uri.getPath();
            imageStream = new FileInputStream(new File(filename));

            if (TextUtils.isEmpty(tmpLocationMessage.body)) {
                tmpLocationMessage.body = "Location";
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "uploadLocationContent failed with " + e.getLocalizedMessage());
        }

        // remove any displayed MessageRow with this URL
        // to avoid duplicate
        final MessageRow locationRow = addMessageRow(tmpLocationMessage);
        locationRow.getEvent().mSentState = Event.SentState.SENDING;

        getSession().getMediasCache().uploadContent(imageStream, tmpLocationMessage.body, thumbnailMimeType, thumbnailUrl, new MXMediaUploadListener() {
            @Override
            public void onUploadStart(String uploadId) {
                getUiHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        onMessageSendingSucceeded(locationRow.getEvent());
                        mAdapter.notifyDataSetChanged();
                    }
                });
            }


            @Override
            public void onUploadCancel(String uploadId) {
                getUiHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        onMessageSendingFailed(locationRow.getEvent());
                    }
                });
            }

            @Override
            public void onUploadError(String uploadId, final int serverResponseCode, final String serverErrorMessage) {
                getUiHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        commonMediaUploadError(serverResponseCode, serverErrorMessage, locationRow);
                    }
                });
            }

            @Override
            public void onUploadComplete(final String uploadId, final String contentUri) {
                getUiHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        // Build the location message
                        LocationMessage message = tmpLocationMessage.deepCopy();

                        // replace the thumbnail and the media contents by the computed ones
                        getMXMediasCache().saveFileMediaForUrl(contentUri, thumbnailUrl, mAdapter.getMaxThumbnailWith(), mAdapter.getMaxThumbnailHeight(), "image/jpeg");

                        message.thumbnail_url = contentUri;

                        // update the event content with the new message info
                        locationRow.getEvent().updateContent(JsonUtils.toJson(message));

                        Log.d(LOG_TAG, "Uploaded to " + contentUri);

                        send(locationRow);
                    }
                });
            }
        });
    }

    //==============================================================================================================
    // Unsent messages management
    //==============================================================================================================

    /**
     * Provides the unsent messages list.
     *
     * @return the unsent messages list
     */
    private List<Event> getUnsentMessages() {
        List<Event> unsent = new ArrayList<>();

        List<Event> undeliverableEvents = mSession.getDataHandler().getStore().getUndeliverableEvents(mRoom.getRoomId());
        List<Event> unknownDeviceEvents = mSession.getDataHandler().getStore().getUnknownDeviceEvents(mRoom.getRoomId());

        if (null != undeliverableEvents) {
            unsent.addAll(undeliverableEvents);
        }

        if (null != unknownDeviceEvents) {
            unsent.addAll(unknownDeviceEvents);
        }

        return unsent;
    }

    /**
     * Delete the unsent (undeliverable messages).
     */
    public void deleteUnsentMessages() {
        List<Event> unsent = getUnsentMessages();

        if (unsent.size() > 0) {
            IMXStore store = mSession.getDataHandler().getStore();

            // reset the timestamp
            for (Event event : unsent) {
                mAdapter.removeEventById(event.eventId);
                store.deleteEvent(event);
            }

            // update the summary
            Event latestEvent = store.getLatestEvent(mRoom.getRoomId());

            // if there is an oldest event, use it to set a summary
            if (latestEvent != null) {
                if (RoomSummary.isSupportedEvent(latestEvent)) {
                    store.storeSummary(latestEvent.roomId, latestEvent, mRoom.getState(), mSession.getMyUserId());
                }
            }

            store.commit();
            mAdapter.notifyDataSetChanged();
        }
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

        List<Event> unsent = getUnsentMessages();

        if (unsent.size() > 0) {
            mResendingEventsList = new ArrayList<>(unsent);

            // reset the timestamp
            for (Event event : mResendingEventsList) {
                event.mSentState = Event.SentState.UNSENT;
            }

            resend(mResendingEventsList.get(0));
            mResendingEventsList.remove(0);
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

        // resend an image ?
        if (message instanceof ImageMessage) {
            ImageMessage imageMessage = (ImageMessage) message;

            // media has not been uploaded
            if (imageMessage.isLocalContent() || imageMessage.isThumbnailLocalContent()) {
                uploadImageContent(imageMessage, null, imageMessage.thumbnailUrl, imageMessage.url, imageMessage.body, imageMessage.getMimeType());
                return;
            }
        } else if (message instanceof FileMessage) {
            FileMessage fileMessage = (FileMessage) message;

            // media has not been uploaded
            if (fileMessage.isLocalContent()) {
                uploadFileContent(fileMessage.url, fileMessage.getMimeType(), fileMessage.body);
                return;
            }
        } else if (message instanceof VideoMessage) {
            VideoMessage videoMessage = (VideoMessage) message;

            // media has not been uploaded
            if (videoMessage.isLocalContent() || videoMessage.isThumbnailLocalContent()) {
                String thumbnailUrl = null;
                String thumbnailMimeType = null;

                if (null != videoMessage.info) {
                    thumbnailUrl = videoMessage.info.thumbnail_url;

                    if (null != videoMessage.info.thumbnail_info) {
                        thumbnailMimeType = videoMessage.info.thumbnail_info.mimetype;
                    }
                }

                uploadVideoContent(videoMessage, null, thumbnailUrl, thumbnailMimeType, videoMessage.url, videoMessage.body, videoMessage.getVideoMimeType());
                return;
            } else if (message instanceof LocationMessage) {
                LocationMessage locationMessage = (LocationMessage) message;

                // media has not been uploaded
                if (locationMessage.isLocalThumbnailContent()) {
                    String thumbMimeType = null;

                    if (null != locationMessage.thumbnail_info) {
                        thumbMimeType = locationMessage.thumbnail_info.mimetype;
                    }

                    uploadLocationContent(locationMessage.thumbnail_url, thumbMimeType, locationMessage.geo_uri, locationMessage.body);
                    return;
                }
            }
        }

        send(message);
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
        if (null != MatrixMessageListFragment.this.getActivity()) {
            if (error instanceof Exception) {
                Log.e(LOG_TAG, "Network error: " + ((Exception) error).getMessage());
                Toast.makeText(MatrixMessageListFragment.this.getActivity(), getActivity().getString(R.string.network_error), Toast.LENGTH_SHORT).show();

            } else if (error instanceof MatrixError) {
                final MatrixError matrixError = (MatrixError) error;
                Log.e(LOG_TAG, "Matrix error" + " : " + matrixError.errcode + " - " + matrixError.getLocalizedMessage());
                Toast.makeText(MatrixMessageListFragment.this.getActivity(), getActivity().getString(R.string.matrix_error) + " : " + matrixError.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
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

                    mAdapter.notifyDataSetChanged();
                    // trick to avoid that the list jump to the latest item.
                    mMessageListView.setAdapter(mMessageListView.getAdapter());

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

                        Log.d(LOG_TAG, "backPaginate : ends with " + countDiff + " new items (total : " + mAdapter.getCount() + ")");

                        // check if some messages have been added
                        if (0 != countDiff) {
                            mAdapter.notifyDataSetChanged();

                            // trick to avoid that the list jump to the latest item.
                            mMessageListView.setAdapter(mMessageListView.getAdapter());

                            final int expectedPos = fillHistory ? (mAdapter.getCount() - 1) : (mMessageListView.getFirstVisiblePosition() + countDiff);

                            Log.d(LOG_TAG, "backPaginate : jump to " + expectedPos);

                            //private int mFirstVisibleRowY  = INVALID_VIEW_Y_POS;

                            if (fillHistory || (UNDEFINED_VIEW_Y_POS == mFirstVisibleRowY)) {
                                // do not use count because some messages are not displayed
                                // so we compute the new pos
                                mMessageListView.setSelection(expectedPos);
                            } else {
                                mMessageListView.setSelectionFromTop(expectedPos, -mFirstVisibleRowY);
                            }
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
            mEventTimeLine.cancelPaginationRequest();
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

    //==============================================================================================================
    // MatrixMessagesFragment methods
    //==============================================================================================================

    @Override
    public void onEvent(final Event event, final EventTimeline.Direction direction, final RoomState roomState) {
        if (direction == EventTimeline.Direction.FORWARDS) {
            getUiHandler().post(new Runnable() {
                @Override
                public void run() {
                    if (Event.EVENT_TYPE_REDACTION.equals(event.getType())) {
                        MessageRow messageRow = mAdapter.getMessageRow(event.getRedacts());

                        if (null != messageRow) {
                            Event prunedEvent = mSession.getDataHandler().getStore().getEvent(event.getRedacts(), event.roomId);

                            if (null == prunedEvent) {
                                mAdapter.removeEventById(event.getRedacts());
                            } else {
                                messageRow.updateEvent(prunedEvent);
                                JsonObject content = messageRow.getEvent().getContentAsJsonObject();

                                boolean hasToRemoved = (null == content) || (null == content.entrySet()) || (0 == content.entrySet().size());

                                // test if the event is displayable
                                // GA issue : the activity can be null
                                if (!hasToRemoved && (null != getActivity())) {
                                    EventDisplay eventDisplay = new EventDisplay(getActivity(), prunedEvent, roomState);
                                    hasToRemoved = TextUtils.isEmpty(eventDisplay.getTextualDisplay());
                                }

                                // event is removed if it has no more content.
                                if (hasToRemoved) {
                                    mAdapter.removeEventById(prunedEvent.eventId);
                                }
                            }

                            mAdapter.notifyDataSetChanged();
                        }
                    } else if (Event.EVENT_TYPE_TYPING.equals(event.getType())) {
                        if (null != mRoom) {
                            mAdapter.setTypingUsers(mRoom.getTypingUsers());
                        }
                    } else {
                        if (canAddEvent(event)) {
                            // refresh the listView only when it is a live timeline or a search
                            mAdapter.add(new MessageRow(event, roomState), (null == mEventTimeLine) || mEventTimeLine.isLiveTimeline());
                        }
                    }
                }
            });
        } else {
            if (canAddEvent(event)) {
                mAdapter.addToFront(event, roomState);
            }
        }
    }

    @Override
    public void onSentEvent(Event event) {
        // detect if a message was sent but not yet added to the adapter
        // For example, the quick reply does not use the fragement to send messages
        // Thus, the messages are not added to the adapater.
        // onEvent is not called because the server event echo manages an event sent by itself
        if ((null == mAdapter.getMessageRow(event.eventId)) && canAddEvent(event)) {
            // refresh the listView only when it is a live timeline or a search
            mAdapter.add(new MessageRow(event, mRoom.getLiveState()), true);
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

            ArrayList<String> senders = new ArrayList<>();
            ArrayList<String> eventIds = new ArrayList<>();

            for (int index = firstPos; index <= lastPos; index++) {
                MessageRow row = mAdapter.getItem(index);

                senders.add(row.getEvent().getSender());
                eventIds.add(row.getEvent().eventId);
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
            Log.e(LOG_TAG, "onReceiptEvent failed with " + e.getLocalizedMessage());
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
        mMessageListView.post(new Runnable() {
            @Override
            public void run() {
                mLockFwdPagination = false;
                mIsInitialSyncing = false;
                // search the event pos in the adapter
                // some events are not displayed so the added events count cannot be used.
                int eventPos = 0;
                for (; eventPos < mAdapter.getCount(); eventPos++) {
                    if (TextUtils.equals(mAdapter.getItem(eventPos).getEvent().eventId, mEventId)) {
                        break;
                    }
                }

                View parentView = (View) mMessageListView.getParent();

                mAdapter.notifyDataSetChanged();

                mMessageListView.setAdapter(mAdapter);

                // center the message in the
                mMessageListView.setSelectionFromTop(eventPos, parentView.getHeight() / 2);
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
                    Log.e(LOG_TAG, "getRoomPreviewData failed with " + e.getLocalizedMessage());
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

    /***
     * MessageAdapter listener
     ***/
    @Override
    public void onRowClick(int position) {
    }

    @Override
    public boolean onRowLongClick(int position) {
        return false;
    }

    @Override
    public void onContentClick(int position) {
    }

    @Override
    public boolean onContentLongClick(int position) {
        return false;
    }

    @Override
    public void onAvatarClick(String userId) {
    }

    @Override
    public boolean onAvatarLongClick(String userId) {
        return false;
    }

    @Override
    public void onSenderNameClick(String userId, String displayName) {
    }

    @Override
    public void onMediaDownloaded(int position) {
    }

    @Override
    public void onReadReceiptClick(String eventId, String userId, ReceiptData receipt) {
    }

    @Override
    public boolean onReadReceiptLongClick(String eventId, String userId, ReceiptData receipt) {
        return false;
    }

    @Override
    public void onMoreReadReceiptClick(String eventId) {
    }

    @Override
    public boolean onMoreReadReceiptLongClick(String eventId) {
        return false;
    }

    @Override
    public void onURLClick(Uri uri) {
        if (null != uri) {
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.putExtra(Browser.EXTRA_APPLICATION_ID, getActivity().getPackageName());
            getActivity().startActivity(intent);
        }
    }

    @Override
    public boolean shouldHighlightEvent(Event event) {
        String eventId = event.eventId;

        // cache the dedicated rule because it is slow to find them out
        Object ruleAsVoid = mBingRulesByEventId.get(eventId);

        if (null != ruleAsVoid) {
            if (ruleAsVoid instanceof BingRule) {
                return ((BingRule) ruleAsVoid).shouldHighlight();
            }
            return false;
        }

        boolean res = false;

        BingRule rule = mSession.getDataHandler().getBingRulesManager().fulfilledBingRule(event);

        if (null != rule) {
            res = rule.shouldHighlight();
            mBingRulesByEventId.put(eventId, rule);
        } else {
            mBingRulesByEventId.put(eventId, eventId);
        }

        return res;
    }

    @Override
    public void onMatrixUserIdClick(String userId) {
    }

    @Override
    public void onRoomAliasClick(String roomAlias) {
    }

    @Override
    public void onRoomIdClick(String roomId) {
    }

    @Override
    public void onMessageIdClick(String messageId) {
    }

    private int mInvalidIndexesCount = 0;

    @Override
    public void onInvalidIndexes() {
        mInvalidIndexesCount++;

        // it should happen once
        // else we assume that the adapter is really corrupted
        // It seems better to close the linked activity to avoid infinite refresh.
        if (1 == mInvalidIndexesCount) {
            mMessageListView.post(new Runnable() {
                @Override
                public void run() {
                    mAdapter.notifyDataSetChanged();
                }
            });
        } else {
            mMessageListView.post(new Runnable() {
                @Override
                public void run() {
                    if (null != getActivity()) {
                        getActivity().finish();
                    }
                }
            });
        }
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

                                mAdapter.notifyDataSetChanged();
                                // trick to avoid that the list jump to the latest item.
                                mMessageListView.setAdapter(mMessageListView.getAdapter());

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
                Log.e(LOG_TAG, "Network error: " + e.getMessage());
                onError();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                Log.e(LOG_TAG, "Matrix error" + " : " + e.errcode + " - " + e.getLocalizedMessage());
                onError();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                Log.e(LOG_TAG, "onUnexpectedError error" + e.getMessage());
                onError();
            }
        };


        if (mIsMediaSearch) {
            mSession.searchMediasByName(mPattern, roomIds, mNextBatch, callback);

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
        ArrayList<MessageRow> messageRows = new ArrayList<>(searchResults.size());

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
                Log.e(LOG_TAG, "onSearchResponse failed with " + e.getLocalizedMessage());
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
                                        Log.e(LOG_TAG, "onSearchResultListener failed with " + e.getLocalizedMessage());
                                    }
                                }
                            }
                        });
                    }

                    // the request will be auto restarted when a valid network will be found
                    @Override
                    public void onNetworkError(Exception e) {
                        Log.e(LOG_TAG, "Network error: " + e.getMessage());
                        onError();
                    }

                    @Override
                    public void onMatrixError(MatrixError e) {
                        Log.e(LOG_TAG, "Matrix error" + " : " + e.errcode + " - " + e.getLocalizedMessage());
                        onError();
                    }

                    @Override
                    public void onUnexpectedError(Exception e) {
                        Log.e(LOG_TAG, "onUnexpectedError error" + e.getMessage());
                        onError();
                    }
                };

                if (isMediaSearch) {
                    mSession.searchMediasByName(mPattern, roomIds, null, searchCallback);

                } else {
                    mSession.searchMessagesByText(mPattern, roomIds, null, searchCallback);
                }
            }
        }
    }
}
