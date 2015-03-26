/*
 * Copyright 2015 OpenMarket Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.matrix.matrixandroidsdk.activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.webkit.MimeTypeMap;
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
import org.matrix.androidsdk.util.ContentManager;
import org.matrix.matrixandroidsdk.ConsoleApplication;
import org.matrix.matrixandroidsdk.Matrix;
import org.matrix.matrixandroidsdk.MyPresenceManager;
import org.matrix.matrixandroidsdk.R;
import org.matrix.matrixandroidsdk.adapters.AdapterUtils;
import org.matrix.matrixandroidsdk.contacts.ContactsManager;
import org.matrix.matrixandroidsdk.contacts.PIDsRetriever;
import org.matrix.matrixandroidsdk.db.ConsoleLatestChatMessageCache;
import org.matrix.matrixandroidsdk.db.ConsoleMediasCache;
import org.matrix.matrixandroidsdk.services.EventStreamService;
import org.matrix.matrixandroidsdk.util.RageShake;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Member;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;

/**
 * Contains useful functions which are called in multiple activities.
 */
public class CommonActivityUtils {

    public static boolean handleMenuItemSelected(Activity activity, int id) {
        if (id == R.id.action_logout) {
            logout(activity);
            return true;
        } else if (id == R.id.action_disconnect) {
            disconnect(activity);
            return true;
        } else if (id == R.id.action_settings) {
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

        // clear the latest messages cache
        ConsoleLatestChatMessageCache.clearCache(context);

        // clear the preferences
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit();

        // clear credentials
        Matrix.getInstance(context).clearDefaultSessionAndCredentials();

        // reset the contacts
        PIDsRetriever.reset();
        ContactsManager.reset();

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

        MXSession session = Matrix.getInstance(fromActivity).getDefaultSession();
        Room room = session.getDataHandler().getRoom(roomId);

        // do not open a leaving room.
        // it does not make.
        if ((null != room) && (room.isLeaving())) {
            return;
        }

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

        for (Room room : rooms) {
            Collection<RoomMember> members = room.getMembers();

            if (members.size() == 2) {
                for (RoomMember member : members) {
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
            session.createRoom(null, null, RoomState.VISIBILITY_PRIVATE, null, new SimpleApiCallback<String>(fromActivity) {

                @Override
                public void onSuccess(String roomId) {
                    final Room room = session.getDataHandler().getRoom(roomId);

                    room.invite(otherUserId, new SimpleApiCallback<Void>(this) {
                        @Override
                        public void onSuccess(Void info) {
                            CommonActivityUtils.goToRoomPage(room.getRoomId(), fromActivity);

                            callback.onSuccess(null);
                        }

                        @Override
                        public void onMatrixError(MatrixError e) {
                            if (null != callback) {
                                callback.onMatrixError(e);
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
                        callback.onMatrixError(e);
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

    /**
     * Check if the userId format is valid with the matrix standard.
     * It should start with a @ and ends with the home server suffix.
     *
     * @param userId           the userID to check
     * @param homeServerSuffix the home server suffix
     * @return the checked user ID
     */
    public static String checkUserId(String userId, String homeServerSuffix) {
        String res = userId;

        if (res.length() > 0) {
            if (!res.startsWith("@")) {
                res = "@" + res;
            }

            if (res.indexOf(":") < 0) {
                res += homeServerSuffix;
            }
        }

        return res;
    }

    /**
     * Parse an userIDS text into a list.
     *
     * @param userIDsText      the userIDs text.
     * @param homeServerSuffix the home server suffix
     * @return the userIDs list.
     */
    public static ArrayList<String> parseUserIDsList(String userIDsText, String homeServerSuffix) {
        ArrayList<String> userIDsList = new ArrayList<String>();

        if (!TextUtils.isEmpty(userIDsText)) {
            userIDsText = userIDsText.trim();

            if (!TextUtils.isEmpty(userIDsText)) {
                // they are separated by a ;
                String[] splitItems = userIDsText.split(";");

                for (int i = 0; i < splitItems.length; i++) {
                    String item = splitItems[i];

                    // avoid null name
                    if (item.length() > 0) {
                        // add missing @ or home suffix
                        String checkedItem = CommonActivityUtils.checkUserId(item, homeServerSuffix);

                        // not yet added ? -> add it
                        if (userIDsList.indexOf(checkedItem) < 0) {
                            userIDsList.add(checkedItem);
                        }
                    }
                }
            }
        }

        return userIDsList;
    }

    /**
     * @param context the context
     * @param filename the filename
     * @return true if a file named "filename" is stored in the downloads directory
     */
    public static Boolean doesFileExistInDownloads(Context context, String filename) {
        File dstDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);

        if (dstDir != null) {
            dstDir.mkdirs();
        }

        File dstFile = new File(dstDir, filename);
        return dstFile.exists();
    }

    /**
     * Save a media in the downloads directory and offer to open it with a third party application.
     * @param activity the activity
     * @param savedMediaPath the media path
     * @param mimeType the media mime type.
     */
    public static void openMedia(final Activity activity, final String savedMediaPath, final String mimeType) {
        if ((null != activity) && (null != savedMediaPath)) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        File file = new File(savedMediaPath);
                        Intent intent = new Intent();
                        intent.setAction(android.content.Intent.ACTION_VIEW);
                        intent.setDataAndType(Uri.fromFile(file), mimeType);
                        activity.startActivity(intent);
                    } catch (ActivityNotFoundException e) {
                        Toast.makeText(activity, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                    }
                }
            });
        }
    }

    /**
     * Copy a file into a dstPath directory.
     * The output filename can be provided.
     * The output file is not overriden if it is already exist.
     * @param context the context
     * @param sourceFilePath the file source path
     * @param dstDirPath the dst path
     * @param outputFilename optional the output filename
     * @return the downloads file path if the file exists or has been properly saved
     */
    public static String saveFileInto(Context context, String sourceFilePath, String dstDirPath, String outputFilename) {
        // sanity check
        if ((null == sourceFilePath) || (null == dstDirPath)) {
            return null;
        }

        // defines another name for the external media
        String dstFileName;

        // build a filename is not provided
        if (null == outputFilename) {
            // extract the file extension from the uri
            int dotPos = sourceFilePath.lastIndexOf(".");

            String fileExt = "";
            if (dotPos > 0) {
                fileExt = sourceFilePath.substring(dotPos);
            }

            dstFileName = "MatrixConsole_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + fileExt;
        } else {
            dstFileName = outputFilename;
        }

        File dstDir = Environment.getExternalStoragePublicDirectory(dstDirPath);
        if (dstDir != null) {
            dstDir.mkdirs();
        }

        File dstFile = new File(dstDir, dstFileName);

        // Copy source file to destination
        InputStream inputStream = null;
        FileOutputStream outputStream = null;
        try {
            // create only the
            if (!dstFile.exists()) {
                dstFile.createNewFile();

                inputStream = context.openFileInput(sourceFilePath);
                outputStream = new FileOutputStream(dstFile);

                byte[] buffer = new byte[1024 * 10];
                int len;
                while ((len = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, len);
                }
            }
        } catch (Exception e) {
            dstFile = null;
        } finally {
            // Close resources
            try {
                if (inputStream != null) inputStream.close();
                if (outputStream != null) outputStream.close();
            } catch (Exception e) {
            }
        }

        if (null != dstFile) {
            return dstFile.getAbsolutePath();
        } else {
            return null;
        }
    }

    /**
     * Save a media URI into the download directory
     * @param context the context
     * @param path the media path
     * @param filename the filename (optional)
     * @return the downloads file path
     */
    public static String saveMediaIntoDownloads(Context context, String path, String filename) {
        return saveFileInto(context, path, Environment.DIRECTORY_DOWNLOADS, filename);
    }

    /**
     * Save an image URI into the gallery
     * @param context the context.
     * @param imageFilePath the image path to save.
     */
    public static String saveImageIntoGallery(Context context, String imageFilePath) {
        String filePath = saveFileInto(context, imageFilePath, Environment.DIRECTORY_PICTURES, null);

        if (null != filePath) {
            // This broadcasts that there's been a change in the media directory
            context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(new File(filePath))));
        }

        return filePath;
    }
}
