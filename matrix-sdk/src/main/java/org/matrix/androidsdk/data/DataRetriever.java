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

import android.util.Log;

import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.client.RoomsRestClient;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.TokensChunkResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * Layer for retrieving data either from the storage implementation, or from the server if the information is not available.
 */
public class DataRetriever {

    private IMXStore mStore;
    private RoomsRestClient mRestClient;

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
     * Request older messages than the given token. These will come from storage if available, from the server otherwise.
     * @param roomId the room id
     * @param token the token to go back from. Null to start from live.
     * @param callback the onComplete callback
     */
    public void requestRoomHistory(final String roomId, String token, final ApiCallback<TokensChunkResponse<Event>> callback) {
        final TokensChunkResponse<Event> storageResponse = mStore.getEarlierMessages(roomId, token, RoomsRestClient.DEFAULT_MESSAGES_PAGINATION_LIMIT);

        if (storageResponse != null) {
            final android.os.Handler handler = new android.os.Handler();

            // call the callback with a delay (and on the UI thread).
            // to reproduce the same behaviour as a network request. 
            Runnable r = new Runnable() {
                @Override
                public void run() {
                    handler.post(new Runnable() {
                        public void run() {
                            callback.onSuccess(storageResponse);
                        }
                    });
                }
            };

            Thread t = new Thread(r);
            t.start();
        }
        else {
            mRestClient.getEarlierMessages(roomId, token, RoomsRestClient.DEFAULT_MESSAGES_PAGINATION_LIMIT, new SimpleApiCallback<TokensChunkResponse<Event>>(callback) {
                @Override
                public void onSuccess(TokensChunkResponse<Event> info) {
                    // Watch for the one event overlap
                    Event oldestEvent = mStore.getOldestEvent(roomId);

                    if (info.chunk.size() != 0) {
                        info.chunk.get(0).mToken = info.start;
                        info.chunk.get(info.chunk.size()-1).mToken = info.end;

                        Event firstReturnedEvent = info.chunk.get(0);
                        if ((oldestEvent != null) && (firstReturnedEvent != null)
                                && oldestEvent.eventId.equals(firstReturnedEvent.eventId)) {
                            info.chunk.remove(0);
                        }
                    }

                    mStore.storeRoomEvents(roomId, info, Room.EventDirection.BACKWARDS);
                    callback.onSuccess(info);
                }
            });
        }
    }
}
