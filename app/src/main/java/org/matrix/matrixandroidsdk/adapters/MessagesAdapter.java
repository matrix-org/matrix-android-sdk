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

package org.matrix.matrixandroidsdk.adapters;

import android.app.ActionBar;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.ExifInterface;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.JsonNull;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.MyUser;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.rest.model.ContentResponse;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.ImageInfo;
import org.matrix.androidsdk.rest.model.ImageMessage;
import org.matrix.androidsdk.rest.model.Message;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.util.ContentManager;
import org.matrix.androidsdk.util.JsonUtils;
import org.matrix.matrixandroidsdk.Matrix;
import org.matrix.matrixandroidsdk.R;
import org.matrix.matrixandroidsdk.activity.ImageWebViewActivity;
import org.matrix.matrixandroidsdk.activity.MemberDetailsActivity;
import org.matrix.matrixandroidsdk.contacts.Contact;
import org.matrix.matrixandroidsdk.db.ConsoleMediasCache;
import org.matrix.matrixandroidsdk.db.ConsoleContentProvider;
import org.matrix.matrixandroidsdk.util.EventUtils;
import org.matrix.matrixandroidsdk.view.PieFractionView;

import java.io.File;
import java.io.ObjectOutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

/**
 * An adapter which can display events. Events are not limited to m.room.message event types, but
 * can include topic changes (m.room.topic) and room member changes (m.room.member).
 */
public class MessagesAdapter extends ArrayAdapter<MessageRow> {
    public static interface MessagesAdapterClickListener {
        /**
         * Called when the body item is clicked.
         * Some views like textView don't dispatch the click event
         * to their parent view.
         */
        public void onItemClick(int position);
    }

    // text, images, notices(topics, room names, membership changes,
    // displayname changes, avatar url changes), and emotes!
    private static final int NUM_ROW_TYPES = 4;

    private static final int ROW_TYPE_TEXT = 0;
    private static final int ROW_TYPE_IMAGE = 1;
    private static final int ROW_TYPE_NOTICE = 2;
    private static final int ROW_TYPE_EMOTE = 3;

    private static final String LOG_TAG = "MessagesAdapter";

    public static final float MAX_IMAGE_WIDTH_SCREEN_RATIO = 0.45F;
    public static final float MAX_IMAGE_HEIGHT_SCREEN_RATIO = 0.45F;

    private ArrayList<String>mTypingUsers = new ArrayList<String>();

    private Context mContext;
    private HashMap<Integer, Integer> mRowTypeToLayoutId = new HashMap<Integer, Integer>();
    private LayoutInflater mLayoutInflater;

    // To keep track of events and avoid duplicates. For instance, we add a message event
    // when the current user sends one but it will also come down the event stream
    private HashMap<String, MessageRow> mEventRowMap = new HashMap<String, MessageRow>();

    // when a message is sent, the content is displayed until to get the echo from the server
    private HashMap<String, MessageRow> mWaitingEchoRowMap = new HashMap<String, MessageRow>();

    private int mOddColourResId;
    private int mEvenColourResId;

    private int normalColor;
    private int emoteColor;
    private int notSentColor;
    private int sendingColor;
    private int highlightColor;

    private int mMaxImageWidth;
    private int mMaxImageHeight;

    private MessagesAdapterClickListener mMessagesAdapterClickListener = null;

    private DateFormat mDateFormat;

    private MXSession mSession;

    public MessagesAdapter(Context context, int textResLayoutId, int imageResLayoutId,
                           int noticeResLayoutId, int emoteRestLayoutId) {
        super(context, 0);
        mContext = context;
        mRowTypeToLayoutId.put(ROW_TYPE_TEXT, textResLayoutId);
        mRowTypeToLayoutId.put(ROW_TYPE_IMAGE, imageResLayoutId);
        mRowTypeToLayoutId.put(ROW_TYPE_NOTICE, noticeResLayoutId);
        mRowTypeToLayoutId.put(ROW_TYPE_EMOTE, emoteRestLayoutId);
        mLayoutInflater = LayoutInflater.from(mContext);
        mDateFormat = new SimpleDateFormat("MMM d HH:mm", Locale.getDefault());
        // the refresh will be triggered only when it is required
        // for example, retrieve the historical messages triggers a refresh for each message
        setNotifyOnChange(false);

        normalColor = context.getResources().getColor(R.color.message_normal);
        emoteColor = context.getResources().getColor(R.color.message_emote);
        notSentColor = context.getResources().getColor(R.color.message_not_sent);
        sendingColor = context.getResources().getColor(R.color.message_sending);
        highlightColor = context.getResources().getColor(R.color.message_highlighted);

        WindowManager wm = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        mMaxImageWidth = Math.round(display.getWidth() * MAX_IMAGE_WIDTH_SCREEN_RATIO);
        mMaxImageHeight = Math.round(display.getHeight() * MAX_IMAGE_HEIGHT_SCREEN_RATIO);

        mSession = Matrix.getInstance(mContext).getDefaultSession();
    }

