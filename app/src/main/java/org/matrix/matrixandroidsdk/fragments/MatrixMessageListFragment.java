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

package org.matrix.matrixandroidsdk.fragments;

import android.app.AlertDialog;
import android.content.DialogInterface;

import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.ContentResponse;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.FileMessage;
import org.matrix.androidsdk.rest.model.ImageMessage;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.Message;
import org.matrix.androidsdk.util.ContentManager;
import org.matrix.androidsdk.util.JsonUtils;
import org.matrix.matrixandroidsdk.Matrix;
import org.matrix.matrixandroidsdk.R;
import org.matrix.matrixandroidsdk.ToastErrorHandler;
import org.matrix.matrixandroidsdk.activity.CommonActivityUtils;
import org.matrix.matrixandroidsdk.adapters.MessageRow;
import org.matrix.matrixandroidsdk.adapters.MessagesAdapter;
import org.matrix.matrixandroidsdk.db.ConsoleMediasCache;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

import retrofit.RetrofitError;

/**
 * UI Fragment containing matrix messages for a given room.
 * Contains {@link MatrixMessagesFragment} as a nested fragment to do the work.
 */
public class MatrixMessageListFragment extends Fragment implements MatrixMessagesFragment.MatrixMessagesListener, MessagesAdapter.MessagesAdapterClickListener {

    private static final String TAG_FRAGMENT_MESSAGE_DETAILS = "org.matrix.androidsdk.RoomActivity.TAG_FRAGMENT_MESSAGE_DETALS";

    public static final String ARG_ROOM_ID = "org.matrix.matrixandroidsdk.fragments.MatrixMessageListFragment.ARG_ROOM_ID";
    public static final String ARG_LAYOUT_ID = "org.matrix.matrixandroidsdk.fragments.MatrixMessageListFragment.ARG_LAYOUT_ID";

    private static final String TAG_FRAGMENT_MATRIX_MESSAGES = "org.matrix.androidsdk.RoomActivity.TAG_FRAGMENT_MATRIX_MESSAGES";
    private static final String LOG_TAG = "ErrorListener";

    public static MatrixMessageListFragment newInstance(String roomId) {
        return newInstance(roomId, R.layout.fragment_matrix_message_list_fragment);
    }

