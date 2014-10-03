package org.matrix.androidsdk.sync;

import org.matrix.androidsdk.MXApiService;

/**
 * Created by JOACHIMR on 02/10/2014.
 */
public class EventsThread extends Thread {

    MXApiService mApiService;
    SyncThreadListener mListener;

    public EventsThread(MXApiService apiService, SyncThreadListener listener) {
        super("Sync thread");
        mListener = listener;
    }

    public interface SyncThreadListener {
        public void onInitialSyncComplete();
        public void onEventReceived();
    }

    @Override
    public void run() {
        // Start with initial sync
        mApiService.initialSync();
    }
}
