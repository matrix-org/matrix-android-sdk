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
import android.net.Uri
import org.matrix.androidsdk.HomeServerConnectionConfig
import org.matrix.androidsdk.MXSession
import org.matrix.androidsdk.core.callback.ApiCallback
import org.matrix.androidsdk.core.callback.SimpleApiCallback
import org.matrix.androidsdk.core.model.MatrixError
import org.matrix.androidsdk.features.terms.TermsNotSignedException
import org.matrix.androidsdk.listeners.MXEventListener
import org.matrix.androidsdk.rest.client.IdentityAuthRestClient
import org.matrix.androidsdk.rest.client.ThirdPidRestClient
import org.matrix.androidsdk.rest.model.identityserver.HashDetailResponse
import org.matrix.androidsdk.rest.model.identityserver.IdentityServerRegisterResponse
import org.matrix.androidsdk.rest.model.openid.RequestOpenIdTokenResponse
import javax.net.ssl.HttpsURLConnection

class IdentityServerManager(val mxSession: MXSession,
                            context: Context) {

    init {
        setIdentityServerUrl(mxSession.homeServerConfig.identityServerUri?.toString())

        mxSession.dataHandler.addListener(object : MXEventListener() {
            override fun onAccountDataUpdated() {
                // Has the identity server been updated?
            }
        })
    }

    private val identityServerTokensStore = IdentityServerTokensStore(context)

    private var identityPath: String? = null

    // Rest clients
    private var identityAuthRestClient: IdentityAuthRestClient? = null
    private var thirdPidRestClient: ThirdPidRestClient? = null

    /**
     * Update the identity server Url. It is up the the client to persist this data
     * @param newUrl: null to remove identityServer
     */
    fun setIdentityServerUrl(newUrl: String?) {
        identityPath = newUrl

        if (newUrl.isNullOrBlank()) {
            identityAuthRestClient = null
            thirdPidRestClient = null
        } else {
            val alteredHsConfig = HomeServerConnectionConfig.Builder(mxSession.homeServerConfig)
                    .withHomeServerUri(Uri.parse(newUrl))
                    .build()

            identityAuthRestClient = IdentityAuthRestClient(alteredHsConfig)
            thirdPidRestClient = ThirdPidRestClient(alteredHsConfig)
        }

        // TODO Update AccountData, but not the first time
    }

    /**
     *
     */
    private fun getToken(callback: ApiCallback<String>) {
        val storeToken = identityServerTokensStore
                .getToken(mxSession.myUserId, mxSession.homeServerConfig.identityServerUri?.toString() ?: "")

        if (storeToken.isNullOrEmpty().not()) {
            callback.onSuccess(storeToken)
        } else {
            // Request a new token
            mxSession.openIdToken(object : SimpleApiCallback<RequestOpenIdTokenResponse>(callback) {
                override fun onSuccess(info: RequestOpenIdTokenResponse) {
                    requestIdentityServerToken(info, callback)
                }

            })
        }

/*

        identityAuthRestClient?.setAccessToken(storeToken)

        // Check validity
        identityAuthRestClient?.checkAccount(storeToken, object : ApiCallback<Unit> {
            override fun onSuccess(info: Unit?) {
            }

            override fun onUnexpectedError(e: Exception?) {
            }

            override fun onNetworkError(e: Exception?) {
            }

            override fun onMatrixError(e: MatrixError?) {
            }

        })
*/
    }

    private fun requestIdentityServerToken(requestOpenIdTokenResponse: RequestOpenIdTokenResponse, callback: ApiCallback<String>) {
        identityAuthRestClient?.register(requestOpenIdTokenResponse, object : SimpleApiCallback<IdentityServerRegisterResponse>(callback) {
            override fun onSuccess(info: IdentityServerRegisterResponse) {
                // Store the token for next time
                identityServerTokensStore.setToken(mxSession.myUserId, identityPath!!, info.identityServerAccessToken)

                callback.onSuccess(info.identityServerAccessToken)
            }

            override fun onMatrixError(e: MatrixError) {
                // Handle 404
                if (e.mStatus == 404) {
                    callback.onUnexpectedError(IdentityServerV2ApiNotAvailable())
                } else {
                    super.onMatrixError(e)
                }
            }
        })
    }

    fun lookup3Pids(addresses: MutableList<String>, mediums: MutableList<String>, callback: ApiCallback<MutableList<String>>) {
        val client = thirdPidRestClient
        if (client == null) {
            callback.onUnexpectedError(IdentityServerNotConfiguredException())
        } else {
            getToken(object : SimpleApiCallback<String>(callback) {
                override fun onSuccess(info: String) {
                    client.setAccessToken(info)

                    // We need the look up param
                    client.getLookupParam(object : SimpleApiCallback<HashDetailResponse>(callback) {
                        override fun onSuccess(info: HashDetailResponse) {
                            client.lookup3PidsV2(info, addresses, mediums, callback)
                        }

                        override fun onMatrixError(e: MatrixError) {
                            if (e.mStatus == HttpsURLConnection.HTTP_UNAUTHORIZED /* 401 */) {
                                // 401 -> Renew token
                                // Reset the token we know and start again
                                identityServerTokensStore
                                        .resetToken(mxSession.myUserId, mxSession.homeServerConfig.identityServerUri?.toString() ?: "")
                                lookup3Pids(addresses, mediums, callback)
                            } else if (e.mStatus == HttpsURLConnection.HTTP_FORBIDDEN /* 403 */
                                    && e.errcode == MatrixError.TERMS_NOT_SIGNED) {
                                callback.onUnexpectedError(TermsNotSignedException(info))
                            } else {
                                super.onMatrixError(e)
                            }
                        }
                    })
                }

                override fun onUnexpectedError(e: Exception) {
                    if (e is IdentityServerV2ApiNotAvailable) {
                        // Fallback to v1 API
                        client.lookup3Pids(addresses, mediums, callback)
                    } else {
                        super.onUnexpectedError(e)
                    }
                }
            })
        }
    }

    fun submitValidationToken(medium: String, token: String, clientSecret: String, sid: String, callback: ApiCallback<Boolean>) {
        val client = thirdPidRestClient
        if (client == null) {
            callback.onUnexpectedError(IdentityServerNotConfiguredException())
        } else {
            getToken(object : SimpleApiCallback<String>(callback) {
                override fun onSuccess(info: String) {
                    client.setAccessToken(info)
                    client.submitValidationToken(medium, token, clientSecret, sid, object : SimpleApiCallback<Boolean>(callback) {
                        override fun onSuccess(info: Boolean) {
                            callback.onSuccess(info)
                        }

                        override fun onMatrixError(e: MatrixError) {
                            if (e.mStatus == HttpsURLConnection.HTTP_UNAUTHORIZED /* 401 */) {
                                // 401 -> Renew token
                                // Reset the token we know and start again
                                identityServerTokensStore
                                        .resetToken(mxSession.myUserId, mxSession.homeServerConfig.identityServerUri?.toString() ?: "")
                                submitValidationToken(medium, token, clientSecret, sid, callback)
                            } else if (e.mStatus == HttpsURLConnection.HTTP_FORBIDDEN /* 403 */
                                    && e.errcode == MatrixError.TERMS_NOT_SIGNED) {
                                callback.onUnexpectedError(TermsNotSignedException(info))
                            } else {
                                super.onMatrixError(e)
                            }
                        }
                    })
                }

                override fun onUnexpectedError(e: Exception) {
                    if (e is IdentityServerV2ApiNotAvailable) {
                        // Fallback to v1 API
                        client.submitValidationTokenLegacy(medium, token, clientSecret, sid, callback)
                    } else {
                        super.onUnexpectedError(e)
                    }
                }
            })
        }
    }
}