    public static MatrixMessageListFragment newInstance(String roomId, int layoutResId) {
        MatrixMessageListFragment f = new MatrixMessageListFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ROOM_ID, roomId);
        args.putInt(ARG_LAYOUT_ID, layoutResId);
        f.setArguments(args);
        return f;
    }

    private MatrixMessagesFragment mMatrixMessagesFragment;
    private MessagesAdapter mAdapter;
    private ListView mMessageListView;
    private Handler mUiHandler;
    private MXSession mSession;
    private Room mRoom;
    private boolean mDisplayAllEvents = true;

    private AlertDialog mRedactResendAlert = null;

    // avoid to catch up old content if the initial sync is in progress
    private boolean mIsInitialSyncing = true;
    private boolean mIsCatchingUp = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        // for dispatching data to add to the adapter we need to be on the main thread
        mUiHandler = new Handler(Looper.getMainLooper());

        mSession = Matrix.getInstance(getActivity()).getDefaultSession();

        Bundle args = getArguments();
        String roomId = args.getString(ARG_ROOM_ID);
        mRoom = mSession.getDataHandler().getRoom(roomId);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        Bundle args = getArguments();
        View v = inflater.inflate(args.getInt(ARG_LAYOUT_ID), container, false);
        mMessageListView = ((ListView)v.findViewById(R.id.listView_messages));
        if (mAdapter == null) {
            // only init the adapter if it wasn't before, so we can preserve messages/position.
            mAdapter = new MessagesAdapter(getActivity(),
                    R.layout.adapter_item_message_text,
                    R.layout.adapter_item_message_image,
                    R.layout.adapter_item_message_notice,
                    R.layout.adapter_item_message_emote,
                    R.layout.adapter_item_message_file
            );
        }
        mAdapter.setTypingUsers(mRoom.getTypingUsers());
        mMessageListView.setAdapter(mAdapter);
        mMessageListView.setSelection(0);
        mMessageListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                MatrixMessageListFragment.this.onItemClick(position);
            }
        });

        mAdapter.setMessagesAdapterClickListener(new MessagesAdapter.MessagesAdapterClickListener() {
            @Override
            public void onItemClick(int position) {
                MatrixMessageListFragment.this.onItemClick(position);
            }
        });

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        mDisplayAllEvents = preferences.getBoolean(getString(R.string.settings_key_display_all_events), false);

        return v;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        Bundle args = getArguments();
        FragmentManager fm = getActivity().getSupportFragmentManager();
        mMatrixMessagesFragment = (MatrixMessagesFragment) fm.findFragmentByTag(TAG_FRAGMENT_MATRIX_MESSAGES);

        if (mMatrixMessagesFragment == null) {
            // this fragment controls all the logic for handling messages / API calls
            mMatrixMessagesFragment = MatrixMessagesFragment.newInstance(args.getString(ARG_ROOM_ID), this);
            fm.beginTransaction().add(mMatrixMessagesFragment, TAG_FRAGMENT_MATRIX_MESSAGES).commit();
        }
        else {
            // Reset the listener because this is not done when the system restores the fragment (newInstance is not called)
            mMatrixMessagesFragment.setMatrixMessagesListener(this);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mMessageListView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                //check only when the user scrolls the content
                if  (scrollState == SCROLL_STATE_TOUCH_SCROLL) {
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
                if ((firstVisibleItem == 0) && (!mIsInitialSyncing) && (visibleItemCount != totalItemCount)) {
                    requestHistory();
                }
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    public void sendTextMessage(String body) {
        sendMessage(Message.MSGTYPE_TEXT, body);
    }

    // create a dummy message row for the message
    // It is added to the Adapter
    // return the created Message
    private MessageRow addMessageRow(Message message) {
        Event event = new Event(message, mSession.getCredentials().userId, mRoom.getRoomId());
        mSession.getDataHandler().storeLiveRoomEvent(event);

        MessageRow messageRow = new MessageRow(event, mRoom.getLiveState());
        mAdapter.add(messageRow);

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

    /**
     * Upload a media content
     * @param mediaUrl the media Uurl
     * @param mimeType the media mime type
     * @param messageBody the message body
     */
    public void uploadMediaContent(final String mediaUrl, final String mimeType, final String messageBody) {
        // create a tmp row
        final FileMessage tmpFileMessage = new FileMessage();

        tmpFileMessage.url = mediaUrl;
        tmpFileMessage.body = messageBody;

        FileInputStream fileStream = null;

        try {
            Uri uri = Uri.parse(mediaUrl);
            Room.fillFileInfo(getActivity(), tmpFileMessage, uri, mimeType);

            String filename = uri.getPath();
            fileStream = new FileInputStream (new File(filename));

        } catch (Exception e) {
        }

        // remove any displayed MessageRow with this URL
        // to avoid duplicate
        final MessageRow messageRow = addMessageRow(tmpFileMessage);
        messageRow.getEvent().mSentState = Event.SentState.SENDING;

        mSession.getContentManager().uploadContent(fileStream, mimeType, mediaUrl, new ContentManager.UploadCallback() {
            @Override
            public void onUploadProgress(String anUploadId, int percentageProgress) {
            }

            @Override
            public void onUploadComplete(final String anUploadId, final ContentResponse uploadResponse) {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        FileMessage message = tmpFileMessage;

                        if ((null != uploadResponse) && (null != uploadResponse.contentUri)) {
                            // Build the image message
                            message = new FileMessage();

                            // replace the thumbnail and the media contents by the computed ones
                            ConsoleMediasCache.saveFileMediaForUrl(getActivity(), uploadResponse.contentUri, mediaUrl, tmpFileMessage.getMimeType());
                            message.url = uploadResponse.contentUri;
                            message.info = tmpFileMessage.info;
                            message.body = tmpFileMessage.body;

                            // update the event content with the new message info
                            messageRow.getEvent().content = JsonUtils.toJson(message);

                            Log.d(LOG_TAG, "Uploaded to " + uploadResponse.contentUri);
                        }

                        // warn the user that the media upload fails
                        if ((null == uploadResponse) || (null == uploadResponse.contentUri)) {
                            messageRow.getEvent().mSentState = Event.SentState.UNDELIVERABLE;

                            Toast.makeText(getActivity(),
                                    getString(R.string.message_failed_to_upload),
                                    Toast.LENGTH_LONG).show();
                        } else {
                            // send the message
                            if (message.url != null)  {
                                send(messageRow);
                            }
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
     * @param thumbnailUrl the thumbnail Url
     * @param imageUrl the image Uri
     * @param mimeType the image mine type
     */
    public void uploadImageContent(final String thumbnailUrl, final String imageUrl, final String mimeType) {
        // create a tmp row
        final ImageMessage tmpImageMessage = new ImageMessage();

        tmpImageMessage.url = imageUrl;
        tmpImageMessage.thumbnailUrl = thumbnailUrl;
        tmpImageMessage.body = "Image";

        FileInputStream imageStream = null;

        try {
            Uri uri = Uri.parse(imageUrl);
            Room.fillImageInfo(getActivity(), tmpImageMessage, uri, mimeType);

            String filename = uri.getPath();
            imageStream = new FileInputStream (new File(filename));

        } catch (Exception e) {
        }

        // remove any displayed MessageRow with this URL
        // to avoid duplicate
        final MessageRow imageRow = addMessageRow(tmpImageMessage);
        imageRow.getEvent().mSentState = Event.SentState.SENDING;

        mSession.getContentManager().uploadContent(imageStream, mimeType, imageUrl, new ContentManager.UploadCallback() {
            @Override
            public void onUploadProgress(String anUploadId, int percentageProgress) {
            }

            @Override
            public void onUploadComplete(final String anUploadId, final ContentResponse uploadResponse) {
                getActivity().runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    ImageMessage message = tmpImageMessage;

                                                    if ((null != uploadResponse) && (null != uploadResponse.contentUri)) {
                                                        // Build the image message
                                                        message = new ImageMessage();

                                                        // replace the thumbnail and the media contents by the computed ones
                                                        ConsoleMediasCache.saveFileMediaForUrl(getActivity(), uploadResponse.contentUri, thumbnailUrl, mAdapter.getMaxThumbnailWith(), mAdapter.getMaxThumbnailHeight(), "image/jpeg");
                                                        ConsoleMediasCache.saveFileMediaForUrl(getActivity(), uploadResponse.contentUri, imageUrl, tmpImageMessage.getMimeType());

                                                        message.thumbnailUrl = null;
                                                        message.url = uploadResponse.contentUri;
                                                        message.info = tmpImageMessage.info;
                                                        message.body = "Image";

                                                        // update the event content with the new message info
                                                        imageRow.getEvent().content = JsonUtils.toJson(message);

                                                        Log.d(LOG_TAG, "Uploaded to " + uploadResponse.contentUri);
                                                    }

                                                    // warn the user that the media upload fails
                                                    if ((null == uploadResponse) || (null == uploadResponse.contentUri)) {
                                                        imageRow.getEvent().mSentState = Event.SentState.UNDELIVERABLE;

                                                        Toast.makeText(getActivity(),
                                                                getString(R.string.message_failed_to_upload),
                                                                Toast.LENGTH_LONG).show();
                                                    } else {
                                                        // send the message
                                                        if (message.url != null)  {
                                                            send(imageRow);
                                                        }
                                                    }
                                                }
                                            });

            }
        });
    }

    private void resend(Event event) {
        // remove the event
        mSession.getDataHandler().deleteRoomEvent(event);
        mAdapter.removeEventById(event.eventId);

        // send it again
        final Message message = JsonUtils.toMessage(event.content);

        // resend an image ?
        if (message instanceof ImageMessage) {
            ImageMessage imageMessage = (ImageMessage)message;

            // media has not been uploaded
            if (imageMessage.isLocalContent()) {
                uploadImageContent(imageMessage.thumbnailUrl, imageMessage.url, imageMessage.getMimeType());
                return;
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
                    mAdapter.waitForEcho(messageRow);
                }

                private void commonFailure(Event event) {
                    // display the error message only if the message cannot be resent
                    if ((null != event.unsentException) && (event.isUndeliverable())) {
                        if ((event.unsentException instanceof RetrofitError) && ((RetrofitError) event.unsentException).isNetworkError()) {
                            Toast.makeText(getActivity(), getActivity().getString(R.string.unable_to_send_message) + " : " + getActivity().getString(R.string.network_error), Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(getActivity(), getActivity().getString(R.string.unable_to_send_message) + " : " + event.unsentException.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                        }
                    } else if (null != event.unsentMatrixError) {
                        Toast.makeText(getActivity(), getActivity().getString(R.string.unable_to_send_message) + " : " + event.unsentMatrixError.error + ".", Toast.LENGTH_LONG).show();
                    }

                    mAdapter.notifyDataSetChanged();
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

    private void displayLoadingProgress() {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final View progressView = getActivity().findViewById(R.id.loading_room_content_progress);

                if (null != progressView) {
                    progressView.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    private void dismissLoadingProgress() {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final View progressView = getActivity().findViewById(R.id.loading_room_content_progress);

                if (null != progressView) {
                    progressView.setVisibility(View.GONE);
                }
            }
        });
    }

    public void requestHistory() {
        // avoid launching catchup if there is already one in progress
        if (!mIsCatchingUp) {
            mIsCatchingUp = true;
            final int firstPos = mMessageListView.getFirstVisiblePosition();

            boolean isStarted = mMatrixMessagesFragment.requestHistory(new SimpleApiCallback<Integer>(getActivity()) {
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
                            mMessageListView.setSelection(firstPos + count);
                            mIsCatchingUp = false;
                        }
                    });
                }

                // the request will be auto restarted when a valid network will be found
                @Override
                public void onNetworkError(Exception e) {
                    Log.e(LOG_TAG, "Network error: " + e.getMessage());
                    dismissLoadingProgress();

                    MatrixMessageListFragment.this.getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MatrixMessageListFragment.this.getActivity(), getActivity().getString(R.string.network_error), Toast.LENGTH_SHORT).show();
                            MatrixMessageListFragment.this.dismissLoadingProgress();
                            mIsCatchingUp = false;
                        }
                    });
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    dismissLoadingProgress();

                    Log.e(LOG_TAG, "Matrix error" + " : " + e.errcode + " - " + e.error);
                    // The access token was not recognized: log out
                    if (MatrixError.UNKNOWN_TOKEN.equals(e.errcode)) {
                        CommonActivityUtils.logout(MatrixMessageListFragment.this.getActivity());
                    }

                    final MatrixError matrixError = e;

                    MatrixMessageListFragment.this.getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MatrixMessageListFragment.this.getActivity(), getActivity().getString(R.string.matrix_error) + " : " + matrixError.error, Toast.LENGTH_SHORT).show();
                            MatrixMessageListFragment.this.dismissLoadingProgress();
                            mIsCatchingUp = false;
                        }
                    });
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    dismissLoadingProgress();

                    Log.e(LOG_TAG, getActivity().getString(R.string.unexpected_error) + " : " + e.getMessage());
                    MatrixMessageListFragment.this.dismissLoadingProgress();
                    mIsCatchingUp = false;
                }
            });

            if (isStarted) {
                displayLoadingProgress();
            }
        }
    }

    private void redactEvent(String eventId) {
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
                 Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(type);
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
        if (mMessageListView.getFirstVisiblePosition() == 0) {
            displayLoadingProgress();
            mIsCatchingUp = true;

            mMatrixMessagesFragment.requestHistory(new SimpleApiCallback<Integer>(getActivity()) {
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

                @Override
                public void onNetworkError(Exception e) {
                    dismissLoadingProgress();
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    dismissLoadingProgress();
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    dismissLoadingProgress();
                }
            });
        }
    }

    /**
     * Item selection management
     */
    private static final int OPTION_CANCEL = 0;
    private static final int OPTION_RESEND = 1;
    private static final int OPTION_REDACT = 2;
    private static final int OPTION_MESSAGE_DETAILS = 3;

    private String[] buildOptionLabels(List<Integer> options) {
        String[] labels = new String[options.size()];
        for (int i = 0; i < options.size(); i++) {
            String label = "";
            switch (options.get(i)) {
                case OPTION_CANCEL:
                    label = getString(R.string.cancel);
                    break;
                case OPTION_RESEND:
                    label = getString(R.string.resend);
                    break;
                case OPTION_REDACT:
                    label = getString(R.string.redact);
                    break;
                case OPTION_MESSAGE_DETAILS:
                    label = getString(R.string.message_details);
                    break;
            }
            labels[i] = label;
        }

        return labels;
    }

    public void onItemClick(int position) {
        final MessageRow messageRow = mAdapter.getItem(position);
        final List<Integer> options = new ArrayList<Integer>();
        if (messageRow.getEvent().canBeResent()) {
            options.add(OPTION_RESEND);
        } else if (messageRow.getEvent().mSentState == Event.SentState.SENT) {
            options.add(OPTION_REDACT);
        }

        // display the JSON
        options.add(OPTION_MESSAGE_DETAILS);

        // do not launch an other alert if the user did not manage this one.
        if ((options.size() != 0) && (null == mRedactResendAlert)) {
            options.add(OPTION_CANCEL);
            mRedactResendAlert = new AlertDialog.Builder(getActivity())
                    .setItems(buildOptionLabels(options), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            switch (options.get(which)) {
                                case OPTION_CANCEL:
                                    dialog.cancel();
                                    mRedactResendAlert = null;
                                    break;
                                case OPTION_RESEND:
                                    getActivity().runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            resend(messageRow.getEvent());
                                        }
                                    });
                                    mRedactResendAlert = null;
                                    break;
                                case OPTION_REDACT:
                                    getActivity().runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            redactEvent(messageRow.getEvent().eventId);
                                        }
                                    });
                                    mRedactResendAlert = null;
                                    break;
                                case OPTION_MESSAGE_DETAILS:
                                    getActivity().runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            FragmentManager fm =  getActivity().getSupportFragmentManager();

                                            MessageDetailsFragment fragment = (MessageDetailsFragment) fm.findFragmentByTag(TAG_FRAGMENT_MESSAGE_DETAILS);
                                            if (fragment != null) {
                                                fragment.dismissAllowingStateLoss();
                                            }
                                            fragment = MessageDetailsFragment.newInstance(messageRow.getEvent().toString());
                                            fragment.show(fm, TAG_FRAGMENT_MESSAGE_DETAILS);
                                        }
                                    });
                                    mRedactResendAlert = null;
                                    break;
                            }
                        }
                    })
                    .create();

            mRedactResendAlert.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    mRedactResendAlert = null;
                }
            });

            try {
                mRedactResendAlert.show();
            } catch (Exception e) {
            }
        }
    }

    // thumbnails management
    public int getMaxThumbnailWith() {
        return mAdapter.getMaxThumbnailWith();
    }

    public int getMaxThumbnailHeight() {
        return mAdapter.getMaxThumbnailHeight();
    }
}
