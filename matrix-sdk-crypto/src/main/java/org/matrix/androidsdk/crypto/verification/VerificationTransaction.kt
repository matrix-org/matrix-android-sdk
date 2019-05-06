/*
 * Copyright 2019 New Vector Ltd
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
package org.matrix.androidsdk.crypto.verification

import org.matrix.androidsdk.crypto.interfaces.CryptoSession
import org.matrix.androidsdk.crypto.rest.model.crypto.SendToDeviceObject

/**
 * Generic interactive key verification transaction
 */
abstract class VerificationTransaction(val transactionId: String,
                                       val otherUserId: String,
                                       var otherDeviceId: String? = null,
                                       val isIncoming: Boolean) {

    interface Listener {
        fun transactionUpdated(tx: VerificationTransaction)
    }

    protected var listeners = ArrayList<Listener>()

    fun addListener(listener: Listener) {
        if (!listeners.contains(listener)) listeners.add(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    abstract fun acceptToDeviceEvent(session: CryptoSession, senderId: String, event: SendToDeviceObject)

    abstract fun cancel(session: CryptoSession, code: CancelCode)
}
