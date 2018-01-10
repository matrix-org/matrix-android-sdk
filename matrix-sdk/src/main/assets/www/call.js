"use strict";
/**
 * This is an internal module. See {@link createNewMatrixCall} for the public API.
 * @module webrtc/call
 */
var DEBUG = false;  // set true to enable console logging.

// events: hangup, error, replaced

function androidLog(message) {
    Android.wlog(message);
}

function AndroidCallError(message) {
    Android.wCallError(message);
}

function AndroidSendEvent(roomId, event, content) {
    Android.wSendEvent(roomId, event, content);
}

function AndroidOnStateUpdate(state) {
    Android.wOnStateUpdate(state);
}

function AndroidGetTurnServer() {
    return Android.wgetTurnServer();
}

/**
 * Construct a new Matrix Call.
 * @constructor
 * @param {Object} opts Config options.
 * @param {string} opts.roomId The room ID for this call.
 * @param {Object} opts.webRtc The WebRTC globals from the browser.
 * @param {Object} opts.URL The URL global.
 * @param {Array<Object>} opts.turnServers Optional. A list of TURN servers.
 * @param {MatrixClient} opts.client The Matrix Client instance to send events to.
 */
function MatrixCall(opts) {
    this.roomId = opts.roomId;
    /*this.client = opts.client;*/
    this.webRtc = opts.webRtc;
    this.URL = opts.URL;
    // Array of Objects with urls, username, credential keys
    this.turnServers = opts.turnServers || [{
        urls: [MatrixCall.FALLBACK_STUN_SERVER]
    }];

    this.callId = "c" + new Date().getTime();
    this.state = 'fledgling';
    this.didConnect = false;
}

/** The length of time a call can be ringing for. */
MatrixCall.CALL_TIMEOUT_MS = 120000;
/** The fallback server to use for STUN. */
MatrixCall.FALLBACK_STUN_SERVER = 'stun:stun.l.google.com:19302';

/**
 * update the matrix call state
 */
MatrixCall.prototype.updateState = function(state) {
    this.state = state;

    AndroidOnStateUpdate(state);

    // the sound has been muted until the connection is established
    if (this.state == 'connected') {
        if (this.localAVStream) {
            var audioTracks = this.localAVStream.getAudioTracks();
            for (var i = 0; i < audioTracks.length; i++) {
                audioTracks[i].enabled = true;
            }
        }

        if (this.remoteAVStream) {
            var audioTracks = this.remoteAVStream.getAudioTracks();
            for (var i = 0; i < audioTracks.length; i++) {
                audioTracks[i].enabled = true;
            }
        }

        // the local preview is displayed in fullscren until the connection is established
        this.getLocalVideoElement().parentElement.style.top = "5%";
        this.getLocalVideoElement().parentElement.style.left = "5%";
        this.getLocalVideoElement().parentElement.style.width = "25%";
        this.getLocalVideoElement().parentElement.style.height = "25%";

        _tryPlayRemoteStream(this);
    }

    if (this.getRemoteVideoElement()) {
        if (this.state == 'connected') {
           this.getRemoteVideoElement().style.display = 'block';
        } else {
           this.getRemoteVideoElement().style.display = 'none';
        }
    }
}

/**
 * Place a voice call to this room.
 * @throws If you have not specified a listener for 'error' events.
 */
MatrixCall.prototype.placeVoiceCall = function(remoteVideoElement, localVideoElement) {
    androidLog("placeVoiceCall");

    // required to have the callee sound
    // else the received stream is never started
    this.remoteVideoElement = remoteVideoElement;

    _placeCallWithConstraints(this, _getUserMediaVideoContraints('voice'));
    this.type = 'voice';
};

/**
 * Place a video call to this room.
 * @param {Element} remoteVideoElement a <code>&lt;video&gt;</code> DOM element
 * to render video to.
 * @param {Element} localVideoElement a <code>&lt;video&gt;</code> DOM element
 * to render the local camera preview.
 * @throws If you have not specified a listener for 'error' events.
 */
MatrixCall.prototype.placeVideoCall = function(remoteVideoElement, localVideoElement) {
    androidLog("placeVideoCall");

    this.localVideoElement = localVideoElement;
    this.remoteVideoElement = remoteVideoElement;

    _placeCallWithConstraints(this, _getUserMediaVideoContraints('video'));
    this.type = 'video';
};

