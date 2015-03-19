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

import java.io.File;

public class ImageMessage extends Message {
    public ImageInfo info;
    public ImageInfo thumbnailInfo;
    public String url;
    public String thumbnailUrl;

    public ImageMessage() {
        msgtype = MSGTYPE_IMAGE;
    }

    public boolean isLocalContent() {
        return (null != url) && (url.startsWith("file://"));
    }

    public String getMimeType() {
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
        if ((thumbnailUrl != null) && thumbnailUrl.startsWith("file://")) {
            try {
                File file = new File(Uri.parse(thumbnailUrl).getPath());

                if (!file.exists()) {
                    thumbnailUrl = null;
                }
            } catch (Exception e) {

            }
        }

        if ((url != null) && url.startsWith("file://")) {
            try {
                File file = new File(Uri.parse(url).getPath());

                if (!file.exists()) {
                    url = null;
                }
            } catch (Exception e) {

            }
        }
    }
}
