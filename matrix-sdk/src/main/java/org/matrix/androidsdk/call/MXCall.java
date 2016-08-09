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
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;

/**
 * This class is the default implementation
 */
public class MXCall implements IMXCall {
    private static final String LOG_TAG = "MXCall";

    protected MXSession mSession = null;
    protected Context mContext = null;
    protected JsonElement mTurnServer = null;
    protected Room mCallingRoom = null;
    protected Room mCallSignalingRoom = null;

    private final ArrayList<MXCallListener> mCallListeners = new ArrayList<>();

    // the current call id
    protected String mCallId = null;
    protected boolean mIsVideoCall = false;
    protected boolean mIsIncoming = false;
    private boolean mIsConference = false;

    protected final ArrayList<Event> mPendingEvents = new ArrayList<>();
    private Event mPendingEvent = null;
    protected Timer mCallTimeoutTimer = null;

    // call start time
    private long mStartTime = -1;

    // UI thread handler
    final Handler mUIThreadHandler = new Handler();

    /**
     * Create the call view
     */
    public void createCallView() {
    }

    /**
     * The activity is paused.
     */
    public void onPause() {
    }

    /**
     * The activity is resumed.
     */
    public void onResume() {
    }

    // actions (must be done after onViewReady()
    /**
     * Start a call.
     */
    public void placeCall(VideoLayoutConfiguration aLocalVideoPosition) {
    }

    /**
     * Prepare a call reception.
     * @param callInviteParams the invitation Event content
     * @param callId the call ID
     * @param aLocalVideoPosition position of the local video attendee
     */
    public void prepareIncomingCall(JsonObject callInviteParams, String callId,  VideoLayoutConfiguration aLocalVideoPosition) {
    }

    /**
     * The call has been detected as an incoming one.
     * The application launched the dedicated activity and expects to launch the incoming call.
     * @param aLocalVideoPosition position of the local video attendee
     */
    public void launchIncomingCall(VideoLayoutConfiguration aLocalVideoPosition) {
    }

    @Override
    public void updateLocalVideoRendererPosition(VideoLayoutConfiguration aLocalVideoPosition){
        Log.w(LOG_TAG,"## updateLocalVideoRendererPosition(): not implemented");
    }

    // events thread
    /**
     * Manage the call events.
     * @param event the call event.
     */
    public void handleCallEvent(Event event) {
    }

    // user actions
    /**
     * The call is accepted.
     */
    public void answer() {
    }

    /**
     * The call has been has answered on another device.
     */
    public void onAnsweredElsewhere() {

    }
    /**
     * The call is hung up.
     */
    public void hangup(String reason) {
    }

    // getters / setters
    /**
     * @return the callId
     */
    public String getCallId() {
        return mCallId;
    }

    /**
     * Set the callId
     */
    public void setCallId(String callId) {
        mCallId = callId;
    }

    /**
     * @return the linked room
     */
    public Room getRoom() {
        return mCallingRoom;
    }

    /**
     * @return the call signaling room
     */
    public Room getCallSignalingRoom() {
        return mCallSignalingRoom;
    }

    /**
     * Set the linked room
     * @param room the room
     */
    /*public void setRoom(Room room) {
        setRooms(room, room);
    }*/

    /**
     * Set the linked rooms.
     * @param room the room
     * @param callSignalingRoom the call signaling room.
     */
    public void setRooms(Room room, Room callSignalingRoom) {
        mCallingRoom = room;
        mCallSignalingRoom = callSignalingRoom;
    }

    /**
     * @return the session
     */
    public MXSession getSession() {
        return mSession;
    }

    /**
     * @return true if the call is an incoming call.
     */
    public boolean isIncoming() {
        return mIsIncoming;
    }

    /**
     * @param isIncoming true if the call is an incoming one.
     */
    public void setIsIncoming(boolean isIncoming) {
        mIsIncoming = isIncoming;
    }


    /**
     * Defines the call type
     */
    public void setIsVideo(boolean isVideo) {
        mIsVideoCall = isVideo;
    }

    /**
     * @return true if the call is a video call.
     */
    public boolean isVideo() {
        return mIsVideoCall;
    }

    /**
     * Defines the call conference status
     */
    public void setIsConference(boolean isConference) {
        mIsConference = isConference;
    }

    /**
     * @return true if the call is a conference call.
     */
    public boolean isConference() {
        return mIsConference;
    }

