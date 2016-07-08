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

import android.view.View;

import com.google.gson.JsonObject;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.rest.model.Event;

/**
 * A call interface
 */
public interface IMXCall {
    // call state events
    // the call is an empty shell nothing has been initialized
    String CALL_STATE_CREATED = "IMXCall.CALL_STATE_CREATED";

    // the callview has been created
    String CALL_STATE_CREATING_CALL_VIEW = "IMXCall.CALL_STATE_CREATING_CALL_VIEW";

    // the call is preparing
    String CALL_STATE_FLEDGLING = "IMXCall.CALL_STATE_FLEDGLING";

    // incoming/outgoing calls : initializing the local audio / video
    String CALL_STATE_WAIT_LOCAL_MEDIA = "IMXCall.CALL_STATE_WAIT_LOCAL_MEDIA";

    // incoming calls : the local media is retrieved
    String CALL_STATE_WAIT_CREATE_OFFER = "IMXCall.CALL_STATE_WAIT_CREATE_OFFER";

    // outgoing calls : the call invitation is sent
    String CALL_STATE_INVITE_SENT = "IMXCall.CALL_STATE_INVITE_SENT";

    // the device is ringing
    // incoming calls : after applying the incoming params
    // outgoing calls : after getting the m.call.invite echo
    String CALL_STATE_RINGING = "IMXCall.CALL_STATE_RINGING";

    // incoming calls : create the call answer
    String CALL_STATE_CREATE_ANSWER = "IMXCall.CALL_STATE_CREATE_ANSWER";

    // the call connection is connecting
    String CALL_STATE_CONNECTING = "IMXCall.CALL_STATE_CONNECTING";

    //
    String CALL_STATE_CONNECTED = "IMXCall.CALL_STATE_CONNECTED";

    // call is ended
    String CALL_STATE_ENDED = "IMXCall.CALL_STATE_ENDED";

    // error codes
    // cannot initialize the camera
    String CALL_ERROR_CAMERA_INIT_FAILED = "IMXCall.CALL_ERROR_CAMERA_INIT_FAILED";

    // cannot initialize the call.
    String CALL_ERROR_CALL_INIT_FAILED = "IMXCall.CALL_ERROR_CALL_INIT_FAILED";

    // ICE error
    String CALL_ERROR_ICE_FAILED = "IMXCall.CALL_ERROR_ICE_FAILED";

    // the user did not respond to the call.
    String CALL_ERROR_USER_NOT_RESPONDING = "IMXCall.CALL_ERROR_USER_NOT_RESPONDING";


    class VideoLayoutConfiguration {
        public final static int INVALID_VALUE = -1;

        // parameters of the video of the local user (small video)
        /** margin left in percentage of screen resolution for the local user video **/
        public int mX;
        /** margin top in percentage of screen resolution for the local user video **/
        public int mY;

        /** width in percentage of screen resolution for the local user video **/
        public int mWidth;
        /** video height in percentage of screen resolution for the local user video **/
        public int mHeight;

        public boolean isPortrait;

        public VideoLayoutConfiguration(int aX, int aY, int aWidth, int aHeight) {
            mX = aX;
            mY = aY;
            mWidth = aWidth;
            mHeight = aHeight;
        }

        public VideoLayoutConfiguration() {
            mX = INVALID_VALUE;
            mY = INVALID_VALUE;
            mWidth = INVALID_VALUE;
            mHeight = INVALID_VALUE;
        }
    }

    interface MXCallListener {
        /**
         * Called when the call state change
         */
        void onStateDidChange(String state);

        /**
         * Called when the call fails
         */
        void onCallError(String error);

        /**
         * The callview must be added to a layout
         * @param callview the callview
         */
        void onViewLoading(View callview);

        /**
         * Warn when the call view is ready
         */
        void onViewReady();

        /**
         * The call was answered on another device
         */
        void onCallAnsweredElsewhere();

        /**
         * Warn that the call isEnded
         */
        void onCallEnd();
    }

    // creator

    /**
     * Create the callview
     */
    void createCallView();

    /**
     * The activity is paused.
     */
    void onPause();

    /**
     * The activity is resumed.
     */
    void onResume();

    // actions (must be done after onViewReady()
    /**
     * Start a call.
     */
    void placeCall();

    /**
     * Prepare a call reception.
     * @param callInviteParams the invitation Event content
     * @param callId the call ID
     */
    void prepareIncomingCall(JsonObject callInviteParams, String callId);

    /**
     * The call has been detected as an incoming one.
     * The application launched the dedicated activity and expects to launch the incoming call.
     */
    void launchIncomingCall();

    /**
     * Set the layout configuration used in the video call over IP.
     */
    void setVideoLayoutParameters(VideoLayoutConfiguration aConfigurationToApply);

    /**
     * Force the update of the small local video renderer
     */
    void updateSmallLocalVideoRenderer();

    // events thread
    /**
     * Manage the call events.
     * @param event the call event.
     */
    void handleCallEvent(Event event);

    // user actions
    /**
     * The call is accepted.
     */
    void answer();

    /**
     * The call has been has answered on another device.
     */
    void onAnsweredElsewhere();

    /**
     * The call is hung up.
     */
    void hangup(String reason);

    // listener managemenent
    void addListener(MXCallListener callListener);
    void removeListener(MXCallListener callListener);

    // getters / setters
    /**
     * @return the callId
     */
    String getCallId();

    /**
     * Set the callId
     */
    void setCallId(String callId);

    /**
     * @return the linked room
     */
    Room getRoom();

    /**
     * Set the linked room.
     * @param room the room
     */
    void setRoom(Room room);

    /**
     * @return the session
     */
    MXSession getSession();

    /**
     * @return true if the call is an incoming call.
     */
    boolean isIncoming();

    /**
     * @param isIncoming true if the call is an incoming one.
     */
    void setIsIncoming(boolean isIncoming);

    /**
     * Defines the call type
     */
    void setIsVideo(boolean isVideo);

    /**
     * @return true if the call is a video call.
     */
    boolean isVideo();

    /**
     * @return the callstate (must be a CALL_STATE_XX value)
     */
    String getCallState();

    /**
     * @return the callView
     */
    View getCallView();

    /**
     * @return the callView visibility
     */
    int getVisibility();

    /**
     * Set the callview visibility
     * @return true if the operation succeeds
     */
    boolean setVisibility(int visibility);

    /**
     * Toogle the speaker
     */
    void toggleSpeaker();

    /**
     * @return the call start time in ms since epoch, -1 if not defined.
     */
    long getCallStartTime();

    /**
     * @return the call elapsed time in seconds, -1 if not defined.
     */
    long getCallElapsedTime();
}