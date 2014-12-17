package org.matrix.matrixandroidsdk;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.widget.EditText;

import org.matrix.matrixandroidsdk.services.EventStreamService;

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
            return true;
        }
        return false;
    }

    public static void logout(Activity context) {
        stopEventStream(context);

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

    public static AlertDialog createEditTextAlert(Activity context, String title, String hint, final OnSubmitListener listener) {
        final AlertDialog.Builder alert = new AlertDialog.Builder(context);
        final EditText input = new EditText(context);
        if (hint != null) {
            input.setHint(hint);
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
                    listener.onCancelled();
                }
            }
        );
        return alert.create();
    }
}
