/*
 * Copyright 2018 New Vector Ltd
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
package org.matrix.androidsdk.lazyloading;

import org.matrix.androidsdk.MXSession;

import java.util.List;

/**
 * Data holder for lazy loading tests
 * The sessions are not synced by default as you want to perform some custom tests
 */
public class RoomNameScenarioData {
    final public List<MXSession> userSessions;

    final String roomId;

    public RoomNameScenarioData(List<MXSession> userSessions, String roomId) {
        this.userSessions = userSessions;
        this.roomId = roomId;
    }
}