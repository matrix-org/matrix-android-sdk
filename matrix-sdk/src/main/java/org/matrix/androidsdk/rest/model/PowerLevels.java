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

import java.util.HashMap;
import java.util.Map;

public class PowerLevels implements java.io.Serializable {
    public int ban = 50;
    public int kick = 50;
    public int invite = 50;
    public int redact = 50;

    public int eventsDefault = 50;
    public Map<String, Integer> events = new HashMap<String, Integer>();

    public int usersDefault = 0;
    public Map<String, Integer> users = new HashMap<String, Integer>();

    public int stateDefault = 50;

    public PowerLevels deepCopy() {
        PowerLevels copy = new PowerLevels();
        copy.ban = ban;
        copy.kick = kick;
        copy.invite = invite;
        copy.redact = redact;

        copy.eventsDefault = eventsDefault;
        copy.events = new HashMap<String, Integer>();
        copy.events.putAll(events);

        copy.usersDefault = usersDefault;
        copy.users = new HashMap<String, Integer>();
        copy.users.putAll(users);

        copy.stateDefault = stateDefault;

        return copy;
    }

    public int getUserPowerLevel(String userId) {
        Integer powerLevel = users.get(userId);
        return (powerLevel != null) ? powerLevel : usersDefault;
    }

    public void setUserPowerLevel(String userId, int powerLevel) {
        if (null != userId) {
            users.put(userId, Integer.valueOf(powerLevel));
        }
    }

    /**
     *  Helper to get the minimum power level the user must have to send an event of the given type
     as a message.
     * @param eventTypeString the type of event (in Event.EVENT_TYPE_XXX values)
     * @return the required minimum power level.
     */
    public int minimumPowerLevelForSendingEventAsMessage(String eventTypeString) {
        int minimumPowerLevel = eventsDefault;

        if ((null != eventTypeString) && events.containsKey(eventTypeString)) {
            minimumPowerLevel = events.get(eventTypeString);
        }

        return minimumPowerLevel;
    }

    /**
     * Helper to get the minimum power level the user must have to send an event of the given type
     * as a state event.
     * @param eventTypeString the type of event (in Event.EVENT_TYPE_STATE_ values).
     * @return the required minimum power level.
     */
    public int minimumPowerLevelForSendingEventAsStateEvent(String eventTypeString) {
        int minimumPowerLevel = stateDefault;

        if ((null != eventTypeString) && events.containsKey(eventTypeString)) {
            minimumPowerLevel = events.get(eventTypeString);
        }

        return minimumPowerLevel;
    }
}