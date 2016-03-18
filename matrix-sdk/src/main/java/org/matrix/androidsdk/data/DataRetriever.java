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

import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.client.RoomsRestClient;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.TokensChunkResponse;

import java.util.Collection;
import java.util.HashMap;

/**
 * Layer for retrieving data either from the storage implementation, or from the server if the information is not available.
 */
public class DataRetriever {

    private IMXStore mStore;
    private RoomsRestClient mRestClient;

    private HashMap<String, String> mPendingRequestTokenByRoomId = new HashMap<String, String>();
    private HashMap<String, String> mPendingRemoteRequestTokenByRoomId = new HashMap<String, String>();

    public void setStore(IMXStore store) {
        mStore = store;
    }

    public RoomsRestClient getRoomsRestClient() {
        return mRestClient;
    }

    public void setRoomsRestClient(RoomsRestClient client) {
        mRestClient = client;
    }

    /**
     * Provides the cached messages for a dedicated roomId
     * @param roomId the roomId
     * @return the events list, null if the room does not exist
     */
    public Collection<Event> getCachedRoomMessages(final String roomId) {
        return mStore.getRoomMessages(roomId);
    }

    /**
     * Cancel any history requests for a dedicated room
     * @param roomId the room id.
     */
    public void cancelHistoryRequest(String roomId) {
        clearPendingToken(mPendingRequestTokenByRoomId, roomId);
    }

    /**
     * Cancel any request history requests for a dedicated room
     * @param roomId the room id.
     */
    public void cancelRemoteHistoryRequest(String roomId) {
        clearPendingToken(mPendingRemoteRequestTokenByRoomId, roomId);
    }

    /**
     * Request messages than the given token. These will come from storage if available, from the server otherwise.
     * @param roomId the room id
     * @param token the token to go back from. Null to start from live.
     * @param direction the pagination direction
     * @param callback the onComplete callback
     */
    public void paginate(final String roomId, final String token, final Room.EventDirection direction, final ApiCallback<TokensChunkResponse<Event>> callback) {
        TokensChunkResponse<Event> storageResponse = null;

        if (Room.EventDirection.BACKWARDS == direction) {
            storageResponse = mStore.getEarlierMessages(roomId, token, RoomsRestClient.DEFAULT_MESSAGES_PAGINATION_LIMIT);
        }

        putPendingToken(mPendingRequestTokenByRoomId, roomId, token + "_" + direction);

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
                            if (TextUtils.equals(getPendingToken(mPendingRequestTokenByRoomId, roomId), token + "_" + direction)) {
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
            mRestClient.messagesFrom(roomId, token, direction, RoomsRestClient.DEFAULT_MESSAGES_PAGINATION_LIMIT, new SimpleApiCallback<TokensChunkResponse<Event>>(callback) {
                @Override
                public void onSuccess(TokensChunkResponse<Event> events) {
                    if (TextUtils.equals(getPendingToken(mPendingRequestTokenByRoomId, roomId), token + "_" + direction)) {
                        // Watch for the one event overlap
                        Event oldestEvent = mStore.getOldestEvent(roomId);

                        if (events.chunk.size() != 0) {

                            if (direction == Room.EventDirection.BACKWARDS) {
                                events.chunk.get(0).mToken = events.start;
                                events.chunk.get(events.chunk.size() - 1).mToken = events.end;

                                Event firstReturnedEvent = events.chunk.get(0);
                                if ((oldestEvent != null) && (firstReturnedEvent != null)
                                        && TextUtils.equals(oldestEvent.eventId, firstReturnedEvent.eventId)) {
                                    events.chunk.remove(0);
                                }
                            } else {
                                // TODO manage forward direction
                            }

                            mStore.storeRoomEvents(roomId, events, direction);
                        }

                        callback.onSuccess(events);
                    }
                }
            });
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

        mRestClient.messagesFrom(roomId, token, Room.EventDirection.BACKWARDS, paginationCount, new SimpleApiCallback<TokensChunkResponse<Event>>(callback) {
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
