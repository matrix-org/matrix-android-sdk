package org.matrix.matrixandroidsdk.fragments;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;

import org.matrix.androidsdk.MXApiClient;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.api.response.Event;
import org.matrix.androidsdk.api.response.Message;
import org.matrix.androidsdk.api.response.TokensChunkResponse;
import org.matrix.matrixandroidsdk.Matrix;

import java.util.List;

/**
 * A non-UI fragment containing logic for extracting messages from a room, including handling
 * pagination. For a UI implementation of this, see {@link MatrixMessageListFragment}.
 */
public class MatrixMessagesFragment extends Fragment {
    public static final String ARG_ROOM_ID = "org.matrix.matrixandroidsdk.fragments.MatrixMessageFragment.ARG_ROOM_ID";

    public static MatrixMessagesFragment newInstance(String roomId) {
        MatrixMessagesFragment fragment = new MatrixMessagesFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ROOM_ID, roomId);
        fragment.setArguments(args);
        return fragment;
    }

    public interface MatrixMessagesListener {
        /**
         * Some messages have been received and need to be displayed.
         * @param messages The messages received. They should be added to the end of the list.
         */
        public void onReceiveMessages(List<Message> messages);
    }

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
    }

    /* Public API below */

    /**
     * Request earlier messages in this room.
     * @param callback The callback to invoke when more messages have arrived.
     */
    public void requestPagination(final MXApiClient.ApiCallback<TokensChunkResponse<Event>> callback) {
        mSession.getRoomsApiClient().getEarlierMessages(mRoomId, mEarliestToken,
                new MXApiClient.ApiCallback<TokensChunkResponse<Event>>() {

                    @Override
                    public void onSuccess(TokensChunkResponse<Event> info) {

                        callback.onSuccess(info);
                    }
                });
    }





}