/**
 * Retrieve the local <code>&lt;video&gt;</code> DOM element.
 * @return {Element} The dom element
 */
MatrixCall.prototype.getLocalVideoElement = function() {
    return this.localVideoElement;
};

/**
 * Retrieve the remote <code>&lt;video&gt;</code> DOM element.
 * @return {Element} The dom element
 */
MatrixCall.prototype.getRemoteVideoElement = function() {
    return this.remoteVideoElement;
};

/**
 * Set the remote <code>&lt;video&gt;</code> DOM element. If this call is active,
 * video will be rendered to it immediately.
 * @param {Element} element The <code>&lt;video&gt;</code> DOM element.
 */
MatrixCall.prototype.setRemoteVideoElement = function(element) {
    this.remoteVideoElement = element;
};

/**
 * Configure this call from an invite event. Used by MatrixClient.
 * @protected
 * @param {MatrixEvent} event The m.call.invite event
 */
MatrixCall.prototype._initWithInvite = function(msg) {

    this.msg = msg;
    this.peerConn = _createPeerConnection(this);
    var self = this;
    if (this.peerConn) {
        this.peerConn.setRemoteDescription(
            new this.webRtc.RtcSessionDescription(this.msg.offer),
            hookCallback(self, self._onSetRemoteDescriptionSuccess),
            hookCallback(self, self._onSetRemoteDescriptionError)
        );
    }

    this.updateState('wait_local_media');
    this.direction = 'inbound';

    if (self.getLocalVideoElement()) {
        self.getLocalVideoElement().style.display = 'none';
    }

    if (self.getRemoteVideoElement()) {
        self.getRemoteVideoElement().style.display = 'none';
    }

    // firefox and OpenWebRTC's RTCPeerConnection doesn't add streams until it
    // starts getting media on them so we need to figure out whether a video
    // channel has been offered by ourselves.
    if (this.msg.offer.sdp.indexOf('m=video') > -1) {
        this.type = 'video';
    }
    else {
        this.type = 'voice';
    }

    this.webRtc.getUserMedia(
        _getUserMediaVideoContraints(this.type),
        hookCallback(self, self._gotUserMediaForIncomingCall),
        hookCallback(self, self._getUserMediaFailed)
    );
};

/**
 * Configure this call from a hangup event. Used by MatrixClient.
 * @protected
 * @param {MatrixEvent} event The m.call.hangup event
 */
MatrixCall.prototype._initWithHangup = function(event) {
    // perverse as it may seem, sometimes we want to instantiate a call with a
    // hangup message (because when getting the state of the room on load, events
    // come in reverse order and we want to remember that a call has been hung up)
    this.msg = event.getContent();
    this.updateState('ended');
};

/**
 * Answer a call.
 */
MatrixCall.prototype.answer = function() {
    debuglog("Answering call " + this.callId);
    var self = this;

    // audio case : do not wait after local AV
    if (!this.localAVStream && !this.waitForLocalAVStream) {
        this.updateState('wait_local_media');
    } else if (this.localAVStream) {
        self._create_answer();
    }
};

/**
 * Hangup a call.
 * @param {string} reason The reason why the call is being hung up.
 * @param {boolean} suppressEvent True to suppress emitting an event.
 */
MatrixCall.prototype.hangup = function(reason, suppressEvent) {
    debuglog("Ending call " + this.callId);
    terminate(this, "local", reason, !suppressEvent);
    var content = {
        version: 0,
        call_id: this.callId,
        reason: reason
    };
    sendEvent(this, 'm.call.hangup', content);
};

/**
 * Internal
 * @private
 * @param {Object} stream
 */
MatrixCall.prototype._gotUserMediaForInvite = function(stream) {
    debuglog("_gotUserMediaForInvite");

    if (this.successor) {
        this.successor._gotUserMediaForAnswer(stream);
        return;
    }
    if (this.state == 'ended') {
        return;
    }
    var self = this;
    var videoEl = this.getLocalVideoElement();

    if (videoEl && this.type == 'video') {
        videoEl.autoplay = true;
        videoEl.src = this.URL.createObjectURL(stream);
        videoEl.muted = true;
        setTimeout(function() {
            var vel = self.getLocalVideoElement();
            if (vel.play) {
                vel.play();
            }
        }, 0);
    }


    this.localAVStream = stream;

    // mute the sound until the call is connected
    var audioTracks = stream.getAudioTracks();
    for (var i = 0; i < audioTracks.length; i++) {
        audioTracks[i].enabled = false;
    }

    this.peerConn = _createPeerConnection(this);
    this.peerConn.addStream(stream);
    this.peerConn.createOffer(
        hookCallback(self, self._gotLocalOffer),
        hookCallback(self, self._getLocalOfferFailed)
    );

    this.updateState('create_offer');
};


