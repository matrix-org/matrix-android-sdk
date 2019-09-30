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

package org.matrix.androidsdk.core

import org.matrix.androidsdk.rest.model.Versions

/**
 * Extension for class [Versions]
 */

// MatrixClientServerAPIVersion
private const val VERSION_R0_0_1 = "r0_0_1"
private const val VERSION_R0_1_0 = "r0_1_0"
private const val VERSION_R0_2_0 = "r0_2_0"
private const val VERSION_R0_3_0 = "r0_3_0"
private const val VERSION_R0_4_0 = "r0_4_0"
private const val VERSION_R0_5_0 = "r0_5_0"
private const val VERSION_R0_6_0 = "r0_6_0"

// MatrixVersionsFeature
private const val FEATURE_LAZY_LOAD_MEMBERS = "m.lazy_load_members"
private const val FEATURE_REQUIRE_IDENTITY_SERVER = "m.require_identity_server"
private const val FEATURE_ID_ACCESS_TOKEN = "m.id_access_token"
private const val FEATURE_SEPARATE_ADD_AND_BIND = "m.separate_add_and_bind"

/**
 * Return true if the server support the lazy loading of room members
 *
 * @param versions the Versions object from the server request
 * @return true if the server support the lazy loading of room members
 */
fun Versions.supportLazyLoadMembers(): Boolean {
    return supportedVersions?.contains(VERSION_R0_5_0) == true
            || unstableFeatures?.get(FEATURE_LAZY_LOAD_MEMBERS) == true
}

/**
 * Indicate if the `id_server` parameter is required when registering with an 3pid,
 * adding a 3pid or resetting password.
 */
fun Versions.doesServerRequireIdentityServerParam(): Boolean {
    if (supportedVersions?.contains(VERSION_R0_6_0) == true) return false
    return unstableFeatures[FEATURE_REQUIRE_IDENTITY_SERVER] ?: true
}

/**
 * Indicate if the `id_access_token` parameter can be safely passed to the homeserver.
 * Some homeservers may trigger errors if they are not prepared for the new parameter.
 */
fun Versions.doesServerAcceptIdentityAccessToken(): Boolean {
    return supportedVersions?.contains(VERSION_R0_6_0) == true
            || unstableFeatures[FEATURE_ID_ACCESS_TOKEN] ?: false
}

fun Versions.doesServerSeparatesAddAndBind(): Boolean {
    return supportedVersions?.contains(VERSION_R0_6_0) == true
            || unstableFeatures[FEATURE_SEPARATE_ADD_AND_BIND] ?: false
}