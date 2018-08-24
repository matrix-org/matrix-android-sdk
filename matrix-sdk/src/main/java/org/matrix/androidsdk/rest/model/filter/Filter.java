package org.matrix.androidsdk.rest.model.filter;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.List;

/**
 * Represents "Filter" as mentioned in the SPEC
 * https://matrix.org/docs/spec/client_server/r0.3.0.html#post-matrix-client-r0-user-userid-filter
 */
public class Filter implements Serializable {

    public Integer limit;

    public List<String> senders;

    @SerializedName("not_senders")
    public List<String> notSenders;

    public List<String> types;

    @SerializedName("not_types")
    public List<String> notTypes;

    public List<String> rooms;

    @SerializedName("not_rooms")
    public List<String> notRooms;
}
