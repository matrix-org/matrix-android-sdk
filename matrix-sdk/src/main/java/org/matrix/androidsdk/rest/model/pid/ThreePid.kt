/*
 * Copyright 2014 OpenMarket Ltd
 * Copyright 2017 Vector Creations Ltd
 * Copyright 2018 New Vector Ltd
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
package org.matrix.androidsdk.rest.model.pid

import android.content.Context
import android.os.Parcelable
import kotlinx.android.parcel.Parcelize
import org.matrix.androidsdk.R
import java.util.*

/**
 * 3 pid
 */
@Parcelize
data class ThreePid(
        /** Types of third party media. */
        val medium: String,

        /**
         * When a client tries to add a phone number to their account, they first need to call /requestToken on the homeserver.
         * The user will get a code sent via SMS and give it to the client.
         * The code received by sms will be sent to this submit url
         */
        var submitUrl: String? = null,

        var emailAddress: String? = null,
        /**
         * The current client secret key used during email validation.
         */
        var clientSecret : String = UUID.randomUUID().toString(),

        /**
         * The phone number of the user
         * Used when MEDIUM_MSISDN
         */
        var phoneNumber: String? = null,

        var country: String? = null,

        var sid: String? = null,

        /**
         * The number of attempts
         */
        var sendAttempt: Int = 0,

        /**
         * Current validation state (AUTH_STATE_XXX)
         */
        var state: State = State.TOKEN_UNKNOWN
) : Parcelable {

    //TODO state is not really used, will be needed for resend mail?
    enum class State {
        TOKEN_UNKNOWN,
        TOKEN_REQUESTED,
        TOKEN_RECEIVED,
        TOKEN_SUBMITTED,
        TOKEN_AUTHENTIFICATED
    }


//    /**
//     * Clear the validation parameters
//     */
//    private fun resetValidationParameters() {
//        state = State.TOKEN_UNKNOWN
//        clientSecret = UUID.randomUUID().toString()
//        sendAttempt = 1
//        sid = null
//    }

    companion object {
        /**
         * Types of third party media.
         * The list is not exhaustive and depends on the Identity server capabilities.
         */
        const val MEDIUM_EMAIL = "email"
        const val MEDIUM_MSISDN = "msisdn"


        fun fromEmail(email: String) = ThreePid(MEDIUM_EMAIL).apply {
            this.emailAddress = email.toLowerCase()
        }


        fun fromPhoneNumber(msisdn: String, country: String? = null) = ThreePid(MEDIUM_MSISDN).apply {
            this.phoneNumber = msisdn
            this.country = country?.toUpperCase()
        }

        fun getMediumFriendlyName(medium: String, context: Context): String {
            var mediumFriendlyName = ""
            when (medium) {
                MEDIUM_EMAIL  -> mediumFriendlyName = context.getString(R.string.medium_email)
                MEDIUM_MSISDN -> mediumFriendlyName = context.getString(R.string.medium_phone_number)
            }

            return mediumFriendlyName
        }
    }

}
