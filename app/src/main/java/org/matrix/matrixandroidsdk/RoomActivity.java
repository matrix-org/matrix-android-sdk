package org.matrix.matrixandroidsdk;

import android.content.Intent;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.matrix.androidsdk.MXApiClient;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.api.response.Event;
import org.matrix.androidsdk.api.response.TokensChunkResponse;
import org.matrix.androidsdk.data.Room;
import org.matrix.matrixandroidsdk.adapters.MessagesAdapter;


public class RoomActivity extends ActionBarActivity {

    public static final String EXTRA_ROOM_ID = "org.matrix.matrixandroidsdk.RoomActivity.EXTRA_ROOM_ID";

    private static final String LOG_TAG = "RoomActivity";

    private MXSession mSession;
    private String mRoomId;
    private String mLast;
    private MessagesAdapter mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_room);

        Intent intent = getIntent();
        if (!intent.hasExtra(EXTRA_ROOM_ID)) {
            Log.e(LOG_TAG, "No room ID extra.");
            finish();
            return;
        }
        String roomId = intent.getStringExtra(EXTRA_ROOM_ID);
        Toast.makeText(RoomActivity.this, "Display >> " + roomId, Toast.LENGTH_SHORT).show();

        MXSession session = Matrix.getInstance(getApplicationContext()).getDefaultSession();
        if (session == null) {
            Log.e(LOG_TAG, "No MXSession.");
            finish();
            return;
        }
        mSession = session;
        mRoomId = roomId;

        Room room = mSession.getDataHandler().getStore().getRoom(roomId);
        String title = room.getName();
        if (title == null) {
            title = room.getRoomId();
        }
        setTitle(title);

        TextView topicView = ((TextView)findViewById(R.id.textView_roomTopic));
        topicView.setText(room.getTopic());


        final ListView messageListView = ((ListView)findViewById(R.id.listView_messages));
        final MessagesAdapter adapter = new MessagesAdapter(this,
                R.layout.adapter_item_messages,
                R.layout.adapter_item_images
        );
        messageListView.setAdapter(adapter);
        mAdapter = adapter;

        mSession.getRoomsApiClient().getLatestRoomMessages(roomId, new MXApiClient.ApiCallback<TokensChunkResponse<Event>>() {
            @Override
            public void onSuccess(TokensChunkResponse<Event> info) {
                // add in reversed order since they come down in reversed order (newest first)
                for (int i=info.chunk.size()-1; i>=0; i--) {
                    adapter.add(info.chunk.get(i));
                }
                mLast = info.end;
            }
        });
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
        else if (id == R.id.action_load_more) {
            mSession.getRoomsApiClient().getEarlierMessages(mRoomId, mLast, new MXApiClient.ApiCallback<TokensChunkResponse<Event>>() {

                @Override
                public void onSuccess(TokensChunkResponse<Event> info) {
                    // add to top of list.
                    for (Event event : info.chunk) {
                        mAdapter.addToFront(event);
                    }
                    mLast = info.end;
                }
            });
        }
        return super.onOptionsItemSelected(item);
    }
}
