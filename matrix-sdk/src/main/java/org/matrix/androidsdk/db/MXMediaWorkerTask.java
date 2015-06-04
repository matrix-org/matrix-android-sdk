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
import android.net.Uri;
import android.os.AsyncTask;
import android.support.v4.util.LruCache;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.widget.ImageView;

import org.matrix.androidsdk.util.ImageUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;

class MXMediaWorkerTask extends AsyncTask<Integer, Integer, Bitmap> {

    private static final String LOG_TAG = "MediaWorkerTask";

    private static HashMap<String, MXMediaWorkerTask> mPendingDownloadByUrl = new HashMap<String, MXMediaWorkerTask>();

    private static LruCache<String, Bitmap> sMemoryCache = null;

    private ArrayList<MXMediasCache.DownloadCallback> mCallbacks = new ArrayList<MXMediasCache.DownloadCallback>();
    private final ArrayList<WeakReference<ImageView>> mImageViewReferences;
    private String mUrl;
    private String mMimeType;
    private Context mApplicationContext;
    private File mDirectoryFile = null;
    private int mRotation = 0;
    private int mProgress = 0;

    public static void clearBitmapsCache() {
        sMemoryCache.evictAll();
    }

    public String getUrl() {
        return mUrl;
    }

    /**
     * Check if there is a pending download for the url.
     * @param url The url to check the existence
     * @return the dedicated BitmapWorkerTask if it exists.
     */
    public static MXMediaWorkerTask mediaWorkerTaskForUrl(String url) {
        if ((url != null) &&  mPendingDownloadByUrl.containsKey(url)) {
            MXMediaWorkerTask task;
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
        }

        if (null == uniqueId) {
            uniqueId = "" + Math.abs(input.hashCode());
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
        String name = "file_" + MXMediaWorkerTask.uniqueId(Url);

        if (null == mimeType) {
            mimeType = "image/jpeg";
        }

        String fileExtension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);

        // some devices don't support .jpeg files
        if ("jpeg".equals(fileExtension)) {
            fileExtension = "jpg";
        }

        if (null != fileExtension) {
            name += "." + fileExtension;
        }

        return name;
    }

