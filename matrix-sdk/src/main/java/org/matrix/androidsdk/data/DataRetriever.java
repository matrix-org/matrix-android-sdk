/* 
 * Copyright 2014 OpenMarket Ltd
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
package org.matrix.androidsdk.data;

import android.os.Looper;
import android.text.TextUtils;

import org.matrix.androidsdk.data.store.IMXStore;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.client.RoomsRestClient;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.TokensChunkResponse;
import org.matrix.androidsdk.util.Log;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Layer for retrieving data either from the storage implementation, or from the server if the information is not available.
 */
public class DataRetriever {
    private static final String LOG_TAG = DataRetriever.class.getSimpleName();

    private RoomsRestClient mRestClient;

    private final Map<String, String> mPendingForwardRequestTokenByRoomId = new HashMap<>();
    private final Map<String, String> mPendingBackwardRequestTokenByRoomId = new HashMap<>();
    private final Map<String, String> mPendingRemoteRequestTokenByRoomId = new HashMap<>();

    public RoomsRestClient getRoomsRestClient() {
        return mRestClient;
    }

    public void setRoomsRestClient(RoomsRestClient client) {
        mRestClient = client;
    }

    /**
     * Provides the cached messages for a dedicated roomId
     *
     * @param store  the store.
     * @param roomId the roomId
     * @return the events list, null if the room does not exist
     */
    public Collection<Event> getCachedRoomMessages(IMXStore store, final String roomId) {
        return store.getRoomMessages(roomId);
    }

    /**
     * Cancel any history requests for a dedicated room
     *
     * @param roomId the room id.
     */
    public void cancelHistoryRequest(String roomId) {
        Log.d(LOG_TAG, "## cancelHistoryRequest() : roomId " + roomId);

        clearPendingToken(mPendingForwardRequestTokenByRoomId, roomId);
        clearPendingToken(mPendingBackwardRequestTokenByRoomId, roomId);
    }

    /**
     * Cancel any request history requests for a dedicated room
     *
     * @param roomId the room id.
     */
    public void cancelRemoteHistoryRequest(String roomId) {
        Log.d(LOG_TAG, "## cancelRemoteHistoryRequest() : roomId " + roomId);

        clearPendingToken(mPendingRemoteRequestTokenByRoomId, roomId);
    }

