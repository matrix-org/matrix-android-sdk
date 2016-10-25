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

package org.matrix.androidsdk.crypto.algorithms.megolm;

import android.text.TextUtils;
import android.util.Log;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.crypto.MXCrypto;
import org.matrix.androidsdk.crypto.MXCryptoAlgorithms;
import org.matrix.androidsdk.crypto.MXOlmDevice;
import org.matrix.androidsdk.crypto.algorithms.IMXEncrypting;
import org.matrix.androidsdk.crypto.data.MXDeviceInfo;
import org.matrix.androidsdk.crypto.data.MXOlmSessionResult;
import org.matrix.androidsdk.crypto.data.MXQueuedEncryption;
import org.matrix.androidsdk.crypto.data.MXUsersDevicesMap;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.util.JsonUtils;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class MXMegolmEncryption implements IMXEncrypting {

    private static final String LOG_TAG = "MXMegolmEncryption";

    private MXSession mSession;
    private MXCrypto mCrypto;

    // The id of the room we will be sending to.
    private String mRoomId;

    private String mdeviceId;

    private boolean mPrepOperationIsProgress;

    private boolean mShareOperationIsProgress;

    private String mOutboundSessionId;

    private boolean mDiscardNewSession;

    // Devices which have joined since we last sent a message.
    // userId -> {deviceId -> @(YES)}
    // If deviceId is "*", share keys with all devices of the user.
    private MXUsersDevicesMap<Boolean> mDevicesPendingKeyShare;

    private ArrayList<MXQueuedEncryption> mPendingEncryptions;

    @Override
    public void initWithMatrixSession(MXSession matrixSession, String roomId) {

        mSession = matrixSession;
        mCrypto = matrixSession.getCrypto();

        mRoomId = roomId;
        mdeviceId = matrixSession.getCredentials().deviceId;

        mPendingEncryptions = new ArrayList<>();
    }

    @Override
    public void encryptEventContent(JsonElement eventContent, String eventType, Room room, ApiCallback<JsonElement> callback) {
        // Queue the encryption request
        // It will be processed when everything is set up
        MXQueuedEncryption queuedEncryption = new MXQueuedEncryption();

        queuedEncryption.mEventContent = eventContent;
        queuedEncryption.mEventType = eventType;
        queuedEncryption.mApiCallback = callback;

        mPendingEncryptions.add(queuedEncryption);

        ensureOutboundSessionInRoom(room, new ApiCallback<String>() {
            @Override
            public void onSuccess(String sessionId) {
                mOutboundSessionId = sessionId;
                processPendingEncryptions();
            }

            @Override
            public void onNetworkError(Exception e) {
                for (MXQueuedEncryption queuedEncryption : mPendingEncryptions) {
                    queuedEncryption.mApiCallback.onNetworkError(e);
                }

                mPendingEncryptions.clear();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                for (MXQueuedEncryption queuedEncryption : mPendingEncryptions) {
                    queuedEncryption.mApiCallback.onMatrixError(e);
                }

                mPendingEncryptions.clear();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                for (MXQueuedEncryption queuedEncryption : mPendingEncryptions) {
                    queuedEncryption.mApiCallback.onUnexpectedError(e);
                }

                mPendingEncryptions.clear();
            }
        });
    }

    @Override
    public void onRoomMembership(Event event, RoomMember member, String oldMembership) {
        String newMembership = member.membership;

        if (TextUtils.equals(newMembership, RoomMember.MEMBERSHIP_JOIN)) {
            onNewRoomMember(member.getUserId());
            return;
        }

        if (TextUtils.equals(newMembership, RoomMember.MEMBERSHIP_INVITE) && !TextUtils.equals(oldMembership, RoomMember.MEMBERSHIP_JOIN)) {
            // We don't (yet) share keys with invited members, so nothing to do yet
            return;
        }

        // Otherwise we assume the user is leaving, and start a new outbound session.
        if (null != mOutboundSessionId) {
            Log.d(LOG_TAG, "## onRoomMembership() : Discarding outbound megolm session due to change in membership of " + member.getUserId() +  " "  + oldMembership + " -> " + newMembership);
        }

        if (mPrepOperationIsProgress) {
            Log.d(LOG_TAG, "## onRoomMembership() : Discarding as-yet-incomplete megolm session due to change in membership of " + member.getUserId() +  " "  + oldMembership + " -> " + newMembership);
            mDiscardNewSession = true;
        }

    }

    @Override
    public void onNewDevice(String deviceId, String userId) {
        Set<String> d = mDevicesPendingKeyShare.deviceIdsForUser(userId);
        ArrayList<String> deviceIds;

        if (null == d) {
            deviceIds = new ArrayList<>();
        } else {
            deviceIds = new ArrayList<>(d);
        }

        if ((deviceIds.size() == 1) && TextUtils.equals(deviceIds.get(0), "*")) {
            // We already want to share keys with all devices for this user
        } else {
            // Add the device to the list of devices to share keys with
            // The keys will be shared at the next encryption request
            mDevicesPendingKeyShare.setObject(true, userId, deviceId);
        }
    }

    /**
     * Prepare a bew session in a dedicated room
     * @param room the room
     * @param callback the asynchronous callback
     */
    private void prepareNewSessionInRoom(Room room, final ApiCallback<String> callback) {
        MXOlmDevice olmDevice = mCrypto.getOlmDevice();

        final String sessionId = olmDevice.createOutboundGroupSession();

        HashMap<String, String> keysClaimedMap = new HashMap<>();
        keysClaimedMap.put("ed25519", olmDevice.getDeviceEd25519Key());

        olmDevice.addInboundGroupSession(sessionId, olmDevice.sessionKeyForOutboundGroupSession(sessionId), mRoomId, olmDevice.getDeviceCurve25519Key(), keysClaimedMap);

        // We're going to share the key with all current members of the room,
        // so we can reset this.
        mDevicesPendingKeyShare = new MXUsersDevicesMap<>(null);

        final MXUsersDevicesMap<Boolean> shareMap = new MXUsersDevicesMap<>(null);

        Collection<RoomMember> joinedMembers = room.getJoinedMembers();

        for (RoomMember member : joinedMembers) {
            HashMap<String, Boolean> submap = new HashMap<>();
            submap.put("*", true);

            shareMap.setObjects(submap, member.getUserId());
        }

        mCrypto.downloadKeys(new ArrayList<>(shareMap.userIds()), false, new ApiCallback<MXUsersDevicesMap<MXDeviceInfo>>() {
            @Override
            public void onSuccess(MXUsersDevicesMap<MXDeviceInfo> info) {
                shareKey(sessionId, shareMap, new ApiCallback<Void>() {
                    @Override
                    public void onSuccess(Void info) {

                        if (mDiscardNewSession) {
                            // we've had cause to reset the session_id since starting this process.
                            // we'll use the current session for any currently pending events, but
                            // don't save it as the current _outboundSessionId, so that new events
                            // will use a new session.
                            Log.e(LOG_TAG, "## prepareNewSessionInRoom() : complete, but discarding");
                        } else {
                            mOutboundSessionId = sessionId;
                        }

                        mDiscardNewSession = false;

                        if (null != callback) {
                            callback.onSuccess(sessionId);
                        }
                    }

                    @Override
                    public void onNetworkError(Exception e) {
                        mDiscardNewSession = false;
                        if (null != callback) {
                            callback.onNetworkError(e);
                        }
                    }

                    @Override
                    public void onMatrixError(MatrixError e) {
                        mDiscardNewSession = false;
                        if (null != callback) {
                            callback.onMatrixError(e);
                        }
                    }

                    @Override
                    public void onUnexpectedError(Exception e) {
                        mDiscardNewSession = false;
                        if (null != callback) {
                            callback.onUnexpectedError(e);
                        }
                    }
                });
            }

            @Override
            public void onNetworkError(Exception e) {
                mDiscardNewSession = false;
                if (null != callback) {
                    callback.onNetworkError(e);
                }
            }

            @Override
            public void onMatrixError(MatrixError e) {
                mDiscardNewSession = false;
                if (null != callback) {
                    callback.onMatrixError(e);
                }
            }

            @Override
            public void onUnexpectedError(Exception e) {
                mDiscardNewSession = false;
                if (null != callback) {
                    callback.onUnexpectedError(e);
                }
            }
        });
    }

    /**
     * Ensure the outbound session
     * @param room the room
     * @param callback the asynchronous callback.
     */
    private void ensureOutboundSessionInRoom(Room room, final ApiCallback<String> callback) {
        if (mPrepOperationIsProgress) {
            // Prep already in progress
            return;
        }

        // Need to make a brand new session?
        if (TextUtils.isEmpty(mOutboundSessionId)) {
            mPrepOperationIsProgress = true;

            prepareNewSessionInRoom(room, new ApiCallback<String>() {
                @Override
                public void onSuccess(String sessionId) {
                    mPrepOperationIsProgress = false;
                    if (null != callback) {
                        callback.onSuccess(sessionId);
                    }
                }

                @Override
                public void onNetworkError(Exception e) {
                    mPrepOperationIsProgress = false;
                    if (null != callback) {
                        callback.onNetworkError(e);
                    }
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    mPrepOperationIsProgress = false;
                    if (null != callback) {
                        callback.onMatrixError(e);
                    }
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    mPrepOperationIsProgress = false;
                    if (null != callback) {
                        callback.onUnexpectedError(e);
                    }
                }
            });

            return;
        }

        if (mShareOperationIsProgress) {
            // Key share already in progress
            return;
        }

        // Prep already done, but check for new devices
        MXUsersDevicesMap<Boolean> shareMap = mDevicesPendingKeyShare;
        mDevicesPendingKeyShare = new MXUsersDevicesMap<>(null);

        Set<String> userIds = mDevicesPendingKeyShare.userIds();

        // Check each user is (still) a member of the room
        for (String userId : userIds) {
            RoomMember member = room.getMember(userId);

            if (!TextUtils.equals(member.membership, RoomMember.MEMBERSHIP_JOIN)) {
                shareMap.removeObjectsForUser(userId);
            }

        }

        mShareOperationIsProgress = true;

        shareKey(mOutboundSessionId, shareMap, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                mShareOperationIsProgress = false;
                if (null != callback) {
                    callback.onSuccess(null);
                }
            }

            @Override
            public void onNetworkError(Exception e) {
                mShareOperationIsProgress = false;
                if (null != callback) {
                    callback.onNetworkError(e);
                }
            }

            @Override
            public void onMatrixError(MatrixError e) {
                mShareOperationIsProgress = false;
                if (null != callback) {
                    callback.onMatrixError(e);
                }
            }

            @Override
            public void onUnexpectedError(Exception e) {
                mShareOperationIsProgress = false;
                if (null != callback) {
                    callback.onUnexpectedError(e);
                }
            }
        });
    }


    private void shareKey(final String sessionId, final MXUsersDevicesMap<Boolean>shareMap, final ApiCallback<Void> callback) {
        HashMap<String, Object> submap = new HashMap<>();
        submap.put("algorithm", MXCryptoAlgorithms.MXCRYPTO_ALGORITHM_MEGOLM);
        submap.put("room_id", mRoomId);
        submap.put("session_id", sessionId);
        submap.put("session_key", mCrypto.getOlmDevice().sessionKeyForOutboundGroupSession(sessionId));
        submap.put("chain_index", new ArrayList<>(mCrypto.getOlmDevice().messageIndexForOutboundGroupSession(sessionId)));

        final HashMap<String, Object> payload = new HashMap<>();
        payload.put("type", Event.EVENT_TYPE_ROOM_KEY);
        payload.put("content", submap);

        mCrypto.ensureOlmSessionsForUsers(new ArrayList<>(shareMap.userIds()), new ApiCallback<MXUsersDevicesMap<MXOlmSessionResult>>() {
            @Override
            public void onSuccess(MXUsersDevicesMap<MXOlmSessionResult> results) {
                MXUsersDevicesMap<Map<String, Object>> contentMap = new MXUsersDevicesMap<>(null);

                boolean haveTargets = false;
                Set<String> userIds = results.userIds();

                for (String userId : userIds) {
                    Set<String> set = shareMap.deviceIdsForUser(userId);
                    ArrayList<String> devicesToShareWith;

                    if (null == set) {
                        devicesToShareWith = new ArrayList<>();
                    } else {
                        devicesToShareWith = new ArrayList<>(set);
                    }

                    Set<String> deviceIdsForUser = results.deviceIdsForUser(userId);

                    for (String deviceID : deviceIdsForUser){
                        if ((devicesToShareWith.size() == 1) &&  TextUtils.equals(devicesToShareWith.get(0), "*")) {
                            // all devices
                        }
                        else if (devicesToShareWith.indexOf(deviceID) < 0)
                        {
                            // not a new device
                            continue;
                        }

                        MXOlmSessionResult sessionResult = results.objectForDevice(deviceID, userId);

                        if ((null == sessionResult) || (null == sessionResult.mSessionId)) {
                            // no session with this device, probably because there
                            // were no one-time keys.
                            //
                            // we could send them a to_device message anyway, as a
                            // signal that they have missed out on the key sharing
                            // message because of the lack of keys, but there's not
                            // much point in that really; it will mostly serve to clog
                            // up to_device inboxes.
                            //
                            // ensureOlmSessionsForUsers has already done the logging,
                            // so just skip it.
                            continue;
                        }

                        Log.e(LOG_TAG, "## shareKey() : Sharing keys with device " + userId + ":" + deviceID);

                        MXDeviceInfo deviceInfo = sessionResult.mDevice;

                        ArrayList<String> participantsKey = new ArrayList<>();
                        participantsKey.add(deviceInfo.identityKey());

                        contentMap.setObject(mCrypto.encryptMessage(payload, participantsKey), userId, deviceID);
                        haveTargets = true;
                    }
                }

                if (haveTargets) {
                    mSession.getCryptoRestClient().sendToDevice(Event.EVENT_TYPE_MESSAGE_ENCRYPTED, contentMap, new ApiCallback<Void>() {
                                @Override
                                public void onSuccess(Void info) {
                                    if (null != callback) {
                                        callback.onSuccess(null);
                                    }
                                }

                                @Override
                                public void onNetworkError(Exception e) {
                                    if (null != callback) {
                                        callback.onNetworkError(e);
                                    }
                                }

                                @Override
                                public void onMatrixError(MatrixError e) {
                                    if (null != callback) {
                                        callback.onMatrixError(e);
                                    }
                                }

                                @Override
                                public void onUnexpectedError(Exception e) {
                                    if (null != callback) {
                                        callback.onUnexpectedError(e);
                                    }
                                }
                            });
                } else {
                    if (null != callback) {
                        callback.onSuccess(null);
                    }
                }
            }

            @Override
            public void onNetworkError(Exception e) {
                if (null != callback) {
                    callback.onNetworkError(e);
                }
            }

            @Override
            public void onMatrixError(MatrixError e) {
                if (null != callback) {
                    callback.onMatrixError(e);
                }
            }

            @Override
            public void onUnexpectedError(Exception e) {
                if (null != callback) {
                    callback.onUnexpectedError(e);
                }
            }
        });
    }

    /**
     * Handle a new user joining a room.
     * @param userId the new member.
     */
    private void onNewRoomMember(String userId) {
        // Make sure we have a list of this user's devices. We are happy to use a
        // cached version here: we assume that if we already have a list of the
        // user's devices, then we already share an e2e room with them, which means
        // that they will have announced any new devices via an m.new_device.
        ArrayList<String> userIds = new ArrayList<>();
        userIds.add(userId);

        mCrypto.downloadKeys(userIds, false, null);

        // also flag this user up for needing a keyshare.
        mDevicesPendingKeyShare.setObject(true, userId, "*");
    }

    private void processPendingEncryptions() {
        // Everything is in place, encrypt all pending events
        for (MXQueuedEncryption queuedEncryption : mPendingEncryptions) {
            HashMap<String, Object> payloadJson = new HashMap<>();

            payloadJson.put("room_id", mRoomId);
            payloadJson.put("type", queuedEncryption.mEventType);
            payloadJson.put("content", queuedEncryption.mEventContent);

            String payloadString = JsonUtils.canonicalize(JsonUtils.getGson(false).toJsonTree(payloadJson)).toString();

            try {
                payloadString = URLEncoder.encode(payloadString, "utf-8");
            } catch (Exception e) {
                Log.e(LOG_TAG, "## processPendingEncryptionsWithError : RLEncoder.encode failed " + e.getMessage());
            }

            String ciphertext = mCrypto.getOlmDevice().encryptGroupMessage(mOutboundSessionId, payloadString);

            HashMap<String, Object> map = new HashMap<>();
            map.put("algorithm", MXCryptoAlgorithms.MXCRYPTO_ALGORITHM_MEGOLM);
            map.put("sender_key", mCrypto.getOlmDevice().getDeviceCurve25519Key());
            map.put("ciphertext", ciphertext);
            map.put("session_id", mOutboundSessionId);

            // Include our device ID so that recipients can send us a
            // m.new_device message if they don't have our session key.
            map.put("device_id", mdeviceId);

            queuedEncryption.mApiCallback.onSuccess(JsonUtils.getGson(false).toJsonTree(map));
        }
        mPendingEncryptions.clear();
    }
}
