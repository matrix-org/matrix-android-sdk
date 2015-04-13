/*
 * Copyright 2015 OpenMarket Ltd
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

package org.matrix.matrixandroidsdk.adapters;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.matrix.androidsdk.MXSession;
import org.matrix.matrixandroidsdk.R;
import org.matrix.androidsdk.adapters.RoomSummaryAdapter;

/**
 * An adapter which can display room information.
 */
public class ConsoleRoomSummaryAdapter extends RoomSummaryAdapter {

    public ConsoleRoomSummaryAdapter(MXSession session, Context context, int layoutResourceId, int headerLayoutResourceId)  {
        super(session, context, layoutResourceId, headerLayoutResourceId);
    }

    public int getUnreadMessageBackgroundColor() {
        return mContext.getResources().getColor(R.color.room_summary_unread_background);
    }

    public int getHighlightMessageBackgroundColor() {
        return mContext.getResources().getColor(R.color.room_summary_highlight_background);
    }

    public int getPublicHighlightMessageBackgroundColor() {
        return mContext.getResources().getColor(R.color.room_summary_public_highlight_background);
    }

    public boolean displayPublicRooms() {
        // the user can force to clear the public rooms with the recents ones
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        return preferences.getBoolean(mContext.getString(R.string.settings_key_display_public_rooms_recents), true);
    }

    public String myRoomsTitle() {
        return mContext.getResources().getString(R.string.my_rooms);
    }

    public String publicRoomsTitle() {
        return mContext.getResources().getString(R.string.action_public_rooms);
    }
}
