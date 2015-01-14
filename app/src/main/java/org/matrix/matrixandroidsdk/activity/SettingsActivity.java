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
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.MyUser;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.ContentResponse;
import org.matrix.androidsdk.util.ContentManager;
import org.matrix.matrixandroidsdk.Matrix;
import org.matrix.matrixandroidsdk.R;
import org.matrix.matrixandroidsdk.adapters.AdapterUtils;
import org.matrix.matrixandroidsdk.util.ResourceUtils;
import org.matrix.matrixandroidsdk.util.UIUtils;

public class SettingsActivity extends ActionBarActivity {

    private static final String LOG_TAG = "SettingsActivity";

    private static final int REQUEST_IMAGE = 0;

    private MyUser mMyUser;

    // Views
    private ImageView mAvatarImageView;
    private EditText mDisplayNameEditText;
    private Button mSaveButton;

    private Uri newAvatarUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_settings);

        MXSession session = Matrix.getInstance(this).getDefaultSession();
        mMyUser = session.getMyUser();

        mAvatarImageView = (ImageView) findViewById(R.id.imageView_avatar);
        if (mMyUser.avatarUrl == null) {
            mAvatarImageView.setImageResource(R.drawable.ic_contact_picture_holo_light);
        }
        else {
            int size = getResources().getDimensionPixelSize(R.dimen.profile_avatar_size);
            AdapterUtils.loadThumbnailBitmap(mAvatarImageView, mMyUser.avatarUrl, size, size);
        }
        mAvatarImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent fileIntent = new Intent(Intent.ACTION_PICK);
                fileIntent.setType("image/*");
                startActivityForResult(fileIntent, REQUEST_IMAGE);
            }
        });

        mDisplayNameEditText = (EditText) findViewById(R.id.editText_displayName);
        mDisplayNameEditText.setText(mMyUser.displayname);
        mDisplayNameEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateSaveButton();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        mSaveButton = (Button) findViewById(R.id.button_save);
        mSaveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveChanges();
            }
        });

        // Config information
        TextView hsTextView = (TextView) findViewById(R.id.textView_configHomeServer);
        hsTextView.setText(getString(R.string.settings_config_home_server, session.getCredentials().homeServer));

//        TextView isTextView = (TextView) findViewById(R.id.textView_configIdentityServer);

        TextView userIdTextView = (TextView) findViewById(R.id.textView_configUserId);
        userIdTextView.setText(getString(R.string.settings_config_user_id, mMyUser.userId));

        TextView tokenTextView = (TextView) findViewById(R.id.textView_configAccessToken);
        tokenTextView.setText(getString(R.string.settings_config_access_token, session.getCredentials().accessToken));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_IMAGE) {
                newAvatarUri = data.getData();
                mAvatarImageView.setImageURI(newAvatarUri);
                mSaveButton.setEnabled(true); // Enable the save button if it wasn't already
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (areChanges()) {
            // The user is trying to leave with unsaved changes. Warn about that
            new AlertDialog.Builder(this)
                    .setMessage(R.string.message_unsaved_changes)
                    .setPositiveButton(R.string.stay, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    })
                    .setNegativeButton(R.string.leave, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            SettingsActivity.super.onBackPressed();
                        }
                    })
                    .create()
                    .show();
        }
        else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void saveChanges() {
        // Save things
        final String nameFromForm = mDisplayNameEditText.getText().toString();

        final ApiCallback<Void> changeCallback = UIUtils.buildOnChangeCallback(this);

        if (UIUtils.hasFieldChanged(mMyUser.displayname, nameFromForm)) {
            mMyUser.updateDisplayName(nameFromForm, new SimpleApiCallback<Void>(changeCallback) {
                @Override
                public void onSuccess(Void info) {
                    super.onSuccess(info);
                    updateSaveButton();
                }
            });
        }

        if (newAvatarUri != null) {
            final String selectedPath = ResourceUtils.getImagePath(this, newAvatarUri);
            Log.d(LOG_TAG, "Selected image to upload: " + selectedPath);
            MXSession session = Matrix.getInstance(this).getDefaultSession();
            session.getContentManager().uploadContent(selectedPath, new ContentManager.UploadCallback() {
                @Override
                public void onUploadComplete(ContentResponse uploadResponse) {
                    if (uploadResponse != null) {
                        Log.d(LOG_TAG, "Uploaded to " + uploadResponse.contentUri);
                        mMyUser.updateAvatarUrl(uploadResponse.contentUri, new SimpleApiCallback<Void>(changeCallback) {
                            @Override
                            public void onSuccess(Void info) {
                                super.onSuccess(info);
                                newAvatarUri = null; // Reset this because its being set is how we know there's been a change
                                updateSaveButton();
                            }
                        });
                    }
                }
            });
        }
    }

    private void updateSaveButton() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mSaveButton.setEnabled(areChanges());
            }
        });
    }

    private boolean areChanges() {
        return (newAvatarUri != null)
                || UIUtils.hasFieldChanged(mMyUser.displayname, mDisplayNameEditText.getText().toString());
    }
}
