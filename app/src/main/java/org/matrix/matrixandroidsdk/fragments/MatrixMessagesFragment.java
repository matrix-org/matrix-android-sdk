package org.matrix.matrixandroidsdk.fragments;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;

import org.matrix.androidsdk.MXApiClient;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.api.response.Event;
import org.matrix.androidsdk.api.response.Message;
import org.matrix.androidsdk.api.response.TokensChunkResponse;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.matrixandroidsdk.Matrix;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A non-UI fragment containing logic for extracting messages from a room, including handling
 * pagination. For a UI implementation of this, see {@link MatrixMessageListFragment}.
 */
public class MatrixMessagesFragment extends Fragment {
    public static final String ARG_ROOM_ID = "org.matrix.matrixandroidsdk.fragments.MatrixMessageFragment.ARG_ROOM_ID";

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

        mSession.getRoomsApiClient().getLatestRoomMessages(mRoomId, new MXApiClient.ApiCallback<TokensChunkResponse<Event>>() {
            @Override
            public void onSuccess(TokensChunkResponse<Event> info) {
                // return in reversed order since they come down in reversed order (newest first)
                Collections.reverse(info.chunk);
                mMatrixMessagesListener.onReceiveMessages(info.chunk);
                mEarliestToken = info.end;
            }
        });

        mSession.getDataHandler().addListener(new MXEventListener() {
            @Override
            public void onMessageReceived(Room room, Event event) {
                if (!mRoomId.equals(room.getRoomId())) {
                    return;
                }
                List<Event> events = new ArrayList<Event>();
                events.add(event);
                mMatrixMessagesListener.onReceiveMessages(events);
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
    public void requestPagination(final MXApiClient.ApiCallback<List<Event>> callback) {
        mSession.getRoomsApiClient().getEarlierMessages(mRoomId, mEarliestToken, new MXApiClient.ApiCallback<TokensChunkResponse<Event>>() {

            @Override
            public void onSuccess(TokensChunkResponse<Event> info) {
                // add to top of list.
                callback.onSuccess(info.chunk);
                mEarliestToken = info.end;
            }
        });
    }





}
