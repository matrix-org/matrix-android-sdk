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

import org.matrix.androidsdk.rest.model.MatrixError;

/**
 * Represents a standard error response.
 */
public class MXCryptoError extends MatrixError {

    /**
     * Error codes
     */
    public static final String ENCRYPTING_NOT_ENABLED_ERROR_CODE= "ENCRYPTING_NOT_ENABLED";
    public static final String UNABLE_TO_ENCRYPT_ERROR_CODE = "UNABLE_TO_ENCRYPT";
    public static final String UNABLE_TO_DECRYPT_ERROR_CODE = "UNABLE_TO_DECRYPT";
    public static final String UNKNOWN_INBOUND_SESSION_ID_ERROR_CODE = "UNKNOWN_INBOUND_SESSION_ID";
    public static final String INBOUND_SESSION_MISMATCH_ROOM_ID_ERROR_CODE = "INBOUND_SESSION_MISMATCH_ROOM_ID";
    public static final String MISSING_FIELDS_ERROR_CODE = "MISSING_FIELDS";
    public static final String MISSING_CIPHER_TEXT_ERROR_CODE = "MISSING_CIPHER_TEXT";
    public static final String NOT_INCLUDE_IN_RECIPIENTS_ERROR_CODE = "NOT_INCLUDE_IN_RECIPIENTS";
    public static final String BAD_RECIPIENT_ERROR_CODE = "BAD_RECIPIENT";
    public static final String BAD_RECIPIENT_KEY_ERROR_CODE = "BAD_RECIPIENT_KEY";
    public static final String FORWARDED_MESSAGE_ERROR_CODE = "FORWARDED_MESSAGE";
    public static final String BAD_ROOM_ERROR_CODE = "BAD_ROOM";
    public static final String BAD_ENCRYPTED_MESSAGE_ERROR_CODE = "BAD_ENCRYPTED_MESSAGE";
    public static final String DUPLICATED_MESSAGE_INDEX_ERROR_CODE = "DUPLICATED_MESSAGE_INDEX";
    public static final String MISSING_PROPERTY_ERROR_CODE = "MISSING_PROPERTY";

    /**
     * Error reasons
     */
    public static final String ENCRYPTING_NOT_ENABLED_REASON = "Encryption not enabled";
    public static final String UNABLE_TO_ENCRYPT_REASON =  "Unable to encrypt %s";
    public static final String UNABLE_TO_DECRYPT_REASON =  "Unable to decrypt %1$s. Olm error: %%2$s";
    public static final String UNKNOWN_INBOUND_SESSSION_ID_REASON = "Unknown inbound session id";
    public static final String INBOUND_SESSION_MISMATCH_ROOM_ID_REASON = "Mismatched room_id for inbound group session (expected %1$s, was %2$s)";
    public static final String MISSING_FIELDS_REASON = "Missing fields in input";
    public static final String MISSING_CIPHER_TEXT_REASON = "Missing ciphertext";
    public static final String NOT_INCLUDED_IN_RECIPIENT_REASON = "Not included in recipients";
    public static final String BAD_RECIPIENT_REASON = "Message was intented for %1$s";
    public static final String BAD_RECIPIENT_KEY_REASON = "Message not intended for this device";
    public static final String FORWARDED_MESSAGE_REASON = "Message forwarded from %1$s";
    public static final String BAD_ROOM_REASON =  "Message intended for room %1$s";
    public static final String BAD_ENCRYPTED_MESSAGE_REASON = "Bad Encrypted Message";
    public static final String DUPLICATE_MESSAGE_INDEX_REASON = "Duplicate message index, possible replay attack %1$s";
    public static final String ERROR_MISSING_PROPERTY_REASON = "No '%1$s' property. Cannot prevent unknown-key attack";


    /**
     * Create a crypto error
     * @param code the error code (see XX_ERROR_CODE)
     * @param errorDescription the error description
     */
    public MXCryptoError(String code, String errorDescription) {
        errcode = code;
        error = errorDescription;
    }
}
