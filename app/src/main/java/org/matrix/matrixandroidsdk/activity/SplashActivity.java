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

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.listeners.IMXEventListener;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.matrixandroidsdk.ErrorListener;
import org.matrix.matrixandroidsdk.Matrix;
import org.matrix.matrixandroidsdk.R;
import org.matrix.matrixandroidsdk.gcm.GcmRegistrationManager;
import org.matrix.matrixandroidsdk.services.EventStreamService;

import java.util.Collection;
import java.util.HashMap;

public class SplashActivity extends MXCActionBarActivity {

    private Collection<MXSession> mSessions;
    private GcmRegistrationManager mGcmRegistrationManager;

    private boolean mInitialSyncComplete = false;
    private boolean mPusherRegistrationComplete = false;

    private HashMap<MXSession, IMXEventListener> mListeners;
    private HashMap<MXSession, IMXEventListener> mDoneListeners;

    private void finishIfReady() {
        if (mInitialSyncComplete && mPusherRegistrationComplete) {
            // Go to the home page
            startActivity(new Intent(SplashActivity.this, HomeActivity.class));
            SplashActivity.this.finish();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_splash);

        mSessions =  Matrix.getInstance(getApplicationContext()).getSessions();

        if (mSessions == null) {
            finish();
            return;
        }

        mListeners = new HashMap<MXSession, IMXEventListener>();
        mDoneListeners = new HashMap<MXSession, IMXEventListener>();

        for(MXSession session : mSessions) {

            final MXSession fSession = session;

            final IMXEventListener eventListener = new MXEventListener() {
                @Override
                public void onInitialSyncComplete(String accountId) {
                    super.onInitialSyncComplete(accountId);
                    Boolean noMoreListener;

                    synchronized(mListeners) {
                        mDoneListeners.put(fSession, mListeners.get(fSession));
                        // do not remove the listeners here
                        // it crashes the application because of the upper loop
                        //fSession.getDataHandler().removeListener(mListeners.get(fSession));
                        // remove from the pendings list

                        mListeners.remove(fSession);
                        noMoreListener = mInitialSyncComplete = (mListeners.size() == 0);
                    }

                    if (noMoreListener) {
                        finishIfReady();
                    }
                }
            };

            mListeners.put(fSession, eventListener);
            fSession.getDataHandler().addListener(eventListener);

            // Start the event stream service
            Intent intent = new Intent(this, EventStreamService.class);
            intent.putExtra(EventStreamService.EXTRA_ACCOUNT_ID, fSession.getCredentials().userId);
            intent.putExtra(EventStreamService.EXTRA_STREAM_ACTION, EventStreamService.StreamAction.START.ordinal());
            startService(intent);

            // Set the main error listener
            fSession.setFailureCallback(new ErrorListener(this));
        }

        mGcmRegistrationManager = Matrix.getInstance(getApplicationContext())
                .getSharedGcmRegistrationManager();
        mGcmRegistrationManager.setListener(new GcmRegistrationManager.GcmRegistrationIdListener() {
            @Override
            public void onPusherRegistered() {
                mPusherRegistrationComplete = true;
                finishIfReady();
            }
        });
        mGcmRegistrationManager.registerPusherInBackground();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        Collection<MXSession> sessions = mDoneListeners.keySet();

        for(MXSession session : sessions) {
            session.getDataHandler().removeListener(mDoneListeners.get(session));
        }

        mGcmRegistrationManager.setListener(null);
    }
}
