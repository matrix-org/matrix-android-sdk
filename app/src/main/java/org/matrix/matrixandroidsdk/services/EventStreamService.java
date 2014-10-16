package org.matrix.matrixandroidsdk.services;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import org.matrix.androidsdk.MXSession;
import org.matrix.matrixandroidsdk.HomeActivity;
import org.matrix.matrixandroidsdk.Matrix;
import org.matrix.matrixandroidsdk.R;

/**
 * A foreground service in charge of controlling whether the event stream is running on not.
 */
public class EventStreamService extends Service {
    public static final String EXTRA_KILL_STREAM = "org.matrix.matrixandroidsdk.services.EventStreamService.EXTRA_KILL_STREAM";

    private static final String LOG_TAG = "EventStreamService";
    private static final int NOTIFICATION_ID = 42;

    private MXSession mSession;
    private boolean mStarted;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        boolean killStream = intent.getBooleanExtra(EXTRA_KILL_STREAM, false);
        if (killStream) {
            stop();
        }
        else {
            start();
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
        if (mStarted) {
            Log.w(LOG_TAG, "Already started.");
            return;
        }
        Log.d(LOG_TAG, "start()");
        if (mSession == null) {
            mSession = Matrix.getInstance(getApplicationContext()).getDefaultSession();
            if (mSession == null) {
                Log.e(LOG_TAG, "No valid MXSession.");
                return;
            }
        }

        mSession.startEventStream();

        Notification notification = buildNotification();
        startForeground(NOTIFICATION_ID, notification);
        mStarted = true;
    }

    private void stop() {
        Log.d(LOG_TAG, "stop()");
        stopForeground(true);
        if (mSession != null) {
            mSession.stopEventStream();
        }
        mSession = null;
        mStarted = false;
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
