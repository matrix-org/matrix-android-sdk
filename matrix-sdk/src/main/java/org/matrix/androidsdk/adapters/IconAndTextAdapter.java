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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import org.matrix.androidsdk.R;

/**
 * An adapter which can display m.room.member content.
 */
public class IconAndTextAdapter extends ArrayAdapter<IconAndTextAdapter.Entry> {

    private final Context mContext;
    private final LayoutInflater mLayoutInflater;
    private final int mLayoutResourceId;
    private Integer mBackgroundColor = null;
    private Integer mTextColor = null;

    protected class Entry {
        protected final Integer mIconResId;
        protected final Integer mTextResId;

        protected Entry(Integer iconResId, Integer textResId) {
            mIconResId = iconResId;
            mTextResId = textResId;
        }
    }

    /**
     * Construct an adapter which will display a list of entries
     *
     * @param context          Activity context
     * @param layoutResourceId The resource ID of the layout for each item. Must have TextViews with
     *                         the IDs: roomMembersAdapter_name, roomMembersAdapter_membership, and
     *                         an ImageView with the ID avatar_img.
     */
    public IconAndTextAdapter(Context context, int layoutResourceId) {
        super(context, layoutResourceId);
        mContext = context;
        mLayoutResourceId = layoutResourceId;
        mLayoutInflater = LayoutInflater.from(mContext);
    }

    /**
     * Add a new entry in the adapter
     *
     * @param iconResourceId the entry icon identifier
     * @param textResourceId the entry text resourceId
     */
    public void add(int iconResourceId, int textResourceId) {
        this.add(new Entry(iconResourceId, textResourceId));
    }

    /**
     * Set the background color of the cell.
     *
     * @param color the background color.
     */
    public void setBackgroundColor(Integer color) {
        mBackgroundColor = color;
    }

    /**
     * Set the cell text color.
     *
     * @param color the text color.
     */
    public void setTextColor(Integer color) {
        mTextColor = color;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = mLayoutInflater.inflate(mLayoutResourceId, parent, false);
        }

        Entry entry = getItem(position);

        // text value
        TextView textView = convertView.findViewById(R.id.textView_icon_and_text);
        textView.setText(mContext.getString(entry.mTextResId));

        if (null != mTextColor) {
            textView.setTextColor(mTextColor);
        }

        ImageView imageView = convertView.findViewById(R.id.imageView_icon_and_text);
        imageView.setImageResource(entry.mIconResId);

        if (null != mBackgroundColor) {
            convertView.setBackgroundColor(mBackgroundColor);
        }

        return convertView;
    }
}
