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

import android.os.Looper
import org.junit.Assert.*
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.matrix.androidsdk.RestClientHttpClientFactory
import org.matrix.androidsdk.RestHttpClientFactoryProvider
import org.matrix.androidsdk.common.CommonTestHelper
import org.matrix.androidsdk.common.MockOkHttpInterceptor
import org.matrix.androidsdk.common.TestApiCallback
import java.util.concurrent.CountDownLatch

@FixMethodOrder(MethodSorters.JVM)
class AutoDiscoveryTest {

    companion object {
        const val WELL_KNOWN_PATH = ".well-known/matrix/client"
        const val MX_CLIENT_VERSION_PATH = "_matrix/client/versions"
        const val MX_ID_PATH = "_matrix/identity/api/v1"
    }

    private val mTestHelper = CommonTestHelper()

    //If the returned status code is 404, then IGNORE.
    @Test
    fun testAutoDiscoveryNotFound() {
        val discovery = arrangeAndAct(
                listOf(
                        MockOkHttpInterceptor.SimpleRule(WELL_KNOWN_PATH, 404)
                )
        )

        //Assert
        assertFail(discovery, AutoDiscovery.Action.IGNORE)
    }

    //If the returned status code is not 200 then FAIL_PROMPT.
    @Test
    fun testAutoDiscoveryNotOK() {
        val discovery = arrangeAndAct(
                listOf(
                        MockOkHttpInterceptor.SimpleRule(WELL_KNOWN_PATH, 500)
                )
        )

        //Assert
        assertFail(discovery, AutoDiscovery.Action.FAIL_PROMPT)
    }

    //  If the response body is empty then FAIL_PROMPT.
    @Test
    fun testAutoDiscoveryEmptyBody() {
        val discovery = arrangeAndAct(
                listOf(
                        MockOkHttpInterceptor.SimpleRule(WELL_KNOWN_PATH, 200, "")
                )
        )

        //Assert
        assertFail(discovery, AutoDiscovery.Action.FAIL_PROMPT)
    }

    //   If the content cannot be parsed, then FAIL_PROMPT.
    @Test
    fun testAutoDiscoveryNotJSON() {
        //Arrange
        val mockBody = "<html><h1>Hello world!</h1></html>"

        val discovery = arrangeAndAct(
                listOf(
                        MockOkHttpInterceptor.SimpleRule(WELL_KNOWN_PATH, 200, mockBody)
                )
        )

        //Assert
        assertFail(discovery, AutoDiscovery.Action.FAIL_PROMPT)
    }

    //If  m.homeserver value is not provided, then FAIL_PROMPT.
    @Test
    fun testAutoDiscoveryMissingHS() {
        //Arrange
        val mockBody = """
        {
            "m.homesorv4r" : {}
        }
        """

        val discovery = arrangeAndAct(
                listOf(
                        MockOkHttpInterceptor.SimpleRule(WELL_KNOWN_PATH, 200, mockBody)
                )
        )

        //Assert
        assertFail(discovery, AutoDiscovery.Action.FAIL_PROMPT)
    }

    // if base_url from m.homeserver is not provided, then FAIL_PROMPT.
    @Test
    fun testAutoDiscoveryMissingHSBaseURl() {
        //Arrange
        val mockBody = "{\"m.homeserver\" : {}}"

        val discovery = arrangeAndAct(
                listOf(
                        MockOkHttpInterceptor.SimpleRule(WELL_KNOWN_PATH, 200, mockBody)
                )
        )

        //Assert
        assertFail(discovery, AutoDiscovery.Action.FAIL_PROMPT)
    }

    // if base_url from m.homeserver is not an URL, then FAIL_ERROR.
    @Test
    fun testAutoDiscoveryHSBaseURlInvalid() {
        //Arrange
        val invalidURL = "foo\$[level]/r\$[y]"
        val mockBody = """
            {
                "m.homeserver" : {
                    "base_url" : "$invalidURL"
                }
            }
            """

        val discovery = arrangeAndAct(
                listOf(
                        MockOkHttpInterceptor.SimpleRule(WELL_KNOWN_PATH, 200, mockBody)
                )
        )

        //Assert
        assertFail(discovery, AutoDiscovery.Action.FAIL_ERROR)
    }


