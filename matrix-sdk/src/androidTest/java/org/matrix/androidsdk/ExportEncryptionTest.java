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

package org.matrix.androidsdk;

import android.support.test.runner.AndroidJUnit4;

import org.junit.Assert;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.matrix.androidsdk.crypto.MXMegolmExportEncryption;

/**
 * Unit tests ExportEncryptionTest.
 */
@RunWith(AndroidJUnit4.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ExportEncryptionTest {

    @Test
    public void checkExportError1() throws Exception {
        String password = "password";
        String input = "-----";
        boolean failed = false;

        try {
            MXMegolmExportEncryption.decryptMegolmKeyFile(input.getBytes("UTF-8"), password);
        } catch (Exception e) {
            failed = true;
        }

        Assert.assertTrue(failed);
    }

    @Test
    public void checkExportError2() throws Exception {
        String password = "password";
        String input = "-----BEGIN MEGOLM SESSION DATA-----\n" + "-----";
        boolean failed = false;

        try {
            MXMegolmExportEncryption.decryptMegolmKeyFile(input.getBytes("UTF-8"), password);
        } catch (Exception e) {
            failed = true;
        }

        Assert.assertTrue(failed);
    }

    @Test
    public void checkExportError3() throws Exception {
        String password = "password";
        String input = "-----BEGIN MEGOLM SESSION DATA-----\n" +
                " AXNhbHRzYWx0c2FsdHNhbHSIiIiIiIiIiIiIiIiIiIiIAAAACmIRUW2OjZ3L2l6j9h0lHlV3M2dx\n" +
                " cissyYBxjsfsAn\n" +
                " -----END MEGOLM SESSION DATA-----";
        boolean failed = false;

        try {
            MXMegolmExportEncryption.decryptMegolmKeyFile(input.getBytes("UTF-8"), password);
        } catch (Exception e) {
            failed = true;
        }

        Assert.assertTrue(failed);
    }

    @Test
    public void checkExportDecrypt1() throws Exception {
        String password = "password";
        String input = "-----BEGIN MEGOLM SESSION DATA-----\nAXNhbHRzYWx0c2FsdHNhbHSIiIiIiIiIiIiIiIiIiIiIAAAACmIRUW2OjZ3L2l6j9h0lHlV3M2dx\n" +
                "cissyYBxjsfsAndErh065A8=\n-----END MEGOLM SESSION DATA-----";
        String expectedString = "plain";

        String decodedString = null;
        try {
            decodedString = MXMegolmExportEncryption.decryptMegolmKeyFile(input.getBytes("UTF-8"), password);
        } catch (Exception e) {
            Assert.fail("## checkExportDecrypt1() failed : " + e.getMessage());
        }

        Assert.assertEquals("## checkExportDecrypt1() : expectedString " + expectedString + " -- decodedString " + decodedString,
                expectedString,
                decodedString);
    }

    @Test
    public void checkExportDecrypt2() throws Exception {
        String password = "betterpassword";
        String input = "-----BEGIN MEGOLM SESSION DATA-----\nAW1vcmVzYWx0bW9yZXNhbHT//////////wAAAAAAAAAAAAAD6KyBpe1Niv5M5NPm4ZATsJo5nghk\n" +
                "KYu63a0YQ5DRhUWEKk7CcMkrKnAUiZny\n-----END MEGOLM SESSION DATA-----";
        String expectedString = "Hello, World";

        String decodedString = null;
        try {
            decodedString = MXMegolmExportEncryption.decryptMegolmKeyFile(input.getBytes("UTF-8"), password);
        } catch (Exception e) {
            Assert.fail("## checkExportDecrypt2() failed : " + e.getMessage());
        }

        Assert.assertEquals("## checkExportDecrypt2() : expectedString " + expectedString + " -- decodedString " + decodedString,
                expectedString,
                decodedString);
    }

    @Test
    public void checkExportDecrypt3() throws Exception {
        String password = "SWORDFISH";
        String input = "-----BEGIN MEGOLM SESSION DATA-----\nAXllc3NhbHR5Z29vZG5lc3P//////////wAAAAAAAAAAAAAD6OIW+Je7gwvjd4kYrb+49gKCfExw\n" +
                "MgJBMD4mrhLkmgAngwR1pHjbWXaoGybtiAYr0moQ93GrBQsCzPbvl82rZhaXO3iH5uHo/RCEpOqp\nPgg29363BGR+/Ripq/VCLKGNbw==\n-----END MEGOLM SESSION DATA-----";
        String expectedString = "alphanumericallyalphanumericallyalphanumericallyalphanumerically";

        String decodedString = null;
        try {
            decodedString = MXMegolmExportEncryption.decryptMegolmKeyFile(input.getBytes("UTF-8"), password);
        } catch (Exception e) {
            Assert.fail("## checkExportDecrypt3() failed : " + e.getMessage());
        }

        Assert.assertEquals("## checkExportDecrypt3() : expectedString " + expectedString + " -- decodedString " + decodedString,
                expectedString,
                decodedString);
    }

    @Test
    public void checkExportEncrypt1() throws Exception {
        String password = "password";
        String expectedString = "plain";
        String decodedString = null;

        try {
            decodedString = MXMegolmExportEncryption
                    .decryptMegolmKeyFile(MXMegolmExportEncryption.encryptMegolmKeyFile(expectedString, password, 1000), password);
        } catch (Exception e) {
            Assert.fail("## checkExportEncrypt1() failed : " + e.getMessage());
        }

        Assert.assertEquals("## checkExportEncrypt1() : expectedString " + expectedString + " -- decodedString " + decodedString,
                expectedString,
                decodedString);
    }

    @Test
    public void checkExportEncrypt2() throws Exception {
        String password = "betterpassword";
        String expectedString = "Hello, World";
        String decodedString = null;

        try {
            decodedString = MXMegolmExportEncryption
                    .decryptMegolmKeyFile(MXMegolmExportEncryption.encryptMegolmKeyFile(expectedString, password, 1000), password);
        } catch (Exception e) {
            Assert.fail("## checkExportEncrypt2() failed : " + e.getMessage());
        }

        Assert.assertEquals("## checkExportEncrypt2() : expectedString " + expectedString + " -- decodedString " + decodedString,
                expectedString,
                decodedString);
    }

    @Test
    public void checkExportEncrypt3() throws Exception {
        String password = "SWORDFISH";
        String expectedString = "alphanumericallyalphanumericallyalphanumericallyalphanumerically";
        String decodedString = null;

        try {
            decodedString = MXMegolmExportEncryption
                    .decryptMegolmKeyFile(MXMegolmExportEncryption.encryptMegolmKeyFile(expectedString, password, 1000), password);
        } catch (Exception e) {
            Assert.fail("## checkExportEncrypt3() failed : " + e.getMessage());
        }

        Assert.assertEquals("## checkExportEncrypt3() : expectedString " + expectedString + " -- decodedString " + decodedString,
                expectedString,
                decodedString);
    }

    @Test
    public void checkExportEncrypt4() throws Exception {
        String password = "passwordpasswordpasswordpasswordpasswordpasswordpasswordpasswordpasswordpasswordpasswordpasswordpasswordpasswordpasswordpassword" +
                "passwordpasswordpasswordpasswordpasswordpasswordpasswordpasswordpasswordpasswordpasswordpasswordpasswordpasswordpasswordpassword";
        String expectedString = "alphanumericallyalphanumericallyalphanumericallyalphanumerically";
        String decodedString = null;

        try {
            decodedString = MXMegolmExportEncryption
                    .decryptMegolmKeyFile(MXMegolmExportEncryption.encryptMegolmKeyFile(expectedString, password, 1000), password);
        } catch (Exception e) {
            Assert.fail("## checkExportEncrypt4() failed : " + e.getMessage());
        }

        Assert.assertEquals("## checkExportEncrypt4() : expectedString " + expectedString + " -- decodedString " + decodedString,
                expectedString,
                decodedString);
    }
}
