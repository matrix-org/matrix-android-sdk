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

import android.util.Log;

import com.google.gson.JsonObject;

import org.matrix.androidsdk.RestClient;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.listeners.IMXNetworkEventListener;
import org.matrix.androidsdk.rest.api.RoomsApi;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.RestAdapterCallback;
import org.matrix.androidsdk.rest.model.BannedUser;
import org.matrix.androidsdk.rest.model.CreateRoomResponse;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.ImageMessage;
import org.matrix.androidsdk.rest.model.Message;
import org.matrix.androidsdk.rest.model.PowerLevels;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.RoomResponse;
import org.matrix.androidsdk.rest.model.TokensChunkResponse;
import org.matrix.androidsdk.rest.model.Typing;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.rest.model.login.Credentials;

import java.util.List;

import retrofit.client.Response;

/**
 * Class used to make requests to the rooms API.
 */
public class RoomsRestClient extends RestClient<RoomsApi> {

    private static final String LOG_TAG = "RoomsRestClient";
    protected static final int MESSAGES_PAGINATION_LIMIT = 20;

    /**
     * {@inheritDoc}
     */
    public RoomsRestClient(Credentials credentials) {
        super(credentials, RoomsApi.class, RestClient.URI_API_PREFIX);
    }

    /**
     * Send a message to a room.
     * @param roomId the room id
     * @param message the message
     * @param callback the callback containing the created event if successful
     */
    public void sendMessage(String roomId, Message message, ApiCallback<Event> callback) {
        // the messages have their dedicated method in MXSession to be resent if there is no avaliable network
        mApi.sendMessage(roomId, message, new RestAdapterCallback<Event>(callback, null));
    }

