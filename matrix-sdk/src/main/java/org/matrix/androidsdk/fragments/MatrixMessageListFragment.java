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
import android.util.Log;
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
import org.matrix.androidsdk.data.EventTimeline;
import org.matrix.androidsdk.data.IMXStore;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomPreviewData;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.data.RoomSummary;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.listeners.IMXEventListener;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.callback.ToastErrorHandler;
import org.matrix.androidsdk.rest.model.ContentResponse;
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
import org.matrix.androidsdk.util.ContentManager;
import org.matrix.androidsdk.util.EventDisplay;
import org.matrix.androidsdk.util.JsonUtils;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import retrofit.RetrofitError;

/**
 * UI Fragment containing matrix messages for a given room.
 * Contains {@link MatrixMessagesFragment} as a nested fragment to do the work.
 */
public class MatrixMessageListFragment extends Fragment implements MatrixMessagesFragment.MatrixMessagesListener, MessagesAdapter.MessagesAdapterEventsListener {

    public interface OnSearchResultListener {
        void onSearchSucceed(int nbrMessages);
        void onSearchFailed();
    }

    public interface RoomPreviewDataListener {
        RoomPreviewData getRoomPreviewData();
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

    // timeline management
    protected boolean mIsLive = true;

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
    private HashMap<String, Timer> mPendingRelaunchTimersByEventId = new HashMap<>();

    private HashMap<String, Object> mBingRulesByEventId = new HashMap<>();

    // scroll to to the dedicated index when the device has been rotated
    private int mFirstVisibleRow = -1;

    // scroll to the index when loaded
    private int mScrollToIndex = -1;

    // y pos of the first visible row
    private int mFirstVisibleRowY  = UNDEFINED_VIEW_Y_POS;

    // used to retrieve the preview data
    protected RoomPreviewDataListener mRoomPreviewDataListener;

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

    private IMXEventListener mEventsListenener = new MXEventListener() {
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
    };

   protected AbsListView.OnScrollListener mScrollListener = new AbsListView.OnScrollListener() {
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

        @Override
        public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {

            // store the current Y pos to jump to the right pos when backpaginating
            mFirstVisibleRowY = UNDEFINED_VIEW_Y_POS;
            View v = mMessageListView.getChildAt(firstVisibleItem);
            if (null != v) {
                mFirstVisibleRowY = v.getTop();
            }

            if ((firstVisibleItem < 2) && (visibleItemCount != totalItemCount) && (0 != visibleItemCount)) {
                Log.d(LOG_TAG, "onScroll - backPaginate");
                backPaginate(false);
            } else if ((firstVisibleItem + visibleItemCount + 10) >= totalItemCount) {
                Log.d(LOG_TAG, "onScroll - forwardPaginate");
                forwardPaginate();
            }
        }
    };

    public MessagesAdapter createMessagesAdapter() {
        return null;
    }

