/* 
 * Copyright 2016 OpenMarket Ltd
 * Copyright 2017 Vector Creations Ltd
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
import org.matrix.androidsdk.util.Log;

import com.google.gson.JsonObject;

import org.matrix.androidsdk.HomeserverConnectionConfig;
import org.matrix.androidsdk.RestClient;
import org.matrix.androidsdk.data.EventTimeline;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.rest.api.RoomsApi;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.RestAdapterCallback;
import org.matrix.androidsdk.rest.model.BannedUser;
import org.matrix.androidsdk.rest.model.CreateRoomResponse;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.EventContext;
import org.matrix.androidsdk.rest.model.Message;
import org.matrix.androidsdk.rest.model.PowerLevels;
import org.matrix.androidsdk.rest.model.ReportContentParams;
import org.matrix.androidsdk.rest.model.RoomAliasDescription;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.RoomResponse;
import org.matrix.androidsdk.rest.model.TokensChunkResponse;
import org.matrix.androidsdk.rest.model.Typing;
import org.matrix.androidsdk.rest.model.User;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Class used to make requests to the rooms API.
 */
public class RoomsRestClient extends RestClient<RoomsApi> {

    private static final String LOG_TAG = "RoomsRestClient";
    public static final int DEFAULT_MESSAGES_PAGINATION_LIMIT = 30;

    /**
     * {@inheritDoc}
     */
    public RoomsRestClient(HomeserverConnectionConfig hsConfig) {
        super(hsConfig, RoomsApi.class, RestClient.URI_API_PREFIX_PATH_R0, false);
    }

