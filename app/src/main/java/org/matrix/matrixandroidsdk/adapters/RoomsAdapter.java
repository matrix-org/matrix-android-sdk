package org.matrix.matrixandroidsdk.adapters;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import org.matrix.androidsdk.data.RoomState;
import org.matrix.matrixandroidsdk.R;

import java.util.Comparator;

/**
 * An adapter which can display room information.
 */
public class RoomsAdapter extends ArrayAdapter<RoomState> {

    private Context mContext;
    private LayoutInflater mLayoutInflater;
    private int mLayoutResourceId;

    private int mOddColourResId;
    private int mEvenColourResId;

    /**
     * Construct an adapter which will display a list of rooms.
     * @param context Activity context
     * @param layoutResourceId The resource ID of the layout for each item. Must have TextViews with
     *                         the IDs: roomsAdapter_roomName, roomsAdapter_roomTopic
     */
    public RoomsAdapter(Context context, int layoutResourceId) {
        super(context, layoutResourceId);
        mContext = context;
        mLayoutResourceId = layoutResourceId;
        mLayoutInflater = LayoutInflater.from(mContext);
        setNotifyOnChange(true);
    }

    public void sortRooms() {
        this.sort(new Comparator<RoomState>() {
            @Override
            public int compare(RoomState roomState, RoomState roomState2) {
                String lhs = getRoomName(roomState);
                String rhs = getRoomName(roomState2);
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

    public void setAlternatingColours(int oddResId, int evenResId) {
        mOddColourResId = oddResId;
        mEvenColourResId = evenResId;
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
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = mLayoutInflater.inflate(mLayoutResourceId, parent, false);
        }

        RoomState room = getItem(position);

        TextView textView = (TextView) convertView.findViewById(R.id.roomsAdapter_roomTopic);
        textView.setText(room.topic);
        textView = (TextView) convertView.findViewById(R.id.roomsAdapter_roomName);
        textView.setText(getRoomName(room));


        if (mOddColourResId != 0 && mEvenColourResId != 0) {
            convertView.setBackgroundColor(position%2 == 0 ? mEvenColourResId : mOddColourResId);
        }

        return convertView;

    }
}
