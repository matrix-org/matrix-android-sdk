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

import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.view.MotionEvent;
import android.view.View;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.adapters.MessageRow;
import org.matrix.androidsdk.adapters.MessagesAdapter;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.fragments.IconAndTextDialogFragment;
import org.matrix.androidsdk.fragments.MatrixMessageListFragment;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.matrixandroidsdk.Matrix;
import org.matrix.matrixandroidsdk.R;
import org.matrix.matrixandroidsdk.activity.CommonActivityUtils;
import org.matrix.matrixandroidsdk.activity.MXCActionBarActivity;
import org.matrix.matrixandroidsdk.adapters.ConsoleMessagesAdapter;

import java.util.ArrayList;
import java.util.List;

public class ConsoleMessageListFragment extends MatrixMessageListFragment {

    public static ConsoleMessageListFragment newInstance(String roomId, int layoutResId) {
        ConsoleMessageListFragment f = new ConsoleMessageListFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ROOM_ID, roomId);
        args.putInt(ARG_LAYOUT_ID, layoutResId);
        f.setArguments(args);
        return f;
    }

    @Override
    public MXSession getMXSession() {
        return Matrix.getInstance(getActivity()).getDefaultSession();
    }

    @Override
    public MXMediasCache getMXMediasCache() {
       return Matrix.getInstance(getActivity()).getDefaultMediasCache();
    }

    @Override
    public MessagesAdapter createMessagesAdapter() {
        // use the defaults message layouts
        // can set any adapters
        return new ConsoleMessagesAdapter(mSession, getActivity(), getMXMediasCache());
    }

    /**
     * The user scrolls the list.
     * Apply an expected behaviour
     * @param event the scroll event
     */
    @Override
    public void onListTouch(MotionEvent event) {
        // the user scroll over the keyboard
        // hides the keyboard
        if (mCheckSlideToHide && (event.getY() > mMessageListView.getHeight())) {
            mCheckSlideToHide = false;
            MXCActionBarActivity.dismissKeyboard(getActivity());
        }
    }

    /**
     * return true to display all the events.
     * else the unknown events will be hidden.
     */
    @Override
    public boolean isDisplayAllEvents() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        return preferences.getBoolean(getString(R.string.settings_key_display_all_events), false);
    }

    /**
     * Display a global spinner or any UI item to warn the user that there are some pending actions.
     */
    @Override
    public void displayLoadingProgress() {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final View progressView = getActivity().findViewById(R.id.loading_room_content_progress);

                if (null != progressView) {
                    progressView.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    /**
     * Dismiss any global spinner.
     */
    @Override
    public void dismissLoadingProgress() {
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final View progressView = getActivity().findViewById(R.id.loading_room_content_progress);

                if (null != progressView) {
                    progressView.setVisibility(View.GONE);
                }
            }
        });
    }

    /**
     * logout from the application
     */
    @Override
    public void logout() {
        CommonActivityUtils.logout(ConsoleMessageListFragment.this.getActivity());
    }

    /**
     * User actions when the user click on message row.
     * This example displays a menu to perform some actions on the message.
     */
    @Override
    public void onItemClick(int position) {
        final MessageRow messageRow = mAdapter.getItem(position);
        final List<Integer> textIds = new ArrayList<>();
        final List<Integer> iconIds = new ArrayList<Integer>();

        if (messageRow.getEvent().canBeResent()) {
            textIds.add(R.string.resend);
            iconIds.add(R.drawable.ic_material_send);
        } else if (messageRow.getEvent().mSentState == Event.SentState.SENT) {
            textIds.add(R.string.redact);
            iconIds.add(R.drawable.ic_material_clear);
        }

        // display the JSON
        textIds.add(R.string.message_details);
        iconIds.add(R.drawable.ic_material_description);

        FragmentManager fm = getActivity().getSupportFragmentManager();
        IconAndTextDialogFragment fragment = (IconAndTextDialogFragment) fm.findFragmentByTag(TAG_FRAGMENT_MESSAGE_OPTIONS);

        if (fragment != null) {
            fragment.dismissAllowingStateLoss();
        }

        Integer[] lIcons = iconIds.toArray(new Integer[iconIds.size()]);
        Integer[] lTexts = textIds.toArray(new Integer[iconIds.size()]);

        fragment = IconAndTextDialogFragment.newInstance(lIcons, lTexts);
        fragment.setOnClickListener(new IconAndTextDialogFragment.OnItemClickListener() {
            @Override
            public void onItemClick(IconAndTextDialogFragment dialogFragment, int position) {
                Integer selectedVal = textIds.get(position);

                if (selectedVal == R.string.resend) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            resend(messageRow.getEvent());
                        }
                    });
                } else if (selectedVal == R.string.redact) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            redactEvent(messageRow.getEvent().eventId);
                        }
                    });
                } else if (selectedVal == R.string.message_details) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            FragmentManager fm =  getActivity().getSupportFragmentManager();

                            MessageDetailsFragment fragment = (MessageDetailsFragment) fm.findFragmentByTag(TAG_FRAGMENT_MESSAGE_DETAILS);
                            if (fragment != null) {
                                fragment.dismissAllowingStateLoss();
                            }
                            fragment = MessageDetailsFragment.newInstance(messageRow.getEvent().toString());
                            fragment.show(fm, TAG_FRAGMENT_MESSAGE_DETAILS);
                        }
                    });
                }
            }
        });

        fragment.show(fm, TAG_FRAGMENT_MESSAGE_OPTIONS);
    }

}
