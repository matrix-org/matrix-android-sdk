package org.matrix.androidsdk.api;

import org.matrix.androidsdk.api.response.User;

import java.util.List;

import retrofit.Callback;
import retrofit.http.Body;
import retrofit.http.GET;
import retrofit.http.POST;
import retrofit.http.PUT;
import retrofit.http.Path;

/**
 * The presence REST API.
 */
public interface PresenceApi {

    /**
     * Set this user's presence state.
     * @param userId the user id
     * @param userPresence a User object with possibly the presence and statusMsg fields
     * @param callback the asynchronous callback called when finished
     */
    @PUT("/presence/{userId}/status")
    public void presenceStatus(@Path("userId") String userId, @Body User userPresence, Callback<Void> callback);

    /**
     * Get a user's presence state.
     * @param userId the user id
     * @param callback the asynchronous callback called with the response
     */
    @GET("/presence/{userId}/status")
    public void presenceStatus(@Path("userId") String userId, Callback<User> callback);
}
