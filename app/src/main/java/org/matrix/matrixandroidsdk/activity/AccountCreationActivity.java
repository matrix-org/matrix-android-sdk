package org.matrix.matrixandroidsdk.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.matrix.matrixandroidsdk.R;

import java.net.URLDecoder;
import java.util.HashMap;

public class AccountCreationActivity extends Activity {
    public static String EXTRA_HOME_SERVER_ID = "org.matrix.matrixandroidsdk.activity.EXTRA_HOME_SERVER_ID";

    WebView mWebView = null;

    @Override
    protected void onCreate(Bundle savedInstanceState)  {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_account_creation);

        mWebView = (WebView) findViewById(R.id.account_creation_webview);
        mWebView.getSettings().setJavaScriptEnabled(true);

        Intent intent = getIntent();

        // if the home server path is provided
        // use it
        if (intent.hasExtra(EXTRA_HOME_SERVER_ID)) {
            mWebView.loadUrl(intent.getStringExtra(EXTRA_HOME_SERVER_ID) + "/_matrix/static/client/register/");
        } else {
            // use a default one
            mWebView.loadUrl("https://matrix.org/_matrix/static/client/register/");
        }

        mWebView.setWebViewClient(new WebViewClient(){
            @Override
            public void onPageFinished(WebView view, String url) {
                // avoid infinite onPageFinished call
                if (url.startsWith("http")) {
                    // Generic method to make a bridge between JS and the UIWebView
                    final String MXCJavascriptSendObjectMessage = "javascript:window.matrixRegistration.sendObjectMessage = function(parameters) { var iframe = document.createElement('iframe');  iframe.setAttribute('src', 'js:' + JSON.stringify(parameters));  document.documentElement.appendChild(iframe); iframe.parentNode.removeChild(iframe); iframe = null; };";

                    view.loadUrl(MXCJavascriptSendObjectMessage);

                    // The function the fallback page calls when the registration is complete
                    final String MXCJavascriptOnRegistered = "javascript:window.matrixRegistration.onRegistered = function(homeserverUrl, userId, accessToken) { matrixRegistration.sendObjectMessage({ 'action': 'onRegistered', 'homeServer': homeserverUrl,'userId': userId,  'accessToken': accessToken  }); };";

                    view.loadUrl(MXCJavascriptOnRegistered);
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(android.webkit.WebView view, java.lang.String url) {

                if ((null != url) &&  url.startsWith("js:")) {
                    String json = url.substring(3);
                    HashMap<String, String> parameters = null;

                    try {
                        // URL decode
                        json = URLDecoder.decode(json, "UTF-8");
                        parameters = new Gson().fromJson(json, new TypeToken<HashMap<String, String>>() {}.getType());

                    } catch (Exception e) {
                    }

                    // succeeds to parse parameters
                    if (null != parameters) {
                        // check the required paramaters
                        if (parameters.containsKey("homeServer") && parameters.containsKey("userId") && parameters.containsKey("accessToken") && parameters.containsKey("action")) {
                            final String homeServer = parameters.get("homeServer");
                            final String userId =  parameters.get("userId");
                            final String accessToken =  parameters.get("accessToken");
                            String action =  parameters.get("action");

                            // check the action
                            if (action.equals("onRegistered")) {
                                AccountCreationActivity.this.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Intent returnIntent = new Intent();
                                        returnIntent.putExtra("homeServer", homeServer);
                                        returnIntent.putExtra("userId", userId);
                                        returnIntent.putExtra("accessToken", accessToken);
                                        setResult(RESULT_OK, returnIntent);

                                        AccountCreationActivity.this.finish();
                                    }
                                });
                            }
                        }
                    }
                    return true;
                }
                return true;
            }
        });
    }
}
