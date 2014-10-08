package org.matrix.androidsdk.api;

import com.google.gson.JsonObject;

import org.matrix.androidsdk.api.response.User;

import retrofit.Callback;
import retrofit.http.Body;
import retrofit.http.GET;
import retrofit.http.PUT;
import retrofit.http.Path;

/**
 * The profile REST API.
 */
public interface ProfileApi {

    /**
     * Update a user's display name.
     * @param userId the user id
     * @param user the user object containing the new display name
     * @param callback the asynchronous callback to call when finished
     */
    @PUT("/profile/{userId}/displayname")
    public void displayname(@Path("userId") String userId, @Body User user, Callback<Void> callback);

    /**
     * Get a user's display name.
     * @param userId the user id
     * @param callback the asynchronous callback to call when finished
     */
    @GET("/profile/{userId}/displayname")
    public void displayname(@Path("userId") String userId, Callback<User> callback);

    /**
     * Update a user's avatar URL.
     * @param userId the user id
     * @param user the user object containing the new avatar url
     * @param callback the asynchronous callback to call when finished
     */
    @PUT("/profile/{userId}/avatar_url")
    public void avatarUrl(@Path("userId") String userId, @Body User user, Callback<Void> callback);

    /**
     * Get a user's avatar URL.
     * @param userId the user id
     * @param callback the asynchronous callback to call when finished
     */
    @GET("/profile/{userId}/avatar_url")
    public void avatarUrl(@Path("userId") String userId, Callback<User> callback);
}