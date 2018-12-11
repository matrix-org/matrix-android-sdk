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
package org.matrix.androidsdk.rest.model;

/**
 * Class to contain a matrix content scanner error.
 */
public class MediaScanError {
    public static final String MCS_MEDIA_REQUEST_FAILED = "MCS_MEDIA_REQUEST_FAILED";
    public static final String MCS_MEDIA_FAILED_TO_DECRYPT = "MCS_MEDIA_FAILED_TO_DECRYPT";
    public static final String MCS_MEDIA_NOT_CLEAN = "MCS_MEDIA_NOT_CLEAN";
    public static final String MCS_BAD_DECRYPTION = "MCS_BAD_DECRYPTION";
    public static final String MCS_MALFORMED_JSON = "MCS_MALFORMED_JSON";

    // The error description (Human-readable information about the error)
    public String info;
    // The error reason
    public String reason;
}