    /**
     * @return the callstate (must be a CALL_STATE_XX value)
     */
    public String getCallState() {
        return null;
    }

    /**
     * @return the callView
     */
    public View getCallView() {
        return null;
    }

    /**
     * @return the callView visibility
     */
    public int getVisibility() {
        return View.GONE;
    }

    /**
     * Set the callview visibility
     * @return true if the operation succeeds
     */
    public boolean setVisibility(int visibility) {
        return false;
    }

    /**
     * @return if the call is ended.
     */
    public boolean isCallEnded() {
        return TextUtils.equals(CALL_STATE_ENDED, getCallState());
    }

    /**
     * Toogle the speaker
     */
    public void toggleSpeaker() {
        AudioManager audioManager = (AudioManager)mContext.getSystemService(Context.AUDIO_SERVICE);

        if (null != audioManager) {
            MXCallsManager.setSpeakerphoneOn(mContext, !audioManager.isSpeakerphoneOn());
        }
    }

    /**
     * @return the call start time in ms since epoch, -1 if not defined.
     */
    public long getCallStartTime() {
        return mStartTime;
    }

    /**
     * @return the call elapsed time in seconds, -1 if not defined.
     */
    public long getCallElapsedTime() {
        if (-1 == mStartTime) {
            return -1;
        }

        return (System.currentTimeMillis() - mStartTime) / 1000;
    }

    //==============================================================================================================
    // call events listener
    //==============================================================================================================

    /**
     * Add a listener.
     * @param callListener the listener to add
     */
    public void addListener(MXCallListener callListener) {
        if (null != callListener) {
            synchronized (LOG_TAG) {
                mCallListeners.add(callListener);
            }
        }
    }

    /**
     * Remove a listener
     * @param callListener the listener to remove
     */
    public void removeListener(MXCallListener callListener) {
        if (null != callListener) {
            synchronized (LOG_TAG) {
                mCallListeners.remove(callListener);
            }
        }
    }

    /**
     * Remove the listeners
     */
    public void clearListeners() {
        synchronized (LOG_TAG) {
            mCallListeners.clear();
        }
    }

    /**
     * @return the call listeners
     */
    private List<MXCallListener> getCallListeners() {
        ArrayList<MXCallListener> listeners;

        synchronized (LOG_TAG) {
            listeners = new ArrayList<>(mCallListeners);
        }

        return listeners;
    }

    /**
     * dispatch the onViewLoading callback
     * @param callView the callview
     */
    protected void dispatchOnViewLoading(View callView) {
        if (isCallEnded()) {
            Log.d(LOG_TAG, "dispatchOnViewLoading : the call is ended");
            return;
        }

        Log.d(LOG_TAG, "dispatchOnViewLoading");

        List<MXCallListener> listeners = getCallListeners();

        for (MXCallListener listener : listeners) {
            try {
                listener.onViewLoading(callView);
            } catch (Exception e) {
                Log.e(LOG_TAG, "dispatchOnViewLoading : " + e.getMessage());
            }
        }
    }

    /**
     * dispatch the onViewReady event
     */
    protected void dispatchOnViewReady() {
        if (isCallEnded()) {
            Log.d(LOG_TAG, "dispatchOnViewReady : the call is ended");
            return;
        }

        Log.d(LOG_TAG, "dispatchOnViewReady");

        List<MXCallListener> listeners = getCallListeners();

        for (MXCallListener listener : listeners) {
            try {
                listener.onViewReady();
            } catch (Exception e) {
                Log.e(LOG_TAG, "dispatchOnViewReady : " + e.getMessage());
            }
        }
    }

    /**
     * dispatch the onCallError event
     */
    protected void dispatchOnCallError(String error) {
        if (isCallEnded()) {
            Log.d(LOG_TAG, "dispatchOnCallError : the call is ended");
            return;
        }

        Log.d(LOG_TAG, "dispatchOnCallError");

        List<MXCallListener> listeners = getCallListeners();

        for (MXCallListener listener : listeners) {
            try {
                listener.onCallError(error);
            } catch (Exception e) {
                Log.e(LOG_TAG, "dispatchOnCallError : " + e.getMessage());
            }
        }
    }

