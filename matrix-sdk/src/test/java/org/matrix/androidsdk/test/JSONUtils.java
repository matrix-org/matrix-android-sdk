package org.matrix.androidsdk.test;

import junit.framework.Assert;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Utility class for making JSON.
 */
public class JSONUtils {

    public static JSONObject createChunk(JSONArray array) {
        return JSONUtils.createChunk(array, "start_token", "end_token");
    }

    public static JSONObject createChunk(JSONArray array, String start, String end) {
        try {
            JSONObject json = new JSONObject();
            json.put("start", start);
            json.put("end", end);
            json.put("chunk", array);
            return json;
        } catch (JSONException e) {
            Assert.assertTrue("JSONUtils.createChunk: " + e, false);
        }
        return null;
    }

    public static JSONObject error(int code) {
        try {
            JSONObject json = new JSONObject();
            json.put("errcode", code);
            json.put("error", "Uh-oh: " + code);
            return json;
        } catch (JSONException e) {
            Assert.assertTrue("JSONUtils.error: " + e, false);
        }
        return null;
    }
}
