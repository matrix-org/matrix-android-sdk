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
package org.matrix.androidsdk.core.model;

public final class HttpError {
    private final String errorBody;
    private final int httpCode;

    public HttpError(String errorBody, int httpCode) {
        this.errorBody = errorBody;
        this.httpCode = httpCode;
    }

    public String getErrorBody() {
        return errorBody;
    }

    public int getHttpCode() {
        return httpCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        HttpError httpError = (HttpError) o;

        if (httpCode != httpError.httpCode) return false;
        return errorBody != null ?
                errorBody.equals(httpError.errorBody) :
                httpError.errorBody == null;
    }

    @Override
    public int hashCode() {
        int result = errorBody != null ? errorBody.hashCode() : 0;
        result = 31 * result + httpCode;
        return result;
    }

    @Override
    public String toString() {
        return "HttpError{" +
                "errorBody='" + errorBody + '\'' +
                ", httpCode=" + httpCode +
                '}';
    }
}
