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

package org.matrix.androidsdk.adapters;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.ExifInterface;
import android.net.Uri;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.BackgroundColorSpan;
import android.text.style.CharacterStyle;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.URLSpan;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.squareup.okhttp.internal.Platform;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.R;
import org.matrix.androidsdk.data.IMXStore;
import org.matrix.androidsdk.data.MyUser;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.rest.model.ContentResponse;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.EventContent;
import org.matrix.androidsdk.rest.model.FileMessage;
import org.matrix.androidsdk.rest.model.ImageInfo;
import org.matrix.androidsdk.rest.model.ImageMessage;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.Message;
import org.matrix.androidsdk.rest.model.ReceiptData;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.rest.model.VideoInfo;
import org.matrix.androidsdk.rest.model.VideoMessage;
import org.matrix.androidsdk.util.ContentManager;
import org.matrix.androidsdk.util.EventDisplay;
import org.matrix.androidsdk.util.EventUtils;
import org.matrix.androidsdk.util.JsonUtils;
import org.matrix.androidsdk.view.PieFractionView;
import org.sufficientlysecure.htmltextview.HtmlTextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 * An adapter which can display events. Events are not limited to m.room.message event types, but
 * can include topic changes (m.room.topic) and room member changes (m.room.member).
 */
public abstract class MessagesAdapter extends ArrayAdapter<MessageRow> {

    public interface MessagesAdapterEventsListener {
        /**
         * Call when the row is clicked.
         * @param position the cell position.
         */
        void onRowClick(int position);

        /**
         * Call when the row is long clicked.
         * @param position the cell position.
         * @return true if managed
         */
        boolean onRowLongClick(int position);

        /**
         * Called when a click is performed on the message content
         * @param position the cell position
         */
        void onContentClick(int position);

        /**
         * Called when a long click is performed on the message content
         * @param position the cell position
         * @return true if managed
         */
        boolean onContentLongClick(int position);

        /**
         * Define the action to perform when the user tap on an avatar
         * @param userId the user ID
         */
        void onAvatarClick(String userId);

        /**
         * Define the action to perform when the user performs a long tap on an avatar
         * @param userId the user ID
         * @return true if the long clik event is managed
         */
        boolean onAvatarLongClick(String userId);

        /**
         * Define the action to perform when the user taps on the message sender
         * @param userId
         * @param displayName
         */
        void onSenderNameClick(String userId, String displayName);

        /**
         * A media download is done
         * @param position
         */
        void onMediaDownloaded(int position);

        /**
         * Define the action to perform when the user taps on the read receipt.
         * @param eventId the eventID
         * @param userId the userId.
         * @param receipt the receipt.
         */
        void onReadReceiptClick(String eventId, String userId, ReceiptData receipt);

        /**
         * Define the action to perform when the user performs a long tap on the read receipt.
         * @param eventId the eventID
         * @param userId the userId.
         * @param receipt the receipt.
         * @return true if the long click event is managed
         */
        boolean onReadReceiptLongClick(String eventId, String userId, ReceiptData receipt);

        /**
         * Define the action to perform when the user taps on the more read receipts button.
         * @param eventId the eventID
         */
        void onMoreReadReceiptClick(String eventId);

        /**
         * Define the action to perform when the user performs a long tap  on the more read receipts button.
         * @param eventId the eventID
         * @return true if the long clik event is managed
         */
        boolean onMoreReadReceiptLongClick(String eventId);

        /**
         * An url has been clicked in a message text.
         * @param uri the uri.
         */
        void onURLClick(Uri uri);

        /**
         * Tells if an event body must be highlighted
         * @param event the event
         * @return true to highlight it.
         */
         boolean shouldHighlightEvent(Event event);
    }

    protected static final int ROW_TYPE_TEXT = 0;
    protected static final int ROW_TYPE_IMAGE = 1;
    protected static final int ROW_TYPE_NOTICE = 2;
    protected static final int ROW_TYPE_EMOTE = 3;
    protected static final int ROW_TYPE_FILE = 4;
    protected static final int ROW_TYPE_VIDEO = 5;
    protected static final int NUM_ROW_TYPES = 6;

    private static final String LOG_TAG = "MessagesAdapter";

    protected ArrayList<String>mTypingUsers = new ArrayList<String>();

    protected Context mContext;
    private HashMap<Integer, Integer> mRowTypeToLayoutId = new HashMap<Integer, Integer>();
    protected LayoutInflater mLayoutInflater;

    // To keep track of events and avoid duplicates. For instance, we add a message event
    // when the current user sends one but it will also come down the event stream
    private HashMap<String, MessageRow> mEventRowMap = new HashMap<String, MessageRow>();

    // avoid searching bingrule at each refresh
    private HashMap<String, Integer> mTextColorByEventId = new HashMap<String, Integer>();

    private HashMap<String, User> mUserByUserId = new HashMap<String, User>();

    private HashMap<String, Integer> mEventType = new HashMap<String, Integer>();

    protected int normalColor;
    protected int notSentColor;
    protected int sendingColor;
    protected int highlightColor;
    protected int searchHighlightColor;

    protected int mMaxImageWidth;
    protected int mMaxImageHeight;

    // media cache
    protected MXMediasCache mMediasCache;

    // events listener
    protected MessagesAdapterEventsListener mMessagesAdapterEventsListener = null;
    protected MXSession mSession;

    protected boolean mIsSearchMode = false;
    protected String mPattern = null;
    private ArrayList<MessageRow>  mLiveMessagesRowList = null;

    // customization methods
    public int normalMesageColor(Context context) {
        return context.getResources().getColor(R.color.message_normal);
    }

    public int notSentMessageColor(Context context) {
        return context.getResources().getColor(R.color.message_not_sent);
    }

    public int sendingMessageColor(Context context) {
        return context.getResources().getColor(R.color.message_sending);
    }

    public int highlightMessageColor(Context context) {
        return context.getResources().getColor(R.color.message_highlighted);
    }

    public int searchHighlightMessageColor(Context context) {
        return context.getResources().getColor(R.color.message_highlighted);
    }

    /**
     * Find the user from his user ID
     * @param userId the user ID
     * @return the linked User
     */
    protected User getUser(String userId) {
        if (mUserByUserId.containsKey(userId)) {
            return mUserByUserId.get(userId);
        }

        IMXStore store = mSession.getDataHandler().getStore();
        User user = store.getUser(userId);

        if (null != user) {
            mUserByUserId.put(userId, user);
        }

        return user;
    }

    // must be implemented by base class
    public abstract int presenceOfflineColor();
    public abstract int presenceOnlineColor();
    public abstract int presenceUnavailableColor();

    /**
     * Refresh the presence ring of an user.
     * @param presenceView the presence ring view.
     * @param userId the user userID.
     */
    protected void refreshPresenceRing(ImageView presenceView, String userId) {
        String presence = null;

        User user = getUser(userId);

        if (null != user) {
            presence = user.presence;
        }

        if (User.PRESENCE_ONLINE.equals(presence)) {
            presenceView.setColorFilter(presenceOnlineColor());
        } else if (User.PRESENCE_UNAVAILABLE.equals(presence)) {
            presenceView.setColorFilter(presenceUnavailableColor());
        } else if (User.PRESENCE_OFFLINE.equals(presence)) {
            presenceView.setColorFilter(presenceOfflineColor());
        } else {
            presenceView.setColorFilter(android.R.color.transparent);
        }
    }

    /**
     * Default constructor.
     * @param session the dedicated MXSession
     * @param context the context
     * @param mediasCache the medias cache
     */
    public MessagesAdapter(MXSession session, Context context, MXMediasCache mediasCache) {
        this(session, context,
                R.layout.adapter_item_message_text_emote_notice,
                R.layout.adapter_item_message_image_video,
                R.layout.adapter_item_message_text_emote_notice,
                R.layout.adapter_item_message_text_emote_notice,
                R.layout.adapter_item_message_file,
                R.layout.adapter_item_message_image_video,
                mediasCache);
    }

