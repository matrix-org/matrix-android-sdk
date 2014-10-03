package org.matrix.matrixandroidsdk;

import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import org.matrix.androidsdk.MXApiService;
import org.matrix.androidsdk.api.response.PublicRoom;

import java.util.List;

public class MainActivity extends ActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MXApiService apiService = new MXApiService("matrix.org");

        final TextView mainTextView = (TextView) findViewById(R.id.main_text);
        apiService.loadPublicRooms(new MXApiService.LoadPublicRoomsCallback() {
            @Override
            public void onRoomsLoaded(List<PublicRoom> publicRooms) {
                StringBuilder sb = new StringBuilder();
                for (PublicRoom publicRoom : publicRooms) {
                    sb.append(publicRoom.getName())
                            .append("\n");
                }
                mainTextView.setText(sb.toString());
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
