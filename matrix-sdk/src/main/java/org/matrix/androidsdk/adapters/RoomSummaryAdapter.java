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

package org.matrix.androidsdk.adapters;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import org.matrix.androidsdk.R;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.data.RoomSummary;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.PublicRoom;
import org.matrix.androidsdk.util.EventDisplay;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

public abstract class RoomSummaryAdapter extends BaseExpandableListAdapter {

    protected Context mContext;
    private LayoutInflater mLayoutInflater;
    private int mLayoutResourceId;
    private int mHeaderLayoutResourceId;

    private int mUnreadColor;
    private int mHighlightColor;
    private int mPublicHighlightColor;

    private int mUnreadTextColor;
    private int mHighlightTextColor;
    private int mDefaultTextColor;
    private int mSectionTitleColor;

    private ArrayList<ArrayList<RoomSummary>> mRecentsSummariesList;

    protected List<List<PublicRoom>> mPublicRoomsLists = null;
    protected List<String> mPublicRoomsHomeServerLists = null;

    public int mPublicsGroupStartIndex = -1;

    private  boolean mDisplayAllGroups = true;

    private ArrayList<ArrayList<RoomSummary>> mFilteredRecentsSummariesList = null;
    private ArrayList<ArrayList<PublicRoom>> mFilteredPublicRoomsList = null;

    private String mSearchedPattern = "";

    private ArrayList<String> mHighLightedRooms = new ArrayList<String>();
    protected ArrayList<HashMap<String, RoomSummary>> mSummaryMapsBySection = new ArrayList<HashMap<String, RoomSummary>>();

    // abstract methods
    public abstract int getUnreadMessageBackgroundColor();
    public abstract int getHighlightMessageBackgroundColor();
    public abstract int getPublicHighlightMessageBackgroundColor();
    public abstract boolean displayPublicRooms();
    public abstract String myRoomsTitle(int section);
    public abstract String publicRoomsTitle(int section);
    public abstract Room roomFromRoomSummary(RoomSummary roomSummary);
    public abstract String memberDisplayName(String matrixId, String userId);

    protected int getUnreadMessageTextColor() {
        return Color.BLACK;
    }

    protected int getHighlightMessageTextColor() {
        return Color.BLACK;
    }

    protected int getDefaultTextColor() {
        return Color.BLACK;
    }

    protected int getSectionTitleColor() {
        return Color.BLACK;
    }

