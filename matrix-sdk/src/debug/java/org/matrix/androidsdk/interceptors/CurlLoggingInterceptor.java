/*
 * Copyright (C) 2016 Jeff Gilfelt.
 * Copyright 2018 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.matrix.androidsdk.interceptors;

import java.io.IOException;
import java.nio.charset.Charset;

import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import okio.Buffer;

/**
 * An OkHttp interceptor that logs requests as curl shell commands. They can then
 * be copied, pasted and executed inside a terminal environment. This might be
 * useful for troubleshooting client/server API interaction during development,
 * making it easy to isolate and share requests made by the app. <p> Warning: The
 * logs generated by this interceptor have the potential to leak sensitive
 * information. It should only be used in a controlled manner or in a
 * non-production environment.
 */
public class CurlLoggingInterceptor implements Interceptor {

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private final HttpLoggingInterceptor.Logger logger;
    private String curlOptions;

    public CurlLoggingInterceptor() {
        this(HttpLoggingInterceptor.Logger.DEFAULT);
    }

    public CurlLoggingInterceptor(HttpLoggingInterceptor.Logger logger) {
        this.logger = logger;
    }

    /**
     * Set any additional curl command options (see 'curl --help').
     */
    public void setCurlOptions(String curlOptions) {
        this.curlOptions = curlOptions;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();

        boolean compressed = false;

        String curlCmd = "curl";
        if (curlOptions != null) {
            curlCmd += " " + curlOptions;
        }
        curlCmd += " -X " + request.method();

        RequestBody requestBody = request.body();
        if (requestBody != null) {
            Buffer buffer = new Buffer();
            requestBody.writeTo(buffer);
            Charset charset = UTF8;
            MediaType contentType = requestBody.contentType();
            if (contentType != null) {
                charset = contentType.charset(UTF8);
            }
            // try to keep to a single line and use a subshell to preserve any line breaks
            curlCmd += " --data $'" + buffer.readString(charset).replace("\n", "\\n") + "'";
        }

        Headers headers = request.headers();
        for (int i = 0, count = headers.size(); i < count; i++) {
            String name = headers.name(i);
            String value = headers.value(i);
            if ("Accept-Encoding".equalsIgnoreCase(name) && "gzip".equalsIgnoreCase(value)) {
                compressed = true;
            }
            curlCmd += " -H " + "\"" + name + ": " + value + "\"";
        }

        curlCmd += ((compressed) ? " --compressed " : " ") + request.url().toString()
                // Escape '!' because it is interpreted by the shell prompt
                .replaceAll("!", "\\!")
                // Replace localhost for emulator by localhost for shell
                .replaceAll("://10.0.2.2:8080/", "://127.0.0.1:8080/");

        // Add Json formatting
        curlCmd += " | python -m json.tool";

        logger.log("--- cURL (" + request.url() + ")");
        logger.log(curlCmd);

        return chain.proceed(request);
    }
}