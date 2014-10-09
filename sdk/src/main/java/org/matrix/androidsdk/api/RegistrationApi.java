package org.matrix.androidsdk.api;

import com.google.gson.JsonObject;

import org.matrix.androidsdk.api.response.login.LoginFlowResponse;
import org.matrix.androidsdk.api.response.login.LoginParams;

import retrofit.Callback;
import retrofit.http.Body;
import retrofit.http.GET;
import retrofit.http.POST;

/**
 * The Registration REST API.
 */
public interface RegistrationApi {

    /**
     * Get the different registration flows supported by the server.
     * @param callback the asynchronous callback called with the response
     */
    @GET("/register")
    public void register(Callback<LoginFlowResponse> callback);

    /**
     * Pass params to the server for the current registration phase.
     * @param loginParams the registration parameters
     * @param callback the asynchronous callback called with the response
     */
    @POST("/register")
    public void register(@Body LoginParams loginParams, Callback<JsonObject> callback);
}
