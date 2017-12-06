/* 
 * Copyright 2014 OpenMarket Ltd
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

import org.matrix.androidsdk.util.Log;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.rest.model.RoomMember;

import java.util.Collection;

public class RoomMemberCountCondition extends Condition {

    private static final String LOG_TAG = RoomMemberCountCondition.class.getSimpleName();

    // NB: Leave the strings in order of descending length
    private static final String[] PREFIX_ARR = new String[]{"==", "<=", ">=", "<", ">", ""};

    public String is;
    private String comparisonPrefix = null;
    private int limit;
    private boolean parseError = false;

    public RoomMemberCountCondition() {
        kind = Condition.KIND_ROOM_MEMBER_COUNT;
    }

    @Override
    public String toString() {
        return "RoomMemberCountCondition{" + "is='" + is + "'}'";
    }

    public boolean isSatisfied(Room room) {
        // sanity check
        if (room == null) return false;

        if (parseError) return false;

        // Parse the is field into prefix and number the first time
        if (comparisonPrefix == null) {
            parseIsField();
            if (parseError) return false;
        }

        int numMembers = getNumberOfMembers(room);
        if ("==".equals(comparisonPrefix) || "".equals(comparisonPrefix)) {
            return numMembers == limit;
        }
        if ("<".equals(comparisonPrefix)) {
            return numMembers < limit;
        }
        if (">".equals(comparisonPrefix)) {
            return numMembers > limit;
        }
        if ("<=".equals(comparisonPrefix)) {
            return numMembers <= limit;
        }
        if (">=".equals(comparisonPrefix)) {
            return numMembers >= limit;
        }
        return false;
    }

    /**
     * Count joined room members in the room.
     *
     * @param room the room
     * @return the number of joined members
     */
    private int getNumberOfMembers(Room room) {
        Collection<RoomMember> members = room.getMembers();
        int n = 0;

        for (RoomMember member : members) {
            if (RoomMember.MEMBERSHIP_JOIN.equals(member.membership)) {
                n++;
            }
        }
        return n;
    }

    /**
     * Parse the is field to extract meaningful information.
     */
    protected void parseIsField() {
        for (String prefix : PREFIX_ARR) {
            if (is.startsWith(prefix)) {
                comparisonPrefix = prefix;
                break;
            }
        }

        if (comparisonPrefix == null) {
            parseError = true;
        } else {
            try {
                limit = Integer.parseInt(is.substring(comparisonPrefix.length()));
            } catch (NumberFormatException e) {
                parseError = true;
            }
        }

        if (parseError) {
            Log.e(LOG_TAG, "parsing error : " + is);
        }
    }
}
