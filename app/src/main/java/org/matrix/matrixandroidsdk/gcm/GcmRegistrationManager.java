package org.matrix.matrixandroidsdk.gcm;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.gcm.GoogleCloudMessaging;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.matrixandroidsdk.Matrix;
import org.matrix.matrixandroidsdk.R;

import java.io.IOException;

/**
 * Helper class to store the GCM registration ID in {@link SharedPreferences}
 */
public final class GcmRegistrationManager {
    private static String LOG_TAG = "GcmRegistrationManager";

    public static final String PREFS_GCM = "org.matrix.matrixandroidsdk.gcm.GcmRegistrationManager";
    public static final String PREFS_KEY_REG_ID_PREFIX = "REG_ID-";

    public static final String PREFS_PUSHER_APP_ID_KEY = "org.matrix.matrixandroidsdk.gcm.GcmRegistrationManager.pusherAppId";
    public static final String PREFS_SENDER_ID_KEY = "org.matrix.matrixandroidsdk.gcm.GcmRegistrationManager.senderId";
    public static final String PREFS_PUSHER_URL_KEY = "org.matrix.matrixandroidsdk.gcm.GcmRegistrationManager.pusherUrl";
    public static final String PREFS_PUSHER_FILE_TAG_KEY = "org.matrix.matrixandroidsdk.gcm.GcmRegistrationManager.pusherFileTag";
    public static final String PREFS_APP_VERSION = "org.matrix.matrixandroidsdk.gcm.GcmRegistrationManager.appVersion";

    // TODO: Make this configurable at build time
    private static String DEFAULT_SENDER_ID = "0";
    private static String DEFAULT_PUSHER_APP_ID = "org.matrix.console.android";
    private static String DEFAULT_PUSHER_URL = "http://matrix.org/_matrix/push/v1/notify";
    private static String DEFAULT_PUSHER_FILE_TAG = "mobile";

    // theses both entries can be updated from the settings page in debug mode
    private String mPusherAppId = null;
    private String mSenderId = null;
    private String mPusherUrl = null;
    private String mPusherFileTag = null;

    private String mPusherAppName = null;
    private String mPusherLang = null;

    private enum RegistrationState {
        UNREGISTRATED,
        REGISTRATING,
        REGISTRED,
        UNREGISTRATING
    };

    private static String mBasePusherDeviceName = Build.MODEL.trim();

    private Context mContext;
    private RegistrationState mRegistrationState = RegistrationState.UNREGISTRATED;

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

    /**
     * reset the Registration
     */
    public void reset() {
        unregisterPusher(null);

        // remove the keys
        getSharedPreferences().edit().clear().commit();

        loadGcmData();
    }

    /*
        getters & setters
     */
    public String pusherAppId() {
        return mPusherAppId;
    }

    public void setPusherAppId(String pusherAppId) {
        if (!TextUtils.isEmpty(pusherAppId) && !pusherAppId.equals(mPusherAppId)) {
            mPusherAppId = pusherAppId;
            SaveGCMData();
        }
    }

    public String senderId() {
        return mSenderId;
    }

    public void setSenderId(String senderId) {
        if (!TextUtils.isEmpty(senderId) && !senderId.equals(mSenderId)) {
            mSenderId = senderId;
            SaveGCMData();
        }
    }

    public String pusherUrl() {
        return mPusherUrl;
    }

    public void setPusherUrl(String pusherUrl) {
        if (!TextUtils.isEmpty(pusherUrl) && !pusherUrl.equals(mPusherUrl)) {
            mPusherUrl = pusherUrl;
            SaveGCMData();
        }
    }

    public String pusherFileTag() {
        return mPusherFileTag;
    }

    public void setPusherFileTag(String pusherFileTag) {
        if (!TextUtils.isEmpty(pusherFileTag) && !pusherFileTag.equals(mPusherFileTag)) {
            mPusherFileTag = pusherFileTag;
            SaveGCMData();
        }
    }

    public interface GcmRegistrationIdListener {
        void onPusherRegistered();
        void onPusherRegistrationFailed();
    }

    public interface GcmUnregistrationIdListener {
        void onPusherUnregistered();
        void onPusherUnregistrationFailed();
    }

    /**
     * Check the device to make sure it has the Google Play Services APK. If
     * it doesn't, display a dialog that allows users to download the APK from
     * the Google Play Store or enable it in the device's system settings.
     */
    private boolean checkPlayServices() {
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(mContext);
        if (resultCode != ConnectionResult.SUCCESS) {
            if (GooglePlayServicesUtil.isUserRecoverableError(resultCode)) {
                Log.e(LOG_TAG, "checkPlayServices isUserRecoverableError " +  GooglePlayServicesUtil.getErrorString(resultCode));
            } else {
                Log.e(LOG_TAG, "This device is not supported.");
            }
            return false;
        }
        return true;
    }

