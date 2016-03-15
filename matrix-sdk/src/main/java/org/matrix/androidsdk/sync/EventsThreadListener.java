package org.matrix.androidsdk.sync;

import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.InitialSyncResponse;
import org.matrix.androidsdk.rest.model.SyncV2.SyncResponse;

import java.util.List;

/**
 * Interface to implement to listen to the event thread.
 */
public interface EventsThreadListener {

    /**
     * Called with the response of the initial sync.
     * @param response the response (can be null)
     */
    void onInitialSyncComplete(InitialSyncResponse response);

    /**
     * API V2 support.
     * Call when a sync request has been performed with the API V2.
     * @param response the response (can be null)
     * @param isInitialSync true if the response is triggered by an initial sync
     */
    void onSyncV2Response(SyncResponse response, Boolean isInitialSync);

    /**
     * Called when getting the users presences.
     * @param presence the users presence
     */
    void onMembersPresencesSyncComplete(List<Event> presence);

    /**
     * Called every time events come down the stream.
     * @param events the events
     * @param latestToken the token of the latest event
     */
    void onEventsReceived(List<Event> events, String latestToken);

    /**
     * the server returns an invalid token error
     */
    void onInvalidToken();
}
