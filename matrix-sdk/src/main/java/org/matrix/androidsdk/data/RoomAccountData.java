/*
 * Copyright 2014 OpenMarket Ltd
 * Copyright 2017 Vector Creations Ltd
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

package org.matrix.androidsdk.data;

import com.google.gson.JsonObject;

import androidx.annotation.Nullable;

import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.sync.AccountDataElement;

import java.util.Map;
import java.util.Set;

/**
 * Class representing private data that the user has defined for a room.
 */
public class RoomAccountData implements java.io.Serializable {

    private static final long serialVersionUID = -8406116277864521120L;

    // The tags the user defined for this room.
    // The key is the tag name. The value, the associated RoomTag object.
    private Map<String, RoomTag> tags = null;

    // Tell whether the user allows the URL preview in this room.
    // By default we consider the user allows the URL preview.
    private boolean isURLPreviewAllowedByUser = true;

    /**
     * Process an event that modifies room account data (like m.tag event).
     *
     * @param event an event
     */
    public void handleEvent(Event event) {
        if (event.getType().equals(Event.EVENT_TYPE_TAGS)) {
            tags = RoomTag.roomTagsWithTagEvent(event);
        } else if (event.getType().equals(Event.EVENT_TYPE_URL_PREVIEW)) {
            final JsonObject jsonObject = event.getContentAsJsonObject();
            if (jsonObject != null && jsonObject.has(AccountDataElement.ACCOUNT_DATA_KEY_URL_PREVIEW_DISABLE)) {
                final boolean disabled = jsonObject.get(AccountDataElement.ACCOUNT_DATA_KEY_URL_PREVIEW_DISABLE).getAsBoolean();
                isURLPreviewAllowedByUser = !disabled;
            }
        }
    }

    /**
     * Provide a RoomTag for a key.
     *
     * @param key the key.
     * @return the roomTag if it is found else null
     */
    @Nullable
    public RoomTag roomTag(String key) {
        if ((null != tags) && tags.containsKey(key)) {
            return tags.get(key);
        }
        return null;
    }

    /**
     * @return true if some tags have been defined for the room
     */
    public boolean hasRoomTags() {
        return (null != tags) && (tags.size() > 0);
    }

    /**
     * @return the list of the keys used to defined the room tags, or null if there is no tag
     */
    @Nullable
    public Set<String> getRoomTagsKeys() {
        if (hasRoomTags()) {
            return tags.keySet();
        } else {
            return null;
        }
    }

    /**
     * Tells if the URL preview is allowed by the user in this room.
     *
     * @return true if allowed.
     */
    public boolean isURLPreviewAllowedByUser() {
        return isURLPreviewAllowedByUser;
    }

    /**
     * @deprecated use hasRoomTags() instead.
     * @return true if some tags have been defined for the room
     */
    public boolean hasTags() {
        return hasRoomTags();
    }

    /**
     * @deprecated use getRoomTagsKeys() instead.
     * @return the list of the keys used to defined the room tags, or null if there is no tag
     */
    @Nullable
    public Set<String> getKeys() {
        return getRoomTagsKeys();
    }
}
