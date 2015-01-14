package org.matrix.matrixandroidsdk.adapters;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.matrixandroidsdk.Matrix;
import org.matrix.matrixandroidsdk.R;

import java.util.Comparator;
import java.util.HashMap;

/**
 * An adapter which can display m.room.member content.
 */
public class RoomMembersAdapter extends ArrayAdapter<RoomMember> {

    private Context mContext;
    private LayoutInflater mLayoutInflater;
    private int mLayoutResourceId;

    private int mOddColourResId;
    private int mEvenColourResId;

    private HashMap<String, String> mMembershipStrings = new HashMap<String, String>();

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
        this.sort(new Comparator<RoomMember>() {
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
        });
    }

    public void setAlternatingColours(int oddResId, int evenResId) {
        mOddColourResId = oddResId;
        mEvenColourResId = evenResId;
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

        TextView textView = (TextView) convertView.findViewById(R.id.roomMembersAdapter_name);
        textView.setText(member.getName());
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

        User user = Matrix.getInstance(mContext).getDefaultSession().getDataHandler().getStore().getUser(member.getUserId());

        // The presence ring
        ImageView presenceRing = (ImageView) convertView.findViewById(R.id.imageView_presenceRing);
        if (User.PRESENCE_ONLINE.equals(user.presence)) {
            presenceRing.setColorFilter(mContext.getResources().getColor(R.color.presence_online));
        }
        else if (User.PRESENCE_UNAVAILABLE.equals(user.presence)) {
            presenceRing.setColorFilter(mContext.getResources().getColor(R.color.presence_unavailable));
        }
        else {
            presenceRing.setColorFilter(mContext.getResources().getColor(R.color.presence_unknown));
        }

        if (mOddColourResId != 0 && mEvenColourResId != 0) {
            convertView.setBackgroundColor(position % 2 == 0 ? mEvenColourResId : mOddColourResId);
        }

        return convertView;

    }
}
