package org.matrix.androidsdk.rest.client;

import com.google.gson.Gson;

import org.matrix.androidsdk.HomeServerConnectionConfig;
import org.matrix.androidsdk.RestClient;
import org.matrix.androidsdk.rest.api.FilterApi;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.RestAdapterCallback;
import org.matrix.androidsdk.rest.model.filter.FilterBody;
import org.matrix.androidsdk.rest.model.filter.FilterResponse;

import retrofit2.Response;

public class FilterRestClient extends RestClient<FilterApi>{

    /**
     * {@inheritDoc}
     */
    public FilterRestClient(HomeServerConnectionConfig hsConfig) {
        super(hsConfig, FilterApi.class, RestClient.URI_API_PREFIX_PATH_R0, false);
    }

    /**
     * Uploads a FilterBody to homeserver
     *
     * @param userId   the user id
     * @param filterBody FilterBody which should be send to server
     * @param callback on success callback containing a String with populated filterId
     */
    public void uploadFilter(final String userId, final FilterBody filterBody, final ApiCallback<FilterResponse> callback) {
        final String description = "uploadFilter userId : " + userId + " filter : " + filterBody;

        mApi.uploadFilter(userId, filterBody)
                .enqueue(new RestAdapterCallback<FilterResponse>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                    @Override
                    public void onRetry() {
                        uploadFilter(userId, filterBody, callback);
                    }
                }));
    }

    /**
     * Get a user's filter by filterId
     *
     * @param userId   the user id
     * @param filterId the filter id
     * @param callback on success callback containing a User object with populated filterbody
     */
    public void getFilter(final String userId, final String filterId, final ApiCallback<FilterBody> callback) {
        final String description = "getFilter userId : " + userId + " filterId : " + filterId;

        mApi.getFilterById(userId, filterId)
                .enqueue(new RestAdapterCallback<FilterBody>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                    @Override
                    public void onRetry() {
                        getFilter(userId, filterId, callback);
                    }
                }));
    }
}
