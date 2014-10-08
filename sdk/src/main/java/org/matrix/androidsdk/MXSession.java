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

    private String mAccessToken = null;

    public MXSession(MXApiClient apiService, MXData mxData) {
        mApiClient = apiService;
        mData = mxData;

        mEventsThread = new EventsThread(mApiClient, new EventsThreadListener(mData));
    }

    public void startEventStream(String accessToken) {
        mAccessToken = accessToken;
        mApiClient.setAccessToken(accessToken);
        if (accessToken != null) {
            mEventsThread.start();
        }
    }

    public boolean isLoggedIn() {
        return mAccessToken != null;
    }

    public MXApiClient getApiService() {
        return mApiClient;
    }

    public MXData getData() {
        return mData;
    }
}
