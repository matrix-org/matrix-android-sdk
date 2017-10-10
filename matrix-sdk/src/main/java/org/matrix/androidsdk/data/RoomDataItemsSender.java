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

import android.content.ClipDescription;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.HandlerThread;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Html;
import android.text.TextUtils;

import org.matrix.androidsdk.MXDataHandler;
import org.matrix.androidsdk.crypto.MXEncryptedAttachments;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.listeners.MXMediaUploadListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.AudioMessage;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.FileMessage;
import org.matrix.androidsdk.rest.model.ImageMessage;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.MediaMessage;
import org.matrix.androidsdk.rest.model.Message;
import org.matrix.androidsdk.rest.model.VideoMessage;
import org.matrix.androidsdk.util.ImageUtils;
import org.matrix.androidsdk.util.JsonUtils;
import org.matrix.androidsdk.util.Log;
import org.matrix.androidsdk.util.MXOsHandler;
import org.matrix.androidsdk.util.ResourceUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Room helper to send messages in the right order.
 */
class RoomDataItemsSender {
    private static final String LOG_TAG = RoomDataItemsSender.class.getSimpleName();

    // pending events list
    private final List<RoomDataItem> mPendingItems = new ArrayList<>();

    // linked room
    private final Room mRoom;

    private final MXDataHandler mDataHandler;

    // linked context
    private final Context mContext;

    // the sending item
    private RoomDataItem mSendingItem;

    private static android.os.Handler mUiHandler = null;

    // dedicated threads
    private static HandlerThread mEventHandlerThread = null;
    private static android.os.Handler mEventHandler = null;

    private static HandlerThread mEncodingHandlerThread = null;
    private static android.os.Handler mEncodingHandler = null;

    /**
     * Constructor
     * @param context the context
     * @param room the room
     */
    RoomDataItemsSender(Context context, MXDataHandler dataHandler, Room room) {
        mRoom = room;
        mContext = context;
        mDataHandler = dataHandler;

        if (null == mUiHandler) {
            mUiHandler = new android.os.Handler(Looper.getMainLooper());

            mEventHandlerThread = new HandlerThread("RoomDataItemsSender_event", Thread.MIN_PRIORITY);
            mEventHandlerThread.start();
            mEventHandler = new android.os.Handler(mEventHandlerThread.getLooper());

            mEncodingHandlerThread = new HandlerThread("RoomDataItemsSender_encoding", Thread.MIN_PRIORITY);
            mEncodingHandlerThread.start();
            mEncodingHandler = new android.os.Handler(mEncodingHandlerThread.getLooper());
        }
    }

