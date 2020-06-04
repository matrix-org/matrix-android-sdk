/*
 * Copyright 2020 New Vector Ltd
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

package org.matrix.androidsdk.crypto.internal.otk

import org.matrix.androidsdk.core.Log
import org.matrix.androidsdk.core.callback.ApiCallback
import org.matrix.androidsdk.core.model.MatrixError
import org.matrix.androidsdk.crypto.data.MXDeviceInfo
import org.matrix.androidsdk.crypto.data.MXKey
import org.matrix.androidsdk.crypto.data.MXOlmSessionResult
import org.matrix.androidsdk.crypto.data.MXUsersDevicesMap
import org.matrix.androidsdk.crypto.internal.MXCryptoImpl

/**
 * This class keep the several requests to get OneTimeKeys.
 * Several requests may ask for the same deviceId, so this class spread the answer to all the caller
 */
class OneTimeKeysResponseHandler(
        private val mxCryptoImpl: MXCryptoImpl
) {
    companion object {
        private const val LOG_TAG = "OneTimeKeysResponseHandler"
    }

    data class PendingRequest(
            val devicesByUser: Map<String, List<MXDeviceInfo>>,
            /**
             * The callback which will be called when the result are full
             */
            val callback: ApiCallback<MXUsersDevicesMap<MXOlmSessionResult>>,
            val aggregatedResult: MXUsersDevicesMap<MXOlmSessionResult>
    )

    private val pendingRequests = mutableListOf<PendingRequest>()

    fun addPendingRequest(pendingRequest: PendingRequest) {
        pendingRequests.add(pendingRequest)
    }

    fun removePendingRequest(pendingRequest: PendingRequest) {
        pendingRequests.remove(pendingRequest)
    }

    /**
     * Return the list of device identity keys we are establishing an olm session with.
     */
    fun getDeviceIdentityKeysWithOlmSessionsInProgress(): Set<String> {
        return pendingRequests.toList()
                .map { it.devicesByUser }
                .flatMap { it.values }
                .flatten()
                .map { it.identityKey() }
                .toSet()
    }

    fun onOtkRetrieved(oneTimeKeys: MXUsersDevicesMap<MXKey>) {
        Log.d(LOG_TAG, "## claimOneTimeKeysForUsersDevices() : keysClaimResponse.oneTimeKeys: $oneTimeKeys")

        val cacheOfSessionId = mutableMapOf<MXDeviceInfo, String?>()

        // Spread the result to all pending requests
        pendingRequests.toList().forEach { pendingRequest ->
            pendingRequest.aggregatedResult.map.keys.forEach { userId ->
                pendingRequest.aggregatedResult.map[userId]!!.keys.forEach { deviceId ->
                    val result = pendingRequest.aggregatedResult.getObject(deviceId, userId)!!

                    if (result.mSessionId != null) {
                        // We already have a result for this device
                        Unit
                    } else {
                        // Check in cache
                        val sessionId = cacheOfSessionId.getOrPut(result.mDevice) {
                            val key = oneTimeKeys.getObject(deviceId, userId)
                                    ?.takeIf { it.type == MXKey.KEY_SIGNED_CURVE_25519_TYPE }

                            if (key == null) {
                                Log.d(LOG_TAG, "## ensureOlmSessionsForDevices() : No one-time keys " + MXKey.KEY_SIGNED_CURVE_25519_TYPE
                                        + " for device " + userId + " : " + deviceId)
                                null
                            } else {
                                mxCryptoImpl.verifyKeyAndStartSession(key, userId, result.mDevice)
                            }
                        }

                        result.mSessionId = sessionId
                        result.hasResult = true
                    }
                }
            }
        }

        // Ok, call the callback if all sessions are established
        if (!mxCryptoImpl.hasBeenReleased()) {
            pendingRequests.toList().forEach { pendingRequest ->
                if (pendingRequest.aggregatedResult.map.all { a -> a.value.all { b -> b.value.hasResult } }) {
                    mxCryptoImpl.getUIHandler().post {
                        pendingRequest.callback.onSuccess(pendingRequest.aggregatedResult)

                        // Also remove the pending request
                        pendingRequests.remove(pendingRequest)
                    }
                }
            }
        }
    }

    fun onNetworkError(e: Exception, usersDevicesToClaim: MXUsersDevicesMap<String>) {
        onError(usersDevicesToClaim) {
            it.callback.onNetworkError(e)
        }
    }

    fun onMatrixError(e: MatrixError, usersDevicesToClaim: MXUsersDevicesMap<String>) {
        onError(usersDevicesToClaim) {
            it.callback.onMatrixError(e)
        }
    }

    fun onUnexpectedError(e: Exception, usersDevicesToClaim: MXUsersDevicesMap<String>) {
        onError(usersDevicesToClaim) {
            it.callback.onUnexpectedError(e)
        }
    }

    private fun onError(usersDevicesToClaim: MXUsersDevicesMap<String>, callCallback: ((PendingRequest) -> Unit)) {
        // Spread the failure to all the pending requests which are waiting for this session
        val concernedPendingRequests = mutableSetOf<PendingRequest>()

        pendingRequests.toList().forEach mainForEach@{ pendingRequest ->
            pendingRequest.aggregatedResult.map.keys.forEach { userId ->
                pendingRequest.aggregatedResult.map[userId]!!.keys.forEach { deviceId ->
                    if (usersDevicesToClaim.getObject(deviceId, userId) == MXKey.KEY_SIGNED_CURVE_25519_TYPE) {
                        // This pending request was waiting for a key
                        concernedPendingRequests.add(pendingRequest)
                        return@mainForEach
                    }
                }
            }
        }

        if (!mxCryptoImpl.hasBeenReleased()) {
            concernedPendingRequests.forEach {
                mxCryptoImpl.getUIHandler().post {
                    callCallback.invoke(it)
                }
            }
        }

        // Also remove the pending requests
        pendingRequests.removeAll(concernedPendingRequests)
    }
}