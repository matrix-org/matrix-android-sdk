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

import org.matrix.androidsdk.RestClient;
import org.matrix.androidsdk.rest.client.RoomsRestClient;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.TokensChunkResponse;

/**
 * Layer for retrieving data either from the storage implementation, or from the server if the information is not available.
 */
public class DataRetriever {

    public static interface PaginationCallback {
        public void onComplete(TokensChunkResponse<Event> response);
    }

    private IMXStore mStore;
    private RoomsRestClient mRestClient;

    public void setStore(IMXStore store) {
        mStore = store;
    }

    public void setRoomsRestClient(RoomsRestClient client) {
        mRestClient = client;
    }

    public void requestRoomPagination(String roomId, String token, final PaginationCallback callback) {
        TokensChunkResponse<Event> storageResponse = mStore.getRoomEvents(roomId, token);
        if (storageResponse != null) {
            callback.onComplete(storageResponse);
        }
        else {
            mRestClient.getEarlierMessages(roomId, token, new RestClient.SimpleApiCallback<TokensChunkResponse<Event>>() {
                @Override
                public void onSuccess(TokensChunkResponse<Event> info) {
                    callback.onComplete(info);
                }
            });
        }
    }


}