    // if base_url from m.homeserver is not a valid Home Server, then FAIL_ERROR.
    @Test
    fun testAutoDiscoveryNotValideHSURL() {
        //Arrange
        val baseURl = "https://myhs.org"

        val mockBody = """
            {
                "m.homeserver" : {
                    "base_url" : "$baseURl"
                }
            }
            """

        val discovery = arrangeAndAct(
                listOf(
                        MockOkHttpInterceptor.SimpleRule(WELL_KNOWN_PATH, 200, mockBody),
                        MockOkHttpInterceptor.SimpleRule(baseURl, 404)
                ))

        //Assert
        assertFail(discovery, AutoDiscovery.Action.FAIL_ERROR)
    }


    @Test
    fun testAutoDiscoveryHomeServerSuccess() {
        //Arrange
        val baseURl = "https://myhs.org"
        val mockBody = """
            {
                "m.homeserver" : {
                    "base_url" : "$baseURl"
                }
            }
            """
        val hsVersionResponse = """
                {
                    "versions": ["r0.4.0"],
                    "unstable_features": { "m.lazy_load_members": true}
                }

        """

        val discovery = arrangeAndAct(
                listOf(
                        MockOkHttpInterceptor.SimpleRule(WELL_KNOWN_PATH, 200, mockBody),
                        MockOkHttpInterceptor.SimpleRule("$baseURl/$MX_CLIENT_VERSION_PATH", 200, hsVersionResponse)
                ))

        //Assert
        assertEquals(AutoDiscovery.Action.PROMPT, discovery.action)
        assertNotNull(discovery.wellKnown?.homeServer)
        assertEquals(baseURl, discovery.wellKnown!!.homeServer?.baseURL)
        assertNull(discovery.wellKnown?.identityServer)
    }

    @Test
    fun testAutoDiscoveryInvalidIdServerMissingBaseURl() {
        //Arrange
        val baseURl = "https://myhs.org"
        val mockBody = """
            {
                "m.homeserver" : {
                    "base_url" : "$baseURl"
                },
                "m.identity_server" : {}
            }
            """
        val hsVersionResponse = """
                {
                    "versions": ["r0.4.0"],
                    "unstable_features": { "m.lazy_load_members": true}
                }

        """

        val discovery = arrangeAndAct(
                listOf(
                        MockOkHttpInterceptor.SimpleRule(WELL_KNOWN_PATH, 200, mockBody),
                        MockOkHttpInterceptor.SimpleRule("$baseURl/$MX_CLIENT_VERSION_PATH", 200, hsVersionResponse)
                ))

        //Assert
        assertFail(discovery, AutoDiscovery.Action.FAIL_ERROR)
    }

    @Test
    fun testAutoDiscoveryInvalidIdServerInvalidBaseURl() {
        //Arrange
        val baseURl = "https://myhs.org"
        val idServerBaseURL = ""
        val mockBody = """
            {
                "m.homeserver" : {
                    "base_url" : "$baseURl"
                },
                "m.identity_server" : {
                    "base_url" : "$idServerBaseURL"
                }
            }
            """
        val hsVersionResponse = """
                {
                    "versions": ["r0.4.0"],
                    "unstable_features": { "m.lazy_load_members": true}
                }

        """

        val discovery = arrangeAndAct(
                listOf(
                        MockOkHttpInterceptor.SimpleRule(WELL_KNOWN_PATH, 200, mockBody),
                        MockOkHttpInterceptor.SimpleRule("$baseURl/$MX_CLIENT_VERSION_PATH", 200, hsVersionResponse)
                ))

        //Assert
        assertFail(discovery, AutoDiscovery.Action.FAIL_ERROR)
    }

