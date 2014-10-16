package org.matrix.matrixandroidsdk;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import org.matrix.androidsdk.MXApiClient;
import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.api.response.MatrixError;
import org.matrix.androidsdk.api.response.login.Credentials;
import org.matrix.androidsdk.rest.client.LoginApiClient;
import org.matrix.matrixandroidsdk.store.LoginStorage;


/**
 * Displays the login screen.
 */
public class LoginActivity extends ActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        if (hasCredentials()) {
            goToHomepage();
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
    }

    private void onLoginClick(String hsUrl, String username, String password) {
        if (!hsUrl.startsWith("http")) {
            Toast.makeText(this, "URL must start with http[s]://", Toast.LENGTH_SHORT).show();
            return;
        }
        LoginApiClient client = new LoginApiClient(Uri.parse(hsUrl));
        // TODO: This client should check that it can use u/p login on this home server!!!
        client.loginWithPassword(username, password, new LoginApiClient.SimpleApiCallback<Credentials>() {

            @Override
            public void onSuccess(Credentials credentials) {
                MXSession session = Matrix.getInstance(getApplicationContext()).createSession(credentials);
                Matrix.getInstance(getApplicationContext()).setDefaultSession(session);
                goToHomepage();
            }

            @Override
            public void onMatrixError(MatrixError e) {
                String msg = "Unable to login: " + e.error + "("+e.errcode+")";
                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
            }
        });
    }

    private boolean hasCredentials() {
        return Matrix.getInstance(this).getDefaultSession() != null;
    }


    private void goToHomepage() {
        startActivity(new Intent(this, HomeActivity.class));
    }
}
