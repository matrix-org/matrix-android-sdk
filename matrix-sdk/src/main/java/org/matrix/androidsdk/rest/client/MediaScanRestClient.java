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

import android.support.annotation.Nullable;

import org.matrix.androidsdk.HomeServerConnectionConfig;
import org.matrix.androidsdk.RestClient;
import org.matrix.androidsdk.data.store.IMXStore;
import org.matrix.androidsdk.rest.api.MediaScanApi;
import org.matrix.androidsdk.rest.callback.ApiCallback;
import org.matrix.androidsdk.rest.callback.DefaultRetrofit2CallbackWrapper;
import org.matrix.androidsdk.rest.callback.SimpleApiCallback;
import org.matrix.androidsdk.rest.model.EncryptedMediaScanBody;
import org.matrix.androidsdk.rest.model.MatrixError;
import org.matrix.androidsdk.rest.model.MediaScanPublicKeyResult;
import org.matrix.androidsdk.rest.model.MediaScanResult;

/**
 * Class used to make requests to the anti-virus scanner API.
 */
public class MediaScanRestClient extends RestClient<MediaScanApi> {

    @Nullable
    private IMXStore mMxStore;

    /**
     * {@inheritDoc}
     */
    public MediaScanRestClient(HomeServerConnectionConfig hsConfig) {
        super(hsConfig, MediaScanApi.class, RestClient.URI_API_PREFIX_PATH_MEDIA_PROXY_UNSTABLE, false, EndPointServer.ANTIVIRUS_SERVER);
    }

    /**
     * Set MxStore instance
     *
     * @param mxStore
     */
    public void setMxStore(IMXStore mxStore) {
        mMxStore = mxStore;
    }

    /**
     * Get the current public curve25519 key that the AV server is advertising.
     * Read the value form cache if any
     *
     * @param callback on success callback containing the server public key
     */
    public void getServerPublicKey(final ApiCallback<String> callback) {
        if (mMxStore == null) {
            callback.onUnexpectedError(new Exception("MxStore not configured"));
            return;
        }

        // Check in cache
        String keyFromCache = mMxStore.getAntivirusServerPublicKey();
        if (keyFromCache != null) {
            callback.onSuccess(keyFromCache);
        } else {
            mApi.getServerPublicKey().enqueue(new DefaultRetrofit2CallbackWrapper<>(new SimpleApiCallback<MediaScanPublicKeyResult>(callback) {
                @Override
                public void onSuccess(MediaScanPublicKeyResult info) {
                    // Store the key in cache for next times
                    mMxStore.setAntivirusServerPublicKey(info.mCurve25519PublicKey);

                    callback.onSuccess(info.mCurve25519PublicKey);
                }

                @Override
                public void onMatrixError(MatrixError e) {
                    if (e.mStatus == 404) {
                        // On 404 consider the public key is not available, so do not encrypt body
                        mMxStore.setAntivirusServerPublicKey("");

                        callback.onSuccess("");
                    } else {
                        super.onMatrixError(e);
                    }
                }
            }));
        }
    }

    /**
     * Reset Antivirus server public key on cache
     */
    public void resetServerPublicKey() {
        if (mMxStore != null) {
            mMxStore.setAntivirusServerPublicKey(null);
        }
    }

    /**
     * Scan an unencrypted file.
     *
     * @param domain   the server name extracted from the matrix content uri
     * @param mediaId  the media id extracted from the matrix content uri
     * @param callback on success callback containing a MediaScanResult object
     */
    public void scanUnencryptedFile(final String domain, final String mediaId, final ApiCallback<MediaScanResult> callback) {
        mApi.scanUnencrypted(domain, mediaId).enqueue(new DefaultRetrofit2CallbackWrapper<>(callback));
    }

    /**
     * Scan an encrypted file.
     *
     * @param encryptedMediaScanBody the encryption information required to decrypt the content before scanning it.
     * @param callback               on success callback containing a MediaScanResult object
     */
    public void scanEncryptedFile(final EncryptedMediaScanBody encryptedMediaScanBody, final ApiCallback<MediaScanResult> callback) {
        // TODO Encrypt encryptedMediaScanBody?


        mApi.scanEncrypted(encryptedMediaScanBody).enqueue(new DefaultRetrofit2CallbackWrapper<>(callback));
    }
}
