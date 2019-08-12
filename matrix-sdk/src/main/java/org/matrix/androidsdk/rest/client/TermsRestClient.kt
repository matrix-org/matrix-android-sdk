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
package org.matrix.androidsdk.rest.client

import android.net.Uri
import org.matrix.androidsdk.HomeServerConnectionConfig
import org.matrix.androidsdk.RestClient
import org.matrix.androidsdk.core.callback.ApiCallback
import org.matrix.androidsdk.core.rest.DefaultRetrofit2CallbackWrapper
import org.matrix.androidsdk.rest.api.TermsApi
import org.matrix.androidsdk.rest.model.terms.AcceptTermsBody
import org.matrix.androidsdk.rest.model.terms.TermsResponse

internal class TermsRestClient :
        RestClient<TermsApi>(HomeServerConnectionConfig.Builder()
                .withHomeServerUri(Uri.parse("https://foo.bar"))
                .build(),
                TermsApi::class.java, "") {

    fun get(prefix: String, callback: ApiCallback<TermsResponse>) {
        mApi.getTerms("${prefix}terms").enqueue(DefaultRetrofit2CallbackWrapper(callback))
    }

    fun agreeToTerms(prefix: String, agreedUrls: List<String>, callback: ApiCallback<Unit>) {
        mApi.agreeToTerms("${prefix}terms", AcceptTermsBody(agreedUrls)).enqueue(DefaultRetrofit2CallbackWrapper(callback))
    }

}