package org.matrix.matrixandroidsdk.activity;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
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

import java.util.List;

/**
 * Displays the main screen of the app, with rooms the user has joined and the ability to create
 * new rooms.
 */
public class HomeActivity extends MXCActionBarActivity {
    private ExpandableListView mMyRoomList = null;

    public static int recentsGroupIndex = 0;
    public static int publicRoomsGroupIndex = 1;
    private List<PublicRoom> mPublicRooms = null;

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
                    mAdapter.setPublicRoomsList(mPublicRooms);
                    mAdapter.sortSummaries();
                    mAdapter.notifyDataSetChanged();
                    mMyRoomList.expandGroup(recentsGroupIndex);

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

                        // If we're not currently viewing this room, increment the unread count
                        if (!event.roomId.equals(ViewedRoomTracker.getInstance().getViewedRoomId())) {
                            mAdapter.incrementUnreadCount(event.roomId);
                        }

                        mAdapter.sortSummaries();
                        mAdapter.notifyDataSetChanged();
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
                summary.setName("Room Invitation");
            }

            // only add summaries to rooms we have not left.
            if (!isMembershipInRoom(RoomMember.MEMBERSHIP_LEAVE, selfUserId, summary)) {
                mAdapter.addRoomSummary(summary);
            }
        }
    };

    private MXSession mSession;
    private RoomSummaryAdapter mAdapter;

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

        getActionBar().setDisplayShowTitleEnabled(false);

        mSession = Matrix.getInstance(getApplicationContext()).getDefaultSession();
        if (mSession == null) {
            finish();
            return;
        }

        mMyRoomList = (ExpandableListView)findViewById(R.id.listView_myRooms);
        mAdapter = new RoomSummaryAdapter(this, R.layout.adapter_item_my_rooms);
        mMyRoomList.setAdapter(mAdapter);

        mSession.getDataHandler().addListener(mListener);

        mMyRoomList.setOnChildClickListener(new ExpandableListView.OnChildClickListener() {
            @Override
            public boolean onChildClick(ExpandableListView parent, View v,
                                        int groupPosition, int childPosition, long id) {
                String roomId;

                if (groupPosition == recentsGroupIndex) {
                    roomId = mAdapter.getRoomSummaryAt(childPosition).getRoomId();
                    mAdapter.resetUnreadCount(roomId);
                } else {
                    roomId = mAdapter.getPublicRoomAt(childPosition).roomId;
                }

                goToRoomPage(roomId);
                mAdapter.notifyDataSetChanged();

                return true;
            }
        });

        mMyRoomList.setOnGroupCollapseListener(new ExpandableListView.OnGroupCollapseListener() {
            @Override
            public void onGroupCollapse (int groupPosition) {
                if (groupPosition == publicRoomsGroupIndex) {
                    if (!mMyRoomList.isGroupExpanded(recentsGroupIndex)) {
                        mMyRoomList.expandGroup(recentsGroupIndex);
                    }
                } else {
                    if (!mMyRoomList.isGroupExpanded(publicRoomsGroupIndex)) {
                        mMyRoomList.expandGroup(publicRoomsGroupIndex);
                    }
                }
            }
        });


        mMyRoomList.setOnGroupExpandListener(new ExpandableListView.OnGroupExpandListener() {
            @Override
            public void onGroupExpand (int groupPosition) {
                if (groupPosition == publicRoomsGroupIndex) {
                    refreshPublicRoomsList();
                }
            }
        });
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
    }

    @Override
    protected void onResume() {
        super.onResume();
        MyPresenceManager.getInstance(this).advertiseOnline();
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

         if (id == R.id.action_create_public_room) {
            createRoom(true);
            return true;
        }
        else if (id == R.id.action_create_private_room) {
            createRoom(false);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void goToPublicRoomPage() {
        startActivity(new Intent(this, PublicRoomsActivity.class));
    }

    public void goToRoomPage(String roomId) {
        Intent intent = new Intent(this, RoomActivity.class);
        intent.putExtra(RoomActivity.EXTRA_ROOM_ID, roomId);
        startActivity(intent);
    }

    private void createRoom(final boolean isPublic) {
        final String roomVisibility = isPublic ? RoomState.VISIBILITY_PUBLIC : RoomState.VISIBILITY_PRIVATE;
        // For public rooms, we ask for the alias; for private, the room name
        String alertTitle = getString(isPublic ? R.string.create_room_set_alias : R.string.create_room_set_name);
        String textFieldHint = getString(isPublic ? R.string.create_room_alias_hint : R.string.create_room_name_hint);

        AlertDialog alert = CommonActivityUtils.createEditTextAlert(this, alertTitle, textFieldHint, new CommonActivityUtils.OnSubmitListener() {
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
                        goToRoomPage(info);
                    }
                });
            }

            @Override
            public void onCancelled() {}
        });
        alert.show();
    }
}
