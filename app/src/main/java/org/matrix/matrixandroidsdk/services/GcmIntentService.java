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
