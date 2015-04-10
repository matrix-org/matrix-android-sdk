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

import org.matrix.androidsdk.listeners.IMXNetworkEventListener;
import org.matrix.androidsdk.network.NetworkConnectivityReceiver;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.RestAdapterCallback;
import org.matrix.androidsdk.rest.model.ContentResponse;
import org.matrix.androidsdk.rest.model.ImageInfo;
import org.matrix.androidsdk.rest.model.MatrixError;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

import javax.net.ssl.SSLException;

/**
 * Class for accessing content from the current session.
 */
public class ContentManager {

    public static final String MATRIX_CONTENT_URI_SCHEME = "mxc://";

    public static final String METHOD_CROP = "crop";
    public static final String METHOD_SCALE = "scale";

    private static final String URI_PREFIX_CONTENT_API = "/_matrix/media/v1";

    private static final String LOG_TAG = "ContentManager";

    // home server Uri
    private String mHsUri;

    // user access token
    private String mAccessToken;

    // unsent events manager
    // it will restart the the upload if it fails
    private UnsentEventsManager mUnsentEventsManager;

    // upload ID -> task
    private static HashMap<String, ContentUploadTask> mPendingUploadByUploadId = new HashMap<String, ContentUploadTask>();

    /**
     * Interface to implement to get the mxc URI of uploaded content.
     */
    public static interface UploadCallback {
        /**
         * Warn of the progress upload
         * @param uploadId the upload Identifier
         * @param percentageProgress the progress value
         */
        public void onUploadProgress(String uploadId, int percentageProgress);

        /**
         * Called when the upload is complete or has failed.
         * @param uploadResponse the ContentResponse object containing the mxc URI or null if the upload failed
         */
        public void onUploadComplete(String uploadId, ContentResponse uploadResponse);
    }

    /**
     * Default constructor.
     * @param hsUri the home server URL
     * @param accessToken the user's access token
     */
    public ContentManager(String hsUri, String accessToken, UnsentEventsManager unsentEventsManager) {
        mHsUri = hsUri;
        mAccessToken = accessToken;
        mUnsentEventsManager = unsentEventsManager;
    }

    /**
     * Clear the content content
     */
    public void clear() {
        Collection<ContentUploadTask> tasks = mPendingUploadByUploadId.values();

        // cancels the running task
        for(ContentUploadTask task : tasks) {
            try {
                task.cancel(true);
            } catch (Exception e) {
            }
        }

        mPendingUploadByUploadId.clear();
    }

