/* 
 * Copyright 2014 OpenMarket Ltd
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
package org.matrix.androidsdk.util;

import android.util.Log;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.rest.json.ConditionDeserializer;
import org.matrix.androidsdk.rest.model.ContentResponse;
import org.matrix.androidsdk.rest.model.EncryptedEventContent;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.EventContent;
import org.matrix.androidsdk.rest.model.FileMessage;
import org.matrix.androidsdk.rest.model.ImageMessage;
import org.matrix.androidsdk.rest.model.LocationMessage;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.Message;
import org.matrix.androidsdk.rest.model.NewDeviceContent;
import org.matrix.androidsdk.rest.model.PowerLevels;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.RoomTags;
import org.matrix.androidsdk.rest.model.RoomThirdPartyInvite;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.rest.model.VideoMessage;
import org.matrix.androidsdk.rest.model.bingrules.Condition;
import org.matrix.androidsdk.rest.model.login.RegistrationFlowResponse;

import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.TreeSet;

/**
 * Static methods for converting json into objects.
 */
public class JsonUtils {

    private static final String LOG_TAG = "JsonUtils";

    private static Gson gson = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .excludeFieldsWithModifiers(Modifier.PRIVATE, Modifier.STATIC)
            .registerTypeAdapter(Condition.class, new ConditionDeserializer())
            .create();

    // add a call to serializeNulls().
    // by default the null parameters are not sent in the requests.
    // serializeNulls forces to add them.
    private static Gson gsonWithNullSerialization = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .excludeFieldsWithModifiers(Modifier.PRIVATE, Modifier.STATIC)
            .serializeNulls()
            .registerTypeAdapter(Condition.class, new ConditionDeserializer())
            .create();

    public static Gson getGson(boolean withNullSerialization) {
        return withNullSerialization ? gsonWithNullSerialization : gson;
    }

    public static RoomState toRoomState(JsonElement jsonObject) {
        return gson.fromJson(jsonObject, RoomState.class);
    }

    public static User toUser(JsonElement jsonObject) {
        return gson.fromJson(jsonObject, User.class);
    }

    public static RoomMember toRoomMember(JsonElement jsonObject) {
        return gson.fromJson(jsonObject, RoomMember.class);
    }

    public static RoomTags toRoomTags(JsonElement jsonObject) {
        return gson.fromJson(jsonObject, RoomTags.class);
    }

    public static MatrixError toMatrixError(JsonElement jsonObject) {
        return gson.fromJson(jsonObject, MatrixError.class);
    }

    public static JsonElement toJson(RoomMember roomMember) {
        return gson.toJsonTree(roomMember);
    }

    public static String getMessageMsgType(JsonElement jsonObject) {
        Message message = gson.fromJson(jsonObject, Message.class);
        return message.msgtype;
    }

    public static Message toMessage(JsonElement jsonObject) {
        Message message = gson.fromJson(jsonObject, Message.class);

        // Try to return the right subclass
        if (Message.MSGTYPE_IMAGE.equals(message.msgtype)) {
            return toImageMessage(jsonObject);
        }

        if (Message.MSGTYPE_VIDEO.equals(message.msgtype)) {
            return toVideoMessage(jsonObject);
        }

        if (Message.MSGTYPE_LOCATION.equals(message.msgtype)) {
            return toLocationMessage(jsonObject);
        }

        // Try to return the right subclass
        if (Message.MSGTYPE_FILE.equals(message.msgtype)) {
            return toFileMessage(jsonObject);
        }

        // Fall back to the generic Message type
        return message;
    }

    public static JsonObject toJson(Message message) {
        return (JsonObject) gson.toJsonTree(message);
    }

    public static Event toEvent(JsonElement jsonObject) {
        return gson.fromJson(jsonObject, Event.class);
    }

    public static EncryptedEventContent toEncryptedEventContent(JsonElement jsonObject) {
        return gson.fromJson(jsonObject, EncryptedEventContent.class);
    }

    public static EventContent toEventContent(JsonElement jsonObject) {
        return gson.fromJson(jsonObject, EventContent.class);
    }

    public static ImageMessage toImageMessage(JsonElement jsonObject) {
        return gson.fromJson(jsonObject, ImageMessage.class);
    }

    public static FileMessage toFileMessage(JsonElement jsonObject) {
        return gson.fromJson(jsonObject, FileMessage.class);
    }

    public static VideoMessage toVideoMessage(JsonElement jsonObject) {
        return gson.fromJson(jsonObject, VideoMessage.class);
    }

    public static LocationMessage toLocationMessage(JsonElement jsonObject) {
        return gson.fromJson(jsonObject, LocationMessage.class);
    }

    public static ContentResponse toContentResponse(String jsonString) {
        return gson.fromJson(jsonString, ContentResponse.class);
    }

    public static PowerLevels toPowerLevels(JsonElement jsonObject) {
        return gson.fromJson(jsonObject, PowerLevels.class);
    }

    public static RoomThirdPartyInvite toRoomThirdPartyInvite(JsonElement jsonObject) {
        return gson.fromJson(jsonObject, RoomThirdPartyInvite.class);
    }

    public static RegistrationFlowResponse toRegistrationFlowResponse(String jsonString) {
        return gson.fromJson(jsonString, RegistrationFlowResponse.class);
    }

    public static JsonObject toJson(Event event) {
        return (JsonObject) gson.toJsonTree(event);
    }

    public static NewDeviceContent toNewDeviceContent(JsonElement jsonObject) {
        return gson.fromJson(jsonObject, NewDeviceContent.class);
    }

    /**
     * Create a canonicalized json string for an object
     * @param object the object to convert
     * @return the canonicalized string
     */
    public static String getCanonicalizedJsonString(Object object) {
        String canonicalizedJsonString = null;

        if (null != object) {
            if (object instanceof JsonElement) {
                canonicalizedJsonString = gson.toJson(canonicalize((JsonElement)object));
            } else {
                canonicalizedJsonString = gson.toJson(canonicalize(gson.toJsonTree(object)));
            }

            if (null != canonicalizedJsonString) {
                canonicalizedJsonString = canonicalizedJsonString.replace("\\/", "/");
            }
        }

        return canonicalizedJsonString;
    }

    /**
     * Canonicalize a JsonElement element
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
     * Convert a string from an UTF8 String
     * @param s the string to convert
     * @return the utf-16 string
     */
    public static String convertFromUTF8(String s) {
        String out = s;

        if (null != out) {
            try {
                byte[] bytes = out.getBytes();
                out = new String(bytes, "UTF-8");
            } catch (Exception e) {
                Log.e(LOG_TAG, "## convertFromUTF8()  failed " + e.getMessage());
            }
        }

        return out;
    }

    /**
     * Convert a string to an UTF8 String
     * @param s the string to convert
     * @return the utf-8 string
     */
    public static String convertToUTF8(String s) {
        String out = s;

        if (null != out) {
            try {
                byte[] bytes = out.getBytes("UTF-8");
                out = new String(bytes);
            } catch (Exception e) {
                Log.e(LOG_TAG, "## convertToUTF8()  failed " + e.getMessage());
            }
        }

        return out;
    }

}
