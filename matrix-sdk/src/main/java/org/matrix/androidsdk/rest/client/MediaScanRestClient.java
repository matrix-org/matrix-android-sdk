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
package org.matrix.androidsdk.rest.client;

import org.matrix.androidsdk.HomeServerConnectionConfig;
import org.matrix.androidsdk.RestClient;
import org.matrix.androidsdk.rest.api.MediaScanApi;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.DefaultRetrofit2CallbackWrapper;
import org.matrix.androidsdk.rest.model.MediaScanResult;
import org.matrix.androidsdk.rest.model.crypto.EncryptedFileInfo;

/**
 * Class used to make requests to the anti-virus scanner API.
 */
public class MediaScanRestClient extends RestClient<MediaScanApi> {

    /**
     * {@inheritDoc}
     */
    public MediaScanRestClient(HomeServerConnectionConfig hsConfig) {
        super(hsConfig, MediaScanApi.class, RestClient.URI_API_PREFIX_PATH_MEDIA_PROXY_UNSTABLE, false, EndPointServer.ANTIVIRUS_SERVER);
    }

    /**
     * Scan an unencrypted file.
     *
     * @param domain   the server name extracted from the matrix content uri
     * @param mediaId   the media id extracted from the matrix content uri
     * @param callback on success callback containing a MediaScanResult object
     */
    public void scanUnencryptedFile(final String domain, final String mediaId, final ApiCallback<MediaScanResult> callback) {

        mApi.scanUnencrypted(domain, mediaId).enqueue(new DefaultRetrofit2CallbackWrapper<>(callback));
    }

    /**
     * Scan an encrypted file.
     *
     * @param encryptedFileInfo the encryption information
     * @param callback on success callback containing a MediaScanResult object
     */
    public void scanEncryptedFile(final EncryptedFileInfo encryptedFileInfo, final ApiCallback<MediaScanResult> callback) {

        mApi.scanEncrypted(encryptedFileInfo).enqueue(new DefaultRetrofit2CallbackWrapper<>(callback));
    }
}
