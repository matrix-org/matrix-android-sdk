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

package org.matrix.matrixandroidsdk.store;

import android.content.Context;
import android.content.SharedPreferences;

import org.matrix.androidsdk.rest.model.login.Credentials;

/**
 * Stores login credentials in SharedPreferences.
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

    /**
     * Set the default login credentials.
     * @param credentials The credentials to set, or null to wipe the stored credentials.
     * @return True if the credentials were set.
     */
    public boolean setDefaultCredentials(Credentials credentials) {
        SharedPreferences prefs = mContext.getSharedPreferences(PREFS_LOGIN, Context.MODE_PRIVATE);
        SharedPreferences.Editor e = prefs.edit();
        e.putString(PREFS_KEY_ACCESS_TOKEN, credentials != null ? credentials.accessToken : null);
        e.putString(PREFS_KEY_HOME_SERVER, credentials != null ? credentials.homeServer : null);
        e.putString(PREFS_KEY_USERNAME, credentials != null ? credentials.userId : null);
        return e.commit();
    }

}
