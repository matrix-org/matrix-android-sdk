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

package org.matrix.androidsdk.crypto.cryptostore;

import android.content.Context;

import androidx.annotation.Nullable;

import org.jetbrains.annotations.NotNull;
import org.matrix.androidsdk.crypto.IncomingRoomKeyRequest;
import org.matrix.androidsdk.crypto.OutgoingRoomKeyRequest;
import org.matrix.androidsdk.crypto.cryptostore.db.model.KeysBackupDataEntity;
import org.matrix.androidsdk.crypto.data.MXDeviceInfo;
import org.matrix.androidsdk.crypto.data.MXOlmInboundGroupSession2;
import org.matrix.androidsdk.crypto.data.MXOlmSession;
import org.matrix.androidsdk.crypto.model.crypto.RoomKeyRequestBody;
import org.matrix.androidsdk.rest.model.login.Credentials;
import org.matrix.olm.OlmAccount;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * the crypto data store
 */
public interface IMXCryptoStore {
    /**
     * Init a crypto store for the passed credentials.
     *
     * @param context     the application context
     * @param credentials the credentials of the account.
     */
    void initWithCredentials(Context context, Credentials credentials);

    /**
     * @return if the store is corrupted.
     */
    boolean isCorrupted();

    /**
     * Indicate if the store contains data for the passed account.
     *
     * @return true means that the user enabled the crypto in a previous session
     */
    boolean hasData();

    /**
     * Delete the crypto store for the passed credentials.
     */
    void deleteStore();

    /**
     * open any existing crypto store
     */
    void open();

    /**
     * Close the store
     */
    void close();

    /**
     * Store the device id.
     *
     * @param deviceId the device id
     */
    void storeDeviceId(String deviceId);

    /**
     * @return the device id
     */
    String getDeviceId();

    /**
     * Store the end to end account for the logged-in user.
     *
     * @param account the account to save
     */
    void storeAccount(OlmAccount account);

    /**
     * @return the olm account
     */
    OlmAccount getAccount();

    /**
     * Store a device for a user.
     *
     * @param userId the user's id.
     * @param device the device to store.
     */
    void storeUserDevice(String userId, MXDeviceInfo device);

    /**
     * Retrieve a device for a user.
     *
     * @param deviceId the device id.
     * @param userId   the user's id.
     * @return the device
     */
    MXDeviceInfo getUserDevice(String deviceId, String userId);

    /**
     * Retrieve a device by its identity key.
     *
     * @param identityKey the device identity key (`MXDeviceInfo.identityKey`)
     * @return the device or null if not found
     */
    @Nullable
    MXDeviceInfo deviceWithIdentityKey(String identityKey);

    /**
     * Store the known devices for a user.
     *
     * @param userId  The user's id.
     * @param devices A map from device id to 'MXDevice' object for the device.
     */
    void storeUserDevices(String userId, Map<String, MXDeviceInfo> devices);

    /**
     * Retrieve the known devices for a user.
     *
     * @param userId The user's id.
     * @return The devices map if some devices are known, else null
     */
    Map<String, MXDeviceInfo> getUserDevices(String userId);

    /**
     * Store the crypto algorithm for a room.
     *
     * @param roomId    the id of the room.
     * @param algorithm the algorithm.
     */
    void storeRoomAlgorithm(String roomId, String algorithm);

    /**
     * Provides the algorithm used in a dedicated room.
     *
     * @param roomId the room id
     * @return the algorithm, null is the room is not encrypted
     */
    String getRoomAlgorithm(String roomId);

    /**
     * Store a session between the logged-in user and another device.
     *
     * @param session   the end-to-end session.
     * @param deviceKey the public key of the other device.
     */
    void storeSession(MXOlmSession session, String deviceKey);

    /**
     * Retrieve the end-to-end session ids between the logged-in user and another
     * device.
     *
     * @param deviceKey the public key of the other device.
     * @return A set of sessionId, or null if device is not known
     */
    @Nullable
    Set<String> getDeviceSessionIds(String deviceKey);

    /**
     * Retrieve an end-to-end session between the logged-in user and another
     * device.
     *
     * @param sessionId the session Id.
     * @param deviceKey the public key of the other device.
     * @return The Base64 end-to-end session, or null if not found
     */
    @Nullable
    MXOlmSession getDeviceSession(String sessionId, String deviceKey);

    /**
     * Retrieve the last used sessionId, regarding `lastReceivedMessageTs`, or null if no session exist
     *
     * @param deviceKey the public key of the other device.
     * @return last used sessionId, or null if not found
     */
    @Nullable
    String getLastUsedSessionId(String deviceKey);

    /**
     * Store inbound group sessions.
     *
     * @param sessions the inbound group sessions to store.
     */
    void storeInboundGroupSessions(@NotNull List<MXOlmInboundGroupSession2> sessions);

    /**
     * Retrieve an inbound group session.
     *
     * @param sessionId the session identifier.
     * @param senderKey the base64-encoded curve25519 key of the sender.
     * @return an inbound group session.
     */
    @Nullable
    MXOlmInboundGroupSession2 getInboundGroupSession(String sessionId, String senderKey);

    /**
     * Retrieve the known inbound group sessions.
     *
     * @return the list of all known group sessions, to export them.
     */
    List<MXOlmInboundGroupSession2> getInboundGroupSessions();

