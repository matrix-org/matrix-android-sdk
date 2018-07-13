package org.matrix.androidsdk.data.metrics;

/**
 * This interface defines methods for collecting metrics data associated with startup times and stats
 * Those callbacks can be called from any threads
 */
public interface MetricsListener {

    /**
     * Called when the initial sync is finished
     *
     * @param duration of the sync
     */
    void onInitialSyncFinished(long duration);

    /**
     * Called when the incremental sync is finished
     *
     * @param duration of the sync
     */
    void onIncrementalSyncFinished(long duration);

    /**
     * Called when a store is preloaded
     *
     * @param duration of the preload
     */
    void onStorePreloaded(long duration);

    /**
     * Called when a sync is complete
     *
     * @param nbOfRooms loaded in the @SyncResponse
     */
    void onRoomsLoaded(int nbOfRooms);

}
