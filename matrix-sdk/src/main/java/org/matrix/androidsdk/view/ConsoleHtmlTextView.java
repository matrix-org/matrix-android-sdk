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
package org.matrix.androidsdk.view;

import android.content.Context;
import android.text.Html;
import android.util.AttributeSet;
import android.util.Log;

import org.sufficientlysecure.htmltextview.HtmlLocalImageGetter;
import org.sufficientlysecure.htmltextview.HtmlRemoteImageGetter;
import org.sufficientlysecure.htmltextview.HtmlTagHandler;
import org.sufficientlysecure.htmltextview.HtmlTextView;
import org.sufficientlysecure.htmltextview.JellyBeanSpanFixTextView;
import org.sufficientlysecure.htmltextview.LocalLinkMovementMethod;

public class ConsoleHtmlTextView extends JellyBeanSpanFixTextView {

    public static final String TAG = "ConsoleHtmlTextView";


    public ConsoleHtmlTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public ConsoleHtmlTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ConsoleHtmlTextView(Context context) {
        super(context);
    }

    /**
     * Parses String containing HTML to Android's Spannable format and displays it in this TextView.
     *
     * @param html String containing HTML, for example: "<b>Hello world!</b>"
     */
    public void setHtmlFromString(String html, Html.ImageGetter imageGetter) {
        Html.ImageGetter htmlImageGetter;
        if (imageGetter instanceof HtmlTextView.LocalImageGetter) {
            htmlImageGetter = new HtmlLocalImageGetter(getContext());
        } else if (imageGetter instanceof HtmlTextView.RemoteImageGetter) {
            htmlImageGetter = new HtmlRemoteImageGetter(this,
                    ((HtmlTextView.RemoteImageGetter) imageGetter).baseUrl);
        } else {
            Log.e(TAG, "Wrong imageGetter!");
            return;
        }

        // this uses Android's Html class for basic parsing, and HtmlTagHandler
        final HtmlTagHandler htmlTagHandler = new HtmlTagHandler();
        setText(Html.fromHtml(html, htmlImageGetter, htmlTagHandler));

        // make links work
        setMovementMethod(LocalLinkMovementMethod.getInstance());
    }
}
