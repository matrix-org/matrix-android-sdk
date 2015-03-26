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

    // any network state listener
    private List<IMXNetworkEventListener> mNetworkEventListeners = new ArrayList<IMXNetworkEventListener>();

    // the one call listeners are listeners which are expected to be called ONCE
    // the device is connected to a data network
    private List<IMXNetworkEventListener> mOnNetworkConnectedEventListeners = new ArrayList<IMXNetworkEventListener>();

    private boolean mIsConnected = false;

    @Override
    public void onReceive(final Context context, final Intent intent) {
        try {
            ConnectivityManager connMgr = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);

            // avoid triggering useless info
            if (mIsConnected != (connMgr.getActiveNetworkInfo() != null)) {
                mIsConnected = connMgr.getActiveNetworkInfo() != null;
                onNetworkUpdate();
            }
        }
        catch (Exception e) {
            Log.e(LOG_TAG, "Failed to report connectivity: " + e.getLocalizedMessage());
        }
    }

    /**
     * Clear the events listener data.
     */
    public void clear() {
        mNetworkEventListeners.clear();
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
     * Add a ONE CALL network event listener.
     * The listener is called when a data connection is established.
     * The listener is removed from the listeners list once its callback is called.
     * @param networkEventListener the event listener to add
     */
    public void addOnConnectedEventListener(final IMXNetworkEventListener networkEventListener) {
        if (null != networkEventListener) {
            mOnNetworkConnectedEventListeners.add(networkEventListener);
        }
    }

    /**
     * Remove a network event listener.
     * @param networkEventListener the event listener to remove
     */
    public void removeEventListener(final IMXNetworkEventListener networkEventListener) {
        mNetworkEventListeners.remove(networkEventListener);
        mOnNetworkConnectedEventListeners.remove(networkEventListener);
    }

    /**
     * Warn the listener that a network updated has been triggered
     */
    public synchronized void onNetworkUpdate() {
        for (IMXNetworkEventListener listener : mNetworkEventListeners) {
            try {
                listener.onNetworkConnectionUpdate(mIsConnected);
            } catch (Exception e) {
            }
        }

        // onConnected listeners are called once
        // and only when there is an available network connection
        if (mIsConnected) {
            for (IMXNetworkEventListener listener : mOnNetworkConnectedEventListeners) {
                try {
                    listener.onNetworkConnectionUpdate(mIsConnected);
                } catch(Exception e) {

                }
            }
            mOnNetworkConnectedEventListeners.clear();
        }
    }

    /**
     * @return true if the application is connected to a data network
     */
    public boolean isConnected() {
        return mIsConnected;
    }
}