/**
 * Internal
 * @private
 * @param {Object} stream
 */
MatrixCall.prototype._gotUserMediaForIncomingCall = function(stream) {
     debuglog("_gotUserMediaForInbound");

    var self = this;
    if (self.state == 'ended') {
        return;
    }
    var localVidEl = self.getLocalVideoElement();

    localVidEl.style.display = 'block';

    if (localVidEl) {
        localVidEl.autoplay = true;
        localVidEl.src = self.URL.createObjectURL(stream);
        localVidEl.muted = self;
        setTimeout(function() {
            var vel = self.getLocalVideoElement();
            if (vel.play) {
                vel.play();
            }
        }, 0);
    }

    self.localAVStream = stream;

    // mute the incoming sound until the connection is established
    var audioTracks = stream.getAudioTracks();
    for (var i = 0; i < audioTracks.length; i++) {
        audioTracks[i].enabled = false;
    }
    self.peerConn.addStream(stream);

    this.updateState('ringing');
}

MatrixCall.prototype._create_answer = function() {
    var self = this;
    if (self.state == 'ended') {
        return;
    }

    var constraints = {
        'mandatory': {
            'OfferToReceiveAudio': true,
            'OfferToReceiveVideo': self.type == 'video'
        },
    };
    self.peerConn.createAnswer(function(description) {
        debuglog("Created answer: " + description);
        self.peerConn.setLocalDescription(description, function() {
            var content = {
                version: 0,
                call_id: self.callId,
                answer: {
                    sdp: self.peerConn.localDescription.sdp,
                    type: self.peerConn.localDescription.type
                }
            };
            sendEvent(self, 'm.call.answer', content);
            self.updateState('connecting');
        }, function() {
            debuglog("Error setting local description!");
        }, constraints);
    });

    this.updateState('create_answer');
}

/**
 * Internal
 * @private
 * @param {Object} stream
 */
MatrixCall.prototype._gotUserMediaForAnswer = function(stream) {
     debuglog("_gotUserMediaForAnswer");

    var self = this;
    if (self.state == 'ended') {
        return;
    }
    var localVidEl = self.getLocalVideoElement();

    if (localVidEl && self.type == 'video') {
        localVidEl.autoplay = true;
        localVidEl.src = self.URL.createObjectURL(stream);
        localVidEl.muted = self;
        setTimeout(function() {
            var vel = self.getLocalVideoElement();
            if (vel.play) {
                vel.play();
            }
        }, 0);
    }

    self.localAVStream = stream;

    // mute until the connection is established
    var audioTracks = stream.getAudioTracks();
    for (var i = 0; i < audioTracks.length; i++) {
        audioTracks[i].enabled = false;
    }
    self.peerConn.addStream(stream);

    self._create_answer();
};

/**
 * Internal
 * @private
 * @param {Object} event
 */
MatrixCall.prototype._gotLocalIceCandidate = function(event) {
    if (event.candidate) {
        debuglog(
            "Got local ICE " + event.candidate.sdpMid + " candidate: " +
            event.candidate.candidate
        );
        // As with the offer, note we need to make a copy of this object, not
        // pass the original: that broke in Chrome ~m43.
        var c = {
            candidate: event.candidate.candidate,
            sdpMid: event.candidate.sdpMid,
            sdpMLineIndex: event.candidate.sdpMLineIndex
        };

        var cands = [];
        cands.push(c);

        var content = {
            version: 0,
            call_id: this.callId,
            candidates: cands
        };

        debuglog("Attempting to send the candidate");
        sendEvent(self, 'm.call.candidates', content);
    }
};

/**
 * Used by MatrixClient.
 * @protected
 * @param {Object} cand
 */
