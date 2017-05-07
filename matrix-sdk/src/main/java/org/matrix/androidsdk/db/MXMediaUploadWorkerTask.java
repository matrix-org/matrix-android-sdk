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
import android.os.Looper;
import android.util.Pair;

import org.matrix.androidsdk.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.matrix.androidsdk.listeners.IMXMediaUploadListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.ContentResponse;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.ssl.CertUtil;
import org.matrix.androidsdk.util.ContentManager;
import org.matrix.androidsdk.util.JsonUtils;

import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

/**
 * Private AsyncTask used to upload files.
 */
public class MXMediaUploadWorkerTask extends AsyncTask<Void, IMXMediaUploadListener.UploadStats, String> {

    private static final String LOG_TAG = "MXMediaUploadWorkerTask";

    // upload ID -> task
    private static final HashMap<String, MXMediaUploadWorkerTask> mPendingUploadByUploadId = new HashMap<>();

    // progress listener
    private ArrayList<IMXMediaUploadListener> mUploadListeners = new ArrayList<>();

    // the upload stats
    private IMXMediaUploadListener.UploadStats mUploadStats;

    // the media mimeType
    private final String mMimeType;

    // the media to upload
    private final InputStream mContentStream;

    // its unique identifier
    private final String mUploadId;

    // store the server response to provide it the listeners
    private String mResponseFromServer = null;

    // tells if the current upload has been cancelled.
    private boolean mIsCancelled = false;

    /**
     * Tells if the upload has been completed
     */
    private boolean mIsDone = false;

    // upload const
    private static final int UPLOAD_BUFFER_READ_SIZE = 1024 * 32;

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

    // the upload server HTTP response code
    private int mResponseCode = -1;

    // the media file name
    private String mFilename = null;

    // the content manager
    private final ContentManager mContentManager;

    /**
     * Check if there is a pending download for the url.
     * @param uploadId The id to check the existence
     * @return the dedicated BitmapWorkerTask if it exists.
     */
    public static MXMediaUploadWorkerTask getMediaDUploadWorkerTask(String uploadId) {
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
                task.cancelUpload();
                task.cancel(true);
            } catch (Exception e) {
                Log.e(LOG_TAG, "cancelPendingUploads " + e.getLocalizedMessage());
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
            Log.e(LOG_TAG, "MXMediaUploadWorkerTask " + e.getLocalizedMessage());
        }

        if ((null != listener) && (mUploadListeners.indexOf(listener) < 0)) {
            mUploadListeners.add(listener);
        }
        mMimeType = mimeType;
        mContentStream = contentStream;
        mUploadId = uploadId;
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
     * @return the upload progress
     */
    public int getProgress() {
        if (null != mUploadStats) {
            return mUploadStats.mProgress;
        }
        return -1;
    }

    /**
     * @return the upload stats
     */
    public IMXMediaUploadListener.UploadStats getStats() {
        return mUploadStats;
    }

    /**
     * @return true if the current upload has been cancelled.
     */
    private synchronized boolean isUploadCancelled() {
        return mIsCancelled;
    }

    /**
     * Cancel the current upload.
     */
    public synchronized void cancelUpload() {
        mIsCancelled = true;
    }

    /**
     * refresh the progress info
     */
    private void publishProgress(long startUploadTime) {
        mUploadStats.mElapsedTime = (int)((System.currentTimeMillis() - startUploadTime) / 1000);

        if (0 != mUploadStats.mFileSize) {
            // Uploading data is 90% of the job
            // the other 10s is the end of the connection related actions
            mUploadStats.mProgress = (int) (((long) mUploadStats.mUploadedSize) * 96 / mUploadStats.mFileSize);
        }

        // avoid zero div
        if (System.currentTimeMillis() != startUploadTime) {
            mUploadStats.mBitRate = (int)(((long)mUploadStats.mUploadedSize)  * 1000 / (System.currentTimeMillis() - startUploadTime) / 1024);
        } else {
            mUploadStats.mBitRate = 0;
        }

        if (0 != mUploadStats.mBitRate) {
            mUploadStats.mEstimatedRemainingTime = (mUploadStats.mFileSize - mUploadStats.mUploadedSize) / 1024 / mUploadStats.mBitRate;
        } else {
            mUploadStats.mEstimatedRemainingTime = -1;
        }

        publishProgress(mUploadStats);
    }

