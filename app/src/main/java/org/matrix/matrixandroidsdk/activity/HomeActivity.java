/*
 * Copyright 2014 OpenMarket Ltd
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

package org.matrix.matrixandroidsdk.activity;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ExpandableListView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.data.RoomSummary;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.PublicRoom;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.util.JsonUtils;
import org.matrix.matrixandroidsdk.Matrix;
import org.matrix.matrixandroidsdk.MyPresenceManager;
import org.matrix.matrixandroidsdk.R;
import org.matrix.matrixandroidsdk.ViewedRoomTracker;
import org.matrix.matrixandroidsdk.adapters.RoomSummaryAdapter;
import org.matrix.matrixandroidsdk.util.EventUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Displays the main screen of the app, with rooms the user has joined and the ability to create
 * new rooms.
 */
public class HomeActivity extends MXCActionBarActivity {
    private ExpandableListView mMyRoomList = null;

    private static final String UNREAD_MESSAGE_MAP = "UNREAD_MESSAGE_MAP";
    private static final String PUBLIC_ROOMS_LIST = "PUBLIC_ROOMS_LIST";
    public static final String EXTRA_JUMP_TO_ROOM_ID = "org.matrix.matrixandroidsdk.HomeActivity.EXTRA_JUMP_TO_ROOM_ID";

    private List<PublicRoom> mPublicRooms = null;

    private boolean mIsPaused = false;
    private boolean mSortSummaryAtResume = false;

    private String mAutomaticallyOpenedRoomId = null;

