/*
 * Copyright 2014 OpenMarket Ltd
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

import android.text.TextUtils;

import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.client.ThirdPidRestClient;

import java.util.UUID;

/**
 * 3 pid
 */
public class ThreePid {
    /**
     * Types of third party media.
     * The list is not exhautive and depends on the Identity server capabilities.
     */
    public static final String MEDIUM_EMAIL = "email";
    public static final String MEDIUM_MSISDN = "msisdn";

    // state
    public static final int AUTH_STATE_TOKEN_UNKNOWN = 0;
    public static final int AUTH_STATE_TOKEN_REQUESTED = 1;
    public static final int AUTH_STATE_TOKEN_RECEIVED = 2;
    public static final int AUTH_STATE_TOKEN_SUBMITTED = 3;
    public static final int AUTH_STATE_TOKEN_AUTHENTIFICATED = 4;

    /**
     *  The 3rd party system where the user is defined.
     */
    public String medium;

    /**
     * The id of the user in the 3rd party system.
     */
    public String address;

    /**
     * The current client secret key used during email validation.
     */
    public String clientSecret;

    /**
     * The current session identifier during email validation.
     */
    public String sid;

    /**
     * The number of attempts
     */
    public int sendAttempt;

    /**
     * Current validation state (AUTH_STATE_XXX)
     */
    private int mValidationState;

    /**
     * Two params constructors
     * @param anAddress the address.
     * @param aMedium the address medium
     */
    public ThreePid(String anAddress, String aMedium) {
        address = anAddress;
        medium = aMedium;
    }

    /**
     * Clear the validation parameters
     */
    private void resetValidationParameters() {
        mValidationState = AUTH_STATE_TOKEN_UNKNOWN;

        clientSecret = null;
        sendAttempt = 1;
        sid = null;
    }

    /**
     * Request a validation token.
     * @param restClient the restclient to use.
     * @param nextLink the nextLink
     * @param callback the callback when the operation is done
     */
    public void requestValidationToken(final ThirdPidRestClient restClient, String nextLink, final ApiCallback<Void> callback) {
        // sanity check
        if ((null != restClient) && (mValidationState != AUTH_STATE_TOKEN_REQUESTED)) {

            if (mValidationState != AUTH_STATE_TOKEN_UNKNOWN) {
                resetValidationParameters();
            }

            if (TextUtils.equals(medium, MEDIUM_EMAIL)) {
                clientSecret =  UUID.randomUUID().toString();
                mValidationState = AUTH_STATE_TOKEN_REQUESTED;

                restClient.requestValidationToken(address, clientSecret, sendAttempt, nextLink, new ApiCallback<RequestEmailValidationResponse>() {

                    @Override
                    public void onSuccess(RequestEmailValidationResponse requestEmailValidationResponse) {

                        if (TextUtils.equals(requestEmailValidationResponse.clientSecret, clientSecret)) {
                            mValidationState = AUTH_STATE_TOKEN_RECEIVED;
                            sid = requestEmailValidationResponse.sid;
                            callback.onSuccess(null);
                        }
                    }

                    private void commonError() {
                        sendAttempt++;
                        mValidationState = AUTH_STATE_TOKEN_UNKNOWN;
                    }

                    @Override
                    public void onNetworkError(Exception e) {
                        commonError();
                        callback.onNetworkError(e);
                    }

                    @Override
                    public void onMatrixError(MatrixError e) {
                        commonError();
                        callback.onMatrixError(e);
                    }

                    @Override
                    public void onUnexpectedError(Exception e) {
                        commonError();
                        callback.onUnexpectedError(e);
                    }
                });
            }
        }
    }
}
