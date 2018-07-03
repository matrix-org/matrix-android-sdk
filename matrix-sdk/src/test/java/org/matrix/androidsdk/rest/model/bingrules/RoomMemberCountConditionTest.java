/* 
 * Copyright 2014 OpenMarket Ltd
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
package org.matrix.androidsdk.rest.model.bingrules;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class RoomMemberCountConditionTest {

    private RoomMemberCountCondition condition = new RoomMemberCountCondition();

    @Mock
    private Room mockRoom;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        setUpThreeRoomMembers();
    }

    private void setUpThreeRoomMembers() {
        List<RoomMember> members = new ArrayList<>();

        RoomMember rm1 = new RoomMember();
        rm1.membership = RoomMember.MEMBERSHIP_JOIN;
        members.add(rm1);

        RoomMember rm2 = new RoomMember();
        rm2.membership = RoomMember.MEMBERSHIP_JOIN;
        members.add(rm2);

        RoomMember rm3 = new RoomMember();
        rm3.membership = RoomMember.MEMBERSHIP_JOIN;
        members.add(rm3);

        // This one shouldn't count because they're not joined
        RoomMember rm4 = new RoomMember();
        rm4.membership = RoomMember.MEMBERSHIP_LEAVE;
        members.add(rm4);

        Mockito.when(mockRoom.getMembers()).thenReturn(members);
    }

    @Test
    public void testRoomMemberCount() {
        condition.is = "3";
        condition.parseIsField();
        Assert.assertTrue(condition.isSatisfied(mockRoom));

        condition.is = "4";
        condition.parseIsField();
        Assert.assertFalse(condition.isSatisfied(mockRoom));

        condition.is = "2";
        condition.parseIsField();
        Assert.assertFalse(condition.isSatisfied(mockRoom));
    }

    @Test
    public void testEquals() {
        condition.is = "==3";
        condition.parseIsField();
        Assert.assertTrue(condition.isSatisfied(mockRoom));

        condition.is = "==5";
        condition.parseIsField();
        Assert.assertFalse(condition.isSatisfied(mockRoom));
    }

    @Test
    public void testLess() {
        condition.is = "<5";
        condition.parseIsField();
        Assert.assertTrue(condition.isSatisfied(mockRoom));

        condition.is = "<3";
        condition.parseIsField();
        Assert.assertFalse(condition.isSatisfied(mockRoom));
    }

    @Test
    public void testGreater() {
        condition.is = ">2";
        condition.parseIsField();
        Assert.assertTrue(condition.isSatisfied(mockRoom));

        condition.is = ">3";
        condition.parseIsField();
        Assert.assertFalse(condition.isSatisfied(mockRoom));
    }

    @Test
    public void testLessOrEqual() {
        condition.is = "<=5";
        condition.parseIsField();
        Assert.assertTrue(condition.isSatisfied(mockRoom));

        condition.is = "<=3";
        condition.parseIsField();
        Assert.assertTrue(condition.isSatisfied(mockRoom));

        condition.is = "<=2";
        condition.parseIsField();
        Assert.assertFalse(condition.isSatisfied(mockRoom));
    }

    @Test
    public void testGreaterOrEqual() {
        condition.is = ">=2";
        condition.parseIsField();
        Assert.assertTrue(condition.isSatisfied(mockRoom));

        condition.is = ">=3";
        condition.parseIsField();
        Assert.assertTrue(condition.isSatisfied(mockRoom));

        condition.is = ">=5";
        condition.parseIsField();
        Assert.assertFalse(condition.isSatisfied(mockRoom));
    }

    @Test
    public void testParseError() {
        condition.is = "f2";
        condition.parseIsField();
        Assert.assertFalse(condition.isSatisfied(mockRoom));

        condition.is = "x";
        condition.parseIsField();
        Assert.assertFalse(condition.isSatisfied(mockRoom));
    }
}
