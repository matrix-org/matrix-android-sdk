package org.matrix.androidsdk.sync;

import org.matrix.androidsdk.MXApiService;
import org.matrix.androidsdk.api.response.Event;
import org.matrix.androidsdk.api.response.InitialSyncResponse;
import org.matrix.androidsdk.api.response.TokensChunkResponse;

import java.util.List;

/**
 * Thread that continually watches the event stream and sends events to its listener.
 */
public class EventsThread extends Thread {

    MXApiService mApiService;
    EventsThreadListener mListener;

    public EventsThread(MXApiService apiService, EventsThreadListener listener) {
        super("Sync thread");
        mApiService = apiService;
        mListener = listener;
    }

    public interface EventsThreadListener {
        public void onInitialSyncComplete(InitialSyncResponse response);
        public void onEventsReceived(List<Event> events);
    }

    @Override
    public void run() {
        // Start with initial sync
        InitialSyncResponse initialResponse = mApiService.initialSync();
        mListener.onInitialSyncComplete(initialResponse);

        String currentToken = initialResponse.end;

        // Then work from there
        while (true) {
            TokensChunkResponse<Event> eventsResponse = mApiService.events(currentToken);
            mListener.onEventsReceived(eventsResponse.chunk);
            currentToken = eventsResponse.end;
        }
    }
}
