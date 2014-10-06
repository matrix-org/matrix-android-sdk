package org.matrix.androidsdk.api.response;

import java.util.List;

/**
 * Response object from an initial sync
 */
public class InitialSyncResponse {
    public String end;
    public List<Event> presence;
    public List<RoomResponse> rooms;
}
