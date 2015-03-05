package org.matrix.matrixandroidsdk.activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.preference.PreferenceManager;
import android.widget.EditText;
import android.widget.Toast;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.data.RoomSummary;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.matrixandroidsdk.ConsoleApplication;
import org.matrix.matrixandroidsdk.Matrix;
import org.matrix.matrixandroidsdk.MyPresenceManager;
import org.matrix.matrixandroidsdk.R;
import org.matrix.matrixandroidsdk.adapters.AdapterUtils;
import org.matrix.matrixandroidsdk.db.ConsoleMediasCache;
import org.matrix.matrixandroidsdk.services.EventStreamService;
import org.matrix.matrixandroidsdk.util.RageShake;

import java.lang.reflect.Member;
import java.util.Collection;

/**
 * Contains useful functions which are called in multiple activities.
 */
public class CommonActivityUtils {

    public static boolean handleMenuItemSelected(Activity activity, int id) {
        if (id == R.id.action_logout) {
            logout(activity);
            return true;
        }
        else if (id == R.id.action_disconnect) {
            disconnect(activity);
            return true;
        }
        else if (id == R.id.action_settings) {
            activity.startActivity(new Intent(activity, SettingsActivity.class));
            return true;
        }
        return false;
    }

    public static void logout(Activity context) {
        stopEventStream(context);

        // Publish to the server that we're now offline
        MyPresenceManager.getInstance(context).advertiseOffline();

        // clear the medias cache
        ConsoleMediasCache.clearCache(context);

        // clear the preferences
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit();

        // clear credentials
        Matrix.getInstance(context).clearDefaultSessionAndCredentials();

        // go to login page
        context.startActivity(new Intent(context, LoginActivity.class));
        context.finish();
    }

    public static void disconnect(Activity context) {
        stopEventStream(context);

        // Clear session
        Matrix.getInstance(context).clearDefaultSession();

        context.finish();
    }

    public static void stopEventStream(Activity context) {
        // kill active connections
        Intent killStreamService = new Intent(context, EventStreamService.class);
        killStreamService.putExtra(EventStreamService.EXTRA_STREAM_ACTION, EventStreamService.StreamAction.STOP.ordinal());
        context.startService(killStreamService);
    }

    public static void pauseEventStream(Activity activity) {
        Intent streamService = new Intent(activity, EventStreamService.class);
        streamService.putExtra(EventStreamService.EXTRA_STREAM_ACTION, EventStreamService.StreamAction.PAUSE.ordinal());
        activity.startService(streamService);
    }

    public static void resumeEventStream(Activity activity) {
        Intent streamService = new Intent(activity, EventStreamService.class);
        streamService.putExtra(EventStreamService.EXTRA_STREAM_ACTION, EventStreamService.StreamAction.RESUME.ordinal());
        activity.startService(streamService);
    }

    public interface OnSubmitListener {
        public void onSubmit(String text);
        public void onCancelled();
    }

    public static AlertDialog createEditTextAlert(Activity context, String title, String hint, String initialText, final OnSubmitListener listener) {
        final AlertDialog.Builder alert = new AlertDialog.Builder(context);
        final EditText input = new EditText(context);
        if (hint != null) {
            input.setHint(hint);
        }

        if (initialText != null) {
            input.setText(initialText);
        }
        alert.setTitle(title);
        alert.setView(input);
        alert.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                String value = input.getText().toString().trim();
                listener.onSubmit(value);
            }
        });

        alert.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    dialog.cancel();
                }
            }
        );

        alert.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                listener.onCancelled();
            }
        });

        AlertDialog dialog = alert.create();
        // add the dialog to be rendered in the screenshot
        RageShake.getInstance().registerDialog(dialog);
        
        return dialog;
    }

    public static void goToRoomPage(final String roomId, final Activity fromActivity) {
        fromActivity.runOnUiThread(new Runnable() {
           @Override
               public void run() {
                   // if the activity is not the home activity
                   if (!(fromActivity instanceof HomeActivity)) {
                       // pop to the home activity
                       Intent intent = new Intent(fromActivity, HomeActivity.class);
                       intent.setFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);
                       intent.putExtra(HomeActivity.EXTRA_JUMP_TO_ROOM_ID, roomId);
                       fromActivity.startActivity(intent);
                   } else {
                       // already to the home activity
                       // so just need to open the room activity
                       Intent intent = new Intent(fromActivity, RoomActivity.class);
                       intent.putExtra(RoomActivity.EXTRA_ROOM_ID, roomId);
                       fromActivity.startActivity(intent);
                   }
               }
           }
        );
    }

    public static void goToOneToOneRoom(final String otherUserId, final Activity fromActivity, final ApiCallback<Void> callback) {

        // sanity check
        if (null == otherUserId) {
            return;
        }

        // check first if the 1:1 room already exists
        final MXSession session = Matrix.getInstance(fromActivity.getApplicationContext()).getDefaultSession();

        // sanity check
        if (null == session) {
            return;
        }

        // so, list the existing room, and search the 2 users room with this other users
        String roomId = null;
        Collection<Room> rooms = session.getDataHandler().getStore().getRooms();

        for(Room room : rooms) {
            Collection<RoomMember>members = room.getMembers();

            if (members.size() == 2) {
                for(RoomMember member : members) {
                    if (member.getUserId().equals(otherUserId)) {
                        roomId = room.getRoomId();
                        break;
                    }
                }
            }
        }

        // the room already exists -> switch to it
        if (null != roomId) {
            CommonActivityUtils.goToRoomPage(roomId, fromActivity);

            // everything is ok
            if (null != callback) {
                callback.onSuccess(null);
            }
        } else {

            session.createRoom(null, null, RoomState.VISIBILITY_PRIVATE, null, new SimpleApiCallback<String>() {

                @Override
                public void onSuccess(String roomId) {
                    final Room room = session.getDataHandler().getRoom(roomId);

                    room.invite(otherUserId, new SimpleApiCallback<Void>() {
                        @Override
                        public void onSuccess(Void info) {
                            CommonActivityUtils.goToRoomPage(room.getRoomId(), fromActivity);

                            callback.onSuccess(null);
                        }

                        @Override
                        public void onMatrixError(MatrixError e) {
                            if (null != callback) {
                                callback.onMatrixError( e);
                            }
                        }

                        @Override
                        public void onNetworkError(Exception e) {
                            if (null != callback) {
                                callback.onNetworkError(e);
                            }
                        }

                        @Override
                        public void onUnexpectedError(Exception e) {
                            if (null != callback) {
                                callback.onUnexpectedError(e);
                            }
                        }

                    });
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    if (null != callback) {
                        callback.onMatrixError( e);
                    }
                }

                @Override
                public void onNetworkError(Exception e) {
                    if (null != callback) {
                        callback.onNetworkError(e);
                    }
                }

                @Override
                public void onUnexpectedError(Exception e) {
                    if (null != callback) {
                        callback.onUnexpectedError(e);
                    }
                }
            });
        }
    }
}
