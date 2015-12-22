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
package org.matrix.androidsdk.rest.client;

import android.text.TextUtils;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.matrix.androidsdk.HomeserverConnectionConfig;
import org.matrix.androidsdk.RestClient;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.rest.api.RoomsApi;
import org.matrix.androidsdk.rest.api.RoomsApiV2;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.RestAdapterCallback;
import org.matrix.androidsdk.rest.model.BannedUser;
import org.matrix.androidsdk.rest.model.CreateRoomResponse;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.Message;
import org.matrix.androidsdk.rest.model.PowerLevels;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.RoomResponse;
import org.matrix.androidsdk.rest.model.TokensChunkResponse;
import org.matrix.androidsdk.rest.model.Typing;
import org.matrix.androidsdk.rest.model.User;

import java.util.HashMap;
import java.util.List;

import retrofit.Callback;
import retrofit.client.Response;
import retrofit.http.Body;
import retrofit.http.PUT;
import retrofit.http.Path;

/**
 * Class used to make requests to the rooms API.
 */
public class RoomsRestClientV2 extends RestClient<RoomsApiV2> {

    private static final String LOG_TAG = "RoomsRestClientV2";

    /**
     * {@inheritDoc}
     */
    public RoomsRestClientV2(HomeserverConnectionConfig hsConfig) {
        super(hsConfig, RoomsApiV2.class, RestClient.URI_API_PREFIX_V2, false);
    }

    /**
     * Send a read receipt.
     * @param roomId the room id
     * @param eventId the latest event Id
     * @param callback the callback containing the created event if successful
     */
    public void sendReadReceipt(final String roomId, final String eventId, final ApiCallback<Void> callback) {
        final String description = "sendReadReceipt : roomId " + roomId + " - eventId " + eventId;

        // empty body by now
        JsonObject content = new JsonObject();

        mApi.sendReadReceipt(roomId, eventId, content, new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                try {
                    sendReadReceipt(roomId, eventId, callback);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "resend sendReadReceipt : failed " + e.getMessage());
                }
            }
        }));
    }

    /**
     * Add a tag to a room.
     * Use this method to update the order of an existing tag.
     *
     * @param roomId the roomId
     * @param tag the new tag to add to the room.
     * @param order the order.
     * @param callback the operation callback
     */
    public void addTag(final String roomId, final String tag, final Double order, final ApiCallback<Void> callback) {
        final String description = "addTag : roomId " + roomId + " - tag " + tag + " - order " + order;

        HashMap<String, Object> hashmap = new HashMap<String, Object>();
        hashmap.put("order", order);

        mApi.addTag(mCredentials.userId, roomId, tag, hashmap, new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                try {
                    addTag(roomId, tag, order, callback);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "resend addTag : failed " + e.getMessage());
                }
            }
        }));
    }

    /**
     * Remove a tag to a room.
     *
     * @param roomId the roomId
     * @param tag the new tag to add to the room.
     * @param callback the operation callback
     */
    public void removeTag(final String roomId, final String tag, final ApiCallback<Void> callback) {
        final String description = "addTag : roomId " + roomId + " - tag " + tag;

        mApi.removeTag(mCredentials.userId, roomId, tag, new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                try {
                    removeTag(roomId, tag, callback);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "resend removeTag : failed " + e.getMessage());
                }
            }
        }));
    }
}
