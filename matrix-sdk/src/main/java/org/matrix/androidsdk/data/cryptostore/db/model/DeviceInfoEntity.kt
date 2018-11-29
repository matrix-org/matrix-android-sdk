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

package org.matrix.androidsdk.data.cryptostore.db.model

import io.realm.RealmObject
import io.realm.RealmResults
import io.realm.annotations.LinkingObjects
import io.realm.annotations.PrimaryKey
import org.matrix.androidsdk.crypto.data.MXDeviceInfo
import org.matrix.androidsdk.data.cryptostore.db.deserializeFromRealm
import org.matrix.androidsdk.data.cryptostore.db.serializeForRealm

fun DeviceInfoEntity.Companion.createPrimaryKey(userId: String, deviceId: String) = "$userId|$deviceId"

// deviceInfoData contains serialized data
open class DeviceInfoEntity(@PrimaryKey var primaryKey: String = "",
                            var deviceId: String? = null,
                            var identityKey: String? = null,
                            var deviceInfoData: String? = null)
    : RealmObject() {

    // Deserialize data
    fun getDeviceInfo(): MXDeviceInfo? {
        return deserializeFromRealm(deviceInfoData)
    }

    // Serialize data
    fun putDeviceInfo(deviceInfo: MXDeviceInfo?) {
        deviceInfoData = serializeForRealm(deviceInfo)
    }

    @LinkingObjects("devices")
    val users: RealmResults<UserEntity>? = null

    companion object
}
