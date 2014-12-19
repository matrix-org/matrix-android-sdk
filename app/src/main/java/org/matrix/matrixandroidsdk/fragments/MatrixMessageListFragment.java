package org.matrix.matrixandroidsdk.fragments;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ListView;

import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.matrixandroidsdk.R;
import org.matrix.matrixandroidsdk.adapters.MessagesAdapter;

/**
 * UI Fragment containing matrix messages for a given room.
 * Contains {@link MatrixMessagesFragment} as a nested fragment to do the work.
 */
public class MatrixMessageListFragment extends Fragment implements MatrixMessagesFragment.MatrixMessagesListener {
    public static final String ARG_ROOM_ID = "org.matrix.matrixandroidsdk.fragments.MatrixMessageListFragment.ARG_ROOM_ID";
    public static final String ARG_LAYOUT_ID = "org.matrix.matrixandroidsdk.fragments.MatrixMessageListFragment.ARG_LAYOUT_ID";

    private static final String TAG_FRAGMENT_MATRIX_MESSAGES = "org.matrix.androidsdk.RoomActivity.TAG_FRAGMENT_MATRIX_MESSAGES";

    public static MatrixMessageListFragment newInstance(String roomId) {
        return newInstance(roomId, R.layout.fragment_matrix_message_list_fragment);
    }

    public static MatrixMessageListFragment newInstance(String roomId, int layoutResId) {
        MatrixMessageListFragment f = new MatrixMessageListFragment();
        Bundle args = new Bundle();
        args.putString(ARG_ROOM_ID, roomId);
        args.putInt(ARG_LAYOUT_ID, layoutResId);
        f.setArguments(args);
        return f;
    }

    public interface MatrixMessageListListener {

    }

    private MatrixMessageListListener mMatrixMessageListListener;
    private MatrixMessagesFragment mMatrixMessagesFragment;
    private MessagesAdapter mAdapter;
    private ListView mMessageListView;
    private Handler mUiHandler;
    private String mRoomId;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        // for dispatching data to add to the adapter we need to be on the main thread
        mUiHandler = new Handler(Looper.getMainLooper());

        Bundle args = getArguments();
        mRoomId = args.getString(ARG_ROOM_ID);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        Bundle args = getArguments();
        View v = inflater.inflate(args.getInt(ARG_LAYOUT_ID), container, false);
        mMessageListView = ((ListView)v.findViewById(R.id.listView_messages));
        if (mAdapter == null) {
            // only init the adapter if it wasn't before, so we can preserve messages/position.
            mAdapter = new MessagesAdapter(getActivity(),
                    R.layout.adapter_item_messages,
                    R.layout.adapter_item_images,
                    R.layout.adapter_item_message_notice,
                    R.layout.adapter_item_message_emote
            );
        }
        mMessageListView.setAdapter(mAdapter);
        mMessageListView.setSelection(0);

        return v;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        Bundle args = getArguments();
        FragmentManager fm = getActivity().getSupportFragmentManager();
        mMatrixMessagesFragment = (MatrixMessagesFragment) fm.findFragmentByTag(TAG_FRAGMENT_MATRIX_MESSAGES);

        if (mMatrixMessagesFragment == null) {
            // this fragment controls all the logic for handling messages / API calls
            mMatrixMessagesFragment = MatrixMessagesFragment.newInstance(args.getString(ARG_ROOM_ID), this);
            fm.beginTransaction().add(mMatrixMessagesFragment, TAG_FRAGMENT_MATRIX_MESSAGES).commit();
        }
        else {
            // Reset the listener because this is not done when the system restores the fragment (newInstance is not called)
            mMatrixMessagesFragment.setMatrixMessagesListener(this);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mMessageListView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                // If we scroll to the top, load more history
                if (firstVisibleItem == 0) {
                    requestHistory();
                }
            }
        });
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mMatrixMessageListListener = (MatrixMessageListListener)activity;
        }
        catch (ClassCastException e) {
            throw new ClassCastException("Activity "+activity+" must implement MatrixMessageListListener");
        }
    }

    public void sendMessage(String body) {
        mMatrixMessagesFragment.sendMessage(body);
    }

    public void sendEmote(String emote) {
        mMatrixMessagesFragment.sendEmote(emote);
    }

    public void requestHistory() {
        final int firstPos = mMessageListView.getFirstVisiblePosition();

        mMatrixMessagesFragment.requestHistory(new SimpleApiCallback<Integer>() {
            @Override
            public void onSuccess(final Integer count) {
                // Scroll the list down to where it was before adding rows to the top
                mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mMessageListView.setSelection(firstPos + count);
                    }
                });
            }
        });
    }

    @Override
    public void onLiveEvent(final Event event, final RoomState roomState) {
        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                mAdapter.add(event, roomState);
            }
        });
    }

    @Override
    public void onBackEvent(final Event event, final RoomState roomState) {
        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                mAdapter.addToFront(event, roomState);
            }
        });
    }

    @Override
    public void onInitialMessagesLoaded() {
        // Jump to the bottom of the list
        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                mMessageListView.setSelection(mAdapter.getCount() - 1);
            }
        });
    }
}
