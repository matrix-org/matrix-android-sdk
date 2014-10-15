package org.matrix.matrixandroidsdk.adapters;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import org.matrix.androidsdk.api.response.Event;
import org.matrix.matrixandroidsdk.R;

public class MessagesAdapter extends ArrayAdapter<Event> {

    private Context mContext;
    private int mLayoutId;
    private LayoutInflater mLayoutInflater;

    private int mOddColourResId;
    private int mEvenColourResId;

    public MessagesAdapter(Context context, int resLayoutId) {
        super(context, resLayoutId);
        mContext = context;
        mLayoutId = resLayoutId;
        mLayoutInflater = LayoutInflater.from(mContext);
        setNotifyOnChange(true);
    }

    public void setAlternatingColours(int oddResId, int evenResId) {
        mOddColourResId = oddResId;
        mEvenColourResId = evenResId;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = mLayoutInflater.inflate(mLayoutId, parent, false);
        }

        Event msg = getItem(position);

        TextView textView = (TextView) convertView.findViewById(R.id.messagesAdapter_body);
        textView.setText(msg.content.get("body").getAsString());
        textView = (TextView) convertView.findViewById(R.id.messagesAdapter_sender);
        textView.setText(msg.userId);

        if (mOddColourResId != 0 && mEvenColourResId != 0) {
            convertView.setBackgroundColor(position%2 == 0 ? mEvenColourResId : mOddColourResId);
        }

        return convertView;

    }
}
