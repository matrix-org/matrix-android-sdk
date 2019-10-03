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

import androidx.annotation.Nullable;
import android.text.TextUtils;

import org.matrix.androidsdk.HomeServerConnectionConfig;
import org.matrix.androidsdk.RestClient;
import org.matrix.androidsdk.core.JsonUtility;
import org.matrix.androidsdk.core.JsonUtils;
import org.matrix.androidsdk.core.callback.ApiCallback;
import org.matrix.androidsdk.core.callback.SimpleApiCallback;
import org.matrix.androidsdk.core.model.MatrixError;
import org.matrix.androidsdk.crypto.model.crypto.EncryptedBodyFileInfo;
import org.matrix.androidsdk.data.store.IMXStore;
import org.matrix.androidsdk.rest.api.MediaScanApi;
import org.matrix.androidsdk.rest.callback.RestAdapterCallback;
import org.matrix.androidsdk.rest.model.EncryptedMediaScanBody;
import org.matrix.androidsdk.rest.model.EncryptedMediaScanEncryptedBody;
import org.matrix.androidsdk.rest.model.MediaScanError;
import org.matrix.androidsdk.rest.model.MediaScanPublicKeyResult;
import org.matrix.androidsdk.rest.model.MediaScanResult;
import org.matrix.olm.OlmException;
import org.matrix.olm.OlmPkEncryption;
import org.matrix.olm.OlmPkMessage;

import java.net.HttpURLConnection;

import retrofit2.Call;

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
        super(hsConfig, MediaScanApi.class, RestClient.URI_API_PREFIX_PATH_MEDIA_PROXY_UNSTABLE, JsonUtils.getGson(false), EndPointServer.ANTIVIRUS_SERVER);
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
     * Read the value from cache if any
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
            mApi.getServerPublicKey()
                    .enqueue(new RestAdapterCallback<>("getServerPublicKey",
                            null,
                            new SimpleApiCallback<MediaScanPublicKeyResult>(callback) {
                                @Override
                                public void onSuccess(MediaScanPublicKeyResult info) {
                                    // Store the key in cache for next times
                                    mMxStore.setAntivirusServerPublicKey(info.mCurve25519PublicKey);

                                    // Note: for some reason info.mCurve25519PublicKey may be null
                                    if (info.mCurve25519PublicKey != null) {
                                        callback.onSuccess(info.mCurve25519PublicKey);
                                    } else {
                                        callback.onUnexpectedError(new Exception("Unable to get server public key from Json"));
                                    }
                                }

                                @Override
                                public void onMatrixError(MatrixError e) {
                                    // Old Antivirus scanner instance will return a 404
                                    if (e.mStatus == HttpURLConnection.HTTP_NOT_FOUND) {
                                        // On 404 consider the public key is not available, so do not encrypt body
                                        mMxStore.setAntivirusServerPublicKey("");

                                        callback.onSuccess("");
                                    } else {
                                        super.onMatrixError(e);
                                    }
                                }
                            },
                            null));
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
        mApi.scanUnencrypted(domain, mediaId)
                .enqueue(new RestAdapterCallback<>("scanUnencryptedFile",
                        null,
                        callback,
                        null));
    }

    /**
     * Scan an encrypted file.
     *
     * @param encryptedMediaScanBody the encryption information required to decrypt the content before scanning it.
     * @param callback               on success callback containing a MediaScanResult object
     */
    public void scanEncryptedFile(final EncryptedMediaScanBody encryptedMediaScanBody, final ApiCallback<MediaScanResult> callback) {
        // Encrypt encryptedMediaScanBody if the server support it
        getServerPublicKey(new SimpleApiCallback<String>(callback) {
            @Override
            public void onSuccess(String serverPublicKey) {
                Call<MediaScanResult> request;

                // Encrypt the data, if antivirus server supports it
                if (!TextUtils.isEmpty(serverPublicKey)) {
                    try {
                        OlmPkEncryption olmPkEncryption = new OlmPkEncryption();
                        olmPkEncryption.setRecipientKey(serverPublicKey);

                        String data = JsonUtility.getCanonicalizedJsonString(encryptedMediaScanBody);

                        OlmPkMessage message = olmPkEncryption.encrypt(data);

                        EncryptedMediaScanEncryptedBody encryptedMediaScanEncryptedBody = new EncryptedMediaScanEncryptedBody();
                        encryptedMediaScanEncryptedBody.encryptedBodyFileInfo = new EncryptedBodyFileInfo(message);

                        request = mApi.scanEncrypted(encryptedMediaScanEncryptedBody);
                    } catch (OlmException e) {
                        // should not happen. Send the error to the caller
                        request = null;
                        callback.onUnexpectedError(e);
                    }
                } else {
                    // No public key on this server, do not encrypt data
                    request = mApi.scanEncrypted(encryptedMediaScanBody);
                }

                if (request != null) {
                    request.enqueue(new RestAdapterCallback<>("scanEncryptedFile",
                            null,
                            new SimpleApiCallback<MediaScanResult>(callback) {
                                @Override
                                public void onSuccess(MediaScanResult scanResult) {
                                    callback.onSuccess(scanResult);
                                }

                                @Override
                                public void onMatrixError(MatrixError e) {
                                    // Check whether the provided encrypted_body could not be decrypted.
                                    if (e.mStatus == HttpURLConnection.HTTP_FORBIDDEN) {
                                        MediaScanError mcsError;
                                        try {
                                            mcsError = JsonUtils.getGson(false).fromJson(e.mErrorBodyAsString, MediaScanError.class);
                                        } catch (Exception exc) {
                                            mcsError = null;
                                        }
                                        if (mcsError != null && MediaScanError.MCS_BAD_DECRYPTION.equals(mcsError.reason)) {
                                            // The client should request again the public key of the server.
                                            resetServerPublicKey();
                                        }
                                    }

                                    super.onMatrixError(e);
                                }
                            },
                            null));
                }
            }
        });
    }
}
