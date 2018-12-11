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

import org.matrix.androidsdk.rest.client.LoginRestClient
import org.matrix.androidsdk.rest.model.login.LocalizedFlowDataLoginTerms
import org.matrix.androidsdk.rest.model.login.RegistrationFlowResponse

/**
 * Get the public key for captcha registration
 *
 * @return public key
 */
fun getCaptchaPublicKey(registrationFlowResponse: RegistrationFlowResponse?): String? {
    var publicKey: String? = null
    registrationFlowResponse?.params?.let {
        val recaptchaParamsAsObject = it.get(LoginRestClient.LOGIN_FLOW_TYPE_RECAPTCHA)
        if (recaptchaParamsAsObject is Map<*, *>) {
            publicKey = recaptchaParamsAsObject["public_key"] as String
        }
    }
    return publicKey
}

private data class UrlAndName(val url: String, val name: String)

/**
 * This method extract the policies from the login terms parameter, regarding the user language.
 * For each policy, if user language is not found, the default language is used and if not found, the first url and name are used (not predictable)
 *
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
 *
 * @param registrationFlowResponse the registration flow to extract data from
 * @param userLanguage the user language
 * @param defaultLanguage the default language to use if the user language is not found for a policy in registrationFlowResponse
 */
fun getLocalizedLoginTerms(registrationFlowResponse: RegistrationFlowResponse?,
                           userLanguage: String = "en",
                           defaultLanguage: String = "en"): List<LocalizedFlowDataLoginTerms> {
    val result = ArrayList<LocalizedFlowDataLoginTerms>()

    try {
        registrationFlowResponse?.params?.let {
            val termsAsObject = it[LoginRestClient.LOGIN_FLOW_TYPE_TERMS]
            if (termsAsObject is Map<*, *>) {
                val policies = termsAsObject["policies"]

                if (policies is Map<*, *>) {
                    policies.keys.forEach { policyName ->
                        val localizedFlowDataLoginTerms = LocalizedFlowDataLoginTerms()
                        localizedFlowDataLoginTerms.policyName = policyName as String

                        val policy = policies[policyName]

                        // Enter this policy
                        if (policy is Map<*, *>) {
                            // Version
                            localizedFlowDataLoginTerms.version = policy["version"] as String?

                            var userLanguageUrlAndName: UrlAndName? = null
                            var defaultLanguageUrlAndName: UrlAndName? = null
                            var firstUrlAndName: UrlAndName? = null

                            // Search for language
                            policy.keys.forEach { policyKey ->
                                when (policyKey) {
                                    "version" -> Unit // Ignore
                                    userLanguage -> {
                                        // We found the data for the user language
                                        userLanguageUrlAndName = extractUrlAndName(policy[policyKey])
                                    }
                                    defaultLanguage -> {
                                        // We found default language
                                        defaultLanguageUrlAndName = extractUrlAndName(policy[policyKey])
                                    }
                                    else -> {
                                        if (firstUrlAndName == null) {
                                            // Get at least some data
                                            firstUrlAndName = extractUrlAndName(policy[policyKey])
                                        }
                                    }
                                }
                            }

                            // Copy found language data by priority
                            when {
                                userLanguageUrlAndName != null -> {
                                    localizedFlowDataLoginTerms.localizedUrl = userLanguageUrlAndName!!.url
                                    localizedFlowDataLoginTerms.localizedName = userLanguageUrlAndName!!.name
                                }
                                defaultLanguageUrlAndName != null -> {
                                    localizedFlowDataLoginTerms.localizedUrl = defaultLanguageUrlAndName!!.url
                                    localizedFlowDataLoginTerms.localizedName = defaultLanguageUrlAndName!!.name
                                }
                                firstUrlAndName != null -> {
                                    localizedFlowDataLoginTerms.localizedUrl = firstUrlAndName!!.url
                                    localizedFlowDataLoginTerms.localizedName = firstUrlAndName!!.name
                                }
                            }
                        }

                        result.add(localizedFlowDataLoginTerms)
                    }
                }
            }
        }
    } catch (e: Exception) {

    }

    return result
}

private fun extractUrlAndName(policyData: Any?): UrlAndName? {
    if (policyData is Map<*, *>) {
        val url = policyData["url"] as String?
        val name = policyData["name"] as String?

        if (url != null && name != null) {
            return UrlAndName(url, name)
        }
    }
    return null
}
