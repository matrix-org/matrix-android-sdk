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
import org.matrix.androidsdk.rest.api.WellKnownAPI
import org.matrix.androidsdk.rest.callback.RestAdapterCallback
import org.matrix.androidsdk.rest.model.WellKnown

class WellKnownRestClient :
        RestClient<WellKnownAPI>(HomeServerConnectionConfig.Builder().withHomeServerUri(Uri.parse("https://foo.bar")).build(),
                WellKnownAPI::class.java, "") {

    fun getWellKnown(domain: String, callback: ApiCallback<WellKnown>) {
        mApi.getWellKnown(domain).enqueue(RestAdapterCallback("getWellKnown", null, callback, null))
    }

}
