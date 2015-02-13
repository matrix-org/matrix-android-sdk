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
package org.matrix.androidsdk.network;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.util.Log;

import org.matrix.androidsdk.listeners.IMXEventListener;
import org.matrix.androidsdk.listeners.IMXNetworkEventListener;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.User;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NetworkConnectivityReceiver extends BroadcastReceiver {
    private static final String LOG_TAG = "NetworkConnectivityReceiver";

    private List<IMXNetworkEventListener> mNetworkEventListeners = new ArrayList<IMXNetworkEventListener>();

    private boolean mIsConnected = false;

    @Override
    public void onReceive(final Context context, final Intent intent) {
        try {
            ConnectivityManager connMgr = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
            mIsConnected = connMgr.getActiveNetworkInfo() != null;
            onNetworkUpdate();
        }
        catch (Exception e) {
            Log.e(LOG_TAG, "Failed to report connectivity: "+e);
        }
    }

    /**
     * Add a network event listener.
     * @param networkEventListener the event listener to add
     */
    public void addEventListener(final IMXNetworkEventListener networkEventListener) {
        if (null != networkEventListener) {
            mNetworkEventListeners.add(networkEventListener);
        }
    }

    /**
     * Remove a network event listener.
     * @param networkEventListener the event listener to remove
     */
    public void removeEventListener(final IMXNetworkEventListener networkEventListener) {
        mNetworkEventListeners.remove(networkEventListener);
    }

    /**
     * Warn the listener that a network updated has been triggered
     */
    public synchronized void onNetworkUpdate() {
        for(IMXNetworkEventListener listener : mNetworkEventListeners) {
            listener.onNetworkConnectionUpdate(mIsConnected);
        }
    }
}
