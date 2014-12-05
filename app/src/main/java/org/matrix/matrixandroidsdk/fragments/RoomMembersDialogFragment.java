package org.matrix.matrixandroidsdk.fragments;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.matrixandroidsdk.Matrix;
import org.matrix.matrixandroidsdk.R;
import org.matrix.matrixandroidsdk.adapters.RoomMembersAdapter;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * A dialog fragment showing a list of room members for a given room.
 */
public class RoomMembersDialogFragment extends DialogFragment {
    private static final String LOG_TAG = "RoomMembersDialogFragment";

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

        final Room room = mSession.getDataHandler().getRoom(mRoomId);
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

        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            private static final int OPTION_CANCEL = 0;
            private static final int OPTION_KICK = 1;
            private static final int OPTION_BAN = 2;

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // Get the member and display the possible actions for them
                final RoomMember roomMember = mAdapter.getItem(position);

                // TODO: Filter out unavailable options (depending on power levels and the member's state)
                final List<Integer> options = Arrays.asList(new Integer[] {OPTION_KICK, OPTION_BAN, OPTION_CANCEL});

                final ApiCallback callback = new SimpleApiCallback() {
                    @Override
                    public void onMatrixError(MatrixError e) {
                        if (MatrixError.FORBIDDEN.equals(e.errcode)) {
                            Toast.makeText(getActivity(), e.error, Toast.LENGTH_LONG).show();
                        }
                    }
                };

                new AlertDialog.Builder(getActivity())
                        .setItems(buildOptionLabels(options), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                switch (options.get(which)) {
                                    case OPTION_CANCEL:
                                        dialog.cancel();
                                        break;
                                    case OPTION_KICK:
                                        room.kick(roomMember.getUser().userId, callback);
                                        dialog.dismiss();
                                        break;
                                    case OPTION_BAN:
                                        room.ban(roomMember.getUser().userId, callback);
                                        dialog.dismiss();
                                        break;
                                    default:
                                        Log.e(LOG_TAG, "Unknown option: " + which);
                                }
                            }
                        })
                        .create()
                        .show();
            }

            private String[] buildOptionLabels(List<Integer> options) {
                String[] labels = new String[options.size()];
                for (int i = 0; i < options.size(); i++) {
                    String label = "";
                    switch (options.get(i)) {
                        case OPTION_CANCEL:
                            label = getString(R.string.cancel);
                            break;
                        case OPTION_KICK:
                            label = getString(R.string.kick);
                            break;
                        case OPTION_BAN:
                            label = getString(R.string.ban);
                            break;
                    }
                    labels[i] = label;
                }

                return labels;
            }
        });

        return v;
    }
}
