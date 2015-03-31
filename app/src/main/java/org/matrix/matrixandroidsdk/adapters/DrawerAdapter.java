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

package org.matrix.matrixandroidsdk.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import org.matrix.matrixandroidsdk.R;

/**
 * An adapter which can display room information.
 */
public class DrawerAdapter extends ArrayAdapter<DrawerAdapter.Entry> {
    public class Entry {
        public int mIconResourceId;
        public String mText;

        public Entry(int iconResourceId, String text) {
            mIconResourceId = iconResourceId;
            mText = text;
        }
    };

    private LayoutInflater mLayoutInflater;
    private int mLayoutResourceId;

    private Context mContext;

    /**
     * Construct an adapter which will display a list of rooms.
     * @param context Activity context
     * @param layoutResourceId The resource ID of the layout for each item. Must have TextViews with
     *                         the IDs: roomsAdapter_roomName, roomsAdapter_roomTopic
     */
    public DrawerAdapter(Context context, int layoutResourceId) {
        super(context, layoutResourceId);
        mContext = context;
        mLayoutResourceId = layoutResourceId;
        mLayoutInflater = LayoutInflater.from(mContext);
        setNotifyOnChange(true);
    }

    /**
     * Add a new entry in the adapter
     * @param iconResourceId the entry icon
     * @param text the entry text
     */
    public void add(int iconResourceId, String text) {
        this.add(new Entry(iconResourceId, text));
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = mLayoutInflater.inflate(mLayoutResourceId, parent, false);
        }

        Entry entry = getItem(position);

        TextView textView = (TextView) convertView.findViewById(R.id.adapter_drawer_text);
        textView.setText(entry.mText);

        ImageView imageView = (ImageView) convertView.findViewById(R.id.adapter_drawer_thumbnail);
        imageView.setImageResource(entry.mIconResourceId);

        return convertView;
    }
}
