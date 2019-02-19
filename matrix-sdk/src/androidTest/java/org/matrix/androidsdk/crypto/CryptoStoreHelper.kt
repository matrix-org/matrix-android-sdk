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

@file:Suppress("DEPRECATION")

package org.matrix.androidsdk.crypto

import android.support.test.InstrumentationRegistry
import org.matrix.androidsdk.data.cryptostore.IMXCryptoStore
import org.matrix.androidsdk.data.cryptostore.MXFileCryptoStore
import org.matrix.androidsdk.data.cryptostore.db.RealmCryptoStore
import org.matrix.androidsdk.rest.model.login.Credentials
import java.util.*

class CryptoStoreHelper {

    fun createStore(useRealm: Boolean): IMXCryptoStore {
        val context = InstrumentationRegistry.getContext()

        return (if (useRealm) RealmCryptoStore() else MXFileCryptoStore(false)).apply {
            initWithCredentials(context, createCredential())
        }
    }

    fun createCredential() = Credentials().apply {
        userId = "userId_" + Random().nextInt()
        deviceId = "deviceId_sample"
        // File store need a home server and a access token to create MetaData, do not ask me why
        homeServer = "http://matrix.org"
        accessToken = "access_token"
    }
}