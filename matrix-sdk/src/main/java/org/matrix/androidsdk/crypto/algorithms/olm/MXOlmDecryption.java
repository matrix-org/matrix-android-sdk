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

package org.matrix.androidsdk.crypto.algorithms.olm;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.crypto.MXCryptoError;
import org.matrix.androidsdk.crypto.MXOlmDevice;
import org.matrix.androidsdk.crypto.algorithms.IMXDecrypting;
import org.matrix.androidsdk.crypto.algorithms.MXDecryptionResult;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.util.JsonUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * An interface for encrypting data
 */
public class MXOlmDecryption implements IMXDecrypting {
    private static final String LOG_TAG = "MXOlmDecryption";

    // The olm device interface
    private MXOlmDevice mOlmDevice;


    @Override
    public void initWithMatrixSession(MXSession matrixSession) {
        mOlmDevice = matrixSession.getCrypto().getOlmDevice();
    }

    @Override
    public MXDecryptionResult decryptEvent(Event event) {
        MXDecryptionResult result = null;

        try {
            JsonObject eventContent = event.getContentAsJsonObject();
            Gson gson = JsonUtils.getGson(false);

            String deviceKey = eventContent.get("sender_key").getAsString();
            Map<String, Object> ciphertext = gson.fromJson(eventContent.get("ciphertext"), new TypeToken<Map<String, Object>>() {
            }.getType());

            if (null == ciphertext) {
                result = new MXDecryptionResult();
                result.mCryptoError = new MXCryptoError(MXCryptoError.MISSING_CIPHERTEXT);
                return result;
            }

            if (!ciphertext.containsKey(mOlmDevice.getDeviceCurve25519Key())) {
                result = new MXDecryptionResult();
                result.mCryptoError = new MXCryptoError(MXCryptoError.NOT_INCLUDED_IN_RECIPIENTS);
                return result;
            }

            // The message for myUser
            Map<String, Object> message = (Map<String, Object>) ciphertext.get(mOlmDevice.getDeviceCurve25519Key());
            String payloadString = decryptMessage(message, deviceKey);

            if (null == payloadString) {
                Log.e(LOG_TAG, "## decryptEvent() Failed to decrypt Olm event (id= " + event.eventId + " ) from " + deviceKey);
                result = new MXDecryptionResult();
                result.mCryptoError = new MXCryptoError(MXCryptoError.BAD_ENCRYPTED_MESSAGE);
                return result;
            }

            result = new MXDecryptionResult();
            result.mPayload = new JsonParser().parse(JsonUtils.convertFromUTF8(payloadString));

            HashMap<String, String> keysProved = new HashMap<>();
            keysProved.put("curve25519", deviceKey);
            result.mKeysProved = keysProved;
            result.mKeysClaimed = gson.fromJson(result.mPayload.getAsJsonObject().get("keys"), new TypeToken<Map<String, String>>() {
            }.getType());
        } catch (Exception e) {
            Log.e(LOG_TAG, "## decryptEvent failed " + e.getMessage());
        }

        return result;

    }

    @Override
    public  void onRoomKeyEvent(Event event) {
        // No impact for olm
    }

    /**
     Attempt to decrypt an Olm message.

     @param theirDeviceIdentityKey the Curve25519 identity key of the sender.
     @param message message object, with 'type' and 'body' fields.

     @return payload, if decrypted successfully.
     */
    private String decryptMessage(Map<String, Object>message, String theirDeviceIdentityKey) {
        Set<String> sessionIdsSet =  mOlmDevice.sessionIdsForDevice(theirDeviceIdentityKey);

        ArrayList<String> sessionIds;

        if (null == sessionIdsSet) {
            sessionIds = new ArrayList<>();
        } else {
            sessionIds = new ArrayList<>(sessionIdsSet);
        }

        String messageBody = (String)message.get("body");
        Integer messageType = null;

        Object typeAsVoid = message.get("type");

        if (null != typeAsVoid) {
            if (typeAsVoid instanceof Double) {
                messageType = new Integer (((Double)typeAsVoid).intValue());
            } else  if (typeAsVoid instanceof Integer) {
                messageType = (Integer)typeAsVoid;
            } else if (typeAsVoid instanceof Long) {
                messageType = new Integer (((Long)typeAsVoid).intValue());
            }
        }

        if ((null == messageBody) || (null == messageType)) {
            return null;
        }

        // Try each session in turn
        // decryptionErrors = {};
        for (String sessionId : sessionIds) {
            String payload = mOlmDevice.decryptMessage(messageBody, messageType, sessionId, theirDeviceIdentityKey);

            if (null != payload) {
                Log.d(LOG_TAG, "## decryptMessage() : Decrypted Olm message from " + theirDeviceIdentityKey + " with session " + sessionId);
                return payload;
            } else {
                boolean foundSession = mOlmDevice.matchesSession(theirDeviceIdentityKey, sessionId, messageType, messageBody);

                if (foundSession) {
                    // Decryption failed, but it was a prekey message matching this
                    // session, so it should have worked.
                    Log.e(LOG_TAG, "## decryptMessage() : Error decrypting prekey message with existing session id " + sessionId + ":TODO");
                    return null;
                }
            }
        }

        if (messageType != 0) {
            // not a prekey message, so it should have matched an existing session, but it
            // didn't work.

            if (sessionIds.size() == 0) {
                Log.e(LOG_TAG, "## decryptMessage() : No existing sessions");
            } else {
                Log.e(LOG_TAG, "## decryptMessage() : Error decrypting non-prekey message with existing sessions");
            }

            return null;
        }

        // prekey message which doesn't match any existing sessions: make a new
        // session.
        Map<String, String> res = mOlmDevice.createInboundSession(theirDeviceIdentityKey, messageType, messageBody);

        if (null == res) {
            Log.e(LOG_TAG, "## decryptMessage() :  Error decrypting non-prekey message with existing sessions");
            return null;
        }

        Log.d(LOG_TAG, "## decryptMessage() :  Created new inbound Olm session get id " + res.get("session_id") + " with " +  theirDeviceIdentityKey);

        return res.get("payload");
    }
}
