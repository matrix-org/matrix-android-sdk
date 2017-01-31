/*
 * Copyright 2017 OpenMarket Ltd
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

import org.matrix.androidsdk.util.Log;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Utility class to import/export the crypto data
 */
public class MXMegolmExportEncryption {
    private static final String LOG_TAG = "MXCryptoExport";

    /**
     * Convert a signed byte to a int value
     * @param bVal teh byte value to convert
     * @return the matched int value
     */
    private static final int byteToInt(byte bVal) {
        return bVal & 0xFF;
    }

    /**
     * Decrypt a megolm key file
     * @param data the data to decrypt
     * @param password the password.
     * @return the decrypted output.
     */
    public static String decryptMegolmKeyFile(byte[] data, String password) throws Exception {
        byte[] body = unpackMegolmKeyFile(data);

        // check we have a version byte
        if ((null == body) || (body.length == 0)) {
            Log.e(LOG_TAG, "## decryptMegolmKeyFile() : Invalid file: too short");
            throw new Exception("Invalid file: too short");
        }

        byte version = body[0];
        if (version != 1) {
            Log.e(LOG_TAG, "## decryptMegolmKeyFile() : Invalid file: too short");
            throw new Exception("Unsupported version");
        }

        int ciphertextLength = body.length-(1+16+16+4+32);
        if (body.length < 0) {
            throw new Error("Invalid file: too short");
        }

        byte[] salt = Arrays.copyOfRange(body, 1, 1+16);
        byte[] iv = Arrays.copyOfRange(body, 17, 17+16);
        int iterations = byteToInt(body[33]) << 24 | byteToInt(body[34])<< 16 | byteToInt(body[35]) << 8 | byteToInt(body[36]);
        byte[] ciphertext = Arrays.copyOfRange(body, 37, 37+ciphertextLength);
        byte[] hmac = Arrays.copyOfRange(body, body.length - 32, body.length);

        DeriveKeysRes deriveKeysRes = deriveKeys(salt, iterations, password);

        byte[] toVerify = Arrays.copyOfRange(body, 0, body.length - 32);

        SecretKey macKey = new SecretKeySpec(deriveKeysRes.hmac_key, "HmacSHA256");
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(macKey);
        byte[] digest = mac.doFinal(toVerify);

        if (!Arrays.equals(hmac, digest)) {
            Log.e(LOG_TAG, "## decryptMegolmKeyFile() : Authentication check failed: incorrect password?");
            throw new Exception("Authentication check failed: incorrect password?");
        }

        Cipher decryptCipher = Cipher.getInstance("AES/CTR/NoPadding");

        SecretKeySpec secretKeySpec = new SecretKeySpec(deriveKeysRes.aes_key, "AES");
        IvParameterSpec ivParameterSpec = new IvParameterSpec(iv);
        decryptCipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec);

        ByteArrayOutputStream outStream = new ByteArrayOutputStream();
        outStream.write(decryptCipher.update(ciphertext));
        outStream.write(decryptCipher.doFinal());

        String decodedString = new String(outStream.toByteArray(), "UTF-8");

        outStream.close();

        return decodedString;
    }

    static final String HEADER_LINE = "-----BEGIN MEGOLM SESSION DATA-----";
    static final String TRAILER_LINE = "-----END MEGOLM SESSION DATA-----";

    /**
     * Unbase64 an ascii-armoured megolm key file
     * Strips the header and trailer lines, and unbase64s the content
     * @param data the input data
     * @return unbase64ed content
     */
    private static byte[] unpackMegolmKeyFile(byte[] data) throws Exception {
        String fileStr = new String(data, "UTF-8");

        // look for the start line
        int lineStart = 0;

        while (true) {
            int lineEnd = fileStr.indexOf('\n', lineStart);

            if (lineEnd < 0) {
                Log.e(LOG_TAG, "## unpackMegolmKeyFile() : Header line not found");
                throw new Exception("Header line not found");
            }

            String line = fileStr.substring(lineStart, lineEnd).trim();

            // start the next line after the newline
            lineStart = lineEnd+1;

            if (TextUtils.equals(line, HEADER_LINE)) {
                break;
            }
        }

        int dataStart = lineStart;

        // look for the end line
        while (true) {
            int lineEnd = fileStr.indexOf('\n', lineStart);
            String line;

            if (lineEnd < 0) {
                line = fileStr.substring(lineStart).trim();
            } else {
                line = fileStr.substring(lineStart, lineEnd).trim();
            }

            if (TextUtils.equals(line, TRAILER_LINE)) {
                break;
            }

            if (lineEnd < 0) {
                Log.e(LOG_TAG, "## unpackMegolmKeyFile() : Trailer line not found");
                throw new Exception("Trailer line not found");
            }

            // start the next line after the newline
            lineStart = lineEnd+1;
        }

        int dataEnd = lineStart;

        // Receiving side
        return Base64.decode(fileStr.substring(dataStart, dataEnd), Base64.DEFAULT);
    }

    private static class DeriveKeysRes {
        public DeriveKeysRes() {

        }

        public byte[] aes_key;
        public byte[] hmac_key;
    }

    /**
     * Derive the AES and HMAC-SHA-256 keys for the file
     *
     * @param salt  salt for pbkdf
     * @param iterations number of pbkdf iterations
     * @param password  password
     * @return {Promise<[CryptoKey, CryptoKey]>} promise for [aes key, hmac key]
     */
    private static DeriveKeysRes deriveKeys(byte[] salt, int iterations, String password) throws Exception {
        DeriveKeysRes res = new DeriveKeysRes();

        PBEKeySpec keySpec = new PBEKeySpec(password.toCharArray(), salt, iterations, 512);
        SecretKey derivedKey = new PBKDF2KeyImpl(keySpec, "HmacSHA512");

        byte[] keybits = derivedKey.getEncoded();

        byte[] aes_key = Arrays.copyOfRange(keybits, 0, 32);
        byte[] hmac_key = Arrays.copyOfRange(keybits, 32, keybits.length);

        SecretKeySpec key = new SecretKeySpec(Arrays.copyOfRange(aes_key, 0, 16), "AES");
        IvParameterSpec iv = new IvParameterSpec(Arrays.copyOfRange(aes_key, 16, 32));

        Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, iv);
        res.aes_key = aes_key;

        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secret_key = new SecretKeySpec(hmac_key, "HmacSHA256");
        sha256_HMAC.init(secret_key);
        res.hmac_key = hmac_key;

        return res;
    }
}