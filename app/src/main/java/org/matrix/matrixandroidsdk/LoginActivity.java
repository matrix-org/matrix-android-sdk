package org.matrix.matrixandroidsdk;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.View;
import android.widget.EditText;

import org.matrix.matrixandroidsdk.store.LoginStorage;


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
                onLoginClick(username, password);
            }
        });
    }

    private void onLoginClick(String username, String password) {
        // TODO submit credentials then save via LoginStorage.saveCredentials(Credentials)
        goToHomepage();
    }

    private boolean hasCredentials() {
        LoginStorage ls = new LoginStorage(this);
        return ls.getDefaultCredentials() != null;
    }


    private void goToHomepage() {
        startActivity(new Intent(this, HomeActivity.class));
    }
}
