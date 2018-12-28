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

package org.matrix.androidsdk.data.cryptostore.db

import io.realm.DynamicRealm
import io.realm.RealmMigration
import org.matrix.androidsdk.data.cryptostore.db.model.OlmSessionEntityFields
import org.matrix.androidsdk.util.Log

internal object RealmCryptoStoreMigration : RealmMigration {

    private const val LOG_TAG = "RealmCryptoStoreMigration"

    const val CRYPTO_STORE_SCHEMA_VERSION = 1L

    override fun migrate(realm: DynamicRealm, oldVersion: Long, newVersion: Long) {
        Log.d(LOG_TAG, "Migrating Realm Crypto from $oldVersion to $newVersion")

        if (oldVersion <= 0) {
            Log.d(LOG_TAG, "Step 0 -> 1")
            Log.d(LOG_TAG, "Add field lastReceivedMessageTs (Long) and set the value to 0")

            realm.schema.get("OlmSessionEntity")
                    ?.addField(OlmSessionEntityFields.LAST_RECEIVED_MESSAGE_TS, Long::class.java)
                    ?.transform {
                        it.setLong(OlmSessionEntityFields.LAST_RECEIVED_MESSAGE_TS, 0)
                    }
        }
    }
}
