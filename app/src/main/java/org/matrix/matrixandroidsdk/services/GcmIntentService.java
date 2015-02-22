package org.matrix.matrixandroidsdk.services;

import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;

import com.google.android.gms.gcm.GoogleCloudMessaging;

import org.matrix.matrixandroidsdk.GcmBroadcastReceiver;

/**
 * Service that receives messages from GCM.
 */
public class GcmIntentService extends IntentService {

    private static final String LOG_TAG = "GcmIntentService";
    private static final int MSG_NOTIFICATION_ID = 43;

    public GcmIntentService() {
        super("GcmIntentService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Bundle extras = intent.getExtras();
        GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(this);
        // The getMessageType() intent parameter must be the intent you received
        // in your BroadcastReceiver.
        String messageType = gcm.getMessageType(intent);

        if (messageType.equals(GoogleCloudMessaging.MESSAGE_TYPE_MESSAGE)) {
            // TODO: handlePushNotification(extras);
        }

        GcmBroadcastReceiver.completeWakefulIntent(intent);
    }
}
