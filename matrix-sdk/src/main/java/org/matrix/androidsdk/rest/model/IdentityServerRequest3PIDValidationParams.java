/*
 * Copyright 2019 The Matrix.org Foundation C.I.C.
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

package org.matrix.androidsdk.rest.model;


public class IdentityServerRequest3PIDValidationParams {
    // the email address
    public String email;

    // the client secret key
    public String client_secret;

    // the attempt count
    public Integer send_attempt;

    // the nextlink (given if it is a registration process)
    public String next_link;

    public String phone_number;

    public String country;

    public static IdentityServerRequest3PIDValidationParams forEmail(String email, String clientSecret, Integer sendAttempt) {
        IdentityServerRequest3PIDValidationParams params = new IdentityServerRequest3PIDValidationParams();
        params.email = email;
        params.client_secret = clientSecret;
        params.send_attempt = sendAttempt;
        return params;
    }

    public static IdentityServerRequest3PIDValidationParams forPhoneNumber(String phoneNumber,
                                                                           String countryCode,
                                                                           String clientSecret, Integer sendAttempt) {
        IdentityServerRequest3PIDValidationParams params = new IdentityServerRequest3PIDValidationParams();
        params.phone_number = phoneNumber;
        params.country = countryCode;
        params.client_secret = clientSecret;
        params.send_attempt = sendAttempt;
        return params;
    }
}