    /**
     * Send a new item message in the room
     * @param item the item to send
     */
    void send(final RoomDataItem item) {
        mEventHandler.post(new Runnable() {
            @Override
            public void run() {
                if (null == item.getEvent()) {
                    Message message = null;
                    String mimeType = item.getMimeType(mContext);

                    // avoid null case
                    if (null == mimeType) {
                        mimeType = "";
                    }

                    if ((null == item.getUri()) && (TextUtils.equals(ClipDescription.MIMETYPE_TEXT_PLAIN, mimeType) || TextUtils.equals(ClipDescription.MIMETYPE_TEXT_HTML, mimeType))) {
                        message = buildTextMessage(item);
                    } else if (mimeType.startsWith("image/")) {
                        message = buildImageMessage(item);
                    } else if (mimeType.startsWith("video/")) {
                        message = buildVideoMessage(item);
                    } else {
                        message = buildFileMessage(item);
                    }

                    if (null == message) {
                        Log.e(LOG_TAG, "## send " + item + " not supported");
                        return;
                    }

                    item.setMessageType(message.msgtype);

                    Event event = new Event(message, mDataHandler.getUserId(), mRoom.getRoomId());
                    item.setEvent(event);
                }

                item.getEvent().mSentState = Event.SentState.UNSENT;
                mRoom.storeOutgoingEvent(item.getEvent());
                mDataHandler.getStore().commit();

                if (null != item.getRoomDataItemListener()) {
                    mUiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            item.getRoomDataItemListener().onEventCreated(item);
                        }
                    });
                }

                synchronized (LOG_TAG) {
                    if (!mPendingItems.contains(item)) {
                        mPendingItems.add(item);
                    }
                }

                mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        // send the item
                        sendNext();
                    }
                });
            }
        });
    }

    private void skip() {
        synchronized (LOG_TAG) {
            mSendingItem = null;
        }

        sendNext();
    }

    /**
     * Send the next pending item
     */
    private void sendNext() {
        RoomDataItem dataItem;

        synchronized (LOG_TAG) {
            // please wait

            if (null != mSendingItem) {
                return;
            }

            if (!mPendingItems.isEmpty()) {
                mSendingItem = mPendingItems.get(0);
                mPendingItems.remove(0);
            } else {
                // nothing to do
                return;
            }

            dataItem = mSendingItem;
        }

        if (uploadMedias(dataItem)) {
            return;
        }

        //
        sendEvent(dataItem.getEvent());
    }

    private void sendEvent(final Event event) {
        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                // nothing more to upload
                mRoom.sendEvent(event, new ApiCallback<Void>() {
                    private ApiCallback<Void> getCallback() {
                        ApiCallback<Void> callback;

                        synchronized (LOG_TAG) {
                            callback = mSendingItem.getSendingCallback();
                            mSendingItem = null;
                        }

                        return callback;
                    }

                    @Override
                    public void onSuccess(Void info) {
                        ApiCallback<Void> callback = getCallback();

                        if (null != callback) {
                            try {
                                callback.onSuccess(null);
                            } catch (Exception e) {
                                Log.e(LOG_TAG, "## sendNext() failed " + e.getMessage());
                            }
                        }

                        sendNext();
                    }

                    @Override
                    public void onNetworkError(Exception e) {
                        ApiCallback<Void> callback = getCallback();

                        if (null != callback) {
                            try {
                                callback.onNetworkError(e);
                            } catch (Exception e2) {
                                Log.e(LOG_TAG, "## sendNext() failed " + e2.getMessage());
                            }
                        }

                        sendNext();
                    }

                    @Override
                    public void onMatrixError(MatrixError e) {
                        ApiCallback<Void> callback = getCallback();

                        if (null != callback) {
                            try {
                                callback.onMatrixError(e);
                            } catch (Exception e2) {
                                Log.e(LOG_TAG, "## sendNext() failed " + e2.getMessage());
                            }
                        }

                        sendNext();
                    }

                    @Override
                    public void onUnexpectedError(Exception e) {
                        ApiCallback<Void> callback = getCallback();

                        if (null != callback) {
                            try {
                                callback.onUnexpectedError(e);
                            } catch (Exception e2) {
                                Log.e(LOG_TAG, "## sendNext() failed " + e2.getMessage());
                            }
                        }

                        sendNext();
                    }
                });
            }
        });
    }


    private Message buildTextMessage(RoomDataItem item) {
        CharSequence sequence = item.getText();
        String htmlText = item.getHtmlText();
        String text = null;

        if (null == sequence) {
            if (null != htmlText) {
                text = Html.fromHtml(htmlText).toString();
            }
        } else {
            text = sequence.toString();
        }

        Message message = new Message();
        message.msgtype = (null == item.getMessageType()) ? Message.MSGTYPE_TEXT : item.getMessageType();
        message.body = text;

        if (!TextUtils.isEmpty(htmlText)) {
            message.formatted_body = htmlText;
            message.format = Message.FORMAT_MATRIX_HTML;
        }

        return message;
    }

    /**
     * Returns the thumbnail path of shot image.
     * @param picturePath the image path
     * @return the thumbnail image path.
     */
    private static String getThumbnailPath(String picturePath) {
        if (!TextUtils.isEmpty(picturePath) && picturePath.endsWith(".jpg")) {
            return picturePath.replace(".jpg", "_thumb.jpg");
        }

        return null;
    }

    /**
     * Retrieves the image thumbnail saved by the medias picker.
     * @param sharedDataItem the sharedItem
     * @return the thumbnail if it exits.
     */
    private Bitmap getMediasPickerThumbnail(RoomDataItem sharedDataItem) {
        Bitmap thumbnailBitmap = null;

        try {
            String thumbPath = getThumbnailPath(sharedDataItem.getUri().getPath());

            if (null != thumbPath) {
                File thumbFile = new File(thumbPath);

                if (thumbFile.exists()) {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                    thumbnailBitmap = BitmapFactory.decodeFile(thumbPath, options);
                }
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "cannot restore the medias picker thumbnail " + e.getMessage());
        } catch (OutOfMemoryError oom) {
            Log.e(LOG_TAG, "cannot restore the medias picker thumbnail oom");
        }

        return thumbnailBitmap;
    }

    private Message buildImageMessage(RoomDataItem item) {
        final MXMediasCache mediasCache = mDataHandler.getMediasCache();
        String mimeType = item.getMimeType(mContext);

        ResourceUtils.Resource resource = ResourceUtils.openResource(mContext, item.getUri(), mimeType);

        // save the file in the filesystem
        String mediaUrl = mediasCache.saveMedia(resource.mContentStream, null, mimeType);
        resource.close();

        // compute the thumbnail
        Bitmap thumbnailBitmap = item.getFullScreenImageKindThumbnail(mContext);

        if (null == thumbnailBitmap) {
            thumbnailBitmap = getMediasPickerThumbnail(item);
        }

        if ((null == thumbnailBitmap)
                && item.containsCustomObject(RoomDataItem.MAX_THUMBNAIL_HEIGHT_KEY)
                && item.containsCustomObject(RoomDataItem.MAX_THUMBNAIL_WIDTH_KEY)) {
            int maxThumbnailWidth = (int) item.getCustomObject(RoomDataItem.MAX_THUMBNAIL_WIDTH_KEY);
            int maxThumbnailHeight = (int) item.getCustomObject(RoomDataItem.MAX_THUMBNAIL_HEIGHT_KEY);
            thumbnailBitmap = ResourceUtils.createThumbnailBitmap(mContext, item.getUri(), maxThumbnailWidth, maxThumbnailHeight);
        }

        if (null == thumbnailBitmap) {
            thumbnailBitmap = item.getMiniKindImageThumbnail(mContext);
        }

        String thumbnailURL = null;

        if (null != thumbnailBitmap) {
            thumbnailURL = mediasCache.saveBitmap(thumbnailBitmap, null);
        }

        // get the exif rotation angle
        final int rotationAngle = ImageUtils.getRotationAngleForBitmap(mContext, Uri.parse(mediaUrl));

        if (0 != rotationAngle) {
            // always apply the rotation to the image
            ImageUtils.rotateImage(mContext, thumbnailURL, rotationAngle, mediasCache);
        }

        ImageMessage imageMessage = new ImageMessage();
        imageMessage.url = mediaUrl;
        imageMessage.thumbnailUrl = thumbnailURL;
        imageMessage.body = item.getFileName(mContext);

        if (TextUtils.isEmpty(imageMessage.body)) {
            imageMessage.body = "Image";
        }

        Uri imageUri = Uri.parse(mediaUrl);

        if (null == imageMessage.info) {
            Room.fillImageInfo(mContext, imageMessage, imageUri, mimeType);
        }

        if ((null != thumbnailURL) && (null == imageMessage.thumbnailInfo)) {
            Uri thumbUri = Uri.parse(thumbnailURL);
            Room.fillThumbnailInfo(mContext, imageMessage, thumbUri, "image/jpeg");
        }

        return imageMessage;
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
            thumbUrl = mDataHandler.getMediasCache().saveBitmap(thumb, null);
        } catch (Exception e) {
            Log.e(LOG_TAG, "getVideoThumbailUrl failed with " + e.getMessage());
        }

        return thumbUrl;
    }

    private Message buildVideoMessage(RoomDataItem item) {
        final MXMediasCache mediasCache = mDataHandler.getMediasCache();
        String mimeType = item.getMimeType(mContext);

        ResourceUtils.Resource resource = ResourceUtils.openResource(mContext, item.getUri(), mimeType);

        // save the file in the filesystem
        String mediaUrl = mediasCache.saveMedia(resource.mContentStream, null, mimeType);
        resource.close();

        String thumbnailUrl = getVideoThumbnailUrl(mediaUrl);

        if (null == thumbnailUrl) {
            return buildFileMessage(item);
        }

        VideoMessage videoMessage = new VideoMessage();
        videoMessage.url = mediaUrl;
        videoMessage.body = item.getFileName(mContext);

        try {
            Uri videoUri = Uri.parse(mediaUrl);
            Uri thumbnailUri = (null != thumbnailUrl) ? Uri.parse(thumbnailUrl) : null;
            Room.fillVideoInfo(mContext, videoMessage, videoUri, item.getMimeType(mContext), thumbnailUri, "image/jpeg");

            if (null == videoMessage.body) {
                videoMessage.body = videoUri.getLastPathSegment();
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "uploadVideoContent : fillVideoInfo failed " + e.getMessage());
        }

        return videoMessage;
    }

    private Message buildFileMessage(RoomDataItem item) {
        final MXMediasCache mediasCache = mDataHandler.getMediasCache();
        String mimeType = item.getMimeType(mContext);

        ResourceUtils.Resource resource = ResourceUtils.openResource(mContext, item.getUri(), mimeType);

        // save the file in the filesystem
        String mediaUrl = mediasCache.saveMedia(resource.mContentStream, null, mimeType);
        resource.close();

        FileMessage fileMessage;

        if (mimeType.startsWith("audio/")) {
            fileMessage = new AudioMessage();
        } else {
            fileMessage = new FileMessage();
        }

        fileMessage.url = mediaUrl;
        fileMessage.body = item.getFileName(mContext);

        try {
            Uri uri = Uri.parse(mediaUrl);
            Room.fillFileInfo(mContext, fileMessage, uri, mimeType);

            if (null == fileMessage.body) {
                fileMessage.body = uri.getLastPathSegment();
            }

        } catch (Exception e) {
            Log.e(LOG_TAG, "buildFileMessage failed with " + e.getMessage());
        }

        return fileMessage;
    }


    private boolean uploadMedias(final RoomDataItem dataItem) {
        final Event event = dataItem.getEvent();
        final Message message = JsonUtils.toMessage(event.getContent());

        if (!(message instanceof MediaMessage)) {
            return false;
        }

        final MediaMessage mediaMessage = (MediaMessage)message;
        final String url;
        final String fMimeType;

        if (mediaMessage.isThumbnailLocalContent()) {
            url = mediaMessage.getThumbnailUrl();
            fMimeType = "image/jpeg";
        } else if (mediaMessage.isLocalContent()) {
            url = mediaMessage.getUrl();
            fMimeType = mediaMessage.getMimeType();
        } else {
            return false;
        }

        mEncodingHandler.post(new Runnable() {
            @Override
            public void run() {
                Uri uri = Uri.parse(url);
                String mimeType = fMimeType;

                final MXEncryptedAttachments.EncryptionResult encryptionResult;
                InputStream imageStream;

                try {
                    imageStream = new FileInputStream(new File(uri.getPath()));
                    if (mRoom.isEncrypted() /*&& mSession.isCryptoEnabled()*/ && (null != imageStream)) {
                        encryptionResult = MXEncryptedAttachments.encryptAttachment(imageStream, mimeType);
                        imageStream.close();

                        if (null != encryptionResult) {
                            imageStream = encryptionResult.mEncryptedStream;
                            mimeType = "application/octet-stream";
                        } else {
                            skip();
                            //displayEncryptionAlert();
                            return;
                        }
                    } else {
                        encryptionResult = null;
                    }
                } catch (Exception e) {
                    skip();
                    return;
                }

                //
                event.mSentState = Event.SentState.SENDING;

                final MXMediasCache mediasCache = mDataHandler.getMediasCache();

                mediasCache.uploadContent(imageStream, mediaMessage.isThumbnailLocalContent() ? ("thumb" + message.body) : message.body, mimeType, url, new MXMediaUploadListener() {
                    @Override
                    public void onUploadStart(final String uploadId) {
                        mUiHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (null != dataItem.getMediaUploadListener()) {
                                    dataItem.getMediaUploadListener().onUploadStart(uploadId);
                                }
                            }
                        });
                    }

                    @Override
                    public void onUploadCancel(final String uploadId) {
                        mUiHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (null != dataItem.getMediaUploadListener()) {
                                    dataItem.getMediaUploadListener().onUploadCancel(uploadId);
                                }

                                synchronized (LOG_TAG) {
                                    synchronized (LOG_TAG) {
                                        mSendingItem = null;
                                    }
                                    sendNext();
                                }
                            }
                        });
                    }

                    @Override
                    public void onUploadError(final String uploadId, final int serverResponseCode, final String serverErrorMessage) {
                        mUiHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (null != dataItem.getMediaUploadListener()) {
                                    dataItem.getMediaUploadListener().onUploadError(uploadId, serverResponseCode, serverErrorMessage);
                                }

                                synchronized (LOG_TAG) {
                                    synchronized (LOG_TAG) {
                                        mSendingItem = null;
                                    }
                                    sendNext();
                                }
                            }
                        });
                    }

                    @Override
                    public void onUploadComplete(final String uploadId, final String contentUri) {
                        mUiHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (mediaMessage.isThumbnailLocalContent()) {
                                    mediaMessage.setThumbnailUrl(encryptionResult, contentUri);

                                    if (null != encryptionResult) {
                                        mediasCache.saveFileMediaForUrl(contentUri, url, -1, -1, "image/jpeg");
                                    } else {
                                        int maxThumbnailWidth = (int) dataItem.getCustomObject(RoomDataItem.MAX_THUMBNAIL_WIDTH_KEY);
                                        int maxThumbnailHeight = (int) dataItem.getCustomObject(RoomDataItem.MAX_THUMBNAIL_HEIGHT_KEY);

                                        mediasCache.saveFileMediaForUrl(contentUri, url, maxThumbnailWidth, maxThumbnailHeight, "image/jpeg");
                                    }

                                    // update the event content with the new message info
                                    event.updateContent(JsonUtils.toJson(message));

                                    // force to save the room events list
                                    // https://github.com/vector-im/riot-android/issues/1390
                                    mDataHandler.getStore().flushRoomEvents(mRoom.getRoomId());

                                    // upload the high res picture
                                    uploadMedias(dataItem);
                                } else {
                                    // replace the thumbnail and the media contents by the computed one
                                    mediasCache.saveFileMediaForUrl(contentUri, url, mediaMessage.getMimeType());
                                    mediaMessage.setUrl(encryptionResult, contentUri);

                                    // update the event content with the new message info
                                    event.updateContent(JsonUtils.toJson(message));

                                    // force to save the room events list
                                    // https://github.com/vector-im/riot-android/issues/1390
                                    mDataHandler.getStore().flushRoomEvents(mRoom.getRoomId());

                                    Log.d(LOG_TAG, "Uploaded to " + contentUri);

                                    // send
                                    sendEvent(event);
                                }

                                if (null != dataItem.getMediaUploadListener()) {
                                    dataItem.getMediaUploadListener().onUploadComplete(uploadId, contentUri);
                                }

                            }
                        });
                    }
                });
            }
        });

        return true;
    }
}
