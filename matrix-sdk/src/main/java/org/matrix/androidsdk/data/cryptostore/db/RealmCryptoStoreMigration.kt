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
import org.matrix.androidsdk.data.cryptostore.db.model.IncomingRoomKeyRequestEntityFields
import org.matrix.androidsdk.data.cryptostore.db.model.KeysBackupDataEntityFields
import org.matrix.androidsdk.data.cryptostore.db.model.OlmSessionEntityFields
import org.matrix.androidsdk.data.cryptostore.db.model.OutgoingRoomKeyRequestEntityFields
import org.matrix.androidsdk.util.Log

internal object RealmCryptoStoreMigration : RealmMigration {

    private const val LOG_TAG = "RealmCryptoStoreMigration"

    const val CRYPTO_STORE_SCHEMA_VERSION = 2L

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

        if (oldVersion <= 1) {
            Log.d(LOG_TAG, "Step 1 -> 2")
            Log.d(LOG_TAG, "Update IncomingRoomKeyRequestEntity format: requestBodyString field is exploded into several fields")

            realm.schema.get("IncomingRoomKeyRequestEntity")
                    ?.addField(IncomingRoomKeyRequestEntityFields.REQUEST_BODY_ALGORITHM, String::class.java)
                    ?.addField(IncomingRoomKeyRequestEntityFields.REQUEST_BODY_ROOM_ID, String::class.java)
                    ?.addField(IncomingRoomKeyRequestEntityFields.REQUEST_BODY_SENDER_KEY, String::class.java)
                    ?.addField(IncomingRoomKeyRequestEntityFields.REQUEST_BODY_SESSION_ID, String::class.java)
                    ?.transform { dynamicObject ->
                        val requestBodyString = dynamicObject.getString("requestBodyString")
                        try {
                            // It was a map before
                            val map: Map<String, String>? = deserializeFromRealm(requestBodyString)

                            map?.let {
                                dynamicObject.setString(IncomingRoomKeyRequestEntityFields.REQUEST_BODY_ALGORITHM, it["algorithm"])
                                dynamicObject.setString(IncomingRoomKeyRequestEntityFields.REQUEST_BODY_ROOM_ID, it["room_id"])
                                dynamicObject.setString(IncomingRoomKeyRequestEntityFields.REQUEST_BODY_SENDER_KEY, it["sender_key"])
                                dynamicObject.setString(IncomingRoomKeyRequestEntityFields.REQUEST_BODY_SESSION_ID, it["session_id"])
                            }
                        } catch (e: Exception) {
                            Log.d(LOG_TAG, "Error", e)
                        }
                    }
                    ?.removeField("requestBodyString")

            Log.d(LOG_TAG, "Update IncomingRoomKeyRequestEntity format: requestBodyString field is exploded into several fields")

            realm.schema.get("OutgoingRoomKeyRequestEntity")
                    ?.addField(OutgoingRoomKeyRequestEntityFields.REQUEST_BODY_ALGORITHM, String::class.java)
                    ?.addField(OutgoingRoomKeyRequestEntityFields.REQUEST_BODY_ROOM_ID, String::class.java)
                    ?.addField(OutgoingRoomKeyRequestEntityFields.REQUEST_BODY_SENDER_KEY, String::class.java)
                    ?.addField(OutgoingRoomKeyRequestEntityFields.REQUEST_BODY_SESSION_ID, String::class.java)
                    ?.transform { dynamicObject ->
                        val requestBodyString = dynamicObject.getString("requestBodyString")
                        try {
                            // It was a map before
                            val map: Map<String, String>? = deserializeFromRealm(requestBodyString)

                            map?.let {
                                dynamicObject.setString(IncomingRoomKeyRequestEntityFields.REQUEST_BODY_ALGORITHM, it["algorithm"])
                                dynamicObject.setString(IncomingRoomKeyRequestEntityFields.REQUEST_BODY_ROOM_ID, it["room_id"])
                                dynamicObject.setString(IncomingRoomKeyRequestEntityFields.REQUEST_BODY_SENDER_KEY, it["sender_key"])
                                dynamicObject.setString(IncomingRoomKeyRequestEntityFields.REQUEST_BODY_SESSION_ID, it["session_id"])
                            }
                        } catch (e: Exception) {
                            Log.d(LOG_TAG, "Error", e)
                        }
                    }
                    ?.removeField("requestBodyString")

            Log.d(LOG_TAG, "Create KeysBackupDataEntity")

            realm.schema.create("KeysBackupDataEntity")
                    .addField(KeysBackupDataEntityFields.PRIMARY_KEY, Integer::class.java)
                    .addPrimaryKey(KeysBackupDataEntityFields.PRIMARY_KEY)
                    .setRequired(KeysBackupDataEntityFields.PRIMARY_KEY, true)
                    .addField(KeysBackupDataEntityFields.BACKUP_LAST_SERVER_HASH, String::class.java)
                    .addField(KeysBackupDataEntityFields.BACKUP_LAST_SERVER_NUMBER_OF_KEYS, Integer::class.java)
        }
    }
}
