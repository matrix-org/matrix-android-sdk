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

import com.google.gson.internal.LinkedTreeMap
import org.matrix.androidsdk.rest.client.LoginRestClient
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
        if (recaptchaParamsAsObject is LinkedTreeMap<*, *>) {
            publicKey = recaptchaParamsAsObject["public_key"] as String
        }
    }
    return publicKey
}