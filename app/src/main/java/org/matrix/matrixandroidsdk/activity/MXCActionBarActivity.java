/*
 * Copyright 2014 OpenMarket Ltd
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

import android.support.v7.app.ActionBarActivity;
import android.view.KeyEvent;
import android.view.MenuItem;

import org.matrix.androidsdk.MXSession;
import org.matrix.matrixandroidsdk.ConsoleApplication;
import org.matrix.matrixandroidsdk.Matrix;
import org.matrix.matrixandroidsdk.services.EventStreamService;

/**
 * extends ActionBarActivity to manage the rageshake
 */
public class MXCActionBarActivity extends ActionBarActivity {

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_MENU) && (null == getActionBar())) {
            // This is to fix a bug in the v7 support lib. If there is no options menu and you hit MENU, it will crash with a
            // NPE @ android.support.v7.app.ActionBarImplICS.getThemedContext(ActionBarImplICS.java:274)
            // This can safely be removed if we add in menu options on this screen
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ConsoleApplication.setCurrentActivity(null);

        ((ConsoleApplication)getApplication()).startActivityTransitionTimer();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ConsoleApplication.setCurrentActivity(this);

        // refresh the push rules when debackgrounding the application
        if (((ConsoleApplication)getApplication()).isInBackground) {
            Matrix matrixInstance =  Matrix.getInstance(getApplicationContext());

            // sanity check
            if (null != matrixInstance) {
                final MXSession session = matrixInstance.getDefaultSession();

                if ((null != session) && (null != session.getDataHandler())) {
                    session.getDataHandler().refreshPushRules();
                }
            }

            EventStreamService.cancelNotificationsForRoomId(null);
        }

        ((ConsoleApplication)getApplication()).stopActivityTransitionTimer();
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            // pop the activity to avoid creating a new instance of the parent activity
            this.onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
