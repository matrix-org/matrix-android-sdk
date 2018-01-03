/* 
 * Copyright 2014 OpenMarket Ltd
 * Copyright 2017 Vector Creations Ltd
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

import com.google.gson.FieldNamingStrategy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.rest.json.ConditionDeserializer;
import org.matrix.androidsdk.rest.model.ContentResponse;
import org.matrix.androidsdk.rest.model.crypto.EncryptedEventContent;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.EventContent;
import org.matrix.androidsdk.rest.model.message.FileMessage;
import org.matrix.androidsdk.rest.model.crypto.ForwardedRoomKeyContent;
import org.matrix.androidsdk.rest.model.message.ImageInfo;
import org.matrix.androidsdk.rest.model.message.ImageMessage;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.message.MediaMessage;
import org.matrix.androidsdk.rest.model.message.Message;
import org.matrix.androidsdk.rest.model.crypto.NewDeviceContent;
import org.matrix.androidsdk.rest.model.crypto.OlmEventContent;
import org.matrix.androidsdk.rest.model.crypto.OlmPayloadContent;
import org.matrix.androidsdk.rest.model.PowerLevels;
import org.matrix.androidsdk.rest.model.crypto.RoomKeyContent;
import org.matrix.androidsdk.rest.model.crypto.RoomKeyRequest;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.RoomTags;
import org.matrix.androidsdk.rest.model.pid.RoomThirdPartyInvite;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.rest.model.message.VideoMessage;
import org.matrix.androidsdk.rest.model.bingrules.Condition;
import org.matrix.androidsdk.rest.model.login.RegistrationFlowResponse;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/**
 * Static methods for converting json into objects.
 */
public class JsonUtils {
    private static final String LOG_TAG = JsonUtils.class.getSimpleName();

    /**
     * Based on FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES.
     * toLowerCase() is replaced by toLowerCase(Locale.ENGLISH).
     * In some languages like turkish, toLowerCase does not provide the expected string.
     * e.g _I is not converted to _i.
     */
    public static class MatrixFieldNamingStrategy implements FieldNamingStrategy {

        /**
         * Converts the field name that uses camel-case define word separation into
         * separate words that are separated by the provided {@code separatorString}.
         */
        private static String separateCamelCase(String name, String separator) {
            StringBuilder translation = new StringBuilder();
            for (int i = 0; i < name.length(); i++) {
                char character = name.charAt(i);
                if (Character.isUpperCase(character) && translation.length() != 0) {
                    translation.append(separator);
                }
                translation.append(character);
            }
            return translation.toString();
        }

        /**
         * Translates the field name into its JSON field name representation.
         *
         * @param f the field object that we are translating
         * @return the translated field name.
         * @since 1.3
         */
        public String translateName(Field f) {
            return separateCamelCase(f.getName(), "_").toLowerCase(Locale.ENGLISH);
        }
    }

    private static final Gson gson = new GsonBuilder()
            .setFieldNamingStrategy(new MatrixFieldNamingStrategy())
            .excludeFieldsWithModifiers(Modifier.PRIVATE, Modifier.STATIC)
            .registerTypeAdapter(Condition.class, new ConditionDeserializer())
            .create();

    // add a call to serializeNulls().
    // by default the null parameters are not sent in the requests.
    // serializeNulls forces to add them.
    private static final Gson gsonWithNullSerialization = new GsonBuilder()
            .setFieldNamingStrategy(new MatrixFieldNamingStrategy())
            .excludeFieldsWithModifiers(Modifier.PRIVATE, Modifier.STATIC)
            .serializeNulls()
            .registerTypeAdapter(Condition.class, new ConditionDeserializer())
            .create();

    // for crypto (canonicalize)
    // avoid converting "=" to \u003d
    private static final Gson gsonWithoutHtmlEscaping = new GsonBuilder()
            .setFieldNamingStrategy(new MatrixFieldNamingStrategy())
            .disableHtmlEscaping()
            .excludeFieldsWithModifiers(Modifier.PRIVATE, Modifier.STATIC)
            .registerTypeAdapter(Condition.class, new ConditionDeserializer())
            .create();

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
     * Convert a JSON object to a room state.
     * The result is never null.
     *
     * @param jsonObject the json to convert
     * @return a room state
     */
    public static RoomState toRoomState(JsonElement jsonObject) {
        RoomState roomState = null;

        try {
            roomState = gson.fromJson(jsonObject, RoomState.class);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## toRoomState failed " + e.getMessage());
        }

        if (null == roomState) {
            roomState = new RoomState();
        }

        return roomState;
    }

