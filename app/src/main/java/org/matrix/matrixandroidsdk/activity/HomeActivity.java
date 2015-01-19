package org.matrix.matrixandroidsdk.activity;

import android.app.AlertDialog;
import android.content.Intent;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.data.RoomSummary;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.util.JsonUtils;
import org.matrix.matrixandroidsdk.Matrix;
import org.matrix.matrixandroidsdk.R;
import org.matrix.matrixandroidsdk.adapters.RoomSummaryAdapter;

/**
 * Displays the main screen of the app, with rooms the user has joined and the ability to create
 * new rooms.
 */
public class HomeActivity extends ActionBarActivity {

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
                            mAdapter.remove(summary);
                        }

                        // Watch for potential room name changes
                        else if (Event.EVENT_TYPE_STATE_ROOM_NAME.equals(event.type)
                                || Event.EVENT_TYPE_STATE_ROOM_ALIASES.equals(event.type)
                                || Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(event.type)) {
                            summary.setName(room.getName(mSession.getCredentials().userId));
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
                mAdapter.add(summary);
            }
        }
    };

    private MXSession mSession;
    private RoomSummaryAdapter mAdapter;

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

        final ListView myRoomList = (ListView)findViewById(R.id.listView_myRooms);
        mAdapter = new RoomSummaryAdapter(this, R.layout.adapter_item_my_rooms);
        myRoomList.setAdapter(mAdapter);

        mSession.getDataHandler().addListener(mListener);

        myRoomList.setOnItemClickListener(new AdapterView.OnItemClickListener(){

            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                goToRoomPage(mAdapter.getItem(i).getRoomId());
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mSession.getDataHandler().removeListener(mListener);
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

        if (id == R.id.action_public_rooms) {
            goToPublicRoomPage();
            return true;
        }
        else if (id == R.id.action_create_public_room) {
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

    private void goToRoomPage(String roomId) {
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
