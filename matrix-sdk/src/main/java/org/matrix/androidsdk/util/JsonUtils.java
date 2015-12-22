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

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.rest.json.ConditionDeserializer;
import org.matrix.androidsdk.rest.model.ContentResponse;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.FileMessage;
import org.matrix.androidsdk.rest.model.ImageMessage;
import org.matrix.androidsdk.rest.model.LocationMessage;
import org.matrix.androidsdk.rest.model.Message;
import org.matrix.androidsdk.rest.model.PowerLevels;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.RoomTags;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.rest.model.VideoMessage;
import org.matrix.androidsdk.rest.model.bingrules.Condition;

import java.lang.reflect.Modifier;

/**
 * Static methods for converting json into objects.
 */
public class JsonUtils {

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

    public static JsonElement toJson(RoomMember roomMember) {
        return gson.toJsonTree(roomMember);
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

    public static JsonObject toJson(Event event) {
        return (JsonObject) gson.toJsonTree(event);
    }
}
