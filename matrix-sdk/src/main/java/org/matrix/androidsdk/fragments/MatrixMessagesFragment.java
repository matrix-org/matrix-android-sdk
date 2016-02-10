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

package org.matrix.androidsdk.fragments;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.util.Log;
import android.widget.Toast;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.R;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.listeners.IMXEventListener;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.RoomMember;

import java.util.List;

/**
 * A non-UI fragment containing logic for extracting messages from a room, including handling
 * pagination. For a UI implementation of this, see {@link MatrixMessageListFragment}.
 */
public class MatrixMessagesFragment extends Fragment {
    /**
     * The room ID to get messages for.
     * Fragment argument: String.
     */
    public static final String ARG_ROOM_ID = "org.matrix.androidsdk.fragments.MatrixMessageFragment.ARG_ROOM_ID";

    private static final String LOG_TAG = "MatrixMessagesFragment";

    public static MatrixMessagesFragment newInstance(MXSession session, String roomId, MatrixMessagesListener listener) {
        MatrixMessagesFragment fragment = new MatrixMessagesFragment();
        Bundle args = new Bundle();


        if (null == listener) {
            throw new RuntimeException("Must define a listener.");
        }

        if (null == session) {
            throw new RuntimeException("Must define a session.");
        }

        if (null != roomId) {
            args.putString(ARG_ROOM_ID, roomId);
        }

        fragment.setArguments(args);
        fragment.setMatrixMessagesListener(listener);
        fragment.setMXSession(session);
        return fragment;
    }

    public interface MatrixMessagesListener {
        void onLiveEvent(Event event, RoomState roomState);
        void onLiveEventsChunkProcessed();
        void onBackEvent(Event event, RoomState roomState);
        void onDeleteEvent(Event event);
        void onResendingEvent(Event event);
        void onResentEvent(Event event);
        void onReceiptEvent(List<String> senderIds);
        void onRoomSyncWithLimitedTimeline();


        /**
         * Called when the first batch of messages is loaded.
         */
        void onInitialMessagesLoaded();

        // UI events
        void displayLoadingProgress();
        void dismissLoadingProgress();
        void logout();
    }

    // The listener to send messages back
    private MatrixMessagesListener mMatrixMessagesListener;
    // The adapted listener to register to the SDK
    private IMXEventListener mEventListener;
    private Context mContext;
    private MXSession mSession;
    private Room mRoom;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        mContext = getActivity().getApplicationContext();

        String roomId = getArguments().getString(ARG_ROOM_ID);

        // this code should never be called
        // but we've got some crashes when the session was null
        // so try to find it from the fragments call stack.
        if (null == mSession) {
            List<Fragment> fragments = null;
            FragmentManager fm = getActivity().getSupportFragmentManager();

            if (null != fm) {
                fragments = fm.getFragments();
            }

            if (null != fragments) {
                for (Fragment fragment : fragments) {
                    if (fragment instanceof MatrixMessageListFragment) {
                        mMatrixMessagesListener = (MatrixMessageListFragment) fragment;
                        mSession = ((MatrixMessageListFragment) fragment).getSession();
                    }
                }
            }
        }

        if (mSession == null) {
            throw new RuntimeException("Must have valid default MXSession.");
        }

        // check if this room has been joined, if not, join it then get messages.
        mRoom = mSession.getDataHandler().getRoom(roomId);
        boolean joinedRoom = false;

        // does the room already exist ?
        if (mRoom != null) {
            // init the history
            mRoom.initHistory();
            // check if some required fields are initialized
            // else, the joining could have been half broken (network error)
            if (null != mRoom.getLiveState().creator) {
                RoomMember self = mRoom.getMember(mSession.getCredentials().userId);
                if (self != null && RoomMember.MEMBERSHIP_JOIN.equals(self.membership)) {
                    joinedRoom = true;
                }
            }

            mEventListener = new MXEventListener() {
                @Override
                public void onLiveEvent(Event event, RoomState roomState) {
                    mMatrixMessagesListener.onLiveEvent(event, roomState);
                }

                @Override
                public void onLiveEventsChunkProcessed() {
                    mMatrixMessagesListener.onLiveEventsChunkProcessed();
                }

                @Override
                public void onBackEvent(Event event, RoomState roomState) {
                    mMatrixMessagesListener.onBackEvent(event, roomState);
                }

                @Override
                public void onDeleteEvent(Event event) {
                    mMatrixMessagesListener.onDeleteEvent(event);
                }

                @Override
                public void onResendingEvent(Event event) {
                    mMatrixMessagesListener.onResendingEvent(event);
                }

                @Override
                public void onResentEvent(Event event) {
                    mMatrixMessagesListener.onResentEvent(event);
                }

                @Override
                public void onReceiptEvent(String roomId, List<String> senderIds) {
                    mMatrixMessagesListener.onReceiptEvent(senderIds);
                }

                @Override
                public void onRoomSyncWithLimitedTimeline(String roomId) {
                    mMatrixMessagesListener.onRoomSyncWithLimitedTimeline();
                    requestInitialHistory();
                }
            };

            mRoom.addEventListener(mEventListener);

            if (!joinedRoom) {
                Log.i(LOG_TAG, "Joining room >> " + roomId);
                joinRoom();
            }
            else {
                requestInitialHistory();
            }
        } else {
            mMatrixMessagesListener.onInitialMessagesLoaded();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (null != mRoom) {
            mRoom.removeEventListener(mEventListener);
        }
    }

