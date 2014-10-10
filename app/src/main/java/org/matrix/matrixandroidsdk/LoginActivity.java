package org.matrix.matrixandroidsdk;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.View;


public class LoginActivity extends ActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        findViewById(R.id.button_login).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onLoginClick();
            }
        });
    }

    private void onLoginClick() {
        // TODO submit credentials
        goToHomepage();
    }

    private void goToHomepage() {
        startActivity(new Intent(this, HomeActivity.class));
    }
}
