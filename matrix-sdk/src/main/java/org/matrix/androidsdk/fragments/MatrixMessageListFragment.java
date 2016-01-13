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

import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.R;
import org.matrix.androidsdk.adapters.MessageRow;
import org.matrix.androidsdk.adapters.MessagesAdapter;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
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
import org.matrix.androidsdk.rest.model.Search.SearchParams;
import org.matrix.androidsdk.rest.model.Search.SearchResponse;
import org.matrix.androidsdk.rest.model.Search.SearchResult;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.rest.model.VideoMessage;
import org.matrix.androidsdk.util.ContentManager;
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

    protected static final String TAG_FRAGMENT_MESSAGE_OPTIONS = "org.matrix.androidsdk.RoomActivity.TAG_FRAGMENT_MESSAGE_OPTIONS";
    protected static final String TAG_FRAGMENT_MESSAGE_DETAILS = "org.matrix.androidsdk.RoomActivity.TAG_FRAGMENT_MESSAGE_DETAILS";

    public static final String ARG_ROOM_ID = "org.matrix.androidsdk.fragments.MatrixMessageListFragment.ARG_ROOM_ID";
    public static final String ARG_MATRIX_ID = "org.matrix.androidsdk.fragments.MatrixMessageListFragment.ARG_MATRIX_ID";
    public static final String ARG_LAYOUT_ID = "org.matrix.androidsdk.fragments.MatrixMessageListFragment.ARG_LAYOUT_ID";

    private static final String TAG_FRAGMENT_MATRIX_MESSAGES = "org.matrix.androidsdk.RoomActivity.TAG_FRAGMENT_MATRIX_MESSAGES";
    private static final String LOG_TAG = "ErrorListener";

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
    private Handler mUiHandler;
    protected MXSession mSession;
    protected String mMatrixId;
    protected Room mRoom;
    protected String mPattern = null;
    protected String mNextBatch = null;
    private boolean mDisplayAllEvents = true;
    public boolean mCheckSlideToHide = false;

    // avoid to catch up old content if the initial sync is in progress
    private boolean mIsInitialSyncing = true;
    private boolean mIsCatchingUp = false;

    private Handler uiThreadHandler;

    private HashMap<String, Timer> mPendingRelaunchTimersByEventId = new HashMap<String, Timer>();

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

    private IMXEventListener mEventsListenener = new MXEventListener() {
        @Override
        public void onPresenceUpdate(Event event, final User user) {
            // Someone's presence has changed, reprocess the whole list
            uiThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    // check first if the userID has sent some messages in the room history
                    Boolean refresh = mAdapter.isDisplayedUser(user.userId);

                    if (refresh) {
                        // check, if the avatar is currently displayed

                        // The Math.min is required because the adapter and mMessageListView could be unsynchronized.
                        // ensure there is no IndexOfOutBound exception.
                        int firstVisibleRow = Math.min(mMessageListView.getFirstVisiblePosition(), mAdapter.getCount());
                        int lastVisibleRow = Math.min(mMessageListView.getLastVisiblePosition(), mAdapter.getCount());

                        refresh = false;

                        for (int i = firstVisibleRow; i <= lastVisibleRow; i++) {
                            MessageRow row = mAdapter.getItem(i);
                            refresh |= TextUtils.equals(user.userId, row.getEvent().userId);
                        }
                    }

                    if (refresh) {
                        mAdapter.notifyDataSetChanged();
                    }
                }
            });
        }

        /**
         * User presences was synchronized..
         */
        @Override
        public void onPresencesSyncComplete() {
            mAdapter.notifyDataSetChanged();
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
        super.onCreate(savedInstanceState);
        uiThreadHandler = new Handler();
        setRetainInstance(true);
    }

    /**
     * return true to display all the events.
     * else the unknown events will be hidden.
     */
    public boolean isDisplayAllEvents() {
        return true;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
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
        mRoom = mSession.getDataHandler().getRoom(roomId);

        View v = inflater.inflate(args.getInt(ARG_LAYOUT_ID), container, false);
        mMessageListView = ((ListView)v.findViewById(R.id.listView_messages));

        int selectionIndex = -1;

        if (mAdapter == null) {
            // only init the adapter if it wasn't before, so we can preserve messages/position.
            mAdapter = createMessagesAdapter();

            if (null == getMXMediasCache()) {
                throw new RuntimeException("Must have valid default MessagesAdapter.");
            }
        } else {
            if (savedInstanceState.containsKey("FIRST_VISIBLE_ROW")) {
                selectionIndex = savedInstanceState.getInt("FIRST_VISIBLE_ROW");
            }
        }

        mAdapter.setTypingUsers(mRoom.getTypingUsers());
        mMessageListView.setAdapter(mAdapter);

        if (-1 != selectionIndex) {
            final int fselectionIndex = selectionIndex;

            // fill the page
            mMessageListView.post(new Runnable() {
                @Override
                public void run() {
                    mMessageListView.setSelection(fselectionIndex);
                }
            });
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
     * Update the searched pattern.
     * @param pattern the pattern to find out. null to disable the search mode
     */
    public void searchPattern(final String pattern) {
        if (!TextUtils.equals(mPattern, pattern)) {
            mPattern = pattern;
            mAdapter.setSearchPattern(mPattern);

            // something to search
            if (!TextUtils.isEmpty(mPattern)) {
                mSession.searchMessageText(mPattern, Arrays.asList(mRoom.getRoomId()), null, new ApiCallback<SearchResponse>() {
                    @Override
                    public void onSuccess(final SearchResponse searchResponse) {
                        MatrixMessageListFragment.this.getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                // check that the pattern was not modified before the end of the search
                                if (TextUtils.equals(mPattern, pattern)) {
                                    List<SearchResult> searchResults =  searchResponse.searchCategories.roomEvents.results;
                                    ArrayList<MessageRow> messageRows = new ArrayList<MessageRow>(searchResults.size());

                                    for(SearchResult searchResult : searchResults) {
                                        messageRows.add(new MessageRow(searchResult.result, mRoom.getLiveState()));
                                    }

                                    Collections.reverse(messageRows);

                                    mAdapter.clear();
                                    mAdapter.addAll(messageRows);


                                    mNextBatch = searchResponse.searchCategories.roomEvents.nextBatch;
                                }
                            }
                        });
                    }

                    // the request will be auto restarted when a valid network will be found
                    @Override
                    public void onNetworkError(Exception e) {
                        Log.e(LOG_TAG, "Network error: " + e.getMessage());
                    }

                    @Override
                    public void onMatrixError(MatrixError e) {
                        Log.e(LOG_TAG, "Matrix error" + " : " + e.errcode + " - " + e.getLocalizedMessage());
                    }

                    @Override
                    public void onUnexpectedError(Exception e) {
                        Log.e(LOG_TAG, "onUnexpectedError error" + e.getMessage());
                    }
                });
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

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        Bundle args = getArguments();
        FragmentManager fm = getActivity().getSupportFragmentManager();
        mMatrixMessagesFragment = (MatrixMessagesFragment) fm.findFragmentByTag(TAG_FRAGMENT_MATRIX_MESSAGES);

        if (mMatrixMessagesFragment == null) {
            // this fragment controls all the logic for handling messages / API calls
            mMatrixMessagesFragment = createMessagesFragmentInstance(args.getString(ARG_ROOM_ID));
            fm.beginTransaction().add(mMatrixMessagesFragment, TAG_FRAGMENT_MATRIX_MESSAGES).commit();
        }
        else {
            // Reset the listener because this is not done when the system restores the fragment (newInstance is not called)
            mMatrixMessagesFragment.setMatrixMessagesListener(this);
        }
    }
    @Override
    public void onPause() {
        super.onPause();

        // check if the session has not been logged out
        if (mSession.isActive()) {
            mSession.getDataHandler().getRoom(mRoom.getRoomId()).removeEventListener(mEventsListenener);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mSession.getDataHandler().getRoom(mRoom.getRoomId()).addEventListener(mEventsListenener);

        mMessageListView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                mCheckSlideToHide = (scrollState == SCROLL_STATE_TOUCH_SCROLL);

                //check only when the user scrolls the content
                if (scrollState == SCROLL_STATE_TOUCH_SCROLL) {
                    int firstVisibleRow = mMessageListView.getFirstVisiblePosition();
                    int lastVisibleRow = mMessageListView.getLastVisiblePosition();
                    int count = mMessageListView.getCount();

                    // All the messages are displayed within the same page
                    if ((count > 0) && (firstVisibleRow == 0) && (lastVisibleRow == (count - 1)) && (!mIsInitialSyncing)) {
                        requestHistory();
                    }
                }
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                // If we scroll to the top, load more history
                // so not load history if there is an initial sync progress
                // or the whole room content fits in a single page
                if ((firstVisibleItem == 0) && (!mIsInitialSyncing) && (visibleItemCount != totalItemCount) && (0 != visibleItemCount)) {
                    requestHistory();
                }
            }
        });
    }

    @Override
    public void onDestroy() {
        if (null != mPendingRelaunchTimersByEventId) {
            for (Timer timer : mPendingRelaunchTimersByEventId.values()) {
                timer.cancel();
            }

            mPendingRelaunchTimersByEventId = null;
        }

        super.onDestroy();
    }

    public void sendTextMessage(String body) {
        sendMessage(Message.MSGTYPE_TEXT, body);
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
        Event event = new Event(message, mSession.getCredentials().userId, mRoom.getRoomId());
        getSession().getDataHandler().storeLiveRoomEvent(event);

        MessageRow messageRow = new MessageRow(event, mRoom.getLiveState());
        mAdapter.add(messageRow);

        scrollToBottom();

        Log.d(LOG_TAG, "AddMessage Row : commit");
        getSession().getDataHandler().getStore().commit();
        return messageRow;
    }

    private void sendMessage(String msgType, String body) {
        Message message = new Message();
        message.msgtype = msgType;
        message.body = body;
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

                            MatrixMessageListFragment.this.getActivity().runOnUiThread(
                                    new Runnable() {
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

                Toast.makeText(getActivity(),
                        (null != serverErrorMessage) ? serverErrorMessage : getString(R.string.message_failed_to_upload),
                        Toast.LENGTH_LONG).show();
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
                getActivity().runOnUiThread(new Runnable() {
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
     * Upload a video message
     * The video thumbnail will be computed
     * @param videoUrl the video url
     * @param body the message body
     * @param videoMimeType the video mime type
     */
    public void uploadVideoContent(final String videoUrl, final String body, final String videoMimeType) {
        String thumbUrl = null;
        try {
            Uri uri = Uri.parse(videoUrl);
            Bitmap thumb = ThumbnailUtils.createVideoThumbnail(uri.getPath(), MediaStore.Images.Thumbnails.MINI_KIND);
            thumbUrl = getMXMediasCache().saveBitmap(thumb, null);
        } catch (Exception e) {

        }
        this.uploadVideoContent(null, null, thumbUrl, "image/jpeg", videoUrl, body, videoMimeType);
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

        final Boolean isContentUpload = TextUtils.equals(uploadId, videoUrl);
        final VideoMessage fVideoMessage = tmpVideoMessage;

        getSession().getContentManager().uploadContent(imageStream, filename, mimeType, uploadId, new ContentManager.UploadCallback() {
            @Override
            public void onUploadStart(String uploadId) {
                getActivity().runOnUiThread(new Runnable() {
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
                getActivity().runOnUiThread(new Runnable() {
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
                                getMXMediasCache().saveFileMediaForUrl(uploadResponse.contentUri, thumbnailUrl, mAdapter.getMaxThumbnailWith(), mAdapter.getMaxThumbnailHeight(), thumbnailMimeType);
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
                getActivity().runOnUiThread(new Runnable() {
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
                getActivity().runOnUiThread(new Runnable() {
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

    protected void resend(Event event) {
        // sanity check
        // should never happen but got it in a GA issue
        if (null == event.eventId) {
            Log.e(LOG_TAG, "resend : got an event with a null eventId");
            return;
        }

        if (null == mPendingRelaunchTimersByEventId) {
            Log.e(LOG_TAG, "resend : with a destroyed list fragment");
            return;
        }

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
        final Event event = messageRow.getEvent();

        if (!event.isUndeliverable()) {

            mMatrixMessagesFragment.sendEvent(event, new ApiCallback<Void>() {
                @Override
                public void onSuccess(Void info) {
                    MatrixMessageListFragment.this.getActivity().runOnUiThread (
                            new Runnable() {
                                @Override
                                public void run() {
                                    mAdapter.waitForEcho(messageRow);
                                }
                            }
                    );
                }

                private void commonFailure(final Event event) {
                    MatrixMessageListFragment.this.getActivity().runOnUiThread(
                            new Runnable() {
                                @Override
                                public void run() {
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
                    );
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
     * Display a global spinner or any UI item to warn the user that there are some pending actions.
     */
    public void displayLoadingProgress() {
    }

    /**
     * Dismiss any global spinner.
     */
    public void dismissLoadingProgress() {
    }

    /**
     * logout from the application
     */
    public void logout() {
    }

    public void refresh() {
        mAdapter.notifyDataSetChanged();
    }

    /**
     * Manage the request history error cases.
     * @param error the error object.
     */
    private void onRequestError(Object error) {
        if (error instanceof Exception) {
            Log.e(LOG_TAG, "Network error: " + ((Exception) error).getMessage());
            MatrixMessageListFragment.this.getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MatrixMessageListFragment.this.getActivity(), getActivity().getString(R.string.network_error), Toast.LENGTH_SHORT).show();
                }
            });
        } else if (error instanceof MatrixError) {
            final MatrixError matrixError = (MatrixError)error;

            Log.e(LOG_TAG, "Matrix error" + " : " + matrixError.errcode + " - " + matrixError.getLocalizedMessage());
            // The access token was not recognized: log out
            if (MatrixError.UNKNOWN_TOKEN.equals(matrixError.errcode)) {
                logout();
            }

            MatrixMessageListFragment.this.getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MatrixMessageListFragment.this.getActivity(), getActivity().getString(R.string.matrix_error) + " : " + matrixError.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        }

        MatrixMessageListFragment.this.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                MatrixMessageListFragment.this.dismissLoadingProgress();
                mIsCatchingUp = false;
            }
        });
    }

    /**
     * Search the pattern on a pagination server side.
     */
    public void requestSearchHistory() {
        // there is no more server message
        if (TextUtils.isEmpty(mNextBatch)) {
            mIsCatchingUp = false;
            return;
        }

        mIsCatchingUp = true;

        final int firstPos = mMessageListView.getFirstVisiblePosition();
        final String fPattern = mPattern;
        final int countBeforeUpdate = mAdapter.getCount();

        MatrixMessageListFragment.this.displayLoadingProgress();

        mSession.searchMessageText(mPattern, Arrays.asList(mRoom.getRoomId()), mNextBatch, new ApiCallback<SearchResponse>() {
            @Override
            public void onSuccess(final SearchResponse searchResponse) {
                MatrixMessageListFragment.this.getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // check that the pattern was not modified before the end of the search
                        if (TextUtils.equals(mPattern, fPattern)) {
                            List<SearchResult> searchResults =  searchResponse.searchCategories.roomEvents.results;

                            // is there any result to display
                            if (0 != searchResults.size()) {
                                mAdapter.setNotifyOnChange(false);

                                for (SearchResult searchResult : searchResults) {
                                    MessageRow row = new MessageRow(searchResult.result, mRoom.getLiveState());
                                    mAdapter.insert(row, 0);
                                }

                                mNextBatch = searchResponse.searchCategories.roomEvents.nextBatch;

                                // Scroll the list down to where it was before adding rows to the top
                                mUiHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        // refresh the list only at the end of the sync
                                        // else the one by one message refresh gives a weird UX
                                        // The application is almost frozen during the
                                        mAdapter.notifyDataSetChanged();

                                        // do not use count because some messages are not displayed
                                        // so we compute the new pos
                                        mMessageListView.setSelection(firstPos + (mAdapter.getCount() - countBeforeUpdate));
                                        mIsCatchingUp = false;
                                    }
                                });
                            }

                            MatrixMessageListFragment.this.dismissLoadingProgress();
                        }
                    }
                });
            }

            // the request will be auto restarted when a valid network will be found
            @Override
            public void onNetworkError(Exception e) {
                Log.e(LOG_TAG, "Network error: " + e.getMessage());
                MatrixMessageListFragment.this.dismissLoadingProgress();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                Log.e(LOG_TAG, "Matrix error" + " : " + e.errcode + " - " + e.getLocalizedMessage());
                MatrixMessageListFragment.this.dismissLoadingProgress();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                Log.e(LOG_TAG, "onUnexpectedError error" + e.getMessage());
                MatrixMessageListFragment.this.dismissLoadingProgress();
            }
        });
    }


    public void requestHistory() {
        // avoid launching catchup if there is already one in progress
        // or during a search
        if (!mIsCatchingUp) {

            // in search mode,
            if (!TextUtils.isEmpty(mPattern)) {
                requestSearchHistory();
                return;
            }

            mIsCatchingUp = true;
            final int firstPos = mMessageListView.getFirstVisiblePosition();
            final int countBeforeUpdate = mAdapter.getCount();

            boolean isStarted = mMatrixMessagesFragment.requestHistory(new SimpleApiCallback<Integer>(getActivity()) {
                @Override
                public void onSuccess(final Integer count) {
                    dismissLoadingProgress();

                    // Scroll the list down to where it was before adding rows to the top
                    mUiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            // check if some messages have been added
                            // do not refresh the UI if no message have been added
                            if (0 != (mAdapter.getCount() - countBeforeUpdate)) {
                                // refresh the list only at the end of the sync
                                // else the one by one message refresh gives a weird UX
                                // The application is almost frozen during the
                                mAdapter.notifyDataSetChanged();


                                // do not use count because some messages are not displayed
                                // so we compute the new pos
                                mMessageListView.setSelection(firstPos + (mAdapter.getCount() - countBeforeUpdate));
                            }

                            mIsCatchingUp = false;
                        }
                    });
                }

                // the request will be auto restarted when a valid network will be found
                @Override
                public void onNetworkError(Exception e) {
                    onRequestError(e);
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    onRequestError(e);
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    onRequestError(e);
                }
            });

            if (isStarted && (null != getActivity())) {
                displayLoadingProgress();
            }
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
                 (event.isCallEvent() &&  (!Event.EVENT_TYPE_CALL_CANDIDATES.equals(type)))
                ;
    }

    @Override
    public void onLiveEvent(final Event event, final RoomState roomState) {
        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                if (Event.EVENT_TYPE_REDACTION.equals(event.type)) {
                    mAdapter.removeEventById(event.redacts);
                    mAdapter.notifyDataSetChanged();
                } else if (Event.EVENT_TYPE_TYPING.equals(event.type)) {
                    mAdapter.setTypingUsers(mRoom.getTypingUsers());
                } else {
                    if (canAddEvent(event)) {
                        mAdapter.add(event, roomState);
                    }
                }
            }
        });
    }

    @Override
    public void onLiveEventsChunkProcessed() {
       // NOP
    }

    @Override
    public void onBackEvent(final Event event, final RoomState roomState) {
        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                if (canAddEvent(event)) {
                    mAdapter.addToFront(event, roomState);
                }
            }
        });
    }

    @Override
    public void onDeleteEvent(final Event event) {
        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mPendingRelaunchTimersByEventId.containsKey(event.eventId)) {
                    Timer timer = mPendingRelaunchTimersByEventId.get(event.eventId);
                    timer.cancel();
                    mPendingRelaunchTimersByEventId.remove(event.eventId);
                }

                mAdapter.removeEventById(event.eventId);
                mAdapter.notifyDataSetChanged();
            }
        });
    }

    @Override
    public void onResendingEvent(final Event event) {
        // not anymore required
        // because the message keeps the same UI until the server echo is receieved.
        /*mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                mAdapter.notifyDataSetChanged();
            }
        });*/
    }

    @Override
    public void onResentEvent(final Event event) {
        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                mAdapter.notifyDataSetChanged();
            }
        });
    }

    @Override
    public void onReceiptEvent() {
        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                mAdapter.notifyDataSetChanged();
            }
        });
    }

    public void onInitialMessagesLoaded() {
        // Jump to the bottom of the list
        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                dismissLoadingProgress();

                // refresh the list only at the end of the sync
                // else the one by one message refresh gives a weird UX
                // The application is almost frozen during the
                mAdapter.notifyDataSetChanged();
                mMessageListView.setSelection(mAdapter.getCount() - 1);

                mIsInitialSyncing = false;

                // fill the page
                mMessageListView.post(new Runnable() {
                    @Override
                    public void run() {
                        fillHistoryPage();
                    }
                });
            }
        });
    }

    /**
     * Paginate the room until to fill the current page or there is no more item to display.
     */
    private void fillHistoryPage() {
        // does nothing if the activity has been killed
        if (null == getActivity()) {
            return;
        }

        if (mMessageListView.getFirstVisiblePosition() == 0) {
            mIsCatchingUp = mMatrixMessagesFragment.requestHistory(new SimpleApiCallback<Integer>(getActivity()) {
                @Override
                public void onSuccess(final Integer count) {
                    dismissLoadingProgress();
                    // Scroll the list down to where it was before adding rows to the top
                    mUiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            // refresh the list only at the end of the sync
                            // else the one by one message refresh gives a weird UX
                            // The application is almost frozen during the
                            mAdapter.notifyDataSetChanged();
                            mIsCatchingUp = false;

                            if (count != 0) {
                                mMessageListView.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        fillHistoryPage();
                                    }
                                });
                            }
                        }
                    });
                }

                private void onError(String message) {
                    if (!TextUtils.isEmpty(message)) {
                        Toast.makeText(MatrixMessageListFragment.this.getActivity(), message, Toast.LENGTH_SHORT).show();
                    }

                    dismissLoadingProgress();
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

            if (mIsCatchingUp) {
                displayLoadingProgress();
            }
        }
    }

    /***  MessageAdapter listener  ***/
    public void onRowClick(int position) {
    }

    public Boolean onRowLongClick(int position) {
        return false;
    }

    public void onContentClick(int position) {
    }

    public Boolean onContentLongClick(int position) {
        return false;
    }

    public void onAvatarClick(String userId) {
    }

    public Boolean onAvatarLongClick(String userId) {
        return false;
    }

    public void onSenderNameClick(String userId, String displayName) {
    }

    public void onMediaDownloaded(int position) {
    }

    public void onReadReceiptClick(String eventId, String userId, ReceiptData receipt) {
    }

    public boolean onReadReceiptLongClick(String eventId, String userId, ReceiptData receipt) {
        return false;
    }

    public void onMoreReadReceiptClick(String eventId) {
    }

    public boolean onMoreReadReceiptLongClick(String eventId) {
        return false;
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
