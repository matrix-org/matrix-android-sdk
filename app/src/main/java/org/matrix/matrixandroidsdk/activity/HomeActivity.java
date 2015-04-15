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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.Toast;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.data.RoomSummary;
import org.matrix.androidsdk.fragments.IconAndTextDialogFragment;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.PublicRoom;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.util.EventUtils;
import org.matrix.androidsdk.util.JsonUtils;
import org.matrix.matrixandroidsdk.Matrix;
import org.matrix.matrixandroidsdk.MyPresenceManager;
import org.matrix.matrixandroidsdk.R;
import org.matrix.matrixandroidsdk.ViewedRoomTracker;
import org.matrix.matrixandroidsdk.adapters.ConsoleRoomSummaryAdapter;
import org.matrix.matrixandroidsdk.fragments.ContactsListDialogFragment;
import org.matrix.matrixandroidsdk.fragments.RoomCreationDialogFragment;
import org.matrix.matrixandroidsdk.util.RageShake;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
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
    private static final String TAG_FRAGMENT_ROOM_OPTIONS = "org.matrix.androidsdk.HomeActivity.TAG_FRAGMENT_ROOM_OPTIONS";

    public static final String EXTRA_JUMP_TO_ROOM_ID = "org.matrix.matrixandroidsdk.HomeActivity.EXTRA_JUMP_TO_ROOM_ID";
    public static final String EXTRA_ROOM_INTENT = "org.matrix.matrixandroidsdk.HomeActivity.EXTRA_ROOM_INTENT";

    private List<PublicRoom> mPublicRooms = null;

    private boolean mIsPaused = false;

    private String mAutomaticallyOpenedRoomId = null;
    private Intent mOpenedRoomIntent = null;

    // sliding menu
    private final Integer[] mSlideMenuTitleIds = new Integer[]{
            //R.string.action_search_contact,
            //R.string.action_search_room,
            R.string.create_room,
            R.string.join_room,
           // R.string.action_mark_all_as_read,
            R.string.action_settings,
            R.string.send_bug_report,
            R.string.action_disconnect,
            R.string.action_logout,
    };

    // sliding menu
    private final Integer[] mSlideMenuResourceIds = new Integer[]{
            //R.drawable.ic_material_search, // R.string.action_search_contact,
            //R.drawable.ic_material_find_in_page, // R.string.action_search_room,
            R.drawable.ic_material_group_add, //R.string.create_room,
            R.drawable.ic_material_group, // R.string.join_room,
            //R.drawable.ic_material_done_all, // R.string.action_mark_all_as_read,
            R.drawable.ic_material_settings, //  R.string.action_settings,
            R.drawable.ic_material_bug_report, // R.string.send_bug_report,
            R.drawable.ic_material_clear, // R.string.action_disconnect,
            R.drawable.ic_material_exit_to_app, // R.string.action_logout,
    };


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
                    // done onResume
                    //refreshPublicRoomsList();
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
                        String selfUserId = mSession.getCredentials().userId;
                        Room room = mSession.getDataHandler().getRoom(event.roomId);

                        // roomState is the state before the update
                        // need to update to updated state so the live state
                        mAdapter.setLatestEvent(event, (null == room) ? roomState : room.getLiveState());

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

                            if (EventUtils.shouldHighlight(mSession, HomeActivity.this, event)) {
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
    private ConsoleRoomSummaryAdapter mAdapter;
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

        addSlidingMenu(mSlideMenuResourceIds, mSlideMenuTitleIds, true);

        mSession = Matrix.getInstance(getApplicationContext()).getDefaultSession();
        if (mSession == null) {
            finish();
            return;
        }

        mMyRoomList = (ExpandableListView) findViewById(R.id.listView_myRooms);
        // the chevron is managed in the header view
        mMyRoomList.setGroupIndicator(null);
        mAdapter = new ConsoleRoomSummaryAdapter(mSession, this, R.layout.adapter_item_my_rooms, R.layout.adapter_room_section_header);

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

        final Intent intent = getIntent();
        if (intent.hasExtra(EXTRA_JUMP_TO_ROOM_ID)) {
            mAutomaticallyOpenedRoomId = intent.getStringExtra(EXTRA_JUMP_TO_ROOM_ID);
        }

        if (intent.hasExtra(EXTRA_ROOM_INTENT)) {
            mOpenedRoomIntent = intent.getParcelableExtra(EXTRA_ROOM_INTENT);
        }

        String action = intent.getAction();
        String type = intent.getType();

        // send files from external application
        if ((Intent.ACTION_SEND.equals(action) || Intent.ACTION_SEND_MULTIPLE.equals(action)) && type != null) {
            this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    CommonActivityUtils.sendFilesTo(HomeActivity.this, mSession, intent);
                }
            });
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

                    Room room = mSession.getDataHandler().getRoom(roomId);
                    // cannot join a leaving room
                    if ((null == room) || room.isLeaving()) {
                        roomId = null;
                    }

                    mAdapter.resetUnreadCount(roomId);
                } else if (mAdapter.isPublicsGroupIndex(groupPosition)) {
                    roomId = mAdapter.getPublicRoomAt(childPosition).roomId;
                }

                if (null != roomId){
                    CommonActivityUtils.goToRoomPage(roomId, HomeActivity.this, null);
                }
                return true;
            }
        });

        mMyRoomList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {

                if (ExpandableListView.getPackedPositionType(id) == ExpandableListView.PACKED_POSITION_TYPE_CHILD) {
                    long packedPos = ((ExpandableListView) parent).getExpandableListPosition(position);
                    int groupPosition = ExpandableListView.getPackedPositionGroup(packedPos);

                    if (mAdapter.isRecentsGroupIndex(groupPosition)) {
                        final int childPosition = ExpandableListView.getPackedPositionChild(packedPos);

                        FragmentManager fm = HomeActivity.this.getSupportFragmentManager();
                        IconAndTextDialogFragment fragment = (IconAndTextDialogFragment) fm.findFragmentByTag(TAG_FRAGMENT_ROOM_OPTIONS);

                        if (fragment != null) {
                            fragment.dismissAllowingStateLoss();
                        }

                        final Integer[] lIcons = new Integer[]{R.drawable.ic_material_exit_to_app};
                        final Integer[] lTexts = new Integer[]{R.string.action_leave};

                        fragment = IconAndTextDialogFragment.newInstance(lIcons, lTexts);
                        fragment.setOnClickListener(new IconAndTextDialogFragment.OnItemClickListener() {
                            @Override
                            public void onItemClick(IconAndTextDialogFragment dialogFragment, int position) {
                                Integer selectedVal = lTexts[position];

                                if (selectedVal == R.string.action_leave) {
                                    HomeActivity.this.runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            String roomId = mAdapter.getRoomSummaryAt(childPosition).getRoomId();
                                            Room room = mSession.getDataHandler().getRoom(roomId);

                                            if (null != room) {
                                                room.leave(new SimpleApiCallback<Void>(HomeActivity.this) {
                                                    @Override
                                                    public void onSuccess(Void info) {
                                                    }
                                                });
                                            }
                                        }
                                    });
                                }
                            }
                        });

                        fragment.show(fm, TAG_FRAGMENT_ROOM_OPTIONS);

                        return true;
                    }
                }

                return false;
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
                    CommonActivityUtils.goToRoomPage(HomeActivity.this.mAutomaticallyOpenedRoomId, HomeActivity.this, mOpenedRoomIntent);
                    HomeActivity.this.mAutomaticallyOpenedRoomId = null;
                    HomeActivity.this.mOpenedRoomIntent = null;
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

        if (intent.hasExtra(EXTRA_ROOM_INTENT)) {
            mOpenedRoomIntent = intent.getParcelableExtra(EXTRA_ROOM_INTENT);
        }
    }

    @Override
    protected void selectDrawItem(int position) {
        // Highlight the selected item, update the title, and close the drawer
        mDrawerList.setItemChecked(position, true);

        final int id = (position == 0) ? R.string.action_settings : mSlideMenuTitleIds[position - 1];

        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (id == R.string.action_search_contact) {
                    toggleSearchContacts();
                } else if (id == R.string.action_search_room) {
                    toggleSearchButton();
                } else if (id == R.string.create_room) {
                    createRoom();
                } else if (id ==  R.string.join_room) {
                    joinRoomByName();
                } else if (id ==  R.string.action_mark_all_as_read) {
                    markAllMessagesAsRead();
                } else if (id ==  R.string.action_settings) {
                    HomeActivity.this.startActivity(new Intent(HomeActivity.this, SettingsActivity.class));
                } else if (id ==  R.string.action_disconnect) {
                    CommonActivityUtils.disconnect(HomeActivity.this);
                } else if (id ==  R.string.send_bug_report) {
                    RageShake.getInstance().sendBugReport();
                } else if (id ==  R.string.action_logout) {
                    CommonActivityUtils.logout(HomeActivity.this);
                }
            }
        });

        mDrawerLayout.closeDrawer(mDrawerList);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_SEARCH)) {
            toggleSearchButton();
            return true;
        }

        if ((keyCode == KeyEvent.KEYCODE_MENU)) {
            HomeActivity.this.startActivity(new Intent(HomeActivity.this, SettingsActivity.class));
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.home, menu);
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.ic_action_search_contact) {
            toggleSearchContacts();
        } else if (id == R.id.ic_action_search_room) {
            toggleSearchButton();
        } else if (id == R.id.ic_action_mark_all_as_read) {
            markAllMessagesAsRead();
        }

        return super.onOptionsItemSelected(item);
    }

    private void toggleSearchContacts() {
        FragmentManager fm = getSupportFragmentManager();

        ContactsListDialogFragment fragment = (ContactsListDialogFragment) fm.findFragmentByTag(TAG_FRAGMENT_CONTACTS_LIST);
        if (fragment != null) {
            fragment.dismissAllowingStateLoss();
        }
        fragment = ContactsListDialogFragment.newInstance();
        fragment.show(fm, TAG_FRAGMENT_CONTACTS_LIST);
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
                            CommonActivityUtils.goToRoomPage(roomId, HomeActivity.this, null);
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

    private void markAllMessagesAsRead() {
        mAdapter.resetUnreadCounts();
        mAdapter.notifyDataSetChanged();
    }
}
