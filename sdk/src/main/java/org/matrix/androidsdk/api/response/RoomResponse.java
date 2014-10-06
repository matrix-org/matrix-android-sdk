package org.matrix.androidsdk.api.response;

import java.util.List;

/**
 * Class representing a room from a JSON response.
 */
public class RoomResponse {
    public String roomId;
    public String membership;
    public String visibility;
    public TokensChunkResponse<IdentifiedEvent> messages;
    public List<StateEvent> state;
}