    public void setAlternatingColours(int oddResId, int evenResId) {
        mOddColourResId = oddResId;
        mEvenColourResId = evenResId;
    }

    @Override
    public void notifyDataSetChanged() {
        super.notifyDataSetChanged();
    }

    @Override
    public int getViewTypeCount() {
        return NUM_ROW_TYPES;
    }

    public void addToFront(Event event, RoomState roomState) {
        MessageRow row = new MessageRow(event, roomState);
        if (shouldSave(row)) {
            // ensure that notifyDataSetChanged is not called
            // it seems that setNotifyOnChange is resetted to to  default value
            setNotifyOnChange(false);
            insert(row, 0);
            if (row.getEvent().eventId != null) {
                mEventRowMap.put(row.getEvent().eventId, row);
            }
        }
    }

    public void add(Event event, RoomState roomState) {
        add(new MessageRow(event, roomState));
    }

    public void waitForEcho(MessageRow row) {
        String eventId = row.getEvent().eventId;

        // the echo has already been received
        if (mEventRowMap.containsKey(eventId)) {
            mWaitingEchoRowMap.remove(eventId);
            this.remove(row);
        } else {
            mEventRowMap.put(eventId, row);
            mWaitingEchoRowMap.put(eventId, row);
        }
    }

    @Override
    public void add(MessageRow row) {
        if (shouldSave(row)) {
            super.add(row);
            if (row.getEvent().eventId != null) {
                mEventRowMap.put(row.getEvent().eventId, row);
            }

            if (row.getEvent().isWaitingForEcho()) {
                mWaitingEchoRowMap.put(row.getEvent().eventId, row);
            }

            this.notifyDataSetChanged();
        }
    }

    public void removeEventById(String eventId) {
        MessageRow row = mEventRowMap.get(eventId);

        if (row != null) {
            remove(row);
        }
    }

    private boolean shouldSave(MessageRow row) {
        boolean shouldSave = isDisplayableEvent(row.getEvent(), row.getRoomState());

        if (shouldSave) {
            String eventId = row.getEvent().eventId;

            shouldSave = !mEventRowMap.containsKey(eventId);

            // a message has already been store with the same eventID
            if (!shouldSave) {
                MessageRow currentRow = mEventRowMap.get(eventId);

                // Long.MAX_VALUE means that it is a temporary event
                shouldSave = (currentRow.getEvent().age == Long.MAX_VALUE);

                if (!shouldSave) {
                    shouldSave = mWaitingEchoRowMap.containsKey(eventId);

                    // remove the waiting echo message
                    if (shouldSave) {
                        super.remove(mWaitingEchoRowMap.get(eventId));
                        mWaitingEchoRowMap.remove(eventId);
                    }
                } else {
                    super.remove(currentRow);
                }
            }
        }

        return shouldSave;
    }

    private int getItemViewType(Event event) {
        if (Event.EVENT_TYPE_MESSAGE.equals(event.type)) {
            Message message = JsonUtils.toMessage(event.content);

            if (Message.MSGTYPE_TEXT.equals(message.msgtype)) {
                return ROW_TYPE_TEXT;
            }
            else if (Message.MSGTYPE_IMAGE.equals(message.msgtype)) {
                return ROW_TYPE_IMAGE;
            }
            else if (Message.MSGTYPE_EMOTE.equals(message.msgtype)) {
                return ROW_TYPE_EMOTE;
            }
            else {
                // Default is to display the body as text
                return ROW_TYPE_TEXT;
            }
        }
        else if (Event.EVENT_TYPE_STATE_ROOM_TOPIC.equals(event.type) ||
                Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(event.type) ||
                Event.EVENT_TYPE_STATE_ROOM_NAME.equals(event.type)) {
            return ROW_TYPE_NOTICE;
        }
        else {
            throw new RuntimeException("Unknown event type: " + event.type);
        }
    }

