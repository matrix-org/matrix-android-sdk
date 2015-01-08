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

import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.data.MyUser;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.matrixandroidsdk.Matrix;
import org.matrix.matrixandroidsdk.R;
import org.matrix.matrixandroidsdk.adapters.AdapterUtils;
import org.matrix.matrixandroidsdk.util.UIUtils;

public class SettingsActivity extends ActionBarActivity {

    private MyUser mMyUser;

    // Views
    private ImageView mAvatarImageView;
    private EditText mDisplayNameEditText;

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

        mDisplayNameEditText = (EditText) findViewById(R.id.editText_displayName);
        mDisplayNameEditText.setText(mMyUser.displayname);

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
    protected void onDestroy() {
        // Save changes when we leave
        saveChanges();

        super.onDestroy();
    }

    private void saveChanges() {
        // Save things
        String nameFromForm = mDisplayNameEditText.getText().toString();

        ApiCallback<Void> changeCallback = UIUtils.buildOnChangeCallback(this);

        if (UIUtils.hasFieldChanged(mMyUser.displayname, nameFromForm)) {
            mMyUser.updateDisplayName(nameFromForm, changeCallback);
        }
    }
}
