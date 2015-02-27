package org.matrix.matrixandroidsdk.activity;

import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.media.RemoteControlClient;
import android.net.Uri;
import android.os.MemoryFile;
import android.provider.MediaStore;
import android.support.v4.app.FragmentManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.MyUser;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.ContentResponse;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.ImageInfo;
import org.matrix.androidsdk.rest.model.ImageMessage;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.util.ContentManager;
import org.matrix.androidsdk.util.JsonUtils;
import org.matrix.matrixandroidsdk.ErrorListener;
import org.matrix.matrixandroidsdk.Matrix;
import org.matrix.matrixandroidsdk.MyPresenceManager;
import org.matrix.matrixandroidsdk.R;
import org.matrix.matrixandroidsdk.ViewedRoomTracker;
import org.matrix.matrixandroidsdk.adapters.AdapterUtils;
import org.matrix.matrixandroidsdk.fragments.MatrixMessageListFragment;
import org.matrix.matrixandroidsdk.fragments.RoomMembersDialogFragment;
import org.matrix.matrixandroidsdk.services.EventStreamService;
import org.matrix.matrixandroidsdk.util.ResourceUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Displays a single room with messages.
 */
public class RoomActivity extends MXCActionBarActivity implements MatrixMessageListFragment.MatrixMessageListFragmentListener {

    public static final String EXTRA_ROOM_ID = "org.matrix.matrixandroidsdk.RoomActivity.EXTRA_ROOM_ID";

    private static final String TAG_FRAGMENT_MATRIX_MESSAGE_LIST = "org.matrix.androidsdk.RoomActivity.TAG_FRAGMENT_MATRIX_MESSAGE_LIST";
    private static final String TAG_FRAGMENT_MEMBERS_DIALOG = "org.matrix.androidsdk.RoomActivity.TAG_FRAGMENT_MEMBERS_DIALOG";
    private static final String LOG_TAG = "RoomActivity";
    private static final int TYPING_TIMEOUT_MS = 10000;

    // defines the command line operations
    // the user can write theses messages to perform some room events
    private static final String CMD_CHANGE_DISPLAY_NAME = "/nick";
    private static final String CMD_EMOTE = "/me";
    private static final String CMD_JOIN_ROOM = "/join";
    private static final String CMD_KICK_USER = "/kick";
    private static final String CMD_BAN_USER = "/ban";
    private static final String CMD_UNBAN_USER = "/unban";
    private static final String CMD_SET_USER_POWER_LEVEL = "/op";
    private static final String CMD_RESET_USER_POWER_LEVEL = "/deop";

    private static final int REQUEST_IMAGE = 0;

    private MatrixMessageListFragment mMatrixMessageListFragment;
    private MXSession mSession;
    private Room mRoom;

    // typing event management
    private Timer mTypingTimer = null;
    private TimerTask mTypingTimerTask;
    private long  mLastTypingDate = 0;

    private MXEventListener mEventListener = new MXEventListener() {
        @Override
        public void onLiveEvent(final Event event, RoomState roomState) {
            RoomActivity.this.runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    // The various events that could possibly change the room title
                    if (Event.EVENT_TYPE_STATE_ROOM_NAME.equals(event.type)
                            || Event.EVENT_TYPE_STATE_ROOM_ALIASES.equals(event.type)
                            || Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(event.type)) {
                        setTitle(mRoom.getName(mSession.getCredentials().userId));
                    }
                    else if (Event.EVENT_TYPE_STATE_ROOM_TOPIC.equals(event.type)) {
                        Log.e(LOG_TAG, "Updating room topic.");
                        RoomState roomState = JsonUtils.toRoomState(event.content);
                        setTopic(roomState.topic);
                    }
                }
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_room);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        Intent intent = getIntent();
        if (!intent.hasExtra(EXTRA_ROOM_ID)) {
            Log.e(LOG_TAG, "No room ID extra.");
            finish();
            return;
        }

