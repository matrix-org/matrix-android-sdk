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

package org.matrix.androidsdk.lazyloading;

import android.support.test.InstrumentationRegistry;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.common.CommonTestHelper;

import java.util.Arrays;
import java.util.List;

@FixMethodOrder(MethodSorters.JVM)
public class RoomNameTest {

    private CommonTestHelper mTestHelper = new CommonTestHelper();
    private RoomNameTestHelper mRoomNameTestHelper = new RoomNameTestHelper(mTestHelper);

    private List<Integer> userQuantities = Arrays.asList(1, 2, 3, 10);

    @BeforeClass
    public static void init() {
        MXSession.initUserAgent(InstrumentationRegistry.getContext(), null);
    }

    @Test
    public void RoomName_noName_ShouldLoadAllMembers() throws Exception {
        RoomState_noName(false);
    }

    @Test
    public void RoomName_noName_LazyLoading() throws Exception {
        RoomState_noName(true);
    }

    private void RoomState_noName(final boolean withLazyLoading) throws Exception {
        for (int qty : userQuantities) {
            RoomNameScenarioData data = mRoomNameTestHelper.createScenario(qty, null, withLazyLoading);
            checkAllName(data, null);
            mRoomNameTestHelper.clearAllSessions(data);
        }
    }

    @Test
    public void RoomName_name_ShouldLoadAllMembers() throws Exception {
        RoomState_name(false);
    }

    @Test
    public void RoomName_name_LazyLoading() throws Exception {
        RoomState_name(true);
    }

    private void RoomState_name(final boolean withLazyLoading) throws Exception {
        for (int qty : userQuantities) {
            RoomNameScenarioData data = mRoomNameTestHelper.createScenario(qty, "Room name " + qty, withLazyLoading);
            checkAllName(data, "Room name " + qty);
            mRoomNameTestHelper.clearAllSessions(data);
        }
    }

    /* ==========================================================================================
     * PRIVATE
     * ========================================================================================== */

    private void checkAllName(RoomNameScenarioData roomNameScenarioData, String expectedName) {
        for (MXSession session : roomNameScenarioData.userSessions) {
            Assert.assertEquals(expectedName, session.getDataHandler().getRoom(roomNameScenarioData.roomId).getState().name);
        }
    }
}
