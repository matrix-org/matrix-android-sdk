package org.matrix.matrixandroidsdk.adapters;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.util.LruCache;
import android.text.Html;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.Message;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.util.ContentManager;
import org.matrix.matrixandroidsdk.Matrix;
import org.matrix.matrixandroidsdk.R;
import org.matrix.matrixandroidsdk.view.PieFractionView;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

/**
 * Contains useful functions for adapters.
 */
public class AdapterUtils {
    private static final String LOG_TAG = "AdapterUtils";

    public static String BROADCAST_DOWNLOAD_PROGRESS = "org.matrix.matrixandroidsdk.adapters.AdapterUtils.BROADCAST_DOWNLOAD_PROGRESS";
    public static String DOWNLOAD_PROGRESS_VALUE = "org.matrix.matrixandroidsdk.adapters.AdapterUtils.DOWNLOAD_PROGRESS_VALUE";
    public static String DOWNLOAD_PROGRESS_IDENTIFIER = "org.matrix.matrixandroidsdk.adapters.AdapterUtils.DOWNLOAD_PROGRESS_IDENTIFIER";

    public static class EventDisplay {
        private Event mEvent;
        private Context mContext;
        private boolean mPrependAuthor;
        private RoomState mRoomState;

        public EventDisplay(Context context, Event event, RoomState roomState) {
            mContext = context.getApplicationContext();
            mEvent = event;
            mRoomState = roomState;
        }

        /**
         * <p>Prepend the text with the author's name if they have not been mentioned in the text.</p>
         * This will prepend text messages with the author's name. This will NOT prepend things like
         * emote, room topic changes, etc which already mention the author's name in the message.
         * @param prepend
         */
        public void setPrependMessagesWithAuthor(boolean prepend) {
            mPrependAuthor = prepend;
        }

        private String getUserDisplayName(String userId) {
            return mRoomState.getMemberName(userId);
        }

        /**
         * Get the textual body for this event.
         * @return The text or null if it isn't possible.
         */
        public CharSequence getTextualDisplay() {
            CharSequence text = null;
            try {
                String userDisplayName = getUserDisplayName(mEvent.userId);
                if (Event.EVENT_TYPE_MESSAGE.equals(mEvent.type)) {

                    String msgtype = (null != mEvent.content.get("msgtype")) ? mEvent.content.get("msgtype").getAsString() : "";

                    if (msgtype.equals(Message.MSGTYPE_IMAGE)) {
                        text = mContext.getString(R.string.summary_user_sent_image, userDisplayName);
                    } else {
                        // all m.room.message events should support the 'body' key fallback, so use it.
                        text = mEvent.content.get("body") == null ? null : mEvent.content.get("body").getAsString();

                        // check for html formatting
                        if (mEvent.content.has("formatted_body") && mEvent.content.has("format")) {
                            String format = mEvent.content.getAsJsonPrimitive("format").getAsString();
                            if ("org.matrix.custom.html".equals(format)) {
                                text = Html.fromHtml(mEvent.content.getAsJsonPrimitive("formatted_body").getAsString());
                            }
                        }

                        if (mPrependAuthor) {
                            text = mContext.getString(R.string.summary_message, userDisplayName, text);
                        }
                    }
                }
                else if (Event.EVENT_TYPE_STATE_ROOM_TOPIC.equals(mEvent.type)) {
                    // pretty print 'XXX changed the topic to YYYY'
                    text = mContext.getString(R.string.notice_topic_changed,
                            userDisplayName, mEvent.content.getAsJsonPrimitive("topic").getAsString());
                }
                else if (Event.EVENT_TYPE_STATE_ROOM_NAME.equals(mEvent.type)) {
                    // pretty print 'XXX changed the room name to YYYY'
                    text = mContext.getString(R.string.notice_room_name_changed,
                            userDisplayName, mEvent.content.getAsJsonPrimitive("name").getAsString());
                }
                else if (Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(mEvent.type)) {
                    // m.room.member is used to represent at least 3 different changes in state: membership,
                    // avatar pic url and display name. We need to figure out which thing changed to display
                    // the right text.
                    JsonObject prevState = mEvent.prevContent;
                    if (prevState == null) {
                        // if there is no previous state, it has to be an invite or a join as they are the first
                        // m.room.member events for a user.
                        text = getMembershipNotice(mEvent);
                    }
                    else {
                        // check if the membership changed
                        if (hasStringValueChanged(mEvent, "membership")) {
                            text = getMembershipNotice(mEvent);
                        }
                        // check if avatar url changed
                        else if (hasStringValueChanged(mEvent, "avatar_url")) {
                            text = getAvatarChangeNotice(mEvent);
                        }
                        // check if the display name changed.
                        else if (hasStringValueChanged(mEvent, "displayname")) {
                            text = getDisplayNameChangeNotice(mEvent);
                        }
                        else {
                            // assume it is a membership notice
                            // some other members could also play with the application
                            text = getMembershipNotice(mEvent);
                        }
                    }
                }
            }
            catch (Exception e) {
                Log.e(LOG_TAG, "getTextualDisplay() "+e);
            }

            return text;
        }