    /**
     * Search a cached bitmap from an url.
     * rotationAngle is set to Integer.MAX_VALUE when undefined : the EXIF metadata must be checked.
     *
     * @param baseFile the base file
     * @param url the media url
     * @param rotation the bitmap rotation
     * @param mimeType the mime type
     * @return the cached bitmap or null it does not exist
     */
    public static Bitmap bitmapForURL(Context context, File baseFile, String url, int rotation, String mimeType) {
        Bitmap bitmap = null;

        // sanity check
        if (null != url) {

            if (null == sMemoryCache) {
                int lruSize = Math.min(2 * 1024 * 1024, (int)Runtime.getRuntime().maxMemory() / 8);

                Log.d(LOG_TAG, "bitmapForURL  lruSize : " + lruSize);

                sMemoryCache = new LruCache<String, Bitmap>(lruSize){
                    @Override
                    protected int sizeOf(String key, Bitmap bitmap) {
                        return bitmap.getRowBytes() * bitmap.getHeight(); // size in bytes
                    }
                };
            }

            // the image is downloading in background
            if (null != mediaWorkerTaskForUrl(url)) {
                return null;
            }

            synchronized (sMemoryCache) {
                bitmap = sMemoryCache.get(url);
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

                    FileInputStream fis = new FileInputStream (file);

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

                                // cache only small images
                                // caching large images does not make sense
                                // it would replace small ones.
                                // let assume that the application must be faster when showing the chat history.
                                if ((bitmap.getWidth() < 1000) && (bitmap.getHeight() < 1000)) {
                                    sMemoryCache.put(url, bitmap);
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
     * BitmapWorkerTask creator
     * @param appContext the context
     * @param directoryFile the directry in which the media must be stored
     * @param url the media url
     * @param mimeType the mime type.
     */
    public MXMediaWorkerTask(Context appContext, File directoryFile, String url, String mimeType) {
        commonInit(appContext, url, mimeType);
        mDirectoryFile = directoryFile;
        mImageViewReferences = new ArrayList<WeakReference<ImageView>>();
    }

    /**
     * BitmapWorkerTask creator
     * @param appContext the context
     * @param directoryFile the directry in which the media must be stored
     * @param url the media url
     * @param rotation the rotation
     * @param mimeType the mime type.
     */
    public MXMediaWorkerTask(Context appContext, File directoryFile, String url, int rotation, String mimeType) {
        commonInit(appContext, url, mimeType);
        mImageViewReferences = new ArrayList<WeakReference<ImageView>>();
        mDirectoryFile = directoryFile;
        mRotation = rotation;
    }

    /**
     * BitmapWorkerTask creator
     * @param task another bitmap task
     */
    public MXMediaWorkerTask(MXMediaWorkerTask task) {
        mApplicationContext = task.mApplicationContext;
        mUrl = task.mUrl;
        mRotation = task.mRotation;
        synchronized(mPendingDownloadByUrl) {
            mPendingDownloadByUrl.put(mUrl, this);
        }
        mMimeType = task.mMimeType;
        mImageViewReferences = task.mImageViewReferences;
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
    public void addCallback(MXMediasCache.DownloadCallback callback) {
        mCallbacks.add(callback);
    }

    /**
     * Returns the download progress.
     * @return the download progress
     */
    public int getProgress() {
        return mProgress;
    }

    private Boolean isBitmapDownload() {
        return (null == mMimeType) || mMimeType.startsWith("image/");
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
                // add a timeout to avoid infinite loading display.
                connection.setReadTimeout(10 * 1000);
                filelen = connection.getContentLength();
                stream = connection.getInputStream();
            } catch (FileNotFoundException e) {
                Log.d(LOG_TAG, "MediaWorkerTask " + mUrl + " does not exist");
                if (isBitmapDownload()) {
                    bitmap = BitmapFactory.decodeResource(mApplicationContext.getResources(), android.R.drawable.ic_menu_gallery);
                }
            }

            String filename = MXMediaWorkerTask.buildFileName(mUrl, mMimeType) + ".tmp";
            FileOutputStream fos = new FileOutputStream(new File(mDirectoryFile, filename));

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

                    mProgress = 100;
                }
                catch (OutOfMemoryError outOfMemoryError) {
                    Log.e(LOG_TAG, "MediaWorkerTask : out of memory");
                }
                catch (Exception e) {
                    Log.e(LOG_TAG, "MediaWorkerTask fail to read image " + e.getMessage());
                }

                close(stream);
            }

            fos.flush();
            fos.close();

            // the file has been successfully downloaded
            if (mProgress == 100) {
                try {
                    File originalFile = new File(mDirectoryFile, filename);
                    String newFileName = MXMediaWorkerTask.buildFileName(mUrl, mMimeType);
                    File newFile = new File(mDirectoryFile, newFileName);
                    if (newFile.exists()) {
                        // Or you could throw here.
                        mApplicationContext.deleteFile(newFileName);
                    }
                    originalFile.renameTo(newFile);
                } catch (Exception e) {
                }
            }

            Log.d(LOG_TAG, "download is done (" + mUrl + ")");

            synchronized(mPendingDownloadByUrl) {
                mPendingDownloadByUrl.remove(mUrl);
            }

            // load the bitmap from the cache
            if (isBitmapDownload()) {
                // get the bitmap from the filesytem
                if (null == bitmap) {
                    bitmap = MXMediaWorkerTask.bitmapForURL(mApplicationContext, mDirectoryFile, key, mRotation, mMimeType);
                }
            }

            return bitmap;
        }
        catch (Exception e) {
            // remove the image from the loading one
            // else the loading will be stucked (and never be tried again).
            synchronized(mPendingDownloadByUrl) {
                mPendingDownloadByUrl.remove(mUrl);
            }
            Log.e(LOG_TAG, "Unable to load bitmap: "+e);
            return null;
        }
    }

    /**
     * Dispatch progress update to the callbacks.
     * @param progress the new progress value
     */
    private void sendProgress(int progress) {
        for(MXMediasCache.DownloadCallback callback : mCallbacks) {
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
        for(MXMediasCache.DownloadCallback callback : mCallbacks) {
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

    private void close(InputStream stream) {
        try {
            stream.close();
        }
        catch (Exception e) {} // don't care, it's being closed!
    }
}
