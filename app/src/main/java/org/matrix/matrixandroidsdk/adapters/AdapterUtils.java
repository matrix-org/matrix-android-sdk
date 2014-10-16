package org.matrix.matrixandroidsdk.adapters;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.support.v4.util.LruCache;
import android.util.Log;
import android.widget.ImageView;

import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.URL;

/**
 * Contains useful functions for adapters.
 */
public class AdapterUtils {
    private static final String LOG_TAG = "AdapterUtils";

    public static void loadBitmap(ImageView imageView, String url) {
        imageView.setTag(url);
        BitmapWorkerTask task = new BitmapWorkerTask(imageView, url);
        task.execute();
    }

    static class BitmapWorkerTask extends AsyncTask<Integer, Void, Bitmap> {

        private static final int MEMORY_CACHE_MB = 6;

        private static LruCache<String, Bitmap> sMemoryCache = new LruCache<String, Bitmap>(1024 * 1024 * MEMORY_CACHE_MB){
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getRowBytes() * bitmap.getHeight(); // size in bytes
            }
        };

        private final WeakReference<ImageView> mImageViewReference;
        private String mUrl;
        private int mMaxPx;

        public BitmapWorkerTask(ImageView imageView, String url) {
            this(imageView, url, 320);
        }

        public BitmapWorkerTask(ImageView imageView, String url, int maxPx) {
            mImageViewReference = new WeakReference<ImageView>(imageView);
            mUrl = url;
            mMaxPx = maxPx;
        }

        // Decode image in background.
        @Override
        protected Bitmap doInBackground(Integer... params) {
            try {
                // check the in-memory cache
                String key = mUrl +"-" + mMaxPx;
                Bitmap bm = sMemoryCache.get(key);
                if (bm != null) {
                    return bm;
                }


                URL url = new URL(mUrl);
                Log.d(LOG_TAG, "BitmapWorkerTask open >>>>> " + mUrl);
                InputStream stream = url.openConnection().getInputStream();
                BitmapFactory.Options o = decodeBitmapDimensions(stream);
                int sampleSize = getSampleSize(o.outWidth, o.outHeight, mMaxPx);
                close(stream); // checking the sample size processed the stream so it's useless now.
                stream = url.openConnection().getInputStream();
                // decode within the limits specified.
                BitmapFactory.Options bitmapOptions = new BitmapFactory.Options();
                bitmapOptions.inSampleSize = sampleSize;
                bitmapOptions.inDither = true;
                bitmapOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;
                Bitmap bitmap = BitmapFactory.decodeStream(stream, null, bitmapOptions);
                close(stream);
                if (bitmap != null) {
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
