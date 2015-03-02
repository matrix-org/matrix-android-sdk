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

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.ExifInterface;
import android.media.Image;
import android.net.Uri;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import org.matrix.androidsdk.MXDataHandler;
import org.matrix.androidsdk.listeners.IMXEventListener;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.BannedUser;
import org.matrix.androidsdk.rest.model.ContentResponse;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.ImageInfo;
import org.matrix.androidsdk.rest.model.ImageMessage;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.Message;
import org.matrix.androidsdk.rest.model.PowerLevels;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.RoomResponse;
import org.matrix.androidsdk.rest.model.TokensChunkResponse;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.util.ContentManager;
import org.matrix.androidsdk.util.JsonUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class representing a room and the interactions we have with it.
 */
public class Room {

    private static final String LOG_TAG = "Room";

    /**
     * The direction from which an incoming event is considered.
     * <ul>
     * <li>FORWARDS for events coming down the live event stream</li>
     * <li>BACKWARDS for old events requested through pagination</li>
     * </ul>
     */
    public static enum EventDirection {
        /**
         * The direction for events coming down the live event stream.
         */
        FORWARDS,

        /**
         * The direction for old events requested through pagination.
         */
        BACKWARDS
    }

    private String mRoomId;
    private RoomState mLiveState = new RoomState();
    private RoomState mBackState = new RoomState();

    private DataRetriever mDataRetriever;
    private MXDataHandler mDataHandler;
    private ContentManager mContentManager;

    private String mMyUserId = null;

    // Map to keep track of the listeners the client adds vs. the ones we actually register to the global data handler.
    // This is needed to find the right one when removing the listener.
    private Map<IMXEventListener, IMXEventListener> mEventListeners = new HashMap<IMXEventListener, IMXEventListener>();

    private boolean isPaginating = false;
    private boolean canStillPaginate = true;
    // This is used to block live events and history requests until the state is fully processed and ready
    private boolean isReady = false;

    // userIds list
    private ArrayList<String>mTypingUsers = new ArrayList<String>();

    public String getRoomId() {
        return this.mRoomId;
    }

    public void setRoomId(String roomId) {
        mRoomId = roomId;
        mLiveState.roomId = roomId;
        mBackState.roomId = roomId;
    }

    public RoomState getLiveState() {
        return mLiveState;
    }

    public Collection<RoomMember> getMembers() {
        return mLiveState.getMembers();
    }

    public void setMember(String userId, RoomMember member) {
        mLiveState.setMember(userId, member);
    }

    public RoomMember getMember(String userId) {
        return mLiveState.getMember(userId);
    }

    public String getTopic() {
        return this.mLiveState.topic;
    }

    public String getName(String selfUserId) {
        return mLiveState.getDisplayName(selfUserId);
    }

    public String getVisibility() {
        return mLiveState.visibility;
    }

    public void setVisibility(String visibility) {
        mLiveState.visibility = visibility;
    }

    public void setMyUserId(String userId) { mMyUserId = userId; }

    public void setContentManager(ContentManager contentManager) {
        mContentManager = contentManager;
    }

    /**
     * Set the data retriever for storage/server requests.
     * @param dataRetriever should be the main DataRetriever object
     */
    public void setDataRetriever(DataRetriever dataRetriever) {
        mDataRetriever = dataRetriever;
    }

    /**
     * Set the event listener to send back events to. This is typically the DataHandler for dispatching the events to listeners.
     * @param dataHandler should be the main data handler for dispatching back events to registered listeners.
     */
    public void setDataHandler(MXDataHandler dataHandler) {
        mDataHandler = dataHandler;
        mLiveState.mDataHandler = mDataHandler;
        mBackState.mDataHandler = mDataHandler;
    }

