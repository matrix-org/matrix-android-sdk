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
import org.matrix.androidsdk.util.JsonUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

public class MXMegolmDecryption implements IMXDecrypting {
    private static final String LOG_TAG = "MXMegolmDecryption";

    /**
     *  The olm device interface
     */
    private MXOlmDevice mOlmDevice;

    // the matrix session
    private MXSession mSession;

    /**
     * Events which we couldn't decrypt due to unknown sessions / indexes: map from
     * senderKey|sessionId to timelines to list of MatrixEvents.
     */
    private HashMap<String, /* senderKey|sessionId */
                HashMap<String /* timelineId */, ArrayList<Event>>> mPendingEvents;

    /**
     * Init the object fields
     * @param matrixSession the matrix session
     */
    @Override
    public void initWithMatrixSession(MXSession matrixSession) {
        mSession = matrixSession;
        mOlmDevice = matrixSession.getCrypto().getOlmDevice();
        mPendingEvents = new HashMap<>();
    }

    @Override
    public boolean decryptEvent(Event event, String timeline) {
        // sanity check
        if (null == event) {
            Log.e(LOG_TAG, "## decryptEvent() : null event");
            return false;
        }

        JsonObject object = event.content.getAsJsonObject();

        if (null == object) {
            Log.e(LOG_TAG, "## decryptEvent() : cannot convert to jsonobject");
            return false;
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
            event.setCryptoError(new MXCryptoError(MXCryptoError.MISSING_FIELDS_ERROR_CODE, MXCryptoError.MISSING_FIELDS_REASON));
            return false;
        }

        event.setClearEvent(null);

        MXDecryptionResult result = mOlmDevice.decryptGroupMessage(ciphertext, event.roomId, timeline, sessionId, senderKey);

        // the decryption succeeds
        if ((null != result) && (null != result.mPayload) && (null == result.mCryptoError)) {
            Event clearedEvent = JsonUtils.toEvent(result.mPayload);
            clearedEvent.setKeysProved(result.mKeysProved);
            clearedEvent.setKeysClaimed(result.mKeysClaimed);
            event.setClearEvent(clearedEvent);
        } else if (null != result.mCryptoError) {
            if (result.mCryptoError.isOlmError()) {
                if (TextUtils.equals("UNKNOWN_MESSAGE_INDEX", result.mCryptoError.error)) {
                    addEventToPendingList(event, timeline);
                }

                // Package olm error into MXDecryptingErrorDomain
                String reason = String.format(MXCryptoError.UNABLE_TO_DECRYPT_REASON, ciphertext, result.mCryptoError.error);
                result.mCryptoError = new MXCryptoError(MXCryptoError.UNABLE_TO_DECRYPT_ERROR_CODE, reason);
            } else if (TextUtils.equals(result.mCryptoError.errcode, MXCryptoError.UNKNOWN_INBOUND_SESSION_ID_ERROR_CODE)) {
                addEventToPendingList(event, timeline);
            }

            event.setCryptoError(result.mCryptoError);
        }

        return null != event.getClearEvent();
    }

    /**
     * Add an event to the list of those we couldn't decrypt the first time we
     * saw them.
     * @param event the event to try to decrypt later
     * @param timelineId the timeline identifier
     */
    private void addEventToPendingList(Event event, String timelineId) {
        JsonObject object = event.content.getAsJsonObject();
        String senderKey = null;
        String sessionId = null;

        try {
            senderKey = object.get("sender_key").getAsString();
            sessionId = object.get("session_id").getAsString();
        } catch (Exception e) {
            Log.e(LOG_TAG, "## decryptEvent() : parsing failed " + e.getMessage());
        }

        String k = senderKey + "|" + sessionId;

        // avoid undefined timelineId
        if (TextUtils.isEmpty(timelineId)) {
            timelineId = "";
        }

        if (!mPendingEvents.containsKey(k)) {
            mPendingEvents.put(k, new HashMap<String, ArrayList<Event>>());
        }

        if (!mPendingEvents.get(k).containsKey(timelineId)) {
            mPendingEvents.get(k).put(timelineId, new ArrayList<Event>());
        }

        Log.d(LOG_TAG, "## addEventToPendingList() : add " + event);
        mPendingEvents.get(k).get(timelineId).add(event);
    }

    /**
     * Handle a key event.
     * @param roomKeyEvent the key event.
     */
    @Override
    public void onRoomKeyEvent(Event roomKeyEvent) {
        Log.d(LOG_TAG, "## onRoomKeyEvent(), Adding key "); // from " + event);

        JsonObject object = roomKeyEvent.getContentAsJsonObject();

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

        mOlmDevice.addInboundGroupSession(sessionId, sessionKey, roomId, roomKeyEvent.senderKey(), roomKeyEvent.getKeysClaimed());

        String k = roomKeyEvent.senderKey() + "|" + sessionId;

        HashMap<String, ArrayList<Event>> pending = mPendingEvents.get(k);

        if (null != pending) {
            // Have another go at decrypting events sent with this session.
            mPendingEvents.remove(k);

            Set<String> timelineIds = pending.keySet();

            for (String timelineId : timelineIds) {
                ArrayList<Event> events = pending.get(timelineId);

                for(Event event : events) {
                    if (decryptEvent(event, TextUtils.isEmpty(timelineId) ? null : timelineId)) {
                        mSession.getDataHandler().onEventDecrypted(event);
                        Log.d(LOG_TAG, "## onRoomKeyEvent() : successful re-decryption of " + event.eventId);
                    } else {
                        Log.e(LOG_TAG, "## onRoomKeyEvent() : Still can't decrypt " + event.eventId + ". Error " + event.getCryptoError().getMessage());
                    }
                }
            }
        }

    }
}
