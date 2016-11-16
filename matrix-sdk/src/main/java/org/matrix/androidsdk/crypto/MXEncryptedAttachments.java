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

import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import org.matrix.androidsdk.rest.model.EncryptedFileInfo;
import org.matrix.androidsdk.rest.model.EncryptedFileKey;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import java.io.Serializable;
import java.security.MessageDigest;
import java.security.SecureRandom;

import java.util.Arrays;
import java.util.HashMap;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class MXEncryptedAttachments implements Serializable {
    private static final String LOG_TAG = "MXEncryptAtt";

    /**
     * Define the result of an encryption file
     */
    public static class EncryptionResult {
        public EncryptedFileInfo mEncryptedFileInfo;
        public InputStream mEncryptedStream;

        public EncryptionResult() {
        }
    }

    /***
     * Encrypt an attachment stream.
     * @param attachmentStream the attachment stream
     * @return the encryption file info
     */
    public static EncryptionResult encryptAttachment(InputStream attachmentStream, String mimetype) {
        SecureRandom secureRandom = new SecureRandom();

        // generate a random iv key
        byte[] ivBytes = new byte[16];
        secureRandom.nextBytes(ivBytes);

        byte[] key = new byte[32];
        secureRandom.nextBytes(key);

        ByteArrayOutputStream outStream = new ByteArrayOutputStream();

        try {
            Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
            SecretKeySpec secretKeySpec = new SecretKeySpec(key, "AES");
            IvParameterSpec ivParameterSpec = new IvParameterSpec(ivBytes);
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivParameterSpec);

            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");

            byte[] data = new byte[32 * 1024];
            int read = attachmentStream.read(data);

            while (read != -1) {
                byte[] encodedBytes = cipher.update(data, 0, read);
                messageDigest.update(encodedBytes, 0, encodedBytes.length);
                outStream.write(encodedBytes);
                read = attachmentStream.read(data);
            }

            byte[] encodedBytes = cipher.doFinal();
            messageDigest.update(encodedBytes, 0, encodedBytes.length);
            outStream.write(encodedBytes);

            EncryptionResult result = new EncryptionResult();
            result.mEncryptedFileInfo = new EncryptedFileInfo();
            result.mEncryptedFileInfo.key = new EncryptedFileKey();
            result.mEncryptedFileInfo.mimetype = mimetype;
            result.mEncryptedFileInfo.key.alg = "A256CTR";
            result.mEncryptedFileInfo.key.ext = true;
            result.mEncryptedFileInfo.key.key_ops = Arrays.asList("encrypt", "decrypt");
            result.mEncryptedFileInfo.key.kty = "oct";
            result.mEncryptedFileInfo.key.k = base64ToBase64Url(Base64.encodeToString(key, Base64.DEFAULT));
            result.mEncryptedFileInfo.iv = Base64.encodeToString(ivBytes, Base64.DEFAULT).replace("\n", "").replace("=", "");

            result.mEncryptedFileInfo.hashes = new HashMap();
            result.mEncryptedFileInfo.hashes.put("sha256", base64ToUnpaddedBase64(Base64.encodeToString(messageDigest.digest(), Base64.DEFAULT)));

            result.mEncryptedStream = new ByteArrayInputStream(outStream.toByteArray());
            outStream.close();
            return result;
        } catch (Exception e) {
            Log.e(LOG_TAG, "## encryptAttachment failed " + e.getMessage());
        } catch (OutOfMemoryError oom) {
            Log.e(LOG_TAG, "## encryptAttachment failed " + oom.getMessage());
        }

        if (null != outStream) {
            try {
                outStream.close();
            } catch (Exception e) {
                Log.e(LOG_TAG, "## encryptAttachment() : fail to close outStream");
            }
        }

        return null;
    }

    /**
     * Decrypt an attachment
     * @param attachmentStream the attahcment stream
     * @param encryptedFileInfo the encryption file info
     * @return the decrypted attachment stream
     */
    public static InputStream decryptAttachment(InputStream attachmentStream, EncryptedFileInfo encryptedFileInfo) {
        // sanity checks
        if ((null == attachmentStream) || (null == encryptedFileInfo)) {
            Log.e(LOG_TAG, "## decryptAttachment() : null parameters");
            return null;
        }

        if (TextUtils.isEmpty(encryptedFileInfo.iv) ||
                (null == encryptedFileInfo.key) ||
                (null == encryptedFileInfo.hashes) ||
                !encryptedFileInfo.hashes.containsKey("sha256")
                ) {
            Log.e(LOG_TAG, "## decryptAttachment() : some fields are not defined");
            return null;
        }

        if (!TextUtils.equals(encryptedFileInfo.key.alg, "A256CTR") ||
                !TextUtils.equals(encryptedFileInfo.key.kty, "oct") ||
                TextUtils.isEmpty(encryptedFileInfo.key.k)) {
            Log.e(LOG_TAG, "## decryptAttachment() : invalid key fields");
            return null;
        }

        ByteArrayOutputStream outStream = new ByteArrayOutputStream();

        try {
            byte[] key = Base64.decode(base64UrlToBase64(encryptedFileInfo.key.k), Base64.DEFAULT);
            byte[] ivBytes =  Base64.decode(encryptedFileInfo.iv, Base64.DEFAULT);

            Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
            SecretKeySpec secretKeySpec = new SecretKeySpec(key, "AES");
            IvParameterSpec ivParameterSpec = new IvParameterSpec(ivBytes);
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec);

            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");

            byte[] data = new byte[32 * 1024];
            int read = attachmentStream.read(data);

            byte[] decodedBytes;

            while (read != -1) {
                messageDigest.update(data, 0, read);

                decodedBytes = cipher.update(data, 0, read);
                outStream.write(decodedBytes);
                read = attachmentStream.read(data);
            }

            decodedBytes = cipher.doFinal();
            messageDigest.update(decodedBytes);
            outStream.write(decodedBytes);

            String currentDigestValue = base64ToUnpaddedBase64(Base64.encodeToString(messageDigest.digest(), Base64.DEFAULT));

            if (!TextUtils.equals(encryptedFileInfo.hashes.get("sha256"), currentDigestValue)) {
                Log.e(LOG_TAG, "## decryptAttachment() :  Digest value mismatch");
                outStream.close();
                return null;
            }

            return new ByteArrayInputStream(outStream.toByteArray());
        } catch (Exception e) {
            Log.e(LOG_TAG, "## decryptAttachment() :  failed " + e.getMessage());

            try {
                outStream.close();
                outStream = null;
            } catch (Exception closeException) {
                Log.e(LOG_TAG, "## decryptAttachment() :  fail to close the file");
            }
        } catch (OutOfMemoryError oom) {
            Log.e(LOG_TAG, "## decryptAttachment() :  failed " + oom.getMessage());

            try {
                outStream.close();
                outStream = null;
            } catch (Exception closeException) {
                Log.e(LOG_TAG, "## decryptAttachment() :  fail to close the file");
            }
        }

        return null;
    }

    /**
     * Base64 URL conversion methods
     */

    private static String base64UrlToBase64(String base64Url) {
        if (null != base64Url) {
            base64Url = base64Url.replaceAll("-", "+");
            base64Url = base64Url.replaceAll("_", "/");
        }

        return base64Url;
    }

    private static String base64ToBase64Url(String base64) {
        if (null != base64) {
            base64 = base64.replaceAll("\n", "");
            base64 = base64.replaceAll("\\+", "-");
            base64 = base64.replaceAll("/", "_");
            base64 = base64.replaceAll("=", "");
        }
        return base64;
    }

    private static String base64ToUnpaddedBase64(String base64) {
        if (null != base64) {
            base64 = base64.replaceAll("\n", "");
            base64 = base64.replaceAll("=", "");
        }

        return base64;
    }
}