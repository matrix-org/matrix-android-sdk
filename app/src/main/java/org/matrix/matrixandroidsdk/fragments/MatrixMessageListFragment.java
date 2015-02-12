package org.matrix.matrixandroidsdk.fragments;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
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
import org.matrix.androidsdk.rest.model.ImageMessage;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.Message;
import org.matrix.androidsdk.util.JsonUtils;
import org.matrix.matrixandroidsdk.Matrix;
import org.matrix.matrixandroidsdk.R;
import org.matrix.matrixandroidsdk.ToastErrorHandler;
import org.matrix.matrixandroidsdk.adapters.MessageRow;
import org.matrix.matrixandroidsdk.adapters.MessagesAdapter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * UI Fragment containing matrix messages for a given room.
 * Contains {@link MatrixMessagesFragment} as a nested fragment to do the work.
 */
public class MatrixMessageListFragment extends Fragment implements MatrixMessagesFragment.MatrixMessagesListener {

    public static interface MatrixMessageListFragmentListener {
        /**
         * Called when the first batch of messages is loaded.
         */
        public void onInitialMessagesLoaded();
    }

    public static final String ARG_ROOM_ID = "org.matrix.matrixandroidsdk.fragments.MatrixMessageListFragment.ARG_ROOM_ID";
    public static final String ARG_LAYOUT_ID = "org.matrix.matrixandroidsdk.fragments.MatrixMessageListFragment.ARG_LAYOUT_ID";

    private static final String TAG_FRAGMENT_MATRIX_MESSAGES = "org.matrix.androidsdk.RoomActivity.TAG_FRAGMENT_MATRIX_MESSAGES";

    // listener to warn activity that the initial sync is done
    private MatrixMessageListFragmentListener mMatrixMessageListFragmentListener = null;

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

    private MatrixMessagesFragment mMatrixMessagesFragment;
    private MessagesAdapter mAdapter;
    private ListView mMessageListView;
    private Handler mUiHandler;
    private MXSession mSession;
    private Room mRoom;

