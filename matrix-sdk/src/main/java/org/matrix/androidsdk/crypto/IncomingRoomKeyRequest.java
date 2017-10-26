/*
 * Copyright 2016 OpenMarket Ltd
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

package org.matrix.androidsdk.crypto;

import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.RoomKeyRequest;
import org.matrix.androidsdk.rest.model.RoomKeyRequestBody;

import org.matrix.androidsdk.util.JsonUtils;

import java.io.Serializable;

public class IncomingRoomKeyRequest implements Serializable {

    public String mUserId;
    public String mDeviceId;
    public String mRequestId;
    public RoomKeyRequestBody mRequestBody;
    public Runnable mShare;

    public IncomingRoomKeyRequest(Event event) {
        mUserId = event.getSender();

        RoomKeyRequest roomKeyRequest = JsonUtils.toRoomKeyRequest(event.getContentAsJsonObject());
        mDeviceId = roomKeyRequest.requesting_device_id;
        mRequestId = roomKeyRequest.request_id;
        mRequestBody = (null != roomKeyRequest.body) ? roomKeyRequest.body : new RoomKeyRequestBody();
    }


}

