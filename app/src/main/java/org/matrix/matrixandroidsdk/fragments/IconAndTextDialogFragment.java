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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.IMXStore;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomSummary;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.matrixandroidsdk.ConsoleApplication;
import org.matrix.matrixandroidsdk.Matrix;
import org.matrix.matrixandroidsdk.R;
import org.matrix.matrixandroidsdk.activity.MemberDetailsActivity;
import org.matrix.matrixandroidsdk.adapters.IconAndTextAdapter;
import org.matrix.matrixandroidsdk.adapters.MembersInvitationAdapter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;

/**
 * A dialog fragment showing a list of icon + text entry
 */
public class IconAndTextDialogFragment extends DialogFragment {

    private static final String LOG_TAG = "IconAndTextDialogFragment";

    // params
    public static final String ARG_ICONS_LIST_ID = "org.matrix.matrixandroidsdk.fragments.MembersInvitationDialogFragment.ARG_ICONS_LIST_ID";
    public static final String ARG_TEXTS_LIST_ID = "org.matrix.matrixandroidsdk.fragments.MembersInvitationDialogFragment.ARG_TEXTS_LIST_ID";

    /**
     * Interface definition for a callback to be invoked when an item in this
     * AdapterView has been clicked.
     */
    public interface OnItemClickListener {
        /**
         * Callback method to be invoked when an item is clicked.
         * @param dialogFragment the dialog.
         * @param position The clicked position
         */
        public void onItemClick(IconAndTextDialogFragment dialogFragment, int position);
    }

    private ListView mListView;
    private IconAndTextAdapter mAdapter;

    private ArrayList<Integer> mIconResourcesList;
    private ArrayList<Integer> mTextResourcesList;

    private OnItemClickListener mOnItemClickListener;


    public static IconAndTextDialogFragment newInstance(Integer[] iconResourcesList, Integer[] textResourcesList)  {
        IconAndTextDialogFragment f = new IconAndTextDialogFragment();
        Bundle args = new Bundle();

        args.putIntegerArrayList(ARG_ICONS_LIST_ID,  new ArrayList<Integer>(Arrays.asList(iconResourcesList)));
        args.putIntegerArrayList(ARG_TEXTS_LIST_ID,  new ArrayList<Integer>(Arrays.asList(textResourcesList)));

        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mIconResourcesList = getArguments().getIntegerArrayList(ARG_ICONS_LIST_ID);
        mTextResourcesList = getArguments().getIntegerArrayList(ARG_TEXTS_LIST_ID);
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
     * @param v the dialog view.
     */
    void initView(View v) {
        mListView = ((ListView)v.findViewById(R.id.listView_icon_and_text));
        mAdapter = new IconAndTextAdapter(getActivity(), R.layout.adapter_item_icon_and_text);

        for(int index = 0; index < mIconResourcesList.size(); index++) {
            mAdapter.add(mIconResourcesList.get(index), mTextResourcesList.get(index));
        }

        mListView.setAdapter(mAdapter);
    }

    /**
     * Register a callback to be invoked when this view is clicked.
     *
     */
    public void setOnClickListener(OnItemClickListener l) {
        mOnItemClickListener = l;
    }
}
