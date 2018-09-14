/*
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

package org.matrix.androidsdk.adapters;

import android.content.Context;
import android.widget.ArrayAdapter;

import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.RoomMember;

import java.util.List;

/**
 * Abstract implementation of messages list
 */
public abstract class AbstractMessagesAdapter extends ArrayAdapter<MessageRow> {

    // default constructor
    public AbstractMessagesAdapter(Context context, int view) {
        super(context, view);
    }

    /*
     * *********************************************************************************************
     * Items getter / setter
     * *********************************************************************************************
     */

    /**
     * Add a row and refresh the adapter if it is required.
     *
     * @param row     the row to append
     * @param refresh true to refresh the display.
     */
    public abstract void add(MessageRow row, boolean refresh);

    /**
     * Add a message row to the top.
     *
     * @param row the row to append
     */
    public abstract void addToFront(MessageRow row);

    /**
     * Provides the messageRow from an event Id.
     *
     * @param eventId the event Id.
     * @return the message row.
     */
    public abstract MessageRow getMessageRow(String eventId);

    /**
     * Get the closest row after the given event
     * Used when we need to jump to an event that is not displayed
     *
     * @param event the event
     * @return closest row the closest row
     */
    public abstract MessageRow getClosestRow(Event event);

    /**
     * Get the closest row after the given event id/ts
     * Used when we need to jump to an event that is not displayed
     *
     * @param eventId the event id
     * @param eventTs the event timestamp
     * @return closest row
     */
    public abstract MessageRow getClosestRowFromTs(final String eventId, final long eventTs);

    /**
     * Get the closest row before the given event id/ts
     *
     * @param eventId the event id
     * @param eventTs the event timestamp
     * @return closest row
     */
    public abstract MessageRow getClosestRowBeforeTs(final String eventId, final long eventTs);

    /**
     * Update the message row to a new event id.
     *
     * @param event      the new event
     * @param oldEventId the old message row event id.
     */
    public abstract void updateEventById(Event event, String oldEventId);

    /**
     * Remove an event by an eventId
     *
     * @param eventId the event id.
     */
    public abstract void removeEventById(String eventId);

    /*
     * *********************************************************************************************
     * Display modes
     * *********************************************************************************************
     */

    /**
     * Update the preview mode status
     *
     * @param isPreviewMode true to display the adapter in preview mode
     */
    public abstract void setIsPreviewMode(boolean isPreviewMode);

    /**
     * Set whether we are ine preview mode to show unread messages
     *
     * @param isUnreadViewMode true if it shoudl be displayed in preview mode.
     */
    public abstract void setIsUnreadViewMode(boolean isUnreadViewMode);

    /**
     * Get whether we are in preview mode to show unread messages
     *
     * @return true if preview to show unread messages
     */
    public abstract boolean isUnreadViewMode();

    /*
     * *********************************************************************************************
     * Preview mode
     * *********************************************************************************************
     */

    /**
     * Defines the search pattern.
     *
     * @param pattern the pattern to search.
     */
    public abstract void setSearchPattern(String pattern);

    /*
     * *********************************************************************************************
     * Read markers
     * *********************************************************************************************
     */

    /**
     * Reset the read marker event so read marker view will not be displayed again on same event
     */
    public abstract void resetReadMarker();

    /**
     * Specify the last read message (to display read marker line)
     *
     * @param readMarkerEventId  the read marker event id.
     * @param readReceiptEventId the read receipt event id.
     */
    public abstract void updateReadMarker(final String readMarkerEventId, final String readReceiptEventId);

    /*
     * *********************************************************************************************
     * Others
     * *********************************************************************************************
     */

    /**
     * @return the max thumbnail width
     */
    public abstract int getMaxThumbnailWidth();

    /**
     * @return the max thumbnail height
     */
    public abstract int getMaxThumbnailHeight();

    /**
     * Notify that some bing rules could have been updated.
     */
    public abstract void onBingRulesUpdate();

    /**
     * Give the list of retrieved room members.
     *
     * @param roomMembers the full list of room members. The state of the room may not contain all user when lazy loading is enabled.
     */
    public abstract void setLiveRoomMembers(List<RoomMember> roomMembers);
}
