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

package org.matrix.androidsdk.data;

import android.text.TextUtils;

import com.google.gson.JsonObject;

import org.matrix.androidsdk.MXDataHandler;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.PowerLevels;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.RoomThirdPartyInvite;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.util.JsonUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The state of a room.
 */
public class RoomState implements java.io.Serializable {
    public static final String DIRECTORY_VISIBILITY_PRIVATE = "private";
    public static final String DIRECTORY_VISIBILITY_PUBLIC = "public";

    public static final String JOIN_RULE_PUBLIC = "public";
    public static final String JOIN_RULE_INVITE = "invite";

    /** room access is granted to guests **/
    public static final String GUEST_ACCESS_CAN_JOIN = "can_join";
    /** room access is denied to guests **/
    public static final String GUEST_ACCESS_FORBIDDEN = "forbidden";

    public static final String HISTORY_VISIBILITY_SHARED = "shared";
    public static final String HISTORY_VISIBILITY_INVITED = "invited";
    public static final String HISTORY_VISIBILITY_JOINED = "joined";
    public static final String HISTORY_VISIBILITY_WORLD_READABLE = "world_readable";

    // Public members used for JSON mapping

    // The room ID
    public String roomId;

    // The power level of room members
    private PowerLevels powerLevels;

    // The aliases of this room.
    public List<String> aliases;

    // Informs which alias is the canonical one.
    public String alias;

    // The name of the room as provided by the home server.
    public String name;

    // The topic of the room.
    public String topic;

    // The avatar url of the room.
    public String url;

    // the room creator (user id)
    public String creator;

    // the join rule
    public String join_rule;

    /** the guest access policy of the room **/
    public String guest_access;

    // SPEC-134
    public String history_visibility;

    // the public room alias / name
    public String roomAliasName;

    /**  the room visibility in the directory list (i.e. public, private...) **/
    public String visibility;

    /**
     * The number of unread messages that match the push notification rules.
     * It is based on the notificationCount field in /sync response.
     */
    private int mNotificationCount;

    /**
     * The number of highlighted unread messages (subset of notifications).
     * It is based on the notificationCount field in /sync response.
     */
    private int mHighlightCount;

    // the associated token
    private String token;

    // the room members
    private Map<String, RoomMember> mMembers = new HashMap<String, RoomMember>();

    // the third party invite members
    private Map<String, RoomThirdPartyInvite> mThirdPartyInvites = new HashMap<String, RoomThirdPartyInvite>();

    /**
     * Cache for [self memberWithThirdPartyInviteToken].
     * The key is the 3pid invite token.
     */
    private Map<String, RoomMember> mMembersWithThirdPartyInviteTokenCache = new HashMap<String, RoomMember>();

    /**
     * Additional and optional metadata got from initialSync
     */
    private String mMembership;

    /**
     * Tell if the roomstate if a live one.
     */
    private boolean mIsLive;


    // the unitary tests crash when MXDataHandler type is set.
    private transient Object mDataHandler = null;

    // member display cache
    private transient HashMap<String, String> mMemberDisplayNameByUserId = new HashMap<String, String>();

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    // avatar Url makes more sense than url.
    public String getAvatarUrl() {
        return url;
    }

    public Collection<RoomMember> getMembers() {
        ArrayList<RoomMember> res;

        synchronized (this) {
            // make a copy to avoid concurrency modifications
            res = new ArrayList<>(mMembers.values());
        }

        return res;
    }

    public void setMember(String userId, RoomMember member) {
        // Populate a basic user object if there is none
        if (member.getUserId() == null) {
            member.setUserId(userId);
        }
        synchronized (this) {
            if (null != mMemberDisplayNameByUserId) {
                mMemberDisplayNameByUserId.remove(userId);
            }
            mMembers.put(userId, member);
        }
    }

    public RoomMember getMember(String userId) {
        RoomMember member;

        synchronized (this) {
            member = mMembers.get(userId);
        }

        return member;
    }

    public void removeMember(String userId) {
        synchronized (this) {
            mMembers.remove(userId);
        }
    }

    public RoomMember memberWithThirdPartyInviteToken(String thirdPartyInviteToken) {
        return mMembersWithThirdPartyInviteTokenCache.get(thirdPartyInviteToken);
    }

    public RoomThirdPartyInvite thirdPartyInviteWithToken(String thirdPartyInviteToken) {
        return mThirdPartyInvites.get(thirdPartyInviteToken);
    }

