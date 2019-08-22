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

package org.matrix.androidsdk.features.terms

import org.matrix.androidsdk.MXSession
import org.matrix.androidsdk.RestClient
import org.matrix.androidsdk.core.Log
import org.matrix.androidsdk.core.callback.ApiCallback
import org.matrix.androidsdk.core.callback.SimpleApiCallback
import org.matrix.androidsdk.rest.client.TermsRestClient
import org.matrix.androidsdk.rest.model.sync.AccountDataElement
import org.matrix.androidsdk.rest.model.terms.TermsResponse

class TermsManager(private val mxSession: MXSession) {
    private val termsRestClient = TermsRestClient()

    enum class ServiceType {
        IntegrationManager,
        IdentityService
    }

    fun get(serviceType: ServiceType, baseUrl: String, callback: ApiCallback<GetTermsResponse>) {
        val sep = if (baseUrl.endsWith("/")) "" else "/"

        val url = when (serviceType) {
            ServiceType.IntegrationManager -> "$baseUrl$sep${RestClient.URI_INTEGRATION_MANAGER_PATH}"
            ServiceType.IdentityService    -> "$baseUrl$sep${RestClient.URI_IDENTITY_PATH_V2}"
        }

        termsRestClient.get(url, object : SimpleApiCallback<TermsResponse>(callback) {
            override fun onSuccess(info: TermsResponse) {
                callback.onSuccess(GetTermsResponse(info, getAlreadyAcceptedTermUrlsFromAccountData()))
            }
        })
    }

    fun agreeToTerms(serviceType: ServiceType, baseUrl: String, agreedUrls: List<String>, token: String, callback: ApiCallback<Unit>) {
        termsRestClient.setAccessToken(token)

        val sep = if (baseUrl.endsWith("/")) "" else "/"

        val url = when (serviceType) {
            ServiceType.IntegrationManager -> "$baseUrl$sep${RestClient.URI_INTEGRATION_MANAGER_PATH}"
            ServiceType.IdentityService    -> "$baseUrl$sep${RestClient.URI_IDENTITY_PATH_V2}"
        }

        termsRestClient.agreeToTerms(url, agreedUrls, object : SimpleApiCallback<Unit>(callback) {
            override fun onSuccess(info: Unit) {
                //client SHOULD update this account data section adding any the URLs
                // of any additional documents that the user agreed to this list.
                //Get current m.accepted_terms append new ones and update account data
                val listOfAcceptedTerms = getAlreadyAcceptedTermUrlsFromAccountData()

                val newList = listOfAcceptedTerms.toMutableSet().apply { addAll(agreedUrls) }.toList()

                mxSession.myUserId.let { userId ->
                    mxSession.accountDataRestClient?.setAccountData(
                            userId,
                            AccountDataElement.ACCOUNT_DATA_ACCEPTED_TERMS,
                            mapOf(AccountDataElement.ACCOUNT_DATA_KEY_ACCEPTED_TERMS to newList),
                            object : SimpleApiCallback<Void?>(callback) {
                                override fun onSuccess(info: Void?) {
                                    Log.d(LOG_TAG, "Account data accepted terms updated")
                                    callback.onSuccess(Unit)
                                }
                            }
                    )
                }
            }
        })
    }

    private fun getAlreadyAcceptedTermUrlsFromAccountData(): Set<String> {
        val accountDataCurrentAcceptedTerms =
                mxSession.dataHandler.store.getAccountDataElement(AccountDataElement.ACCOUNT_DATA_ACCEPTED_TERMS)

        return accountDataCurrentAcceptedTerms?.content
                ?.get(AccountDataElement.ACCOUNT_DATA_KEY_ACCEPTED_TERMS) as? Set<String> ?: emptySet()
    }

    companion object {
        private const val LOG_TAG = "TermsManager"
    }
}