    /**
     * Send a message to room
     * @param transactionId the unique transaction id (it should avoid duplicated messages)
     * @param roomId the room id
     * @param message the message
     * @param callback the callback containing the created event if successful
     */
    public void sendMessage(final String transactionId, final String roomId, final Message message, final ApiCallback<Event> callback) {
        // privacy
        // final String description = "SendMessage : roomId " + roomId + " - message " + message.body;
        final String description = "SendMessage : roomId " + roomId;

        // the messages have their dedicated method in MXSession to be resent if there is no available network
        mApi.sendMessage(transactionId, roomId, message).enqueue(new RestAdapterCallback<Event>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                try {
                    sendMessage(transactionId, roomId, message, callback);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "resend sendMessage : failed " + e.getLocalizedMessage());
                }
            }
        }));
    }

    /**
     * Send an event to a room.
     * @param transactionId the unique transaction id (it should avoid duplicated messages)
     * @param roomId the room id
     * @param eventType the type of event
     * @param content the event content
     * @param callback the callback containing the created event if successful
     */
    public void sendEventToRoom(final String transactionId, final String roomId, final String eventType, final JsonObject content, final ApiCallback<Event> callback) {
        // privacy
        //final String description = "sendEvent : roomId " + roomId + " - eventType " + eventType + " content " + content;
        final String description = "sendEvent : roomId " + roomId + " - eventType " + eventType;

        mApi.send(transactionId, roomId, eventType, content).enqueue(new RestAdapterCallback<Event>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                try {
                    sendEventToRoom(transactionId, roomId, eventType, content, callback);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "resend sendEvent : failed " + e.getLocalizedMessage());
                }
            }
        }));
    }

    /**
     * Get a limited amount of messages, for the given room starting from the given token. The amount of message is set to {@link #DEFAULT_MESSAGES_PAGINATION_LIMIT}.
     * @param roomId the room id
     * @param fromToken the token identifying the message to start from
     * @param direction the direction
     * @param limit the maximum number of messages to retrieve.
     * @param callback the callback called with the response. Messages will be returned in reverse order.
     */
    public void getRoomMessagesFrom(final String roomId, final String fromToken, final EventTimeline.Direction direction,  final int limit, final ApiCallback<TokensChunkResponse<Event>> callback) {
        final String description = "messagesFrom : roomId " + roomId + " fromToken " + fromToken + "with direction " + direction +  " with limit " + limit;

        mApi.getRoomMessagesFrom(roomId, (direction == EventTimeline.Direction.BACKWARDS) ? "b" : "f", fromToken, limit).enqueue(new RestAdapterCallback<TokensChunkResponse<Event>>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                try {
                    getRoomMessagesFrom(roomId, fromToken, direction, limit, callback);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "resend messagesFrom : failed " + e.getLocalizedMessage());
                }
            }
        }));
    }
    
    /**
     * Invite a user to a room.
     * @param roomId the room id
     * @param userId the user id
     * @param callback the async callback
     */
    public void inviteUserToRoom(final String roomId, final String userId, final ApiCallback<Void> callback) {
        final String description = "inviteToRoom : roomId " + roomId + " userId " + userId;

        User user = new User();
        user.user_id = userId;
        mApi.invite(roomId, user).enqueue(new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                try {
                    inviteUserToRoom(roomId, userId, callback);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "resend inviteToRoom : failed " + e.getLocalizedMessage());
                }
            }
        }));
    }

    /**
     * Invite a user by his email address to a room.
     * @param roomId the room id
     * @param email the email
     * @param callback the async callback
     */
    public void inviteByEmailToRoom(final String roomId, final String email, final ApiCallback<Void> callback) {
        inviteThreePidToRoom("email", email, roomId, callback);
    }

    /**
     * Invite an user from a 3Pids.
     * @param medium the medium
     * @param address the address
     * @param roomId the room id
     * @param callback the async callback
     */
    private void inviteThreePidToRoom(final String medium, final String address, final String roomId, final ApiCallback<Void> callback) {
        // privacy
        //final String description = "inviteThreePidToRoom : medium " + medium + " address " + address + " roomId " + roomId;
        final String description = "inviteThreePidToRoom : medium " + medium + " roomId " + roomId;

        // This request must not have the protocol part
        String identityServer = mHsConfig.getIdentityServerUri().toString();

        if (identityServer.startsWith("http://")) {
            identityServer = identityServer.substring("http://".length());
        } else if (identityServer.startsWith("https://")) {
            identityServer = identityServer.substring("https://".length());
        }

        HashMap<String, String> parameters = new HashMap<>();
        parameters.put("id_server", identityServer);
        parameters.put("medium", medium);
        parameters.put("address", address);

        mApi.invite(roomId, parameters).enqueue(new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                try {
                    inviteThreePidToRoom(medium, address, roomId, callback);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "resend inviteThreePidToRoom : failed " + e.getLocalizedMessage());
                }
            }
        }));
    }

    /**
     * Join a room by its roomAlias or its roomId.
     * @param roomIdOrAlias the room id or the room alias
     * @param callback the async callback
     */
    public void joinRoom(final String roomIdOrAlias, final ApiCallback<RoomResponse> callback) {
        joinRoom(roomIdOrAlias, null, callback);
    }

    /**
     * Join a room by its roomAlias or its roomId with some parameters.
     * @param roomIdOrAlias the room id or the room alias
     * @param params the joining parameters.
     * @param callback the async callback
     */
    public void joinRoom(final String roomIdOrAlias, final HashMap<String, Object> params, final ApiCallback<RoomResponse> callback) {
        final String description = "joinRoom : roomId " + roomIdOrAlias;

        mApi.joinRoomByAliasOrId(roomIdOrAlias, (null == params) ? new HashMap<String, Object>() : params).enqueue(new RestAdapterCallback<RoomResponse>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                try {
                    joinRoom(roomIdOrAlias, params, callback);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "resend joinRoomByAlias : failed " + e.getLocalizedMessage());
                }
            }
        }));
    }

    /**
     * Leave a room.
     * @param roomId the room id
     * @param callback the async callback
     */
    public void leaveRoom(final String roomId, final ApiCallback<Void> callback) {
        final String description = "leaveRoom : roomId " + roomId;

        mApi.leave(roomId, new JsonObject()).enqueue(new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                try {
                    leaveRoom(roomId, callback);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "resend leaveRoom : failed " + e.getLocalizedMessage());
                }
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
        final String description = "kickFromRoom : roomId " + roomId + " userId " + userId;

        // Kicking is done by posting that the user is now in a "leave" state
        RoomMember member = new RoomMember();
        member.membership = RoomMember.MEMBERSHIP_LEAVE;

        mApi.updateRoomMember(roomId, userId, member).enqueue(new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                try {
                    kickFromRoom(roomId, userId, callback);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "resend kickFromRoom : failed " + e.getLocalizedMessage());
                }
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
        final String description = "banFromRoom : roomId " + roomId + " userId " + user.userId;

        mApi.ban(roomId, user).enqueue(new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                try {
                    banFromRoom(roomId, user, callback);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "resend banFromRoom : failed " + e.getLocalizedMessage());
                }
            }
        }));
    }

    /**
     * Unban an user from a room.
     * @param roomId the room id
     * @param user the banned user (userId)
     * @param callback the async callback
     */
    public void unbanFromRoom(final String roomId, final BannedUser user, final ApiCallback<Void> callback) {
        final String description = "Unban : roomId " + roomId + " userId " + user.userId;

        mApi.unban(roomId, user).enqueue(new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                try {
                    unbanFromRoom(roomId, user, callback);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "resend unbanFromRoom : failed " + e.getLocalizedMessage());
                }
            }
        }));
    }

    /**
     * Create a new room.
     * @param name the room name
     * @param topic the room topic
     * @param visibility the room visibility
     * @param alias an optional room alias
     * @param guestAccess the guest access rule (see {@link RoomState#GUEST_ACCESS_CAN_JOIN} or {@link RoomState#GUEST_ACCESS_FORBIDDEN})
     * @param historyVisibility the history visibility
     * @param callback the async callback
     */
    public void createRoom(final String name, final String topic, final String visibility, final String alias, final String guestAccess, final String historyVisibility, final ApiCallback<CreateRoomResponse> callback) {
        // privacy
        //final String description = "createRoom : name " + name + " topic " + topic;
        final String description = "createRoom";

        RoomState roomState = new RoomState();
        // avoid empty strings
        // The server does not always response when a string is empty
        // replace them by null
        roomState.name = TextUtils.isEmpty(name) ? null : name;
        roomState.topic = TextUtils.isEmpty(topic) ? null : topic;
        roomState.visibility = visibility;
        roomState.roomAliasName = TextUtils.isEmpty(alias) ? null : alias;
        roomState.guest_access = TextUtils.isEmpty(guestAccess) ? null : guestAccess;
        roomState.history_visibility = TextUtils.isEmpty(historyVisibility) ? null : historyVisibility;

        mApi.createRoom(roomState).enqueue(new RestAdapterCallback<CreateRoomResponse>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                try {
                    createRoom(name, topic, visibility, alias, guestAccess, historyVisibility, callback);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "resend createRoom failed " + e.getLocalizedMessage());
                }
            }
        }));
    }

    /**
     * Create a new room.
     * @param parameters the room creation parameters
     * @param callback the async callback
     */
    public void createRoom(final Map<String, Object> parameters, final ApiCallback<CreateRoomResponse> callback) {
        // privacy
        //final String description = "createRoom : name " + name + " topic " + topic;
        final String description = "createRoom";

        mApi.createRoom(parameters).enqueue(new RestAdapterCallback<CreateRoomResponse>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                try {
                    createRoom(parameters, callback);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "resend createRoom failed " + e.getLocalizedMessage());
                }
            }
        }));
    }

    /**
     * Perform an initial sync on the room
     * @param roomId the room id
     * @param callback the async callback
     */
    public void initialSync(final String roomId, final ApiCallback<RoomResponse> callback) {
        final String description = "initialSync : roomId " + roomId;

        mApi.initialSync(roomId, DEFAULT_MESSAGES_PAGINATION_LIMIT).enqueue(new RestAdapterCallback<RoomResponse>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                try {
                    initialSync(roomId, callback);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "resend initialSync failed " + e.getLocalizedMessage());
                }
            }
        }));
    }

    /**
     * Get the context surrounding an event.
     * @param roomId the room id
     * @param eventId the event Id
     * @param limit the maximum number of messages to retrieve
     * @param callback the asynchronous callback called with the response
     */
    public void getContextOfEvent(final String roomId, final String eventId, final int limit, final ApiCallback<EventContext> callback) {
        final String description = "getContextOfEvent : roomId " + roomId + " eventId " + eventId + " limit " + limit;

        mApi.getContextOfEvent(roomId, eventId, limit).enqueue(new RestAdapterCallback<EventContext>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                try {
                    getContextOfEvent(roomId, eventId, limit, callback);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "resend getContextOfEvent failed " + e.getLocalizedMessage());
                }
            }
        }));
    }

    /**
     * Update the room name.
     * @param roomId the room id
     * @param name the room name
     * @param callback the async callback
     */
    public void updateRoomName(final String roomId, final String name, final ApiCallback<Void> callback) {
        final String description = "updateName : roomId " + roomId + " name " + name;

        RoomState roomState = new RoomState();
        roomState.name = name;

        mApi.setRoomName(roomId, roomState).enqueue(new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                try {
                    updateRoomName(roomId, name, callback);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "resend updateName failed " + e.getLocalizedMessage());
                }
            }
        }));
    }

    /**
     * Update the room name.
     * @param roomId the room id
     * @param canonicalAlias the canonical alias
     * @param callback the async callback
     */
    public void updateCanonicalAlias(final String roomId, final String canonicalAlias, final ApiCallback<Void> callback) {
        final String description = "updateCanonicalAlias : roomId " + roomId + " canonicalAlias " + canonicalAlias;

        RoomState roomState = new RoomState();
        roomState.alias = canonicalAlias;

        mApi.setCanonicalAlias(roomId, roomState).enqueue(new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                try {
                    updateCanonicalAlias(roomId, canonicalAlias, callback);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "resend updateCanonicalAlias failed " + e.getLocalizedMessage());
                }
            }
        }));
    }

    /**
     * Update the room name.
     * @param roomId the room id
     * @param aVisibility the visibility
     * @param callback the async callback
     */
    public void updateHistoryVisibility(final String roomId, final String aVisibility, final ApiCallback<Void> callback) {
        final String description = "updateHistoryVisibility : roomId " + roomId + " visibility " + aVisibility;

        RoomState roomState = new RoomState();
        roomState.history_visibility = aVisibility;

        mApi.setHistoryVisibility(roomId, roomState).enqueue(new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                try {
                    updateHistoryVisibility(roomId, aVisibility, callback);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "resend updateHistoryVisibility failed " + e.getLocalizedMessage());
                }
            }
        }));
    }

    /**
     * Update the directory visibility of the room.
     * @param aRoomId the room id
     * @param aDirectoryVisibility the visibility of the room in the directory list
     * @param callback the async callback response
     */
    public void updateDirectoryVisibility(final String aRoomId, final String aDirectoryVisibility, final ApiCallback<Void> callback) {
        final String description = "updateRoomDirectoryVisibility : roomId=" + aRoomId + " visibility=" + aDirectoryVisibility;

        RoomState roomState = new RoomState();
        roomState.visibility = aDirectoryVisibility;

        mApi.setRoomDirectoryVisibility(aRoomId, roomState).enqueue(new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                try {
                    updateDirectoryVisibility(aRoomId, aDirectoryVisibility, callback);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "resend updateHistoryVisibility failed " + e.getLocalizedMessage());
                }
            }
        }));
    }


    /**
     * Get the directory visibility of the room (see {@link #updateDirectoryVisibility(String, String, ApiCallback)}).
     * @param aRoomId the room ID
     * @param callback on success callback containing a RoomState object populated with the directory visibility
     */
    public void getDirectoryVisibility(final String aRoomId, final ApiCallback<RoomState> callback) {
        final String description = "getRoomDirectoryVisibility userId=" + aRoomId;

        mApi.getRoomDirectoryVisibility(aRoomId).enqueue(new RestAdapterCallback<RoomState>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                getDirectoryVisibility(aRoomId, callback);
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
        final String description = "updateTopic : roomId " + roomId + " topic " + topic;

        RoomState roomState = new RoomState();
        roomState.topic = topic;

        mApi.setRoomTopic(roomId, roomState).enqueue(new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                try {
                    updateTopic(roomId, topic, callback);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "resend updateTopic failed " + e.getLocalizedMessage());
                }
            }
        }));
    }

    /**
     * Redact an event.
     * @param roomId the room id
     * @param eventId the event id
     * @param callback the callback containing the created event if successful
     */
    public void redactEvent(final String roomId, final String eventId, final ApiCallback<Event> callback) {
        final String description = "redactEvent : roomId " + roomId + " eventId " + eventId;

        mApi.redactEvent(roomId, eventId, new JsonObject()).enqueue(new RestAdapterCallback<Event>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                Log.e(LOG_TAG, "resend redactEvent " + roomId);

                try {
                    redactEvent(roomId, eventId, callback);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "resend redactEvent failed " + e.getLocalizedMessage());
                }
            }
        }));
    }

    /**
     * Report an event.
     * @param roomId the room id
     * @param eventId the event id
     * @param score the metric to let the user rate the severity of the abuse. It ranges from -100 “most offensive” to 0 “inoffensive”
     * @param reason the reason
     * @param callback the callback containing the created event if successful
     */
    public void reportEvent(final String roomId, final String eventId, final int score, final String reason, final ApiCallback<Void> callback) {
        final String description = "report : roomId " + roomId + " eventId " + eventId;

        ReportContentParams content = new ReportContentParams();

        ArrayList<Integer> scores = new ArrayList<>();
        scores.add(score);

        content.score = scores;
        content.reason = reason;


        mApi.reportEvent(roomId, eventId, content).enqueue(new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                try {
                    reportEvent(roomId, eventId, score, reason, callback);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "resend report failed " + e.getLocalizedMessage());
                }
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
        final String description = "updatePowerLevels : roomId " + roomId + " powerLevels " + powerLevels;

        mApi.setPowerLevels(roomId, powerLevels).enqueue(new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                try {
                    updatePowerLevels(roomId, powerLevels, callback);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "resend updatePowerLevels failed " + e.getLocalizedMessage());
                }
            }
        }));
    }

    /**
     * Send a state events.
     * @param roomId the dedicated room id
     * @param eventType the event type
     * @param params the put parameters
     * @param callback the asynchronous callback
     */
    public void sendStateEvent(final String roomId, final String eventType, final Map<String, Object> params, final ApiCallback<Void> callback) {
        final String description = "sendStateEvent : roomId " + roomId + " - eventType "+ eventType;

        mApi.sendStateEvent(roomId, eventType, params).enqueue(new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                try {
                    sendStateEvent(roomId, eventType, params, callback);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "resend sendStateEvent failed " + e.getLocalizedMessage());
                }
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
        final String description = "sendTypingNotification : roomId " + roomId + " isTyping " + isTyping;

        Typing typing = new Typing();
        typing.typing = isTyping;

        if (-1 != timeout) {
            typing.timeout = timeout;
        }

        // never resend typing on network error
        mApi.setTypingNotification(roomId, userId, typing).enqueue(new RestAdapterCallback<Void>(description, null, callback, null));
    }

    /**
     * Update the room avatar url.
     * @param roomId the room id
     * @param avatarUrl canonical alias
     * @param callback the async callback
     */
    public void updateAvatarUrl(final String roomId, final String avatarUrl, final ApiCallback<Void> callback) {
        final String description = "updateAvatarUrl : roomId " + roomId + " avatarUrl " + avatarUrl;

        HashMap<String, String> params = new HashMap<>();
        params.put("url", avatarUrl);

        mApi.setRoomAvatarUrl(roomId, params).enqueue(new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                try {
                    updateAvatarUrl(roomId, avatarUrl, callback);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "resend updateAvatarUrl failed " + e.getLocalizedMessage());
                }
            }
        }));
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

        mApi.sendReadReceipt(roomId, eventId, content).enqueue(new RestAdapterCallback<Void>(description, mUnsentEventsManager, true, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                try {
                    sendReadReceipt(roomId, eventId, callback);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "resend sendReadReceipt : failed " + e.getLocalizedMessage());
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

        HashMap<String, Object> hashMap = new HashMap<>();
        hashMap.put("order", order);

        mApi.addTag(mCredentials.userId, roomId, tag, hashMap).enqueue(new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                try {
                    addTag(roomId, tag, order, callback);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "resend addTag : failed " + e.getLocalizedMessage());
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
        final String description = "removeTag : roomId " + roomId + " - tag " + tag;

        mApi.removeTag(mCredentials.userId, roomId, tag).enqueue(new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                try {
                    removeTag(roomId, tag, callback);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "resend removeTag : failed " + e.getLocalizedMessage());
                }
            }
        }));
    }

    /**
     * Get the room ID corresponding to this room alias.
     * @param roomAlias the room alias.
     * @param callback the operation callback
     */
    public void getRoomIdByAlias(final String roomAlias, final ApiCallback<RoomAliasDescription> callback) {
        final String description = "getRoomIdByAlias : "+ roomAlias;

        mApi.getRoomIdByAlias(roomAlias).enqueue(new RestAdapterCallback<RoomAliasDescription>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                try {
                    getRoomIdByAlias(roomAlias, callback);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "resend getRoomIdByAlias : failed " + e.getLocalizedMessage());
                }
            }
        }));
    }

    /**
     * Set the room ID corresponding to a room alias.
     * @param roomId the room id.
     * @param roomAlias the room alias.
     * @param callback the operation callback
     */
    public void setRoomIdByAlias(final String roomId, final String roomAlias, final ApiCallback<Void> callback) {
        final String description = "setRoomIdByAlias : roomAlias " + roomAlias + " - roomId : " + roomId;

        RoomAliasDescription roomAliasDescription = new RoomAliasDescription();
        roomAliasDescription.room_id = roomId;

        mApi.setRoomIdByAlias(roomAlias, roomAliasDescription).enqueue(new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                try {
                    setRoomIdByAlias(roomId, roomAlias, callback);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "resend setRoomIdByAlias : failed " + e.getLocalizedMessage());
                }
            }
        }));
    }

    /**
     * Remove the room alias.
     * @param roomAlias the room alias.
     * @param callback the room alias description
     */
    public void removeRoomAlias(final String roomAlias, final ApiCallback<Void> callback) {
        final String description = "removeRoomAlias : "+ roomAlias;

        mApi.removeRoomAlias(roomAlias).enqueue(new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                try {
                    removeRoomAlias(roomAlias, callback);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "resend removeRoomAlias : failed " + e.getLocalizedMessage());
                }
            }
        }));
    }

    /**
     * Update the join rule of the room.
     * To make the room private, the aJoinRule must be set to {@link RoomState#JOIN_RULE_INVITE}.
     * @param aRoomId the room id
     * @param aJoinRule the join rule: {@link RoomState#JOIN_RULE_PUBLIC} or {@link RoomState#JOIN_RULE_INVITE}
     * @param callback the async callback response
     */
    public void updateJoinRules(final String aRoomId, final String aJoinRule, final ApiCallback<Void> callback) {
        final String description = "updateJoinRules : roomId=" + aRoomId + " rule=" + aJoinRule;

        // build RoomState as input parameter
        RoomState roomStateParam = new RoomState();
        roomStateParam.join_rule = aJoinRule;

        mApi.setJoinRules(aRoomId, roomStateParam).enqueue(new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                try {
                    updateJoinRules(aRoomId, aJoinRule, callback);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "resend updateJoinRules failed " + e.getLocalizedMessage());
                }
            }
        }));
    }

    /**
     * Update the guest access rule of the room.
     * To deny guest access to the room, aGuestAccessRule must be set to {@link RoomState#GUEST_ACCESS_FORBIDDEN}
     * @param aRoomId the room id
     * @param aGuestAccessRule the guest access rule: {@link RoomState#GUEST_ACCESS_CAN_JOIN} or {@link RoomState#GUEST_ACCESS_FORBIDDEN}
     * @param callback the async callback response
     */
    public void updateGuestAccess(final String aRoomId, final String aGuestAccessRule, final ApiCallback<Void> callback) {
        final String description = "updateGuestAccess : roomId=" + aRoomId + " rule=" + aGuestAccessRule;

        // build RoomState as input parameter
        RoomState roomStateParam = new RoomState();
        roomStateParam.guest_access = aGuestAccessRule;

        mApi.setGuestAccess(aRoomId, roomStateParam).enqueue(new RestAdapterCallback<Void>(description, mUnsentEventsManager, callback, new RestAdapterCallback.RequestRetryCallBack() {
            @Override
            public void onRetry() {
                try {
                    updateGuestAccess(aRoomId, aGuestAccessRule, callback);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "resend updateJoinRules failed " + e.getLocalizedMessage());
                }
            }

        }));
    }
}
