/*
 * Copyright 2018 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.matrix.androidsdk.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.TreeSet;

public class JsonUtility {
    private static final String LOG_TAG = JsonUtility.class.getSimpleName();

    private static final Gson basicGson = new Gson();

    private static final Gson gson = new GsonBuilder()
            .excludeFieldsWithModifiers(Modifier.PRIVATE, Modifier.STATIC)
            .create();

    // add a call to serializeNulls().
    // by default the null parameters are not sent in the requests.
    // serializeNulls forces to add them.
    private static final Gson gsonWithNullSerialization = new GsonBuilder()
            .excludeFieldsWithModifiers(Modifier.PRIVATE, Modifier.STATIC)
            .serializeNulls()
            .create();

    // for crypto (canonicalize)
    // avoid converting "=" to \u003d
    private static final Gson gsonWithoutHtmlEscaping = new GsonBuilder()
            .disableHtmlEscaping()
            .excludeFieldsWithModifiers(Modifier.PRIVATE, Modifier.STATIC)
            .create();

    /**
     * Canonicalize a JsonElement element
     *
     * @param src the src
     * @return the canonicalize element
     */
    public static JsonElement canonicalize(JsonElement src) {
        // sanity check
        if (null == src) {
            return null;
        }

        if (src instanceof JsonArray) {
            // Canonicalize each element of the array
            JsonArray srcArray = (JsonArray) src;
            JsonArray result = new JsonArray();
            for (int i = 0; i < srcArray.size(); i++) {
                result.add(canonicalize(srcArray.get(i)));
            }
            return result;
        } else if (src instanceof JsonObject) {
            // Sort the attributes by name, and the canonicalize each element of the object
            JsonObject srcObject = (JsonObject) src;
            JsonObject result = new JsonObject();
            TreeSet<String> attributes = new TreeSet<>();

            for (Map.Entry<String, JsonElement> entry : srcObject.entrySet()) {
                attributes.add(entry.getKey());
            }
            for (String attribute : attributes) {
                result.add(attribute, canonicalize(srcObject.get(attribute)));
            }
            return result;
        } else {
            return src;
        }
    }

    /**
     * Create a canonicalized json string for an object
     *
     * @param object the object to convert
     * @return the canonicalized string
     */
    public static String getCanonicalizedJsonString(Object object) {
        String canonicalizedJsonString = null;

        if (null != object) {
            if (object instanceof JsonElement) {
                canonicalizedJsonString = gsonWithoutHtmlEscaping.toJson(canonicalize((JsonElement) object));
            } else {
                canonicalizedJsonString = gsonWithoutHtmlEscaping.toJson(canonicalize(gsonWithoutHtmlEscaping.toJsonTree(object)));
            }

            if (null != canonicalizedJsonString) {
                canonicalizedJsonString = canonicalizedJsonString.replace("\\/", "/");
            }
        }

        return canonicalizedJsonString;
    }

    /**
     * Provides the basic JSON parser.
     *
     * @return the basic JSON parser
     */
    public static Gson getBasicGson() {
        return basicGson;
    }

    /**
     * Provides the JSON parser.
     *
     * @param withNullSerialization true to serialise the null parameters
     * @return the JSON parser
     */
    public static Gson getGson(boolean withNullSerialization) {
        return withNullSerialization ? gsonWithNullSerialization : gson;
    }

    /**
     * Convert a JSON object into a class instance.
     * The returned value cannot be null.
     *
     * @param jsonObject the json object to convert
     * @param aClass     the class
     * @return the converted object
     */
    public static <T> T toClass(JsonElement jsonObject, Class<T> aClass) {
        T object = null;
        try {
            object = gson.fromJson(jsonObject, aClass);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## toClass failed " + e.getMessage(), e);
        }
        if (null == object) {
            try {
                final Constructor<T> constructor = aClass.getConstructor();
                object = constructor.newInstance();
            } catch (Throwable t) {
                Log.e(LOG_TAG, "## toClass failed " + t.getMessage(), t);
            }
        }
        return object;
    }

    /**
     * Convert a stringified JSON into a class instance.
     * The returned value cannot be null.
     *
     * @param jsonObjectAsString the json object as string to convert
     * @param aClass             the class
     * @return the converted object
     */
    public static <T> T toClass(String jsonObjectAsString, Class<T> aClass) {
        T object = null;
        try {
            object = gson.fromJson(jsonObjectAsString, aClass);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## toClass failed " + e.getMessage(), e);
        }
        if (null == object) {
            try {
                final Constructor<T> constructor = aClass.getConstructor();
                object = constructor.newInstance();
            } catch (Throwable t) {
                Log.e(LOG_TAG, "## toClass failed " + t.getMessage(), t);
            }
        }
        return object;
    }
}
