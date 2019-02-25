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

import android.os.Handler
import android.os.Looper
import okhttp3.*
import org.json.JSONObject
import org.matrix.androidsdk.rest.callback.ApiCallback
import org.matrix.androidsdk.util.Log
import java.io.IOException
import java.net.URL


class AutoDiscovery(var okHttpClient: OkHttpClient = OkHttpClient()) {

    companion object {
        val instance = AutoDiscovery()
        private val LOG_TAG = AutoDiscovery::class.java.name
    }

    data class DiscoveredClientConfig(val action: Action, val homeServerUrl: String? = null, val idendityServerUrl: String? = null)

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

    private val mUiHandler = Handler(Looper.getMainLooper());

    fun findClientConfig(domain: String, callback: ApiCallback<DiscoveredClientConfig>) {

        val request = Request.Builder()
                .url("https://$domain/.well-known/matrix/client")
                .build()

        /**
         * Fetches a JSON object from a given URL, as expected by all .well-known
         * related lookups. If the server gives a 404 then the `action` will be
         * IGNORE. If the server returns something that isn't JSON, the `action`
         * will be FAIL_PROMPT. For any other failure the `action` will be FAIL_PROMPT.
         *
         * The returned object will be a result of the call in object form with
         * the following properties:
         *   raw: The JSON object returned by the server.
         *   action: One of SUCCESS, IGNORE, or FAIL_PROMPT.
         *   reason: Relatively human readable description of what went wrong.
         *   error: The actual Error, if one exists.
         *   */
        okHttpClient.newCall(request).enqueue(object : Callback {

            override fun onResponse(call: Call, response: Response) {
                val code = response.code()
                //a. If the returned status code is 404, then IGNORE.
                if (code == 404) {
                    //IGNORE
                    mUiHandler.post {
                        callback.onSuccess(DiscoveredClientConfig(Action.IGNORE))
                    }
                    return
                }
                // b. If the returned status code is not 200, or the response body is empty, then FAIL_PROMPT.
                if (code != 200) {
                    //FAIL_PROMPT
                    mUiHandler.post {
                        callback.onSuccess(DiscoveredClientConfig(Action.FAIL_PROMPT))
                    }
                    return
                }
                response.body().use { responseBody ->

                    val rawString = responseBody?.string()
                    if (rawString == null) {
                        //b. If the returned status code is not 200, or the response body is empty, then FAIL_PROMPT.
                        mUiHandler.post {
                            callback.onSuccess(DiscoveredClientConfig(Action.FAIL_PROMPT))
                        }
                        return
                    } else try {
                        val wellKnownJsonElement = JSONObject(rawString)

                        val homeServerInformation = wellKnownJsonElement.optJSONObject("m.homeserver")
                        if (homeServerInformation == null || !homeServerInformation.has("base_url")) {
                            //d.i If this value is not provided, then FAIL_PROMPT.
                            mUiHandler.post {
                                callback.onSuccess(DiscoveredClientConfig(Action.FAIL_PROMPT))
                            }
                            return
                        }
                        val homeServerUrl = homeServerInformation.optString("base_url", "")
                        //Validate the homeserver base URL
                        try {
                            URL(homeServerUrl)
                        } catch (e: Throwable) {
                            //i. Parse it as a URL. If it is not a URL, then FAIL_ERROR
                            mUiHandler.post {
                                callback.onSuccess(DiscoveredClientConfig(Action.FAIL_ERROR))
                            }
                            return
                        }
                        //Clients SHOULD validate that the URL points to a valid homeserver.
                        val validateHomeServerRequest = Request.Builder()
                                .url("${homeServerUrl}/_matrix/client/versions")
                                .build()

                        // If any step in the validation fails, then FAIL_ERROR
                        okHttpClient.newCall(validateHomeServerRequest).execute().use { response ->
                            try {
                                val bodyString = response?.body()?.string()
                                val jsonElement = JSONObject(bodyString)
                                if (jsonElement.optJSONArray("versions") == null) {
                                    mUiHandler.post {
                                        callback.onSuccess(DiscoveredClientConfig(Action.FAIL_ERROR))
                                    }
                                    return
                                }
                            } catch (jException: Throwable) {
                                Log.d(LOG_TAG,"AutoDiscovery error", jException)
                                mUiHandler.post {
                                    callback.onSuccess(DiscoveredClientConfig(Action.FAIL_ERROR))
                                }
                                return
                            }
                        }


                        // If the m.identity_server property is present, extract the base_url value for use as the base URL of the identity server.
                        // Validation for this URL is done as in the step above, but using /_matrix/identity/api/v1 as the endpoint to connect to.
                        // If the m.identity_server property is present, but does not have a base_url value, then FAIL_ERROR.

                        val identityServerInformation = wellKnownJsonElement.optJSONObject("m.identity_server")
                        if (identityServerInformation == null) {
                            val config = DiscoveredClientConfig(Action.PROMPT, homeServerUrl = homeServerUrl!!)
                            mUiHandler.post {
                                callback.onSuccess(config)
                            }
                        } else {
                            var identityServerBaseURL: String? = null
                            identityServerBaseURL = identityServerInformation.optString("base_url", null)
                            if (identityServerBaseURL == null) {
                                mUiHandler.post {
                                    callback.onSuccess(DiscoveredClientConfig(Action.FAIL_ERROR))
                                }
                                return
                            }

                            //Validate the homeserver base URL
                            try {
                                URL(identityServerBaseURL)
                            } catch (e: Throwable) {
                                Log.d(LOG_TAG,"AutoDiscovery error", e)
                                //i. Parse it as a URL. If it is not a URL, then FAIL_ERROR
                                mUiHandler.post {
                                    callback.onSuccess(DiscoveredClientConfig(Action.FAIL_ERROR))
                                }
                                return
                            }

                            //need to validate
                            val validateIdentityServerRequest = Request.Builder()
                                    .url("$identityServerBaseURL/_matrix/identity/api/v1")
                                    .build()

                            // If any step in the validation fails, then FAIL_ERROR
                            okHttpClient.newCall(validateIdentityServerRequest).enqueue(object : Callback {

                                override fun onResponse(call: Call, response: Response) {
                                    if (response.isSuccessful) {
                                        val config = DiscoveredClientConfig(Action.PROMPT, homeServerUrl!!, identityServerBaseURL)
                                        mUiHandler.post {
                                            callback.onSuccess(config)
                                        }
                                    } else {
                                        mUiHandler.post {
                                            callback.onSuccess(DiscoveredClientConfig(Action.FAIL_ERROR))
                                        }
                                    }
                                }

                                override fun onFailure(call: Call, e: IOException) {
                                    Log.d(LOG_TAG,"AutoDiscovery error", e)
                                    mUiHandler.post {
                                        callback.onSuccess(DiscoveredClientConfig(Action.FAIL_ERROR))
                                    }
                                }

                            })

                        }

                    } catch (e: Throwable) {
                        Log.d(LOG_TAG,"AutoDiscovery error", e)
                        //C. Parse the response body as a JSON object
                        //    i.If the content cannot be parsed, then FAIL_PROMPT.
                        mUiHandler.post {
                            callback.onSuccess(DiscoveredClientConfig(Action.FAIL_PROMPT))
                        }
                        return
                    }

                }

            }

            override fun onFailure(call: Call, e: IOException) {
                mUiHandler.post {
                    callback.onNetworkError(e)
                }
            }
        })
    }

}