    /**
     * Add an event listener to this room. Only events relative to the room will come down.
     * @param eventListener the event listener to add
     */
    public void addEventListener(final IMXEventListener eventListener) {
        // Create a global listener that we'll add to the data handler
        IMXEventListener globalListener = new MXEventListener() {
            @Override
            public void onPresenceUpdate(Event event, User user) {
                // Only pass event through if the user is a member of the room
                if (getMember(user.userId) != null) {
                    eventListener.onPresenceUpdate(event, user);
                }
            }

            @Override
            public void onLiveEvent(Event event, RoomState roomState) {
                // Filter out events for other rooms and events while we are joining (before the room is ready)
                if (mRoomId.equals(event.roomId) && isReady) {

                    if (event.type.equals(Event.EVENT_TYPE_TYPING)) {
                        // Typing notifications events are not room messages nor room state events
                        // They are just volatile information

                        if (event.content.has("user_ids")) {
                            mTypingUsers = null;

                            try {
                                mTypingUsers =  (new Gson()).fromJson(event.content.get("user_ids"), new TypeToken<List<String>>(){}.getType());
                            } catch (Exception e) {

                            }

                            // avoid null list
                            if (null == mTypingUsers) {
                                mTypingUsers = new ArrayList<String>();
                            }
                        }
                    }
                    eventListener.onLiveEvent(event, roomState);
                }
            }

            @Override
            public void onBackEvent(Event event, RoomState roomState) {
                // Filter out events for other rooms
                if (mRoomId.equals(event.roomId)) {
                    eventListener.onBackEvent(event, roomState);
                }
            }

            @Override
            public void onDeleteEvent(Event event) {
                // Filter out events for other rooms
                if (mRoomId.equals(event.roomId)) {
                    eventListener.onDeleteEvent(event);
                }
            }

            @Override
            public void onResendEvent(Event event) {
                // Filter out events for other rooms
                if (mRoomId.equals(event.roomId)) {
                    eventListener.onResendEvent(event);
                }
            }

        };
        mEventListeners.put(eventListener, globalListener);
        mDataHandler.addListener(globalListener);
    }

    /**
     * Remove an event listener.
     * @param eventListener the event listener to remove
     */
    public void removeEventListener(IMXEventListener eventListener) {
        mDataHandler.removeListener(mEventListeners.get(eventListener));
        mEventListeners.remove(eventListener);
    }

    /**
     * Reset the back state so that future history requests start over from live.
     * Must be called when opening a room if interested in history.
     */
    public void initHistory() {
        mBackState = mLiveState.deepCopy();
        canStillPaginate = true;
    }

    /**
     * Process a state event to keep the internal live and back states up to date.
     * @param event the state event
     * @param direction the direction; ie. forwards for live state, backwards for back state
     */
    public void processStateEvent(Event event, EventDirection direction) {
        RoomState affectedState = (direction == EventDirection.FORWARDS) ? mLiveState : mBackState;
        affectedState.applyState(event, direction);
    }

    /**
     * Process the live state events for the room. Only once this is done is the room considered ready to pass on events.
     * @param stateEvents the state events describing the state of the room
     */
    public void processLiveState(List<Event> stateEvents) {
        for (Event event : stateEvents) {
            processStateEvent(event, EventDirection.FORWARDS);
        }
        isReady = true;
    }

    /**
     * Send a message to the room.
     * The error callbacks will never been called
     * The provided event contains the error description.
     * @param message the message
     * @param callback the callback with the created event
     */
    public void sendMessage(final Message message, final ApiCallback<Event> callback) {
        final ApiCallback<Event> localCB = new ApiCallback<Event>() {
                @Override
                public void onSuccess(Event info) {
                    callback.onSuccess(info);
                }

                private Event storeUnsentMessage() {
                    Event dummyEvent = new Event();
                    dummyEvent.type = Event.EVENT_TYPE_MESSAGE;
                    dummyEvent.content = JsonUtils.toJson(message);
                    dummyEvent.originServerTs = System.currentTimeMillis();
                    dummyEvent.userId = mMyUserId;
                    dummyEvent.isUnsent = true;
                    dummyEvent.roomId = mRoomId;
                    // create a dummy identifier
                    dummyEvent.createDummyEventId();
                    mDataHandler.storeLiveRoomEvent(dummyEvent);

                    return dummyEvent;
                }

                @Override
                public void onNetworkError(Exception e) {
                    Event event = storeUnsentMessage();
                    event.unsentException = e;
                    callback.onSuccess(event);
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    Event event = storeUnsentMessage();
                    event.unsentMatrixError = e;
                    callback.onSuccess(event);
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    Event event = storeUnsentMessage();
                    event.unsentException = e;
                    callback.onSuccess(event);
                }
            };

        // check if the media upload has failed
        // do not send the message if the upload fails
        // but save it in the message MXStore
        if (message instanceof ImageMessage) {
            ImageMessage imageMessage = (ImageMessage)message;

            // file url -> the upload has failed
            if (imageMessage.isLocalContent()) {
                localCB.onMatrixError(null);
                return;
            }
        }
        mDataRetriever.getRoomsRestClient().sendMessage(mRoomId, message, localCB);
    }

