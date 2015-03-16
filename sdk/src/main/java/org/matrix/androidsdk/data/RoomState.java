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
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.rest.model.PowerLevels;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.util.ContentManager;
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
public class RoomState {
    public static final String VISIBILITY_PRIVATE = "private";
    public static final String VISIBILITY_PUBLIC = "public";

    // Public members used for JSON mapping
    public String roomId;
    public String name;
    public String topic;
    public String roomAliasName;
    public String visibility;
    public String creator;
    public String joinRule;
    public MXDataHandler mDataHandler = null;
    public List<String> aliases;

    private String token;
    private Map<String, RoomMember> mMembers = new HashMap<String, RoomMember>();
    private PowerLevels powerLevels;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Collection<RoomMember> getMembers() {
        return mMembers.values();
    }

    public void setMember(String userId, RoomMember member) {
        // Populate a basic user object if there is none
        if (member.getUserId() == null) {
            member.setUserId(userId);
        }
        mMembers.put(userId, member);
    }

    public RoomMember getMember(String userId) {
        return mMembers.get(userId);
    }

    public void removeMember(String userId) {
        mMembers.remove(userId);
    }

    public PowerLevels getPowerLevels() {
        return powerLevels;
    }

    public void setPowerLevels(PowerLevels powerLevels) {
        this.powerLevels = powerLevels;
    }

    /**
     * Make a deep copy of this room state object.
     * @return the copy
     */
    public RoomState deepCopy() {
        RoomState copy = new RoomState();
        copy.roomId = roomId;
        copy.name = name;
        copy.topic = topic;
        copy.roomAliasName = roomAliasName;
        copy.visibility = visibility;
        copy.creator = creator;
        copy.joinRule = joinRule;
        copy.mDataHandler = mDataHandler;
        copy.aliases = (aliases == null) ? null : new ArrayList<String>(aliases);

        Iterator it = mMembers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, RoomMember> pair = (Map.Entry<String, RoomMember>) it.next();
            copy.setMember(pair.getKey(), pair.getValue().deepCopy());
        }

        copy.setPowerLevels((powerLevels == null) ? null : powerLevels.deepCopy());

        return copy;
    }

    /**
     * Returns the first room alias.
     * @return the first room alias
     */
    public String getFirstAlias() {
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
        String displayName = null, alias = null;

        if ((aliases != null) && (aliases.size() != 0)) {
            alias = aliases.get(0);
        }

        if (name != null) {
            displayName = name;
        }
        else if (alias != null) {
            displayName = alias;
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

        if ((displayName != null) && (alias != null) && !displayName.equals(alias)) {
            displayName += " (" + alias + ")";
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
     */
    public void applyState(Event event, Room.EventDirection direction) {
        if (event.stateKey == null) return; // Ignore non-state events

        JsonObject contentToConsider = (direction == Room.EventDirection.FORWARDS) ? event.content : event.prevContent;

        if (Event.EVENT_TYPE_STATE_ROOM_NAME.equals(event.type)) {
            RoomState roomState = JsonUtils.toRoomState(contentToConsider);
            name = (roomState == null) ? null : roomState.name;
        }
        else if (Event.EVENT_TYPE_STATE_ROOM_TOPIC.equals(event.type)) {
            RoomState roomState = JsonUtils.toRoomState(contentToConsider);
            topic = (roomState == null) ? null : roomState.topic;
        }
        else if (Event.EVENT_TYPE_STATE_ROOM_CREATE.equals(event.type)) {
            RoomState roomState = JsonUtils.toRoomState(contentToConsider);
            creator = (roomState == null) ? null : roomState.creator;
        }
        else if (Event.EVENT_TYPE_STATE_ROOM_JOIN_RULES.equals(event.type)) {
            RoomState roomState = JsonUtils.toRoomState(contentToConsider);
            joinRule = (roomState == null) ? null : roomState.joinRule;
        }
        else if (Event.EVENT_TYPE_STATE_ROOM_ALIASES.equals(event.type)) {
            RoomState roomState = JsonUtils.toRoomState(contentToConsider);
            aliases = (roomState == null) ? null : roomState.aliases;
        }
        else if (Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(event.type)) {
            RoomMember member = JsonUtils.toRoomMember(contentToConsider);
            String userId = event.stateKey;
            if (member == null) {
                removeMember(userId);
            }
            else {
                setMember(userId, member);
            }
        }
        else if (Event.EVENT_TYPE_STATE_ROOM_POWER_LEVELS.equals(event.type)) {
            powerLevels = JsonUtils.toPowerLevels(contentToConsider);
        }
    }

    /**
     * Return an unique display name of the member userId.
     * @param userId
     * @return unique display name
     */
    public String getMemberName(String userId) {
        // sanity check
        if (null == userId) {
            return null;
        }

        String displayName = null;

        // Get the user display name from the member list of the room
        RoomMember member = getMember(userId);

        // Do not consider null display name
        if ((null != member) &&  !TextUtils.isEmpty(member.displayname)) {
            displayName = member.displayname;

            // Disambiguate users who have the same displayname in the room
            for(RoomMember aMember : mMembers.values()) {
                if (!aMember.getUserId().equals(userId) && displayName.equals(aMember.displayname)) {
                    displayName += "(" + userId + ")";
                    break;
                }
            }
        }

        // The user may not have joined the room yet. So try to resolve display name from presence data
        // Note: This data may not be available
        if ((null == displayName) && (null != mDataHandler)) {
            User user = mDataHandler.getUser(userId);

            if (null != user) {
                displayName = user.displayname;
            }
        }

        if (null == displayName) {
            // By default, use the user ID
            displayName = userId;
        }

        return displayName;
    }
}
