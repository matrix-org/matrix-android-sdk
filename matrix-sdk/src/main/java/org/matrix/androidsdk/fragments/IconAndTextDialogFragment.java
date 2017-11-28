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

package org.matrix.androidsdk.fragments;

import android.app.AlertDialog;
import android.app.Dialog;
import android.support.v4.app.DialogFragment;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import org.matrix.androidsdk.R;
import org.matrix.androidsdk.adapters.IconAndTextAdapter;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * A dialog fragment showing a list of icon + text entry
 */
public class IconAndTextDialogFragment extends DialogFragment {

    private static final String LOG_TAG = IconAndTextDialogFragment.class.getSimpleName();

    // params
    public static final String ARG_ICONS_LIST_ID = "org.matrix.androidsdk.fragments.IconAndTextDialogFragment.ARG_ICONS_LIST_ID";
    public static final String ARG_TEXTS_LIST_ID = "org.matrix.androidsdk.fragments.IconAndTextDialogFragment.ARG_TEXTS_LIST_ID";
    public static final String ARG_BACKGROUND_COLOR = "org.matrix.androidsdk.fragments.IconAndTextDialogFragment.ARG_BACKGROUND_COLOR";
    public static final String ARG_TEXT_COLOR = "org.matrix.androidsdk.fragments.IconAndTextDialogFragment.ARG_TEXT_COLOR";

    /**
     * Interface definition for a callback to be invoked when an item in this
     * AdapterView has been clicked.
     */
    public interface OnItemClickListener {
        /**
         * Callback method to be invoked when an item is clicked.
         *
         * @param dialogFragment the dialog.
         * @param position       The clicked position
         */
        void onItemClick(IconAndTextDialogFragment dialogFragment, int position);
    }

    private ListView mListView;

    private ArrayList<Integer> mIconResourcesList;
    private ArrayList<Integer> mTextResourcesList;
    private Integer mBackgroundColor = null;
    private Integer mTextColor = null;

    private OnItemClickListener mOnItemClickListener;


    public static IconAndTextDialogFragment newInstance(Integer[] iconResourcesList, Integer[] textResourcesList) {
        return IconAndTextDialogFragment.newInstance(iconResourcesList, textResourcesList, null, null);
    }

    public static IconAndTextDialogFragment newInstance(Integer[] iconResourcesList, Integer[] textResourcesList, Integer backgroundColor, Integer textColor) {
        IconAndTextDialogFragment f = new IconAndTextDialogFragment();
        Bundle args = new Bundle();

        args.putIntegerArrayList(ARG_ICONS_LIST_ID, new ArrayList<>(Arrays.asList(iconResourcesList)));
        args.putIntegerArrayList(ARG_TEXTS_LIST_ID, new ArrayList<>(Arrays.asList(textResourcesList)));

        if (null != backgroundColor) {
            args.putInt(ARG_BACKGROUND_COLOR, backgroundColor);
        }

        if (null != textColor) {
            args.putInt(ARG_TEXT_COLOR, textColor);
        }

        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mIconResourcesList = getArguments().getIntegerArrayList(ARG_ICONS_LIST_ID);
        mTextResourcesList = getArguments().getIntegerArrayList(ARG_TEXTS_LIST_ID);

        if (getArguments().containsKey(ARG_BACKGROUND_COLOR)) {
            mBackgroundColor = getArguments().getInt(ARG_BACKGROUND_COLOR);
        }

        if (getArguments().containsKey(ARG_TEXT_COLOR)) {
            mTextColor = getArguments().getInt(ARG_TEXT_COLOR);
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        View view = getActivity().getLayoutInflater().inflate(R.layout.fragment_dialog_icon_text_list, null);
        builder.setView(view);
        initView(view);

        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (null != mOnItemClickListener) {
                    mOnItemClickListener.onItemClick(IconAndTextDialogFragment.this, position);
                }

                IconAndTextDialogFragment.this.dismiss();
            }
        });


        return builder.create();
    }

    /**
     * Init the dialog view.
     *
     * @param v the dialog view.
     */
    void initView(View v) {
        mListView = v.findViewById(R.id.listView_icon_and_text);
        IconAndTextAdapter adapter = new IconAndTextAdapter(getActivity(), R.layout.adapter_item_icon_and_text);

        for (int index = 0; index < mIconResourcesList.size(); index++) {
            adapter.add(mIconResourcesList.get(index), mTextResourcesList.get(index));
        }

        if (null != mBackgroundColor) {
            mListView.setBackgroundColor(mBackgroundColor);
            adapter.setBackgroundColor(mBackgroundColor);
        }

        if (null != mTextColor) {
            adapter.setTextColor(mTextColor);
        }

        mListView.setAdapter(adapter);
    }

    /**
     * Register a callback to be invoked when this view is clicked.
     *
     * @param l the listener
     */
    public void setOnClickListener(OnItemClickListener l) {
        mOnItemClickListener = l;
    }
}
