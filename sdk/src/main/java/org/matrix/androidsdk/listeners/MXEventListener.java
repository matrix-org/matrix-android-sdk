package org.matrix.androidsdk.listeners;

import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.User;

/**
 * A no-op class implementing {@link IMXEventListener} so listeners can just implement the methods
 * that they require.
 */
public class MXEventListener implements IMXEventListener {

    @Override
    public void onUserPresenceUpdated(User user) {

    }

    @Override
    public void onMessageReceived(Room room, Event event) {

    }

    @Override
    public void onEventReceived(Event event) {

    }

    @Override
    public void onRoomStateUpdated(Room room, Event event, Object oldVal, Object newVal) {

    }

    @Override
    public void onInitialSyncComplete() {

    }

    @Override
    public void onInvitedToRoom(Room room) {

    }
}
