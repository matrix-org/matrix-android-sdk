package org.matrix.androidsdk;

import android.support.test.runner.AndroidJUnit4;
import android.text.TextUtils;

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.junit.runners.MethodSorters;
import org.matrix.androidsdk.crypto.MXMegolmExportEncryption;

import static org.junit.Assert.assertTrue;


/**
 * Unit tests ExportEncryptionTest.
 */
@RunWith(AndroidJUnit4.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ExportEncryptionTest {


    @Test
    public void checkExportEncrypt1() throws Exception {
        String password = "password";
        String expectedString = "plain";
        String decodedString = null;

        try {
            decodedString = MXMegolmExportEncryption.decryptMegolmKeyFile(MXMegolmExportEncryption.encryptMegolmKeyFile(expectedString, password, 1000), password);
        } catch (Exception e) {
            assertTrue("## checkExportEncrypt1() failed : " + e.getMessage(), false);
        }

        assertTrue("## checkExportEncrypt1() : expectedString " + expectedString + " -- decodedString " + decodedString  ,TextUtils.equals(expectedString, decodedString));
    }
}
