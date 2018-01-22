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

package org.matrix.androidsdk.rest.model;

import android.text.TextUtils;

import org.matrix.androidsdk.HomeServerConnectionConfig;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.rest.model.pid.Invite3Pid;
import org.matrix.androidsdk.rest.model.pid.ThreePid;
import org.matrix.androidsdk.util.JsonUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CreateRoomParams {

    public static final String PRESET_PRIVATE_CHAT = "private_chat";
    public static final String PRESET_PUBLIC_CHAT = "public_chat";
    public static final String PRESET_TRUSTED_PRIVATE_CHAT = "trusted_private_chat";

    /**
     * A public visibility indicates that the room will be shown in the published room list.
     * A private visibility will hide the room from the published room list.
     * Rooms default to private visibility if this key is not included.
     * NB: This should not be confused with join_rules which also uses the word public. One of: ["public", "private"]
     */
    public String visibility;

    /**
     * The desired room alias local part. If this is included, a room alias will be created and mapped to the newly created room.
     * The alias will belong on the same homeserver which created the room.
     * For example, if this was set to "foo" and sent to the homeserver "example.com" the complete room alias would be #foo:example.com.
     */
    public String roomAliasName;

    /**
     * If this is included, an m.room.name event will be sent into the room to indicate the name of the room.
     * See Room Events for more information on m.room.name.
     */
    public String name;

    /**
     * If this is included, an m.room.topic event will be sent into the room to indicate the topic for the room.
     * See Room Events for more information on m.room.topic.
     */
    public String topic;

    /**
     * Whether guests can join the room. One of: ["can_join", "forbidden"]
     */
    public String guest_access;

    /**
     * Who can see the room history. One of: ["invited", "joined", "shared", "world_readable"]
     */
    public String history_visibility;

    /**
     * A list of user IDs to invite to the room.
     * This will tell the server to invite everyone in the list to the newly created room.
     */
    public List<String> invite;

    /**
     * A list of objects representing third party IDs to invite into the room.
     */
    public List<Invite3Pid> invite_3pid;

    /**
     * Extra keys to be added to the content of the m.room.create.
     * The server will clobber the following keys: creator.
     * Future versions of the specification may allow the server to clobber other keys.
     */
    public Object creation_content;

    /**
     * A list of state events to set in the new room.
     * This allows the user to override the default state events set in the new room.
     * The expected format of the state events are an object with type, state_key and content keys set.
     * Takes precedence over events set by presets, but gets overriden by name and topic keys.
     */
    public List<Event> initial_state;

    /**
     * Convenience parameter for setting various default state events based on a preset. Must be either:
     * private_chat => join_rules is set to invite. history_visibility is set to shared.
     * trusted_private_chat => join_rules is set to invite. history_visibility is set to shared. All invitees are given the same power level as the room creator.
     * public_chat: => join_rules is set to public. history_visibility is set to shared. One of: ["private_chat", "public_chat", "trusted_private_chat"]
     */
    public String preset;

    /**
     * This flag makes the server set the is_direct flag on the m.room.member events sent to the users in invite and invite_3pid.
     * See Direct Messaging for more information.
     */
    public Boolean is_direct;

    /**
     * Add the crypto algorithm to the room creation parameters.
     *
     * @param algorithm the algorithm
     */
    public void addCryptoAlgorithm(String algorithm) {
        if (!TextUtils.isEmpty(algorithm)) {
            Event algoEvent = new Event();
            algoEvent.type = Event.EVENT_TYPE_MESSAGE_ENCRYPTION;

            Map<String, String> contentMap = new HashMap<>();
            contentMap.put("algorithm", algorithm);
            algoEvent.content = JsonUtils.getGson(false).toJsonTree(contentMap);

            if (null == initial_state) {
                initial_state = Arrays.asList(algoEvent);
            } else {
                initial_state.add(algoEvent);
            }
        }
    }

    /**
     * Mark as a direct message room.
     */
    public void setDirectMessage() {
        preset = CreateRoomParams.PRESET_TRUSTED_PRIVATE_CHAT;
        is_direct = true;
    }

    /**
     * @return the invite count
     */
    private int getInviteCount() {
        return (null == invite) ? 0 : invite.size();
    }

    /**
     * @return the pid invite count
     */
    private int getInvite3PidCount() {
        return (null == invite_3pid) ? 0 : invite_3pid.size();
    }

    /**
     * Tells if the created room can be a direct chat one.
     *
     * @return if it is a direct chat
     */
    public boolean isDirect() {
        return TextUtils.equals(preset, CreateRoomParams.PRESET_TRUSTED_PRIVATE_CHAT) && (null != is_direct) && is_direct &&
                (1 == getInviteCount() || (1 == getInvite3PidCount()));
    }

    /**
     * @return the first invited user id
     */
    public String getFirstInvitedUserId() {
        if (0 != getInviteCount()) {
            return invite.get(0);
        }

        if (0 != getInvite3PidCount()) {
            return invite_3pid.get(0).address;
        }

        return null;
    }

    /**
     * Add some ids to the room creation
     * ids might be a matrix id or an email address.
     *
     * @param ids the participant ids to add.
     */
    public void addParticipantIds(HomeServerConnectionConfig hsConfig, List<String> ids) {
        for (String id : ids) {
            if (android.util.Patterns.EMAIL_ADDRESS.matcher(id).matches()) {
                if (null == invite_3pid) {
                    invite_3pid = new ArrayList<>();
                }

                Invite3Pid pid = new Invite3Pid();
                pid.id_server = hsConfig.getIdentityServerUri().getHost();
                pid.medium = ThreePid.MEDIUM_EMAIL;
                pid.address = id;

                invite_3pid.add(pid);
            } else if (MXSession.isUserId(id)) {
                // do not invite oneself
                if (!TextUtils.equals(hsConfig.getCredentials().userId, id)) {
                    if (null == invite) {
                        invite = new ArrayList<>();
                    }

                    invite.add(id);
                }

            } // TODO add phonenumbers when it will be available
        }
    }
}
