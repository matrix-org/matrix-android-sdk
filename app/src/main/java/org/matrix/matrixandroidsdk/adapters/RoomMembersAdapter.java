package org.matrix.matrixandroidsdk.adapters;

import android.content.Context;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import org.matrix.androidsdk.rest.model.PowerLevels;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.matrixandroidsdk.R;
import org.matrix.matrixandroidsdk.view.PieFractionView;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * An adapter which can display m.room.member content.
 */
public class RoomMembersAdapter extends ArrayAdapter<RoomMember> {

    private Context mContext;
    private LayoutInflater mLayoutInflater;
    private int mLayoutResourceId;

    private int mOddColourResId;
    private int mEvenColourResId;

    private PowerLevels mPowerLevels;
    private int maxPowerLevel;

    private HashMap<String, String> mMembershipStrings = new HashMap<String, String>();

    private Map<String, User> mUserMap = new HashMap<String, User>();

    // Comparator to order members alphabetically
    private Comparator<RoomMember> alphaComparator = new Comparator<RoomMember>() {
        @Override
        public int compare(RoomMember member1, RoomMember member2) {
            String lhs = getMemberName(member1);
            String rhs = getMemberName(member2);
            if (lhs == null) {
                return -1;
            }
            else if (rhs == null) {
                return 1;
            }
            if (lhs.startsWith("@")) {
                lhs = lhs.substring(1);
            }
            if (rhs.startsWith("@")) {
                rhs = rhs.substring(1);
            }
            return String.CASE_INSENSITIVE_ORDER.compare(lhs, rhs);
        }
    };

    // Comparator to order members by last active time
    private Comparator<RoomMember> lastActiveComparator = new Comparator<RoomMember>() {
        @Override
        public int compare(RoomMember lhs, RoomMember rhs) {
            User lUser = mUserMap.get(lhs.getUserId());
            User rUser = mUserMap.get(rhs.getUserId());

            // Null cases
            if (lUser == null) {
                if (rUser == null) {
                    // Fall back to alphabetical order
                    return alphaComparator.compare(lhs, rhs);
                }
                return 1;
            }
            if (rUser == null) {
                return -1;
            }

            // Non-null cases
            long lLastActive = lUser.getRealLastActiveAgo();
            long rLastActive = rUser.getRealLastActiveAgo();
            if (lLastActive < rLastActive) return -1;
            if (lLastActive > rLastActive) return 1;

            // Fall back to alphabetical order
            return alphaComparator.compare(lhs, rhs);
        }
    };

    /**
     * Construct an adapter which will display a list of room members.
     * @param context Activity context
     * @param layoutResourceId The resource ID of the layout for each item. Must have TextViews with
     *                         the IDs: roomMembersAdapter_name, roomMembersAdapter_membership, and
     *                         an ImageView with the ID avatar_img.
     */
    public RoomMembersAdapter(Context context, int layoutResourceId) {
        super(context, layoutResourceId);
        mContext = context;
        mLayoutResourceId = layoutResourceId;
        mLayoutInflater = LayoutInflater.from(mContext);
        setNotifyOnChange(true);

        mMembershipStrings.put(RoomMember.MEMBERSHIP_INVITE, context.getString(R.string.membership_invite));
        mMembershipStrings.put(RoomMember.MEMBERSHIP_JOIN, context.getString(R.string.membership_join));
        mMembershipStrings.put(RoomMember.MEMBERSHIP_LEAVE, context.getString(R.string.membership_leave));
        mMembershipStrings.put(RoomMember.MEMBERSHIP_BAN, context.getString(R.string.membership_ban));

    }

    public void sortMembers() {
        sort(lastActiveComparator);
    }

    public void setAlternatingColours(int oddResId, int evenResId) {
        mOddColourResId = oddResId;
        mEvenColourResId = evenResId;
    }

