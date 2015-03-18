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

package org.matrix.matrixandroidsdk.db;


import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.v4.util.LruCache;
import android.util.Log;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.ImageView;
import android.widget.LinearLayout;

import org.matrix.androidsdk.rest.model.ContentResponse;
import org.matrix.androidsdk.util.ContentManager;
import org.matrix.matrixandroidsdk.Matrix;
import org.matrix.matrixandroidsdk.R;
import org.matrix.matrixandroidsdk.activity.CommonActivityUtils;
import org.matrix.matrixandroidsdk.view.PieFractionView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;

public class ConsoleMediasCache {

    /**
     * Interface to implement to get the mxc URI of downloaded content.
     */
    public static interface DownloadCallback {
        /**
         * Warn of the progress download
         * @param downloadId the download Identifier
         * @param percentageProgress the progress value
         */
        public void onDownloadProgress(String downloadId, int percentageProgress);

        /**
         * Called when the upload is complete or has failed.
         * @param downloadId the download Identifier
         */
        public void onDownloadComplete(String downloadId);
    }

    private static final String LOG_TAG = "ConsoleMediasCache";

    /**
     * Compute the filesystem cache size
     * @param context
     * @return the medias cache size in bytes
     */
    public static long cacheSize(Activity context) {
        long size = 0;
        String[] filesList = context.fileList();

        for(String filename : filesList) {
            try {
                File file = new File(context.getFilesDir(), filename);
                size += file.length();
            } catch (Exception e) {

            }
        }
        return size;
    }

    /**
     * Clear the medias caches.
     * @param context The application context to use.
     */
    public static void clearCache(Activity context) {
        String[] filesList = context.fileList();

        for(String file : filesList) {
            try {
                context.deleteFile(file);
            } catch (Exception e) {

            }
        }

        BitmapWorkerTask.clearBitmapsCache();
    }

    /**
     * Convert matrix url into http one.
     * @param context the context
     * @param url the matrix url
     * @param width the expected image width
     * @param height the expected image height
     * @return the URL to access the described resource.
     */
    private static String downloadableUrl(Context context, String url, int width, int height) {
        // check if the Url is a matrix one
        if (url.startsWith(ContentManager.MATRIX_CONTENT_URI_SCHEME)) {
            ContentManager contentManager = Matrix.getInstance(context).getDefaultSession().getContentManager();

            if ((width > 0) && (height > 0)) {
                return contentManager.getDownloadableThumbnailUrl(url, width, height, ContentManager.METHOD_SCALE);
            } else {
                return contentManager.getDownloadableUrl(url);
            }
        } else {
            return url;
        }
    }

    /**
     * Return the cache file name for a media
     * @param context the context
     * @param url the media url
     * @param mimeType the mime type
     * @return the cache file name (private directory)
     */
    public static String mediaCacheFilename(Context context, String url, String mimeType) {
        return mediaCacheFilename(context, url, -1, -1, mimeType);
    }

    public static String mediaCacheFilename(Context context, String url, int width, int height, String mimeType) {
        // sanity check
        if (null == url) {
            return null;
        }

        String filename = BitmapWorkerTask.buildFileName(downloadableUrl(context, url, width, height), mimeType);

        try {
            // already a local file
            if (filename.startsWith("file:")) {
                Uri uri = Uri.parse(filename);
                filename = uri.getLastPathSegment();
            }

            File file = new File(context.getApplicationContext().getFilesDir(), filename);

            if (!file.exists()) {
                filename = null;
            }

        } catch (Exception e) {
            filename = null;
        }

        return filename;
    }

    /**
     * Save a bitmap to the local cache
     * it could be used for unsent media to allow them to be resent.
     * @param bitmap the bitmap to save
     * @param defaultFileName the filename is provided, if null, a filename will be generated
     * @return the media cache URL
     */
    public static String saveBitmap(Bitmap bitmap, Context context, String defaultFileName) {
        String filename = "file" + System.currentTimeMillis() + ".jpg";
        String cacheURL = null;

        try {
            if (null != defaultFileName) {
                File file = new File(defaultFileName);
                file.delete();

                filename = Uri.fromFile(file).getLastPathSegment();
            }

            FileOutputStream fos = context.openFileOutput(filename, Context.MODE_PRIVATE);

            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);

            fos.flush();
            fos.close();

            cacheURL = Uri.fromFile(context.getFileStreamPath(filename)).toString();
        } catch (Exception e) {
        }

