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
package org.matrix.androidsdk.rest.model.group;

import java.io.Serializable;

/**
 * This class represents a community in Matrix.
 */
public class Group implements Serializable {

    /**
     * The group id.
     */
    private String mGroupId;

    /**
     * The community summary.
     */
    private GroupSummary mSummary = new GroupSummary();

    /**
     * The rooms of the community.
     */
    private GroupRooms mRooms = new GroupRooms();

    /**
     * The community members.
     */
    private GroupUsers mUsers = new GroupUsers();

    /**
     * The user membership.
     */
    private String mMembership;

    /**
     * The identifier of the potential inviter (tells wether an invite is pending for this group).
     */
    private String mInviter;

    /**
     * Create an instance with a group id.
     *
     * @param groupId the identifier.
     * @return the MXGroup instance.
     */
    public Group(String groupId) {
        mGroupId = groupId;
    }

    /**
     * @return the group ID
     */
    public String getGroupId() {
        return mGroupId;
    }

    /**
     * @return the group summary
     */
    public GroupSummary getGroupSummary() {
        return mSummary;
    }


    /**
     * Update the group summary
     *
     * @param aGroupSummary the new group summary
     */
    public void setGroupSummary(GroupSummary aGroupSummary) {
        mSummary = aGroupSummary;
    }

    /**
     * @return the group rooms
     */
    public GroupRooms getGroupRooms() {
        return mRooms;
    }

    /**
     * Update the group rooms
     *
     * @param aGroupRooms the new group rooms
     */
    public void setGroupRooms(GroupRooms aGroupRooms) {
        mRooms = aGroupRooms;
    }

    /**
     * @return the group users
     */
    public GroupUsers getGroupUsers() {
        return mUsers;
    }

    /**
     * Update the group users
     *
     * @param aGroupUsers the group users
     */
    public void setGroupUsers(GroupUsers aGroupUsers) {
        mUsers = aGroupUsers;
    }

    /**
     * Update the membership
     *
     * @param membership the new membership
     */
    public void setMembership(String membership) {
        mMembership = membership;
    }

    /**
     * @return the membership
     */
    public String getMembership() {
        return mMembership;
    }

    /**
     * @return the inviter
     */
    public String getInviter() {
        return mInviter;
    }

    /**
     * Update the inviter.
     *
     * @param inviter the inviter.
     */
    public void setInviter(String inviter) {
        mInviter = inviter;
    }
}