    /**
     * Remove an inbound group session
     *
     * @param sessionId the session identifier.
     * @param senderKey the base64-encoded curve25519 key of the sender.
     */
    void removeInboundGroupSession(String sessionId, String senderKey);

    /* ==========================================================================================
     * Keys backup
     * ========================================================================================== */

    /**
     * Mark all inbound group sessions as not backed up.
     */
    void resetBackupMarkers();

    /**
     * Mark inbound group sessions as backed up on the user homeserver.
     *
     * @param sessions the sessions
     */
    void markBackupDoneForInboundGroupSessions(@NotNull List<MXOlmInboundGroupSession2> sessions);

    /**
     * Retrieve inbound group sessions that are not yet backed up.
     *
     * @param limit the maximum number of sessions to return.
     * @return an array of non backed up inbound group sessions.
     */
    List<MXOlmInboundGroupSession2> inboundGroupSessionsToBackup(int limit);

    /**
     * Number of stored inbound group sessions.
     *
     * @param onlyBackedUp if true, count only session marked as backed up.
     * @return a count.
     */
    int inboundGroupSessionsCount(boolean onlyBackedUp);

    /**
     * Set the global override for whether the client should ever send encrypted
     * messages to unverified devices.
     * If false, it can still be overridden per-room.
     * If true, it overrides the per-room settings.
     *
     * @param block true to unilaterally blacklist all
     */
    void setGlobalBlacklistUnverifiedDevices(boolean block);

    /**
     * @return true to unilaterally blacklist all unverified devices.
     */
    boolean getGlobalBlacklistUnverifiedDevices();

    /**
     * Updates the rooms ids list in which the messages are not encrypted for the unverified devices.
     *
     * @param roomIds the room ids list
     */
    void setRoomsListBlacklistUnverifiedDevices(List<String> roomIds);

    /**
     * Provides the rooms ids list in which the messages are not encrypted for the unverified devices.
     *
     * @return the room Ids list
     */
    List<String> getRoomsListBlacklistUnverifiedDevices();

    /**
     * Set the current keys backup version
     *
     * @param keyBackupVersion the keys backup version or null to delete it
     */
    void setKeyBackupVersion(@Nullable String keyBackupVersion);

    /**
     * Get the current keys backup version
     */
    @Nullable
    String getKeyBackupVersion();

    /**
     * Set the keys backup local data
     *
     * @param keysBackupData the keys backup local data, or null to erase data
     */
    void setKeysBackupData(@Nullable KeysBackupDataEntity keysBackupData);

    /**
     * Get the current keys backup local data
     */
    @Nullable
    KeysBackupDataEntity getKeysBackupData();

    /**
     * @return the devices statuses map (userId -> tracking status)
     */
    Map<String, Integer> getDeviceTrackingStatuses();

    /**
     * Save the device statuses
     *
     * @param deviceTrackingStatuses the device tracking statuses
     */
    void saveDeviceTrackingStatuses(Map<String, Integer> deviceTrackingStatuses);

    /**
     * Get the tracking status of a specified userId devices.
     *
     * @param userId       the user id
     * @param defaultValue the default value
     * @return the tracking status
     */
    int getDeviceTrackingStatus(String userId, int defaultValue);

    /**
     * Look for an existing outgoing room key request, and if none is found, return null
     *
     * @param requestBody the request body
     * @return an OutgoingRoomKeyRequest instance or null
     */
    @Nullable
    OutgoingRoomKeyRequest getOutgoingRoomKeyRequest(RoomKeyRequestBody requestBody);

    /**
     * Look for an existing outgoing room key request, and if none is found, add a new one.
     *
     * @param request the request
     * @return either the same instance as passed in, or the existing one.
     */
    @Nullable
    OutgoingRoomKeyRequest getOrAddOutgoingRoomKeyRequest(OutgoingRoomKeyRequest request);

    /**
     * Look for room key requests by state.
     *
     * @param states the states
     * @return an OutgoingRoomKeyRequest or null
     */
    @Nullable
    OutgoingRoomKeyRequest getOutgoingRoomKeyRequestByState(Set<OutgoingRoomKeyRequest.RequestState> states);

    /**
     * Update an existing outgoing request.
     *
     * @param request the request
     */
    void updateOutgoingRoomKeyRequest(OutgoingRoomKeyRequest request);

    /**
     * Delete an outgoing room key request.
     *
     * @param transactionId the transaction id.
     */
    void deleteOutgoingRoomKeyRequest(String transactionId);

    /**
     * Store an incomingRoomKeyRequest instance
     *
     * @param incomingRoomKeyRequest the incoming key request
     */
    void storeIncomingRoomKeyRequest(IncomingRoomKeyRequest incomingRoomKeyRequest);

    /**
     * Delete an incomingRoomKeyRequest instance
     *
     * @param incomingRoomKeyRequest the incoming key request
     */
    void deleteIncomingRoomKeyRequest(IncomingRoomKeyRequest incomingRoomKeyRequest);

    /**
     * Search an IncomingRoomKeyRequest
     *
     * @param userId    the user id
     * @param deviceId  the device id
     * @param requestId the request id
     * @return an IncomingRoomKeyRequest if it exists, else null
     */
    IncomingRoomKeyRequest getIncomingRoomKeyRequest(String userId, String deviceId, String requestId);

    /**
     * @return the pending IncomingRoomKeyRequest requests
     */
    List<IncomingRoomKeyRequest> getPendingIncomingRoomKeyRequests();
}