    @Override
    public int getItemViewType(int position) {
        MessageRow row = getItem(position);
        return getItemViewType(row.getEvent());
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        try {
            switch (getItemViewType(position)) {
                case ROW_TYPE_TEXT:
                    return getTextView(position, convertView, parent);
                case ROW_TYPE_IMAGE:
                    return getImageView(position, convertView, parent);
                case ROW_TYPE_NOTICE:
                    return getNoticeView(position, convertView, parent);
                case ROW_TYPE_EMOTE:
                    return getEmoteView(position, convertView, parent);
                default:
                    throw new RuntimeException("Unknown item view type for position " + position);
            }
        }
        catch (Exception e) {
            Log.e(LOG_TAG, "Failed to render view at position " + position + ": "+e);
            return convertView;
        }
    }

    private String getUserDisplayName(String userId, RoomState roomState) {
        RoomMember roomMember = roomState.getMember(userId);
        return (roomMember != null) ? roomMember.getName() : userId;
    }

    // return true if convertView is merged with previous View
    private boolean manageSubView(int position, View convertView, View subView, int msgType) {
        MessageRow row = getItem(position);
        Event msg = row.getEvent();
        RoomState roomState = row.getRoomState();

        MyUser myUser = mSession.getMyUser();
        Boolean isMyEvent = myUser.userId.equals(msg.userId);

        // isMergedView -> the message is going to be merged with the previous one
        // willBeMerged -> false if it is the last message of the user
        boolean isMergedView = false;
        boolean willBeMerged = false;

        convertView.setClickable(false);

        // the notice messages are never merged
        if (msgType != ROW_TYPE_NOTICE) {
            //
            String prevUserId = null;
            if (position > 0) {
                MessageRow prevRow = getItem(position - 1);

                if ((null != prevRow) && (getItemViewType(prevRow.getEvent()) != ROW_TYPE_NOTICE)) {
                    prevUserId = prevRow.getEvent().userId;
                }
            }

            String nextUserId = null;

            if ((position + 1) < this.getCount()) {
                MessageRow nextRow = getItem(position + 1);

                if ((null != nextRow) && (getItemViewType(nextRow.getEvent()) != ROW_TYPE_NOTICE)) {
                    nextUserId = nextRow.getEvent().userId;
                }
            }

            isMergedView = (null != prevUserId) && (prevUserId.equals(msg.userId));
            willBeMerged = (null != nextUserId) && (nextUserId.equals(msg.userId));
        }

        // manage sender text
        TextView textView = (TextView) convertView.findViewById(R.id.messagesAdapter_sender);
        if (null != textView) {
            if (isMergedView || isMyEvent) {
                textView.setVisibility(View.GONE);
            } else {
                textView.setVisibility(View.VISIBLE);
                textView.setText(getUserDisplayName(msg.userId, row.getRoomState()));
            }
        }

        TextView leftTsTextView = (TextView) convertView.findViewById(R.id.messagesAdapter_timestamp_left);
        TextView rightTsTextView = (TextView) convertView.findViewById(R.id.messagesAdapter_timestamp_right);
        TextView tsTextView;

        if (isMyEvent) {
            tsTextView = leftTsTextView;
            rightTsTextView.setVisibility(View.GONE);
        } else {
            leftTsTextView.setVisibility(View.GONE);
            tsTextView = rightTsTextView;
        }

        tsTextView.setVisibility(View.VISIBLE);
        tsTextView.setText(getTimestamp(msg.originServerTs));

        if (row.getEvent().isUndeliverable()) {
            tsTextView.setTextColor(notSentColor);
        } else {
            tsTextView.setTextColor(Color.parseColor("#FFAAAAAA"));
        }

        // Sender avatar
        RoomMember sender = roomState.getMember(msg.userId);

        View avatarLeftView = convertView.findViewById(R.id.messagesAdapter_roundAvatar_left);
        View avatarRightView = convertView.findViewById(R.id.messagesAdapter_roundAvatar_right);
        View avatarLayoutView;

        if (isMyEvent) {
            avatarLayoutView = avatarRightView;
            avatarLeftView.setVisibility(View.GONE);

        } else {
            avatarLayoutView = avatarLeftView;
            avatarRightView.setVisibility(View.GONE);

            final String userId = msg.userId;
            final String roomId = roomState.roomId;

            avatarLeftView.setClickable(true);

            // click on the avatar opens the details page
            avatarLeftView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent startRoomInfoIntent = new Intent(mContext, MemberDetailsActivity.class);
                    startRoomInfoIntent.putExtra(MemberDetailsActivity.EXTRA_ROOM_ID, roomId);
                    startRoomInfoIntent.putExtra(MemberDetailsActivity.EXTRA_USER_ID, userId);
                    mContext.startActivity(startRoomInfoIntent);
                }
            });
        }

        ImageView avatarImageView = (ImageView) avatarLayoutView.findViewById(R.id.avatar_img);

        if (isMergedView) {
            avatarLayoutView.setVisibility(View.GONE);
        } else {
            avatarLayoutView.setVisibility(View.VISIBLE);
            avatarImageView.setTag(null);
            avatarImageView.setImageResource(R.drawable.ic_contact_picture_holo_light);

            String url = null;

            if (sender != null) {
                url = sender.avatarUrl;
            } else {
                // join event
                // check if the avatar_url is defined in the event body
                // roomState is updated after managing this event
                // so, this user could miss
                if (msg.content.has("avatar_url")) {
                    url = msg.content.get("avatar_url") == JsonNull.INSTANCE ? null : msg.content.get("avatar_url").getAsString();
                }
            }

            if (TextUtils.isEmpty(url) && (null != msg.userId)) {
                url = AdapterUtils.getIdenticonURL(msg.userId);
            }

            if (!TextUtils.isEmpty(url)) {
                loadAvatar(avatarImageView, url);
            }

            // display the typing icon when required
            ImageView typingImage = (ImageView) avatarLayoutView.findViewById(R.id.avatar_typing_img);
            typingImage.setVisibility((!isMyEvent && (mTypingUsers.indexOf(msg.userId) >= 0)) ? View.VISIBLE : View.GONE);
        }

        // if the messages are merged
        // the thumbnail is hidden
        // and the subview must be moved to be aligned with the previous body
        View bodyLayoutView = convertView.findViewById(R.id.messagesAdapter_body_layout);
        ViewGroup.MarginLayoutParams bodyLayout = (ViewGroup.MarginLayoutParams) bodyLayoutView.getLayoutParams();
        FrameLayout.LayoutParams subViewLinearLayout = (FrameLayout.LayoutParams) subView.getLayoutParams();

        View view = convertView.findViewById(R.id.messagesAdapter_roundAvatar_left);
        ViewGroup.LayoutParams avatarLayout = view.getLayoutParams();

        if (!isMyEvent) {
            subViewLinearLayout.gravity =  Gravity.LEFT | Gravity.CENTER_VERTICAL;

            if (isMergedView) {
                bodyLayout.setMargins(avatarLayout.width, bodyLayout.topMargin, 4, bodyLayout.bottomMargin);

            } else {
                bodyLayout.setMargins(4, bodyLayout.topMargin, 4, bodyLayout.bottomMargin);
            }
            subView.setLayoutParams(bodyLayout);
        } else {
            subViewLinearLayout.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
            
            if (isMergedView) {
                bodyLayout.setMargins(4, bodyLayout.topMargin, avatarLayout.width, bodyLayout.bottomMargin);
            } else {
                bodyLayout.setMargins(4, bodyLayout.topMargin, 4, bodyLayout.bottomMargin);
            }
        }

        bodyLayoutView.setLayoutParams(bodyLayout);
        subView.setLayoutParams(subViewLinearLayout);

        view = convertView.findViewById(R.id.messagesAdapter_message_separator);

        if (null != view) {
            view.setVisibility((willBeMerged || ((position+1) == this.getCount())) ? View.GONE : View.VISIBLE);
        }

        return isMergedView;
    }

    private View getTextView(final int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = mLayoutInflater.inflate(mRowTypeToLayoutId.get(ROW_TYPE_TEXT), parent, false);
        }

        MessageRow row = getItem(position);
        Event msg = row.getEvent();
        RoomState roomState = row.getRoomState();

        AdapterUtils.EventDisplay display = new AdapterUtils.EventDisplay(mContext, msg, roomState);
        final CharSequence body = display.getTextualDisplay();
        final TextView bodyTextView = (TextView) convertView.findViewById(R.id.messagesAdapter_body);
        bodyTextView.setText(body);

        int textColor;

        if (row.getEvent().isSending()) {
            textColor = sendingColor;
        } else if (row.getEvent().isUndeliverable()) {
            textColor = notSentColor;
        } else {
            textColor = (EventUtils.shouldHighlight(mContext, msg) ? highlightColor : normalColor);
        }

        bodyTextView.setTextColor(textColor);

        this.manageSubView(position, convertView, bodyTextView, ROW_TYPE_TEXT);

        // add a click listener because the text view gains the focus.
        //  mMessageListView.setOnItemClickListener is never called.
        convertView.setClickable(true);
        // click on the avatar opens the details page
        convertView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // warn listener of click events if there is no selection
                if (!bodyTextView.hasSelection() && (null != mMessagesAdapterClickListener)) {
                    bodyTextView.requestFocus();
                    mMessagesAdapterClickListener.onItemClick(position);
                }
            }
        });

        setBackgroundColour(convertView, position);
        return convertView;
    }

    private View getImageView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = mLayoutInflater.inflate(mRowTypeToLayoutId.get(ROW_TYPE_IMAGE), parent, false);
        }

        MessageRow row = getItem(position);
        Event msg = row.getEvent();

        final ImageMessage imageMessage = JsonUtils.toImageMessage(msg.content);

        String thumbUrl = null;
        ImageInfo imageInfo = null;

        if (imageMessage != null) {
            // Backwards compatibility with events from before Synapse 0.6.0
            if (imageMessage.thumbnailUrl != null) {
                thumbUrl = imageMessage.thumbnailUrl;
            } else if (imageMessage.url != null) {
                thumbUrl = imageMessage.url;
            }

            imageInfo = imageMessage.info;
        }

        // display a type watermark
        final ImageView imageTypeView = (ImageView) convertView.findViewById(R.id.messagesAdapter_image_type);
        imageTypeView.setBackgroundColor(Color.TRANSPARENT);

        final boolean displayTypeIcon = "image/gif".equals(imageMessage.getMimeType());

        if (displayTypeIcon) {
            imageTypeView.setImageBitmap(BitmapFactory.decodeResource(getContext().getResources(), R.drawable.filetype_gif));
            imageTypeView.setVisibility(View.VISIBLE);
        } else {
            imageTypeView.setVisibility(View.GONE);
        }

        ImageView imageView = (ImageView) convertView.findViewById(R.id.messagesAdapter_image);

        final int maxImageWidth = mMaxImageWidth;
        final int maxImageHeight = mMaxImageHeight;
        final int rotationAngle = ((null != imageInfo) && (imageInfo.rotation != null)) ? imageInfo.rotation : 0;

        // reset the bitmap to ensure that it is not reused from older cells
        imageView.setImageBitmap(null);
        final String downloadId = ConsoleMediasCache.loadBitmap(imageView, thumbUrl, maxImageWidth, maxImageHeight, rotationAngle, "image/jpeg");

        // display a pie char
        final LinearLayout downloadProgressLayout = (LinearLayout) convertView.findViewById(R.id.download_content_layout);
        final PieFractionView downloadPieFractionView = (PieFractionView) convertView.findViewById(R.id.download_content_piechart);

        if (null != downloadProgressLayout) {
            if (null != downloadId) {
                imageTypeView.setVisibility(View.GONE);
                downloadProgressLayout.setVisibility(View.VISIBLE);
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) downloadProgressLayout.getLayoutParams();

                int frameHeight = -1;

                // if the image size is known
                // compute the expected thumbnail height
                if ((null != imageInfo) && (null != imageInfo.w) && (null != imageInfo.h)) {
                    int imageW = imageInfo.w;
                    int imageH = imageInfo.h;

                    // swap width and height if the image is side oriented
                    if ((rotationAngle == 90) || (rotationAngle == 270)) {
                        int tmp = imageW;
                        imageW = imageH;
                        imageH = tmp;
                    }

                    if ((imageW > 0) && (imageH > 0)) {
                        frameHeight = Math.min(maxImageWidth * imageH / imageW, maxImageHeight);
                    }
                }

                // if no defined height
                // use the pie chart one.
                if (frameHeight < 0) {
                    frameHeight = downloadPieFractionView.getHeight();
                }

                // apply it the layout
                // it avoid row jumping when the image is downloaded
                lp.height = frameHeight;

                final String fDownloadId = downloadId;

                ConsoleMediasCache.addDownloadListener(downloadId, new ConsoleMediasCache.DownloadCallback() {
                    @Override
                    public void onDownloadProgress(String aDownloadId, int percentageProgress) {
                        if (aDownloadId.equals(fDownloadId)) {
                            downloadPieFractionView.setFraction(percentageProgress);
                        }
                    }

                    @Override
                    public void onDownloadComplete(String aDownloadId) {

                        if (displayTypeIcon) {
                            imageTypeView.setVisibility(View.VISIBLE);
                        }

                        if (aDownloadId.equals(fDownloadId)) {
                            downloadProgressLayout.setVisibility(View.GONE);
                        }
                    }
                });

                downloadPieFractionView.setFraction(ConsoleMediasCache.progressValueForDownloadId(downloadId));

            } else {
                downloadProgressLayout.setVisibility(View.GONE);
            }
        }

        // The API doesn't make any strong guarantees about the thumbnail size, so also scale
        // locally if needed.
        imageView.setMaxWidth(maxImageWidth);
        imageView.setMaxHeight(maxImageHeight);
        imageView.setBackgroundColor(Color.TRANSPARENT);

        if ((imageMessage != null) && (imageMessage.url != null)) {
            imageView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                    if (null != imageMessage.url) {
                        Intent viewImageIntent = new Intent(mContext, ImageWebViewActivity.class);
                        viewImageIntent.putExtra(ImageWebViewActivity.KEY_HIGHRES_IMAGE_URI, imageMessage.url);
                        viewImageIntent.putExtra(ImageWebViewActivity.KEY_THUMBNAIL_WIDTH, maxImageWidth);
                        viewImageIntent.putExtra(ImageWebViewActivity.KEY_THUMBNAIL_HEIGHT, maxImageHeight);
                        viewImageIntent.putExtra(ImageWebViewActivity.KEY_IMAGE_ROTATION, rotationAngle);
                        if (null != imageMessage.getMimeType()) {
                            viewImageIntent.putExtra(ImageWebViewActivity.KEY_HIGHRES_MIME_TYPE, imageMessage.getMimeType());
                        }
                        mContext.startActivity(viewImageIntent);
                    }
                }
            });
        }

        // manage the upload progress
        final LinearLayout uploadProgressLayout = (LinearLayout) convertView.findViewById(R.id.upload_content_layout);
        final PieFractionView uploadFractionView = (PieFractionView) convertView.findViewById(R.id.upload_content_piechart);

        final ProgressBar uploadSpinner = (ProgressBar) convertView.findViewById(R.id.upload_event_spinner);
        final ImageView uploadFailedImage = (ImageView) convertView.findViewById(R.id.upload_event_failed);

        int progress = -1;

        if (mSession.getMyUser().userId.equals(msg.userId)) {
            progress = mSession.getContentManager().getUploadProgress(imageMessage.url);

            if (progress >= 0) {
                final String url = imageMessage.url;

                mSession.getContentManager().addUploadListener(url, new ContentManager.UploadCallback() {
                    @Override
                    public void onUploadProgress(String anUploadId, int percentageProgress) {
                        if (url.equals(anUploadId)) {
                            uploadFractionView.setFraction(percentageProgress);
                        }
                    }

                    @Override
                    public void onUploadComplete(String anUploadId, ContentResponse uploadResponse) {
                        if (url.equals(anUploadId)) {
                            uploadProgressLayout.setVisibility(View.GONE);
                            uploadSpinner.setVisibility(View.VISIBLE);
                        }
                    }
                });
            }
        }

        uploadSpinner.setVisibility(((progress < 0) && row.getEvent().isSending())? View.VISIBLE : View.GONE);
        uploadFailedImage.setVisibility(row.getEvent().isUndeliverable() ? View.VISIBLE : View.GONE);

        uploadFractionView.setFraction(progress);
        uploadProgressLayout.setVisibility((progress >= 0) ? View.VISIBLE : View.GONE);

        View imageLayout =  convertView.findViewById(R.id.messagesAdapter_image_layout);
        imageLayout.setAlpha(row.getEvent().isSent() ? 1.0f : 0.5f);

        this.manageSubView(position, convertView, imageLayout, ROW_TYPE_IMAGE);

        setBackgroundColour(convertView, position);
        return convertView;
    }

    private View getNoticeView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = mLayoutInflater.inflate(mRowTypeToLayoutId.get(ROW_TYPE_NOTICE), parent, false);
        }

        MessageRow row = getItem(position);
        Event msg = row.getEvent();
        RoomState roomState = row.getRoomState();

        AdapterUtils.EventDisplay display = new AdapterUtils.EventDisplay(mContext, msg, roomState);
        CharSequence notice = display.getTextualDisplay();

        TextView noticeTextView = (TextView) convertView.findViewById(R.id.messagesAdapter_notice);
        noticeTextView.setText(notice);

        this.manageSubView(position, convertView, noticeTextView, ROW_TYPE_NOTICE);

        return convertView;
    }

    private void loadAvatar(ImageView avatarView, String url) {
        int size = getContext().getResources().getDimensionPixelSize(R.dimen.chat_avatar_size);
        ConsoleMediasCache.loadAvatarThumbnail(avatarView, url, size);
    }

    private View getEmoteView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = mLayoutInflater.inflate(mRowTypeToLayoutId.get(ROW_TYPE_EMOTE), parent, false);
        }

        MessageRow row = getItem(position);
        Event msg = row.getEvent();
        RoomState roomState = row.getRoomState();

        String emote = getUserDisplayName(msg.userId, roomState) + " ";

        AdapterUtils.EventDisplay display = new AdapterUtils.EventDisplay(mContext, msg, roomState);
        emote += display.getTextualDisplay();

        TextView emoteTextView = (TextView) convertView.findViewById(R.id.messagesAdapter_emote);
        emoteTextView.setText(emote);

        int textColor;

        if (row.getEvent().isSending()) {
            textColor = sendingColor;
        } else if (row.getEvent().isUndeliverable()) {
            textColor = notSentColor;
        } else {
            textColor = emoteColor;
        }

        emoteTextView.setTextColor(textColor);

        this.manageSubView(position, convertView, emoteTextView, ROW_TYPE_EMOTE);

        return convertView;
    }

    private String getTimestamp(long ts) {
        return mDateFormat.format(new Date(ts));
    }

    private void setBackgroundColour(View view, int position) {
        if (mOddColourResId != 0 && mEvenColourResId != 0) {
            view.setBackgroundColor(position%2 == 0 ? mEvenColourResId : mOddColourResId);
        }
    }

    private boolean isDisplayableEvent(Event event, RoomState roomState) {
        if (Event.EVENT_TYPE_MESSAGE.equals(event.type)) {
            // A message is displayable as long as it has a body
            Message message = JsonUtils.toMessage(event.content);
            return (message.body != null) && (!message.body.equals(""));
        }
        else if (Event.EVENT_TYPE_STATE_ROOM_TOPIC.equals(event.type)
                || Event.EVENT_TYPE_STATE_ROOM_NAME.equals(event.type)) {
            return true;
        }
        else if (Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(event.type)) {
            // if we can display text for it, it's valid.
            AdapterUtils.EventDisplay display = new AdapterUtils.EventDisplay(mContext, event, roomState);
            return display.getTextualDisplay() != null;
        }
        return false;
    }

    public void setTypingUsers(ArrayList<String> typingUsers) {
        boolean refresh = mTypingUsers.size() != typingUsers.size();

        // same length -> ensure that there is an update
        if (!refresh) {
            // do not refresh if the both lists empty
            if (mTypingUsers.size() != 0) {
                for(String userId : mTypingUsers) {
                    // one userID is defined in one list not in the other one
                    if (typingUsers.indexOf(userId) < 0) {
                        refresh = true;
                        break;
                    }
                }
            }
        }

         mTypingUsers = typingUsers;

        if (refresh) {
            notifyDataSetChanged();
        }
    }

    public void setMessagesAdapterClickListener(MessagesAdapterClickListener messagesAdapterClickListener) {
        mMessagesAdapterClickListener = messagesAdapterClickListener;
    }

    // thumbnails management
    public int getMaxThumbnailWith() {
        return mMaxImageWidth;
    }

    public int getMaxThumbnailHeight() {
        return mMaxImageHeight;
    }
}
