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
package org.matrix.androidsdk;

import org.matrix.androidsdk.api.response.login.Credentials;
import org.matrix.androidsdk.rest.client.EventsApiClient;
import org.matrix.androidsdk.rest.client.LoginApiClient;
import org.matrix.androidsdk.rest.client.PresenceApiClient;
import org.matrix.androidsdk.rest.client.ProfileApiClient;
import org.matrix.androidsdk.rest.client.RegistrationApiClient;
import org.matrix.androidsdk.rest.client.RoomsApiClient;
import org.matrix.androidsdk.sync.EventsThread;
import org.matrix.androidsdk.sync.EventsThreadListener;

/**
 * Class that represents the Matrix session.
 */
public class MXSession {

    private MXData mData;
    private EventsThread mEventsThread;
    private Credentials mCredentials;

    // Api clients
    EventsApiClient mEventsApiClient = new EventsApiClient();
    ProfileApiClient mProfileApiClient = new ProfileApiClient();
    PresenceApiClient mPresenceApiClient = new PresenceApiClient();
    LoginApiClient mLoginApiClient = new LoginApiClient();
    RegistrationApiClient mRegistrationApiClient = new RegistrationApiClient();
    RoomsApiClient mRoomsApiClient = new RoomsApiClient();

    public MXSession(MXData mxData) {
        mData = mxData;
        mEventsThread = new EventsThread(mEventsApiClient, new EventsThreadListener(mxData));
    }

    public MXSession(MXData mxData, Credentials credentials) {
        this(mxData);
        setCredentials(credentials);
    }

    public void setCredentials(Credentials credentials) {
        mCredentials = credentials;
        mEventsApiClient.setCredentials(credentials);
        mProfileApiClient.setCredentials(credentials);
        mPresenceApiClient.setCredentials(credentials);
        mLoginApiClient.setCredentials(credentials);
        mRegistrationApiClient.setCredentials(credentials);
        mRoomsApiClient.setCredentials(credentials);
    }

    public MXData getData() {
        return mData;
    }

    public EventsApiClient getEventsApiClient() {
        return mEventsApiClient;
    }

    public ProfileApiClient getProfileApiClient() {
        return mProfileApiClient;
    }

    public PresenceApiClient getPresenceApiClient() {
        return mPresenceApiClient;
    }

    public LoginApiClient getLoginApiClient() {
        return mLoginApiClient;
    }

    public RegistrationApiClient getRegistrationApiClient() {
        return mRegistrationApiClient;
    }

    public RoomsApiClient getRoomsApiClient() {
        return mRoomsApiClient;
    }

    protected void setEventsApiClient(EventsApiClient eventsApiClient) {
        this.mEventsApiClient = eventsApiClient;
    }

    protected void setProfileApiClient(ProfileApiClient profileApiClient) {
        this.mProfileApiClient = profileApiClient;
    }

    protected void setPresenceApiClient(PresenceApiClient presenceApiClient) {
        this.mPresenceApiClient = presenceApiClient;
    }

    protected void setLoginApiClient(LoginApiClient loginApiClient) {
        this.mLoginApiClient = loginApiClient;
    }

    protected void setRegistrationApiClient(RegistrationApiClient registrationApiClient) {
        this.mRegistrationApiClient = registrationApiClient;
    }

    protected void setRoomsApiClient(RoomsApiClient roomsApiClient) {
        this.mRoomsApiClient = roomsApiClient;
    }

    public void startEventStream() {
        if (mCredentials.accessToken != null) {
            mEventsThread.start();
        }
    }

    public void stopEventStream() {
        // TODO: Actually stop the thread
    }
}
