package org.matrix.matrixandroidsdk.adapters;

import android.content.Context;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.gson.JsonPrimitive;

import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.Message;
import org.matrix.matrixandroidsdk.R;

/**
 * An adapter which can display events. Events are not limited to m.room.message event types, but
 * can include topic changes (m.room.topic) and room member changes (m.room.member).
 */
public class MessagesAdapter extends ArrayAdapter<Event> {

    private static final int NUM_ROW_TYPES = 2; // text and images.
    private static final int ROW_TYPE_TEXT = 0;
    private static final int ROW_TYPE_IMAGE = 1;

    private static final String LOG_TAG = "MessagesAdapter";

    private Context mContext;
    private int mTextLayoutId;
    private int mImageLayoutId;
    private LayoutInflater mLayoutInflater;

    private int mOddColourResId;
    private int mEvenColourResId;

    public MessagesAdapter(Context context, int textResLayoutId, int imageResLayoutId) {
        super(context, 0);
        mContext = context;
        mTextLayoutId = textResLayoutId;
        mImageLayoutId = imageResLayoutId;
        mLayoutInflater = LayoutInflater.from(mContext);
        setNotifyOnChange(true);
    }

    public void setAlternatingColours(int oddResId, int evenResId) {
        mOddColourResId = oddResId;
        mEvenColourResId = evenResId;
    }

    @Override
    public int getViewTypeCount() {
        return NUM_ROW_TYPES;
    }

    public void addToFront(Event event) {
        if (isKnownEvent(event)) {
            this.insert(event, 0);
        }
    }

    @Override
    public void add(Event event) {
        if (isKnownEvent(event)) {
            super.add(event);
        }
    }

    @Override
    public int getItemViewType(int position) {
        Event event = getItem(position);
        String msgType = event.content.getAsJsonPrimitive("msgtype").getAsString();

        if (msgType.equals(Message.MSGTYPE_TEXT)) {
            return ROW_TYPE_TEXT;
        }
        else if (msgType.equals(Message.MSGTYPE_IMAGE)) {
            return ROW_TYPE_IMAGE;
        }
        else {
            throw new RuntimeException("Unknown msgtype: "+msgType);
        }
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        switch (getItemViewType(position)) {
            case ROW_TYPE_TEXT:
                return getTextView(position, convertView, parent);
            case ROW_TYPE_IMAGE:
                return getImageView(position, convertView, parent);
            default:
                throw new RuntimeException("Unknown item view type for position " + position);
        }
    }


    private View getTextView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = mLayoutInflater.inflate(mTextLayoutId, parent, false);
        }

        Event msg = getItem(position);
        String body = msg.content.get("body") == null ? null : msg.content.get("body").getAsString();


        TextView textView = (TextView) convertView.findViewById(R.id.messagesAdapter_body);
        textView.setText(body);

        // check for html formatting
        if (msg.content.has("formatted_body") && msg.content.has("format")) {
            try {
                String format = msg.content.getAsJsonPrimitive("format").getAsString();
                if ("org.matrix.custom.html".equals(format)) {
                    textView.setText(Html.fromHtml(msg.content.getAsJsonPrimitive("formatted_body").getAsString()));
                }
            }
            catch (Exception e) {
                // ignore: The json object was probably malformed and we have already set the fallback
            }
        }

        textView = (TextView) convertView.findViewById(R.id.messagesAdapter_sender);
        textView.setText(msg.userId);

        setBackgroundColour(convertView, position);
        return convertView;

    }

    private View getImageView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = mLayoutInflater.inflate(mImageLayoutId, parent, false);
        }
        Event msg = getItem(position);
        String thumbUrl = msg.content.get("thumbnail_url") == null ? null : msg.content.get("thumbnail_url").getAsString();

        ImageView imageView = (ImageView) convertView.findViewById(R.id.messagesAdapter_image);
        AdapterUtils.loadBitmap(imageView, thumbUrl);

        TextView textView = (TextView) convertView.findViewById(R.id.messagesAdapter_sender);
        textView.setText(msg.userId);
        setBackgroundColour(convertView, position);
        return convertView;
    }

    private void setBackgroundColour(View view, int position) {
        if (mOddColourResId != 0 && mEvenColourResId != 0) {
            view.setBackgroundColor(position%2 == 0 ? mEvenColourResId : mOddColourResId);
        }
    }

    private boolean isKnownEvent(Event event) {
        if (Event.EVENT_TYPE_MESSAGE.equals(event.type)) {
            JsonPrimitive j = event.content.getAsJsonPrimitive("msgtype");
            String msgType = j == null ? null : j.getAsString();
            if (Message.MSGTYPE_IMAGE.equals(msgType) || Message.MSGTYPE_TEXT.equals(msgType)) {
                return true;
            }
        }
        return false;
    }


}
