/*
 * Copyright 2017 Vector Creations Ltd
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
package org.matrix.androidsdk.rest.model.message;

import android.net.Uri;
import android.support.annotation.Nullable;

import org.matrix.androidsdk.crypto.MXEncryptedAttachments;
import org.matrix.androidsdk.util.Log;

import java.io.File;

public class MediaMessage extends Message {
    private static final String LOG_TAG = MediaMessage.class.getSimpleName();

    /**
     * @return the media URL
     */
    @Nullable
    public String getUrl() {
        return null;
    }

    public void setUrl(MXEncryptedAttachments.EncryptionResult encryptionResult, String url) {
    }

    /**
     * @return the thumbnail url
     */
    @Nullable
    public String getThumbnailUrl() {
        return null;
    }

    public void setThumbnailUrl(MXEncryptedAttachments.EncryptionResult encryptionResult, String url) {
    }

    /**
     * @return true if the thumbnail is a file url
     */
    public boolean isThumbnailLocalContent() {
        String thumbUrl = getThumbnailUrl();
        return (null != thumbUrl) && thumbUrl.startsWith(FILE_SCHEME);
    }

    /**
     * @return true if the media url is a file one.
     */
    public boolean isLocalContent() {
        String url = getUrl();
        return (null != url) && url.startsWith(FILE_SCHEME);
    }

    /**
     * @return The image mimetype. null is not defined.
     */
    @Nullable
    public String getMimeType() {
        return null;
    }

    /**
     * Checks if the media Urls are still valid.
     * The media Urls could define a file path.
     * They could have been deleted after a media cache cleaning.
     */
    public void checkMediaUrls() {
        String thumbUrl = getThumbnailUrl();

        if ((null != thumbUrl) && thumbUrl.startsWith(FILE_SCHEME)) {
            try {
                File file = new File(Uri.parse(thumbUrl).getPath());

                if (!file.exists()) {
                    setThumbnailUrl(null, null);
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "## checkMediaUrls() failed" + e.getMessage(), e);
            }
        }

        String url = getUrl();

        if ((url != null) && url.startsWith(FILE_SCHEME)) {
            try {
                File file = new File(Uri.parse(url).getPath());

                if (!file.exists()) {
                    setUrl(null, null);
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "## checkMediaUrls() failed" + e.getMessage(), e);
            }
        }
    }
}