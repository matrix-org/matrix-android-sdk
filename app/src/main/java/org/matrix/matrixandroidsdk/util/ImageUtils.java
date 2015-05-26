/* 
 * Copyright 2014 OpenMarket Ltd
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
package org.matrix.matrixandroidsdk.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class ImageUtils {

    private static final String LOG_TAG = "ImageUtils";

    public static  BitmapFactory.Options decodeBitmapDimensions(InputStream stream) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(stream,null,o);
        if (o.outHeight == -1 || o.outWidth == -1) {
            // this doesn't look like an image...
            Log.e(LOG_TAG, "Cannot resize input stream, failed to get w/h.");
            return null;
        }
        return o;
    }

    public static int getSampleSize(int w, int h, int maxSize) {
        int highestDimensionSize = (h > w) ? h : w;
        double ratio = (highestDimensionSize > maxSize) ? (highestDimensionSize / maxSize) : 1.0;
        int sampleSize = Integer.highestOneBit((int)Math.floor(ratio));
        if (sampleSize == 0) {
            sampleSize = 1;
        }
        return sampleSize;
    }

    /**
     * Resize an image from its stream.
     * @param fullImageStream the image stream
     * @param maxSize the square side to draw the image in. -1 to ignore.
     * @param aSampleSize the image dimension divider.
     * @param quality the image quality (0 -> 100)
     * @return a stream of the resized imaged
     * @throws IOException
     */
    public static InputStream resizeImage(InputStream fullImageStream, int maxSize, int aSampleSize, int quality) throws IOException {
        /*
         * This is all a bit of a mess because android doesn't ship with sensible bitmap streaming libraries.
         *
         * General structure here is: (N = size of file, M = decompressed size)
         * - Copy inputstream to outstream (Usage: 2N)
         * - Release inputstream (Usage: N)
         * - Copy outstream to instream (Usage: 2N) --- This is done to make sure we can .reset() the stream else we would potentially
         *                                              have to re-download the file once we knew the dimensions of the image (!!!)
         * - Release outstream (Usage: N)
         * - Decode image dimensions, if the size is good, just return instream, else:
         * - Decode the full image with the new sample size (Usage: N + M)
         * - Release instream (Usage: M)
         * - Bitmap compress to JPEG output stream (Usage: N + M)
         * - Release bitmap (Usage: N)
         * - Return input stream of output stream (Usage: N)
         * Usages assume immediate GC, which is no guarantee. If it didn't, the total usage is 5N + M. In an extreme scenario
         * of a full 8 MP image roughly 1.85MB file (3264x2448), this equates to roughly 25 MB of memory. On average, it will
         * maybe not immediately release the streams but will probably in the future, so maybe 3N which is ~5.55MB - either
         * way this isn't cool.
         */

        ByteArrayOutputStream outstream = new ByteArrayOutputStream();

        // copy the bytes we just got to the byte array output stream so we can resize....
        byte[] buffer = new byte[2048];
        int l;
        while ((l = fullImageStream.read(buffer)) != -1) {
            outstream.write(buffer, 0, l);
        }

        // we're done with the input stream now so get rid of it (bearing in mind this could be several MB..)
        fullImageStream.close();
        fullImageStream = null;

        // get the width/height of the image without decoding ALL THE THINGS (though this still makes a copy of the compressed image :/)
        ByteArrayInputStream bais = new ByteArrayInputStream(outstream.toByteArray());

        // allow it to GC..
        outstream.close();
        outstream = null;

        BitmapFactory.Options o = decodeBitmapDimensions(bais);
        if (o == null) {
            return null;
        }
        int w = o.outWidth;
        int h = o.outHeight;
        bais.reset(); // yay no need to re-read the stream (which is why we dumped to another stream)
        int sampleSize = (maxSize == -1) ? aSampleSize : getSampleSize(w,h, maxSize);

        if (sampleSize == 1) {
            // small optimisation
            return bais;
        }
        else {
            // yucky, we have to decompress the entire (albeit subsampled) bitmap into memory then dump it back into a stream
            o = new BitmapFactory.Options();
            o.inSampleSize = sampleSize;
            Bitmap bitmap = BitmapFactory.decodeStream(bais, null, o);
            if (bitmap == null) {
                return null;
            }

            bais.close();
            bais = null;

            // recopy it back into an input stream :/
            outstream = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outstream);

            // cleanup
            bitmap.recycle();
            bitmap = null;

            return new ByteArrayInputStream(outstream.toByteArray());
        }
    }
}
