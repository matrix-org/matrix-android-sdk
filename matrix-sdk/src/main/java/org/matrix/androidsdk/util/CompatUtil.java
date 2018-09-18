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

package org.matrix.androidsdk.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.security.KeyPairGeneratorSpec;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.util.Base64;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.RSAKeyGenParameterSpec;
import java.util.Calendar;
import java.util.zip.GZIPOutputStream;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.security.auth.x500.X500Principal;

public class CompatUtil {
    private static final String TAG = CompatUtil.class.getSimpleName();
    private static final String ANDROID_KEY_STORE_PROVIDER = "AndroidKeyStore";
    private static final String AES_GCM_CIPHER_TYPE = "AES/GCM/NoPadding";
    private static final int AES_GCM_KEY_SIZE_IN_BITS = 128;
    private static final int AES_GCM_IV_LENGTH = 12;
    private static final String AES_LOCAL_PROTECTION_KEY_ALIAS = "aes_local_protection";

    private static final String RSA_WRAP_LOCAL_PROTECTION_KEY_ALIAS = "rsa_wrap_local_protection";
    private static final String RSA_WRAP_CIPHER_TYPE = "RSA/NONE/PKCS1Padding";
    private static final String AES_WRAPPED_PROTECTION_KEY_SHARED_PREFERENCE = "aes_wrapped_local_protection";

    private static SecretKey sLocalProtectionKey;
    private static SecureRandom sPrng;

