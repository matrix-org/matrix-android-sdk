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
     * An event was received. This will be called for *every* event, including known events.
     * @param event The event received.
     */
    public void onEventReceived(Event event);

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
}