    /**
     * Trigger a back pagination for a dedicated room from Token.
     *
     * @param store    the store to use
     * @param roomId   the room Id
     * @param token    the start token.
     * @param limit    the maximum number of messages to retrieve
     * @param callback the callback
     */
    public void backPaginate(final IMXStore store, final String roomId, final String token,
                             final int limit, final ApiCallback<TokensChunkResponse<Event>> callback) {
        // reach the marker end
        if (TextUtils.equals(token, Event.PAGINATE_BACK_TOKEN_END)) {
            // nothing more to provide
            final android.os.Handler handler = new android.os.Handler(Looper.getMainLooper());

            // call the callback with a delay
            // to reproduce the same behaviour as a network request.
            // except for the initial request.
            Runnable r = new Runnable() {
                @Override
                public void run() {
                    handler.postDelayed(new Runnable() {
                        public void run() {
                            callback.onSuccess(new TokensChunkResponse<Event>());
                        }
                    }, 0);
                }
            };

            handler.post(r);

            return;
        }

        Log.d(LOG_TAG, "## backPaginate() : starts for roomId " + roomId);

        TokensChunkResponse<Event> storageResponse = store.getEarlierMessages(roomId, token, limit);

        putPendingToken(mPendingBackwardRequestTokenByRoomId, roomId, token);

        if (storageResponse != null) {
            final android.os.Handler handler = new android.os.Handler(Looper.getMainLooper());
            final TokensChunkResponse<Event> fStorageResponse = storageResponse;

            Log.d(LOG_TAG, "## backPaginate() : some data has been retrieved into the local storage (" + fStorageResponse.chunk.size() + " events)");

            // call the callback with a delay
            // to reproduce the same behaviour as a network request.
            // except for the initial request.
            Runnable r = new Runnable() {
                @Override
                public void run() {
                    handler.postDelayed(new Runnable() {
                        public void run() {
                            String expectedToken = getPendingToken(mPendingBackwardRequestTokenByRoomId, roomId);
                            Log.d(LOG_TAG, "## backPaginate() : local store roomId " + roomId + " token " + token + " vs " + expectedToken);

                            if (TextUtils.equals(expectedToken, token)) {
                                clearPendingToken(mPendingBackwardRequestTokenByRoomId, roomId);
                                callback.onSuccess(fStorageResponse);
                            }
                        }
                    }, 0);
                }
            };

            Thread t = new Thread(r);
            t.start();
        } else {
            Log.d(LOG_TAG, "## backPaginate() : trigger a remote request");
            mRestClient.getRoomMessagesFrom(roomId, token, EventTimeline.Direction.BACKWARDS, limit,
                    new SimpleApiCallback<TokensChunkResponse<Event>>(callback) {
                @Override
                public void onSuccess(TokensChunkResponse<Event> events) {
                    String expectedToken = getPendingToken(mPendingBackwardRequestTokenByRoomId, roomId);

                    Log.d(LOG_TAG, "## backPaginate() succeeds : roomId " + roomId + " token " + token + " vs " + expectedToken);

                    if (TextUtils.equals(expectedToken, token)) {
                        clearPendingToken(mPendingBackwardRequestTokenByRoomId, roomId);

                        // Watch for the one event overlap
                        Event oldestEvent = store.getOldestEvent(roomId);

                        if (events.chunk.size() != 0) {
                            events.chunk.get(0).mToken = events.start;

                            // there is no more data on server side
                            if (null == events.end) {
                                events.end = Event.PAGINATE_BACK_TOKEN_END;
                            }

                            events.chunk.get(events.chunk.size() - 1).mToken = events.end;

                            Event firstReturnedEvent = events.chunk.get(0);
                            if ((oldestEvent != null) && (firstReturnedEvent != null)
                                    && TextUtils.equals(oldestEvent.eventId, firstReturnedEvent.eventId)) {
                                events.chunk.remove(0);
                            }

                            store.storeRoomEvents(roomId, events, EventTimeline.Direction.BACKWARDS);
                        }

                        Log.d(LOG_TAG, "## backPaginate() succeed : roomId " + roomId + " token " + token + " got " + events.chunk.size());
                        callback.onSuccess(events);
                    }
                }

                private void logErrorMessage(String expectedToken, String errorMessage) {
                    Log.e(LOG_TAG, "## backPaginate() failed : roomId " + roomId + " token " + token + " expected " + expectedToken + " with " + errorMessage);
                }

                @Override
                public void onNetworkError(Exception e) {
                    String expectedToken = getPendingToken(mPendingBackwardRequestTokenByRoomId, roomId);
                    logErrorMessage(expectedToken, e.getMessage());

                    // dispatch only if it is expected
                    if (TextUtils.equals(token, expectedToken)) {
                        clearPendingToken(mPendingBackwardRequestTokenByRoomId, roomId);
                        callback.onNetworkError(e);
                    }
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    String expectedToken = getPendingToken(mPendingBackwardRequestTokenByRoomId, roomId);
                    logErrorMessage(expectedToken, e.getMessage());

                    // dispatch only if it is expected
                    if (TextUtils.equals(token, expectedToken)) {
                        clearPendingToken(mPendingBackwardRequestTokenByRoomId, roomId);
                        callback.onMatrixError(e);
                    }
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    String expectedToken = getPendingToken(mPendingBackwardRequestTokenByRoomId, roomId);
                    logErrorMessage(expectedToken, e.getMessage());

                    // dispatch only if it is expected
                    if (TextUtils.equals(token, expectedToken)) {
                        clearPendingToken(mPendingBackwardRequestTokenByRoomId, roomId);
                        callback.onUnexpectedError(e);
                    }
                }
            });
        }
    }

