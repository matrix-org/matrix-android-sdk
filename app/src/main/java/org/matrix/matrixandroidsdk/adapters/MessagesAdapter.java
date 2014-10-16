package org.matrix.matrixandroidsdk.adapters;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.gson.JsonPrimitive;

import org.matrix.androidsdk.api.response.Event;
import org.matrix.androidsdk.api.response.Message;
import org.matrix.matrixandroidsdk.R;

import java.lang.ref.WeakReference;
import java.net.URL;

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
        loadBitmap(imageView, thumbUrl);

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

    private void loadBitmap(ImageView imageView, String url) {
        imageView.setTag(url);
        BitmapWorkerTask task = new BitmapWorkerTask(imageView, url);
        task.execute();
    }

    class BitmapWorkerTask extends AsyncTask<Integer, Void, Bitmap> {
        private final WeakReference<ImageView> mImageViewReference;
        private String mUrl;

        public BitmapWorkerTask(ImageView imageView, String url) {
            mImageViewReference = new WeakReference<ImageView>(imageView);
            mUrl = url;
        }

        // Decode image in background.
        @Override
        protected Bitmap doInBackground(Integer... params) {
            try {
                URL url = new URL(mUrl);
                Log.e(LOG_TAG, "open >>>>> "+mUrl);
                return BitmapFactory.decodeStream(url.openConnection().getInputStream());
            }
            catch (Exception e) {
                Log.e(LOG_TAG, "Unable to load bitmap: "+e);
                return null;
            }
        }

        // Once complete, see if ImageView is still around and set bitmap.
        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (mImageViewReference != null && bitmap != null) {
                final ImageView imageView = mImageViewReference.get();
                if (imageView != null && mUrl.equals(imageView.getTag())) {
                    imageView.setImageBitmap(bitmap);
                }
            }
        }
    }
}
