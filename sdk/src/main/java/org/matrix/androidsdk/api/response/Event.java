package org.matrix.androidsdk.api.response;

import com.google.gson.JsonObject;

/**
 * Created by JOACHIMR on 03/10/2014.
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
