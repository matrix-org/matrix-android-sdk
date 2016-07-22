/*
 * Copyright 2016 OpenMarket Ltd
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

package org.matrix.androidsdk.listeners;

import org.matrix.androidsdk.rest.model.ContentResponse;

/**
 * Interface to monitor a media upload.
 */
public interface IMXMediaUploadListener {
    /**
     * Warn of the upload starts
     * @param uploadId the upload Identifier
     */
    void onUploadStart(String uploadId);

    /**
     * Warn of the progress upload
     * @param uploadId the upload Identifier
     * @param percentageProgress the progress value
     */
    void onUploadProgress(String uploadId, int percentageProgress);

    /**
     * warn that an upload has been cancelled.
     * @param uploadId
     */
    void onUploadCancel(String uploadId);

    /**
     * Called when the upload is complete or has failed.
     * @param uploadResponse the ContentResponse object containing the mxc URI or null if the upload failed
     */
    void onUploadComplete(String uploadId, ContentResponse uploadResponse, int serverResponseCode, String serverErrorMessage);
}