    @Test
    fun testAutoDiscoveryInvalidIdServer() {
        //Arrange
        val baseURl = "https://myhs.org"
        val idServerBaseURL = "https://myhs.org"

        val mockBody = """
            {
                "m.homeserver" : {
                    "base_url" : "$baseURl"
                },
                "m.identity_server" : {
                    "base_url" : "$idServerBaseURL"
                }
            }
            """
        val hsVersionResponse = """
                {
                    "versions": ["r0.4.0"],
                    "unstable_features": { "m.lazy_load_members": true}
                }

        """

        val discovery = arrangeAndAct(
                listOf(
                        MockOkHttpInterceptor.SimpleRule(WELL_KNOWN_PATH, 200, mockBody),
                        MockOkHttpInterceptor.SimpleRule("$baseURl/$MX_CLIENT_VERSION_PATH", 200, hsVersionResponse)
                ))

        //Assert
        assertFail(discovery, AutoDiscovery.Action.FAIL_ERROR)
    }


    @Test
    fun testAutoDiscoverySuccessful() {
        //Arrange
        val baseURl = "https://myhs.org"
        val idServerBaseURL = "https://boom.org"
        val mockBody = """
            {
                "m.homeserver" : {
                    "base_url" : "$baseURl"
                },
                "m.identity_server" : {
                    "base_url" : "$idServerBaseURL"
                }
            }
            """
        val hsVersionResponse = """
                {
                    "versions": ["r0.4.0"],
                    "unstable_features": { "m.lazy_load_members": true}
                }

        """

        val idServerResponse = "{}"

        val discovery = arrangeAndAct(
                listOf(
                        MockOkHttpInterceptor.SimpleRule(WELL_KNOWN_PATH, 200, mockBody),
                        MockOkHttpInterceptor.SimpleRule("$baseURl/$MX_CLIENT_VERSION_PATH", 200, hsVersionResponse),
                        MockOkHttpInterceptor.SimpleRule("$idServerBaseURL/$MX_ID_PATH", 200, idServerResponse)
                ))

        //Assert
        assertEquals(AutoDiscovery.Action.PROMPT, discovery.action)
        assertNotNull(discovery.wellKnown?.homeServer)
        assertNotNull(discovery.wellKnown?.identityServer)
        assertEquals(baseURl, discovery.wellKnown?.homeServer?.baseURL)
        assertEquals(idServerBaseURL, discovery.wellKnown?.identityServer?.baseURL)
    }

    /* ==========================================================================================
     * Private
     * ========================================================================================== */

    private fun arrangeAndAct(rules: List<MockOkHttpInterceptor.Rule>)
            : AutoDiscovery.DiscoveredClientConfig {
        //Arrange
        val mockInterceptor = MockOkHttpInterceptor()

        rules.forEach {
            mockInterceptor.addRule(it)
        }

        RestHttpClientFactoryProvider.defaultProvider = RestClientHttpClientFactory(mockInterceptor)
        val ad = AutoDiscovery()

        //Act
        var callbackThread: Thread? = null
        val lock = CountDownLatch(1)

        var discovery: AutoDiscovery.DiscoveredClientConfig? = null
        ad.findClientConfig("matrix.org", object : TestApiCallback<AutoDiscovery.DiscoveredClientConfig>(lock) {
            override fun onSuccess(info: AutoDiscovery.DiscoveredClientConfig) {
                discovery = info
                callbackThread = Thread.currentThread()
                super.onSuccess(info)
            }

        })

        mTestHelper.await(lock)

        //Assert
        assertNotNull(discovery)
        assertEquals("Callback should be in main thread", Looper.getMainLooper().thread, callbackThread)

        return discovery!!
    }

    private fun assertFail(discovery: AutoDiscovery.DiscoveredClientConfig, expectedAction: AutoDiscovery.Action) {
        assertEquals(expectedAction, discovery.action)
        assertNull(discovery.wellKnown?.homeServer)
        assertNull(discovery.wellKnown?.identityServer)
    }
}