    public static String getIdenticonURL(String userId) {
        // sanity check
        if (null != userId) {
            String urlEncodedUser = null;
            try {
                urlEncodedUser = java.net.URLEncoder.encode(userId, "UTF-8");
            } catch (Exception e) {
            }

            return ContentManager.MATRIX_CONTENT_URI_SCHEME + "identicon/" + urlEncodedUser;
        }

        return null;
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
     */
    public void uploadContent(InputStream contentStream, String mimeType, String uploadId, UploadCallback callback) {
        try {
            new ContentUploadTask(contentStream, mimeType, callback, uploadId).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        } catch (Exception e) {
            // cannot start the task
            callback.onUploadComplete(uploadId, null);
        }
    }

    /**
     * Returns the upload progress (percentage) for a dedicated uploadId
     * @param uploadId The uploadId.
     * @return the upload percentage. -1 means there is no pending upload.
     */
    public int getUploadProgress(String uploadId) {
        if ((null != uploadId) && mPendingUploadByUploadId.containsKey(uploadId)) {
            return mPendingUploadByUploadId.get(uploadId).getProgress();
        }
        return -1;
    }

    /**
     * Add an upload listener for an uploadId.
     * @param uploadId The uploadId.
     * @param callback the async callback returning a mxc: URI to access the uploaded file
     */
    public void addUploadListener(String uploadId, UploadCallback callback) {
        if ((null != uploadId) && mPendingUploadByUploadId.containsKey(uploadId)) {
            mPendingUploadByUploadId.get(uploadId).addCallback(callback);
        }
    }

    /**
     * Private AsyncTask used to upload files.
     */
    private class ContentUploadTask extends AsyncTask<Void, Integer, String> {
        // progress callbacks
        private ArrayList<UploadCallback> mCallbacks = new ArrayList<UploadCallback>();

        // the progress rate
        private int mProgress = 0;

        // the media mimeType
        private String mimeType;

        // the media to upload
        private InputStream contentStream;

        // its unique identifier
        private String mUploadId;

        // the upload exception
        private Exception mFailureException;

        // store the server response to provide it the listeners
        private String mResponseFromServer = null;

        // dummy ApiCallback uses to be warned when the upload must be declared as "undeliverable".
        private ApiCallback mApiCallback;

        /**
         * Public constructor
         * @param contentStream the stream to upload
         * @param mimeType the mime type
         * @param callback the upload callback
         * @param uploadId the upload Identifier
         */
        public ContentUploadTask(InputStream contentStream, String mimeType, UploadCallback callback, String uploadId) {

            try {
                contentStream.reset();
            } catch (Exception e) {

            }

            if (mCallbacks.indexOf(callback) < 0) {
                mCallbacks.add(callback);
            }
            this.mimeType = mimeType;
            this.contentStream = contentStream;
            this.mUploadId = uploadId;
            this.mFailureException = null;

            // dummy callback to be warned that the upload must be cancelled.
            mApiCallback = new ApiCallback() {
                @Override
                public void onSuccess(Object info) {

                }

                @Override
                public void onNetworkError(Exception e) {

                }

                @Override
                public void onMatrixError(MatrixError e) {

                }

                @Override
                public void onUnexpectedError(Exception e) {
                    dispatchResult(mResponseFromServer);
                }
            };

            if (null != uploadId) {
                mPendingUploadByUploadId.put(uploadId, this);
            }
        }

        /**
         * Private contrustor.
         * @param contentStream the stream to upload
         * @param mimeType the mime type
         * @param someCallbacks the callbacks list
         * @param uploadId the upload Identifier
         * @param apiCallback the dummy apicallback (it is used as identifier by the contentManager)
         */
        private ContentUploadTask(InputStream contentStream, String mimeType, ArrayList<UploadCallback> someCallbacks, String uploadId, ApiCallback apiCallback) {

            try {
                contentStream.reset();
            } catch (Exception e) {

            }

            this.mApiCallback = apiCallback;
            this.mCallbacks = someCallbacks;
            this.mimeType = mimeType;
            this.contentStream = contentStream;
            this.mUploadId = uploadId;
            this.mFailureException = null;

            if (null != uploadId) {
                mPendingUploadByUploadId.put(uploadId, this);
            }
        }

        /**
         * Add a callback to the callbacks list
         * @param callback
         */
        public void addCallback(UploadCallback callback) {
            mCallbacks.add(callback);
        }

        /**
         * @return the upload progress
         */
        public int getProgress() {
            return mProgress;
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

                    // assume that the data upload is 90 % of the time
                    // closing the stream requires also some 100ms
                    mProgress = (totalWritten * 90 / totalSize) ;

                    Log.d(LOG_TAG, "Upload " + " : " + mProgress);
                    publishProgress(mProgress);

                    bytesRead = contentStream.read(buffer, 0, bufferSize);
                }
                publishProgress(mProgress = 92);
                dos.flush();
                publishProgress(mProgress = 94);
                dos.close();
                publishProgress(mProgress = 96);

                // Read the SERVER RESPONSE
                int status = conn.getResponseCode();

                publishProgress(mProgress = 98);

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
                mFailureException = e;
                Log.e(LOG_TAG, "Error: " + e.getMessage());
            }

            return responseFromServer;
        }
        @Override
        protected void onProgressUpdate(Integer... progress) {
            super.onProgressUpdate(progress);
            Log.d(LOG_TAG, "UI Upload " + mHsUri + " : " + mProgress);

            for (UploadCallback callback : mCallbacks) {
                try {
                    callback.onUploadProgress(mUploadId, progress[0]);
                } catch (Exception e) {
                }
            }
        }

        /**
         * Dispatch the result to the callbacks
         * @param s the server response
         */
        private void dispatchResult(final String s) {
            if (null != mUploadId) {
                mPendingUploadByUploadId.remove(mUploadId);
            }
            mUnsentEventsManager.onEventSent(mApiCallback);

            // close the source stream
            try {
                contentStream.close();
            } catch (Exception e) {
            }

            ContentResponse uploadResponse = (s == null) ? null : JsonUtils.toContentResponse(s);

            for (UploadCallback callback : mCallbacks) {
                try {
                    callback.onUploadComplete(mUploadId, uploadResponse);
                } catch (Exception e) {
                }
            }
        }

        @Override
        protected void onPostExecute(final String s) {
            // do not call the callback if cancelled.
            if (!isCancelled()) {
                // connection error
                if ((null != mFailureException) && ((mFailureException instanceof UnknownHostException) || (mFailureException instanceof SSLException))) {
                    mResponseFromServer = s;
                    // public void onEventSendingFailed(final RetrofitError retrofitError, final ApiCallback apiCallback, final RestAdapterCallback.RequestRetryCallBack requestRetryCallBack) {
                    mUnsentEventsManager.onEventSendingFailed(null, null, mApiCallback,  new RestAdapterCallback.RequestRetryCallBack() {
                        @Override
                        public void onRetry() {
                            try {
                                ContentUploadTask task = new ContentUploadTask(contentStream, mimeType, mCallbacks, mUploadId, mApiCallback);
                                mPendingUploadByUploadId.put(mUploadId, task);
                                task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                            } catch (Exception e) {
                                // cannot start the task
                                dispatchResult(s);
                            }
                        }
                    });
                } else {
                    dispatchResult(s);
                }
            }
        }
    }
}
