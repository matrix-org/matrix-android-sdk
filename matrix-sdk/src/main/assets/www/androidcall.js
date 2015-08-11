"use strict";
console.log("Loading call");
var BASE_URL = "https://matrix.org";
var mxCall;

// debug tools
function showToast(toast) {
     Android.showToast(toast);
}

// initializers
function getRoomId() {
    return Android.wgetRoomId();
}

function getCallId() {
    return Android.wgetCallId();
}

// call when the webview is loaded
function onLoaded() {
    return Android.wOnLoaded();
}

// start call methods
function placeVoiceCall() {
	mxCall.placeVoiceCall(
		document.getElementById("remote"),
		document.getElementById("self")
	);
}

function placeVideoCall() {
	mxCall.placeVideoCall(
		document.getElementById("remote"),
		document.getElementById("self")
	);
}

function setCallId(callId) {
	mxCall.callId = callId;
}

function initWithInvite(callId, msg) {
	mxCall.localVideoElement = document.getElementById("self");
	mxCall.remoteVideoElement = document.getElementById("remote");
	mxCall.callId = callId;
    mxCall._initWithInvite(msg);
}

// receive candidate
function gotRemoteCandidate(candidate) {
    mxCall._gotRemoteIceCandidate(candidate);
}

// receive candidates
function gotRemoteCandidates(candidates) {
	for (var i = 0; i < candidates.length; i++) {
		mxCall._gotRemoteIceCandidate(candidates[i]);
	}
}


// the user accept the call
function answerCall() {
    mxCall.answer();
}

// the callee accepts the call
function receivedAnswer(msg) {
    mxCall._receivedAnswer(msg);
}

// hangup methods
function onHangupReceived(msg) {
    mxCall._onHangupReceived(msg);
}

function hangup() {
	mxCall.hangup("", false);
}

function onAnsweredElsewhere() {
	mxCall._onAnsweredElsewhere();
}

window.onload = function() {
	mxCall = createNewMatrixCall(getRoomId());
	mxCall.callId = getCallId();
	onLoaded();
};
