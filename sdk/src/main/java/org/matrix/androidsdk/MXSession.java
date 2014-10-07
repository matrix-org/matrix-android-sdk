package org.matrix.androidsdk;

import org.matrix.androidsdk.sync.EventsThread;
import org.matrix.androidsdk.sync.EventsThreadListener;

/**
 * Class that represents the Matrix session.
 */
public class MXSession {

    private MXApiService mApiService;
    private MXData mData;
    private EventsThread mEventsThread;

    private String mAccessToken = null;

    public MXSession(MXApiService apiService, MXData mxData) {
        mApiService = apiService;
        mData = mxData;

        mEventsThread = new EventsThread(mApiService, new EventsThreadListener(mData));
    }

    public void setAccessToken(String accessToken) {
        mAccessToken = accessToken;
        mApiService.setAccessToken(accessToken);
        if (accessToken != null) {
            mEventsThread.start();
        }
    }

    public boolean isLoggedIn() {
        return mAccessToken != null;
    }

    public MXApiService getApiService() {
        return mApiService;
    }

    public MXData getData() {
        return mData;
    }
}
