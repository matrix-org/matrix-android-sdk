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
package org.matrix.androidsdk.rest.client;


import org.matrix.androidsdk.HomeServerConnectionConfig;
import org.matrix.androidsdk.RestClient;
import org.matrix.androidsdk.rest.api.GroupsApi;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.RestAdapterCallback;
import org.matrix.androidsdk.rest.model.group.AcceptGroupInvitationParams;
import org.matrix.androidsdk.rest.model.group.AddGroupParams;
import org.matrix.androidsdk.rest.model.group.CreateGroupParams;
import org.matrix.androidsdk.rest.model.group.CreateGroupResponse;
import org.matrix.androidsdk.rest.model.group.GetGroupsResponse;
import org.matrix.androidsdk.rest.model.group.GetUserPublicisedGroupsResponse;
import org.matrix.androidsdk.rest.model.group.GroupInviteUserParams;
import org.matrix.androidsdk.rest.model.group.GroupInviteUserResponse;
import org.matrix.androidsdk.rest.model.group.GroupKickUserParams;
import org.matrix.androidsdk.rest.model.group.GroupProfile;
import org.matrix.androidsdk.rest.model.group.GroupRooms;
import org.matrix.androidsdk.rest.model.group.GroupSummary;
import org.matrix.androidsdk.rest.model.group.GroupUsers;
import org.matrix.androidsdk.rest.model.group.LeaveGroupParams;
import org.matrix.androidsdk.rest.model.group.UpdatePubliciseParams;

import java.util.List;

import retrofit.client.Response;


/**
 * Class used to make requests to the groups API.
 */
public class GroupsRestClient extends RestClient<GroupsApi> {

    /**
     * {@inheritDoc}
     */
    public GroupsRestClient(HomeServerConnectionConfig hsConfig) {
        super(hsConfig, GroupsApi.class, RestClient.URI_API_PREFIX_PATH_R0, false);
    }

    protected GroupsRestClient(GroupsApi api) {
        mApi = api;
    }