    /**
     * Convert a JSON object to an User.
     * The result is never null.
     *
     * @param jsonObject the json to convert
     * @return an user
     */
    public static User toUser(JsonElement jsonObject) {
        User user = null;

        try {
            user = gson.fromJson(jsonObject, User.class);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## toUser failed " + e.getMessage());
        }

        if (null == user) {
            user = new User();
        }

        return user;
    }

    /**
     * Convert a JSON object to a RoomMember.
     * The result is never null.
     *
     * @param jsonObject the json to convert
     * @return a RoomMember
     */
    public static RoomMember toRoomMember(JsonElement jsonObject) {
        RoomMember roomMember = null;

        try {
            roomMember = gson.fromJson(jsonObject, RoomMember.class);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## toRoomMember failed " + e.getMessage());
        }

        if (null == roomMember) {
            roomMember = new RoomMember();
        }

        return roomMember;
    }

    /**
     * Convert a JSON object to a RoomTags.
     * The result is never null.
     *
     * @param jsonObject the json to convert
     * @return a RoomTags
     */
    public static RoomTags toRoomTags(JsonElement jsonObject) {
        RoomTags roomTags = null;

        try {
            roomTags = gson.fromJson(jsonObject, RoomTags.class);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## toRoomTags failed " + e.getMessage());
        }

        if (null == roomTags) {
            roomTags = new RoomTags();
        }

        return roomTags;
    }

    /**
     *
     * @param jsonObject
     * @return
     */
    public static MatrixError toMatrixError(JsonElement jsonObject) {
        try {
            return gson.fromJson(jsonObject, MatrixError.class);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## toMatrixError failed " + e.getMessage());
        }

        return new MatrixError();
    }

    public static JsonElement toJson(RoomMember roomMember) {
        try {
            return gson.toJsonTree(roomMember);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## toJson failed " + e.getMessage());
        }

        return null;
    }

    public static String getMessageMsgType(JsonElement jsonObject) {
        try {
            Message message = gson.fromJson(jsonObject, Message.class);
            return message.msgtype;
        } catch (Exception e) {
            Log.e(LOG_TAG, "## getMessageMsgType failed " + e.getMessage());
        }

        return null;
    }

    public static Message toMessage(JsonElement jsonObject) {
        try {
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

            if (Message.MSGTYPE_AUDIO.equals(message.msgtype)) {
                return toAudioMessage(jsonObject);
            }

            // Fall back to the generic Message type
            return message;
        } catch (Exception e) {
            Log.e(LOG_TAG, "## toMessage failed " + e.getMessage());
        }

        return new Message();
    }

    public static JsonObject toJson(Message message) {
        try {
            return (JsonObject) gson.toJsonTree(message);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## toJson failed " + e.getMessage());
        }

        return null;
    }

    public static Event toEvent(JsonElement jsonObject) {
        try {
            return gson.fromJson(jsonObject, Event.class);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## toEvent failed " + e.getMessage());
        }

        return new Event();
    }

    public static EncryptedEventContent toEncryptedEventContent(JsonElement jsonObject) {
        try {
            return gson.fromJson(jsonObject, EncryptedEventContent.class);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## toEncryptedEventContent failed " + e.getMessage());
        }

        return new EncryptedEventContent();
    }

    public static OlmEventContent toOlmEventContent(JsonElement jsonObject) {
        try {
            return gson.fromJson(jsonObject, OlmEventContent.class);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## toOlmEventContent failed " + e.getMessage());
        }

        return new OlmEventContent();
    }

    public static OlmPayloadContent toOlmPayloadContent(JsonElement jsonObject) {
        try {
            return gson.fromJson(jsonObject, OlmPayloadContent.class);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## toOlmPayloadContent failed " + e.getMessage());
        }

        return new OlmPayloadContent();
    }

    public static EventContent toEventContent(JsonElement jsonObject) {
        try {
            return gson.fromJson(jsonObject, EventContent.class);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## toEventContent failed " + e.getMessage());
        }

        return new EventContent();
    }

