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
package org.matrix.androidsdk.rest.api;

import org.matrix.androidsdk.rest.model.MediaScanResult;
import org.matrix.androidsdk.rest.model.crypto.EncryptedFileInfo;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

/**
 * The matrix content scanner REST API.
 */
public interface MediaScanApi {
    /**
     * Scan an unencrypted file.
     *
     * @param domain the server name
     * @param mediaId the user id
     */
    @GET("scan/{domain}/{mediaId}")
    Call<MediaScanResult> scanUnencrypted(@Path("domain") String domain, @Path("mediaId") String mediaId);

    /**
     * Scan an encrypted file.
     *
     * @param encryptedFileInfo the encrypted file information
     */
    @POST("scan_encrypted")
    Call<MediaScanResult> scanEncrypted(@Body EncryptedFileInfo encryptedFileInfo);
}
