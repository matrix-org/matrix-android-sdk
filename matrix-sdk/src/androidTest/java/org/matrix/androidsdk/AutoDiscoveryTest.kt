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

package org.matrix.androidsdk

import android.os.Looper
import org.junit.Assert
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.matrix.androidsdk.common.MockOkHttpInterceptor
import org.matrix.androidsdk.common.TestApiCallback
import org.matrix.androidsdk.login.AutoDiscovery
import java.util.concurrent.CountDownLatch


@FixMethodOrder(MethodSorters.JVM)
class AutoDiscoveryTest {

    @Test
    //If the returned status code is 404, then IGNORE.
    fun testAutoDiscoveryNotFound() {

        val mockInterceptor = MockOkHttpInterceptor()
        mockInterceptor.addRule(MockOkHttpInterceptor.SimpleRule(".well-known/matrix/client", 404))

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
        lock.await()
        Assert.assertNotNull(discovery)
        Assert.assertEquals(AutoDiscovery.Action.IGNORE, discovery!!.action)
        Assert.assertNull(discovery?.wellKnown?.homeServer)
        Assert.assertNull(discovery?.wellKnown?.identityServer)
        Assert.assertTrue("Callback should be in main thread", Looper.getMainLooper().thread == callbackThread)

    }

    @Test
    //If the returned status code is not 200 then FAIL_PROMPT.
    fun testAutoDiscoveryNotOK() {

        //Arrange
        val mockInterceptor = MockOkHttpInterceptor()
        mockInterceptor.addRule(MockOkHttpInterceptor.SimpleRule(".well-known/matrix/client", 500))

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
        lock.await()


        //Assert
        Assert.assertNotNull(discovery)
        Assert.assertEquals(AutoDiscovery.Action.FAIL_PROMPT, discovery!!.action)
        Assert.assertNull(discovery?.wellKnown?.homeServer)
        Assert.assertNull(discovery?.wellKnown?.identityServer)
        Assert.assertTrue("Callback should be in main thread", Looper.getMainLooper().thread == callbackThread)
    }

    @Test
    //  If the response body is empty then FAIL_PROMPT.
    fun testAutoDiscoveryEmptyBody() {

        //Arrange
        val mockInterceptor = MockOkHttpInterceptor()
        mockInterceptor.addRule(MockOkHttpInterceptor.SimpleRule(".well-known/matrix/client", 200, ""))
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
        lock.await()

        //Assert
        Assert.assertNotNull(discovery)
        Assert.assertEquals(AutoDiscovery.Action.FAIL_PROMPT, discovery!!.action)
        Assert.assertNull(discovery?.wellKnown?.homeServer)
        Assert.assertNull(discovery?.wellKnown?.identityServer)
        Assert.assertTrue("Callback should be in main thread", Looper.getMainLooper().thread == callbackThread)
    }

    @Test
    //   If the content cannot be parsed, then FAIL_PROMPT.
    fun testAutoDiscoveryNotJSON() {

        //Arrange
        val malformedValue = "<html><h1>Hello world!</h1></html>"
        val mockInterceptor = MockOkHttpInterceptor()
        mockInterceptor.addRule(MockOkHttpInterceptor.SimpleRule(".well-known/matrix/client", 200, malformedValue))
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
        lock.await()
        Assert.assertNotNull(discovery)
        Assert.assertEquals(AutoDiscovery.Action.FAIL_PROMPT, discovery!!.action)
        Assert.assertNull(discovery?.wellKnown?.homeServer)
        Assert.assertNull(discovery?.wellKnown?.homeServer)
        Assert.assertTrue("Callback should be in main thread", Looper.getMainLooper().thread == callbackThread)

    }


    @Test
    //If  m.homeserver value is not provided, then FAIL_PROMPT.
    fun testAutoDiscoveryMissingHS() {

        //Arrange
        val mockBody = "{\"m.homesorv4r\" : \"{}\"}"
        val mockInterceptor = MockOkHttpInterceptor()
        mockInterceptor.addRule(MockOkHttpInterceptor.SimpleRule(".well-known/matrix/client", 200, mockBody))
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
        lock.await()

        //Assert
        Assert.assertNotNull(discovery)
        Assert.assertEquals(AutoDiscovery.Action.FAIL_PROMPT, discovery!!.action)
        Assert.assertNull(discovery?.wellKnown?.homeServer)
        Assert.assertNull(discovery?.wellKnown?.identityServer)
        Assert.assertTrue("Callback should be in main thread", Looper.getMainLooper().thread == callbackThread)
    }

    @Test
    // if base_url from m.homeserver is not provided, then FAIL_PROMPT.
    fun testAutoDiscoveryMissingHSBaseURl() {

        //Arrange
        val mockBody = "{\"m.homeserver\" : {}}"
        val mockInterceptor = MockOkHttpInterceptor()
        mockInterceptor.addRule(MockOkHttpInterceptor.SimpleRule(".well-known/matrix/client", 200, mockBody))
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
        lock.await()
        Assert.assertNotNull(discovery)
        Assert.assertEquals(AutoDiscovery.Action.FAIL_PROMPT, discovery!!.action)
        Assert.assertNull(discovery?.wellKnown?.homeServer)
        Assert.assertNull(discovery?.wellKnown?.identityServer)
        Assert.assertTrue("Callback should be in main thread", Looper.getMainLooper().thread == callbackThread)

    }

