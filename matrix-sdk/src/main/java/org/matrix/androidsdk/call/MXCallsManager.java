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
import java.util.HashMap;

public class MXCallsManager {
    private static final String LOG_TAG = "MXCallsManager";

    public interface MXCallsManagerListener {
        /**
         * Called when there is an incoming call within the room.
         */
        public void onIncomingCall(IMXCall call);
    }

    private MXSession mSession = null;
    private Context mContext = null;

    // UI thread handler
    final Handler mUIThreadHandler = new Handler();

    // active calls
    private HashMap<String, IMXCall> mCallsByCallId = new HashMap<String, IMXCall>();

    // listeners
    private ArrayList<MXCallsManagerListener> mListeners = new ArrayList<MXCallsManagerListener>();

    // incoming calls
    private ArrayList<String> mxPendingIncomingCallId = new ArrayList<String>();

    public MXCallsManager(MXSession session, Context context) {
        mSession = session;
        mContext = context;
    }

    /**
     * @return true if the call feature is supported
     */
    public Boolean isSupported() {
        return MXChromeCall.isSupported();
    }

    /**
     * listeners management
     **/

    public void addListener(MXCallsManagerListener listener) {
        synchronized (this) {
            if (null != listener) {
                if (mListeners.indexOf(listener) < 0) {
                    mListeners.add(listener);
                }
            }
        }
    }

    public void removeListener(MXCallsManagerListener listener) {
        synchronized (this) {
            if (null != listener) {
                if (mListeners.indexOf(listener) < 0) {
                    mListeners.add(listener);
                }
            }
        }
    }

    /**
     * dispatch the onIncomingCall event to the listeners
     * @param call the call
     */
    private void onIncomingCall(IMXCall call) {
        synchronized (this) {
            for(MXCallsManagerListener l : mListeners) {
                try {
                    l.onIncomingCall(call);
                } catch (Exception e) {
                }
            }
        }
    }

    /**
     * create a new call
     * @param callId the call Id (null to use a default value)
     * @return the IMXCall
     */
    private IMXCall createCall(String callId) {
        // TODO switch IMXCall object
        IMXCall call = new MXChromeCall(mSession, mContext);

        // a valid callid is provided
        if (null != callId) {
            call.setCallId(callId);
        }

        return call;
    }

    /**
     * Returns the IMXCall from its callId.
     * @param callId the call Id
     * @return the IMXCall if it exists
     */
    public IMXCall callWithCallId(String callId) {
        return callWithCallId(callId, false);
    }

    /**
     * Returns the IMXCall from its callId.
     * @param callId the call Id
     * @param create create the IMXCall if it does not exist
     * @return the IMXCall if it exists
     */
    private IMXCall callWithCallId(String callId, Boolean create) {
        IMXCall call = null;

        // check if the call exists
        if (null != callId) {
            synchronized (this) {
                call = mCallsByCallId.get(callId);
            }
        }

        // the call does not exist but request to create it
        if ((null == call) && create) {
            call = createCall(callId);
            synchronized (this) {
                mCallsByCallId.put(call.getCallId(), call);
            }
        }

        return call;
    }

    /**
     * Manage the call events.
     * @param event the call event.
     */
    public void handleCallEvent(final Event event) {
        if (event.isCallEvent() && isSupported()) {
            // always run the call event in the UI thread
            // MXChromeCall does not work properly in other thread (because of the webview)
            mUIThreadHandler.post(new Runnable() {
                @Override
                public void run() {
                    Boolean isMyEvent = event.userId.equals(mSession.getMyUser().userId);
                    Room room = mSession.getDataHandler().getRoom(event.roomId);

                    String callId = null;

                    try {
                        callId = event.content.getAsJsonPrimitive("call_id").getAsString();
                    } catch (Exception e) {

                    }
                    // sanity check
                    if ((null != callId) && (null != room)) {
                        // receive an invitation
                        if (Event.EVENT_TYPE_CALL_INVITE.equals(event.type)) {
                            long lifeTime = System.currentTimeMillis() - event.getOriginServerTs();

                            // ignore older call messages
                            if (lifeTime < 30000) {
                                // create the call only it is triggered from someone else
                                IMXCall call = callWithCallId(callId, !isMyEvent);

                                // sanity check
                                if (null != call) {
                                    // init the information
                                    call.setRoom(room);

                                    if (!isMyEvent) {
                                        call.prepareIncomingCall(event.content, callId);
                                        call.setIsIncoming(true);
                                        mxPendingIncomingCallId.add(callId);
                                    } else {
                                        call.handleCallEvent(event);
                                    }
                                }
                            }

                        } else if (Event.EVENT_TYPE_CALL_CANDIDATES.equals(event.type)) {
                            if (!isMyEvent) {
                                IMXCall call = callWithCallId(callId);

                                if (null != call) {
                                    call.setRoom(room);
                                    call.handleCallEvent(event);
                                }
                            }
                        } else if (Event.EVENT_TYPE_CALL_ANSWER.equals(event.type)) {
                            IMXCall call = callWithCallId(callId);

                            if (null != call) {
                                // assume it is a catch up call.
                                // the creation / candidates /
                                // the call has been answered on another device
                                if (IMXCall.CALL_STATE_CREATED.equals(call.getCallState())) {
                                    synchronized (this) {
                                        mCallsByCallId.remove(callId);
                                    }
                                } else {
                                    call.setRoom(room);
                                    call.handleCallEvent(event);
                                }
                            }
                        } else if (Event.EVENT_TYPE_CALL_HANGUP.equals(event.type)) {

                            IMXCall call = callWithCallId(callId);
                            if (null != call) {
                                call.setRoom(room);

                                if (!IMXCall.CALL_STATE_CREATED.equals(call.getCallState())) {
                                    call.handleCallEvent(event);
                                }

                                synchronized (this) {
                                    mCallsByCallId.remove(callId);
                                }
                            }
                        }
                    }
                }
            });
        }
    }

    /**
     * check if there is a pending incoming call
     */
    public void checkPendingIncomingCalls() {
        mUIThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mxPendingIncomingCallId.size() > 0) {
                    for (String callId : mxPendingIncomingCallId) {
                        IMXCall call = callWithCallId(callId);

                        if (null != call) {
                            onIncomingCall(call);
                        }
                    }
                    mxPendingIncomingCallId.clear();
                }
            }
        });
    }

    /**
     * Create an IMXCall in the room RoomId
     * @param RoomId the roomId of the room
     * @return the IMXCall if it can be done
     */
    public IMXCall createCallInRoom(String RoomId) {
        IMXCall call = null;
        Room room = mSession.getDataHandler().getRoom(RoomId);

        // sanity check
        if (null != room) {
            if (isSupported()) {
                call = callWithCallId(null, true);
                call.setRoom(room);
            }
        }

        return call;
    }
}
