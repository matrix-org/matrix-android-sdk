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

package org.matrix.androidsdk.rest.model.terms

import com.google.gson.annotations.SerializedName
import org.matrix.androidsdk.rest.model.login.LocalizedFlowDataLoginTerms

/**
 * This class represent a localized privacy policy for registration Flow.
 */
data class TermsResponse(
        @JvmField
        @SerializedName("policies")
        val policies: Map<String, *>? = null
) {

    fun getLocalizedTerms(userLanguage: String,
                          defaultLanguage: String = "en"): List<LocalizedFlowDataLoginTerms> {
        return policies?.map {
            val tos = policies[it.key] as? Map<*, *> ?: return@map null
            ((tos[userLanguage] ?: tos[defaultLanguage]) as? Map<*, *>)?.let { termsMap ->
                val name = termsMap[NAME] as? String
                val url = termsMap[URL] as? String
                LocalizedFlowDataLoginTerms(
                        policyName = it.key,
                        localizedUrl = url,
                        localizedName = name,
                        version = tos[VERSION] as? String
                )
            }
        }?.filterNotNull() ?: emptyList()
    }

    companion object {
        const val VERSION = "version"
        const val NAME = "name"
        const val URL = "url"
    }
}