        return cacheURL;
    }

    /**
     * Save a media to the local cache
     * it could be used for unsent media to allow them to be resent.
     * @param stream the file stream to save
     * @param defaultFileName the filename is provided, if null, a filename will be generated
     * @param mimeType the mime type.
     * @return the media cache URL
     */
    public static String saveMedia(InputStream stream, Context context, String defaultFileName, String mimeType) {
        String filename = defaultFileName;

        if (null == filename) {
            filename = "file" + System.currentTimeMillis();

            if (null != mimeType) {
                String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);

                if (null != extension) {
                    filename += "." + extension;
                }
            }
        }

        String cacheURL = null;

        try {
            FileOutputStream fos = context.openFileOutput(filename, Context.MODE_PRIVATE);

            try {
                byte[] buf = new byte[1024 * 32];

                int len;
                while ((len = stream.read(buf)) != -1) {
                    fos.write(buf, 0, len);
                }
            } catch (Exception e) {
            }

            fos.flush();
            fos.close();
            stream.close();

            cacheURL = Uri.fromFile(context.getFileStreamPath(filename)).toString();
        } catch (Exception e) {

        }

        return cacheURL;
    }

    /**
     * Returns the cached bitmap with the expected rotation angle.
     * @param context the context
     * @param url the bitmap url
     * @param rotationAngle the rotation angle (degrees)
     * @param mimeType the mimeType.
     * @return the bitmap or null if it does not exist
     */
    public static Bitmap bitmapForUrl(Context context, String url, int rotationAngle, String mimeType)  {
        return BitmapWorkerTask.bitmapForURL(context, url, rotationAngle, mimeType);
    }

    /**
     * Replace a media cache by a file content.
     * @param context the context
     * @param mediaUrl the mediaUrl
     * @param mimeType the mimeType.
     * @param fileUrl the file which replaces the cached media.
     */
    public static void saveFileMediaForUrl(Context context, String mediaUrl, String fileUrl, String mimeType) {
        saveFileMediaForUrl(context, mediaUrl, fileUrl, -1, -1, mimeType);
    }

    /**
     * Replace a media cache by a file content.
     * MediaUrl is the same model as the one used in loadBitmap.
     * @param context the context
     * @param mediaUrl the mediaUrl
     * @param fileUrl the file which replaces the cached media.
     * @param width the expected image width
     * @param height the expected image height
     * @param mimeType the mimeType.
     */
    public static void saveFileMediaForUrl(Context context, String mediaUrl, String fileUrl, int width, int height, String mimeType) {
        String downloadableUrl = downloadableUrl(context, mediaUrl, width, height);
        String filename = BitmapWorkerTask.buildFileName(downloadableUrl, mimeType);

        try {
            // delete the current content
            File destFile = new File(context.getFilesDir(), filename);

            if (destFile.exists()) {
                try {
                    destFile.delete();
                } catch (Exception e) {
                }
            }

            Uri uri = Uri.parse(fileUrl);
            File srcFile = new File(uri.getPath());
            srcFile.renameTo(destFile);

        } catch (Exception e) {

        }
    }
    /**
     * Load an avatar thumbnail.
     * The imageView image is updated when the bitmap is loaded or downloaded.
     * @param imageView Ihe imageView to update with the image.
     * @param url the image url
     * @param side the avatar thumbnail side
     * @return a download identifier if the image is not cached.
     */
    public static String loadAvatarThumbnail(ImageView imageView, String url, int side) {
        return loadBitmap(imageView, url, side, side, 0, null);
    }

    /**
     * Load a bitmap from the url.
     * The imageView image is updated when the bitmap is loaded or downloaded.
     * @param imageView Ihe imageView to update with the image.
     * @param url the image url
     * @param rotationAngle the rotation angle (degrees)
     * @param mimeType the mimeType.
     * @return a download identifier if the image is not cached.
     */
    public static String loadBitmap(ImageView imageView, String url, int rotationAngle, String mimeType) {
        return loadBitmap(imageView, url, -1, -1, rotationAngle, mimeType);
    }

    /**
     * Load a bitmap from the url.
     * The imageView image is updated when the bitmap is loaded or downloaded.
     * @param context The context
     * @param url the image url
     * @param rotationAngle the rotation angle (degrees)
     * @param mimeType the mimeType.
     * @return a download identifier if the image is not cached.
     */
    public static String loadBitmap(Context context, String url, int rotationAngle, String mimeType) {
        return loadBitmap(context, null, url, -1, -1, rotationAngle, mimeType);
    }

    /**
     * Load a bitmap from an url.
     * The imageView image is updated when the bitmap is loaded or downloaded.
     * The width/height parameters are optional. If they are > 0, download a thumbnail.
     * @param imageView the imageView to fill when the image is downloaded
     * @param url the image url
     * @param width the expected image width
     * @param height the expected image height
     * @param rotationAngle the rotation angle (degrees)
     * @param mimeType the mimeType.
     * @return a download identifier if the image is not cached
     */
    public static String loadBitmap(ImageView imageView, String url, int width, int height, int rotationAngle, String mimeType) {
        return loadBitmap(imageView.getContext(), imageView, url, width, height, rotationAngle, mimeType);
    }

    /**
     * Load a bitmap from an url.
     * The imageView image is updated when the bitmap is loaded or downloaded.
     * The width/height parameters are optional. If they are > 0, download a thumbnail.
     * @param context the context
     * @param imageView the imageView to fill when the image is downloaded
     * @param url the image url
     * @param width the expected image width
     * @param height the expected image height
     * @param rotationAngle the rotation angle (degrees)
     * @param mimeType the mimeType.
     * @return a download identifier if the image is not cached
     */
    public static String loadBitmap(Context context, ImageView imageView, String url, int width, int height, int rotationAngle, String mimeType) {
        if (null == url) {
            return null;
        }

        // request invalid bitmap size
        if ((0 == width) || (0 == height)) {
            return null;
        }

        String downloadableUrl = downloadableUrl(context, url, width, height);

        if (null != imageView) {
            imageView.setTag(downloadableUrl);
        }

        // if the mime type is not provided, assume it is a jpeg file
        if (null == mimeType) {
            mimeType = "image/jpeg";
        }

        // check if the bitmap is already cached
        Bitmap bitmap = BitmapWorkerTask.bitmapForURL(context.getApplicationContext(),downloadableUrl, rotationAngle, mimeType);

        if (null != bitmap) {
            if (null != imageView) {
                // display it
                imageView.setImageBitmap(bitmap);
            }
            downloadableUrl = null;
        } else {
            BitmapWorkerTask currentTask = BitmapWorkerTask.bitmapWorkerTaskForUrl(downloadableUrl);

            if (null != currentTask) {
                if (null != imageView) {
                    currentTask.addImageView(imageView);
                }
            } else {
                // download it in background
                BitmapWorkerTask task = new BitmapWorkerTask(context, downloadableUrl, rotationAngle, mimeType);

                if (null != imageView) {
                    task.addImageView(imageView);
                }
                task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null);
            }
        }

        return downloadableUrl;
    }

    /**
     * Returns the download progress (percentage).
     * @param downloadId the downloadId provided by loadBitmap;
     * @return the download progress
     */
    public static int progressValueForDownloadId(String downloadId) {
        BitmapWorkerTask currentTask = BitmapWorkerTask.bitmapWorkerTaskForUrl(downloadId);

        if (null != currentTask) {
            return currentTask.getProgress();
        }
        return -1;
    }

    /**
     * Add a download listener for an downloadId.
     * @param downloadId The uploadId.
     * @param callback the async callback
     */
    public static void addDownloadListener(String downloadId, DownloadCallback callback) {
        BitmapWorkerTask currentTask = BitmapWorkerTask.bitmapWorkerTaskForUrl(downloadId);

        if (null != currentTask) {
            currentTask.addCallback(callback);
        }
    }

    static class BitmapWorkerTask extends AsyncTask<Integer, Integer, Bitmap> {

        private static final int MEMORY_CACHE_MB = 16;
        private static HashMap<String, BitmapWorkerTask> mPendingDownloadByUrl = new HashMap<String, BitmapWorkerTask>();

        private static LruCache<String, Bitmap> sMemoryCache = new LruCache<String, Bitmap>(1024 * 1024 * MEMORY_CACHE_MB){
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getRowBytes() * bitmap.getHeight(); // size in bytes
            }
        };

        private ArrayList<DownloadCallback> mCallbacks = new ArrayList<DownloadCallback>();
        private final ArrayList<WeakReference<ImageView>> mImageViewReferences;
        private String mUrl;
        private String mMimeType;
        private Context mApplicationContext;
        private int mRotation = 0;
        private int mProgress = 0;

        public static void clearBitmapsCache() {
            sMemoryCache.evictAll();
        }

        /**
         * Check if there is a pending download for the url.
         * @param url The url to check the existence
         * @return the dedicated BitmapWorkerTask if it exists.
         */
        public static BitmapWorkerTask bitmapWorkerTaskForUrl(String url) {
            if ((url != null) &&  mPendingDownloadByUrl.containsKey(url)) {
                BitmapWorkerTask task;
                synchronized(mPendingDownloadByUrl) {
                    task = mPendingDownloadByUrl.get(url);
                }
                return task;
            } else {
                return null;
            }
        }

        /**
         * Build a filename from an url
         * @param Url the media url
         * @param mimeType the mime type;
         * @return the cache filename
         */
        public static String buildFileName(String Url, String mimeType) {
            String name = "file_" + Math.abs(Url.hashCode());

            if (null == mimeType) {
                mimeType = "image/jpeg";
            }

            String fileExtension =  MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);

            if (null != fileExtension) {
                name += "." + fileExtension;
            }

            return name;
        }

        /**
         * Search a cached bitmap from an url.
         * @param appContext the context
         * @param url the media url
         * @param rotation the bitmap rotation
         * @param mimeType the mime type
         * @return the cached bitmap or null it does not exist
         */
        public static Bitmap bitmapForURL(Context appContext, String url, int rotation, String mimeType) {
            Bitmap bitmap = null;

            // sanity check
            if (null != url) {

                // the image is downloading in background
                if (null != bitmapWorkerTaskForUrl(url)) {
                    return null;
                }

                synchronized (sMemoryCache) {
                    bitmap = sMemoryCache.get(url);
                }

                // check if the image has not been saved in file system
                if ((null == bitmap) && (null != appContext)) {
                    String filename = null;

                    // the url is a file one
                    if (url.startsWith("file:")) {
                        // try to parse it
                        try {
                            Uri uri = Uri.parse(url);
                            filename = uri.getPath();

                        } catch (Exception e) {
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
                        FileInputStream fis;

                        if (filename.startsWith(File.separator)) {
                            fis = new FileInputStream (new File(filename));
                        } else {
                            fis = appContext.getApplicationContext().openFileInput(filename);
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
                                synchronized (sMemoryCache) {

                                    if (0 != rotation) {
                                        try {
                                            android.graphics.Matrix bitmapMatrix = new android.graphics.Matrix();
                                            bitmapMatrix.postRotate(rotation);

                                            Bitmap transformedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), bitmapMatrix, false);
                                            bitmap.recycle();
                                            bitmap =  transformedBitmap;
                                        } catch (OutOfMemoryError ex) {
                                        }
                                    }

                                    sMemoryCache.put(url, bitmap);
                                }
                            }

                            fis.close();
                        }

                    } catch (FileNotFoundException e) {
                        Log.e(LOG_TAG, "bitmapForURL() : " + filename + " does not exist");
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "bitmapForURL() "+e);

                    }
                }
            }

            return bitmap;
        }

        /**
         * BitmapWorkerTask creator
         * @param appContext the context
         * @param url the media url
         * @param rotation the rotation
         * @param mimeType the mime type.
         */
        public BitmapWorkerTask(Context appContext,  String url, int rotation, String mimeType) {
            mApplicationContext = appContext;
            mUrl = url;
            mRotation = rotation;
            synchronized(mPendingDownloadByUrl) {
                mPendingDownloadByUrl.put(url, this);
            }
            mMimeType = mimeType;
            mImageViewReferences = new ArrayList<WeakReference<ImageView>>();
        }

        /**
         * Add an imageView to the list to refresh when the bitmap is downloaded.
         * @param imageView an image view instance to refresh.
         */
        public void addImageView(ImageView imageView) {
            mImageViewReferences.add(new WeakReference<ImageView>(imageView));
        }

        /**
         * Add a download callback.
         * @param callback the download callback to add
         */
        public void addCallback(DownloadCallback callback) {
            mCallbacks.add(callback);
        }

        /**
         * Returns the download progress.
         * @return the download progress
         */
        public int getProgress() {
            return mProgress;
        }

        // Decode image in background.
        @Override
        protected Bitmap doInBackground(Integer... params) {
            try {
                // check the in-memory cache
                String key = mUrl;

                URL url = new URL(mUrl);
                Log.d(LOG_TAG, "BitmapWorkerTask open >>>>> " + mUrl);

                InputStream stream = null;
                Bitmap bitmap = null;

                long filelen = -1;

                try {
                    URLConnection connection = url.openConnection();
                    filelen = connection.getContentLength();
                    stream = connection.getInputStream();
                } catch (FileNotFoundException e) {
                    Log.d(LOG_TAG, "BitmapWorkerTask " + mUrl + " does not exist");
                    bitmap = BitmapFactory.decodeResource(mApplicationContext.getResources(), R.drawable.ic_menu_gallery);
                }

                String filename = BitmapWorkerTask.buildFileName(mUrl, mMimeType);
                FileOutputStream fos = mApplicationContext.openFileOutput(filename, Context.MODE_PRIVATE);

                // a bitmap has been provided
                if (null != bitmap) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                } else {
                    try {
                        int totalDownloaded = 0;

                        byte[] buf = new byte[1024 * 32];
                        int len;
                        while ((len = stream.read(buf)) != -1) {
                            fos.write(buf, 0, len);

                            totalDownloaded += len;

                            int progress = 0;

                            if (filelen > 0) {
                                if (totalDownloaded >= filelen) {
                                    progress = 99;
                                } else {
                                    progress = (int)(totalDownloaded * 100 / filelen);
                                }
                            } else {
                                progress = -1;
                            }

                            Log.d(LOG_TAG, "download " + progress + " (" + mUrl + ")");

                            publishProgress(mProgress = progress);
                        }

                    }
                    catch (OutOfMemoryError outOfMemoryError) {
                    }
                    catch (Exception e) {
                        e = e;
                    }

                    close(stream);
                }

                fos.flush();
                fos.close();

                Log.d(LOG_TAG, "download is done (" + mUrl + ")");

                synchronized(mPendingDownloadByUrl) {
                    mPendingDownloadByUrl.remove(mUrl);
                }

                // get the bitmap from the filesytem
                if (null == bitmap) {
                    bitmap = BitmapWorkerTask.bitmapForURL(mApplicationContext, key, mRotation, mMimeType);
                }

                synchronized (sMemoryCache) {
                    cacheBitmap(key, bitmap);
                }

                return bitmap;
            }
            catch (Exception e) {
                Log.e(LOG_TAG, "Unable to load bitmap: "+e);
                return null;
            }
        }

        /**
         * Dispatch progress update to the callbacks.
         * @param progress the new progress value
         */
        private void sendProgress(int progress) {
            for(DownloadCallback callback : mCallbacks) {
                try {
                    callback.onDownloadProgress(mUrl, progress);
                } catch (Exception e) {
                }
            }
        }

        /**
         * Dispatch end of download
         */
        private void sendDownloadComplete() {
            for(DownloadCallback callback : mCallbacks) {
                try {
                    callback.onDownloadComplete(mUrl);
                } catch (Exception e) {

                }
            }
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {
            super.onProgressUpdate(progress);
            sendProgress(progress[0]);
        }

        // Once complete, see if ImageView is still around and set bitmap.
        @Override
        protected void onPostExecute(Bitmap bitmap) {
            sendDownloadComplete();

            // update the imageView image
            if (bitmap != null) {
                for(WeakReference<ImageView> weakRef : mImageViewReferences) {
                    final ImageView imageView = weakRef.get();

                    if (imageView != null && mUrl.equals(imageView.getTag())) {
                        imageView.setImageBitmap(bitmap);
                    }
                }
            }
        }

        private void cacheBitmap(String key, Bitmap bitmap) {
            // for now we'll just in-memory cache this. In future, they should be written to the
            // cache directory as well.
            sMemoryCache.put(key, bitmap);
        }

        private void close(InputStream stream) {
            try {
                stream.close();
            }
            catch (Exception e) {} // don't care, it's being closed!
        }
    }

}
