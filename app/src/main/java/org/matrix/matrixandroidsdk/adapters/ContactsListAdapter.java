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
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import org.matrix.matrixandroidsdk.R;
import org.matrix.matrixandroidsdk.contacts.Contact;

/**
 * An adapter which can display m.room.member content.
 */
public class ContactsListAdapter extends ArrayAdapter<Contact> {

    private Context mContext;
    private LayoutInflater mLayoutInflater;
    private int mLayoutResourceId;

    /**
     * Construct an adapter which will display a list of room members.
     * @param context Activity context
     * @param layoutResourceId The resource ID of the layout for each item. Must have TextViews with
     *                         the IDs: roomMembersAdapter_name, roomMembersAdapter_membership, and
     *                         an ImageView with the ID avatar_img.
     */
    public ContactsListAdapter(Context context, int layoutResourceId) {
        super(context, layoutResourceId);
        mContext = context;
        mLayoutResourceId = layoutResourceId;
        mLayoutInflater = LayoutInflater.from(mContext);

        // let the caller manages the refresh
        setNotifyOnChange(false);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        if (convertView == null) {
            convertView = mLayoutInflater.inflate(mLayoutResourceId, parent, false);
        }

        final Contact contact = getItem(position);

        // Member name
        TextView textView = (TextView) convertView.findViewById(R.id.contact_name);
        textView.setText(contact.mDisplayName);

        // matrix info
        ImageView matrixIcon = (ImageView) convertView.findViewById(R.id.imageView_matrix_user);
        TextView matrixIDTextView = (TextView) convertView.findViewById(R.id.contact_userId);

        if (contact.hasMatridIds(mContext)) {
            matrixIDTextView.setText(contact.getFirstMatrixId());
            matrixIDTextView.setVisibility(View.VISIBLE);
            matrixIcon.setVisibility(View.VISIBLE);
        } else {
            matrixIDTextView.setVisibility(View.GONE);
            matrixIcon.setVisibility(View.GONE);
        }

        // member thumbnail
        ImageView imageView = (ImageView) convertView.findViewById(R.id.avatar_img);

        Bitmap bitmap = contact.mThumbnail;

        if ((null != contact.mThumbnailUri) && (null == bitmap)) {
            try {
                contact.mThumbnail = bitmap = MediaStore.Images.Media.getBitmap(mContext.getContentResolver(), Uri.parse(contact.mThumbnailUri));
            } catch (Exception e) {
            }
        }

        if (null == bitmap) {
            imageView.setImageResource(R.drawable.ic_contact_picture_holo_light);
        } else {
            imageView.setImageBitmap(bitmap);
        }

        return convertView;
    }
}
