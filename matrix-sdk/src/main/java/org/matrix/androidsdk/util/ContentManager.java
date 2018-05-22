/* 
 * Copyright 2016 OpenMarket Ltd
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

package org.matrix.androidsdk.util;

import android.support.annotation.Nullable;

import org.matrix.androidsdk.HomeServerConnectionConfig;

/**
 * Class for accessing content from the current session.
 */
public class ContentManager {
    private static final String LOG_TAG = ContentManager.class.getSimpleName();

    public static final String MATRIX_CONTENT_URI_SCHEME = "mxc://";

    public static final String METHOD_CROP = "crop";
    public static final String METHOD_SCALE = "scale";

    public static final String URI_PREFIX_CONTENT_API = "/_matrix/media/v1/";

    // HS config
    private final HomeServerConnectionConfig mHsConfig;

    // the unsent events Manager
    private final UnsentEventsManager mUnsentEventsManager;

    /**
     * Default constructor.
     *
     * @param hsConfig            the HomeserverConnectionConfig to use
     * @param unsentEventsManager the unsent events manager
     */
    public ContentManager(HomeServerConnectionConfig hsConfig, UnsentEventsManager unsentEventsManager) {
        mHsConfig = hsConfig;
        mUnsentEventsManager = unsentEventsManager;
    }

    /**
     * @return the hs config.
     */
    public HomeServerConnectionConfig getHsConfig() {
        return mHsConfig;
    }

    /**
     * @return the unsent events manager
     */
    public UnsentEventsManager getUnsentEventsManager() {
        return mUnsentEventsManager;
    }

    /**
     * Compute the identicon URL for an userId.
     *
     * @param userId the user id.
     * @return the url
     */
    public static String getIdenticonURL(String userId) {
        // sanity check
        if (null != userId) {
            String urlEncodedUser = null;
            try {
                urlEncodedUser = java.net.URLEncoder.encode(userId, "UTF-8");
            } catch (Exception e) {
                Log.e(LOG_TAG, "## getIdenticonURL() : java.net.URLEncoder.encode failed " + e.getMessage());
            }

            return ContentManager.MATRIX_CONTENT_URI_SCHEME + "identicon/" + urlEncodedUser;
        }

        return null;
    }

    /**
     * Check whether an url is a valid matrix content url.
     *
     * @param contentUrl the content URL (in the form of "mxc://...").
     * @return true if contentUrl is valid.
     */
    public boolean isValidMatrixContentUrl(String contentUrl) {
        return (null != contentUrl && contentUrl.startsWith(MATRIX_CONTENT_URI_SCHEME));
    }

    /**
     * Get the actual URL for accessing the full-size image of a Matrix media content URI.
     *
     * @param contentUrl the Matrix media content URI (in the form of "mxc://...").
     * @return the URL to access the described resource, or null if the url is invalid.
     */
    @Nullable
    public String getDownloadableUrl(String contentUrl) {
        if (isValidMatrixContentUrl(contentUrl)) {
            String mediaServerAndId = contentUrl.substring(MATRIX_CONTENT_URI_SCHEME.length());
            return mHsConfig.getHomeserverUri().toString() + URI_PREFIX_CONTENT_API + "download/" + mediaServerAndId;
        } else {
            // do not allow non-mxc content URLs: we should not be making requests out to whatever http urls people send us
            return null;
        }
    }

    /**
     * Get the actual URL for accessing the thumbnail image of a given Matrix media content URI.
     *
     * @param contentUrl the Matrix media content URI (in the form of "mxc://...").
     * @param width      the desired width
     * @param height     the desired height
     * @param method     the desired scale method (METHOD_CROP or METHOD_SCALE)
     * @return the URL to access the described resource, or null if the url is invalid.
     */
    @Nullable
    public String getDownloadableThumbnailUrl(String contentUrl, int width, int height, String method) {
        if (isValidMatrixContentUrl(contentUrl)) {
            String mediaServerAndId = contentUrl.substring(MATRIX_CONTENT_URI_SCHEME.length());

            // ignore the #auto pattern
            if (mediaServerAndId.endsWith("#auto")) {
                mediaServerAndId = mediaServerAndId.substring(0, mediaServerAndId.length() - "#auto".length());
            }

            String url = mHsConfig.getHomeserverUri().toString() + URI_PREFIX_CONTENT_API;

            // identicon server has no thumbnail path
            if (mediaServerAndId.indexOf("identicon") < 0) {
                url += "thumbnail/";
            }

            url += mediaServerAndId;
            url += "?width=" + width;
            url += "&height=" + height;
            url += "&method=" + method;
            return url;
        } else {
            // do not allow non-mxc content URLs: we should not be making requests out to whatever http urls people send us
            return null;
        }
    }
}
