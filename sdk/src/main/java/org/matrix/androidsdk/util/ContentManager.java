/* 
 * Copyright 2014 OpenMarket Ltd
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
package org.matrix.androidsdk.util;

import android.os.AsyncTask;
import android.util.Log;

import org.matrix.androidsdk.rest.model.ContentResponse;
import org.matrix.androidsdk.rest.model.ImageInfo;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Class for accessing content from the current session.
 */
public class ContentManager {

    public static final String MATRIX_CONTENT_URI_SCHEME = "mxc://";

    public static final String METHOD_CROP = "crop";
    public static final String METHOD_SCALE = "scale";

    private static final String URI_PREFIX_CONTENT_API = "/_matrix/media/v1";

    private static final String LOG_TAG = "ContentManager";

    private String mHsUri;
    private String mAccessToken;

    /**
     * Interface to implement to get the mxc URI of uploaded content.
     */
    public static interface UploadCallback {

        /**
         * Called when the upload is complete or has failed.
         * @param uploadResponse the ContentResponse object containing the mxc URI or null if the upload failed
         */
        public void onUploadComplete(ContentResponse uploadResponse);


        /**
         * Warn of the progress upload
         * @param uploadId the upload Identifier
         * @param percentageProgress the progress value
         */
        public void onUploadProgress(String uploadId, int percentageProgress);
    }

    /**
     * Default constructor.
     * @param hsUri the home server URL
     * @param accessToken the user's access token
     */
    public ContentManager(String hsUri, String accessToken) {
        mHsUri = hsUri;
        mAccessToken = accessToken;
    }

    /**
     * Get an actual URL for accessing the full-size image of the given content URI.
     * @param contentUrl the mxc:// content URI
     * @return the URL to access the described resource
     */
    public String getDownloadableUrl(String contentUrl) {
        if (contentUrl == null) return null;
        if (contentUrl.startsWith(MATRIX_CONTENT_URI_SCHEME)) {
            String mediaServerAndId = contentUrl.substring(MATRIX_CONTENT_URI_SCHEME.length());
            return mHsUri + URI_PREFIX_CONTENT_API + "/download/" + mediaServerAndId;
        }
        else {
            return contentUrl;
        }
    }

    /**
     * Get an actual URL for accessing the thumbnail image of the given content URI.
     * @param contentUrl the mxc:// content URI
     * @param width the desired width
     * @param height the desired height
     * @param method the desired scale method (METHOD_CROP or METHOD_SCALE)
     * @return the URL to access the described resource
     */
    public String getDownloadableThumbnailUrl(String contentUrl, int width, int height, String method) {
        if (contentUrl == null) return null;
        if (contentUrl.startsWith(MATRIX_CONTENT_URI_SCHEME)) {
            String mediaServerAndId = contentUrl.substring(MATRIX_CONTENT_URI_SCHEME.length());

            // ignore the #auto pattern
            if (mediaServerAndId.endsWith("#auto")) {
                mediaServerAndId = mediaServerAndId.substring(0, mediaServerAndId.length() - "#auto".length());
            }

            String url = mHsUri + URI_PREFIX_CONTENT_API + "/";

            // identicon server has no thumbnail path
            if (mediaServerAndId.indexOf("identicon") < 0) {
                url += "thumbnail/";
            }

            url +=  mediaServerAndId;
            url += "?width=" + width;
            url += "&height=" + height;
            url += "&method=" + method;
            return url;
        }
        else {
            return contentUrl;
        }
    }

    /**
     * Upload a file.
     * @param contentStream a stream with the content to upload
     * @param callback the async callback returning a mxc: URI to access the uploaded file
     * @return an upload id
     */
    public String uploadContent(InputStream contentStream, String mimeType, UploadCallback callback) {
        String uploadId = System.currentTimeMillis() + "";

        new ContentUploadTask(contentStream, mimeType, callback, uploadId).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

        return uploadId;
    }

    /**
     * Private AsyncTask used to upload files.
     */
    private class ContentUploadTask extends AsyncTask<Void, Integer, String> {

        private UploadCallback callback;
        private String mimeType;
        private InputStream contentStream;
        private String mUploadId;
        private int mProgress = 0;

        public ContentUploadTask(InputStream contentStream, String mimeType, UploadCallback callback, String uploadId) {
            this.callback = callback;
            this.mimeType = mimeType;
            this.contentStream = contentStream;
            this.mUploadId = uploadId;
        }

        @Override
        protected String doInBackground(Void... params) {
            HttpURLConnection conn;
            DataOutputStream dos;

            int bytesRead, bytesAvailable, bufferSize, totalWritten, totalSize;

            byte[] buffer;

            int maxBufferSize = 1024 * 32;

            String responseFromServer = null;

            String urlString = mHsUri + URI_PREFIX_CONTENT_API + "/upload?access_token=" + mAccessToken;

            try
            {
                URL url = new URL(urlString);

                conn = (HttpURLConnection) url.openConnection();
                conn.setDoInput(true);
                conn.setDoOutput(true);
                conn.setUseCaches(false);
                conn.setRequestMethod("POST");

                conn.setRequestProperty("Content-Type", mimeType);
                conn.setRequestProperty("Content-Length", Integer.toString(contentStream.available()));

                conn.connect();

                dos = new DataOutputStream(conn.getOutputStream() );

                // create a buffer of maximum size

                totalSize = bytesAvailable = contentStream.available();
                totalWritten = 0;
                bufferSize = Math.min(bytesAvailable, maxBufferSize);
                buffer = new byte[bufferSize];

                Log.d(LOG_TAG, "Start Upload (" + totalSize + " bytes)");

                // read file and write it into form...
                bytesRead = contentStream.read(buffer, 0, bufferSize);

                while (bytesRead > 0) {
                    dos.write(buffer, 0, bufferSize);
                    totalWritten += bufferSize;
                    bytesAvailable = contentStream.available();
                    bufferSize = Math.min(bytesAvailable, maxBufferSize);

                    mProgress = (totalWritten * 100 / totalSize);
                    Log.d(LOG_TAG, "Upload " + " : " + mProgress);
                    publishProgress(mProgress);

                    bytesRead = contentStream.read(buffer, 0, bufferSize);
                }

                // close streams
                contentStream.close();
                dos.flush();
                dos.close();

                // Read the SERVER RESPONSE
                int status = conn.getResponseCode();

                Log.d(LOG_TAG, "Upload is done with response code" + status);

                if (status == 200) {
                    InputStream is = conn.getInputStream();
                    int ch;
                    StringBuffer b = new StringBuffer();
                    while ((ch = is.read()) != -1) {
                        b.append((char) ch);
                    }
                    responseFromServer = b.toString();
                    is.close();
                }
                else {
                    Log.e(LOG_TAG, "Error: Upload returned " + status + " status code");
                    return null;
                }

            }
            catch (Exception e) {
                Log.e(LOG_TAG, "Error: " + e.getMessage());
            }

            return responseFromServer;
        }
        @Override
        protected void onProgressUpdate(Integer... progress) {
            super.onProgressUpdate(progress);
            Log.d(LOG_TAG, "UI Upload " + mHsUri + " : " + mProgress);
            callback.onUploadProgress(mUploadId, progress[0]);
        }

        @Override
        protected void onPostExecute(String s) {
            callback.onUploadComplete((s == null) ? null : JsonUtils.toContentResponse(s));
        }
    }
}
