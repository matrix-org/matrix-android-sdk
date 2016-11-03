/*
 * Copyright 2015 OpenMarket Ltd
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

package org.matrix.androidsdk.crypto.algorithms;

import com.google.gson.JsonElement;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.RoomMember;

/**
 * An interface for encrypting data
 */
public interface IMXEncrypting {

    /**
     * Init
     * @param matrixSession the related 'MXSession'.
     * @param roomId the id of the room we will be sending to.
     */
    void initWithMatrixSession(MXSession matrixSession, String roomId);

    /**
     * Encrypt an event content according to the configuration of the room.
     * @param eventContent the content of the event.
     * @param eventType the type of the event.
     * @param room the room the event will be sent. //@TODO 'room' duplicates roomId in initWithMatrixSession, no?
     * @param callback the asynchronous callback
     */
    void encryptEventContent(JsonElement eventContent, String eventType, Room room, ApiCallback<JsonElement> callback);

    /**
     * Called when the membership of a member of the room changes.
     * @param event the event causing the change.
     * @param member the user whose membership changed.
     * @param oldMembership the previous membership.
     */
     void onRoomMembership(Event event, RoomMember member, String oldMembership);

    /**
     * Called when a new device announces itself in the room
     * @param {string} userId    owner of the device
     * @param {string} deviceId  deviceId of the device
     */
    void onNewDevice(String deviceId, String userId);

    /**
     * Called when the device verification status is updated
     * @param deviceId owner of the device
     * @param userId deviceId of the device
     */
    void onDeviceVerificationStatusUpdate(String userId, String deviceId);
}
