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

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import org.matrix.androidsdk.MXDataHandler;
import org.matrix.androidsdk.data.store.IMXStore;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.client.GroupsRestClient;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.group.CreateGroupParams;
import org.matrix.androidsdk.rest.model.group.Group;
import org.matrix.androidsdk.rest.model.group.GroupProfile;
import org.matrix.androidsdk.rest.model.group.GroupRooms;
import org.matrix.androidsdk.rest.model.group.GroupSummary;
import org.matrix.androidsdk.rest.model.group.GroupSyncProfile;
import org.matrix.androidsdk.rest.model.group.GroupUsers;
import org.matrix.androidsdk.util.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
    private Set<SimpleApiCallback<Void>> mRefreshProfilesCallback = new HashSet<>();

    //
    private final Map<String, ApiCallback<Void>> mPendingJoinGroups = new HashMap<>();
    private final Map<String, ApiCallback<Void>> mPendingLeaveGroups = new HashMap<>();

    private Handler mUIHandler;

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

        mUIHandler = new Handler(Looper.getMainLooper());
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
     * @return the groups list in which the user is invited
     */
    public Collection<Group> getInvitedGroups() {
        List<Group> invitedGroups = new ArrayList<>();
        Collection<Group> groups = getGroups();

        for (Group group : groups) {
            if (group.isInvited()) {
                invitedGroups.add(group);
            }
        }

        return invitedGroups;
    }

    /**
     * @return the joined groups
     */
    public Collection<Group> getJoinedGroups() {
        List<Group> joinedGroups = new ArrayList<>(getGroups());
        joinedGroups.removeAll(getInvitedGroups());

        return joinedGroups;
    }

    /**
     * Manage the group joining.
     *
     * @param groupId the group id
     * @param notify  true to notify
     */
    public void onJoinGroup(final String groupId, final boolean notify) {
        Group group = getGroup(groupId);

        if (null == group) {
            group = new Group(groupId);
        }

        if (TextUtils.equals(RoomMember.MEMBERSHIP_JOIN, group.getMembership())) {
            Log.d(LOG_TAG, "## onJoinGroup() : the group " + groupId + " was already joined");
            return;
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

                    if (null != mPendingJoinGroups.get(groupId)) {
                        mPendingJoinGroups.get(groupId).onSuccess(null);
                        mPendingJoinGroups.remove(groupId);
                    }
                }
            }

            @Override
            public void onNetworkError(Exception e) {
                Log.e(LOG_TAG, "## onJoinGroup() : failed " + e.getMessage());
                onDone();

                if (null != mPendingJoinGroups.get(groupId)) {
                    mPendingJoinGroups.get(groupId).onNetworkError(e);
                    mPendingJoinGroups.remove(groupId);
                }
            }

            @Override
            public void onMatrixError(MatrixError e) {
                Log.e(LOG_TAG, "## onMatrixError() : failed " + e.getMessage());
                onDone();

                if (null != mPendingJoinGroups.get(groupId)) {
                    mPendingJoinGroups.get(groupId).onMatrixError(e);
                    mPendingJoinGroups.remove(groupId);
                }
            }

            @Override
            public void onUnexpectedError(Exception e) {
                Log.e(LOG_TAG, "## onUnexpectedError() : failed " + e.getMessage());
                onDone();

                if (null != mPendingJoinGroups.get(groupId)) {
                    mPendingJoinGroups.get(groupId).onUnexpectedError(e);
                    mPendingJoinGroups.remove(groupId);
                }
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
    public void onNewGroupInvitation(final String groupId, final GroupSyncProfile profile, final String inviter, final boolean notify) {
        Group group = getGroup(groupId);

        // it should always be null
        if (null == group) {
            group = new Group(groupId);
        }

        GroupSummary summary = new GroupSummary();
        summary.profile = new GroupProfile();
        if (null != profile) {
            summary.profile.name = profile.name;
            summary.profile.avatarUrl = profile.avatarUrl;
        }

        group.setGroupSummary(summary);
        group.setInviter(inviter);
        group.setMembership(RoomMember.MEMBERSHIP_INVITE);

        mStore.storeGroup(group);

        if (notify) {
            mUIHandler.post(new Runnable() {
                @Override
                public void run() {
                    mDataHandler.onNewGroupInvitation(groupId);
                }
            });
        }
    }

    /**
     * Remove a group.
     *
     * @param groupId the group id.
     * @param notify  true to notify
     */
    public void onLeaveGroup(final String groupId, final boolean notify) {
        if (null != mStore.getGroup(groupId)) {
            mStore.deleteGroup(groupId);

            mUIHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (notify) {
                        mDataHandler.onLeaveGroup(groupId);
                    }

                    if (mPendingLeaveGroups.containsKey(groupId)) {
                        mPendingLeaveGroups.get(groupId).onSuccess(null);
                        mPendingLeaveGroups.remove(groupId);
                    }
                }
            });
        }
    }

    /**
     * Refresh the group profiles
     *
     * @param callback the asynchronous callback
     */
    public void refreshGroupProfiles(SimpleApiCallback<Void> callback) {
        if (!mRefreshProfilesCallback.isEmpty()) {
            Log.d(LOG_TAG, "## refreshGroupProfiles() : there already is a pending request");
            mRefreshProfilesCallback.add(callback);
            return;
        }

        mRefreshProfilesCallback.add(callback);
        refreshGroupProfiles(getGroups().iterator());
    }

    /**
     * Internal method to refresh the group profiles.
     *
     * @param iterator the iterator.
     */
    private void refreshGroupProfiles(final Iterator<Group> iterator) {
        if (!iterator.hasNext()) {
            for (SimpleApiCallback<Void> callback : mRefreshProfilesCallback) {
                try {
                    callback.onSuccess(null);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "## refreshGroupProfiles() failed " + e.getMessage());
                }
            }
            mRefreshProfilesCallback.clear();
            return;
        }

        final String groupId = iterator.next().getGroupId();

        mGroupsRestClient.getGroupProfile(groupId, new ApiCallback<GroupProfile>() {
            private void onDone() {
                refreshGroupProfiles(iterator);
            }

            @Override
            public void onSuccess(GroupProfile profile) {
                Group group = getGroup(groupId);

                if (null != group) {
                    group.setGroupProfile(profile);
                    mStore.flushGroup(group);
                }

                onDone();
            }

            @Override
            public void onNetworkError(Exception e) {
                Log.e(LOG_TAG, "## refreshGroupProfiles() : failed " + e.getMessage());
                onDone();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                Log.e(LOG_TAG, "## refreshGroupProfiles() : failed " + e.getMessage());
                onDone();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                Log.e(LOG_TAG, "## refreshGroupProfiles() : failed " + e.getMessage());
                onDone();
            }
        });
    }

    /**
     * Join a group.
     *
     * @param groupId  the group id
     * @param callback the asynchronous callback
     */
    public void joinGroup(final String groupId, final ApiCallback<Void> callback) {
        getGroupsRestClient().joinGroup(groupId, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                Group group = getGroup(groupId);
                // not yet synced -> wait it is synced
                if ((null == group) || TextUtils.equals(group.getMembership(), RoomMember.MEMBERSHIP_INVITE)) {
                    mPendingJoinGroups.put(groupId, callback);
                    onJoinGroup(groupId, true);
                } else {
                    callback.onSuccess(null);
                }
            }

            @Override
            public void onNetworkError(Exception e) {
                callback.onNetworkError(e);
            }

            @Override
            public void onMatrixError(MatrixError e) {
                callback.onMatrixError(e);
            }

            @Override
            public void onUnexpectedError(Exception e) {
                callback.onUnexpectedError(e);
            }
        });
    }

    /**
     * Leave a group.
     *
     * @param groupId  the group id
     * @param callback the asynchronous callback
     */
    public void leaveGroup(final String groupId, final ApiCallback<Void> callback) {
        getGroupsRestClient().leaveGroup(groupId, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                Group group = getGroup(groupId);
                // not yet synced -> wait it is synced
                if (null != group) {
                    mPendingLeaveGroups.put(groupId, callback);
                    onLeaveGroup(groupId, true);
                } else {
                    callback.onSuccess(null);
                }
            }

            @Override
            public void onNetworkError(Exception e) {
                callback.onNetworkError(e);
            }

            @Override
            public void onMatrixError(MatrixError e) {
                callback.onMatrixError(e);
            }

            @Override
            public void onUnexpectedError(Exception e) {
                callback.onUnexpectedError(e);
            }
        });
    }

    /**
     * Create a group.
     *
     * @param localPart the local part
     * @param groupName the group human name
     * @param callback  the asynchronous callback
     */
    public void createGroup(String localPart, String groupName, final ApiCallback<String> callback) {
        final CreateGroupParams params = new CreateGroupParams();
        params.localpart = localPart;
        params.profile = new GroupProfile();
        params.profile.name = groupName;

        getGroupsRestClient().createGroup(params, new ApiCallback<String>() {
            @Override
            public void onSuccess(String groupId) {
                Group group = getGroup(groupId);

                // if the group does not exist, create it
                if (null == group) {
                    group = new Group(groupId);
                    group.setGroupProfile(params.profile);
                    group.setMembership(RoomMember.MEMBERSHIP_JOIN);
                    mStore.storeGroup(group);
                }

                callback.onSuccess(groupId);
            }

            @Override
            public void onNetworkError(Exception e) {
                callback.onNetworkError(e);
            }

            @Override
            public void onMatrixError(MatrixError e) {
                callback.onMatrixError(e);
            }

            @Override
            public void onUnexpectedError(Exception e) {
                callback.onUnexpectedError(e);
            }
        });
    }


    /**
     * Refresh the group data i.e the invited users list, the users list and the rooms list.
     *
     * @param group  the group
     * @param callback the asynchronous callback
     */
    public void refreshGroupData(Group group, ApiCallback<Void> callback) {
        refreshGroupData(group, GROUP_REFRESH_STEP_PROFILE, callback);
    }

    private static final int GROUP_REFRESH_STEP_PROFILE = 0;
    private static final int GROUP_REFRESH_STEP_ROOMS_LIST = 1;
    private static final int GROUP_REFRESH_STEP_USERS_LIST = 2;
    private static final int GROUP_REFRESH_STEP_INVITED_USERS_LIST = 3;

    /**
     * Internal method to refresh the group informations.
     *
     * @param group  the group
     * @param step     the current step
     * @param callback the asynchronous callback
     */
    private void refreshGroupData(final Group group, final int step, final ApiCallback<Void> callback) {
        if (step == GROUP_REFRESH_STEP_PROFILE) {
            getGroupsRestClient().getGroupProfile(group.getGroupId(), new ApiCallback<GroupProfile>() {
                @Override
                public void onSuccess(GroupProfile groupProfile) {
                    group.setGroupProfile(groupProfile);
                    mStore.flushGroup(group);
                    refreshGroupData(group, GROUP_REFRESH_STEP_ROOMS_LIST, callback);
                }

                @Override
                public void onNetworkError(Exception e) {
                    callback.onNetworkError(e);
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    callback.onMatrixError(e);
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    callback.onUnexpectedError(e);
                }
            });

            return;
        }

        if (step == GROUP_REFRESH_STEP_ROOMS_LIST) {
            getGroupsRestClient().getGroupRooms(group.getGroupId(), new ApiCallback<GroupRooms>() {
                @Override
                public void onSuccess(GroupRooms groupRooms) {
                    group.setGroupRooms(groupRooms);
                    mStore.flushGroup(group);
                    refreshGroupData(group, GROUP_REFRESH_STEP_USERS_LIST, callback);
                }

                @Override
                public void onNetworkError(Exception e) {
                    callback.onNetworkError(e);
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    callback.onMatrixError(e);
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    callback.onUnexpectedError(e);
                }
            });
            return;
        }

        if (step == GROUP_REFRESH_STEP_USERS_LIST) {
            getGroupsRestClient().getGroupUsers(group.getGroupId(), new ApiCallback<GroupUsers>() {
                @Override
                public void onSuccess(GroupUsers groupUsers) {
                    group.setGroupUsers(groupUsers);
                    mStore.flushGroup(group);
                    refreshGroupData(group, GROUP_REFRESH_STEP_INVITED_USERS_LIST, callback);
                }

                @Override
                public void onNetworkError(Exception e) {
                    callback.onNetworkError(e);
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    callback.onMatrixError(e);
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    callback.onUnexpectedError(e);
                }
            });
            return;
        }


        //if (step == GROUP_REFRESH_STEP_INVITED_USERS_LIST)

        getGroupsRestClient().getGroupInvitedUsers(group.getGroupId(), new ApiCallback<GroupUsers>() {
            @Override
            public void onSuccess(GroupUsers groupUsers) {
                group.setInvitedGroupUsers(groupUsers);

                if (null != mStore.getGroup(group.getGroupId())) {
                    mStore.flushGroup(group);
                }
                callback.onSuccess(null);
            }

            @Override
            public void onNetworkError(Exception e) {
                callback.onNetworkError(e);
            }

            @Override
            public void onMatrixError(MatrixError e) {
                callback.onMatrixError(e);
            }

            @Override
            public void onUnexpectedError(Exception e) {
                callback.onUnexpectedError(e);
            }
        });
    }
}
