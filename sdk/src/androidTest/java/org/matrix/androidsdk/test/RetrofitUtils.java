package org.matrix.androidsdk.test;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import retrofit.client.Header;
import retrofit.client.Response;
import retrofit.mime.TypedInput;

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
}
