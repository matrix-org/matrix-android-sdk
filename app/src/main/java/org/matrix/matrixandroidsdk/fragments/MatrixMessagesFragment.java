package org.matrix.matrixandroidsdk.fragments;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.listeners.IMXEventListener;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.Message;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.matrixandroidsdk.Matrix;

/**
 * A non-UI fragment containing logic for extracting messages from a room, including handling
 * pagination. For a UI implementation of this, see {@link MatrixMessageListFragment}.
 */
public class MatrixMessagesFragment extends Fragment {
    /**
     * The room ID to get messages for.
     * Fragment argument: String.
     */
    public static final String ARG_ROOM_ID = "org.matrix.matrixandroidsdk.fragments.MatrixMessageFragment.ARG_ROOM_ID";

    private static final String LOG_TAG = "MatrixMessagesFragment";

    public static MatrixMessagesFragment newInstance(String roomId, MatrixMessagesListener listener) {
        MatrixMessagesFragment fragment = new MatrixMessagesFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ROOM_ID, roomId);
        fragment.setArguments(args);
        fragment.setMatrixMessagesListener(listener);
        return fragment;
    }

    public static interface MatrixMessagesListener {
        public void onLiveEvent(Event event, RoomState roomState);
        public void onBackEvent(Event event, RoomState roomState);

        /**
         * Called when the first batch of messages is loaded.
         */
        public void onInitialMessagesLoaded();
    }

    // The listener to send messages back
    private MatrixMessagesListener mMatrixMessagesListener;
    // The adapted listener to register to the SDK
    private IMXEventListener mEventListener;
    private Context mContext;
    private Room mRoom;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        mContext = getActivity().getApplicationContext();
        // TODO : Specify which session should be used.
        MXSession session = Matrix.getInstance(mContext).getDefaultSession();
        String roomId = getArguments().getString(ARG_ROOM_ID);
        if (roomId == null) {
            throw new RuntimeException("Must have a room ID specified.");
        }
        if (session == null) {
            throw new RuntimeException("Must have valid default MXSession.");
        }

        // check if this room has been joined, if not, join it then get messages.
        mRoom = session.getDataHandler().getRoom(roomId);
        boolean joinedRoom = false;
        mRoom.initHistory();
        if (mRoom != null) {
            RoomMember self = mRoom.getMember(session.getCredentials().userId);
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
            public void onBackEvent(Event event, RoomState roomState) {
                mMatrixMessagesListener.onBackEvent(event, roomState);
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
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mRoom.removeEventListener(mEventListener);
    }

    private void joinRoom() {
        mRoom.join(new SimpleApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                requestInitialHistory();
            }
        });
    }

    /**
     * Request messages in this room upon entering.
     */
    private void requestInitialHistory() {
        requestHistory(new SimpleApiCallback<Integer>() {
            @Override
            public void onSuccess(Integer info) {
                mMatrixMessagesListener.onInitialMessagesLoaded();
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
     * Request earlier messages in this room.
     * @param callback the callback
     */
    public void requestHistory(ApiCallback<Integer> callback) {
        mRoom.requestHistory(callback);
    }

    public void send(Message message, ApiCallback<Event> callback) {
        mRoom.sendMessage(message, callback);
    }
}
