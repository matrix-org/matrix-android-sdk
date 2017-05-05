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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Looper;
import android.support.v4.util.LruCache;
import android.text.TextUtils;
import org.matrix.androidsdk.util.Log;

import android.util.Pair;
import android.webkit.MimeTypeMap;
import android.widget.ImageView;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import org.matrix.androidsdk.HomeserverConnectionConfig;
import org.matrix.androidsdk.crypto.MXEncryptedAttachments;
import org.matrix.androidsdk.listeners.IMXMediaDownloadListener;
import org.matrix.androidsdk.rest.model.EncryptedFileInfo;
import org.matrix.androidsdk.ssl.CertUtil;
import org.matrix.androidsdk.util.ImageUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

/**
 * This class manages the media downloading in background.
 */
class MXMediaDownloadWorkerTask extends AsyncTask<Integer, IMXMediaDownloadListener.DownloadStats, Void> {

    private static final String LOG_TAG = "MXMediaDwndWorkerTk";

    /**
     * Pending media URLs
     */
    private static final HashMap<String, MXMediaDownloadWorkerTask> mPendingDownloadByUrl = new HashMap<>();

    /**
     * List of unreachable media urls.
     */
    private static final ArrayList<String> mUnreachableUrls = new ArrayList<>();

    // avoid sync on "this" because it might differ if there is a timer.
    private static final Object mSyncObject = new Object();

    /**
     * The medias cache
     */
    private static LruCache<String, Bitmap> mBitmapByUrlCache = null;

    /**
     * The downloaded media callbacks.
     */
    private final ArrayList<IMXMediaDownloadListener> mDownloadListeners = new ArrayList<>();

    /**
     * The ImageView list to refresh when the media is downloaded.
     */
    private final ArrayList<WeakReference<ImageView>> mImageViewReferences;

    /**
     * The media URL.
     */
    private String mUrl;

    /**
     * The media mime type
     */
    private String mMimeType;

    /**
     * The application context
     */
    private Context mApplicationContext;

    /**
     * The directory in which the media must be stored.
     */
    private File mDirectoryFile = null;

    /**
     * The rotation to apply.
     */
    private int mRotation = 0;

    /**
     * The download stats.
     */
    private IMXMediaDownloadListener.DownloadStats mDownloadStats;

    /**
     * Tells the download has been cancelled.
     */
    private boolean mIsDownloadCancelled = false;

    /**
     * Tells if the download has been completed
     */
    private boolean mIsDone = false;

    /**
     * Error message.
     */
    private JsonElement mErrorAsJsonElement;

    /**
     * The home server config.
     */
    private final HomeserverConnectionConfig mHsConfig;

    /**
     * The bitmap to use when the URL is unreachable.
     */
    private Bitmap mDefaultBitmap;

    /**
     * the encrypted file information
     */
    private EncryptedFileInfo mEncryptedFileInfo;

    /**
     * Download constants
     */
    private static final int DOWNLOAD_TIME_OUT = 10 * 1000;
    private static final int DOWNLOAD_BUFFER_READ_SIZE = 1024 * 32;


    //==============================================================================================================
    // static methods
    //==============================================================================================================

    /**
     * Clear the internal cache.
     */
    public static void clearBitmapsCache() {
        // sMemoryCache can be null if no bitmap have been downloaded.
        if (null != mBitmapByUrlCache) {
            mBitmapByUrlCache.evictAll();
        }
    }

    /**
     * Check if there is a pending download for the url.
     * @param url The url to check the existence
     * @return the dedicated MXMediaDownloadWorkerTask if it exists.
     */
    public static MXMediaDownloadWorkerTask getMediaDownloadWorkerTask(String url) {
        if ((url != null) &&  mPendingDownloadByUrl.containsKey(url)) {
            MXMediaDownloadWorkerTask task;
            synchronized(mPendingDownloadByUrl) {
                task = mPendingDownloadByUrl.get(url);
            }
            return task;
        } else {
            return null;
        }
    }

    /**
     * Generate an unique ID for a string
     * @param input the string
     * @return the unique ID
     */
    private static String uniqueId(String input){
        String uniqueId = null;

        try {
            MessageDigest mDigest = MessageDigest.getInstance("SHA1");
            byte[] result = mDigest.digest(input.getBytes());
            StringBuffer sb = new StringBuffer();

            for (int i = 0; i < result.length; i++) {
                sb.append(Integer.toString((result[i] & 0xff) + 0x100, 16).substring(1));
            }

            uniqueId = sb.toString();
        } catch (Exception e) {
            Log.e(LOG_TAG, "uniqueId failed " + e.getLocalizedMessage());
        }

        if (null == uniqueId) {
            uniqueId = "" + Math.abs(input.hashCode() + (System.currentTimeMillis() + "").hashCode());
        }

        return uniqueId;
    }

