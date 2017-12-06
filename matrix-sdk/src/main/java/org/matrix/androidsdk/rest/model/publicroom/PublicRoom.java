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
package org.matrix.androidsdk.rest.model.publicroom;

import org.matrix.androidsdk.data.RoomState;

/**
 * Class representing the objects returned by /publicRooms call.
 */
public class PublicRoom extends RoomState implements java.io.Serializable {

    // number of members which have joined the room (the members list is not provided)
    public int numJoinedMembers;

    // the server name
    public String serverName;

    // true when the room history is visible (room preview)
    public Boolean worldReadable;

    // a guest can join the room
    public Boolean guestCanJoin;
}
