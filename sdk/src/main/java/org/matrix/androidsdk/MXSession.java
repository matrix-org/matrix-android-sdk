package org.matrix.androidsdk;

import org.matrix.androidsdk.sync.EventsThread;
import org.matrix.androidsdk.sync.EventsThreadListener;

/**
 * Class that represents the Matrix session.
 */
public class MXSession {

    private MXApiClient mApiClient;
    private MXData mData;
    private EventsThread mEventsThread;

    public MXSession(MXApiClient apiService, MXData mxData) {
        mApiClient = apiService;
        mData = mxData;

        mEventsThread = new EventsThread(mApiClient, new EventsThreadListener(mData));
    }

    public void startEventStream() {
        if (mApiClient.getCredentials().accessToken != null) {
            mEventsThread.start();
        }
    }

    public MXApiClient getApiClient() {
        return mApiClient;
    }

    public MXData getData() {
        return mData;
    }
}
