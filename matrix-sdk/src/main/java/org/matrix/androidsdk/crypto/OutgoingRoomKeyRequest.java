/*
 * Copyright 2016 OpenMarket Ltd
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

package org.matrix.androidsdk.crypto;

import org.jetbrains.annotations.Nullable;
import org.matrix.androidsdk.rest.model.crypto.RoomKeyRequestBody;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * Represents an outgoing room key request
 * <p>
 * Keep Serializable for legacy FileStore (which will be removed in the future)
 */
public class OutgoingRoomKeyRequest implements Serializable {

    /**
     * possible states for a room key request
     * <p>
     * The state machine looks like:
     * <pre>
     *
     *      |
     *      V
     *    UNSENT  -----------------------------+
     *      |                                  |
     *      | (send successful)                | (cancellation requested)
     *      V                                  |
     *     SENT                                |
     *      |--------------------------------  |  --------------+
     *      |                                  |                |
     *      |                                  |                | (cancellation requested with intent
     *      |                                  |                | to resend a new request)
     *      | (cancellation requested)         |                |
     *      V                                  |                V
     *  CANCELLATION_PENDING                   | CANCELLATION_PENDING_AND_WILL_RESEND
     *      |                                  |                |
     *      | (cancellation sent)              |                | (cancellation sent. Create new request
     *      |                                  |                |  in the UNSENT state)
     *      V                                  |                |
     *  (deleted)  <---------------------------+----------------+
     *  </pre>
     */

    public enum RequestState {
        /**
         * request not yet sent
         */
        UNSENT,
        /**
         * request sent, awaiting reply
         */
        SENT,
        /**
         * reply received, cancellation not yet sent
         */
        CANCELLATION_PENDING,
        /**
         * Cancellation not yet sent, once sent, a new request will be done
         */
        CANCELLATION_PENDING_AND_WILL_RESEND,
        /**
         * sending failed
         */
        FAILED;

        @Nullable
        public static RequestState from(int state) {
            switch (state) {
                case 0:
                    return UNSENT;
                case 1:
                    return SENT;
                case 2:
                    return CANCELLATION_PENDING;
                case 3:
                    return CANCELLATION_PENDING_AND_WILL_RESEND;
                case 4:
                    return FAILED;
            }
            return null;
        }
    }

    // Unique id for this request. Used for both
    // an id within the request for later pairing with a cancellation, and for
    // the transaction id when sending the to_device messages to our local
    public String mRequestId;

    // transaction id for the cancellation, if any
    public String mCancellationTxnId;

    // list of recipients for the request
    public List<Map<String, String>> mRecipients;

    // RequestBody
    public RoomKeyRequestBody mRequestBody;

    // current state of this request
    public RequestState mState;

    public OutgoingRoomKeyRequest(RoomKeyRequestBody requestBody, List<Map<String, String>> recipients, String requestId, RequestState state) {
        mRequestBody = requestBody;
        mRecipients = recipients;
        mRequestId = requestId;
        mState = state;
    }

    /**
     * Used only for log.
     *
     * @return the room id.
     */
    public String getRoomId() {
        if (null != mRequestBody) {
            return mRequestBody.roomId;
        }

        return null;
    }

    /**
     * Used only for log.
     *
     * @return the session id
     */
    public String getSessionId() {
        if (null != mRequestBody) {
            return mRequestBody.sessionId;
        }

        return null;
    }
}

