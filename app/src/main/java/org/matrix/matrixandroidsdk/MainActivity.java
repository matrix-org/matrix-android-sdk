package org.matrix.matrixandroidsdk;

import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import org.matrix.androidsdk.MXApiService;
import org.matrix.androidsdk.MXData;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.api.response.PublicRoom;
import org.matrix.androidsdk.api.response.User;
import org.matrix.androidsdk.data.Room;

import java.util.List;

public class MainActivity extends ActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final MXSession matrixSession = new MXSession("matrix.org");

        final TextView publicRoomsTextView = (TextView) findViewById(R.id.prooms_text);
        matrixSession.getApiService().loadPublicRooms(new MXApiService.LoadPublicRoomsCallback() {
            @Override
            public void onRoomsLoaded(List<PublicRoom> publicRooms) {
                StringBuilder sb = new StringBuilder();
                for (PublicRoom publicRoom : publicRooms) {
                    sb.append(publicRoom.name)
                            .append("\n");
                }
                publicRoomsTextView.setText(sb.toString());
            }
        });

        final TextView usersTextView = (TextView) findViewById(R.id.users_text);
        matrixSession.getData().addUserDataListener(new MXData.DataUpdateListener() {
            @Override
            public void onUpdate() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        StringBuilder sb = new StringBuilder();
                        for (User user : matrixSession.getData().getUsers()) {
                            sb.append(user.userId)
                                    .append("\n");
                        }
                        usersTextView.setText(sb.toString());
                    }
                });
            }
        });

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
        getMenuInflater().inflate(R.menu.main, menu);
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
        return super.onOptionsItemSelected(item);
    }
}
