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
package org.matrix.androidsdk.rest.model.group;

import android.text.TextUtils;

import java.io.Serializable;

/**
 * This class represents a room linked to a community
 */
public class GroupRoom implements Serializable {
    /**
     * The main address of the room.
     */
    public String canonicalAlias;

    /**
     * The ID of the room.
     */
    public String roomId;

    /**
     * The name of the room, if any. May be nil.
     */
    public String name;

    /**
     * The topic of the room, if any. May be nil.
     */
    public String topic;

    /**
     * The number of members joined to the room.
     */
    public Integer numJoinedMembers;

    /**
     * Whether the room may be viewed by guest users without joining.
     */
    public Boolean worldReadable;

    /**
     * Whether guest users may join the room and participate in it.
     * If they can, they will be subject to ordinary power level rules like any other user.
     */
    public Boolean guestCanJoin;

    /**
     * The URL for the room's avatar. May be nil.
     */
    public String avatarUrl;

    /**
     * Tell whether the room is public.
     */
    public Boolean isPublic;

    /**
     * @return the display name
     */
    public String getDisplayName() {
        return !TextUtils.isEmpty(name) ? name : roomId;
    }
}
