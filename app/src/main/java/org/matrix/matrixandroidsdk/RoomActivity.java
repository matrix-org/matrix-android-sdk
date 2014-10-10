package org.matrix.matrixandroidsdk;

import android.content.Intent;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;


public class RoomActivity extends ActionBarActivity {

    public static final String EXTRA_ROOM_ID = "org.matrix.matrixandroidsdk.RoomActivity.EXTRA_ROOM_ID";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_room);

        Intent intent = getIntent();
        if (intent.hasExtra(EXTRA_ROOM_ID)) {
            String roomId = intent.getStringExtra(EXTRA_ROOM_ID);
            Toast.makeText(RoomActivity.this, "Display >> " + roomId, Toast.LENGTH_SHORT).show();
        }

        // TODO: join room if you need to (check with Matrix singleton)
        // TODO: Request messages/state if you need to.
        // TODO: Load up MatrixMessageListFragment to display messages.
        // TODO: Get the Room instance being represent to get the room name/topic/etc

    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.room, menu);
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
