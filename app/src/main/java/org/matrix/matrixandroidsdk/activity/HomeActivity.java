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
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v4.widget.DrawerLayout;
import android.support.v4.app.ActionBarDrawerToggle;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.ListView;
import android.widget.Toast;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.data.RoomSummary;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.PublicRoom;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.util.JsonUtils;
import org.matrix.matrixandroidsdk.Matrix;
import org.matrix.matrixandroidsdk.MyPresenceManager;
import org.matrix.matrixandroidsdk.R;
import org.matrix.matrixandroidsdk.ViewedRoomTracker;
import org.matrix.matrixandroidsdk.adapters.DrawerAdapter;
import org.matrix.matrixandroidsdk.adapters.RoomSummaryAdapter;
import org.matrix.matrixandroidsdk.fragments.ContactsListDialogFragment;
import org.matrix.matrixandroidsdk.fragments.MembersInvitationDialogFragment;
import org.matrix.matrixandroidsdk.fragments.RoomCreationDialogFragment;
import org.matrix.matrixandroidsdk.util.EventUtils;
import org.matrix.matrixandroidsdk.util.RageShake;

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

    private static final String TAG_FRAGMENT_CONTACTS_LIST = "org.matrix.androidsdk.HomeActivity.TAG_FRAGMENT_CONTACTS_LIST";
    private static final String TAG_FRAGMENT_CREATE_ROOM_DIALOG = "org.matrix.androidsdk.HomeActivity.TAG_FRAGMENT_CREATE_ROOM_DIALOG";

    public static final String EXTRA_JUMP_TO_ROOM_ID = "org.matrix.matrixandroidsdk.HomeActivity.EXTRA_JUMP_TO_ROOM_ID";

    private List<PublicRoom> mPublicRooms = null;

    private boolean mIsPaused = false;

    private String mAutomaticallyOpenedRoomId = null;

    // sliding menu
    private Integer[] mSlideMenuTitleIds = new Integer[]{
            R.string.action_search_contact,
            R.string.action_search_room,
            R.string.create_room,
            R.string.join_room,
            R.string.action_mark_all_as_read,
            R.string.action_settings,
            R.string.send_bug_report,
            R.string.action_disconnect,
            R.string.action_logout,
    };

    // sliding menu
    private Integer[] mSlideMenuResourceIds = new Integer[]{
            R.drawable.ic_menu_search, // R.string.action_search_contact,
            R.drawable.ic_menu_search, // R.string.action_search_room,
            R.drawable.ic_menu_btn_add, //R.string.create_room,
            R.drawable.ic_menu_start_conversation, // R.string.join_room,
            R.drawable.ic_menu_start_conversation, // R.string.action_mark_all_as_read,
            R.drawable.ic_menu_settings_holo_light, //  R.string.action_settings,
            R.drawable.ic_menu_settings_holo_light, // R.string.send_bug_report,
            R.drawable.ic_menu_end_conversation, // R.string.action_disconnect,
            R.drawable.ic_menu_end_conversation, // R.string.action_logout,
    };

    private DrawerLayout mDrawerLayout;
    private ListView mDrawerList;
    private ActionBarDrawerToggle mDrawerToggle;

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
        public void onRoomInitialSyncComplete(final String roomId) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mAdapter.sortSummaries();
                    mAdapter.notifyDataSetChanged();
                }
            });
        }

        @Override
        public void onRoomInternalUpdate(String roomId) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mAdapter.sortSummaries();
                    mAdapter.notifyDataSetChanged();
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

                                    // add the room summary if the user has
                                    if ((RoomMember.MEMBERSHIP_INVITE.equals(member.membership) || RoomMember.MEMBERSHIP_JOIN.equals(member.membership))
                                            && event.stateKey.equals(selfUserId)) {
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
        Matrix.getInstance(getApplicationContext()).getDefaultSession().getEventsApiClient().loadPublicRooms(new SimpleApiCallback<List<PublicRoom>>(this) {
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

        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mDrawerList = (ListView) findViewById(R.id.left_drawer);

        // Set the adapter for the list view
        DrawerAdapter adapter = new DrawerAdapter(this, R.layout.adapter_drawer_item);

        for(int index = 0; index < mSlideMenuTitleIds.length; index++) {
            adapter.add(mSlideMenuResourceIds[index], getString(mSlideMenuTitleIds[index]));
        }

        mDrawerList.setAdapter(adapter);

        // Set the list's click listener
        mDrawerList.setOnItemClickListener(new DrawerItemClickListener());

        mDrawerToggle = new ActionBarDrawerToggle(
                this,                  /* host Activity */
                mDrawerLayout,         /* DrawerLayout object */
                R.drawable.ic_drawer,  /* nav drawer icon to replace 'Up' caret */
                R.string.action_open,  /* "open drawer" description */
                R.string.action_close  /* "close drawer" description */
        ) {

            public void onDrawerClosed(View view) {
            }

            public void onDrawerOpened(View drawerView) {
            }
        };

        // Set the drawer toggle as the DrawerListener
        mDrawerLayout.setDrawerListener(mDrawerToggle);

        // display the home and title button
        if (null != getSupportActionBar()) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
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

        // some unsent messages could have been added
        // it does not trigger any live event.
        // So, it is safer to sort the messages when debackgrounding
        mAdapter.sortSummaries();
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
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        mDrawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mDrawerToggle.onConfigurationChanged(newConfig);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Pass the event to ActionBarDrawerToggle, if it returns
        // true, then it has handled the app icon touch event
        if (mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Run the dedicated sliding menu action
     * @param position selected menu entry
     */
    private void selectItem(int position) {
        // Highlight the selected item, update the title, and close the drawer
        mDrawerList.setItemChecked(position, true);

        int id = mSlideMenuTitleIds[position];

        if (id == R.string.action_search_contact) {
            FragmentManager fm = getSupportFragmentManager();

            ContactsListDialogFragment fragment = (ContactsListDialogFragment) fm.findFragmentByTag(TAG_FRAGMENT_CONTACTS_LIST);
            if (fragment != null) {
                fragment.dismissAllowingStateLoss();
            }
            fragment = ContactsListDialogFragment.newInstance();
            fragment.show(fm, TAG_FRAGMENT_CONTACTS_LIST);
        } else if (id == R.string.action_search_room) {
            toggleSearchButton();
        } else if (id == R.string.create_room) {
            createRoom();
        } else if (id ==  R.string.join_room) {
            joinRoomByName();
        } else if (id ==  R.string.action_mark_all_as_read) {
            markAllMessagesAsRead();
        } else if (id ==  R.string.action_settings) {
            this.startActivity(new Intent(this, SettingsActivity.class));
        } else if (id ==  R.string.action_disconnect) {
            CommonActivityUtils.disconnect(this);
        } else if (id ==  R.string.send_bug_report) {
            RageShake.getInstance().sendBugReport();
        } else if (id ==  R.string.action_logout) {
            CommonActivityUtils.logout(this);
        }

        mDrawerLayout.closeDrawer(mDrawerList);
    }

    private class DrawerItemClickListener implements ListView.OnItemClickListener {
        @Override
        public void onItemClick(AdapterView parent, View view, int position, long id) {
            selectItem(position);
        }
    }


    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_SEARCH)) {
            toggleSearchButton();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void joinRoomByName() {
        AlertDialog alert = CommonActivityUtils.createEditTextAlert(this, getString(R.string.join_room_title),  getString(R.string.join_room_hint), null, new CommonActivityUtils.OnSubmitListener() {
            @Override
            public void onSubmit(String text) {
                MXSession session = Matrix.getInstance(getApplicationContext()).getDefaultSession();

                session.joinRoom(text, new ApiCallback<String>() {
                    @Override
                    public void onSuccess(String roomId) {
                        if (null != roomId) {
                            CommonActivityUtils.goToRoomPage(roomId, HomeActivity.this);
                        }
                    }

                    @Override
                    public void onNetworkError(Exception e) {
                        Toast.makeText(HomeActivity.this,
                                getString(R.string.network_error),
                                Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onMatrixError(MatrixError e) {
                        Toast.makeText(HomeActivity.this,
                                e.error,
                                Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onUnexpectedError(Exception e) {
                        Toast.makeText(HomeActivity.this,
                                e.getLocalizedMessage(),
                                Toast.LENGTH_LONG).show();

                    }
                });
            }

            @Override
            public void onCancelled() {}
        });
        alert.show();
    }

    private void createRoom() {
        FragmentManager fm = getSupportFragmentManager();

        RoomCreationDialogFragment fragment = (RoomCreationDialogFragment) fm.findFragmentByTag(TAG_FRAGMENT_CREATE_ROOM_DIALOG);
        if (fragment != null) {
            fragment.dismissAllowingStateLoss();
        }
        fragment = RoomCreationDialogFragment.newInstance();
        fragment.show(fm, TAG_FRAGMENT_CREATE_ROOM_DIALOG);
    }

    private void markAllMessagesAsRead(){
        mAdapter.resetUnreadCounts();
        mAdapter.notifyDataSetChanged();
    }
}