        // the user has tapped on the "View" notification button
        if ((null != intent.getAction()) && (intent.getAction().startsWith(EventStreamService.TAP_TO_VIEW_ACTION))) {
            // remove any pending notifications
            NotificationManager notificationsManager = (NotificationManager) this.getSystemService(Context.NOTIFICATION_SERVICE);
            notificationsManager.cancelAll();
        }


        String roomId = intent.getStringExtra(EXTRA_ROOM_ID);
        Log.i(LOG_TAG, "Displaying "+roomId);

        findViewById(R.id.button_send).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                EditText editText = (EditText)findViewById(R.id.editText_messageBox);
                String body = editText.getText().toString();
                sendMessage(body);
                editText.setText("");
            }
        });

        findViewById(R.id.button_more).setOnClickListener(new View.OnClickListener() {
            private static final int OPTION_CANCEL = 0;
            private static final int OPTION_ATTACH_IMAGE = 1;
            private static final int OPTION_INVITE = 2;

            @Override
            public void onClick(View v) {
                final int[] options = new int[] {OPTION_ATTACH_IMAGE, OPTION_INVITE, OPTION_CANCEL};

                new AlertDialog.Builder(RoomActivity.this)
                        .setItems(buildOptionLabels(options), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                switch (options[which]) {
                                    case OPTION_CANCEL:
                                        dialog.cancel();
                                        break;
                                    case OPTION_ATTACH_IMAGE:
                                        Intent fileIntent = new Intent(Intent.ACTION_PICK);
                                        fileIntent.setType("image/*");
                                        startActivityForResult(fileIntent, REQUEST_IMAGE);
                                        break;
                                    case OPTION_INVITE: {
                                            final MXSession session = Matrix.getInstance(getApplicationContext()).getDefaultSession();
                                            if (session != null) {
                                                AlertDialog alert = CommonActivityUtils.createEditTextAlert(RoomActivity.this, "Invite User", "@localpart:domain", null, new CommonActivityUtils.OnSubmitListener() {
                                                    @Override
                                                    public void onSubmit(final String text) {
                                                        if (TextUtils.isEmpty(text)) {
                                                            return;
                                                        }
                                                        if (!text.startsWith("@") || !text.contains(":")) {
                                                            Toast.makeText(getApplicationContext(), "User must be of the form '@name:example.com'.", Toast.LENGTH_LONG).show();
                                                            return;
                                                        }
                                                        mRoom.invite(text.trim(), new SimpleApiCallback<Void>() {
                                                            @Override
                                                            public void onSuccess(Void info) {
                                                                Toast.makeText(getApplicationContext(), "Sent invite to " + text.trim() + ".", Toast.LENGTH_LONG).show();
                                                            }
                                                        });
                                                    }

                                                    @Override
                                                    public void onCancelled() {

                                                    }
                                                });
                                                alert.show();
                                            }
                                        }

                                        break;
                                }
                            }
                        })
                        .show();
            }

            private String[] buildOptionLabels(int[] options) {
                String[] labels = new String[options.length];
                for (int i = 0; i < options.length; i++) {
                    String label = "";
                    switch (options[i]) {
                        case OPTION_CANCEL:
                            label = getString(R.string.cancel);
                            break;
                        case OPTION_ATTACH_IMAGE:
                            label = getString(R.string.option_attach_image);
                            break;
                        case OPTION_INVITE:
                            label = getString(R.string.option_invite);
                            break;
                    }
                    labels[i] = label;
                }

                return labels;
            }
        });

        final EditText editText = (EditText)findViewById(R.id.editText_messageBox);
        editText.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(android.text.Editable s) {
                handleTypingNotification(editText.getText().length() != 0);
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });

        // make sure we're logged in.
        mSession = Matrix.getInstance(getApplicationContext()).getDefaultSession();
        if (mSession == null) {
            Log.e(LOG_TAG, "No MXSession.");
            finish();
            return;
        }

        mRoom = mSession.getDataHandler().getRoom(roomId);

        FragmentManager fm = getSupportFragmentManager();
        mMatrixMessageListFragment = (MatrixMessageListFragment) fm.findFragmentByTag(TAG_FRAGMENT_MATRIX_MESSAGE_LIST);

        if (mMatrixMessageListFragment == null) {
            // this fragment displays messages and handles all message logic
            mMatrixMessageListFragment = MatrixMessageListFragment.newInstance(mRoom.getRoomId());
            fm.beginTransaction().add(R.id.anchor_fragment_messages, mMatrixMessageListFragment, TAG_FRAGMENT_MATRIX_MESSAGE_LIST).commit();
        }

        // set general room information
        setTitle(mRoom.getName(mSession.getCredentials().userId));
        setTopic(mRoom.getTopic());

        // warn when the initial sync is performed
        // The events listeners are not triggered until the room initial sync is done.
        // So, the room name might be invalid until this first sync.
        mMatrixMessageListFragment.setMatrixMessageListFragmentListener(this);

        // listen for room name or topic changes
        mRoom.addEventListener(mEventListener);

        // The error listener needs the current activity
        mSession.setFailureCallback(new ErrorListener(this));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mRoom.removeEventListener(mEventListener);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ViewedRoomTracker.getInstance().setViewedRoomId(null);
        MyPresenceManager.getInstance(this).advertiseUnavailableAfterDelay();
        mMatrixMessageListFragment.setMatrixMessageListFragmentListener(null);
        // warn other member that the typing is ended
        cancelTypingNotification();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ViewedRoomTracker.getInstance().setViewedRoomId(mRoom.getRoomId());
        MyPresenceManager.getInstance(this).advertiseOnline();

        // warn when the initial sync is performed
        // The events listeners are not triggered until the room initial sync is done.
        // So, the room name might be invalid until this first sync.
        mMatrixMessageListFragment.setMatrixMessageListFragmentListener(this);

        EventStreamService.cancelNotificationsForRoomId(mRoom.getRoomId());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.room, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (CommonActivityUtils.handleMenuItemSelected(this, id)) {
            return true;
        }

        if (id == R.id.action_leave) {
            MXSession session = Matrix.getInstance(getApplicationContext()).getDefaultSession();
            if (session != null) {
                mRoom.leave(new SimpleApiCallback<Void>() {

                    @Override
                    public void onSuccess(Void info) {
                        RoomActivity.this.finish();
                    }
                });
            }
        }
        else if (id == R.id.action_members) {
            FragmentManager fm = getSupportFragmentManager();

            RoomMembersDialogFragment fragment = (RoomMembersDialogFragment) fm.findFragmentByTag(TAG_FRAGMENT_MEMBERS_DIALOG);
            if (fragment != null) {
                fragment.dismissAllowingStateLoss();
            }
            fragment = RoomMembersDialogFragment.newInstance(mRoom.getRoomId());
            fragment.show(fm, TAG_FRAGMENT_MEMBERS_DIALOG);
        }
        else if (id == R.id.action_info) {
            Intent startRoomInfoIntent = new Intent(this, RoomInfoActivity.class);
            startRoomInfoIntent.putExtra(EXTRA_ROOM_ID, mRoom.getRoomId());
            startActivity(startRoomInfoIntent);
        }
        return super.onOptionsItemSelected(item);
    }

    private void setTopic(String topic) {
        if (null !=  this.getActionBar()) {
            this.getActionBar().setSubtitle(topic);
        }
    }


    /**
     * check if the text message is an IRC command.
     * If it is an IRC command, it is executed
     * @param body
     * @return true if body defines an IRC command
     */
    private boolean manageIRCCommand(String body) {
        boolean isIRCCmd = false;

        // check if it has the IRC marker
        if ((null != body) && (body.startsWith("/"))) {
            MXSession session = Matrix.getInstance(this).getDefaultSession();

            final ApiCallback callback = new SimpleApiCallback<Void>() {
                @Override
                public void onMatrixError(MatrixError e) {
                    if (MatrixError.FORBIDDEN.equals(e.errcode)) {
                        Toast.makeText(RoomActivity.this, e.error, Toast.LENGTH_LONG).show();
                    }
                }
            };

            if (body.startsWith(CMD_CHANGE_DISPLAY_NAME)) {
                isIRCCmd = true;

                String newDisplayname = body.substring(CMD_CHANGE_DISPLAY_NAME.length()).trim();

                if (newDisplayname.length() > 0) {
                    MyUser myUser = session.getMyUser();

                    myUser.updateDisplayName(newDisplayname, callback);
                }
            } else if (body.startsWith(CMD_EMOTE)) {
                isIRCCmd = true;

                String message = body.substring(CMD_EMOTE.length()).trim();

                if (message.length() > 0) {
                    mMatrixMessageListFragment.sendEmote(message);
                }
            } else if (body.startsWith(CMD_JOIN_ROOM)) {
                isIRCCmd = true;

                String roomAlias = body.substring(CMD_JOIN_ROOM.length()).trim();

                if (roomAlias.length() > 0) {
                    session.joinRoomByRoomAlias(roomAlias,new SimpleApiCallback<String>() {

                        @Override
                        public void onSuccess(String roomId) {
                            if (null != roomId) {
                                CommonActivityUtils.goToRoomPage(roomId, RoomActivity.this);
                            }
                        }
                    });
                }
            } else if (body.startsWith(CMD_KICK_USER)) {
                isIRCCmd = true;

                String params = body.substring(CMD_KICK_USER.length()).trim();
                String[] paramsList = params.split(" ");

                String kickedUserID = paramsList[0];

                if (kickedUserID.length() > 0) {
                    mRoom.kick(kickedUserID, callback);
                }
            } else if (body.startsWith(CMD_BAN_USER)) {
                isIRCCmd = true;

                String params = body.substring(CMD_BAN_USER.length()).trim();
                String[] paramsList = params.split(" ");

                String bannedUserID = paramsList[0];
                String reason = params.substring(bannedUserID.length()).trim();

                if (bannedUserID.length() > 0) {
                    mRoom.ban(bannedUserID, reason, callback);
                }
            } else if (body.startsWith(CMD_UNBAN_USER)) {
                isIRCCmd = true;

                String params = body.substring(CMD_UNBAN_USER.length()).trim();
                String[] paramsList = params.split(" ");

                String unbannedUserID = paramsList[0];

                if (unbannedUserID.length() > 0) {
                    mRoom.unban(unbannedUserID, callback);
                }
            } else if (body.startsWith(CMD_SET_USER_POWER_LEVEL)) {
                isIRCCmd = true;

                String params = body.substring(CMD_SET_USER_POWER_LEVEL.length()).trim();
                String[] paramsList = params.split(" ");

                String userID = paramsList[0];
                String powerLevelsAsString  = params.substring(userID.length()).trim();

                try {
                    if ((userID.length() > 0) && (powerLevelsAsString.length() > 0)) {
                        mRoom.updateUserPowerLevels(userID, Integer.parseInt(powerLevelsAsString), callback);
                    }
                } catch(Exception e){

                }
            } else if (body.startsWith(CMD_RESET_USER_POWER_LEVEL)) {
                isIRCCmd = true;

                String params = body.substring(CMD_RESET_USER_POWER_LEVEL.length()).trim();
                String[] paramsList = params.split(" ");

                String userID = paramsList[0];

                if (userID.length() > 0) {
                    mRoom.updateUserPowerLevels(userID, 0, callback);
                }
            }
        }

        return isIRCCmd;
    }


    private void sendMessage(String body) {
        if (!TextUtils.isEmpty(body)) {
            if (!manageIRCCommand(body)) {
                mMatrixMessageListFragment.sendTextMessage(body);
            }
        }
    }

    /**
     * upload an image content.
     * It might be triggered from a media selection : imageUri is used to compute thumbnails.
     * Or, it could have been called to resend an image.
     * @param imageUrl the image Uri
     * @param mimeType the image mine type
     * @param retriedMessage the imagemessage to resend
     */
    public void uploadImageContent(final String imageUrl, final String mimeType, final ImageMessage retriedMessage) {
        final ProgressDialog progressDialog = ProgressDialog.show(this, null, getString(R.string.message_uploading), true);

        FileInputStream imageStream = null;

        try {
            Uri uri = Uri.parse(imageUrl);
            String filename = uri.getPath();
            imageStream = new FileInputStream (new File(filename));

        } catch (Exception e) {

        }

        final String uploadId = mSession.getContentManager().uploadContent(imageStream, mimeType, new ContentManager.UploadCallback() {

            @Override
            public void onUploadProgress(String anUploadId, int percentageProgress) {
                progressDialog.setMessage(getString(R.string.message_uploading) + " (" + percentageProgress + "%)");
            }

            @Override
            public void onUploadComplete(ContentResponse uploadResponse) {
                // Build the image message
                ImageMessage message = new ImageMessage();

                if ((null != uploadResponse) && (null != uploadResponse.contentUri)) {
                    // a thumbnail url could have been set if the upload has failed
                    // it is a file URL one but it must not be sent
                    message.thumbnailUrl = null;
                    message.url = uploadResponse.contentUri;

                    // try to extract the image size
                    try {
                        Uri uri = Uri.parse(imageUrl);
                        String filename = uri.getPath();

                        File file = new File(filename);

                        try {
                            ExifInterface exifMedia = new ExifInterface(filename);
                            String width = exifMedia.getAttribute(ExifInterface.TAG_IMAGE_WIDTH);
                            String height = exifMedia.getAttribute(ExifInterface.TAG_IMAGE_LENGTH);

                            if ((null != width) && (null != height)) {
                                ImageInfo imageInfo = new ImageInfo();

                                imageInfo.w = Integer.parseInt(width);
                                imageInfo.h = Integer.parseInt(height);
                                imageInfo.mimetype = mimeType;
                                imageInfo.size = file.length();

                                message.info = imageInfo;
                            }
                        } catch (Exception e) {
                        }

                        // TODO the file should not be deleted
                        // it should be used to avoid downloading high res pict
                        file.delete();

                    } catch (Exception e) {

                    }

                    Log.d(LOG_TAG, "Uploaded to " + uploadResponse.contentUri);
                } else {
                    Log.d(LOG_TAG, "Failed to upload");

                    // try to resend an image
                    if (null != retriedMessage) {
                        message.url = retriedMessage.url;
                        message.thumbnailUrl = retriedMessage.thumbnailUrl;
                    } else {

                        try {
                            message.url = imageUrl;

                            Bitmap fullSizeBitmap = AdapterUtils.bitmapForUrl(message.url, RoomActivity.this);

                            double thumbnailWidth = fullSizeBitmap.getWidth();
                            double thumbnailHeight = fullSizeBitmap.getHeight();

                            // the thumbnails are reduced to a 256 * 256 pixels
                            if (thumbnailWidth > thumbnailHeight) {
                                thumbnailWidth = 256.0;
                                thumbnailHeight = thumbnailWidth * fullSizeBitmap.getHeight() / fullSizeBitmap.getWidth();
                            } else {
                                thumbnailHeight = 256.0;
                                thumbnailWidth = thumbnailHeight * fullSizeBitmap.getWidth() / fullSizeBitmap.getHeight();
                            }

                            Bitmap thumbnail = Bitmap.createScaledBitmap(fullSizeBitmap, (int) thumbnailWidth, (int) thumbnailHeight, false);
                            message.thumbnailUrl = AdapterUtils.saveBitmap(thumbnail, RoomActivity.this, null);

                            // save memory consumption
                            fullSizeBitmap.recycle();
                            fullSizeBitmap = null;
                            System.gc();

                        } catch (Exception e) {
                            // really fail to upload the image...
                        }
                    }
                }

                message.info = new ImageInfo();
                message.info.mimetype = mimeType;
                // message to display in the summary recents
                message.body = "Image";

                // warn the user that the media upload fails
                if ((null == uploadResponse) || (null == uploadResponse.contentUri)) {
                    Toast.makeText(RoomActivity.this,
                            getString(R.string.message_failed_to_upload),
                            Toast.LENGTH_LONG).show();
                }

                // sanity check
                if (message.url != null) {
                    mMatrixMessageListFragment.sendImage(message);
                }
                progressDialog.dismiss();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_IMAGE) {
                final Uri imageUri = data.getData();
                ResourceUtils.Resource resource = ResourceUtils.openResource(this, imageUri);
                if (resource == null) {
                    Toast.makeText(RoomActivity.this,
                            getString(R.string.message_failed_to_upload),
                            Toast.LENGTH_LONG).show();
                    return;
                }

                // extract the rotation angle
                // to manage exif rotation
                int rotationAngle = -1;

                int orientation = AdapterUtils.getOrientationForBitmap(this, imageUri);

                if (ExifInterface.ORIENTATION_ROTATE_90 == orientation) {
                    orientation = 90;
                } else if (ExifInterface.ORIENTATION_ROTATE_180 == orientation) {
                    orientation = 180 ;
                } else if (ExifInterface.ORIENTATION_ROTATE_270 == orientation) {
                    orientation = 270;
                }

                // save the file in the filesystem
                String imageUrl =  AdapterUtils.saveMedia(resource.contentStream, RoomActivity.this, null);
                String mimeType = resource.mimeType;

                try {
                    resource.contentStream.close();
                } catch(Exception e) {
                }

                // check if the image orientation has been found
                // seems that galery images orientation is the thumbnail one
                if (-1 == rotationAngle) {

                    try {
                        String[] orientationColumn = {MediaStore.Images.Media.ORIENTATION};
                        Cursor cur = managedQuery(imageUri, orientationColumn, null, null, null);
                        if (cur != null && cur.moveToFirst()) {
                            orientation = cur.getInt(cur.getColumnIndex(orientationColumn[0]));
                        }
                    } catch (Exception e) {
                    }
                }

                // the image has a rotation to apply or the mimetype is unknown
                if ((orientation > 0) || (null == mimeType) || (mimeType.equals("image/*"))) {
                    try {
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inPreferredConfig = Bitmap.Config.ARGB_8888;

                        resource = ResourceUtils.openResource(this, imageUri);

                        Bitmap bitmap = BitmapFactory.decodeStream(resource.contentStream, null, options);

                        if (null != bitmap) {
                            Uri uri = Uri.parse(imageUrl);

                            // there is a rotation to apply
                            if (orientation > 0) {
                                android.graphics.Matrix bitmapMatrix = new android.graphics.Matrix();
                                bitmapMatrix.postRotate(orientation);
                                
								// the rotation could fail because there is no more available memory
                                try {
                                    Bitmap transformedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), bitmapMatrix, false);
                                    AdapterUtils.saveBitmap(transformedBitmap, RoomActivity.this, uri.getPath());
                                    transformedBitmap.recycle();
                                    transformedBitmap = null;
                                } catch (OutOfMemoryError ex) {
                                }
                            } else {
                                AdapterUtils.saveBitmap(bitmap, RoomActivity.this, uri.getPath());
                            }

                            // reduce the memory consumption
                            bitmap.recycle();
                            bitmap = null;

                            System.gc();

                            // the images are save in jpeg format
                            mimeType = "image/jpeg";
                        } else {
                            imageUrl = null;
                        }

                        resource.contentStream.close();

                    } catch (Exception e) {
                    }
                }

                // is the image content valid ?
                if (null != imageUrl) {
                    uploadImageContent(imageUrl, mimeType, null);
                }
            }
        }
    }

    @Override
    public void onInitialMessagesLoaded() {
        // set general room information
        setTitle(mRoom.getName(mSession.getCredentials().userId));
        setTopic(mRoom.getTopic());
    }

    /**
     * send a typing event notification
     * @param isTyping typing param
     */
    void handleTypingNotification(boolean isTyping) {
        int notificationTimeoutMS = -1;
        if (isTyping) {
            // Check whether a typing event has been already reported to server (We wait for the end of the local timout before considering this new event)
            if (null != mTypingTimer) {
                // Refresh date of the last observed typing
                System.currentTimeMillis();
                mLastTypingDate = System.currentTimeMillis();
                return;
            }

            int timerTimeoutInMs = TYPING_TIMEOUT_MS;

            if (0 != mLastTypingDate) {
                long lastTypingAge = System.currentTimeMillis() - mLastTypingDate;
                if (lastTypingAge < timerTimeoutInMs) {
                    // Subtract the time interval since last typing from the timer timeout
                    timerTimeoutInMs -= lastTypingAge;
                } else {
                    timerTimeoutInMs = 0;
                }
            } else {
                // Keep date of this typing event
                mLastTypingDate = System.currentTimeMillis();
            }

            if (timerTimeoutInMs > 0) {
                mTypingTimer = new Timer();
                mTypingTimerTask = new TimerTask() {
                    public void run() {
                        if (mTypingTimerTask != null) {
                            mTypingTimerTask.cancel();
                            mTypingTimerTask = null;
                        }

                        if (mTypingTimer != null) {
                            mTypingTimer.cancel();
                            mTypingTimer = null;
                        }
                        // Post a new typing notification
                        RoomActivity.this.handleTypingNotification(0 != mLastTypingDate);
                    }
                };
                mTypingTimer.schedule(mTypingTimerTask, TYPING_TIMEOUT_MS);

                // Compute the notification timeout in ms (consider the double of the local typing timeout)
                notificationTimeoutMS = TYPING_TIMEOUT_MS * 2;
            } else {
                // This typing event is too old, we will ignore it
                isTyping = false;
            }
        }
        else {
            // Cancel any typing timer
            if (mTypingTimerTask != null) {
                mTypingTimerTask.cancel();
                mTypingTimerTask = null;
            }

            if (mTypingTimer != null) {
                mTypingTimer.cancel();
                mTypingTimer = null;
            }
            // Reset last typing date
            mLastTypingDate = 0;
        }

        final boolean typingStatus = isTyping;

        mRoom.sendTypingNotification(typingStatus, notificationTimeoutMS, new SimpleApiCallback<Void>() {
            @Override
            public void onSuccess(Void info) {
                // Reset last typing date
                mLastTypingDate = 0;
            }

            @Override
            public void onNetworkError(Exception e) {
                if (mTypingTimerTask != null) {
                    mTypingTimerTask.cancel();
                    mTypingTimerTask = null;
                }

                if (mTypingTimer != null) {
                    mTypingTimer.cancel();
                    mTypingTimer = null;
                }
                // do not send again
                // assume that the typing event is optional
            }
        });
    }

    void cancelTypingNotification() {
        if (0 != mLastTypingDate) {
            if (mTypingTimerTask != null) {
                mTypingTimerTask.cancel();
                mTypingTimerTask = null;
            }
            if (mTypingTimer != null) {
                mTypingTimer.cancel();
                mTypingTimer = null;
            }

            mLastTypingDate = 0;

            mRoom.sendTypingNotification(false, -1, new SimpleApiCallback<Void>() {
            });
        }
    }
}
