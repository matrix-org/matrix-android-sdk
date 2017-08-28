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
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import org.matrix.androidsdk.network.NetworkConnectivityReceiver;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.util.Log;
import android.webkit.MimeTypeMap;
import android.widget.ImageView;


import org.matrix.androidsdk.HomeserverConnectionConfig;
import org.matrix.androidsdk.listeners.IMXMediaDownloadListener;
import org.matrix.androidsdk.listeners.IMXMediaUploadListener;
import org.matrix.androidsdk.listeners.MXMediaDownloadListener;
import org.matrix.androidsdk.rest.model.EncryptedFileInfo;
import org.matrix.androidsdk.util.ContentManager;
import org.matrix.androidsdk.util.ContentUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;

public class MXMediasCache {

    private static final String LOG_TAG = "MXMediasCache";

    /**
     * The medias folders.
     */
    private static final String MXMEDIA_STORE_FOLDER = "MXMediaStore";
    private static final String MXMEDIA_STORE_MEMBER_THUMBNAILS_FOLDER = "MXMemberThumbnailsStore";
    private static final String MXMEDIA_STORE_IMAGES_FOLDER = "Images";
    private static final String MXMEDIA_STORE_OTHERS_FOLDER = "Others";

    /**
     * The content manager
     */
    private ContentManager mContentManager = null;

    /**
     * The medias folders list.
     */
    private File mMediasFolderFile = null;
    private File mImagesFolderFile = null;
    private File mOthersFolderFile = null;
    private File mThumbnailsFolderFile = null;

    // track the network updates
    private NetworkConnectivityReceiver mNetworkConnectivityReceiver;

    /**
     * Clear the former medias cache.
     * The dirtree has been updated.
     *
     * @param directory The upper directory file.
     */
    private void cleanFormerMediasCache(File directory) {
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
     * Constructor
     * @param contentManager the content manager.
     * @param networkConnectivityReceiver the network connectivity receiver
     * @param userID the account user Id.
     * @param context the context
     */
    public MXMediasCache(ContentManager contentManager, NetworkConnectivityReceiver networkConnectivityReceiver, String userID, Context context) {
        mContentManager = contentManager;
        mNetworkConnectivityReceiver = networkConnectivityReceiver;

        File mediaBaseFolderFile = new File(context.getApplicationContext().getFilesDir(), MXMEDIA_STORE_FOLDER);

        if (!mediaBaseFolderFile.exists()) {
            cleanFormerMediasCache(context.getApplicationContext().getFilesDir());
            mediaBaseFolderFile.mkdirs();
        }

        // create the dir tree
        mMediasFolderFile = new File(mediaBaseFolderFile, userID);
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
     * Compute the medias cache size
     *
     * @param context the context
     * @param callback the asynchronous callback
     */
    public static void getCachesSize(final Context context, final SimpleApiCallback<Long> callback) {
        AsyncTask<Void, Void, Long> task = new AsyncTask<Void, Void, Long>() {
            @Override
            protected Long doInBackground(Void... params) {
                return ContentUtils.getDirectorySize(context, new File(context.getApplicationContext().getFilesDir(), MXMEDIA_STORE_FOLDER), 1);
            }

            @Override
            protected void onPostExecute(Long result) {
                Log.d(LOG_TAG, "## getCachesSize() : " + result);
                if (null != callback) {
                    callback.onSuccess(result);
                }
            }
        };
        try {
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## getCachesSize() : failed " + e.getMessage());
            task.cancel(true);
        }
    }

    /**
     * Remove medias older than ts
     * @param ts the ts
     * @param filePathToKeep set of files to keep
     * @return length of deleted files
     */
    public long removeMediasBefore(long ts, Set<String> filePathToKeep) {
        long length = 0;

        length += removeMediasBefore(getMediasFolderFile(), ts, filePathToKeep);
        length += removeMediasBefore(getThumbnailsFolderFile(), ts, filePathToKeep);

        return length;
    }

    /**
     * Recursive method to remove older messages
     * @param folder the base folder
     * @param aTs the ts
     * @param filePathToKeep set of files to keep
     * @return length of deleted files
     */
    private long removeMediasBefore(File folder, long aTs, Set<String> filePathToKeep) {
        long length = 0;
        File[] files = folder.listFiles();

        if (null != files) {
            for (int i = 0; i < files.length; i++) {
                File file = files[i];

                if (!file.isDirectory()) {

                    if (!filePathToKeep.contains(file.getPath())) {
                        long ts = ContentUtils.getLastAccessTime(file);
                        if (ts < aTs) {
                            length += file.length();
                            file.delete();
                        }
                    }
                } else {
                    length += removeMediasBefore(file, aTs, filePathToKeep);
                }
            }
        }

        return length;
    }

    /**
     * Clear the medias caches.
     */
    public void clear() {
        ContentUtils.deleteDirectory(getMediasFolderFile());

        ContentUtils.deleteDirectory(mThumbnailsFolderFile);

        // clear the media cache
        MXMediaDownloadWorkerTask.clearBitmapsCache();

        // cancel pending uploads.
        MXMediaUploadWorkerTask.cancelPendingUploads();
    }

    /**
     * The thumbnails cached is not cleared when logging out a session
     * because many sessions share the same thumbnails.
     * This method must be called when performing an application logout
     * i.e. logging out of all sessions.
     */
    public static void clearThumbnailsCache(Context applicationContext) {
        ContentUtils.deleteDirectory(new File(new File(applicationContext.getApplicationContext().getFilesDir(), MXMediasCache.MXMEDIA_STORE_FOLDER), MXMEDIA_STORE_MEMBER_THUMBNAILS_FOLDER));
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
                return mContentManager.getDownloadableThumbnailUrl(url, width, height, ContentManager.METHOD_SCALE);
            } else {
                return mContentManager.getDownloadableUrl(url);
            }
        } else {
            return url;
        }
    }

