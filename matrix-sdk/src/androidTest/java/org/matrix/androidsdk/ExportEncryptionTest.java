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
    public void checkExportDecrypt1() throws Exception {
        String password = "password";
        String input =  "-----BEGIN MEGOLM SESSION DATA-----\nAXNhbHRzYWx0c2FsdHNhbHSIiIiIiIiIiIiIiIiIiIiIAAAACmIRUW2OjZ3L2l6j9h0lHlV3M2dx\ncissyYBxjsfsAndErh065A8=\n-----END MEGOLM SESSION DATA-----";
        String expectedString = "plain";

        String decodedString = null;
        try {
            decodedString = MXMegolmExportEncryption.decryptMegolmKeyFile(input.getBytes("UTF-8"), password);
        } catch (Exception e) {
            assertTrue("## checkExportDecrypt1() failed : " + e.getMessage(), false);
        }

        assertTrue("## checkExportDecrypt1() : expectedString " + expectedString + " -- decodedString " + decodedString  ,TextUtils.equals(expectedString, decodedString));
    }
}
