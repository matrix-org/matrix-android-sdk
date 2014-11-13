package org.matrix.matrixandroidsdk.fragments;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.matrixandroidsdk.Matrix;
import org.matrix.matrixandroidsdk.R;
import org.matrix.matrixandroidsdk.adapters.RoomMembersAdapter;

import java.util.Collection;

/**
 * A dialog fragment showing a list of room members for a given room.
 */
public class RoomMembersDialogFragment extends DialogFragment {
    public static final String ARG_ROOM_ID = "org.matrix.matrixandroidsdk.fragments.RoomMembersDialogFragment.ARG_ROOM_ID";

    public static RoomMembersDialogFragment newInstance(String roomId) {
        RoomMembersDialogFragment f= new RoomMembersDialogFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ROOM_ID, roomId);
        f.setArguments(args);
        return f;
    }

    private ListView mListView;
    private RoomMembersAdapter mAdapter;
    private String mRoomId;
    private MXSession mSession;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mRoomId = getArguments().getString(ARG_ROOM_ID);
        Context context = getActivity().getApplicationContext();

        mSession = Matrix.getInstance(context).getDefaultSession();
        if (mSession == null) {
            throw new RuntimeException("No MXSession.");
        }

    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog d = super.onCreateDialog(savedInstanceState);
        d.setTitle("Member List");
        return d;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View v = inflater.inflate(R.layout.fragment_dialog_member_list, container, false);
        mListView = ((ListView)v.findViewById(R.id.listView_members));
        mAdapter = new RoomMembersAdapter(getActivity(),
                R.layout.adapter_item_room_members
        );

        Room room = mSession.getDataHandler().getRoom(mRoomId);
        if (room != null) {
            Collection<RoomMember> members = room.getMembers();
            if (members != null) {
                for (RoomMember m : members) {
                    mAdapter.add(m);
                }
                mAdapter.sortMembers();
            }
        }

        mListView.setAdapter(mAdapter);

        return v;
    }
}
