package org.matrix.matrixandroidsdk;

import android.content.Intent;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.GridView;
import android.widget.TextView;

import org.matrix.androidsdk.MXApiClient;
import org.matrix.androidsdk.MXData;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.api.response.PublicRoom;
import org.matrix.androidsdk.api.response.login.Credentials;
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
        final MXSession matrixSession = new MXSession(client, new MXData());
        matrixSession.startEventStream();

        final TextView userRoomsTextView = (TextView) findViewById(R.id.urooms_text);
        matrixSession.getData().addGlobalRoomDataListener(new MXData.DataUpdateListener() {
            @Override
            public void onUpdate() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        StringBuilder sb = new StringBuilder();
                        for (Room room : matrixSession.getData().getRooms()) {
                            sb.append(room.getRoomId())
                                    .append("\n");
                        }
                        userRoomsTextView.setText(sb.toString());
                    }
                });
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
}
