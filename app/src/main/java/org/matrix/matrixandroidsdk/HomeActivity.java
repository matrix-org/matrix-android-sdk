package org.matrix.matrixandroidsdk;

import android.content.Intent;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.ListView;
import android.widget.TextView;

import org.matrix.androidsdk.MXApiClient;
import org.matrix.androidsdk.MXData;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.api.response.PublicRoom;
import org.matrix.androidsdk.api.response.login.Credentials;
import org.matrix.androidsdk.data.MXMemoryStore;
import org.matrix.androidsdk.data.Room;
import org.matrix.matrixandroidsdk.adapters.RoomsAdapter;

import java.util.List;

public class HomeActivity extends ActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        Credentials creds = new Credentials();
        creds.accessToken = "";
        creds.homeServer = "https://matrix.org";
        creds.userId = "";
        MXApiClient client = new MXApiClient("matrix.org");
        client.setCredentials(creds);
        final MXSession matrixSession = new MXSession(client, new MXData(new MXMemoryStore()));
        matrixSession.startEventStream();

        final ListView myRoomList = (ListView)findViewById(R.id.listView_myRooms);
        final RoomsAdapter adapter = new RoomsAdapter(this, R.layout.adapter_item_my_rooms);
        myRoomList.setAdapter(adapter);

        matrixSession.getData().addGlobalRoomDataListener(new MXData.DataUpdateListener() {
            @Override
            public void onUpdate() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        for (Room room : matrixSession.getData().getRooms()) {
                            adapter.addIfNotExist(room.getRoomState());
                        }
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

    private void createRoom(boolean isPublic) {
        if (isPublic) {
            // TODO: Create dialog to get a room alias
            // TODO: Then request to create room
            String allocatedRoomId = "";
            goToRoomPage(allocatedRoomId);
        }
        else {
            // TODO: Request to create room
            String allocatedRoomId = "";
            goToRoomPage(allocatedRoomId);
        }
    }
}
