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
import org.matrix.androidsdk.rest.model.ImageMessage;
import org.matrix.androidsdk.rest.model.Message;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.TextMessage;
import org.matrix.androidsdk.rest.model.User;

/**
 * Static methods for converting json into objects.
 */
public class JsonUtils {

    private static Gson gson = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();

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
        return gson.fromJson(jsonObject, Message.class);
    }

    public static TextMessage toTextMessage(JsonObject jsonObject) {
        return gson.fromJson(jsonObject, TextMessage.class);
    }

    public static ImageMessage toImageMessage(JsonObject jsonObject) {
        return gson.fromJson(jsonObject, ImageMessage.class);
    }
}
