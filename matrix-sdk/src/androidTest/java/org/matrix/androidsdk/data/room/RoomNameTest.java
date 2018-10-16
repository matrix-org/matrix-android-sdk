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
import org.matrix.androidsdk.MXDataHandler;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.data.RoomSummary;
import org.matrix.androidsdk.data.store.IMXStore;
import org.matrix.androidsdk.data.store.MXMemoryStore;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.login.Credentials;
import org.matrix.androidsdk.rest.model.sync.RoomSyncSummary;

import java.util.ArrayList;

@FixMethodOrder(MethodSorters.JVM)
public class RoomNameTest {

    @Test
    public void RoomName_getRoomDisplayName_LL_emptyRoom() {
        RoomName_getRoomDisplayName_emptyRoom(true);
    }

    @Test
    public void RoomName_getRoomDisplayName_noLL_emptyRoom() {
        RoomName_getRoomDisplayName_emptyRoom(false);
    }

    private void RoomName_getRoomDisplayName_emptyRoom(boolean withLazyLoading) {
        Context context = InstrumentationRegistry.getContext();
        Room room = createRoom(context, withLazyLoading, 0);

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
            Room room = createRoom(context, withLazyLoading, i);

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

        room = createRoom(context, withLazyLoading, 1);
        Assert.assertEquals(getUserName(1), room.getRoomDisplayName(context));

        room = createRoom(context, withLazyLoading, 2);
        Assert.assertEquals(getUserName(1) + " and " + getUserName(2), room.getRoomDisplayName(context));

        room = createRoom(context, withLazyLoading, 3);
        Assert.assertEquals(getUserName(1) + " and 2 others", room.getRoomDisplayName(context));

        room = createRoom(context, withLazyLoading, 4);
        Assert.assertEquals(getUserName(1) + " and 3 others", room.getRoomDisplayName(context));

        room = createRoom(context, withLazyLoading, 5);
        Assert.assertEquals(getUserName(1) + " and 4 others", room.getRoomDisplayName(context));

        room = createRoom(context, withLazyLoading, 10);
        Assert.assertEquals(getUserName(1) + " and 9 others", room.getRoomDisplayName(context));
    }

    // TODO Test with me as a member
    // TODO Test with invitation

    /* ==========================================================================================
     * Private
     * ========================================================================================== */

    private Room createRoom(Context context, boolean withLazyLoading, int nbOfMembers) {
        Credentials credentials = new Credentials();
        IMXStore store = new MXMemoryStore(credentials, context);

        MXDataHandler mxDataHandler = new MXDataHandler(store, credentials);
        mxDataHandler.setLazyLoadingEnabled(withLazyLoading);

        Room room = new Room(mxDataHandler, store, getRoomId());

        store.storeRoom(room);

        if (withLazyLoading) {
            // We need a room summary
            RoomSummary roomSummary = new RoomSummary();
            roomSummary.setRoomId(getRoomId());

            RoomSyncSummary roomSyncSummary = new RoomSyncSummary();

            roomSyncSummary.joinedMembersCount = nbOfMembers;
            roomSyncSummary.invitedMembersCount = 0;

            // heroes
            if (nbOfMembers > 0) {
                roomSyncSummary.heroes = new ArrayList<>();
                for (int i = 1; i <= Math.min(5, nbOfMembers); i++) {
                    roomSyncSummary.heroes.add(getUserId(i));
                }
            }

            roomSummary.setRoomSyncSummary(roomSyncSummary);

            store.storeSummary(roomSummary);
        }

        initMembers(room.getState(), nbOfMembers);

        return room;
    }

    private void initMembers(RoomState roomState, int nbOfMembers) {
        for (int i = 1; i <= nbOfMembers; i++) {
            roomState.setMember(getUserId(i), createRoomMember(i));
        }
    }

    private RoomMember createRoomMember(int i) {
        RoomMember roomMember = new RoomMember();

        roomMember.setUserId(getUserId(i));
        roomMember.displayname = getUserName(i);
        roomMember.membership = RoomMember.MEMBERSHIP_JOIN;
        // Add a TS because they will be ordered
        roomMember.setOriginServerTs(i);

        return roomMember;
    }

    private String getRoomId() {
        return "!RoomId";
    }

    private String getUserId(int i) {
        return "UserId_" + i;
    }

    private String getUserName(int i) {
        return "UserName_" + i;
    }
}