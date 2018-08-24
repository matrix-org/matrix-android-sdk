package org.matrix.androidsdk.rest.model.filter;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Class which can be parsed to a filter json string. Used for POST and GET
 * Have a look here for further information:
 * https://matrix.org/docs/spec/client_server/r0.3.0.html#post-matrix-client-r0-user-userid-filter
 */
public class FilterBody implements Serializable {
    public static final String LOG_TAG = FilterBody.class.getSimpleName();

    @SerializedName("event_fields")
    public List<String> eventFields;

    @SerializedName("event_format")
    public String eventFormat;

    public Filter presence;

    @SerializedName("account_data")
    public Filter accountData;

    public RoomFilter room;

    private static FilterBody dataSaveModeFilterBody;

    /**
     *
     * @return FilterBody which represents "{\"room\": {\"ephemeral\": {\"types\": [\"m.receipt\"]}}, \"presence\":{\"notTypes\": [\"*\"]}}"
     */
    public static FilterBody getDataSaveModeFilterBody() {
        if (dataSaveModeFilterBody == null) {
            FilterBody result = new FilterBody();
            result.room = new RoomFilter();
            result.room.ephemeral = new RoomEventFilter();
            result.room.ephemeral.types = new ArrayList<>();
            result.room.ephemeral.types.add("m.receipt");

            result.presence = new Filter();
            result.presence.notTypes = new ArrayList<>();
            result.presence.notTypes.add("*");
            dataSaveModeFilterBody = result;
        }
        return dataSaveModeFilterBody;
    }

    @Override
    public String toString() {
        return LOG_TAG + toJSONString();
    }

    public String toJSONString() {
        return new Gson().toJson(this);
    }
}