    /**
     * Expanded constructor.
     * each message type has its own layout.
     * @param session the dedicated layout.
     * @param context the context
     * @param textResLayoutId the text message layout.
     * @param imageResLayoutId the image message layout.
     * @param noticeResLayoutId the notice message layout.
     * @param emoteRestLayoutId the emote message layout
     * @param fileResLayoutId the file message layout
     * @param videoResLayoutId the video message layout
     * @param mediasCache the medias cache.
     */
    public MessagesAdapter(MXSession session, Context context, int textResLayoutId, int imageResLayoutId,
                           int noticeResLayoutId, int emoteRestLayoutId, int fileResLayoutId, int videoResLayoutId, MXMediasCache mediasCache) {
        super(context, 0);
        mContext = context;
        mRowTypeToLayoutId.put(ROW_TYPE_TEXT, textResLayoutId);
        mRowTypeToLayoutId.put(ROW_TYPE_IMAGE, imageResLayoutId);
        mRowTypeToLayoutId.put(ROW_TYPE_NOTICE, noticeResLayoutId);
        mRowTypeToLayoutId.put(ROW_TYPE_EMOTE, emoteRestLayoutId);
        mRowTypeToLayoutId.put(ROW_TYPE_FILE, fileResLayoutId);
        mRowTypeToLayoutId.put(ROW_TYPE_VIDEO, videoResLayoutId);
        mMediasCache = mediasCache;
        mLayoutInflater = LayoutInflater.from(mContext);
        // the refresh will be triggered only when it is required
        // for example, retrieve the historical messages triggers a refresh for each message
        setNotifyOnChange(false);

        normalColor = normalMesageColor(context);
        notSentColor = notSentMessageColor(context);
        sendingColor = sendingMessageColor(context);
        highlightColor = highlightMessageColor(context);
        searchHighlightColor = searchHighlightMessageColor(context);

        WindowManager wm = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        // use the MediaStore.Images.Thumbnails MINI_KIND size.
        // it avoid having a mix of large and small thumbnails.
        mMaxImageWidth = 512; //Math.round(display.getWidth() * MAX_IMAGE_WIDTH_SCREEN_RATIO);
        mMaxImageHeight = 384; //Math.round(display.getHeight() * MAX_IMAGE_HEIGHT_SCREEN_RATIO);

        mSession = session;
    }

    @Override
    public int getViewTypeCount() {
        return NUM_ROW_TYPES;
    }

    @Override
    public void notifyDataSetChanged() {
        super.notifyDataSetChanged();
    }

    @Override
    public void clear() {
        super.clear();
        if (!mIsSearchMode) {
            mEventRowMap.clear();
        }
    }

    /**
     * Cancel any pending search and replace the adapter content.
     * @param rows the new adapter content.
     */
    public void cancelSearchWith(ArrayList<MessageRow> rows) {
        mPattern = null;
        mIsSearchMode = false;
        this.clear();
        this.addAll(rows);
        mLiveMessagesRowList = null;
    }

    /**
     * Defines the search pattern.
     * @param pattern the pattern to search.
     */
    public void setSearchPattern(String pattern) {
        if (!TextUtils.equals(pattern, mPattern)) {
            mPattern = pattern;
            mIsSearchMode = !TextUtils.isEmpty(mPattern);

            // in search mode, the live row are backuped to store the live events
            if (mIsSearchMode) {
                // save once
                if (null == mLiveMessagesRowList) {
                    // backup live events
                    mLiveMessagesRowList = new ArrayList<MessageRow>();
                    for (int pos = 0; pos < this.getCount(); pos++) {
                        mLiveMessagesRowList.add(this.getItem(pos));
                    }
                }
            } else if (null != mLiveMessagesRowList) {
                // clear and restore the backuped list
                this.clear();
                this.addAll(mLiveMessagesRowList);
                mLiveMessagesRowList = null;
            }
        }
    }

    /**
     * Add an event to the top of the events list.
     * @param event the event to add
     * @param roomState the event roomstate
     */
    public void addToFront(Event event, RoomState roomState) {
        MessageRow row = new MessageRow(event, roomState);
        if (isSupportedRow(row)) {
            // ensure that notifyDataSetChanged is not called
            // it seems that setNotifyOnChange is reinitialized to true;
            setNotifyOnChange(false);

            if (mIsSearchMode) {
                mLiveMessagesRowList.add(0, row);
            } else {
                insert(row, 0);
            }

            if (row.getEvent().eventId != null) {
                mEventRowMap.put(row.getEvent().eventId, row);
            }
        }
    }

    /**
     * Append an event.
     * @param event  the event to append.
     * @param roomState the event roomstate
     */
    public void add(Event event, RoomState roomState) {
        add(new MessageRow(event, roomState));
    }

    @Override
    public void remove(MessageRow row) {
        if (mIsSearchMode) {
            mLiveMessagesRowList.remove(row);
        } else {
            super.remove(row);
        }
    }

    @Override
    public void add(MessageRow row) {
        add(row, true);
    }

    /**
     * Add a row and refresh the adapter if it is required.
     * @param row the row to append
     * @param refresh tru to refresh the display.
     */
    public void add(MessageRow row, boolean refresh) {
        // ensure that notifyDataSetChanged is not called
        // it seems that setNotifyOnChange is reinitialized to true;
        setNotifyOnChange(false);

        if (isSupportedRow(row)) {
            if (mIsSearchMode) {
                mLiveMessagesRowList.add(row);
            } else {
                super.add(row);
            }
            if (row.getEvent().eventId != null) {
                mEventRowMap.put(row.getEvent().eventId, row);
            }

            if ((!mIsSearchMode) && refresh) {
                this.notifyDataSetChanged();
            }
        }
    }

    /**
     * Remove an event by an eventId
     * @param eventId
     */
    public void removeEventById(String eventId) {
        // ensure that notifyDataSetChanged is not called
        // it seems that setNotifyOnChange is reinitialized to true;
        setNotifyOnChange(false);

        MessageRow row = mEventRowMap.get(eventId);

        if (row != null) {
            remove(row);
        }
    }

    public void updateEventById(Event event, String oldEventId) {
        MessageRow row = mEventRowMap.get(event.eventId);

        // the event is not yet defined
        if (null == row) {
            MessageRow oldRow = mEventRowMap.get(oldEventId);

            if (null != oldRow) {
                mEventRowMap.remove(oldEventId);
                mEventRowMap.put(event.eventId, oldRow);
            }
        } else  {
            // the destinated eventId already exists
            // remove the old display
            removeEventById(oldEventId);
        }

        notifyDataSetChanged();

    }


    /**
     * Check if the row must be added to the list.
     * @param row the row to check.
     * @return true if should be added
     */
    protected boolean isSupportedRow(MessageRow row) {
        boolean isSupported = isDisplayableEvent(row.getEvent(), row.getRoomState());

        if (isSupported) {
            String eventId = row.getEvent().eventId;

            MessageRow currentRow = mEventRowMap.get(eventId);

            // the row should be added only if the message has not been received
            isSupported = (null == currentRow);

            // check if the message is already received
            if (null != currentRow) {
                // waiting for echo
                // the message is displayed as sent event if the echo has not been received
                // it avoids displaying a pending message whereas the message has been sent
                if (currentRow.getEvent().getAge() == Long.MAX_VALUE) {
                    currentRow.updateEvent(row.getEvent());
                }
            }
        }

        return isSupported;
    }

