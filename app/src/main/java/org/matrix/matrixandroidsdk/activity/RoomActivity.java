package org.matrix.matrixandroidsdk.activity;

import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
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
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
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
import org.matrix.matrixandroidsdk.db.ConsoleLatestChatMessageCache;
import org.matrix.matrixandroidsdk.db.ConsoleMediasCache;
import org.matrix.matrixandroidsdk.fragments.MatrixMessageListFragment;
import org.matrix.matrixandroidsdk.fragments.MembersInvitationDialogFragment;
import org.matrix.matrixandroidsdk.fragments.RoomMembersDialogFragment;
import org.matrix.matrixandroidsdk.services.EventStreamService;
import org.matrix.matrixandroidsdk.util.ResourceUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Displays a single room with messages.
 */
public class RoomActivity extends MXCActionBarActivity implements MatrixMessageListFragment.MatrixMessageListFragmentListener {

    public static final String EXTRA_ROOM_ID = "org.matrix.matrixandroidsdk.RoomActivity.EXTRA_ROOM_ID";

    private static final String TAG_FRAGMENT_MATRIX_MESSAGE_LIST = "org.matrix.androidsdk.RoomActivity.TAG_FRAGMENT_MATRIX_MESSAGE_LIST";
    private static final String TAG_FRAGMENT_MEMBERS_DIALOG = "org.matrix.androidsdk.RoomActivity.TAG_FRAGMENT_MEMBERS_DIALOG";
    private static final String TAG_FRAGMENT_INVITATION_MEMBERS_DIALOG = "org.matrix.androidsdk.RoomActivity.TAG_FRAGMENT_INVITATION_MEMBERS_DIALOG";
    private static final String LOG_TAG = "RoomActivity";
    private static final int TYPING_TIMEOUT_MS = 10000;

    private static final String CAMERA_VALUE_TITLE = "attachment"; // Samsung devices need a filepath to write to or else won't return a Uri (!!!)

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
    private static final int TAKE_IMAGE = 0;

    private MatrixMessageListFragment mMatrixMessageListFragment;
    private MXSession mSession;
    private Room mRoom;

