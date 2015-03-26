/* 
 * Copyright 2014 OpenMarket Ltd
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

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.MyUser;
import org.matrix.androidsdk.data.Room;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.listeners.MXEventListener;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.PowerLevels;
import org.matrix.androidsdk.rest.model.RoomMember;
import org.matrix.androidsdk.util.JsonUtils;
import org.matrix.matrixandroidsdk.Matrix;
import org.matrix.matrixandroidsdk.R;
import org.matrix.matrixandroidsdk.adapters.AdapterUtils;
import org.matrix.matrixandroidsdk.db.ConsoleMediasCache;

import java.util.ArrayList;
import java.util.Collection;

public class MemberDetailsActivity extends MXCActionBarActivity {

    private static final String LOG_TAG = "MemberDetailsActivity";

    public static final String EXTRA_ROOM_ID = "org.matrix.matrixandroidsdk.MemberDetailsActivity.EXTRA_ROOM_ID";
    public static final String EXTRA_USER_ID = "org.matrix.matrixandroidsdk.MemberDetailsActivity.EXTRA_USER_ID";

    // info
    private Room mRoom;
    private String mRoomId;
    private String mUserId;
    private RoomMember mMember;
    private MXSession mSession;

    // Views
    private ImageView mThumbnailImageView;
    private TextView mMatrixIdTextView;
    private ArrayList<Button>mButtonsList;

    private MXEventListener mEventListener = new MXEventListener() {
        @Override
        public void onLiveEvent(final Event event, RoomState roomState) {
            MemberDetailsActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // check if the event is received for the current room
                    // check if there is a member update
                    if ((Event.EVENT_TYPE_STATE_ROOM_MEMBER.equals(event.type)) || (Event.EVENT_TYPE_STATE_ROOM_POWER_LEVELS.equals(event.type))) {

                        // update only if it is the current user
                        if ((null != event.userId) && (event.userId.equals(mUserId))) {
                            MemberDetailsActivity.this.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    //
                                    MemberDetailsActivity.this.refreshRoomMember();
                                    MemberDetailsActivity.this.refresh();
                                }
                            });
                        }
                    }
                }
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_member_details);

        Intent intent = getIntent();
        if (!intent.hasExtra(EXTRA_ROOM_ID)) {
            Log.e(LOG_TAG, "No room ID extra.");
            finish();
            return;
        }
        mRoomId = intent.getStringExtra(EXTRA_ROOM_ID);

        if (!intent.hasExtra(EXTRA_USER_ID)) {
            Log.e(LOG_TAG, "No user ID extra.");
            finish();
            return;
        }

        mUserId = intent.getStringExtra(EXTRA_USER_ID);

        mSession = Matrix.getInstance(getApplicationContext()).getDefaultSession();
        mRoom = mSession.getDataHandler().getRoom(mRoomId);

        if (null == mRoom) {
            Log.e(LOG_TAG, "The room is not found");
            finish();
            return;
        }

        // find out the room member
        Collection<RoomMember> members = mRoom.getMembers();
        for(RoomMember member : members) {
            if (member.getUserId().equals(mUserId)) {
                mMember = member;
                break;
            }
        }

        // sanity checks
        if (null == mMember) {
            Log.e(LOG_TAG, "The user " + mUserId + " is not in the room " + mRoomId);
            finish();
            return;
        }

        mButtonsList = new ArrayList<Button>();
        mButtonsList.add((Button)findViewById(R.id.contact_button_1));
        mButtonsList.add((Button)findViewById(R.id.contact_button_2));
        mButtonsList.add((Button)findViewById(R.id.contact_button_3));
        mButtonsList.add((Button)findViewById(R.id.contact_button_4));

        // set the click listener for each button
        for(Button button : mButtonsList) {
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    String text = (String)((Button)v).getText();

                    final View refreshingView = findViewById(R.id.profile_mask);
                    final ApiCallback callback = new SimpleApiCallback<Void>(MemberDetailsActivity.this) {
                        @Override
                        public void onMatrixError(MatrixError e) {
                            if (MatrixError.FORBIDDEN.equals(e.errcode)) {
                                Toast.makeText(MemberDetailsActivity.this, e.error, Toast.LENGTH_LONG).show();
                            }

                            MemberDetailsActivity.this.refresh();
                        }

                        @Override
                        public void onSuccess(Void info) {
                            MemberDetailsActivity.this.refresh();
                        }
                    };

                    // disable the buttons
                    for(Button button : mButtonsList){
                        button.setEnabled(false);
                    }

                    if (text.equals(getResources().getString(R.string.kick))) {
                        refreshingView.setVisibility(View.VISIBLE);
                        mRoom.kick(mMember.getUserId(), callback);
                    } else  if (text.equals(getResources().getString(R.string.ban))) {
                        refreshingView.setVisibility(View.VISIBLE);
                        mRoom.ban(mMember.getUserId(), null, callback);
                    } else  if (text.equals(getResources().getString(R.string.unban))) {
                        refreshingView.setVisibility(View.VISIBLE);
                        mRoom.unban(mMember.getUserId(), callback);
                    } else  if (text.equals(getResources().getString(R.string.invite))) {
                        refreshingView.setVisibility(View.VISIBLE);
                        mRoom.invite(mMember.getUserId(), callback);
                    } else  if (text.equals(getResources().getString(R.string.chat))) {
                        refreshingView.setVisibility(View.VISIBLE);
                        MemberDetailsActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                CommonActivityUtils.goToOneToOneRoom(mUserId, MemberDetailsActivity.this, new SimpleApiCallback<Void>(MemberDetailsActivity.this) {
                                    @Override
                                    public void onMatrixError(MatrixError e) {
                                        if (MatrixError.FORBIDDEN.equals(e.errcode)) {
                                            Toast.makeText(MemberDetailsActivity.this, e.error, Toast.LENGTH_LONG).show();
                                        }
                                        MemberDetailsActivity.this.refresh();
                                    }

                                    @Override
                                    public void onNetworkError(Exception e) {
                                        Toast.makeText(MemberDetailsActivity.this, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                                        MemberDetailsActivity.this.refresh();
                                    }

                                    @Override
                                    public void onUnexpectedError(Exception e) {
                                        Toast.makeText(MemberDetailsActivity.this, e.getLocalizedMessage(), Toast.LENGTH_LONG).show();
                                        MemberDetailsActivity.this.refresh();
                                    }
                                });
                            }
                        });
                    } else  if (text.equals(getResources().getString(R.string.set_power_level))) {
                        String title = getResources().getString(R.string.set_power_level);
                        String initText =  mRoom.getLiveState().getPowerLevels().getUserPowerLevel(mUserId) + "";

                        final AlertDialog alert = CommonActivityUtils.createEditTextAlert(MemberDetailsActivity.this,title,null,initText,new CommonActivityUtils.OnSubmitListener() {
                            @Override
                            public void onSubmit(String text) {
                                if (text.length() == 0) {
                                    return;
                                }

                                int newPowerLevel = -1;

                                try {
                                    newPowerLevel = Integer.parseInt(text);
                                }
                                catch (Exception e) {
                                }

                                if (newPowerLevel >= 0) {
                                    refreshingView.setVisibility(View.VISIBLE);
                                    mRoom.updateUserPowerLevels(mMember.getUserId(), newPowerLevel, callback);
                                } else {
                                    MemberDetailsActivity.this.refresh();
                                }
                            }

                            @Override
                            public void onCancelled() {
                                MemberDetailsActivity.this.refresh();
                            }
                        });

                        MemberDetailsActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                alert.show();
                            }
                        });
                    }
                }
            });
        }

        mSession.getDataHandler().getRoom(mRoom.getRoomId()).addEventListener(mEventListener);

        // load the thumbnail
        mThumbnailImageView = (ImageView) findViewById(R.id.imageView_avatar);

        // set the title
        mMatrixIdTextView = (TextView) findViewById(R.id.textView_matrixid);

        // refresh the activity views
        refresh();
    }

    private void refreshRoomMember() {
        mRoom = mSession.getDataHandler().getRoom(mRoomId);

        if (null != mRoom){
            // find out the room member
            Collection<RoomMember> members = mRoom.getMembers();
            for (RoomMember member : members) {
                if (member.getUserId().equals(mUserId)) {
                    mMember = member;
                    break;
                }
            }
        }
    }

    /**
     * refresh each activity views
     */
    private void refresh() {

        final View refreshingView = findViewById(R.id.profile_mask);
        refreshingView.setVisibility(View.GONE);

        mMatrixIdTextView.setText(mUserId);
        this.setTitle(mMember.displayname);
        this.refreshProfileThumbnail();

        MyUser myUser = Matrix.getInstance(this).getDefaultSession().getMyUser();
        ArrayList<String> buttonTitles = new ArrayList<String>();

        // Check user's power level before allowing an action (kick, ban, ...)
        PowerLevels powerLevels = mRoom.getLiveState().getPowerLevels();

        int userPowerLevel = powerLevels.getUserPowerLevel(mUserId);
        int myPowerLevel = powerLevels.getUserPowerLevel(myUser.userId);

        // Consider the case of the user himself
        if (mUserId.equals(myUser.userId)) {
            buttonTitles.add(getResources().getString(R.string.leave));

            if (userPowerLevel >= powerLevels.stateDefault) {
                buttonTitles.add(getResources().getString(R.string.set_power_level));
            }
        } else {

            if ((RoomMember.MEMBERSHIP_JOIN.equals(mMember.membership)) || (RoomMember.MEMBERSHIP_INVITE.equals(mMember.membership))) {
                // Check conditions to be able to kick someone
                if ((myPowerLevel >= powerLevels.kick) && (myPowerLevel >= userPowerLevel)) {
                    buttonTitles.add(getResources().getString(R.string.kick));
                }

                // Check conditions to be able to ban someone
                if ((myPowerLevel >= powerLevels.ban) && (myPowerLevel >= userPowerLevel)) {
                    buttonTitles.add(getResources().getString(R.string.ban));
                }
            } else if (RoomMember.MEMBERSHIP_LEAVE.equals(mMember.membership)) {
                // Check conditions to be able to invite someone
                if (myPowerLevel >= powerLevels.invite) {
                    buttonTitles.add(getResources().getString(R.string.invite));
                }
                // Check conditions to be able to ban someone
                if (myPowerLevel >= powerLevels.ban) {
                    buttonTitles.add(getResources().getString(R.string.ban));
                }
            } else if (RoomMember.MEMBERSHIP_BAN.equals(mMember.membership)) {
                // Check conditions to be able to invite someone
                if (myPowerLevel >= powerLevels.ban) {
                    buttonTitles.add(getResources().getString(R.string.unban));
                }
            }

            // update power level
            if (myPowerLevel >= powerLevels.stateDefault) {
                buttonTitles.add(getResources().getString(R.string.set_power_level));
            }

            // allow to invite an user if the room has > 2 users
            // else it will reopen this chat
            if (mRoom.getMembers().size() > 2) {
                buttonTitles.add(getResources().getString(R.string.chat));
            }
        }

        // display the available buttons
        int buttonIndex = 0;
        for(; buttonIndex < buttonTitles.size(); buttonIndex++) {
            Button button = mButtonsList.get(buttonIndex);
            button.setVisibility(View.VISIBLE);
            button.setEnabled(true);
            button.setText(buttonTitles.get(buttonIndex));
        }

        for(;buttonIndex < mButtonsList.size(); buttonIndex++) {
            Button button = mButtonsList.get(buttonIndex);
            button.setVisibility(View.INVISIBLE);
        }
    }

    /**
     * refresh the profile thumbnail
     */
    private void refreshProfileThumbnail() {
        mThumbnailImageView.setImageResource(R.drawable.ic_contact_picture_holo_light);

        if (mMember.avatarUrl != null) {
            int size = getResources().getDimensionPixelSize(R.dimen.profile_avatar_size);
            ConsoleMediasCache.loadAvatarThumbnail(mThumbnailImageView, mMember.avatarUrl, size);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mRoom.removeEventListener(mEventListener);
    }
}
