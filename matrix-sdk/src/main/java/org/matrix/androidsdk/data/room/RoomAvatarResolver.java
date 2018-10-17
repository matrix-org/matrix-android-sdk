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

import android.support.annotation.Nullable;
import android.text.TextUtils;

import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.rest.model.RoomMember;

/**
 * This class computes room avatar
 */
public class RoomAvatarResolver {

    private static final String LOG_TAG = RoomAvatarResolver.class.getSimpleName();

    private final Room mRoom;

    public RoomAvatarResolver(Room room) {
        mRoom = room;
    }

    /**
     * Compute the room avatar url
     *
     * @return the room avatar url, can be a fallback to a room member avatar or null
     */
    @Nullable
    public String resolve() {
        String res = mRoom.getState().getAvatarUrl();

        if (res == null) {
            if (mRoom.isInvited()) {
                // In this case, if LazyLoading is ON, we cannot rely of mRoom.getNumberOfMembers() (it will return 0)
                if (mRoom.getState().getLoadedMembers().size() == 1) {
                    res = mRoom.getState().getLoadedMembers().get(0).getAvatarUrl();
                } else if (mRoom.getState().getLoadedMembers().size() > 1) {
                    RoomMember m1 = mRoom.getState().getLoadedMembers().get(0);
                    RoomMember m2 = mRoom.getState().getLoadedMembers().get(1);

                    res = TextUtils.equals(m1.getUserId(), mRoom.getDataHandler().getUserId()) ? m2.getAvatarUrl() : m1.getAvatarUrl();
                }
            } else {
                // detect if it is a room with no more than 2 members (i.e. an alone or a 1:1 chat)
                if (mRoom.getNumberOfMembers() == 1 && !mRoom.getState().getLoadedMembers().isEmpty()) {
                    res = mRoom.getState().getLoadedMembers().get(0).getAvatarUrl();
                } else if (mRoom.getNumberOfMembers() == 2 && mRoom.getState().getLoadedMembers().size() > 1) {
                    RoomMember m1 = mRoom.getState().getLoadedMembers().get(0);
                    RoomMember m2 = mRoom.getState().getLoadedMembers().get(1);

                    res = TextUtils.equals(m1.getUserId(), mRoom.getDataHandler().getUserId()) ? m2.getAvatarUrl() : m1.getAvatarUrl();
                }
            }
        }

        return res;
    }
}