    /**
     * Register to the GCM.
     * @param registrationListener the events listener.
     */
    public void registerPusher(final GcmRegistrationIdListener registrationListener) {
        // already registred
        if (mRegistrationState == RegistrationState.REGISTRED) {
            if (null != registrationListener) {
                registrationListener.onPusherRegistered();
            }
        } else if (mRegistrationState != RegistrationState.UNREGISTRATED) {
            if (null != registrationListener) {
                registrationListener.onPusherRegistrationFailed();
            }
        } else {

            mRegistrationState = RegistrationState.REGISTRATING;

            new AsyncTask<Void, Void, String>() {
                @Override
                protected String doInBackground(Void... voids) {
                    String registrationId = null;

                    if (checkPlayServices()) {
                        registrationId = getRegistrationId();

                        if (registrationId != null) {
                            registerPusher(registrationId);
                        }
                    }
                    return registrationId;
                }

                @Override
                protected void onPostExecute(String registrationId) {

                    if (registrationId != null) {
                        mRegistrationState = RegistrationState.REGISTRED;
                    } else {
                        mRegistrationState = RegistrationState.UNREGISTRATED;
                    }

                    setStoredRegistrationId(registrationId);

                    // warn the listener
                    if (null != registrationListener) {
                        try {
                            if (registrationId != null) {
                                registrationListener.onPusherRegistered();
                            } else {
                                registrationListener.onPusherRegistrationFailed();
                            }
                        } catch (Exception e) {
                        }
                    }
                }
            }.execute();
        }
    }

    /**
     * Unregister from the GCM.
     * @param unregistrationListener the events listener.
     */
    public void unregisterPusher(final GcmUnregistrationIdListener unregistrationListener) {

        // already unregistred
        if (mRegistrationState == RegistrationState.UNREGISTRATED) {
            if (null != unregistrationListener) {
                unregistrationListener.onPusherUnregistered();
            }
        } else if (mRegistrationState != RegistrationState.REGISTRED) {
            if (null != unregistrationListener) {
                unregistrationListener.onPusherUnregistrationFailed();
            }
        } else {
            mRegistrationState = RegistrationState.UNREGISTRATING;

            new AsyncTask<Void, Void, Void>() {
                @Override
                protected Void doInBackground(Void... voids) {

                    try {
                        // and callback if not.
                        GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(mContext);
                        gcm.unregister();
                    } catch (IOException e) {
                    }

                    // should warn the sever that the user unregistres his device.
                    return null;
                }

                @Override
                protected void onPostExecute(Void param) {
                    setStoredRegistrationId(null);

                    mRegistrationState = RegistrationState.UNREGISTRATED;

                    // warn the listener
                    if (null != unregistrationListener) {
                        try {
                            unregistrationListener.onPusherUnregistered();
                        } catch (Exception e) {
                        }
                    }
                }
            }.execute();
        }
    }

    /**
     * @return true if use GCM
     */
    public Boolean useGCM() {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        return preferences.getBoolean(mContext.getString(R.string.settings_key_use_gcm), false);
    }

    public Boolean isRegistred() {
        return mRegistrationState == RegistrationState.REGISTRED;
    }

    private String getRegistrationId() {
        String registrationId = getStoredRegistrationId();

        // Check if app was updated; if so, it must clear the registration ID
        // since the existing registration ID is not guaranteed to work with
        // the new app version.
        if (isNewAppVersion()) {
            registrationId = null;
            setStoredRegistrationId(null);
        }

        if (registrationId == null) {
            try {
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
                            mPusherLang, mPusherAppName, mBasePusherDeviceName,
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
     * @return true if the current application version is not the same the expected one.
     */
    private Boolean isNewAppVersion() {
        try {
            PackageInfo pInfo = mContext.getPackageManager().getPackageInfo(mContext.getPackageName(), 0);
            int currentVersion = pInfo.versionCode;

            int registeredVersion = getSharedPreferences().getInt(PREFS_APP_VERSION, Integer.MIN_VALUE);

            if (registeredVersion != currentVersion) {
                Log.d(LOG_TAG, "App version changed.");
                getSharedPreferences().edit().putInt(PREFS_APP_VERSION, currentVersion).commit();
                return true;
            }

            return false;
        } catch (Exception e) {

        }

        return true;
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

            editor.putString(PREFS_PUSHER_APP_ID_KEY, mPusherAppId);
            editor.putString(PREFS_SENDER_ID_KEY, mSenderId);
            editor.putString(PREFS_PUSHER_URL_KEY, mPusherUrl);
            editor.putString(PREFS_PUSHER_FILE_TAG_KEY, mPusherFileTag);

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

            {
                String pusherAppId = preferences.getString(PREFS_PUSHER_APP_ID_KEY, null);
                mPusherAppId = TextUtils.isEmpty(pusherAppId) ? DEFAULT_PUSHER_APP_ID : pusherAppId;
            }

            {
                String senderId = preferences.getString(PREFS_SENDER_ID_KEY, null);
                mSenderId = TextUtils.isEmpty(senderId) ? DEFAULT_SENDER_ID : senderId;
            }

            {
                String pusherUrl = preferences.getString(PREFS_PUSHER_URL_KEY, null);
                mPusherUrl = TextUtils.isEmpty(pusherUrl) ? DEFAULT_PUSHER_URL : pusherUrl;
            }

            {
                String pusherFileTag = preferences.getString(PREFS_PUSHER_FILE_TAG_KEY, null);
                mPusherFileTag = TextUtils.isEmpty(pusherFileTag) ? DEFAULT_PUSHER_FILE_TAG : pusherFileTag;
            }
        } catch (Exception e) {

        }
    }
}
