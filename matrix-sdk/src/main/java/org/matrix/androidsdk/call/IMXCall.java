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

import java.io.Serializable;

/**
 * Audio/video call interface.
 * See {@link MXJingleCall} and {@link MXChromeCall}.
 */
public interface IMXCall {

    // call ending use cases (see {@link #dispatchOnCallEnd}):
    int END_CALL_REASON_UNDEFINED = -1;
    /** the callee has rejected the incoming call **/
    int END_CALL_REASON_PEER_HANG_UP = 0;
    /** the callee has rejected the incoming call from another device **/
    int END_CALL_REASON_PEER_HANG_UP_ELSEWHERE = 1;
    /** call ended by the local user himself **/
    int END_CALL_REASON_USER_HIMSELF = 2;

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

    class VideoLayoutConfiguration implements Serializable{
        public final static int INVALID_VALUE = -1;

        @Override
        public String toString() {
            return "VideoLayoutConfiguration{" +
                    "mIsPortrait=" + mIsPortrait +
                    ", X=" + mX +
                    ", Y=" + mY +
                    ", Width=" + mWidth +
                    ", Height=" + mHeight +
                    '}';
        }

        // parameters of the video of the local user (small video)
        /** margin left in percentage of the screen resolution for the local user video **/
        public int mX;
        /** margin top in percentage of the screen resolution for the local user video **/
        public int mY;

        /** width in percentage of the screen resolution for the local user video **/
        public int mWidth;
        /** video height in percentage of the screen resolution for the local user video **/
        public int mHeight;

        public boolean mIsPortrait;

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
         * @param callView the callview
         */
        void onViewLoading(View callView);

        /**
         * Warn when the call view is ready
         */
        void onViewReady();

        /**
         * The call was answered on another device
         */
        void onCallAnsweredElsewhere();

        /**
         * Warn that the call is ended
         * @param aReasonId the reason of the call ending
         */
        void onCallEnd(final int aReasonId);

        /**
         * The video preview size has been updated.
         * @param width the new width (non scaled size)
         * @param height the new height (non scaled size)
         */
        void onPreviewSizeChanged(int width, int height);
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

    // actions (must be done after dispatchOnViewReady()
    /**
     * Start a call.
     * @param aLocalVideoPosition position of the local video attendee
     */
    void placeCall(VideoLayoutConfiguration aLocalVideoPosition);

    /**
     * Prepare a call reception.
     * @param aCallInviteParams the invitation Event content
     * @param aCallId the call ID
     * @param aLocalVideoPosition position of the local video attendee
     */
    void prepareIncomingCall(JsonObject aCallInviteParams, String aCallId, VideoLayoutConfiguration aLocalVideoPosition);

    /**
     * The call has been detected as an incoming one.
     * The application launched the dedicated activity and expects to launch the incoming call.
     * @param aLocalVideoPosition position of the local video attendee
     */
    void launchIncomingCall(VideoLayoutConfiguration aLocalVideoPosition);

     /**
     * The video will be displayed according to the values set in aConfigurationToApply.
     * @param aConfigurationToApply the new position to be applied
     */
    void updateLocalVideoRendererPosition(VideoLayoutConfiguration aConfigurationToApply);

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

    /**
     * Add a listener to the call manager.
     */
    void addListener(MXCallListener callListener);

    /**
     * Remove a listener from the call manager.
     */
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
     * Set the linked rooms (conference call)
     * @param room the room
     * @param callSignalingRoom the call signaling room.
     */
    void setRooms(Room room, Room callSignalingRoom);

    /**
     * @return the call signaling room
     */
    Room getCallSignalingRoom();

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
     * Set the call type: video or voice
     * @param isVideo true for video call, false for VoIP
     */
    void setIsVideo(boolean isVideo);

    /**
     * @return true if the call is a video call.
     */
    boolean isVideo();

    /**
     * Defines the call conference status
     */
    void setIsConference(boolean isConference);

    /**
     * @return true if the call is a conference call.
     */
    boolean isConference();

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
     * @return the call start time in ms since epoch, -1 if not defined.
     */
    long getCallStartTime();

    /**
     * @return the call elapsed time in seconds, -1 if not defined.
     */
    long getCallElapsedTime();

    /**
     * Switch between device cameras. The transmitted stream is modified
     * according to the new camera in use.
     * If the camera used in the video call is the front one, calling
     * switchRearFrontCamera(), will make the rear one to be used, and vice versa.
     * If only one camera is available, nothing is done.
     * @return true if the switch succeed, false otherwise.
     */
    boolean switchRearFrontCamera();

    /**
     * Indicate if a camera switch was performed or not.
     * For some reason switching the camera from front to rear and
     * vice versa, could not be performed (ie. only one camera is available).
     *
     * <br>See {@link #switchRearFrontCamera()}.
     * @return true if camera was switched, false otherwise
     */
    boolean isCameraSwitched();

    /**
     * Indicate if the device supports camera switching.
     * <p>See {@link #switchRearFrontCamera()}.
     * @return true if switch camera is supported, false otherwise
     */
    boolean isSwitchCameraSupported();

    /**
     * Mute/Unmute the recording of the local video attendee. Set isVideoMuted
     * to true to enable the recording of the video, if set to false no recording
     * is performed.
     * @param isVideoMuted true to mute the video recording, false to unmute
     */
    void muteVideoRecording(boolean isVideoMuted);

    /**
     * Return the recording mute status of the local video attendee.
     *
     * <br>See {@link #muteVideoRecording(boolean)}.
     * @return true if video recording is muted, false otherwise
     */
    boolean isVideoRecordingMuted();


}