    /**
     * Construct an adapter which will display a list of rooms.
     * @param context Activity context
     * @param layoutResourceId The resource ID of the layout for each item. Must have TextViews with
     *                         the IDs: roomsAdapter_roomName, roomsAdapter_roomTopic
     * @param headerLayoutResourceId the header layout id
     */
    public RoomSummaryAdapter(Context context, int nbrSections, int layoutResourceId, int headerLayoutResourceId) {
        mContext = context;
        mLayoutResourceId = layoutResourceId;
        mHeaderLayoutResourceId = headerLayoutResourceId;
        mLayoutInflater = LayoutInflater.from(mContext);
        //setNotifyOnChange(false);

        mRecentsSummariesList = new ArrayList<ArrayList<RoomSummary>>();
        for(int section = 0; section < nbrSections; section++) {
            mRecentsSummariesList.add(new ArrayList<RoomSummary>());
            mSummaryMapsBySection.add(new HashMap<String, RoomSummary>());
        }

        mPublicRoomsLists  = null;
        mUnreadColor = getUnreadMessageBackgroundColor();
        mHighlightColor = getHighlightMessageBackgroundColor();
        mPublicHighlightColor = getPublicHighlightMessageBackgroundColor();

        mUnreadTextColor = getUnreadMessageTextColor();
        mHighlightTextColor = getHighlightMessageTextColor();
        mDefaultTextColor = getDefaultTextColor();
        mSectionTitleColor = getSectionTitleColor();
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
        mFilteredRecentsSummariesList = new ArrayList<ArrayList<RoomSummary>>();
        mFilteredPublicRoomsList = new ArrayList<ArrayList<PublicRoom>>();

        // there is a pattern to search
        if (mSearchedPattern.length() > 0) {

            for(int index = 0; index < mRecentsSummariesList.size(); index++) {
                ArrayList<RoomSummary> roomSummaries = mRecentsSummariesList.get(index);
                ArrayList<RoomSummary> filteredRes = new ArrayList<RoomSummary>();

                // search in the recent rooms
                for (RoomSummary summary : roomSummaries) {
                    String roomName = summary.getRoomName();

                    if (!TextUtils.isEmpty(roomName) && (roomName.toLowerCase().indexOf(mSearchedPattern) >= 0)) {
                        filteredRes.add(summary);
                    } else {
                        String topic = summary.getRoomTopic();

                        if (!TextUtils.isEmpty(topic) && (topic.toLowerCase().indexOf(mSearchedPattern) >= 0)) {
                            filteredRes.add(summary);
                        }
                    }
                }
                mFilteredRecentsSummariesList.add(filteredRes);
            }

            // set to null until it is initialized
            if (null != mPublicRoomsLists) {
                for(List<PublicRoom> publicRoomslist : mPublicRoomsLists) {

                    ArrayList<PublicRoom> fiteredList = new ArrayList<PublicRoom>();
                    mFilteredPublicRoomsList.add(fiteredList);


                    for (PublicRoom publicRoom : publicRoomslist) {
                        String roomName = publicRoom.name;

                        if (!TextUtils.isEmpty(roomName) && (roomName.toLowerCase().indexOf(mSearchedPattern) >= 0)) {
                            fiteredList.add(publicRoom);
                        } else {
                            String alias = publicRoom.roomAliasName;

                            if (!TextUtils.isEmpty(alias) && (alias.toLowerCase().indexOf(mSearchedPattern) >= 0)) {
                                fiteredList.add(publicRoom);
                            }
                        }
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
        return (mPublicsGroupStartIndex < 0) || (groupIndex < mPublicsGroupStartIndex);
    }

    /**
     * Check if the group index is the public ones.
     * @param groupIndex the group index.
     * @return true if the group is the publics one.
     */
    public boolean isPublicsGroupIndex(int groupIndex) {
        return (mPublicsGroupStartIndex > 0)  && (groupIndex >= mPublicsGroupStartIndex);
    }

    /**
     * Force to display all the groups
     * @param displayAllGroups status
     */
    public void setDisplayAllGroups(boolean displayAllGroups) {
        displayAllGroups |= displayPublicRooms();

        if (mDisplayAllGroups != displayAllGroups) {
            mDisplayAllGroups = displayAllGroups;
            notifyDataSetChanged();
        }
    }

    /**
     * public rooms list management
     */
    public void setPublicRoomsList(List<List<PublicRoom>> aRoomsListList, List<String> homeServerNamesList) {
        mPublicRoomsLists = aRoomsListList;
        mPublicRoomsHomeServerLists = homeServerNamesList;

        if (null != aRoomsListList) {
            for(List<PublicRoom> publicRoomsList : mPublicRoomsLists) {
                // the public rooms must only be sorted once
                // sortSummaries is called at each new displayable event.
                Collections.sort(publicRoomsList, new Comparator<PublicRoom>() {
                    @Override
                    public int compare(PublicRoom publicRoom, PublicRoom publicRoom2) {
                        return publicRoom2.numJoinedMembers - publicRoom.numJoinedMembers;
                    }
                });
            }
        }
    }

    /**
     * Returns the public Room at position (group, section)
     * @param groupIndex the group index.
     * @param section the section index.
     * @return the matched PublicRoom if it exists, else null.
     */
    public PublicRoom getPublicRoomAt(int groupIndex, int section) {
        if (mSearchedPattern.length() > 0) {
            return mFilteredPublicRoomsList.get(groupIndex - mPublicsGroupStartIndex).get(section);
        } else {
            if (null != mPublicRoomsLists) {
                return mPublicRoomsLists.get(groupIndex - mPublicsGroupStartIndex).get(section);
            } return null;
        }
    }

    /**
     * Returns the home server URL for the group index.
     * @param groupIndex the group index.
     * @return the home server URL.
     */
    public String getHomeServerURLAt(int groupIndex) {
        if (null != mPublicRoomsHomeServerLists) {
            return mPublicRoomsHomeServerLists.get(groupIndex - mPublicsGroupStartIndex);
        } return null;

    }

    /**
     * recent rooms list management
     */
    public ArrayList<ArrayList<RoomSummary>> getRecentsSummariesList() {
        return mRecentsSummariesList;
    }

    public void addRoomSummary(int section, RoomSummary roomSummary) {
        if (section < mRecentsSummariesList.size()) {
            ArrayList<RoomSummary> list = mRecentsSummariesList.get(section);
            HashMap<String, RoomSummary> maps = mSummaryMapsBySection.get(section);

            // avoid multiple definitions
            if (maps.get(roomSummary.getRoomId()) == null) {
                list.add(roomSummary);
                maps.put(roomSummary.getRoomId(), roomSummary);
            }
        }
    }

    public RoomSummary getRoomSummaryAt(int section, int index) {
        if (mSearchedPattern.length() > 0) {
            return mFilteredRecentsSummariesList.get(section).get(index);
        } else {
            return mRecentsSummariesList.get(section).get(index);
        }
    }

    public void removeRoomSummary(int section, RoomSummary roomSummary) {
        mRecentsSummariesList.get(section).remove(roomSummary);

        if (null != roomSummary.getRoomId()) {
            mSummaryMapsBySection.get(section).remove(roomSummary.getRoomId());
        }
    }

    public RoomSummary getSummaryByRoomId(int section, String roomId) {
        ArrayList<RoomSummary> list = mRecentsSummariesList.get(section);

        for (int i=0; i< list.size(); i++) {
            RoomSummary summary = list.get(i);
            if (roomId.equals(summary.getRoomId())) {
                return summary;
            }
        }
        return null;
    }

    public void removeSection(int section) {
        mRecentsSummariesList.remove(section);

        if ((null != mFilteredRecentsSummariesList) && (mFilteredRecentsSummariesList.size() > section)) {
            mFilteredRecentsSummariesList.remove(section);
        }

        mSummaryMapsBySection.remove(section);
    }

    /**
     * Set the latest event for a room summary.
     * @param event The latest event
     * @param roomState the roomState
     * @param refresh true to refresh the UI
     */
    public void setLatestEvent(int section, Event event, RoomState roomState, Boolean refresh) {
        RoomSummary summary = getSummaryByRoomId(section, event.roomId);
        if (summary != null) {
            summary.setLatestEvent(event);
            summary.setLatestRoomState(roomState);

            // refresh on demand
            if (refresh) {
                sortSummaries();
                notifyDataSetChanged();
            }
        }
    }

    /**
     * Increments the unread message counters for a dedicated room.
     * @param roomId The room identifier
     */
    public void incrementUnreadCount(int section, String roomId) {
        RoomSummary roomSummary  = mSummaryMapsBySection.get(section).get(roomId);

        if (null != roomSummary) {
            roomSummary.incrementUnreadMessagesCount();
        }
    }

    /**
     * Defines that the room must be highlighted in the rooms list
     * @param roomId The room ID of the room to highlight.
     */
    public void highlightRoom(String roomId) {
        if (mHighLightedRooms.indexOf(roomId) < 0) {
            mHighLightedRooms.add(roomId);
        }
    }

    /**
     * Reset the unread messages counter and remove the rooms from the highlighted rooms lists.
     * @param section the section
     * @param roomId the room Id.
     * @return true if there is an update.
     */
    public boolean resetUnreadCount(int section, String roomId) {
        boolean res = false;

        RoomSummary roomSummary  = mSummaryMapsBySection.get(section).get(roomId);

        // sanity check
        if (null != roomSummary) {
            res |= roomSummary.resetUnreadMessagesCount();
            res |= roomSummary.setHighlighted(false);
        }

        return res;
    }

    /**
     * Reset the unread message counters in a section.
     * @param section the section.
     * @return true if something has been updated.
     */
    public boolean resetUnreadCounts(int section) {
        boolean res = false;

        Collection<RoomSummary> summaries = mSummaryMapsBySection.get(section).values();

        for(RoomSummary summary : summaries) {
            res |= summary.resetUnreadMessagesCount();
            res |= summary.setHighlighted(false);
        }

        return res;
    }

    /**
     * Sort the room summaries list.
     * 1 - Sort by the latest event timestamp (most recent first).
     * 2 - Sort the public rooms by the number of members (bigger room first)
     */
    public void sortSummaries() {
        for(int section = 0; section < mRecentsSummariesList.size(); section++) {
            ArrayList<RoomSummary> summariesList = mRecentsSummariesList.get(section);

            Collections.sort(summariesList, new Comparator<RoomSummary>() {
                @Override
                public int compare(RoomSummary lhs, RoomSummary rhs) {
                    if (lhs == null || lhs.getLatestEvent() == null) {
                        return 1;
                    } else if (rhs == null || rhs.getLatestEvent() == null) {
                        return -1;
                    }

                    if (lhs.getLatestEvent().getOriginServerTs() > rhs.getLatestEvent().getOriginServerTs()) {
                        return -1;
                    } else if (lhs.getLatestEvent().getOriginServerTs() < rhs.getLatestEvent().getOriginServerTs()) {
                        return 1;
                    }
                    return 0;
                }
            });
        }
    }

    /**
     * Provides the formatted timestamp to display.
     * null means that the timestamp text must be hidden.
     * @param event the event.
     * @return  the formatted timestamp to display.
     */
    protected String getFormattedTimestamp(Event event) {
        return event.formattedOriginServerTs();
    }

    @Override
    public View getChildView(int groupPosition, final int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {

        // display a spinner while loading the public rooms
        // detect if the view is progressbar_waiting_room_members one
        View spinner = null;
        if (null != convertView) {
            spinner = convertView.findViewById(R.id.progressbar_waiting_room_members);
        }

        // assume that some public rooms are defined
        if (isPublicsGroupIndex(groupPosition) && (null == mPublicRoomsLists)) {
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

        try {
            // default UI
            // when a room is deleting, the UI is dimmed
            final View deleteProgress = (View) convertView.findViewById(R.id.roomSummaryAdapter_delete_progress);
            deleteProgress.setVisibility(View.GONE);
            convertView.setAlpha(1.0f);

            int textColor = mDefaultTextColor;

            if (isRecentsGroupIndex(groupPosition)) {
                List<RoomSummary> summariesList = (mSearchedPattern.length() > 0) ? mFilteredRecentsSummariesList.get(groupPosition) : mRecentsSummariesList.get(groupPosition);

                // should never happen but in some races conditions, it happened.
                if (0 == summariesList.size()) {
                    return convertView;
                }

                RoomSummary summary = (childPosition < summariesList.size()) ? summariesList.get(childPosition) : summariesList.get(summariesList.size() - 1);
                Integer unreadCount = summary.getUnreadMessagesCount();

                CharSequence message = summary.getRoomTopic();
                String timestamp = null;

                // background color
                if (summary.isHighlighted()) {
                    convertView.setBackgroundColor(mHighlightColor);
                    textColor = mHighlightTextColor;
                } else if ((unreadCount == null) || (unreadCount == 0)) {
                    convertView.setBackgroundColor(0);
                } else {
                    convertView.setBackgroundColor(mUnreadColor);
                    textColor = mUnreadTextColor;
                }

                TextView textView = (TextView) convertView.findViewById(R.id.roomSummaryAdapter_roomName);

                RoomState latestRoomState = summary.getLatestRoomState();
                if (null == latestRoomState) {
                    Room room = roomFromRoomSummary(summary);

                    if ((null != room) && (null != room.getLiveState())) {
                        latestRoomState = room.getLiveState().deepCopy();
                        // store it to avoid retrieving it once
                        summary.setLatestRoomState(latestRoomState);
                    }
                }

                // the public rooms are displayed with bold fonts
                if ((null != latestRoomState) && (null != latestRoomState.visibility) && latestRoomState.visibility.equals(RoomState.VISIBILITY_PUBLIC)) {
                    textView.setTypeface(null, Typeface.BOLD);
                } else {
                    textView.setTypeface(null, Typeface.NORMAL);
                }

                textView.setTextColor(textColor);

                // display the unread messages count
                String roomNameMessage = ((latestRoomState != null) && !summary.isInvited()) ? latestRoomState.getDisplayName(summary.getMatrixId()) : summary.getRoomName();

                if (null != roomNameMessage) {
                    if ((null != unreadCount) && (unreadCount > 0) && !summary.isInvited()) {
                        roomNameMessage += " (" + unreadCount + ")";
                    }
                }

                textView.setText(roomNameMessage);

                if (summary.getLatestEvent() != null) {
                    EventDisplay display = new EventDisplay(mContext, summary.getLatestEvent(), latestRoomState);
                    display.setPrependMessagesWithAuthor(true);
                    message = display.getTextualDisplay();
                    timestamp = getFormattedTimestamp(summary.getLatestEvent());
                }

                // check if this is an invite
                if (summary.isInvited() && (null != summary.getInviterUserId())) {
                    String inviterName = summary.getInviterUserId();
                    String myName = summary.getMatrixId();

                    if (null != latestRoomState) {
                        inviterName = latestRoomState.getMemberName(inviterName);
                        myName = latestRoomState.getMemberName(myName);
                    } else {
                        inviterName = memberDisplayName(summary.getMatrixId(), inviterName);
                        myName = memberDisplayName(summary.getMatrixId(), myName);
                    }

                    message = mContext.getString(R.string.notice_room_invite, inviterName, myName);
                }

                textView = (TextView) convertView.findViewById(R.id.roomSummaryAdapter_message);
                textView.setText(message);
                textView.setTextColor(textColor);
                textView = (TextView) convertView.findViewById(R.id.roomSummaryAdapter_ts);
                textView.setVisibility(View.VISIBLE);
                textView.setText(timestamp);
                textView.setTextColor(textColor);

                Room room = roomFromRoomSummary(summary);

                if ((null != room) && room.isLeaving()) {
                    convertView.setAlpha(0.3f);
                    deleteProgress.setVisibility(View.VISIBLE);
                }
            } else {
                int index = groupPosition - mPublicsGroupStartIndex;
                List<PublicRoom> publicRoomsList = null;

                if (mSearchedPattern.length() > 0) {
                    // add sanity checks
                    // GA issue : could crash while rotating the screen
                    if ((null != mFilteredPublicRoomsList) && (index < mFilteredPublicRoomsList.size())) {
                        publicRoomsList = mFilteredPublicRoomsList.get(index);
                    }
                } else {
                    // add sanity checks
                    // GA issue : could crash while rotating the screen
                    if ((null != mPublicRoomsLists) && (index < mPublicRoomsLists.size())) {
                        publicRoomsList = mPublicRoomsLists.get(index);
                    }
                }

                // sanity checks failed.
                if (null == publicRoomsList) {
                    TextView textView = (TextView) convertView.findViewById(R.id.roomSummaryAdapter_roomName);
                    textView.setTypeface(null, Typeface.BOLD);
                    textView.setTextColor(textColor);
                    textView.setText("");

                    textView = (TextView) convertView.findViewById(R.id.roomSummaryAdapter_message);
                    textView.setTextColor(textColor);
                    textView.setText("");

                    textView = (TextView) convertView.findViewById(R.id.roomSummaryAdapter_ts);
                    textView.setTextColor(textColor);
                    textView.setVisibility(View.VISIBLE);
                    textView.setText("");

                    convertView.setBackgroundColor(0);
                } else {
                    PublicRoom publicRoom = publicRoomsList.get(childPosition);

                    String matrixId = null;

                    if ((mRecentsSummariesList.size() > 0) && (mRecentsSummariesList.get(0).size() > 0)) {
                        matrixId = mRecentsSummariesList.get(0).get(0).getMatrixId();
                    }

                    String displayName = publicRoom.getDisplayName(matrixId);

                    TextView textView = (TextView) convertView.findViewById(R.id.roomSummaryAdapter_roomName);
                    textView.setTypeface(null, Typeface.BOLD);
                    textView.setTextColor(textColor);
                    textView.setText(displayName);

                    textView = (TextView) convertView.findViewById(R.id.roomSummaryAdapter_message);
                    textView.setText(publicRoom.topic);
                    textView.setTextColor(textColor);

                    textView = (TextView) convertView.findViewById(R.id.roomSummaryAdapter_ts);
                    textView.setVisibility(View.VISIBLE);
                    textView.setTextColor(textColor);

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
            }
        } catch (Exception e) {
            // prefer having a weird UI instead of a crash
        }

        return convertView;
    }

    @Override
    public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = mLayoutInflater.inflate(mHeaderLayoutResourceId, null);
        }

        TextView heading = (TextView) convertView.findViewById(R.id.heading);

        if (isRecentsGroupIndex(groupPosition)) {

            int unreadCount = 0;

            Collection<RoomSummary> summaries = mSummaryMapsBySection.get(groupPosition).values();

            for(RoomSummary summary : summaries) {
                unreadCount += summary.getUnreadMessagesCount();
            }

            String header = myRoomsTitle(groupPosition);

            if (unreadCount > 0) {
                header += " ("  + unreadCount + ")";
            }

            heading.setText(header);
        } else {
            heading.setText(publicRoomsTitle(groupPosition));
        }

        heading.setTextColor(mSectionTitleColor);

        ImageView imageView = (ImageView) convertView.findViewById(R.id.heading_image);

        if (isExpanded) {
            imageView.setImageResource(R.drawable.expander_close_holo_light);
        } else {
            imageView.setImageResource(R.drawable.expander_open_holo_light);
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
        if (isRecentsGroupIndex(groupPosition)) {
            ArrayList<ArrayList<RoomSummary>> list = (mSearchedPattern.length() > 0) ? mFilteredRecentsSummariesList : mRecentsSummariesList;

            if ((null == list) || (list.size() <= groupPosition)) {
                return 0;
            } else {
                return list.get(groupPosition).size();
            }
        } else {
            int index = groupPosition - mPublicsGroupStartIndex;

            if (!displayPublicRooms()) {
                return 0;
            }
            // display a spinner until the public rooms are loaded
            //
            else if (null == mPublicRoomsLists) {
                return 1;
            } else if (mPublicRoomsLists.get(index).size() == 0) {
                return 0;
            } else {
                return (mSearchedPattern.length() > 0) ? mFilteredPublicRoomsList.get(index).size() : mPublicRoomsLists.get(index).size();
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

        mPublicsGroupStartIndex = -1;

        count += mRecentsSummariesList.size();

        // display the public rooms in the recents only if there is no dedicated room
        if ((mRecentsSummariesList.size() == 0) || mDisplayAllGroups) {
            mPublicsGroupStartIndex = count;

            if (null == mPublicRoomsLists) {
                count++;
            } else {
                count += mPublicRoomsLists.size();
            }
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
