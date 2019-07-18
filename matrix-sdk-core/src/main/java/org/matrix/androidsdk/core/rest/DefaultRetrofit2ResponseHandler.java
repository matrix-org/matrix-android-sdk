/*
 * Copyright 2018 New Vector Ltd
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

package org.matrix.androidsdk.core.rest;

import org.matrix.androidsdk.core.model.HttpError;

import java.io.IOException;

import retrofit2.Response;

public class DefaultRetrofit2ResponseHandler {
    public static <T> void handleResponse(Response<T> response, Listener<T> listener)
            throws IOException {
        if (response.isSuccessful()) {
            listener.onSuccess(response);
        } else {
            String errorBody = response.errorBody().string();
            listener.onHttpError(new HttpError(errorBody, response.code()));
        }
    }

    public interface Listener<T> {
        void onSuccess(Response<T> response);

        void onHttpError(HttpError httpError);
    }
}
