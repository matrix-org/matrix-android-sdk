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
import android.os.Looper;
import android.text.Html;
import android.text.TextUtils;

import org.matrix.androidsdk.MXDataHandler;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.Message;
import org.matrix.androidsdk.util.Log;
import org.matrix.androidsdk.util.MXOsHandler;

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

    private final MXOsHandler mUiHandler;

    /**
     * Constructor
     * @param context the context
     * @param room the room
     */
    RoomDataItemsSender(Context context, MXDataHandler dataHandler, Room room) {
        mRoom = room;
        mContext = context;
        mDataHandler = dataHandler;

        mUiHandler = new MXOsHandler(Looper.getMainLooper());
    }

    /**
     * Send a new item message in the room
     * @param item the item to send
     */
    void send(final RoomDataItem item) {
        if (null == item.getEvent()) {
            Message message = null;
            String mimeType = item.getMimeType(mContext);

            // avoid null case
            if (null == mimeType) {
                mimeType = "";
            }

            if ((null == item.getUri()) && (TextUtils.equals(ClipDescription.MIMETYPE_TEXT_PLAIN, mimeType) || TextUtils.equals(ClipDescription.MIMETYPE_TEXT_HTML, mimeType))) {
                message = buildTextMessage(item);
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

    /**
     * Send the next pending item
     */
    private void sendNext() {
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
        }

        // nothing more to upload
        mRoom.sendEvent(mSendingItem.getEvent(), new ApiCallback<Void>() {

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
}
