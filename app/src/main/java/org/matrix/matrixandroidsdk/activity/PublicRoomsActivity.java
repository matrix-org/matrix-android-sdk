/*
 * Copyright 2015 OpenMarket Ltd
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

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.GridView;

import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.PublicRoom;
import org.matrix.matrixandroidsdk.ErrorListener;
import org.matrix.matrixandroidsdk.Matrix;
import org.matrix.matrixandroidsdk.MyPresenceManager;
import org.matrix.matrixandroidsdk.R;
import org.matrix.matrixandroidsdk.adapters.RoomsAdapter;

import java.util.List;


/**
 * Displays a list of available public rooms.
 */
public class PublicRoomsActivity extends MXCActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_public_rooms);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        final GridView publicRoomsGridView = (GridView) findViewById(R.id.gridView_publicRoomList);
        final RoomsAdapter adapter = new RoomsAdapter(this, R.layout.adapter_item_public_rooms);
        adapter.setAlternatingColours(0xFFFFFFFF, 0xFFEEEEEE);
        publicRoomsGridView.setAdapter(adapter);
        Matrix.getInstance(getApplicationContext()).getDefaultSession().getEventsApiClient().loadPublicRooms(new SimpleApiCallback<List<PublicRoom>>(this) {
            @Override
            public void onSuccess(List<PublicRoom> publicRooms) {
                for (PublicRoom publicRoom : publicRooms) {
                    adapter.add(publicRoom);
                }
                adapter.sortRooms();
            }
        });

        publicRoomsGridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                RoomState room = adapter.getItem(i);
                String roomId = room.roomId;
                goToRoom(roomId);
            }
        });

        // The error listener needs the current activity
        Matrix.getInstance(getApplicationContext()).getDefaultSession().setFailureCallback(new ErrorListener(this));
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
        getMenuInflater().inflate(R.menu.public_rooms, menu);
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

        if (id == R.id.action_my_rooms) {
            finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void goToRoom(String roomId) {
        Intent intent = new Intent(this, RoomActivity.class);
        intent.putExtra(RoomActivity.EXTRA_ROOM_ID, roomId);
        startActivity(intent);
    }
}