MatrixCall.prototype._gotRemoteIceCandidate = function(cand) {
    if (this.state == 'ended') {
        return;
    }

    debuglog("Got remote ICE " + cand.sdpMid + " candidate: " + cand.candidate);
    this.peerConn.addIceCandidate(
        new this.webRtc.RtcIceCandidate(cand),
        function() {},
        function(e) {}
    );
};

/**
 * Used by MatrixClient.
 * @protected
 * @param {Object} msg
 */
MatrixCall.prototype._receivedAnswer = function(msg) {
    debuglog("_receivedAnswer");

    if (this.state == 'ended') {
        return;
    }

    var self = this;
    this.peerConn.setRemoteDescription(
        new this.webRtc.RtcSessionDescription(msg.answer),
        hookCallback(self, self._onSetRemoteDescriptionSuccess),
        hookCallback(self, self._onSetRemoteDescriptionError)
    );

    this.updateState('connecting');
};

/**
 * Internal
 * @private
 * @param {Object} description
 */
MatrixCall.prototype._gotLocalOffer = function(description) {
    var self = this;
    debuglog("Created offer: " + description);

    if (self.state == 'ended') {
        debuglog("Ignoring newly created offer on call ID " + self.callId +
            " because the call has ended");
        return;
    }

    self.peerConn.setLocalDescription(description, function() {
        var content = {
            version: 0,
            call_id: self.callId,
            // OpenWebRTC appears to add extra stuff (like the DTLS fingerprint)
            // to the description when setting it on the peerconnection.
            // According to the spec it should only add ICE
            // candidates. Any ICE candidates that have already been generated
            // at this point will probably be sent both in the offer and separately.
            // Also, note that we have to make a new object here, copying the
            // type and sdp properties.
            // Passing the RTCSessionDescription object as-is doesn't work in
            // Chrome (as of about m43).
            offer: {
                sdp: self.peerConn.localDescription.sdp,
                type: self.peerConn.localDescription.type
            },
            lifetime: MatrixCall.CALL_TIMEOUT_MS
        };

        sendEvent(self, 'm.call.invite', content);
        self.updateState('invite_sent');
    }, function() {
        debuglog("Error setting local description!");
    });
};

/**
 * Internal
 * @private
 * @param {Object} error
 */
MatrixCall.prototype._getLocalOfferFailed = function(error) {
    AndroidCallError("user_media_failed");
    this.hangup("user_media_failed");
};

/**
 * Internal
 * @private
 */
MatrixCall.prototype._getUserMediaFailed = function() {
    AndroidCallError("user_media_failed");
    this.hangup("user_media_failed");
};

/**
 * Internal
 * @private
 */
MatrixCall.prototype._onIceConnectionStateChanged = function() {
    if (this.state == 'ended') {
        return; // because ICE can still complete as we're ending the call
    }
    debuglog(
        "Ice connection state changed to: " + this.peerConn.iceConnectionState
    );

    // ideally we'd consider the call to be connected when we get media but
    // chrome doesn't implement any of the 'onstarted' events yet
    if (this.peerConn.iceConnectionState == 'completed' ||
            this.peerConn.iceConnectionState == 'connected') {
        this.updateState('connected');
        this.didConnect = true;
    } else if (this.peerConn.iceConnectionState == 'failed') {
        AndroidCallError("ice_failed");
        this.hangup('ice_failed');
    }
};

/**
 * Internal
 * @private
 */
MatrixCall.prototype._onSignallingStateChanged = function() {
    debuglog(
        "call " + this.callId + ": Signalling state changed to: " +
        this.peerConn.signalingState
    );
};

/**
 * Internal
 * @private
 */
MatrixCall.prototype._onSetRemoteDescriptionSuccess = function() {
    debuglog("Set remote description");
};

/**
 * Internal
 * @private
 * @param {Object} e
 */
MatrixCall.prototype._onSetRemoteDescriptionError = function(e) {
    debuglog("Failed to set remote description" + e);
};

/**
 * Internal
 * @private
 * @param {Object} event
 */
