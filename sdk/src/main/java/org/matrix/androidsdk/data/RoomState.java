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

import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.User;

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
    // Public members used for JSON mapping
    public String roomId;
    public String name;
    public String topic;
    public String roomAliasName;
    public String visibility;
    public String creator;
    public String joinRule;
    public String level;
    public List<String> aliases;

    private String token;
    private Map<String, RoomMember> mMembers = new HashMap<String, RoomMember>();

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
        if (member.getUser() == null) {
            User user = new User();
            user.userId = userId;
            member.setUser(user);
        }
        mMembers.put(userId, member);
    }

    public RoomMember getMember(String userId) {
        return mMembers.get(userId);
    }

    public RoomState deepCopy() {
        RoomState copy = new RoomState();
        copy.roomId = roomId;
        copy.name = name;
        copy.topic = topic;
        copy.roomAliasName = roomAliasName;
        copy.visibility = visibility;
        copy.creator = creator;
        copy.joinRule = joinRule;
        copy.level = level;
        copy.aliases = new ArrayList<String>(aliases);

        Iterator it = mMembers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, RoomMember> pair = (Map.Entry<String, RoomMember>) it.next();
            copy.setMember(pair.getKey(), pair.getValue().deepCopy());
        }

        return copy;
    }
}
