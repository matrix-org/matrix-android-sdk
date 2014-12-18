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
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.matrixandroidsdk.activity.HomeActivity;
import org.matrix.matrixandroidsdk.Matrix;
import org.matrix.matrixandroidsdk.R;
import org.matrix.matrixandroidsdk.util.EventUtils;

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

    private MXEventListener mListener = new MXEventListener() {
        @Override
        public void onLiveEvent(Event event, RoomState roomState) {
            if (EventUtils.shouldNotify(EventStreamService.this, event)) {
                String from = event.userId;
                String body = event.content.getAsJsonPrimitive("body").getAsString();
                Notification n = buildMessageNotification(from, body);
                NotificationManager nm = (NotificationManager) EventStreamService.this.getSystemService(Context.NOTIFICATION_SERVICE);
                Log.w(LOG_TAG, "onMessageEvent >>>> " + event);
                nm.notify(MSG_NOTIFICATION_ID, n);
            }
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

        mSession.getDataHandler().addListener(mListener);
        mSession.startEventStream();
        startWithNotification();
    }

    private void stop() {
        stopForeground(true);
        if (mSession != null) {
            mSession.stopEventStream();
            mSession.getDataHandler().removeListener(mListener);
        }
        mSession = null;
        mState = StreamAction.STOP;
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
        startWithNotification();
    }

    private void startWithNotification() {
        Notification notification = buildNotification();
        startForeground(NOTIFICATION_ID, notification);
        mState = StreamAction.START;
    }

    private Notification buildMessageNotification(String from, String body) {
        Notification.Builder builder = new Notification.Builder(this);
        builder.setWhen(System.currentTimeMillis());
        builder.setContentTitle(from + " (Matrix)");
        builder.setContentText(body);
        builder.setAutoCancel(true);
        builder.setSmallIcon(R.drawable.ic_menu_start_conversation);
        Notification n = builder.getNotification();
        n.flags |= Notification.FLAG_SHOW_LIGHTS;
        n.defaults |= Notification.DEFAULT_LIGHTS;
//        n.defaults |= Notification.DEFAULT_VIBRATE;
        return n;
    }

    private Notification buildNotification() {
        Notification notification = new Notification(
                R.drawable.ic_menu_start_conversation,
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
}
