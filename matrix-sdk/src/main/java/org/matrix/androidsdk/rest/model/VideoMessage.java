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

import android.net.Uri;
import org.matrix.androidsdk.util.Log;

import java.io.File;
public class VideoMessage extends Message {
    private static final String LOG_TAG = "VideoMessage";

    public VideoInfo info;
    public String url;

    // encrypted medias
    // url and thumbnailUrl are replaced by their dedicated file
    public EncryptedFileInfo file;

    public VideoMessage() {
        msgtype = MSGTYPE_VIDEO;
    }

    /**
     * @return the media url
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
        if ((null != info) && (null != info.thumbnail_url)) {
            return info.thumbnail_url;
        } else if ((null != info) &&  (null != info.thumbnail_file)) {
            return info.thumbnail_file.url;
        }

        return null;
    }

    /**
     * Make a deep copy of this VideoMessage.
     * @return the copy
     */
    public VideoMessage deepCopy() {
        VideoMessage copy = new VideoMessage();
        copy.url = url;
        copy.msgtype = msgtype;
        copy.body = body;

        if (null != info) {
            copy.info = info.deepCopy();
        }

        if (null != file) {
            copy.file = file.deepCopy();
        }

        return copy;
    }

    public boolean isThumbnailLocalContent() {
        return (null != info) && (null != info.thumbnail_url) && (info.thumbnail_url.startsWith("file://"));
    }

    public boolean isLocalContent() {
        return (null != url) && (url.startsWith("file://"));
    }

    /**
     * @return The video mimetype. null is not defined.
     */
    public String getVideoMimeType() {
        if (null != info) {
            return info.mimetype;
        } else {
            return null;
        }
    }

    /**
     * Checks if the media Urls are still valid.
     * The media Urls could define a file path.
     * They could have been deleted after a media cache cleaning.
     */
    public void checkMediaUrls() {
        if ((null != info) && (info.thumbnail_url != null) && info.thumbnail_url.startsWith("file://")) {
            try {
                File file = new File(Uri.parse(info.thumbnail_url).getPath());

                if (!file.exists()) {
                    info.thumbnail_url = null;
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "## checkMediaUrls() failed" + e.getMessage());
            }
        }

        if ((url != null) && url.startsWith("file://")) {
            try {
                File file = new File(Uri.parse(url).getPath());

                if (!file.exists()) {
                    url = null;
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "## checkMediaUrls() failed" + e.getMessage());
            }
        }
    }
}
