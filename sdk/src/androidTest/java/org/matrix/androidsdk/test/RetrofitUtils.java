package org.matrix.androidsdk.test;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import junit.framework.Assert;

import org.json.JSONException;
import org.json.JSONObject;
import org.matrix.androidsdk.api.response.MatrixError;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import retrofit.RetrofitError;
import retrofit.client.Header;
import retrofit.client.Response;
import retrofit.converter.ConversionException;
import retrofit.converter.Converter;
import retrofit.converter.GsonConverter;
import retrofit.mime.TypedInput;
import retrofit.mime.TypedOutput;

/**
 * Utility class for interacting with Retrofit.
 */
public class RetrofitUtils {

    private static final HashMap<Integer, String> sCodeToStatusMsg = new HashMap<Integer, String>();

    static {
        sCodeToStatusMsg.put(200, "OK");
        sCodeToStatusMsg.put(201, "Created");
        sCodeToStatusMsg.put(400, "Bad Request");
        sCodeToStatusMsg.put(401, "Unauthorized");
        sCodeToStatusMsg.put(403, "Forbidden");
        sCodeToStatusMsg.put(404, "Not Found");
        sCodeToStatusMsg.put(409, "Conflict");
        sCodeToStatusMsg.put(500, "Internal Server Error");
    }

    public static Response createJsonResponse(String url, int code, JSONObject json) throws Exception {
        return createJsonResponse(url, code, json, new ArrayList<Header>());
    }

    public static Response createJsonResponse(String url, int code, JSONObject json,
                                              List<Header> additionalHeaders) throws Exception {
        final String jsonStr = json.toString();
        final InputStream stream = new ByteArrayInputStream(jsonStr.getBytes("UTF8"));

        additionalHeaders.add(new Header("Content-Type", "application/json"));

        String status = "Unknown";
        if (sCodeToStatusMsg.containsKey(code)) {
            status = sCodeToStatusMsg.get(code);
        }

        return new Response(url, code, status, additionalHeaders, new TypedInput() {
            @Override
            public String mimeType() {
                return "application/json";
            }

            @Override
            public long length() {
                return jsonStr.length();
            }

            @Override
            public InputStream in() throws IOException {
                return stream;
            }
        });
    }

    public static RetrofitError createNetworkError(String url) {
        return RetrofitError.networkError(url, new IOException("created network error"));
    }

    public static RetrofitError createMatrixError(String url, JSONObject json) {
        try {
            Response response = RetrofitUtils.createJsonResponse(url, json.optInt("errcode", 0), json);
            return RetrofitUtils.createMatrixError(url, response);
        }
        catch (Exception e) {
            Assert.assertTrue("createMatrixError: " + e, false);
        }
        return null;
    }

    public static RetrofitError createMatrixError(String url, Response response) {
        Gson gson = new GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .create();
        Converter converter = new GsonConverter(gson);
        return  RetrofitError.httpError(url, response, converter, MatrixError.class);
    }
}
