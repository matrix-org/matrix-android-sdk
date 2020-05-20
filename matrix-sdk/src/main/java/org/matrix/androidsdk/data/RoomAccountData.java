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

import androidx.annotation.Nullable;

import com.google.gson.JsonObject;

import org.matrix.androidsdk.core.JsonUtils;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.TaggedEventInfo;
import org.matrix.androidsdk.rest.model.TaggedEventsContent;
import org.matrix.androidsdk.rest.model.sync.AccountDataElement;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Class representing private data that the user has defined for a room.
 */
public class RoomAccountData implements java.io.Serializable {

    private static final long serialVersionUID = -8406116277864521120L;

    // The tags the user defined for this room.
    // The key is the tag name. The value, the associated RoomTag object.
    private Map<String, RoomTag> roomTags = null;

    // Tell whether the user allows the URL preview in this room.
    // By default we consider the user allows the URL preview.
    private boolean isURLPreviewAllowedByUser = true;

    // The events the user has marked as a favourite in this room.
    private Map<String, TaggedEventInfo> favouriteEvents = null;

    // The events the user wants to hide in this room.
    private Map<String, TaggedEventInfo> hiddenEvents = null;

    // Store the content of all the provided events by using their event type.
    private Map<String, JsonObject> eventContentsMap = new HashMap<>();

    /**
     * Process an event that modifies room account data (like m.tag event).
     *
     * @param event an event
     */
    public void handleEvent(Event event) {
        final String eventType = event.getType();
        final JsonObject jsonObject = event.getContentAsJsonObject();

        if (eventType.equals(Event.EVENT_TYPE_TAGS)) {
            roomTags = RoomTag.roomTagsWithTagEvent(event);
        } else if (eventType.equals(Event.EVENT_TYPE_URL_PREVIEW)) {
            if (jsonObject != null && jsonObject.has(AccountDataElement.ACCOUNT_DATA_KEY_URL_PREVIEW_DISABLE)) {
                final boolean disabled = jsonObject.get(AccountDataElement.ACCOUNT_DATA_KEY_URL_PREVIEW_DISABLE).getAsBoolean();
                isURLPreviewAllowedByUser = !disabled;
            }
        } else if (eventType.equals(Event.EVENT_TYPE_TAGGED_EVENTS)) {
            final TaggedEventsContent taggedEventContent = JsonUtils.toTaggedEventsContent(jsonObject);
            favouriteEvents = taggedEventContent.getFavouriteEvents();
            hiddenEvents = taggedEventContent.getHiddenEvents();
        }

        // Store by default the content of all the provided events.
        if (jsonObject != null) {
            eventContentsMap.put(eventType, jsonObject);
        } else {
            // Store an empty JsonObject
            eventContentsMap.put(eventType, new JsonObject());
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
        if ((null != roomTags) && roomTags.containsKey(key)) {
            return roomTags.get(key);
        }
        return null;
    }

    /**
     * @return true if some tags have been defined for the room
     */
    public boolean hasRoomTags() {
        return (null != roomTags) && (roomTags.size() > 0);
    }

    /**
     * @return the list of the keys used to defined the room tags, or null if there is no tag
     */
    @Nullable
    public Set<String> getRoomTagsKeys() {
        if (hasRoomTags()) {
            return roomTags.keySet();
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
     * @return the list of the identifiers of the events marked as a favourite in this room.
     */
    public Set<String> getFavouriteEventIds() {
        if (null != favouriteEvents) {
            return favouriteEvents.keySet();
        } else {
            return new HashSet<>();
        }
    }

    /**
     * Provide the information on a favourite event.
     *
     * @param eventId the event identifier.
     * @return a TaggedEventInfo instance if the event is marked as a favourite, else null.
     */
    @Nullable
    public TaggedEventInfo favouriteEventInfo(String eventId) {
        if (null != favouriteEvents) {
            return favouriteEvents.get(eventId);
        }
        return null;
    }

    /**
     * @return the list of the identifiers of the hidden events in this room.
     */
    public Set<String> getHiddenEventIds() {
        if (null != hiddenEvents) {
            return hiddenEvents.keySet();
        } else {
            return new HashSet<>();
        }
    }

    /**
     * Provide the information on a hidden event.
     *
     * @param eventId the event identifier.
     * @return a TaggedEventInfo instance if the event has been hidden by the user, else null.
     */
    @Nullable
    public TaggedEventInfo hiddenEventInfo(String eventId) {
        if (null != hiddenEvents) {
            return hiddenEvents.get(eventId);
        }
        return null;
    }

    /**
     * Provide the content of an handled event according to its type.
     *
     * @param eventType the type of the requested event.
     * @return the event content casted as JsonObject (null if no event has been handled with this type).
     * @apiNote Use this method only when no dedicated method exists for the requested event type.
     */
    @Nullable
    public JsonObject eventContent(String eventType) {
        return eventContentsMap.get(eventType);
    }


    /**
     * @return true if some tags have been defined for the room
     * @deprecated use hasRoomTags() instead.
     */
    public boolean hasTags() {
        return hasRoomTags();
    }

    /**
     * @return the list of the keys used to defined the room tags, or null if there is no tag
     * @deprecated use getRoomTagsKeys() instead.
     */
    @Nullable
    public Set<String> getKeys() {
        return getRoomTagsKeys();
    }
}