    /**
     * Send a message to a room.
     * @param roomId the room id
     * @param eventType the type of event
     * @param content the event content
     * @param callback the callback containing the created event if successful
     */
    public void sendEvent(final String roomId, final String eventType, final JsonObject content, final ApiCallback<Event> callback) {

        mApi.send(roomId, eventType, content, new RestAdapterCallback<Event>(callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onNetworkFailed() {
                final IMXNetworkEventListener listener = new IMXNetworkEventListener() {
                    @Override
                    public void onNetworkConnectionUpdate(boolean isConnected) {
                        Log.e(LOG_TAG, "resend sendEvent " + roomId);

                        try {
                            sendEvent(roomId, eventType, content, callback);
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "resend sendEvent : failed " + e.getMessage());
                        }
                    }
                };

                // add to the network listener until it gets a data connection
                mNetworkConnectivityReceiver.addOnConnectedEventListener(listener);
            }
        }));
    }

    /**
     * Get the last messages for the given room.
     * @param roomId the room id
     * @param callback the callback called with the response. Messages will be returned in reverse order.
     */
    public void getLatestRoomMessages(final String roomId, final ApiCallback<TokensChunkResponse<Event>> callback) {
        mApi.messages(roomId, "b", MESSAGES_PAGINATION_LIMIT, new RestAdapterCallback<TokensChunkResponse<Event>>(callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onNetworkFailed() {
                final IMXNetworkEventListener listener = new IMXNetworkEventListener() {
                    @Override
                    public void onNetworkConnectionUpdate(boolean isConnected) {
                        Log.e(LOG_TAG, "resend getLatestRoomMessages " + roomId);
                        try {
                            getLatestRoomMessages(roomId, callback);
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "resend getLatestRoomMessages : failed " + e.getMessage());
                        }
                    }
                };

                // add to the network listener until it gets a data connection
                mNetworkConnectivityReceiver.addOnConnectedEventListener(listener);
            }
        }));
    }

    /**
     * Get messages for the given room starting from the given token.
     * @param roomId the room id
     * @param fromToken the token identifying the message to start from
     * @param callback the callback called with the response. Messages will be returned in reverse order.
     */
    public void getEarlierMessages(final String roomId, final String fromToken, final ApiCallback<TokensChunkResponse<Event>> callback) {
        mApi.messagesFrom(roomId, "b", fromToken, MESSAGES_PAGINATION_LIMIT, new RestAdapterCallback<TokensChunkResponse<Event>>(callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onNetworkFailed() {
                final IMXNetworkEventListener listener = new IMXNetworkEventListener() {
                    @Override
                    public void onNetworkConnectionUpdate(boolean isConnected) {
                        Log.e(LOG_TAG, "resend getLatestRoomMessages " + roomId);
                        try {
                            getEarlierMessages(roomId, fromToken, callback);
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "resend getLatestRoomMessages : failed " + e.getMessage());
                        }
                    }
                };

                // add to the network listener until it gets a data connection
                mNetworkConnectivityReceiver.addOnConnectedEventListener(listener);
            }
        }));
    }

    /**
     * Get the list of members for the given room.
     * @param roomId the room id
     * @param callback the async callback
     */
    public void getRoomMembers(final String roomId, final ApiCallback<List<RoomMember>> callback) {
        mApi.members(roomId, new RestAdapterCallback<TokensChunkResponse<RoomMember>>(callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onNetworkFailed() {
                final IMXNetworkEventListener listener = new IMXNetworkEventListener() {
                    @Override
                    public void onNetworkConnectionUpdate(boolean isConnected) {
                        Log.e(LOG_TAG, "resend getRoomMembers " + roomId);

                        try {
                            getRoomMembers(roomId, callback);
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "resend getRoomMembers : failed " + e.getMessage());
                        }
                    }
                };

                // add to the network listener until it gets a data connection
                mNetworkConnectivityReceiver.addOnConnectedEventListener(listener);
            }
        }) {
            @Override
            public void success(TokensChunkResponse<RoomMember> messageTokensChunkResponse, Response response) {
                try {
                    callback.onSuccess(messageTokensChunkResponse.chunk);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "getRoomMembers Exception " + e.getMessage());
                }
            }
        });
    }

    /**
     * Get the list of members for the given room.
     * @param roomId the room id
     * @param callback the async callback
     */
    public void getRoomState(final String roomId, final ApiCallback<List<Event>> callback) {
        mApi.state(roomId, new RestAdapterCallback<List<Event>>(callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onNetworkFailed() {
                final IMXNetworkEventListener listener = new IMXNetworkEventListener() {
                    @Override
                    public void onNetworkConnectionUpdate(boolean isConnected) {
                        Log.e(LOG_TAG, "resend getRoomState " + roomId);

                        try {
                            getRoomState(roomId, callback);
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "resend getRoomState : failed " + e.getMessage());
                        }
                    }
                };

                // add to the network listener until it gets a data connection
                mNetworkConnectivityReceiver.addOnConnectedEventListener(listener);
            }
        }));
    }

    /**
     * Invite a user to a room.
     * @param roomId the room id
     * @param userId the user id
     * @param callback the async callback
     */
    public void inviteToRoom(final String roomId, final String userId, final ApiCallback<Void> callback) {
        User user = new User();
        user.userId = userId;
        mApi.invite(roomId, user, new RestAdapterCallback<Void>(callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onNetworkFailed() {
                final IMXNetworkEventListener listener = new IMXNetworkEventListener() {
                    @Override
                    public void onNetworkConnectionUpdate(boolean isConnected) {
                        Log.e(LOG_TAG, "resend inviteToRoom " + roomId);
                        try {
                            inviteToRoom(roomId, userId, callback);
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "resend inviteToRoom : failed " + e.getMessage());
                        }
                    }
                };

                // add to the network listener until it gets a data connection
                mNetworkConnectivityReceiver.addOnConnectedEventListener(listener);
            }
        }));
    }

    /**
     * Join a room.
     * @param roomId the room id
     * @param callback the async callback
     */
    public void joinRoom(final String roomId, final ApiCallback<Void> callback) {
        mApi.join(roomId, new JsonObject(), new RestAdapterCallback<Void>(callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onNetworkFailed() {
                final IMXNetworkEventListener listener = new IMXNetworkEventListener() {
                    @Override
                    public void onNetworkConnectionUpdate(boolean isConnected) {
                        Log.e(LOG_TAG, "resend joinRoom " + roomId);

                        try {
                            joinRoom(roomId, callback);
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "resend joinRoom : failed " + e.getMessage());
                        }
                    }
                };

                // add to the network listener until it gets a data connection
                mNetworkConnectivityReceiver.addOnConnectedEventListener(listener);
            }
        }));
    }

    /**
     * Join a room by its roomAlias or its roomId
     * @param roomId_Alias the room id or the room alias
     * @param callback the async callback
     */
    public void joinRoomByAlias(final String roomId_Alias, final ApiCallback<RoomResponse> callback) {
        mApi.joinRoomByAlias(roomId_Alias, new RestAdapterCallback<RoomResponse>(callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onNetworkFailed() {
                final IMXNetworkEventListener listener = new IMXNetworkEventListener() {
                    @Override
                    public void onNetworkConnectionUpdate(boolean isConnected) {
                        Log.e(LOG_TAG, "resend joinRoomByAlias " + roomId_Alias);

                        try {
                            joinRoomByAlias(roomId_Alias, callback);
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "resend joinRoomByAlias : failed " + e.getMessage());
                        }
                    }
                };

                // add to the network listener until it gets a data connection
                mNetworkConnectivityReceiver.addOnConnectedEventListener(listener);
            }
        }));
    }

    /**
     * Leave a room.
     * @param roomId the room id
     * @param callback the async callback
     */
    public void leaveRoom(final String roomId, final ApiCallback<Void> callback) {
        mApi.leave(roomId, new JsonObject(), new RestAdapterCallback<Void>(callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onNetworkFailed() {
                final IMXNetworkEventListener listener = new IMXNetworkEventListener() {
                    @Override
                    public void onNetworkConnectionUpdate(boolean isConnected) {
                        Log.e(LOG_TAG, "resend leaveRoom " + roomId);

                        try {
                            leaveRoom(roomId, callback);
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "resend leaveRoom : failed " + e.getMessage());
                        }
                    }
                };

                // add to the network listener until it gets a data connection
                mNetworkConnectivityReceiver.addOnConnectedEventListener(listener);
            }
        }));
    }

    /**
     * Kick a user from a room.
     * @param roomId the room id
     * @param userId the user id
     * @param callback the async callback
     */
    public void kickFromRoom(final String roomId, final String userId, final ApiCallback<Void> callback) {
        // Kicking is done by posting that the user is now in a "leave" state
        RoomMember member = new RoomMember();
        member.membership = RoomMember.MEMBERSHIP_LEAVE;

        mApi.roomMember(roomId, userId, member, new RestAdapterCallback<Void>(callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onNetworkFailed() {
                final IMXNetworkEventListener listener = new IMXNetworkEventListener() {
                    @Override
                    public void onNetworkConnectionUpdate(boolean isConnected) {
                        Log.e(LOG_TAG, "resend kickFromRoom " + roomId);

                        try {
                            kickFromRoom(roomId, userId, callback);
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "resend kickFromRoom : failed " + e.getMessage());
                        }
                    }
                };

                // add to the network listener until it gets a data connection
                mNetworkConnectivityReceiver.addOnConnectedEventListener(listener);
            }
        }));
    }

    /**
     * Ban a user from a room.
     * @param roomId the room id
     * @param user the banned user object (userId and reason for ban)
     * @param callback the async callback
     */
    public void banFromRoom(final String roomId, final BannedUser user, final ApiCallback<Void> callback) {
        mApi.ban(roomId, user, new RestAdapterCallback<Void>(callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onNetworkFailed() {
                final IMXNetworkEventListener listener = new IMXNetworkEventListener() {
                    @Override
                    public void onNetworkConnectionUpdate(boolean isConnected) {
                        Log.e(LOG_TAG, "resend banFromRoom " + roomId);

                        try {
                            banFromRoom(roomId, user, callback);
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "resend banFromRoom : failed " + e.getMessage());
                        }
                    }
                };

                // add to the network listener until it gets a data connection
                mNetworkConnectivityReceiver.addOnConnectedEventListener(listener);
            }
        }));
    }

    /**
     * Create a new room.
     * @param name the room name
     * @param topic the room topic
     * @param visibility the room visibility
     * @param alias an optional room alias
     * @param callback the async callback
     */
    public void createRoom(final String name, final String topic, final String visibility, final String alias, final ApiCallback<CreateRoomResponse> callback) {

        RoomState roomState = new RoomState();
        roomState.name = name;
        roomState.topic = topic;
        roomState.visibility = visibility;
        roomState.roomAliasName = alias;

        mApi.createRoom(roomState, new RestAdapterCallback<CreateRoomResponse>(callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onNetworkFailed() {
                final IMXNetworkEventListener listener = new IMXNetworkEventListener() {
                    @Override
                    public void onNetworkConnectionUpdate(boolean isConnected) {
                        Log.e(LOG_TAG, "resend createRoom " + name);

                        try {
                            createRoom(name, topic, visibility, alias, callback);
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "resend createRoom failed " + e.getMessage());
                        }
                    }
                };

                // add to the network listener until it gets a data connection
                mNetworkConnectivityReceiver.addOnConnectedEventListener(listener);
            }
        }));
    }

    /**
     * Perform an initial sync on the room
     * @param roomId the room id
     * @param callback the async callback
     */
    public void initialSync(final String roomId, final ApiCallback<RoomResponse> callback) {
        mApi.initialSync(roomId, MESSAGES_PAGINATION_LIMIT, new RestAdapterCallback<RoomResponse>(callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onNetworkFailed() {
                final IMXNetworkEventListener listener = new IMXNetworkEventListener() {
                    @Override
                    public void onNetworkConnectionUpdate(boolean isConnected) {
                        Log.e(LOG_TAG, "resend initialSync " + roomId);

                        try {
                            initialSync(roomId, callback);
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "resend initialSync failed " + e.getMessage());
                        }
                    }
                };

                // add to the network listener until it gets a data connection
                mNetworkConnectivityReceiver.addOnConnectedEventListener(listener);
            }
        }));
    }

    /**
     * Update the room name.
     * @param roomId the room id
     * @param name the room name
     * @param callback the async callback
     */
    public void updateName(final String roomId, final String name, final ApiCallback<Void> callback) {
        RoomState roomState = new RoomState();
        roomState.name = name;

        mApi.roomName(roomId, roomState, new RestAdapterCallback<Void>(callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onNetworkFailed() {
                final IMXNetworkEventListener listener = new IMXNetworkEventListener() {
                    @Override
                    public void onNetworkConnectionUpdate(boolean isConnected) {
                        Log.e(LOG_TAG, "resend updateName " + roomId);

                        try {
                            updateName(roomId, name, callback);
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "resend updateName failed " + e.getMessage());
                        }
                    }
                };

                // add to the network listener until it gets a data connection
                mNetworkConnectivityReceiver.addOnConnectedEventListener(listener);
            }
        }));
    }

    /**
     * Update the room topic.
     * @param roomId the room id
     * @param topic the room topic
     * @param callback the async callback
     */
    public void updateTopic(final String roomId, final String topic, final ApiCallback<Void> callback) {
        RoomState roomState = new RoomState();
        roomState.topic = topic;

        mApi.roomTopic(roomId, roomState, new RestAdapterCallback<Void>(callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onNetworkFailed() {
                final IMXNetworkEventListener listener = new IMXNetworkEventListener() {
                    @Override
                    public void onNetworkConnectionUpdate(boolean isConnected) {
                        Log.e(LOG_TAG, "resend updateTopic " + roomId);

                        try {
                            updateTopic(roomId, topic, callback);
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "resend updateTopic failed " + e.getMessage());
                        }
                    }
                };

                // add to the network listener until it gets a data connection
                mNetworkConnectivityReceiver.addOnConnectedEventListener(listener);
            }
        }));
    }

    /**
     * Redact an event.
     * @param roomId the room id
     * @param eventId the event id
     * @param callback the callback containing the created event if successful
     */
    public void redact(final String roomId, final String eventId, final ApiCallback<Event> callback) {
        mApi.redact(roomId, eventId, new JsonObject(), new RestAdapterCallback<Event>(callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onNetworkFailed() {
                final IMXNetworkEventListener listener = new IMXNetworkEventListener() {
                    @Override
                    public void onNetworkConnectionUpdate(boolean isConnected) {
                        Log.e(LOG_TAG, "resend redact " + roomId);

                        try {
                            redact(roomId, eventId, callback);
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "resend redact failed " + e.getMessage());
                        }
                    }
                };

                // add to the network listener until it gets a data connection
                mNetworkConnectivityReceiver.addOnConnectedEventListener(listener);
            }
        }));
    }

    /**
     * Update the power levels.
     * @param roomId the room id
     * @param powerLevels the new powerLevels
     * @param callback the async callback
     */
    public void updatePowerLevels(final String roomId, final PowerLevels powerLevels, final ApiCallback<Void> callback) {
        mApi.powerLevels(roomId, powerLevels, new RestAdapterCallback<Void>(callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onNetworkFailed() {
                final IMXNetworkEventListener listener = new IMXNetworkEventListener() {
                    @Override
                    public void onNetworkConnectionUpdate(boolean isConnected) {
                        Log.e(LOG_TAG, "resend updatePowerLevels " + roomId);

                        try {
                            updatePowerLevels(roomId, powerLevels, callback);
                        } catch (Exception e) {
                            Log.e(LOG_TAG, "resend updatePowerLevels failed " + e.getMessage());
                        }
                    }
                };

                // add to the network listener until it gets a data connection
                mNetworkConnectivityReceiver.addOnConnectedEventListener(listener);
            }
        }));
    }

    /**
     * send typing notification
     * @param roomId the room id
     * @param userId the user id
     *
     * @param callback the async callback
     */
    public void sendTypingNotification(String roomId, String userId, boolean isTyping, int timeout,  ApiCallback<Void> callback) {
        Typing typing = new Typing();
        typing.typing = isTyping;

        if (-1 != timeout) {
            typing.timeout = timeout;
        }

        // never resend typing on network error
        mApi.typing(roomId, userId, typing, new RestAdapterCallback<Void>(callback, null));
    }
}
