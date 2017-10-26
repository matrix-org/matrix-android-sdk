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

import android.os.Handler;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.crypto.data.MXUsersDevicesMap;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.util.Log;

import org.matrix.androidsdk.data.cryptostore.IMXCryptoStore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MXOutgoingRoomKeyRequestManager {
    private static final String LOG_TAG = MXOutgoingRoomKeyRequestManager.class.getSimpleName();

    private static final int SEND_KEY_REQUESTS_DELAY_MS = 500;

    // the linked session
    private MXSession mSession;

    // crypto
    private MXCrypto mCrypto;

    // working handler (should not be the UI thread)
    private Handler mWorkingHandler;

    // store
    private IMXCryptoStore mCryptoStore;

    // running
    public boolean mClientRunning;

    // transaction counter
    private int mTxnCtr;

    // sanity check to ensure that we don't end up with two concurrent runs
    // of mSendOutgoingRoomKeyRequestsTimer
    private boolean mSendOutgoingRoomKeyRequestsRunning;

    /**
     * Constructor
     *
     * @param session the session
     */
    public MXOutgoingRoomKeyRequestManager(MXSession session) {
        mSession = session;
        mCrypto = mSession.getCrypto();
        mWorkingHandler = mCrypto.getEncryptingThreadHandler();
        mCryptoStore = mCrypto.getCryptoStore();
    }

    /**
     * Called when the client is started. Sets background processes running.
     */
    public void start() {
        mClientRunning = true;
        startTimer();
    }

    /**
     * Called when the client is stopped. Stops any running background processes.
     */
    public void stop() {
        mClientRunning = false;
    }

    /**
     * Make up a new transaction id
     *
     * @return {string} a new, unique, transaction id
     */
    private String makeTxnId() {
        return "m" + System.currentTimeMillis() + "." + mTxnCtr++;
    }

    /**
     * Send off a room key request, if we haven't already done so.
     * <p>
     * The `requestBody` is compared (with a deep-equality check) against
     * previous queued or sent requests and if it matches, no change is made.
     * Otherwise, a request is added to the pending list, and a job is started
     * in the background to send it.
     *
     * @param requestBody requestBody
     * @param recipients  recipients
     */
    public void sendRoomKeyRequest(final Map<String, String> requestBody, final List<Map<String, String>> recipients) {
        mWorkingHandler.post(new Runnable() {
            @Override
            public void run() {
                OutgoingRoomKeyRequest req = mCryptoStore.getOrAddOutgoingRoomKeyRequest(
                        new OutgoingRoomKeyRequest(requestBody, recipients, makeTxnId(), OutgoingRoomKeyRequest.RequestState.UNSENT));


                if (req.mState == OutgoingRoomKeyRequest.RequestState.UNSENT) {
                    startTimer();
                }
            }
        });
    }

    /**
     * Start the background timer to send queued requests, if the timer isn't already running.
     */
    private void startTimer() {
        if (mSendOutgoingRoomKeyRequestsRunning) {
            return;
        }

        mWorkingHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mWorkingHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mSendOutgoingRoomKeyRequestsRunning) {
                            Log.e(LOG_TAG, "## startTimer() : RoomKeyRequestSend already in progress!");
                        }

                        mSendOutgoingRoomKeyRequestsRunning = true;
                        sendOutgoingRoomKeyRequests();
                    }
                });
            }
        }, SEND_KEY_REQUESTS_DELAY_MS);
    }

    // look for and send any queued requests. Runs itself recursively until
    // there are no more requests, or there is an error (in which case, the
    // timer will be restarted before the promise resolves).
    private void sendOutgoingRoomKeyRequests() {
        if (!this.mClientRunning) {
            mSendOutgoingRoomKeyRequestsRunning = false;
            return;
        }

        Log.d(LOG_TAG, "## sendOutgoingRoomKeyRequests() :  Looking for queued outgoing room key requests");
        OutgoingRoomKeyRequest outgoingRoomKeyRequest = mCryptoStore.getOutgoingRoomKeyRequestByState(OutgoingRoomKeyRequest.RequestState.UNSENT);

        if (null == outgoingRoomKeyRequest) {
            Log.e(LOG_TAG, "## sendOutgoingRoomKeyRequests() : No more outgoing room key requests");
            return;
        }

        sendOutgoingRoomKeyRequest(outgoingRoomKeyRequest);
    }

    // given a RoomKeyRequest, send it and update the request record

    /**
     * Send the outgoing key request.
     *
     * @param request the request
     */
    private void sendOutgoingRoomKeyRequest(OutgoingRoomKeyRequest request) {
        Log.d(LOG_TAG, "## sendOutgoingRoomKeyRequest() : Requesting keys " + request.mRequestBody + " from " + request.mRecipients + " id " + request.mRequestId);

        Map<String, Object> requestMessage = new HashMap<>();
        requestMessage.put("action", "request");
        requestMessage.put("requesting_device_id", mCryptoStore.getDeviceId());
        requestMessage.put("request_id", request.mRequestId);
        requestMessage.put("body", request.mRequestBody);

        sendMessageToDevices(requestMessage, request.mRecipients, request.mRequestId);
    }

    /**
     * Send a RoomKeyRequest to a list of recipients
     *
     * @param message
     * @param recipients
     * @param txnId
     */
    private void sendMessageToDevices(final Map<String, Object> message, final List<Map<String, String>> recipients, final String txnId) {
        MXUsersDevicesMap<Map<String, Object>> contentMap = new MXUsersDevicesMap<>();

        for (Map<String, String> recipient : recipients) {
            contentMap.setObject(message, recipient.get("userId"), recipient.get("deviceId"));
        }

        Log.d(LOG_TAG, "## sendMessageToDevices starts");
        mSession.getCryptoRestClient().sendToDevice(Event.EVENT_TYPE_ROOM_KEY_REQUEST, contentMap, new ApiCallback<Void>() {
            private void onDone(final OutgoingRoomKeyRequest.RequestState state) {
                mWorkingHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mSendOutgoingRoomKeyRequestsRunning = false;
                        mCryptoStore.updateOutgoingRoomKeyRequest(txnId, OutgoingRoomKeyRequest.RequestState.UNSENT, state);
                        startTimer();
                    }
                });
            }

            @Override
            public void onSuccess(Void info) {
                Log.d(LOG_TAG, "## sendMessageToDevices succeed");
                onDone(OutgoingRoomKeyRequest.RequestState.SENT);
            }

            @Override
            public void onNetworkError(Exception e) {
                Log.e(LOG_TAG, "## sendMessageToDevices failed " + e.getMessage());
                onDone(OutgoingRoomKeyRequest.RequestState.FAILED);
            }

            @Override
            public void onMatrixError(MatrixError e) {
                Log.e(LOG_TAG, "## sendMessageToDevices failed " + e.getMessage());
                onDone(OutgoingRoomKeyRequest.RequestState.FAILED);
            }

            @Override
            public void onUnexpectedError(Exception e) {
                Log.e(LOG_TAG, "## sendMessageToDevices failed " + e.getMessage());
                onDone(OutgoingRoomKeyRequest.RequestState.FAILED);
            }
        });
    }
}
