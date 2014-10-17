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

    public static void logout(Activity context) {
        // kill active connections
        Intent killStreamService = new Intent(context, EventStreamService.class);
        killStreamService.putExtra(EventStreamService.EXTRA_KILL_STREAM, true);
        context.startService(killStreamService);

        // clear credentials
        Matrix.getInstance(context).clearDefaultSession();

        // go to login page
        context.startActivity(new Intent(context, LoginActivity.class));
        context.finish();
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
        alert.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                String value = input.getText().toString().trim();
                listener.onSubmit(value);
            }
        });
        alert.setNegativeButton("Cancel",
            new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    dialog.cancel();
                    listener.onCancelled();
                }
            }
        );
        return alert.create();
    }
}