    @Test
    // if base_url from m.homeserver is not an URL, then FAIL_ERROR.
    fun testAutoDiscoveryHSBaseURlInvalid() {

        //Arrange
        val invalidURL = "foo\$[level]/r\$[y]"
        val mockBody = "{\"m.homeserver\" : {\"base_url\" : \"$invalidURL\"}}"
        val mockInterceptor = MockOkHttpInterceptor()
        mockInterceptor.addRule(MockOkHttpInterceptor.SimpleRule(".well-known/matrix/client", 200, mockBody))

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
        lock.await()
        Assert.assertNotNull(discovery)
        Assert.assertEquals(AutoDiscovery.Action.FAIL_ERROR, discovery!!.action)
        Assert.assertNull(discovery?.wellKnown?.homeServer)
        Assert.assertNull(discovery?.wellKnown?.homeServer)
        Assert.assertTrue("Callback should be in main thread", Looper.getMainLooper().thread == callbackThread)

    }


    @Test
    // if base_url from m.homeserver is not a valid Home Server, then FAIL_ERROR.
    fun testAutoDiscoveryNotValideHSURL() {

        //Arrange
        val baseURl = "https://myhs.org"
        val mockBody = "{\"m.homeserver\" : {\"base_url\" : \"$baseURl\"}}"
        val mockInterceptor = MockOkHttpInterceptor()
        mockInterceptor.addRule(MockOkHttpInterceptor.SimpleRule(".well-known/matrix/client", 200, mockBody))
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
        lock.await()
        Assert.assertNotNull(discovery)
        Assert.assertEquals(AutoDiscovery.Action.FAIL_ERROR, discovery!!.action)
        Assert.assertNull(discovery?.wellKnown?.homeServer)
        Assert.assertNull(discovery?.wellKnown?.identityServer)
        Assert.assertTrue("Callback should be in main thread", Looper.getMainLooper().thread == callbackThread)
    }


    @Test
    fun testAutoDiscoveryHomeServerSuccess() {

        //Arrange
        val baseURl = "https://myhs.org"
        val mockBody = "{\"m.homeserver\" : {\"base_url\" : \"$baseURl\"}}"
        val mockInterceptor = MockOkHttpInterceptor()

        val hsVersionResponse = "{\"versions\": [\"r0.0.1\", \"r0.1.0\", \"r0.2.0\", \"r0.3.0\", \"r0.4.0\"]," +
                " \"unstable_features\": {\"m.lazy_load_members\": true}}"

        mockInterceptor.addRule(MockOkHttpInterceptor.SimpleRule(".well-known/matrix/client", 200, mockBody))
        mockInterceptor.addRule(MockOkHttpInterceptor.SimpleRule(baseURl + "/_matrix/client/versions", 200, hsVersionResponse))

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
        lock.await()
        Assert.assertNotNull(discovery)
        Assert.assertEquals(AutoDiscovery.Action.PROMPT, discovery!!.action)
        Assert.assertNotNull(discovery!!.wellKnown?.homeServer)
        Assert.assertEquals(baseURl, discovery!!.wellKnown!!.homeServer?.baseURL)
        Assert.assertNull(discovery?.wellKnown?.identityServer)
        Assert.assertTrue("Callback should be in main thread", Looper.getMainLooper().thread == callbackThread)
    }

    @Test
    fun testAutoDiscoveryInvalidIdServerMissingBaseURl() {

        //Arrange
        val baseURl = "https://myhs.org"
        val mockBody = "{" +
                "\"m.homeserver\" : {\"" +
                "base_url\" : \"$baseURl\"" +
                "}," +
                "\"m.identity_server\" : {}" +
                "}"
        val mockInterceptor = MockOkHttpInterceptor()
        val hsVersionResponse = "{\"versions\": [\"r0.0.1\", \"r0.1.0\", \"r0.2.0\", \"r0.3.0\", \"r0.4.0\"]," +
                " \"unstable_features\": {\"m.lazy_load_members\": true}}"

        mockInterceptor.addRule(MockOkHttpInterceptor.SimpleRule(".well-known/matrix/client", 200, mockBody))
        mockInterceptor.addRule(MockOkHttpInterceptor.SimpleRule(baseURl + "/_matrix/client/versions", 200, hsVersionResponse))

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
        lock.await()
        Assert.assertNotNull(config)
        Assert.assertEquals(AutoDiscovery.Action.FAIL_ERROR, config!!.action)
    }

