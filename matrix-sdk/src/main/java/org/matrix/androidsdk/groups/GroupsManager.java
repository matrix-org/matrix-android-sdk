/*
 * Copyright 2016 OpenMarket Ltd
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

package org.matrix.androidsdk.groups;

import org.matrix.androidsdk.MXDataHandler;
import org.matrix.androidsdk.data.store.IMXStore;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.client.GroupsRestClient;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.group.Group;
import org.matrix.androidsdk.rest.model.group.GroupProfile;
import org.matrix.androidsdk.rest.model.group.GroupSummary;
import org.matrix.androidsdk.rest.model.group.GroupSyncProfile;
import org.matrix.androidsdk.util.Log;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * This class manages the groups
 */
public class GroupsManager {
    private static final String LOG_TAG = GroupsManager.class.getSimpleName();

    private MXDataHandler mDataHandler;
    private GroupsRestClient mGroupsRestClient;
    private IMXStore mStore;

    // callbacks
    private Set<SimpleApiCallback<Void>> mRefreshSummariesCallback = new HashSet<>();


    /**
     * Constructor
     *
     * @param dataHandler the data handler
     * @param restClient  the group rest client
     */
    public GroupsManager(MXDataHandler dataHandler, GroupsRestClient restClient) {
        mDataHandler = dataHandler;
        mStore = mDataHandler.getStore();
        mGroupsRestClient = restClient;
    }

    /**
     * @return the groups rest client
     */
    public GroupsRestClient getGroupsRestClient() {
        return mGroupsRestClient;
    }

    /**
     * Retrieve the group from a group id
     *
     * @param groupId the group id
     * @return the group if it exists
     */
    public Group getGroup(String groupId) {
        return mStore.getGroup(groupId);
    }

    /**
     * @return the existing groups
     */
    public Collection<Group> getGroups() {
        return mStore.getGroups();
    }

    /**
     * Manage the group joining.
     *
     * @param groupId the group id
     * @param notify  true to notify
     */
    public void onJoinGroup(final String groupId, final boolean notify) {
        Group group = getGroup(groupId);

        if (null != group) {
            group = new Group(groupId);
        }

        group.setMembership(RoomMember.MEMBERSHIP_JOIN);
        mStore.storeGroup(group);

        // try retrieve  the summary
        mGroupsRestClient.getGroupSummary(groupId, new ApiCallback<GroupSummary>() {
            /**
             * Common method
             */
            private void onDone() {
                if (notify) {
                    mDataHandler.onJoinGroup(groupId);
                }
            }

            @Override
            public void onSuccess(GroupSummary groupSummary) {
                Group group = getGroup(groupId);

                if (null != group) {
                    group.setGroupSummary(groupSummary);
                    mStore.flushGroup(group);
                    onDone();
                }
            }

            @Override
            public void onNetworkError(Exception e) {
                Log.e(LOG_TAG, "## onJoinGroup() : failed " + e.getMessage());
                onDone();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                Log.e(LOG_TAG, "## onMatrixError() : failed " + e.getMessage());
                onDone();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                Log.e(LOG_TAG, "## onUnexpectedError() : failed " + e.getMessage());
                onDone();
            }
        });
    }

    /**
     * Create a group from an invitation.
     *
     * @param groupId the group id
     * @param profile the profile
     * @param inviter the inviter
     * @param notify  true to notify
     */
    public void onNewGroupInvitation(String groupId, GroupSyncProfile profile, String inviter, boolean notify) {
        Group group = getGroup(groupId);

        // it should always be null
        if (null != group) {
            group = new Group(groupId);
        }

        GroupSummary summary = new GroupSummary();
        summary.profile = new GroupProfile();
        summary.profile.name = profile.name;
        summary.profile.avatarUrl = profile.avatarUrl;

        group.setGroupSummary(summary);
        group.setInviter(inviter);
        group.setMembership(RoomMember.MEMBERSHIP_INVITE);

        mStore.storeGroup(group);

        if (notify) {
            mDataHandler.onNewGroupInvitation(groupId);
        }
    }

    /**
     * Remove a group.
     *
     * @param groupId the group id.
     * @param notify  true to notify
     */
    public void onLeaveGroup(String groupId, boolean notify) {
        mStore.deleteGroup(groupId);

        if (notify) {
            mDataHandler.onLeaveGroup(groupId);
        }
    }

    /**
     * Refresh the group summaries
     *
     * @param callback the asynchronous callback
     */
    public void refreshGroupSummaries(SimpleApiCallback<Void> callback) {
        if (!mRefreshSummariesCallback.isEmpty()) {
            Log.d(LOG_TAG, "## refreshGroupSummaries() : there already is a pending request");
            mRefreshSummariesCallback.add(callback);
            return;
        }

        mRefreshSummariesCallback.add(callback);

        Collection<Group> groups = getGroups();

        groups.iterator();
    }

    /**
     * Internal method to refresh the group summaries.
     *
     * @param iterator the iterator.
     */
    private void refreshGroupSummaries(final Iterator<Group> iterator) {
        if (!iterator.hasNext()) {
            for (SimpleApiCallback<Void> callback : mRefreshSummariesCallback) {
                try {
                    callback.onSuccess(null);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "## refreshGroupSummaries() failed " + e.getMessage());
                }
            }
            mRefreshSummariesCallback.clear();
            return;
        }

        final String groupId = iterator.next().getGroupId();

        mGroupsRestClient.getGroupSummary(groupId, new ApiCallback<GroupSummary>() {
            private void onDone() {
                refreshGroupSummaries(iterator);
            }

            @Override
            public void onSuccess(GroupSummary groupSummary) {
                Group group = getGroup(groupId);

                if (null != group) {
                    group.setGroupSummary(groupSummary);
                    mStore.flushGroup(group);
                    onDone();
                }
            }

            @Override
            public void onNetworkError(Exception e) {
                Log.e(LOG_TAG, "## refreshGroupSummaries() : failed " + e.getMessage());
                onDone();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                Log.e(LOG_TAG, "## refreshGroupSummaries() : failed " + e.getMessage());
                onDone();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                Log.e(LOG_TAG, "## refreshGroupSummaries() : failed " + e.getMessage());
                onDone();
            }
        });
    }
}
