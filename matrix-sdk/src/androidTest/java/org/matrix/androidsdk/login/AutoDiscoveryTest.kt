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

        val mockInterceptor = MockOkHttpInterceptor()
        mockInterceptor.addRule(MockOkHttpInterceptor.SimpleRule(WELL_KNOWN_PATH, 404))

        RestHttpClientFactoryProvider.defaultProvider = RestClientHttpClientFactory(mockInterceptor)
        val ad = AutoDiscovery()

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
        assertNotNull(discovery)
        assertEquals(AutoDiscovery.Action.IGNORE, discovery!!.action)
        assertNull(discovery?.wellKnown?.homeServer)
        assertNull(discovery?.wellKnown?.identityServer)
        assertTrue("Callback should be in main thread", Looper.getMainLooper().thread == callbackThread)

    }

    //If the returned status code is not 200 then FAIL_PROMPT.
    @Test
    fun testAutoDiscoveryNotOK() {

        //Arrange
        val mockInterceptor = MockOkHttpInterceptor()
        mockInterceptor.addRule(MockOkHttpInterceptor.SimpleRule(WELL_KNOWN_PATH, 500))

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
        assertEquals(AutoDiscovery.Action.FAIL_PROMPT, discovery!!.action)
        assertNull(discovery?.wellKnown?.homeServer)
        assertNull(discovery?.wellKnown?.identityServer)
        assertTrue("Callback should be in main thread", Looper.getMainLooper().thread == callbackThread)
    }

    //  If the response body is empty then FAIL_PROMPT.
    @Test
    fun testAutoDiscoveryEmptyBody() {

        //Arrange
        val mockInterceptor = MockOkHttpInterceptor()
        mockInterceptor.addRule(MockOkHttpInterceptor.SimpleRule(WELL_KNOWN_PATH, 200, ""))
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
        assertEquals(AutoDiscovery.Action.FAIL_PROMPT, discovery!!.action)
        assertNull(discovery?.wellKnown?.homeServer)
        assertNull(discovery?.wellKnown?.identityServer)
        assertTrue("Callback should be in main thread", Looper.getMainLooper().thread == callbackThread)
    }

    //   If the content cannot be parsed, then FAIL_PROMPT.
    @Test
    fun testAutoDiscoveryNotJSON() {

        //Arrange
        val malformedValue = "<html><h1>Hello world!</h1></html>"
        val mockInterceptor = MockOkHttpInterceptor()
        mockInterceptor.addRule(MockOkHttpInterceptor.SimpleRule(WELL_KNOWN_PATH, 200, malformedValue))
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

        //Assert
        mTestHelper.await(lock)
        assertNotNull(discovery)
        assertEquals(AutoDiscovery.Action.FAIL_PROMPT, discovery!!.action)
        assertNull(discovery?.wellKnown?.homeServer)
        assertNull(discovery?.wellKnown?.homeServer)
        assertTrue("Callback should be in main thread", Looper.getMainLooper().thread == callbackThread)

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
        val mockInterceptor = MockOkHttpInterceptor()
        mockInterceptor.addRule(MockOkHttpInterceptor.SimpleRule(WELL_KNOWN_PATH, 200, mockBody))
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
        assertEquals(AutoDiscovery.Action.FAIL_PROMPT, discovery!!.action)
        assertNull(discovery?.wellKnown?.homeServer)
        assertNull(discovery?.wellKnown?.identityServer)
        assertTrue("Callback should be in main thread", Looper.getMainLooper().thread == callbackThread)
    }

    // if base_url from m.homeserver is not provided, then FAIL_PROMPT.
    @Test
    fun testAutoDiscoveryMissingHSBaseURl() {

        //Arrange
        val mockBody = "{\"m.homeserver\" : {}}"
        val mockInterceptor = MockOkHttpInterceptor()
        mockInterceptor.addRule(MockOkHttpInterceptor.SimpleRule(WELL_KNOWN_PATH, 200, mockBody))
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

        //Assert
        mTestHelper.await(lock)
        assertNotNull(discovery)
        assertEquals(AutoDiscovery.Action.FAIL_PROMPT, discovery!!.action)
        assertNull(discovery?.wellKnown?.homeServer)
        assertNull(discovery?.wellKnown?.identityServer)
        assertTrue("Callback should be in main thread", Looper.getMainLooper().thread == callbackThread)

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
        val mockInterceptor = MockOkHttpInterceptor()
        mockInterceptor.addRule(MockOkHttpInterceptor.SimpleRule(WELL_KNOWN_PATH, 200, mockBody))

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

        //Assert
        mTestHelper.await(lock)
        assertNotNull(discovery)
        assertEquals(AutoDiscovery.Action.FAIL_ERROR, discovery!!.action)
        assertNull(discovery?.wellKnown?.homeServer)
        assertNull(discovery?.wellKnown?.homeServer)
        assertTrue("Callback should be in main thread", Looper.getMainLooper().thread == callbackThread)

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
        val mockInterceptor = MockOkHttpInterceptor()
        mockInterceptor.addRule(MockOkHttpInterceptor.SimpleRule(WELL_KNOWN_PATH, 200, mockBody))
        mockInterceptor.addRule(MockOkHttpInterceptor.SimpleRule(baseURl, 404))

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

        //Assert
        mTestHelper.await(lock)
        assertNotNull(discovery)
        assertEquals(AutoDiscovery.Action.FAIL_ERROR, discovery!!.action)
        assertNull(discovery?.wellKnown?.homeServer)
        assertNull(discovery?.wellKnown?.identityServer)
        assertTrue("Callback should be in main thread", Looper.getMainLooper().thread == callbackThread)
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

        val mockInterceptor = MockOkHttpInterceptor()
        mockInterceptor.addRule(MockOkHttpInterceptor.SimpleRule(WELL_KNOWN_PATH, 200, mockBody))
        mockInterceptor.addRule(MockOkHttpInterceptor.SimpleRule("$baseURl/$MX_CLIENT_VERSION_PATH", 200, hsVersionResponse))

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

        //Assert
        mTestHelper.await(lock)
        assertNotNull(discovery)
        assertEquals(AutoDiscovery.Action.PROMPT, discovery!!.action)
        assertNotNull(discovery!!.wellKnown?.homeServer)
        assertEquals(baseURl, discovery!!.wellKnown!!.homeServer?.baseURL)
        assertNull(discovery?.wellKnown?.identityServer)
        assertTrue("Callback should be in main thread", Looper.getMainLooper().thread == callbackThread)
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
        val mockInterceptor = MockOkHttpInterceptor()
        mockInterceptor.addRule(MockOkHttpInterceptor.SimpleRule(WELL_KNOWN_PATH, 200, mockBody))
        mockInterceptor.addRule(MockOkHttpInterceptor.SimpleRule("$baseURl/$MX_CLIENT_VERSION_PATH", 200, hsVersionResponse))

        RestHttpClientFactoryProvider.defaultProvider = RestClientHttpClientFactory(mockInterceptor)
        val ad = AutoDiscovery()

        //Act
        val lock = CountDownLatch(1)
        var config: AutoDiscovery.DiscoveredClientConfig? = null
        ad.findClientConfig("matrix.org", object : TestApiCallback<AutoDiscovery.DiscoveredClientConfig>(lock) {
            override fun onSuccess(info: AutoDiscovery.DiscoveredClientConfig) {
                config = info
                super.onSuccess(info)
            }
        })

        //Assert
        mTestHelper.await(lock)
        assertNotNull(config)
        assertEquals(AutoDiscovery.Action.FAIL_ERROR, config!!.action)
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

        val mockInterceptor = MockOkHttpInterceptor()
        mockInterceptor.addRule(MockOkHttpInterceptor.SimpleRule(WELL_KNOWN_PATH, 200, mockBody))
        mockInterceptor.addRule(MockOkHttpInterceptor.SimpleRule("$baseURl/$MX_CLIENT_VERSION_PATH", 200, hsVersionResponse))

        RestHttpClientFactoryProvider.defaultProvider = RestClientHttpClientFactory(mockInterceptor)
        val ad = AutoDiscovery()

        //Act
        val lock = CountDownLatch(1)
        var config: AutoDiscovery.DiscoveredClientConfig? = null
        ad.findClientConfig("matrix.org", object : TestApiCallback<AutoDiscovery.DiscoveredClientConfig>(lock) {
            override fun onSuccess(info: AutoDiscovery.DiscoveredClientConfig) {
                config = info
                super.onSuccess(info)
            }
        })

        //Assert
        mTestHelper.await(lock)
        assertNotNull(config)
        assertEquals(AutoDiscovery.Action.FAIL_ERROR, config!!.action)
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

        val mockInterceptor = MockOkHttpInterceptor()
        mockInterceptor.addRule(MockOkHttpInterceptor.SimpleRule(WELL_KNOWN_PATH, 200, mockBody))
        mockInterceptor.addRule(MockOkHttpInterceptor.SimpleRule("$baseURl/$MX_CLIENT_VERSION_PATH", 200, hsVersionResponse))

        mockInterceptor.addRule(MockOkHttpInterceptor.SimpleRule("$idServerBaseURL/$MX_ID_PATH", 404))

        RestHttpClientFactoryProvider.defaultProvider = RestClientHttpClientFactory(mockInterceptor)
        val ad = AutoDiscovery()

        //Act
        val lock = CountDownLatch(1)
        var config: AutoDiscovery.DiscoveredClientConfig? = null
        ad.findClientConfig("matrix.org", object : TestApiCallback<AutoDiscovery.DiscoveredClientConfig>(lock) {
            override fun onSuccess(info: AutoDiscovery.DiscoveredClientConfig) {
                config = info
                super.onSuccess(info)
            }
        })

        //Assert
        mTestHelper.await(lock)
        assertNotNull(config)
        assertEquals(AutoDiscovery.Action.FAIL_ERROR, config!!.action)
    }


    @Test
    fun testAutoDiscoverySuccessfull() {

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

        val mockInterceptor = MockOkHttpInterceptor()

        val idServerResponse = "{}"

        mockInterceptor.addRule(MockOkHttpInterceptor.SimpleRule(WELL_KNOWN_PATH, 200, mockBody))
        mockInterceptor.addRule(MockOkHttpInterceptor.SimpleRule("$baseURl/$MX_CLIENT_VERSION_PATH", 200, hsVersionResponse))

        mockInterceptor.addRule(MockOkHttpInterceptor.SimpleRule("$idServerBaseURL/$MX_ID_PATH", 200, idServerResponse))


        RestHttpClientFactoryProvider.defaultProvider = RestClientHttpClientFactory(mockInterceptor)
        val ad = AutoDiscovery()

        //Act
        val lock = CountDownLatch(1)
        var config: AutoDiscovery.DiscoveredClientConfig? = null
        ad.findClientConfig("matrix.org", object : TestApiCallback<AutoDiscovery.DiscoveredClientConfig>(lock) {
            override fun onSuccess(info: AutoDiscovery.DiscoveredClientConfig) {
                config = info
                super.onSuccess(info)
            }
        })

        //Assert
        mTestHelper.await(lock)
        assertNotNull(config)
        assertEquals(AutoDiscovery.Action.PROMPT, config!!.action)
        assertNotNull(config?.wellKnown?.homeServer)
        assertNotNull(config!!.wellKnown?.identityServer)
        assertEquals(baseURl, config!!.wellKnown?.homeServer?.baseURL)
        assertEquals(idServerBaseURL, config!!.wellKnown?.identityServer?.baseURL)
    }

}