    public static RoomKeyContent toRoomKeyContent(JsonElement jsonObject) {
        try {
            return gson.fromJson(jsonObject, RoomKeyContent.class);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## RoomKeyContent failed " + e.getMessage());
        }

        return new RoomKeyContent();
    }

    public static RoomKeyRequest toRoomKeyRequest(JsonElement jsonObject) {
        try {
            return gson.fromJson(jsonObject, RoomKeyRequest.class);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## RoomKeyRequest failed " + e.getMessage());
        }

        return new RoomKeyRequest();
    }

    public static ForwardedRoomKeyContent toForwardedRoomKeyContent(JsonElement jsonObject) {
        try {
            return gson.fromJson(jsonObject, ForwardedRoomKeyContent.class);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## ForwardedRoomKeyContent failed " + e.getMessage());
        }

        return new ForwardedRoomKeyContent();
    }

    public static ImageMessage toImageMessage(JsonElement jsonObject) {
        try {
            return gson.fromJson(jsonObject, ImageMessage.class);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## toImageMessage failed " + e.getMessage());
        }

        return new ImageMessage();
    }

    public static FileMessage toFileMessage(JsonElement jsonObject) {
        try {
            return gson.fromJson(jsonObject, FileMessage.class);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## toFileMessage failed " + e.getMessage());
        }

        return new FileMessage();
    }

    public static ImageInfo.AudioMessage toAudioMessage(JsonElement jsonObject) {
        try {
            return gson.fromJson(jsonObject, ImageInfo.AudioMessage.class);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## toAudioMessage failed " + e.getMessage());
        }

        return new ImageInfo.AudioMessage();
    }

    public static VideoMessage toVideoMessage(JsonElement jsonObject) {
        try {
            return gson.fromJson(jsonObject, VideoMessage.class);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## toVideoMessage failed " + e.getMessage());
        }

        return new VideoMessage();
    }

    public static MediaMessage.LocationMessage toLocationMessage(JsonElement jsonObject) {
        try {
            return gson.fromJson(jsonObject, MediaMessage.LocationMessage.class);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## toLocationMessage failed " + e.getMessage());
        }

        return new MediaMessage.LocationMessage();
    }

    public static ContentResponse toContentResponse(String jsonString) {
        try {
            return gson.fromJson(jsonString, ContentResponse.class);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## toContentResponse failed " + e.getMessage());
        }

        return new ContentResponse();
    }

    public static PowerLevels toPowerLevels(JsonElement jsonObject) {
        try {
            return gson.fromJson(jsonObject, PowerLevels.class);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## toPowerLevels failed " + e.getMessage());
        }

        return new PowerLevels();
    }

    public static RoomThirdPartyInvite toRoomThirdPartyInvite(JsonElement jsonObject) {
        try {
            return gson.fromJson(jsonObject, RoomThirdPartyInvite.class);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## toRoomThirdPartyInvite failed " + e.getMessage());
        }

        return new RoomThirdPartyInvite();
    }

    public static RegistrationFlowResponse toRegistrationFlowResponse(String jsonString) {
        try {
            return gson.fromJson(jsonString, RegistrationFlowResponse.class);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## toRegistrationFlowResponse failed " + e.getMessage());
        }

        return new RegistrationFlowResponse();
    }

    public static JsonObject toJson(Event event) {
        try {
            return (JsonObject) gson.toJsonTree(event);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## toJson failed " + e.getMessage());
        }

        return new JsonObject();
    }

    public static NewDeviceContent toNewDeviceContent(JsonElement jsonObject) {
        try {
            return gson.fromJson(jsonObject, NewDeviceContent.class);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## toNewDeviceContent failed " + e.getMessage());
        }

        return new NewDeviceContent();
    }

    private static Object toClass(JsonElement jsonObject, Class aClass) {
        Object object = null;

        try {
            object = gson.fromJson(jsonObject, aClass);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## toClass failed " + e.getMessage());
        }

        if (null == object) {
            Constructor<?>[] constructors = aClass.getConstructors();

            try {
                object = constructors[0].newInstance();
            } catch (Throwable t) {
                Log.e(LOG_TAG, "## toClass failed " + t.getMessage());
            }
        }

        return object;
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
     * Convert a string from an UTF8 String
     *
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
     *
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