    @Override
    protected String doInBackground(Void... params) {
        HttpURLConnection conn;
        DataOutputStream dos;

        mResponseCode = -1;

        int bytesRead, bytesAvailable;
        int totalWritten, totalSize;
        int bufferSize;
        byte[] buffer;

        String serverResponse = null;
        String urlString = mContentManager.getHsConfig().getHomeserverUri().toString() + ContentManager.URI_PREFIX_CONTENT_API + "/upload?access_token=" + mContentManager.getHsConfig().getCredentials().accessToken;

        if (null != mFilename) {
            try {
                String utf8Filename =  URLEncoder.encode(mFilename, "utf-8");
                urlString += "&filename=" + utf8Filename;
            } catch (Exception e) {
                Log.e(LOG_TAG, "doInBackground " + e.getLocalizedMessage());
            }
        }

        try {
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
                    Pair<SSLSocketFactory, X509TrustManager> pair = CertUtil.newPinnedSSLSocketFactory(mContentManager.getHsConfig());
                    sslConn.setSSLSocketFactory(pair.first);
                    sslConn.setHostnameVerifier(CertUtil.newHostnameVerifier(mContentManager.getHsConfig()));
                } catch (Exception e) {
                    Log.e(LOG_TAG, "sslConn " + e.getLocalizedMessage());
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
            bufferSize = Math.min(bytesAvailable, UPLOAD_BUFFER_READ_SIZE);
            buffer = new byte[bufferSize];

            mUploadStats = new IMXMediaUploadListener.UploadStats();
            mUploadStats.mUploadId = mUploadId;
            mUploadStats.mProgress = 0;
            mUploadStats.mUploadedSize = 0;
            mUploadStats.mFileSize = totalSize;
            mUploadStats.mElapsedTime = 0;
            mUploadStats.mEstimatedRemainingTime = -1;
            mUploadStats.mBitRate = 0;

            final long startUploadTime = System.currentTimeMillis();

            Log.d(LOG_TAG, "doInBackground : start Upload (" + totalSize + " bytes)");

            // read file and write it into form...
            bytesRead = mContentStream.read(buffer, 0, bufferSize);

            dispatchOnUploadStart();

            final android.os.Handler uiHandler = new android.os.Handler(Looper.getMainLooper());

            final Timer refreshTimer = new Timer();

            uiHandler.post(new Runnable() {
                @Override
                public void run() {
                    refreshTimer.scheduleAtFixedRate(new TimerTask() {
                        @Override
                        public void run() {
                            uiHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (!mIsDone) {
                                        publishProgress(startUploadTime);
                                    }
                                }
                            });
                        }
                    }, new java.util.Date(), 100);

                }
            });

            while ((bytesRead > 0) && !isUploadCancelled()) {
                dos.write(buffer, 0, bytesRead);
                totalWritten += bytesRead;
                bytesAvailable = mContentStream.available();
                bufferSize = Math.min(bytesAvailable, UPLOAD_BUFFER_READ_SIZE);

                Log.d(LOG_TAG, "doInBackground : totalWritten " + totalWritten + " / totalSize " + totalSize);
                mUploadStats.mUploadedSize = totalWritten;
                bytesRead = mContentStream.read(buffer, 0, bufferSize);
            }
            mIsDone = true;
            uiHandler.post(new Runnable() {
                @Override
                public void run() {
                    refreshTimer.cancel();
                }
            });

            if (!isUploadCancelled()) {
                mUploadStats.mProgress = 96;
                publishProgress(startUploadTime);
                dos.flush();
                mUploadStats.mProgress = 97;
                publishProgress(startUploadTime);
                dos.close();
                mUploadStats.mProgress = 98;
                publishProgress(startUploadTime);

                try {
                    // Read the SERVER RESPONSE
                    mResponseCode = conn.getResponseCode();
                } catch (EOFException eofEx) {
                    mResponseCode = HttpURLConnection.HTTP_INTERNAL_ERROR;
                }

                mUploadStats.mProgress = 99;
                publishProgress(startUploadTime);

                Log.d(LOG_TAG, "doInBackground : Upload is done with response code " + mResponseCode);

                InputStream is;

                if (mResponseCode == HttpURLConnection.HTTP_OK) {
                    is = conn.getInputStream();
                } else {
                    is = conn.getErrorStream();
                }

                int ch;
                StringBuffer b = new StringBuffer();
                while ((ch = is.read()) != -1) {
                    b.append((char) ch);
                }
                serverResponse = b.toString();
                is.close();

                // the server should provide an error description
                if (mResponseCode != HttpURLConnection.HTTP_OK) {
                    try {
                        JSONObject responseJSON = new JSONObject(serverResponse);
                        serverResponse = responseJSON.getString("error");
                    } catch (JSONException e) {
                        Log.e(LOG_TAG, "doInBackground : Error parsing " + e.getLocalizedMessage());
                    }
                }
            } else {
                dos.flush();
                dos.close();
            }

            if (null != conn) {
                conn.disconnect();
            }
        } catch (Exception e) {
            serverResponse = e.getLocalizedMessage();
            Log.e(LOG_TAG, "doInBackground ; failed with error " + e.getClass() + " - " + e.getMessage());
        }

        mResponseFromServer = serverResponse;

        return serverResponse;
    }
    
    @Override
    protected void onProgressUpdate(IMXMediaUploadListener.UploadStats ... progress) {
        super.onProgressUpdate(progress);

        Log.d(LOG_TAG, "Upload " + this + " : " + mUploadStats.mProgress);

        dispatchOnUploadProgress(mUploadStats);
    }

    /**
     * Dispatch the result to the callbacks
     * @param serverResponse the server response
     */
    private void dispatchResult(final String serverResponse) {
        if (null != mUploadId) {
            mPendingUploadByUploadId.remove(mUploadId);
        }

        mContentManager.getUnsentEventsManager().onEventSent(mApiCallback);

        // close the source stream
        try {
            mContentStream.close();
        } catch (Exception e) {
            Log.e(LOG_TAG, "dispatchResult " + e.getLocalizedMessage());
        }

        if (isUploadCancelled()) {
            dispatchOnUploadCancel();
        } else {
            ContentResponse uploadResponse = ((mResponseCode != 200) || (serverResponse == null)) ? null : JsonUtils.toContentResponse(serverResponse);

            if ((null == uploadResponse) || (null == uploadResponse.contentUri)) {
                dispatchOnUploadError(mResponseCode, serverResponse);
            } else {
                dispatchOnUploadComplete(uploadResponse.contentUri);
            }
        }
    }

    @Override
    protected void onPostExecute(final String serverResponseMessage) {
        // do not call the callback if cancelled.
        if (!isCancelled()) {
            dispatchResult(serverResponseMessage);
        }
    }

    //==============================================================================================================
    // Dispatchers
    //==============================================================================================================

    /**
     * Dispatch Upload start
     */
    private void dispatchOnUploadStart() {
        for(IMXMediaUploadListener listener : mUploadListeners) {
            try {
                listener.onUploadStart(mUploadId);

            } catch (Exception e) {
                Log.e(LOG_TAG, "dispatchOnUploadStart failed " + e.getLocalizedMessage());
            }
        }
    }

    /**
     * Dispatch Upload start
     * @param stats the upload stats
     */
    private void dispatchOnUploadProgress(IMXMediaUploadListener.UploadStats stats) {
        for(IMXMediaUploadListener listener : mUploadListeners) {
            try {
                listener.onUploadProgress(mUploadId, stats);
            } catch (Exception e) {
                Log.e(LOG_TAG, "dispatchOnUploadProgress failed " + e.getLocalizedMessage());
            }
        }
    }

    /**
     * Dispatch Upload cancel.
     */
    private void dispatchOnUploadCancel() {
        for(IMXMediaUploadListener listener : mUploadListeners) {
            try {
                listener.onUploadCancel(mUploadId);
            } catch (Exception e) {
                Log.e(LOG_TAG, "listener failed " + e.getLocalizedMessage());
            }
        }
    }

    /**
     * Dispatch Upload error.
     * @param serverResponseCode the server response code.
     * @param serverErrorMessage the server error message
     */
    private void dispatchOnUploadError(int serverResponseCode, String serverErrorMessage) {
        for(IMXMediaUploadListener listener : mUploadListeners) {
            try {
                listener.onUploadError(mUploadId, serverResponseCode, serverErrorMessage);
            } catch (Exception e) {
                Log.e(LOG_TAG, "dispatchOnUploadError failed " + e.getLocalizedMessage());
            }
        }
    }

    /**
     * Dispatch Upload complete.
     * @param contentUri the media uri.
     */
    private void dispatchOnUploadComplete(String contentUri) {
        for(IMXMediaUploadListener listener : mUploadListeners) {
            try {
                listener.onUploadComplete(mUploadId, contentUri);
            } catch (Exception e) {
                Log.e(LOG_TAG, "dispatchOnUploadComplete failed " + e.getLocalizedMessage());
            }
        }
    }
}