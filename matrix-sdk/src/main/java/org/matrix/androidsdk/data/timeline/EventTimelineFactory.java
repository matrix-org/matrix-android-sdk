package org.matrix.androidsdk.data.timeline;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.matrix.androidsdk.MXDataHandler;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.store.IMXStore;
import org.matrix.androidsdk.data.store.MXMemoryStore;

public class EventTimelineFactory {

    /**
     * Constructor from room.
     *
     * @param room the linked room.
     */
    public static EventTimeline liveTimeline(final IMXStore store, final MXDataHandler dataHandler, final Room room, final String roomId) {
        return new EventTimeline(store, dataHandler, room, roomId, null, true);
    }

    /**
     * Constructor from room and event Id
     *
     * @param dataHandler the data handler
     * @param roomId      the room id.
     */
    public static EventTimeline pastTimeline(
            @NonNull MXDataHandler dataHandler,
            @NonNull String roomId) {
        return pastTimeline(dataHandler, roomId, null);
    }

    /**
     * Constructor from room and event Id
     *
     * @param dataHandler the data handler
     * @param eventId     the event id.
     */
    public static EventTimeline pastTimeline(@NonNull MXDataHandler dataHandler,
                                             @NonNull String roomId,
                                             @Nullable String eventId) {
        final MXMemoryStore store = new MXMemoryStore(dataHandler.getCredentials(), null);
        final Room room = dataHandler.getRoom(store, roomId, true);
        final EventTimeline eventTimeline = new EventTimeline(store, dataHandler, room, roomId, eventId, false);
        room.setTimeline(eventTimeline);
        room.setReadyState(true);
        return eventTimeline;
    }


}
