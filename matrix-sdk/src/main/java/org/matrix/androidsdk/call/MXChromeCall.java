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

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.json.JSONObject;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;

import java.lang.reflect.Array;
import java.util.ArrayList;

public class MXChromeCall implements IMXCall {
    private static final String LOG_TAG = "MXChromeCall";

    private MXSession mSession = null;
    private Context mContext = null;
    private Room mRoom = null;
    private ArrayList<MXCallListener> mxCallListeners = new ArrayList<MXCallListener>();

    private WebView mWebView = null;
    private CallWebAppInterface mCallWebAppInterface = null;

    private Boolean mIsIncoming = false;
    private Boolean mIsIncomingPrepared = false;

    private JsonObject mCallInviteParams = null;

    // the current call id
    private String mCallId = null;

    private ArrayList<JsonElement> mPendingCandidates = new ArrayList<JsonElement>();

    // UI thread handler
    final Handler mUIThreadHandler = new Handler();

    private Boolean mIsVideoCall = false;

    /**
     * @return true if this stack can perform calls.
     */
    public static Boolean isSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    }

    // creator
    public MXChromeCall(MXSession session, Context context) {
        if (!isSupported()) {
            throw new AssertionError("MXChromeCall : not supported with the current android version");
        }

        if (null == session) {
            throw new AssertionError("MXChromeCall : session cannot be null");
        }

        if (null == context) {
            throw new AssertionError("MXChromeCall : context cannot be null");
        }

        mCallId = "c" + System.currentTimeMillis();
        mSession = session;
        mContext = context;
    }

    @SuppressLint("NewApi")
    public void createCallView() {
        mUIThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                mWebView = new WebView(mContext);

                // warn that the webview must be added in an activity/fragment
                onViewLoading();

                mUIThreadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mCallWebAppInterface = new CallWebAppInterface();
                        mWebView.addJavascriptInterface(mCallWebAppInterface, "Android");

                        mWebView.setWebContentsDebuggingEnabled(true);
                        WebSettings settings = mWebView.getSettings();

                        // Enable Javascript
                        settings.setJavaScriptEnabled(true);

                        // Use WideViewport and Zoom out if there is no viewport defined
                        settings.setUseWideViewPort(true);
                        settings.setLoadWithOverviewMode(true);

                        // Enable pinch to zoom without the zoom buttons
                        settings.setBuiltInZoomControls(true);

                        // Allow use of Local Storage
                        settings.setDomStorageEnabled(true);

                        settings.setAllowFileAccessFromFileURLs(true);
                        settings.setAllowUniversalAccessFromFileURLs(true);

                        settings.setDisplayZoomControls(false);

                        mWebView.setWebViewClient(new WebViewClient());

                        // AppRTC requires third party cookies to work
                        android.webkit.CookieManager cookieManager = android.webkit.CookieManager.getInstance();
                        cookieManager.setAcceptThirdPartyCookies(mWebView, true);

                        final String url = "file:///android_asset/www/call.html";
                        mWebView.loadUrl(url);

                        mWebView.setWebChromeClient(new WebChromeClient() {
                            @Override
                            public void onPermissionRequest(final PermissionRequest request) {
                                mUIThreadHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        request.grant(request.getResources());
                                    }
                                });
                            }
                        });
                    }
                });
            }
        });
    }


    // actions (must be done after onViewReady()

    /**
     * Start a call.
     */
    public void placeCall() {
        if (CALL_STATE_FLEDGLING.equals(getCallState())) {
            mIsIncoming = false;

            mUIThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    mWebView.loadUrl(mIsVideoCall ? "javascript:placeVideoCall()" : "javascript:placeVoiceCall()");
                }
            });
        }
    }

    /**
     * Prepare a call reception.
     * @param callInviteParams the invitation Event content
     * @param callId the call ID
     */
    public void prepareIncomingCall(final JsonObject callInviteParams, final String callId) {
        mCallId = callId;

        if (CALL_STATE_FLEDGLING.equals(getCallState())) {
            mIsIncoming = true;

            mUIThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    mWebView.loadUrl("javascript:initWithInvite('" + callId + "'," + callInviteParams.toString() + ")");
                    mIsIncomingPrepared = true;

                    mWebView.post(new Runnable() {
                        @Override
                        public void run() {
                            mWebView.loadUrl("javascript:getCallType()");
                            checkPendingCandidates();
                        }
                    });
                }
            });
        } else if (CALL_STATE_CREATED.equals(getCallState())) {
            mCallInviteParams = callInviteParams;
        }
    }

    /**
     * The call has been detected as an incoming one.
     * The application launched the dedicated activity and expects to launch the incoming call.
     */
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
        if (!CALL_STATE_CREATED.equals(getCallState()) && (null != mWebView)) {
            mUIThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    mWebView.loadUrl("javascript:receivedAnswer(" + event.content.toString() + ")");
                }
            });
        }
    }

    /**
     * The other call member hangs up the call.
     * @param event the event
     */
    private void onCallHangup(final Event event) {
        if (!CALL_STATE_CREATED.equals(getCallState()) && (null != mWebView)) {
            mUIThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    mWebView.loadUrl("javascript:onHangupReceived(" + event.content.toString() + ")");

                    mWebView.post(new Runnable() {
                        @Override
                        public void run() {
                            onCallEnd();
                        }
                    });
                }
            });
        }
    }

    /**
     * A new Ice candidate is received
     * @param candidate
     */
    public void onNewCandidate(final JsonElement candidate) {
        if (!CALL_STATE_CREATED.equals(getCallState()) && (null != mWebView)) {
            mWebView.post(new Runnable() {
                @Override
                public void run() {
                    mWebView.loadUrl("javascript:gotRemoteCandidate(" + candidate.toString() + ")");
                }
            });
        }
    }

    /**
     * Add ice candidates
     * @param candidate ic candidate
     */
    private void addCandidate(JsonElement candidate) {
        if (mIsIncomingPrepared || !isIncoming()) {
            onNewCandidate(candidate);
        } else {
            synchronized (LOG_TAG) {
                mPendingCandidates.add(candidate);
            }
        }
    }

    /**
     * Some Ice candidates could have been received while creating the call view.
     * Check if some of them have been defined.
     */
    public void checkPendingCandidates() {
        synchronized (LOG_TAG) {
            for(JsonElement candidate : mPendingCandidates) {
                final JsonElement fcandidate = candidate;

                mWebView.post(new Runnable() {
                    @Override
                    public void run() {
                        onNewCandidate(fcandidate);
                    }
                });
            }

            mPendingCandidates.clear();
        }
    }

    // events thread
    /**
     * Manage the call events.
     * @param event the call event.
     */
    public void handleCallEvent(Event event){
        if (event.isCallEvent()) {
            // event from other member
            if (!event.userId.equals(mSession.getMyUser().userId)) {
                if (Event.EVENT_TYPE_CALL_ANSWER.equals(event.type) && !mIsIncoming) {
                    onCallAnswer(event);
                } else if (Event.EVENT_TYPE_CALL_CANDIDATES.equals(event.type)) {
                    JsonArray candidates = event.content.getAsJsonArray("candidates");

                    for (JsonElement candidate : candidates) {
                        addCandidate(candidate);
                    }
                } else if (Event.EVENT_TYPE_CALL_HANGUP.equals(event.type)) {
                    onCallHangup(event);
                }
            } else if (Event.EVENT_TYPE_CALL_INVITE.equals(event.type)) {
                // server echo : assume that the other device is ringing
                mCallWebAppInterface.mCallState = IMXCall.CALL_STATE_RINGING;

                // warn in the UI thread
                mUIThreadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        onStateDidChange(mCallWebAppInterface.mCallState);
                    }
                });

            }
        }
    }

    // user actions
    /**
     * The call is accepted.
     */
    public void answer() {
        if (!CALL_STATE_CREATED.equals(getCallState()) && (null != mWebView)) {
            mUIThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    mWebView.loadUrl("javascript:answerCall()");
                }
            });
        }
    }

    /**
     * The call is hung up.
     */
    public void hangup() {
        if (!CALL_STATE_CREATED.equals(getCallState()) && (null != mWebView)) {
            mUIThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    mWebView.loadUrl("javascript:hangup()");
                }
            });
        }
    }

    // listener managemenent
    public void addListener(MXCallListener callListener){
        synchronized (LOG_TAG) {
            mxCallListeners.add(callListener);
        }
    }

    public void removeListener(MXCallListener callListener) {
        synchronized (LOG_TAG) {
            mxCallListeners.remove(callListener);
        }
    }

    public void clearListeners() {
        synchronized (LOG_TAG) {
            mxCallListeners.clear();
        }
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
        return mRoom;
    }

    /**
     * Set the linked room.
     * @param room the room
     */
    public void setRoom(Room room) {
        mRoom = room;
    }

    /**
     * @return true if the call is an incoming call.
     */
    public Boolean isIncoming() {
        return mIsIncoming;
    }

    /**
     * @param isIncoming true if the call is an incoming one.
     */
    public void setIsIncoming(Boolean isIncoming) {
        mIsIncoming = isIncoming;
    }

    /**
     * Defines the call type
     */
    public void setIsVideo(Boolean isVideo) {
        mIsVideoCall = isVideo;
    }

    /**
     * @return true if the call is a video call.
     */
    public Boolean isVideo() {
        return mIsVideoCall;
    }

    /**
     * @return the callstate (must be a CALL_STATE_XX value)
     */
    public String getCallState() {
        if (null != mCallWebAppInterface) {
            return mCallWebAppInterface.mCallState;
        } else {
            return CALL_STATE_CREATED;
        }
    }

    /**
     * @return the callView
     */
    public View getCallView() {
        return mWebView;
    }

    // listener methods
    private void onStateDidChange(String newState) {
        synchronized (LOG_TAG) {
            for (MXCallListener listener : mxCallListeners) {
                try {
                    listener.onStateDidChange(newState);
                    ;
                } catch (Exception e) {
                }
            }
        }
    }

    // warn that the webview is loading
    private void onViewLoading() {
        synchronized (LOG_TAG) {
            for (MXCallListener listener : mxCallListeners) {
                try {
                    listener.onViewLoading(mWebView);
                    ;
                } catch (Exception e) {
                }
            }
        }
    }

    private void onViewReady() {
        synchronized (LOG_TAG) {
            for (MXCallListener listener : mxCallListeners) {
                try {
                    listener.onViewReady();
                } catch (Exception e) {
                }
            }
        }
    }

    private void onCallEnd() {
        synchronized (LOG_TAG) {
            for (MXCallListener listener : mxCallListeners) {
                try {
                    listener.onCallEnd();
                } catch (Exception e) {
                }
            }
        }
    }

    // private class
    private class CallWebAppInterface {
        public String mCallState = CALL_STATE_CREATING_CALL_VIEW;

        private ArrayList<Event> mPendingEvents = new ArrayList<Event>();
        private Event mPendingEvent = null;

        CallWebAppInterface()  {
            if (null == mRoom) {
                throw new AssertionError("MXChromeCall : room cannot be null");
            }
        }

        // JS <-> android calls
        @JavascriptInterface
        public String wgetCallId() {
            return mCallId;
        }

        @JavascriptInterface
        public String wgetRoomId() {
            return mRoom.getRoomId();
        }

        @JavascriptInterface
        public void wlog(String message) {
            Log.e(LOG_TAG, "WebView Message : " + message);
        }

        @JavascriptInterface
        public void wCallError(int code , String message) {
            Log.e(LOG_TAG, "WebView error Message : " + message);
        }

        @JavascriptInterface
        public void wEmit(String title , String message) {
            //Toast.makeText(mContext, title + " : " + message, Toast.LENGTH_LONG).show();
        }

        @JavascriptInterface
        public void showToast(String toast)  {
            //Toast.makeText(mContext, toast, Toast.LENGTH_SHORT).show();
        }

        @JavascriptInterface
        public void onCallType(String type)  {
            mIsVideoCall = "video".equals(type);
        }

        @JavascriptInterface
        public void wOnStateUpdate(String jsstate)  {
            String nextState = null;

            if ("fledgling".equals(jsstate)) {
                nextState = CALL_STATE_FLEDGLING;
            } else if ("wait_local_media".equals(jsstate)) {
                nextState = CALL_STATE_WAIT_LOCAL_MEDIA;
            } else if ("create_offer".equals(jsstate)) {
                nextState = CALL_STATE_WAIT_CREATE_OFFER;
            } else if ("invite_sent".equals(jsstate)) {
                nextState = CALL_STATE_INVITE_SENT;
            } else if ("ringing".equals(jsstate)) {
                nextState = CALL_STATE_RINGING;
            } else if ("create_answer".equals(jsstate)) {
                nextState = CALL_STATE_CREATE_ANSWER;
            } else if ("connecting".equals(jsstate)) {
                nextState = CALL_STATE_CONNECTING;
            } else if ("connected".equals(jsstate)) {
                nextState = CALL_STATE_CONNECTED;
            } else if ("ended".equals(jsstate)) {
                nextState = CALL_STATE_ENDED;
            }

            // is there any state update ?
            if ((null != nextState) && !mCallState.equals(nextState)) {
                mCallState = nextState;

                // warn in the UI thread
                mUIThreadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        onStateDidChange(mCallState);
                    }
                });
            }
        }

        @JavascriptInterface
        public void wOnLoaded() {
            mCallState = CALL_STATE_FLEDGLING;

            mUIThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    onViewReady();
                }
            });
        }

        private void sendNextEvent() {
            mUIThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    // ready to send
                    if ((null == mPendingEvent) && (0 != mPendingEvents.size())) {
                        mPendingEvent = mPendingEvents.get(0);
                        mPendingEvents.remove(mPendingEvent);

                        mRoom.sendEvent(mPendingEvent, new ApiCallback<Void>() {
                            @Override
                            public void onSuccess(Void info) {
                                mUIThreadHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (mPendingEvent.type.equals(Event.EVENT_TYPE_CALL_HANGUP)) {
                                            mUIThreadHandler.post(new Runnable() {
                                                @Override
                                                public void run() {
                                                    onCallEnd();
                                                }
                                            });
                                        } else {
                                            mPendingEvent = null;
                                            sendNextEvent();
                                        }
                                    }
                                });
                            }

                            @Override
                            public void onNetworkError(Exception e) {
                            }

                            @Override
                            public void onMatrixError(MatrixError e) {
                            }

                            @Override
                            public void onUnexpectedError(Exception e) {
                            }
                        });
                    }
                }
            });
        }

        @JavascriptInterface
        public void wSendEvent(final String roomId, final String eventType, final String jsonContent) {
            mUIThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        Boolean addIt = true;
                        JsonObject content = (JsonObject) new JsonParser().parse(jsonContent);

                        // merge candidates
                        if (eventType.equals(Event.EVENT_TYPE_CALL_CANDIDATES) && (mPendingEvents.size() > 0)) {
                            try {
                                Event lastEvent = mPendingEvents.get(mPendingEvents.size() - 1);

                                if (lastEvent.type.equals(Event.EVENT_TYPE_CALL_CANDIDATES)) {
                                    JsonObject lastContent = lastEvent.content;

                                    JsonArray lastContentCandidates = lastContent.get("candidates").getAsJsonArray();
                                    JsonArray newContentCandidates = content.get("candidates").getAsJsonArray();

                                    Log.e(LOG_TAG, "Merge candidates from " + lastContentCandidates.size() + " to " + (lastContentCandidates.size() + newContentCandidates.size() + " items."));

                                    lastContentCandidates.addAll(newContentCandidates);

                                    lastEvent.content.remove("candidates");
                                    lastEvent.content.add("candidates", lastContentCandidates);
                                    addIt = false;
                                }
                            } catch (Exception e) {
                            }
                        }

                        if (addIt) {
                            Event event = new Event(eventType, content, mSession.getCredentials().userId, mRoom.getRoomId());

                            if (null != event) {
                                mPendingEvents.add(event);
                            }
                        }

                        // send events
                        sendNextEvent();

                    } catch (Exception e) {

                    }
                }
            });
        }
    }
}
