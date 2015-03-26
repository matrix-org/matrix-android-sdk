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

import java.io.File;
import java.io.OutputStream;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.KeyEvent;

import android.view.View;
import android.webkit.WebView;
import android.widget.ImageView;

import org.matrix.matrixandroidsdk.R;
import org.matrix.matrixandroidsdk.db.ConsoleMediasCache;
import org.matrix.matrixandroidsdk.view.PieFractionView;

public class ImageWebViewActivity extends Activity {
    private static final String LOG_TAG = "ImageWebViewActivity";

    public static final String KEY_HIGHRES_IMAGE_URI = "org.matrix.matrixandroidsdk.activity.ImageWebViewActivity.KEY_HIGHRES_IMAGE_URI";
    public static final String KEY_THUMBNAIL_WIDTH = "org.matrix.matrixandroidsdk.activity.ImageWebViewActivity.KEY_THUMBNAIL_WIDTH";
    public static final String KEY_THUMBNAIL_HEIGHT = "org.matrix.matrixandroidsdk.activity.ImageWebViewActivity.KEY_THUMBNAIL_HEIGHT";
    public static final String KEY_HIGHRES_MIME_TYPE = "org.matrix.matrixandroidsdk.activity.ImageWebViewActivity.KEY_HIGHRES_MIME_TYPE";
    public static final String KEY_IMAGE_ROTATION = "org.matrix.matrixandroidsdk.activity.ImageWebViewActivity.KEY_IMAGE_ROTATION";
    private WebView mWebView;

