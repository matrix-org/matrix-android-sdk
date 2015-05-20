package org.matrix.matrixandroidsdk.gcm;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.util.Log;

import com.google.android.gms.gcm.GoogleCloudMessaging;

import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.matrixandroidsdk.Matrix;

import java.io.IOException;

/**
 * Helper class to store the GCM registration ID in {@link SharedPreferences}
 */
public final class GcmRegistrationManager {
    private static String LOG_TAG = "GcmRegistrationManager";

    // TODO: Make this configurable at build time
    private static String SENDER_ID = "0";

    private static String PUSHER_APP_ID = "org.matrix.console.android";
    // TODO: Get from Package manager, localized
    private static String PUSHER_APP_NAME = "Matrix Console";
    // TODO: Get this from the System
    private static String PUSHER_DEVICE_NAME = "Banaphone";
    // TODO: Get this from the system locale
    private static String PUSHER_LANG = "en";
    // TODO: Make this configurable in settings
    private static String PUSHER_URL = "http://matrix.org/_matrix/push/v1/notify";
    // TODO: Generate a random profile tag, make editable in settings.
    private static String PUSHER_PROFILE_TAG = "mobile";

    public static final String PREFS_GCM = "org.matrix.matrixandroidsdk.gcm.GcmRegistrationManager";
    public static final String PREFS_KEY_REG_ID_PREFIX = "REG_ID-";

    private Context mContext;
    private GcmRegistrationIdListener mListener;

    private Boolean mIsRegistred = false;

    public GcmRegistrationManager(Context appContext) {
        mContext = appContext.getApplicationContext();
    }

    public interface GcmRegistrationIdListener {
        void onPusherRegistered();
    }

    public void setListener(GcmRegistrationIdListener listener) {
        mListener = listener;
    }

    // TODO: handle multi sessions
    public void registerPusherInBackground() {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                String registrationId = getRegistrationId();
                if (registrationId != null) {
                    registerPusher(registrationId);
                } else {
                    // TODO: Handle error by calling a method on the listener
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void result) {
                mIsRegistred = true;
                if (mListener != null) {
                    mListener.onPusherRegistered();
                }
            }
        }.execute();
    }

    public Boolean isRegistred() {
        return mIsRegistred;
    }

    private String getRegistrationId() {
        String registrationId = getStoredRegistrationId();
        if (registrationId == null) {
            try {
                // TODO: Check if (an up to date version of) Google Play Services is available
                // and callback if not.
                GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(mContext);
                registrationId = gcm.register(SENDER_ID);
            } catch (IOException e) {
                registrationId = null;
            }
        }
        return registrationId;
    }

    private void registerPusher(String registrationId) {
        Matrix.getInstance(mContext).getDefaultSession().getPushersRestClient()
                .addHttpPusher(registrationId, PUSHER_APP_ID, PUSHER_PROFILE_TAG,
                        PUSHER_LANG, PUSHER_APP_NAME, PUSHER_DEVICE_NAME,
                        PUSHER_URL, new ApiCallback<Void>() {
                            @Override
                            public void onSuccess(Void info) {
                                Log.d(LOG_TAG, "registerPusher succeeded");
                            }

                            @Override
                            public void onNetworkError(Exception e) {
                                Log.e(LOG_TAG, "registerPusher onNetworkError " + e.getMessage());
                            }

                            @Override
                            public void onMatrixError(MatrixError e) {
                                Log.e(LOG_TAG, "registerPusher onMatrixError " + e.errcode);
                            }

                            @Override
                            public void onUnexpectedError(Exception e) {
                                Log.e(LOG_TAG, "registerPusher onUnexpectedError " + e.getMessage());
                            }
                        });
    }

    /**
     * @return the GCM registration stored for this version of the app or null if none is stored.
     */
    private String getStoredRegistrationId() {
        return getSharedPreferences().getString(getRegistrationIdKey(), null);
    }

    /**
     * Set the GCM registration for the currently-running version of this app.
     * @param registrationId
     */
    private void setStoredRegistrationId(String registrationId) {
        String key = getRegistrationIdKey();
        if (key == null) {
            Log.e(LOG_TAG, "Failed to store registration ID");
            return;
        }

        Log.d(LOG_TAG, "Saving registrationId " + registrationId + " under key " + key);
        getSharedPreferences().edit()
                .putString(key, registrationId)
                .commit();
    }

    private SharedPreferences getSharedPreferences() {
        return mContext.getSharedPreferences(PREFS_GCM, Context.MODE_PRIVATE);
    }

    private String getRegistrationIdKey() {
        try {
            PackageInfo packageInfo = mContext.getPackageManager()
                    .getPackageInfo(mContext.getPackageName(), 0);
            return PREFS_KEY_REG_ID_PREFIX + Integer.toString(packageInfo.versionCode);
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}
