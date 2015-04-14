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
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.MyUser;
import org.matrix.androidsdk.db.MXMediasCache;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.ContentResponse;
import org.matrix.androidsdk.util.ContentManager;
import org.matrix.matrixandroidsdk.Matrix;
import org.matrix.matrixandroidsdk.MyPresenceManager;
import org.matrix.matrixandroidsdk.R;
import org.matrix.matrixandroidsdk.util.ResourceUtils;
import org.matrix.matrixandroidsdk.util.UIUtils;

public class SettingsActivity extends MXCActionBarActivity {

    private static final String LOG_TAG = "SettingsActivity";

    private static final int REQUEST_IMAGE = 0;

    private MyUser mMyUser;

    // Views
    private ImageView mAvatarImageView;
    private EditText mDisplayNameEditText;
    private Button mSaveButton;

    private Uri newAvatarUri;

    private MXMediasCache mMediasCache;

    void refreshProfileThumbnail() {
        mAvatarImageView = (ImageView) findViewById(R.id.imageView_avatar);
        if (mMyUser.avatarUrl == null) {
            mAvatarImageView.setImageResource(R.drawable.ic_contact_picture_holo_light);
        } else {
            int size = getResources().getDimensionPixelSize(R.dimen.profile_avatar_size);
            mMediasCache.loadAvatarThumbnail(mAvatarImageView, mMyUser.avatarUrl, size);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_settings);

        MXSession session = Matrix.getInstance(this).getDefaultSession();
        mMyUser = session.getMyUser();

        mMediasCache = Matrix.getInstance(this).getDefaultMediasCache();

        refreshProfileThumbnail();

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

        String versionName = "";

        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            versionName = pInfo.versionName;
        } catch (Exception e) {

        }

        TextView consoleVersionTextView = (TextView) findViewById(R.id.textView_matrixConsoleVersion);
        consoleVersionTextView.setText(getString(R.string.settings_config_console_version, versionName));

        TextView sdkVersionTextView = (TextView) findViewById(R.id.textView_matrixSDKVersion);
        sdkVersionTextView.setText(getString(R.string.settings_config_sdk_version, versionName));

        TextView buildNumberTextView = (TextView) findViewById(R.id.textView_matrixBuildNumber);
        buildNumberTextView.setText(getString(R.string.settings_config_build_number, ""));

        TextView hsTextView = (TextView) findViewById(R.id.textView_configHomeServer);
        hsTextView.setText(getString(R.string.settings_config_home_server, session.getCredentials().homeServer));

        TextView userIdTextView = (TextView) findViewById(R.id.textView_configUserId);
        userIdTextView.setText(getString(R.string.settings_config_user_id, mMyUser.userId));

        // room settings
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

        listenBoxUpdate(preferences, R.id.checkbox_displayAllEvents, getString(R.string.settings_key_display_all_events), false);
        listenBoxUpdate(preferences, R.id.checkbox_hideUnsupportedEvenst, getString(R.string.settings_key_hide_unsupported_events), true);
        listenBoxUpdate(preferences, R.id.checkbox_sortByLastSeen, getString(R.string.settings_key_sort_by_last_seen), true);
        listenBoxUpdate(preferences, R.id.checkbox_displayLeftMembers, getString(R.string.settings_key_display_left_members), false);
        listenBoxUpdate(preferences, R.id.checkbox_displayPublicRooms, getString(R.string.settings_key_display_public_rooms_recents), true);

        final Button clearCacheButton = (Button) findViewById(R.id.button_clear_cache);

        String cacheSize = android.text.format.Formatter.formatFileSize(this, mMediasCache.cacheSize(this));
        clearCacheButton.setText(getString(R.string.clear_cache)  + " (" + cacheSize + ")");

        clearCacheButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mMediasCache.clearCache(SettingsActivity.this);

                String cacheSize = android.text.format.Formatter.formatFileSize(SettingsActivity.this, mMediasCache.cacheSize(SettingsActivity.this));
                clearCacheButton.setText(getString(R.string.clear_cache)  + " (" + cacheSize + ")");
            }
        });

    }

    private void listenBoxUpdate(final SharedPreferences preferences, int boxId, final String preferenceKey, boolean defaultValue) {
        final CheckBox checkBox = (CheckBox) findViewById(boxId);
        checkBox.setChecked(preferences.getBoolean(preferenceKey, defaultValue));
        checkBox.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        SharedPreferences.Editor editor = preferences.edit();
                        editor.putBoolean(preferenceKey, checkBox.isChecked());
                        editor.commit();
                    }
                }
        );
    }

    @Override
    protected void onPause() {
        super.onPause();
        MyPresenceManager.getInstance(this).advertiseUnavailableAfterDelay();
    }

    @Override
    protected void onResume() {
        super.onResume();
        MyPresenceManager.getInstance(this).advertiseOnline();

        final View refreshingView = findViewById(R.id.profile_mask);
        refreshingView.setVisibility(View.VISIBLE);

        final MXSession session = Matrix.getInstance(this).getDefaultSession();

        // refresh the myUser profile
        if ((null != session) && (null !=  session.getProfileApiClient())) {

            session.getProfileApiClient().displayname(mMyUser.userId, new SimpleApiCallback<String>(this) {
                @Override
                public void onSuccess(String displayname) {
                    mMyUser.displayname = displayname;
                    mDisplayNameEditText.setText(mMyUser.displayname);

                    session.getProfileApiClient().avatarUrl(mMyUser.userId, new SimpleApiCallback<String>(this) {
                        @Override
                        public void onSuccess(String avatarUrl) {
                            mMyUser.avatarUrl = avatarUrl;
                            refreshProfileThumbnail();
                            refreshingView.setVisibility(View.GONE);
                        }
                    });
                }
            });
        }

        // refresh the cache size
        Button clearCacheButton = (Button) findViewById(R.id.button_clear_cache);
        String cacheSize = android.text.format.Formatter.formatFileSize(this, mMediasCache.cacheSize(this));
        clearCacheButton.setText(getString(R.string.clear_cache)  + " (" + cacheSize + ")");
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
            Log.d(LOG_TAG, "Selected image to upload: " + newAvatarUri);
            ResourceUtils.Resource resource = ResourceUtils.openResource(this, newAvatarUri);
            if (resource == null) {
                Toast.makeText(SettingsActivity.this,
                        getString(R.string.settings_failed_to_upload_avatar),
                        Toast.LENGTH_LONG).show();
                return;
            }

            MXSession session = Matrix.getInstance(this).getDefaultSession();

            final ProgressDialog progressDialog = ProgressDialog.show(this, null, getString(R.string.message_uploading), true);

            session.getContentManager().uploadContent(resource.contentStream, resource.mimeType, null, new ContentManager.UploadCallback() {
                @Override
                public void onUploadProgress(String anUploadId, int percentageProgress) {
                    progressDialog.setMessage(getString(R.string.message_uploading) + " (" + percentageProgress + "%)");
                }

                @Override
                public void onUploadComplete(String anUploadId, ContentResponse uploadResponse, String serverErrorMessage)  {
                    if (uploadResponse == null) {
                        Toast.makeText(SettingsActivity.this,
                                getString(R.string.settings_failed_to_upload_avatar),
                                Toast.LENGTH_LONG).show();
                    }
                    else {
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
                    progressDialog.dismiss();
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