MatrixCall.prototype._onAddStream = function(event) {
    debuglog("Stream added" + event);

    var s = event.stream;

    this.remoteAVStream = s;

    if (this.direction == 'inbound') {
        if (s.getVideoTracks().length > 0) {
            this.type = 'video';
        } else {
            this.type = 'voice';
        }
    }

    var self = this;
    forAllTracksOnStream(s, function(t) {
        // not currently implemented in chrome
        t.onstarted = hookCallback(self, self._onRemoteStreamTrackStarted);
    });

    event.stream.onended = hookCallback(self, self._onRemoteStreamEnded);
    // not currently implemented in chrome
    event.stream.onstarted = hookCallback(self, self._onRemoteStreamStarted);

    // disable the sound until the connection is established
    var audioTracks = this.remoteAVStream.getAudioTracks();
    for (var i = 0; i < audioTracks.length; i++) {
        audioTracks[i].enabled = false;
    }

    _tryPlayRemoteStream(this);
};

/**
 * Internal
 * @private
 * @param {Object} event
 */
MatrixCall.prototype._onRemoteStreamStarted = function(event) {
    if (self.getRemoteVideoElement()) {
        self.getRemoteVideoElement().style.display = 'block';
    }
    this.updateState('connected');
};

/**
 * Internal
 * @private
 * @param {Object} event
 */
MatrixCall.prototype._onRemoteStreamEnded = function(event) {
    debuglog("Remote stream ended");

    this.updateState('ended');
    this.hangupParty = 'remote';
    stopAllMedia(this);
    if (this.peerConn.signalingState != 'closed') {
        this.peerConn.close();
    }
};

/**
 * Internal
 * @private
 * @param {Object} event
 */
MatrixCall.prototype._onRemoteStreamTrackStarted = function(event) {
    this.updateState('connected');
};

/**
 * Used by MatrixClient.
 * @protected
 * @param {Object} msg
 */
MatrixCall.prototype._onHangupReceived = function(msg) {
    debuglog("Hangup received");
    terminate(this, "remote", msg.reason, true);
};

/**
 * Used by MatrixClient.
 * @protected
 * @param {Object} msg
 */
MatrixCall.prototype._onAnsweredElsewhere = function() {
    debuglog("Answered elsewhere");
    terminate(this, "remote", "answered_elsewhere", true);
};

/**
 * Internal
 * @param {MatrixCall} self
 * @param {string} eventType
 * @param {Object} content
 */
var sendEvent = function(self, eventType, content) {
    AndroidSendEvent(self.roomId, eventType, JSON.stringify(content));
};

var terminate = function(self, hangupParty, hangupReason, shouldEmit) {
    if (self.getRemoteVideoElement() && self.getRemoteVideoElement().pause) {
        self.getRemoteVideoElement().pause();
    }

    if (self.getLocalVideoElement() && self.getLocalVideoElement().pause) {
        self.getLocalVideoElement().pause();
    }

    self.updateState('ended');
    self.hangupParty = hangupParty;
    self.hangupReason = hangupReason;
    stopAllMedia(self);

    if (self.peerConn && self.peerConn.signalingState !== 'closed') {
        self.peerConn.close();
    }
};

var stopAllMedia = function(self) {
    if (self.localAVStream) {
        forAllTracksOnStream(self.localAVStream, function(t) {
            if (t.stop) {
                t.stop();
            }
        });
        // also call stop on the main stream so firefox will stop sharing
        // the mic
        if (self.localAVStream.stop) {
            self.localAVStream.stop();
        }
    }
    if (self.remoteAVStream) {
        forAllTracksOnStream(self.remoteAVStream, function(t) {
            if (t.stop) {
                t.stop();
            }
        });
    }
};

var _tryPlayRemoteStream = function(self) {
    // play the remote stream only when the connection is established
    // else it triggers buzzer sound while establishing the connection.
    if (self.getRemoteVideoElement() && self.remoteAVStream && (self.state == 'connected')) {
        var player = self.getRemoteVideoElement();
        player.autoplay = true;
        player.src = self.URL.createObjectURL(self.remoteAVStream);
        setTimeout(function() {
            var vel = self.getRemoteVideoElement();
            if (vel.play) {
                vel.play();
            }
        }, 0);
    }
};


var debuglog = function() {
    if (0 < arguments.length) {
        androidLog(arguments[0]);
    }
    if (DEBUG) {
        console.log.apply(console, arguments);
    }
};

var _placeCallWithConstraints = function(self, constraints) {
    //self.client.callList[self.callId] = self;
    self.webRtc.getUserMedia(
        constraints,
        hookCallback(self, self._gotUserMediaForInvite),
        hookCallback(self, self._getUserMediaFailed)
    );
    self.updateState('wait_local_media');
    self.direction = 'outbound';
    self.config = constraints;
};