    /**
     * Trigger a forward pagination for a dedicated room from Token.
     *
     * @param store    the store to use
     * @param roomId   the room Id
     * @param token    the start token.
     * @param callback the callback
     */
    private void forwardPaginate(final IMXStore store, final String roomId, final String token, final ApiCallback<TokensChunkResponse<Event>> callback) {
        putPendingToken(mPendingForwardRequestTokenByRoomId, roomId, token);

        mRestClient.getRoomMessagesFrom(roomId, token, EventTimeline.Direction.FORWARDS, RoomsRestClient.DEFAULT_MESSAGES_PAGINATION_LIMIT,
                new SimpleApiCallback<TokensChunkResponse<Event>>(callback) {
            @Override
            public void onSuccess(TokensChunkResponse<Event> events) {
                if (TextUtils.equals(getPendingToken(mPendingForwardRequestTokenByRoomId, roomId), token)) {
                    clearPendingToken(mPendingForwardRequestTokenByRoomId, roomId);
                    store.storeRoomEvents(roomId, events, EventTimeline.Direction.FORWARDS);
                    callback.onSuccess(events);
                }
            }
        });
    }

    /**
     * Request messages than the given token. These will come from storage if available, from the server otherwise.
     *
     * @param store     the store to use
     * @param roomId    the room id
     * @param token     the token to go back from. Null to start from live.
     * @param direction the pagination direction
     * @param callback  the onComplete callback
     */
    public void paginate(final IMXStore store, final String roomId, final String token,
                         final EventTimeline.Direction direction, final ApiCallback<TokensChunkResponse<Event>> callback) {
        if (direction == EventTimeline.Direction.BACKWARDS) {
            backPaginate(store, roomId, token, RoomsRestClient.DEFAULT_MESSAGES_PAGINATION_LIMIT, callback);
        } else {
            forwardPaginate(store, roomId, token, callback);
        }
    }

    /**
     * Request events to the server. The local cache is not used.
     * The events will not be saved in the local storage.
     *
     * @param roomId          the room id
     * @param token           the token to go back from.
     * @param paginationCount the number of events to retrieve.
     * @param callback        the onComplete callback
     */
    public void requestServerRoomHistory(final String roomId,
                                         final String token,
                                         final int paginationCount,
                                         final ApiCallback<TokensChunkResponse<Event>> callback) {
        putPendingToken(mPendingRemoteRequestTokenByRoomId, roomId, token);

        mRestClient.getRoomMessagesFrom(roomId, token, EventTimeline.Direction.BACKWARDS, paginationCount,
                new SimpleApiCallback<TokensChunkResponse<Event>>(callback) {
            @Override
            public void onSuccess(TokensChunkResponse<Event> info) {

                if (TextUtils.equals(getPendingToken(mPendingRemoteRequestTokenByRoomId, roomId), token)) {
                    if (info.chunk.size() != 0) {
                        info.chunk.get(0).mToken = info.start;
                        info.chunk.get(info.chunk.size() - 1).mToken = info.end;
                    }

                    clearPendingToken(mPendingRemoteRequestTokenByRoomId, roomId);
                    callback.onSuccess(info);
                }
            }
        });
    }

    //==============================================================================================================
    // Pending token management
    //==============================================================================================================

    /**
     * Clear token for a dedicated room
     *
     * @param dict   the token cache
     * @param roomId the room id
     */
    private void clearPendingToken(Map<String, String> dict, String roomId) {
        Log.d(LOG_TAG, "## clearPendingToken() : roomId " + roomId);

        if (null != roomId) {
            synchronized (dict) {
                dict.remove(roomId);
            }
        }
    }

    /**
     * Get the pending token for a dedicated room
     *
     * @param dict   the token cache
     * @param roomId the room Id
     * @return the token
     */
    private String getPendingToken(Map<String, String> dict, String roomId) {
        String expectedToken = "Not a valid token";

        synchronized (dict) {
            // token == null is a valid value
            if (dict.containsKey(roomId)) {
                expectedToken = dict.get(roomId);

                if (TextUtils.isEmpty(expectedToken)) {
                    expectedToken = null;
                }
            }
        }
        Log.d(LOG_TAG, "## getPendingToken() : roomId " + roomId + " token " + expectedToken);

        return expectedToken;
    }

    /**
     * Store a token for a dedicated room
     *
     * @param dict   the token cache
     * @param roomId the room id
     * @param token  the token
     */
    private void putPendingToken(Map<String, String> dict, String roomId, String token) {
        Log.d(LOG_TAG, "## putPendingToken() : roomId " + roomId + " token " + token);

        synchronized (dict) {
            // null is allowed for a request
            if (null == token) {
                dict.put(roomId, "");
            } else {
                dict.put(roomId, token);
            }
        }
    }
}
