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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.listeners.IMXEventListener;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.matrixandroidsdk.ErrorListener;
import org.matrix.matrixandroidsdk.Matrix;
import org.matrix.matrixandroidsdk.R;
import org.matrix.matrixandroidsdk.services.EventStreamService;

public class SplashActivity extends MXCActionBarActivity {

    private MXSession mSession;

    private IMXEventListener mEventListener = new MXEventListener() {
        @Override
        public void onInitialSyncComplete() {
            super.onInitialSyncComplete();

            // Go to the home page
            startActivity(new Intent(SplashActivity.this, HomeActivity.class));
            SplashActivity.this.finish();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_splash);

        mSession = Matrix.getInstance(getApplicationContext()).getDefaultSession();
        if (mSession == null) {
            finish();
            return;
        }

        mSession.getDataHandler().addListener(mEventListener);

        // Start the event stream service
        Intent intent = new Intent(this, EventStreamService.class);
        intent.putExtra(EventStreamService.EXTRA_STREAM_ACTION, EventStreamService.StreamAction.START.ordinal());
        // TODO Add args to specify which session.
        startService(intent);

        // Set the main error listener
        Matrix.getInstance(getApplicationContext()).getDefaultSession().setFailureCallback(new ErrorListener(this));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mSession.getDataHandler().removeListener(mEventListener);
    }
}