var _createPeerConnection = function(self) {
    var servers = self.turnServers;

    var iceServers = [];

    if (servers && servers.uris) {
        iceServers.push({
            'urls': servers.uris,
            'username': servers.username,
            'credential': servers.password,
        });
    } else {
        iceServers = servers;
    }

    var pc = new self.webRtc.RtcPeerConnection({
        iceServers: iceServers
    });
    pc.oniceconnectionstatechange = hookCallback(self, self._onIceConnectionStateChanged);
    pc.onsignalingstatechange = hookCallback(self, self._onSignallingStateChanged);
    pc.onicecandidate = hookCallback(self, self._gotLocalIceCandidate);
    pc.onaddstream = hookCallback(self, self._onAddStream);
    return pc;
};

var _getUserMediaVideoContraints = function(callType) {
    switch (callType) {
        case 'voice':
            return ({audio: true, video: false});
        case 'video':
            return ({audio: true, video: {
                mandatory: {
                    minWidth: 640,
                    //maxWidth: 640,
                    // the local preview is cropped when the height is defined
                    //minHeight: 360,
                    //maxHeight: 360,
                }
            }});
    }
};

var hookCallback = function(call, fn) {
    return function() {
        return fn.apply(call, arguments);
    };
};

var forAllVideoTracksOnStream = function(s, f) {
    var tracks = s.getVideoTracks();
    for (var i = 0; i < tracks.length; i++) {
        f(tracks[i]);
    }
};

var forAllAudioTracksOnStream = function(s, f) {
    var tracks = s.getAudioTracks();
    for (var i = 0; i < tracks.length; i++) {
        f(tracks[i]);
    }
};

var forAllTracksOnStream = function(s, f) {
    forAllVideoTracksOnStream(s, f);
    forAllAudioTracksOnStream(s, f);
};


/**
 * Create a new Matrix call for the browser.
 * @param {MatrixClient} client The client instance to use.
 * @param {string} roomId The room the call is in.
 * @return {MatrixCall} the call or null if the browser doesn't support calling.
 */
function createNewMatrixCall(/*client,*/ roomId) {

    androidLog("createNewMatrixCall");

    var w = window;
    var doc = document;
    if (!w || !doc) {
        return null;
    }
    var webRtc = {};
    webRtc.isOpenWebRTC = function() {
        var scripts = doc.getElementById("script");
        if (!scripts || !scripts.length) {
            return false;
        }
        for (var i = 0; i < scripts.length; i++) {
            if (scripts[i].src.indexOf("owr.js") > -1) {
                return true;
            }
        }
        return false;
    };

    var getUserMedia = (
        w.navigator.getUserMedia || w.navigator.webkitGetUserMedia ||
        w.navigator.mozGetUserMedia
    );
    if (getUserMedia) {
        webRtc.getUserMedia = function() {
            return getUserMedia.apply(w.navigator, arguments);
        };
    }
    webRtc.RtcPeerConnection = (
        w.RTCPeerConnection || w.webkitRTCPeerConnection || w.mozRTCPeerConnection
    );
    webRtc.RtcSessionDescription = (
        w.RTCSessionDescription || w.webkitRTCSessionDescription ||
        w.mozRTCSessionDescription
    );
    webRtc.RtcIceCandidate = (
        w.RTCIceCandidate || w.webkitRTCIceCandidate || w.mozRTCIceCandidate
    );

    webRtc.vendor = null;
    if (w.mozRTCPeerConnection) {
        webRtc.vendor = "mozilla";
    }
    else if (w.webkitRTCPeerConnection) {
        webRtc.vendor = "webkit";
    }
    else if (w.RTCPeerConnection) {
        webRtc.vendor = "generic";
    }
    if (!webRtc.RtcIceCandidate || !webRtc.RtcSessionDescription ||
            !webRtc.RtcPeerConnection || !webRtc.getUserMedia) {
        return null; // Web RTC is not supported.
    }

    var turnServersString = AndroidGetTurnServer();
    var turnServs = null;

    if (turnServersString) {
        turnServs = JSON.parse(turnServersString);
    }

    var opts = {
        webRtc: webRtc,
        turnServers:turnServs,
        URL: w.URL,
        roomId: roomId
    };

    return new MatrixCall(opts);
};
