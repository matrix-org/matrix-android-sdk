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

package org.matrix.androidsdk.crypto.algorithms.megolm;

import android.text.TextUtils;
import android.util.Log;

import com.google.gson.JsonObject;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.crypto.MXCryptoError;
import org.matrix.androidsdk.crypto.MXOlmDevice;
import org.matrix.androidsdk.crypto.algorithms.IMXDecrypting;
import org.matrix.androidsdk.crypto.algorithms.MXDecryptionResult;
import org.matrix.androidsdk.rest.model.Event;

public class MXMegolmDecryption implements IMXDecrypting {
    private static final String LOG_TAG = "MXMegolmDecryption";

    // The olm device interface
    private MXOlmDevice mOlmDevice;

    /**
     * Init the object fields
     * @param matrixSession the matrix session
     */
    @Override
    public void initWithMatrixSession(MXSession matrixSession) {
        mOlmDevice = matrixSession.getCrypto().getOlmDevice();
    }

    /**
     * Decrypt a message
     * @param event the raw event.
     * @return the decryption result. Nil if the event referred to an unknown megolm session.
     */
    @Override
    public MXDecryptionResult decryptEvent(Event event) {
        JsonObject object = event.content.getAsJsonObject();

        if (null == object) {
            Log.e(LOG_TAG, "## decryptEvent() : cannot convert to jsonobject");
            return null;
        }

        String senderKey = null;
        String ciphertext = null;
        String sessionId = null;

        try {
            senderKey = object.get("sender_key").getAsString();
            ciphertext = object.get("ciphertext").getAsString();
            sessionId = object.get("session_id").getAsString();
        } catch (Exception e) {
            Log.e(LOG_TAG, "## decryptEvent() : parsing failed " + e.getMessage());
        }

        if (TextUtils.isEmpty(senderKey) || TextUtils.isEmpty(sessionId) || TextUtils.isEmpty(ciphertext)) {
            MXDecryptionResult result = new MXDecryptionResult();
            result.mCryptoError = new MXCryptoError(MXCryptoError.MISSING_FIELDS);
            return result;
        }

        return mOlmDevice.decryptGroupMessage(ciphertext, event.roomId, sessionId, senderKey);
    }

    /**
     * Handle a key event.
     * @param event the key event.
     */
    @Override
    public void onRoomKeyEvent(Event event) {
        Log.d(LOG_TAG, "## onRoomKeyEvent(), Adding key "); // from " + event);

        JsonObject object = event.getContentAsJsonObject();

        if (null == object) {
            Log.e(LOG_TAG, "## onRoomKeyEvent() : cannot convert to jsonobject");
            return;
        }

        String roomId = null;
        String sessionId = null;
        String sessionKey = null;

        try {
            roomId = object.get("room_id").getAsString();
            sessionId = object.get("session_id").getAsString();
            sessionKey = object.get("session_key").getAsString();
        } catch (Exception e) {
            Log.e(LOG_TAG, "## onRoomKeyEvent() : parsing failed " + e.getMessage());
        }

        if (TextUtils.isEmpty(roomId) || TextUtils.isEmpty(sessionId) || TextUtils.isEmpty(sessionKey)) {
            Log.e(LOG_TAG, "## onRoomKeyEvent() :  Key event is missing fields");
            return;
        }

        mOlmDevice.addInboundGroupSession(sessionId, sessionKey, roomId, event.senderKey(), event.getKeysClaimed());
    }
}
