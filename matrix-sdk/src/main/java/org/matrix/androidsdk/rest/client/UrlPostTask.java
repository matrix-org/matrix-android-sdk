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

package org.matrix.androidsdk.rest.client;

import android.os.AsyncTask;
import android.util.Log;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * UrlPostTask triggers a POST with no param.
 */
public class UrlPostTask extends AsyncTask<String, Void, String> {

    public interface IPostTaskListener {
        /**
         * The post succceeds.
         * @param object the object retrieves in the response.
         */
        void onSucceed(JsonObject object);

        /**
         * The post failed
         * @param errorMessage the error message.
         */
        void onError(String errorMessage);
    }

    private static final String LOG_TAG = "UrlPostTask";

    // the post listener
    private IPostTaskListener mListener;

    @Override
    protected String doInBackground(String... params) {
        String result = "";

        try {
            // Create a new HttpClient and Post Header
            HttpClient httpclient = new DefaultHttpClient();
            HttpPost httppost = new HttpPost(params[0]);
            // Execute HTTP Post Request
            HttpResponse response = httpclient.execute(httppost);

            HttpEntity entity = response.getEntity();

            if (entity != null) {
                // A Simple JSON Response Read
                InputStream instream = entity.getContent();
                result = convertStreamToString(instream);
            }
        } catch(Exception e) {
            // Do something about exceptions
            result = e.getMessage();
        }
        return  result;
    }

    /**
     * Set the post listener
     * @param listener the listener
     */
    public void setListener(IPostTaskListener listener) {
        mListener = listener;
    }

    /**
     * Convert a stream to a string
     * @param is the input stream to convert
     * @return the string
     */
    private static String convertStreamToString(InputStream is) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();

        String line;
        try {
            while ((line = reader.readLine()) != null) {
                sb.append(line + "\n");
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "convertStreamToString " + e.getLocalizedMessage());
        } finally {
            try {
                is.close();
            } catch (Exception e) {
                Log.e(LOG_TAG, "convertStreamToString finally failed " + e.getLocalizedMessage());
            }
        }
        return sb.toString();
    }

    protected void onPostExecute(String result) {
        JsonObject object = null;

        Log.d(LOG_TAG, "onPostExecute " + result);

        try {
            JsonParser parser = new JsonParser();
            object = parser.parse(result).getAsJsonObject();
        } catch (Exception e) {
            Log.e(LOG_TAG, "## onPostExecute() failed" + e.getMessage());
        }

        if (null != mListener) {
            if (null != object) {
                mListener.onSucceed(object);
            } else {
                mListener.onError(result);
            }
        }
    }
}
