package org.matrix.androidsdk.listeners;

import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.User;

public interface IMXEventListener {

    /**
     * User presence was updated.
     * @param user The new user value.
     */
    public void onUserPresenceUpdated(User user);

    /**
     * An m.room.message was received.
     * @param room The room which this message is about.
     * @param event The event representing the message.
     */
    public void onMessageReceived(Room room, Event event);

    /**
     * Room state has been updated via a room state event.
     * @param room The room which has been updated (post-update).
     * @param event The event which updated this room.
     * @param oldVal The previous state value if the event is a known state event, else null.
     * @param newVal The new state value if the event is a known state event.
     */
    public void onRoomStateUpdated(Room room, Event event, Object oldVal, Object newVal);

    /**
     * The initial sync is complete and the store can be queried for current state.
     */
    public void onInitialSyncComplete();

    /**
     * Called when the user is invited to a room via /initialSync.
     * @param room The room the user has been invited to.
     */
    public void onInvitedToRoom(Room room);
}
