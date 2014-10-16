package org.matrix.matrixandroidsdk;

import android.app.AlertDialog;
import android.content.Intent;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import org.matrix.androidsdk.MXApiClient;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.model.CreateRoomResponse;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.matrixandroidsdk.adapters.RoomsAdapter;
import org.matrix.matrixandroidsdk.services.EventStreamService;

/**
 * Displays the main screen of the app, with rooms the user has joined and the ability to create
 * new rooms.
 */
public class HomeActivity extends ActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        final MXSession matrixSession = Matrix.getInstance(getApplicationContext()).getDefaultSession();
        if (matrixSession == null) {
            finish();
            return;
        }

        final ListView myRoomList = (ListView)findViewById(R.id.listView_myRooms);
        final RoomsAdapter adapter = new RoomsAdapter(this, R.layout.adapter_item_my_rooms);
        myRoomList.setAdapter(adapter);

        matrixSession.getDataHandler().addListener(new MXEventListener() {
            @Override
            public void onInitialSyncComplete() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        for (Room room : matrixSession.getDataHandler().getStore().getRooms()) {
                            adapter.add(room.getRoomState());
                        }
                        adapter.sortRooms();
                    }
                });
            }
        });

        myRoomList.setOnItemClickListener(new AdapterView.OnItemClickListener(){

            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                goToRoomPage(adapter.getItem(i).roomId);
            }
        });

        findViewById(R.id.button_newPrivateRoom).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                createRoom(false);
            }
        });

        findViewById(R.id.button_newPublicRoom).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                createRoom(true);
            }
        });

        startEventStream();
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
                    session.getRoomsApiClient().createRoom(null, null, "public", text, new MXApiClient.ApiCallback<CreateRoomResponse>() {

                        @Override
                        public void onSuccess(CreateRoomResponse info) {
                            goToRoomPage(info.roomId);
                        }

                        @Override
                        public void onNetworkError(Exception e) {

                        }

                        @Override
                        public void onMatrixError(MatrixError e) {

                        }

                        @Override
                        public void onUnexpectedError(Exception e) {

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
                    session.getRoomsApiClient().createRoom(text, null, "private", null, new MXApiClient.ApiCallback<CreateRoomResponse>() {

                        @Override
                        public void onSuccess(CreateRoomResponse info) {
                            goToRoomPage(info.roomId);
                        }

                        @Override
                        public void onNetworkError(Exception e) {

                        }

                        @Override
                        public void onMatrixError(MatrixError e) {

                        }

                        @Override
                        public void onUnexpectedError(Exception e) {

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
