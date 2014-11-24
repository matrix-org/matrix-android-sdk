package org.matrix.matrixandroidsdk.fragments;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.widget.Toast;

import org.matrix.androidsdk.RestClient;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.Message;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.TextMessage;
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
    }

    private MatrixMessagesListener mMatrixMessagesListener;
    private MXSession mSession;
    private Context mContext;
    private Room mRoom;
    private boolean mJoinedRoom = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        mContext = getActivity().getApplicationContext();
        // TODO : Specify which session should be used.
        mSession = Matrix.getInstance(mContext).getDefaultSession();
        String roomId = getArguments().getString(ARG_ROOM_ID);
        if (roomId == null) {
            throw new RuntimeException("Must have a room ID specified.");
        }
        if (mSession == null) {
            throw new RuntimeException("Must have valid default MXSession.");
        }

        // check if this room has been joined, if not, join it then get messages.
        mRoom = mSession.getDataHandler().getRoom(roomId);
        mRoom.initHistory();
        if (mRoom != null) {
            RoomMember self = mRoom.getMember(mSession.getCredentials().userId);
            if (self != null && "join".equals(self.membership)) {
                mJoinedRoom = true;
            }
        }

        // FIXME: There is a race here where you could get duplicate messages, where it comes down
        // this stream and again from the store/messages API.
        mSession.getDataHandler().addListener(new MXEventListener() {
            @Override
            public void onLiveEvent(Event event, RoomState roomState) {
                if (mRoom.getRoomId().equals(event.roomId)) {
                    // Wait to have fully joined before handling live events. They will come back down when paginating.
                    if (mJoinedRoom) {
                        mMatrixMessagesListener.onLiveEvent(event, roomState);
                    }
                }
            }

            @Override
            public void onBackEvent(Event event, RoomState roomState) {
                if (mRoom.getRoomId().equals(event.roomId)) {
                    mMatrixMessagesListener.onBackEvent(event, roomState);
                }
            }
        });

        if (!mJoinedRoom) {
            Log.i(LOG_TAG, "Joining room >> " + roomId);
            joinRoom();
        }
        else {
            // Let's leave it up to the layer above to request pagination
//            mRoom.requestHistory();
        }
    }

    private void joinRoom() {
        mRoom.join(new Room.OnCompleteCallback() {
            @Override
            public void onComplete() {
                mJoinedRoom = true;
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
     */
    public void requestHistory(Room.HistoryCompleteCallback callback) {
        // If the room is not yet joined, pagination requests will fail
        if (mJoinedRoom) {
            mRoom.requestHistory(callback);
        }
    }

    /**
     * Send a text message in this room.
     * @param body The text to send.
     */
    public void sendMessage(String body) {
        TextMessage message = new TextMessage();
        message.body = body;
        message.msgtype = Message.MSGTYPE_TEXT;
        send(message);
    }

    /**
     * Send an emote message in this room.
     * @param emote The emote to send.
     */
    public void sendEmote(String emote) {
        TextMessage message = new TextMessage();
        message.body = emote;
        message.msgtype = Message.MSGTYPE_EMOTE;
        send(message);
    }

    public void send(Message message) {
        mSession.getRoomsApiClient().sendMessage(mRoom.getRoomId(), message, new RestClient.SimpleApiCallback<Event>() {

            @Override
            public void onSuccess(Event info) {
                Log.d(LOG_TAG, "onSuccess >>>> " + info);
                // TODO: This should probably be fed back to the caller.
            }

            @Override
            public void onNetworkError(Exception e) {
                Toast.makeText(mContext, "Unable to send message. Connection error.", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                Toast.makeText(mContext, "Unable to send message.", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
