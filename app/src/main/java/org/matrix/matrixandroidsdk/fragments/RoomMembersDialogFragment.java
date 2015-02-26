package org.matrix.matrixandroidsdk.fragments;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
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
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.rest.model.User;
import org.matrix.androidsdk.util.JsonUtils;
import org.matrix.matrixandroidsdk.ConsoleApplication;
import org.matrix.matrixandroidsdk.Matrix;
import org.matrix.matrixandroidsdk.R;
import org.matrix.matrixandroidsdk.activity.MemberDetailsActivity;
import org.matrix.matrixandroidsdk.activity.RoomActivity;
import org.matrix.matrixandroidsdk.activity.RoomInfoActivity;
import org.matrix.matrixandroidsdk.adapters.RoomMembersAdapter;

import java.util.ArrayList;
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

    private Handler uiThreadHandler;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mRoomId = getArguments().getString(ARG_ROOM_ID);
        Context context = getActivity().getApplicationContext();
        uiThreadHandler = new Handler();

        mSession = Matrix.getInstance(context).getDefaultSession();
        if (mSession == null) {
            throw new RuntimeException("No MXSession.");
        }

        mSession.getDataHandler().getRoom(mRoomId).addEventListener(new MXEventListener() {
            @Override
            public void onPresenceUpdate(Event event, final User user) {
                // Someone's presence has changed, reprocess the whole list
                uiThreadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mAdapter.saveUser(user);
                        mAdapter.sortMembers();
                        mAdapter.notifyDataSetChanged();
                    }
                });
            }

            @Override
            public void onLiveEvent(final Event event, RoomState roomState) {
                uiThreadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(event.type)) {
                            RoomMember member = JsonUtils.toRoomMember(event.content);
                            User user = mSession.getDataHandler().getStore().getUser(member.getUserId());

                            // TODO add a flag to hide/show the left users.
                            if (member.hasLeft()) {
                                mAdapter.deleteUser(user);
                                mAdapter.remove(member);
                                mAdapter.notifyDataSetChanged();
                            } else {
                                // the user can be a new one
                                boolean mustResort = mAdapter.saveUser(user);
                                mAdapter.updateMember(event.stateKey, JsonUtils.toRoomMember(event.content));

                                if (mustResort) {
                                    mAdapter.sortMembers();
                                    mAdapter.notifyDataSetChanged();
                                }
                            }
                        }
                        else if (Event.EVENT_TYPE_STATE_ROOM_POWER_LEVELS.equals(event.type)) {
                            mAdapter.setPowerLevels(JsonUtils.toPowerLevels(event.content));
                        }
                    }
                });
            }
        });
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog d = super.onCreateDialog(savedInstanceState);
        d.setTitle(getString(R.string.members_list));
        return d;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View v = inflater.inflate(R.layout.fragment_dialog_member_list, container, false);
        mListView = ((ListView)v.findViewById(R.id.listView_members));

        final Room room = mSession.getDataHandler().getRoom(mRoomId);

        mAdapter = new RoomMembersAdapter(getActivity(), R.layout.adapter_item_room_members, room.getLiveState());

        Collection<RoomMember> members = room.getMembers();
        if (members != null) {
            for (RoomMember m : members) {

                // TODO add a setting flag to enable their display
                // by default the
                if (!m.hasLeft()) {
                    mAdapter.add(m);
                    mAdapter.saveUser(mSession.getDataHandler().getStore().getUser(m.getUserId()));
                }
            }
            mAdapter.sortMembers();
        }

        mAdapter.setPowerLevels(room.getLiveState().getPowerLevels());

        mListView.setAdapter(mAdapter);

        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                // Get the member and display the possible actions for them
                final RoomMember roomMember = mAdapter.getItem(position);
                final Activity activity = ConsoleApplication.getCurrentActivity();

                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Intent startRoomInfoIntent = new Intent(activity, MemberDetailsActivity.class);
                        startRoomInfoIntent.putExtra(MemberDetailsActivity.EXTRA_ROOM_ID, mRoomId);
                        startRoomInfoIntent.putExtra(MemberDetailsActivity.EXTRA_USER_ID, roomMember.getUserId());
                        startActivity(startRoomInfoIntent);
                    }
                });

                // dismiss the member list
                RoomMembersDialogFragment.this.dismiss();
            }
        });

        return v;
    }
}
