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

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.text.TextUtils;
import android.view.View;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.Toast;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.client.LoginRestClient;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.login.Credentials;
import org.matrix.matrixandroidsdk.Matrix;
import org.matrix.matrixandroidsdk.R;

/**
 * Displays the login screen.
 */
public class LoginActivity extends MXCActionBarActivity {

    static final int ACCOUNT_CREATION_ACTIVITY_REQUEST_CODE = 314;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        if (hasCredentials()) {
            goToSplash();
            finish();
        }

        findViewById(R.id.button_login).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String username = ((EditText)findViewById(R.id.editText_username)).getText().toString();
                String password = ((EditText)findViewById(R.id.editText_password)).getText().toString();
                String hs = ((EditText)findViewById(R.id.editText_hs)).getText().toString();
                onLoginClick(hs, username, password);
            }
        });

        findViewById(R.id.button_create_account).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String hs = ((EditText) findViewById(R.id.editText_hs)).getText().toString();

                boolean validHomeServer = false;

                try {
                    Uri hsUri = Uri.parse(hs);
                    validHomeServer = "http".equals(hsUri.getScheme()) || "https".equals(hsUri.getScheme());
                } catch (Exception e) {
                }

                if (!validHomeServer) {
                    Toast.makeText(LoginActivity.this, getString(R.string.login_error_invalid_home_server), Toast.LENGTH_SHORT).show();
                    return;
                }

                Intent intent = new Intent(LoginActivity.this, AccountCreationActivity.class);
                intent.putExtra(AccountCreationActivity.EXTRA_HOME_SERVER_ID, hs);
                startActivityForResult(intent, ACCOUNT_CREATION_ACTIVITY_REQUEST_CODE);
            }
        });
    }

    private void onLoginClick(String hsUrl, String username, String password) {
        if (!hsUrl.startsWith("http")) {
            Toast.makeText(this, getString(R.string.login_error_must_start_http), Toast.LENGTH_SHORT).show();
            return;
        }

        if (TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, getString(R.string.login_error_invalid_credentials), Toast.LENGTH_SHORT).show();
            return;
        }

        LoginRestClient client = null;

        try {
            client = new LoginRestClient(Uri.parse(hsUrl));
        } catch (Exception e) {
        }

        if (null == client) {
            Toast.makeText(this, getString(R.string.login_error_invalid_home_server), Toast.LENGTH_SHORT).show();
            return;
        }

        // TODO: This client should check that it can use u/p login on this home server!!!
        client.loginWithPassword(username, password, new SimpleApiCallback<Credentials>() {
            @Override
            public void onSuccess(Credentials credentials) {
                MXSession session = Matrix.getInstance(getApplicationContext()).createSession(credentials);
                Matrix.getInstance(getApplicationContext()).setDefaultSession(session);
                goToSplash();
                LoginActivity.this.finish();
            }

            @Override
            public void onNetworkError(Exception e) {
                Toast.makeText(getApplicationContext(), getString(R.string.login_error_network_error), Toast.LENGTH_LONG).show();
            }

            @Override
            public void onUnexpectedError(Exception e) {
                String msg = getString(R.string.login_error_unable_login) + " : " + e.getMessage();
                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                String msg = getString(R.string.login_error_unable_login) + " : " + e.error + "("+e.errcode+")";
                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    private boolean hasCredentials() {
        return Matrix.getInstance(this).getDefaultSession() != null;
    }

    private void goToSplash() {
        startActivity(new Intent(this, SplashActivity.class));
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data)  {
        if (ACCOUNT_CREATION_ACTIVITY_REQUEST_CODE == requestCode) {
            if(resultCode == RESULT_OK){
                String homeServer = data.getStringExtra("homeServer");
                String userId = data.getStringExtra("userId");
                String accessToken = data.getStringExtra("accessToken");

                // build a credential with the provided items
                Credentials credentials = new Credentials();
                credentials.userId = userId;
                credentials.homeServer = homeServer;
                credentials.accessToken = accessToken;

                // let's go...
                MXSession session = Matrix.getInstance(getApplicationContext()).createSession(credentials);
                Matrix.getInstance(getApplicationContext()).setDefaultSession(session);
                goToSplash();
                LoginActivity.this.finish();
            }
        }
    }
}
