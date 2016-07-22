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

import com.google.gson.JsonElement;

/**
 * Interface to monitor a media download.
 */
public interface IMXMediasDownloadListener {

    /**
     * The download start
     *
     * @param downloadId the download Identifier
     */
    void onDownloadStart(String downloadId);

    /**
     * Warn of the progress download
     *
     * @param downloadId         the download Identifier
     * @param percentageProgress the progress value
     */
    void onDownloadProgress(String downloadId, int percentageProgress);

    /**
     * Called when the download is complete or has failed.
     *
     * @param downloadId the download Identifier
     */
    void onDownloadComplete(String downloadId);

    /**
     * An error has been returned by the server
     * @param downloadId  the download Identifier
     * @param jsonElement the error
     */
    void onDownloadError(String downloadId, JsonElement jsonElement);

    /**
     * A downloaded has been cancelled.
     * @param downloadId  the download Identifier
     */
    void onDownloadCancel(String downloadId);
}
