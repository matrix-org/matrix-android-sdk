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

package org.matrix.androidsdk.rest.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class RoomEventFilter {
    @SerializedName("limit")
    Integer maxNumberOfEvents;

    @SerializedName("not_senders")
    List<String> mSenderIdsToExclude;

    @SerializedName("not_types")
    List<String> mTypesToExclude;

    @SerializedName("senders")
    List<String> mSenderIds;

    @SerializedName("types")
    List<String> mTypes;

    @SerializedName("not_rooms")
    List<String> mRoomIdsToExclude;

    @SerializedName("rooms")
    List<String> mRooms;

    @SerializedName("contains_url")
    String mContainsUrl;
}
