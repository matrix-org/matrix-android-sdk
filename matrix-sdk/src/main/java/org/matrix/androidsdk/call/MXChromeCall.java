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
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
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
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;

import java.util.ArrayList;
import java.util.Collection;

public class MXChromeCall implements IMXCall {
    private static final String LOG_TAG = "MXChromeCall";

    private MXSession mSession = null;
    private Context mContext = null;
    private Room mRoom = null;
    private ArrayList<MXCallListener> mxCallListeners = new ArrayList<MXCallListener>();

    private WebView mWebView = null;
    private Boolean mWebviewIsloaded = false;
    private CallWebAppInterface mCallWebAppInterface;

    private Boolean mIsIncoming = false;
    private Boolean mIsIncomingPrepared = false;

    private ArrayList<JsonElement> mPendingCandidates = new ArrayList<JsonElement>();

    // UI thread handler
    final Handler mUIThreadHandler = new Handler();

    /**
     * @return true if this stack can perform calls.
     */
    public static Boolean isSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    }
    // creator
    public MXChromeCall(MXSession session, Context context, Room room) {
        if (!isSupported()) {
            throw new AssertionError("MXChromeCall : not supported with the current android version");
        }

        if (null == session) {
            throw new AssertionError("MXChromeCall : session cannot be null");
        }

        if (null == context) {
            throw new AssertionError("MXChromeCall : context cannot be null");
        }

        if (null == room) {
            throw new AssertionError("MXChromeCall : room cannot be null");
        }

        mSession = session;
        mContext = context;
        mRoom = room;
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
     * @param isVideo true if it is a video call.
     */
    public void placeCall(Boolean isVideo) {
        if (mWebviewIsloaded) {
            mIsIncoming = false;
            if (isVideo) {
                mWebView.loadUrl("javascript:placeVideoCall()");
            } else {
                mWebView.loadUrl("javascript:placeVoiceCall()");
            }
        }
    }

    /**
     * Prepare a call reception.
     * @param callInviteParams the invitation Event content
     * @param callId the call ID
     */
    public void prepareIncomingCall(JsonObject callInviteParams, String callId) {
        mIsIncoming = true;
        mWebView.loadUrl("javascript:initWithInvite('" + callId + "'," + callInviteParams.toString() + ")");
        mIsIncomingPrepared = true;

        mWebView.post(new Runnable() {
            @Override
            public void run() {
                checkPendingCandidates();
            }
        });
    }

    /**
     * The callee accepts the call.
     * @param event the event
     */
    private void onCallAnswer(Event event) {
        mWebView.loadUrl("javascript:receivedAnswer(" + event.content.toString() + ")");
    }

    /**
     * The other call member hangs up the call.
     * @param event the event
     */
    private void onCallHangup(Event event) {
        mWebView.loadUrl("javascript:onHangupReceived(" + event.content.toString() + ")");

        mWebView.post(new Runnable() {
            @Override
            public void run() {
                onCallEnd();
            }
        });
    }

    /**
     * A new Ice candidate is received
     * @param candidate
     */
    public void onNewCandidate(JsonElement candidate) {
        mWebView.loadUrl("javascript:gotRemoteCandidate(" + candidate.toString() + ")");
    }

    /**
     * Add ice candidates
     * @param candidate ic candidate
     */
    private void addCandidate(JsonElement candidate) {
        if (mIsIncomingPrepared) {
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
                onNewCandidate(candidate);
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
        if (event.isCallEvent() && !event.userId.equals(mSession.getMyUser().userId)) {
            if (Event.EVENT_TYPE_CALL_ANSWER.equals(event.type) && !mIsIncoming) {
                onCallAnswer(event);
            } else if (Event.EVENT_TYPE_CALL_CANDIDATES.equals(event.type)) {
                JsonArray candidates = event.content.getAsJsonArray("candidates");

                for (JsonElement candidate : candidates) {
                    addCandidate(candidate);
                }
            }
            else if (Event.EVENT_TYPE_CALL_HANGUP.equals(event.type)) {
                onCallHangup(event);
            }
        }
    }

    // user actions
    /**
     * The call is accepted.
     */
    public void answer() {
        mWebView.loadUrl("javascript:answerCall()");
    }

    /**
     * The call is hung up.
     */
    public void hangup() {
        mWebView.loadUrl("javascript:hangup()");
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

    // getters / setters
    /**
     * @return the callId
     */
    public String callId() {
        return null;
    }

    /**
     * @return true if the call is an incoming call.
     */
    public Boolean isIncoming() {
        return false;
    }

    /**
     * @return the callstate (must be a CALL_STATE_XX value)
     */
    public String callState() {
        return null;
    }

    /**
     * @return the callView
     */
    public View callView() {
        return mWebView;
    }

    // listener methods
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

        CallWebAppInterface()  {
        }

        // JS <-> android calls
        @JavascriptInterface
        public String wgetAccessToken() {
            return mSession.getCredentials().accessToken;
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
            Toast.makeText(mContext, title + " : " + message, Toast.LENGTH_LONG).show();
        }

        @JavascriptInterface
        public void showToast(String toast)  {
            Toast.makeText(mContext, toast, Toast.LENGTH_SHORT).show();
        }

        @JavascriptInterface
        public void wOnLoaded() {
            mWebviewIsloaded = true;

            mWebView.post(new Runnable() {
                @Override
                public void run() {
                    onViewReady();
                }
            });
        }

        @JavascriptInterface
        public void wSendEvent(final String roomId, final String eventType, final String jsonContent) {
            try {
                JsonObject content = (JsonObject) new JsonParser().parse(jsonContent);

                Toast.makeText(mContext, eventType, Toast.LENGTH_SHORT).show();

                Event event = new Event(eventType, content, mSession.getCredentials().userId, mRoom.getRoomId());
                mRoom.sendEvent(event, new ApiCallback<Void>() {
                    @Override
                    public void onSuccess(Void info) {
                        if (eventType.equals(Event.EVENT_TYPE_CALL_HANGUP)) {
                            mWebView.post(new Runnable() {
                                @Override
                                public void run() {
                                    onCallEnd();
                                }
                            });
                        }
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

            } catch (Exception e) {
            }
        }
    }
}
