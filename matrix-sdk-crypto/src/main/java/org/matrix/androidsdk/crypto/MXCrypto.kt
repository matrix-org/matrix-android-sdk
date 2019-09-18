/*
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

package org.matrix.androidsdk.crypto

import android.os.Handler
import androidx.annotation.VisibleForTesting
import com.google.gson.JsonElement
import org.matrix.androidsdk.core.callback.ApiCallback
import org.matrix.androidsdk.core.listeners.ProgressListener
import org.matrix.androidsdk.crypto.cryptostore.IMXCryptoStore
import org.matrix.androidsdk.crypto.data.*
import org.matrix.androidsdk.crypto.interfaces.CryptoEvent
import org.matrix.androidsdk.crypto.interfaces.CryptoRoom
import org.matrix.androidsdk.crypto.interfaces.CryptoSyncResponse
import org.matrix.androidsdk.crypto.keysbackup.KeysBackup
import org.matrix.androidsdk.crypto.model.crypto.RoomKeyRequestBody
import org.matrix.androidsdk.crypto.rest.CryptoRestClient
import org.matrix.androidsdk.crypto.verification.VerificationManager
import org.matrix.androidsdk.network.NetworkConnectivityReceiver

interface MXCrypto {
    fun start(isInitialSync: Boolean, aCallback: ApiCallback<Void>)

    fun isStarted(): Boolean

    fun isStarting(): Boolean

    fun isCorrupted(): Boolean

    fun close()

    val keysBackup: KeysBackup

    val shortCodeVerificationManager: VerificationManager

    fun getUIHandler(): Handler
    fun getDecryptingThreadHandler(): Handler

    fun getCryptoRestClient(): CryptoRestClient

    fun encryptEventContent(eventContent: JsonElement,
                            eventType: String,
                            room: CryptoRoom,
                            callback: ApiCallback<MXEncryptEventContentResult>)

    fun onSyncCompleted(syncResponse: CryptoSyncResponse, fromToken: String, isCatchingUp: Boolean)

    fun getOlmDevice(): MXOlmDevice?

    fun setNetworkConnectivityReceiver(networkConnectivityReceiver: NetworkConnectivityReceiver)

    fun checkUnknownDevices(userIds: List<String>, callback: ApiCallback<Void>)

    fun getGlobalBlacklistUnverifiedDevices(callback: ApiCallback<Boolean>)

    fun warnOnUnknownDevices(): Boolean

    @Throws(MXDecryptionException::class)
    fun decryptEvent(event: CryptoEvent, timelineId: String?): MXEventDecryptionResult

    fun resetReplayAttackCheckInTimeline(timelineId: String)

    fun isRoomBlacklistUnverifiedDevices(roomId: String, callback: ApiCallback<Boolean>)

    fun setWarnOnUnknownDevices(warn: Boolean)

    fun setDeviceVerification(verificationStatus: Int, deviceId: String, userId: String, callback: ApiCallback<Void>)

    fun getUserDevices(userId: String): MutableList<MXDeviceInfo>

    fun getDeviceList(): MXDeviceList

    fun setDevicesKnown(devices: List<MXDeviceInfo>, callback: ApiCallback<Void>?)

    fun deviceWithIdentityKey(senderKey: String, algorithm: String): MXDeviceInfo?

    val myDevice: MXDeviceInfo

    fun setGlobalBlacklistUnverifiedDevices(block: Boolean, callback: ApiCallback<Void>)

    fun setRoomUnBlacklistUnverifiedDevices(roomId: String, callback: ApiCallback<Void>)

    fun getDeviceTrackingStatus(userId: String): Int

    fun importRoomKeys(roomKeysAsArray: ByteArray, password: String, progressListener: ProgressListener?, apiCallback: ApiCallback<ImportRoomKeysResult>)

    fun exportRoomKeys(password: String, callback: ApiCallback<ByteArray>)

    fun setRoomBlacklistUnverifiedDevices(roomId: String, apiCallback: ApiCallback<Void>)

    fun getDeviceInfo(userId: String, deviceId: String?, callback: ApiCallback<MXDeviceInfo>)

    fun reRequestRoomKeyForEvent(event: CryptoEvent)

    fun cancelRoomKeyRequest(requestBody: RoomKeyRequestBody)

    fun addRoomKeysRequestListener(listener: RoomKeysRequestListener)

    @VisibleForTesting
    val cryptoStore: IMXCryptoStore

    @VisibleForTesting
    fun ensureOlmSessionsForUsers(users: List<String>, callback: ApiCallback<MXUsersDevicesMap<MXOlmSessionResult>>)
}
