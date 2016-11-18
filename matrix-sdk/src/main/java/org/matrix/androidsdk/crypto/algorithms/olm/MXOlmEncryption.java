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

import android.text.TextUtils;

import com.google.gson.JsonElement;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.crypto.MXCrypto;
import org.matrix.androidsdk.crypto.algorithms.IMXEncrypting;
import org.matrix.androidsdk.crypto.data.MXDeviceInfo;
import org.matrix.androidsdk.crypto.data.MXOlmSessionResult;
import org.matrix.androidsdk.crypto.data.MXUsersDevicesMap;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.util.JsonUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

public class MXOlmEncryption implements IMXEncrypting {

    private MXCrypto mCrypto;

    @Override
    public void initWithMatrixSession(MXSession matrixSession, String roomId) {
        mCrypto = matrixSession.getCrypto();
    }

    @Override
    public void encryptEventContent(final JsonElement eventContent, final String eventType, final Room room, final ApiCallback<JsonElement> callback) {
        // pick the list of recipients based on the membership list.
        //
        // TODO: there is a race condition here! What if a new user turns up
        // just as you are sending a secret message?
        final ArrayList<String> users = new ArrayList<>();

        Collection<RoomMember> joinedMembers = room.getJoinedMembers();

        for(RoomMember m : joinedMembers) {
            users.add(m.getUserId());
        }

        ensureSession(users, new ApiCallback<Void>() {
                    @Override
                    public void onSuccess(Void info) {
                        ArrayList<MXDeviceInfo> deviceInfos = new ArrayList<>();

                        for(String userId : users) {
                            List<MXDeviceInfo> devices = mCrypto.storedDevicesForUser(userId);

                            if (null != devices) {
                                for (MXDeviceInfo device : devices) {
                                    String key = device.identityKey();

                                    if (TextUtils.equals(key, mCrypto.getOlmDevice().getDeviceCurve25519Key())) {
                                        // Don't bother setting up session to ourself
                                        continue;
                                    }

                                    if (device.mVerified == MXDeviceInfo.DEVICE_VERIFICATION_BLOCKED) {
                                        // Don't bother setting up sessions with blocked users
                                        continue;
                                    }

                                    deviceInfos.add(device);
                                }
                            }
                        }

                        HashMap<String, Object> messageMap = new HashMap<>();
                        messageMap.put("room_id", room.getRoomId());
                        messageMap.put("type", eventType);
                        messageMap.put("content", eventContent);

                        mCrypto.encryptMessage(messageMap, deviceInfos);
                        mCrypto.mCryptoStore.flushSessions();
                        callback.onSuccess(JsonUtils.getGson(false).toJsonTree(messageMap));
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
                }
        );
    }

    @Override
    public void onRoomMembership(Event event, RoomMember member, String oldMembership) {
            // No impact for olm
    }

    @Override
    public void onDeviceVerificationStatusUpdate(String userId, String deviceId) {
        // No impact for olm
    }

    @Override
    public void onNewDevice(String deviceId, String userId) {
            // No impact for olm
    }

    /**
     * Ensure that the session
     * @param users the user ids list
     * @param callback the asynchronous callback
     */
    private void ensureSession(final List<String> users, final ApiCallback<Void> callback) {
        mCrypto.downloadKeys(users, true, new ApiCallback<MXUsersDevicesMap<MXDeviceInfo>>() {
            @Override
            public void onSuccess(MXUsersDevicesMap<MXDeviceInfo> info) {
                mCrypto.ensureOlmSessionsForUsers(users, new ApiCallback<MXUsersDevicesMap<MXOlmSessionResult>>() {
                    @Override
                    public void onSuccess(MXUsersDevicesMap<MXOlmSessionResult> result) {
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
}
