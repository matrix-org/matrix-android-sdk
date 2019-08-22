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

package org.matrix.androidsdk.features.identityserver

import android.content.Context
import android.support.v7.preference.PreferenceManager
import org.matrix.androidsdk.core.JsonUtils

class IdentityServerTokensStore(context: Context) {

    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    private val gson = JsonUtils.getBasicGson()

    private data class TokensStore(
            // Keys are user Id
            @JvmField
            val userToServerTokens: MutableMap<String, ServerTokens> = mutableMapOf()
    )

    private data class ServerTokens(
            // Keys are server Url, values are token
            @JvmField
            val serverTokens: MutableMap<String, String> = mutableMapOf()
    )

    fun getToken(userId: String, serverUrl: String): String? {
        return readStore()
                .userToServerTokens[userId]
                ?.serverTokens
                ?.get(serverUrl)
    }

    fun setToken(userId: String, serverUrl: String, token: String) {
        readStore()
                .apply {
                    userToServerTokens.getOrPut(userId) { ServerTokens() }
                            .serverTokens[serverUrl] = token
                }
                .commit()
    }

    fun resetToken(userId: String, serverUrl: String) {
        readStore()
                .apply {
                    userToServerTokens[userId]?.serverTokens?.remove(serverUrl)
                    if (userToServerTokens[userId]?.serverTokens?.isEmpty() == true) {
                        userToServerTokens.remove(userId)
                    }
                }
                .commit()
    }

    private fun readStore(): TokensStore {
        return prefs.getString(IDENTITY_SERVER_TOKENS_PREFERENCE_KEY, null)
                ?.toModel()
                ?: TokensStore()
    }

    private fun TokensStore.commit() {
        prefs.edit()
                .putString(IDENTITY_SERVER_TOKENS_PREFERENCE_KEY, this@commit.fromModel())
                .apply()
    }

    fun clear() {
        prefs.edit()
                .remove(IDENTITY_SERVER_TOKENS_PREFERENCE_KEY)
                .apply()
    }

    private fun String.toModel(): TokensStore? {
        return gson.fromJson<TokensStore>(this, TokensStore::class.java)
    }

    private fun TokensStore.fromModel(): String? {
        return gson.toJson(this)
    }

    companion object {
        private const val IDENTITY_SERVER_TOKENS_PREFERENCE_KEY = "IDENTITY_SERVER_TOKENS_PREFERENCE_KEY"
    }
}