    @Test
    fun testAutoDiscoveryInvalidIdServerInvalidBaseURl() {

        //Arrange
        val baseURl = "https://myhs.org"
        val idServerBaseURL = ""
        val mockBody = "{" +
                "\"m.homeserver\" : {" +
                "\"base_url\" : \"$baseURl\"" +
                "}," +
                "\"m.identity_server\" : {" +
                "\"base_url\" : \"$idServerBaseURL\"" +
                "}" +
                "}"

        val mockInterceptor = MockOkHttpInterceptor()
        val hsVersionResponse = "{\"versions\": [\"r0.0.1\", \"r0.1.0\", \"r0.2.0\", \"r0.3.0\", \"r0.4.0\"]," +
                " \"unstable_features\": {\"m.lazy_load_members\": true}}"

        mockInterceptor.addRule(MockOkHttpInterceptor.SimpleRule(".well-known/matrix/client", 200, mockBody))
        mockInterceptor.addRule(MockOkHttpInterceptor.SimpleRule(baseURl + "/_matrix/client/versions", 200, hsVersionResponse))

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
        lock.await()
        Assert.assertNotNull(config)
        Assert.assertEquals(AutoDiscovery.Action.FAIL_ERROR, config!!.action)
    }

    @Test
    fun testAutoDiscoveryInvalidIdServer() {

        //Arrange
        val baseURl = "https://myhs.org"
        val idServerBaseURL = "https://myhs.org"
        val mockBody = "{" +
                "\"m.homeserver\" : {" +
                "\"base_url\" : \"$baseURl\"" +
                "}," +
                "\"m.identity_server\" : {" +
                "\"base_url\" : \"$idServerBaseURL\"" +
                "}" +
                "}"
        val mockInterceptor = MockOkHttpInterceptor()
        val hsVersionResponse = "{\"versions\": [\"r0.0.1\", \"r0.1.0\", \"r0.2.0\", \"r0.3.0\", \"r0.4.0\"]," +
                " \"unstable_features\": {\"m.lazy_load_members\": true}}"

        mockInterceptor.addRule(MockOkHttpInterceptor.SimpleRule(".well-known/matrix/client", 200, mockBody))
        mockInterceptor.addRule(MockOkHttpInterceptor.SimpleRule(baseURl + "/_matrix/client/versions", 200, hsVersionResponse))

        mockInterceptor.addRule(MockOkHttpInterceptor.SimpleRule(idServerBaseURL + "/_matrix/identity/api/v1", 404))

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
        lock.await()
        Assert.assertNotNull(config)
        Assert.assertEquals(AutoDiscovery.Action.FAIL_ERROR, config!!.action)
    }


    @Test
    fun testAutoDiscoverySuccessfull() {

        //Arrange
        val baseURl = "https://myhs.org"
        val idServerBaseURL = "https://boom.org"
        val mockBody = "{" +
                "\"m.homeserver\" : {" +
                "\"base_url\" : \"$baseURl\"" +
                "}," +
                "\"m.identity_server\" : {" +
                "\"base_url\" : \"$idServerBaseURL\"" +
                "}" +
                "}"
        val mockInterceptor = MockOkHttpInterceptor()
        val hsVersionResponse = "{\"versions\":" +
                " [\"r0.0.1\", \"r0.1.0\", \"r0.2.0\", \"r0.3.0\", \"r0.4.0\"]," +
                " \"unstable_features\": {\"m.lazy_load_members\": true}}"

        val idServerResponse = "{}"

        mockInterceptor.addRule(MockOkHttpInterceptor.SimpleRule(".well-known/matrix/client", 200, mockBody))
        mockInterceptor.addRule(MockOkHttpInterceptor.SimpleRule(baseURl + "/_matrix/client/versions", 200, hsVersionResponse))

        mockInterceptor.addRule(MockOkHttpInterceptor.SimpleRule(idServerBaseURL + "/_matrix/identity/api/v1", 200, idServerResponse))


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
        lock.await()
        Assert.assertNotNull(config)
        Assert.assertEquals(AutoDiscovery.Action.PROMPT, config!!.action)
        Assert.assertNotNull(config?.wellKnown?.homeServer)
        Assert.assertNotNull(config!!.wellKnown?.identityServer)
        Assert.assertEquals(baseURl, config!!.wellKnown?.homeServer?.baseURL)
        Assert.assertEquals(idServerBaseURL, config!!.wellKnown?.identityServer?.baseURL)
    }


//    @Test
//    fun testLocal() {
//
//        val lock = CountDownLatch(1)
//        var config: AutoDiscovery.DiscoveredClientConfig? = null
//        val domain = "kde.org"//TestConstants.TESTS_HOME_SERVER_URL.substring("http://".length)
//        AutoDiscovery.instance.findClientConfig(domain, object : TestApiCallback<AutoDiscovery.DiscoveredClientConfig>(lock) {
//            override fun onSuccess(info: AutoDiscovery.DiscoveredClientConfig) {
//                config = info
//                super.onSuccess(info)
//            }
//        })
//        lock.await()
//
//        Assert.assertNotNull(config)
//        Assert.assertNotNull(config)
//        Assert.assertEquals(AutoDiscovery.Action.PROMPT, config!!.action)
//        Assert.assertNotNull(config?.wellKnown?.homeServer)
//        Assert.assertNotNull(config!!.wellKnown?.identityServer)
//        Assert.assertEquals("https://kde.modular.im", config!!.wellKnown?.homeServer?.baseURL)
//        Assert.assertEquals("https://vector.im", config!!.wellKnown?.identityServer?.baseURL)
//    }

}

