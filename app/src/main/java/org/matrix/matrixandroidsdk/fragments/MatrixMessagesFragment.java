package org.matrix.matrixandroidsdk.fragments;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;

import org.matrix.androidsdk.MXApiClient;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.ApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.Message;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.TextMessage;
import org.matrix.androidsdk.rest.model.TokensChunkResponse;
import org.matrix.matrixandroidsdk.Matrix;

import java.util.ArrayList;
import java.util.Collections;
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

    public interface MatrixMessagesListener {
        /**
         * Some messages have been received and need to be displayed.
         * @param events The events received. They should be added to the end of the list.
         */
        public void onReceiveMessages(List<Event> events);
    }

    private MatrixMessagesListener mMatrixMessagesListener;
    private MXSession mSession;
    private Context mContext;
    private String mRoomId;
    private String mEarliestToken = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        mContext = getActivity().getApplicationContext();
        // TODO : Specify which session should be used.
        mSession = Matrix.getInstance(mContext).getDefaultSession();
        mRoomId = getArguments().getString(ARG_ROOM_ID);
        if (mRoomId == null) {
            throw new RuntimeException("Must have a room ID specified.");
        }
        if (mSession == null) {
            throw new RuntimeException("Must have valid default MXSession.");
        }

        // check if this room has been joined, if not, join it then get messages.
        boolean joinedRoom = false;
        Room room = mSession.getDataHandler().getStore().getRoom(mRoomId);
        if (room != null) {
            RoomMember self = room.getMember(mSession.getCredentials().userId);
            if (self != null && "join".equals(self.membership)) {
                joinedRoom = true;
            }
        }

        if (!joinedRoom) {
            Log.i(LOG_TAG, "Joining room >> " + mRoomId);
            joinRoomThenGetMessages();
        }
        else {
            getAndListenForMessages();
        }
    }

    private void getAndListenForMessages() {
        mSession.getRoomsApiClient().getLatestRoomMessages(mRoomId, new MXApiClient.SimpleApiCallback<TokensChunkResponse<Event>>() {
            @Override
            public void onSuccess(TokensChunkResponse<Event> info) {
                // return in reversed order since they come down in reversed order (newest first)
                Collections.reverse(info.chunk);
                mMatrixMessagesListener.onReceiveMessages(info.chunk);
                mEarliestToken = info.end;
            }
        });
        // FIXME: There is a race here where you could miss messages. Ideally we should be using
        // the initial sync messages and not re-requesting the same messages from /messages API.
        mSession.getDataHandler().addListener(new MXEventListener() {
            @Override
            public void onMessageReceived(Room room, Event event) {
                process(room, event);
            }

            @Override
            public void onRoomStateUpdated(Room room, Event event, Object oldVal, Object newVal) {
                process(room, event);
            }

            private void process(Room room, Event event) {
                if (!mRoomId.equals(room.getRoomId())) {
                    return;
                }
                List<Event> events = new ArrayList<Event>();
                events.add(event);
                mMatrixMessagesListener.onReceiveMessages(events);
            }
        });
    }

    private void joinRoomThenGetMessages() {
        mSession.getRoomsApiClient().joinRoom(mRoomId, new MXApiClient.SimpleApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                getAndListenForMessages();
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
     * @param callback The callback to invoke when more messages have arrived.
     */
    public void requestPagination(final ApiCallback<List<Event>> callback) {
        mSession.getRoomsApiClient().getEarlierMessages(mRoomId, mEarliestToken, new MXApiClient.SimpleApiCallback<TokensChunkResponse<Event>>() {

            @Override
            public void onSuccess(TokensChunkResponse<Event> info) {
                // add to top of list.
                callback.onSuccess(info.chunk);
                mEarliestToken = info.end;
            }
        });
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
        mSession.getRoomsApiClient().sendMessage(mRoomId, message, new MXApiClient.SimpleApiCallback<Event>() {

            @Override
            public void onSuccess(Event info) {
                Log.d(LOG_TAG, "onSuccess >>>> " + info);
                // TODO: This should probably be fed back to the caller.
            }
        });
    }





}
