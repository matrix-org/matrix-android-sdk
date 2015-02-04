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
import com.google.gson.JsonObject;

import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.rest.json.ConditionDeserializer;
import org.matrix.androidsdk.rest.model.ContentResponse;
import org.matrix.androidsdk.rest.model.EmoteMessage;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.ImageMessage;
import org.matrix.androidsdk.rest.model.Message;
import org.matrix.androidsdk.rest.model.PowerLevels;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.TextMessage;
import org.matrix.androidsdk.rest.model.User;
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

    public static Gson getGson() {
        return gson;
    }

    public static RoomState toRoomState(JsonObject jsonObject) {
        return gson.fromJson(jsonObject, RoomState.class);
    }

    public static User toUser(JsonObject jsonObject) {
        return gson.fromJson(jsonObject, User.class);
    }

    public static RoomMember toRoomMember(JsonObject jsonObject) {
        return gson.fromJson(jsonObject, RoomMember.class);
    }

    public static JsonObject toJson(RoomMember roomMember) {
        return (JsonObject) gson.toJsonTree(roomMember);
    }

    public static Message toMessage(JsonObject jsonObject) {
        Message message = gson.fromJson(jsonObject, Message.class);

        // Try to return the right subclass
        if (Message.MSGTYPE_TEXT.equals(message.msgtype)) {
            return toTextMessage(jsonObject);
        }
        else if (Message.MSGTYPE_EMOTE.equals(message.msgtype)) {
            return toEmoteMessage(jsonObject);
        }
        else if (Message.MSGTYPE_IMAGE.equals(message.msgtype)) {
            return toImageMessage(jsonObject);
        }

        // Fall back to the generic Message type
        return message;
    }

    public static JsonObject toJson(Message message) {
        return (JsonObject) gson.toJsonTree(message);
    }

    public static TextMessage toTextMessage(JsonObject jsonObject) {
        return gson.fromJson(jsonObject, TextMessage.class);
    }

    public static ImageMessage toImageMessage(JsonObject jsonObject) {
        return gson.fromJson(jsonObject, ImageMessage.class);
    }

    public static EmoteMessage toEmoteMessage(JsonObject jsonObject) {
        return gson.fromJson(jsonObject, EmoteMessage.class);
    }

    public static ContentResponse toContentResponse(String jsonString) {
        return gson.fromJson(jsonString, ContentResponse.class);
    }

    public static PowerLevels toPowerLevels(JsonObject jsonObject) {
        return gson.fromJson(jsonObject, PowerLevels.class);
    }

    public static JsonObject toJson(Event event) {
        return (JsonObject) gson.toJsonTree(event);
    }
}
