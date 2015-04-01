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

package org.matrix.matrixandroidsdk.services;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.bingrules.BingRule;
import org.matrix.matrixandroidsdk.Matrix;
import org.matrix.matrixandroidsdk.R;
import org.matrix.matrixandroidsdk.ViewedRoomTracker;
import org.matrix.matrixandroidsdk.activity.HomeActivity;
import org.matrix.matrixandroidsdk.adapters.AdapterUtils;
import org.matrix.matrixandroidsdk.util.NotificationUtils;


/**
 * A foreground service in charge of controlling whether the event stream is running or not.
 */
public class EventStreamService extends Service {
    public static enum StreamAction {
        UNKNOWN,
        STOP,
        START,
        PAUSE,
        RESUME
    }
    public static final String EXTRA_STREAM_ACTION = "org.matrix.matrixandroidsdk.services.EventStreamService.EXTRA_STREAM_ACTION";

    private static final String LOG_TAG = "EventStreamService";
    private static final int NOTIFICATION_ID = 42;
    private static final int MSG_NOTIFICATION_ID = 43;

    private MXSession mSession;
    private StreamAction mState = StreamAction.UNKNOWN;

    private String mNotificationRoomId = null;

    private static EventStreamService mActiveEventStreamService = null;


    /**
     * Cancel the push notifications for a dedicated roomId.
     * If the roomId is null, cancel all the push notification.
     * @param roomId
     */
    public static void cancelNotificationsForRoomId(String roomId) {
        if (null != mActiveEventStreamService) {
            mActiveEventStreamService.cancelNotifications(roomId);
        }
    }

    private void cancelNotifications(String roomId) {
        boolean cancelNotifications = true;

        // clear only if the notification has been pushed for a dedicated RoomId
        if (null != roomId) {
            cancelNotifications = (null != mNotificationRoomId) && (mNotificationRoomId.equals(roomId));
        }

        // cancel the notifications
        if (cancelNotifications) {
            NotificationManager nm = (NotificationManager) EventStreamService.this.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.cancelAll();
        }
    }

    private MXEventListener mListener = new MXEventListener() {

        @Override
        public void onBingEvent(Event event, RoomState roomState, BingRule bingRule) {
            Log.i(LOG_TAG, "onMessageEvent >>>> " + event);

            final String roomId = event.roomId;

            // Just don't bing for the room the user's currently in
            if ((roomId != null) && event.roomId.equals(ViewedRoomTracker.getInstance().getViewedRoomId())) {
                return;
            }

            String senderID = event.userId;
            // FIXME: Support event contents with no body
            if (!event.content.has("body")) {
                // only the membership events are supported
                if (!Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(event.type)) {
                    return;
                }
            }

            String body;

            if (Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(event.type)) {
                body = AdapterUtils.EventDisplay.getMembershipNotice(getApplicationContext(), event, roomState);
            } else {
                body = event.content.getAsJsonPrimitive("body").getAsString();
            }

            Room room = mSession.getDataHandler().getRoom(roomId);

            // invalid room ?
            if (null == room) {
                return;
            }

            RoomMember member = room.getMember(senderID);

            // invalid member
            if (null == member) {
                return;
            }

            String roomName = null;
            if(mSession.getMyUser() != null) {
                roomName = room.getName(mSession.getMyUser().userId);
            }

            mNotificationRoomId = roomId;

            Notification n = NotificationUtils.buildMessageNotification(
                    EventStreamService.this,
                    member.getName(), body, event.roomId, roomName, bingRule.shouldPlaySound());
            NotificationManager nm = (NotificationManager) EventStreamService.this.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.cancelAll();

            nm.notify(MSG_NOTIFICATION_ID, n);
        }

        @Override
        public void onResendingEvent(Event event) {
        }

        @Override
        public void onResentEvent(Event event) {
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        StreamAction action = StreamAction.values()[intent.getIntExtra(EXTRA_STREAM_ACTION, StreamAction.UNKNOWN.ordinal())];
        Log.d(LOG_TAG, "onStartCommand >> "+action);
        switch (action) {
            case START:
            case RESUME:
                start();
                break;
            case STOP:
                stop();
                break;
            case PAUSE:
                pause();
                break;
            default:
                break;
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        stop();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void start() {
        if (mState == StreamAction.START) {
            Log.w(LOG_TAG, "Already started.");
            return;
        }
        else if (mState == StreamAction.PAUSE) {
            Log.i(LOG_TAG, "Resuming active stream.");
            resume();
            return;
        }
        if (mSession == null) {
            mSession = Matrix.getInstance(getApplicationContext()).getDefaultSession();
            if (mSession == null) {
                Log.e(LOG_TAG, "No valid MXSession.");
                return;
            }
        }

        mActiveEventStreamService = this;

        mSession.getDataHandler().addListener(mListener);
        mSession.startEventStream();
        if (shouldRunInForeground()) {
            startWithNotification();
        }
    }

    private void stop() {
        stopForeground(true);
        if (mSession != null) {
            mSession.stopEventStream();
            mSession.getDataHandler().removeListener(mListener);
        }
        mSession = null;
        mState = StreamAction.STOP;

        mActiveEventStreamService = null;
    }

    private void pause() {
        stopForeground(true);
        if (mSession != null) {
            mSession.pauseEventStream();
        }
        mState = StreamAction.PAUSE;
    }

    private void resume() {
        if (mSession != null) {
            mSession.resumeEventStream();
        }
        if (shouldRunInForeground()) {
            startWithNotification();
        }
    }

    private void startWithNotification() {
        // TODO: remove the listening for events notification
        Notification notification = buildNotification();
        startForeground(NOTIFICATION_ID, notification);
        mState = StreamAction.START;
    }

    private Notification buildNotification() {
        Notification notification = new Notification(
                R.drawable.ic_menu_small_matrix,
                "Matrix",
                System.currentTimeMillis()
        );

        // go to the home screen if this is clicked.
        Intent i = new Intent(this, HomeActivity.class);

        i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|
                Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pi = PendingIntent.getActivity(this, 0, i, 0);

        notification.setLatestEventInfo(this, getString(R.string.app_name),
                "Listening for events",
                pi);
        notification.flags |= Notification.FLAG_NO_CLEAR;
        return notification;
    }

    private boolean shouldRunInForeground() {
        // TODO: Make configurable in settings, false by default if GCM registration succeeded.
        return true;
    }
}
