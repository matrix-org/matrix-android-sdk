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

package org.matrix.androidsdk.data.room;

import android.content.Context;
import android.support.test.InstrumentationRegistry;

import junit.framework.Assert;

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.matrix.androidsdk.data.Room;

import java.util.ArrayList;

@FixMethodOrder(MethodSorters.JVM)
public class RoomDisplayNameResolverTest {

    private RoomTestHelper mRoomTestHelper = new RoomTestHelper();

    @Test
    public void RoomName_getRoomDisplayName_noLL_emptyRoom() {
        RoomName_getRoomDisplayName_emptyRoom(false);
    }

    @Test
    public void RoomName_getRoomDisplayName_LL_emptyRoom() {
        RoomName_getRoomDisplayName_emptyRoom(true);
    }

    private void RoomName_getRoomDisplayName_emptyRoom(boolean withLazyLoading) {
        Context context = InstrumentationRegistry.getContext();
        Room room = mRoomTestHelper.createRoom(context, withLazyLoading, 0, false);

        Assert.assertEquals("Empty room", room.getRoomDisplayName(context));
    }

    @Test
    public void RoomName_getRoomDisplayName_noLL_roomName() {
        RoomName_getRoomDisplayName_roomName(false);
    }

    @Test
    public void RoomName_getRoomDisplayName_LL_roomName() {
        RoomName_getRoomDisplayName_roomName(true);
    }

    private void RoomName_getRoomDisplayName_roomName(boolean withLazyLoading) {
        Context context = InstrumentationRegistry.getContext();

        // It does not depend on the number of users
        for (int i = 0; i < 10; i++) {
            Room room = mRoomTestHelper.createRoom(context, withLazyLoading, i, false);

            room.getState().aliases = new ArrayList<>();
            room.getState().aliases.add("Alias");
            Assert.assertEquals("Alias", room.getRoomDisplayName(context));

            // Canonical alias get priority over alias
            room.getState().setCanonicalAlias("Canonical");
            Assert.assertEquals("Canonical", room.getRoomDisplayName(context));

            // Room name get priority over alias and canonical alias
            room.getState().name = "Room Name";
            Assert.assertEquals("Room Name", room.getRoomDisplayName(context));
        }
    }

    @Test
    public void RoomName_getRoomDisplayName_noLL_user() {
        RoomName_getRoomDisplayName_user(false);
    }

    @Test
    public void RoomName_getRoomDisplayName_LL_user() {
        RoomName_getRoomDisplayName_user(true);
    }

    private void RoomName_getRoomDisplayName_user(boolean withLazyLoading) {
        Context context = InstrumentationRegistry.getContext();

        Room room;

        // Only me in the room
        room = mRoomTestHelper.createRoom(context, withLazyLoading, 1, false);
        Assert.assertEquals("Empty room", room.getRoomDisplayName(context));

        // One other user in the room
        room = mRoomTestHelper.createRoom(context, withLazyLoading, 2, false);
        Assert.assertEquals("UserName_2", room.getRoomDisplayName(context));

        // 2 other users in the room
        room = mRoomTestHelper.createRoom(context, withLazyLoading, 3, false);
        Assert.assertEquals("UserName_2 and UserName_3", room.getRoomDisplayName(context));

        room = mRoomTestHelper.createRoom(context, withLazyLoading, 4, false);
        Assert.assertEquals("UserName_2 and 3 others", room.getRoomDisplayName(context));

        room = mRoomTestHelper.createRoom(context, withLazyLoading, 5, false);
        Assert.assertEquals("UserName_2 and 4 others", room.getRoomDisplayName(context));

        room = mRoomTestHelper.createRoom(context, withLazyLoading, 10, false);
        Assert.assertEquals("UserName_2 and 9 others", room.getRoomDisplayName(context));
    }

    @Test
    public void RoomName_getRoomDisplayName_noLL_invitation() {
        RoomName_getRoomDisplayName_invitation(false);
    }

    @Test
    public void RoomName_getRoomDisplayName_LL_invitation() {
        RoomName_getRoomDisplayName_invitation(true);
    }

    private void RoomName_getRoomDisplayName_invitation(boolean withLazyLoading) {
        Context context = InstrumentationRegistry.getContext();

        Room room;

        // Only me in the room
        room = mRoomTestHelper.createRoom(context, withLazyLoading, 1, true);
        Assert.assertEquals("Room Invite", room.getRoomDisplayName(context));

        // One other user in the room
        room = mRoomTestHelper.createRoom(context, withLazyLoading, 2, true);
        Assert.assertEquals("Invite from UserName_2", room.getRoomDisplayName(context));

        // 2 other users in the room
        room = mRoomTestHelper.createRoom(context, withLazyLoading, 3, true);
        Assert.assertEquals("Invite from UserName_2", room.getRoomDisplayName(context));

        room = mRoomTestHelper.createRoom(context, withLazyLoading, 4, true);
        Assert.assertEquals("Invite from UserName_2", room.getRoomDisplayName(context));

        room = mRoomTestHelper.createRoom(context, withLazyLoading, 5, true);
        Assert.assertEquals("Invite from UserName_2", room.getRoomDisplayName(context));

        room = mRoomTestHelper.createRoom(context, withLazyLoading, 10, true);
        Assert.assertEquals("Invite from UserName_2", room.getRoomDisplayName(context));
    }
}
