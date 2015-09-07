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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.client.CallRestClient;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

public class MXCallsManager {
    private static final String LOG_TAG = "MXCallsManager";

    public interface MXCallsManagerListener {
        /**
         * Called when there is an incoming call within the room.
         */
        public void onIncomingCall(IMXCall call);

        /**
         * Called when a called has been hung up
         */
        public void onCallHangUp(IMXCall call);
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
    private Boolean mSuspendTurnServerRefresh = false;

    private CallClass mPreferredCallClass = CallClass.JINGLE_CLASS;

    // UI thread handler
    final Handler mUIThreadHandler = new Handler();

    // active calls
    private HashMap<String, IMXCall> mCallsByCallId = new HashMap<String, IMXCall>();

    // listeners
    private ArrayList<MXCallsManagerListener> mListeners = new ArrayList<MXCallsManagerListener>();

    // incoming calls
    private ArrayList<String> mxPendingIncomingCallId = new ArrayList<String>();

    public MXCallsManager(MXSession session, Context context) {
        mSession = session;
        mContext = context;

        mCallResClient = mSession.getCallRestClient();
        refreshTurnServer();
    }

    /**
     * Turn timer management
     */
    public void pauseTurnServerRefresh() {
        mSuspendTurnServerRefresh = true;
    }

    public void unpauseTurnServerRefresh() {
        mSuspendTurnServerRefresh = false;
        if (null != mTurnServerTimer) {
            mTurnServerTimer.cancel();
            mTurnServerTimer = null;
        }
        refreshTurnServer();
    }

    public void stopTurnServerRefresh() {
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

        return res;
    }

    public void refreshTurnServer() {
        if (mSuspendTurnServerRefresh) {
            return;
        }

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
                                mTurnServerTimer.cancel();
                                mTurnServerTimer = null;

                                refreshTurnServer();
                            }
                        }, msDelay);
                    }


                    @Override
                    public void onSuccess(JsonObject info) {
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

                    @Override
                    public void onNetworkError(Exception e) {
                        restartAfter(60000);
                    }

                    @Override
                    public void onMatrixError(MatrixError e) {
                        restartAfter(60000);
                    }

                    @Override
                    public void onUnexpectedError(Exception e) {
                        restartAfter(60000);
                    }
                });
            }
        });
    }

    /**
     * @return true if the call feature is supported
     */
    public Boolean isSupported() {
        return MXChromeCall.isSupported() || MXJingleCall.isSupported();
    }

    /**
     * @return the list of supported classes
     */
    public Collection<CallClass> supportedClass() {
        ArrayList<CallClass> list = new ArrayList<CallClass>();

        if (MXChromeCall.isSupported()) {
            list.add(CallClass.CHROME_CLASS);
        }

        if (MXJingleCall.isSupported()) {
            list.add(CallClass.JINGLE_CLASS);
        }

        return list;
    }

    /**
     * @param callClass set the default callClass
     */
    public void setDefaultCallClass(CallClass callClass) {
        Boolean isUpdatable = false;

        if (callClass == CallClass.CHROME_CLASS) {
            isUpdatable = MXChromeCall.isSupported();
        }

        if (callClass == CallClass.JINGLE_CLASS) {
            isUpdatable = MXJingleCall.isSupported();
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
     * create a new call
     * @param callId the call Id (null to use a default value)
     * @return the IMXCall
     */
    private IMXCall createCall(String callId) {
        IMXCall call = null;

        // default
        if (((CallClass.CHROME_CLASS == mPreferredCallClass) || (CallClass.DEFAULT_CLASS == mPreferredCallClass)) && MXChromeCall.isSupported()) {
            call = new MXChromeCall(mSession, mContext, getTurnServer());
        }

        // Jingle
        if (null == call) {
            call = new MXJingleCall(mSession, mContext, getTurnServer());
        }

        // a valid callid is provided
        if (null != callId) {
            call.setCallId(callId);
        }

        return call;
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
    private IMXCall callWithCallId(String callId, Boolean create) {
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

        return call;
    }

    /**
     * Manage the call events.
     * @param event the call event.
     */
    public void handleCallEvent(final Event event) {
        if (event.isCallEvent() && isSupported()) {
            // always run the call event in the UI thread
            // MXChromeCall does not work properly in other thread (because of the webview)
            mUIThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    Boolean isMyEvent = event.userId.equals(mSession.getMyUser().userId);
                    Room room = mSession.getDataHandler().getRoom(event.roomId);

                    String callId = null;

                    try {
                        callId = event.content.getAsJsonPrimitive("call_id").getAsString();
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
                                    call.setRoom(room);

                                    if (!isMyEvent) {
                                        call.prepareIncomingCall(event.content, callId);
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
                                    call.setRoom(room);
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
                                    synchronized (this) {
                                        mCallsByCallId.remove(callId);
                                    }
                                } else {
                                    call.setRoom(room);
                                    call.handleCallEvent(event);
                                }
                            }
                        } else if (Event.EVENT_TYPE_CALL_HANGUP.equals(event.type)) {
                            final IMXCall call = callWithCallId(callId);
                            if (null != call) {
                                // trigger call events only if the call is active
                                final Boolean isActiveCall = !IMXCall.CALL_STATE_CREATED.equals(call.getCallState());

                                call.setRoom(room);

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
                                        if (isActiveCall) {
                                            onCallHangUp(call);
                                        }
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
     * @param RoomId the roomId of the room
     * @return the IMXCall if it can be done
     */
    public IMXCall createCallInRoom(String RoomId) {
        IMXCall call = null;
        Room room = mSession.getDataHandler().getRoom(RoomId);

        // sanity check
        if (null != room) {
            if (isSupported()) {
                call = callWithCallId(null, true);
                call.setRoom(room);
            }
        }

        return call;
    }

    /**
     * Sets the speakerphone on or off.
     *
     * @param isOn true to turn on speakerphone;
     *           false to turn it off
     */
    public static void setSpeakerphoneOn(Context context, boolean isOn) {
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
}
