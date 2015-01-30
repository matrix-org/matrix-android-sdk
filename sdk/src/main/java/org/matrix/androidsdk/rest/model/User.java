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
package org.matrix.androidsdk.rest.model;

import org.matrix.androidsdk.MXDataHandler;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.listeners.IMXEventListener;
import org.matrix.androidsdk.listeners.MXEventListener;

import java.util.HashMap;
import java.util.Map;

/**
 * Class representing a user.
 */
public class User {
    public static final String PRESENCE_ONLINE = "online";
    public static final String PRESENCE_UNAVAILABLE = "unavailable";
    public static final String PRESENCE_OFFLINE = "offline";
    public static final String PRESENCE_FREE_FOR_CHAT = "free_for_chat";
    public static final String PRESENCE_HIDDEN = "hidden";

    public String userId;
    public String displayname;
    public String avatarUrl;
    public String presence;
    public Long lastActiveAgo;
    public String statusMsg;

    // Used to provide a more realistic last active time:
    // the last active ago time provided by the server + the time that has gone by since
    private long lastPresenceTs;

    // Map to keep track of the listeners the client adds vs. the ones we actually register to the global data handler.
    // This is needed to find the right one when removing the listener.
    private Map<IMXEventListener, IMXEventListener> mEventListeners = new HashMap<IMXEventListener, IMXEventListener>();

    private MXDataHandler mDataHandler;

    protected void clone(User user) {
        if (user != null) {
            userId = user.userId;
            displayname = user.displayname;
            avatarUrl = user.avatarUrl;
            presence = user.presence;
            lastActiveAgo = user.lastActiveAgo;
            statusMsg = user.statusMsg;

            mDataHandler = user.mDataHandler;
        }
    }

    public User deepCopy() {
        User copy = new User();
        copy.clone(this);
        return copy;
    }

    /**
     * Sets the last-active-ago-time received time to now.
     */
    public void lastActiveReceived() {
        lastPresenceTs = System.currentTimeMillis();
    }

    /**
     * Get the user's last active ago time by adding the one given by the server and the time since elapsed.
     * @return how long ago the user was last active (in ms)
     */
    public long getRealLastActiveAgo() {
        return lastActiveAgo + System.currentTimeMillis() - lastPresenceTs;
    }

    /**
     * Set the event listener to send back events to. This is typically the DataHandler for dispatching the events to listeners.
     * @param dataHandler should be the main data handler for dispatching back events to registered listeners.
     */
    public void setDataHandler(MXDataHandler dataHandler) {
        mDataHandler = dataHandler;
    }

    /**
     * Add an event listener to this room. Only events relative to the room will come down.
     * @param eventListener the event listener to add
     */
    public void addEventListener(final IMXEventListener eventListener) {
        // Create a global listener that we'll add to the data handler
        IMXEventListener globalListener = new MXEventListener() {
            @Override
            public void onPresenceUpdate(Event event, User user) {
                // Only pass event through for this user
                if (user.userId.equals(userId)) {
                    eventListener.onPresenceUpdate(event, user);
                }
            }
        };
        mEventListeners.put(eventListener, globalListener);
        mDataHandler.addListener(globalListener);
    }

    /**
     * Remove an event listener.
     * @param eventListener the event listener to remove
     */
    public void removeEventListener(IMXEventListener eventListener) {
        mDataHandler.removeListener(mEventListeners.get(eventListener));
        mEventListeners.remove(eventListener);
    }
}
