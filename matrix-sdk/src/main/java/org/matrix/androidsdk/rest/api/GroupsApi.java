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
package org.matrix.androidsdk.rest.api;

import org.matrix.androidsdk.rest.model.group.AcceptGroupInvitationParams;
import org.matrix.androidsdk.rest.model.group.AddGroupParams;
import org.matrix.androidsdk.rest.model.group.CreateGroupParams;
import org.matrix.androidsdk.rest.model.group.CreateGroupResponse;
import org.matrix.androidsdk.rest.model.group.GetGroupsResponse;
import org.matrix.androidsdk.rest.model.group.GetPublicisedGroupsResponse;
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
import java.util.Map;

import retrofit.Callback;
import retrofit.http.Body;
import retrofit.http.DELETE;
import retrofit.http.GET;
import retrofit.http.POST;
import retrofit.http.PUT;
import retrofit.http.Path;

/**
 * The groups API.
 */
public interface GroupsApi {

    /**
     * Create a group
     *
     * @param params   the group creation params
     * @param callback the asynchronous callback called with the response
     */
    @POST("/create_group")
    void createGroup(@Body CreateGroupParams params, Callback<CreateGroupResponse> callback);

    /**
     * Invite an user to a group.
     *
     * @param groupId  the group id
     * @param userId   the user id
     * @param params   the invitation parameters
     * @param callback the asynchronous callback
     */
    @PUT("/groups/{groupId}/admin/users/invite/{userId}")
    void inviteUser(@Path("groupId") String groupId, @Path("userId") String userId, @Body GroupInviteUserParams params, Callback<GroupInviteUserResponse> callback);

    /**
     * Kick an user from a group.
     *
     * @param groupId  the group id
     * @param userId   the user id
     * @param params   the kick parameters
     * @param callback the asynchronous callback
     */
    @PUT("/groups/{groupId}/users/remove/{userId}")
    void kickUser(@Path("groupId") String groupId, @Path("userId") String userId, @Body GroupKickUserParams params, Callback<Void> callback);

    /**
     * Add a room in a group.
     *
     * @param groupId  the group id
     * @param roomId   the room id
     * @param params   the kick parameters
     * @param callback the asynchronous callback
     */
    @PUT("/groups/{groupId}/admin/rooms/{roomId}")
    void addRoom(@Path("groupId") String groupId, @Path("roomId") String roomId, @Body AddGroupParams params, Callback<Void> callback);

    /**
     * Remove a room from a group.
     *
     * @param groupId  the group id
     * @param roomId   the room id
     * @param callback the asynchronous callback
     */
    @DELETE("/groups/{groupId}/admin/rooms/{roomId}")
    void removeRoom(@Path("groupId") String groupId, @Path("roomId") String roomId, Callback<Void> callback);

    /**
     * Update the group profile.
     *
     * @param groupId  the group id
     * @param profile  the group profile
     * @param callback the asynchronous callback.
     */
    @POST("/groups/{groupId}/profile")
    void updateProfile(@Path("groupId") String groupId, @Body GroupProfile profile, Callback<Void> callback);

    /**
     * Get the group profile.
     *
     * @param groupId  the group id
     * @param callback the asynchronous callback.
     */
    @GET("/groups/{groupId}/profile")
    void getProfile(@Path("groupId") String groupId, Callback<GroupProfile> callback);

    /**
     * Request the invited users list.
     *
     * @param groupId  the group id
     * @param callback the asynchronous callback.
     */
    @GET("/groups/{groupId}/invited_users")
    void getInvitedUsers(@Path("groupId") String groupId, Callback<GroupUsers> callback);

    /**
     * Request the users list.
     *
     * @param groupId  the group id
     * @param callback the asynchronous callback.
     */
    @GET("/groups/{groupId}/users")
    void getUsers(@Path("groupId") String groupId, Callback<GroupUsers> callback);

    /**
     * Request the rooms list.
     *
     * @param groupId  the group id
     * @param callback the asynchronous callback.
     */
    @GET("/groups/{groupId}/rooms")
    void getRooms(@Path("groupId") String groupId, Callback<GroupRooms> callback);

    /**
     * Request a group summary
     *
     * @param groupId  the group id
     * @param callback the asynchronous callback.
     */
    @GET("/groups/{groupId}/summary")
    void getSummary(@Path("groupId") String groupId, Callback<GroupSummary> callback);

    /**
     * Accept an invitation in a group.
     *
     * @param groupId  the group id
     * @param params   the parameters
     * @param callback the asynchronous callback
     */
    @PUT("/groups/{groupId}/self/accept_invite")
    void acceptInvitation(@Path("groupId") String groupId, @Body AcceptGroupInvitationParams params, Callback<Void> callback);

    /**
     * Leave a group
     *
     * @param groupId  the group id
     * @param params   the parameters
     * @param callback the asynchronous callback
     */
    @PUT("/groups/{groupId}/self/leave")
    void leave(@Path("groupId") String groupId, @Body LeaveGroupParams params, Callback<Void> callback);

    /**
     * Update the publicity status.
     *
     * @param groupId  the group id
     * @param params   the parameters
     * @param callback the asynchronous callback
     */
    @PUT("/groups/{groupId}/self/update_publicity")
    void updatePublicity(@Path("groupId") String groupId, @Body UpdatePubliciseParams params, Callback<Void> callback);

    /**
     * Request the joined group list.
     *
     * @param callback the asynchronous callback.
     */
    @GET("/joined_groups")
    void getJoinedGroupIds(Callback<GetGroupsResponse> callback);

    // NOT FEDERATED
    /**
     * Request the publicised groups for an user id.
     *
     * @param userId   the user id
     * @param callback the asynchronous callback.
     */
    //@GET("/publicised_groups/{userId}")
    //void getUserPublicisedGroups(@Path("userId") String userId, Callback<GetUserPublicisedGroupsResponse> callback);

    /**
     * Request the publicised groups for user ids.
     *
     * @param params   the request params
     * @param callback the asynchronous callback
     */
    @POST("/publicised_groups")
    void getPublicisedGroups(@Body Map<String, List<String>> params, Callback<GetPublicisedGroupsResponse> callback);
}
