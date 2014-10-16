package org.matrix.matrixandroidsdk;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import org.matrix.matrixandroidsdk.services.EventStreamService;

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
    }
}
