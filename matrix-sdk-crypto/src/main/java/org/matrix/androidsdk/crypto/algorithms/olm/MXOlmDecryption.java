/*
 * Copyright 2015 OpenMarket Ltd
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

package org.matrix.androidsdk.crypto.algorithms.olm;

import android.text.TextUtils;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import org.matrix.androidsdk.core.JsonUtility;
import org.matrix.androidsdk.core.Log;
import org.matrix.androidsdk.core.StringUtilsKt;
import org.matrix.androidsdk.crypto.IncomingRoomKeyRequest;
import org.matrix.androidsdk.crypto.MXCryptoError;
import org.matrix.androidsdk.crypto.MXDecryptionException;
import org.matrix.androidsdk.crypto.MXEventDecryptionResult;
import org.matrix.androidsdk.crypto.MXOlmDevice;
import org.matrix.androidsdk.crypto.algorithms.IMXDecrypting;
import org.matrix.androidsdk.crypto.interfaces.CryptoEvent;
import org.matrix.androidsdk.crypto.interfaces.CryptoSession;
import org.matrix.androidsdk.crypto.internal.MXCryptoImpl;
import org.matrix.androidsdk.crypto.model.crypto.OlmEventContent;
import org.matrix.androidsdk.crypto.model.crypto.OlmPayloadContent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An interface for encrypting data
 */
public class MXOlmDecryption implements IMXDecrypting {
    private static final String LOG_TAG = "MXOlmDecryption";

    // The olm device interface
    private MXOlmDevice mOlmDevice;

    // the matrix session
    private CryptoSession mSession;

    @Override
    public void initWithMatrixSession(CryptoSession matrixSession, MXCryptoImpl crypto) {
        mSession = matrixSession;
        mOlmDevice = crypto.getOlmDevice();
    }