    /**
     * The user scrolls the list.
     * Apply an expected behaviour
     * @param event the scroll event
     */
    public void onListTouch(MotionEvent event) {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(LOG_TAG, "onCreate");

        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    /**
     * Scroll the listview to a dedicated index when the list is loaded.
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

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.d(LOG_TAG, "onCreateView");

        super.onCreateView(inflater, container, savedInstanceState);
        Bundle args = getArguments();

        // for dispatching data to add to the adapter we need to be on the main thread
        mUiHandler = new Handler(Looper.getMainLooper());

        mMatrixId = args.getString(ARG_MATRIX_ID);
        mSession = getSession(mMatrixId);

        if (null == mSession) {
            throw new RuntimeException("Must have valid default MXSession.");
        }

        if (null == getMXMediasCache()) {
            throw new RuntimeException("Must have valid default MediasCache.");
        }

        String roomId = args.getString(ARG_ROOM_ID);

        View v = inflater.inflate(args.getInt(ARG_LAYOUT_ID), container, false);
        mMessageListView = ((ListView)v.findViewById(R.id.listView_messages));

        if (mAdapter == null) {
            // only init the adapter if it wasn't before, so we can preserve messages/position.
            mAdapter = createMessagesAdapter();

            if (null == getMXMediasCache()) {
                throw new RuntimeException("Must have valid default MessagesAdapter.");
            }
        } else if(null != savedInstanceState) {
            mFirstVisibleRow = savedInstanceState.getInt("FIRST_VISIBLE_ROW", -1);
        }

        mAdapter.setIsPreviewMode(false);

        if (null == mEventTimeLine) {
            mEventId =  args.getString(ARG_EVENT_ID);

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
     * Manage the search response.
     * @param searchResponse the search response
     * @param onSearchResultListener the search result listener
     */
    protected void onSearchResponse(final SearchResponse searchResponse, final OnSearchResultListener onSearchResultListener) {
        List<SearchResult> searchResults =  searchResponse.searchCategories.roomEvents.results;
        ArrayList<MessageRow> messageRows = new ArrayList<>(searchResults.size());

        for(SearchResult searchResult : searchResults) {
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

            if ((null != searchResult.result) && (null != searchResult.result.content)) {
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
     * @param pattern the pattern to search
     * @param onSearchResultListener the search callback
     */
    public void searchPattern(final String pattern, final OnSearchResultListener onSearchResultListener) {
        searchPattern(pattern, false, onSearchResultListener);
    }

    /**
     * Search a pattern in the messages.
     * @param pattern the pattern to search (filename for a media message)
     * @param isMediaSearch true if is it is a media search.
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
                        // check that the pattern was not modified before the end of the search
                        if (TextUtils.equals(mPattern, pattern)) {
                            onSearchResponse(searchResponse, onSearchResultListener);
                        }
                    }

                    private void onError() {
                        if (null != onSearchResultListener) {
                            try {
                                onSearchResultListener.onSearchFailed();
                            } catch (Exception e) {
                                Log.e(LOG_TAG, "onSearchResultListener failed with " + e.getLocalizedMessage());
                            }
                        }
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
                    String[] mediaTypes = {"m.image", "m.video", "m.file"};
                    mSession.searchMediaName(mPattern, roomIds, Arrays.asList(mediaTypes), null, searchCallback);

                } else {
                    mSession.searchMessageText(mPattern, roomIds, null, searchCallback);
                }
            }
        }
    }

    /**
     * Create the messageFragment.
     * Should be inherited.
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
     * Called when the fragment is no longer in use.  This is called
     * after {@link #onStop()} and before {@link #onDetach()}.
     */
    @Override
    public void onDestroy() {
        super.onDestroy();

        // remove listeners to prevent memory leak
        if(null != mMatrixMessagesFragment) {
            mMatrixMessagesFragment.setMatrixMessagesListener(null);
        }
        if(null != mAdapter) {
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
        }
        else {
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
            mRoom.removeEventListener(mEventsListenener);
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
                room.addEventListener(mEventsListenener);
            } else {
                Log.e(LOG_TAG, "the room " + mRoom.getRoomId() + " does not exist anymore");
            }
        }
    }

    public void sendTextMessage(String body) {
        sendMessage(Message.MSGTYPE_TEXT, body, null, null);
    }

    public void sendTextMessage(String body, String formattedBody, String format) {
        sendMessage(Message.MSGTYPE_TEXT, body, formattedBody, format);
    }

    public void scrollToBottom() {
        mMessageListView.postDelayed(new Runnable() {
            @Override
            public void run() {
                mMessageListView.setSelection(mAdapter.getCount() - 1);
            }
        }, 300);
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

    private void sendMessage(String msgType, String body) {
        sendMessage(msgType, body, null, null);
    }

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

    public void sendEmote(String emote) {
        sendMessage(Message.MSGTYPE_EMOTE, emote);
    }

    private void commonMediaUpload(ContentResponse uploadResponse, int serverReponseCode, final String serverErrorMessage, final MessageRow messageRow) {
        // warn the user that the media upload fails
        if ((null == uploadResponse) || (null == uploadResponse.contentUri)) {
            if (serverReponseCode == 500) {
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

                if (null != getActivity()) {
                    Toast.makeText(getActivity(),
                            (null != serverErrorMessage) ? serverErrorMessage : getString(R.string.message_failed_to_upload),
                            Toast.LENGTH_LONG).show();
                }
            }
        } else {
            send(messageRow);
        }
    }

    /**
     * Upload a file content
     * @param mediaUrl the media Uurl
     * @param mimeType the media mime type
     * @param mediaFilename the mediafilename
     */
    public void uploadFileContent(final String mediaUrl, final String mimeType, final String mediaFilename) {
        // create a tmp row
        final FileMessage tmpFileMessage = new FileMessage();

        tmpFileMessage.url = mediaUrl;
        tmpFileMessage.body = mediaFilename;

        FileInputStream fileStream = null;

        try {
            Uri uri = Uri.parse(mediaUrl);
            Room.fillFileInfo(getActivity(), tmpFileMessage, uri, mimeType);

            String filename = uri.getPath();
            fileStream = new FileInputStream (new File(filename));

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

        getSession().getContentManager().uploadContent(fileStream, tmpFileMessage.body, mimeType, mediaUrl, new ContentManager.UploadCallback() {

            @Override
            public void onUploadStart(String uploadId) {
            }

            @Override
            public void onUploadProgress(String anUploadId, int percentageProgress) {
            }

            @Override
            public void onUploadComplete(final String anUploadId, final ContentResponse uploadResponse, final int serverReponseCode, final String serverErrorMessage) {
                getUiHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        if ((null != uploadResponse) && (null != uploadResponse.contentUri)) {
                            // Build the image message
                            FileMessage message = tmpFileMessage.deepCopy();

                            // replace the thumbnail and the media contents by the computed ones
                            getMXMediasCache().saveFileMediaForUrl(uploadResponse.contentUri, mediaUrl, tmpFileMessage.getMimeType());
                            message.url = uploadResponse.contentUri;

                            // update the event content with the new message info
                            messageRow.getEvent().content = JsonUtils.toJson(message);

                            Log.d(LOG_TAG, "Uploaded to " + uploadResponse.contentUri);
                        }

                        commonMediaUpload(uploadResponse, serverReponseCode, serverErrorMessage, messageRow);
                    }
                });
            }
        });
    }

    /**
     * Compute the video thumbnail
     * @param videoUrl the video url
     * @return the video thumbnail
     */
    public String getVideoThumbailUrl(final String videoUrl) {
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
     * @param videoUrl the video url
     * @param body the message body
     * @param videoMimeType the video mime type
     */
    public void uploadVideoContent(final String videoUrl, final String body, final String videoMimeType) {
        uploadVideoContent(videoUrl, getVideoThumbailUrl(videoUrl), body, videoMimeType);
    }

    /**
     * Upload a video message
     * The video thumbnail will be computed
     * @param videoUrl the video url
     * @param thumbUrl the thumbnail Url
     * @param body the message body
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
     * @param thumbnailUrl the thumbnail Url
     * @param thumbnailMimeType the thumbnail mime type
     * @param videoUrl the video url
     * @param body the message body
     * @param videoMimeType the video mime type
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

        FileInputStream imageStream = null;
        String filename = "";
        String uploadId = "";
        String mimeType = "";

        try {
            // the thumbnail has been uploaded ?
            if (tmpVideoMessage.isThumbnailLocalContent()) {
                uploadId = thumbnailUrl;
                imageStream = new FileInputStream(new File(thumbUri.getPath()));
                mimeType = thumbnailMimeType;
            } else {
                uploadId = videoUrl;
                imageStream = new FileInputStream(new File(uri.getPath()));
                filename = tmpVideoMessage.body;
                mimeType = videoMimeType;
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "uploadVideoContent : media parsing failed " + e.getLocalizedMessage());
        }

        final boolean isContentUpload = TextUtils.equals(uploadId, videoUrl);
        final VideoMessage fVideoMessage = tmpVideoMessage;

        getSession().getContentManager().uploadContent(imageStream, filename, mimeType, uploadId, new ContentManager.UploadCallback() {
            @Override
            public void onUploadStart(String uploadId) {
                getUiHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        mAdapter.notifyDataSetChanged();
                    }
                });
            }

            @Override
            public void onUploadProgress(String anUploadId, int percentageProgress) {
            }

            @Override
            public void onUploadComplete(final String anUploadId, final ContentResponse uploadResponse, final int serverReponseCode, final String serverErrorMessage) {
                getUiHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        if ((null != uploadResponse) && (null != uploadResponse.contentUri)) {
                            // the video content has been uploaded
                            if (isContentUpload) {
                                // Build the image message
                                VideoMessage message = fVideoMessage.deepCopy();

                                // replace the thumbnail and the media contents by the computed ones
                                getMXMediasCache().saveFileMediaForUrl(uploadResponse.contentUri, videoUrl, videoMimeType);
                                message.url = uploadResponse.contentUri;

                                // update the event content with the new message info
                                videoRow.getEvent().content = JsonUtils.toJson(message);

                                Log.d(LOG_TAG, "Uploaded to " + uploadResponse.contentUri);
                            } else {
                                // ony upload the thumbnail
                                getMXMediasCache().saveFileMediaForUrl(uploadResponse.contentUri, thumbnailUrl, mAdapter.getMaxThumbnailWith(), mAdapter.getMaxThumbnailHeight(), thumbnailMimeType, true);
                                fVideoMessage.info.thumbnail_url = uploadResponse.contentUri;

                                // upload the video
                                uploadVideoContent(fVideoMessage, videoRow, thumbnailUrl, thumbnailMimeType, videoUrl, fVideoMessage.body, videoMimeType);
                                return;
                            }
                        }

                        commonMediaUpload(uploadResponse, serverReponseCode, serverErrorMessage, videoRow);
                    }
                });
            }
        });
    }

    /**
     * upload an image content.
     * It might be triggered from a media selection : imageUri is used to compute thumbnails.
     * Or, it could have been called to resend an image.
     * @param thumbnailUrl the thumbnail Url
     * @param imageUrl the image Uri
     * @param mediaFilename the mediaFilename
     * @param mimeType the image mine type
     */
    public void uploadImageContent(final String thumbnailUrl, final String imageUrl, final String mediaFilename, final String mimeType) {
        // create a tmp row
        final ImageMessage tmpImageMessage = new ImageMessage();

        tmpImageMessage.url = imageUrl;
        tmpImageMessage.thumbnailUrl = thumbnailUrl;
        tmpImageMessage.body = mediaFilename;

        FileInputStream imageStream = null;

        try {
            Uri uri = Uri.parse(imageUrl);
            Room.fillImageInfo(getActivity(), tmpImageMessage, uri, mimeType);

            String filename = uri.getPath();
            imageStream = new FileInputStream (new File(filename));

            if (null == tmpImageMessage.body) {
                tmpImageMessage.body = uri.getLastPathSegment();
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "uploadImageContent failed with " + e.getLocalizedMessage());
        }

        // remove any displayed MessageRow with this URL
        // to avoid duplicate
        final MessageRow imageRow = addMessageRow(tmpImageMessage);
        imageRow.getEvent().mSentState = Event.SentState.SENDING;

        getSession().getContentManager().uploadContent(imageStream, tmpImageMessage.body, mimeType, imageUrl, new ContentManager.UploadCallback() {
            @Override
            public void onUploadStart(String uploadId) {
            }

            @Override
            public void onUploadProgress(String anUploadId, int percentageProgress) {
            }

            @Override
            public void onUploadComplete(final String anUploadId, final ContentResponse uploadResponse, final int serverReponseCode, final String serverErrorMessage) {
                getUiHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        if ((null != uploadResponse) && (null != uploadResponse.contentUri)) {
                            // Build the image message
                            ImageMessage message = tmpImageMessage.deepCopy();

                            // replace the thumbnail and the media contents by the computed ones
                            getMXMediasCache().saveFileMediaForUrl(uploadResponse.contentUri, thumbnailUrl, mAdapter.getMaxThumbnailWith(), mAdapter.getMaxThumbnailHeight(), "image/jpeg");
                            getMXMediasCache().saveFileMediaForUrl(uploadResponse.contentUri, imageUrl, tmpImageMessage.getMimeType());

                            message.thumbnailUrl = null;
                            message.url = uploadResponse.contentUri;
                            message.info = tmpImageMessage.info;

                            if (TextUtils.isEmpty(message.body)) {
                                message.body = "Image";
                            }

                            // update the event content with the new message info
                            imageRow.getEvent().content = JsonUtils.toJson(message);

                            Log.d(LOG_TAG, "Uploaded to " + uploadResponse.contentUri);
                        }
                        commonMediaUpload(uploadResponse, serverReponseCode, serverErrorMessage, imageRow);
                    }
                });
            }
        });
    }