    private MXEventListener mListener = new MXEventListener() {
        private boolean mInitialSyncComplete = false;

        @Override
        public void onInitialSyncComplete() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mInitialSyncComplete = true;
                    for (RoomSummary summary : mSession.getDataHandler().getStore().getSummaries()) {
                        addSummary(summary);
                    }

                    // highlighted public rooms
                    mAdapter.highlightRoom("Matrix HQ");
                    mAdapter.highlightRoom("#matrix:matrix.org");
                    mAdapter.highlightRoom("#matrix-dev:matrix.org");
                    mAdapter.highlightRoom("#matrix-fr:matrix.org");

                    mAdapter.setPublicRoomsList(mPublicRooms);
                    mAdapter.sortSummaries();
                    mAdapter.notifyDataSetChanged();

                    if (mAdapter.mRecentsGroupIndex >= 0) {
                        mMyRoomList.expandGroup(mAdapter.mRecentsGroupIndex);
                    }

                    if (mAdapter.mPublicsGroupIndex >= 0) {
                        mMyRoomList.expandGroup(mAdapter.mPublicsGroupIndex);
                    }

                    // load the public load in background
                    refreshPublicRoomsList();
                }
            });
        }

        @Override
        public void onLiveEvent(final Event event, final RoomState roomState) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if ((event.roomId != null) && isDisplayableEvent(event)) {
                        mAdapter.setLatestEvent(event, roomState);

                        String selfUserId = mSession.getCredentials().userId;
                        Room room = mSession.getDataHandler().getRoom(event.roomId);

                        RoomSummary summary = mAdapter.getSummaryByRoomId(event.roomId);
                        if (summary == null) {
                            // ROOM_CREATE events will be sent during initial sync. We want to ignore them
                            // until the initial sync is done (that is, only refresh the list when there
                            // are new rooms created AFTER we have synced).
                            if (mInitialSyncComplete) {
                                if (Event.EVENT_TYPE_STATE_ROOM_CREATE.equals(event.type)) {
                                    addNewRoom(event.roomId);
                                } else if (Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(event.type)) {
                                    RoomMember member = JsonUtils.toRoomMember(event.content);
                                    if (RoomMember.MEMBERSHIP_INVITE.equals(member.membership) && event.stateKey.equals(selfUserId)) {
                                        // we were invited to a new room.
                                        addNewRoom(event.roomId);
                                    }
                                }
                            }
                        }

                        // If we've left the room, remove it from the list
                        else if (mInitialSyncComplete && Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(event.type) &&
                                isMembershipInRoom(RoomMember.MEMBERSHIP_LEAVE, selfUserId, summary)) {
                            mAdapter.removeRoomSummary(summary);
                        }

                        // Watch for potential room name changes
                        else if (Event.EVENT_TYPE_STATE_ROOM_NAME.equals(event.type)
                                || Event.EVENT_TYPE_STATE_ROOM_ALIASES.equals(event.type)
                                || Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(event.type)) {
                            summary.setName(room.getName(mSession.getCredentials().userId));
                        }

                        // If we're not currently viewing this room or not sent by myself, increment the unread count
                        if (!event.roomId.equals(ViewedRoomTracker.getInstance().getViewedRoomId()) && !event.userId.equals(selfUserId)) {
                            mAdapter.incrementUnreadCount(event.roomId);

                            if (EventUtils.shouldHighlight(HomeActivity.this, event)) {
                                mAdapter.highlightRoom(event.roomId);
                            }
                        }

                        if (!mIsPaused) {
                            mAdapter.sortSummaries();
                            mAdapter.notifyDataSetChanged();
                        } else {
                            mSortSummaryAtResume = false;
                        }
                    }
                }
            });
        }

        // White list of displayable events
        private boolean isDisplayableEvent(Event event) {
            return Event.EVENT_TYPE_MESSAGE.equals(event.type)
                    || Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(event.type)
                    || Event.EVENT_TYPE_STATE_ROOM_CREATE.equals(event.type)
                    || Event.EVENT_TYPE_STATE_ROOM_NAME.equals(event.type)
                    || Event.EVENT_TYPE_STATE_ROOM_ALIASES.equals(event.type)
                    || Event.EVENT_TYPE_STATE_ROOM_TOPIC.equals(event.type);
        }

        private void addNewRoom(String roomId) {
            RoomSummary summary = mSession.getDataHandler().getStore().getSummary(roomId);
            addSummary(summary);
            mAdapter.sortSummaries();
        }

        private boolean isMembershipInRoom(String membership, String selfUserId, RoomSummary summary) {
            for (RoomMember member : summary.getMembers()) {
                if (membership.equals(member.membership) && selfUserId.equals(member.getUserId())) {
                    return true;
                }
            }
            return false;
        }

        private void addSummary(RoomSummary summary) {
            String selfUserId = mSession.getCredentials().userId;
            boolean isInvited = isMembershipInRoom(RoomMember.MEMBERSHIP_INVITE, selfUserId, summary);
            if (isInvited) {
                Room room = mSession.getDataHandler().getStore().getRoom(summary.getRoomId());

                // display the room name instead of "Room invitation"
                // at least, you know who invited you
                if (null != room) {
                    summary.setName(room.getName(mSession.getCredentials().userId));
                } else {
                    summary.setName(getString(R.string.summary_invitation));
                }
            }

            // only add summaries to rooms we have not left.
            if (!isMembershipInRoom(RoomMember.MEMBERSHIP_LEAVE, selfUserId, summary)) {
                mAdapter.addRoomSummary(summary);
            }
        }
    };

    private MXSession mSession;
    private RoomSummaryAdapter mAdapter;
    private EditText mSearchRoomEditText;

    private void refreshPublicRoomsList() {
        Matrix.getInstance(getApplicationContext()).getDefaultSession().getEventsApiClient().loadPublicRooms(new SimpleApiCallback<List<PublicRoom>>() {
            @Override
            public void onSuccess(List<PublicRoom> publicRooms) {
                mAdapter.setPublicRoomsList(publicRooms);
                mPublicRooms = publicRooms;
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        // sanity check
        if (null != getActionBar()) {
            getActionBar().setDisplayShowTitleEnabled(false);
        }

        mSession = Matrix.getInstance(getApplicationContext()).getDefaultSession();
        if (mSession == null) {
            finish();
            return;
        }

        mMyRoomList = (ExpandableListView) findViewById(R.id.listView_myRooms);
        mAdapter = new RoomSummaryAdapter(this, R.layout.adapter_item_my_rooms);

        if (null != savedInstanceState) {
            if (savedInstanceState.containsKey(UNREAD_MESSAGE_MAP)) {
                // the unread messages map is saved in the bundle
                // It is used to  restore a valid map after a screen rotation for example
                Serializable map = savedInstanceState.getSerializable(UNREAD_MESSAGE_MAP);

                if (null != map) {
                    mAdapter.setUnreadCountMap((HashMap<String, Integer>) map);
                }
            }

            if (savedInstanceState.containsKey(PUBLIC_ROOMS_LIST)) {
                Serializable map = savedInstanceState.getSerializable(PUBLIC_ROOMS_LIST);

                if (null != map) {
                    HashMap<String, PublicRoom> hash = (HashMap<String, PublicRoom>) map;
                    mPublicRooms = new ArrayList<PublicRoom>(hash.values());
                }
            }
        }

        Intent intent = getIntent();
        if (intent.hasExtra(EXTRA_JUMP_TO_ROOM_ID)) {
            mAutomaticallyOpenedRoomId = intent.getStringExtra(EXTRA_JUMP_TO_ROOM_ID);
        }

        mMyRoomList.setAdapter(mAdapter);

        mSession.getDataHandler().addListener(mListener);

        mMyRoomList.setOnChildClickListener(new ExpandableListView.OnChildClickListener() {
            @Override
            public boolean onChildClick(ExpandableListView parent, View v,
                                        int groupPosition, int childPosition, long id) {
                String roomId = null;

                if (mAdapter.isRecentsGroupIndex(groupPosition)) {
                    roomId = mAdapter.getRoomSummaryAt(childPosition).getRoomId();
                    mAdapter.resetUnreadCount(roomId);
                } else if (mAdapter.isPublicsGroupIndex(groupPosition)) {
                    roomId = mAdapter.getPublicRoomAt(childPosition).roomId;
                }

                if (null != roomId) {
                    CommonActivityUtils.goToRoomPage(roomId, HomeActivity.this);
                }
                return true;
            }
        });

        mMyRoomList.setOnGroupExpandListener(new ExpandableListView.OnGroupExpandListener() {
            @Override
            public void onGroupExpand(int groupPosition) {
                if (mAdapter.isPublicsGroupIndex(groupPosition)) {
                    refreshPublicRoomsList();
                }
            }
        });

        mMyRoomList.setOnGroupClickListener(new ExpandableListView.OnGroupClickListener() {
            @Override
            public boolean onGroupClick(ExpandableListView parent, View v, int groupPosition, long id) {
                return mAdapter.getGroupCount() < 2;
            }
        });

        mSearchRoomEditText = (EditText) this.findViewById(R.id.editText_search_room);
        mSearchRoomEditText.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(android.text.Editable s) {
                mAdapter.setSearchedPattern(s.toString());
                mMyRoomList.smoothScrollToPosition(0);
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        // Always call the superclass so it can save the view hierarchy state
        super.onSaveInstanceState(savedInstanceState);

        // save the unread messages counters
        // to avoid resetting counters after a screen rotation
        if ((null != mAdapter) && (null != mAdapter.getUnreadCountMap())) {
            savedInstanceState.putSerializable(UNREAD_MESSAGE_MAP, mAdapter.getUnreadCountMap());
        }

        if (null != mPublicRooms) {
            HashMap<String, PublicRoom> hash = new HashMap<String, PublicRoom>();

            for(PublicRoom publicRoom : mPublicRooms) {
                hash.put(publicRoom.roomId, publicRoom);
            }

            savedInstanceState.putSerializable(PUBLIC_ROOMS_LIST, hash);
        }
    }

    private void expandAllGroups() {
        if (mAdapter.mRecentsGroupIndex >= 0) {
            mMyRoomList.expandGroup(mAdapter.mRecentsGroupIndex);
        }

        if (mAdapter.mPublicsGroupIndex >= 0) {
            mMyRoomList.expandGroup(mAdapter.mPublicsGroupIndex);
        }
    }

    private void collapseAllGroups() {
        if (mAdapter.mRecentsGroupIndex >= 0) {
            mMyRoomList.collapseGroup(mAdapter.mRecentsGroupIndex);
        }

        if (mAdapter.mPublicsGroupIndex >= 0) {
            mMyRoomList.collapseGroup(mAdapter.mPublicsGroupIndex);
        }
    }

    private void toggleSearchButton() {
        if (mSearchRoomEditText.getVisibility() == View.GONE) {
            mSearchRoomEditText.setVisibility(View.VISIBLE);

            // need to collapse/expand the groups to avoid invalid refreshes
            collapseAllGroups();
            mAdapter.setDisplayAllGroups(true);
            expandAllGroups();
        } else {
            // need to collapse/expand the groups to avoid invalid refreshes
            collapseAllGroups();
            mAdapter.setDisplayAllGroups(false);
            expandAllGroups();

            mSearchRoomEditText.setVisibility(View.GONE);

            if (mAdapter.mRecentsGroupIndex >= 0) {
                mMyRoomList.expandGroup(mAdapter.mRecentsGroupIndex);
            }

            if (mAdapter.mPublicsGroupIndex >= 0) {
                mMyRoomList.expandGroup(mAdapter.mPublicsGroupIndex);
            }

            // force to hide the keyboard
            mSearchRoomEditText.postDelayed(new Runnable() {
                public void run() {
                    InputMethodManager keyboard = (InputMethodManager) getSystemService(getApplication().INPUT_METHOD_SERVICE);
                    keyboard.hideSoftInputFromWindow(
                            mSearchRoomEditText.getWindowToken(), 0);
                }
            }, 200);
        }

        mSearchRoomEditText.setText("");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mSession.getDataHandler().removeListener(mListener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        MyPresenceManager.getInstance(this).advertiseUnavailableAfterDelay();
        mIsPaused = true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        MyPresenceManager.getInstance(this).advertiseOnline();
        mIsPaused = false;

        if (mSortSummaryAtResume) {
            mAdapter.sortSummaries();
        }

        // expand/collapse to force th group refresh
        collapseAllGroups();
        // all the groups must be displayed during a search
        mAdapter.setDisplayAllGroups(mSearchRoomEditText.getVisibility() == View.VISIBLE);
        expandAllGroups();

        mAdapter.notifyDataSetChanged();

        if (null != mAutomaticallyOpenedRoomId) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    CommonActivityUtils.goToRoomPage(HomeActivity.this.mAutomaticallyOpenedRoomId, HomeActivity.this);
                    HomeActivity.this.mAutomaticallyOpenedRoomId = null;
                }
            });
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        if (intent.hasExtra(EXTRA_JUMP_TO_ROOM_ID)) {
            mAutomaticallyOpenedRoomId = intent.getStringExtra(EXTRA_JUMP_TO_ROOM_ID);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.home, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (CommonActivityUtils.handleMenuItemSelected(this, id)) {
            return true;
        }

        if (id == R.id.action_mark_all_as_read) {
            markAllMessagesAsRead();
            return true;
        }
        else if (id == R.id.search_room) {
            toggleSearchButton();
            return true;
        }
        else if (id == R.id.action_create_private_room) {
            createRoom(false);
            return true;
        }
        else if (id == R.id.action_create_public_room) {
            createRoom(true);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_SEARCH)) {
            toggleSearchButton();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void createRoom(final boolean isPublic) {
        final String roomVisibility = isPublic ? RoomState.VISIBILITY_PUBLIC : RoomState.VISIBILITY_PRIVATE;
        // For public rooms, we ask for the alias; for private, the room name
        String alertTitle = getString(isPublic ? R.string.create_room_set_alias : R.string.create_room_set_name);
        String textFieldHint = getString(isPublic ? R.string.create_room_alias_hint : R.string.create_room_name_hint);

        AlertDialog alert = CommonActivityUtils.createEditTextAlert(this, alertTitle, textFieldHint, null, new CommonActivityUtils.OnSubmitListener() {
            @Override
            public void onSubmit(String text) {
                if (text.length() == 0) {
                    return;
                }
                MXSession session = Matrix.getInstance(getApplicationContext()).getDefaultSession();
                String alias = isPublic ? text : null;
                String name = isPublic ? null : text;
                session.createRoom(name, null, roomVisibility, alias, new SimpleApiCallback<String>() {

                    @Override
                    public void onSuccess(String info) {
                        CommonActivityUtils.goToRoomPage(info, HomeActivity.this);
                    }
                });
            }

            @Override
            public void onCancelled() {}
        });
        alert.show();
    }

    private void markAllMessagesAsRead(){
        mAdapter.resetUnreadCounts();
        mAdapter.notifyDataSetChanged();
    }
}