    /**
     * Request older messages. They will come down the onBackEvent callback.
     * @param callback callback to implement to be informed that the pagination request has been completed. Can be null.
     * @return true if request starts
     */
    public boolean requestHistory(final ApiCallback<Integer> callback) {
        if (isPaginating // One at a time please
                || !canStillPaginate // If we have already reached the end of history
                || !isReady) { // If the room is not finished being set up
            return false;
        }
        isPaginating = true;
        mDataRetriever.requestRoomHistory(mRoomId, mBackState.getToken(), new SimpleApiCallback<TokensChunkResponse<Event>>(callback) {
            @Override
            public void onSuccess(TokensChunkResponse<Event> response) {
                mBackState.setToken(response.end);
                for (Event event : response.chunk) {
                    if (event.stateKey != null) {
                        processStateEvent(event, EventDirection.BACKWARDS);
                    }
                    mDataHandler.onBackEvent(event, mBackState.deepCopy());
                }
                if (response.chunk.size() == 0) {
                    canStillPaginate = false;
                }
                if (callback != null) {
                    callback.onSuccess(response.chunk.size());
                }
                isPaginating = false;
            }

            @Override
            public void onMatrixError(MatrixError e) {
                // When we've retrieved all the messages from a room, the pagination token is some invalid value
                if (MatrixError.UNKNOWN.equals(e.errcode)) {
                    canStillPaginate = false;
                }
                isPaginating = false;
                super.onMatrixError(e);
            }

            @Override
            public void onNetworkError(Exception e) {
                isPaginating = false;
                super.onNetworkError(e);
            }

            @Override
            public void onUnexpectedError(Exception e) {
                isPaginating = false;
                super.onUnexpectedError(e);
            }
        });

        return true;
    }

    /**
     * Shorthand for {@link #requestHistory(org.matrix.androidsdk.rest.callback.ApiCallback)} with a null callback.
     * @return true if the request starts
     */
    public boolean requestHistory() {
        return requestHistory(null);
    }

    /**
     * Join the room. If successful, the room's current state will be loaded before calling back onComplete.
     * @param callback the callback for when done
     */
    public void join(final ApiCallback<Void> callback) {
        mDataRetriever.getRoomsRestClient().joinRoom(mRoomId, new SimpleApiCallback<Void>(callback) {
            @Override
            public void onSuccess(final Void aVoid) {
                // Once we've joined, we run an initial sync on the room to have all of its information
                initialSync(callback);
            }
        });
    }

    /**
     * Perform a room-level initial sync to get latest messages and pagination token.
     * @param callback the async callback
     */
    public void initialSync(final ApiCallback<Void> callback) {
        mDataRetriever.getRoomsRestClient().initialSync(mRoomId, new SimpleApiCallback<RoomResponse>(callback) {
            @Override
            public void onSuccess(RoomResponse roomInfo) {
                mDataHandler.handleInitialRoomResponse(roomInfo, Room.this);
                if (callback != null) {
                    callback.onSuccess(null);
                }
            }
        });
    }

    /**
     * Shorthand for {@link #join(org.matrix.androidsdk.rest.callback.ApiCallback)} with a null callback.
     */
    public void join() {
        join(null);
    }

