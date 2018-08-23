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

public class RoomTestHelper {
    /**
     * Create a room, with or without lazy loading and with x number of room members
     * First room member will always be the current user
     *
     * @param context
     * @param withLazyLoading
     * @param nbOfMembers
     * @param amIInvited
     * @return
     */
    public Room createRoom(Context context, boolean withLazyLoading, int nbOfMembers, boolean amIInvited) {
        Credentials credentials = new Credentials();
        credentials.userId = getMyUserId();
        IMXStore store = new MXMemoryStore(credentials, context);

        MXDataHandler mxDataHandler = new MXDataHandler(store, credentials);
        mxDataHandler.setLazyLoadingEnabled(withLazyLoading);

        Room room = new Room(mxDataHandler, store, getRoomId());

        store.storeRoom(room);

        RoomSummary roomSummary = new RoomSummary();
        roomSummary.setRoomId(getRoomId());
        store.storeSummary(roomSummary);

        if (amIInvited) {
            roomSummary.setIsInvited();
        } else {
            roomSummary.setIsJoined();
        }

        if (withLazyLoading && !amIInvited) {
            // Populate room summary
            RoomSyncSummary roomSyncSummary = new RoomSyncSummary();

            roomSyncSummary.joinedMembersCount = nbOfMembers;
            roomSyncSummary.invitedMembersCount = 0;

            // heroes
            // Heroes does not include current user
            if (nbOfMembers >= 2) {
                roomSyncSummary.heroes = new ArrayList<>();
                for (int i = 2; i <= Math.min(6, nbOfMembers); i++) {
                    roomSyncSummary.heroes.add(getUserId(i));
                }
            }

            roomSummary.setRoomSyncSummary(roomSyncSummary);
        }

        if (amIInvited) {
            // Maximum 2 members will be sent by the sync
            initMembers(room.getState(), Math.min(2, nbOfMembers), true);

            // Pass the sender name (the inviter id)
            if (nbOfMembers >= 2) {
                room.getMember(getMyUserId()).mSender = getUserId(2);
            }
        } else {
            initMembers(room.getState(), nbOfMembers, false);
        }

        return room;
    }

    private void initMembers(RoomState roomState, int nbOfMembers, boolean amIInvited) {
        for (int i = 1; i <= nbOfMembers; i++) {
            roomState.setMember(getUserId(i), createRoomMember(i, amIInvited));
        }
    }

    private RoomMember createRoomMember(int i, boolean amIInvited) {
        RoomMember roomMember = new RoomMember();

        roomMember.setUserId(getUserId(i));
        roomMember.displayname = getUserName(i);
        if (i == 1 && amIInvited) {
            roomMember.membership = RoomMember.MEMBERSHIP_INVITE;
        } else {
            roomMember.membership = RoomMember.MEMBERSHIP_JOIN;
        }
        // Add a TS because they will be ordered
        roomMember.setOriginServerTs(i);

        return roomMember;
    }

    public String getMyUserId() {
        return "@MyUserId";
    }

    private String getMyUserName() {
        return "MyUserName";
    }

    private String getRoomId() {
        return "!RoomId";
    }

    public String getUserId(int i) {
        if (i == 1) {
            return getMyUserId();
        }

        return "UserId_" + i;
    }

    private String getUserName(int i) {
        if (i == 1) {
            return getMyUserName();
        }

        return "UserName_" + i;
    }
}