    /**
     * Event to view type.
     * @param event the event to convert
     * @return the view type.
     */
    private int getItemViewType(Event event) {
        String eventId = event.eventId;

        if (null != eventId) {
            Integer type = mEventType.get(eventId);

            if (null != type) {
                return type;
            }
        }

        int viewType;

        if (Event.EVENT_TYPE_MESSAGE.equals(event.type)) {

            String msgType = JsonUtils.getMessageMsgType(event.content);

            if (Message.MSGTYPE_TEXT.equals(msgType)) {
                viewType = ROW_TYPE_TEXT;
            } else if (Message.MSGTYPE_IMAGE.equals(msgType)) {
                viewType = ROW_TYPE_IMAGE;
            } else if (Message.MSGTYPE_EMOTE.equals(msgType)) {
                viewType = ROW_TYPE_EMOTE;
            } else if (Message.MSGTYPE_NOTICE.equals(msgType)) {
                viewType = ROW_TYPE_NOTICE;
            } else if (Message.MSGTYPE_FILE.equals(msgType)) {
                viewType = ROW_TYPE_FILE;
            } else if (Message.MSGTYPE_VIDEO.equals(msgType)) {
                viewType = ROW_TYPE_VIDEO;
            } else {
                // Default is to display the body as text
                viewType = ROW_TYPE_TEXT;
            }
        }
        else if (
                event.isCallEvent() ||
                        Event.EVENT_TYPE_STATE_ROOM_TOPIC.equals(event.type) ||
                        Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(event.type) ||
                        Event.EVENT_TYPE_STATE_ROOM_NAME.equals(event.type) ||
                        Event.EVENT_TYPE_STATE_ROOM_THIRD_PARTY_INVITE.equals(event.type)
                ) {
            viewType = ROW_TYPE_NOTICE;
        }
        else {
            throw new RuntimeException("Unknown event type: " + event.type);
        }

        if (null != eventId) {
            mEventType.put(eventId, new Integer(viewType));
        }

        return viewType;
    }

    @Override
    public int getItemViewType(int position) {
        MessageRow row = getItem(position);
        return getItemViewType(row.getEvent());
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        switch (getItemViewType(position)) {
            case ROW_TYPE_TEXT:
                return getTextView(position, convertView, parent);
            case ROW_TYPE_IMAGE:
            case ROW_TYPE_VIDEO:
                    return getImageVideoView(getItemViewType(position), position, convertView, parent);
            case ROW_TYPE_NOTICE:
                return getNoticeView(position, convertView, parent);
            case ROW_TYPE_EMOTE:
                return getEmoteView(position, convertView, parent);
            case ROW_TYPE_FILE:
                return getFileView(position, convertView, parent);
            default:
                throw new RuntimeException("Unknown item view type for position " + position);
        }
    }

    /**
     * Returns an user display name for an user Id.
     * @param userId the user id.
     * @param roomState the room state
     * @return teh user display name.
     */
    protected String getUserDisplayName(String userId, RoomState roomState) {
        if (null != roomState) {
            return roomState.getMemberName(userId);
        } else {
            return userId;
        }
    }

    /**
     * Provides the formatted timestamp to display.
     * null means that the timestamp text must be hidden.
     * @param event the event.
     * @return  the formatted timestamp to display.
     */
    protected String getFormattedTimestamp(Event event) {
        return event.formattedOriginServerTs();
    }


    /**
     * Refresh the avatar thumbnail.
     * @param avatarView the avatar view
     * @param member the member
     * @param userId the member id.
     * @param url the avatar url
     */
    protected void loadMemberAvatar(ImageView avatarView, RoomMember member, String userId, String url) {
        if ((member != null) && (null == url)) {
            url = member.avatarUrl;
        }

        if (TextUtils.isEmpty(url) && (null != userId)) {
            url = ContentManager.getIdenticonURL(userId);
        }

        // define a default one.
        avatarView.setImageResource(R.drawable.ic_contact_picture_holo_light);

        if (!TextUtils.isEmpty(url)) {
            int size = getContext().getResources().getDimensionPixelSize(R.dimen.chat_avatar_size);
            mMediasCache.loadAvatarThumbnail(mSession.getHomeserverConfig(), avatarView, url, size);
        }
    }

    /**
     * Load avatar thumbnail
     * @param avatarView
     * @param url
     */
    protected void loadSmallAvatar(ImageView avatarView, String url) {
        int size = getContext().getResources().getDimensionPixelSize(R.dimen.chat_small_avatar_size);
        mMediasCache.loadAvatarThumbnail(mSession.getHomeserverConfig(), avatarView, url, size);
    }

    /**
     * update the typing view visibility
     * @param avatarLayoutView the avatar layout
     * @param status view.GONE / View.VISIBLE
     */
    protected void setTypingVisibility(View avatarLayoutView, int status) {
        // display the typing icon when required
        ImageView typingImage = (ImageView) avatarLayoutView.findViewById(R.id.avatar_typing_img);
        typingImage.setVisibility(status);
    }

    /**
     * Refresh the receiver thumbnails
     * @param receiversLayout the receiver layout
     * @param leftAlign the avatars are left align i.e. they are ddisplayed from the left to the right one.
     * @param eventId the event Id
     * @param roomState the roomstate.
     */
    protected void refreshReceiverLayout(final LinearLayout receiversLayout, final boolean leftAlign, final String eventId, final RoomState roomState) {
        // sanity checks
        if ((null == roomState) || (null == receiversLayout)) {
            return;
        }

        IMXStore store = mSession.getDataHandler().getStore();
        List<ReceiptData> receipts = store.getEventReceipts(roomState.roomId, eventId, true, true);
        ArrayList<View> imageViews = new ArrayList<View>();

        imageViews.add(receiversLayout.findViewById(R.id.messagesAdapter_avatar1).findViewById(R.id.avatar_img));
        imageViews.add(receiversLayout.findViewById(R.id.messagesAdapter_avatar2).findViewById(R.id.avatar_img));
        imageViews.add(receiversLayout.findViewById(R.id.messagesAdapter_avatar3).findViewById(R.id.avatar_img));

        if (!leftAlign) {
            Collections.reverse(imageViews);
        }

        TextView moreViewLeft  = (TextView)receiversLayout.findViewById(R.id.messagesAdapter_more_than_three_left);
        TextView moreViewRight = (TextView)receiversLayout.findViewById(R.id.messagesAdapter_more_than_three_right);

        int index = 0;

        // the receipts are defined
        if ((null != receipts) && (0 != receipts.size())) {
            int bound = Math.min(receipts.size(), imageViews.size());

            for (; index < bound; index++) {
                final ReceiptData r = receipts.get(index);
                RoomMember member = roomState.getMember(r.userId);
                ImageView imageView = (ImageView) imageViews.get(index);

                imageView.setVisibility(View.VISIBLE);
                imageView.setTag(null);
                imageView.setImageResource(R.drawable.ic_contact_picture_holo_light);

                // the member might be null if he left the room
                if ((null != member) && (null != member.avatarUrl)) {
                    loadSmallAvatar(imageView, member.avatarUrl);
                }

                final String userId = r.userId;

                imageView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mMessagesAdapterEventsListener.onReadReceiptClick(eventId, userId, r);
                    }
                });

