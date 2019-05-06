/*
 * Copyright 2014 OpenMarket Ltd
 * Copyright 2017 Vector Creations Ltd
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
package org.matrix.androidsdk.crypto.model.rest;

import android.support.annotation.Nullable;

import org.matrix.androidsdk.core.interfaces.DatedObject;

/**
 * This class describes the device information
 */
public class DeviceInfo implements DatedObject {
    /**
     * The owner user id
     */
    public String user_id;

    /**
     * The device id
     */
    public String device_id;

    /**
     * The device display name
     */
    public String display_name;

    /**
     * The last time this device has been seen.
     */
    public long last_seen_ts = 0;

    /**
     * The last ip address
     */
    @Nullable
    public String last_seen_ip;

    @Override
    public long getDate() {
        return last_seen_ts;
    }
}
