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

package org.matrix.androidsdk.crypto;

import java.util.List;
import java.util.Map;

/**
 * The type of object we use for importing and exporting megolm session data.
 */
public class MegolmSessionData {
    /**
     * The algorithm used.
     */
    public String algorithm;

    /**
     * Unique id for the session.
     */
    public String session_id;

    /**
     * Sender's Curve25519 device key.
     */
    public String sender_key;

    /**
     * Room this session is used in.
     */
    public String room_id;

    /**
     * Base64'ed key data.
     */
    public String session_key;

    /**
     * Other keys the sender claims.
     */
    public Map<String, String> sender_claimed_keys;

    // This is a shortcut for sender_claimed_keys.get("ed25519")
    // Keep it for compatibility reason.
    public String sender_claimed_ed25519_key;

    /**
     * Devices which forwarded this session to us (normally empty).
     */
    public List<String> forwardingCurve25519KeyChain;
}
