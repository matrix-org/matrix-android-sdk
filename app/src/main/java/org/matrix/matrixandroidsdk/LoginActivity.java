package org.matrix.matrixandroidsdk;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import org.matrix.androidsdk.MXSession;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.client.LoginRestClient;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.login.Credentials;


/**
 * Displays the login screen.
 */
public class LoginActivity extends ActionBarActivity {

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
    }

    private void onLoginClick(String hsUrl, String username, String password) {
        if (!hsUrl.startsWith("http")) {
            Toast.makeText(this, "URL must start with http[s]://", Toast.LENGTH_SHORT).show();
            return;
        }
        LoginRestClient client = new LoginRestClient(Uri.parse(hsUrl));
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
            public void onMatrixError(MatrixError e) {
                String msg = "Unable to login: " + e.error + "("+e.errcode+")";
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
}
