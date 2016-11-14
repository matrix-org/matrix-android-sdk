/*
 * Copyright 2016 OpenMarket Ltd
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

package org.matrix.androidsdk.crypto;


import android.text.TextUtils;

import org.matrix.androidsdk.rest.model.MatrixError;

/**
 * Represents a standard error response.
 */
public class MXCryptoError extends MatrixError {

    public static final String ENCRYPTING_NOT_ENABLE = "Encryption not enabled";
    public static final String UNABLE_TO_ENCRYPT = "Unable to encrypt";
    public static final String UNABLE_TO_DECRYPT = "Unable to decrypt";
    public static final String UNKNOWN_INBOUND_SESSION_ID = "Unknown inbound session id";
    public static final String INBOUND_SESSION_MISMATCHED_ROOM_ID = "Mismatched room_id for inbound group session";
    public static final String MISSING_FIELDS = "Missing fields in input";
    public static final String MISSING_CIPHERTEXT = "Missing ciphertext";
    public static final String NOT_INCLUDED_IN_RECIPIENTS = "Not included in recipients";
    public static final String BAD_ENCRYPTED_MESSAGE = "Bad Encrypted Message";
    public static final String MESSAGE_NOT_INTENDED_FOR_THIS_DEVICE = "Message not intended for this device";
    public static final String MESSAGE_NOT_INTENDED_FOR_THIS_ROOM = "Message not intended for this room";

    public MXCryptoError(String anErrcode) {
        errcode = anErrcode;
    }

    public MXCryptoError(String anErrcode, String anError) {
        errcode = anErrcode;
        error = anError;
    }

    @Override
    public String getLocalizedMessage() {
        if (!TextUtils.isEmpty(errcode) ) {
            if (!TextUtils.isEmpty(error)) {
                return errcode + " (" + error + ")";
            }  else {
                return errcode;
            }
        } else if (!TextUtils.isEmpty(errcode)) {
            return error;
        }

        return "";
    }

    @Override
    public String getMessage() {
        return getLocalizedMessage();
    }
}
