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
package org.matrix.androidsdk.data;

import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.client.RoomsRestClient;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.TokensChunkResponse;

import java.util.Collection;
import java.util.EmptyStackException;
import java.util.HashMap;

/**
 * Layer for retrieving data either from the storage implementation, or from the server if the information is not available.
 */
public class DataRetriever {
    private RoomsRestClient mRestClient;

    private HashMap<String, String> mPendingFordwardRequestTokenByRoomId = new HashMap<String, String>();
    private HashMap<String, String> mPendingBackwardRequestTokenByRoomId = new HashMap<String, String>();
    private HashMap<String, String> mPendingRemoteRequestTokenByRoomId = new HashMap<String, String>();

    public RoomsRestClient getRoomsRestClient() {
        return mRestClient;
    }

    public void setRoomsRestClient(RoomsRestClient client) {
        mRestClient = client;
    }

    /**
     * Provides the cached messages for a dedicated roomId
     * @param store the store.
     * @param roomId the roomId
     * @return the events list, null if the room does not exist
     */
    public Collection<Event> getCachedRoomMessages(IMXStore store, final String roomId) {
        return store.getRoomMessages(roomId);
    }

    /**
     * Cancel any history requests for a dedicated room
     * @param roomId the room id.
     */
    public void cancelHistoryRequest(String roomId) {
        clearPendingToken(mPendingFordwardRequestTokenByRoomId, roomId);
        clearPendingToken(mPendingBackwardRequestTokenByRoomId, roomId);
    }

    /**
     * Cancel any request history requests for a dedicated room
     * @param roomId the room id.
     */
    public void cancelRemoteHistoryRequest(String roomId) {
        clearPendingToken(mPendingRemoteRequestTokenByRoomId, roomId);
    }

    /**
     * Trigger a back pagination for a dedicated room from Token.
     * @param roomId the room Id
     * @param token the start token.
     * @param callback the callback
     */
    private void backPaginate(final IMXStore store, final String roomId, final String token, final ApiCallback<TokensChunkResponse<Event>> callback) {
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
                    }, 100);
                }
            };

            return;
        }

        TokensChunkResponse<Event> storageResponse = store.getEarlierMessages(roomId, token, RoomsRestClient.DEFAULT_MESSAGES_PAGINATION_LIMIT);

        putPendingToken(mPendingBackwardRequestTokenByRoomId, roomId, token);

        if (storageResponse != null) {
            final android.os.Handler handler = new android.os.Handler(Looper.getMainLooper());
            final TokensChunkResponse<Event> fStorageResponse = storageResponse;

            // call the callback with a delay
            // to reproduce the same behaviour as a network request.
            // except for the initial request.
            Runnable r = new Runnable() {
                @Override
                public void run() {
                    handler.postDelayed(new Runnable() {
                        public void run() {
                            if (TextUtils.equals(getPendingToken(mPendingBackwardRequestTokenByRoomId, roomId), token)) {
                                callback.onSuccess(fStorageResponse);
                            }
                        }
                    }, (null == token) ? 0 : 100);
                }
            };

            Thread t = new Thread(r);
            t.start();
        }
        else {
            mRestClient.messagesFrom(roomId, token, EventTimeline.Direction.BACKWARDS, RoomsRestClient.DEFAULT_MESSAGES_PAGINATION_LIMIT, new SimpleApiCallback<TokensChunkResponse<Event>>(callback) {
                @Override
                public void onSuccess(TokensChunkResponse<Event> events) {
                    if (TextUtils.equals(getPendingToken(mPendingBackwardRequestTokenByRoomId, roomId), token)) {
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

                        callback.onSuccess(events);
                    }
                }
            });
        }
    }

    /**
     * Trigger a forward pagination for a dedicated room from Token.
     * @param roomId the room Id
     * @param token the start token.
     * @param callback the callback
     */
    private void forwardPaginate(final IMXStore store, final String roomId, final String token, final ApiCallback<TokensChunkResponse<Event>> callback) {
        putPendingToken(mPendingFordwardRequestTokenByRoomId, roomId, token);

        mRestClient.messagesFrom(roomId, token, EventTimeline.Direction.FORWARDS, RoomsRestClient.DEFAULT_MESSAGES_PAGINATION_LIMIT, new SimpleApiCallback<TokensChunkResponse<Event>>(callback) {
            @Override
            public void onSuccess(TokensChunkResponse<Event> events) {
                if (TextUtils.equals(getPendingToken(mPendingFordwardRequestTokenByRoomId, roomId), token)) {
                    store.storeRoomEvents(roomId, events, EventTimeline.Direction.FORWARDS);
                    callback.onSuccess(events);
                }
            }
        });
    }

    /**
     * Request messages than the given token. These will come from storage if available, from the server otherwise.
     * @param roomId the room id
     * @param token the token to go back from. Null to start from live.
     * @param direction the pagination direction
     * @param callback the onComplete callback
     */
    public void paginate(final IMXStore store, final String roomId, final String token, final EventTimeline.Direction direction, final ApiCallback<TokensChunkResponse<Event>> callback) {
       if (direction == EventTimeline.Direction.BACKWARDS) {
           backPaginate(store, roomId, token, callback);
       } else {
           forwardPaginate(store, roomId, token, callback);
       }
    }

    /**
     * Request events to the server. The local cache is not used.
     * The events will not be saved in the local storage.
     * @param roomId the room id
     * @param token the token to go back from.
     * @param paginationCount the number of events to retrieve.
     * @param callback the onComplete callback
     */
    public void requestServerRoomHistory(final String roomId, final String token, final int paginationCount, final ApiCallback<TokensChunkResponse<Event>> callback) {
        putPendingToken(mPendingRemoteRequestTokenByRoomId, roomId, token);

        mRestClient.messagesFrom(roomId, token, EventTimeline.Direction.BACKWARDS, paginationCount, new SimpleApiCallback<TokensChunkResponse<Event>>(callback) {
            @Override
            public void onSuccess(TokensChunkResponse<Event> info) {

                if (TextUtils.equals(getPendingToken(mPendingRemoteRequestTokenByRoomId, roomId), token)){
                    if (info.chunk.size() != 0) {
                        info.chunk.get(0).mToken = info.start;
                        info.chunk.get(info.chunk.size() - 1).mToken = info.end;
                    }
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
     * @param dict the token cache
     * @param roomId the room id
     */
    private void clearPendingToken(HashMap<String, String> dict, String roomId) {
        if (null != roomId) {
            synchronized (dict) {
                dict.remove(roomId);
            }
        }
    }

    /**
     * Get the pending token for a dedicated room
     * @param dict the token cache
     * @param roomId the room Id
     * @return the token
     */
    private String getPendingToken(HashMap<String, String> dict, String roomId) {
        String expectedToken = "Not a valid token";

        synchronized (dict) {
            // token == null is a valid value
            if(dict.containsKey(roomId)) {
                expectedToken = dict.get(roomId);

                if (TextUtils.isEmpty(expectedToken)) {
                    expectedToken = null;
                }
            }
            dict.remove(roomId);
        }

        return expectedToken;
    }

    /**
     * Store a token for a dedicated room
     * @param dict the token cache
     * @param roomId the room id
     * @param token the token
     */
    private void putPendingToken(HashMap<String, String> dict, String roomId, String token) {
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
