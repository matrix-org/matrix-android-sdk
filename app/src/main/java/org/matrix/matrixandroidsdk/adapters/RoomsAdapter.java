package org.matrix.matrixandroidsdk.adapters;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import org.matrix.androidsdk.data.IRoom;
import org.matrix.matrixandroidsdk.R;

public class RoomsAdapter extends ArrayAdapter<IRoom> {

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

    public void setAlternatingColours(int oddResId, int evenResId) {
        mOddColourResId = oddResId;
        mEvenColourResId = evenResId;
    }

    public void addIfNotExist(IRoom room) {
        for (int i=0; i<getCount(); ++i) {
            IRoom r = this.getItem(i);
            if (r.getRoomId().equals(room.getRoomId())) {
                return;
            }
        }
        add(room);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = mLayoutInflater.inflate(mLayoutResourceId, parent, false);
        }

        IRoom room = getItem(position);

        TextView textView = (TextView) convertView.findViewById(R.id.roomsAdapter_roomTopic);
        textView.setText(room.getTopic());
        textView = (TextView) convertView.findViewById(R.id.roomsAdapter_roomName);
        textView.setText(room.getName());

        if (mOddColourResId != 0 && mEvenColourResId != 0) {
            convertView.setBackgroundColor(position%2 == 0 ? mEvenColourResId : mOddColourResId);
        }

        return convertView;

    }
}
