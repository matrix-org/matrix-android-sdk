package org.matrix.androidsdk.sync;

import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.InitialSyncResponse;

import java.util.List;

/**
 * Interface to implement to listen to the event thread.
 */
public interface IEventsThreadListener {

    /**
     * Called with the response of the initial sync.
     * @param response the response
     */
    public void onInitialSyncComplete(InitialSyncResponse response);

    /**
     * Called every time events come down the stream.
     * @param events the events
     */
    public void onEventsReceived(List<Event> events);
}

    