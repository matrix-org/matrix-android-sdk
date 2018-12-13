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

package org.matrix.androidsdk.login

import org.junit.Assert.*
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import org.matrix.androidsdk.rest.model.login.LocalizedFlowDataLoginTerms
import org.matrix.androidsdk.rest.model.login.RegistrationFlowResponse

@FixMethodOrder(MethodSorters.JVM)
class RegistrationToolsTest {

    @Test
    fun getLocalizedLoginTerms_defaultParam_en() {
        assertEn(getLocalizedLoginTerms(createRegistrationFlowResponse()))
    }

    @Test
    fun getLocalizedLoginTerms_en_en() {
        assertEn(getLocalizedLoginTerms(createRegistrationFlowResponse(), "en"))
    }

    @Test
    fun getLocalizedLoginTerms_en_en_en() {
        assertEn(getLocalizedLoginTerms(createRegistrationFlowResponse(), "en", "en"))
    }

    @Test
    fun getLocalizedLoginTerms_fr_default_fr() {
        assertFr(getLocalizedLoginTerms(createRegistrationFlowResponse(), "fr"))
    }

    @Test
    fun getLocalizedLoginTerms_fr_en_fr() {
        assertFr(getLocalizedLoginTerms(createRegistrationFlowResponse(), "fr", "en"))
    }

    @Test
    fun getLocalizedLoginTerms_fr_fr_fr() {
        assertFr(getLocalizedLoginTerms(createRegistrationFlowResponse(), "fr", "fr"))
    }

    @Test
    fun getLocalizedLoginTerms_de_en() {
        // Test not available language
        assertEn(getLocalizedLoginTerms(createRegistrationFlowResponse(), "de"))
    }

    @Test
    fun getLocalizedLoginTerms_de_en_en() {
        // Test not available language
        assertEn(getLocalizedLoginTerms(createRegistrationFlowResponse(), "de", "en"))
    }

    @Test
    fun getLocalizedLoginTerms_de_fr_fr() {
        // Test not available language, with default to fr
        assertFr(getLocalizedLoginTerms(createRegistrationFlowResponse(), "de", "fr"))
    }

    @Test
    fun getLocalizedLoginTerms_de_de_en() {
        // Test not available language, with not available default -> First language
        assertEn(getLocalizedLoginTerms(createRegistrationFlowResponse(), "de", "de"))
    }

    @Test
    fun getLocalizedLoginTerms_empty_policies_empty() {
        val registrationFlowResponse = RegistrationFlowResponse()
                .apply {
                    params =
                            mapOf("m.login.terms" to
                                    mapOf("policies" to
                                            emptyMap<String, String>()
                                    )
                            )
                }

        assertTrue(getLocalizedLoginTerms(registrationFlowResponse).isEmpty())
    }

    @Test
    fun getLocalizedLoginTerms_two_policies() {
        val registrationFlowResponse = RegistrationFlowResponse()
                .apply {
                    params =
                            mapOf("m.login.terms" to
                                    mapOf("policies" to
                                            mapOf("policy1" to
                                                    mapOf("version" to "1.0",
                                                            "en" to mapOf(
                                                                    "url" to "http:url_en",
                                                                    "name" to "name_en")
                                                    ),
                                                    "policy2" to
                                                            mapOf("version" to "2.0",
                                                                    "en" to mapOf(
                                                                            "url" to "http:url_en2",
                                                                            "name" to "name_en2")
                                                            )
                                            )
                                    )
                            )
                }

        getLocalizedLoginTerms(registrationFlowResponse).let { result ->
            assertEquals(2, result.size)

            result[0].let {
                assertEquals("policy1", it.policyName)
                assertEquals("1.0", it.version)
                assertEquals("name_en", it.localizedName)
                assertEquals("http:url_en", it.localizedUrl)
            }

            result[1].let {
                assertEquals("policy2", it.policyName)
                assertEquals("2.0", it.version)
                assertEquals("name_en2", it.localizedName)
                assertEquals("http:url_en2", it.localizedUrl)
            }
        }
    }

    /* ==========================================================================================
     * Private
     * ========================================================================================== */

    /**
     * Example of Data:
     * <pre>
     * "m.login.terms": {
     *       "policies": {
     *         "privacy_policy": {
     *           "version": "1.0",
     *           "en": {
     *             "url": "http:\/\/matrix.org\/_matrix\/consent?v=1.0",
     *             "name": "Terms and Conditions"
     *           }
     *         }
     *       }
     *     }
     *</pre>
     */
    private fun createRegistrationFlowResponse() = RegistrationFlowResponse()
            .apply {
                params =
                        mapOf("m.login.terms" to
                                mapOf("policies" to
                                        mapOf("policy1" to
                                                mapOf("version" to "1.0",
                                                        "en" to mapOf(
                                                                "url" to "http:url_en",
                                                                "name" to "name_en"),
                                                        "fr" to mapOf(
                                                                "url" to "http:url_fr",
                                                                "name" to "name_fr")
                                                )
                                        )
                                )
                        )
            }

    private fun assertEn(localizedLoginTerms: List<LocalizedFlowDataLoginTerms>) {
        localizedLoginTerms.let { result ->
            assertEquals(1, result.size)

            result.first().let {
                assertEquals("policy1", it.policyName)
                assertEquals("1.0", it.version)
                assertEquals("name_en", it.localizedName)
                assertEquals("http:url_en", it.localizedUrl)
            }
        }
    }

    private fun assertFr(localizedLoginTerms: List<LocalizedFlowDataLoginTerms>) {
        localizedLoginTerms.let { result ->
            assertEquals(1, result.size)

            result.first().let {
                assertEquals("policy1", it.policyName)
                assertEquals("1.0", it.version)
                assertEquals("name_fr", it.localizedName)
                assertEquals("http:url_fr", it.localizedUrl)
            }
        }
    }
}