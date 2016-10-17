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
import org.matrix.androidsdk.crypto.data.MXKey;
import org.matrix.androidsdk.crypto.data.MXOlmInboundGroupSession;
import org.matrix.androidsdk.data.IMXStore;
import org.matrix.androidsdk.util.JsonUtils;
import org.matrix.olm.OlmAccount;
import org.matrix.olm.OlmMessage;
import org.matrix.olm.OlmOutboundGroupSession;
import org.matrix.olm.OlmSession;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class MXOlmDevice {
    public static final String LOG_TAG = "MXOlmDevice";

    // Curve25519 key for the account.
    private String mDeviceCurve25519Key;

    // Ed25519 key for the account.
    private String mDeviceEd25519Key;

    private String olmVersion;

    // The store where crypto data is saved.
    private IMXStore mStore;

    // The OLMKit account instance.
    private OlmAccount mOlmAccount;

    // The OLMKit utility instance.
    // TODO not yet implemented
    //OLMUtility *olmUtility;

    // The outbound group session.
    // They are not stored in 'store' to avoid to remember to which devices we sent the session key.
    // Plus, in cryptography, it is good to refresh sessions from time to time.
    // The key is the session id, the value the outbound group session.
    private HashMap<String, OlmOutboundGroupSession> mOutboundGroupSessionStore;

    /**
     * Constructor
     * @param store the used store
     */
    public MXOlmDevice(IMXStore store) {
        mStore = store;

        // Retrieve the account from the store
        mOlmAccount = store.endToEndAccount();

        if (null == mOlmAccount) {
            // Else, create it
            mOlmAccount =  new OlmAccount();

            mStore.storeEndToEndAccount(mOlmAccount);
            mStore.commit();
        }

        // TODO : not yet implemented
        //olmUtility = [[OLMUtility alloc] init];

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
     * The olm library version.
     */
    public String olmVersion() {
        return olmVersion();
    }

    /**
     * Signs a message with the ed25519 key for this account.
     * @param message the message to be signed.
     * @return the base64-encoded signature.
     */
    public String signMessage(String message) {
        return mOlmAccount.signMessage(message);
    }

    /**
     * Signs a JSON dictionary with the ed25519 key for this account.
     * The signature is done on canonical version of the JSON.
     * @param JSONDictinary the JSON to be signed.
     * @return the base64-encoded signature
     */
    public String signJSON(Map<String, Object> JSONDictinary) {
        // @TODO: sign on canonical
        return signMessage(JSONDictinary.toString());
    }

    /**
     * @return The current (unused, unpublished) one-time keys for this account.
     */
    public Map<String, Map<String, MXKey>> oneTimeKeys() {
        Map<String, Map<String, MXKey>> res = new HashMap<>();

        JSONObject object = mOlmAccount.oneTimeKeys();

        if (null != object) {
            res = JsonUtils.getGson(false).fromJson(object.toString(), new TypeToken<Map<String, Map<String, MXKey>>>() {}.getType());
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

        mStore.storeEndToEndAccount(mOlmAccount);
        mStore.commit();
    }

    /**
     * Generate some new one-time keys
     * @param numKeys number of keys to generate
     */
    public void generateOneTimeKeys(int numKeys) {
        mOlmAccount.generateOneTimeKeys(numKeys);

        mStore.storeEndToEndAccount(mOlmAccount);
        mStore.commit();
    }

    /**
     * Generate a new outbound session.
     * The new session will be stored in the MXStore.
     * @param theirIdentityKey the remote user's Curve25519 identity key
     * @param theirOneTimeKey the remote user's one-time Curve25519 key
     * @return the session id for the outbound session. @TODO OLMSession?
     */
    public String createOutboundSession(String theirIdentityKey, String theirOneTimeKey) {
        OlmSession olmSession =  new OlmSession();
        olmSession.initOutboundSessionWithAccount(mOlmAccount, theirOneTimeKey, theirOneTimeKey);

        mStore.storeEndToEndSession(olmSession, theirIdentityKey);
        mStore.commit();

        return olmSession.sessionIdentifier();
    }

    /**
     *
     * Generate a new inbound session, given an incoming message.
     * @param theirDeviceIdentityKey the remote user's Curve25519 identity key.
     * @param messageType the message_type field from the received message (must be 0).
     * @param ciphertext base64-encoded body from the received message.
     * @TODO
     * @return {{payload: string, session_id: string}} decrypted payload, andsession id of new session.
     * @TODO @raises {Error} if the received message was not valid (for instance, it didn't use a valid one-time key).
     */
    public Map<String, String> createInboundSession(String theirDeviceIdentityKey, int messageType, String ciphertext) {
        OlmSession olmSession = new OlmSession();

        if (null != olmSession.initInboundSessionWithAccountFrom(mOlmAccount, theirDeviceIdentityKey, ciphertext)) {
            mOlmAccount.removeOneTimeKeysForSession(olmSession);
            mStore.storeEndToEndAccount(mOlmAccount);

            OlmMessage olmMessage = new OlmMessage();
            olmMessage.mCipherText = ciphertext;
            olmMessage.mType = messageType;

            String payloadString = olmSession.decryptMessage(olmMessage);
            mStore.storeEndToEndSession(olmSession, theirDeviceIdentityKey);
            mStore.commit();

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
        Map<String, OlmSession> map =  mStore.endToEndSessionsWithDevice(theirDeviceIdentityKey);

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
    public String encryptMessage(String theirDeviceIdentityKey, String sessionId, String payloadString) {
        String ciphertext = null;

        OlmSession olmSession = sessionForDevice(theirDeviceIdentityKey, sessionId);

        if (null != olmSession) {
            OlmMessage olmMessage = olmSession.encryptMessage(payloadString);

            if (null != olmMessage) {
                ciphertext = olmMessage.mCipherText;
            }

            mStore.storeEndToEndSession(olmSession, theirDeviceIdentityKey);
            mStore.commit();
        }

        return ciphertext;
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

            mStore.storeEndToEndSession(olmSession, theirDeviceIdentityKey);
            mStore.commit();
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

        if (null != olmSession) {
            return olmSession.matchesInboundSession(ciphertext);
        }

        return false;
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

        mStore.storeEndToEndInboundGroupSession(session);
        mStore.commit();

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
        MXDecryptionResult result = null;
        MXOlmInboundGroupSession session = mStore.endToEndInboundGroupSessionWithId(sessionId, senderKey);

        if (null != session) {
            // Check that the room id matches the original one for the session. This stops
            // the HS pretending a message was targeting a different room.
            if (TextUtils.equals(roomId, session.mRoomId)) {
                String payloadString = session.mSession.decryptMessage(body);

                mStore.storeEndToEndInboundGroupSession(session);
                mStore.commit();

                result = new MXDecryptionResult();

                try {
                    String urlEncoded = URLEncoder.encode(payloadString.toString(), "utf-8");
                    JsonParser parser = new JsonParser();

                    result.mPayload =  parser.parse(urlEncoded);
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
            } else {
                Log.e(LOG_TAG, "## decryptGroupMessage() : Mismatched room_id for inbound group session (expected " + roomId + " , was " + session.mRoomId);
            }
        }
        else {
            Log.e(LOG_TAG, "## decryptGroupMessage() : Cannot retrieve inbound group session " + sessionId);
        }

        return result;
    }


    //  Utilities
    /**
     * Verify an ed25519 signature on a JSON object.
     * @param key the ed25519 key.
     * @param message the message which was signed.
     * @param signature the base64-encoded signature to be checked.
     *
     * // TODO add exception
     * @return true if valid.
     */
    public boolean verifySignature(String key, String message, String signature) {
        // TODO not yet implemented
        //return [olmUtility ed25519Verify:key message:message signature:signature error:error];
        return true;
    }

    /**
     * Verify an ed25519 signature on a JSON object.
     * @param key the ed25519 key.
     * @param JSONDictinary the JSON object which was signed.
     * @param signature the base64-encoded signature to be checked.
     * @param the result error if there is a problem with the verification.
     * // TODO add exception
     * @return true if valid.
     */

    public boolean verifySignature(String key, Map<String, Object> JSONDictinary, String signature) {
        // @TODO: sign on canonical
        return verifySignature(key, JSONDictinary.toString(), signature);
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
            Map<String, OlmSession> map = mStore.endToEndSessionsWithDevice(theirDeviceIdentityKey);

            if (null != map) {
                return map.get(sessionId);
            }
        }

        return null;
    }
}