    /**
     * Invite a user to this room.
     * @param userId the user id
     * @param callback the callback for when done
     */
    public void invite(String userId, ApiCallback<Void> callback) {
        mDataRetriever.getRoomsRestClient().inviteToRoom(mRoomId, userId, callback);
    }

    /**
     * Leave the room.
     * @param callback the callback for when done
     */
    public void leave(ApiCallback<Void> callback) {
        mDataRetriever.getRoomsRestClient().leaveRoom(mRoomId, callback);
    }

    /**
     * Kick a user from the room.
     * @param userId the user id
     * @param callback the async callback
     */
    public void kick(String userId, ApiCallback<Void> callback) {
        mDataRetriever.getRoomsRestClient().kickFromRoom(mRoomId, userId, callback);
    }

    /**
     * Ban a user from the room.
     * @param userId the user id
     * @param reason ban readon
     * @param callback the async callback
     */
    public void ban(String userId, String reason, ApiCallback<Void> callback) {
        BannedUser user = new BannedUser();
        user.userId = userId;
        if (!TextUtils.isEmpty(reason)) {
            user.reason = reason;
        }
        mDataRetriever.getRoomsRestClient().banFromRoom(mRoomId, user, callback);
    }

    /**
     * Update the power level of the user userId
     * @param userId the user id
     * @param powerLevel the new power level
     * @param callback the callback with the created event
     */
    public void updateUserPowerLevels(String userId, int powerLevel, ApiCallback<Void> callback) {
        PowerLevels powerLevels = getLiveState().getPowerLevels().deepCopy();
        powerLevels.setUserPowerLevel(userId, powerLevel);
        mDataRetriever.getRoomsRestClient().updatePowerLevels(mRoomId, powerLevels, callback);
    }

    /**
     * Unban a user.
     * @param userId the user id
     * @param callback the async callback
     */
    public void unban(String userId, ApiCallback<Void> callback) {
        // Unbanning is just setting a member's state to left, like kick
        kick(userId, callback);
    }

    /**
     * Update the room's name.
     * @param name the new name
     * @param callback the async callback
     */
    public void updateName(String name, ApiCallback<Void> callback) {
        mDataRetriever.getRoomsRestClient().updateName(getRoomId(), name, callback);
    }

    /**
     * Update the room's topic.
     * @param topic the new topic
     * @param callback the async callback
     */
    public void updateTopic(String topic, ApiCallback<Void> callback) {
        mDataRetriever.getRoomsRestClient().updateTopic(getRoomId(), topic, callback);
    }

    /**
     * Redact an event from the room.
     * @param eventId the event's id
     * @param callback the callback with the created event
     */
    public void redact(String eventId, ApiCallback<Event> callback) {
        mDataRetriever.getRoomsRestClient().redact(getRoomId(), eventId, callback);
    }

    /**
     * Get typing users
     * @return the userIds list
     */
    public ArrayList<String> getTypingUsers() {
        return (null == mTypingUsers) ? new ArrayList<String>() : mTypingUsers;
    }

    /**
     * Send a typing notification
     * @param isTyping typing status
     * @param timeout the typing timeout
     */
    public void sendTypingNotification(boolean isTyping, int timeout, ApiCallback<Void> callback) {
        mDataRetriever.getRoomsRestClient().sendTypingNotification(mRoomId, mMyUserId, isTyping, timeout, callback);
    }

    /**
     * Resend the unsend messages
     */
    public void resendUnsentEvents() {
        Collection<Event> events = mDataHandler.getStore().getLatestUnsentEvents(mRoomId);

        // something to resend
        if (events.size() > 0) {
            ArrayList<Event> eventsList = new ArrayList<Event>(events);
            ArrayList<Event> unsentEvents = new ArrayList<Event>();

            // check if some events are already sending
            // to avoid send them twice
            // some network issues could happen
            // eg connected send some unsent messages but do not send all of them
            // deconnected -> connected : some messages could be sent twice
            for(Event event : eventsList){
                if (!event.isSending) {
                    event.isSending = true;
                    unsentEvents.add(event);
                }
            }

            resendEventsList(unsentEvents, 0);
        }
    }

