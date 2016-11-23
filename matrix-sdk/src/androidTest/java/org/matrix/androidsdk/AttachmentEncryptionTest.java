package org.matrix.androidsdk;

import android.os.MemoryFile;
import android.support.test.runner.AndroidJUnit4;
import android.text.TextUtils;
import android.util.Base64;

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.junit.runners.MethodSorters;
import org.matrix.androidsdk.crypto.MXEncryptedAttachments;
import org.matrix.androidsdk.rest.model.EncryptedFileInfo;
import org.matrix.androidsdk.rest.model.EncryptedFileKey;

import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;

import static org.junit.Assert.assertTrue;


/**
 * Unit tests AttachmentEncryptionTest.
 */
@RunWith(AndroidJUnit4.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class AttachmentEncryptionTest {

    private String checkDecryption(String input, EncryptedFileInfo encryptedFileInfo) throws Exception  {
        byte[] in = Base64.decode(input, Base64.DEFAULT);

        MemoryFile memoryFile;

        if (0 == in.length) {
            memoryFile = new MemoryFile("file" + System.currentTimeMillis(), 0);
        } else {
            memoryFile = new MemoryFile("file" + System.currentTimeMillis(), in.length);
            memoryFile.getOutputStream().write(in);
        }

        InputStream decryptedStream = MXEncryptedAttachments.decryptAttachment(memoryFile.getInputStream(), encryptedFileInfo);

        assertTrue(null != decryptedStream);

        byte[] buffer = new byte[100];

        int len = decryptedStream.read(buffer);

        return Base64.encodeToString(buffer, 0, len, Base64.DEFAULT).replaceAll("\n", "").replaceAll("=", "");
    }


    @Test
    public void checkDecrypt1() throws Exception {
        EncryptedFileInfo encryptedFileInfo = new EncryptedFileInfo();
        encryptedFileInfo.v = "v1";
        HashMap<String, String> hashMap = new HashMap<>();
        hashMap.put("sha256", "47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU");
        encryptedFileInfo.hashes = hashMap;

        encryptedFileInfo.key = new EncryptedFileKey();
        encryptedFileInfo.key.alg =  "A256CTR";
        encryptedFileInfo.key.k = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
        encryptedFileInfo.key.key_ops = Arrays.asList("encrypt","decrypt");
        encryptedFileInfo.key.kty = "oct";

        encryptedFileInfo.iv = "AAAAAAAAAAAAAAAAAAAAAA";

        assertTrue(TextUtils.equals(checkDecryption("", encryptedFileInfo), ""));
    }

    @Test
    public void checkDecrypt2() throws Exception {
        EncryptedFileInfo encryptedFileInfo = new EncryptedFileInfo();
        encryptedFileInfo.v = "v1";
        HashMap<String, String> hashMap = new HashMap<>();
        hashMap.put("sha256", "geLWS2ptBew5aPLJRTK+QnI3Krdl3UaxN8qfahHWhfc");
        encryptedFileInfo.hashes = hashMap;

        encryptedFileInfo.key = new EncryptedFileKey();
        encryptedFileInfo.key.alg =  "A256CTR";
        encryptedFileInfo.key.k = "__________________________________________8";
        encryptedFileInfo.key.key_ops = Arrays.asList("encrypt","decrypt");
        encryptedFileInfo.key.kty = "oct";

        encryptedFileInfo.iv = "/////////////////////w";

        assertTrue(TextUtils.equals(checkDecryption("nZxRAVw962fwUQ5/", encryptedFileInfo), "SGVsbG8sIFdvcmxk"));
    }

    @Test
    public void checkDecrypt3() throws Exception {
        EncryptedFileInfo encryptedFileInfo = new EncryptedFileInfo();
        encryptedFileInfo.v = "v1";
        HashMap<String, String> hashMap = new HashMap<>();
        hashMap.put("sha256", "/K4w3G4zlLK312k66KxNPKDkWCn2QAH5aphAkuncTrQ");
        encryptedFileInfo.hashes = hashMap;

        encryptedFileInfo.key = new EncryptedFileKey();
        encryptedFileInfo.key.alg = "A256CTR";
        encryptedFileInfo.key.k = "__________________________________________8";
        encryptedFileInfo.key.key_ops = Arrays.asList("encrypt","decrypt");
        encryptedFileInfo.key.kty = "oct";

        encryptedFileInfo.iv = "/////////////////////w";

        assertTrue(TextUtils.equals(checkDecryption("tJVNBVJ/vl36UQt4Y5e5myqUL3M8OtjRVQljZ+LlwbJeucRIM7CeKDJGGOjlJ1bqpqUdl6zytXJ3dCyvnUi4eQ",
                encryptedFileInfo),
                "YWxwaGFudW1lcmljYWxseWFscGhhbnVtZXJpY2FsbHlhbHBoYW51bWVyaWNhbGx5YWxwaGFudW1lcmljYWxseQ"));
    }

    @Test
    public void checkDecrypt4() throws Exception {
        EncryptedFileInfo encryptedFileInfo = new EncryptedFileInfo();
        encryptedFileInfo.v = "v1";
        HashMap<String, String> hashMap = new HashMap<>();
        hashMap.put("sha256", "LYG/orOViuFwovJpv2YMLSsmVKwLt7pY3f8SYM7KU5E");
        encryptedFileInfo.hashes = hashMap;

        encryptedFileInfo.key = new EncryptedFileKey();
        encryptedFileInfo.key.alg =  "A256CTR";
        encryptedFileInfo.key.k = "__________________________________________8";
        encryptedFileInfo.key.key_ops = Arrays.asList("encrypt","decrypt");
        encryptedFileInfo.key.kty = "oct";

        encryptedFileInfo.iv = "/////////////////////w";

        assertTrue(!TextUtils.equals(checkDecryption(
                "tJVNBVJ/vl36UQt4Y5e5m84bRUrQHhcdLPvS/7EkDvlkDLZXamBB6k8THbiawiKZ5Mnq9PZMSSbgOCvmnUBOMA",
                encryptedFileInfo),
                "YWxwaGFudW1lcmljYWxseWFscGhhbnVtZXJpY2FsbHlhbHBoYW51bWVyaWNhbGx5YWxwaGFudW1lcmljYWxseQ"));
    }
}
