package org.matrix.matrixandroidsdk.gcm;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.android.gms.gcm.GoogleCloudMessaging;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.matrixandroidsdk.Matrix;
import org.matrix.matrixandroidsdk.R;

import java.io.IOException;
import java.util.Locale;

/**
 * Helper class to store the GCM registration ID in {@link SharedPreferences}
 */
public final class GcmRegistrationManager {
    private static String LOG_TAG = "GcmRegistrationManager";

    // theses both entries can be updated from the settings page in debug mode
    private String mPusherAppId = "org.matrix.console.android";
    // TODO: Make this configurable at build time
    private String mSenderId = "0";

    private String mPusherUrl = "http://matrix.org/_matrix/push/v1/notify";
    private String mPusherFileTag = "mobile";

    private String mPusherAppName = null;
    private String mPusherLang = null;

    private static String mBasePusherDeviceName = Build.MODEL.trim();

    public static final String PREFS_GCM = "org.matrix.matrixandroidsdk.gcm.GcmRegistrationManager";
    public static final String PREFS_KEY_REG_ID_PREFIX = "REG_ID-";

    public static final String PUSHER_APP_ID_KEY = "org.matrix.matrixandroidsdk.gcm.GcmRegistrationManager.pusherAppId";
    public static final String SENDER_ID_KEY = "org.matrix.matrixandroidsdk.gcm.GcmRegistrationManager.senderId";
    public static final String PUSHER_URL_KEY = "org.matrix.matrixandroidsdk.gcm.GcmRegistrationManager.pusherUrl";
    public static final String PUSHER_FILE_TAG_KEY = "org.matrix.matrixandroidsdk.gcm.GcmRegistrationManager.pusherFileTag";

    private Context mContext;
    private GcmRegistrationIdListener mListener;

    private Boolean mIsRegistred = false;

    public GcmRegistrationManager(Context appContext) {
        mContext = appContext.getApplicationContext();

        try {
            PackageInfo pInfo = mContext.getPackageManager().getPackageInfo(mContext.getPackageName(), 0);
            mPusherAppName = pInfo.packageName;
            mPusherLang = mContext.getResources().getConfiguration().locale.getLanguage();
        } catch (Exception e) {
            mPusherAppName = "Matrix Console";
            mPusherLang = "en";
        }

        loadGcmData();
    }

    /*
        getters & setters
     */
    public String pusherAppId() {
        return mPusherAppId;
    }

    public void setPusherAppId(String pusherAppId) {
        mPusherAppId = pusherAppId;
        SaveGCMData();
    }

    public String senderId() {
        return mSenderId;
    }

    public void setSenderId(String senderId) {
        mSenderId = senderId;
        SaveGCMData();
    }

    public String pusherUrl() {
        return mPusherUrl;
    }

    public void setPusherUrl(String pusherUrl) {
        mPusherUrl = pusherUrl;
        SaveGCMData();
    }

    public String pusherFileTag() {
        return mPusherFileTag;
    }

    public void setPusherFileTag(String pusherFileTag) {
        mPusherFileTag = pusherFileTag;
        SaveGCMData();
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

    /**
     * @return true if use GCM
     */
    public Boolean useGCM() {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        return preferences.getBoolean(mContext.getString(R.string.settings_key_use_gcm), false);
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
                registrationId = gcm.register(mSenderId);
                //setStoredRegistrationId(registrationId);
            } catch (IOException e) {
                registrationId = null;
            }
        }
        return registrationId;
    }

    private void registerPusher(String registrationId) {
        for(MXSession session : Matrix.getInstance(mContext).getSessions()) {
            session.getPushersRestClient()
                    .addHttpPusher(registrationId, mPusherAppId, mPusherFileTag + "_" + session.getMyUser().userId,
                            mPusherLang, mPusherAppName, mPusherAppName + "_" + session.getMyUser().userId,
                            mPusherUrl, new ApiCallback<Void>() {
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

    /**
     * Save the GCM info to the preferences
     */
    private void SaveGCMData() {
        try {
            SharedPreferences preferences = getSharedPreferences();
            SharedPreferences.Editor editor = preferences.edit();

            editor.putString(PUSHER_APP_ID_KEY, mPusherAppId);
            editor.putString(SENDER_ID_KEY, mSenderId);
            editor.putString(PUSHER_URL_KEY, mPusherUrl);
            editor.putString(PUSHER_FILE_TAG_KEY, mPusherFileTag);

            editor.commit();
        } catch (Exception e) {

        }
    }

    /**
     * Load the GCM info from the preferences
     */
    private void loadGcmData() {
        try {
            SharedPreferences preferences = getSharedPreferences();

            String pusherAppId = preferences.getString(PUSHER_APP_ID_KEY, null);
            if (null != pusherAppId) {
                mPusherAppId = pusherAppId;
            }

            String senderId = preferences.getString(SENDER_ID_KEY, null);
            if (null != senderId) {
                mSenderId = senderId;
            }

            String pusherUrl = preferences.getString(PUSHER_URL_KEY, null);
            if (null != pusherUrl) {
                mPusherUrl = pusherUrl;
            }

            String pusherFileTag = preferences.getString(PUSHER_FILE_TAG_KEY, null);
            if (null != pusherFileTag) {
                mPusherFileTag = pusherFileTag;
            }
        } catch (Exception e) {

        }
    }
}
