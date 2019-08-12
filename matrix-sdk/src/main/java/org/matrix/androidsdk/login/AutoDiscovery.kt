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

package org.matrix.androidsdk.login

import android.net.Uri
import com.google.gson.stream.MalformedJsonException
import org.matrix.androidsdk.HomeServerConnectionConfig
import org.matrix.androidsdk.core.callback.ApiCallback
import org.matrix.androidsdk.core.callback.SimpleApiCallback
import org.matrix.androidsdk.core.model.HttpException
import org.matrix.androidsdk.core.model.MatrixError
import org.matrix.androidsdk.rest.client.IdentityPingRestClient
import org.matrix.androidsdk.rest.client.LoginRestClient
import org.matrix.androidsdk.rest.client.WellKnownRestClient
import org.matrix.androidsdk.rest.model.Versions
import org.matrix.androidsdk.rest.model.WellKnown
import java.io.EOFException
import java.net.MalformedURLException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * This class help to find WellKnown data of a Homeserver and provide action that must be done depending on well-known request result
 * and its data validation
 */
class AutoDiscovery {

    private val wellKnownRestClient = WellKnownRestClient()

    data class DiscoveredClientConfig(
            val action: Action,
            val wellKnown: WellKnown? = null
    )

    enum class Action {
        /**
         * Retrieve the specific piece of information from the user in a way which fits within the existing client user experience,
         * if the client is inclined to do so. Failure can take place instead if no good user experience for this is possible at this point.
         */
        PROMPT,
        /**
         * Stop the current auto-discovery mechanism. If no more auto-discovery mechanisms are available,
         * then the client may use other methods of determining the required parameters, such as prompting the user, or using default values.
         */
        IGNORE,
        /**
         * Inform the user that auto-discovery failed due to invalid/empty data and PROMPT for the parameter.
         */
        FAIL_PROMPT,
        /**
         * Inform the user that auto-discovery did not return any usable URLs. Do not continue further with the current login process.
         * At this point, valid data was obtained, but no homeserver is available to serve the client.
         * No further guess should be attempted and the user should make a conscientious decision what to do next.
         */
        FAIL_ERROR
    }

    /**
     * Find client config
     *
     * - Do the .well-known request
     * - validate homeserver url and identity server url if provide in .well-known result
     * - return action and .well-known data
     *
     * @param domain: homeserver domain, deduced from mx userId (ex: "matrix.org" from userId "@user:matrix.org")
     * @param callback to get the result
     */
    fun findClientConfig(domain: String, callback: ApiCallback<DiscoveredClientConfig>) {
        wellKnownRestClient.getWellKnown(domain, object : SimpleApiCallback<WellKnown>(callback) {
            override fun onSuccess(wellKnown: WellKnown) {
                if (wellKnown.homeServer?.baseURL.isNullOrBlank()) {
                    callback.onSuccess(DiscoveredClientConfig(Action.FAIL_PROMPT))
                } else {
                    val baseURLString = wellKnown.homeServer!!.baseURL!!
                    if (isValidURL(baseURLString)) {
                        //Check that HS is a real one
                        validateHomeServerAndProceed(wellKnown, callback)
                    } else {
                        callback.onSuccess(DiscoveredClientConfig(Action.FAIL_ERROR))
                    }
                }
            }

            override fun onNetworkError(e: Exception) {
                if (e is HttpException) {
                    when (e.httpError.httpCode) {
                        HttpsURLConnection.HTTP_NOT_FOUND -> callback.onSuccess(DiscoveredClientConfig(Action.IGNORE))
                        else -> callback.onSuccess(DiscoveredClientConfig(Action.FAIL_PROMPT))
                    }
                } else if (e is MalformedJsonException || e is EOFException) {
                    callback.onSuccess(DiscoveredClientConfig(Action.FAIL_PROMPT))
                } else {
                    super.onNetworkError(e)
                }
            }
        })
    }

    private fun validateHomeServerAndProceed(wellKnown: WellKnown, callback: ApiCallback<DiscoveredClientConfig>) {
        val hsConfig = HomeServerConnectionConfig.Builder()
                .withHomeServerUri(Uri.parse(wellKnown.homeServer!!.baseURL!!))
                .build()

        LoginRestClient(hsConfig).getVersions(object : ApiCallback<Versions> {
            override fun onSuccess(versions: Versions) {
                if (wellKnown.identityServer == null) {
                    callback.onSuccess(DiscoveredClientConfig(Action.PROMPT, wellKnown))
                } else {
                    //if m.identity_server is present it must be valid
                    if (wellKnown.identityServer!!.baseURL.isNullOrBlank()) {
                        callback.onSuccess(DiscoveredClientConfig(Action.FAIL_ERROR))
                    } else {
                        val identityServerBaseUrl = wellKnown.identityServer!!.baseURL!!
                        if (isValidURL(identityServerBaseUrl)) {
                            validateIdentityServerAndFinish(wellKnown, callback)
                        } else {
                            callback.onSuccess(DiscoveredClientConfig(Action.FAIL_ERROR))
                        }
                    }
                }
            }

            override fun onUnexpectedError(e: Exception?) {
                callback.onSuccess(DiscoveredClientConfig(Action.FAIL_ERROR))
            }

            override fun onNetworkError(e: Exception?) {
                callback.onSuccess(DiscoveredClientConfig(Action.FAIL_ERROR))
            }

            override fun onMatrixError(e: MatrixError?) {
                callback.onSuccess(DiscoveredClientConfig(Action.FAIL_ERROR))
            }

        })
    }

    private fun validateIdentityServerAndFinish(wellKnown: WellKnown, callback: ApiCallback<DiscoveredClientConfig>) {
        val hsConfig = HomeServerConnectionConfig.Builder()
                .withHomeServerUri(Uri.parse(wellKnown.homeServer!!.baseURL!!))
                .withIdentityServerUri(Uri.parse(wellKnown.identityServer!!.baseURL!!))
                .build()

        IdentityPingRestClient(hsConfig).ping(object : ApiCallback<Void> {
            override fun onSuccess(info: Void?) {
                callback.onSuccess(DiscoveredClientConfig(Action.PROMPT, wellKnown))
            }

            override fun onUnexpectedError(e: Exception?) {
                callback.onSuccess(DiscoveredClientConfig(Action.FAIL_ERROR))
            }

            override fun onNetworkError(e: Exception?) {
                callback.onSuccess(DiscoveredClientConfig(Action.FAIL_ERROR))
            }

            override fun onMatrixError(e: MatrixError?) {
                callback.onSuccess(DiscoveredClientConfig(Action.FAIL_ERROR))
            }
        })
    }

    private fun isValidURL(url: String): Boolean {
        return try {
            URL(url)
            true
        } catch (t: MalformedURLException) {
            false
        }
    }
}