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
import android.hardware.Camera;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.rest.model.Event;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoCapturerAndroid;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoRendererGui;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class MXJingleCall extends MXCall {

    private static final String LOG_TAG = "MXJingleCall";

    private static final String VIDEO_TRACK_ID = "ARDAMSv0";
    private static final String AUDIO_TRACK_ID = "ARDAMSa0";

    private static final String MIN_VIDEO_WIDTH_CONSTRAINT = "minWidth";

    private static final int MIN_VIDEO_WIDTH = 640;
    private static final int CAMERA_TYPE_FRONT = 1;
    private static final int CAMERA_TYPE_REAR = 2;
    private static final int CAMERA_TYPE_UNDEFINED = -1;

    static private PeerConnectionFactory mPeerConnectionFactory = null;
    static private String mFrontCameraName = null;
    static private String mBackCameraName = null;
    static private VideoCapturer mVideoCapturer = null;

    private GLSurfaceView mCallView = null;

    private boolean mIsCameraSwitched;
    private boolean mIsVideoSourceStopped = false;
    private VideoSource mVideoSource = null;
    private VideoTrack  mLocalVideoTrack = null;
    private AudioSource mAudioSource = null;
    private AudioTrack  mLocalAudioTrack = null;
    private MediaStream mLocalMediaStream = null;

    private VideoTrack mRemoteVideoTrack = null;
    private PeerConnection mPeerConnection = null;

    // default value
    private String mCallState = CALL_STATE_CREATED;

    private boolean mUsingLargeLocalRenderer = true;
    private VideoRenderer mLargeRemoteRenderer = null;
    private VideoRenderer mSmallLocalRenderer = null;
    private int mLocalRenderWidth = -1;
    private int mLocalRenderHeight = -1;

    private VideoRenderer.Callbacks mLargeLocalRendererCallbacks = null;
    private VideoRenderer.Callbacks mSmallLocalRendererCallbacks;
    private VideoRenderer mLargeLocalRenderer = null;

    private static boolean mIsInitialized = false;
    // null -> not initialized
    // true / false for the supported status
    private static Boolean mIsSupported;

    // candidate management
    private boolean mIsIncomingPrepared = false;
    private JsonArray mPendingCandidates = new JsonArray();

    private JsonObject mCallInviteParams = null;
    private int mCameraInUse = CAMERA_TYPE_UNDEFINED;

    /**
     * @return true if this stack can perform calls.
     */
    public static boolean isSupported(Context context) {
        if (null == mIsSupported) {
            mIsSupported = Build.VERSION.SDK_INT > Build.VERSION_CODES.ICE_CREAM_SANDWICH;

            // the call initialisation is not yet done
            if (mIsSupported) {
                initializeAndroidGlobals(context.getApplicationContext());
            }

            Log.d(LOG_TAG, "isSupported " + mIsSupported);
        }

        return mIsSupported;
    }

    /**
     * Class creator
     * @param session the session
     * @param context the context
     * @param turnServer the turn server
     */
    public MXJingleCall(MXSession session, Context context, JsonElement turnServer) {
        if (!isSupported(context)) {
            throw new AssertionError("MXJingleCall : not supported with the current android version");
        }

        if (null == session) {
            throw new AssertionError("MXJingleCall : session cannot be null");
        }

        if (null == context) {
            throw new AssertionError("MXJingleCall : context cannot be null");
        }

        Log.d(LOG_TAG, "MXJingleCall constructor " + turnServer);

        mCallId = "c" + System.currentTimeMillis();
        mSession = session;
        mContext = context;
        mTurnServer = turnServer;
    }

    /**
     * Initialize the jingle globals
     */
    private static void initializeAndroidGlobals(Context context) {
        if (!mIsInitialized) {
            try {
                mIsInitialized = PeerConnectionFactory.initializeAndroidGlobals(
                        context,
                        true, // enable audio initializing
                        true, // enable video initializing
                        true, // enable hardware acceleration
                        VideoRendererGui.getEGLContext());

                PeerConnectionFactory.initializeFieldTrials(null);
                mIsSupported = true;
                Log.d(LOG_TAG,"## initializeAndroidGlobals(): mIsInitialized="+mIsInitialized);
            } catch (UnsatisfiedLinkError e) {
                Log.e(LOG_TAG, "## initializeAndroidGlobals(): Exception Msg=" + e.getMessage());
                mIsInitialized = true;
                mIsSupported = false;
            } catch (Exception e) {
                Log.e(LOG_TAG, "## initializeAndroidGlobals(): Exception Msg=" + e.getMessage());
                mIsInitialized = true;
                mIsSupported = false;
            }
        }
    }

    /**
     * Create the callviews
     */
    @Override
    public void createCallView() {
        if ((null != mIsSupported) && mIsSupported) {
            Log.d(LOG_TAG, "MXJingleCall createCallView");

            dispatchOnStateDidChange(CALL_STATE_CREATING_CALL_VIEW);
            mUIThreadHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mCallView = new GLSurfaceView(mContext); // set the GLSurfaceView where it should render to
                    mCallView.setVisibility(View.GONE);

                    dispatchOnViewLoading(mCallView);

                    mUIThreadHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            dispatchOnStateDidChange(CALL_STATE_FLEDGLING);
                            dispatchOnViewReady();
                        }
                    });
                }
            }, 10);
        }
    }

    /**
     * The connection is terminated
     * @param endCallReasonId the reason of the call ending
     */
    private void terminate(final int endCallReasonId) {
        Log.d(LOG_TAG, "## terminate(): reason= "+endCallReasonId);

        if (isCallEnded()) {
            return;
        }

        dispatchOnStateDidChange(CALL_STATE_ENDED);

        boolean isPeerConnectionFactoryAllowed = false;

        if (null != mPeerConnection) {
            mPeerConnection.dispose();
            mPeerConnection = null;
            // the call has been initialized so mPeerConnectionFactory can be released
            isPeerConnectionFactoryAllowed = true;
        }

        if (null != mVideoSource) {
            mVideoSource.dispose();
            mVideoSource = null;
        }

        if (null != mAudioSource) {
            mAudioSource.dispose();
            mAudioSource = null;
        }

        // mPeerConnectionFactory is static so it might be used by another call
        // so we test that the current has been created
        if (isPeerConnectionFactoryAllowed && (null != mPeerConnectionFactory)) {
            mPeerConnectionFactory.dispose();
            mPeerConnectionFactory = null;
        }

        if (null != mCallView) {
            final View fCallView = mCallView;

            fCallView.post(new Runnable() {
                @Override
                public void run() {
                    fCallView.setVisibility(View.GONE);
                }
            });

            mCallView = null;
        }

        mUIThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                dispatchOnCallEnd(endCallReasonId);
            }
        });
    }

    /**
     * Send the invite event
     * @param sessionDescription the session description.
     */
    private void sendInvite(final SessionDescription sessionDescription) {
        // check if the call has not been killed
        if (isCallEnded()) {
            Log.d(LOG_TAG, "MXJingleCall isCallEnded");
            return;
        }

        Log.d(LOG_TAG, "MXJingleCall sendInvite");

        // build the invitation event
        JsonObject inviteContent = new JsonObject();
        inviteContent.addProperty("version", 0);
        inviteContent.addProperty("call_id", mCallId);
        inviteContent.addProperty("lifetime", 60000);

        JsonObject offerContent = new JsonObject();
        offerContent.addProperty("sdp", sessionDescription.description);
        offerContent.addProperty("type", sessionDescription.type.canonicalForm());
        inviteContent.add("offer", offerContent);

        Event event = new Event(Event.EVENT_TYPE_CALL_INVITE, inviteContent, mSession.getCredentials().userId, mCallSignalingRoom.getRoomId());

        mPendingEvents.add(event);
            mCallTimeoutTimer = new Timer();
            mCallTimeoutTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        if (getCallState().equals(IMXCall.CALL_STATE_RINGING) || getCallState().equals(IMXCall.CALL_STATE_INVITE_SENT)) {
                            Log.d(LOG_TAG, "sendInvite : CALL_ERROR_USER_NOT_RESPONDING");
                            dispatchOnCallError(CALL_ERROR_USER_NOT_RESPONDING);
                            hangup(null);
                        }

                        // cancel the timer
                        mCallTimeoutTimer.cancel();
                        mCallTimeoutTimer = null;
                    } catch (Exception e) {
                        Log.e(LOG_TAG, "## sendInvite(): Exception Msg= " + e.getMessage());
                    }
                }
            }, 60 * 1000);

        sendNextEvent();
    }

    /**
     * Send the answer event
     * @param sessionDescription the session description
     */
    private void sendAnswer(final SessionDescription sessionDescription) {
        // check if the call has not been killed
        if (isCallEnded()) {
            Log.d(LOG_TAG, "sendAnswer isCallEnded");
            return;
        }

        Log.d(LOG_TAG, "sendAnswer");

        // build the invitation event
        JsonObject answerContent = new JsonObject();
        answerContent.addProperty("version", 0);
        answerContent.addProperty("call_id", mCallId);
        answerContent.addProperty("lifetime", 60000);

        JsonObject offerContent = new JsonObject();
        offerContent.addProperty("sdp", sessionDescription.description);
        offerContent.addProperty("type", sessionDescription.type.canonicalForm());
        answerContent.add("answer", offerContent);

        Event event = new Event(Event.EVENT_TYPE_CALL_ANSWER, answerContent, mSession.getCredentials().userId, mCallSignalingRoom.getRoomId());

        mPendingEvents.add(event);
        sendNextEvent();
    }

    @Override
    public void updateLocalVideoRendererPosition(VideoLayoutConfiguration aConfigurationToApply) {
        try {
            // compute the new layout
            if ((null != mSmallLocalRendererCallbacks) && (null != aConfigurationToApply)) {
                VideoRendererGui.update(mSmallLocalRendererCallbacks, aConfigurationToApply.mX, aConfigurationToApply.mY, aConfigurationToApply.mWidth, aConfigurationToApply.mHeight, VideoRendererGui.ScalingType.SCALE_ASPECT_FIT, true);
                Log.d(LOG_TAG, "## updateLocalVideoRendererPosition(): X=" + aConfigurationToApply.mX + " Y=" + aConfigurationToApply.mY + " width=" + aConfigurationToApply.mWidth + " height" + aConfigurationToApply.mHeight);
            } else {
                Log.w(LOG_TAG,"## updateLocalVideoRendererPosition(): Skipped due to invalid parameters");
            }
        } catch (Exception e) {
            Log.e(LOG_TAG,"## updateLocalVideoRendererPosition(): Exception Msg="+e.getMessage());
            return;
        }

        if(null != mCallView) {
            mCallView.postInvalidate();
        } else {
            Log.w(LOG_TAG,"## updateLocalVideoRendererPosition(): Skipped due to mCallView = null");
        }
    }

    @Override
    public boolean isSwitchCameraSupported() {
        return (VideoCapturerAndroid.getDeviceCount()>1);
    }

    @Override
    public boolean switchRearFrontCamera() {
        boolean isCameraSwitched = false;

        if ((null != mVideoCapturer) && (isSwitchCameraSupported())){
            VideoCapturerAndroid videoCapturerAndroid = (VideoCapturerAndroid)mVideoCapturer;

            if(true == (isCameraSwitched=videoCapturerAndroid.switchCamera(null))) {
                // toggle the video capturer instance
                if (CAMERA_TYPE_FRONT == mCameraInUse) {
                    mCameraInUse = CAMERA_TYPE_REAR;
                } else {
                    mCameraInUse = CAMERA_TYPE_FRONT;
                }

                // compute camera switch new status
                mIsCameraSwitched = !mIsCameraSwitched;

                mUIThreadHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        listenPreviewUpdate();
                    }
                }, 100);

            } else {
                Log.w(LOG_TAG,"## switchRearFrontCamera(): failed");
            }
        } else {
            Log.w(LOG_TAG,"## switchRearFrontCamera(): failure - invalid values");
        }
        return isCameraSwitched;
    }

    @Override
    public void muteVideoRecording(boolean muteValue){
        Log.d(LOG_TAG,"## muteVideoRecording(): muteValue="+ muteValue);

        if (!isCallEnded()) {
            if (null != mLocalVideoTrack) {
                mLocalVideoTrack.setEnabled(!muteValue);
            } else {
                Log.d(LOG_TAG, "## muteVideoRecording(): failure - invalid value");
            }
        } else {
            Log.d(LOG_TAG, "## muteVideoRecording(): the call is ended");
        }
    }

    @Override
    public boolean isVideoRecordingMuted(){
        boolean isMuted = false;

        if (!isCallEnded()) {
            if (null != mLocalVideoTrack) {
                isMuted = !mLocalVideoTrack.enabled();
            } else {
                Log.w(LOG_TAG, "## isVideoRecordingMuted(): failure - invalid value");
            }

            Log.d(LOG_TAG, "## isVideoRecordingMuted() = " + isMuted);
        }  else {
            Log.d(LOG_TAG, "## isVideoRecordingMuted() : the call is ended");
        }

        return isMuted;
    }

    @Override
    public boolean isCameraSwitched(){
        return mIsCameraSwitched;
    }

    /**
     * create the local stream
     */
    private void createLocalStream() {
        Log.d(LOG_TAG, "## createLocalStream(): IN");

        // check there is at least one stream to start a call
        if ((null == mLocalVideoTrack) && (null == mLocalAudioTrack)) {
            Log.d(LOG_TAG, "## createLocalStream(): CALL_ERROR_CALL_INIT_FAILED");

            dispatchOnCallError(CALL_ERROR_CALL_INIT_FAILED);
            hangup("no_stream");
            terminate(IMXCall.END_CALL_REASON_UNDEFINED);
            return;
        }

        // create our local stream to add our audio and video tracks
        mLocalMediaStream = mPeerConnectionFactory.createLocalMediaStream("ARDAMS");
        // add video track to local stream
        if (null != mLocalVideoTrack) {
            mLocalMediaStream.addTrack(mLocalVideoTrack);
        }
        // add audio track to local stream
        if (null != mLocalAudioTrack) {
            mLocalMediaStream.addTrack(mLocalAudioTrack);
        }

        // build ICE servers list
        ArrayList<PeerConnection.IceServer> iceServers = new ArrayList<>();

        if (null != mTurnServer) {
            try {
                String username = null;
                String password = null;
                JsonObject object = mTurnServer.getAsJsonObject();

                if (object.has("username")) {
                    username = object.get("username").getAsString();
                }

                if (object.has("password")) {
                    password = object.get("password").getAsString();
                }

                JsonArray uris = object.get("uris").getAsJsonArray();

                for(int index = 0; index < uris.size(); index++) {
                    String url = uris.get(index).getAsString();

                    if ((null != username) && (null != password)) {
                        iceServers.add(new PeerConnection.IceServer(url, username, password));
                    } else {
                        iceServers.add(new PeerConnection.IceServer(url));
                    }
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "## createLocalStream(): Exception in ICE servers list Msg=" + e.getLocalizedMessage());
            }
        }

        // define at least on server
        if (iceServers.size() == 0) {
            iceServers.add(new PeerConnection.IceServer("stun:stun.l.google.com:19302"));
        }

        // define constraints
        MediaConstraints pcConstraints = new MediaConstraints();
        pcConstraints.optional.add(new MediaConstraints.KeyValuePair("RtpDataChannels", "true"));

        // start connecting to the other peer by creating the peer connection
        mPeerConnection = mPeerConnectionFactory.createPeerConnection(
                iceServers,
                pcConstraints,
                new PeerConnection.Observer() {
                    @Override
                    public void onSignalingChange(PeerConnection.SignalingState signalingState) {
                        Log.d(LOG_TAG, "## mPeerConnection creation: onSignalingChange state=" + signalingState);
                    }

                    @Override
                    public void onIceConnectionChange(final PeerConnection.IceConnectionState iceConnectionState) {
                        Log.d(LOG_TAG, "## mPeerConnection creation: onIceConnectionChange " + iceConnectionState);
                        mUIThreadHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (iceConnectionState == PeerConnection.IceConnectionState.CONNECTED) {
                                    if ((null!=mLocalVideoTrack) && mUsingLargeLocalRenderer && isVideo()) {
                                        mLocalVideoTrack.setEnabled(false);
                                        VideoRendererGui.remove(mLargeLocalRendererCallbacks);
                                        mLocalVideoTrack.removeRenderer(mLargeLocalRenderer);

                                        // in conference call, there is no local preview,
                                        // the local attendee video is sent by the server among the others conference attendees.
                                        if (!isConference()) {
                                            // add local preview, only for 1:1 call
                                            mLocalVideoTrack.addRenderer(mSmallLocalRenderer);
                                        }

                                        listenPreviewUpdate();

                                        mLocalVideoTrack.setEnabled(true);
                                        mUsingLargeLocalRenderer = false;

                                        mCallView.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                if (null != mCallView) {
                                                    mCallView.invalidate();
                                                }
                                            }
                                        });
                                    }

                                    dispatchOnStateDidChange(IMXCall.CALL_STATE_CONNECTED);
                                }
                                // theses states are ignored
                                // only the matrix hangup event is managed
                                /*else if (iceConnectionState == PeerConnection.IceConnectionState.DISCONNECTED) {
                                    // TODO warn the user ?
                                    hangup(null);
                                } else if (iceConnectionState == PeerConnection.IceConnectionState.CLOSED) {
                                    // TODO warn the user ?
                                    terminate();
                                }*/ else if (iceConnectionState == PeerConnection.IceConnectionState.FAILED) {
                                    dispatchOnCallError(CALL_ERROR_ICE_FAILED);
                                    hangup("ice_failed");
                                }
                            }
                        });
                    }

                    @Override
                    public void onIceConnectionReceivingChange(boolean var1) {
                        Log.d(LOG_TAG, "## mPeerConnection creation: onIceConnectionReceivingChange " + var1);
                    }

                    @Override
                    public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
                        Log.d(LOG_TAG, "## mPeerConnection creation: onIceGatheringChange " + iceGatheringState);
                    }

                    @Override
                    public void onIceCandidate(final IceCandidate iceCandidate) {
                        Log.d(LOG_TAG, "## mPeerConnection creation: onIceCandidate " + iceCandidate);

                        mUIThreadHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (!isCallEnded()) {
                                    JsonObject content = new JsonObject();
                                    content.addProperty("version", 0);
                                    content.addProperty("call_id", mCallId);

                                    JsonArray candidates = new JsonArray();
                                    JsonObject cand = new JsonObject();
                                    cand.addProperty("sdpMLineIndex", iceCandidate.sdpMLineIndex);
                                    cand.addProperty("sdpMid", iceCandidate.sdpMid);
                                    cand.addProperty("candidate", iceCandidate.sdp);
                                    candidates.add(cand);
                                    content.add("candidates", candidates);

                                    boolean addIt = true;

                                    // merge candidates
                                    if (mPendingEvents.size() > 0) {
                                        try {
                                            Event lastEvent = mPendingEvents.get(mPendingEvents.size() - 1);

                                            if (TextUtils.equals(lastEvent.getType(), Event.EVENT_TYPE_CALL_CANDIDATES)) {
                                                // return the content cast as a JsonObject
                                                // it is not a copy
                                                JsonObject lastContent = lastEvent.getContentAsJsonObject();

                                                JsonArray lastContentCandidates = lastContent.get("candidates").getAsJsonArray();
                                                JsonArray newContentCandidates = content.get("candidates").getAsJsonArray();

                                                Log.d(LOG_TAG, "Merge candidates from " + lastContentCandidates.size() + " to " + (lastContentCandidates.size() + newContentCandidates.size() + " items."));

                                                lastContentCandidates.addAll(newContentCandidates);

                                                // replace the candidates list
                                                lastContent.remove("candidates");
                                                lastContent.add("candidates", lastContentCandidates);

                                                // don't need to save anything, lastContent is a reference not a copy

                                                addIt = false;
                                            }
                                        } catch (Exception e) {
                                            Log.e(LOG_TAG,"## createLocalStream(): createPeerConnection - onIceCandidate() Exception Msg="+e.getMessage());
                                        }
                                    }

                                    if (addIt) {
                                        Event event = new Event(Event.EVENT_TYPE_CALL_CANDIDATES, content, mSession.getCredentials().userId, mCallSignalingRoom.getRoomId());

                                        mPendingEvents.add(event);
                                        sendNextEvent();
                                    }
                                }
                            }
                        });
                    }

                    @Override
                    public void onAddStream(final MediaStream mediaStream) {
                        Log.d(LOG_TAG, "## mPeerConnection creation: onAddStream " + mediaStream);

                        mUIThreadHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if ((mediaStream.videoTracks.size() == 1) && !isCallEnded()) {
                                    mRemoteVideoTrack = mediaStream.videoTracks.get(0);
                                    mRemoteVideoTrack.setEnabled(true);
                                    mRemoteVideoTrack.addRenderer(mLargeRemoteRenderer);
                                }
                            }
                        });
                    }

                    @Override
                    public void onRemoveStream(final MediaStream mediaStream) {
                        Log.d(LOG_TAG, "## mPeerConnection creation: onRemoveStream " + mediaStream);

                        mUIThreadHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (null != mRemoteVideoTrack) {
                                    mRemoteVideoTrack.dispose();
                                    mRemoteVideoTrack = null;
                                    mediaStream.videoTracks.get(0).dispose();
                                }
                            }
                        });

                    }

                    @Override
                    public void onDataChannel(DataChannel dataChannel) {
                        Log.d(LOG_TAG, "## mPeerConnection creation: onDataChannel " + dataChannel);
                    }

                    @Override
                    public void onRenegotiationNeeded() {
                        Log.d(LOG_TAG, "## mPeerConnection creation: onRenegotiationNeeded");
                    }
                });

        // send our local video and audio stream to make it seen by the other part
        mPeerConnection.addStream(mLocalMediaStream);

        MediaConstraints constraints = new MediaConstraints();
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", isVideo() ? "true" : "false"));

        // call createOffer only for outgoing calls
        if (!isIncoming()) {
            Log.d(LOG_TAG, "## createLocalStream(): !isIncoming() -> createOffer");

            mPeerConnection.createOffer(new SdpObserver() {
                @Override
                public void onCreateSuccess(SessionDescription sessionDescription) {
                    Log.d(LOG_TAG, "createOffer onCreateSuccess");

                    final SessionDescription sdp = new SessionDescription(sessionDescription.type, sessionDescription.description);

                    mUIThreadHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (mPeerConnection != null) {
                                // must be done to before sending the invitation message
                                mPeerConnection.setLocalDescription(new SdpObserver() {
                                    @Override
                                    public void onCreateSuccess(SessionDescription sessionDescription) {
                                        Log.d(LOG_TAG, "setLocalDescription onCreateSuccess");
                                    }

                                    @Override
                                    public void onSetSuccess() {
                                        Log.d(LOG_TAG, "setLocalDescription onSetSuccess");
                                        sendInvite(sdp);
                                        dispatchOnStateDidChange(IMXCall.CALL_STATE_INVITE_SENT);
                                    }

                                    @Override
                                    public void onCreateFailure(String s) {
                                        Log.e(LOG_TAG, "setLocalDescription onCreateFailure " + s);
                                        dispatchOnCallError(CALL_ERROR_CAMERA_INIT_FAILED);
                                        hangup(null);
                                    }

                                    @Override
                                    public void onSetFailure(String s) {
                                        Log.e(LOG_TAG, "setLocalDescription onSetFailure " + s);
                                        dispatchOnCallError(CALL_ERROR_CAMERA_INIT_FAILED);
                                        hangup(null);
                                    }
                                }, sdp);
                            }
                        }
                    });
                }

                @Override
                public void onSetSuccess() {
                    Log.d(LOG_TAG, "createOffer onSetSuccess");
                }

                @Override
                public void onCreateFailure(String s) {
                    Log.d(LOG_TAG, "createOffer onCreateFailure " + s);
                    dispatchOnCallError(CALL_ERROR_CAMERA_INIT_FAILED);
                }

                @Override
                public void onSetFailure(String s) {
                    Log.d(LOG_TAG, "createOffer onSetFailure " + s);
                    dispatchOnCallError(CALL_ERROR_CAMERA_INIT_FAILED);
                }
            }, constraints);

            dispatchOnStateDidChange(IMXCall.CALL_STATE_WAIT_CREATE_OFFER);
        }
    }

    /**
     * @return true if the device has a camera device
     */
    private boolean hasCameraDevice() {
        int devicesNumber = 0;
        try {
            devicesNumber = VideoCapturerAndroid.getDeviceCount();
            mFrontCameraName = VideoCapturerAndroid.getNameOfFrontFacingDevice();
            mBackCameraName = VideoCapturerAndroid.getNameOfBackFacingDevice();
        } catch (Exception e) {
            Log.e(LOG_TAG, "hasCameraDevice " + e.getLocalizedMessage());
        }

        Log.d(LOG_TAG, "hasCameraDevice():  camera number= " + devicesNumber);
        Log.d(LOG_TAG, "hasCameraDevice():  frontCameraName=" + mFrontCameraName + " backCameraName=" + mBackCameraName);

        return (null != mFrontCameraName) || (null != mBackCameraName);
    }

    /**
     * Create the local video stack
     * @return the video track
     */
    private VideoTrack createVideoTrack() { // permission crash
        Log.d(LOG_TAG, "createVideoTrack");

        // create the local renderer only if there is a camera on the device
        if (hasCameraDevice()) {

            try {
                if (null != mFrontCameraName) {
                    mVideoCapturer = VideoCapturerAndroid.create(mFrontCameraName);

                    if (null == mVideoCapturer) {
                        Log.e(LOG_TAG, "Cannot create Video Capturer from front camera");
                    } else {
                        mCameraInUse = CAMERA_TYPE_FRONT;
                    }
                }

                if ((null == mVideoCapturer) && (null != mBackCameraName)) {
                    mVideoCapturer = VideoCapturerAndroid.create(mBackCameraName);

                    if (null == mVideoCapturer) {
                        Log.e(LOG_TAG, "Cannot create Video Capturer from back camera");
                    } else {
                        mCameraInUse = CAMERA_TYPE_REAR;
                    }
                }
            } catch(Exception ex2) {
                // catch exception due to Android M permissions, when
                // a call is received and the permissions (camera and audio) were not yet granted
                Log.e(LOG_TAG, "createVideoTrack(): Exception Msg=" + ex2.getMessage());
            }

            if (null != mVideoCapturer) {
                Log.d(LOG_TAG, "createVideoTrack find a video capturer");

                try {
                    MediaConstraints videoConstraints = new MediaConstraints();

                    videoConstraints.mandatory.add(new MediaConstraints.KeyValuePair(
                            MIN_VIDEO_WIDTH_CONSTRAINT, Integer.toString(MIN_VIDEO_WIDTH)));

                    mVideoSource = mPeerConnectionFactory.createVideoSource(mVideoCapturer, videoConstraints);
                    mLocalVideoTrack = mPeerConnectionFactory.createVideoTrack(VIDEO_TRACK_ID, mVideoSource);
                    mLocalVideoTrack.setEnabled(true);
                    mLocalVideoTrack.addRenderer(mLargeLocalRenderer);
                } catch (Exception e) {
                    Log.e(LOG_TAG, "createVideoSource fails with exception " + e.getLocalizedMessage());

                    mLocalVideoTrack = null;

                    if (null != mVideoSource) {
                        mVideoSource.dispose();
                        mVideoSource = null;
                    }
                }
            } else {
                Log.e(LOG_TAG, "## createVideoTrack(): Cannot create Video Capturer - no camera available");
            }
        }

        return mLocalVideoTrack;
    }

    /**
     * Create the local video stack
     * @return the video track
     */
    private AudioTrack createAudioTrack() {
        Log.d(LOG_TAG, "createAudioTrack");

        MediaConstraints audioConstraints = new MediaConstraints();

        // add all existing audio filters to avoid having echos
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googEchoCancellation", "true"));
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googEchoCancellation2", "true"));
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googDAEchoCancellation", "true"));

        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googTypingNoiseDetection", "true"));

        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googAutoGainControl", "true"));
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googAutoGainControl2", "true"));

        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googNoiseSuppression", "true"));
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googNoiseSuppression2", "true"));

        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googAudioMirroring", "false"));
        audioConstraints.mandatory.add(new MediaConstraints.KeyValuePair("googHighpassFilter", "true"));

        mAudioSource = mPeerConnectionFactory.createAudioSource(audioConstraints);
        mLocalAudioTrack = mPeerConnectionFactory.createAudioTrack(AUDIO_TRACK_ID, mAudioSource);

        return mLocalAudioTrack;
    }

    /**
     * Initialize the call UI
     * @param callInviteParams the invite params
     * @param aLocalVideoPosition position of the local video attendee
     */
    private void initCallUI(final JsonObject callInviteParams, VideoLayoutConfiguration aLocalVideoPosition) {
        Log.d(LOG_TAG, "## initCallUI(): IN");

        if (isCallEnded()) {
            Log.w(LOG_TAG, "## initCallUI(): skipped due to call is ended");
            return;
        }

        if (isVideo()) {
            Log.d(LOG_TAG, "## initCallUI(): building UI video call");

            try {
                // pass a runnable to be run once the surface view is ready
                VideoRendererGui.setView(mCallView, new Runnable() {
                    @Override
                    public void run() {
                        mUIThreadHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (null == mPeerConnectionFactory) {
                                    Log.d(LOG_TAG, "## initCallUI(): video call and no mPeerConnectionFactory");

                                    mPeerConnectionFactory = new PeerConnectionFactory();
                                    createVideoTrack();
                                    createAudioTrack();
                                    createLocalStream();

                                    if (null != callInviteParams) {
                                        dispatchOnStateDidChange(CALL_STATE_RINGING);
                                        setRemoteDescription(callInviteParams);
                                    }
                                }
                            }
                        });
                    }
                });
            } catch (Exception e) {
                // GA issue
                // it seems that setView triggers some exception like "setRenderer has already been called"
                Log.e(LOG_TAG,"## initCallUI(): VideoRendererGui.setView : Exception Msg ="+e.getMessage());
            }

            // create the renderers after the VideoRendererGui.setView
            try {
                Log.d(LOG_TAG, "## initCallUI() building UI");
                //  create the video displaying the remote view sent by the server
                if (isConference()) {
                    mLargeRemoteRenderer = VideoRendererGui.createGui(0, 0, 100, 100, VideoRendererGui.ScalingType.SCALE_ASPECT_FIT, false);
                } else {
                    mLargeRemoteRenderer = VideoRendererGui.createGui(0, 0, 100, 100, VideoRendererGui.ScalingType.SCALE_ASPECT_FILL, false);
                }

                mLargeLocalRendererCallbacks = VideoRendererGui.create(0, 0, 100, 100, VideoRendererGui.ScalingType.SCALE_ASPECT_FILL, true);
                mLargeLocalRenderer = new VideoRenderer(mLargeLocalRendererCallbacks);

                // create the video displaying the local user: horizontal center, just above the video buttons menu
                if(null != aLocalVideoPosition) {
                    mSmallLocalRendererCallbacks = VideoRendererGui.create(aLocalVideoPosition.mX, aLocalVideoPosition.mY, aLocalVideoPosition.mWidth, aLocalVideoPosition.mHeight, VideoRendererGui.ScalingType.SCALE_ASPECT_FIT, true);
                    Log.d(LOG_TAG, "## initCallUI(): "+aLocalVideoPosition);
                } else {
                    // default layout
                    mSmallLocalRendererCallbacks = VideoRendererGui.create(5, 5, 25, 25, VideoRendererGui.ScalingType.SCALE_ASPECT_FIT, true);
                }
                mSmallLocalRenderer = new VideoRenderer(mSmallLocalRendererCallbacks);

            } catch (Exception e) {
                Log.e(LOG_TAG,"## initCallUI(): Exception Msg ="+e.getMessage());
            }

            mCallView.setVisibility(View.VISIBLE);

        } else {
            Log.d(LOG_TAG, "## initCallUI(): build audio call");

            // audio call
            mUIThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (null == mPeerConnectionFactory) {
                        mPeerConnectionFactory = new PeerConnectionFactory();
                        createAudioTrack();
                        createLocalStream();

                        if (null != callInviteParams) {
                            dispatchOnStateDidChange(CALL_STATE_RINGING);
                            setRemoteDescription(callInviteParams);
                        }
                    }
                }
            });
        }
    }

    // actions (must be done after dispatchOnViewReady()

    /**
     * The activity is paused.
     */
    @Override
    public void onPause() {
        super.onPause();

        Log.d(LOG_TAG, "onPause");

        try {
            if (!isCallEnded()) {

                Log.d(LOG_TAG, "onPause with active call");

                if (null != mCallView) {
                    mCallView.onPause();
                }

                if (mVideoSource != null && !mIsVideoSourceStopped) {
                    mVideoSource.stop();
                    mIsVideoSourceStopped = true;
                }
            }
        } catch (Exception e) {
            // race condition
            Log.e(LOG_TAG, "onPause failed " + e.getLocalizedMessage());
        }
    }

    /**
     * The activity is resumed.
     */
    @Override
    public void onResume() {
        super.onResume();

        Log.d(LOG_TAG, "onResume");

        try {
            if (!isCallEnded()) {

                Log.d(LOG_TAG, "onResume with active call");

                if (null != mCallView) {
                    mCallView.onResume();
                }

                if (mVideoSource != null && mIsVideoSourceStopped) {
                    mVideoSource.restart();
                    mIsVideoSourceStopped = false;
                }

                mUIThreadHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        listenPreviewUpdate();
                    }
                }, 100);

            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "onResume failed " + e.getLocalizedMessage());
        }
    }

    /**
     * Start a call.
     */
    @Override
    public void placeCall(VideoLayoutConfiguration aLocalVideoPosition) {
        Log.d(LOG_TAG, "placeCall");

        dispatchOnStateDidChange(IMXCall.CALL_STATE_WAIT_LOCAL_MEDIA);
        initCallUI(null, aLocalVideoPosition);
    }

    /**
     * Set the remote description
     * @param callInviteParams the invitation params
     */
    private void setRemoteDescription(final JsonObject callInviteParams) {
        Log.d(LOG_TAG, "setRemoteDescription " + callInviteParams);

        SessionDescription aDescription = null;
        // extract the description
        try {
            if (callInviteParams.has("offer")) {
                JsonObject answer = callInviteParams.getAsJsonObject("offer");
                String type = answer.get("type").getAsString();
                String sdp = answer.get("sdp").getAsString();

                if (!TextUtils.isEmpty(type) && !TextUtils.isEmpty(sdp)) {
                    aDescription = new SessionDescription(SessionDescription.Type.OFFER, sdp);
                }
            }

        } catch (Exception e) {
            Log.e(LOG_TAG,"## setRemoteDescription(): Exception Msg="+e.getMessage());
        }

        mPeerConnection.setRemoteDescription(new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                Log.d(LOG_TAG, "setRemoteDescription onCreateSuccess");
            }

            @Override
            public void onSetSuccess() {
                Log.d(LOG_TAG, "setRemoteDescription onSetSuccess");
                mIsIncomingPrepared = true;
                mUIThreadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        checkPendingCandidates();
                    }
                });
            }

            @Override
            public void onCreateFailure(String s) {
                Log.e(LOG_TAG, "setRemoteDescription onCreateFailure " + s);
                dispatchOnCallError(CALL_ERROR_CAMERA_INIT_FAILED);
            }

            @Override
            public void onSetFailure(String s) {
                Log.e(LOG_TAG, "setRemoteDescription onSetFailure " + s);
                dispatchOnCallError(CALL_ERROR_CAMERA_INIT_FAILED);
            }
        }, aDescription);
    }

    /**
     * Prepare a call reception.
     * @param aCallInviteParams the invitation Event content
     * @param aCallId the call ID
     */
    @Override
    public void prepareIncomingCall(final JsonObject aCallInviteParams, final String aCallId, final VideoLayoutConfiguration aLocalVideoPosition) {

        Log.d(LOG_TAG, "## prepareIncomingCall : call state " + getCallState());

        mCallId = aCallId;

        if (CALL_STATE_FLEDGLING.equals(getCallState())) {
            mIsIncoming = true;

            dispatchOnStateDidChange(CALL_STATE_WAIT_LOCAL_MEDIA);

            mUIThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    initCallUI(aCallInviteParams, aLocalVideoPosition);
                }
            });
        } else if (CALL_STATE_CREATED.equals(getCallState())) {
            mCallInviteParams = aCallInviteParams;

            // detect call type from the sdp
            try {
                JsonObject offer = mCallInviteParams.get("offer").getAsJsonObject();
                JsonElement sdp = offer.get("sdp");
                String sdpValue = sdp.getAsString();
                setIsVideo(sdpValue.contains("m=video"));
            } catch (Exception e) {
                Log.e(LOG_TAG, "## prepareIncomingCall(): Exception Msg=" + e.getMessage());
            }
        }
    }

    /**
     * The call has been detected as an incoming one.
     * The application launches the dedicated activity and expects to launch the incoming call.
     * The local video attendee is displayed in the screen according to the values given in aLocalVideoPosition.
     * @param aLocalVideoPosition local video position
     */
    @Override
    public void launchIncomingCall(VideoLayoutConfiguration aLocalVideoPosition) {
        Log.d(LOG_TAG, "launchIncomingCall : call state " + getCallState());

        if (CALL_STATE_FLEDGLING.equals(getCallState())) {
            prepareIncomingCall(mCallInviteParams, mCallId, aLocalVideoPosition);
        }
    }

    /**
     * The callee accepts the call.
     * @param event the event
     */
    private void onCallAnswer(final Event event) {
        Log.d(LOG_TAG, "onCallAnswer : call state " + getCallState());

        if (!CALL_STATE_CREATED.equals(getCallState()) && (null != mPeerConnection)) {
            mUIThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    dispatchOnStateDidChange(IMXCall.CALL_STATE_CONNECTING);
                    SessionDescription aDescription = null;

                    // extract the description
                    try {
                        JsonObject eventContent = event.getContentAsJsonObject();

                        if (eventContent.has("answer")) {
                            JsonObject answer = eventContent.getAsJsonObject("answer");
                            String type = answer.get("type").getAsString();
                            String sdp = answer.get("sdp").getAsString();

                            if (!TextUtils.isEmpty(type) && !TextUtils.isEmpty(sdp) && type.equals("answer")) {
                                aDescription = new SessionDescription(SessionDescription.Type.ANSWER, sdp);
                            }
                        }

                    } catch (Exception e) {
                        Log.d(LOG_TAG, "onCallAnswer : " + e.getLocalizedMessage());
                    }

                    mPeerConnection.setRemoteDescription(new SdpObserver() {
                        @Override
                        public void onCreateSuccess(SessionDescription sessionDescription) {
                            Log.d(LOG_TAG, "setRemoteDescription onCreateSuccess");
                        }

                        @Override
                        public void onSetSuccess() {
                            Log.d(LOG_TAG, "setRemoteDescription onSetSuccess");
                        }

                        @Override
                        public void onCreateFailure(String s) {
                            Log.e(LOG_TAG, "setRemoteDescription onCreateFailure " + s);
                            dispatchOnCallError(CALL_ERROR_CAMERA_INIT_FAILED);
                        }

                        @Override
                        public void onSetFailure(String s) {
                            Log.e(LOG_TAG, "setRemoteDescription onSetFailure " + s);
                            dispatchOnCallError(CALL_ERROR_CAMERA_INIT_FAILED);
                        }
                    }, aDescription);
                }
            });
        }
    }

    /**
     * The other call member hangs up the call.
     * @param event the event
     * @param hangUpReasonId hang up reason
     */
    private void onCallHangup(final Event event, final int hangUpReasonId) {
        Log.d(LOG_TAG, "## onCallHangup(): call state=" + getCallState());
        String state = getCallState();

        if (!CALL_STATE_CREATED.equals(state) && (null != mPeerConnection)) {
            mUIThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    terminate(hangUpReasonId);
                }
            });
        } else if(CALL_STATE_WAIT_LOCAL_MEDIA.equals(state) && isVideo()){
            // specific case fixing: a video call hung up by the calling side
            // when the callee is still displaying the InComingCallActivity dialog.
            // If terminate() was not called, the dialog was never dismissed.
            mUIThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    terminate(hangUpReasonId);
                }
            });
        }
    }

    /**
     * A new Ice candidate is received
     * @param candidates the channel candidates
     */
    private void onNewCandidates(final JsonArray candidates) {
        Log.d(LOG_TAG, "## onNewCandidates(): call state " + getCallState() + " with candidates " + candidates);

        if (!CALL_STATE_CREATED.equals(getCallState()) && (null != mPeerConnection)) {
            ArrayList<IceCandidate> candidatesList = new  ArrayList<>();

            // convert the JSON to IceCandidate
            for (int index = 0; index < candidates.size(); index++) {
                JsonObject item = candidates.get(index).getAsJsonObject();
                try {
                    String candidate = item.get("candidate").getAsString();
                    String sdpMid = item.get("sdpMid").getAsString();
                     int sdpLineIndex =  item.get("sdpMLineIndex").getAsInt();

                    candidatesList.add(new IceCandidate(sdpMid, sdpLineIndex, candidate));
                } catch (Exception e) {
                    Log.e(LOG_TAG, "## onNewCandidates(): Exception Msg=" + e.getMessage());
                }
            }

            for(IceCandidate cand : candidatesList) {
                Log.d(LOG_TAG, "## onNewCandidates(): addIceCandidate " + cand);
                mPeerConnection.addIceCandidate(cand);
            }
        }
    }

    /**
     * Add ice candidates
     * @param candidates ic candidates
     */
    private void addCandidates(JsonArray candidates) {
        if (mIsIncomingPrepared || !isIncoming()) {
            Log.d(LOG_TAG, "addCandidates : ready");
            onNewCandidates(candidates);
        } else {
            synchronized (LOG_TAG) {
                Log.d(LOG_TAG, "addCandidates : pending");
                mPendingCandidates.addAll(candidates);
            }
        }
    }

    /**
     * Some Ice candidates could have been received while creating the call view.
     * Check if some of them have been defined.
     */
    private void checkPendingCandidates() {
        Log.d(LOG_TAG, "checkPendingCandidates");

        synchronized (LOG_TAG) {
            onNewCandidates(mPendingCandidates);
            mPendingCandidates = new JsonArray();
        }
    }

    // events thread
    /**
     * Manage the call events.
     * @param event the call event.
     */
    @Override
    public void handleCallEvent(Event event){
        if (event.isCallEvent()) {
            String eventType = event.getType();

            Log.d(LOG_TAG, "handleCallEvent " + eventType);

            // event from other member
            if (!TextUtils.equals(event.getSender(), mSession.getMyUserId())) {
                if (Event.EVENT_TYPE_CALL_ANSWER.equals(eventType) && !mIsIncoming) {
                    onCallAnswer(event);
                } else if (Event.EVENT_TYPE_CALL_CANDIDATES.equals(eventType)) {
                    JsonObject eventContent = event.getContentAsJsonObject();

                    JsonArray candidates = eventContent.getAsJsonArray("candidates");
                    addCandidates(candidates);
                } else if (Event.EVENT_TYPE_CALL_HANGUP.equals(eventType)) {
                    onCallHangup(event, IMXCall.END_CALL_REASON_PEER_HANG_UP);
                }

            } else { // event from the current member, but sent from another device
                switch (eventType) {
                    case Event.EVENT_TYPE_CALL_INVITE:
                        // warn in the UI thread
                        mUIThreadHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                dispatchOnStateDidChange(CALL_STATE_RINGING);
                            }
                        });
                        break;

                    case Event.EVENT_TYPE_CALL_ANSWER:
                        // call answered from another device
                        mUIThreadHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                onAnsweredElsewhere();
                            }
                        });
                        break;

                    case Event.EVENT_TYPE_CALL_HANGUP:
                        // current member answered elsewhere
                        onCallHangup(event, IMXCall.END_CALL_REASON_PEER_HANG_UP_ELSEWHERE);
                        break;

                    default:
                        break;
                } // switch end
            }
        }
    }

    // user actions
    /**
     * The call is accepted.
     */
    @Override
    public void answer() {
        Log.d(LOG_TAG, "answer " + getCallState());

        if (!CALL_STATE_CREATED.equals(getCallState()) && (null != mPeerConnection)) {
            mUIThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (null == mPeerConnection) {
                        Log.d(LOG_TAG, "answer the connection has been closed");
                        return;
                    }

                    dispatchOnStateDidChange(CALL_STATE_CREATE_ANSWER);

                    MediaConstraints constraints = new MediaConstraints();
                    constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
                    constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", isVideo() ? "true" : "false"));

                    mPeerConnection.createAnswer(new SdpObserver() {
                        @Override
                        public void onCreateSuccess(SessionDescription sessionDescription) {
                            Log.d(LOG_TAG, "createAnswer onCreateSuccess");

                            final SessionDescription sdp = new SessionDescription(sessionDescription.type, sessionDescription.description);

                            mUIThreadHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if (mPeerConnection != null) {
                                        // must be done to before sending the invitation message
                                        mPeerConnection.setLocalDescription(new SdpObserver() {
                                            @Override
                                            public void onCreateSuccess(SessionDescription sessionDescription) {
                                                Log.d(LOG_TAG, "setLocalDescription onCreateSuccess");
                                            }

                                            @Override
                                            public void onSetSuccess() {
                                                Log.d(LOG_TAG, "setLocalDescription onSetSuccess");
                                                sendAnswer(sdp);
                                                dispatchOnStateDidChange(IMXCall.CALL_STATE_CONNECTING);
                                            }

                                            @Override
                                            public void onCreateFailure(String s) {
                                                Log.e(LOG_TAG, "setLocalDescription onCreateFailure " + s);
                                                dispatchOnCallError(CALL_ERROR_CAMERA_INIT_FAILED);
                                                hangup(null);
                                            }

                                            @Override
                                            public void onSetFailure(String s) {
                                                Log.e(LOG_TAG, "setLocalDescription onSetFailure " + s);
                                                dispatchOnCallError(CALL_ERROR_CAMERA_INIT_FAILED);
                                                hangup(null);
                                            }
                                        }, sdp);
                                    }
                                }
                            });
                        }

                        @Override
                        public void onSetSuccess() {
                            Log.d(LOG_TAG, "createAnswer onSetSuccess");
                        }

                        @Override
                        public void onCreateFailure(String s) {
                            Log.e(LOG_TAG, "createAnswer onCreateFailure " + s);
                            dispatchOnCallError(CALL_ERROR_CAMERA_INIT_FAILED);
                            hangup(null);
                        }

                        @Override
                        public void onSetFailure(String s) {
                            Log.e(LOG_TAG, "createAnswer onSetFailure " + s);
                            dispatchOnCallError(CALL_ERROR_CAMERA_INIT_FAILED);
                            hangup(null);
                        }
                    }, constraints);

                }
            });
        }

    }

    /**
     * The call is hung up.
     */
    @Override
    public void hangup(String reason) {
        Log.d(LOG_TAG, "## hangup(): reason=" + reason);

        if (!isCallEnded()) {
            sendHangup(reason);
            terminate(IMXCall.END_CALL_REASON_UNDEFINED);
        }
    }

    /**
     * @return the callstate (must be a CALL_STATE_XX value)
     */
    @Override
    public String getCallState() {
        return mCallState;
    }

    /**
     * @return the callView
     */
    @Override
    public View getCallView() {
        return mCallView;
    }

    /**
     * @return the callView visibility
     */
    @Override
    public int getVisibility() {
        if (null != mCallView) {
            return mCallView.getVisibility();
        } else {
            return View.GONE;
        }
    }

    /**
     * The call has been answered on another device.
     * We distinguish the case where an account is active on
     * multiple devices and a video call is launched on the account. In this case
     * the callee who did not answer must display a "answered elsewhere" message.
     */
    @Override
    public void onAnsweredElsewhere() {
        Log.d(LOG_TAG, "onAnsweredElsewhere");
        String state = getCallState();

        if (TextUtils.equals(state,IMXCall.CALL_STATE_RINGING /*if in ringing state on this side*/) ||
                /* the UI is not ready but the call has been stopped
                   because the screen is locked for example */
                TextUtils.equals(state,IMXCall.CALL_STATE_FLEDGLING) ||
           /* specific case to fix: a video call answered elsewhere by another callee side
           when this local callee is still displaying the InComingCallActivity dialog.*/
                (TextUtils.equals(state,CALL_STATE_WAIT_LOCAL_MEDIA) && isVideo())) {
                dispatchAnsweredElsewhere();
                terminate(IMXCall.END_CALL_REASON_UNDEFINED);
        }
    }

    @Override
    protected void dispatchOnStateDidChange(String newState) {
        Log.d(LOG_TAG, "dispatchOnStateDidChange " + newState);

        mCallState = newState;

        // call timeout management
        if (CALL_STATE_CONNECTING.equals(mCallState) || CALL_STATE_CONNECTING.equals(mCallState)) {
            if (null != mCallTimeoutTimer) {
                mCallTimeoutTimer.cancel();
                mCallTimeoutTimer = null;
            }
        }

        super.dispatchOnStateDidChange(newState);
    }

    //==============================================================================================================
    // Preview size management
    //==============================================================================================================

    /**
     * @return the device rotation angle
     */
    private int getDeviceOrientation() {
        try {
            WindowManager wm = (WindowManager) this.mContext.getApplicationContext().getSystemService("window");
            short orientation1;
            switch (wm.getDefaultDisplay().getRotation()) {
                case 0:
                default:
                    orientation1 = 0;
                    break;
                case 1:
                    orientation1 = 90;
                    break;
                case 2:
                    orientation1 = 180;
                    break;
                case 3:
                    orientation1 = 270;
            }

            return orientation1;
        } catch (Exception e) {
            Log.e(LOG_TAG, "## getDeviceOrientation() failed " + e.getMessage());
        }

        return 0;
    }

    /**
     * The camera preview frame has been updated
     * @param camera the camera
     * @param cameraOrientation the camera orientation
     */
    private void onPreviewFrameUpdate(Camera camera, int cameraOrientation) {
        Camera.Size s = camera.getParameters().getPreviewSize();
        final int width;
        final int height;
        int rotation = (360 + cameraOrientation + getDeviceOrientation()) % 360;

        if ((rotation == 90) || (rotation == 270)) {
            width = s.height;
            height = s.width;
        } else {
            width = s.width;
            height = s.height;
        }

        if ((width != mLocalRenderWidth) || (height != mLocalRenderHeight)) {
            mLocalRenderWidth = width;
            mLocalRenderHeight = height;

            mUIThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    dispatchOnPreviewSizeChanged(width, height);
                }
            });
        }
    }

    /**
     * Define a listener to track the local frame update.
     */
    private void listenPreviewUpdate() {
        try {
            if (null != mVideoCapturer) {
                Field field = mVideoCapturer.getClass().getDeclaredField("camera");
                field.setAccessible(true);
                Camera camera = (Camera) field.get(mVideoCapturer);

                if (null != camera) {
                    Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
                    Camera.getCameraInfo(mCameraInUse == CAMERA_TYPE_FRONT ? android.hardware.Camera.CameraInfo.CAMERA_FACING_FRONT : android.hardware.Camera.CameraInfo.CAMERA_FACING_BACK, info);

                    final int cameraOrientation = info.orientation;

                    camera.setPreviewCallbackWithBuffer(new Camera.PreviewCallback() {
                        @Override
                        public void onPreviewFrame(byte[] data, Camera camera) {
                            onPreviewFrameUpdate(camera, cameraOrientation);
                            ((VideoCapturerAndroid) mVideoCapturer).onPreviewFrame(data, camera);
                        }
                    });

                    onPreviewFrameUpdate(camera, cameraOrientation);
                } else {
                    Log.e(LOG_TAG, "## listenPreviewUpdate() : did not find the camera");
                }
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "## listenPreviewUpdate() failed " + e.getMessage());
        }
    }

}
