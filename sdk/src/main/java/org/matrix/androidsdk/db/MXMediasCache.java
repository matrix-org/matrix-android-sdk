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

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.widget.ImageView;

import org.matrix.androidsdk.util.ContentManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.concurrent.RejectedExecutionException;

public class MXMediasCache {

    /**
     * Interface to implement to get the mxc URI of downloaded content.
     */
    public interface DownloadCallback {
        /**
         * Warn of the progress download
         *
         * @param downloadId         the download Identifier
         * @param percentageProgress the progress value
         */
        public void onDownloadProgress(String downloadId, int percentageProgress);

        /**
         * Called when the upload is complete or has failed.
         *
         * @param downloadId the download Identifier
         */
        public void onDownloadComplete(String downloadId);
    }

    private static final String LOG_TAG = "ConsoleMediasCache";
    private ContentManager mContentmanager = null;

    /**
     * constructor
     *
     * @param contentManager
     */
    public MXMediasCache(ContentManager contentManager) {
        mContentmanager = contentManager;
    }

    /**
     * Compute the filesystem cache size
     *
     * @param context
     * @return the medias cache size in bytes
     */
    public long cacheSize(Activity context) {
        long size = 0;
        String[] filesList = context.fileList();

        for (String filename : filesList) {
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
     *
     * @param context The application context to use.
     */
    public void clearCache(Context context) {
        String[] filesList = context.fileList();

        for (String file : filesList) {
            try {
                context.deleteFile(file);
            } catch (Exception e) {

            }
        }

        MXMediaWorkerTask.clearBitmapsCache();
    }

    /**
     * Convert matrix url into http one.
     *
     * @param context the context
     * @param url     the matrix url
     * @param width   the expected image width
     * @param height  the expected image height
     * @return the URL to access the described resource.
     */
    private String downloadableUrl(Context context, String url, int width, int height) {
        // check if the Url is a matrix one
        if ((null != url) && url.startsWith(ContentManager.MATRIX_CONTENT_URI_SCHEME)) {
            if ((width > 0) && (height > 0)) {
                return mContentmanager.getDownloadableThumbnailUrl(url, width, height, ContentManager.METHOD_SCALE);
            } else {
                return mContentmanager.getDownloadableUrl(url);
            }
        } else {
            return url;
        }
    }

    /**
     * Return the cache file name for a media
     *
     * @param context  the context
     * @param url      the media url
     * @param mimeType the mime type
     * @return the cache file name (private directory)
     */
    public String mediaCacheFilename(Context context, String url, String mimeType) {
        return mediaCacheFilename(context, url, -1, -1, mimeType);
    }

    public String mediaCacheFilename(Context context, String url, int width, int height, String mimeType) {
        // sanity check
        if (null == url) {
            return null;
        }

        String filename = MXMediaWorkerTask.buildFileName(downloadableUrl(context, url, width, height), mimeType);

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
     *
     * @param bitmap          the bitmap to save
     * @param defaultFileName the filename is provided, if null, a filename will be generated
     * @return the media cache URL
     */
    public String saveBitmap(Bitmap bitmap, Context context, String defaultFileName) {
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
     *
     * @param stream          the file stream to save
     * @param defaultFileName the filename is provided, if null, a filename will be generated
     * @param mimeType        the mime type.
     * @return the media cache URL
     */
    public String saveMedia(InputStream stream, Context context, String defaultFileName, String mimeType) {
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
     *
     * @param context       the context
     * @param url           the bitmap url
     * @param rotationAngle the rotation angle (degrees)
     * @param mimeType      the mimeType.
     * @return the bitmap or null if it does not exist
     */
    public Bitmap bitmapForUrl(Context context, String url, int rotationAngle, String mimeType) {
        return MXMediaWorkerTask.bitmapForURL(context, url, rotationAngle, mimeType);
    }

    /**
     * Replace a media cache by a file content.
     *
     * @param context  the context
     * @param mediaUrl the mediaUrl
     * @param mimeType the mimeType.
     * @param fileUrl  the file which replaces the cached media.
     */
    public void saveFileMediaForUrl(Context context, String mediaUrl, String fileUrl, String mimeType) {
        saveFileMediaForUrl(context, mediaUrl, fileUrl, -1, -1, mimeType);
    }

    /**
     * Replace a media cache by a file content.
     * MediaUrl is the same model as the one used in loadBitmap.
     *
     * @param context  the context
     * @param mediaUrl the mediaUrl
     * @param fileUrl  the file which replaces the cached media.
     * @param width    the expected image width
     * @param height   the expected image height
     * @param mimeType the mimeType.
     */
    public void saveFileMediaForUrl(Context context, String mediaUrl, String fileUrl, int width, int height, String mimeType) {
        String downloadableUrl = downloadableUrl(context, mediaUrl, width, height);
        String filename = MXMediaWorkerTask.buildFileName(downloadableUrl, mimeType);

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
     *
     * @param imageView Ihe imageView to update with the image.
     * @param url       the image url
     * @param side      the avatar thumbnail side
     * @return a download identifier if the image is not cached.
     */
    public String loadAvatarThumbnail(ImageView imageView, String url, int side) {
        return loadBitmap(imageView, url, side, side, 0, null);
    }

    /**
     * Load a bitmap from the url.
     * The imageView image is updated when the bitmap is loaded or downloaded.
     *
     * @param imageView     Ihe imageView to update with the image.
     * @param url           the image url
     * @param rotationAngle the rotation angle (degrees)
     * @param mimeType      the mimeType.
     * @return a download identifier if the image is not cached.
     */
    public String loadBitmap(ImageView imageView, String url, int rotationAngle, String mimeType) {
        return loadBitmap(imageView, url, -1, -1, rotationAngle, mimeType);
    }

    /**
     * Load a bitmap from the url.
     * The imageView image is updated when the bitmap is loaded or downloaded.
     *
     * @param context       The context
     * @param url           the image url
     * @param rotationAngle the rotation angle (degrees)
     * @param mimeType      the mimeType.
     * @return a download identifier if the image is not cached.
     */
    public String loadBitmap(Context context, String url, int rotationAngle, String mimeType) {
        return loadBitmap(context, null, url, -1, -1, rotationAngle, mimeType);
    }

    /**
     * Load a bitmap from an url.
     * The imageView image is updated when the bitmap is loaded or downloaded.
     * The width/height parameters are optional. If they are > 0, download a thumbnail.
     *
     * @param imageView     the imageView to fill when the image is downloaded
     * @param url           the image url
     * @param width         the expected image width
     * @param height        the expected image height
     * @param rotationAngle the rotation angle (degrees)
     * @param mimeType      the mimeType.
     * @return a download identifier if the image is not cached
     */
    public String loadBitmap(ImageView imageView, String url, int width, int height, int rotationAngle, String mimeType) {
        return loadBitmap(imageView.getContext(), imageView, url, width, height, rotationAngle, mimeType);
    }

    // some tasks have been stacked because there are too many running ones.
    ArrayList<MXMediaWorkerTask> mSuspendedTasks = new ArrayList<MXMediaWorkerTask>();

    /**
     * Retuns the download ID from the media URL.
     *
     * @param context  the application context
     * @param url      the media url
     * @param mimeType the mime type
     * @return the download ID
     */
    public String downloadIdFromUrl(Context context, String url, String mimeType) {
        return downloadableUrl(context, url, -1, -1);
    }

    /**
     * @param context  the application context
     * @param url      the media url
     * @param mimeType the media mimetype
     * @return
     */
    public String downloadMedia(Context context, String url, String mimeType) {
        // sanity checks
        if ((null == mimeType) || (null == url) || (null == context)) {
            return null;
        }

        // is the media already downloaded ?
        if (null != mediaCacheFilename(context, url, mimeType)) {
            return null;
        }

        String downloadableUrl = downloadableUrl(context, url, -1, -1);

        // is the media downloading  ?
        if (null != MXMediaWorkerTask.mediaWorkerTaskForUrl(downloadableUrl)) {
            return downloadableUrl;
        }

        // download it in background
        MXMediaWorkerTask task = new MXMediaWorkerTask(context, downloadableUrl, mimeType);

        // avoid crash if there are too many running task
        try {
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null);
        } catch (RejectedExecutionException e) {
            // too many tasks have been launched
            synchronized (mSuspendedTasks) {
                task.cancel(true);
                // create a new task from the existing one
                task = new MXMediaWorkerTask(task);
                mSuspendedTasks.add(task);
                Log.e(LOG_TAG, "Suspend the task " + task.getUrl());
            }

        } catch (Exception e) {

        }

        return downloadableUrl;
    }

    /**
     * Start any suspended task
     */
    private void launchSuspendedTask() {
        synchronized (mSuspendedTasks)
        {
            // some task have been suspended because there were too many running ones ?
            if (mSuspendedTasks.size() > 0) {

                if (mSuspendedTasks.size() > 0) {
                    MXMediaWorkerTask task = mSuspendedTasks.get(0);

                    Log.d(LOG_TAG, "Restart the task " + task.getUrl());

                    // avoid crash if there are too many running task
                    try {
                        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null);
                        mSuspendedTasks.remove(task);
                    } catch (RejectedExecutionException e) {
                        task.cancel(true);

                        mSuspendedTasks.remove(task);
                        // create a new task from the existing one
                        task = new MXMediaWorkerTask(task);
                        mSuspendedTasks.add(task);
                        Log.d(LOG_TAG, "Suspend again the task " + task.getUrl() + " - " + task.getStatus());

                    } catch (Exception e) {
                        Log.d(LOG_TAG, "Try to Restart a task fails " + e.getMessage());
                    }
                }
            }
        }
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
    public String loadBitmap(Context context, ImageView imageView, String url, int width, int height, int rotationAngle, String mimeType) {
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
        Bitmap bitmap = MXMediaWorkerTask.bitmapForURL(context.getApplicationContext(),downloadableUrl, rotationAngle, mimeType);

        if (null != bitmap) {
            if (null != imageView) {
                // display it
                imageView.setImageBitmap(bitmap);
            }
            downloadableUrl = null;
        } else {
            MXMediaWorkerTask currentTask = MXMediaWorkerTask.mediaWorkerTaskForUrl(downloadableUrl);

            if (null != currentTask) {
                if (null != imageView) {
                    currentTask.addImageView(imageView);
                }
            } else {
                // download it in background
                MXMediaWorkerTask task = new MXMediaWorkerTask(context, downloadableUrl, rotationAngle, mimeType);

                if (null != imageView) {
                    task.addImageView(imageView);
                }

                // check at the end of the download, if a suspended task can be launched again.
                task.addCallback(new DownloadCallback() {
                    @Override
                    public void onDownloadProgress(String downloadId, int percentageProgress) {
                    }

                    @Override
                    public void onDownloadComplete(String downloadId) {
                        MXMediasCache.this.launchSuspendedTask();
                    }
                });

                // avoid crash if there are too many running task
                try {
                    task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, null);
                } catch (RejectedExecutionException e) {
                    // too many tasks have been launched
                    synchronized (mSuspendedTasks) {
                        task.cancel(true);
                        // create a new task from the existing one
                        task = new MXMediaWorkerTask(task);
                        mSuspendedTasks.add(task);
                        Log.e(LOG_TAG, "Suspend the task " + task.getUrl());
                    }

                } catch (Exception e) {
                
                }
            }
        }

        return downloadableUrl;
    }

    /**
     * Returns the download progress (percentage).
     * @param downloadId the downloadId provided by loadBitmap;
     * @return the download progress
     */
    public int progressValueForDownloadId(String downloadId) {
        MXMediaWorkerTask currentTask = MXMediaWorkerTask.mediaWorkerTaskForUrl(downloadId);

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
    public void addDownloadListener(String downloadId, DownloadCallback callback) {
        MXMediaWorkerTask currentTask = MXMediaWorkerTask.mediaWorkerTaskForUrl(downloadId);

        if (null != currentTask) {
            currentTask.addCallback(callback);
        }
    }
}
