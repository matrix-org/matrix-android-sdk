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
        // TODO: This is surprising: the expectation of a simple setFoo would be a simple assignment
        // and nothing more. This should be split out into another method, or this method should be
        // renamed.
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
