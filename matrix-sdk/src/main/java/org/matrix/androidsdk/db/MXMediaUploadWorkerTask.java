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

package org.matrix.androidsdk.db;

import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.matrix.androidsdk.HomeserverConnectionConfig;
import org.matrix.androidsdk.listeners.IMXMediaUploadListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.RestAdapterCallback;
import org.matrix.androidsdk.rest.model.ContentResponse;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.ssl.CertUtil;
import org.matrix.androidsdk.util.ContentManager;
import org.matrix.androidsdk.util.JsonUtils;
import org.matrix.androidsdk.util.UnsentEventsManager;

import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLException;

/**
 * Private AsyncTask used to upload files.
 */
public class MXMediaUploadWorkerTask extends AsyncTask<Void, Integer, String> {

    private static final String LOG_TAG = "MXMediaUploadWorkerTask";

    // upload ID -> task
    private static final HashMap<String, MXMediaUploadWorkerTask> mPendingUploadByUploadId = new HashMap<>();

    // progress listener
    private ArrayList<IMXMediaUploadListener> mUploadListeners = new ArrayList<>();

    // the progress rate
    private int mProgress = 0;

    // the media mimeType
    private String mMimeType;

    // the media to upload
    private InputStream mContentStream;

    // its unique identifier
    private String mUploadId;

    // the upload exception
    private Exception mFailureException;

    // store the server response to provide it the listeners
    private String mResponseFromServer = null;

    // tells if the current upload has been cancelled.
    private boolean mIsCancelled = false;

