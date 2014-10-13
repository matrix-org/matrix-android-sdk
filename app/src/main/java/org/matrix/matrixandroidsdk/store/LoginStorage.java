package org.matrix.matrixandroidsdk.store;

import android.content.Context;
import android.content.SharedPreferences;

import org.apache.http.impl.auth.DigestScheme;
import org.matrix.androidsdk.api.response.login.Credentials;

/**
 * Stores login credentials.
 */
public class LoginStorage {
    public static final String PREFS_LOGIN = "org.matrix.matrixandroidsdk.store.LoginStorage";
    public static final String PREFS_KEY_USERNAME = "org.matrix.matrixandroidsdk.store.LoginStorage.PREFS_KEY_USERNAME";
    public static final String PREFS_KEY_HOME_SERVER = "org.matrix.matrixandroidsdk.store.LoginStorage.PREFS_KEY_HOME_SERVER";
    public static final String PREFS_KEY_ACCESS_TOKEN = "org.matrix.matrixandroidsdk.store.LoginStorage.PREFS_KEY_ACCESS_TOKEN";

    private Context mContext;

    public LoginStorage(Context appContext) {
        mContext = appContext.getApplicationContext();
    }

    public boolean hasCredentials() {
        SharedPreferences prefs = mContext.getSharedPreferences(PREFS_LOGIN, Context.MODE_PRIVATE);
        String username = prefs.getString(PREFS_KEY_USERNAME, null);
        String server = prefs.getString(PREFS_KEY_HOME_SERVER, null);
        String token = prefs.getString(PREFS_KEY_ACCESS_TOKEN, null);
        return (username == null || server == null || token == null) ? false : true;
    }

    public String getUserId() {
        SharedPreferences prefs = mContext.getSharedPreferences(PREFS_LOGIN, Context.MODE_PRIVATE);
        return prefs.getString(PREFS_KEY_USERNAME, null);
    }

    public String getHomeserverUrl() {
        SharedPreferences prefs = mContext.getSharedPreferences(PREFS_LOGIN, Context.MODE_PRIVATE);
        return prefs.getString(PREFS_KEY_HOME_SERVER, null);
    }

    public String getAccessToken() {
        SharedPreferences prefs = mContext.getSharedPreferences(PREFS_LOGIN, Context.MODE_PRIVATE);
        return prefs.getString(PREFS_KEY_ACCESS_TOKEN, null);
    }

    public boolean saveCredentials(Credentials credentials) {
        SharedPreferences prefs = mContext.getSharedPreferences(PREFS_LOGIN, Context.MODE_PRIVATE);
        SharedPreferences.Editor e = prefs.edit();
        e.putString(PREFS_KEY_ACCESS_TOKEN, credentials.accessToken);
        e.putString(PREFS_KEY_HOME_SERVER, credentials.homeServer);
        e.putString(PREFS_KEY_USERNAME, credentials.userId);
        return e.commit();
    }

}
