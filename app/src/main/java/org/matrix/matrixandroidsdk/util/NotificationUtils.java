package org.matrix.matrixandroidsdk.util;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.text.TextUtils;

import org.matrix.matrixandroidsdk.R;
import org.matrix.matrixandroidsdk.activity.LockScreenActivity;
import org.matrix.matrixandroidsdk.activity.RoomActivity;

/**
 * Util class for creating notifications.
 */
public class NotificationUtils {

    public static final String QUICK_LAUNCH_ACTION = "org.matrix.matrixandroidsdk.services.EventStreamService.QUICK_LAUNCH_ACTION";
    public static final String TAP_TO_VIEW_ACTION = "org.matrix.matrixandroidsdk.services.EventStreamService.TAP_TO_VIEW_ACTION";

    public static Notification buildMessageNotification(
            Context context, String from, String body, String roomId, String roomName,
            boolean shouldPlaySound) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context);
        builder.setWhen(System.currentTimeMillis());
        builder.setContentTitle(from);
        builder.setContentText(body);
        builder.setAutoCancel(true);
        builder.setSmallIcon(R.drawable.ic_menu_small_matrix);

        String name = ": ";
        if(!TextUtils.isEmpty(roomName)) {
            name = " (" + roomName + "): ";
        }

        builder.setTicker(from + name + body);

        {
            // Build the pending intent for when the notification is clicked
            Intent roomIntent = new Intent(context, RoomActivity.class);
            roomIntent.putExtra(RoomActivity.EXTRA_ROOM_ID, roomId);
            // Recreate the back stack
            TaskStackBuilder stackBuilder = TaskStackBuilder.create(context)
                    .addParentStack(RoomActivity.class)
                    .addNextIntent(roomIntent);

            builder.setContentIntent(stackBuilder.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT));
        }

        // display the message with more than 1 lines when the device supports it
        NotificationCompat.BigTextStyle textStyle = new NotificationCompat.BigTextStyle();
        textStyle.bigText(from + ":" + body);
        builder.setStyle(textStyle);

        // do not offer to quick respond if the user did not dismiss the previous one
        if (!LockScreenActivity.isDisplayingALockScreenActivity()) {
            // offer to type a quick answer (i.e. without launching the application)
            Intent quickReplyIntent = new Intent(context, LockScreenActivity.class);
            quickReplyIntent.putExtra(LockScreenActivity.EXTRA_ROOM_ID, roomId);
            quickReplyIntent.putExtra(LockScreenActivity.EXTRA_SENDER_NAME, from);
            quickReplyIntent.putExtra(LockScreenActivity.EXTRA_MESSAGE_BODY, body);
            // the action must be unique else the parameters are ignored
            quickReplyIntent.setAction(QUICK_LAUNCH_ACTION + ((int) (System.currentTimeMillis())));
            PendingIntent pIntent = PendingIntent.getActivity(context, 0, quickReplyIntent, 0);
            builder.addAction(
                    R.drawable.ic_menu_edit,
                    context.getString(R.string.action_quick_reply),
                    pIntent);

            // Build the pending intent for when the notification is clicked
            Intent roomIntentTap = new Intent(context, RoomActivity.class);
            roomIntentTap.putExtra(RoomActivity.EXTRA_ROOM_ID, roomId);
            // the action must be unique else the parameters are ignored
            roomIntentTap.setAction(TAP_TO_VIEW_ACTION + ((int) (System.currentTimeMillis())));
            // Recreate the back stack
            TaskStackBuilder stackBuildertap = TaskStackBuilder.create(context)
                    .addParentStack(RoomActivity.class)
                    .addNextIntent(roomIntentTap);
            builder.addAction(
                    R.drawable.ic_menu_start_conversation,
                    context.getString(R.string.action_open),
                    stackBuildertap.getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT));
        }

        Notification n = builder.build();
        n.flags |= Notification.FLAG_SHOW_LIGHTS;
        n.defaults |= Notification.DEFAULT_LIGHTS;

        if (shouldPlaySound) {
            n.defaults |= Notification.DEFAULT_SOUND;
        }
        return n;
    }

    private NotificationUtils() {}
}