    /**
     * Provide the thumbnail file.
     * @param url the thumbnail url/
     * @param size the thumbnail size.
     * @return the File if it exits.
     */
    public File thumbnailCacheFile(String url, int size) {
        // sanity check
        if (null == url) {
            return null;
        }

        String filename = MXMediaDownloadWorkerTask.buildFileName(downloadableUrl(url, size, size), "image/jpeg");

        try {
            File file = new File(getThumbnailsFolderFile(), filename);

            if (file.exists()) {
                return file;
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "thumbnailCacheFile failed " + e.getLocalizedMessage());
        }

        return null;
    }

    /**
     * Return the cache file name for a media defined by its URL and its mimetype.
     * @param url      the media url
     * @param mimeType the mime type
     * @return the media file it is found
     */
    public File mediaCacheFile(String url, String mimeType) {
        return mediaCacheFile(url, -1, -1, mimeType);
    }

    /**
     * Return the cache file name for a media defined by its URL and its mimetype.
     * @param url the media URL
     * @param width the media width
     * @param height the media height
     * @param mimeType the media mime type
     * @return the media file it is found
     */
    public File mediaCacheFile(String url, int width, int height, String mimeType) {
        // sanity check
        if (null == url) {
            return null;
        }

        String filename = (url.startsWith("file:")) ? url : MXMediaDownloadWorkerTask.buildFileName(downloadableUrl(url, width, height), mimeType);

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
            Log.e(LOG_TAG, "mediaCacheFile failed " + e.getLocalizedMessage());
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
            Log.e(LOG_TAG, "saveBitmap failed " + e.getLocalizedMessage());
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

                if (null == extension) {
                    if (mimeType.lastIndexOf("/") >= 0) {
                        extension = mimeType.substring(mimeType.lastIndexOf("/") +1);
                    }
                }

                if (!TextUtils.isEmpty(extension)) {
                    filename += "." + extension;
                }
            }
        }

        String cacheURL = null;

        try {
            File file = new File(getFolderFile(mimeType), filename);

            // if the file exits, delete it
            if (file.exists()) {
                file.delete();
            }

            FileOutputStream fos = new FileOutputStream(file.getPath());

            try {
                byte[] buf = new byte[1024 * 32];

                int len;
                while ((len = stream.read(buf)) != -1) {
                    fos.write(buf, 0, len);
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "saveMedia failed " + e.getLocalizedMessage());
            }

            fos.flush();
            fos.close();
            stream.close();

            cacheURL = Uri.fromFile(file).toString();
        } catch (Exception e) {
            Log.e(LOG_TAG, "saveMedia failed " + e.getLocalizedMessage());

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
        saveFileMediaForUrl(mediaUrl, fileUrl, width, height, mimeType, false);
    }

    /**
     * Copy or Replace a media cache by a file content.
     * MediaUrl is the same model as the one used in loadBitmap.
     *
     * @param mediaUrl the mediaUrl
     * @param fileUrl  the file which replaces the cached media.
     * @param width    the expected image width
     * @param height   the expected image height
     * @param mimeType the mimeType.
     * @param keepSource keep the source file
     */
    public void saveFileMediaForUrl(String mediaUrl, String fileUrl, int width, int height, String mimeType, boolean keepSource) {
        String downloadableUrl = downloadableUrl(mediaUrl, width, height);
        String filename = MXMediaDownloadWorkerTask.buildFileName(downloadableUrl, mimeType);

        try {
            // delete the current content
            File destFile = new File(getFolderFile(mimeType), filename);

            if (destFile.exists()) {
                try {
                    destFile.delete();
                } catch (Exception e) {
                    Log.e(LOG_TAG, "saveFileMediaForUrl delete failed " + e.getLocalizedMessage());
                }
            }

            Uri uri = Uri.parse(fileUrl);
            File srcFile = new File(uri.getPath());

            if (keepSource) {
                InputStream in = new FileInputStream(srcFile);
                OutputStream out = new FileOutputStream(destFile);

                // Transfer bytes from in to out
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                in.close();
                out.close();
            } else {
                srcFile.renameTo(destFile);
            }

        } catch (Exception e) {
            Log.e(LOG_TAG, "saveFileMediaForUrl failed " + e.getLocalizedMessage());
        }
    }

    /**
     * Load an avatar thumbnail.
     * The imageView image is updated when the bitmap is loaded or downloaded.
     *
     * @param hsConfig the home server config.
     * @param imageView Ihe imageView to update with the image.
     * @param url       the image url
     * @param side      the avatar thumbnail side
     * @return a download identifier if the image is not cached.
     */
    public String loadAvatarThumbnail(HomeserverConnectionConfig hsConfig, ImageView imageView, String url, int side) {
        return loadBitmap(imageView.getContext(), hsConfig, imageView, url, side, side, 0, ExifInterface.ORIENTATION_UNDEFINED, null, getThumbnailsFolderFile(), null);
    }

    /**
     * Load an avatar thumbnail.
     * The imageView image is updated when the bitmap is loaded or downloaded.
     *
     * @param hsConfig the home server config.
     * @param imageView Ihe imageView to update with the image.
     * @param url       the image url
     * @param side      the avatar thumbnail side
     * @param aDefaultAvatar the avatar to use when the Url is not reachable.
     * @return a download identifier if the image is not cached.
     */
    public String loadAvatarThumbnail(HomeserverConnectionConfig hsConfig, ImageView imageView, String url, int side, Bitmap aDefaultAvatar) {
        return loadBitmap(imageView.getContext(), hsConfig, imageView, url, side, side, 0, ExifInterface.ORIENTATION_UNDEFINED, null, getThumbnailsFolderFile(), aDefaultAvatar, null);
    }

    /**
     * Tells if the avatar is cached
     * @param url the avatar url to test
     * @return true if the avatar bitmap is cached.
     */
    public boolean isAvatarThumbnailCached(String url, int side) {
        return MXMediaDownloadWorkerTask.isUrlCached(downloadableUrl(url, side, side));
    }

    /**
     * Tells if the media URL is unreachable.
     * @param url the url to test.
     * @return true if the media URL is unreachable.
     */
    public static boolean isMediaUrlUnreachable(String url) {
        return MXMediaDownloadWorkerTask.isMediaUrlUnreachable(url);
    }

    /**
     * Load a bitmap from the url.
     * The imageView image is updated when the bitmap is loaded or downloaded.
     *
     * @param hsConfig      The home server config.
     * @param imageView     The imageView to update with the image.
     * @param url           the image url
     * @param rotationAngle the rotation angle (degrees)
     * @param orientation   the orientation (ExifInterface.ORIENTATION_XXX value)
     * @param mimeType      the mimeType.
     * @param encryptionInfo the encryption file info
     * @return a download identifier if the image is not cached.
     */
    public String loadBitmap(HomeserverConnectionConfig hsConfig, ImageView imageView, String url, int rotationAngle, int orientation, String mimeType, EncryptedFileInfo encryptionInfo) {
        return loadBitmap(hsConfig, imageView, url, -1, -1, rotationAngle, orientation, mimeType, encryptionInfo);
    }

    /**
     * Load a bitmap from the url.
     * The imageView image is updated when the bitmap is loaded or downloaded.
     *
     * @param hsConfig      The home server config.
     * @param context       The context
     * @param url           the image url
     * @param rotationAngle the rotation angle (degrees)
     * @param orientation   the orientation (ExifInterface.ORIENTATION_XXX value)
     * @param mimeType      the mimeType.
     * @param encryptionInfo the encryption file info
     * @return a download identifier if the image is not cached.
     */
    public String loadBitmap(Context context, HomeserverConnectionConfig hsConfig, String url, int rotationAngle, int orientation, String mimeType, EncryptedFileInfo encryptionInfo) {
        return loadBitmap(context, hsConfig, null, url, -1, -1, rotationAngle, orientation, mimeType, getFolderFile(mimeType), encryptionInfo);
    }

    /**
     * Load a bitmap from an url.
     * The imageView image is updated when the bitmap is loaded or downloaded.
     * The width/height parameters are optional. If they are > 0, download a thumbnail.
     * rotationAngle is set to Integer.MAX_VALUE when undefined : the EXIF metadata must be checked.
     *
     * @param hsConfig      The home server config.
     * @param imageView     The imageView to fill when the image is downloaded
     * @param url           the image url
     * @param width         the expected image width
     * @param height        the expected image height
     * @param rotationAngle the rotation angle (degrees)
     * @param orientation   the orientation (ExifInterface.ORIENTATION_XXX value)
     * @param mimeType      the mimeType.
     * @param encryptionInfo the encryption file info
     * @return a download identifier if the image is not cached
     */
    public String loadBitmap(HomeserverConnectionConfig hsConfig, ImageView imageView, String url, int width, int height, int rotationAngle,  int orientation, String mimeType, EncryptedFileInfo encryptionInfo) {
        return loadBitmap(imageView.getContext(), hsConfig, imageView, url, width, height, rotationAngle, orientation, mimeType, getFolderFile(mimeType), encryptionInfo);
    }

    // some tasks have been stacked because there are too many running ones.
    private final ArrayList<MXMediaDownloadWorkerTask> mSuspendedTasks = new ArrayList<>();

    /**
     * Returns the download ID from the media URL.
     *
     * @param url      the media url
     * @return the download ID
     */
    public String downloadIdFromUrl(String url) {
        return downloadableUrl(url, -1, -1);
    }

    /**
     * Download a media.
     * @param context  the application context
     * @param hsConfig the home server config.
     * @param url      the media url
     * @param mimeType the media mimetype
     * @param encryptionInfo the encryption information
     * @return the download identifier.
     */
    public String downloadMedia(Context context, HomeserverConnectionConfig hsConfig, String url, String mimeType, EncryptedFileInfo encryptionInfo) {
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
        if (null != MXMediaDownloadWorkerTask.getMediaDownloadWorkerTask(downloadableUrl)) {
            return downloadableUrl;
        }

        // download it in background
        MXMediaDownloadWorkerTask task = new MXMediaDownloadWorkerTask(context, hsConfig, mNetworkConnectivityReceiver, getFolderFile(mimeType), downloadableUrl, mimeType, encryptionInfo);

        // avoid crash if there are too many running task
        try {
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Integer[])null);
        } catch (RejectedExecutionException e) {
            // too many tasks have been launched
            synchronized (mSuspendedTasks) {
                task.cancel(true);
                // create a new task from the existing one
                task = new MXMediaDownloadWorkerTask(task);
                mSuspendedTasks.add(task);
                // privacy
                //Log.e(LOG_TAG, "Suspend the task " + task.getUrl());
                Log.e(LOG_TAG, "Suspend the task " );
            }

        } catch (Exception e) {
            Log.e(LOG_TAG, "downloadMedia failed " + e.getMessage());
            synchronized (mSuspendedTasks) {
                task.cancel(true);
            }
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
                    MXMediaDownloadWorkerTask task = mSuspendedTasks.get(0);

                    // privacy
                    //Log.d(LOG_TAG, "Restart the task " + task.getUrl());
                    Log.d(LOG_TAG, "Restart a task ");

                    // avoid crash if there are too many running task
                    try {
                        task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Integer[])null);
                        mSuspendedTasks.remove(task);
                    } catch (RejectedExecutionException e) {
                        task.cancel(true);

                        mSuspendedTasks.remove(task);
                        // create a new task from the existing one
                        task = new MXMediaDownloadWorkerTask(task);
                        mSuspendedTasks.add(task);

                        // privacy
                        //Log.d(LOG_TAG, "Suspend again the task " + task.getUrl() + " - " + task.getStatus());
                        Log.d(LOG_TAG, "Suspend again the task " + task.getStatus());

                    } catch (Exception e) {
                        Log.d(LOG_TAG, "Try to Restart a task fails " + e.getMessage());
                    }
                }
            }
        }
    }

    /**
     * Handler to post events on UI thread
     */
    private static Handler mUIHandler = null;

    /**
     * The default bitmap to use when the media cannot be retrieved.
     */
    private static Bitmap mDefaultBitmap = null;

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
     * @param hsConfig the home server config
     * @param imageView the imageView to fill when the image is downloaded
     * @param url the image url
     * @param width the expected image width
     * @param height the expected image height
     * @param rotationAngle the rotation angle (degrees)
     * @param orientation   the orientation (ExifInterface.ORIENTATION_XXX value)
     * @param mimeType the mimeType.
     * @param folderFile the folder where the media should be stored
     * @param encryptionInfo the encryption file information.
     * @return a download identifier if the image is not cached
     */
    public String loadBitmap(Context context, HomeserverConnectionConfig hsConfig, final ImageView imageView, String url, int width, int height, int rotationAngle, int orientation, String mimeType, File folderFile, EncryptedFileInfo encryptionInfo) {
        return loadBitmap(context, hsConfig, imageView, url, width, height, rotationAngle, orientation, mimeType, folderFile, null, encryptionInfo);
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
     * @param hsConfig the home server config
     * @param imageView the imageView to fill when the image is downloaded
     * @param url the image url
     * @param width the expected image width
     * @param height the expected image height
     * @param rotationAngle the rotation angle (degrees)
     * @param orientation   the orientation (ExifInterface.ORIENTATION_XXX value)
     * @param mimeType the mimeType.
     * @param folderFile the folder where the media should be stored
     * @param aDefaultBitmap the default bitmap to use when the url media cannot be retrieved.
     * @param encryptionInfo the file encryption info
     * @return a download identifier if the image is not cached
     */
    public String loadBitmap(Context context, HomeserverConnectionConfig hsConfig, final ImageView imageView, String url, int width, int height, int rotationAngle, int orientation, String mimeType, File folderFile, Bitmap aDefaultBitmap, EncryptedFileInfo encryptionInfo) {
        if (null == url) {
            return null;
        }

        // request invalid bitmap size
        if ((0 == width) || (0 == height)) {
            return null;
        }

        if (null == mDefaultBitmap) {
            mDefaultBitmap = BitmapFactory.decodeResource(context.getResources(), android.R.drawable.ic_menu_gallery);
        }

        Bitmap defaultBimap = (null == aDefaultBitmap) ? mDefaultBitmap : aDefaultBitmap;
        String downloadableUrl;

        // it is not possible to resize an encrypted image
        if (null == encryptionInfo) {
            downloadableUrl = downloadableUrl(url, width, height);
        } else {
            downloadableUrl = downloadableUrl(url, -1, -1);
        }

        // the thumbnail params are ignored when encrypted
        if ((null == encryptionInfo) && (rotationAngle == Integer.MAX_VALUE) && (orientation != ExifInterface.ORIENTATION_UNDEFINED) && (orientation != ExifInterface.ORIENTATION_NORMAL)) {
            if (downloadableUrl.indexOf("?") != -1) {
                downloadableUrl += "&apply_orientation=true";
            } else {
                downloadableUrl += "?apply_orientation=true";
            }
        }

        final String fDownloadableUrl = downloadableUrl;

        if (null != imageView) {
            imageView.setTag(fDownloadableUrl);
        }

        // if the mime type is not provided, assume it is a jpeg file
        if (null == mimeType) {
            mimeType = "image/jpeg";
        }

        // check if the bitmap is already cached
        final Bitmap bitmap = (MXMediaDownloadWorkerTask.isMediaUrlUnreachable(downloadableUrl)) ? defaultBimap : MXMediaDownloadWorkerTask.bitmapForURL(context.getApplicationContext(), folderFile, downloadableUrl, rotationAngle, mimeType);

        if (null != bitmap) {
            if (null != imageView) {
                if (Looper.getMainLooper().getThread() == Thread.currentThread()) {
                    // display it
                    imageView.setImageBitmap(bitmap);
                } else {
                    // init
                    if (null == mUIHandler) {
                        mUIHandler = new Handler(Looper.getMainLooper());
                    }

                    // handle any thread management
                    // the image should be loaded from any thread
                    mUIHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (TextUtils.equals(fDownloadableUrl, (String) imageView.getTag())) {
                                // display it
                                imageView.setImageBitmap(bitmap);
                            }
                        }
                    });
                }
            }

            downloadableUrl = null;
        } else {
            MXMediaDownloadWorkerTask currentTask = MXMediaDownloadWorkerTask.getMediaDownloadWorkerTask(downloadableUrl);

            if (null != currentTask) {
                if (null != imageView) {
                    currentTask.addImageView(imageView);
                }
            } else {
                // download it in background
                MXMediaDownloadWorkerTask task = new MXMediaDownloadWorkerTask(context, hsConfig, mNetworkConnectivityReceiver, folderFile, downloadableUrl, rotationAngle, mimeType, encryptionInfo);

                if (null != imageView) {
                    task.addImageView(imageView);
                }

                task.setDefaultBitmap(defaultBimap);

                // check at the end of the download, if a suspended task can be launched again.
                task.addDownloadListener(new MXMediaDownloadListener() {
                    @Override
                    public void onDownloadComplete(String downloadId) {
                        MXMediasCache.this.launchSuspendedTask();
                    }
                });


                // avoid crash if there are too many running task
                try {
                    task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Integer[])null);
                } catch (RejectedExecutionException e) {
                    // too many tasks have been launched
                    synchronized (mSuspendedTasks) {
                        task.cancel(true);
                        // create a new task from the existing one
                        task = new MXMediaDownloadWorkerTask(task);
                        mSuspendedTasks.add(task);
                        // privacy
                        //Log.e(LOG_TAG, "Suspend the task " + task.getUrl());
                        Log.e(LOG_TAG, "Suspend a task ");
                    }

                } catch (Exception e) {
                    Log.e(LOG_TAG, "loadBitmap failed " + e.getLocalizedMessage());
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
    public int getProgressValueForDownloadId(String downloadId) {
        MXMediaDownloadWorkerTask currentTask = MXMediaDownloadWorkerTask.getMediaDownloadWorkerTask(downloadId);

        if (null != currentTask) {
            return currentTask.getProgress();
        }
        return -1;
    }

    /**
     * Returns the download  stats for a dedicated download id.
     * @param downloadId the downloadId provided by loadBitmap;
     * @return the download stats
     */
    public IMXMediaDownloadListener.DownloadStats getStatsForDownloadId(String downloadId) {
        MXMediaDownloadWorkerTask currentTask = MXMediaDownloadWorkerTask.getMediaDownloadWorkerTask(downloadId);

        if (null != currentTask) {
            return currentTask.getDownloadStats();
        }

        return null;
    }

    /**
     * Add a download listener for an downloadId.
     * @param downloadId The uploadId.
     * @param listener the download listener.
     */
    public void addDownloadListener(String downloadId, IMXMediaDownloadListener listener) {
        MXMediaDownloadWorkerTask currentTask = MXMediaDownloadWorkerTask.getMediaDownloadWorkerTask(downloadId);

        if (null != currentTask) {
            currentTask.addDownloadListener(listener);
        }
    }

    /**
     * Cancel a download.
     * @param downloadId the download id.
     */
    public void cancelDownload(String downloadId) {
        MXMediaDownloadWorkerTask currentTask = MXMediaDownloadWorkerTask.getMediaDownloadWorkerTask(downloadId);

        if (null != currentTask) {
            currentTask.cancelDownload();
        }
    }

    /**
     * Upload a file
     * @param contentStream the stream to upload
     * @param filename the dst fileanme
     * @param mimeType the mimetype
     * @param uploadId the upload id
     * @param listener the upload progress listener
     */
    public void uploadContent(InputStream contentStream, String filename, String mimeType, String uploadId, IMXMediaUploadListener listener) {
        try {
             new MXMediaUploadWorkerTask(mContentManager, contentStream, mimeType, uploadId, filename, listener).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        } catch (Exception e) {
            // cannot start the task
            if (null != listener) {
                listener.onUploadError(uploadId, -1, null);
            }
        }
    }

    /**
     * Returns the upload progress (percentage) for a dedicated uploadId
     * @param uploadId The uploadId.
     * @return the upload percentage. -1 means there is no pending upload.
     */
    public int getProgressValueForUploadId(String uploadId) {
        MXMediaUploadWorkerTask uploadTask = MXMediaUploadWorkerTask.getMediaDUploadWorkerTask(uploadId);

        if (null != uploadTask) {
            return uploadTask.getProgress();
        }

        return -1;
    }

    /**
     * Returns the upload stats for a dedicated uploadId
     * @param uploadId The uploadId.
     * @return the upload stats
     */
    public IMXMediaUploadListener.UploadStats getStatsForUploadId(String uploadId) {
        MXMediaUploadWorkerTask uploadTask = MXMediaUploadWorkerTask.getMediaDUploadWorkerTask(uploadId);

        if (null != uploadTask) {
            return uploadTask.getStats();
        }

        return null;
    }


    /**
     * Add an upload listener for an uploadId.
     * @param uploadId The uploadId.
     * @param listener the upload listener
     */
    public void addUploadListener(String uploadId, IMXMediaUploadListener listener) {
        MXMediaUploadWorkerTask uploadTask = MXMediaUploadWorkerTask.getMediaDUploadWorkerTask(uploadId);

        if (null != uploadTask) {
            uploadTask.addListener(listener);
        }
    }

    /**
     * Cancel an upload.
     * @param uploadId the upload Id
     */
    public void cancelUpload(String uploadId) {
        MXMediaUploadWorkerTask uploadTask = MXMediaUploadWorkerTask.getMediaDUploadWorkerTask(uploadId);

        if (null != uploadTask) {
            uploadTask.cancelUpload();
        }
    }
}
