/*
 * Copyright 2015 OpenMarket Ltd
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

package org.matrix.androidsdk.crypto.algorithms.megolm;

import org.matrix.androidsdk.util.Log;
import org.matrix.androidsdk.crypto.data.MXUsersDevicesMap;

public class MXOutboundSessionInfo {
    private static final String LOG_TAG = "MXOutboundSessionInfo";

    // When the session was created
    private long mCreationTime;

    // The id of the session
    public String mSessionId;

    // Number of times this session has been used
    public int mUseCount;

    // Devices with which we have shared the session key
    // userId -> {deviceId -> msgindex}
    public MXUsersDevicesMap<Integer> mSharedWithDevices;

    // constructor
    public MXOutboundSessionInfo(String sessionId) {
        mSessionId = sessionId;
        mSharedWithDevices = new MXUsersDevicesMap<>();
        mCreationTime = System.currentTimeMillis();
        mUseCount = 0;
    }

    public boolean needsRotation(int rotationPeriodMsgs, int rotationPeriodMs) {
        boolean needsRotation = false;
        long sessionLifetime = System.currentTimeMillis() - mCreationTime;

        if ((mUseCount >= rotationPeriodMsgs) || (sessionLifetime >= rotationPeriodMs)) {
            Log.d(LOG_TAG, "## needsRotation() : Rotating megolm session after " + mUseCount + ", " + sessionLifetime + "ms");
            needsRotation = true;
        }

        return needsRotation;
    }
}
