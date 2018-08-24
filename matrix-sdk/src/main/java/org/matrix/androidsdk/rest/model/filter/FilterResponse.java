package org.matrix.androidsdk.rest.model.filter;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

/**
 * Represents the body which is the response when creating a filter on the server
 * https://matrix.org/docs/spec/client_server/r0.3.0.html#post-matrix-client-r0-user-userid-filter
 */
public class FilterResponse implements Serializable {

    @SerializedName("filter_id")
    public String filterId;
}
