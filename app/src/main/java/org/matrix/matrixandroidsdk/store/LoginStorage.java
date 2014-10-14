package org.matrix.matrixandroidsdk.store;

import android.content.Context;
import android.content.SharedPreferences;

import org.apache.http.impl.auth.DigestScheme;
import org.matrix.androidsdk.api.response.login.Credentials;

/**
 * Stores login credentials for N home servers and N accounts.
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

    public Credentials getDefaultCredentials() {
        SharedPreferences prefs = mContext.getSharedPreferences(PREFS_LOGIN, Context.MODE_PRIVATE);
        String username = prefs.getString(PREFS_KEY_USERNAME, null);
        String server = prefs.getString(PREFS_KEY_HOME_SERVER, null);
        String token = prefs.getString(PREFS_KEY_ACCESS_TOKEN, null);
        if (username == null || server == null || token == null) {
            return null;
        }
        Credentials creds = new Credentials();
        creds.userId = username;
        creds.homeServer = server;
        creds.accessToken = token;
        return creds;
    }

    public boolean setDefaultCredentials(Credentials credentials) {
        SharedPreferences prefs = mContext.getSharedPreferences(PREFS_LOGIN, Context.MODE_PRIVATE);
        SharedPreferences.Editor e = prefs.edit();
        e.putString(PREFS_KEY_ACCESS_TOKEN, credentials.accessToken);
        e.putString(PREFS_KEY_HOME_SERVER, credentials.homeServer);
        e.putString(PREFS_KEY_USERNAME, credentials.userId);
        return e.commit();
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

    /**
     * Save this set of credentials.
     * @param credentials The credentials to save. Will clobber existing credentials for this
     *                    home server / user ID pair.
     * @return True if the credentials were saved.
     */
    public boolean saveCredentials(Credentials credentials) {
        SharedPreferences prefs = mContext.getSharedPreferences(PREFS_LOGIN, Context.MODE_PRIVATE);
        SharedPreferences.Editor e = prefs.edit();
        String scopedPart = getScopedPart(credentials.homeServer, credentials.userId);
        e.putString(PREFS_KEY_ACCESS_TOKEN + scopedPart, credentials.accessToken);
        e.putString(PREFS_KEY_HOME_SERVER + scopedPart, credentials.homeServer);
        e.putString(PREFS_KEY_USERNAME + scopedPart, credentials.userId);
        return e.commit();
    }

    private String getScopedPart(String hs, String userId) {
        return "." + hs + "." + userId;
    }

}
