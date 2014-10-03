package org.matrix.androidsdk;

import org.matrix.androidsdk.sync.EventsThread;
import org.matrix.androidsdk.sync.EventsThreadListener;

/**
 * Singleton that represents the Matrix session.
 */
public class MXSession {

    private MXApiService mApiService;
    private MXData mData;
    private EventsThread mEventsThread;
    private String mAccessToken;

    public MXSession(String hsDomain) {
        mApiService = new MXApiService(hsDomain);
        mData = new MXData();

        mEventsThread = new EventsThread(mApiService, new EventsThreadListener(mData));
        if (isLoggedIn()) {
            mEventsThread.start();
        }
    }

    private void setAccessToken(String accessToken) {
        mAccessToken = accessToken;
        mApiService.setAccessToken(accessToken);
    }

    public boolean isLoggedIn() {
        return mAccessToken != null;
    }
}
