package org.matrix.matrixandroidsdk.adapters;

import android.app.Application;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.v4.util.LruCache;
import android.text.Html;
import android.util.Log;
import android.widget.ImageView;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.Message;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.util.ContentManager;
import org.matrix.matrixandroidsdk.Matrix;
import org.matrix.matrixandroidsdk.R;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;

/**
 * Contains useful functions for adapters.
 */
public class AdapterUtils {
    private static final String LOG_TAG = "AdapterUtils";

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
            RoomMember roomMember = mRoomState.getMember(userId);
            return (roomMember != null) ? roomMember.getName() : userId;
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
     * @return the media cache URL
     */
    public static String saveBitmap(Bitmap bitmap, Context context) {
        String filename = "file" + System.currentTimeMillis();
        String cacheURL = null;

        try {
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
     * @return the media cache URL
     */
    public static String saveMedia(InputStream stream, Context context) {
        String filename = "file" + System.currentTimeMillis();
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
    public static Bitmap bitmapForUrl(String url, Context context) {
        return  BitmapWorkerTask.bitmapForURL(url,context);
    }

    // Bitmap loading and storage
    public static void loadBitmap(ImageView imageView, String url) {
        ContentManager contentManager = Matrix.getInstance(imageView.getContext()).getDefaultSession().getContentManager();
        String downloadableUrl = contentManager.getDownloadableUrl(url);
        imageView.setTag(downloadableUrl);

        // check if the bitmap is already cached
        Bitmap bitmap = BitmapWorkerTask.bitmapForURL(downloadableUrl, imageView.getContext().getApplicationContext());

        if (null != bitmap) {
            // display it
            imageView.setImageBitmap(bitmap);
        } else {
            // download it in background
            BitmapWorkerTask task = new BitmapWorkerTask(imageView, downloadableUrl);
            task.execute();
        }
    }

    public static void loadThumbnailBitmap(ImageView imageView, String url, int width, int height) {
        ContentManager contentManager = Matrix.getInstance(imageView.getContext()).getDefaultSession().getContentManager();
        String downloadableUrl = contentManager.getDownloadableThumbnailUrl(url, width, height, ContentManager.METHOD_SCALE);
        imageView.setTag(downloadableUrl);

        // check if the bitmap is already cached
        Bitmap bitmap = BitmapWorkerTask.bitmapForURL(downloadableUrl, imageView.getContext().getApplicationContext());

        if (null != bitmap) {
            // display it
            imageView.setImageBitmap(bitmap);
        } else {
            // download it in background
            BitmapWorkerTask task = new BitmapWorkerTask(imageView, downloadableUrl);
            task.execute(width, height);
        }
    }

    static class BitmapWorkerTask extends AsyncTask<Integer, Void, Bitmap> {

        private static final int MEMORY_CACHE_MB = 16;

        private static LruCache<String, Bitmap> sMemoryCache = new LruCache<String, Bitmap>(1024 * 1024 * MEMORY_CACHE_MB){
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getRowBytes() * bitmap.getHeight(); // size in bytes
            }
        };

        private final WeakReference<ImageView> mImageViewReference;
        private String mUrl;

        public BitmapWorkerTask(ImageView imageView, String url) {
            mImageViewReference = new WeakReference<ImageView>(imageView);
            mUrl = url;
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
                            fis = context.openFileInput(filename);
                        }

                        if (null != fis) {
                            BitmapFactory.Options options = new BitmapFactory.Options();
                            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                            bitmap = BitmapFactory.decodeStream(fis, null, options);

                            if (null != bitmap) {
                                synchronized (sMemoryCache) {
                                    sMemoryCache.put(url, bitmap);
                                }
                            }

                            fis.close();
                        }
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
                Bitmap bm = BitmapWorkerTask.bitmapForURL(key, mImageViewReference.get().getContext().getApplicationContext());

                if (bm != null) {
                    return bm;
                }

                URL url = new URL(mUrl);
                Log.d(LOG_TAG, "BitmapWorkerTask open >>>>> " + mUrl);
                InputStream stream = url.openConnection().getInputStream();
                Bitmap bitmap;

                ImageView imageView = mImageViewReference.get();

                if ((null != imageView) && (null != imageView.getContext()) && (null != imageView.getContext().getApplicationContext())) {
                    String filename  = "file" + url.hashCode();
                    Context context = imageView.getContext().getApplicationContext();
                    FileOutputStream fos = context.openFileOutput(filename, Context.MODE_PRIVATE);

                    try{
                        byte[] buf = new byte[1024*32];
                        int len;
                        while((len = stream.read(buf)) != -1) {
                            fos.write(buf, 0, len);
                        }
                    } catch (Exception e) {
                    }

                    fos.flush();
                    fos.close();
                    close(stream);

                    // get the bitmap from the filesytem
                    bitmap = BitmapWorkerTask.bitmapForURL(key, context);
                } else {
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

        // Once complete, see if ImageView is still around and set bitmap.
        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (mImageViewReference != null && bitmap != null) {
                final ImageView imageView = mImageViewReference.get();
                if (imageView != null && mUrl.equals(imageView.getTag())) {
                    imageView.setImageBitmap(bitmap);
                }
            }
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
