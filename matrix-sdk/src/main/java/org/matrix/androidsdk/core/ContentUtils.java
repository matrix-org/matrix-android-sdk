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
package org.matrix.androidsdk.core;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.matrix.androidsdk.rest.model.message.ImageInfo;

import java.io.File;

/**
 * Static content utility methods.
 */
public class ContentUtils {
    private static final String LOG_TAG = FileContentUtils.class.getSimpleName();

    /**
     * Build an ImageInfo object based on the image at the given path.
     *
     * @param filePath the path to the image in storage
     * @return the image info
     */
    public static ImageInfo getImageInfoFromFile(String filePath) {
        ImageInfo imageInfo = new ImageInfo();
        try {
            Bitmap imageBitmap = BitmapFactory.decodeFile(filePath);
            imageInfo.w = imageBitmap.getWidth();
            imageInfo.h = imageBitmap.getHeight();

            File file = new File(filePath);
            imageInfo.size = file.length();

            imageInfo.mimetype = FileContentUtils.getMimeType(filePath);
        } catch (OutOfMemoryError oom) {
            Log.e(LOG_TAG, "## getImageInfoFromFile() : oom", oom);
        }

        return imageInfo;
    }
}
