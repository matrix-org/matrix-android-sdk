package org.matrix.matrixandroidsdk.adapters;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseExpandableListAdapter;
import android.widget.CheckedTextView;
import android.widget.ExpandableListView;
import android.widget.TextView;

import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.data.RoomSummary;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.PublicRoom;
import org.matrix.matrixandroidsdk.R;
import org.matrix.matrixandroidsdk.activity.HomeActivity;
import org.matrix.matrixandroidsdk.activity.RoomActivity;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;

public class RoomSummaryAdapter extends BaseExpandableListAdapter {

    private Context mContext;
    private LayoutInflater mLayoutInflater;
    private int mLayoutResourceId;

    private int mOddColourResId;
    private int mEvenColourResId;
    private int mUnreadColor;

    private List<RoomSummary>mRecentsSummariesList;
    private List<PublicRoom>mPublicRoomsList;

    private List<RoomSummary>mFilteredRecentsSummariesList;
    private List<PublicRoom>mFilteredPublicRoomsList;

    private String mSearchedPattern = "";

    private DateFormat mDateFormat;

    private Map<String, Integer> mUnreadCountMap = new HashMap<String, Integer>();

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
        return mPublicRoomsList.get(index);
    }
    /**
     * recents list management
     */

    public void addRoomSummary(RoomSummary roomSummary) {
        mRecentsSummariesList.add(roomSummary);
    }

    public RoomSummary getRoomSummaryAt(int index) {
        return mRecentsSummariesList.get(index);
    }

    public void removeRoomSummary(RoomSummary roomSummary) {
        mRecentsSummariesList.remove(roomSummary);
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

    public void resetUnreadCount(String roomId) {
        mUnreadCountMap.put(roomId, 0);
    }


    public void setAlternatingColours(int oddResId, int evenResId) {
        mOddColourResId = oddResId;
        mEvenColourResId = evenResId;
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
                String lhs = getRoomName(publicRoom);
                String rhs = getRoomName(publicRoom2);
                if (lhs == null) {
                    return -1;
                }
                else if (rhs == null) {
                    return 1;
                }
                if (lhs.startsWith("#")) {
                    lhs = lhs.substring(1);
                }
                if (rhs.startsWith("#")) {
                    rhs = rhs.substring(1);
                }
                return String.CASE_INSENSITIVE_ORDER.compare(lhs, rhs);

            }
        });
    }

    public String getRoomName(RoomState room) {
        if (room == null) {
            return null;
        }
        if (!TextUtils.isEmpty(room.name)) {
            return room.name;
        }
        else if (!TextUtils.isEmpty(room.roomAliasName)) {
            return room.roomAliasName;
        }
        else if (room.aliases != null && room.aliases.size() > 0) {
            return room.aliases.get(0);
        }
        return room.roomId;
    }

    @Override
    public View getChildView(int groupPosition, final int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = mLayoutInflater.inflate(mLayoutResourceId, parent, false);
        }

        if (groupPosition == HomeActivity.recentsGroupIndex) {

            List<RoomSummary> summariesList = (mSearchedPattern.length() > 0) ? mFilteredRecentsSummariesList : mRecentsSummariesList;

            RoomSummary summary = summariesList.get(childPosition);

            Integer unreadCount = mUnreadCountMap.get(summary.getRoomId());
            // Zero for transparent
            convertView.setBackgroundColor(((unreadCount == null) || (unreadCount == 0)) ? 0 : mUnreadColor);

            String numMembers = null;
            CharSequence message = summary.getRoomTopic();
            String timestamp = null;

            TextView textView = (TextView) convertView.findViewById(R.id.roomSummaryAdapter_roomName);
            textView.setText(summary.getRoomName());

            if (summary.getNumMembers() > 0) {
                numMembers = mContext.getResources().getQuantityString(
                        R.plurals.num_members, summary.getNumMembers(),
                        summary.getNumMembers()
                );
            }

            if (summary.getLatestEvent() != null) {
                AdapterUtils.EventDisplay display = new AdapterUtils.EventDisplay(mContext, summary.getLatestEvent(), summary.getLatestRoomState());
                display.setPrependMessagesWithAuthor(true);
                message = display.getTextualDisplay();

                timestamp = mDateFormat.format(new Date(summary.getLatestEvent().originServerTs));
            }

            // check if this is an invite
            if (summary.isInvited()) {
                message = summary.getInviterUserId() + "'s invitation";
            }

            textView = (TextView) convertView.findViewById(R.id.roomSummaryAdapter_message);
            textView.setText(message);
            textView = (TextView) convertView.findViewById(R.id.roomSummaryAdapter_ts);
            textView.setVisibility(View.VISIBLE);
            textView.setText(timestamp);
            textView = (TextView) convertView.findViewById(R.id.roomSummaryAdapter_numUsers);
            textView.setVisibility(View.VISIBLE);
            textView.setText(numMembers);

            if (mOddColourResId != 0 && mEvenColourResId != 0) {
                convertView.setBackgroundColor(childPosition % 2 == 0 ? mEvenColourResId : mOddColourResId);
            }

        } else {
            List<PublicRoom> publicRoomsList = (mSearchedPattern.length() > 0) ? mFilteredPublicRoomsList : mPublicRoomsList;

            RoomState room = publicRoomsList.get(childPosition);

            TextView textView = (TextView) convertView.findViewById(R.id.roomSummaryAdapter_roomName);
            textView.setText(getRoomName(room));

            textView = (TextView) convertView.findViewById(R.id.roomSummaryAdapter_message);
            textView.setText(room.topic);

            textView = (TextView) convertView.findViewById(R.id.roomSummaryAdapter_ts);
            textView.setVisibility(View.GONE);

            textView = (TextView) convertView.findViewById(R.id.roomSummaryAdapter_numUsers);
            textView.setVisibility(View.GONE);

            convertView.setBackgroundColor(0);
        }

        return convertView;
    }

    @Override
    public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = mLayoutInflater.inflate(R.layout.adapter_room_section_header, null);
        }

        TextView heading = (TextView) convertView.findViewById(R.id.heading);

        if (groupPosition == HomeActivity.recentsGroupIndex) {
            heading.setText(mContext.getResources().getString(R.string.my_rooms));
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
        if (groupPosition == HomeActivity.recentsGroupIndex) {
            return (mSearchedPattern.length() > 0) ? mFilteredRecentsSummariesList.size() : mRecentsSummariesList.size();
        } else {
            return (mSearchedPattern.length() > 0) ? mFilteredPublicRoomsList.size() : mPublicRoomsList.size();
        }
    }

    @Override
    public Object getGroup(int groupPosition) {
        return null;
    }

    @Override
    public int getGroupCount() {
        return 2;
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