    /**
     * Build a filename from an url
     * @param Url the media url
     * @param mimeType the mime type;
     * @return the cache filename
     */
    public static String buildFileName(String Url, String mimeType) {
        String name = "file_" + MXMediaDownloadWorkerTask.uniqueId(Url);

        if (!TextUtils.isEmpty(mimeType)){
            String fileExtension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);

            // some devices don't support .jpeg files
            if ("jpeg".equals(fileExtension)) {
                fileExtension = "jpg";
            }

            if (null != fileExtension) {
                name += "." + fileExtension;
            }
        }

        return name;
    }

    /**
     * Tell if the media is cached
     * @param url the media url
     * @return true if the media is cached
     */
    public static boolean isUrlCached(String url) {
        boolean res = false;

        if ((null != mBitmapByUrlCache) && (null != url)) {
            synchronized (mSyncObject) {
                res = (null != mBitmapByUrlCache.get(url));
            }
        }

        return res;
    }

    /**
     * Tells if the media URL is unreachable.
     * @param url the url to test.
     * @return true if the media URL is unreachable.
     */
    public static boolean isMediaUrlUnreachable(String url) {
        boolean res = true;

        if (!TextUtils.isEmpty(url)) {
            synchronized (mUnreachableUrls) {
                res = mUnreachableUrls.indexOf(url) >= 0;
            }
        }

        return res;
    }

    /**
     * Search a cached bitmap from an url.
     * rotationAngle is set to Integer.MAX_VALUE when undefined : the EXIF metadata must be checked.
     *
     * @param baseFile the base file
     * @param url the media url
     * @param rotation the bitmap rotation
     * @param mimeType the mime type
     * @return the cached bitmap
     */
    public static Bitmap bitmapForURL(Context context, File baseFile, String url, int rotation, String mimeType) {
        Bitmap bitmap = null;

        // sanity check
        if (null != url) {

            if (null == mBitmapByUrlCache) {
                int lruSize = Math.min(20 * 1024 * 1024, (int)Runtime.getRuntime().maxMemory() / 8);

                Log.d(LOG_TAG, "bitmapForURL  lruSize : " + lruSize);

                mBitmapByUrlCache = new LruCache<String, Bitmap>(lruSize){
                    @Override
                    protected int sizeOf(String key, Bitmap bitmap) {
                        return bitmap.getRowBytes() * bitmap.getHeight(); // size in bytes
                    }
                };
            }

            // the image is downloading in background
            if (null != getMediaDownloadWorkerTask(url)) {
                return null;
            }

            // the url is invalid
            if (isMediaUrlUnreachable(url)) {
                return null;
            }

            synchronized (mSyncObject) {
                bitmap = mBitmapByUrlCache.get(url);
            }

            // check if the image has not been saved in file system
            if ((null == bitmap) && (null != baseFile)) {
                String filename = null;

                // the url is a file one
                if (url.startsWith("file:")) {
                    // try to parse it
                    try {
                        Uri uri = Uri.parse(url);
                        filename = uri.getPath();

                    } catch (Exception e) {
                        Log.e(LOG_TAG, "bitmapForURL #1 : " + e.getLocalizedMessage());
                    }

                    // cannot extract the filename -> sorry
                    if (null == filename) {
                        return null;
                    }
                }

                // not a valid file name
                if (null == filename) {
                    filename = buildFileName(url, mimeType);
                }

                try {
                    File file = filename.startsWith(File.separator) ? new File(filename) : new File(baseFile, filename);

                    if (!file.exists()) {
                        Log.d(LOG_TAG, "bitmapForURL() : " + filename + " does not exist");
                        return null;
                    }

                    InputStream fis = new FileInputStream (file);

                    // read the metadata
                    if (Integer.MAX_VALUE == rotation) {
                        rotation = ImageUtils.getRotationAngleForBitmap(context,  Uri.fromFile(file));
                    }

                    if (null != fis) {
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inPreferredConfig = Bitmap.Config.ARGB_8888;

                        try {
                            bitmap = BitmapFactory.decodeStream(fis, null, options);
                        } catch (OutOfMemoryError error) {
                            System.gc();
                            Log.e(LOG_TAG, "bitmapForURL() : Out of memory 1 " + error);
                        }

                        //  try again
                        if (null == bitmap) {
                            try {
                                bitmap = BitmapFactory.decodeStream(fis, null, options);
                            } catch (OutOfMemoryError error) {
                                Log.e(LOG_TAG, "bitmapForURL() Out of memory 2" + error);
                            }
                        }

                        if (null != bitmap) {
                            synchronized (mSyncObject) {
                                if (0 != rotation) {
                                    try {
                                        android.graphics.Matrix bitmapMatrix = new android.graphics.Matrix();
                                        bitmapMatrix.postRotate(rotation);

                                        Bitmap transformedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), bitmapMatrix, false);
                                        bitmap.recycle();
                                        bitmap =  transformedBitmap;
                                    } catch (OutOfMemoryError ex) {
                                        Log.e(LOG_TAG, "bitmapForURL rotation error : " + ex.getLocalizedMessage());
                                    }
                                }

                                // cache only small images
                                // caching large images does not make sense
                                // it would replace small ones.
                                // let assume that the application must be faster when showing the chat history.
                                if ((bitmap.getWidth() < 1000) && (bitmap.getHeight() < 1000)) {
                                    mBitmapByUrlCache.put(url, bitmap);
                                }
                            }
                        }

                        fis.close();
                    }

                } catch (FileNotFoundException e) {
                    Log.d(LOG_TAG, "bitmapForURL() : " + filename + " does not exist");
                } catch (Exception e) {
                    Log.e(LOG_TAG, "bitmapForURL() "+e);

                }
            }
        }

        return bitmap;
    }

    //==============================================================================================================
    // class methods
    //==============================================================================================================

    /**
     * Shared initialization methods.
     * @param appContext the application context.
     * @param url the media URL.
     * @param mimeType the mime type.
     */
    private void commonInit(Context appContext,  String url, String mimeType) {
        mApplicationContext = appContext;
        mUrl = url;
        synchronized(mPendingDownloadByUrl) {
            mPendingDownloadByUrl.put(url, this);
        }
        mMimeType = mimeType;
        mRotation = 0;
    }

    /**
     * MXMediaDownloadWorkerTask creator
     * @param appContext the context
     * @param hsConfig the home server config.
     * @param directoryFile the directory in which the media must be stored
     * @param url the media url
     * @param mimeType the mime type.
     * @param encryptedFileInfo the encryption information
     */
    public MXMediaDownloadWorkerTask(Context appContext, HomeserverConnectionConfig hsConfig, File directoryFile, String url, String mimeType, EncryptedFileInfo encryptedFileInfo) {
        commonInit(appContext, url, mimeType);
        mDirectoryFile = directoryFile;
        mImageViewReferences = new ArrayList<>();
        mHsConfig = hsConfig;
        mEncryptedFileInfo = encryptedFileInfo;
    }

    /**
     * MXMediaDownloadWorkerTask creator
     * @param appContext the context
     * @param hsConfig the home server config
     * @param directoryFile the directory in which the media must be stored
     * @param url the media url
     * @param rotation the rotation
     * @param mimeType the mime type.
     * @param encryptedFileInfo the encryption information
     */
    public MXMediaDownloadWorkerTask(Context appContext, HomeserverConnectionConfig hsConfig, File directoryFile, String url, int rotation, String mimeType, EncryptedFileInfo encryptedFileInfo) {
        commonInit(appContext, url, mimeType);
        mImageViewReferences = new ArrayList<>();
        mDirectoryFile = directoryFile;
        mRotation = rotation;
        mHsConfig = hsConfig;
        mEncryptedFileInfo = encryptedFileInfo;
    }

    /**
     * MXMediaDownloadWorkerTask creator
     * @param task another bitmap task
     */
    public MXMediaDownloadWorkerTask(MXMediaDownloadWorkerTask task) {
        mApplicationContext = task.mApplicationContext;
        mUrl = task.mUrl;
        mRotation = task.mRotation;
        synchronized(mPendingDownloadByUrl) {
            mPendingDownloadByUrl.put(mUrl, this);
        }
        mMimeType = task.mMimeType;
        mImageViewReferences = task.mImageViewReferences;
        mHsConfig = task.mHsConfig;
        mEncryptedFileInfo = task.mEncryptedFileInfo;
    }

    /**
     * Cancels the current download.
     */
    public synchronized void cancelDownload() {
        mIsDownloadCancelled = true;
    }

    /**
     * @return tells if the current download has been cancelled.
     */
    public synchronized boolean isDownloadCancelled() {
        return mIsDownloadCancelled;
    }

    /**
     * @return the media URL.
     */
    public String getUrl() {
        return mUrl;
    }

    /**
     * Add an imageView to the list to refresh when the bitmap is downloaded.
     * @param imageView an image view instance to refresh.
     */
    public void addImageView(ImageView imageView) {
        mImageViewReferences.add(new WeakReference<>(imageView));
    }

    /**
     * Set the default bitmap to use when the Url is unreachable.
     * @param aBitmap the bitmap.
     */
    public void setDefaultBitmap(Bitmap aBitmap) {
        mDefaultBitmap = aBitmap;
    }

    /**
     * Add a download listener.
     * @param listener the listener to add.
     */
    public void addDownloadListener(IMXMediaDownloadListener listener) {
        mDownloadListeners.add(listener);
    }

    /**
     * Returns the download progress.
     * @return the download progress
     */
    public int getProgress() {
        if (null != mDownloadStats) {
            return mDownloadStats.mProgress;
        }

        return -1;
    }

    /**
     * @return the download stats
     */
    public IMXMediaDownloadListener.DownloadStats getDownloadStats() {
        return mDownloadStats;
    }

    /**
     * @return true if the current task is an image one.
     */
    private boolean isBitmapDownloadTask() {
        return (null != mMimeType) && mMimeType.startsWith("image/");
    }

    /**
     * Push the download progress.
     * @param startDownloadTime the start download time.
     */
    private void publishProgress(long startDownloadTime) {
        mDownloadStats.mElapsedTime = (int) ((System.currentTimeMillis() - startDownloadTime) / 1000);

        if (mDownloadStats.mFileSize > 0) {
            if (mDownloadStats.mDownloadedSize >= mDownloadStats.mFileSize) {
                mDownloadStats.mProgress = 99;
            } else {
                mDownloadStats.mProgress = (int)(mDownloadStats.mDownloadedSize  * 100L / mDownloadStats.mFileSize);
            }
        } else {
            mDownloadStats.mProgress = -1;
        }

        // avoid zero div
        if (System.currentTimeMillis() != startDownloadTime) {
            mDownloadStats.mBitRate = (int) (mDownloadStats.mDownloadedSize * 1000L / (System.currentTimeMillis() - startDownloadTime) / 1024);
        } else {
            mDownloadStats.mBitRate = -1;
        }

        if ((0 != mDownloadStats.mBitRate) && (mDownloadStats.mFileSize > 0) && (mDownloadStats.mFileSize > mDownloadStats.mDownloadedSize)) {
            mDownloadStats.mEstimatedRemainingTime = (mDownloadStats.mFileSize - mDownloadStats.mDownloadedSize) / 1024 / mDownloadStats.mBitRate;
        } else {
            mDownloadStats.mEstimatedRemainingTime = -1;
        }

        Log.d(LOG_TAG, "publishProgress " + this + " : " + mDownloadStats.mProgress);

        publishProgress(mDownloadStats);
    }

    // Decode image in background.
    @Override
    protected Void doInBackground(Integer... params) {
        try {
            URL url = new URL(mUrl);
            Log.d(LOG_TAG, "MXMediaDownloadWorkerTask " + this + " starts");

            mDownloadStats = new IMXMediaDownloadListener.DownloadStats();
            // don't known yet
            mDownloadStats.mEstimatedRemainingTime = -1;

            InputStream stream = null;

            int filelen = -1;
            URLConnection connection = null;
            
            try {
            	connection = url.openConnection();
            
                if (mHsConfig != null && connection instanceof HttpsURLConnection) {
                    // Add SSL Socket factory.
                    HttpsURLConnection sslConn = (HttpsURLConnection) connection;
                    try {
                        Pair<SSLSocketFactory, X509TrustManager> pair = CertUtil.newPinnedSSLSocketFactory(mHsConfig);
                        sslConn.setSSLSocketFactory(pair.first);
                        sslConn.setHostnameVerifier(CertUtil.newHostnameVerifier(mHsConfig));
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "doInBackground SSL exception " + e.getLocalizedMessage());
                    }
                }

                // add a timeout to avoid infinite loading display.
                connection.setReadTimeout(DOWNLOAD_TIME_OUT);
                filelen = connection.getContentLength();
                stream = connection.getInputStream();
            } catch (Exception e) {
                Log.e(LOG_TAG, "bitmapForURL : fail to open the connection " + e.getMessage());

                InputStream errorStream = ((HttpsURLConnection) connection).getErrorStream();

                if (null != errorStream) {
                    try {
                        BufferedReader streamReader = new BufferedReader(new InputStreamReader(errorStream, "UTF-8"));
                        StringBuilder responseStrBuilder = new StringBuilder();

                        String inputStr;

                        while ((inputStr = streamReader.readLine()) != null) {
                            responseStrBuilder.append(inputStr);
                        }

                        mErrorAsJsonElement = new JsonParser().parse(responseStrBuilder.toString());
                    } catch (Exception ee) {
                        Log.e(LOG_TAG, "bitmapForURL : Error parsing error " + ee.getLocalizedMessage());
                    }
                }

                // privacy
                //Log.d(LOG_TAG, "MediaWorkerTask " + mUrl + " does not exist");
                Log.d(LOG_TAG, "MediaWorkerTask an url does not exist");

                // if some medias are not found
                // do not try to reload them until the next application launch.
                synchronized (mUnreachableUrls) {
                    mUnreachableUrls.add(mUrl);
                }
            }

            dispatchDownloadStart();

            // test if the download has not been cancelled
            if (!isDownloadCancelled() && (null == mErrorAsJsonElement)) {

                final long startDownloadTime = System.currentTimeMillis();

                String filename = MXMediaDownloadWorkerTask.buildFileName(mUrl, mMimeType) + ".tmp";
                FileOutputStream fos = new FileOutputStream(new File(mDirectoryFile, filename));

                mDownloadStats.mDownloadId = mUrl;
                mDownloadStats.mProgress = 0;
                mDownloadStats.mDownloadedSize = 0;
                mDownloadStats.mFileSize = filelen;
                mDownloadStats.mElapsedTime = 0;
                mDownloadStats.mEstimatedRemainingTime = -1;
                mDownloadStats.mBitRate = 0;

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
                                            publishProgress(startDownloadTime);
                                        }
                                    }
                                });
                            }
                        }, new java.util.Date(), 100);
                    }
                });

                try {
                    byte[] buf = new byte[DOWNLOAD_BUFFER_READ_SIZE];
                    int len;
                    while (!isDownloadCancelled() && (len = stream.read(buf)) != -1) {
                        fos.write(buf, 0, len);
                        mDownloadStats.mDownloadedSize += len;
                    }

                    if (!isDownloadCancelled()) {
                        mDownloadStats.mProgress = 100;
                    }
                } catch (OutOfMemoryError outOfMemoryError) {
                    Log.e(LOG_TAG, "doInBackground: out of memory");
                } catch (Exception e) {
                    Log.e(LOG_TAG, "doInBackground fail to read image " + e.getMessage());
                }

                mIsDone = true;

                close(stream);
                fos.flush();
                fos.close();

                if (null != mEncryptedFileInfo) {
                    File file = new File(mDirectoryFile, filename);
                    FileInputStream fis = new FileInputStream(file);
                    InputStream is = MXEncryptedAttachments.decryptAttachment(fis, mEncryptedFileInfo);
                    fis.close();

                    // if the decryption succeeds, replace the encrypted file content by the unencrypted one
                    if (null != is) {
                        mApplicationContext.deleteFile(filename);

                        fos = new FileOutputStream(file);
                        byte[] buf = new byte[DOWNLOAD_BUFFER_READ_SIZE];
                        int len;
                        while ((len = is.read(buf)) != -1) {
                            fos.write(buf, 0, len);
                        }
                    } else {
                        mDownloadStats.mProgress = 0;
                    }
                }

                uiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        refreshTimer.cancel();
                    }
                });

                if ((null != connection) && (connection instanceof HttpsURLConnection)) {
                    ((HttpsURLConnection) connection).disconnect();
                }

                // the file has been successfully downloaded
                if (mDownloadStats.mProgress == 100) {
                    try {
                        File originalFile = new File(mDirectoryFile, filename);
                        String newFileName = MXMediaDownloadWorkerTask.buildFileName(mUrl, mMimeType);
                        File newFile = new File(mDirectoryFile, newFileName);
                        if (newFile.exists()) {
                            // Or you could throw here.
                            mApplicationContext.deleteFile(newFileName);
                        }
                        originalFile.renameTo(newFile);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "doInBackground : renaming error " + e.getLocalizedMessage());
                    }
                }
            }

            if (mDownloadStats.mProgress == 100) {
                Log.d(LOG_TAG, "The download " + this + " is done.");
            } else {
                if (null != mErrorAsJsonElement) {
                    Log.d(LOG_TAG, "The download " + this + " failed : mErrorAsJsonElement " + mErrorAsJsonElement.toString());
                } else {
                    Log.d(LOG_TAG, "The download " + this + " failed.");
                }
            }
        }
        catch (Exception e) {
            Log.e(LOG_TAG, "Unable to download media " + this);
        }

        // remove the image from the loading one
        synchronized(mPendingDownloadByUrl) {
            mPendingDownloadByUrl.remove(mUrl);
        }

        return null;
    }

    /**
     * Close the stream.
     * @param stream the stream to close.
     */
    private void close(InputStream stream) {
        try {
            stream.close();
        }
        catch (Exception e) {
            Log.e(LOG_TAG, "close error " + e.getLocalizedMessage());
        }
    }

    @Override
    protected void onProgressUpdate(IMXMediaDownloadListener.DownloadStats  ... progress) {
        super.onProgressUpdate(progress);
        dispatchOnDownloadProgress(mDownloadStats);
    }

    // Once complete, see if ImageView is still around and set bitmap.
    @Override
    protected void onPostExecute(Void nothing) {
        if (null != mErrorAsJsonElement) {
            dispatchOnDownloadError(mErrorAsJsonElement);
        } else if (isDownloadCancelled()) {
            dispatchDownloadCancel();
        } else {
            dispatchOnDownloadComplete();

            // image download
            // update the linked ImageViews.
            if (isBitmapDownloadTask()) {
                // retrieve the bitmap from the file s
                Bitmap bitmap = MXMediaDownloadWorkerTask.bitmapForURL(mApplicationContext, mDirectoryFile, mUrl, mRotation, mMimeType);

                if (null == bitmap) {
                    bitmap = mDefaultBitmap;
                }

                // update the imageViews image
                if (bitmap != null) {
                    for (WeakReference<ImageView> weakRef : mImageViewReferences) {
                        final ImageView imageView = weakRef.get();

                        if (imageView != null && TextUtils.equals(mUrl, (String) imageView.getTag())) {
                            imageView.setBackgroundColor(Color.TRANSPARENT);
                            imageView.setImageBitmap(bitmap);
                        }
                    }
                }
            }
        }
    }


    //==============================================================================================================
    // Dispatchers
    //==============================================================================================================

    /**
     * Dispatch start event to the callbacks.
     */
    private void dispatchDownloadStart() {
        for(IMXMediaDownloadListener callback : mDownloadListeners) {
            try {
                callback.onDownloadStart(mUrl);
            } catch (Exception e) {
                Log.e(LOG_TAG, "dispatchDownloadStart error " + e.getLocalizedMessage());
            }
        }
    }

    /**
     * Dispatch stats update to the callbacks.
     * @param stats the new stats value
     */
    private void dispatchOnDownloadProgress(IMXMediaDownloadListener.DownloadStats stats) {
        for(IMXMediaDownloadListener callback : mDownloadListeners) {
            try {
                callback.onDownloadProgress(mUrl, stats);
            } catch (Exception e) {
                Log.e(LOG_TAG, "dispatchOnDownloadProgress error " + e.getLocalizedMessage());
            }
        }
    }

    /**
     * Dispatch error message.
     * @param jsonElement the Json error
     */
    private void dispatchOnDownloadError(JsonElement jsonElement) {
        for(IMXMediaDownloadListener callback : mDownloadListeners) {
            try {
                callback.onDownloadError(mUrl, jsonElement);
            } catch (Exception e) {
                Log.e(LOG_TAG, "dispatchOnDownloadError error " + e.getLocalizedMessage());
            }
        }
    }

    /**
     * Dispatch end of download
     */
    private void dispatchOnDownloadComplete() {
        for(IMXMediaDownloadListener callback : mDownloadListeners) {
            try {
                callback.onDownloadComplete(mUrl);
            } catch (Exception e) {
                Log.e(LOG_TAG, "dispatchOnDownloadComplete error " + e.getLocalizedMessage());
            }
        }
    }

    /**
     * Dispatch download cancel
     */
    private void dispatchDownloadCancel() {
        for(IMXMediaDownloadListener callback : mDownloadListeners) {
            try {
                callback.onDownloadCancel(mUrl);
            } catch (Exception e) {
                Log.e(LOG_TAG, "dispatchDownloadCancel error " + e.getLocalizedMessage());
            }
        }
    }
}
