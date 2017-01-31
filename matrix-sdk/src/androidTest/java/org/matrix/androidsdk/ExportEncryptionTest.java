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

    @Test
    public void checkExportDecrypt2() throws Exception {
        String password = "betterpassword";
        String input =  "-----BEGIN MEGOLM SESSION DATA-----\nAW1vcmVzYWx0bW9yZXNhbHT//////////wAAAAAAAAAAAAAD6KyBpe1Niv5M5NPm4ZATsJo5nghk\nKYu63a0YQ5DRhUWEKk7CcMkrKnAUiZny\n-----END MEGOLM SESSION DATA-----";
        String expectedString = "Hello, World";

        String decodedString = null;
        try {
            decodedString = MXMegolmExportEncryption.decryptMegolmKeyFile(input.getBytes("UTF-8"), password);
        } catch (Exception e) {
            assertTrue("## checkExportDecrypt2() failed : " + e.getMessage(), false);
        }

        assertTrue("## checkExportDecrypt2() : expectedString " + expectedString + " -- decodedString " + decodedString  ,TextUtils.equals(expectedString, decodedString));
    }

    @Test
    public void checkExportDecrypt3() throws Exception {
        String password = "SWORDFISH";
        String input =  "-----BEGIN MEGOLM SESSION DATA-----\nAXllc3NhbHR5Z29vZG5lc3P//////////wAAAAAAAAAAAAAD6OIW+Je7gwvjd4kYrb+49gKCfExw\nMgJBMD4mrhLkmgAngwR1pHjbWXaoGybtiAYr0moQ93GrBQsCzPbvl82rZhaXO3iH5uHo/RCEpOqp\nPgg29363BGR+/Ripq/VCLKGNbw==\n-----END MEGOLM SESSION DATA-----";
        String expectedString = "alphanumericallyalphanumericallyalphanumericallyalphanumerically";

        String decodedString = null;
        try {
            decodedString = MXMegolmExportEncryption.decryptMegolmKeyFile(input.getBytes("UTF-8"), password);
        } catch (Exception e) {
            assertTrue("## checkExportDecrypt3() failed : " + e.getMessage(), false);
        }

        assertTrue("## checkExportDecrypt3() : expectedString " + expectedString + " -- decodedString " + decodedString  ,TextUtils.equals(expectedString, decodedString));
    }

    @Test
    public void checkExportDecrypt4() throws Exception {
        String password = "passwordpasswordpasswordpasswordpasswordpasswordpasswordpasswordpasswordpasswordpasswordpasswordpasswordpasswordpasswordpasswordpasswordpasswordpasswordpasswordpasswordpasswordpasswordpasswordpasswordpasswordpasswordpasswordpasswordpasswordpasswordpassword";
        String input =  "-----BEGIN MEGOLM SESSION DATA-----\nAf//////////////////////////////////////////AAAD6IAZJy7IQ7Y0idqSw/bmpngEEVVh\ngsH+8ptgqxw6ZVWQnohr8JsuwH9SwGtiebZuBu5smPCO+RFVWH2cQYslZijXv/BEH/txvhUrrtCd\nbWnSXS9oymiqwUIGs08sXI33ZA==\n-----END MEGOLM SESSION DATA-----";
        String expectedString = "alphanumericallyalphanumericallyalphanumericallyalphanumerically";

        String decodedString = null;
        try {
            decodedString = MXMegolmExportEncryption.decryptMegolmKeyFile(input.getBytes("UTF-8"), password);
        } catch (Exception e) {
            assertTrue("## checkExportDecrypt3() failed : " + e.getMessage(), false);
        }

        assertTrue("## checkExportDecrypt3() : expectedString " + expectedString + " -- decodedString " + decodedString  ,TextUtils.equals(expectedString, decodedString));
    }

}