                imageView.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        return mMessagesAdapterEventsListener.onReadReceiptLongClick(eventId, userId, r);
                    }
                });

            }

            TextView displayedMoreTextView = null;

            if (receipts.size() <= imageViews.size()) {
                moreViewLeft.setVisibility(View.GONE);
                moreViewRight.setVisibility(View.GONE);
            } else {
                int diff = receipts.size() - imageViews.size();

                if (!leftAlign) {
                    displayedMoreTextView = moreViewLeft;
                    moreViewLeft.setVisibility(View.VISIBLE);
                    moreViewRight.setVisibility(View.GONE);

                    moreViewLeft.setText(diff + "+");
                } else {
                    displayedMoreTextView = moreViewRight;
                    moreViewLeft.setVisibility(View.GONE);
                    moreViewRight.setVisibility(View.VISIBLE);
                    moreViewRight.setText("+" + diff);
                }

                displayedMoreTextView.setVisibility((receipts.size() > imageViews.size()) ? View.VISIBLE : View.GONE);

                displayedMoreTextView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mMessagesAdapterEventsListener.onMoreReadReceiptClick(eventId);
                    }
                });

                displayedMoreTextView.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        return mMessagesAdapterEventsListener.onMoreReadReceiptLongClick(eventId);
                    }
                });
            }
        } else {

            moreViewRight.setVisibility(View.GONE);
            moreViewLeft.setVisibility(View.GONE);
        }

        for(; index < imageViews.size(); index++) {
            imageViews.get(index).setVisibility(View.INVISIBLE);
        }
    }

    /***
     * Tells if the sender avatar must be displayed on the right screen side.
     * By default, the self avatar is displayed on right side.
     * The inherited class must override this class to choose where to display them.
     *
     * @param event the event to test.
     * @return true if the avatar must be displayed on right side.
     */
    protected boolean isAvatarDisplayedOnRightSide(Event event) {
        return mSession.getMyUserId().equals(event.getSender()) || Event.EVENT_TYPE_CALL_INVITE.equals(event.type);
    }

    /**
     * Tells if the event body should be merged with the previous event
     * @param event the event
     * @param position the event position in the events list
     * @param shouldBeMerged true if the event should be be merged
     * @return true to merge with the previous event body
     */
    protected boolean mergeView(Event event, int position, boolean shouldBeMerged) {
        return shouldBeMerged;
    }

    /**
     * Some event should never be merged.
     * e.g. the profile info update (avatar, display name...)
     * @param event the event
     * @return true if the event can be merged.
     */
    protected boolean isMergeableEvent(Event event) {
        boolean res = true;

        // user profile update should not be merged
        if (TextUtils.equals(event.type, Event.EVENT_TYPE_STATE_ROOM_MEMBER)) {

            EventContent eventContent = JsonUtils.toEventContent(event.getContentAsJsonObject());
            EventContent prevEventContent = event.getPrevContent();
            String prevMembership = null;

            if (null != prevEventContent) {
                prevMembership = prevEventContent.membership;
            }

            res = !TextUtils.equals(prevMembership, eventContent.membership);
        }

        return res;
    }

    /**
     * Common view management.
     * @param position the item position.
     * @param convertView the row view
     * @param subView the message content view
     * @param msgType the message type
     * @return true if the view is merged.
     */
    protected boolean manageSubView(final int position, View convertView, View subView, int msgType) {
        MessageRow row = getItem(position);
        Event event = row.getEvent();
        RoomState roomState = row.getRoomState();

        convertView.setClickable(false);

        boolean isAvatarOnRightSide = isAvatarDisplayedOnRightSide(event);

        // isMergedView -> the message is going to be merged with the previous one
        // willBeMerged ->tell if a message separator must be displayed
        boolean isMergedView = false;
        boolean willBeMerged = false;

        if (!mIsSearchMode) {

            if ((position > 0) && isMergeableEvent(event)) {
                MessageRow prevRow = getItem(position - 1);
                isMergedView  = TextUtils.equals(prevRow.getEvent().getSender(), event.getSender());
            }

            // not the last message
            if ((position + 1) < this.getCount()) {
                MessageRow nextRow = getItem(position + 1);

                if (isMergeableEvent(event) || isMergeableEvent(nextRow.getEvent())) {
                    willBeMerged = TextUtils.equals(nextRow.getEvent().getSender(), event.getSender());
                }
            }
        }

        // inherited class custom behaviour
        isMergedView = mergeView(event, position, isMergedView);

        View leftTsTextLayout = convertView.findViewById(R.id.message_timestamp_layout_left);
        View rightTsTextLayout = convertView.findViewById(R.id.message_timestamp_layout_right);

        // manage sender text
        TextView textView = (TextView) convertView.findViewById(R.id.messagesAdapter_sender);
        if (null != textView) {
            if (null == rightTsTextLayout) {
                textView.setVisibility(View.VISIBLE);

                if (isMergedView) {
                    textView.setText("");
                } else {
                    textView.setText(getUserDisplayName(event.getSender(), row.getRoomState()));
                }
            }
            else if (isMergedView || isAvatarOnRightSide || (msgType == ROW_TYPE_NOTICE)) {
                textView.setVisibility(View.GONE);
            } else {
                textView.setVisibility(View.VISIBLE);
                textView.setText(getUserDisplayName(event.getSender(), row.getRoomState()));
            }

            final String fSenderId = event.getSender();
            final String fDisplayName = textView.getText().toString();

            textView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (null != mMessagesAdapterEventsListener) {
                        mMessagesAdapterEventsListener.onSenderNameClick(fSenderId, fDisplayName);
                    }
                }
            });
        }

        TextView tsTextView = null;
        TextView leftTsTextView = null;
        TextView rightTsTextView = null;

        if (null != leftTsTextLayout) {
            leftTsTextView = (TextView)leftTsTextLayout.findViewById(R.id.messagesAdapter_timestamp);
        }

        if (null != rightTsTextLayout) {
            rightTsTextView = (TextView)rightTsTextLayout.findViewById(R.id.messagesAdapter_timestamp);
        }

        if (isAvatarOnRightSide) {
            tsTextView = leftTsTextView;
            if (null != rightTsTextView) {
                rightTsTextView.setVisibility(View.GONE);
            }
        } else {
            tsTextView = rightTsTextView;
            if (null != leftTsTextView) {
                leftTsTextView.setVisibility(View.GONE);
            }
        }

        if (null != tsTextView) {
            String timeStamp = getFormattedTimestamp(event);

            if (TextUtils.isEmpty(timeStamp)) {
                tsTextView.setVisibility(View.GONE);
            } else {
                tsTextView.setVisibility(View.VISIBLE);
                tsTextView.setText(timeStamp);

                tsTextView.setGravity(isAvatarOnRightSide ? Gravity.LEFT : Gravity.RIGHT);
            }

            if (row.getEvent().isUndeliverable()) {
                tsTextView.setTextColor(notSentColor);
            } else {
                tsTextView.setTextColor(mContext.getResources().getColor(R.color.chat_gray_text));
            }
        }

        // read receipts
        LinearLayout leftReceiversLayout = null;
        LinearLayout rightReceiversLayout = null;

        if (null != leftTsTextLayout) {
            leftReceiversLayout = (LinearLayout)leftTsTextLayout.findViewById(R.id.messagesAdapter_receivers_list);

            if (null != leftReceiversLayout) {
                leftReceiversLayout.setVisibility(isAvatarOnRightSide ? View.VISIBLE : View.GONE);
            }
        }

        if (null != rightTsTextLayout) {
            rightReceiversLayout =  (LinearLayout)rightTsTextLayout.findViewById(R.id.messagesAdapter_receivers_list);

            if (null != rightReceiversLayout) {
                rightReceiversLayout.setVisibility(isAvatarOnRightSide ? View.GONE : View.VISIBLE);
            }
        }

        refreshReceiverLayout(isAvatarOnRightSide ? leftReceiversLayout : rightReceiversLayout, isAvatarOnRightSide, event.eventId, roomState);

        // Sender avatar
        RoomMember sender = null;

        if (null != roomState) {
            sender = roomState.getMember(event.getSender());
        }

        View avatarLeftView = convertView.findViewById(R.id.messagesAdapter_roundAvatar_left);
        View avatarRightView = convertView.findViewById(R.id.messagesAdapter_roundAvatar_right);
        View avatarLayoutView = null;

        if (isAvatarOnRightSide) {
            avatarLayoutView = avatarRightView;
            
            if (null != avatarLeftView) {
                avatarLeftView.setVisibility(View.GONE);
            }
        } else {
            avatarLayoutView = avatarLeftView;

            if (null != avatarRightView) {
                avatarRightView.setVisibility(View.GONE);
            }

            if (null != avatarLeftView) {
                final String userId = event.getSender();

                avatarLeftView.setClickable(true);

                avatarLeftView.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        if (null != mMessagesAdapterEventsListener) {
                            return mMessagesAdapterEventsListener.onAvatarLongClick(userId);
                        } else {
                            return false;
                        }
                    }
                });

                // click on the avatar opens the details page
                avatarLeftView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (null != mMessagesAdapterEventsListener) {
                            mMessagesAdapterEventsListener.onAvatarClick(userId);
                        }
                    }
                });
            }
        }

        if (null != avatarLayoutView) {
            ImageView avatarImageView = (ImageView) avatarLayoutView.findViewById(R.id.avatar_img);
            ImageView presenceView = (ImageView) avatarLayoutView.findViewById(R.id.imageView_presenceRing);

            final String userId = event.getSender();
            refreshPresenceRing(presenceView, userId);

            if (isMergedView) {
                avatarLayoutView.setVisibility(View.GONE);
            } else {
                avatarLayoutView.setVisibility(View.VISIBLE);
                avatarImageView.setTag(null);

                String url = null;

                // Check whether this avatar url is updated by the current event (This happens in case of new joined member)
                JsonObject msgContent = event.getContentAsJsonObject();

                if (msgContent.has("avatar_url")) {
                    url = msgContent.get("avatar_url") == JsonNull.INSTANCE ? null : msgContent.get("avatar_url").getAsString();
                }

                loadMemberAvatar(avatarImageView, sender, userId, url);

                // display the typing icon when required
                setTypingVisibility(avatarLayoutView, (!isAvatarOnRightSide && (mTypingUsers.indexOf(event.getSender()) >= 0)) ? View.VISIBLE : View.GONE);
            }
        }

        // if the messages are merged
        // the thumbnail is hidden
        // and the subview must be moved to be aligned with the previous body
        View bodyLayoutView = convertView.findViewById(R.id.messagesAdapter_body_layout);
        ViewGroup.MarginLayoutParams bodyLayout = (ViewGroup.MarginLayoutParams) bodyLayoutView.getLayoutParams();
        FrameLayout.LayoutParams subViewLinearLayout = (FrameLayout.LayoutParams) subView.getLayoutParams();

        View view = convertView.findViewById(R.id.messagesAdapter_roundAvatar_left);
        ViewGroup.LayoutParams avatarLayout = view.getLayoutParams();

        if (!isAvatarOnRightSide) {
            subViewLinearLayout.gravity = Gravity.LEFT | Gravity.CENTER_VERTICAL;

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
            view.setVisibility((willBeMerged || ((position + 1) == this.getCount())) ? View.GONE : View.VISIBLE);
        }


        convertView.setClickable(true);

        // click on the avatar opens the details page
        convertView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (null != mMessagesAdapterEventsListener) {
                    mMessagesAdapterEventsListener.onRowClick(position);
                }
            }
        });

        // click on the avatar opens the details page
        convertView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (null != mMessagesAdapterEventsListener) {
                    return mMessagesAdapterEventsListener.onRowLongClick(position);
                }

                return false;
            }
        });

        return isMergedView;
    }

    /**
     * Add click and long click listener on the content view
     * @param contentView
     */
    protected void addContentViewListeners(View contentView, final int position) {
        contentView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (null != mMessagesAdapterEventsListener) {
                    mMessagesAdapterEventsListener.onContentClick(position);
                }
            }
        });

        contentView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (null != mMessagesAdapterEventsListener) {
                    return mMessagesAdapterEventsListener.onContentLongClick(position);
                }

                return false;
            }
        });
    }

    /**
     * Highlight text style
     */
    protected CharacterStyle getHighLightTextStyle() {
        return new BackgroundColorSpan(searchHighlightColor);
    }

    /**
     * Highlight the pattern in the text.
     * @param textView the textView in which the text is displayed.
     * @param text the text to display.
     * @param pattern the pattern to highlight.
     */
    protected void highlightPattern(TextView textView, Spannable text, String pattern) {
        // sanity check
        if (null == textView) {
            return;
        }

        if (!TextUtils.isEmpty(pattern) && !TextUtils.isEmpty(text) && (text.length() >= pattern.length())) {

            String lowerText = text.toString().toLowerCase();
            String lowerPattern = pattern.toLowerCase();

            int start = 0;
            int pos = lowerText.indexOf(lowerPattern, start);

            while (pos >= 0) {
                start = pos + lowerPattern.length();
                text.setSpan(getHighLightTextStyle(), pos, start, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                text.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), pos, start, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                pos = lowerText.indexOf(lowerPattern, start);
            }
        }

        SpannableStringBuilder strBuilder = new SpannableStringBuilder(text);
        URLSpan[] urls = strBuilder.getSpans(0, text.length(), URLSpan.class);

        if ((null != urls) && (urls.length > 0)) {

            for (URLSpan span : urls) {
                makeLinkClickable(strBuilder, span);
            }
            textView.setText(strBuilder);
            textView.setMovementMethod(LinkMovementMethod.getInstance());
        } else {
            textView.setText(text);
        }
    }

    /**
     * Trap the clicked URL.
     * @param strBuilder the input string
     * @param span the URL
     */
    protected void makeLinkClickable(SpannableStringBuilder strBuilder, final URLSpan span) {
        int start = strBuilder.getSpanStart(span);
        int end = strBuilder.getSpanEnd(span);
        int flags = strBuilder.getSpanFlags(span);

        ClickableSpan clickable = new ClickableSpan() {
            public void onClick(View view) {
                mMessagesAdapterEventsListener.onURLClick(Uri.parse(span.getURL()));
            }
        };
        strBuilder.setSpan(clickable, start, end, flags);
        strBuilder.removeSpan(span);
    }


    /**
     * Text message management
     * @param position the message position
     * @param convertView the text message view
     * @param parent the parent view
     * @return the updated text view.
     */
    protected View getTextView(final int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = mLayoutInflater.inflate(mRowTypeToLayoutId.get(ROW_TYPE_TEXT), parent, false);
        }

        MessageRow row = getItem(position);
        Event event = row.getEvent();
        Message message = JsonUtils.toMessage(event.content);
        RoomState roomState = row.getRoomState();

        EventDisplay display = new EventDisplay(mContext, event, roomState);
        CharSequence textualDisplay = display.getTextualDisplay();

        SpannableString body = new SpannableString((null == textualDisplay) ? "" : textualDisplay);
        final TextView bodyTextView = (TextView) convertView.findViewById(R.id.messagesAdapter_body);

        // cannot refresh it
        if (null == bodyTextView) {
            Log.e(LOG_TAG, "getTextView : invalid layout");
            return convertView;
        }

        if (bodyTextView instanceof HtmlTextView) {
            // some HTML markers are not supported by the android textview
            if (TextUtils.equals("org.matrix.custom.html", message.format)) {
                if (!TextUtils.isEmpty(message.formatted_body)) {
                    HtmlTextView textView = ((HtmlTextView)bodyTextView);
                    textView.setHtmlFromString(message.formatted_body, new HtmlTextView.LocalImageGetter());
                    body = new SpannableString(textView.getText());
                }
            }
        }

        if ((null != mMessagesAdapterEventsListener) && mMessagesAdapterEventsListener.shouldHighlightEvent(event)) {
            body.setSpan(new ForegroundColorSpan(highlightColor), 0, body.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        highlightPattern(bodyTextView, body, mPattern);


        int textColor;

        if (row.getEvent().isSending()) {
            textColor = sendingColor;
        } else if (row.getEvent().isUndeliverable()) {
            textColor = notSentColor;
        } else {
            textColor = normalColor;

            // sanity check
            if (null != event.eventId) {
                synchronized (this) {
                    if (!mTextColorByEventId.containsKey(event.eventId)) {
                        String sBody = body.toString();
                        String displayName = mSession.getMyUser().displayname;
                        String userID = mSession.getMyUserId();

                        if (EventUtils.caseInsensitiveFind(displayName, sBody) || EventUtils.caseInsensitiveFind(userID, sBody)) {
                            textColor = highlightColor;
                        } else {
                            textColor = normalColor;
                        }

                        mTextColorByEventId.put(event.eventId, textColor);
                    } else {
                        textColor = mTextColorByEventId.get(event.eventId);
                    }
                }
            }
        }

        bodyTextView.setTextColor(textColor);

        this.manageSubView(position, convertView, bodyTextView, ROW_TYPE_TEXT);

        addContentViewListeners(bodyTextView, position);

        return convertView;
    }

    /**
     * manage the upload piechart
     * @param convertView the media view
     * @param event teh related event
     * @param mediaUrl the media url
     */
    private void manageUploadView(View convertView, Event event, String mediaUrl) {
        final PieFractionView uploadPieFractionView = (PieFractionView) convertView.findViewById(R.id.content_upload_piechart);

        final ProgressBar uploadSpinner = (ProgressBar) convertView.findViewById(R.id.upload_event_spinner);
        final ImageView uploadFailedImage = (ImageView) convertView.findViewById(R.id.upload_event_failed);

        // the dedicated UI items are not found
        if ((null == uploadPieFractionView) || (null == uploadSpinner) || (null == uploadFailedImage)) {
            return;
        }

        // refresh the pie chart only if it is the expected URL
        uploadPieFractionView.setTag(mediaUrl);

        // no upload in progress
        if (!mSession.getMyUserId().equals(event.getSender()) || !event.isSending()) {
            uploadPieFractionView.setVisibility(View.GONE);
            uploadSpinner.setVisibility(View.GONE);
            uploadFailedImage.setVisibility(event.isUndeliverable() ? View.VISIBLE : View.GONE);
            return;
        }

        int progress = mSession.getContentManager().getUploadProgress(mediaUrl);

        if (progress >= 0) {
            mSession.getContentManager().addUploadListener(mediaUrl, new ContentManager.UploadCallback() {
                @Override
                public void onUploadStart(String uploadId) {
                }

                @Override
                public void onUploadProgress(String anUploadId, int percentageProgress) {
                    if (TextUtils.equals((String)uploadPieFractionView.getTag(), anUploadId)) {
                        uploadPieFractionView.setFraction(percentageProgress);
                    }
                }

                @Override
                public void onUploadComplete(final String anUploadId, final ContentResponse uploadResponse, final int serverResponseCode, final String serverErrorMessage) {
                    if (TextUtils.equals((String)uploadPieFractionView.getTag(), anUploadId)) {
                        if ((null == uploadResponse) || (null == uploadResponse.contentUri)) {
                            if (null != serverErrorMessage) {
                                Toast.makeText(MessagesAdapter.this.getContext(),
                                        serverErrorMessage,
                                        Toast.LENGTH_LONG).show();
                            }
                            uploadFailedImage.setVisibility(View.VISIBLE);
                        } else {
                            uploadSpinner.setVisibility(View.VISIBLE);
                        }

                        uploadPieFractionView.setVisibility(View.GONE);
                    }
                }
            });
        }
        uploadFailedImage.setVisibility(View.GONE);
        uploadSpinner.setVisibility((progress < 0) ? View.VISIBLE : View.GONE);
        uploadPieFractionView.setVisibility((progress >= 0) ? View.VISIBLE : View.GONE);
        uploadPieFractionView.setFraction(progress);
    }

    /**
     * Manage the image/video download.
     * It displays the pie chart when it is required.
     * @param convertView the parent view.
     * @param message the image / video message
     * @param position the message position
     */
    protected void manageImageVideoDownload(final View convertView, final Message message, final int position) {
        int maxImageWidth = mMaxImageWidth;
        int maxImageHeight = mMaxImageHeight;
        int rotationAngle = 0;
        int orientation = ExifInterface.ORIENTATION_NORMAL;
        String thumbUrl = null;
        int thumbWidth = -1;
        int thumbHeight = -1;

        // retrieve the common items
        if (message instanceof ImageMessage) {
            ImageMessage imageMessage = (ImageMessage)message;
            imageMessage.checkMediaUrls();

            // Backwards compatibility with events from before Synapse 0.6.0
            if (imageMessage.thumbnailUrl != null) {
                thumbUrl = imageMessage.thumbnailUrl;
            } else if (imageMessage.url != null) {
                thumbUrl = imageMessage.url;
            }

            rotationAngle = imageMessage.getRotation();

            ImageInfo imageInfo  = imageMessage.info;

            if (null != imageInfo) {
                if ((null != imageInfo.w) && (null != imageInfo.h)) {
                    thumbWidth = imageInfo.w;
                    thumbHeight = imageInfo.h;
                }

                if (null != imageInfo.orientation) {
                    orientation = imageInfo.orientation;
                }
            }
        } else { // video
            VideoMessage videoMessage = (VideoMessage) message;
            videoMessage.checkMediaUrls();

            VideoInfo videoinfo = videoMessage.info;

            if (null != videoinfo) {
                thumbUrl = videoinfo.thumbnail_url;

                if ((null != videoMessage.info.thumbnail_info) && (null != videoMessage.info.thumbnail_info.w) && (null != videoMessage.info.thumbnail_info.h)) {
                    thumbWidth = videoMessage.info.thumbnail_info.w;
                    thumbHeight = videoMessage.info.thumbnail_info.h;
                }
            }
        }

        ImageView imageView = (ImageView) convertView.findViewById(R.id.messagesAdapter_image);

        // reset the bitmap
        imageView.setImageBitmap(null);

        RelativeLayout informationLayout = (RelativeLayout) convertView.findViewById(R.id.messagesAdapter_image_layout);
        final FrameLayout.LayoutParams LayoutParams = (FrameLayout.LayoutParams) informationLayout.getLayoutParams();

        // the thumbnails are always pre - rotated
        String downloadId = mMediasCache.loadBitmap(mSession.getHomeserverConfig(), imageView, thumbUrl, maxImageWidth, maxImageHeight, rotationAngle, ExifInterface.ORIENTATION_UNDEFINED, "image/jpeg");

        // for a video check if the media is downloading if there is no thumbnail downnload
        if ((null == downloadId) && (message instanceof VideoMessage)) {
            downloadId = mMediasCache.downloadIdFromUrl(((VideoMessage)message).url);
            // check the progress value
            // display the piechart only if the video is downloading
            if (mMediasCache.progressValueForDownloadId(downloadId) < 0) {
                downloadId = null;
            }
        }

        final PieFractionView downloadPieFractionView = (PieFractionView) convertView.findViewById(R.id.content_download_piechart);

        // the tag is used to detect if the progress value is destinated to this piechart.
        downloadPieFractionView.setTag(downloadId);

        // no download in progress
        if (null != downloadId) {

            downloadPieFractionView.setVisibility(View.VISIBLE);

            int frameHeight = -1;
            int frameWidth = -1;

            // if the image size is known
            // compute the expected thumbnail height
            if ((thumbWidth > 0) && (thumbHeight > 0)) {

                // swap width and height if the image is side oriented
                if ((rotationAngle == 90) || (rotationAngle == 270)) {
                    int tmp = thumbWidth;
                    thumbWidth = thumbHeight;
                    thumbHeight = tmp;
                } else if ((orientation == ExifInterface.ORIENTATION_ROTATE_90) || (orientation == ExifInterface.ORIENTATION_ROTATE_270)) {
                    int tmp = thumbWidth;
                    thumbWidth = thumbHeight;
                    thumbHeight = tmp;
                }

                frameHeight = Math.min(maxImageWidth * thumbHeight / thumbWidth, maxImageHeight);
                frameWidth  = frameHeight * thumbWidth / thumbHeight;
            }

            // ensure that some values are properly initialized
            if (frameHeight < 0) {
                frameHeight = mMaxImageHeight;
            }

            if (frameWidth < 0) {
                frameWidth = mMaxImageWidth;
            }

            // apply it the layout
            // it avoid row jumping when the image is downloaded
            LayoutParams.height = frameHeight;
            LayoutParams.width = frameWidth;

            mMediasCache.addDownloadListener(downloadId, new MXMediasCache.DownloadCallback() {
                @Override
                public void onDownloadStart(String downloadId) {
                }

                @Override
                public void onError(String downloadId, JsonElement jsonElement) {
                    final MatrixError error = JsonUtils.toMatrixError(jsonElement);

                    if ((null != error) && error.isSupportedErrorCode()) {
                        Toast.makeText(MessagesAdapter.this.getContext(), error.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                    }
                }

                @Override
                public void onDownloadProgress(String aDownloadId, int percentageProgress) {
                    if (TextUtils.equals(aDownloadId, (String)downloadPieFractionView.getTag())) {
                        downloadPieFractionView.setFraction(percentageProgress);
                    }
                }

                @Override
                public void onDownloadComplete(String aDownloadId) {
                    if (TextUtils.equals(aDownloadId, (String)downloadPieFractionView.getTag())) {
                        downloadPieFractionView.setVisibility(View.GONE);

                        LayoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                        LayoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT;

                        downloadPieFractionView.setVisibility(View.GONE);

                        if (null != mMessagesAdapterEventsListener) {
                            mMessagesAdapterEventsListener.onMediaDownloaded(position);
                        }
                    }
                }
            });

            downloadPieFractionView.setFraction(mMediasCache.progressValueForDownloadId(downloadId));
        } else {
            LayoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            LayoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT;

            downloadPieFractionView.setVisibility(View.GONE);
        }

        // The API doesn't make any strong guarantees about the thumbnail size, so also scale
        // locally if needed.
        imageView.setMaxWidth(mMaxImageWidth);
        imageView.setMaxHeight(mMaxImageHeight);
        imageView.setBackgroundColor(Color.TRANSPARENT);
    }

    /**
     * Image / Video  message management
     * @param type ROW_TYPE_IMAGE or ROW_TYPE_VIDEO
     * @param position the message position
     * @param convertView the message view
     * @param parent the parent view
     * @return the updated text view.
     */
    protected View getImageVideoView(int type, final int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = mLayoutInflater.inflate(mRowTypeToLayoutId.get(type), parent, false);
        }

        MessageRow row = getItem(position);
        Event msg = row.getEvent();

        Message message;
        int waterMarkResourceId = -1;

        if (type == ROW_TYPE_IMAGE) {
            ImageMessage imageMessage = JsonUtils.toImageMessage(msg.content);

            if ("image/gif".equals(imageMessage.getMimeType())) {
                waterMarkResourceId = R.drawable.filetype_gif;
            }
            message = imageMessage;

        } else {
            message = JsonUtils.toVideoMessage(msg.content);
            waterMarkResourceId = R.drawable.filetype_video;
        }

        // display a type watermark
        final ImageView imageTypeView = (ImageView) convertView.findViewById(R.id.messagesAdapter_image_type);

        if (null == imageTypeView) {
            Log.e(LOG_TAG, "getImageVideoView : invalid layout");
            return convertView;
        }

        imageTypeView.setBackgroundColor(Color.TRANSPARENT);

        if (waterMarkResourceId > 0) {
            imageTypeView.setImageBitmap(BitmapFactory.decodeResource(getContext().getResources(), waterMarkResourceId));
            imageTypeView.setVisibility(View.VISIBLE);
        } else {
            imageTypeView.setVisibility(View.GONE);
        }

        // download management
        manageImageVideoDownload(convertView, message, position);

        // upload management
        if (type == ROW_TYPE_IMAGE) {
            manageUploadView(convertView, msg, ((ImageMessage)message).url);
        } else {
            manageVideoUpload(convertView, msg, (VideoMessage) message);
        }

        // dimmed when the message is not sent
        View imageLayout =  convertView.findViewById(R.id.messagesAdapter_image_layout);
        imageLayout.setAlpha(msg.isSent() ? 1.0f : 0.5f);

        this.manageSubView(position, convertView, imageLayout, type);

        ImageView imageView = (ImageView) convertView.findViewById(R.id.messagesAdapter_image);
        addContentViewListeners(imageView, position);

        return convertView;
    }

    /**
     * Notice message management
     * @param position the message position
     * @param convertView the message view
     * @param parent the parent view
     * @return the updated text view.
     */
    protected View getNoticeView(final int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = mLayoutInflater.inflate(mRowTypeToLayoutId.get(ROW_TYPE_NOTICE), parent, false);
        }

        MessageRow row = getItem(position);
        Event msg = row.getEvent();
        RoomState roomState = row.getRoomState();

        CharSequence notice;

        if (TextUtils.equals(msg.type, Event.EVENT_TYPE_CALL_INVITE)) {
            notice = msg.getSender().equals(mSession.getCredentials().userId) ? mContext.getResources().getString(R.string.notice_outgoing_call) : mContext.getResources().getString(R.string.notice_incoming_call);
        } else {
            EventDisplay display = new EventDisplay(mContext, msg, roomState);
            notice = display.getTextualDisplay();
        }

        TextView noticeTextView = (TextView) convertView.findViewById(R.id.messagesAdapter_body);

        if (null == noticeTextView) {
            Log.e(LOG_TAG, "getNoticeView : invalid layout");
            return convertView;
        }

        noticeTextView.setText(notice);

        this.manageSubView(position, convertView, noticeTextView, ROW_TYPE_NOTICE);

        addContentViewListeners(noticeTextView, position);

        noticeTextView.setAlpha(0.6f);

        return convertView;
    }

    /**
     * Emote message management
     * @param position the message position
     * @param convertView the message view
     * @param parent the parent view
     * @return the updated text view.
     */
    protected View getEmoteView(final int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = mLayoutInflater.inflate(mRowTypeToLayoutId.get(ROW_TYPE_EMOTE), parent, false);
        }

        MessageRow row = getItem(position);
        Event msg = row.getEvent();
        RoomState roomState = row.getRoomState();

        EventDisplay display = new EventDisplay(mContext, msg, roomState);

        TextView emoteTextView = (TextView) convertView.findViewById(R.id.messagesAdapter_body);

        if (null == emoteTextView) {
            Log.e(LOG_TAG, "getEmoteView : invalid layout");
            return convertView;
        }

        emoteTextView.setText(display.getTextualDisplay());

        int textColor;

        if (row.getEvent().isSending()) {
            textColor = sendingColor;
        } else if (row.getEvent().isUndeliverable()) {
            textColor = notSentColor;
        } else {
            textColor = normalColor;
        }

        emoteTextView.setTextColor(textColor);

        this.manageSubView(position, convertView, emoteTextView, ROW_TYPE_EMOTE);

        addContentViewListeners(emoteTextView, position);

        return convertView;
    }

    /**
     * Manage the file download items.
     * i.e. the piechart while downloading the file
     * @param convertView
     * @param fileMessage
     * @param position
     */
    protected void manageFileDownload(View convertView, FileMessage fileMessage, final int position) {
        String downloadId = mMediasCache.downloadIdFromUrl(fileMessage.url);

        // check the progress value
        // display the piechart only if the file is downloading
        if (mMediasCache.progressValueForDownloadId(downloadId) < 0) {
            downloadId = null;
        }

        final PieFractionView downloadPieFractionView = (PieFractionView) convertView.findViewById(R.id.content_download_piechart);
        downloadPieFractionView.setTag(downloadId);

        // no download in progress
        if (null != downloadId) {
            downloadPieFractionView.setVisibility(View.VISIBLE);

            mMediasCache.addDownloadListener(downloadId, new MXMediasCache.DownloadCallback() {
                @Override
                public void onDownloadStart(String downloadId) {
                }

                @Override
                public void onError(String downloadId, JsonElement jsonElement) {
                    final MatrixError error = JsonUtils.toMatrixError(jsonElement);

                    if ((null != error) && error.isSupportedErrorCode()) {
                        Toast.makeText(MessagesAdapter.this.getContext(), error.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                    }
                }

                @Override
                public void onDownloadProgress(String aDownloadId, int percentageProgress) {
                    if (TextUtils.equals(aDownloadId, (String)downloadPieFractionView.getTag())) {
                        downloadPieFractionView.setFraction(percentageProgress);
                    }
                }

                @Override
                public void onDownloadComplete(String aDownloadId) {
                    if (TextUtils.equals(aDownloadId, (String)downloadPieFractionView.getTag())) {
                        downloadPieFractionView.setVisibility(View.GONE);

                        if (null != mMessagesAdapterEventsListener) {
                            mMessagesAdapterEventsListener.onMediaDownloaded(position);
                        }
                    }
                }
            });

            downloadPieFractionView.setFraction(mMediasCache.progressValueForDownloadId(downloadId));
        } else {
            downloadPieFractionView.setVisibility(View.GONE);
        }
    }

    /**
     * File message management
     * @param position the message position
     * @param convertView the message view
     * @param parent the parent view
     * @return the updated text view.
     */
    protected View getFileView(final int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = mLayoutInflater.inflate(mRowTypeToLayoutId.get(ROW_TYPE_FILE), parent, false);
        }

        MessageRow row = getItem(position);
        Event msg = row.getEvent();

        final FileMessage fileMessage = JsonUtils.toFileMessage(msg.content);
        final TextView fileTextView = (TextView) convertView.findViewById(R.id.messagesAdapter_filename);

        if (null == fileTextView) {
            Log.e(LOG_TAG, "getFileView : invalid layout");
            return convertView;
        }

        fileTextView.setPaintFlags(fileTextView.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        fileTextView.setText("\n" + fileMessage.body + "\n");

        manageFileDownload(convertView, fileMessage, position);
        manageUploadView(convertView, msg, fileMessage.url);

        View fileLayout =  convertView.findViewById(R.id.messagesAdapter_file_layout);
        this.manageSubView(position, convertView, fileLayout, ROW_TYPE_FILE);

        addContentViewListeners(fileTextView, position);

        return convertView;
    }

    /**
     * Manage the video upload
     * @param convertView the base view
     * @param videoEvent the video event
     * @param videoMessage the video message
     */
    protected void manageVideoUpload(View convertView, Event videoEvent, VideoMessage videoMessage) {
        final PieFractionView uploadPieFractionView = (PieFractionView) convertView.findViewById(R.id.content_upload_piechart);
        final ProgressBar uploadSpinner = (ProgressBar) convertView.findViewById(R.id.upload_event_spinner);
        final ImageView uploadFailedImage = (ImageView) convertView.findViewById(R.id.upload_event_failed);

        // the dedicated UI items are not found
        if ((null == uploadPieFractionView) || (null == uploadSpinner) || (null == uploadFailedImage)) {
            return;
        }

        // refresh the piechart only if it is the expected URL
        uploadPieFractionView.setTag(null);

        // not the sender ?
        if (!mSession.getMyUserId().equals(videoEvent.getSender()) || videoEvent.isUndeliverable() || (null == videoMessage.info)) {
            uploadPieFractionView.setVisibility(View.GONE);
            uploadSpinner.setVisibility(View.GONE);
            uploadFailedImage.setVisibility(videoEvent.isUndeliverable() ? View.VISIBLE : View.GONE);
            return;
        }

        String uploadingUrl = videoMessage.info.thumbnail_url;

        int progress = mSession.getContentManager().getUploadProgress(uploadingUrl);

        // the thumbnail has been uploaded, upload the video
        if (progress < 0) {
            uploadingUrl = videoMessage.url;
            progress = mSession.getContentManager().getUploadProgress(uploadingUrl);
        }

        if (progress >= 0) {
            uploadPieFractionView.setTag(uploadingUrl);

            final boolean isContentUpload = TextUtils.equals(uploadingUrl, videoMessage.url);

            mSession.getContentManager().addUploadListener(uploadingUrl, new ContentManager.UploadCallback() {
                @Override
                public void onUploadStart(String uploadId) {

                }

                @Override
                public void onUploadProgress(String anUploadId, int percentageProgress) {
                    if (TextUtils.equals((String)uploadPieFractionView.getTag(), anUploadId)) {
                        int progress;

                        if (isContentUpload) {
                            progress = 10 + (percentageProgress * 90 / 100);
                        } else {
                            progress = (percentageProgress * 10 / 100);
                        }

                        uploadPieFractionView.setFraction(progress);
                    }
                }

                @Override
                public void onUploadComplete(final String anUploadId, final ContentResponse uploadResponse, final int serverResponseCode, final String serverErrorMessage) {
                    if (TextUtils.equals((String)uploadPieFractionView.getTag(), anUploadId)) {
                        if ((null == uploadResponse) || (null == uploadResponse.contentUri)) {
                            if (null != serverErrorMessage) {
                                Toast.makeText(MessagesAdapter.this.getContext(),
                                        serverErrorMessage,
                                        Toast.LENGTH_LONG).show();
                            }
                            uploadFailedImage.setVisibility(View.VISIBLE);
                        } else {
                            uploadSpinner.setVisibility(View.VISIBLE);
                        }

                        uploadSpinner.setVisibility(View.GONE);
                    }
                }
            });
        }

        uploadFailedImage.setVisibility(View.GONE);
        uploadSpinner.setVisibility(((progress < 0) && videoEvent.isSending()) ? View.VISIBLE : View.GONE);
        uploadPieFractionView.setVisibility(((progress >= 0) && videoEvent.isSending()) ? View.VISIBLE : View.GONE);
        uploadPieFractionView.setFraction(progress);
    }

    /**
     * Check if an event should be added to the events list.
     * @param event the event to check.
     * @param roomState the rooms state
     * @return true if the event is managed.
     */
    protected boolean isDisplayableEvent(Event event, RoomState roomState) {
        if (Event.EVENT_TYPE_MESSAGE.equals(event.type)) {
            // A message is displayable as long as it has a body
            Message message = JsonUtils.toMessage(event.content);
            return (message.body != null) && (!message.body.equals(""));
        }
        else if (Event.EVENT_TYPE_STATE_ROOM_TOPIC.equals(event.type)
                || Event.EVENT_TYPE_STATE_ROOM_NAME.equals(event.type)) {
            return true;
        }
        else if (event.isCallEvent()) {
            // display only the start call
            return Event.EVENT_TYPE_CALL_INVITE.equals(event.type);
        }
        else if (Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(event.type) || Event.EVENT_TYPE_STATE_ROOM_THIRD_PARTY_INVITE.equals(event.type)) {
            // if we can display text for it, it's valid.
            EventDisplay display = new EventDisplay(mContext, event, roomState);
            return display.getTextualDisplay() != null;
        }
        return false;
    }

    /**
     * Call when there are some updates in the typing users list.
     */
    protected void onTypingUsersUpdate() {
        notifyDataSetChanged();
    }

    /**
     * Update the typing users list
     * @param typingUsers
     */
    public void setTypingUsers(ArrayList<String> typingUsers) {
        // sanity checks
        if (null != mTypingUsers) {
            // avoid null case.
            if (null == typingUsers) {
                typingUsers = new ArrayList<String>();
            }

            boolean refresh = mTypingUsers.size() != typingUsers.size();

            if (mTypingUsers.size() == 1) {
                // avoid refreshing when the self user is alone
                String userId = mTypingUsers.get(0);

                if (TextUtils.equals(userId, mSession.getMyUserId())) {
                    mTypingUsers = typingUsers;
                    return;
                }
            }

            // same length -> ensure that there is an update
            if (!refresh) {
                // do not refresh if the both lists empty
                if (mTypingUsers.size() != 0) {
                    for (String userId : mTypingUsers) {
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
                onTypingUsersUpdate();
            }
        }
    }

    /**
     * Define the events listener
     * @param listener teh events listener
     */
    public void setMessagesAdapterEventsListener(MessagesAdapterEventsListener listener) {
        mMessagesAdapterEventsListener = listener;
    }

    /**
     * @return the max thumbnail width
     */
    public int getMaxThumbnailWith() {
        return mMaxImageWidth;
    }

    /**
     * @return the max thumbnail height
     */
    public int getMaxThumbnailHeight() {
        return mMaxImageHeight;
    }

    /**
     * Notify the fragment that some bing rules could have been updated.
     */
    public void onBingRulesUpdate() {
        synchronized (this) {
            mTextColorByEventId = new HashMap<String, Integer>();
        }
        this.notifyDataSetChanged();
    }

    /**
     * @return true if the user has sent some messages in this room history.
     */
    public boolean isDisplayedUser(String userId) {
        // check if the user has been displayed in the room history
        return (null != userId) && mUserByUserId.containsKey(userId);
    }
}