        private String getMembershipNotice(Event msg) {
            String membership = msg.content.getAsJsonPrimitive("membership").getAsString();
            String userDisplayName = null;

            String prevMembership = null;

            if (null != msg.prevContent) {
                prevMembership = msg.prevContent.getAsJsonPrimitive("membership").getAsString();
            }

            // the displayname could be defined in the event
            // use it instead of the getUserDisplayName result
            // the user could have joined the before his roomMember has been created.
            if (msg.content.has("displayname")) {
                userDisplayName =  msg.content.get("displayname") == JsonNull.INSTANCE ? null : msg.content.get("displayname").getAsString();
            }

            // cannot retrieve the display name from the event
            if (null == userDisplayName) {
                // retrieve it by the room members list
                userDisplayName = getUserDisplayName(msg.userId);
            }

            if (RoomMember.MEMBERSHIP_INVITE.equals(membership)) {
                return mContext.getString(R.string.notice_room_invite, userDisplayName, getUserDisplayName(msg.stateKey));
            }
            else if (RoomMember.MEMBERSHIP_JOIN.equals(membership)) {
                return mContext.getString(R.string.notice_room_join, userDisplayName);
            }
            else if (RoomMember.MEMBERSHIP_LEAVE.equals(membership)) {
                // 2 cases here: this member may have left voluntarily or they may have been "left" by someone else ie. kicked
                if (msg.userId.equals(msg.stateKey)) {
                    return mContext.getString(R.string.notice_room_leave, userDisplayName);
                } else if (null != prevMembership) {
                    if (prevMembership.equals(RoomMember.MEMBERSHIP_JOIN) || prevMembership.equals(RoomMember.MEMBERSHIP_INVITE)) {
                        return mContext.getString(R.string.notice_room_kick, userDisplayName, getUserDisplayName(msg.stateKey));
                    } else if (prevMembership.equals(RoomMember.MEMBERSHIP_BAN)) {
                        return mContext.getString(R.string.notice_room_unban, userDisplayName, getUserDisplayName(msg.stateKey));
                    }
                }
            }
            else if (RoomMember.MEMBERSHIP_BAN.equals(membership)) {
                return mContext.getString(R.string.notice_room_ban, userDisplayName, getUserDisplayName(msg.stateKey));
            }
            else {
                // eh?
                Log.e(LOG_TAG, "Unknown membership: "+membership);
            }
            return null;
        }

        private String getAvatarChangeNotice(Event msg) {
            // TODO: Pictures!
            return mContext.getString(R.string.notice_avatar_url_changed, getUserDisplayName(msg.userId));
        }

        private String getDisplayNameChangeNotice(Event msg) {
            return mContext.getString(R.string.notice_display_name_changed,
                    getUserDisplayName(msg.userId),
                    msg.content.getAsJsonPrimitive("displayname").getAsString()
            );
        }