    /**
     * dispatch the onStateDidChange event
     */
    protected void dispatchOnStateDidChange(String newState) {
        Log.d(LOG_TAG, "dispatchOnCallErrorOnStateDidChange : " + newState);

        // set the call start time
        if (TextUtils.equals(CALL_STATE_CONNECTED, newState) && (-1 == mStartTime)) {
            mStartTime = System.currentTimeMillis();
        }

        //  the call is ended.
        if (TextUtils.equals(CALL_STATE_ENDED, newState)) {
            mStartTime = -1;
        }

        List<MXCallListener> listeners = getCallListeners();

        for (MXCallListener listener : listeners) {
            try {
                listener.onStateDidChange(newState);
            } catch (Exception e) {
                Log.e(LOG_TAG, "dispatchOnCallErrorOnStateDidChange : " + e.getMessage());
            }
        }
    }

    /**
     * dispatch the onCallAnsweredElsewhere event
     */
    protected void dispatchAnsweredElsewhere() {
        Log.d(LOG_TAG, "dispatchAnsweredElsewhere");

        List<MXCallListener> listeners = getCallListeners();

        for (MXCallListener listener : listeners) {
            try {
                listener.onCallAnsweredElsewhere();
            } catch (Exception e) {
                Log.e(LOG_TAG, "dispatchAnsweredElsewhere : " + e.getMessage());
            }
        }
    }

    /**
     * dispatch the onCallEnd event
     */
    protected void dispatchOnCallEnd() {
        Log.d(LOG_TAG, "dispatchOnCallEnd");

        List<MXCallListener> listeners = getCallListeners();

        for (MXCallListener listener : listeners) {
            try {
                listener.onCallEnd();
            } catch (Exception e) {
                Log.e(LOG_TAG, "dispatchOnCallEnd : " + e.getMessage());
            }
        }
    }

    /**
     * Send the next pending events
     */
    protected void sendNextEvent() {
        mUIThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                // do not send any new message
                if (isCallEnded() && (null != mPendingEvents)) {
                    mPendingEvents.clear();
                }

                // ready to send
                if ((null == mPendingEvent) && (0 != mPendingEvents.size())) {
                    mPendingEvent = mPendingEvents.get(0);
                    mPendingEvents.remove(mPendingEvent);

                    mCallSignalingRoom.sendEvent(mPendingEvent, new ApiCallback<Void>() {
                        @Override
                        public void onSuccess(Void info) {
                            mUIThreadHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    mPendingEvent = null;
                                    sendNextEvent();
                                }
                            });
                        }

                        private void commonFailure() {
                            // let try next candidate event
                            if (TextUtils.equals(mPendingEvent.type, Event.EVENT_TYPE_CALL_CANDIDATES)) {
                                mUIThreadHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        mPendingEvent = null;
                                        sendNextEvent();
                                    }
                                });
                            } else {
                                hangup("");
                            }
                        }

                        @Override
                        public void onNetworkError(Exception e) {
                            commonFailure();
                        }

                        @Override
                        public void onMatrixError(MatrixError e) {
                            commonFailure();
                        }

                        @Override
                        public void onUnexpectedError(Exception e) {
                            commonFailure();
                        }
                    });
                }
            }
        });
    }

    /**
     * send an hang up event
     * @param reason the reason
     */
    protected void sendHangup(String reason) {
        JsonObject hangupContent = new JsonObject();

        hangupContent.add("version", new JsonPrimitive(0));
        hangupContent.add("call_id", new JsonPrimitive(this.mCallId));

        if (!TextUtils.isEmpty(reason)) {
            hangupContent.add("reason", new JsonPrimitive(reason));
        }

        Event event = new Event(Event.EVENT_TYPE_CALL_HANGUP, hangupContent, mSession.getCredentials().userId, mCallSignalingRoom.getRoomId());

        mUIThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                dispatchOnCallEnd();
            }
        });

        Log.d(LOG_TAG, "sendHangup " + reason);

        mCallSignalingRoom.sendEvent(event, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                Log.d(LOG_TAG, "sendHangup done");
            }

            @Override
            public void onNetworkError(Exception e) {
                Log.d(LOG_TAG, "sendHangup onNetworkError " + e.getMessage());
            }

            @Override
            public void onMatrixError(MatrixError e) {
                Log.d(LOG_TAG, "sendHangup onMatrixError " + e.getLocalizedMessage());
            }

            @Override
            public void onUnexpectedError(Exception e) {
                Log.d(LOG_TAG, "sendHangup onUnexpectedError " + e.getMessage());
            }
        });
    }
}
