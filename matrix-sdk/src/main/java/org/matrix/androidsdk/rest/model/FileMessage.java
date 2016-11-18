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

import android.content.ClipDescription;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.MimeTypeMap;

import java.io.File;

public class FileMessage extends Message {
    private static final String LOG_TAG = "FileMessage";

    public FileInfo info;
    public String url;

    // encrypted medias
    // url and thumbnailUrl are replaced by their dedicated file
    public EncryptedFileInfo file;

    public FileMessage() {
        msgtype = MSGTYPE_FILE;
    }

    /**
     * @return the file url
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

        if (null != file) {
            copy.file = file.deepCopy();
        }

        return copy;
    }

    public boolean isLocalContent() {
        return (null != url) && (url.startsWith("file://"));
    }

    public String getMimeType() {
        if (null != info) {
            // the mimetype was not provided or it's invalid
            // some android application set the mimetype to text/uri-list
            // it should be fixed on application side but we need to patch it on client side.
            if ((TextUtils.isEmpty(info.mimetype) || ClipDescription.MIMETYPE_TEXT_URILIST.equals(info.mimetype)) && (body.indexOf('.') > 0)) {
                // the body should contain the filename so try to extract the mimetype from the extension
                String extension =  body.substring(body.lastIndexOf('.') + 1, body.length());

                try {
                    info.mimetype = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());
                } catch (Exception e) {
                    Log.e(LOG_TAG, "## getMimeType() : getMimeTypeFromExtensionfailed " + e.getMessage());
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
                Log.e(LOG_TAG, "## checkMediaUrls() failed " + e.getMessage());
            }
        }
    }
}
