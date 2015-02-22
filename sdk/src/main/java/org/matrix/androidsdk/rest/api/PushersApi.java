package org.matrix.androidsdk.rest.api;

import org.matrix.androidsdk.data.Pusher;

import retrofit.Callback;
import retrofit.http.Body;
import retrofit.http.POST;

/**
 * The pusher API
 */
public interface PushersApi {

    @POST("/pushers/set")
    public void set(@Body Pusher pusher, Callback<Void> callback);
}
