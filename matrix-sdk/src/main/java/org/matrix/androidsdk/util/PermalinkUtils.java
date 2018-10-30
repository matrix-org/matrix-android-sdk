/*
 * Copyright 2018 New Vector Ltd
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

import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import org.matrix.androidsdk.MXPatterns;
import org.matrix.androidsdk.rest.model.Event;

import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Useful methods to deals with Matrix permalink
 */
public class PermalinkUtils {
    private static final String LOG_TAG = PermalinkUtils.class.getSimpleName();

    private static final String MATRIX_TO_URL_BASE = "https://matrix.to/#/";

    // index of each items in the map when parsing a universal link
    public static final String ULINK_ROOM_ID_OR_ALIAS_KEY = "ULINK_ROOM_ID_OR_ALIAS_KEY";
    public static final String ULINK_MATRIX_USER_ID_KEY = "ULINK_MATRIX_USER_ID_KEY";
    public static final String ULINK_GROUP_ID_KEY = "ULINK_GROUP_ID_KEY";
    public static final String ULINK_EVENT_ID_KEY = "ULINK_EVENT_ID_KEY";

    /**
     * Creates a permalink for an event.
     * Ex: "https://matrix.to/#/!nbzmcXAqpxBXjAdgoX:matrix.org/$1531497316352799BevdV:matrix.org"
     *
     * @param event the event
     * @return the permalink, or null in case of error
     */
    @Nullable
    public static String createPermalink(Event event) {
        if (event == null) {
            return null;
        }

        return createPermalink(event.roomId, event.eventId);
    }

    /**
     * Creates a permalink for an id (can be a user Id, Room Id, etc.).
     * Ex: "https://matrix.to/#/@benoit:matrix.org"
     *
     * @param id the id
     * @return the permalink, or null in case of error
     */
    @Nullable
    public static String createPermalink(String id) {
        if (TextUtils.isEmpty(id)) {
            return null;
        }

        return MATRIX_TO_URL_BASE + escape(id);
    }

    /**
     * Creates a permalink for an event. If you have an event you can use {@link #createPermalink(Event)}
     * Ex: "https://matrix.to/#/!nbzmcXAqpxBXjAdgoX:matrix.org/$1531497316352799BevdV:matrix.org"
     *
     * @param roomId  the id of the room
     * @param eventId the id of the event
     * @return the permalink
     */
    @NonNull
    public static String createPermalink(@NonNull String roomId, @NonNull String eventId) {
        return MATRIX_TO_URL_BASE + escape(roomId) + "/" + escape(eventId);
    }

    /**
     * Extract the linked id from the universal link
     *
     * @param url the universal link, Ex: "https://matrix.to/#/@benoit:matrix.org"
     * @return the id from the url, ex: "@benoit:matrix.org", or null if the url is not a permalink
     */
    public static String getLinkedId(@Nullable String url) {
        boolean isSupported = url != null && url.startsWith(MATRIX_TO_URL_BASE);

        if (isSupported) {
            return url.substring(MATRIX_TO_URL_BASE.length());
        }

        return null;
    }

    /**
     * Escape '/' in id, because it is used as a separator
     *
     * @param id the id to escape
     * @return the escaped id
     */
    private static String escape(String id) {
        return id.replaceAll("/", "%2F");
    }

    /***
     * Tries to parse an universal link.
     *
     * @param uri the uri to parse
     * @param supportedHosts list of supported hosts, not including "matrix.to"
     * @param supportedPaths list of supported paths, when the host is in supportedHosts
     * @return the universal link items, or null if the universal link is invalid
     */
    @Nullable
    public static Map<String, String> parseUniversalLink(@Nullable Uri uri,
                                                         @NonNull List<String> supportedHosts,
                                                         @NonNull List<String> supportedPaths) {
        Map<String, String> map = null;

        try {
            // sanity check
            if (uri == null || TextUtils.isEmpty(uri.getPath())) {
                Log.e(LOG_TAG, "## parseUniversalLink : null");
                return null;
            }

            if (!supportedHosts.contains(uri.getHost()) && !TextUtils.equals(uri.getHost(), "matrix.to")) {
                Log.e(LOG_TAG, "## parseUniversalLink : unsupported host " + uri.getHost());
                return null;
            }

            boolean isSupportedHost = supportedHosts.contains(uri.getHost());

            // when the uri host is in supportedHosts (and is not "matrix.to"), it is followed by a dedicated path
            if (isSupportedHost && !supportedPaths.contains(uri.getPath())) {
                Log.e(LOG_TAG, "## parseUniversalLink : not supported");
                return null;
            }

            // remove the server part
            String uriFragment;
            if ((uriFragment = uri.getFragment()) != null) {
                uriFragment = uriFragment.substring(1); // get rid of first "/"
            } else {
                Log.e(LOG_TAG, "## parseUniversalLink : cannot extract path");
                return null;
            }

            String temp[] = uriFragment.split("/", 3); // limit to 3 for security concerns (stack overflow injection)

            if (!isSupportedHost) {
                List<String> compliantList = new ArrayList<>(Arrays.asList(temp));
                compliantList.add(0, "room");
                temp = compliantList.toArray(new String[compliantList.size()]);
            }

            if (temp.length < 2) {
                Log.e(LOG_TAG, "## parseUniversalLink : too short");
                return null;
            }

            if (!TextUtils.equals(temp[0], "room") && !TextUtils.equals(temp[0], "user")) {
                Log.e(LOG_TAG, "## parseUniversalLink : not supported " + temp[0]);
                return null;
            }

            map = new HashMap<>();

            String firstParam = temp[1];

            if (MXPatterns.isUserId(firstParam)) {
                if (temp.length > 2) {
                    Log.e(LOG_TAG, "## parseUniversalLink : universal link to member id is too long");
                    return null;
                }

                map.put(ULINK_MATRIX_USER_ID_KEY, firstParam);
            } else if (MXPatterns.isRoomAlias(firstParam) || MXPatterns.isRoomId(firstParam)) {
                map.put(ULINK_ROOM_ID_OR_ALIAS_KEY, firstParam);
            } else if (MXPatterns.isGroupId(firstParam)) {
                map.put(ULINK_GROUP_ID_KEY, firstParam);
            }

            // room id only ?
            if (temp.length > 2) {
                String eventId = temp[2];

                if (MXPatterns.isEventId(eventId)) {
                    map.put(ULINK_EVENT_ID_KEY, temp[2]);
                } else {
                    uri = Uri.parse(uri.toString().replace("#/room/", "room/"));

                    map.put(ULINK_ROOM_ID_OR_ALIAS_KEY, uri.getLastPathSegment());

                    Set<String> names = uri.getQueryParameterNames();

                    for (String name : names) {
                        String value = uri.getQueryParameter(name);

                        try {
                            value = URLDecoder.decode(value, "UTF-8");
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "## parseUniversalLink : URLDecoder.decode " + e.getMessage(), e);
                            return null;
                        }

                        map.put(name, value);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "## parseUniversalLink : crashes " + e.getLocalizedMessage(), e);
        }

        // check if the parsing succeeds
        if (map != null && map.isEmpty()) {
            Log.e(LOG_TAG, "## parseUniversalLink : empty dictionary");
            return null;
        }

        return map;
    }
}
