package org.matrix.androidsdk.rest.model.filter;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.List;

/**
 * Represents "RoomEventFilter" as mentioned in the SPEC
 * https://matrix.org/docs/spec/client_server/r0.3.0.html#post-matrix-client-r0-user-userid-filter
 */
public class RoomEventFilter implements Serializable {

    public Integer limit;

    @SerializedName("not_senders")
    public List<String> notSenders;

    @SerializedName("not_types")
    public List<String> notTypes;

    public List<String> senders;

    public List<String> types;

    public List<String> rooms;

    @SerializedName("not_rooms")
    public List<String> notRooms;

    public Boolean contains_url;
}