    private int mRotationAngle = 0;
    private String mThumbnailUri = null;
    private String mHighResUri = null;
    private String mHighResMimeType = null;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_web_view);

        mWebView = (WebView)findViewById(R.id.image_webview);
        
        final Intent intent = getIntent();
        if (intent == null) {
            Log.e(LOG_TAG, "Need an intent to view.");
            finish();
            return;
        }

        mHighResUri = intent.getStringExtra(KEY_HIGHRES_IMAGE_URI);
        mRotationAngle = intent.getIntExtra(KEY_IMAGE_ROTATION, 0);
        mHighResMimeType = intent.getStringExtra(KEY_HIGHRES_MIME_TYPE);

        if (mHighResUri == null) {
            Log.e(LOG_TAG, "No Image URI");
            finish();
            return;
        }

        int thumbnailWidth = intent.getIntExtra(KEY_THUMBNAIL_WIDTH, 0);
        int thumbnailHeight = intent.getIntExtra(KEY_THUMBNAIL_HEIGHT, 0);

        if ((thumbnailWidth <= 0) || (thumbnailHeight <= 0)) {
            Log.e(LOG_TAG, "Invalid thumbnail size");
            finish();
            return;
        }

        String css = "body { background-color: #000; height: 100%; width: 100%; margin: 0px; padding: 0px; }" +
                ".wrap { position: absolute; left: 0px; right: 0px; width: 100%; height: 100%; " +
                "display: -webkit-box; -webkit-box-pack: center; -webkit-box-align: center; " +
                "display: box; box-pack: center; box-align: center; } ";

        if (mRotationAngle != 0) css += "#image { " + calcCssRotation(mRotationAngle) + " } ";
        if (mRotationAngle != 0) css += "#thumbnail { " + calcCssRotation(mRotationAngle) + " } ";

        final String fcss= css;
        final String viewportContent = "width=640";

        final PieFractionView pieFractionView = (PieFractionView)findViewById(R.id.download_zoomed_image_piechart);

        String path = ConsoleMediasCache.mediaCacheFilename(this, mHighResUri, mHighResMimeType);

        // is the high picture already downloaded ?
        if (null != path) {
            mThumbnailUri = mHighResUri = "file://" + (new File(this.getFilesDir(), path)).getPath();
            pieFractionView.setVisibility(View.GONE);
        } else {
            mThumbnailUri = null;

            // try to retrieve the thumbnail
            path = ConsoleMediasCache.mediaCacheFilename(this, mHighResUri, thumbnailWidth, thumbnailHeight, null);
            if (null == path) {
                Log.e(LOG_TAG, "No Image thumbnail");
                finish();
                return;
            }

            final String loadingUri = mHighResUri;
            mThumbnailUri = mHighResUri = "file://" + (new File(this.getFilesDir(), path)).getPath();

            final String downloadId = ConsoleMediasCache.loadBitmap(this, loadingUri, mRotationAngle, mHighResMimeType);

            if (null != downloadId) {
                pieFractionView.setFraction(ConsoleMediasCache.progressValueForDownloadId(downloadId));

                ConsoleMediasCache.addDownloadListener(downloadId, new ConsoleMediasCache.DownloadCallback() {
                    @Override
                    public void onDownloadProgress(String aDownloadId, int percentageProgress) {
                        if (aDownloadId.equals(downloadId)) {
                            pieFractionView.setFraction(percentageProgress);
                        }
                    }

                    @Override
                    public void onDownloadComplete(String aDownloadId) {
                        if (aDownloadId.equals(downloadId)) {
                            pieFractionView.setVisibility(View.GONE);

                            String path = ConsoleMediasCache.mediaCacheFilename(ImageWebViewActivity.this, loadingUri, mHighResMimeType);

                            if (null != path) {
                                final File file =  new File(ImageWebViewActivity.this.getFilesDir(), path);
                                Uri uri = Uri.fromFile(file);
                                mHighResUri = uri.toString();

                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Uri mediaUri = Uri.parse(mHighResUri);

                                        // save in the gallery
                                        CommonActivityUtils.saveImageIntoGallery(ImageWebViewActivity.this, file.getName());

                                        // refresh the UI
                                        loadImage(mediaUri, viewportContent, fcss);
                                    }
                                });
                            }
                        }
                    }
                });
            }
        }

        mWebView.getSettings().setJavaScriptEnabled(true);
        mWebView.getSettings().setLoadWithOverviewMode(true);
        mWebView.getSettings().setUseWideViewPort(true);
        mWebView.getSettings().setBuiltInZoomControls(true);

        loadImage(Uri.parse(mHighResUri), viewportContent, css);
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if ( keyCode == KeyEvent.KEYCODE_MENU ) {
            // This is to fix a bug in the v7 support lib. If there is no options menu and you hit MENU, it will crash with a
            // NPE @ android.support.v7.app.ActionBarImplICS.getThemedContext(ActionBarImplICS.java:274)
            // This can safely be removed if we add in menu options on this screen
            return true;
        }
        return super.onKeyDown(keyCode, event);
    } 
    
    private void loadImage(Uri imageUri, String viewportContent, String css) {
        String html =
                "<html><head><meta name='viewport' content='" +
                        viewportContent +
                        "'/>" +
                        "<style type='text/css'>" +
                        css +
                        "</style></head>" +
                        "<body> <div class='wrap'>" + "<img " +
                        ( "src='" + imageUri.toString() + "'") +
                        " onerror='this.style.display=\"none\"' id='image' " + viewportContent + "/>" + "</div>" +
                        "</body>" + "</html>";

        String mime = "text/html";
        String encoding = "utf-8";

        Log.i(LOG_TAG, html);
        mWebView.loadDataWithBaseURL(null, html, mime, encoding, null);
        mWebView.requestLayout();
    }
    
    private String calcCssRotation(int rot) {
        if (rot == 90 || rot == 180 || rot == 270) {
            // we hardcode these as strings rather than building them up programatically just to make it easier to fiddle them
            // for particular cases (currently)
            final String rot90 = "-wekbkit-transform-origin: 50% 50%; -webkit-transform: rotate(90deg);";
            final String rot180 = "-webkit-transform: rotate(180deg);";
            final String rot270 = "-wekbkit-transform-origin: 50% 50%; -webkit-transform: rotate(270deg);";

            switch (rot) {
            case 90:
                return rot90;
            case 180:
                return rot180;
            case 270:
                return rot270;
            }
        }
        return "";
    }
}
