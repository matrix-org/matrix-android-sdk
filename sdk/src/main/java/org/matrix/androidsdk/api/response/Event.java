package org.matrix.androidsdk.api.response;

import com.google.gson.JsonObject;

/**
 * Generic event class with all possible fields for events.
 */
public class Event {
    public String type;
    public JsonObject content;

    public String eventId;
    public String roomId;
    public String userId;
    public long ts;
    public long age;

    // Specific to state events
    public String stateKey;
    public String prevState;
}