    /**
     * Gets the bitmap rotation angle from the {@link android.media.ExifInterface}.
     * @param context Application context for the content resolver.
     * @param uri The URI to find the orientation for.  Must be local.
     * @return The orientation value, which may be {@link android.media.ExifInterface#ORIENTATION_UNDEFINED}.
     */
    public static int getRotationAngleForBitmap(Context context, Uri uri) {
        int orientation = getOrientationForBitmap(context, uri);

        int rotationAngle = 0;

        if (ExifInterface.ORIENTATION_ROTATE_90 == orientation) {
            rotationAngle = 90;
        } else if (ExifInterface.ORIENTATION_ROTATE_180 == orientation) {
            rotationAngle = 180 ;
        } else if (ExifInterface.ORIENTATION_ROTATE_270 == orientation) {
            rotationAngle = 270;
        }

        return rotationAngle;
    }

    /**
     * Gets the {@link ExifInterface} value for the orientation for this local bitmap Uri.
     * @param context Application context for the content resolver.
     * @param uri The URI to find the orientation for.  Must be local.
     * @return The orientation value, which may be {@link ExifInterface#ORIENTATION_UNDEFINED}.
     */
    public static int getOrientationForBitmap(Context context, Uri uri) {
        int orientation = ExifInterface.ORIENTATION_UNDEFINED;

        if (uri == null) {
            return orientation;
        }

        if (uri.getScheme().equals("content")) {
            String [] proj= {MediaStore.Images.Media.DATA};
            Cursor cursor = null;
            try {
                cursor = context.getContentResolver().query( uri, proj, null, null, null);
                if (cursor != null && cursor.getCount() > 0) {
                    cursor.moveToFirst();
                    int idxData = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                    String path = cursor.getString(idxData);
                    if (TextUtils.isEmpty(path)) {
                        Log.w(LOG_TAG, "Cannot find path in media db for uri " + uri);
                        return orientation;
                    }
                    ExifInterface exif = new ExifInterface(path);
                    orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);
                }
            }
            catch (Exception e) {
                // eg SecurityException from com.google.android.apps.photos.content.GooglePhotosImageProvider URIs
                // eg IOException from trying to parse the returned path as a file when it is an http uri.
                Log.e(LOG_TAG, "Cannot get orientation for bitmap: "+e);
            }
            finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        else if (uri.getScheme().equals("file")) {
            try {
                ExifInterface exif = new ExifInterface(uri.getPath());
                orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED);
            }
            catch (Exception e) {
                Log.e(LOG_TAG, "Cannot get EXIF for file uri "+uri+" because "+e);
            }
        }