    // dummy ApiCallback uses to be warned when the upload must be declared as "undeliverable".
    private ApiCallback mApiCallback = new ApiCallback() {
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

    // the upload server response code
    private int mResponseCode = -1;

    // the media file name
    private String mFilename = null;

    // the content manager
    private ContentManager mContentManager;

    /**
     * Check if there is a pending download for the url.
     * @param uploadId The id to check the existence
     * @return the dedicated BitmapWorkerTask if it exists.
     */
    public static MXMediaUploadWorkerTask getMediaDUploadWorkerTaskForId(String uploadId) {
        if ((uploadId != null) &&  mPendingUploadByUploadId.containsKey(uploadId)) {
            MXMediaUploadWorkerTask task;
            synchronized(mPendingUploadByUploadId) {
                task = mPendingUploadByUploadId.get(uploadId);
            }
            return task;
        } else {
            return null;
        }
    }

    /**
     * Cancel the pending uploads.
     */
    public static void cancelPendingUploads() {
        Collection<MXMediaUploadWorkerTask> tasks = mPendingUploadByUploadId.values();

        // cancels the running task
        for(MXMediaUploadWorkerTask task : tasks) {
            try {
                task.cancel(true);
            } catch (Exception e) {
            }
        }

        mPendingUploadByUploadId.clear();
    }

    /**
     * Constructor
     * @param contentManager the content manager
     * @param contentStream the stream to upload
     * @param mimeType the mime type
     * @param uploadId the upload id
     * @param filename the dest filename
     * @param listener the upload listener
     */
    public MXMediaUploadWorkerTask(ContentManager contentManager, InputStream contentStream, String mimeType, String uploadId, String filename, IMXMediaUploadListener listener) {
        try {
            contentStream.reset();
        } catch (Exception e) {

        }

        if ((null != listener) && (mUploadListeners.indexOf(listener) < 0)) {
            mUploadListeners.add(listener);
        }
        mMimeType = mimeType;
        mContentStream = contentStream;
        mUploadId = uploadId;
        mFailureException = null;
        mFilename = filename;
        mContentManager = contentManager;

        if (null != uploadId) {
            mPendingUploadByUploadId.put(uploadId, this);
        }
    }

    /**
     * Add an upload listener
     * @param aListener the listener to add.
     */
    public void addListener(IMXMediaUploadListener aListener) {
        if ((null != aListener) && (mUploadListeners.indexOf(aListener) < 0)) {
            mUploadListeners.add(aListener);
        }
    }

    /**
     * Create a deep of the current instance.
     * @return the deep copy.
     */
    private MXMediaUploadWorkerTask deepCopy() {
        MXMediaUploadWorkerTask copy = new MXMediaUploadWorkerTask(mContentManager, mContentStream, mMimeType, mUploadId, mFilename, null);
        copy.mUploadListeners = new ArrayList<>(mUploadListeners);
        copy.mApiCallback = mApiCallback;

        return copy;
    }

    /**
     * @return the upload progress
     */
    public int getProgress() {
        return mProgress;
    }

    /**
     * @return true if the current upload has been cancelled.
     */
    public synchronized boolean isUploadCancelled() {
        return mIsCancelled;
    }

    /**
     * Cancel the current upload.
     */
    public synchronized void cancelUpload() {
        mIsCancelled = true;
    }

    @Override
    protected String doInBackground(Void... params) {
        HttpURLConnection conn;
        DataOutputStream dos;

        mResponseCode = -1;

        int bytesRead, bytesAvailable, bufferSize, totalWritten, totalSize;
        byte[] buffer;
        int maxBufferSize = 1024 * 32;

        String responseFromServer = null;
        String urlString = mContentManager.getHsConfig().getHomeserverUri().toString() + ContentManager.URI_PREFIX_CONTENT_API + "/upload?access_token=" + mContentManager.getHsConfig().getCredentials().accessToken;

        if (null != mFilename) {
            try {
                String utf8Filename =  URLEncoder.encode(mFilename, "utf-8");
                urlString += "&filename=" + utf8Filename;
            } catch (Exception e) {
            }
        }

        try
        {
            URL url = new URL(urlString);

            conn = (HttpURLConnection) url.openConnection();
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            conn.setRequestMethod("POST");

            if (conn instanceof HttpsURLConnection) {
                // Add SSL Socket factory.
                HttpsURLConnection sslConn = (HttpsURLConnection) conn;
                try {
                    sslConn.setSSLSocketFactory(CertUtil.newPinnedSSLSocketFactory(mContentManager.getHsConfig()));
                    sslConn.setHostnameVerifier(CertUtil.newHostnameVerifier(mContentManager.getHsConfig()));
                } catch (Exception e) {
                }
            }

            conn.setRequestProperty("Content-Type", mMimeType);
            conn.setRequestProperty("Content-Length", Integer.toString(mContentStream.available()));
            // avoid caching data before really sending them.
            conn.setFixedLengthStreamingMode(mContentStream.available());

            conn.connect();

            dos = new DataOutputStream(conn.getOutputStream());

            // create a buffer of maximum size

            totalSize = bytesAvailable = mContentStream.available();
            totalWritten = 0;
            bufferSize = Math.min(bytesAvailable, maxBufferSize);
            buffer = new byte[bufferSize];

            Log.d(LOG_TAG, "Start Upload (" + totalSize + " bytes)");

            // read file and write it into form...
            bytesRead = mContentStream.read(buffer, 0, bufferSize);

            dispatchUploadStart();

            while ((bytesRead > 0) && !isUploadCancelled()) {
                dos.write(buffer, 0, bufferSize);
                totalWritten += bufferSize;
                bytesAvailable = mContentStream.available();
                bufferSize = Math.min(bytesAvailable, maxBufferSize);

                // assume that the data upload is 90 % of the time
                // closing the stream requires also some 100ms
                mProgress = (totalWritten * 90 / totalSize) ;

                Log.d(LOG_TAG, "Upload " + " : " + mProgress);
                publishProgress(mProgress);

                bytesRead = mContentStream.read(buffer, 0, bufferSize);
            }
            if (!isUploadCancelled()) {
                publishProgress(mProgress = 92);
                dos.flush();
                publishProgress(mProgress = 94);
                dos.close();
                publishProgress(mProgress = 96);

                try {
                    // Read the SERVER RESPONSE
                    mResponseCode = conn.getResponseCode();
                } catch (EOFException eofEx) {
                    mResponseCode = 500;
                }

                publishProgress(mProgress = 98);

                Log.d(LOG_TAG, "Upload is done with response code" + mResponseCode);

                InputStream is;

                if (mResponseCode == 200) {
                    is = conn.getInputStream();
                } else {
                    is = conn.getErrorStream();
                }

                int ch;
                StringBuffer b = new StringBuffer();
                while ((ch = is.read()) != -1) {
                    b.append((char) ch);
                }
                responseFromServer = b.toString();
                is.close();

                // the server should provide an error description
                if (mResponseCode != 200) {
                    try {
                        JSONObject responseJSON = new JSONObject(responseFromServer);
                        responseFromServer = responseJSON.getString("error");
                    } catch (JSONException e) {
                    }
                }
            } else {
                dos.flush();
                dos.close();
            }
        } catch (Exception e) {
            mFailureException = e;
            Log.e(LOG_TAG, "Error: " + e.getClass() + " - " + e.getMessage());
        }

        return responseFromServer;
    }
    @Override
    protected void onProgressUpdate(Integer... progress) {
        super.onProgressUpdate(progress);

        Log.d(LOG_TAG, "Upload " + this + " : " + mProgress);

        dispatchUploadProgress(progress[0]);
    }

    /**
     * Dispatch the result to the callbacks
     * @param s the server response
     */
    private void dispatchResult(final String s) {
        if (null != mUploadId) {
            mPendingUploadByUploadId.remove(mUploadId);
        }

        mContentManager.getUnsentEventsManager().onEventSent(mApiCallback);

        // close the source stream
        try {
            mContentStream.close();
        } catch (Exception e) {
        }

        if (isUploadCancelled()) {
            dispatchUploadCancel();
        } else {
            ContentResponse uploadResponse = ((mResponseCode != 200) || (s == null)) ? null : JsonUtils.toContentResponse(s);
            dispatchUploadComplete(uploadResponse, s);
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
                mContentManager.getUnsentEventsManager().onEventSendingFailed(null, null, mApiCallback,  new RestAdapterCallback.RequestRetryCallBack() {
                    @Override
                    public void onRetry() {
                        try {MXMediaUploadWorkerTask task = deepCopy();
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

    //==============================================================================================================
    // Dispatchers
    //==============================================================================================================

    /**
     * Dispatch Upload start
     */
    private void dispatchUploadStart() {
        for(IMXMediaUploadListener listener : mUploadListeners) {
            try {
                listener.onUploadStart(mUploadId);

            } catch (Exception e) {
                Log.e(LOG_TAG, "dispatchUploadStart failed " + e.getLocalizedMessage());
            }
        }
    }

    /**
     * Dispatch Upload start
     * @param progress the progress valie
     */
    private void dispatchUploadProgress(int progress) {
        for(IMXMediaUploadListener listener : mUploadListeners) {
            try {
                listener.onUploadProgress(mUploadId, progress);
            } catch (Exception e) {
                Log.e(LOG_TAG, "dispatchUploadProgress failed " + e.getLocalizedMessage());
            }
        }
    }

    /**
     * Dispatch Upload cancel.
     */
    private void dispatchUploadCancel() {
        for(IMXMediaUploadListener listener : mUploadListeners) {
            try {
                listener.onUploadCancel(mUploadId);
            } catch (Exception e) {
                Log.e(LOG_TAG, "dispatchUploadCancel failed " + e.getLocalizedMessage());
            }
        }
    }

    /**
     * Dispatch Upload complete.
     * @param uploadResponse the upload server response.
     * @param errorMessage the error description
     */
    private void dispatchUploadComplete(ContentResponse uploadResponse, String errorMessage) {
        for(IMXMediaUploadListener listener : mUploadListeners) {
            try {
                listener.onUploadComplete(mUploadId, uploadResponse, mResponseCode, (mResponseCode != 200) ? errorMessage : null);
            } catch (Exception e) {
                Log.e(LOG_TAG, "dispatchUploadCancel failed " + e.getLocalizedMessage());
            }
        }
    }
}