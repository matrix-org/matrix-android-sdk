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

@FixMethodOrder(MethodSorters.JVM)
public class RoomAvatarResolverTest {

    private RoomTestHelper mRoomTestHelper = new RoomTestHelper();

    @Test
    public void RoomAvatar_getAvatar_noLL_avatar() {
        RoomAvatar_getAvatar_avatar(false);
    }

    @Test
    public void RoomAvatar_getAvatar_LL_avatar() {
        RoomAvatar_getAvatar_avatar(true);
    }

    private void RoomAvatar_getAvatar_avatar(boolean withLazyLoading) {
        Context context = InstrumentationRegistry.getContext();

        // It does not depend on the number of users
        for (int i = 0; i < 10; i++) {
            Room room = mRoomTestHelper.createRoom(context, withLazyLoading, i, false);

            room.getState().avatar_url = "mxc://avatar_url";
            Assert.assertEquals("mxc://avatar_url", room.getAvatarUrl());
        }
    }

    @Test
    public void RoomAvatar_getAvatar_noLL_noAvatar() {
        RoomAvatar_getAvatar_noAvatar(false);
    }

    @Test
    public void RoomAvatar_getAvatar_LL_noAvatar() {
        RoomAvatar_getAvatar_noAvatar(true);
    }

    private void RoomAvatar_getAvatar_noAvatar(boolean withLazyLoading) {
        Context context = InstrumentationRegistry.getContext();

        Room room;

        // Only me in the room
        room = mRoomTestHelper.createRoom(context, withLazyLoading, 1, false);
        Assert.assertNull(room.getAvatarUrl());

        // I have an avatar
        room.getMember(mRoomTestHelper.getMyUserId()).avatarUrl = "mxc://my_avatar_url";
        Assert.assertEquals("mxc://my_avatar_url", room.getAvatarUrl());

        // One other user in the room
        room = mRoomTestHelper.createRoom(context, withLazyLoading, 2, false);
        Assert.assertNull(room.getAvatarUrl());

        // I have an avatar
        room.getMember(mRoomTestHelper.getMyUserId()).avatarUrl = "mxc://my_avatar_url";
        Assert.assertNull(room.getAvatarUrl());

        // Other user has an avatar
        room.getMember(mRoomTestHelper.getUserId(2)).avatarUrl = "mxc://other_user_avatar_url";
        Assert.assertEquals("mxc://other_user_avatar_url", room.getAvatarUrl());

        // 2 other users in the room
        room = mRoomTestHelper.createRoom(context, withLazyLoading, 3, false);

        // I have an avatar
        room.getMember(mRoomTestHelper.getMyUserId()).avatarUrl = "mxc://my_avatar_url";
        // Other user has an avatar
        room.getMember(mRoomTestHelper.getUserId(2)).avatarUrl = "mxc://other_user_avatar_url";

        Assert.assertNull(room.getAvatarUrl());
    }

    @Test
    public void RoomAvatar_getAvatar_noLL_invitation() {
        RoomAvatar_getAvatar_invitation(false);
    }

    @Test
    public void RoomAvatar_getAvatar_LL_invitation() {
        RoomAvatar_getAvatar_invitation(true);
    }

    private void RoomAvatar_getAvatar_invitation(boolean withLazyLoading) {
        Context context = InstrumentationRegistry.getContext();

        Room room;

        // Only me in the room
        room = mRoomTestHelper.createRoom(context, withLazyLoading, 1, true);
        Assert.assertNull(room.getAvatarUrl());

        // I have an avatar
        room.getMember(mRoomTestHelper.getMyUserId()).avatarUrl = "mxc://my_avatar_url";
        Assert.assertEquals("mxc://my_avatar_url", room.getAvatarUrl());

        // One other user in the room
        room = mRoomTestHelper.createRoom(context, withLazyLoading, 2, true);
        Assert.assertNull(room.getAvatarUrl());

        // I have an avatar
        room.getMember(mRoomTestHelper.getMyUserId()).avatarUrl = "mxc://my_avatar_url";
        Assert.assertNull(room.getAvatarUrl());

        // Inviter has an avatar
        room.getMember(mRoomTestHelper.getUserId(2)).avatarUrl = "mxc://other_user_avatar_url";
        Assert.assertEquals("mxc://other_user_avatar_url", room.getAvatarUrl());

        // 2 other users in the room
        room = mRoomTestHelper.createRoom(context, withLazyLoading, 3, true);

        // I have an avatar
        room.getMember(mRoomTestHelper.getMyUserId()).avatarUrl = "mxc://my_avatar_url";
        // Other user has an avatar
        room.getMember(mRoomTestHelper.getUserId(2)).avatarUrl = "mxc://other_user_avatar_url";

        Assert.assertEquals("mxc://other_user_avatar_url", room.getAvatarUrl());
    }
}