        return orientation;
    }

    /**
     * Fills the imageMessage imageInfo.
     * @param context Application context for the content resolver.
     * @param imageMessage The imageMessage to fill.
     * @param imageUri The fullsize image uri.
     * @param mimeType The image mimeType
     * @return The orientation value, which may be {@link ExifInterface#ORIENTATION_UNDEFINED}.
     */
    public static void fillImageInfo(Context context, ImageMessage imageMessage, Uri imageUri, String mimeType) {
        try {
            ImageInfo imageInfo = new ImageInfo();

            String filename = imageUri.getPath();
            File file = new File(filename);

            ExifInterface exifMedia = new ExifInterface(filename);
            String width = exifMedia.getAttribute(ExifInterface.TAG_IMAGE_WIDTH);
            String height = exifMedia.getAttribute(ExifInterface.TAG_IMAGE_LENGTH);

            if ((null != width) && (null != height)) {
                imageInfo.w = Integer.parseInt(width);
                imageInfo.h = Integer.parseInt(height);
            }

            imageInfo.mimetype = mimeType;
            imageInfo.size = file.length();
            imageInfo.rotation = getRotationAngleForBitmap(context, imageUri);

            imageMessage.info = imageInfo;

        } catch (Exception e) {
        }
    }

    /**
     * Resend events list.
     * Wait that the event is resent before sending the next one
     * to keep the genuine order
     */
    private void resendEventsList(final ArrayList<Event> evensList, final int index) {
        if ((evensList.size() > 0) && (index < evensList.size())) {
            final Event oldEvent = evensList.get(index);

            mDataHandler.onResendEvent(oldEvent);

            boolean hasPreviousTask = false;
            final Message message = JsonUtils.toMessage(oldEvent.content);

            if (message instanceof ImageMessage) {
                final ImageMessage imageMessage = (ImageMessage) message;

                if (imageMessage.isLocalContent()) {
                    String filename;
                    // try to parse it
                    try {
                        Uri uri = Uri.parse(imageMessage.url);
                        filename = uri.getPath();
                        FileInputStream fis = new FileInputStream(new File(filename));

                        hasPreviousTask = true;

                        if (null != fis) {
                            mContentManager.uploadContent(fis, imageMessage.info.mimetype, imageMessage.url, new ContentManager.UploadCallback() {

                                @Override
                                public void onUploadProgress(String anUploadId, int percentageProgress) {
                                }

                                @Override
                                public void onUploadComplete(String anUploadId, ContentResponse uploadResponse) {
                                    ImageMessage uploadedMessage = (ImageMessage) JsonUtils.toMessage(oldEvent.content);

                                    if ((null != uploadResponse) && (null != uploadResponse.contentUri)) {
                                        // a thumbnail url could have been set if the upload has failed
                                        // it is a file URL one but it must not be sent
                                        uploadedMessage.thumbnailUrl = null;
                                        uploadedMessage.url = uploadResponse.contentUri;
                                    } else {
                                        // keep the URLs
                                        uploadedMessage.thumbnailUrl = imageMessage.thumbnailUrl;
                                        uploadedMessage.url = imageMessage.url;
                                    }

                                    uploadedMessage.info = imageMessage.info;
                                    uploadedMessage.body = imageMessage.body;

                                    sendMessage(uploadedMessage, new ApiCallback<Event>() {
                                        @Override
                                        public void onSuccess(Event sentEvent) {
                                            oldEvent.isSending = false;
                                            mDataHandler.deleteRoomEvent(oldEvent);
                                            mDataHandler.onDeleteEvent(oldEvent);

                                            // update with updated fields
                                            oldEvent.eventId = sentEvent.eventId;
                                            oldEvent.isUnsent = sentEvent.isUnsent;
                                            oldEvent.unsentException = sentEvent.unsentException;
                                            oldEvent.unsentMatrixError = sentEvent.unsentMatrixError;

                                            mDataHandler.onLiveEvent(oldEvent, getLiveState());

                                            // send the next one
                                            Room.this.resendEventsList(evensList, index + 1);
                                        }

                                        // theses 3 methods will never be called
                                        @Override
                                        public void onNetworkError(Exception e) {
                                        }

                                        @Override
                                        public void onMatrixError(MatrixError e) {
                                        }

                                        @Override
                                        public void onUnexpectedError(Exception e) {
                                        }
                                    });
                                }
                            });
                        }
                    } catch (Exception e) {

                    }
                }
            }

            // no pending request
            if (!hasPreviousTask) {
                sendMessage(message, new ApiCallback<Event>() {
                    @Override
                    public void onSuccess(Event sentEvent) {
                        oldEvent.isSending = false;
                        mDataHandler.deleteRoomEvent(oldEvent);
                        mDataHandler.onDeleteEvent(oldEvent);

                        // update with updated fields
                        oldEvent.eventId = sentEvent.eventId;
                        oldEvent.isUnsent = sentEvent.isUnsent;
                        oldEvent.unsentException = sentEvent.unsentException;
                        oldEvent.unsentMatrixError = sentEvent.unsentMatrixError;

                        mDataHandler.onLiveEvent(oldEvent, getLiveState());

                        // send the next one
                        Room.this.resendEventsList(evensList, index + 1);
                    }

                    // theses 3 methods will never be called
                    @Override
                    public void onNetworkError(Exception e) {
                    }

                    @Override
                    public void onMatrixError(MatrixError e) {
                    }

                    @Override
                    public void onUnexpectedError(Exception e) {
                    }
                });
            }
        }
    }
}