        private boolean hasStringValueChanged(Event msg, String key) {
            JsonObject prevContent = msg.prevContent;
            if (prevContent.has(key) && msg.content.has(key)) {
                String old = prevContent.get(key) == JsonNull.INSTANCE ? null : prevContent.get(key).getAsString();
                String current = msg.content.get(key) == JsonNull.INSTANCE ? null : msg.content.get(key).getAsString();
                if (old == null && current == null) {
                    return false;
                }
                else if (old != null) {
                    return !old.equals(current);
                }
                else {
                    return !current.equals(old);
                }
            }
            else if (!prevContent.has(key) && !msg.content.has(key)) {
                return false; // this key isn't in either prev or current
            }
            else {
                return true; // this key is in one but not the other.
            }
        }
    }

    /**
     * Clear the medias caches.
     * @param context The application context to use.
     */
    public static void clearMediasCache(Activity context) {
        String[] filesList = context.fileList();

        for(String file : filesList) {
            try {
                context.deleteFile(file);
            } catch (Exception e) {

            }
        }
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
     * Save a bitmap to the local cache
     * it could be used for unsent media to allow them to be resent.
     * @param bitmap the bitmap to save
     * @param defaultFileName the filename is provided, if null, a filename will be generated
     * @return the media cache URL
     */
    public static String saveBitmap(Bitmap bitmap, Context context, String defaultFileName) {
        String filename = "file" + System.currentTimeMillis();
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
     * @return the media cache URL
     */
    public static String saveMedia(InputStream stream, Context context, String defaultFileName) {
        String filename = (defaultFileName == null) ? ("file" + System.currentTimeMillis()) : defaultFileName;
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

    // return a bitmap from the cache
    // null if it does not exist
    public static Bitmap bitmapForUrl(String url, Context context)  {
        return BitmapWorkerTask.bitmapForURL(url,context);
    }

    // wrapper to loadBitmap
    public static String loadBitmap(ImageView imageView, String url) {
        return loadBitmap(imageView, url, -1, -1, true);
    }
    public static String loadThumbnailBitmap(ImageView imageView, String url, int width, int height) {
        return loadBitmap(imageView, url, width, height, true);
    }

    public static int progressValueForDownloadId(String downloadId) {
        BitmapWorkerTask currentTask = BitmapWorkerTask.bitmapWorkerTaskForUrl(downloadId);

        if (null != currentTask) {
            return currentTask.getProgress();
        }
        return 0;
    }

    /**
     * Load a bitmap from an url.
     * If an imageview is provided, fill it with the downloaded image.
     * The width/height parameters are optional. If they are > 0, download a thumbnail.
     * @param imageView the imaggeView Tto fill when the image is downloaded
     * @param url the image url
     * @param width the expected image width
     * @param height the expected image height
     * @param download download the image if it is not cached
     * @return a download identifier if the image is not cached
     */
    public static String loadBitmap(ImageView imageView, String url, int width, int height, boolean download) {
        if (null == url) {
            return null;
        }

        String downloadableUrl;

        // if the url is not a local file
        if (!url.startsWith("file:")) {
            ContentManager contentManager = Matrix.getInstance(imageView.getContext()).getDefaultSession().getContentManager();

            if ((width > 0) && (height > 0)) {
                downloadableUrl = contentManager.getDownloadableThumbnailUrl(url, width, height, ContentManager.METHOD_SCALE);
            } else {
                downloadableUrl = contentManager.getDownloadableUrl(url);
            }
        } else {
            downloadableUrl = url;
        }

        imageView.setTag(downloadableUrl);

        // check if the bitmap is already cached
        Bitmap bitmap = BitmapWorkerTask.bitmapForURL(downloadableUrl, imageView.getContext().getApplicationContext());

        if (null != bitmap) {
            // display it
            imageView.setImageBitmap(bitmap);
            downloadableUrl = null;
        } else if (download) {
            BitmapWorkerTask currentTask = BitmapWorkerTask.bitmapWorkerTaskForUrl(downloadableUrl);

            if (null != currentTask) {
                currentTask.addImageView(imageView);
            } else {
                // download it in background
                BitmapWorkerTask task = new BitmapWorkerTask(imageView, downloadableUrl);
                task.execute();
            }
        }

        return downloadableUrl;
    }

    /**
     * Gets the {@link ExifInterface} value for the orientation for this local bitmap Uri.
     * @param context Application context for the content resolver.
     * @param uri The URI to find the orientation for.  Must be local.
     * @return The orientation value, which may be {@link ExifInterface#ORIENTATION_UNDEFINED}.
     */
    public static int getOrientationForBitmap(Context context, Uri uri) {
        int orientation = ExifInterface.ORIENTATION_UNDEFINED;

        if (uri == null) {
            return orientation;
        }

        if (uri.getScheme().equals("content")) {
            String [] proj={MediaStore.Images.Media.DATA};
            Cursor cursor = null;
            try {
                cursor = context.getContentResolver().query( uri, proj, null, null, null);
                if (cursor != null && cursor.getCount() > 0) {
                    cursor.moveToFirst();
                    int idxData = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                    String path = cursor.getString(idxData);
                    if (TextUtils.isEmpty(path)) {
                        Log.w(LOG_TAG, "Cannot find path in media db for uri "+uri);
                        return orientation;
                    }
                    ExifInterface exif = new ExifInterface(path);
                    return exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);
                }
            }
            catch (Exception e) {
                // eg SecurityException from com.google.android.apps.photos.content.GooglePhotosImageProvider URIs
                // eg IOException from trying to parse the returned path as a file when it is an http uri.
                Log.e(LOG_TAG, "Cannot get orientation for bitmap: "+e);
            }
            finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        else if (uri.getScheme().equals("file")) {
            try {
                ExifInterface exif = new ExifInterface(uri.getPath());
                return exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);
            }
            catch (Exception e) {
                Log.e(LOG_TAG, "Cannot get EXIF for file uri "+uri+" because "+e);
            }
        }

        return orientation;
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

        private final ArrayList<WeakReference<ImageView>> mImageViewReferences;
        private String mUrl;
        private int mProgress = 0;

        public static BitmapWorkerTask bitmapWorkerTaskForUrl(String url) {
            if ((url != null) &&  mPendingDownloadByUrl.containsKey(url)) {
                return mPendingDownloadByUrl.get(url);
            } else {
                return null;
            }
        }

        public void addImageView(ImageView imageView) {
            mImageViewReferences.add(new WeakReference<ImageView>(imageView));
        }

        public BitmapWorkerTask(ImageView imageView, String url) {
            mImageViewReferences = new ArrayList<WeakReference<ImageView>>();
            addImageView(imageView);
            mUrl = url;
            mPendingDownloadByUrl.put(url, this);
        }

        public int getProgress() {
            return mProgress;
        }

        public static Bitmap bitmapForURL(String url, Context context) {
            Bitmap bitmap = null;

            // sanity check
            if (null != url) {
                synchronized (sMemoryCache) {
                    bitmap = sMemoryCache.get(url);
                }

                // check if the image has not been saved in file system
                if ((null == bitmap) && (null != context)) {
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
                        filename = "file" + url.hashCode();;
                    }

                    try {
                        FileInputStream fis;

                        if (filename.startsWith(File.separator)) {
                            fis = new FileInputStream (new File(filename));
                        } else {
                            fis = context.getApplicationContext().openFileInput(filename);
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

                // retrieve the Application context
                ImageView imageView = mImageViewReferences.get(0).get();
                Context applicationContext = null;

                if ((null != imageView.getContext()) && (null != imageView.getContext().getApplicationContext())) {
                    applicationContext = imageView.getContext().getApplicationContext();
                }

                long filelen = -1;

                try {
                    URLConnection connection = url.openConnection();
                    filelen = connection.getContentLength();
                    stream = connection.getInputStream();
                } catch (FileNotFoundException e) {
                    Log.d(LOG_TAG, "BitmapWorkerTask " + mUrl + " does not exist");

                    if(null != applicationContext) {
                        bitmap = BitmapFactory.decodeResource(applicationContext.getResources(), R.drawable.ic_menu_gallery);
                    }
                }

                if  (null != applicationContext) {
                    String filename  = "file" + url.hashCode();
                    FileOutputStream fos = applicationContext.openFileOutput(filename, Context.MODE_PRIVATE);

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

                        } catch (Exception e) {
                        }

                        close(stream);
                    }

                    fos.flush();
                    fos.close();

                    Log.d(LOG_TAG, "download is done (" + mUrl + ")");

                    // get the bitmap from the filesytem
                    if (null == bitmap) {
                        bitmap = BitmapWorkerTask.bitmapForURL(key, applicationContext);
                    }
                } else if (null != stream) {
                    BitmapFactory.Options bitmapOptions = new BitmapFactory.Options();
                    bitmapOptions.inDither = true;
                    bitmapOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;
                    bitmap = BitmapFactory.decodeStream(stream, null, bitmapOptions);
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

        private void sendProgress(int progress) {
            for(WeakReference<ImageView> weakRef : mImageViewReferences) {
                final ImageView imageView = weakRef.get();

                // check if the imageview still expect to have this content
                if ((null != imageView) && mUrl.equals(imageView.getTag())) {
                    Object parent = imageView.getParent();

                    if (parent instanceof View) {
                        View parentView = (View) (imageView.getParent());

                        PieFractionView pieFractionView = (PieFractionView) parentView.findViewById(R.id.download_content_piechart);

                        if (null != pieFractionView) {
                            pieFractionView.setFraction(progress);

                            if (progress >= 100) {
                                LinearLayout progressLayout = (LinearLayout) parentView.findViewById(R.id.download_content_layout);
                                progressLayout.setVisibility(View.GONE);
                            }
                        }
                    }
                }
            }

            /*Intent intent = new Intent();
            intent.setAction(BROADCAST_DOWNLOAD_PROGRESS);
            intent.putExtra(DOWNLOAD_PROGRESS_VALUE, progress);
            intent.putExtra(DOWNLOAD_PROGRESS_IDENTIFIER, mUrl);

            LocalBroadcastManager.getInstance( mImageViewReferences.get(0).get().getContext()).sendBroadcast(intent);*/
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {
            super.onProgressUpdate(progress);
            sendProgress(progress[0]);
        }

        // Once complete, see if ImageView is still around and set bitmap.
        @Override
        protected void onPostExecute(Bitmap bitmap) {
            // update the imageView image
            if (bitmap != null) {
                for(WeakReference<ImageView> weakRef : mImageViewReferences) {
                    final ImageView imageView = weakRef.get();

                    if (imageView != null && mUrl.equals(imageView.getTag())) {
                        imageView.setImageBitmap(bitmap);
                        sendProgress(100);
                    }
                }
            }

            mPendingDownloadByUrl.remove(mUrl);
        }

        /**
         * Get the width/height of the image at the end of this InputStream without dumping it all
         * in memory or disk. Note that this action processes the stream entirely, so a new stream
         * will be required (or this stream has to be rewindable) if the image is to be loaded from
         * it.
         * @param stream The input stream representing an image.
         * @return The bitmap info
         * @throws IOException If there isn't a decodable width and height for this image.
         */
        private BitmapFactory.Options decodeBitmapDimensions(InputStream stream) throws IOException {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(stream, null, o);
            if (o.outHeight == -1 || o.outWidth == -1) {
                // this doesn't look like an image...
                throw new IOException("Cannot resize input stream, failed to get w/h.");
            }
            return o;
        }

        /**
         * Get the inSampleSize required to decode this image to the 'right' size.
         * @param w The width of the image in px
         * @param h The height of the image in px
         * @param maxSizePx The max dimension allowed in px
         * @return The sample size to use.
         */
        private int getSampleSize(int w, int h, int maxSizePx) {
            int highestDimensionSize = (h > w) ? h : w;
            double ratio = (highestDimensionSize > maxSizePx) ? (highestDimensionSize / maxSizePx) : 1.0;
            int sampleSize = Integer.highestOneBit((int)Math.floor(ratio));
            if (sampleSize == 0) {
                sampleSize = 1;
            }
            return sampleSize;
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