    private void displayLoadingProgress() {
        if (null != mMatrixMessagesListener) {
            mMatrixMessagesListener.displayLoadingProgress();
        }
    }

    private void dismissLoadingProgress() {
        if (null != mMatrixMessagesListener) {
            mMatrixMessagesListener.dismissLoadingProgress();
        }
    }

    public void logout() {
        if (null != mMatrixMessagesListener) {
            mMatrixMessagesListener.logout();
        }
    }

    private void joinRoom() {
        displayLoadingProgress();

        mRoom.join(new SimpleApiCallback<Void>(getActivity()) {
            @Override
            public void onSuccess(Void info) {
                requestInitialHistory();
            }

            // the request will be automatically restarted when a valid network will be found
            @Override
            public void onNetworkError(Exception e) {
                Log.e(LOG_TAG, "Network error: " + e.getMessage());

                MatrixMessagesFragment.this.getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MatrixMessagesFragment.this.getActivity(), getActivity().getString(R.string.network_error), Toast.LENGTH_SHORT).show();
                        MatrixMessagesFragment.this.dismissLoadingProgress();
                    }
                });
            }

            @Override
            public void onMatrixError(MatrixError e) {
                Log.e(LOG_TAG, "Matrix error: " + e.errcode + " - " + e.getLocalizedMessage());
                // The access token was not recognized: log out
                if (MatrixError.UNKNOWN_TOKEN.equals(e.errcode)) {
                    logout();
                }

                final MatrixError matrixError = e;

                MatrixMessagesFragment.this.getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MatrixMessagesFragment.this.getActivity(), getActivity().getString(R.string.matrix_error) + " : " + matrixError.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
                        MatrixMessagesFragment.this.dismissLoadingProgress();
                    }
                });
            }

            @Override
            public void onUnexpectedError(Exception e) {
                Log.e(LOG_TAG, getActivity().getString(R.string.unexpected_error) + " : " + e.getMessage());
                MatrixMessagesFragment.this.dismissLoadingProgress();
            }
        });
    }

    /**
     * Request messages in this room upon entering.
     */
    private void requestInitialHistory() {
        displayLoadingProgress();

        // the initial sync will be retrieved when a network connection will be found
        requestHistory(new SimpleApiCallback<Integer>(getActivity()) {
            @Override
            public void onSuccess(Integer info) {
                MatrixMessagesFragment.this.dismissLoadingProgress();
                mMatrixMessagesListener.onInitialMessagesLoaded();
            }

            @Override
            public void onNetworkError(Exception e) {
                Toast.makeText(getActivity(), e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                MatrixMessagesFragment.this.dismissLoadingProgress();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                Toast.makeText(getActivity(), e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                MatrixMessagesFragment.this.dismissLoadingProgress();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                MatrixMessagesFragment.this.dismissLoadingProgress();
                Toast.makeText(getActivity(), e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    /* Public API below */

    /**
     * Set the listener which will be informed of matrix messages. This setter is provided so either
     * a Fragment or an Activity can directly receive callbacks.
     * @param listener the listener for this fragment
     */
    public void setMatrixMessagesListener(MatrixMessagesListener listener) {
        mMatrixMessagesListener = listener;
    }

    /**
     * Set the MX session
     * @param session
     */
    public void setMXSession(MXSession session) {
        mSession = session;
    }
    /**
     * Request earlier messages in this room.
     * @param callback the callback
     * @return true if the request is really started
     */
    public boolean requestHistory(ApiCallback<Integer> callback) {
        if (null != mRoom) {
            return mRoom.requestHistory(callback);
        } else {
            return false;
        }
    }

    public void sendEvent(Event event, ApiCallback<Void> callback) {
        if (null != mRoom) {
            mRoom.sendEvent(event, callback);
        }
    }

    public void redact(String eventId, ApiCallback<Event> callback) {
        if (null != mRoom) {
            mRoom.redact(eventId, callback);
        }
    }
}
