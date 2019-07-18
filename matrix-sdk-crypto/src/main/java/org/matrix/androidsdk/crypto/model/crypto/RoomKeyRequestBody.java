/*
 * Copyright 2016 OpenMarket Ltd
 * Copyright 2019 New Vector Ltd
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
package org.matrix.androidsdk.crypto.model.crypto;

import com.google.gson.annotations.SerializedName;

/**
 * Class representing an room key request body content
 * <p>
 * Note: Keep Serializable interface for the Realm migration
 */
public class RoomKeyRequestBody implements java.io.Serializable {
    public String algorithm;

    @SerializedName("room_id")
    public String roomId;

    @SerializedName("sender_key")
    public String senderKey;

    @SerializedName("session_id")
    public String sessionId;
}