    public Collection<RoomThirdPartyInvite> thirdPartyInvites() {
        return mThirdPartyInvites.values();
    }

    public PowerLevels getPowerLevels() {
        if (null != powerLevels) {
            return powerLevels.deepCopy();
        } else {
            return null;
        }
    }

    public void setPowerLevels(PowerLevels powerLevels) {
        this.powerLevels = powerLevels;
    }

    public void setDataHandler(MXDataHandler dataHandler) {
        mDataHandler = dataHandler;
    }

    public void setNotificationCount(int notificationCount) {
        mNotificationCount = notificationCount;
    }

    public int getNotificationCount() {
        return mNotificationCount;
    }

    public void setHighlightCount(int highlightCount) {
        mHighlightCount = highlightCount;
    }

    public int getHighlightCount() {
        return mHighlightCount;
    }

    /**
     * Check if the user userId can back paginate.
     * @param userId the user Id.
     * @return true if the user can backpaginate.
     */
    public boolean canBackPaginated(String userId) {
        RoomMember member = getMember(userId);
        String membership = (null != member) ? member.membership : "";
        String visibility = TextUtils.isEmpty(history_visibility) ? HISTORY_VISIBILITY_SHARED : history_visibility;

        return  visibility.equals(HISTORY_VISIBILITY_SHARED) ||
                (RoomMember.MEMBERSHIP_JOIN.equals(membership)) /*&&visibility == invited or joined */  ||
                (RoomMember.MEMBERSHIP_INVITE.equals(membership) && visibility.equals(HISTORY_VISIBILITY_INVITED))
                ;
    }

    /**
     * Make a deep copy of this room state object.
     * @return the copy
     */
    public RoomState deepCopy() {

        RoomState copy = new RoomState();
        copy.roomId = roomId;
        copy.setPowerLevels((powerLevels == null) ? null : powerLevels.deepCopy());
        copy.aliases = (aliases == null) ? null : new ArrayList<String>(aliases);
        copy.alias = this.alias;
        copy.name = name;
        copy.topic = topic;
        copy.url = url;
        copy.creator = creator;
        copy.join_rule = join_rule;
        copy.guest_access = guest_access;
        copy.history_visibility = history_visibility;
        copy.visibility = visibility;
        copy.roomAliasName = roomAliasName;
        copy.token = token;
        copy.mDataHandler = mDataHandler;
        copy.mMembership = mMembership;
        copy.mIsLive = mIsLive;


        synchronized (this) {
            Iterator it = mMembers.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, RoomMember> pair = (Map.Entry<String, RoomMember>) it.next();
                copy.setMember(pair.getKey(), pair.getValue().deepCopy());
            }

            Collection<String> keys = mThirdPartyInvites.keySet();
            for(String key : keys) {
                copy.mThirdPartyInvites.put(key, mThirdPartyInvites.get(key).deepCopy());
            }

            keys = mMembersWithThirdPartyInviteTokenCache.keySet();
            for(String key : keys) {
                copy.mMembersWithThirdPartyInviteTokenCache.put(key, mMembersWithThirdPartyInviteTokenCache.get(key).deepCopy());
            }
        }

