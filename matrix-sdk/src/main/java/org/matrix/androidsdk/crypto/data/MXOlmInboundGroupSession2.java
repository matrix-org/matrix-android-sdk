/*
 * Copyright 2016 OpenMarket Ltd
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

package org.matrix.androidsdk.crypto.data;

import android.support.annotation.Nullable;
import android.text.TextUtils;

import org.matrix.androidsdk.crypto.CryptoConstantsKt;
import org.matrix.androidsdk.crypto.MegolmSessionData;
import org.matrix.androidsdk.util.Log;
import org.matrix.olm.OlmInboundGroupSession;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


/**
 * This class adds more context to a OLMInboundGroupSession object.
 * This allows additional checks. The class implements NSCoding so that the context can be stored.
 */
public class MXOlmInboundGroupSession2 implements Serializable {
    //
    private static final String LOG_TAG = MXOlmInboundGroupSession2.class.getSimpleName();

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

    // Devices which forwarded this session to us (normally empty).
    public List<String> mForwardingCurve25519KeyChain = new ArrayList<>();

    /**
     * Constructor
     *
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
     *
     * @param sessionKey the session key
     * @param isImported true if it is an imported session key
     */
    public MXOlmInboundGroupSession2(String sessionKey, boolean isImported) {
        try {
            if (!isImported) {
                mSession = new OlmInboundGroupSession(sessionKey);
            } else {
                mSession = OlmInboundGroupSession.importSession(sessionKey);
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "Cannot create : " + e.getMessage(), e);
        }
    }

    /**
     * Create a new instance from the provided keys map.
     *
     * @param megolmSessionData the megolm session data
     * @throws Exception if the data are invalid
     */
    public MXOlmInboundGroupSession2(MegolmSessionData megolmSessionData) throws Exception {
        try {
            mSession = OlmInboundGroupSession.importSession(megolmSessionData.sessionKey);

            if (!TextUtils.equals(mSession.sessionIdentifier(), megolmSessionData.sessionId)) {
                throw new Exception("Mismatched group session Id");
            }

            mSenderKey = megolmSessionData.senderKey;
            mKeysClaimed = megolmSessionData.senderClaimedKeys;
            mRoomId = megolmSessionData.roomId;
        } catch (Exception e) {
            throw new Exception(e.getMessage());
        }
    }

    /**
     * Export the inbound group session keys
     *
     * @return the inbound group session as MegolmSessionData if the operation succeeds
     */
    @Nullable
    public MegolmSessionData exportKeys() {
        MegolmSessionData megolmSessionData = new MegolmSessionData();

        try {
            if (null == mForwardingCurve25519KeyChain) {
                mForwardingCurve25519KeyChain = new ArrayList<>();
            }

            megolmSessionData.senderClaimedEd25519Key = mKeysClaimed.get("ed25519");
            megolmSessionData.forwardingCurve25519KeyChain = new ArrayList<>(mForwardingCurve25519KeyChain);
            megolmSessionData.senderKey = mSenderKey;
            megolmSessionData.senderClaimedKeys = mKeysClaimed;
            megolmSessionData.roomId = mRoomId;
            megolmSessionData.sessionId = mSession.sessionIdentifier();
            megolmSessionData.sessionKey = mSession.export(mSession.getFirstKnownIndex());
            megolmSessionData.algorithm = CryptoConstantsKt.MXCRYPTO_ALGORITHM_MEGOLM;
        } catch (Exception e) {
            megolmSessionData = null;
            Log.e(LOG_TAG, "## export() : senderKey " + mSenderKey + " failed " + e.getMessage(), e);
        }

        return megolmSessionData;
    }

    /**
     * @return the first known message index
     */
    public Long getFirstKnownIndex() {
        if (null != mSession) {
            try {
                return mSession.getFirstKnownIndex();
            } catch (Exception e) {
                Log.e(LOG_TAG, "## getFirstKnownIndex() : getFirstKnownIndex failed " + e.getMessage(), e);
            }
        }

        return null;
    }

    /**
     * Export the session for a message index.
     *
     * @param messageIndex the message index
     * @return the exported data
     */
    public String exportSession(long messageIndex) {
        if (null != mSession) {
            try {
                return mSession.export(messageIndex);
            } catch (Exception e) {
                Log.e(LOG_TAG, "## exportSession() : export failed " + e.getMessage(), e);
            }
        }

        return null;
    }
}