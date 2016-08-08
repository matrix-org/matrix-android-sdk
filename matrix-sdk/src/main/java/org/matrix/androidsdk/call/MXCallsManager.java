/*
 * Copyright 2015 OpenMarket Ltd
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

package org.matrix.androidsdk.call;

import android.content.Context;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.client.CallRestClient;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.EventContent;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.util.JsonUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MXCallsManager {
    private static final String LOG_TAG = "MXCallsManager";

    public interface MXCallsManagerListener {
        /**
         * Called when there is an incoming call within the room.
         */
        void onIncomingCall(IMXCall call);

        /**
         * Called when a called has been hung up
         */
        void onCallHangUp(IMXCall call);

        /**
         * A voip conference started in a room.
         * @param roomId the room id
         */
        void onVoipConferenceStarted(String roomId);

        /**
         * A voip conference finhised in a room.
         * @param roomId the room id
         */
        void onVoipConferenceFinished(String roomId);
    }

    /**
     * Defines the call classes.
     */
    public enum CallClass {
        CHROME_CLASS,
        JINGLE_CLASS,
        DEFAULT_CLASS
    }

    private MXSession mSession = null;
    private Context mContext = null;

    private CallRestClient mCallResClient = null;
    private JsonElement mTurnServer = null;
    private Timer mTurnServerTimer = null;
    private boolean mSuspendTurnServerRefresh = false;

    private CallClass mPreferredCallClass = CallClass.JINGLE_CLASS;

    // active calls
    private HashMap<String, IMXCall> mCallsByCallId = new HashMap<String, IMXCall>();

    // listeners
    private ArrayList<MXCallsManagerListener> mListeners = new ArrayList<MXCallsManagerListener>();

    // incoming calls
    private ArrayList<String> mxPendingIncomingCallId = new ArrayList<String>();

    // UI handler
    private Handler mUIThreadHandler;

    public MXCallsManager(MXSession session, Context context) {
        mSession = session;
        mContext = context;

        mUIThreadHandler = new Handler(Looper.getMainLooper());

        mCallResClient = mSession.getCallRestClient();

        mSession.getDataHandler().addListener(new MXEventListener() {
            @Override
            public void onLiveEvent(Event event, RoomState roomState) {
                if (TextUtils.equals(event.type, Event.EVENT_TYPE_STATE_ROOM_MEMBER)) {
                    // check on the conference user
                    if (TextUtils.equals(event.sender, MXCallsManager.getConferenceUserId(event.roomId))) {
                        EventContent eventContent = JsonUtils.toEventContent(event.getContentAsJsonObject());

                        if (TextUtils.equals(eventContent.membership, RoomMember.MEMBERSHIP_LEAVE)) {
                            onVoipConferenceFinished(event.roomId);
                        } if (TextUtils.equals(eventContent.membership, RoomMember.MEMBERSHIP_JOIN)) {
                            onVoipConferenceStarted(event.roomId);
                        }
                    }
                }
            }
        });

        refreshTurnServer();
    }

    /**
     * Turn timer management
     */
    public void pauseTurnServerRefresh() {
        mSuspendTurnServerRefresh = true;
    }

    public void unpauseTurnServerRefresh() {
        Log.d(LOG_TAG, "unpauseTurnServerRefresh");

        mSuspendTurnServerRefresh = false;
        if (null != mTurnServerTimer) {
            mTurnServerTimer.cancel();
            mTurnServerTimer = null;
        }
        refreshTurnServer();
    }

    public void stopTurnServerRefresh() {
        Log.d(LOG_TAG, "stopTurnServerRefresh");

        mSuspendTurnServerRefresh = true;
        if (null != mTurnServerTimer) {
            mTurnServerTimer.cancel();
            mTurnServerTimer = null;
        }
    }

    /**
     * @return the turn server
     */
    public JsonElement getTurnServer() {
        JsonElement res;

        synchronized (LOG_TAG) {
            res = mTurnServer;
        }

        // privacy logs
        //Log.d(LOG_TAG, "getTurnServer " + res);
        Log.d(LOG_TAG, "getTurnServer ");

        return res;
    }

    public void refreshTurnServer() {
        if (mSuspendTurnServerRefresh) {
            return;
        }

        Log.d(LOG_TAG, "refreshTurnServer");

        mUIThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                mCallResClient.getTurnServer(new ApiCallback<JsonObject>() {
                    private void restartAfter(int msDelay) {
                        if (null != mTurnServerTimer) {
                            mTurnServerTimer.cancel();
                        }

                        mTurnServerTimer = new Timer();
                        mTurnServerTimer.schedule(new TimerTask() {
                            @Override
                            public void run() {
                                Log.d(LOG_TAG, "refreshTurnServer cancelled");
                                mTurnServerTimer.cancel();
                                mTurnServerTimer = null;

                                refreshTurnServer();
                            }
                        }, msDelay);
                    }


                    @Override
                    public void onSuccess(JsonObject info) {
                        // privacy
                        Log.d(LOG_TAG, "onSuccess ");
                        //Log.d(LOG_TAG, "onSuccess " + info);

                        if (null != info) {
                            if (info.has("uris")) {
                                synchronized (LOG_TAG) {
                                    mTurnServer = info;
                                }
                            }

                            if (info.has("ttl")) {
                                int ttl = 60000;

                                try {
                                    ttl = info.get("ttl").getAsInt();
                                    // restart a 90 % before ttl expires
                                    ttl = ttl * 9 / 10;
                                } catch (Exception e) {

                                }

                                restartAfter(ttl);
                            }
                        }
                    }

                    @Override
                    public void onNetworkError(Exception e) {
                        restartAfter(60000);
                    }

                    @Override
                    public void onMatrixError(MatrixError e) {
                        if (TextUtils.equals(e.errcode, MatrixError.LIMIT_EXCEEDED)) {
                            restartAfter(60000);
                        }
                    }

                    @Override
                    public void onUnexpectedError(Exception e) {
                        // should never happen
                    }
                });
            }
        });
    }

    /**
     * @return true if the call feature is supported
     */
    public boolean isSupported() {
        return MXChromeCall.isSupported() || MXJingleCall.isSupported(mContext);
    }

    /**
     * @return the list of supported classes
     */
    public Collection<CallClass> supportedClass() {
        ArrayList<CallClass> list = new ArrayList<CallClass>();

        if (MXChromeCall.isSupported()) {
            list.add(CallClass.CHROME_CLASS);
        }

        if (MXJingleCall.isSupported(mContext)) {
            list.add(CallClass.JINGLE_CLASS);
        }

        Log.d(LOG_TAG, "supportedClass " + list);

        return list;
    }

    /**
     * @param callClass set the default callClass
     */
    public void setDefaultCallClass(CallClass callClass) {
        Log.d(LOG_TAG, "setDefaultCallClass " + callClass);

        boolean isUpdatable = false;

        if (callClass == CallClass.CHROME_CLASS) {
            isUpdatable = MXChromeCall.isSupported();
        }

        if (callClass == CallClass.JINGLE_CLASS) {
            isUpdatable = MXJingleCall.isSupported(mContext);
        }

        if (isUpdatable) {
            mPreferredCallClass = callClass;
        }
    }

    /**
     * listeners management
     **/

    public void addListener(MXCallsManagerListener listener) {
        synchronized (this) {
            if (null != listener) {
                if (mListeners.indexOf(listener) < 0) {
                    mListeners.add(listener);
                }
            }
        }
    }

    public void removeListener(MXCallsManagerListener listener) {
        synchronized (this) {
            if (null != listener) {
                mListeners.remove(listener);
            }
        }
    }

    /**
     * dispatch the onIncomingCall event to the listeners
     * @param call the call
     */
    private void onIncomingCall(IMXCall call) {
        Log.d(LOG_TAG, "onIncomingCall");

        synchronized (this) {
            for(MXCallsManagerListener l : mListeners) {
                try {
                    l.onIncomingCall(call);
                } catch (Exception e) {
                }
            }
        }
    }

    /**
     * dispatch the onCallHangUp event to the listeners
     * @param call the call
     */
    private void onCallHangUp(IMXCall call) {
        Log.d(LOG_TAG, "onCallHangUp");

        synchronized (this) {
            for(MXCallsManagerListener l : mListeners) {
                try {
                    l.onCallHangUp(call);
                } catch (Exception e) {
                }
            }
        }
    }

    /**
     * dispatch the onVoipConferenceStarted event to the listeners
     * @param roomId the room Id
     */
    private void onVoipConferenceStarted(String roomId) {
        Log.d(LOG_TAG, "onVoipConferenceStarted : " + roomId);

        synchronized (this) {
            for(MXCallsManagerListener l : mListeners) {
                try {
                    l.onVoipConferenceStarted(roomId);
                } catch (Exception e) {
                }
            }
        }
    }

    /**
     * dispatch the onVoipConferenceFinished event to the listeners
     * @param roomId the room Id
     */
    private void onVoipConferenceFinished(String roomId) {
        Log.d(LOG_TAG, "onVoipConferenceFinished : " + roomId);

        synchronized (this) {
            for(MXCallsManagerListener l : mListeners) {
                try {
                    l.onVoipConferenceFinished(roomId);
                } catch (Exception e) {
                }
            }
        }
    }

    /**
     * create a new call
     * @param callId the call Id (null to use a default value)
     * @return the IMXCall
     */
    private IMXCall createCall(String callId) {
        Log.d(LOG_TAG, "createCall " + callId);

        IMXCall call = null;

        // default
        if (((CallClass.CHROME_CLASS == mPreferredCallClass) || (CallClass.DEFAULT_CLASS == mPreferredCallClass)) && MXChromeCall.isSupported()) {
            call = new MXChromeCall(mSession, mContext, getTurnServer());
        }

        // Jingle
        if (null == call) {
            try {
                call = new MXJingleCall(mSession, mContext, getTurnServer());
            } catch (Exception e) {
                Log.e(LOG_TAG, "createCall " + e.getLocalizedMessage());
            }
        }

        // a valid callid is provided
        if (null != callId) {
            call.setCallId(callId);
        }

        return call;
    }

    /**
     * Search a call from its dedicated room id.
     * @param roomId the room id
     * @return the IMXCall if it exists
     */
    public IMXCall callWithRoomId(String roomId) {
        Collection<IMXCall> calls;

        synchronized (this) {
            calls = mCallsByCallId.values();
        }

        for(IMXCall call : calls) {
            if (TextUtils.equals(roomId, call.getRoom().getRoomId())) {
                return call;
            }
        }

        return null;
    }

    /**
     * Returns the IMXCall from its callId.
     * @param callId the call Id
     * @return the IMXCall if it exists
     */
    public IMXCall callWithCallId(String callId) {
        return callWithCallId(callId, false);
    }

    /**
     * Returns the IMXCall from its callId.
     * @param callId the call Id
     * @param create create the IMXCall if it does not exist
     * @return the IMXCall if it exists
     */
    private IMXCall callWithCallId(String callId, boolean create) {
        IMXCall call = null;

        // check if the call exists
        if (null != callId) {
            synchronized (this) {
                call = mCallsByCallId.get(callId);
            }
        }

        // the call does not exist but request to create it
        if ((null == call) && create) {
            call = createCall(callId);
            synchronized (this) {
                mCallsByCallId.put(call.getCallId(), call);
            }
        }

        Log.d(LOG_TAG, "callWithCallId " + callId + " " + call);

        return call;
    }

    /**
     * @return true if there are some active calls.
     */
    public boolean hasActiveCalls() {
        boolean res;

        synchronized (this) {
            res = (0 != mCallsByCallId.size());
        }

        Log.d(LOG_TAG, "hasActiveCalls " + res);

        return res;
    }

    /**
     * Manage the call events.
     * @param event the call event.
     */
    public void handleCallEvent(final Event event) {
        if (event.isCallEvent() && isSupported()) {
            Log.d(LOG_TAG, "handleCallEvent " + event.type);

            // always run the call event in the UI thread
            // MXChromeCall does not work properly in other thread (because of the webview)
            mUIThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    boolean isMyEvent = TextUtils.equals(event.getSender(), mSession.getMyUserId());
                    Room room = mSession.getDataHandler().getRoom(event.roomId);

                    String callId = null;
                    JsonObject eventContent = null;

                    try {
                        eventContent = event.getContentAsJsonObject();
                        callId = eventContent.getAsJsonPrimitive("call_id").getAsString();
                    } catch (Exception e) {

                    }
                    // sanity check
                    if ((null != callId) && (null != room)) {
                        // receive an invitation
                        if (Event.EVENT_TYPE_CALL_INVITE.equals(event.type)) {
                            long lifeTime = System.currentTimeMillis() - event.getOriginServerTs();

                            // ignore older call messages
                            if (lifeTime < 30000) {
                                // create the call only it is triggered from someone else
                                IMXCall call = callWithCallId(callId, !isMyEvent);

                                // sanity check
                                if (null != call) {
                                    // init the information
                                    if (null == call.getRoom()) {
                                        call.setRooms(room, room);
                                    }

                                    if (!isMyEvent) {
                                        call.prepareIncomingCall(eventContent, callId, null);
                                        call.setIsIncoming(true);
                                        mxPendingIncomingCallId.add(callId);
                                    } else {
                                        call.handleCallEvent(event);
                                    }
                                }
                            }

                        } else if (Event.EVENT_TYPE_CALL_CANDIDATES.equals(event.type)) {
                            if (!isMyEvent) {
                                IMXCall call = callWithCallId(callId);

                                if (null != call) {
                                    if (null == call.getRoom()) {
                                        call.setRooms(room, room);
                                    }
                                    call.handleCallEvent(event);
                                }
                            }
                        } else if (Event.EVENT_TYPE_CALL_ANSWER.equals(event.type)) {
                            IMXCall call = callWithCallId(callId);

                            if (null != call) {
                                // assume it is a catch up call.
                                // the creation / candidates /
                                // the call has been answered on another device
                                if (IMXCall.CALL_STATE_CREATED.equals(call.getCallState())) {
                                    call.onAnsweredElsewhere();
                                    synchronized (this) {
                                        mCallsByCallId.remove(callId);
                                    }
                                } else {
                                    if (null == call.getRoom()) {
                                        call.setRooms(room, room);
                                    }
                                    call.handleCallEvent(event);
                                }
                            }
                        } else if (Event.EVENT_TYPE_CALL_HANGUP.equals(event.type)) {
                            final IMXCall call = callWithCallId(callId);
                            if (null != call) {
                                // trigger call events only if the call is active
                                final boolean isActiveCall = !IMXCall.CALL_STATE_CREATED.equals(call.getCallState());

                                if (null == call.getRoom()) {
                                    call.setRooms(room, room);
                                }

                                if (isActiveCall) {
                                    call.handleCallEvent(event);
                                }

                                synchronized (this) {
                                    mCallsByCallId.remove(callId);
                                }

                                // warn that a call has been hung up
                                mUIThreadHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        // must warn anyway any listener that the call has been killed
                                        // for example, when the device is in locked screen
                                        // the callview is not created but the device is ringing
                                        // if the other participant ends the call, the ring should stop
                                        onCallHangUp(call);
                                    }
                                });
                            }
                        }
                    }
                }
            });
        }
    }

    /**
     * check if there is a pending incoming call
     */
    public void checkPendingIncomingCalls() {
        Log.d(LOG_TAG, "checkPendingIncomingCalls");

        mUIThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mxPendingIncomingCallId.size() > 0) {
                    for (String callId : mxPendingIncomingCallId) {
                        IMXCall call = callWithCallId(callId);

                        if (null != call) {
                            onIncomingCall(call);
                        }
                    }
                    mxPendingIncomingCallId.clear();
                }
            }
        });
    }

    /**
     * Create an IMXCall in the room RoomId
     * @param roomId the room roomId
     * @param callback the async callback
     */
    public void createCallInRoom(final String roomId, final ApiCallback<IMXCall> callback) {
        Log.d(LOG_TAG, "createCallInRoom " + roomId);

        final Room room = mSession.getDataHandler().getRoom(roomId);

        // sanity check
        if (null != room) {
            if (isSupported()) {
                int joinedMembers = room.getJoinedMembers().size();

                if (joinedMembers > 1) {

                    if (joinedMembers == 2) {
                        final IMXCall call = callWithCallId(null, true);
                        call.setRooms(room, room);

                        if (null != callback) {
                            mUIThreadHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    callback.onSuccess(call);
                                }
                            });
                        }
                    } else {
                        inviteConferenceUser(room, new ApiCallback<Void>() {
                            @Override
                            public void onSuccess(Void info) {
                                getConferenceUserRoom(room.getRoomId(), new ApiCallback<Room>() {
                                    @Override
                                    public void onSuccess(Room conferenceRoom) {
                                        final IMXCall call = callWithCallId(null, true);
                                        call.setRooms(room, conferenceRoom);
                                        call.setIsConference(true);

                                        if (null != callback) {
                                            mUIThreadHandler.post(new Runnable() {
                                                @Override
                                                public void run() {
                                                    callback.onSuccess(call);
                                                }
                                            });
                                        }
                                    }

                                    @Override
                                    public void onNetworkError(Exception e) {
                                        if (null != callback) {
                                            callback.onNetworkError(e);
                                        }
                                    }

                                    @Override
                                    public void onMatrixError(MatrixError e) {
                                        if (null != callback) {
                                            callback.onMatrixError(e);
                                        }
                                    }

                                    @Override
                                    public void onUnexpectedError(Exception e) {
                                        if (null != callback) {
                                            callback.onUnexpectedError(e);
                                        }
                                    }
                                });
                            }

                            @Override
                            public void onNetworkError(Exception e) {
                                if (null != callback) {
                                    callback.onNetworkError(e);
                                }
                            }

                            @Override
                            public void onMatrixError(MatrixError e) {
                                if (null != callback) {
                                    callback.onMatrixError(e);
                                }
                            }

                            @Override
                            public void onUnexpectedError(Exception e) {
                                if (null != callback) {
                                    callback.onUnexpectedError(e);
                                }
                            }
                        });
                    }
                } else {
                    if (null != callback) {
                        callback.onMatrixError(new MatrixError(MatrixError.NOT_SUPPORTED, "too few users"));
                    }
                }
            } else {
                if (null != callback) {
                    callback.onMatrixError(new MatrixError(MatrixError.NOT_SUPPORTED, "VOIP is not supported"));
                }
            }
        } else {
            if (null != callback) {
                callback.onMatrixError(new MatrixError(MatrixError.NOT_FOUND, "room not found"));
            }
        }
    }

    /**
     * Sets the speakerphone on or off.
     *
     * @param isOn true to turn on speakerphone;
     *           false to turn it off
     */
    public static void setSpeakerphoneOn(Context context, boolean isOn) {
        Log.d(LOG_TAG, "setSpeakerphoneOn " + isOn);

        AudioManager audioManager = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);

        // ignore speaker button if a bluetooth headset is connected
        if (!audioManager.isBluetoothA2dpOn()) {
            int audioMode = audioManager.getMode();

            // do not update the mode in VOip mode (Chrome call)
            if (audioMode != AudioManager.MODE_IN_COMMUNICATION) {
                audioMode = isOn ? AudioManager.MODE_NORMAL : AudioManager.MODE_IN_CALL;
            }

            // update only if there is an update
            // MXChromecall crashes if there is an update whereas nothing has been updated
            if (audioManager.getMode() != audioMode) {
                audioManager.setMode(audioMode);
            }

            if (isOn != audioManager.isSpeakerphoneOn()) {
                audioManager.setSpeakerphoneOn(isOn);
            }
        }
    }

    //==============================================================================================================
    // Conference call
    //==============================================================================================================


    // Copied from vector-web:
    // FIXME: This currently forces Vector to try to hit the matrix.org AS for conferencing.
    // This is bad because it prevents people running their own ASes from being used.
    // This isn't permanent and will be customisable in the future: see the proposal
    // at docs/conferencing.md for more info.
    private static final String USER_PREFIX = "fs_";
    private static final String DOMAIN = "matrix.org";
    private static final HashMap<String, String> mConferenceUserIdByRoomId = new HashMap<>();
    /**
     * Return the id of the conference user dedicated for a room Id
     * @param roomId the room id
     * @return the conference user id
     */
    public static final String getConferenceUserId(String roomId) {
        // sanity check
        if (null == roomId) {
            return null;
        }

        String conferenceUserId = mConferenceUserIdByRoomId.get(roomId);

        // it does not exist, compute it.
        if (null == conferenceUserId) {
            byte[] data = null;

            try {
                data = roomId.getBytes("UTF-8");
            } catch (Exception e) {
                Log.e(LOG_TAG, "conferenceUserIdForRoom failed " + e.getMessage());
            }

            if (null == data) {
                return null;
            }

            String base64 = Base64.encodeToString(data, Base64.NO_WRAP | Base64.URL_SAFE).replace("=", "");
            conferenceUserId = "@" + USER_PREFIX + base64 + ":" + DOMAIN;

            mConferenceUserIdByRoomId.put(roomId, conferenceUserId);
        }

        return conferenceUserId;
    }

    /**
     * Test if the provided user is a valid conference user Id
     * @param userId the user id to test
     * @return true if it is a valid conference user id
     */
    public static boolean isConferenceUserId(String userId) {
        // test first if it a known conference user id
        if (mConferenceUserIdByRoomId.values().contains(userId)) {
            return true;
        }

        boolean res = false;

        String prefix = "@" + USER_PREFIX;
        String suffix = ":" + DOMAIN;

        if (!TextUtils.isEmpty(userId) && userId.startsWith(prefix) && userId.endsWith(suffix)) {
            String roomIdBase64 = userId.substring(prefix.length(), userId.length() - suffix.length());

            try {
                byte[] data = Base64.decode(roomIdBase64, Base64.NO_WRAP | Base64.URL_SAFE);
                String roomId = new String(data, "UTF-8");
                res = MXSession.PATTERN_MATRIX_ROOM_IDENTIFIER.matcher(roomId).matches();
            } catch (Exception e) {
                Log.e(LOG_TAG, "isConferenceUserId : failed " + e.getMessage());
            }
        }

        return res;
    }

    /**
     * Invite the conference user to a room.
     * It is mandatory before starting a conference call.
     * @param room the room
     * @param callback the async callback
     */
    private void inviteConferenceUser(final Room room, final ApiCallback<Void> callback) {
        String conferenceUserId = getConferenceUserId(room.getRoomId());
        RoomMember conferenceMember = room.getMember(conferenceUserId);

        if ((null != conferenceMember) && TextUtils.equals(conferenceMember.membership, RoomMember.MEMBERSHIP_JOIN)) {
            mUIThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onSuccess(null);
                }
            });
        } else {
            room.invite(conferenceUserId, callback);
        }
    }

    /**
     * Get the room with the conference user dedicated for the passed room.
     * @param roomId the room id.
     * @param callback the async callback.
     */
    private void getConferenceUserRoom(final String roomId, final ApiCallback<Room> callback) {
        String conferenceUserId = getConferenceUserId(roomId);

        Room conferenceRoom = null;
        Collection<Room> rooms = mSession.getDataHandler().getStore().getRooms();

        // Use an existing 1:1 with the conference user; else make one
        for(Room room : rooms) {
            if (room.isConferenceUserRoom() && (2 == room.getMembers().size()) && (null != room.getMember(conferenceUserId))) {
                conferenceRoom = room;
                break;
            }
        }

        if (null != conferenceRoom) {
            final Room fConferenceRoom = conferenceRoom;
            mSession.getDataHandler().getStore().commit();

            mUIThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    callback.onSuccess(fConferenceRoom);
                }
            });
        } else {
            HashMap<String, Object> params = new HashMap<>();
            params.put("preset", "private_chat");
            params.put("invite", Arrays.asList(conferenceUserId));

            mSession.createRoom(params, new ApiCallback<String>() {
                @Override
                public void onSuccess(String roomId) {
                    Room room = mSession.getDataHandler().getRoom(roomId);

                    if (null != room) {
                        room.setIsConferenceUserRoom(true);
                        mSession.getDataHandler().getStore().commit();
                        callback.onSuccess(room);
                    }
                }

                @Override
                public void onNetworkError(Exception e) {
                    callback.onNetworkError(e);
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    callback.onMatrixError(e);
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    callback.onUnexpectedError(e);
                }
            });
        }
    }
}