    private String mLatestTakePictureCameraUri; // has to be String not Uri because of Serializable

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
                ConsoleLatestChatMessageCache.updateLatestMessage(RoomActivity.this, mRoom.getRoomId(), "");
                editText.setText("");
            }
        });

        findViewById(R.id.button_more).setOnClickListener(new View.OnClickListener() {
            private static final int OPTION_CANCEL = 0;
            private static final int OPTION_ATTACH_IMAGE = 1;
            private static final int OPTION_TAKE_IMAGE = 2;
            private static final int OPTION_INVITE_BY_NAME = 3;
            private static final int OPTION_INVITE_BY_LIST = 4;

            @Override
            public void onClick(View v) {
                final int[] options = new int[] {OPTION_ATTACH_IMAGE, OPTION_TAKE_IMAGE, OPTION_INVITE_BY_NAME, OPTION_INVITE_BY_LIST, OPTION_CANCEL};

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
                                    case OPTION_TAKE_IMAGE:
                                        Intent captureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

                                        // the following is a fix for buggy 2.x devices
                                        Date date = new Date();
                                        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss", Locale.US);
                                        ContentValues values = new ContentValues();
                                        values.put(MediaStore.Images.Media.TITLE, CAMERA_VALUE_TITLE + formatter.format(date));
                                        // The Galaxy S not only requires the name of the file to output the image to, but will also not
                                        // set the mime type of the picture it just took (!!!). We assume that the Galaxy S takes image/jpegs
                                        // so the attachment uploader doesn't freak out about there being no mimetype in the content database.
                                        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                                        Uri dummyUri = null;
                                        try {
                                            dummyUri = RoomActivity.this.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                                        }
                                        catch (UnsupportedOperationException uoe) {
                                            Log.e(LOG_TAG, "Unable to insert camera URI into MediaStore.Images.Media.EXTERNAL_CONTENT_URI - no SD card? Attempting to insert into device storage.");
                                            try {
                                                dummyUri = RoomActivity.this.getContentResolver().insert(MediaStore.Images.Media.INTERNAL_CONTENT_URI, values);
                                            }
                                            catch (Exception e) {
                                                Log.e(LOG_TAG, "Unable to insert camera URI into internal storage. Giving up. "+e);
                                            }
                                        }
                                        catch (Exception e) {
                                            Log.e(LOG_TAG, "Unable to insert camera URI into MediaStore.Images.Media.EXTERNAL_CONTENT_URI. "+e);
                                        }
                                        if (dummyUri != null) {
                                            captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, dummyUri);
                                        }
                                        // Store the dummy URI which will be set to a placeholder location. When all is lost on samsung devices,
                                        // this will point to the data we're looking for.
                                        // Because Activities tend to use a single MediaProvider for all their intents, this field will only be the
                                        // *latest* TAKE_PICTURE Uri. This is deemed acceptable as the normal flow is to create the intent then immediately
                                        // fire it, meaning onActivityResult/getUri will be the next thing called, not another createIntentFor.
                                        RoomActivity.this.mLatestTakePictureCameraUri = dummyUri == null ? null : dummyUri.toString();

                                        startActivityForResult(captureIntent, TAKE_IMAGE);
                                        break;

                                    case OPTION_INVITE_BY_NAME:
                                        AlertDialog alert = CommonActivityUtils.createEditTextAlert(RoomActivity.this, RoomActivity.this.getResources().getString(R.string.title_activity_invite_user), "@localpart:domain", null, new CommonActivityUtils.OnSubmitListener() {
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
                                        break;

                                    case OPTION_INVITE_BY_LIST:
                                        final MXSession session = Matrix.getInstance(getApplicationContext()).getDefaultSession();
                                        if (session != null) {
                                            FragmentManager fm = getSupportFragmentManager();

                                            MembersInvitationDialogFragment fragment = (MembersInvitationDialogFragment) fm.findFragmentByTag(TAG_FRAGMENT_INVITATION_MEMBERS_DIALOG);
                                            if (fragment != null) {
                                                fragment.dismissAllowingStateLoss();
                                            }
                                            fragment = MembersInvitationDialogFragment.newInstance(mRoom.getRoomId());
                                            fragment.show(fm, TAG_FRAGMENT_INVITATION_MEMBERS_DIALOG);
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
                        case OPTION_TAKE_IMAGE:
                            label = getString(R.string.option_take_image);
                            break;
                        case OPTION_INVITE_BY_NAME:
                            label = getString(R.string.option_invite_by_name);
                            break;
                        case OPTION_INVITE_BY_LIST:
                            label = getString(R.string.option_invite_by_list);
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
                ConsoleLatestChatMessageCache.updateLatestMessage(RoomActivity.this, mRoom.getRoomId(), editText.getText().toString());
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
        // add sanity check
        // the activity creation could have been cancelled because the roomId was missing
        if ((null != mRoom) && (null != mEventListener)) {
            mRoom.removeEventListener(mEventListener);
        }

        super.onDestroy();
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

        EditText editText = (EditText)findViewById(R.id.editText_messageBox);
        String cachedText = ConsoleLatestChatMessageCache.getLatestText(this, mRoom.getRoomId());

        if (!cachedText.equals(editText.getText())) {
            editText.setText("");
            editText.append(cachedText);
        }
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
                    session.joinRoomByRoomAliasOrId(roomAlias,new SimpleApiCallback<String>() {

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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            if ((requestCode == REQUEST_IMAGE) || (requestCode == TAKE_IMAGE)) {
                Uri dataUri;

                if (null != data) {
                    dataUri =  data.getData();
                } else {
                    dataUri =  mLatestTakePictureCameraUri == null ? null : Uri.parse(mLatestTakePictureCameraUri);
                }

                mLatestTakePictureCameraUri = null;

                final Uri imageUri = dataUri;

                ResourceUtils.Resource resource = ResourceUtils.openResource(this, imageUri);
                if (resource == null) {
                    Toast.makeText(RoomActivity.this,
                            getString(R.string.message_failed_to_upload),
                            Toast.LENGTH_LONG).show();
                    return;
                }

                // save the file in the filesystem
                String imageUrl =  ConsoleMediasCache.saveMedia(resource.contentStream, RoomActivity.this, null);
                String mimeType = resource.mimeType;

                try {
                    resource.contentStream.close();
                } catch(Exception e) {
                }

                // try to retrieve the gallery thumbnail
                // if the image comes from the gallery..
                Bitmap thumbnailBitmap = null;

                try {
                    ContentResolver resolver = getContentResolver();
                    List uriPath = imageUri.getPathSegments();
                    long imageId = Long.parseLong((String)(uriPath.get(uriPath.size() - 1)));

                    thumbnailBitmap = MediaStore.Images.Thumbnails.getThumbnail(resolver, imageId, MediaStore.Images.Thumbnails.MINI_KIND, null);
                } catch (Exception e) {

                }

                // no thumbnail has been found or the mimetype is unknown
                if ((null == thumbnailBitmap) || (null == mimeType) || (mimeType.equals("image/*"))) {

                    // need to decompress the high res image
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                    resource = ResourceUtils.openResource(this, imageUri);

                    // get the full size bitmap
                    Bitmap fullSizeBitmap = BitmapFactory.decodeStream(resource.contentStream, null, options);

                    // create a thumbnail bitmap if there is none
                    if (null == thumbnailBitmap) {
                        if (fullSizeBitmap != null) {
                            double fullSizeWidth = fullSizeBitmap.getWidth();
                            double fullSizeHeight = fullSizeBitmap.getHeight();

                            double thumbnailWidth = mMatrixMessageListFragment.getMaxThumbnailWith();
                            double thumbnailHeight =  mMatrixMessageListFragment.getMaxThumbnailHeight();

                            if (fullSizeWidth > fullSizeHeight) {
                                thumbnailHeight = thumbnailWidth * fullSizeHeight / fullSizeWidth;
                            } else {
                                thumbnailWidth = thumbnailHeight * fullSizeWidth / fullSizeHeight;
                            }

                            try {
                                thumbnailBitmap = Bitmap.createScaledBitmap(fullSizeBitmap, (int) thumbnailWidth, (int) thumbnailHeight, false);
                            } catch (OutOfMemoryError ex) {
                            }
                        }
                    }

                    // unknown mimetype
                    if ((null == mimeType) || (mimeType.equals("image/*"))) {
                        try {
                            if (null != fullSizeBitmap) {
                                Uri uri = Uri.parse(imageUrl);
                                try {
                                    ConsoleMediasCache.saveBitmap(fullSizeBitmap, RoomActivity.this, uri.getPath());
                                } catch (OutOfMemoryError ex) {
                                }

                                // the images are save in jpeg format
                                mimeType = "image/jpeg";
                            } else {
                                imageUrl = null;
                            }

                            resource.contentStream.close();

                        } catch (Exception e) {
                            imageUrl = null;
                        }
                    }

                    // reduce the memory consumption
                    fullSizeBitmap.recycle();
                    System.gc();
                }

                String thumbnailURL = ConsoleMediasCache.saveBitmap(thumbnailBitmap, RoomActivity.this, null);
                thumbnailBitmap.recycle();

                // is the image content valid ?
                if ((null != imageUrl) && (null != thumbnailURL)) {
                    mMatrixMessageListFragment.uploadImageContent(thumbnailURL, imageUrl, mimeType);
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
