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

import android.text.TextUtils;

import org.matrix.androidsdk.MXDataHandler;
import org.matrix.androidsdk.listeners.IMXEventListener;
import org.matrix.androidsdk.listeners.MXEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Class representing a user.
 */
public class User {
    // the user presence values
    public static final String PRESENCE_ONLINE = "online";
    public static final String PRESENCE_UNAVAILABLE = "unavailable";
    public static final String PRESENCE_OFFLINE = "offline";
    public static final String PRESENCE_FREE_FOR_CHAT = "free_for_chat";
    public static final String PRESENCE_HIDDEN = "hidden";

    // user fields provided by the server
    public String user_id;
    public String displayname;
    public String avatar_url;
    public String presence;
    public Boolean currently_active;
    public Long lastActiveAgo;
    public String statusMsg;

    // Used to provide a more realistic last active time:
    // the last active ago time provided by the server + the time that has gone by since
    private long lastPresenceTs;

    // Map to keep track of the listeners the client adds vs. the ones we actually register to the global data handler.
    // This is needed to find the right one when removing the listener.
    private final Map<IMXEventListener, IMXEventListener> mEventListeners = new HashMap<>();

    // data handler
    protected MXDataHandler mDataHandler;

    // events listeners list
    private ArrayList<IMXEventListener> pendingListeners = new ArrayList<>();

    // avatar URLs setter / getter
    public String getAvatarUrl() {
        return avatar_url;
    }
    public void setAvatarUrl(String newAvatarUrl) {
        avatar_url = newAvatarUrl;
    }

    /**
     * Clone an user into this instance
     * @param user the user to clone.
     */
    protected void clone(User user) {
        if (user != null) {
            user_id = user.user_id;
            displayname = user.displayname;
            avatar_url = user.avatar_url;
            presence = user.presence;
            lastActiveAgo = user.lastActiveAgo;
            statusMsg = user.statusMsg;
            pendingListeners = user.pendingListeners;

            mDataHandler = user.mDataHandler;
        }
    }

    /**
     * Create a deep copy of the current user
     * @return
     */
    public User deepCopy() {
        User copy = new User();
        copy.clone(this);
        return copy;
    }

    /**
     * Tells if an user is active
     * @return true if the user is active
     */
    public boolean isActive() {
        return TextUtils.equals(presence, PRESENCE_ONLINE) || ((null != currently_active) && currently_active);
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
    public long getAbsoluteLastActiveAgo() {
        // sanity check
        if (null == lastActiveAgo) {
            return 0;
        } else {
            return lastActiveAgo + System.currentTimeMillis() - lastPresenceTs;
        }
    }

    /**
     * Set the event listener to send back events to. This is typically the DataHandler for dispatching the events to listeners.
     * @param dataHandler should be the main data handler for dispatching back events to registered listeners.
     */
    public void setDataHandler(MXDataHandler dataHandler) {
        mDataHandler = dataHandler;

        for(IMXEventListener listener : pendingListeners) {
            mDataHandler.addListener(listener);
        }
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
                if (user.user_id.equals(user_id)) {
                    eventListener.onPresenceUpdate(event, user);
                }
            }
        };
        mEventListeners.put(eventListener, globalListener);

        // the handler could be set later
        if (null != mDataHandler) {
            mDataHandler.addListener(globalListener);
        } else {
            pendingListeners.add(globalListener);
        }
    }

    /**
     * Remove an event listener.
     * @param eventListener the event listener to remove
     */
    public void removeEventListener(IMXEventListener eventListener) {

        if (null != mDataHandler) {
            mDataHandler.removeListener(mEventListeners.get(eventListener));
        } else {
            pendingListeners.remove(mEventListeners.get(eventListener));
        }

        mEventListeners.remove(eventListener);
    }
}
