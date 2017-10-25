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
import android.util.Pair;

import org.matrix.androidsdk.crypto.IncomingRoomKeyRequest;
import org.matrix.androidsdk.crypto.MXCryptoAlgorithms;
import org.matrix.androidsdk.crypto.data.MXDeviceInfo;
import org.matrix.androidsdk.crypto.data.MXOlmSessionResult;
import org.matrix.androidsdk.crypto.data.MXUsersDevicesMap;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.ForwardedRoomKeyContent;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.RoomKeyRequestBody;
import org.matrix.androidsdk.util.Log;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.crypto.MXCryptoError;
import org.matrix.androidsdk.crypto.MXOlmDevice;
import org.matrix.androidsdk.crypto.algorithms.IMXDecrypting;
import org.matrix.androidsdk.crypto.algorithms.MXDecryptionResult;
import org.matrix.androidsdk.rest.model.EncryptedEventContent;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.RoomKeyContent;
import org.matrix.androidsdk.util.JsonUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class MXMegolmDecryption implements IMXDecrypting {
    private static final String LOG_TAG = "MXMegolmDecryption";

    /**
     * The olm device interface
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
     *
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

        EncryptedEventContent encryptedEventContent = JsonUtils.toEncryptedEventContent(event.getWireContent().getAsJsonObject());

        String senderKey = encryptedEventContent.sender_key;
        String ciphertext = encryptedEventContent.ciphertext;
        String sessionId = encryptedEventContent.session_id;

        if (TextUtils.isEmpty(senderKey) || TextUtils.isEmpty(sessionId) || TextUtils.isEmpty(ciphertext)) {
            event.setCryptoError(new MXCryptoError(MXCryptoError.MISSING_FIELDS_ERROR_CODE, MXCryptoError.UNABLE_TO_DECRYPT, MXCryptoError.MISSING_FIELDS_REASON));
            return false;
        }

        event.setClearEvent(null);
        event.setCryptoError(null);

        MXDecryptionResult result = mOlmDevice.decryptGroupMessage(ciphertext, event.roomId, timeline, sessionId, senderKey);

        // the decryption succeeds
        if ((null != result) && (null != result.mPayload) && (null == result.mCryptoError)) {
            Event clearedEvent = JsonUtils.toEvent(result.mPayload);
            clearedEvent.setKeysProved(result.mKeysProved);
            clearedEvent.setKeysClaimed(result.mKeysClaimed);
            event.setClearEvent(clearedEvent);
        } else if ((null != result) && (null != result.mCryptoError)) {
            if (result.mCryptoError.isOlmError()) {
                if (TextUtils.equals("UNKNOWN_MESSAGE_INDEX", result.mCryptoError.error)) {
                    addEventToPendingList(event, timeline);
                }

                String reason = String.format(MXCryptoError.OLM_REASON, result.mCryptoError.error);
                String detailedReason = String.format(MXCryptoError.DETAILLED_OLM_REASON, ciphertext, result.mCryptoError.error);

                result.mCryptoError = new MXCryptoError(
                        MXCryptoError.OLM_ERROR_CODE,
                        reason,
                        detailedReason);
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
     *
     * @param event      the event to try to decrypt later
     * @param timelineId the timeline identifier
     */
    private void addEventToPendingList(Event event, String timelineId) {
        EncryptedEventContent encryptedEventContent = JsonUtils.toEncryptedEventContent(event.getWireContent().getAsJsonObject());

        String senderKey = encryptedEventContent.sender_key;
        String sessionId = encryptedEventContent.session_id;

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

        if (mPendingEvents.get(k).get(timelineId).indexOf(event) < 0) {
            Log.d(LOG_TAG, "## addEventToPendingList() : add Event " + event.eventId + " in room id " + event.roomId);
            mPendingEvents.get(k).get(timelineId).add(event);
        }
    }

    /**
     * Handle a key event.
     *
     * @param roomKeyEvent the key event.
     */
    @Override
    public void onRoomKeyEvent(Event roomKeyEvent) {
        boolean exportFormat = false;
        RoomKeyContent roomKeyContent = JsonUtils.toRoomKeyContent(roomKeyEvent.getContentAsJsonObject());

        String roomId = roomKeyContent.room_id;
        String sessionId = roomKeyContent.session_id;
        String sessionKey = roomKeyContent.session_key;
        String senderKey = roomKeyEvent.senderKey();
        Map<String, String> keysClaimed = new HashMap<>();

        if (TextUtils.isEmpty(roomId) || TextUtils.isEmpty(sessionId) || TextUtils.isEmpty(sessionKey)) {
            Log.e(LOG_TAG, "## onRoomKeyEvent() :  Key event is missing fields");
            return;
        }

        if (TextUtils.equals(roomKeyEvent.getType(), Event.EVENT_TYPE_FORWARDED_ROOM_KEY)) {
            Log.d(LOG_TAG, "## onRoomKeyEvent(), Adding key : roomId " + roomId + " sessionId " + sessionId + " sessionKey " + sessionKey); // from " + event);
            ForwardedRoomKeyContent forwardedRoomKeyContent = JsonUtils.toForwardedRoomKeyContent(roomKeyEvent.getContentAsJsonObject());

            exportFormat = true;
            senderKey = forwardedRoomKeyContent.sender_key;
            if (null == senderKey) {
                Log.e(LOG_TAG, "## onRoomKeyEvent() : forwarded_room_key event is missing sender_key field");
                return;
            }
        } else {
            Log.d(LOG_TAG, "## onRoomKeyEvent(), Adding key : roomId " + roomId + " sessionId " + sessionId + " sessionKey " + sessionKey); // from " + event);

            if (null == senderKey) {
                Log.e(LOG_TAG, "## onRoomKeyEvent() : key event has no sender key (not encrypted?)");
                return;
            }

            // inherit the claimed ed25519 key from the setup message
            keysClaimed = roomKeyEvent.getKeysClaimed();
        }

        mOlmDevice.addInboundGroupSession(sessionId, sessionKey, roomId, senderKey, keysClaimed, exportFormat);
        onNewSession(roomKeyEvent.senderKey(), sessionId);
    }

    /**
     * Check if the some messages can be decrypted with a new session
     *
     * @param senderKey the session sender key
     * @param sessionId the session id
     */
    public void onNewSession(String senderKey, String sessionId) {
        String k = senderKey + "|" + sessionId;

        HashMap<String, ArrayList<Event>> pending = mPendingEvents.get(k);

        if (null != pending) {
            // Have another go at decrypting events sent with this session.
            mPendingEvents.remove(k);

            Set<String> timelineIds = pending.keySet();

            for (String timelineId : timelineIds) {
                ArrayList<Event> events = pending.get(timelineId);

                for (Event event : events) {
                    if (decryptEvent(event, TextUtils.isEmpty(timelineId) ? null : timelineId)) {
                        final Event fEvent = event;
                        mSession.getCrypto().getUIHandler().post(new Runnable() {
                            @Override
                            public void run() {
                                mSession.getDataHandler().onEventDecrypted(fEvent);
                            }
                        });
                        Log.d(LOG_TAG, "## onNewSession() : successful re-decryption of " + event.eventId);
                    } else {
                        Log.e(LOG_TAG, "## onNewSession() : Still can't decrypt " + event.eventId + ". Error " + event.getCryptoError().getMessage());
                    }
                }
            }
        }
    }

    @Override
    public boolean hasKeysForKeyRequest(IncomingRoomKeyRequest request) {
        if ((null != request) && (null != request.mRequestBody)) {
            return mOlmDevice.hasInboundSessionKeys(request.mRequestBody.room_id, request.mRequestBody.sender_key, request.mRequestBody.session_id);
        }

        return false;
    }

    @Override
    public void shareKeysWithDevice(final IncomingRoomKeyRequest request) {
        // sanity checks
        if ((null == request) || (null == request.mRequestBody)) {
            return;
        }

        final String userId = request.mUserId;
        final String deviceId = request.mDeviceId;
        final MXDeviceInfo deviceInfo = mSession.getCrypto().mCryptoStore.getUserDevice(deviceId, userId);
        final RoomKeyRequestBody body = request.mRequestBody;

        HashMap<String, ArrayList<MXDeviceInfo>> devicesByUser = new HashMap<>();
        devicesByUser.put(userId, new ArrayList<>(Arrays.asList(deviceInfo)));

        mSession.getCrypto().ensureOlmSessionsForDevices(devicesByUser, new ApiCallback<MXUsersDevicesMap<MXOlmSessionResult>>() {
            @Override
            public void onSuccess(MXUsersDevicesMap<MXOlmSessionResult> map) {
                MXOlmSessionResult olmSessionResult = map.getObject(deviceId, userId);

                if ((null == olmSessionResult) && (null == olmSessionResult.mSessionId)) {
                    // no session with this device, probably because there
                    // were no one-time keys.
                    //
                    // ensureOlmSessionsForUsers has already done the logging,
                    // so just skip it.
                    return;
                }

                Log.d(LOG_TAG, "## shareKeysWithDevice() : sharing keys for session " + body.sender_key + "|" + body.session_id + " with device " + userId + ":" + deviceId);

                Pair<Long, String> key = mSession.getCrypto().getOlmDevice().getInboundGroupSessionKey(body.room_id, body.sender_key, body.session_id);

                Map<String, Object> payloadJson = new HashMap<>();
                payloadJson.put("type", Event.EVENT_TYPE_FORWARDED_ROOM_KEY);

                Map<String, Object> contentMap = new HashMap<>();
                payloadJson.put("content", contentMap);

                contentMap.put("algorithm", MXCryptoAlgorithms.MXCRYPTO_ALGORITHM_MEGOLM);
                contentMap.put("room_id", body.room_id);
                contentMap.put("sender_key", body.sender_key);
                contentMap.put("session_id", body.session_id);
                contentMap.put("session_key", key.second);
                contentMap.put("chain_index", key.first);

                Map<String, Object> encodedPayload = mSession.getCrypto().encryptMessage(payloadJson, Arrays.asList(deviceInfo));
                MXUsersDevicesMap<Map<String, Object>> sendToDeviceMap = new MXUsersDevicesMap<>();
                sendToDeviceMap.setObject(encodedPayload, userId, deviceId);

                Log.d(LOG_TAG, "## shareKeysWithDevice() : sending to " + userId + ":" + deviceId);
                mSession.getCryptoRestClient().sendToDevice(Event.EVENT_TYPE_MESSAGE_ENCRYPTED, sendToDeviceMap, new ApiCallback<Void>() {
                    @Override
                    public void onSuccess(Void info) {
                        Log.d(LOG_TAG, "## shareKeysWithDevice() : sent to " + userId + ":" + deviceId);
                    }

                    @Override
                    public void onNetworkError(Exception e) {
                        Log.e(LOG_TAG, "## shareKeysWithDevice() : sendToDevice " + userId + ":" + deviceId + " failed " + e.getMessage());
                    }

                    @Override
                    public void onMatrixError(MatrixError e) {
                        Log.e(LOG_TAG, "## shareKeysWithDevice() : sendToDevice " + userId + ":" + deviceId + " failed " + e.getMessage());
                    }

                    @Override
                    public void onUnexpectedError(Exception e) {
                        Log.e(LOG_TAG, "## shareKeysWithDevice() : sendToDevice " + userId + ":" + deviceId + " failed " + e.getMessage());
                    }
                });
            }

            @Override
            public void onNetworkError(Exception e) {
                Log.e(LOG_TAG, "## shareKeysWithDevice() : ensureOlmSessionsForDevices " + userId + ":" + deviceId + " failed " + e.getMessage());
            }

            @Override
            public void onMatrixError(MatrixError e) {
                Log.e(LOG_TAG, "## shareKeysWithDevice() : ensureOlmSessionsForDevices " + userId + ":" + deviceId + " failed " + e.getMessage());
            }

            @Override
            public void onUnexpectedError(Exception e) {
                Log.e(LOG_TAG, "## shareKeysWithDevice() : ensureOlmSessionsForDevices " + userId + ":" + deviceId + " failed " + e.getMessage());
            }
        });
    }
}
