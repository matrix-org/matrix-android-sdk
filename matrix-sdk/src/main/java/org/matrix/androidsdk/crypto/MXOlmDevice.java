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

package org.matrix.androidsdk.crypto;

import android.text.TextUtils;
import android.util.Log;

import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import org.json.JSONObject;
import org.matrix.androidsdk.crypto.algorithms.MXDecryptionResult;
import org.matrix.androidsdk.crypto.data.MXOlmInboundGroupSession;
import org.matrix.androidsdk.data.cryptostore.IMXCryptoStore;
import org.matrix.androidsdk.util.JsonUtils;
import org.matrix.olm.OlmAccount;
import org.matrix.olm.OlmInboundGroupSession;
import org.matrix.olm.OlmMessage;
import org.matrix.olm.OlmOutboundGroupSession;
import org.matrix.olm.OlmSession;
import org.matrix.olm.OlmUtility;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class MXOlmDevice {
    private static final String LOG_TAG = "MXOlmDevice";

    // Curve25519 key for the account.
    private String mDeviceCurve25519Key;

    // Ed25519 key for the account.
    private String mDeviceEd25519Key;

    // The store where crypto data is saved.
    private final IMXCryptoStore mStore;

    // The OLMKit account instance.
    private OlmAccount mOlmAccount;

    // The OLMKit utility instance.
    private final OlmUtility mOlmUtility;

    // The outbound group session.
    // They are not stored in 'store' to avoid to remember to which devices we sent the session key.
    // Plus, in cryptography, it is good to refresh sessions from time to time.
    // The key is the session id, the value the outbound group session.
    private final HashMap<String, OlmOutboundGroupSession> mOutboundGroupSessionStore;

    /**
     * Constructor
     * @param store the used store
     */
    public MXOlmDevice(IMXCryptoStore store) {
        mStore = store;

        // Retrieve the account from the store
        mOlmAccount = mStore.getAccount();

        if (null == mOlmAccount) {
            // Else, create it
            try {
                mOlmAccount = new OlmAccount();
            } catch (Exception e) {
                Log.e(LOG_TAG, "MXOlmDevice : cannot initialize mOlmAccount " + e.getMessage());
            }

            mStore.storeAccount(mOlmAccount);
        }

        mOlmUtility = new OlmUtility();

        mOutboundGroupSessionStore = new HashMap<>();

        try {
            mDeviceCurve25519Key = mOlmAccount.identityKeys().getString(OlmAccount.JSON_KEY_IDENTITY_KEY);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## MXOlmDevice : cannot find " + OlmAccount.JSON_KEY_IDENTITY_KEY);
        }

        try {
            mDeviceEd25519Key = mOlmAccount.identityKeys().getString(OlmAccount.JSON_KEY_FINGER_PRINT_KEY);
        } catch (Exception e) {
            Log.e(LOG_TAG, "## MXOlmDevice : cannot find " + OlmAccount.JSON_KEY_FINGER_PRINT_KEY);
        }
    }

    /**
     * Release the instance
     */
    public void release() {
        mOlmAccount.releaseAccount();
    }

    /**
     * @return the Curve25519 key for the account.
     */
    public String getDeviceCurve25519Key() {
        return mDeviceCurve25519Key;
    }

    /**
     * @return the Ed25519 key for the account.
     */
    public String getDeviceEd25519Key() {
        return mDeviceEd25519Key;
    }

    /**
     * Signs a message with the ed25519 key for this account.
     * @param message the message to be signed.
     * @return the base64-encoded signature.
     */
    private String signMessage(String message) {
        return mOlmAccount.signMessage(message);
    }

    /**
     * Signs a JSON dictionary with the ed25519 key for this account.
     * The signature is done on canonical version of the JSON.
     * @param JSONDictionary the JSON to be signed.
     * @return the base64-encoded signature
     */
    public String signJSON(Map<String, Object> JSONDictionary) {
        return signMessage(JsonUtils.getCanonicalizedJsonString(JSONDictionary));
    }

    /**
     * @return The current (unused, unpublished) one-time keys for this account.
     */
    public Map<String, Map<String, String>> oneTimeKeys() {
        Map<String, Map<String, String>> res = new HashMap<>();

        JSONObject object = mOlmAccount.oneTimeKeys();

        if (null != object) {
            res = JsonUtils.getGson(false).fromJson(object.toString(), new TypeToken<Map<String, Map<String, String>>>() {}.getType());
        }

        return res;
    }

    /**
     * @return The maximum number of one-time keys the olm account can store.
     */
    public long maxNumberOfOneTimeKeys() {
        return mOlmAccount.maxOneTimeKeys();
    }

    /**
     * Marks all of the one-time keys as published.
     */
    public void markKeysAsPublished() {
        mOlmAccount.markOneTimeKeysAsPublished();

        mStore.storeAccount(mOlmAccount);
    }

    /**
     * Generate some new one-time keys
     * @param numKeys number of keys to generate
     */
    public void generateOneTimeKeys(int numKeys) {
        mOlmAccount.generateOneTimeKeys(numKeys);

        mStore.storeAccount(mOlmAccount);
    }

    /**
     * Generate a new outbound session.
     * The new session will be stored in the MXStore.
     * @param theirIdentityKey the remote user's Curve25519 identity key
     * @param theirOneTimeKey the remote user's one-time Curve25519 key
     * @return the session id for the outbound session. @TODO OLMSession?
     */
    public String createOutboundSession(String theirIdentityKey, String theirOneTimeKey) {
        Log.d(LOG_TAG, "## createOutboundSession() ; theirIdentityKey " + theirIdentityKey + " theirOneTimeKey " + theirOneTimeKey);

        try {
            OlmSession olmSession = new OlmSession();
            olmSession.initOutboundSessionWithAccount(mOlmAccount, theirIdentityKey, theirOneTimeKey);
            mStore.storeSession(olmSession, theirIdentityKey, false);

            Log.d(LOG_TAG, "## createOutboundSession() ;  olmSession.sessionIdentifier: " + olmSession.sessionIdentifier());

            return olmSession.sessionIdentifier();
        } catch (Exception e) {
            Log.e(LOG_TAG, "## createOutboundSession() failed ; " + e.getMessage());
        }

        return null;
    }

    /**
     *
     * Generate a new inbound session, given an incoming message.
     * @param theirDeviceIdentityKey the remote user's Curve25519 identity key.
     * @param messageType the message_type field from the received message (must be 0).
     * @param ciphertext base64-encoded body from the received message.
     * @return {{payload: string, session_id: string}} decrypted payload, andsession id of new session.
     */
    public Map<String, String> createInboundSession(String theirDeviceIdentityKey, int messageType, String ciphertext) {

        Log.d(LOG_TAG, "## createInboundSession() : " + theirDeviceIdentityKey);

        OlmSession olmSession = null;

        try {
            olmSession = new OlmSession();
        } catch (Exception e) {
            Log.d(LOG_TAG, "## createInboundSession() : OlmSession creation failed " + e.getMessage());
        }

        if (olmSession.initInboundSessionWithAccountFrom(mOlmAccount, theirDeviceIdentityKey, ciphertext) >= 0) {
            Log.d(LOG_TAG, "## createInboundSession() : " + olmSession.sessionIdentifier());

            mOlmAccount.removeOneTimeKeysForSession(olmSession);
            mStore.storeAccount(mOlmAccount);

            Log.d(LOG_TAG, "## createInboundSession() : ciphertext: " +  ciphertext);
            try {
                Log.d(LOG_TAG, "## createInboundSession() :ciphertext: SHA256:" + mOlmUtility.sha256(URLEncoder.encode(ciphertext, "utf-8")));
            } catch (Exception e) {
                Log.e(LOG_TAG, "## createInboundSession() :ciphertext: cannot encode ciphertext");
            }

            OlmMessage olmMessage = new OlmMessage();
            olmMessage.mCipherText = ciphertext;
            olmMessage.mType = messageType;

            String payloadString = olmSession.decryptMessage(olmMessage);
            mStore.storeSession(olmSession, theirDeviceIdentityKey, true);

            HashMap<String, String> res = new HashMap<>();

            if (!TextUtils.isEmpty(payloadString)) {
                res.put("payload", payloadString);
            }

            if (!TextUtils.isEmpty(olmSession.sessionIdentifier())) {
                res.put("session_id", olmSession.sessionIdentifier());
            }

            return res;
        }

        return null;
    }

    /**
     * Get a list of known session IDs for the given device.
     * @param theirDeviceIdentityKey the Curve25519 identity key for the remote device.
     * @return a list of known session ids for the device.
     */
    public Set<String> sessionIdsForDevice(String theirDeviceIdentityKey) {
        Map<String, OlmSession> map =  mStore.sessionsWithDevice(theirDeviceIdentityKey);

        if (null != map) {
            return map.keySet();
        }

        return null;
    }

    /**
     * Get the right olm session id for encrypting messages to the given identity key.
     * @param theirDeviceIdentityKey the Curve25519 identity key for the remote device.
     * @return the session id, or nil if no established session.
     */
    public String sessionIdForDevice(String theirDeviceIdentityKey) {
        String sessionId = null;
        Set<String> sessionIds = sessionIdsForDevice(theirDeviceIdentityKey);

        if ((null != sessionIds) && (0 != sessionIds.size())) {
            ArrayList<String> sessionIdsList = new ArrayList<>(sessionIds);
            Collections.sort(sessionIdsList);
            sessionId = sessionIdsList.get(0);
        }

        return sessionId;
    }

    /**
     * Encrypt an outgoing message using an existing session.
     * @param theirDeviceIdentityKey the Curve25519 identity key for the remote device.
     * @param sessionId the id of the active session
     * @param payloadString the payload to be encrypted and sent
     * @return the cipher text
     */
    public Map<String, Object> encryptMessage(String theirDeviceIdentityKey, String sessionId, String payloadString) {
        HashMap<String, Object> res = null;
        OlmMessage olmMessage;
        OlmSession olmSession = sessionForDevice(theirDeviceIdentityKey, sessionId);

        if (null != olmSession) {
            Log.d(LOG_TAG, "## encryptMessage() : olmSession.sessionIdentifier: " + olmSession.sessionIdentifier());
            Log.d(LOG_TAG, "## encryptMessage() : payloadString: " + payloadString);

            olmMessage = olmSession.encryptMessage(payloadString);

            mStore.storeSession(olmSession, theirDeviceIdentityKey, false);
            res = new HashMap<>();

            res.put("body", olmMessage.mCipherText);
            res.put("type", olmMessage.mType);
        }

        return res;
    }

    /**
     * Decrypt an incoming message using an existing session.
     * @param ciphertext the base64-encoded body from the received message.
     * @param messageType message_type field from the received message.
     * @param theirDeviceIdentityKey the Curve25519 identity key for the remote device.
     * @param sessionId the id of the active session.
     * @return the decrypted payload.
     */
    public String decryptMessage(String  ciphertext, int messageType, String sessionId, String theirDeviceIdentityKey) {
        String payloadString = null;

        OlmSession olmSession = sessionForDevice(theirDeviceIdentityKey, sessionId);

        if (null != olmSession) {
            OlmMessage olmMessage = new OlmMessage();
            olmMessage.mCipherText = ciphertext;
            olmMessage.mType = messageType;
            payloadString = olmSession.decryptMessage(olmMessage);

            mStore.storeSession(olmSession, theirDeviceIdentityKey, true);
        }

        return payloadString;
    }

    /**
     * Determine if an incoming messages is a prekey message matching an existing session.
     * @param theirDeviceIdentityKey the Curve25519 identity key for the remote device.
     * @param sessionId the id of the active session.
     * @param messageType message_type field from the received message.
     * @param ciphertext the base64-encoded body from the received message.
     * @return YES if the received message is a prekey message which matchesthe given session.
     */
    public boolean matchesSession(String theirDeviceIdentityKey, String sessionId, int messageType, String ciphertext) {
        if (messageType != 0) {
            return false;
        }

        OlmSession olmSession = sessionForDevice(theirDeviceIdentityKey, sessionId);
        return (null != olmSession) && olmSession.matchesInboundSession(ciphertext);
    }


    // Outbound group session
    /**
     * Generate a new outbound group session.
     * @return the session id for the outbound session.
     */
    public String createOutboundGroupSession() {
        try {
            OlmOutboundGroupSession session = new OlmOutboundGroupSession();
            mOutboundGroupSessionStore.put(session.sessionIdentifier(), session);
            return session.sessionIdentifier();
        } catch (Exception e) {
            Log.e(LOG_TAG, "createOutboundGroupSession " + e.getMessage());
        }
        return null;
    }

    /**
     * Get the current session key of  an outbound group session.
     * @param sessionId the id of the outbound group session.
     * @return the base64-encoded secret key.
     */
    public String sessionKeyForOutboundGroupSession(String sessionId) {
        if (!TextUtils.isEmpty(sessionId)) {
            return mOutboundGroupSessionStore.get(sessionId).sessionKey();
        }
        return null;
    }

    /**
     * Get the current message index of an outbound group session.
     * @param sessionId the id of the outbound group session.
     * @return the current chain index.
     */
    public int messageIndexForOutboundGroupSession(String sessionId) {
        if (!TextUtils.isEmpty(sessionId)) {
            return mOutboundGroupSessionStore.get(sessionId).messageIndex();
        }
        return 0;
    }

    /**
     * Encrypt an outgoing message with an outbound group session.
     * @param sessionId the id of the outbound group session.
     * @param payloadString the payload to be encrypted and sent.
     * @return ciphertext
     */
    public String encryptGroupMessage(String sessionId, String payloadString) {
        if (!TextUtils.isEmpty(sessionId) && !TextUtils.isEmpty(payloadString)) {
            return mOutboundGroupSessionStore.get(sessionId).encryptMessage(payloadString);
        }
        return null;
    }

    //  Inbound group session
    /**
     * Add an inbound group session to the session store.
     * @param sessionId the session identifier.
     * @param sessionKey base64-encoded secret key.
     * @param roomId the id of the room in which this session will be used.
     * @param senderKey the base64-encoded curve25519 key of the sender.
     * @param keysClaimed Other keys the sender claims.
     * @return true if the operation succeeds.
     */
    public boolean addInboundGroupSession(String sessionId, String sessionKey, String roomId, String senderKey, Map<String, String> keysClaimed) {
        MXOlmInboundGroupSession session = new MXOlmInboundGroupSession(sessionKey);

        // sanity check
        if ((null == session) || (null == session.mSession)) {
            Log.e(LOG_TAG, "## addInboundGroupSession : invalid session");
            return false;
        }

        if (!TextUtils.equals(session.mSession.sessionIdentifier(), sessionId)) {
            Log.e(LOG_TAG, "## addInboundGroupSession : ERROR: Mismatched group session ID from senderKey: " + senderKey);
            return false;
        }

        session.mSenderKey = senderKey;
        session.mRoomId = roomId;
        session.mKeysClaimed = keysClaimed;

        mStore.storeInboundGroupSession(session);

        return true;
    }

    /**
     * Decrypt a received message with an inbound group session.
     * @param body the base64-encoded body of the encrypted message.
     * @param roomId theroom in which the message was received.
     * @param sessionId the session identifier.
     * @param senderKey the base64-encoded curve25519 key of the sender.
     * @return the decrypting result. Nil if the sessionId is unknown.
     */
    public MXDecryptionResult decryptGroupMessage(String body, String roomId, String sessionId, String senderKey) {
        MXDecryptionResult result = new MXDecryptionResult();
        MXOlmInboundGroupSession session = mStore.inboundGroupSessionWithId(sessionId, senderKey);

        if (null != session) {
            // Check that the room id matches the original one for the session. This stops
            // the HS pretending a message was targeting a different room.
            if (TextUtils.equals(roomId, session.mRoomId)) {
                String payloadString = null;
                StringBuffer errorMessage = new StringBuffer();
                try {
                    OlmInboundGroupSession.DecryptIndex index = new OlmInboundGroupSession.DecryptIndex();
                    payloadString = session.mSession.decryptMessage(body, index, errorMessage);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "## decryptGroupMessage () : decryptMessage failed " + e.getMessage());
                }

                if (null != payloadString) {
                    mStore.storeInboundGroupSession(session);
                    try {
                        JsonParser parser = new JsonParser();
                        result.mPayload = parser.parse(JsonUtils.convertFromUTF8(payloadString));
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "## decryptGroupMessage() : RLEncoder.encode failed " + e.getMessage());
                        return null;
                    }

                    if (null == result.mPayload) {
                        Log.e(LOG_TAG, "## decryptGroupMessage() : fails to parse the payload");
                        return null;
                    }

                    result.mKeysClaimed = session.mKeysClaimed;

                    // The sender must have had the senderKey to persuade us to save the
                    // session.
                    HashMap<String, String> map = new HashMap<>();
                    map.put("curve25519", senderKey);
                    result.mKeysProved = map;
                }  else {
                    result.mCryptoError = new MXCryptoError(MXCryptoError.UNABLE_TO_DECRYPT, errorMessage.toString());
                    Log.e(LOG_TAG, "## decryptGroupMessage() : failed to decode the message");
                }
            } else {
                result.mCryptoError = new MXCryptoError(MXCryptoError.INBOUND_SESSION_MISMATCHED_ROOM_ID, roomId + "<->" + session.mRoomId);
                Log.e(LOG_TAG, "## decryptGroupMessage() : Mismatched room_id for inbound group session (expected " + roomId + " , was " + session.mRoomId);
            }
        }
        else {
            result.mCryptoError = new MXCryptoError(MXCryptoError.UNKNOWN_INBOUND_SESSION_ID);
            Log.e(LOG_TAG, "## decryptGroupMessage() : Cannot retrieve inbound group session " + sessionId);
        }

        return result;
    }

    //  Utilities
    /**
     * Verify an ed25519 signature on a JSON object.
     * @param key the ed25519 key.
     * @param JSONDictinary the JSON object which was signed.
     * @param signature the base64-encoded signature to be checked.
     * @return true if valid.
     */

    public boolean verifySignature(String key, Map<String, Object> JSONDictinary, String signature) {
        StringBuffer error = new StringBuffer();

        // Check signature on the canonical version of the JSON
        return mOlmUtility.verifyEd25519Signature(signature, key, JsonUtils.getCanonicalizedJsonString(JSONDictinary), error);
    }

    /**
     * Calculate the SHA-256 hash of the input and encodes it as base64.
     * @param message  the message to hash.
     * @return the base64-encoded hash value.
     */
    public String sha256(String message) {
        return mOlmUtility.sha256(JsonUtils.convertToUTF8(message));
    }

    /**
     * Search an OlmSession
     * @param theirDeviceIdentityKey the device key
     * @param sessionId the session Id
     * @return
     */
    private OlmSession sessionForDevice(String theirDeviceIdentityKey, String sessionId) {
        // sanity check
        if (!TextUtils.isEmpty(theirDeviceIdentityKey) && !TextUtils.isEmpty(sessionId)) {
            Map<String, OlmSession> map = mStore.sessionsWithDevice(theirDeviceIdentityKey);

            if (null != map) {
                return map.get(sessionId);
            }
        }

        return null;
    }
}