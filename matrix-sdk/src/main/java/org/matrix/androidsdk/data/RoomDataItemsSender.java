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
import android.net.Uri;
import android.os.HandlerThread;
import android.os.Looper;
import android.text.Html;
import android.text.TextUtils;

import org.matrix.androidsdk.MXDataHandler;
import org.matrix.androidsdk.crypto.MXEncryptedAttachments;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.listeners.MXMediaUploadListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.ImageMessage;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.Message;
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
import java.util.concurrent.Exchanger;

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

    // dedicated thread
    private static HandlerThread mHandlerThread = null;
    private static android.os.Handler mMediasSendingHandler = null;

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
            mHandlerThread = new HandlerThread("RoomDataItemsSenderBackgroundThread", Thread.MIN_PRIORITY);
            mHandlerThread.start();

            mMediasSendingHandler = new android.os.Handler(mHandlerThread.getLooper());
        }
    }

    /**
     * Send a new item message in the room
     * @param item the item to send
     */
    void send(final RoomDataItem item) {
        mMediasSendingHandler.post(new Runnable() {
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
                        item.setMessageType(Message.MSGTYPE_IMAGE);
                    }

                    if (null == message) {
                        Log.e(LOG_TAG, "## send " + item + " not supported");
                        return;
                    }

                    Event event = new Event(message, mDataHandler.getUserId(), mRoom.getRoomId());
                    item.setEvent(event);
                    event.mSentState = Event.SentState.UNSENT;
                    mRoom.storeOutgoingEvent(event);
                    mDataHandler.getStore().commit();

                    if (null != item.getRoomDataItemListener()) {
                        mUiHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                item.getRoomDataItemListener().onSending(item);
                            }
                        });
                    }
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

        if (TextUtils.equals(dataItem.getMessageType(), Message.MSGTYPE_IMAGE) && uploadImageMedias(dataItem)) {
            return;
        }

        //
        sendEvent(dataItem.getEvent());
    }

    private void sendEvent(Event event) {
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

    private boolean uploadImageMedias(final RoomDataItem dataItem) {
        final Event event = dataItem.getEvent();
        final ImageMessage imageMessage = JsonUtils.toImageMessage(event.getContent());

        final String url;
        final String fMimeType;

        if (imageMessage.isThumbnailLocalContent()) {
            url = imageMessage.getThumbnailUrl();
            fMimeType = "image/jpeg";
        } else if (imageMessage.isLocalContent()) {
            url = imageMessage.getUrl();
            fMimeType = imageMessage.getMimeType();
        } else {
            return false;
        }

        mMediasSendingHandler.post(new Runnable() {
            @Override
            public void run() {
                Uri uri = Uri.parse(url);
                String mimeType = fMimeType;

                final MXEncryptedAttachments.EncryptionResult encryptionResult;
                InputStream imageStream = null;

                try {
                    imageStream = new FileInputStream(new File(uri.getPath()));
                    if (mRoom.isEncrypted() /*&& mSession.isCryptoEnabled()*/ && (null != imageStream)) {
                        encryptionResult = MXEncryptedAttachments.encryptAttachment(imageStream, mimeType);
                        imageStream.close();
                    } else {
                        encryptionResult = null;
                    }

                    if (null != encryptionResult) {
                        imageStream = encryptionResult.mEncryptedStream;
                        mimeType = "application/octet-stream";
                    } else {
                        skip();
                        //displayEncryptionAlert();
                        return;
                    }
                } catch (Exception e) {
                    skip();
                    return;
                }

                //
                event.mSentState = Event.SentState.SENDING;

                final MXMediasCache mediasCache = mDataHandler.getMediasCache();

                mediasCache.uploadContent(imageStream, imageMessage.isThumbnailLocalContent() ? ("thumb" + imageMessage.body) : imageMessage.body, mimeType, url, new MXMediaUploadListener() {
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
                                if (imageMessage.isThumbnailLocalContent()) {
                                    if (null != encryptionResult) {
                                        imageMessage.info.thumbnail_file = encryptionResult.mEncryptedFileInfo;
                                        imageMessage.info.thumbnail_file.url = contentUri;
                                        imageMessage.thumbnailUrl = null;
                                        mediasCache.saveFileMediaForUrl(contentUri, url, -1, -1, "image/jpeg");
                                    } else {
                                        imageMessage.thumbnailUrl = contentUri;
                                        int maxThumbnailWidth = (int) dataItem.getCustomObject(RoomDataItem.MAX_THUMBNAIL_WIDTH_KEY);
                                        int maxThumbnailHeight = (int) dataItem.getCustomObject(RoomDataItem.MAX_THUMBNAIL_HEIGHT_KEY);

                                        mediasCache.saveFileMediaForUrl(contentUri, url, maxThumbnailWidth, maxThumbnailHeight, "image/jpeg");
                                    }

                                    // update the event content with the new message info
                                    event.updateContent(JsonUtils.toJson(imageMessage));

                                    // force to save the room events list
                                    // https://github.com/vector-im/riot-android/issues/1390
                                    mDataHandler.getStore().flushRoomEvents(mRoom.getRoomId());

                                    // upload the high res picture
                                    uploadImageMedias(dataItem);
                                } else {
                                    // replace the thumbnail and the media contents by the computed one
                                    mediasCache.saveFileMediaForUrl(contentUri, url, imageMessage.getMimeType());

                                    if (null != encryptionResult) {
                                        imageMessage.file = encryptionResult.mEncryptedFileInfo;
                                        imageMessage.file.url = contentUri;
                                        imageMessage.url = null;
                                    } else {
                                        imageMessage.url = contentUri;
                                    }

                                    // update the event content with the new message info
                                    event.updateContent(JsonUtils.toJson(imageMessage));

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
