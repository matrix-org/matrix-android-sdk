package org.matrix.androidsdk.rest.model.filter;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.List;

/**
 * Represents "RoomFilter" as mentioned in the SPEC
 * https://matrix.org/docs/spec/client_server/r0.3.0.html#post-matrix-client-r0-user-userid-filter
 */
public class RoomFilter implements Serializable {

    public List<String> not_rooms;

    public List<String> rooms;

    public RoomEventFilter ephemeral;

    @SerializedName("include_leave")
    public Boolean includeLeave;

    public RoomEventFilter state;

    public RoomEventFilter timeline;

    @SerializedName("account_data")
    public RoomEventFilter accountData;
}
