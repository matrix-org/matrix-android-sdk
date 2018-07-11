package org.matrix.androidsdk.data.metrics;

/*
* This interface defines methods for collecting metrics data associated with startup times and stats
*/

public interface MetricsListener {

        void onInitialSyncFinished(long duration);

        void onIncrementalSyncFinished(long duration);

        void onStorePreloaded(long duration);

        void onRoomsLoaded(int nbOfRooms);

}
