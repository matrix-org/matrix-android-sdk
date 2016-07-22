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
import android.text.TextUtils;
import android.webkit.MimeTypeMap;

import java.io.File;

public class FileMessage extends Message {
    public FileInfo info;
    public String url;

    public FileMessage() {
        msgtype = MSGTYPE_FILE;
    }

    /**
     * Make a deep copy of this VideoMessage.
     * @return the copy
     */
    public FileMessage deepCopy() {
        FileMessage copy = new FileMessage();
        copy.msgtype = msgtype;
        copy.body = body;
        copy.url = url;

        if (null != info) {
            copy.info = info.deepCopy();
        }

        return copy;
    }

    public boolean isLocalContent() {
        return (null != url) && (url.startsWith("file://"));
    }

    public String getMimeType() {
        if (null != info) {
            // the mimetype was not provided
            if (TextUtils.isEmpty(info.mimetype) && (body.indexOf('.') > 0)) {
                // the body should contain the filename so try to extract the mimetype from the extension
                String extension =  body.substring(body.lastIndexOf('.') + 1, body.length());

                try {
                    info.mimetype = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());
                } catch (Exception e) {

                }
            }

            if (TextUtils.isEmpty(info.mimetype)) {
                info.mimetype = "application/octet-stream";
            }

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
