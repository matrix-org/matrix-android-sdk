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
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.widget.ImageView;

import org.matrix.androidsdk.util.ContentManager;
import org.matrix.androidsdk.util.ContentUtils;

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
    final String MXMEDIA_STORE_FOLDER = "MXMediaStore";
    final String MXMEDIA_STORE_MEMBER_THUMBNAILS_FOLDER = "MXMemberThumbnailsStore";
    final String MXMEDIA_STORE_IMAGES_FOLDER = "Images";
    final String MXMEDIA_STORE_OTHERS_FOLDER = "Others";

    private ContentManager mContentmanager = null;

    private String mUserID = null;
    private File mMediasFolderFile = null;
    private File mImagesFolderFile = null;
    private File mOthersFolderFile = null;
    private File mThumbnailsFolderFile = null;

    /**
     * Clear the former medias cache.
     * The dirtree has been updated.
     *
     * @param directory The upper directory file.
     */
    public void cleanFormerMediasCache(File directory) {
        File[] files = directory.listFiles();

        if (null != files) {
            for(int i=0; i<files.length; i++) {
                if(!files[i].isDirectory()) {
                    String fileName = files[i].getName();

                    // remove standard medias 
                    if (fileName.endsWith(".jpeg") || fileName.endsWith(".jpg") || fileName.endsWith(".tmp") || fileName.endsWith(".gif")) {
                        files[i].delete();
                    }
                }
            }
        }
    }

    /**
     * constructor
     *
     * @param contentManager
     */
    public MXMediasCache(ContentManager contentManager, String userID, Context context) {
        mContentmanager = contentManager;
        mUserID = userID;

        File mediaBaseFolderFile = new File(context.getApplicationContext().getFilesDir(), MXMEDIA_STORE_FOLDER);

        if (!mediaBaseFolderFile.exists()) {
            cleanFormerMediasCache(context.getApplicationContext().getFilesDir());
            mediaBaseFolderFile.mkdirs();
        }

        // create the dir tree
        mMediasFolderFile = new File(mediaBaseFolderFile, mUserID);
        mImagesFolderFile = new File(mMediasFolderFile, MXMEDIA_STORE_IMAGES_FOLDER);
        mOthersFolderFile = new File(mMediasFolderFile, MXMEDIA_STORE_OTHERS_FOLDER);

        mThumbnailsFolderFile = new File(mediaBaseFolderFile, MXMEDIA_STORE_MEMBER_THUMBNAILS_FOLDER);
    }

    /**
     * Returns the mediasFolder files.
     * Creates it if it does not exist
     *
     * @return the medias folder file.
     */
    private File getMediasFolderFile() {
        if (!mMediasFolderFile.exists()) {
            mMediasFolderFile.mkdirs();
        }

        return mMediasFolderFile;
    }

    /**
     * Returns the folder file for a dedicated mimetype.
     * Creates it if it does not exist
     *
     * @param mimeType the media mimetype.
     * @return the folder file.
     */
    private File getFolderFile(String mimeType) {
        File file;

        //
        if ((null == mimeType) || mimeType.startsWith("image/")) {
            file = mImagesFolderFile;
        } else {
            file = mOthersFolderFile;
        }

        if (!file.exists()) {
            file.mkdirs();
        }

        return file;
    }

    /**
     * Returns the thumbnails folder.
     * Creates it if it does not exist
     *
     * @return the thumbnails folder file.
     */
    private File getThumbnailsFolderFile() {
        if (!mThumbnailsFolderFile.exists()) {
            mThumbnailsFolderFile.mkdirs();
        }

        return mThumbnailsFolderFile;
    }

    /**
     * Recursive method to compute a directory sie
     * @param directory the directory.
     * @return the directory size in bytes.
     */
    private long cacheSize(File directory) {
        long size = 0;

        File[] files = directory.listFiles();

        if (null != files) {
            for(int i=0; i<files.length; i++) {
                File file = files[i];

                if(!file.isDirectory()) {
                    size += file.length();
                } else {
                    size += cacheSize(file);
                }
            }
        }

        return size;
    }

    /**
     * Compute the medias cache size
     *
     * @return the medias cache size in bytes
     */
    public long cacheSize() {
        return cacheSize(getMediasFolderFile());
    }

    /**
     * Clear the medias caches.
     */
    public void clearCache() {
        ContentUtils.deleteDirectory(getMediasFolderFile());
        // clear the media cache
        MXMediaWorkerTask.clearBitmapsCache();
    }

    /**
     * Convert matrix url into http one.
     *
     * @param url     the matrix url
     * @param width   the expected image width
     * @param height  the expected image height
     * @return the URL to access the described resource.
     */
    private String downloadableUrl(String url, int width, int height) {
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
     * @param url      the media url
     * @param mimeType the mime type
     * @return the cache file
     */
    public File mediaCacheFile(String url, String mimeType) {
        return mediaCacheFile(url, -1, -1, mimeType);
    }

    public File mediaCacheFile(String url, int width, int height, String mimeType) {
        // sanity check
        if (null == url) {
            return null;
        }

        String filename = MXMediaWorkerTask.buildFileName(downloadableUrl(url, width, height), mimeType);

        try {
            // already a local file
            if (filename.startsWith("file:")) {
                Uri uri = Uri.parse(filename);
                filename = uri.getLastPathSegment();
            }

            File file = new File(getFolderFile(mimeType), filename);

            if (file.exists()) {
                return file;
            }

        } catch (Exception e) {
        }

        return null;
    }

    /**
     * Save a bitmap to the local cache
     * it could be used for unsent media to allow them to be resent.
     *
     * @param bitmap          the bitmap to save
     * @param defaultFileName the filename is provided, if null, a filename will be generated
     * @return the media cache URL
     */
    public String saveBitmap(Bitmap bitmap, String defaultFileName) {
        String filename = "file" + System.currentTimeMillis() + ".jpg";
        String cacheURL = null;

        try {
            if (null != defaultFileName) {
                File file = new File(getFolderFile(null), defaultFileName);
                file.delete();

                filename = Uri.fromFile(file).getLastPathSegment();
            }

            File file = new File(getFolderFile(null), filename);
            FileOutputStream fos = new FileOutputStream(file.getPath());

            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);

            fos.flush();
            fos.close();

            cacheURL = Uri.fromFile(file).toString();
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
    public String saveMedia(InputStream stream, String defaultFileName, String mimeType) {
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
            File file = new File(getFolderFile(mimeType), filename);
            FileOutputStream fos = new FileOutputStream(file.getPath());

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

            cacheURL = Uri.fromFile(file).toString();
        } catch (Exception e) {

        }

        return cacheURL;
    }

    /**
     * Replace a media cache by a file content.
     *
     * @param mediaUrl the mediaUrl
     * @param mimeType the mimeType.
     * @param fileUrl  the file which replaces the cached media.
     */
    public void saveFileMediaForUrl(String mediaUrl, String fileUrl, String mimeType) {
        saveFileMediaForUrl(mediaUrl, fileUrl, -1, -1, mimeType);
    }

    /**
     * Replace a media cache by a file content.
     * MediaUrl is the same model as the one used in loadBitmap.
     *
     * @param mediaUrl the mediaUrl
     * @param fileUrl  the file which replaces the cached media.
     * @param width    the expected image width
     * @param height   the expected image height
     * @param mimeType the mimeType.
     */
    public void saveFileMediaForUrl(String mediaUrl, String fileUrl, int width, int height, String mimeType) {
        String downloadableUrl = downloadableUrl(mediaUrl, width, height);
        String filename = MXMediaWorkerTask.buildFileName(downloadableUrl, mimeType);

        try {
            // delete the current content
            File destFile = new File(getFolderFile(mimeType), filename);

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
        return loadBitmap(imageView.getContext(), imageView, url, side, side, 0, ExifInterface.ORIENTATION_UNDEFINED,  null, getThumbnailsFolderFile());
    }

    /**
     * Load a bitmap from the url.
     * The imageView image is updated when the bitmap is loaded or downloaded.
     *
     * @param imageView     Ihe imageView to update with the image.
     * @param url           the image url
     * @param rotationAngle the rotation angle (degrees)
     * @param orientation   the orientation (ExifInterface.ORIENTATION_XXX value)
     * @param mimeType      the mimeType.
     * @return a download identifier if the image is not cached.
     */
    public String loadBitmap(ImageView imageView, String url, int rotationAngle, int orientation, String mimeType) {
        return loadBitmap(imageView, url, -1, -1, rotationAngle, orientation, mimeType);
    }

    /**
     * Load a bitmap from the url.
     * The imageView image is updated when the bitmap is loaded or downloaded.
     *
     * @param context       The context
     * @param url           the image url
     * @param rotationAngle the rotation angle (degrees)
     * @param orientation   the orientation (ExifInterface.ORIENTATION_XXX value)
     * @param mimeType      the mimeType.
     * @return a download identifier if the image is not cached.
     */
    public String loadBitmap(Context context, String url, int rotationAngle, int orientation, String mimeType) {
        return loadBitmap(context, null, url, -1, -1, rotationAngle, orientation, mimeType, getFolderFile(mimeType));
    }

    /**
     * Load a bitmap from an url.
     * The imageView image is updated when the bitmap is loaded or downloaded.
     * The width/height parameters are optional. If they are > 0, download a thumbnail.
     * rotationAngle is set to Integer.MAX_VALUE when undefined : the EXIF metadata must be checked.
     *
     * @param imageView     the imageView to fill when the image is downloaded
     * @param url           the image url
     * @param width         the expected image width
     * @param height        the expected image height
     * @param rotationAngle the rotation angle (degrees)
     * @param orientation   the orientation (ExifInterface.ORIENTATION_XXX value)
     * @param mimeType      the mimeType.
     * @return a download identifier if the image is not cached
     */
    public String loadBitmap(ImageView imageView, String url, int width, int height, int rotationAngle,  int orientation, String mimeType) {
        return loadBitmap(imageView.getContext(), imageView, url, width, height, rotationAngle, orientation, mimeType, getFolderFile(mimeType));
    }

    // some tasks have been stacked because there are too many running ones.
    ArrayList<MXMediaWorkerTask> mSuspendedTasks = new ArrayList<MXMediaWorkerTask>();

    /**
     * Retuns the download ID from the media URL.
     *
     * @param url      the media url
     * @return the download ID
     */
    public String downloadIdFromUrl(String url) {
        return downloadableUrl(url, -1, -1);
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
        if (null != mediaCacheFile(url, mimeType)) {
            return null;
        }

        String downloadableUrl = downloadableUrl(url, -1, -1);

        // is the media downloading  ?
        if (null != MXMediaWorkerTask.mediaWorkerTaskForUrl(downloadableUrl)) {
            return downloadableUrl;
        }

        // download it in background
        MXMediaWorkerTask task = new MXMediaWorkerTask(context, getFolderFile(mimeType), downloadableUrl, mimeType);

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
     *
     * The rotation angle is checked first.
     * If rotationAngle is set to Integer.MAX_VALUE, check the orientation is defined to a valid value.
     * If the orientation is defined, request the properly oriented image to the server
     *
     *
     * @param context the context
     * @param imageView the imageView to fill when the image is downloaded
     * @param url the image url
     * @param width the expected image width
     * @param height the expected image height
     * @param rotationAngle the rotation angle (degrees)
     * @param orientation   the orientation (ExifInterface.ORIENTATION_XXX value)
     * @param mimeType the mimeType.
     * @param folderFile tye folder where the media should be stored
     * @return a download identifier if the image is not cached
     */
    public String loadBitmap(Context context, ImageView imageView, String url, int width, int height, int rotationAngle, int orientation, String mimeType, File folderFile) {
        if (null == url) {
            return null;
        }

        // request invalid bitmap size
        if ((0 == width) || (0 == height)) {
            return null;
        }

        String downloadableUrl = downloadableUrl(url, width, height);

        if ((rotationAngle == Integer.MAX_VALUE) && (orientation != ExifInterface.ORIENTATION_UNDEFINED) && (orientation != ExifInterface.ORIENTATION_NORMAL)) {
            if (downloadableUrl.indexOf("?") != -1) {
                downloadableUrl += "&apply_orientation=true";
            } else {
                downloadableUrl += "?apply_orientation=true";
            }
        }

        if (null != imageView) {
            imageView.setTag(downloadableUrl);
        }

        // if the mime type is not provided, assume it is a jpeg file
        if (null == mimeType) {
            mimeType = "image/jpeg";
        }

        // check if the bitmap is already cached
        Bitmap bitmap = MXMediaWorkerTask.bitmapForURL(context.getApplicationContext(), folderFile, downloadableUrl, rotationAngle, mimeType);

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
                MXMediaWorkerTask task = new MXMediaWorkerTask(context, folderFile, downloadableUrl, rotationAngle, mimeType);

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