    /**
     * upload an image content.
     * It might be triggered from a media selection : imageUri is used to compute thumbnails.
     * Or, it could have been called to resend an image.
     * @param thumbnailUrl the thumbnail Url
     * @param thumbnailMimeType the thumbnail mimetype
     * @param geo_uri the geo_uri
     * @param body the message body
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
            imageStream = new FileInputStream (new File(filename));

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

        getSession().getContentManager().uploadContent(imageStream, tmpLocationMessage.body, thumbnailMimeType, thumbnailUrl, new ContentManager.UploadCallback() {
            @Override
            public void onUploadStart(String uploadId) {
            }

            @Override
            public void onUploadProgress(String anUploadId, int percentageProgress) {
            }

            @Override
            public void onUploadComplete(final String anUploadId, final ContentResponse uploadResponse, final int serverReponseCode, final String serverErrorMessage) {
                getUiHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        if ((null != uploadResponse) && (null != uploadResponse.contentUri)) {
                            // Build the location message
                            LocationMessage message = tmpLocationMessage.deepCopy();

                            // replace the thumbnail and the media contents by the computed ones
                            getMXMediasCache().saveFileMediaForUrl(uploadResponse.contentUri, thumbnailUrl, mAdapter.getMaxThumbnailWith(), mAdapter.getMaxThumbnailHeight(), "image/jpeg");

                            message.thumbnail_url = uploadResponse.contentUri;

                            // update the event content with the new message info
                            locationRow.getEvent().content = JsonUtils.toJson(message);

                            Log.d(LOG_TAG, "Uploaded to " + uploadResponse.contentUri);
                        }

                        commonMediaUpload(uploadResponse, serverReponseCode, serverErrorMessage, locationRow);
                    }
                });
            }
        });
    }

    public void deleteUnsentMessages() {
        Collection<Event> unsent = mSession.getDataHandler().getStore().getUndeliverableEvents(mRoom.getRoomId());

        if ((null != unsent) && (unsent.size() > 0)) {
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

        Collection<Event> unsent = mSession.getDataHandler().getStore().getUndeliverableEvents(mRoom.getRoomId());

        if ((null != unsent) && (unsent.size() > 0)) {
            mResendingEventsList =  new ArrayList<>(unsent);

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
        final Message message = JsonUtils.toMessage(event.content);

        // resend an image ?
        if (message instanceof ImageMessage) {
            ImageMessage imageMessage = (ImageMessage)message;

            // media has not been uploaded
            if (imageMessage.isLocalContent()) {
                uploadImageContent(imageMessage.thumbnailUrl, imageMessage.url, imageMessage.body, imageMessage.getMimeType());
                return;
            }
        } else if (message instanceof FileMessage) {
            FileMessage fileMessage = (FileMessage)message;

            // media has not been uploaded
            if (fileMessage.isLocalContent()) {
                uploadFileContent(fileMessage.url, fileMessage.getMimeType(), fileMessage.body);
                return;
            }
        } else if (message instanceof VideoMessage) {
            VideoMessage videoMessage = (VideoMessage)message;

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
                LocationMessage locationMessage = (LocationMessage)message;

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

    private void send(final Message message) {
        send(addMessageRow(message));
    }

    private void send(final MessageRow messageRow)  {
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
                    mAdapter.updateEventById(event, prevEventId);

                    // pending resending ?
                    if ((null != mResendingEventsList) && (mResendingEventsList.size() > 0)) {
                        resend(mResendingEventsList.get(0));
                        mResendingEventsList.remove(0);
                    }
                }

                private void commonFailure(final Event event) {
                    if (null != MatrixMessageListFragment.this.getActivity()) {
                        // display the error message only if the message cannot be resent
                        if ((null != event.unsentException) && (event.isUndeliverable())) {
                            if ((event.unsentException instanceof RetrofitError) && ((RetrofitError) event.unsentException).isNetworkError()) {
                                Toast.makeText(getActivity(), getActivity().getString(R.string.unable_to_send_message) + " : " + getActivity().getString(R.string.network_error), Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(getActivity(), getActivity().getString(R.string.unable_to_send_message) + " : " + event.unsentException.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                            }
                        } else if (null != event.unsentMatrixError) {
                            Toast.makeText(getActivity(), getActivity().getString(R.string.unable_to_send_message) + " : " + event.unsentMatrixError.getLocalizedMessage() + ".", Toast.LENGTH_LONG).show();
                        }

                        mAdapter.notifyDataSetChanged();
                    }
                }

                @Override
                public void onNetworkError(Exception e) {
                    commonFailure(event);
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    commonFailure(event);
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    commonFailure(event);
                }
            });
        }
    }

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

    public void refresh() {
        mAdapter.notifyDataSetChanged();
    }


    /**
     * Manage the request history error cases.
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
                if (TextUtils.equals(mPattern, fPattern)) {
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
            String[] mediaTypes = {"m.image", "m.video", "m.file"};
            mSession.searchMediaName(mPattern, roomIds, Arrays.asList(mediaTypes), mNextBatch, callback);

        } else {
            mSession.searchMessageText(mPattern, roomIds, mNextBatch, callback);
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
            Log.d(LOG_TAG, "The forward pagination is not supported for the live timeline.");
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

        // in search mode,
        if (!TextUtils.isEmpty(mPattern)) {
            Log.d(LOG_TAG, "backPaginate with pattern " + mPattern);
            requestSearchHistory();
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

                        int countDiff = mAdapter.getCount() - countBeforeUpdate;

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
                                            if (fillHistory) {
                                                if ((mMessageListView.getVisibility() == View.VISIBLE) && mMessageListView.getFirstVisiblePosition() < 10) {
                                                    Log.d(LOG_TAG, "backPaginate : fill history");
                                                    backPaginate(fillHistory);
                                                } else {
                                                    Log.d(LOG_TAG, "backPaginate : history should be filled");
                                                    hideLoadingBackProgress();
                                                    mIsInitialSyncing = false;
                                                    mMessageListView.setOnScrollListener(mScrollListener);
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

    protected void redactEvent(String eventId) {
        // Do nothing on success, the event will be hidden when the redaction event comes down the event stream
        mMatrixMessagesFragment.redact(eventId,
                new SimpleApiCallback<Event>(new ToastErrorHandler(getActivity(), getActivity().getString(R.string.could_not_redact))));
    }

    private boolean canAddEvent(Event event) {
        String type = event.type;

        return mDisplayAllEvents ||
                 Event.EVENT_TYPE_MESSAGE.equals(type)          ||
                 Event.EVENT_TYPE_STATE_ROOM_NAME.equals(type)  ||
                 Event.EVENT_TYPE_STATE_ROOM_TOPIC.equals(type) ||
                 Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(type) ||
                 Event.EVENT_TYPE_STATE_ROOM_THIRD_PARTY_INVITE.equals(type) ||
                 Event.EVENT_TYPE_STATE_HISTORY_VISIBILITY.equals(type) ||
                 (event.isCallEvent() &&  (!Event.EVENT_TYPE_CALL_CANDIDATES.equals(type)))
                ;
    }

    @Override
    public void onEvent(final Event event, final EventTimeline.Direction direction, final RoomState roomState) {
        if (direction == EventTimeline.Direction.FORWARDS) {
            getUiHandler().post(new Runnable() {
                @Override
                public void run() {
                    if (Event.EVENT_TYPE_REDACTION.equals(event.type)) {
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
                                if (!hasToRemoved) {
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
                    } else if (Event.EVENT_TYPE_TYPING.equals(event.type)) {
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

    public void onInitialMessagesLoaded() {
        Log.d(LOG_TAG, "onInitialMessagesLoaded");

        // Jump to the bottom of the list
        getUiHandler().post(new Runnable() {
            @Override
            public void run() {
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
                                mMessageListView.setOnScrollListener(mScrollListener);
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
                                mMessageListView.setOnScrollListener(mScrollListener);
                            }
                        });

                    } else {
                        mIsInitialSyncing = false;
                        mMessageListView.setOnScrollListener(mScrollListener);
                    }
                }
            }
        });
    }

    @Override public EventTimeline getEventTimeLine() {
        return mEventTimeLine;
    }

    @Override public void onTimelineInitialized() {
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
    public  RoomPreviewData getRoomPreviewData() {
        if (null != getActivity()) {
            // test if the listener has bee retrieved
            if (null == mRoomPreviewDataListener) {
                try {
                    mRoomPreviewDataListener = (RoomPreviewDataListener) getActivity();
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
    public void onRoomSyncWithLimitedTimeline() {
        mAdapter.clear();
    }

    /***  MessageAdapter listener  ***/
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
                return ((BingRule)ruleAsVoid).shouldHighlight();
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

    // thumbnails management
    public int getMaxThumbnailWith() {
        return mAdapter.getMaxThumbnailWith();
    }

    public int getMaxThumbnailHeight() {
        return mAdapter.getMaxThumbnailHeight();
    }

    /**
     * Notify the fragment that some bing rules could have been updated.
     */
    public void onBingRulesUpdate() {
        mAdapter.onBingRulesUpdate();
    }
}