        return copy;
    }


    /**
     * @return the room alias
     */
    public String getAlias() {
        // SPEC-125
        if (!TextUtils.isEmpty(alias)) {
            return alias;
        } else if(!TextUtils.isEmpty(getFirstAlias())) {
            return getFirstAlias();
        }

        return null;
    }

    /**
     * Returns the first room alias.
     * @return the first room alias
     */
    private String getFirstAlias() {
        if ((aliases != null) && (aliases.size() != 0)) {
            return aliases.get(0);
        }

        return null;
    }

    /**
     * Build and return the room's display name.
     * @param selfUserId this user's user id (to exclude from members)
     * @return the display name
     */
    public String getDisplayName(String selfUserId) {
        String displayName = null;
        String alias = getAlias();

        synchronized (this) {
            if (name != null) {
                displayName = name;
            } else if (!TextUtils.isEmpty(alias)) {
                displayName = getAlias();
            }
            // compute a name
            else if (mMembers.size() > 0) {
                Iterator it = mMembers.entrySet().iterator();
                Map.Entry<String, RoomMember> otherUserPair = null;

                if ((mMembers.size() >= 3) && (selfUserId != null)) {
                    // this is a group chat and should have the names of participants
                    // according to "(<num> <name1>, <name2>, <name3> ..."
                    int count = 0;

                    displayName = "";

                    while (it.hasNext()) {
                        Map.Entry<String, RoomMember> pair = (Map.Entry<String, RoomMember>) it.next();

                        if (!selfUserId.equals(pair.getKey())) {
                            otherUserPair = pair;

                            if (count > 0) {
                                displayName += ", ";
                            }

                            if (otherUserPair.getValue().getName() != null) {
                                displayName += getMemberName(otherUserPair.getValue().getUserId()); // The member name
                            } else {
                                displayName += getMemberName(otherUserPair.getKey()); // The user id
                            }
                            count++;
                        }
                    }
                    displayName = "(" + count + ") " + displayName;
                } else {
                    // by default, it is oneself name
                    displayName = getMemberName(selfUserId);

                    // A One2One private room can default to being called like the other guy
                    if (selfUserId != null) {
                        while (it.hasNext()) {
                            Map.Entry<String, RoomMember> pair = (Map.Entry<String, RoomMember>) it.next();
                            if (!selfUserId.equals(pair.getKey())) {
                                otherUserPair = pair;
                                break;
                            }
                        }
                    }

                    if (otherUserPair != null) {
                        if (otherUserPair.getValue().getName() != null) {
                            displayName = getMemberName(otherUserPair.getValue().getUserId()); // The member name
                        } else {
                            displayName = getMemberName(otherUserPair.getKey()); // The user id
                        }
                    }
                }
            }
        }

        if ((displayName != null) && (alias != null) && !displayName.equals(alias)) {
            if (TextUtils.isEmpty(displayName)) {
                displayName = alias;
            } else {
                displayName += " (" + alias + ")";
            }
        }

        if (displayName == null) {
            displayName = roomId;
        }

        return displayName;
    }

    /**
     * Apply the given event (relevant for state changes) to our state.
     * @param event the event
     * @param direction how the event should affect the state: Forwards for applying, backwards for un-applying (applying the previous state)
     * @return true if the event is managed
     */
    public boolean applyState(Event event, EventTimeline.Direction direction) {
        if (event.stateKey == null) {
            return false;
        }

        JsonObject contentToConsider = (direction == EventTimeline.Direction.FORWARDS) ? event.getContentAsJsonObject() : event.getPrevContentAsJsonObject();

        try {
            if (Event.EVENT_TYPE_STATE_ROOM_NAME.equals(event.type)) {
                RoomState roomState = JsonUtils.toRoomState(contentToConsider);
                name = (roomState == null) ? null : roomState.name;
            } else if (Event.EVENT_TYPE_STATE_ROOM_TOPIC.equals(event.type)) {
                RoomState roomState = JsonUtils.toRoomState(contentToConsider);
                topic = (roomState == null) ? null : roomState.topic;
            } else if (Event.EVENT_TYPE_STATE_ROOM_CREATE.equals(event.type)) {
                RoomState roomState = JsonUtils.toRoomState(contentToConsider);
                creator = (roomState == null) ? null : roomState.creator;
            } else if (Event.EVENT_TYPE_STATE_ROOM_JOIN_RULES.equals(event.type)) {
                RoomState roomState = JsonUtils.toRoomState(contentToConsider);
                join_rule = (roomState == null) ? null : roomState.join_rule;
            } else if (Event.EVENT_TYPE_STATE_ROOM_GUEST_ACCESS.equals(event.type)) {
                RoomState roomState = JsonUtils.toRoomState(contentToConsider);
                guest_access = (roomState == null) ? null : roomState.guest_access;
            } else if (Event.EVENT_TYPE_STATE_ROOM_ALIASES.equals(event.type)) {
                RoomState roomState = JsonUtils.toRoomState(contentToConsider);
                aliases = (roomState == null) ? null : roomState.aliases;
            } else if (Event.EVENT_TYPE_STATE_CANONICAL_ALIAS.equals(event.type)) {
                // SPEC-125
                RoomState roomState = JsonUtils.toRoomState(contentToConsider);
                alias = (roomState == null) ? null : roomState.alias;
            } else if (Event.EVENT_TYPE_STATE_HISTORY_VISIBILITY.equals(event.type)) {
                // SPEC-134
                RoomState roomState = JsonUtils.toRoomState(contentToConsider);
                history_visibility = (roomState == null) ? null : roomState.history_visibility;
            } else if (Event.EVENT_TYPE_STATE_ROOM_AVATAR.equals(event.type)) {
                RoomState roomState = JsonUtils.toRoomState(contentToConsider);
                url = (roomState == null) ? null : roomState.url;
            } else if (Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(event.type)) {
                RoomMember member = JsonUtils.toRoomMember(contentToConsider);
                String userId = event.stateKey;
                if (member == null) {
                    // the member has already been removed
                    if (null == getMember(userId)) {
                        return false;
                    }
                    removeMember(userId);
                } else {
                    member.setUserId(userId);
                    member.setOriginServerTs(event.getOriginServerTs());
                    member.setInviterId(event.getSender());

                    RoomMember currentMember = getMember(userId);

                    // check if the member is the same
                    // duplicated message ?
                    if (member.equals(currentMember)) {
                        return false;
                    }

                    // when a member leaves a room, his avatar is not anymore provided
                    if ((direction == EventTimeline.Direction.FORWARDS) ) {
                        if (null != currentMember) {
                            if (member.membership.equals(RoomMember.MEMBERSHIP_LEAVE) || member.membership.equals(RoomMember.MEMBERSHIP_BAN)) {
                                if (null == member.avatarUrl) {
                                    member.avatarUrl = currentMember.avatarUrl;
                                }
                            }
                        }

                        if (null != mDataHandler) {
                            ((MXDataHandler)mDataHandler).getStore().updateUserWithRoomMemberEvent(member);
                        }
                    }

                    // Cache room member event that is successor of a third party invite event
                    if (!TextUtils.isEmpty(member.thirdPartyInviteToken)) {
                        mMembersWithThirdPartyInviteTokenCache.put(member.thirdPartyInviteToken, member);
                    }

                    setMember(userId, member);
                }
            } else if (Event.EVENT_TYPE_STATE_ROOM_POWER_LEVELS.equals(event.type)) {
                powerLevels = JsonUtils.toPowerLevels(contentToConsider);
            } else if (Event.EVENT_TYPE_STATE_ROOM_THIRD_PARTY_INVITE.equals(event.type)) {
                RoomThirdPartyInvite thirdPartyInvite  = JsonUtils.toRoomThirdPartyInvite(contentToConsider);

                thirdPartyInvite.token = event.stateKey;

                if (!TextUtils.isEmpty(thirdPartyInvite.token)) {
                    mThirdPartyInvites.put(thirdPartyInvite.token, thirdPartyInvite);
                }
            }
        } catch (Exception e) {
        }

        return true;
    }

    /**
     * @return true if the room is a public one
     */
    public boolean isPublic() {
        return TextUtils.equals((null != visibility) ? visibility : join_rule, DIRECTORY_VISIBILITY_PUBLIC);
    }

    /**
     * Return an unique display name of the member userId.
     * @param userId the user id
     * @return unique display name
     */
    public String getMemberName(String userId) {
        // sanity check
        if (null == userId) {
            return null;
        }

        String displayName;

        synchronized (this) {
            if (null == mMemberDisplayNameByUserId) {
                mMemberDisplayNameByUserId = new HashMap<String, String>();
            }
            displayName = mMemberDisplayNameByUserId.get(userId);
        }

        if (null != displayName) {
            return displayName;
        }

        // Get the user display name from the member list of the room
        RoomMember member = getMember(userId);

        // Do not consider null display name
        if ((null != member) &&  !TextUtils.isEmpty(member.displayname)) {
            displayName = member.displayname;

            synchronized (this) {
                ArrayList<String> matrixIds = new ArrayList<String>();

                // Disambiguate users who have the same displayname in the room
                for (RoomMember aMember : mMembers.values()) {
                    if (displayName.equals(aMember.displayname)) {
                        matrixIds.add(aMember.getUserId());
                    }
                }

                // if several users have the same displayname
                // index it i.e bob (<Matrix id>)
                if (matrixIds.size() > 1) {
                    displayName += " (" + userId + ")";
                }
            }
        }

        // The user may not have joined the room yet. So try to resolve display name from presence data
        // Note: This data may not be available
        if ((null == displayName) && (null != mDataHandler)) {
            User user = ((MXDataHandler)mDataHandler).getUser(userId);

            if (null != user) {
                displayName = user.displayname;
            }
        }

        if (null == displayName) {
            // By default, use the user ID
            displayName = userId;
        }

        mMemberDisplayNameByUserId.put(userId, displayName);

        return displayName;
    }
}
