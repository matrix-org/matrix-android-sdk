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

import org.junit.Test;
import org.matrix.androidsdk.MXDataHandler;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.store.IMXStore;
import org.matrix.androidsdk.data.store.MXMemoryStore;
import org.matrix.androidsdk.rest.model.login.Credentials;

import java.util.ArrayList;

public class RoomNameTest {

    @Test
    public void RoomName_getRoomDisplayName_emptyRoom() {
        Context context = InstrumentationRegistry.getContext();
        Room room = createRoom(context, false);

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
        Room room = createRoom(context, withLazyLoading);

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

    /* ==========================================================================================
     * Private
     * ========================================================================================== */

    private Room createRoom(Context context, boolean withLazyLoading) {
        Credentials credentials = new Credentials();
        IMXStore store = new MXMemoryStore(credentials, context);

        MXDataHandler mxDataHandler = new MXDataHandler(store, credentials);
        mxDataHandler.setLazyLoadingEnabled(withLazyLoading);

        Room room = new Room(mxDataHandler, store, "roomId");

        return room;
    }
}
