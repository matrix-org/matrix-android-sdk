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
import android.text.TextUtils;

import org.matrix.androidsdk.R;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.data.comparator.Comparators;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.util.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * This class computes room display name
 */
public class RoomDisplayNameResolver {

    private static final String LOG_TAG = RoomDisplayNameResolver.class.getSimpleName();

    private final Room mRoom;

    public RoomDisplayNameResolver(Room room) {
        mRoom = room;
    }

    /**
     * Compute the room display name
     *
     * @param context
     * @return the room display name
     */
    public String resolve(Context context) {
        try {
            // this algorithm is the one defined in
            // https://github.com/matrix-org/matrix-js-sdk/blob/develop/lib/models/room.js#L617
            // calculateRoomName(room, userId)

            // For Lazy Loaded room, see algorithm here:
            // https://docs.google.com/document/d/11i14UI1cUz-OJ0knD5BFu7fmT6Fo327zvMYqfSAR7xs/edit#heading=h.qif6pkqyjgzn

            RoomState roomState = mRoom.getState();

            if (!TextUtils.isEmpty(roomState.name)) {
                return roomState.name;
            }

            if (!TextUtils.isEmpty(roomState.getCanonicalAlias())) {
                return roomState.getCanonicalAlias();
            }

            // Temporary patch: use the first alias if available
            if (roomState.aliases != null && !roomState.aliases.isEmpty()) {
                return roomState.aliases.get(0);
            }

            // List of active members, current user excluded
            List<RoomMember> othersActiveMembers = new ArrayList<>();
            RoomMember currentUser = null;

            int nbOfOtherMembers;

            if (mRoom.getDataHandler().isLazyLoadingEnabled()
                    && mRoom.isJoined()
                    && mRoom.getRoomSummary() != null) {
                List<String> heroes = mRoom.getRoomSummary().getHeroes();

                for (String id : heroes) {
                    RoomMember roomMember = roomState.getMember(id);

                    if (roomMember != null) {
                        othersActiveMembers.add(roomMember);
                    }
                }
            } else {
                Collection<RoomMember> members = roomState.getDisplayableLoadedMembers();

                for (RoomMember member : members) {
                    if (!TextUtils.equals(member.membership, RoomMember.MEMBERSHIP_LEAVE)) {
                        if (TextUtils.equals(member.getUserId(), mRoom.getDataHandler().getUserId())) {
                            currentUser = member;
                        } else {
                            othersActiveMembers.add(member);
                        }
                    }
                }

                Collections.sort(othersActiveMembers, Comparators.ascComparator);
            }

            nbOfOtherMembers = othersActiveMembers.size();

            String displayName;

            if (mRoom.isInvited()) {
                if (currentUser != null
                        && !othersActiveMembers.isEmpty()
                        && !TextUtils.isEmpty(currentUser.mSender)) {
                    // extract who invited us to the room
                    displayName = context.getString(R.string.room_displayname_invite_from, roomState.getMemberName(currentUser.mSender));
                } else {
                    displayName = context.getString(R.string.room_displayname_room_invite);
                }
            } else {
                if (nbOfOtherMembers == 0) {
                    displayName = context.getString(R.string.room_displayname_empty_room);
                } else if (nbOfOtherMembers == 1) {
                    RoomMember member = othersActiveMembers.get(0);
                    displayName = roomState.getMemberName(member.getUserId());
                } else if (nbOfOtherMembers == 2) {
                    RoomMember member1 = othersActiveMembers.get(0);
                    RoomMember member2 = othersActiveMembers.get(1);

                    displayName = context.getString(R.string.room_displayname_two_members,
                            roomState.getMemberName(member1.getUserId()), roomState.getMemberName(member2.getUserId()));
                } else {
                    RoomMember member = othersActiveMembers.get(0);
                    displayName = context.getResources().getQuantityString(R.plurals.room_displayname_three_and_more_members,
                            mRoom.getNumberOfJoinedMembers() - 1,
                            roomState.getMemberName(member.getUserId()),
                            mRoom.getNumberOfJoinedMembers() - 1);
                }
            }

            return displayName;
        } catch (Exception e) {
            Log.e(LOG_TAG, "## Computing room display name failed " + e.getMessage(), e);
        }

        return mRoom.getRoomId();
    }
}
