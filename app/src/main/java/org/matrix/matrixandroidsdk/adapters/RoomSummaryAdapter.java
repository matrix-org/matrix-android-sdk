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
import android.graphics.Typeface;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.Button;
import android.widget.TextView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.data.RoomSummary;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.PublicRoom;
import org.matrix.matrixandroidsdk.Matrix;
import org.matrix.matrixandroidsdk.R;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class RoomSummaryAdapter extends BaseExpandableListAdapter {

    private Context mContext;
    private LayoutInflater mLayoutInflater;
    private int mLayoutResourceId;

    private int mUnreadColor;
    private int mHighlightColor;
    private int mPublicHighlightColor;

    private List<RoomSummary>mRecentsSummariesList;
    private List<PublicRoom>mPublicRoomsList;

    public int mRecentsGroupIndex = -1;
    public int mPublicsGroupIndex = -1;

    private  boolean mDisplayAllGroups = true;

    private List<RoomSummary>mFilteredRecentsSummariesList;
    private List<PublicRoom>mFilteredPublicRoomsList;

    private String mSearchedPattern = "";

    private DateFormat mDateFormat;

    private String mMyUserId = null;

    private ArrayList<String> mHighLightedRooms = new ArrayList<String>();
    private HashMap<String, Integer> mUnreadCountMap = new HashMap<String, Integer>();

    /**
     * Construct an adapter which will display a list of rooms.
     * @param context Activity context
     * @param layoutResourceId The resource ID of the layout for each item. Must have TextViews with
     *                         the IDs: roomsAdapter_roomName, roomsAdapter_roomTopic
     */
    public RoomSummaryAdapter(Context context, int layoutResourceId) {
        //super(context, layoutResourceId);
        mContext = context;
        mLayoutResourceId = layoutResourceId;
        mLayoutInflater = LayoutInflater.from(mContext);
        mDateFormat = new SimpleDateFormat("MMM d HH:mm", Locale.getDefault());
        //setNotifyOnChange(false);

        mRecentsSummariesList = new ArrayList<RoomSummary>();
        mPublicRoomsList  = new ArrayList<PublicRoom>();
        mUnreadColor = context.getResources().getColor(R.color.room_summary_unread_background);
        mHighlightColor = context.getResources().getColor(R.color.room_summary_highlight_background);
        mPublicHighlightColor = context.getResources().getColor(R.color.room_summary_public_highlight_background);

        mMyUserId = Matrix.getInstance(context.getApplicationContext()).getDefaultSession().getCredentials().userId;
    }

    /**
     *  unread messages map
     */
    public  HashMap<String, Integer> getUnreadCountMap() {
        return mUnreadCountMap;
    }

    public void setUnreadCountMap(HashMap<String, Integer> map) {
        mUnreadCountMap = map;
    }

    /**
     *  search management
     */

    public void setSearchedPattern(String pattern) {
        if (null == pattern) {
            pattern = "";
        }

        if (!pattern.equals(mSearchedPattern)) {
            mSearchedPattern = pattern.toLowerCase();
            this.notifyDataSetChanged();
        }
    }

    @Override
    public void notifyDataSetChanged() {
    // sanity check
    // ensure that the client is not logged out before refreshing the UI
    // the refresh could have been triggered with delay after a logout
    if (!Matrix.hasValidValidSession()) {
        return;
    }

    mFilteredRecentsSummariesList = new ArrayList<RoomSummary>();
        mFilteredPublicRoomsList = new ArrayList<PublicRoom>();

        // there is a pattern to search
        if (mSearchedPattern.length() > 0) {

            // search in the recent rooms
            for (RoomSummary summary : mRecentsSummariesList) {
                String roomName = summary.getRoomName();

                if (!TextUtils.isEmpty(roomName) && (roomName.toLowerCase().indexOf(mSearchedPattern) >= 0)) {
                    mFilteredRecentsSummariesList.add(summary);
                } else {
                    String topic = summary.getRoomTopic();

                    if (!TextUtils.isEmpty(topic) && (topic.toLowerCase().indexOf(mSearchedPattern) >= 0)) {
                        mFilteredRecentsSummariesList.add(summary);
                    }
                }
            }

            for (PublicRoom publicRoom : mPublicRoomsList) {
                String roomName = publicRoom.name;

                if (!TextUtils.isEmpty(roomName) && (roomName.toLowerCase().indexOf(mSearchedPattern) >= 0)) {
                    mFilteredPublicRoomsList.add(publicRoom);
                } else {
                    String alias = publicRoom.roomAliasName;

                    if (!TextUtils.isEmpty(alias) && (alias.toLowerCase().indexOf(mSearchedPattern) >= 0)) {
                        mFilteredPublicRoomsList.add(publicRoom);
                    }
                }
            }
        }

        super.notifyDataSetChanged();
    }

    /**
     * Check if the group index is the recents one.
     * @param groupIndex the group index.
     * @return true if the recents group oone
     */
    public boolean isRecentsGroupIndex(int groupIndex) {
        return groupIndex == mRecentsGroupIndex;
    }

    /**
     * Check if the group index is the public ones.
     * @param groupIndex the group index.
     * @return true if the group is the publics one.
     */
    public boolean isPublicsGroupIndex(int groupIndex) {
        return groupIndex == mPublicsGroupIndex;
    }

    /**
     * Force to display all the groups
     * @param displayAllGroups status
     */
    public void setDisplayAllGroups(boolean displayAllGroups) {
        // the user can force to clear the public rooms with the recents ones
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(mContext);
        displayAllGroups |= preferences.getBoolean(mContext.getString(R.string.settings_key_display_public_rooms_recents), true);

        if (mDisplayAllGroups != displayAllGroups) {
            mDisplayAllGroups = displayAllGroups;
            notifyDataSetChanged();
        }
    }

    /**
     * publics list management
     */

    public void setPublicRoomsList(List<PublicRoom> aRoomsList) {
        if (null == aRoomsList) {
            mPublicRoomsList  = new ArrayList<PublicRoom>();
        } else {
            mPublicRoomsList = aRoomsList;
            sortSummaries();
        }

        this.notifyDataSetChanged();
    }

    public PublicRoom getPublicRoomAt(int index) {
        if (mSearchedPattern.length() > 0) {
            return mFilteredPublicRoomsList.get(index);
        } else {
            return mPublicRoomsList.get(index);
        }
    }
    /**
     * recents list management
     */

    public List<RoomSummary> getRecentsSummariesList() {
        return mRecentsSummariesList;
    }

    public void addRoomSummary(RoomSummary roomSummary) {
        // check if the summary is not added twice.
        for(RoomSummary rSum : mRecentsSummariesList) {
            if (rSum.getRoomId().equals(roomSummary.getRoomId())) {
                return;
            }
        }

        mRecentsSummariesList.add(roomSummary);
    }

    public RoomSummary getRoomSummaryAt(int index) {
        if (mSearchedPattern.length() > 0) {
            return mFilteredRecentsSummariesList.get(index);
        } else {
            return mRecentsSummariesList.get(index);
        }
    }

    public void removeRoomSummary(RoomSummary roomSummary) {
        mRecentsSummariesList.remove(roomSummary);

        if (null != roomSummary.getRoomId()) {
            mUnreadCountMap.remove(roomSummary.getRoomId());
        }
    }

    public RoomSummary getSummaryByRoomId(String roomId) {
        for (int i=0; i<mRecentsSummariesList.size(); i++) {
            RoomSummary summary = mRecentsSummariesList.get(i);
            if (roomId.equals(summary.getRoomId())) {
                return summary;
            }
        }
        return null;
    }

    /**
     * Set the latest event for a room summary.
     * @param event The latest event
     */
    public void setLatestEvent(Event event, RoomState roomState) {
        RoomSummary summary = getSummaryByRoomId(event.roomId);
        if (summary != null) {
            summary.setLatestEvent(event);
            summary.setLatestRoomState(roomState);
            sortSummaries();
            notifyDataSetChanged();
        }
    }

    public void incrementUnreadCount(String roomId) {
        Integer count = mUnreadCountMap.get(roomId);
        if (count == null) {
            count = 0;
        }
        mUnreadCountMap.put(roomId, count + 1);
    }

    public void highlightRoom(String roomId) {
        if (mHighLightedRooms.indexOf(roomId) < 0) {
            mHighLightedRooms.add(roomId);
        }
    }

    public void resetUnreadCount(String roomId) {
        mUnreadCountMap.put(roomId, 0);
        mHighLightedRooms.remove(roomId);
    }

    public void resetUnreadCounts() {
        Set<String> roomIds = mUnreadCountMap.keySet();

        for(String roomId : roomIds) {
            resetUnreadCount(roomId);
        }
    }

    public void sortSummaries() {
        Collections.sort(mRecentsSummariesList, new Comparator<RoomSummary>() {
            @Override
            public int compare(RoomSummary lhs, RoomSummary rhs) {
                if (lhs == null || lhs.getLatestEvent() == null) {
                    return 1;
                } else if (rhs == null || rhs.getLatestEvent() == null) {
                    return -1;
                }

                if (lhs.getLatestEvent().originServerTs > rhs.getLatestEvent().originServerTs) {
                    return -1;
                } else if (lhs.getLatestEvent().originServerTs < rhs.getLatestEvent().originServerTs) {
                    return 1;
                }
                return 0;
            }
        });

        Collections.sort(mPublicRoomsList, new Comparator<PublicRoom>() {
            @Override
            public int compare(PublicRoom publicRoom, PublicRoom publicRoom2) {
                return publicRoom2.numJoinedMembers - publicRoom.numJoinedMembers;
            }
        });
    }

    @Override
    public View getChildView(int groupPosition, final int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {

        // display a spinner while loading the public rooms
        //

        // detect if the view is progressbar_waiting_room_members one
        View spinner = null;
        if (null != convertView) {
            spinner = convertView.findViewById(R.id.progressbar_waiting_room_members);
        }

        // assume that some public rooms are defined
        if ((groupPosition == mPublicsGroupIndex) && (0 == mPublicRoomsList.size())) {
            if (null == spinner) {
                convertView = mLayoutInflater.inflate(R.layout.adapter_item_waiting_room_members, parent, false);
            }
            return convertView;
        }

        // must not reuse the view if it is not the right type
        if (null != spinner) {
            convertView = null;
        }

        if (convertView == null) {
            convertView = mLayoutInflater.inflate(mLayoutResourceId, parent, false);
        }

        final Button deleteButton = (Button) convertView.findViewById(R.id.roomSummaryAdapter_delete_button);
        deleteButton.setVisibility((groupPosition == mRecentsGroupIndex) ? View.VISIBLE : View.GONE);

        final View deleteProgress = (View) convertView.findViewById(R.id.roomSummaryAdapter_delete_progress);
        deleteProgress.setVisibility(View.GONE);
        
        // required to have the onChildClick event
        // does not work when settings in the adapter xml file.
        deleteButton.setFocusable(false);

        if (groupPosition == mRecentsGroupIndex) {
            List<RoomSummary> summariesList = (mSearchedPattern.length() > 0) ? mFilteredRecentsSummariesList : mRecentsSummariesList;

            RoomSummary summary = summariesList.get(childPosition);

            Integer unreadCount = mUnreadCountMap.get(summary.getRoomId());

            if ((unreadCount == null) || (unreadCount == 0)) {
                convertView.setBackgroundColor(0);
            } else if (mHighLightedRooms.indexOf(summary.getRoomId()) >= 0) {
                convertView.setBackgroundColor(mHighlightColor);
            } else {
                convertView.setBackgroundColor(mUnreadColor);
            }

            CharSequence message = summary.getRoomTopic();
            String timestamp = null;

            TextView textView = (TextView) convertView.findViewById(R.id.roomSummaryAdapter_roomName);

            RoomState latestRoomState = summary.getLatestRoomState();
            if (null == latestRoomState) {
                Room room = Matrix.getInstance(mContext.getApplicationContext()).getDefaultSession().getDataHandler().getStore().getRoom(summary.getRoomId());

                if (null != room.getLiveState()) {
                    latestRoomState = room.getLiveState().deepCopy();
                }
            }

            // the public rooms are displayed with bold fonts
            if ((null != latestRoomState) && (null != latestRoomState.visibility) && latestRoomState.visibility.equals(RoomState.VISIBILITY_PUBLIC)) {
                textView.setTypeface(null, Typeface.BOLD);
            } else {
                textView.setTypeface(null, Typeface.NORMAL);
            }

            // display the unread messages count
            String roomNameMessage = (latestRoomState != null) ? latestRoomState.getDisplayName(mMyUserId) : summary.getRoomName();

            if (null != roomNameMessage) {
                if ((null != unreadCount) && (unreadCount > 0)) {
                    roomNameMessage += " (" + unreadCount + ")";
                }
            }

            textView.setText(roomNameMessage);

            if (summary.getLatestEvent() != null) {
                AdapterUtils.EventDisplay display = new AdapterUtils.EventDisplay(mContext, summary.getLatestEvent(), latestRoomState);
                display.setPrependMessagesWithAuthor(true);
                message = display.getTextualDisplay();

                timestamp = mDateFormat.format(new Date(summary.getLatestEvent().originServerTs));
            }

            // check if this is an invite
            if (summary.isInvited()) {
                String memberName = summary.getInviterUserId();

                if (null != latestRoomState) {
                    memberName = latestRoomState.getMemberName(memberName);
                }

                message = mContext.getString(R.string.summary_user_invitation, memberName);
            }

            textView = (TextView) convertView.findViewById(R.id.roomSummaryAdapter_message);
            textView.setText(message);
            textView = (TextView) convertView.findViewById(R.id.roomSummaryAdapter_ts);
            textView.setVisibility(View.VISIBLE);
            textView.setText(timestamp);

            // the user taps on
            final String fRoomid = summary.getRoomId();
            deleteButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    MXSession session = Matrix.getInstance(mContext.getApplicationContext()).getDefaultSession();
                    Room room = session.getDataHandler().getRoom(fRoomid);

                    if (null != room) {
                        room.leave(new SimpleApiCallback<Void>(mContext, deleteButton) {
                            @Override
                            public void onSuccess(Void info) {

                            }
                        });
                    }
                }
            });

            MXSession session = Matrix.getInstance(mContext.getApplicationContext()).getDefaultSession();
            Room room = session.getDataHandler().getRoom(fRoomid);

            if (room.isLeaving()) {
                convertView.setAlpha(0.3f);
                deleteButton.setVisibility(View.GONE);
                deleteProgress.setVisibility(View.VISIBLE);
            } else {
                convertView.setAlpha(1.0f);
                deleteButton.setVisibility(View.VISIBLE);
                deleteProgress.setVisibility(View.GONE);
            }

        } else {
            List<PublicRoom> publicRoomsList = (mSearchedPattern.length() > 0) ? mFilteredPublicRoomsList : mPublicRoomsList;
            PublicRoom publicRoom = publicRoomsList.get(childPosition);
            String displayName = publicRoom.getDisplayName(mMyUserId);

            TextView textView = (TextView) convertView.findViewById(R.id.roomSummaryAdapter_roomName);
            textView.setTypeface(null, Typeface.BOLD);
            textView.setText(displayName);

            textView = (TextView) convertView.findViewById(R.id.roomSummaryAdapter_message);
            textView.setText(publicRoom.topic);

            textView = (TextView) convertView.findViewById(R.id.roomSummaryAdapter_ts);
            textView.setVisibility(View.VISIBLE);

            if (publicRoom.numJoinedMembers > 1) {
                textView.setText(publicRoom.numJoinedMembers + " " + mContext.getString(R.string.users));
            } else {
                textView.setText(publicRoom.numJoinedMembers + " " + mContext.getString(R.string.user));
            }

            String alias = publicRoom.getFirstAlias();

            if ((null != alias) && (mHighLightedRooms.indexOf(alias) >= 0)) {
                convertView.setBackgroundColor(mPublicHighlightColor);
            } else {
                convertView.setBackgroundColor(0);
            }
        }

        return convertView;
    }

    @Override
    public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = mLayoutInflater.inflate(R.layout.adapter_room_section_header, null);
        }

        TextView heading = (TextView) convertView.findViewById(R.id.heading);

        if (groupPosition == mRecentsGroupIndex) {

            int unreadCount = 0;

            for(Integer i : mUnreadCountMap.values()) {
                unreadCount += i;
            }

            String header = mContext.getResources().getString(R.string.my_rooms);

            if (unreadCount > 0) {
                header += " ("  + unreadCount + ")";
            }

            heading.setText(header);
        } else {
            heading.setText(mContext.getResources().getString(R.string.action_public_rooms));
        }

        return convertView;
    }

    @Override
    public Object getChild(int groupPosition, int childPosition) {
        return null;
    }

    @Override
    public long getChildId(int groupPosition, int childPosition) {
        return 0;
    }

    @Override
    public int getChildrenCount(int groupPosition) {
        if (groupPosition == mRecentsGroupIndex) {
            return (mSearchedPattern.length() > 0) ? mFilteredRecentsSummariesList.size() : mRecentsSummariesList.size();
        } else {
            // display a spinner until the public rooms are loaded
            if (mPublicRoomsList.size() == 0) {
                return 1;
            } else {
                return (mSearchedPattern.length() > 0) ? mFilteredPublicRoomsList.size() : mPublicRoomsList.size();
            }
        }
    }

    @Override
    public Object getGroup(int groupPosition) {
        return null;
    }

    @Override
    public int getGroupCount() {
        int count = 0;

        mRecentsGroupIndex = -1;
        mPublicsGroupIndex = -1;

        if ((mRecentsSummariesList.size() > 0) || mDisplayAllGroups) {
            mRecentsGroupIndex = count;
            count++;
        }

        // display the public rooms in the recents only if there is no dedicated room
        if ((mRecentsSummariesList.size() == 0) || mDisplayAllGroups) {
            mPublicsGroupIndex = count;
            count++;
        }

        return count;
    }

    @Override
    public void onGroupCollapsed(int groupPosition) {
        super.onGroupCollapsed(groupPosition);
    }

    @Override
    public void onGroupExpanded(int groupPosition) {
        super.onGroupExpanded(groupPosition);
    }

    @Override
    public long getGroupId(int groupPosition) {
        return 0;
    }

    @Override
    public boolean hasStableIds() {
        return false;
    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return true;
    }


}
