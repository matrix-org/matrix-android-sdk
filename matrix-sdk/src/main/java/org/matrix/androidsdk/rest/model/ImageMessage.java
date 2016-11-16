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
package org.matrix.androidsdk.rest.model;

import android.media.ExifInterface;
import android.net.Uri;
import android.util.Log;

import java.io.File;

public class ImageMessage extends Message {
    private static final String LOG_TAG = "ImageMessage";

    public ImageInfo info;
    public ImageInfo thumbnailInfo;
    public String url;
    public String thumbnailUrl;

    // encrypted medias
    // url and thumbnailUrl are replaced by their dedicated file
    public EncryptedFileInfo file;
    public EncryptedFileInfo thumbnail_file;

    public ImageMessage() {
        msgtype = MSGTYPE_IMAGE;
    }

    /**
     * Make a deep copy of this ImageMessage.
     * @return the copy
     */
    public ImageMessage deepCopy() {
        ImageMessage copy = new ImageMessage();
        copy.msgtype = msgtype;
        copy.body = body;
        copy.url = url;
        copy.thumbnailUrl = thumbnailUrl;

        if (null != info) {
            copy.info = info.deepCopy();
        }

        if (null != thumbnailInfo) {
            copy.thumbnailInfo = thumbnailInfo.deepCopy();
        }

        if (null != file) {
            copy.file = file.deepCopy();
        }

        if (null != thumbnail_file) {
            copy.thumbnail_file = thumbnail_file.deepCopy();
        }

        return copy;
    }

    /**
     * @return the media URL
     */
    public String getUrl() {
        if (null != url) {
            return url;
        } else if (null != file) {
            return file.url;
        } else {
            return null;
        }
    }

    /**
     * @return the thumbnail url
     */
    public String getThumbnailUrl() {
        if (null != thumbnailUrl) {
            return thumbnailUrl;
        } else if (null != thumbnail_file) {
            return thumbnail_file.url;
        } else {
            return null;
        }
    }

    public boolean isLocalContent() {
        return (null != url) && (url.startsWith("file://"));
    }

    /**
     * @return The image mimetype. null is not defined.
     */
    public String getMimeType() {
        if (null != file) {
            return file.mimetype;
        } else if (null != info) {
            return info.mimetype;
        } else {
            return null;
        }
    }

    /**
     * @return the rotation angle. Integer.MAX_VALUE if not defined.
     */
    public int getRotation() {
        if ((null != info) && (null != info.rotation)) {
            return info.rotation;
        } else {
            return Integer.MAX_VALUE;
        }
    }

    /**
     * @return the image orientation. ExifInterface.ORIENTATION_UNDEFINED if not defined.
     */
    public int getOrientation() {
        if ((null != info) && (null != info.orientation)) {
            return info.orientation;
        } else {
            return ExifInterface.ORIENTATION_UNDEFINED;
        }
    }

    /**
     * Checks if the media Urls are still valid.
     * The media Urls could define a file path.
     * They could have been deleted after a media cache cleaning.
     */
    public void checkMediaUrls() {
        if ((thumbnailUrl != null) && thumbnailUrl.startsWith("file://")) {
            try {
                File file = new File(Uri.parse(thumbnailUrl).getPath());

                if (!file.exists()) {
                    thumbnailUrl = null;
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "## checkMediaUrls() failed " + e.getMessage());
            }
        }

        if ((url != null) && url.startsWith("file://")) {
            try {
                File file = new File(Uri.parse(url).getPath());

                if (!file.exists()) {
                    url = null;
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "## checkMediaUrls() failed " + e.getMessage());
            }
        }
    }
}
