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

import org.matrix.androidsdk.core.callback.ApiCallback;
import org.matrix.androidsdk.core.model.HttpError;
import org.matrix.androidsdk.core.model.HttpException;

import java.io.IOException;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DefaultRetrofit2CallbackWrapper<T>
        implements Callback<T>, DefaultRetrofit2ResponseHandler.Listener<T> {

    private final ApiCallback<T> apiCallback;

    public DefaultRetrofit2CallbackWrapper(ApiCallback<T> apiCallback) {
        this.apiCallback = apiCallback;
    }

    public ApiCallback<T> getApiCallback() {
        return apiCallback;
    }

    @Override
    public void onResponse(Call<T> call, Response<T> response) {
        try {
            handleResponse(response);
        } catch (IOException e) {
            apiCallback.onUnexpectedError(e);
        }
    }

    private void handleResponse(Response<T> response) throws IOException {
        DefaultRetrofit2ResponseHandler.handleResponse(response, this);
    }

    @Override
    public void onFailure(Call<T> call, Throwable t) {
        apiCallback.onNetworkError((Exception) t);
    }

    @Override
    public void onSuccess(Response<T> response) {
        apiCallback.onSuccess(response.body());
    }

    @Override
    public void onHttpError(HttpError httpError) {
        apiCallback.onNetworkError(new HttpException(httpError));
    }
}
