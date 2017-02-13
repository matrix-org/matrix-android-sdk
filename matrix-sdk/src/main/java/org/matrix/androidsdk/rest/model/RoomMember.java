/*
 * Copyright 2014 OpenMarket Ltd
 * Copyright 2017 Vector Creations Ltd
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
package org.matrix.androidsdk.rest.model;

import android.text.TextUtils;

import java.util.Comparator;
/**
 * Class representing a room member: a user with membership information.
 */
public class RoomMember implements java.io.Serializable {
    public static final String MEMBERSHIP_JOIN = "join";
    public static final String MEMBERSHIP_INVITE = "invite";
    public static final String MEMBERSHIP_LEAVE = "leave";
    public static final String MEMBERSHIP_BAN = "ban";

    public String displayname;
    public String avatarUrl;
    public String membership;
    public Invite thirdPartyInvite;

    // tells that the inviter starts a direct chat room
    public Boolean is_direct;

    private String userId = null;
    // timestamp of the event which has created this member
    private long mOriginServerTs = -1;
    // the id of the sender which has created this member
    private String mInviter;

    // the event used to build the room member
    private Event mOriginalEvent = null;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setOriginServerTs(long aTs) {
        mOriginServerTs = aTs;
    }

    public long getOriginServerTs() {
        return mOriginServerTs;
    }

    public void setOriginalEvent(Event event) {
        mOriginalEvent = event;
    }

    public Event getOriginalEvent() {
        return mOriginalEvent;
    }

    public String getInviterId() {
        return mInviter;
    }

    public void setInviterId(String userId) {
        mInviter = userId;
    }

    public String getThirdPartyInviteToken() {
        if ((null != thirdPartyInvite) && (null != thirdPartyInvite.signed)) {
            return thirdPartyInvite.signed.token;
        }

        return null;
    }

    // Comparator to order members alphabetically
    public static Comparator<RoomMember> alphaComparator = new Comparator<RoomMember>() {
        @Override
        public int compare(RoomMember member1, RoomMember member2) {
            String lhs = member1.getName();
            String rhs = member2.getName();

            if (lhs == null) {
                return -1;
            }
            else if (rhs == null) {
                return 1;
            }
            if (lhs.startsWith("@")) {
                lhs = lhs.substring(1);
            }
            if (rhs.startsWith("@")) {
                rhs = rhs.substring(1);
            }
            return String.CASE_INSENSITIVE_ORDER.compare(lhs, rhs);
        }
    };

    /**
     * Test if a room member fields matches with a pattern.
     * The check is done with the displayname and the userId.
     * @param aPattern the pattern to search.
     * @return true if it matches.
     */
    public boolean matchWithPattern(String aPattern) {
        if (TextUtils.isEmpty(aPattern) || TextUtils.isEmpty(aPattern.trim())) {
            return false;
        }

        boolean res = false;

        if (!TextUtils.isEmpty(displayname)) {
            res = (displayname.toLowerCase().indexOf(aPattern) >= 0);
        }

        if (!res && !TextUtils.isEmpty(userId)) {
            res = (userId.toLowerCase().indexOf(aPattern) >= 0);
        }

        return res;
    }

    /**
     * Test if a room member matches with a reg ex.
     * The check is done with the displayname and the userId.
     * @param aRegEx the reg ex
     * @return true if it matches.
     */
    public boolean matchWithRegEx(String aRegEx) {
        if (TextUtils.isEmpty(aRegEx)) {
            return false;
        }

        boolean res = false;

        if (!TextUtils.isEmpty(displayname)) {
            res = displayname.matches(aRegEx);
        }

        if (!res && !TextUtils.isEmpty(userId)) {
            res = userId.matches(aRegEx);
        }

        return res;
    }

    /**
     * Compare two members.
     * The members are equals if each field have the same value.
     * @param otherMember the member to compare.
     * @return true if they define the same member.
     */
    public boolean equals(RoomMember otherMember) {
        // compare to null
        if (null == otherMember) {
            return false;
        }

        // compare display name
        boolean isEqual = TextUtils.equals(displayname, otherMember.displayname);

        if (isEqual) {
            isEqual = TextUtils.equals(avatarUrl, otherMember.avatarUrl);
        }

        if (isEqual) {
            isEqual = TextUtils.equals(membership, otherMember.membership);
        }

        if (isEqual) {
            isEqual = TextUtils.equals(userId, otherMember.userId);
        }

        return isEqual;
    }

    public String getName() {
        if (displayname != null) {
            return displayname;
        }
        if (userId != null) {
            return userId;
        }
        return null;
    }

    public RoomMember deepCopy() {
        RoomMember copy = new RoomMember();
        copy.displayname = displayname;
        copy.avatarUrl = avatarUrl;
        copy.membership = membership;
        copy.userId = userId;
        copy.mOriginalEvent = mOriginalEvent;
        return copy;
    }

    public boolean hasLeft() {
        return RoomMember.MEMBERSHIP_BAN.equals(membership) || RoomMember.MEMBERSHIP_LEAVE.equals(membership);
    }
}