    /**
     * Create a group.
     *
     * @param params   the room creation parameters
     * @param callback the asynchronous callback.
     */
    public void createGroup(final CreateGroupParams params, final ApiCallback<String> callback) {
        final String description = "createGroup " + params.localpart;

        try {
            mApi.createGroup(params, new RestAdapterCallback<CreateGroupResponse>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                @Override
                public void onRetry() {
                    createGroup(params, callback);
                }
            }
            ) {
                @Override
                public void success(CreateGroupResponse createGroupResponse, Response response) {
                    onEventSent();
                    callback.onSuccess(createGroupResponse.group_id);
                }
            });
        } catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Invite an user in a group.
     *
     * @param groupId  the group id
     * @param userId   the user id
     * @param callback the asynchronous callback.
     */
    public void inviteUserInGroup(final String groupId, final String userId, final ApiCallback<String> callback) {
        final String description = "inviteUserInGroup " + groupId + " - " + userId;

        try {
            GroupInviteUserParams params = new GroupInviteUserParams();

            mApi.inviteUser(groupId, userId, params, new RestAdapterCallback<GroupInviteUserResponse>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                @Override
                public void onRetry() {
                    inviteUserInGroup(groupId, userId, callback);
                }
            }
            ) {
                @Override
                public void success(GroupInviteUserResponse groupInviteUserResponse, Response response) {
                    onEventSent();
                    callback.onSuccess(groupInviteUserResponse.state);
                }
            });
        } catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Kick an user from a group.
     *
     * @param groupId  the group id
     * @param userId   the user id
     * @param callback the asynchronous callback.
     */
    public void KickUserFromGroup(final String groupId, final String userId, final ApiCallback<Void> callback) {
        final String description = "KickFromGroup " + groupId + " " + userId;

        try {
            GroupKickUserParams params = new GroupKickUserParams();

            mApi.kickUser(groupId, userId, params, new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                @Override
                public void onRetry() {
                    KickUserFromGroup(groupId, userId, callback);
                }
            }));
        } catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Add a room in a group.
     *
     * @param groupId  the group id
     * @param roomId   the room id
     * @param callback the asynchronous callback.
     */
    public void addRoomInGroup(final String groupId, final String roomId, final ApiCallback<Void> callback) {
        final String description = "addRoomInGroup " + groupId + " " + roomId;

        try {
            AddGroupParams params = new AddGroupParams();

            mApi.addRoom(groupId, roomId, params, new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                @Override
                public void onRetry() {
                    addRoomInGroup(groupId, roomId, callback);
                }
            }));
        } catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Remove a room from a group.
     *
     * @param groupId  the group id
     * @param roomId   the room id
     * @param callback the asynchronous callback.
     */
    public void removeRoomFromGroup(final String groupId, final String roomId, final ApiCallback<Void> callback) {
        final String description = "removeRoomFromGroup " + groupId + " " + roomId;

        try {
            mApi.removeRoom(groupId, roomId, new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                @Override
                public void onRetry() {
                    removeRoomFromGroup(groupId, roomId, callback);
                }
            }));
        } catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Update a group profile.
     *
     * @param groupId  the group id
     * @param profile  the profile
     * @param callback the asynchronous callback.
     */
    public void updateGroupProfile(final String groupId, final GroupProfile profile, final ApiCallback<Void> callback) {
        final String description = "updateGroupProfile " + groupId;

        try {
            mApi.updateProfile(groupId, profile, new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                @Override
                public void onRetry() {
                    updateGroupProfile(groupId, profile, callback);
                }
            }));
        } catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Update a group profile.
     *
     * @param groupId  the group id
     * @param callback the asynchronous callback.
     */
    public void getGroupProfile(final String groupId, final ApiCallback<GroupProfile> callback) {
        final String description = "getGroupProfile " + groupId;

        try {
            mApi.getProfile(groupId, new RestAdapterCallback<GroupProfile>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                @Override
                public void onRetry() {
                    getGroupProfile(groupId, callback);
                }
            }));
        } catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Request the group invited users.
     *
     * @param groupId  the group id
     * @param callback the asynchronous callback.
     */
    public void getGroupInvitedUsers(final String groupId, final ApiCallback<GroupUsers> callback) {
        final String description = "getGroupInvitedUsers " + groupId;

        try {
            mApi.getInvitedUsers(groupId, new RestAdapterCallback<GroupUsers>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                @Override
                public void onRetry() {
                    getGroupInvitedUsers(groupId, callback);
                }
            }));
        } catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Request the group rooms.
     *
     * @param groupId  the group id
     * @param callback the asynchronous callback.
     */
    public void getGroupRooms(final String groupId, final ApiCallback<GroupRooms> callback) {
        final String description = "getGroupRooms " + groupId;

        try {
            mApi.getRooms(groupId, new RestAdapterCallback<GroupRooms>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                @Override
                public void onRetry() {
                    getGroupRooms(groupId, callback);
                }
            }));
        } catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Request the group users.
     *
     * @param groupId  the group id
     * @param callback the asynchronous callback.
     */
    public void getGroupUsers(final String groupId, final ApiCallback<GroupUsers> callback) {
        final String description = "getGroupUsers " + groupId;

        try {
            mApi.getUsers(groupId, new RestAdapterCallback<GroupUsers>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                @Override
                public void onRetry() {
                    getGroupUsers(groupId, callback);
                }
            }));
        } catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Request a group summary
     *
     * @param groupId  the group id
     * @param callback the asynchronous callback.
     */
    public void getGroupSummary(final String groupId, final ApiCallback<GroupSummary> callback) {
        final String description = "getGroupSummary " + groupId;

        try {
            mApi.getSummary(groupId, new RestAdapterCallback<GroupSummary>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                @Override
                public void onRetry() {
                    getGroupSummary(groupId, callback);
                }
            }));
        } catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Join a group.
     *
     * @param groupId  the group id
     * @param callback the asynchronous callback.
     */
    public void joinGroup(final String groupId, final ApiCallback<Void> callback) {
        final String description = "joinGroup " + groupId;

        try {
            AcceptGroupInvitationParams params = new AcceptGroupInvitationParams();

            mApi.acceptInvitation(groupId, params, new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                @Override
                public void onRetry() {
                    joinGroup(groupId, callback);
                }
            }));
        } catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Leave a group.
     *
     * @param groupId  the group id
     * @param callback the asynchronous callback.
     */
    public void leaveGroup(final String groupId, final ApiCallback<Void> callback) {
        final String description = "leaveGroup " + groupId;

        try {
            LeaveGroupParams params = new LeaveGroupParams();

            mApi.leave(groupId, params, new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                @Override
                public void onRetry() {
                    leaveGroup(groupId, callback);
                }
            }));
        } catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Update a group publicity status.
     *
     * @param groupId   the group id
     * @param publicity the new publicity status
     * @param callback  the asynchronous callback.
     */
    public void updateGroupPublicity(final String groupId, final boolean publicity, final ApiCallback<Void> callback) {
        final String description = "updateGroupPublicity " + groupId + " - " + publicity;

        try {
            UpdatePubliciseParams params = new UpdatePubliciseParams();
            params.publicise = publicity;

            mApi.updatePublicity(groupId, params, new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                @Override
                public void onRetry() {
                    leaveGroup(groupId, callback);
                }
            }));
        } catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Request the joined groups.
     *
     * @param callback the asynchronous callback.
     */
    public void getJoinedGroups(final ApiCallback<List<String>> callback) {
        final String description = "getJoinedGroups";

        try {
            mApi.getJoinedGroupIds(new RestAdapterCallback<GetGroupsResponse>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                @Override
                public void onRetry() {
                    getJoinedGroups(callback);
                }
            }
            ) {
                @Override
                public void success(GetGroupsResponse getRoomsResponse, Response response) {
                    onEventSent();
                    callback.onSuccess(getRoomsResponse.groupIds);
                }
            });
        } catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }

    /**
     * Request the publicised groups for an user.
     *
     * @param userId   the user id
     * @param callback the asynchronous callback.
     */
    public void getUserPublicisedGroups(final String userId, final ApiCallback<List<String>> callback) {
        final String description = "getUserPublicisedGroups " + userId;

        try {
            mApi.getUserPublicisedGroups(userId, new RestAdapterCallback<GetUserPublicisedGroupsResponse>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
                @Override
                public void onRetry() {
                    getUserPublicisedGroups(userId, callback);
                }
            }
            ) {
                @Override
                public void success(GetUserPublicisedGroupsResponse getUserPublicisedGroupsResponse, Response response) {
                    onEventSent();
                    callback.onSuccess(getUserPublicisedGroupsResponse.groups);
                }
            });
        } catch (Throwable t) {
            callback.onUnexpectedError(new Exception(t));
        }
    }
}
