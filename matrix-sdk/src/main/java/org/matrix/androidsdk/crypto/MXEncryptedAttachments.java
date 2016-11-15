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

package org.matrix.androidsdk.crypto;

import android.util.Base64;
import android.util.Log;

import org.matrix.androidsdk.rest.model.EncryptedFileInfo;
import org.matrix.androidsdk.rest.model.EncryptedFileKey;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.security.acl.LastOwnerException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class MXEncryptedAttachments {
    private static final String LOG_TAG = "MXEncryptAtt";


    /**
     * Define the result of an encryption file
     */
    public static class EncryptionResult {
        public Cipher mCipher;
        public EncryptedFileInfo mEncryptedFileInfo;

        public EncryptionResult() {
        }
    }

    /***
     * Encrypt an attachment stream.
     * @param attachmentStream the attachment stream
     * @return the encryption file info
     */
    public static EncryptionResult encryptAttachment(InputStream attachmentStream, String mimetype, OutputStream outputStream) {
        SecureRandom secureRandom = new SecureRandom();

        // generate a random iv key
        byte[] ivBytes = new byte[16];
        secureRandom.nextBytes(ivBytes);

        byte[] key = new byte[32];
        secureRandom.nextBytes(key);

        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            SecretKeySpec secretKeySpec = new SecretKeySpec(key, "AES");
            IvParameterSpec ivParameterSpec = new IvParameterSpec(ivBytes);
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivParameterSpec);

            CipherOutputStream cos;

            cos = new CipherOutputStream(outputStream, cipher);
            byte[] data = new byte[1024];
            int read = attachmentStream.read(data);
            while (read != -1) {
                cos.write(data, 0, read);
                read = attachmentStream.read(data);
            }
            cos.flush();

            EncryptionResult result = new EncryptionResult();
            result.mCipher = cipher;
            result.mEncryptedFileInfo = new EncryptedFileInfo();
            result.mEncryptedFileInfo.key = new EncryptedFileKey();
            result.mEncryptedFileInfo.mimetype = mimetype;
            result.mEncryptedFileInfo.key.alg = "A256CTR";
            result.mEncryptedFileInfo.key.ext = true;
            result.mEncryptedFileInfo.key.key_ops = Arrays.asList("encrypt", "decrypt");
            result.mEncryptedFileInfo.key.kty = "oct";
            result.mEncryptedFileInfo.key.k = base64ToBase64Url(Base64.encodeToString(key, Base64.DEFAULT));

            result.mEncryptedFileInfo.iv = Base64.encodeToString(ivBytes, Base64.DEFAULT);

            result.mEncryptedFileInfo.hashes = new HashMap();
            result.mEncryptedFileInfo.hashes.put("sha256", base64ToUnpaddedBase64(Base64.encodeToString(cipher.doFinal(), Base64.DEFAULT)));
            return result;
        } catch (Exception e) {
            Log.e(LOG_TAG, "## encryptAttachment failed " + e.getMessage());
        }

        return null;
    }

    /**
     * Decrypt an attachment stream.
     * @param attachmentStream the attachment stream to decode
     * @param encryptedFileInfo the encryption info
     * @return the decrypted stream
     */
    public static FileOutputStream encryptAttachment(FileInputStream attachmentStream, EncryptedFileInfo encryptedFileInfo) {
        return null;
    }


    public static String base64UrlToBase64(String base64Url) {
        if (null != base64Url) {
            base64Url = base64Url.replaceAll("-", "+");
            base64Url = base64Url.replaceAll("_", "/");
        }

        return base64Url;
    }

    public static String base64ToBase64Url(String base64) {
        if (null != base64) {
            base64 = base64.replaceAll("\\+", "-");
            base64 = base64.replaceAll("/", "_");
        }

        return base64;
    }

    public static String base64ToUnpaddedBase64(String base64) {
        if (null != base64) {
            base64 = base64.replaceAll("=", "");
        }

        return base64;
    }
}