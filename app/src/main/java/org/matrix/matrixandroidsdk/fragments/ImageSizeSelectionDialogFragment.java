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

package org.matrix.matrixandroidsdk.fragments;

import android.app.Dialog;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import org.matrix.matrixandroidsdk.R;
import org.matrix.matrixandroidsdk.adapters.ImageSizesAdapter;

import java.util.Collection;

/**
 * A dialog fragment showing a list of image selections string
 */
public class ImageSizeSelectionDialogFragment  extends DialogFragment {
    private static final String LOG_TAG = "ImageSizeSelectionDialogFragment";

    public static interface ImageSizeListener {
        public void onSelected(int pos);
    }

    public static ImageSizeSelectionDialogFragment newInstance(Collection<String> entries) {
        ImageSizeSelectionDialogFragment f= new ImageSizeSelectionDialogFragment();
        Bundle args = new Bundle();
        f.setArguments(args);
        f.setEntries(entries);
        return f;
    }

    private ListView mListView;
    private ImageSizesAdapter mAdapter;
    private Collection<String>  mEntries = null;
    private ImageSizeListener mListener = null;

    public void setEntries(Collection<String> entries) {
        mEntries = entries;
    }

    public void setListener(ImageSizeListener listener) {
        mListener = listener;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog d = super.onCreateDialog(savedInstanceState);
        d.setTitle(getString(R.string.compression_options));
        return d;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View v = inflater.inflate(R.layout.fragment_dialog_accounts_list, container, false);
        mListView = ((ListView)v.findViewById(R.id.listView_accounts));

        mAdapter = new ImageSizesAdapter(getActivity(), R.layout.adapter_item_image_size);
        mAdapter.addAll(mEntries);
        mListView.setAdapter(mAdapter);

        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                if (null != mListener) {
                    mListener.onSelected(position);
                }

                // dismiss the list
                ImageSizeSelectionDialogFragment.this.dismiss();
            }
        });

        return v;
    }
}
