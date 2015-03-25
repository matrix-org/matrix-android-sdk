package org.matrix.matrixandroidsdk.gcm;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import com.google.android.gms.gcm.GoogleCloudMessaging;

import java.io.IOException;

/**
 * Created by leon on 2/28/15.
 */
public class GetGcmRegistrationIdTask extends AsyncTask<Void,Void,String> {

    private static final String LOG_TAG = "GcmUtils";
    private static String SENDER_ID = "173057950585";

    private Context mContext;

    GetGcmRegistrationIdTask(Context context) {
        mContext = context;
    }

    @Override
    protected String doInBackground(Void... voids) {
        try {
            GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(mContext);
            return gcm.register(SENDER_ID);
        } catch (IOException e) {
            Log.e(LOG_TAG, "Failed to register with GCM", e);
            return null;
        }
    }
}