    /**
     * Create a GZIPOutputStream instance
     * Special treatment on KitKat device, force the syncFlush param to false
     * Before Kitkat, this param does not exist and after Kitkat it is set to false by default
     *
     * @param outputStream the output stream
     */
    public static GZIPOutputStream createGzipOutputStream(OutputStream outputStream) throws IOException {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT) {
            return new GZIPOutputStream(outputStream, false);
        } else {
            return new GZIPOutputStream(outputStream);
        }
    }

    /**
     * Returns the AES key used for local storage encryption/decryption with AES/GCM.
     * The key is created if it does not exist already in the keystore.
     * From Marshmallow, this key is generated and operated directly from the android keystore.
     * From KitKat and before Marshmallow, this key is stored in the application shared preferences
     * wrapped by a RSA key generated and operated directly from the android keystore.
     * Before Kitkat, this param does not exist and after Kitkat it is set to false by default.
     *
     * @param context the context holding the application shared preferences
     */
    @RequiresApi(Build.VERSION_CODES.KITKAT)
    private static synchronized SecretKey getAesGcmLocalProtectionKey(Context context)
            throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException,
            NoSuchProviderException, InvalidAlgorithmParameterException, NoSuchPaddingException,
            InvalidKeyException, IllegalBlockSizeException, UnrecoverableKeyException {
        if (sLocalProtectionKey == null) {
            final KeyStore keyStore = KeyStore.getInstance(ANDROID_KEY_STORE_PROVIDER);
            keyStore.load(null);

            Log.i(TAG, "Loading local protection key");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (keyStore.containsAlias(AES_LOCAL_PROTECTION_KEY_ALIAS)) {
                    Log.i(TAG, "AES local protection key found in keystore");
                    sLocalProtectionKey = (SecretKey) keyStore.getKey(AES_LOCAL_PROTECTION_KEY_ALIAS, null);
                } else {
                    Log.i(TAG, "Generating AES key with keystore");
                    final KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE_PROVIDER);
                    generator.init(
                            new KeyGenParameterSpec.Builder(AES_LOCAL_PROTECTION_KEY_ALIAS,
                                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                                    .setKeySize(AES_GCM_KEY_SIZE_IN_BITS)
                                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                                    .build());
                    sLocalProtectionKey = generator.generateKey();
                }
            } else {
                final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
                final String wrappedAesKeyString = sharedPreferences.getString(AES_WRAPPED_PROTECTION_KEY_SHARED_PREFERENCE, null);
                if (wrappedAesKeyString != null && keyStore.containsAlias(RSA_WRAP_LOCAL_PROTECTION_KEY_ALIAS)) {
                    Log.i(TAG, "RSA + wrapped AES local protection keys found in keystore");
                    final PrivateKey privateKey = (PrivateKey) keyStore.getKey(RSA_WRAP_LOCAL_PROTECTION_KEY_ALIAS, null);
                    final byte[] wrappedAesKey = Base64.decode(wrappedAesKeyString, 0);
                    final Cipher cipher = Cipher.getInstance(RSA_WRAP_CIPHER_TYPE);
                    cipher.init(Cipher.UNWRAP_MODE, privateKey);
                    sLocalProtectionKey = (SecretKey) cipher.unwrap(wrappedAesKey, "AES", Cipher.SECRET_KEY);
                } else {
                    Log.i(TAG, "Generating RSA key pair with keystore");
                    final KeyPairGenerator generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEY_STORE_PROVIDER);
                    final Calendar start = Calendar.getInstance();
                    final Calendar end = Calendar.getInstance();
                    end.add(Calendar.YEAR, 10);

                    generator.initialize(
                            new KeyPairGeneratorSpec.Builder(context)
                                    .setAlgorithmParameterSpec(new RSAKeyGenParameterSpec(2048, RSAKeyGenParameterSpec.F4))
                                    .setAlias(RSA_WRAP_LOCAL_PROTECTION_KEY_ALIAS)
                                    .setSubject(new X500Principal("CN=matrix-android-sdk"))
                                    .setStartDate(start.getTime())
                                    .setEndDate(end.getTime())
                                    .setSerialNumber(BigInteger.ONE)
                                    .build());
                    final KeyPair keyPair = generator.generateKeyPair();

                    Log.i(TAG, "Generating wrapped AES key");

                    final byte[] aesKeyRaw = new byte[AES_GCM_KEY_SIZE_IN_BITS / Byte.SIZE];
                    getPrng().nextBytes(aesKeyRaw);
                    sLocalProtectionKey = new SecretKeySpec(aesKeyRaw, "AES");

                    final Cipher cipher = Cipher.getInstance(RSA_WRAP_CIPHER_TYPE);
                    cipher.init(Cipher.WRAP_MODE, keyPair.getPublic());
                    byte[] wrappedAesKey = cipher.wrap(sLocalProtectionKey);

                    final SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
                    editor.putString(AES_WRAPPED_PROTECTION_KEY_SHARED_PREFERENCE, Base64.encodeToString(wrappedAesKey, 0));
                    editor.apply();
                }
            }
        }

        return sLocalProtectionKey;
    }


    /**
     * Returns the unique SecureRandom instance shared for all local storage encryption operations.
     */
    private static SecureRandom getPrng() {
        if (sPrng == null) {
            sPrng = new SecureRandom();
        }

        return sPrng;
    }

    /**
     * Create a CipherOutputStream instance.
     * Before Kitkat, this method will return out as local storage encryption is not implemented for
     * devices before KitKat.
     *
     * @param out the output stream
     * @param context the context holding the application shared preferences
     */
    @Nullable
    public static OutputStream createCipherOutputStream(OutputStream out, Context context)
            throws IOException, CertificateException, NoSuchAlgorithmException,
            UnrecoverableKeyException, InvalidKeyException, InvalidAlgorithmParameterException,
            NoSuchPaddingException, NoSuchProviderException, KeyStoreException, IllegalBlockSizeException {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return out;
        }

        final SecretKey key = getAesGcmLocalProtectionKey(context);
        if (key == null) {
            throw new KeyStoreException();
        }

        final Cipher cipher = Cipher.getInstance(AES_GCM_CIPHER_TYPE);
        byte[] iv = null;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            cipher.init(Cipher.ENCRYPT_MODE, key);
            iv = cipher.getIV();
        } else {
            iv = new byte[AES_GCM_IV_LENGTH];
            getPrng().nextBytes(iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));
        }

        if (iv.length != AES_GCM_IV_LENGTH) {
            Log.e(TAG, "Invalid IV length " + iv.length);
            return null;
        }

        out.write(iv.length);
        out.write(iv);

        return new CipherOutputStream(out, cipher);
    }

    /**
     * Create a CipherInputStream instance.
     * Before Kitkat, this method will return in as local storage encryption is not implemented
     * for devices before KitKat.
     *
     * @param in the output stream
     * @param context the context holding the application shared preferences
     */
    @Nullable
    public static InputStream createCipherInputStream(InputStream in, Context context)
            throws NoSuchPaddingException, NoSuchAlgorithmException, CertificateException,
            InvalidKeyException, KeyStoreException, UnrecoverableKeyException, IllegalBlockSizeException,
            NoSuchProviderException, InvalidAlgorithmParameterException, IOException {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return in;
        }

        in.mark(4 + AES_GCM_IV_LENGTH);
        final int iv_len = in.read();
        if (iv_len != AES_GCM_IV_LENGTH) {
            Log.e(TAG, "Invalid IV length " + iv_len);
            in.reset();
            return null;
        }

        final byte[] iv = new byte[iv_len];
        in.read(iv);

        final Cipher cipher = Cipher.getInstance(AES_GCM_CIPHER_TYPE);

        final SecretKey key = getAesGcmLocalProtectionKey(context);
        if (key == null) {
            throw new KeyStoreException();
        }

        AlgorithmParameterSpec spec = null;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            spec = new GCMParameterSpec(AES_GCM_KEY_SIZE_IN_BITS, iv);
        } else {
            spec = new IvParameterSpec(iv);
        }

        cipher.init(Cipher.DECRYPT_MODE, key, spec);

        return new CipherInputStream(in, cipher);
    }
}