    @Override
    public MXEventDecryptionResult decryptEvent(CryptoEvent event, String timeline) throws MXDecryptionException {
        // sanity check
        if (null == event) {
            Log.e(LOG_TAG, "## decryptEvent() : null event");
            return null;
        }

        OlmEventContent olmEventContent = event.toOlmEventContent();
        String deviceKey = olmEventContent.sender_key;
        Map<String, Object> ciphertext = olmEventContent.ciphertext;

        if (null == ciphertext) {
            Log.e(LOG_TAG, "## decryptEvent() : missing cipher text");
            throw new MXDecryptionException(new MXCryptoError(MXCryptoError.MISSING_CIPHER_TEXT_ERROR_CODE,
                    MXCryptoError.UNABLE_TO_DECRYPT, MXCryptoError.MISSING_CIPHER_TEXT_REASON));
        }

        if (!ciphertext.containsKey(mOlmDevice.getDeviceCurve25519Key())) {
            Log.e(LOG_TAG, "## decryptEvent() : our device " + mOlmDevice.getDeviceCurve25519Key()
                    + " is not included in recipients. Event " + event.getContentAsJsonObject());
            throw new MXDecryptionException(new MXCryptoError(MXCryptoError.NOT_INCLUDE_IN_RECIPIENTS_ERROR_CODE,
                    MXCryptoError.UNABLE_TO_DECRYPT, MXCryptoError.NOT_INCLUDED_IN_RECIPIENT_REASON));
        }

        // The message for myUser
        Map<String, Object> message = (Map<String, Object>) ciphertext.get(mOlmDevice.getDeviceCurve25519Key());
        String payloadString = decryptMessage(message, deviceKey);

        if (null == payloadString) {
            Log.e(LOG_TAG, "## decryptEvent() Failed to decrypt Olm event (id= " + event.getEventId() + " ) from " + deviceKey);
            throw new MXDecryptionException(new MXCryptoError(MXCryptoError.BAD_ENCRYPTED_MESSAGE_ERROR_CODE,
                    MXCryptoError.UNABLE_TO_DECRYPT, MXCryptoError.BAD_ENCRYPTED_MESSAGE_REASON));
        }

        JsonElement payload = new JsonParser().parse(StringUtilsKt.convertFromUTF8(payloadString));

        if (null == payload) {
            Log.e(LOG_TAG, "## decryptEvent failed : null payload");
            throw new MXDecryptionException(new MXCryptoError(MXCryptoError.UNABLE_TO_DECRYPT_ERROR_CODE,
                    MXCryptoError.UNABLE_TO_DECRYPT, MXCryptoError.MISSING_CIPHER_TEXT_REASON));
        }

        OlmPayloadContent olmPayloadContent = JsonUtility.toClass(payload, OlmPayloadContent.class);

        if (TextUtils.isEmpty(olmPayloadContent.recipient)) {
            String reason = String.format(MXCryptoError.ERROR_MISSING_PROPERTY_REASON, "recipient");
            Log.e(LOG_TAG, "## decryptEvent() : " + reason);
            throw new MXDecryptionException(new MXCryptoError(MXCryptoError.MISSING_PROPERTY_ERROR_CODE,
                    MXCryptoError.UNABLE_TO_DECRYPT, reason));
        }

        if (!TextUtils.equals(olmPayloadContent.recipient, mSession.getMyUserId())) {
            Log.e(LOG_TAG, "## decryptEvent() : Event " + event.getEventId() + ": Intended recipient " + olmPayloadContent.recipient
                    + " does not match our id " + mSession.getMyUserId());
            throw new MXDecryptionException(new MXCryptoError(MXCryptoError.BAD_RECIPIENT_ERROR_CODE,
                    MXCryptoError.UNABLE_TO_DECRYPT, String.format(MXCryptoError.BAD_RECIPIENT_REASON, olmPayloadContent.recipient)));
        }

        if (null == olmPayloadContent.recipient_keys) {
            Log.e(LOG_TAG, "## decryptEvent() : Olm event (id=" + event.getEventId()
                    + ") contains no " + "'recipient_keys' property; cannot prevent unknown-key attack");
            throw new MXDecryptionException(new MXCryptoError(MXCryptoError.MISSING_PROPERTY_ERROR_CODE,
                    MXCryptoError.UNABLE_TO_DECRYPT, String.format(MXCryptoError.ERROR_MISSING_PROPERTY_REASON, "recipient_keys")));
        }

        String ed25519 = olmPayloadContent.recipient_keys.get("ed25519");

        if (!TextUtils.equals(ed25519, mOlmDevice.getDeviceEd25519Key())) {
            Log.e(LOG_TAG, "## decryptEvent() : Event " + event.getEventId() + ": Intended recipient ed25519 key " + ed25519 + " did not match ours");
            throw new MXDecryptionException(new MXCryptoError(MXCryptoError.BAD_RECIPIENT_KEY_ERROR_CODE,
                    MXCryptoError.UNABLE_TO_DECRYPT, MXCryptoError.BAD_RECIPIENT_KEY_REASON));
        }

        if (TextUtils.isEmpty(olmPayloadContent.sender)) {
            Log.e(LOG_TAG, "## decryptEvent() : Olm event (id=" + event.getEventId()
                    + ") contains no 'sender' property; cannot prevent unknown-key attack");
            throw new MXDecryptionException(new MXCryptoError(MXCryptoError.MISSING_PROPERTY_ERROR_CODE,
                    MXCryptoError.UNABLE_TO_DECRYPT, String.format(MXCryptoError.ERROR_MISSING_PROPERTY_REASON, "sender")));
        }

        if (!TextUtils.equals(olmPayloadContent.sender, event.getSender())) {
            Log.e(LOG_TAG, "Event " + event.getEventId() + ": original sender " + olmPayloadContent.sender
                    + " does not match reported sender " + event.getSender());
            throw new MXDecryptionException(new MXCryptoError(MXCryptoError.FORWARDED_MESSAGE_ERROR_CODE,
                    MXCryptoError.UNABLE_TO_DECRYPT, String.format(MXCryptoError.FORWARDED_MESSAGE_REASON, olmPayloadContent.sender)));
        }

        if (!TextUtils.equals(olmPayloadContent.room_id, event.getRoomId())) {
            Log.e(LOG_TAG, "## decryptEvent() : Event " + event.getEventId() + ": original room " + olmPayloadContent.room_id
                    + " does not match reported room " + event.getRoomId());
            throw new MXDecryptionException(new MXCryptoError(MXCryptoError.BAD_ROOM_ERROR_CODE,
                    MXCryptoError.UNABLE_TO_DECRYPT, String.format(MXCryptoError.BAD_ROOM_REASON, olmPayloadContent.room_id)));
        }

        if (null == olmPayloadContent.keys) {
            Log.e(LOG_TAG, "## decryptEvent failed : null keys");
            throw new MXDecryptionException(new MXCryptoError(MXCryptoError.UNABLE_TO_DECRYPT_ERROR_CODE,
                    MXCryptoError.UNABLE_TO_DECRYPT, MXCryptoError.MISSING_CIPHER_TEXT_REASON));
        }

        MXEventDecryptionResult result = new MXEventDecryptionResult();
        result.mClearEvent = payload;
        result.mSenderCurve25519Key = deviceKey;
        result.mClaimedEd25519Key = olmPayloadContent.keys.get("ed25519");

        return result;
    }

    @Override
    public void onRoomKeyEvent(CryptoEvent event) {
        // No impact for olm
    }

    @Override
    public void onNewSession(String senderKey, String sessionId) {
        // No impact for olm
    }

    @Override
    public boolean hasKeysForKeyRequest(IncomingRoomKeyRequest request) {
        return false;
    }

    @Override
    public void shareKeysWithDevice(IncomingRoomKeyRequest request) {
    }

    /**
     * Attempt to decrypt an Olm message.
     *
     * @param theirDeviceIdentityKey the Curve25519 identity key of the sender.
     * @param message                message object, with 'type' and 'body' fields.
     * @return payload, if decrypted successfully.
     */
    private String decryptMessage(Map<String, Object> message, String theirDeviceIdentityKey) {
        Set<String> sessionIdsSet = mOlmDevice.getSessionIds(theirDeviceIdentityKey);

        List<String> sessionIds;

        if (null == sessionIdsSet) {
            sessionIds = new ArrayList<>();
        } else {
            sessionIds = new ArrayList<>(sessionIdsSet);
        }

        String messageBody = (String) message.get("body");
        Integer messageType = null;

        Object typeAsVoid = message.get("type");

        if (null != typeAsVoid) {
            if (typeAsVoid instanceof Double) {
                messageType = ((Double) typeAsVoid).intValue();
            } else if (typeAsVoid instanceof Integer) {
                messageType = (Integer) typeAsVoid;
            } else if (typeAsVoid instanceof Long) {
                messageType = ((Long) typeAsVoid).intValue();
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

        Log.d(LOG_TAG, "## decryptMessage() :  Created new inbound Olm session get id " + res.get("session_id") + " with " + theirDeviceIdentityKey);

        return res.get("payload");
    }
}
