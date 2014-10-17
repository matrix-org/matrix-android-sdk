package org.matrix.matrixandroidsdk;

import android.app.AlertDialog;
import android.content.Intent;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import org.matrix.androidsdk.MXApiClient;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomSummary;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.ApiCallback;
import org.matrix.androidsdk.rest.model.CreateRoomResponse;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.matrixandroidsdk.adapters.RoomSummaryAdapter;
import org.matrix.matrixandroidsdk.adapters.RoomsAdapter;
import org.matrix.matrixandroidsdk.services.EventStreamService;

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
                    loadSummaries();
                }
            });
        }

        @Override
        public void onMessageReceived(Room room, final Event event) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mAdapter.setLatestEvent(event);
                }
            });
        }

        @Override
        public void onRoomStateUpdated(Room room, final Event event, Object oldVal, Object newVal) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    String selfUserId = mSession.getCredentials().userId;

                    RoomSummary summary = mAdapter.getSummaryByRoomId(event.roomId);
                    if (summary == null) {
                        // ROOM_CREATE events will be sent during initial sync. We want to ignore them
                        // until the initial sync is done (that is, only refresh the list when there
                        // are new rooms created AFTER we have synced).
                        if (mInitialSyncComplete) {
                            if (Event.EVENT_TYPE_STATE_ROOM_CREATE.equals(event.type)) {
                                refreshAdapter();
                            }
                            else if (Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(event.type)) {
                                try {
                                    if (RoomMember.MEMBERSHIP_INVITE.equals(event.content.getAsJsonPrimitive("membership").getAsString()) &&
                                            event.stateKey.equals(selfUserId)) {
                                        // we were invited to a new room.
                                        refreshAdapter();
                                    }
                                }
                                catch (Exception e) {} // bad json
                            }
                        }
                        return;
                    }
                    summary.setLatestEvent(event);

                    if (mInitialSyncComplete && Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(event.type) &&
                            isMembershipInRoom(RoomMember.MEMBERSHIP_LEAVE, selfUserId, summary)) {
                        // we've left this room, so refresh the entire list.
                        refreshAdapter();
                        return;
                    }


                    if (Event.EVENT_TYPE_STATE_ROOM_NAME.equals(event.type)) {
                        try {
                            summary.setName(event.content.getAsJsonPrimitive("name").getAsString());
                        }
                        catch (Exception e) {} // malformed json, discard.
                    }
                    else if (Event.EVENT_TYPE_STATE_ROOM_ALIASES.equals(event.type)) {
                        // force reload on aliases change so it can load the right name/alias
                        refreshAdapter();
                        return;
                    }
                    mAdapter.sortSummaries();
                    mAdapter.notifyDataSetChanged();
                }
            });
        }

        private void refreshAdapter() {
            mAdapter.clear();
            loadSummaries();
        }

        private boolean isMembershipInRoom(String membership, String selfUserId, RoomSummary summary) {
            for (RoomMember member : summary.getMembers()) {
                if (membership.equals(member.membership) &&
                        selfUserId.equals(member.userId)) {
                    return true;
                }
            }
            return false;
        }

        private void loadSummaries() {
            String selfUserId = mSession.getCredentials().userId;
            for (RoomSummary summary : mSession.getDataHandler().getStore().getSummaries()) {
                boolean isInvited = isMembershipInRoom(RoomMember.MEMBERSHIP_INVITE, selfUserId, summary);
                if (isInvited) {
                    summary.setName("Room Invitation");
                }

                // only add summaries to rooms we have not left.
                if (!isMembershipInRoom(RoomMember.MEMBERSHIP_LEAVE, selfUserId, summary)) {
                    mAdapter.add(summary);
                }
            }
            mAdapter.sortSummaries();
        }
    };

    private MXSession mSession;
    private RoomSummaryAdapter mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

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

        startEventStream();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mSession.getDataHandler().removeListener(mListener);
    }

    public void startEventStream() {
        Intent intent = new Intent(this, EventStreamService.class);
        // TODO Add args to specify which session.
        startService(intent);
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
        if (id == R.id.action_settings) {
            return true;
        }
        else if (id == R.id.action_public_rooms) {
            goToPublicRoomPage();
            return true;
        }
        else if (id == R.id.action_logout) {
            CommonActivityUtils.logout(this);
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
        if (isPublic) {
            AlertDialog alert = CommonActivityUtils.createEditTextAlert(this, "Set Room Alias", "alias-name", new CommonActivityUtils.OnSubmitListener() {
                @Override
                public void onSubmit(String text) {
                    if (text.length() == 0) {
                        return;
                    }
                    MXSession session = Matrix.getInstance(getApplicationContext()).getDefaultSession();
                    session.getRoomsApiClient().createRoom(null, null, "public", text, new MXApiClient.SimpleApiCallback<CreateRoomResponse>() {

                        @Override
                        public void onSuccess(CreateRoomResponse info) {
                            goToRoomPage(info.roomId);
                        }
                    });
                }

                @Override
                public void onCancelled() {}
            });
            alert.show();
        }
        else {
            AlertDialog alert = CommonActivityUtils.createEditTextAlert(this, "Set Room Name", "My Room", new CommonActivityUtils.OnSubmitListener() {
                @Override
                public void onSubmit(String text) {
                    if (text.length() == 0) {
                        return;
                    }
                    MXSession session = Matrix.getInstance(getApplicationContext()).getDefaultSession();
                    session.getRoomsApiClient().createRoom(text, null, "private", null, new MXApiClient.SimpleApiCallback<CreateRoomResponse>() {

                        @Override
                        public void onSuccess(CreateRoomResponse info) {
                            goToRoomPage(info.roomId);
                        }
                    });
                }

                @Override
                public void onCancelled() {}
            });
            alert.show();
        }
    }


}