    public void setPowerLevels(PowerLevels powerLevels) {
        mPowerLevels = powerLevels;
        if (powerLevels != null) {
            // Process power levels to find the max. The display will show power levels as a fraction of this
            maxPowerLevel = powerLevels.usersDefault;
            Iterator it = powerLevels.users.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Integer> pair = (Map.Entry<String, Integer>) it.next();
                if (pair.getValue() > maxPowerLevel) maxPowerLevel = pair.getValue();
            }
        }
        notifyDataSetChanged();
    }

    public void saveUser(User user) {
        if (user != null) {
            mUserMap.put(user.userId, user);
        }
        notifyDataSetChanged();
    }

    public String getMemberName(RoomMember member) {
        return getMemberName(member, true);
    }

    public String getMemberName(RoomMember member, boolean withUserId) {
        if (member == null) {
            return null;
        }
        if (!TextUtils.isEmpty(member.displayname)) {
            return withUserId ? member.displayname + "(" + member.getUserId() +")" : member.displayname;
        }
        return member.getUserId();
    }

    public void updateMember(String userId, RoomMember member) {
        for (int i = 0; i < getCount(); i++) {
            RoomMember m = getItem(i);
            if (userId.equals(m.getUserId())) {
                // Copy members
                m.displayname = member.displayname;
                m.avatarUrl = member.avatarUrl;
                m.membership = member.membership;
                notifyDataSetChanged();
                break;
            }
        }
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = mLayoutInflater.inflate(mLayoutResourceId, parent, false);
        }

        RoomMember member = getItem(position);

        User user = mUserMap.get(member.getUserId());

        // Member name and last seen time
        TextView textView = (TextView) convertView.findViewById(R.id.roomMembersAdapter_name);

        if (user == null) {
            textView.setText(member.getName());
        }
        else {
            String memberName = member.getName();
            String lastActiveDisplay = "(" + buildLastActiveDisplay(user.getRealLastActiveAgo()) + ")";

            SpannableStringBuilder ssb = new SpannableStringBuilder(memberName + " " + lastActiveDisplay);
            int lastSeenTextColor = mContext.getResources().getColor(R.color.member_list_last_seen_text);
            ssb.setSpan(new ForegroundColorSpan(lastSeenTextColor), memberName.length(), ssb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            textView.setText(ssb);
        }

        textView = (TextView) convertView.findViewById(R.id.roomMembersAdapter_membership);
        textView.setText(mMembershipStrings.get(member.membership));
        textView = (TextView) convertView.findViewById(R.id.roomMembersAdapter_userId);
        textView.setText(member.getUserId());

        ImageView imageView = (ImageView) convertView.findViewById(R.id.avatar_img);
        imageView.setTag(null);
        imageView.setImageResource(R.drawable.ic_contact_picture_holo_light);
        String url = member.avatarUrl;
        if (!TextUtils.isEmpty(url)) {
            int size = getContext().getResources().getDimensionPixelSize(R.dimen.member_list_avatar_size);
            AdapterUtils.loadThumbnailBitmap(imageView, url, size, size);
        }

        // The presence ring
        ImageView presenceRing = (ImageView) convertView.findViewById(R.id.imageView_presenceRing);
        presenceRing.setColorFilter(mContext.getResources().getColor(android.R.color.transparent));
        if (user != null) {
            if (User.PRESENCE_ONLINE.equals(user.presence)) {
                presenceRing.setColorFilter(mContext.getResources().getColor(R.color.presence_online));
            } else if (User.PRESENCE_UNAVAILABLE.equals(user.presence)) {
                presenceRing.setColorFilter(mContext.getResources().getColor(R.color.presence_unavailable));
            }
        }

        // The power level disc
        PieFractionView pieFractionView = (PieFractionView) convertView.findViewById(R.id.powerDisc);
        if (mPowerLevels == null) {
            pieFractionView.setVisibility(View.GONE);
        }
        else {
            int powerLevel = mPowerLevels.getUserPowerLevel(member.getUserId());
            pieFractionView.setVisibility((powerLevel == 0) ? View.GONE : View.VISIBLE);
            pieFractionView.setFraction(powerLevel * 100 / maxPowerLevel);
        }

        if (mOddColourResId != 0 && mEvenColourResId != 0) {
            convertView.setBackgroundColor(position % 2 == 0 ? mEvenColourResId : mOddColourResId);
        }

        return convertView;

    }

    private String buildLastActiveDisplay(long lastActiveAgo) {
        lastActiveAgo /= 1000; // In seconds
        if (lastActiveAgo < 60) {
            return mContext.getString(R.string.last_seen_secs, lastActiveAgo);
        }

        lastActiveAgo /= 60; // In minutes
        if (lastActiveAgo < 60) {
            return mContext.getString(R.string.last_seen_mins, lastActiveAgo);
        }

        lastActiveAgo /= 60; // In hours
        if (lastActiveAgo < 24) {
            return mContext.getString(R.string.last_seen_hours, lastActiveAgo);
        }

        lastActiveAgo /= 24; // In days
        return mContext.getString(R.string.last_seen_days, lastActiveAgo);
    }
}
