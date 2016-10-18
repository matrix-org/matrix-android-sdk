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
import org.matrix.androidsdk.crypto.MXOlmDevice;
import org.matrix.androidsdk.crypto.algorithms.IMXDecrypting;
import org.matrix.androidsdk.crypto.algorithms.MXDecryptionResult;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.util.JsonUtils;

import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * An interface for encrypting data
 */
public class MXOlmDecryption implements IMXDecrypting {
    private static String LOG_TAG = "MXOlmDecryption";

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
                // @TODO: error
                //throw new base.DecryptionError("Missing ciphertext");
                return null;
            }

            if (!ciphertext.containsKey(mOlmDevice.getDeviceCurve25519Key())) {
                // @TODO: error
                //throw new base.DecryptionError("Not included in recipients");
                return null;
            }

            // The message for myUser
            Map<String, Object> message = (Map<String, Object>) ciphertext.get(mOlmDevice.getDeviceCurve25519Key());
            String payloadString = decryptMessage(message, deviceKey);

            if (null == payloadString) {
                Log.e(LOG_TAG, "## decryptEvent() Failed to decrypt Olm event (id= " + event.eventId + " ) from " + deviceKey);
                // @TODO: error
                // throw new base.DecryptionError("Bad Encrypted Message");
            }

            result = new MXDecryptionResult();
            result.mPayload = new JsonParser().parse(URLEncoder.encode(payloadString, "utf-8"));

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
        Set<String> sessionIds = mOlmDevice.sessionIdsForDevice(theirDeviceIdentityKey);

        // Try each session in turn
        for (String sessionId : sessionIds) {
            String payload = null;

            try {
                payload = mOlmDevice.decryptMessage((String) message.get("body"), (int) message.get("type"), sessionId, theirDeviceIdentityKey);
            } catch (Exception e) {
                Log.e(LOG_TAG, "## decryptMessage() : failed " + e.getMessage());
            }


            if (null != payload) {
                Log.d(LOG_TAG, "## decryptMessage(): Decrypted Olm message from " + theirDeviceIdentityKey + "   with session" + sessionId);
                return payload;
            } else {
                // @TODO
            }
        }
        return null;
    }
}