    private MXEventListener mEventListener = new MXEventListener() {
        @Override
        public void onLiveEvent(final Event event, RoomState roomState) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                   if (event.type.equals(Event.EVENT_TYPE_TYPING)) {
                       mAdapter.setTypingUsers(mRoom.getTypingUsers());
                   }
                }
            });
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        // for dispatching data to add to the adapter we need to be on the main thread
        mUiHandler = new Handler(Looper.getMainLooper());

        mSession = Matrix.getInstance(getActivity()).getDefaultSession();

        Bundle args = getArguments();
        String roomId = args.getString(ARG_ROOM_ID);
        mRoom = mSession.getDataHandler().getRoom(roomId);
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
        mAdapter.setTypingUsers(mRoom.getTypingUsers());

        mRoom.addEventListener(mEventListener);

        mMessageListView.setAdapter(mAdapter);
        mMessageListView.setSelection(0);
        mMessageListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            private static final int OPTION_CANCEL = 0;
            private static final int OPTION_RESEND = 1;
            private static final int OPTION_REDACT = 2;

            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                final MessageRow messageRow = mAdapter.getItem(position);
                final List<Integer> options = new ArrayList<Integer>();
                if (messageRow.getSentState() == MessageRow.SentState.NOT_SENT) {
                    options.add(OPTION_RESEND);
                } else if (messageRow.getSentState() == MessageRow.SentState.SENT) {
                    options.add(OPTION_REDACT);
                }

                if (options.size() != 0) {
                    options.add(OPTION_CANCEL);
                    new AlertDialog.Builder(getActivity())
                            .setItems(buildOptionLabels(options), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    switch (options.get(which)) {
                                        case OPTION_CANCEL:
                                            dialog.cancel();
                                            break;
                                        case OPTION_RESEND:
                                            Message message = JsonUtils.toMessage(messageRow.getEvent().content);
                                            send(message);
                                            break;
                                        case OPTION_REDACT:
                                            redactEvent(messageRow.getEvent().eventId);
                                            break;
                                    }
                                }
                            })
                            .create()
                            .show();

                    return true;
                }

                return false;
            }

            private String[] buildOptionLabels(List<Integer> options) {
                String[] labels = new String[options.size()];
                for (int i = 0; i < options.size(); i++) {
                    String label = "";
                    switch (options.get(i)) {
                        case OPTION_CANCEL:
                            label = getString(R.string.cancel);
                            break;
                        case OPTION_RESEND:
                            label = getString(R.string.resend);
                            break;
                        case OPTION_REDACT:
                            label = getString(R.string.redact);
                            break;
                    }
                    labels[i] = label;
                }

                return labels;
            }
        });

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
    public void onDestroy() {
        super.onDestroy();
        mRoom.removeEventListener(mEventListener);
    }

    public void sendTextMessage(String body) {
        sendMessage(Message.MSGTYPE_TEXT, body);
    }

    private void sendMessage(String msgType, String body) {
        Message message = new Message();
        message.msgtype = msgType;
        message.body = body;
        send(message);
    }

    public void sendImage(ImageMessage imageMessage) {
        send(imageMessage);
    }

    public void sendEmote(String emote) {
        sendMessage(Message.MSGTYPE_EMOTE, emote);
    }

    private void send(Message message) {
        Event dummyEvent = new Event();
        dummyEvent.type = Event.EVENT_TYPE_MESSAGE;
        dummyEvent.content = JsonUtils.toJson(message);
        dummyEvent.originServerTs = System.currentTimeMillis();
        dummyEvent.userId = mSession.getCredentials().userId;

        final MessageRow tmpRow = new MessageRow(dummyEvent, mRoom.getLiveState());
        tmpRow.setSentState(MessageRow.SentState.SENDING);

        mMatrixMessagesFragment.send(message, new ApiCallback<Event>() {
            @Override
            public void onSuccess(Event info) {
                mAdapter.remove(tmpRow);
                mAdapter.add(info, mRoom.getLiveState());
                // NotifyOnChange has been disabled to avoid useless refreshes
                mAdapter.notifyDataSetChanged();
            }

            private void markError() {
                tmpRow.setSentState(MessageRow.SentState.NOT_SENT);
                mAdapter.notifyDataSetChanged();
            }

            @Override
            public void onNetworkError(Exception e) {
                markError();
                Toast.makeText(getActivity(), "Unable to send message. Connection error.", Toast.LENGTH_LONG).show();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                markError();
                Toast.makeText(getActivity(), "Unable to send message. " + e.error + ".", Toast.LENGTH_LONG).show();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                markError();
                Toast.makeText(getActivity(), "Unable to send message.", Toast.LENGTH_LONG).show();
            }
        });

        mAdapter.add(tmpRow);
        // NotifyOnChange has been disabled to avoid useless refreshes
        mAdapter.notifyDataSetChanged();
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
                        // refresh the list only at the end of the sync
                        // else the one by one message refresh gives a weird UX
                        // The application is almost frozen during the
                        mAdapter.notifyDataSetChanged();
                        mMessageListView.setSelection(firstPos + count);
                    }
                });
            }
        });
    }

    private void redactEvent(String eventId) {
        // Do nothing on success, the event will be hidden when the redaction event comes down the event stream
        mMatrixMessagesFragment.redact(eventId,
                new SimpleApiCallback<Event>(new ToastErrorHandler(getActivity(), "Couldn't redact")));
    }

    @Override
    public void onLiveEvent(final Event event, final RoomState roomState) {
        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                if (Event.EVENT_TYPE_REDACTION.equals(event.type)) {
                    mAdapter.removeEventById(event.redacts);
                }
                else if (!Event.EVENT_TYPE_TYPING.equals(event.type)) {
                    mAdapter.add(event, roomState);
                }
                mAdapter.notifyDataSetChanged();
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
                // refresh the list only at the end of the sync
                // else the one by one message refresh gives a weird UX
                // The application is almost frozen during the
                mAdapter.notifyDataSetChanged();
                mMessageListView.setSelection(mAdapter.getCount() - 1);

                if (null != mMatrixMessageListFragmentListener) {
                    mMatrixMessageListFragmentListener.onInitialMessagesLoaded();
                }
            }
        });
    }

    /**
     * Set the listener which will be informed of matrix messages. This setter is provided so either
     * a Fragment or an Activity can directly receive callbacks.
     * @param listener the listener for this fragment
     */
    public void setMatrixMessageListFragmentListener(MatrixMessageListFragmentListener listener) {
        mMatrixMessageListFragmentListener = listener;
    }
}
