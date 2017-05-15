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
import android.os.Handler;
import android.text.TextUtils;
import org.matrix.androidsdk.util.Log;
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

    /**
     * The session
     */
    protected MXSession mSession;

    /**
     * The context
     */
    protected Context mContext;

    /**
     * the turn servers
     */
    protected JsonElement mTurnServer;

    /**
     * The room in which the call is performed.
     */
    protected Room mCallingRoom;

    /**
     * The room in which the call events are sent.
     * It might differ from mCallingRoom if it is a conference call.
     * For a 1:1 call, it will be equal to mCallingRoom.
     */
    protected Room mCallSignalingRoom;

    /**
     * The call events listeners
     */
    private final ArrayList<MXCallListener> mCallListeners = new ArrayList<>();

    /**
     * the call id
     */
    protected String mCallId;

    /**
     * Tells if it is a video call
     */
    protected boolean mIsVideoCall = false;

    /**
     * Tells if it is an incoming call
     */
    protected boolean mIsIncoming = false;

    /**
     * Tells if it is a conference call.
     */
    private boolean mIsConference = false;

    /**
     * List of events to sends to mCallSignalingRoom
     */
    protected final ArrayList<Event> mPendingEvents = new ArrayList<>();

    /**
     * The sending eevent.
     */
    private Event mPendingEvent;

    /**
     * The not responding timer
     */
    protected Timer mCallTimeoutTimer;

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

    // actions (must be done after dispatchOnViewReady()
    /**
     * Start a call.
     */
    public void placeCall(VideoLayoutConfiguration aLocalVideoPosition) {
    }

    /**
     * Prepare a call reception.
     * @param aCallInviteParams the invitation Event content
     * @param aCallId the call ID
     * @param aLocalVideoPosition position of the local video attendee
     */
    public void prepareIncomingCall(JsonObject aCallInviteParams, String aCallId, VideoLayoutConfiguration aLocalVideoPosition) {
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

    @Override
    public boolean switchRearFrontCamera(){
        Log.w(LOG_TAG,"## switchRearFrontCamera(): not implemented");
        return false;
    }

    @Override
    public boolean isCameraSwitched() {
        Log.w(LOG_TAG,"## isCameraSwitched(): not implemented");
        return false;
    }

    @Override
    public boolean isSwitchCameraSupported() {
        Log.w(LOG_TAG,"## isSwitchCameraSupported(): not implemented");
        return false;
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

    /*
     * Set the linked room
     * @param room the room
     */
    /*public void setRoom(Room room) {
        setRooms(room, room);
    }*/

    /**
     * Set the linked rooms.
     * @param room the room where the conference take place
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
     * Dispatch the onViewLoading event to the listeners.
     * @param callView the callview
     */
    protected void dispatchOnViewLoading(View callView) {
        if (isCallEnded()) {
            Log.d(LOG_TAG, "## dispatchOnViewLoading(): the call is ended");
            return;
        }

        Log.d(LOG_TAG, "## dispatchOnViewLoading()");

        List<MXCallListener> listeners = getCallListeners();

        for (MXCallListener listener : listeners) {
            try {
                listener.onViewLoading(callView);
            } catch (Exception e) {
                Log.e(LOG_TAG,"## dispatchOnViewLoading(): Exception Msg="+e.getMessage());
           }
        }
    }

    /**
     * Dispatch the onViewReady event to the listeners.
     */
    protected void dispatchOnViewReady() {
        if (isCallEnded()) {
            Log.d(LOG_TAG, "## dispatchOnViewReady() : the call is ended");
            return;
        }

        Log.d(LOG_TAG, "## dispatchOnViewReady()");

        List<MXCallListener> listeners = getCallListeners();

        for (MXCallListener listener : listeners) {
            try {
                listener.onViewReady();
            } catch (Exception e) {
                Log.e(LOG_TAG,"## dispatchOnViewReady(): Exception Msg="+e.getMessage());
            }
        }
    }

    /**
     * Dispatch the onCallError event to the listeners.
     * @param error error message
     */
    protected void dispatchOnCallError(String error) {
        if (isCallEnded()) {
            Log.d(LOG_TAG, "## dispatchOnCallError() : the call is ended");
            return;
        }

        Log.d(LOG_TAG, "## dispatchOnCallError()");

        List<MXCallListener> listeners = getCallListeners();

        for (MXCallListener listener : listeners) {
            try {
                listener.onCallError(error);
            } catch (Exception e) {
                Log.e(LOG_TAG, "## dispatchOnCallError(): " + e.getMessage());
            }
        }
    }

    /**
     * Dispatch the onStateDidChange event to the listeners.
     * @param newState the new state
     */
    protected void dispatchOnStateDidChange(String newState) {
    Log.d(LOG_TAG, "## dispatchOnCallErrorOnStateDidChange(): " + newState);

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
                Log.e(LOG_TAG,"## dispatchOnStateDidChange(): Exception Msg="+e.getMessage());
            }
        }
    }

    /**
     * Dispatch the onCallAnsweredElsewhere event to the listeners.
     */
    protected void dispatchAnsweredElsewhere() {
        Log.d(LOG_TAG, "## dispatchAnsweredElsewhere()");

        List<MXCallListener> listeners = getCallListeners();

        for (MXCallListener listener : listeners) {
            try {
                listener.onCallAnsweredElsewhere();
            } catch (Exception e) {
                Log.e(LOG_TAG,"## dispatchAnsweredElsewhere(): Exception Msg="+e.getMessage());
            }
        }
    }

    /**
     * Dispatch the onCallEnd event to the listeners.
     * @param aEndCallReasonId the reason of the call ending
     */
    protected void dispatchOnCallEnd(int aEndCallReasonId) {
        Log.d(LOG_TAG, "## dispatchOnCallEnd(): endReason="+aEndCallReasonId);

        List<MXCallListener> listeners = getCallListeners();

        for (MXCallListener listener : listeners) {
            try {
                listener.onCallEnd(aEndCallReasonId);
            } catch (Exception e) {
                Log.e(LOG_TAG,"## dispatchOnCallEnd(): Exception Msg="+e.getMessage());
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
                            if (TextUtils.equals(mPendingEvent.getType(), Event.EVENT_TYPE_CALL_CANDIDATES)) {
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
     * Dispatch the onPreviewSizeChanged event to the listeners.
     * @param width the preview width
     * @param height the preview height
     */
    protected void dispatchOnPreviewSizeChanged(int width, int height) {
        Log.d(LOG_TAG, "## dispatchOnPreviewSizeChanged(): width =" + width + " - height =" + height);

        List<MXCallListener> listeners = getCallListeners();

        for (MXCallListener listener : listeners) {
            try {
                listener.onPreviewSizeChanged(width, height);
            } catch (Exception e) {
                Log.e(LOG_TAG,"## dispatchOnPreviewSizeChanged(): Exception Msg="+e.getMessage());
            }
        }
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

		// local notification to indicate the end of call
        mUIThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                dispatchOnCallEnd(END_CALL_REASON_USER_HIMSELF);
            }
        });

        Log.d(LOG_TAG, "## sendHangup(): reason=" + reason);

        // send hang up event to the server
        mCallSignalingRoom.sendEvent(event, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                Log.d(LOG_TAG, "## sendHangup(): onSuccess");
            }

            @Override
            public void onNetworkError(Exception e) {
                Log.d(LOG_TAG, "## sendHangup(): onNetworkError Msg=" + e.getMessage());
            }

            @Override
            public void onMatrixError(MatrixError e) {
                Log.d(LOG_TAG, "## sendHangup(): onMatrixError Msg=" + e.getLocalizedMessage());
            }

            @Override
            public void onUnexpectedError(Exception e) {
                Log.d(LOG_TAG, "## sendHangup(): onUnexpectedError Msg=" + e.getMessage());
            }
        });
    }

    @Override
    public void muteVideoRecording(boolean isVideoMuted){
        Log.w(LOG_TAG,"## muteVideoRecording(): not implemented");
    }

    @Override
    public boolean isVideoRecordingMuted() {
        Log.w(LOG_TAG,"## muteVideoRecording(): not implemented - default value = false");
        return false;
    }
}
