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
import org.json.JSONObject
import org.matrix.androidsdk.HomeServerConnectionConfig
import org.matrix.androidsdk.rest.callback.ApiCallback
import org.matrix.androidsdk.rest.client.AutoDiscoveryRestClient
import org.matrix.androidsdk.rest.client.IdentityPingRestClient
import org.matrix.androidsdk.rest.client.LoginRestClient
import org.matrix.androidsdk.rest.model.HttpException
import org.matrix.androidsdk.rest.model.MatrixError
import org.matrix.androidsdk.rest.model.Versions
import org.matrix.androidsdk.rest.model.WellKnown
import java.lang.Exception
import java.net.MalformedURLException
import java.net.URL
import javax.net.ssl.HttpsURLConnection


class AutoDiscovery {

    private var discoveryRestClient = AutoDiscoveryRestClient()

    data class DiscoveredClientConfig(
            val action: Action,
            val wellKnown: WellKnown? = null
    )

    enum class Action {
        /*
         Retrieve the specific piece of information from the user in a way which fits within the existing client user experience,
         if the client is inclined to do so. Failure can take place instead if no good user experience for this is possible at this point.
         */
        PROMPT,
        /*
         Stop the current auto-discovery mechanism. If no more auto-discovery mechanisms are available,
         then the client may use other methods of determining the required parameters, such as prompting the user, or using default values.
         */
        IGNORE,
        /*
            Inform the user that auto-discovery failed due to invalid/empty data and PROMPT for the parameter.
         */
        FAIL_PROMPT,
        /*
         Inform the user that auto-discovery did not return any usable URLs. Do not continue further with the current login process.
         At this point, valid data was obtained, but no homeserver is available to serve the client.
         No further guess should be attempted and the user should make a conscientious decision what to do next.
         */
        FAIL_ERROR,

    }


    fun findClientConfig(domain: String, callback: ApiCallback<DiscoveredClientConfig>) {

        discoveryRestClient.getWellKnown(domain, object : ApiCallback<WellKnown> {
            override fun onSuccess(wellKnown: WellKnown) {
                if (wellKnown.homeServer?.baseURL.isNullOrBlank()) {
                    callback.onSuccess(DiscoveredClientConfig(Action.FAIL_PROMPT))
                } else {
                    val baseURLString = wellKnown.homeServer!!.baseURL!!
                    if (!isValidURL(baseURLString)) {
                        callback.onSuccess(DiscoveredClientConfig(Action.FAIL_ERROR))
                        return
                    }
                    //Check that HS is a real one
                    validateHomeServerAndProceed(wellKnown, callback)
                }
            }

            override fun onUnexpectedError(e: Exception) {
                callback.onUnexpectedError(e)
            }

            override fun onNetworkError(e: Exception) {
                if (e is HttpException) {
                    when (e.httpError.httpCode) {
                        HttpsURLConnection.HTTP_NOT_FOUND -> callback.onSuccess(DiscoveredClientConfig(Action.IGNORE))
                        else -> callback.onSuccess(DiscoveredClientConfig(Action.FAIL_PROMPT))
                    }
                    return
                }
                callback.onSuccess(DiscoveredClientConfig(Action.FAIL_PROMPT))
            }

            override fun onMatrixError(e: MatrixError) {
                callback.onMatrixError(e)
            }

        })
    }

    private fun validateHomeServerAndProceed(wellKnown: WellKnown, callback: ApiCallback<DiscoveredClientConfig>) {

        val hsConfig = HomeServerConnectionConfig.Builder()
                .withHomeServerUri(Uri.parse(wellKnown.homeServer!!.baseURL!!))
                .build()
        val loginRestClient = LoginRestClient(hsConfig)
        loginRestClient.getVersions(object : ApiCallback<Versions> {
            override fun onSuccess(versions: Versions) {

                if (wellKnown.identityServer == null) {
                    callback.onSuccess(DiscoveredClientConfig(Action.PROMPT, wellKnown))
                    return
                }

                //if m.identity_server is present it must be valid
                if (wellKnown.identityServer!!.baseURL.isNullOrBlank()) {
                    callback.onSuccess(DiscoveredClientConfig(Action.FAIL_ERROR))
                    return
                }
                val identityServerBaseUrl = wellKnown.identityServer!!.baseURL!!
                if (!isValidURL(identityServerBaseUrl)) {
                    callback.onSuccess(DiscoveredClientConfig(Action.FAIL_ERROR))
                    return
                }
                validateIdentityServerAndFinish(wellKnown, callback)
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
        val idCheckClient = IdentityPingRestClient(hsConfig)
        idCheckClient.ping(object : ApiCallback<JSONObject> {
            override fun onSuccess(info: JSONObject?) {
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