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
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

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

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class MXJingleCall extends MXCall {

    private static final String LOG_TAG = "MXJingleCall";

    public static final String VIDEO_TRACK_ID = "ARDAMSv0";
    public static final String AUDIO_TRACK_ID = "ARDAMSa0";

    private static final String MIN_VIDEO_WIDTH_CONSTRAINT = "minWidth";

    private static final int MIN_VIDEO_WIDTH = 640;

    static PeerConnectionFactory mPeerConnectionFactory = null;
    static String mFrontCameraName = null;
    static String mBackCameraName = null;
    static VideoCapturer mVideoCapturer = null;

    private GLSurfaceView mCallView = null;

    private boolean mIsVideoSourceStopped = false;
    private VideoSource mVideoSource = null;
    private VideoTrack  mLocalVideoTrack = null;
    private AudioSource mAudioSource = null;
    private AudioTrack  mLocalAudioTrack = null;
    private MediaStream mLocalMediaStream = null;

    private VideoTrack mRemoteVideoTrack = null;
    private PeerConnection mPeerConnection = null;

    // default value
    public String mCallState = CALL_STATE_CREATED;

    private Boolean mUsingLargeLocalRenderer = true;
    private VideoRenderer mLargeRemoteRenderer = null;
    private VideoRenderer mSmallLocalRenderer = null;

    private VideoRenderer.Callbacks mLargeLocalRendererCallbacks = null;
    private VideoRenderer mLargeLocalRenderer = null;

    private static boolean mIsInitialized = false;

    // candidate management
    private boolean mIsIncomingPrepared = false;
    private JsonArray mPendingCandidates = new JsonArray();

    private JsonObject mCallInviteParams = null;

    /**
     * @return true if this stack can perform calls.
     */
    public static boolean isSupported() {
        return Build.VERSION.SDK_INT > Build.VERSION_CODES.ICE_CREAM_SANDWICH;
    }

    /**
     * Class creator
     * @param session the session
     * @param context the context
     * @param turnServer the turn server
     */
    public MXJingleCall(MXSession session, Context context, JsonElement turnServer) {
        if (!isSupported()) {
            throw new AssertionError("MXJingleCall : not supported with the current android version");
        }

        if (null == session) {
            throw new AssertionError("MXJingleCall : session cannot be null");
        }

        if (null == context) {
            throw new AssertionError("MXJingleCall : context cannot be null");
        }

        mCallId = "c" + System.currentTimeMillis();
        mSession = session;
        mContext = context;
        mTurnServer = turnServer;
    }

    /**
     * Initialize the jingle globals
     */
    private void initializeAndroidGlobals() {
        if (!mIsInitialized) {
            mIsInitialized = PeerConnectionFactory.initializeAndroidGlobals(
                    mContext,
                    true,
                    true,
                    true,
                    VideoRendererGui.getEGLContext());

            PeerConnectionFactory.initializeFieldTrials(null);
        }

        if (!mIsInitialized) {
            throw new AssertionError("MXJingleCall : cannot initialize PeerConnectionFactory");
        }
    }

    /**
     * Create the callviews
     */
    @Override
    public void createCallView() {
        initializeAndroidGlobals();

        onStateDidChange(CALL_STATE_CREATING_CALL_VIEW);
        mUIThreadHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mCallView = new GLSurfaceView(mContext);
                mCallView.setVisibility(View.GONE);

                onViewLoading(mCallView);

                mUIThreadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        onStateDidChange(CALL_STATE_FLEDGLING);
                        onViewReady();
                    }
                });
            }
        }, 10);
    }

    /**
     * The connection is terminated
     */
    private void terminate() {
        if (isCallEnded()) {
            return;
        }

        onStateDidChange(CALL_STATE_ENDED);

        if (null != mPeerConnection) {
            mPeerConnection.dispose();
            mPeerConnection = null;
        }

        if (null != mVideoSource) {
            mVideoSource.dispose();
            mVideoSource = null;
        }

        if (null != mAudioSource) {
            mAudioSource.dispose();
            mAudioSource = null;
        }

        if (null != mPeerConnectionFactory) {
            mPeerConnectionFactory.dispose();
            mPeerConnectionFactory = null;
        }

        mCallView.setVisibility(View.GONE);
        mCallView = null;

        mUIThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                onCallEnd();
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
            return;
        }

        // build the invitation event
        JsonObject inviteContent = new JsonObject();
        inviteContent.addProperty("version", 0);
        inviteContent.addProperty("call_id", mCallId);
        inviteContent.addProperty("lifetime", 60000);

        JsonObject offerContent = new JsonObject();
        offerContent.addProperty("sdp", sessionDescription.description);
        offerContent.addProperty("type", sessionDescription.type.canonicalForm());
        inviteContent.add("offer", offerContent);

        Event event = new Event(Event.EVENT_TYPE_CALL_INVITE, inviteContent, mSession.getCredentials().userId, mRoom.getRoomId());

        if (null != event) {
            mPendingEvents.add(event);
                mCallTimeoutTimer = new Timer();
                mCallTimeoutTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        try {
                            if (getCallState().equals(IMXCall.CALL_STATE_RINGING) || getCallState().equals(IMXCall.CALL_STATE_INVITE_SENT)) {
                                onCallError(CALL_ERROR_USER_NOT_RESPONDING);
                                hangup(null);
                            }

                            // cancel the timer
                            mCallTimeoutTimer.cancel();
                            mCallTimeoutTimer = null;
                        } catch (Exception e) {

                        }
                    }
                }, 60 * 1000);

            sendNextEvent();
        }
    }

    /**
     * Send the answer event
     * @param sessionDescription
     */
    private void sendAnswer(final SessionDescription sessionDescription) {
        // check if the call has not been killed
        if (isCallEnded()) {
            return;
        }

        // build the invitation event
        JsonObject answerContent = new JsonObject();
        answerContent.addProperty("version", 0);
        answerContent.addProperty("call_id", mCallId);
        answerContent.addProperty("lifetime", 60000);

        JsonObject offerContent = new JsonObject();
        offerContent.addProperty("sdp", sessionDescription.description);
        offerContent.addProperty("type", sessionDescription.type.canonicalForm());
        answerContent.add("answer", offerContent);

        Event event = new Event(Event.EVENT_TYPE_CALL_ANSWER, answerContent, mSession.getCredentials().userId, mRoom.getRoomId());

        if (null != event) {
            mPendingEvents.add(event);
            sendNextEvent();
        }
    }

    /**
     * create the local stream
     */
    private void createLocalStream() {

        // check there is at least one stream to start a call
        if ((null == mLocalVideoTrack) && (null == mLocalAudioTrack)) {
            onCallError(CALL_ERROR_CALL_INIT_FAILED);
            hangup("no_stream");
            terminate();
            return;
        }

        mLocalMediaStream = mPeerConnectionFactory.createLocalMediaStream("ARDAMS");

        // add the tracks
        if (null != mLocalVideoTrack) {
            mLocalMediaStream.addTrack(mLocalVideoTrack);
        }

        if (null != mLocalAudioTrack) {
            mLocalMediaStream.addTrack(mLocalAudioTrack);
        }

        ArrayList<PeerConnection.IceServer> iceServers = new ArrayList<PeerConnection.IceServer>();

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
            }
        }

        // define at least on server
        if (iceServers.size() == 0) {
            iceServers.add(new PeerConnection.IceServer("stun:stun.l.google.com:19302"));
        }

        MediaConstraints pcConstraints = new MediaConstraints();

        pcConstraints.optional.add(
                new MediaConstraints.KeyValuePair("RtpDataChannels", "true"));

        mPeerConnection = mPeerConnectionFactory.createPeerConnection(
                iceServers,
                pcConstraints,
                new PeerConnection.Observer() {
                    @Override
                    public void onSignalingChange(PeerConnection.SignalingState signalingState) {
                        Log.d(LOG_TAG, "mPeerConnection onSignalingChange " + signalingState);
                    }

                    @Override
                    public void onIceConnectionChange(final PeerConnection.IceConnectionState iceConnectionState) {
                        Log.d(LOG_TAG, "mPeerConnection onIceConnectionChange " + iceConnectionState);
                        mUIThreadHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (iceConnectionState == PeerConnection.IceConnectionState.CONNECTED) {
                                    if (mUsingLargeLocalRenderer && isVideo()) {
                                        mLocalVideoTrack.setEnabled(false);
                                        VideoRendererGui.remove(mLargeLocalRendererCallbacks);
                                        mLocalVideoTrack.removeRenderer(mLargeLocalRenderer);
                                        mLocalVideoTrack.addRenderer(mSmallLocalRenderer);
                                        mLocalVideoTrack.setEnabled(true);
                                        mUsingLargeLocalRenderer = false;

                                        mCallView.post(new Runnable() {
                                            @Override
                                            public void run() {
                                                mCallView.invalidate();
                                            }
                                        });
                                    }

                                    onStateDidChange(IMXCall.CALL_STATE_CONNECTED);
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
                                    onCallError(CALL_ERROR_ICE_FAILED);
                                    hangup("ice_failed");
                                }
                            }
                        });
                    }

                    @Override
                    public void onIceConnectionReceivingChange(boolean var1) {
                        Log.d(LOG_TAG, "mPeerConnection onIceConnectionReceivingChange " + var1);
                    }

                    @Override
                    public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
                        Log.d(LOG_TAG, "mPeerConnection onIceGatheringChange " + iceGatheringState);
                    }

                    @Override
                    public void onIceCandidate(final IceCandidate iceCandidate) {
                        Log.d(LOG_TAG, "mPeerConnection onIceCandidate " + iceCandidate);

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

                                            if (TextUtils.equals(lastEvent.type, Event.EVENT_TYPE_CALL_CANDIDATES)) {
                                                JsonObject lastContent = lastEvent.getContentAsJsonObject();

                                                JsonArray lastContentCandidates = lastContent.get("candidates").getAsJsonArray();
                                                JsonArray newContentCandidates = content.get("candidates").getAsJsonArray();

                                                Log.d(LOG_TAG, "Merge candidates from " + lastContentCandidates.size() + " to " + (lastContentCandidates.size() + newContentCandidates.size() + " items."));

                                                lastContentCandidates.addAll(newContentCandidates);

                                                lastContent.remove("candidates");
                                                lastContent.add("candidates", lastContentCandidates);
                                                addIt = false;
                                            }
                                        } catch (Exception e) {
                                        }
                                    }

                                    if (addIt) {
                                        Event event = new Event(Event.EVENT_TYPE_CALL_CANDIDATES, content, mSession.getCredentials().userId, mRoom.getRoomId());

                                        if (null != event) {
                                            mPendingEvents.add(event);
                                        }
                                        sendNextEvent();
                                    }
                                }
                            }
                        });
                    }

                    @Override
                    public void onAddStream(final MediaStream mediaStream) {
                        Log.d(LOG_TAG, "mPeerConnection onAddStream " + mediaStream);

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
                        Log.d(LOG_TAG, "mPeerConnection  onRemoveStream " + mediaStream);

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
                        Log.d(LOG_TAG, "mPeerConnection onDataChannel " + dataChannel);
                    }

                    @Override
                    public void onRenegotiationNeeded() {
                        Log.d(LOG_TAG, "mPeerConnection onRenegotiationNeeded");
                    }
                });

        mPeerConnection.addStream(mLocalMediaStream);

        MediaConstraints constraints = new MediaConstraints();
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        constraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", isVideo() ? "true" : "false"));

        if (!isIncoming()) {
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
                                        onStateDidChange(IMXCall.CALL_STATE_INVITE_SENT);
                                    }

                                    @Override
                                    public void onCreateFailure(String s) {
                                        Log.e(LOG_TAG, "setLocalDescription onCreateFailure " + s);
                                        onCallError(CALL_ERROR_CAMERA_INIT_FAILED);
                                        hangup(null);
                                    }

                                    @Override
                                    public void onSetFailure(String s) {
                                        Log.e(LOG_TAG, "setLocalDescription onSetFailure " + s);
                                        onCallError(CALL_ERROR_CAMERA_INIT_FAILED);
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
                    onCallError(CALL_ERROR_CAMERA_INIT_FAILED);
                }

                @Override
                public void onSetFailure(String s) {
                    Log.d(LOG_TAG, "createOffer onSetFailure " + s);
                    onCallError(CALL_ERROR_CAMERA_INIT_FAILED);
                }
            }, constraints);

            onStateDidChange(IMXCall.CALL_STATE_WAIT_CREATE_OFFER);
        } else {
        }
    }

    /**
     * @return true if the device has a camera device
     */
    private boolean hasCameraDevice() {
        try {
            mFrontCameraName = VideoCapturerAndroid.getNameOfFrontFacingDevice();
            mBackCameraName = VideoCapturerAndroid.getNameOfBackFacingDevice();
        } catch (Exception e) {
        }

        return (null != mFrontCameraName) || (null != mBackCameraName);
    }

    /**
     * Create the local video stack
     * @return the video track
     */
    private VideoTrack createVideoTrack() {
        // create the local renderer only if there is a camera on the device
        if (hasCameraDevice()) {

            if (null != mFrontCameraName) {
                mVideoCapturer = VideoCapturerAndroid.create(mFrontCameraName);

                if (null == mVideoCapturer) {
                    Log.e(LOG_TAG, "Cannot create Video Capturer from front camera");
                }
            }

            if ((null == mVideoCapturer) && (null != mBackCameraName)) {
                mVideoCapturer = VideoCapturerAndroid.create(mBackCameraName);

                if (null == mVideoCapturer) {
                    Log.e(LOG_TAG, "Cannot create Video Capturer from back camera");
                }
            }

            if (null != mVideoCapturer) {
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
                Log.e(LOG_TAG, "Cannot create Video Capturer");
            }
        }

        return mLocalVideoTrack;
    }

    /**
     * Create the local video stack
     * @return the video track
     */
    private AudioTrack createAudioTrack() {
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
     */
    private void initCallUI(final JsonObject callInviteParams) {
        if (isCallEnded()) {
            return;
        }

        if (isVideo()) {
            VideoRendererGui.setView(mCallView, new Runnable() {
                @Override
                public void run() {
                    mUIThreadHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (null == mPeerConnectionFactory) {
                                mPeerConnectionFactory = new PeerConnectionFactory();
                                createVideoTrack();
                                createAudioTrack();
                                createLocalStream();

                                if (null != callInviteParams) {
                                    onStateDidChange(CALL_STATE_RINGING);
                                    setRemoteDescription(callInviteParams);
                                }
                            }
                        }
                    });
                }
            });

            // create the renderers after the VideoRendererGui.setView
            try {
                mLargeRemoteRenderer = VideoRendererGui.createGui(0, 0, 100, 100, VideoRendererGui.ScalingType.SCALE_ASPECT_FIT, false);

                mLargeLocalRendererCallbacks = VideoRendererGui.create(0, 0, 100, 100, VideoRendererGui.ScalingType.SCALE_ASPECT_FIT, true);
                mLargeLocalRenderer = new VideoRenderer(mLargeLocalRendererCallbacks);

                mSmallLocalRenderer = VideoRendererGui.createGui(5, 5, 25, 25, VideoRendererGui.ScalingType.SCALE_ASPECT_FIT, true);
            } catch (Exception e) {
            }

            mCallView.setVisibility(View.VISIBLE);

        } else {
            // audio call
            mUIThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (null == mPeerConnectionFactory) {
                        mPeerConnectionFactory = new PeerConnectionFactory();
                        createAudioTrack();
                        createLocalStream();

                        if (null != callInviteParams) {
                            onStateDidChange(CALL_STATE_RINGING);
                            setRemoteDescription(callInviteParams);
                        }
                    }
                }
            });
        }
    }

    // actions (must be done after onViewReady()

    /**
     * The activity is paused.
     */
    @Override
    public void onPause() {
        super.onPause();

        try {
            if (!isCallEnded()) {
                if (null != mCallView) {
                    mCallView.onPause();
                }

                if (mVideoSource != null) {
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

        try {
            if (!isCallEnded()) {
                if (null != mCallView) {
                    mCallView.onResume();
                }

                if (mVideoSource != null && mIsVideoSourceStopped) {
                    mVideoSource.restart();
                    mIsVideoSourceStopped = false;
                }
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "onResume failed " + e.getLocalizedMessage());
        }
    }

    /**
     * Start a call.
     */
    @Override
    public void placeCall() {
        onStateDidChange(IMXCall.CALL_STATE_WAIT_LOCAL_MEDIA);
        initCallUI(null);
    }

    /**
     * Set the remote description
     * @param callInviteParams the invitation params
     */
    private void setRemoteDescription(final JsonObject callInviteParams) {
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
                onCallError(CALL_ERROR_CAMERA_INIT_FAILED);
            }

            @Override
            public void onSetFailure(String s) {
                Log.e(LOG_TAG, "setRemoteDescription onSetFailure " + s);
                onCallError(CALL_ERROR_CAMERA_INIT_FAILED);
            }
        }, aDescription);
    }

    /**
     * Prepare a call reception.
     * @param callInviteParams the invitation Event content
     * @param callId the call ID
     */
    @Override
    public void prepareIncomingCall(final JsonObject callInviteParams, final String callId) {
        mCallId = callId;

        if (CALL_STATE_FLEDGLING.equals(getCallState())) {
            mIsIncoming = true;

            onStateDidChange(CALL_STATE_WAIT_LOCAL_MEDIA);

            mUIThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    initCallUI(callInviteParams);
                }
            });
        } else if (CALL_STATE_CREATED.equals(getCallState())) {
            mCallInviteParams = callInviteParams;

            // detect call type from the sdp
            try {
                JsonObject offer = mCallInviteParams.get("offer").getAsJsonObject();
                JsonElement sdp = offer.get("sdp");
                String sdpValue = sdp.getAsString();
                setIsVideo(sdpValue.indexOf("m=video") >= 0);
            } catch (Exception e) {
            }
        }
    }

    /**
     * The call has been detected as an incoming one.
     * The application launched the dedicated activity and expects to launch the incoming call.
     */
    @Override
    public void launchIncomingCall() {
        if (CALL_STATE_FLEDGLING.equals(getCallState())) {
            prepareIncomingCall(mCallInviteParams, mCallId);
        }
    }

    /**
     * The callee accepts the call.
     * @param event the event
     */
    private void onCallAnswer(final Event event) {
        if (!CALL_STATE_CREATED.equals(getCallState()) && (null != mPeerConnection)) {
            mUIThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    onStateDidChange(IMXCall.CALL_STATE_CONNECTING);
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
                            onCallError(CALL_ERROR_CAMERA_INIT_FAILED);
                        }

                        @Override
                        public void onSetFailure(String s) {
                            Log.e(LOG_TAG, "setRemoteDescription onSetFailure " + s);
                            onCallError(CALL_ERROR_CAMERA_INIT_FAILED);
                        }
                    }, aDescription);
                }
            });
        }
    }

    /**
     * The other call member hangs up the call.
     * @param event the event
     */
    private void onCallHangup(final Event event) {
        if (!CALL_STATE_CREATED.equals(getCallState()) && (null != mPeerConnection)) {
            mUIThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    terminate();
                }
            });
        }
    }

    /**
     * A new Ice candidate is received
     * @param candidates
     */
    public void onNewCandidates(final JsonArray candidates) {
        if (!CALL_STATE_CREATED.equals(getCallState()) && (null != mPeerConnection)) {
            ArrayList<IceCandidate> candidatesList = new  ArrayList<IceCandidate>();

            // convert the JSON to IceCandidate
            for (int index = 0; index < candidates.size(); index++) {
                JsonObject item = candidates.get(index).getAsJsonObject();
                try {
                    String candidate = item.get("candidate").getAsString();
                    String sdpMid = item.get("sdpMid").getAsString();
                     int sdpLineIndex =  item.get("sdpMLineIndex").getAsInt();

                    candidatesList.add(new IceCandidate(sdpMid, sdpLineIndex, candidate));
                } catch (Exception e) {
                    Log.e(LOG_TAG, "onNewCandidates fails " + e.getLocalizedMessage());
                }
            }

            for(IceCandidate cand : candidatesList) {
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
            onNewCandidates(candidates);
        } else {
            synchronized (LOG_TAG) {
                mPendingCandidates.addAll(candidates);
            }
        }
    }

    /**
     * Some Ice candidates could have been received while creating the call view.
     * Check if some of them have been defined.
     */
    public void checkPendingCandidates() {
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
            // event from other member
            if (!TextUtils.equals(event.getSender(), mSession.getMyUserId())) {
                if (Event.EVENT_TYPE_CALL_ANSWER.equals(event.type) && !mIsIncoming) {
                    onCallAnswer(event);
                } else if (Event.EVENT_TYPE_CALL_CANDIDATES.equals(event.type)) {
                    JsonObject eventContent = event.getContentAsJsonObject();

                    JsonArray candidates = eventContent.getAsJsonArray("candidates");
                    addCandidates(candidates);
                } else if (Event.EVENT_TYPE_CALL_HANGUP.equals(event.type)) {
                    onCallHangup(event);
                }
            } else if (Event.EVENT_TYPE_CALL_INVITE.equals(event.type)) {
                // warn in the UI thread
                mUIThreadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        onStateDidChange(CALL_STATE_RINGING);
                    }
                });

            } else if (Event.EVENT_TYPE_CALL_ANSWER.equals(event.type)) {
                // check if the call has not been answer in another device
                mUIThreadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        // ring on this side
                        if (TextUtils.equals(getCallState(), IMXCall.CALL_STATE_RINGING)) {
                            onAnsweredElsewhere();
                        }
                    }
                });

            }
        }
    }

    // user actions
    /**
     * The call is accepted.
     */
    @Override
    public void answer() {
        if (!CALL_STATE_CREATED.equals(getCallState()) && (null != mPeerConnection)) {
            mUIThreadHandler.post(new Runnable() {
                @Override
                public void run() {

                    onStateDidChange(CALL_STATE_CREATE_ANSWER);

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
                                                onStateDidChange(IMXCall.CALL_STATE_CONNECTING);
                                            }

                                            @Override
                                            public void onCreateFailure(String s) {
                                                Log.e(LOG_TAG, "setLocalDescription onCreateFailure " + s);
                                                onCallError(CALL_ERROR_CAMERA_INIT_FAILED);
                                                hangup(null);
                                            }

                                            @Override
                                            public void onSetFailure(String s) {
                                                Log.e(LOG_TAG, "setLocalDescription onSetFailure " + s);
                                                onCallError(CALL_ERROR_CAMERA_INIT_FAILED);
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
                            onCallError(CALL_ERROR_CAMERA_INIT_FAILED);
                            hangup(null);
                        }

                        @Override
                        public void onSetFailure(String s) {
                            Log.e(LOG_TAG, "createAnswer onSetFailure " + s);
                            onCallError(CALL_ERROR_CAMERA_INIT_FAILED);
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
        if (!isCallEnded() && (null != mPeerConnection)) {
            sendHangup(reason);
            terminate();
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
     * The call has been has answered on another device.
     */
    @Override
    public void onAnsweredElsewhere() {
        dispatchAnsweredElsewhere();
        terminate();
    }

    @Override
    protected void onStateDidChange(String newState) {
        mCallState = newState;

        // call timeout management
        if (CALL_STATE_CONNECTING.equals(mCallState) || CALL_STATE_CONNECTING.equals(mCallState)) {
            if (null != mCallTimeoutTimer) {
                mCallTimeoutTimer.cancel();
                mCallTimeoutTimer = null;
            }
        }

        super.onStateDidChange(newState);
    }
}
