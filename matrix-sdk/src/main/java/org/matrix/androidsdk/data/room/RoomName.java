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
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.util.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * This class helps to compute a room name
 */
public class RoomName {

    private static final String LOG_TAG = RoomName.class.getSimpleName();

    private final Room mRoom;

    public RoomName(Room room) {
        mRoom = room;
    }

    public String getRoomDisplayName(Context context) {
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

            List<RoomMember> othersActiveMembers = new ArrayList<>();
            List<RoomMember> activeMembers = new ArrayList<>();

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
                        if (!TextUtils.equals(member.getUserId(), mRoom.getDataHandler().getUserId())) {
                            othersActiveMembers.add(member);
                        }
                        activeMembers.add(member);
                    }
                }

                Collections.sort(othersActiveMembers, new Comparator<RoomMember>() {
                    @Override
                    public int compare(RoomMember m1, RoomMember m2) {
                        long diff = m1.getOriginServerTs() - m2.getOriginServerTs();

                        return (diff == 0) ? 0 : ((diff < 0) ? -1 : +1);
                    }
                });
            }

            nbOfOtherMembers = othersActiveMembers.size();

            String displayName;

            if (mRoom.isInvited()) {
                if (othersActiveMembers.size() == 1) {
                    // this is current user
                    RoomMember member = activeMembers.get(0);

                    if (TextUtils.equals(member.membership, RoomMember.MEMBERSHIP_INVITE)) {
                        if (!TextUtils.isEmpty(member.mSender)) {
                            // extract who invited us to the room
                            displayName = context.getString(R.string.room_displayname_invite_from, roomState.getMemberName(member.mSender));
                        } else {
                            displayName = context.getString(R.string.room_displayname_room_invite);
                        }
                    } else {
                        displayName = context.getString(R.string.room_displayname_room_invite);
                    }
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
            Log.e(LOG_TAG, "## getRoomDisplayName() failed " + e.getMessage(), e);
        }

        return mRoom.getRoomId();
    }
}
