package org.matrix.androidsdk.sync;

import org.matrix.androidsdk.MXData;

/**
 * Created by JOACHIMR on 02/10/2014.
 */
public class EventsThreadListener implements EventsThread.SyncThreadListener {

    MXData mData;

    public EventsThreadListener(MXData data) {
        mData = data;
    }

    @Override
    public void onInitialSyncComplete() {

    }

    @Override
    public void onEventReceived() {

    }
}
