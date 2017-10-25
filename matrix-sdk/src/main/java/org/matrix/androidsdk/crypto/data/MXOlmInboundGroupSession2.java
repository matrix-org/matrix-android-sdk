/*
 * Copyright 2016 OpenMarket Ltd
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

package org.matrix.androidsdk.crypto.data;

import android.text.TextUtils;

import org.matrix.androidsdk.crypto.MXCryptoAlgorithms;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.util.Log;

import org.matrix.olm.OlmInboundGroupSession;

import java.io.Serializable;

import java.util.HashMap;
import java.util.Map;


/**
 * This class adds more context to a OLMInboundGroupSession object.
 * This allows additional checks. The class implements NSCoding so that the context can be stored.
 */
public class MXOlmInboundGroupSession2 implements Serializable {
    //
    private static final String LOG_TAG = "OlmInboundGroupSession";

    // define a serialVersionUID to avoid having to redefine the class after updates
    private static final long serialVersionUID = 201702011617L;

    // The associated olm inbound group session.
    public OlmInboundGroupSession mSession;

    // The room in which this session is used.
    public String mRoomId;

    // The base64-encoded curve25519 key of the sender.
    public String mSenderKey;

    // Other keys the sender claims.
    public Map<String, String> mKeysClaimed;


    /**
     * Constructor
     * @param prevFormatSession the previous session format
     */
    public MXOlmInboundGroupSession2(MXOlmInboundGroupSession prevFormatSession) {
        mSession = prevFormatSession.mSession;
        mRoomId = prevFormatSession.mRoomId;
        mSenderKey = prevFormatSession.mSenderKey;
        mKeysClaimed = prevFormatSession.mKeysClaimed;
    }

    /**
     * Constructor
     * @param sessionKey the session key
     *
     */
    public MXOlmInboundGroupSession2(String sessionKey, boolean isImported) {
        try {
            if (!isImported) {
                mSession = new OlmInboundGroupSession(sessionKey);
            } else {
                mSession = OlmInboundGroupSession.importSession(sessionKey);
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "Cannot create : " + e.getMessage());
        }
    }

    /**
     * Create a new instance from the provided keys map.
     */
    public MXOlmInboundGroupSession2(Map<String, Object> map) throws Exception {
        try {
            mSession = OlmInboundGroupSession.importSession((String)map.get("session_key"));

            if (!TextUtils.equals(mSession.sessionIdentifier(), (String)map.get("session_id"))) {
                throw new Exception("Mismatched group session Id");
            }

            mSenderKey = (String)map.get("sender_key");
            mKeysClaimed = (Map<String, String>)map.get("sender_claimed_keys");
            mRoomId = (String)map.get("room_id");
        } catch (Exception e) {
            throw new Exception(e.getMessage());
        }
    }

    /**
     * Export the inbound group session keys
     * @return the inbound group session as map if the operation succeeds
     */
    public Map<String, Object> exportKeys() {
        HashMap<String, Object> map = new HashMap<>();

        try {
            map.put("sender_key", mSenderKey);
            map.put("sender_claimed_keys", mKeysClaimed);
            map.put("room_id", mRoomId);
            map.put("session_id", mSession.sessionIdentifier());
            map.put("session_key", mSession.export(mSession.getFirstKnownIndex()));
            map.put("algorithm", MXCryptoAlgorithms.MXCRYPTO_ALGORITHM_MEGOLM);
        } catch (Exception e) {
            map = null;
            Log.e(LOG_TAG, "## export() : senderKey " + mSenderKey + " failed " + e.getMessage());
        }

        return map;
    }

    /**
     * @return the first known message index
     */
    public Long getFirstKnownIndex() {
        if (null != mSession) {
            try {
                return mSession.getFirstKnownIndex();
            } catch (Exception e) {
                Log.e(LOG_TAG, "## getFirstKnownIndex() : getFirstKnownIndex failed " + e.getMessage());
            }
        }

        return null;
    }

    /**
     * Export the session for a message index.
     * @param messageIndex the message index
     * @return the exported data
     */
    public String exportSession(long messageIndex) {
        if (null != mSession) {
            try {
                return mSession.export(messageIndex);
            } catch (Exception e) {
                Log.e(LOG_TAG, "## exportSession() : export failed " + e.getMessage());
            }
        }

        return null;
    }
}