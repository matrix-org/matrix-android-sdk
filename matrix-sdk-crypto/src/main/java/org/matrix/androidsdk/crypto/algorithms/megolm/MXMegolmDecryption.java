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

package org.matrix.androidsdk.crypto.algorithms.megolm;

import android.support.annotation.Nullable;
import android.text.TextUtils;

import org.matrix.androidsdk.core.Log;
import org.matrix.androidsdk.core.callback.ApiCallback;
import org.matrix.androidsdk.core.model.MatrixError;
import org.matrix.androidsdk.crypto.IncomingRoomKeyRequest;
import org.matrix.androidsdk.crypto.MXCryptoError;
import org.matrix.androidsdk.crypto.MXDecryptionException;
import org.matrix.androidsdk.crypto.MXEventDecryptionResult;
import org.matrix.androidsdk.crypto.MXOlmDevice;
import org.matrix.androidsdk.crypto.algorithms.IMXDecrypting;
import org.matrix.androidsdk.crypto.algorithms.MXDecryptionResult;
import org.matrix.androidsdk.crypto.data.MXDeviceInfo;
import org.matrix.androidsdk.crypto.data.MXOlmInboundGroupSession2;
import org.matrix.androidsdk.crypto.data.MXOlmSessionResult;
import org.matrix.androidsdk.crypto.data.MXUsersDevicesMap;
import org.matrix.androidsdk.crypto.interfaces.CryptoEvent;
import org.matrix.androidsdk.crypto.interfaces.CryptoSession;
import org.matrix.androidsdk.crypto.internal.MXCryptoImpl;
import org.matrix.androidsdk.crypto.model.crypto.EncryptedEventContent;
import org.matrix.androidsdk.crypto.model.crypto.ForwardedRoomKeyContent;
import org.matrix.androidsdk.crypto.model.crypto.RoomKeyContent;
import org.matrix.androidsdk.crypto.model.crypto.RoomKeyRequestBody;
import org.matrix.androidsdk.crypto.rest.model.crypto.EncryptedMessage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MXMegolmDecryption implements IMXDecrypting {
    private static final String LOG_TAG = MXMegolmDecryption.class.getSimpleName();

    /**
     * The olm device interface
     */
    private MXOlmDevice mOlmDevice;

    // the matrix session
    private CryptoSession mSession;

    private MXCryptoImpl mCrypto;

    /**
     * Events which we couldn't decrypt due to unknown sessions / indexes: map from
     * senderKey|sessionId to timelines to list of MatrixEvents.
     */
    private Map<String, /* senderKey|sessionId */
            Map<String /* timelineId */, List<CryptoEvent>>> mPendingEvents;

    /**
     * Init the object fields
     *
     * @param matrixSession the matrix session
     */
    @Override
    public void initWithMatrixSession(CryptoSession matrixSession, MXCryptoImpl crypto) {
        mSession = matrixSession;
        mCrypto = crypto;
        mOlmDevice = crypto.getOlmDevice();
        mPendingEvents = new HashMap<>();
    }

    @Override
    @Nullable
    public MXEventDecryptionResult decryptEvent(CryptoEvent event, String timeline) throws MXDecryptionException {
        return decryptEvent(event, timeline, true);
    }

    @Nullable
    private MXEventDecryptionResult decryptEvent(CryptoEvent event, String timeline, boolean requestKeysOnFail) throws MXDecryptionException {
        // sanity check
        if (null == event) {
            Log.e(LOG_TAG, "## decryptEvent() : null event");
            return null;
        }

        EncryptedEventContent encryptedEventContent = event.toEncryptedEventContent();

        String senderKey = encryptedEventContent.sender_key;
        String ciphertext = encryptedEventContent.ciphertext;
        String sessionId = encryptedEventContent.session_id;

        if (TextUtils.isEmpty(senderKey) || TextUtils.isEmpty(sessionId) || TextUtils.isEmpty(ciphertext)) {
            throw new MXDecryptionException(new MXCryptoError(MXCryptoError.MISSING_FIELDS_ERROR_CODE,
                    MXCryptoError.UNABLE_TO_DECRYPT, MXCryptoError.MISSING_FIELDS_REASON));
        }

        MXEventDecryptionResult eventDecryptionResult = null;
        MXCryptoError cryptoError = null;
        MXDecryptionResult decryptGroupMessageResult = null;

        try {
            decryptGroupMessageResult = mOlmDevice.decryptGroupMessage(ciphertext, event.getRoomId(), timeline, sessionId, senderKey);
        } catch (MXDecryptionException e) {
            cryptoError = e.getCryptoError();
        }

        // the decryption succeeds
        if ((null != decryptGroupMessageResult) && (null != decryptGroupMessageResult.mPayload) && (null == cryptoError)) {
            eventDecryptionResult = new MXEventDecryptionResult();

            eventDecryptionResult.mClearEvent = decryptGroupMessageResult.mPayload;
            eventDecryptionResult.mSenderCurve25519Key = decryptGroupMessageResult.mSenderKey;

            if (null != decryptGroupMessageResult.mKeysClaimed) {
                eventDecryptionResult.mClaimedEd25519Key = decryptGroupMessageResult.mKeysClaimed.get("ed25519");
            }

            eventDecryptionResult.mForwardingCurve25519KeyChain = decryptGroupMessageResult.mForwardingCurve25519KeyChain;
        } else if (null != cryptoError) {
            if (cryptoError.isOlmError()) {
                if (TextUtils.equals("UNKNOWN_MESSAGE_INDEX", cryptoError.error)) {
                    addEventToPendingList(event, timeline);

                    if (requestKeysOnFail) {
                        requestKeysForEvent(event);
                    }
                }

                String reason = String.format(MXCryptoError.OLM_REASON, cryptoError.error);
                String detailedReason = String.format(MXCryptoError.DETAILLED_OLM_REASON, ciphertext, cryptoError.error);

                throw new MXDecryptionException(new MXCryptoError(
                        MXCryptoError.OLM_ERROR_CODE,
                        reason,
                        detailedReason));
            } else if (TextUtils.equals(cryptoError.errcode, MXCryptoError.UNKNOWN_INBOUND_SESSION_ID_ERROR_CODE)) {
                addEventToPendingList(event, timeline);
                if (requestKeysOnFail) {
                    requestKeysForEvent(event);
                }
            }

            throw new MXDecryptionException(cryptoError);
        }

        return eventDecryptionResult;
    }

    /**
     * Helper for the real decryptEvent and for _retryDecryption. If
     * requestKeysOnFail is true, we'll send an m.room_key_request when we fail
     * to decrypt the event due to missing megolm keys.
     *
     * @param event the event
     */
    private void requestKeysForEvent(CryptoEvent event) {
        String sender = event.getSender();
        EncryptedEventContent wireContent = event.toEncryptedEventContent();

        List<Map<String, String>> recipients = new ArrayList<>();

        Map<String, String> selfMap = new HashMap<>();
        selfMap.put("userId", mSession.getMyUserId());
        selfMap.put("deviceId", "*");
        recipients.add(selfMap);

        if (!TextUtils.equals(sender, mSession.getMyUserId())) {
            Map<String, String> senderMap = new HashMap<>();
            senderMap.put("userId", sender);
            senderMap.put("deviceId", wireContent.device_id);
            recipients.add(senderMap);
        }

        RoomKeyRequestBody requestBody = new RoomKeyRequestBody();

        requestBody.roomId = event.getRoomId();
        requestBody.algorithm = wireContent.algorithm;
        requestBody.senderKey = wireContent.sender_key;
        requestBody.sessionId = wireContent.session_id;

        mCrypto.requestRoomKey(requestBody, recipients);
    }

    /**
     * Add an event to the list of those we couldn't decrypt the first time we
     * saw them.
     *
     * @param event      the event to try to decrypt later
     * @param timelineId the timeline identifier
     */
    private void addEventToPendingList(CryptoEvent event, String timelineId) {
        EncryptedEventContent encryptedEventContent = event.toEncryptedEventContent();

        String senderKey = encryptedEventContent.sender_key;
        String sessionId = encryptedEventContent.session_id;

        String k = senderKey + "|" + sessionId;

        // avoid undefined timelineId
        if (TextUtils.isEmpty(timelineId)) {
            timelineId = "";
        }

        if (!mPendingEvents.containsKey(k)) {
            mPendingEvents.put(k, new HashMap<String, List<CryptoEvent>>());
        }

        if (!mPendingEvents.get(k).containsKey(timelineId)) {
            mPendingEvents.get(k).put(timelineId, new ArrayList<CryptoEvent>());
        }

        if (mPendingEvents.get(k).get(timelineId).indexOf(event) < 0) {
            Log.d(LOG_TAG, "## addEventToPendingList() : add Event " + event.getEventId() + " in room id " + event.getRoomId());
            mPendingEvents.get(k).get(timelineId).add(event);
        }
    }

    /**
     * Handle a key event.
     *
     * @param roomKeyEvent the key event.
     */
    @Override
    public void onRoomKeyEvent(CryptoEvent roomKeyEvent) {
        boolean exportFormat = false;
        RoomKeyContent roomKeyContent = roomKeyEvent.toRoomKeyContent();

        String roomId = roomKeyContent.room_id;
        String sessionId = roomKeyContent.session_id;
        String sessionKey = roomKeyContent.session_key;
        String senderKey = roomKeyEvent.getSenderKey();
        Map<String, String> keysClaimed = new HashMap<>();
        List<String> forwarding_curve25519_key_chain = null;

        if (TextUtils.isEmpty(roomId) || TextUtils.isEmpty(sessionId) || TextUtils.isEmpty(sessionKey)) {
            Log.e(LOG_TAG, "## onRoomKeyEvent() :  Key event is missing fields");
            return;
        }

        if (TextUtils.equals(roomKeyEvent.getType(), CryptoEvent.EVENT_TYPE_FORWARDED_ROOM_KEY)) {
            Log.d(LOG_TAG, "## onRoomKeyEvent(), forward adding key : roomId " + roomId + " sessionId " + sessionId
                    + " sessionKey " + sessionKey); // from " + event);
            ForwardedRoomKeyContent forwardedRoomKeyContent = roomKeyEvent.toForwardedRoomKeyContent();

            if (null == forwardedRoomKeyContent.forwarding_curve25519_key_chain) {
                forwarding_curve25519_key_chain = new ArrayList<>();
            } else {
                forwarding_curve25519_key_chain = new ArrayList<>(forwardedRoomKeyContent.forwarding_curve25519_key_chain);
            }

            forwarding_curve25519_key_chain.add(senderKey);

            exportFormat = true;
            senderKey = forwardedRoomKeyContent.sender_key;
            if (null == senderKey) {
                Log.e(LOG_TAG, "## onRoomKeyEvent() : forwarded_room_key event is missing sender_key field");
                return;
            }

            String ed25519Key = forwardedRoomKeyContent.sender_claimed_ed25519_key;

            if (null == ed25519Key) {
                Log.e(LOG_TAG, "## forwarded_room_key_event is missing sender_claimed_ed25519_key field");
                return;
            }

            keysClaimed.put("ed25519", ed25519Key);
        } else {
            Log.d(LOG_TAG, "## onRoomKeyEvent(), Adding key : roomId " + roomId + " sessionId " + sessionId
                    + " sessionKey " + sessionKey); // from " + event);

            if (null == senderKey) {
                Log.e(LOG_TAG, "## onRoomKeyEvent() : key event has no sender key (not encrypted?)");
                return;
            }

            // inherit the claimed ed25519 key from the setup message
            keysClaimed = roomKeyEvent.getKeysClaimed();
        }

        boolean added = mOlmDevice.addInboundGroupSession(sessionId, sessionKey, roomId, senderKey, forwarding_curve25519_key_chain, keysClaimed, exportFormat);

        if (added) {
            mSession.requireCrypto().getKeysBackup().maybeBackupKeys();

            RoomKeyRequestBody content = new RoomKeyRequestBody();

            content.algorithm = roomKeyContent.algorithm;
            content.roomId = roomKeyContent.room_id;
            content.sessionId = roomKeyContent.session_id;
            content.senderKey = senderKey;

            mSession.requireCrypto().cancelRoomKeyRequest(content);

            onNewSession(senderKey, sessionId);
        }
    }

    /**
     * Check if the some messages can be decrypted with a new session
     *
     * @param senderKey the session sender key
     * @param sessionId the session id
     */
    public void onNewSession(String senderKey, String sessionId) {
        String k = senderKey + "|" + sessionId;

        Map<String, List<CryptoEvent>> pending = mPendingEvents.get(k);

        if (null != pending) {
            // Have another go at decrypting events sent with this session.
            mPendingEvents.remove(k);

            Set<String> timelineIds = pending.keySet();

            for (String timelineId : timelineIds) {
                List<CryptoEvent> events = pending.get(timelineId);

                for (CryptoEvent event : events) {
                    MXEventDecryptionResult result = null;

                    try {
                        result = decryptEvent(event, TextUtils.isEmpty(timelineId) ? null : timelineId);
                    } catch (MXDecryptionException e) {
                        Log.e(LOG_TAG, "## onNewSession() : Still can't decrypt " + event.getEventId() + ". Error " + e.getMessage(), e);
                        event.setCryptoError(e.getCryptoError());
                    }

                    if (null != result) {
                        final CryptoEvent fEvent = event;
                        final MXEventDecryptionResult fResut = result;
                        mCrypto.getUIHandler().post(new Runnable() {
                            @Override
                            public void run() {
                                fEvent.setClearData(fResut);
                                mSession.getDataHandler().onEventDecrypted(fEvent);
                            }
                        });
                        Log.d(LOG_TAG, "## onNewSession() : successful re-decryption of " + event.getEventId());
                    }
                }
            }
        }
    }

    @Override
    public boolean hasKeysForKeyRequest(IncomingRoomKeyRequest request) {
        return (null != request)
                && (null != request.mRequestBody)
                && mOlmDevice.hasInboundSessionKeys(request.mRequestBody.roomId, request.mRequestBody.senderKey, request.mRequestBody.sessionId);
    }

    @Override
    public void shareKeysWithDevice(final IncomingRoomKeyRequest request) {
        // sanity checks
        if ((null == request) || (null == request.mRequestBody)) {
            return;
        }

        final String userId = request.mUserId;

        mCrypto.getDeviceList().downloadKeys(Arrays.asList(userId), false, new ApiCallback<MXUsersDevicesMap<MXDeviceInfo>>() {
            @Override
            public void onSuccess(MXUsersDevicesMap<MXDeviceInfo> devicesMap) {
                final String deviceId = request.mDeviceId;
                final MXDeviceInfo deviceInfo = mCrypto.mCryptoStore.getUserDevice(deviceId, userId);

                if (null != deviceInfo) {
                    final RoomKeyRequestBody body = request.mRequestBody;

                    Map<String, List<MXDeviceInfo>> devicesByUser = new HashMap<>();
                    devicesByUser.put(userId, new ArrayList<>(Arrays.asList(deviceInfo)));

                    mCrypto.ensureOlmSessionsForDevices(devicesByUser, new ApiCallback<MXUsersDevicesMap<MXOlmSessionResult>>() {
                        @Override
                        public void onSuccess(MXUsersDevicesMap<MXOlmSessionResult> map) {
                            MXOlmSessionResult olmSessionResult = map.getObject(deviceId, userId);

                            if ((null == olmSessionResult) || (null == olmSessionResult.mSessionId)) {
                                // no session with this device, probably because there
                                // were no one-time keys.
                                //
                                // ensureOlmSessionsForUsers has already done the logging,
                                // so just skip it.
                                return;
                            }

                            Log.d(LOG_TAG, "## shareKeysWithDevice() : sharing keys for session " + body.senderKey + "|" + body.sessionId
                                    + " with device " + userId + ":" + deviceId);

                            MXOlmInboundGroupSession2 inboundGroupSession = mCrypto
                                    .getOlmDevice().getInboundGroupSession(body.sessionId, body.senderKey, body.roomId);

                            Map<String, Object> payloadJson = new HashMap<>();
                            payloadJson.put("type", CryptoEvent.EVENT_TYPE_FORWARDED_ROOM_KEY);
                            payloadJson.put("content", inboundGroupSession.exportKeys());

                            EncryptedMessage encodedPayload = mCrypto.encryptMessage(payloadJson, Arrays.asList(deviceInfo));
                            MXUsersDevicesMap<Object> sendToDeviceMap = new MXUsersDevicesMap<>();
                            sendToDeviceMap.setObject(encodedPayload, userId, deviceId);

                            Log.d(LOG_TAG, "## shareKeysWithDevice() : sending to " + userId + ":" + deviceId);
                            mCrypto.getCryptoRestClient().sendToDevice(CryptoEvent.EVENT_TYPE_MESSAGE_ENCRYPTED, sendToDeviceMap, new ApiCallback<Void>() {
                                @Override
                                public void onSuccess(Void info) {
                                    Log.d(LOG_TAG, "## shareKeysWithDevice() : sent to " + userId + ":" + deviceId);
                                }

                                @Override
                                public void onNetworkError(Exception e) {
                                    Log.e(LOG_TAG, "## shareKeysWithDevice() : sendToDevice " + userId + ":" + deviceId + " failed " + e.getMessage(), e);
                                }

                                @Override
                                public void onMatrixError(MatrixError e) {
                                    Log.e(LOG_TAG, "## shareKeysWithDevice() : sendToDevice " + userId + ":" + deviceId + " failed " + e.getMessage());
                                }

                                @Override
                                public void onUnexpectedError(Exception e) {
                                    Log.e(LOG_TAG, "## shareKeysWithDevice() : sendToDevice " + userId + ":" + deviceId + " failed " + e.getMessage(), e);
                                }
                            });
                        }

                        @Override
                        public void onNetworkError(Exception e) {
                            Log.e(LOG_TAG, "## shareKeysWithDevice() : ensureOlmSessionsForDevices " + userId + ":" + deviceId + " failed "
                                    + e.getMessage(), e);
                        }

                        @Override
                        public void onMatrixError(MatrixError e) {
                            Log.e(LOG_TAG, "## shareKeysWithDevice() : ensureOlmSessionsForDevices " + userId + ":" + deviceId + " failed " + e.getMessage());
                        }

                        @Override
                        public void onUnexpectedError(Exception e) {
                            Log.e(LOG_TAG, "## shareKeysWithDevice() : ensureOlmSessionsForDevices " + userId + ":" + deviceId + " failed "
                                    + e.getMessage(), e);
                        }
                    });
                } else {
                    Log.e(LOG_TAG, "## shareKeysWithDevice() : ensureOlmSessionsForDevices " + userId + ":" + deviceId + " not found");
                }
            }

            @Override
            public void onNetworkError(Exception e) {
                Log.e(LOG_TAG, "## shareKeysWithDevice() : downloadKeys " + userId + " failed " + e.getMessage(), e);
            }

            @Override
            public void onMatrixError(MatrixError e) {
                Log.e(LOG_TAG, "## shareKeysWithDevice() : downloadKeys " + userId + " failed " + e.getMessage());
            }

            @Override
            public void onUnexpectedError(Exception e) {
                Log.e(LOG_TAG, "## shareKeysWithDevice() : downloadKeys " + userId + " failed " + e.getMessage(), e);
            }
        });
    }
}
