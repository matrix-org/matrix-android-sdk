/*
 * Copyright 2018 New Vector Ltd
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

package org.matrix.androidsdk.interceptors;

import android.support.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.matrix.androidsdk.BuildConfig;
import org.matrix.androidsdk.util.Log;

import okhttp3.internal.platform.Platform;
import okhttp3.logging.HttpLoggingInterceptor;

public class FormattedJsonHttpLogger implements HttpLoggingInterceptor.Logger {
    private static final String LOG_TAG = FormattedJsonHttpLogger.class.getSimpleName();

    private static final int INDENT_SPACE = 2;

    /**
     * Log the message and try to log it again as a JSON formatted string
     * Note: it can consume a lot of memory but it is only in DEBUG mode
     *
     * @param message
     */
    @Override
    public synchronized void log(@NonNull String message) {
        // In RELEASE there is no log, but for sure, test again BuildConfig.DEBUG
        if (BuildConfig.DEBUG) {
            // Keep default behavior from Platform.java
            Platform.get().log(Platform.INFO, message, null);

            if (message.startsWith("{")) {
                // JSON Detected
                try {
                    JSONObject o = new JSONObject(message);
                    logJson(o.toString(INDENT_SPACE));
                } catch (JSONException e) {
                    // Finally this is not a JSON string...
                    Log.e(LOG_TAG, e.getMessage(), e);
                }
            } else if (message.startsWith("[")) {
                // JSON Array detected
                try {
                    JSONArray o = new JSONArray(message);
                    logJson(o.toString(INDENT_SPACE));
                } catch (JSONException e) {
                    // Finally not JSON...
                    Log.e(LOG_TAG, e.getMessage(), e);
                }
            }
            // Else not a json string to log
        }
    }

    private void logJson(String formattedJson) {
        String[] arr = formattedJson.split("\n");
        for (String s : arr) {
            Platform.get().log(Platform.INFO, s, null);
        }
    }
}
