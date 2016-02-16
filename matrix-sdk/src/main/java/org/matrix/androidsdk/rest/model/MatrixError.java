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
package org.matrix.androidsdk.rest.model;

/**
 * Represents a standard error response.
 */
public class MatrixError implements java.io.Serializable {
    public static final String FORBIDDEN = "M_FORBIDDEN";
    public static final String UNKNOWN_TOKEN = "M_UNKNOWN_TOKEN";
    public static final String BAD_JSON = "M_BAD_JSON";
    public static final String NOT_JSON = "M_NOT_JSON";
    public static final String NOT_FOUND = "M_NOT_FOUND";
    public static final String LIMIT_EXCEEDED = "M_LIMIT_EXCEEDED";
    public static final String TOO_LARGE = "M_TOO_LARGE";
    public static final String USER_IN_USE = "M_USER_IN_USE";
    public static final String ROOM_IN_USE = "M_ROOM_IN_USE";
    public static final String BAD_PAGINATION = "M_BAD_PAGINATION";
    public static final String UNKNOWN= "M_UNKNOWN";

    public String errcode;
    public String error;
    public Integer retry_after_ms;

    public String getLocalizedMessage() {
        String localizedMessage = "";

        if (null != error) {
            localizedMessage += error;
        }

        if (null != errcode) {
            localizedMessage += "(" +  errcode + ")";
        }

        return localizedMessage;
    }

    /**
     * @return true if the error code is a supported one
     */
    public boolean isSupportedErrorCode () {
       return   MatrixError.FORBIDDEN.equals(errcode) ||
                MatrixError.UNKNOWN_TOKEN.equals(errcode) ||
                MatrixError.BAD_JSON.equals(errcode) ||
                MatrixError.NOT_JSON.equals(errcode) ||
                MatrixError.NOT_FOUND.equals(errcode) ||
                MatrixError.LIMIT_EXCEEDED.equals(errcode) ||
                MatrixError.USER_IN_USE.equals(errcode) ||
                MatrixError.ROOM_IN_USE.equals(errcode) ||
                MatrixError.TOO_LARGE.equals(errcode) ||
                MatrixError.BAD_PAGINATION.equals(errcode);
    }
}
