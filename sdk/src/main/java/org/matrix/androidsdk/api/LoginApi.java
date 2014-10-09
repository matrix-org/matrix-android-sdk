package org.matrix.androidsdk.api;

import com.google.gson.JsonObject;

import org.matrix.androidsdk.api.response.login.LoginFlowResponse;
import org.matrix.androidsdk.api.response.login.LoginParams;

import retrofit.Callback;
import retrofit.http.Body;
import retrofit.http.GET;
import retrofit.http.POST;

/**
 * The login REST API.
 */
public interface LoginApi {

    /**
     * Get the different login flows supported by the server.
     * @param callback the asynchronous callback called with the response
     */
    @GET("/login")
    public void login(Callback<LoginFlowResponse> callback);

    /**
     * Pass params to the server for the current login phase.
     * @param loginParams the login parameters
     * @param callback the asynchronous callback called with the response
     */
    @POST("/login")
    public void login(@Body LoginParams loginParams, Callback<JsonObject> callback);
}
